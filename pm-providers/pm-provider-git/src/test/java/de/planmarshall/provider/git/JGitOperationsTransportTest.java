/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.git;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.provider.git.GitOperations.CommitInfo;
import de.planmarshall.provider.git.GitOperations.GitCredential;
import de.planmarshall.provider.git.GitOperations.PushInput;
import de.planmarshall.provider.git.GitOperations.RemoteInput;
import de.planmarshall.provider.git.testing.generators.GitGenerators;

/**
 * The transport of {@link JGitOperations} over {@code file://}: push, fetch, the non-fast-forward and lease refusals,
 * the remote policy, and the credential's equality.
 */
@EnableGeneratorController
@DisplayName("JGitOperations (native git variant): fetch and push over file://")
class JGitOperationsTransportTest extends JGitOperationsFixtures {

    private Path remote;

    private String remoteHead;

    @BeforeEach
    void bareRemote() throws GitAPIException, IOException, URISyntaxException {
        remoteHead = "refs/heads/" + branch;
        remote = temp.resolve("remote.git");
        Git.init().setBare(true).setDirectory(remote.toFile()).setInitialBranch(branch).setFs(new RefusingFS())
                .call().close();
        try (Git jgit = Git.open(repo.toFile())) {
            jgit.remoteAdd().setName(ORIGIN).setUri(new URIish(remote.toUri().toString())).call();
        }
    }

    private GitResult<GitOperations.RefUpdate> push(Path root, String expected) {
        return git.push(new PushInput(root, ORIGIN, HEAD, remoteHead, expected,
                null));
    }

    @Test
    @DisplayName("pushes, fetches into a second clone, and rejects a non-fast-forward and a stale lease")
    void pushFetchReject() throws Exception {
        String head = git.open(repo).value().head();
        var pushed = push(repo, null);
        assertEquals(GitOutcome.OK, pushed.outcome(), "the first push is accepted");
        assertEquals(head, pushed.value().newId(), "the remote now holds the head");

        Path other = temp.resolve("other");
        Git.cloneRepository().setURI(remote.toUri().toString()).setDirectory(other.toFile()).setFs(new RefusingFS())
                .call().close();
        Files.writeString(other.resolve(file()), text());
        CommitInfo otherCommit = commitIn(other, text());
        assertEquals(GitOutcome.OK, push(other, head).outcome(), "lease on the expected head holds");

        write(file(), text());
        commit(text());
        assertEquals(GitOutcome.REJECTED_NON_FAST_FORWARD, push(repo, null).outcome(),
                "a push behind the remote is no fast-forward");
        assertEquals(GitOutcome.REJECTED_LEASE, push(repo, head).outcome(), "a stale lease is rejected");

        var fetched = git.fetch(new RemoteInput(repo, ORIGIN, List.of(), null));
        assertEquals(GitOutcome.OK, fetched.outcome(), "the fetch from the remote succeeds");
        assertEquals(otherCommit.id(), fetched.value().stream()
                .filter(u -> ("refs/remotes/" + ORIGIN + "/" + branch).equals(u.ref())).findFirst().orElseThrow()
                .newId(), "the tracking ref holds the other clone's commit");
    }

    @Test
    @DisplayName("refuses remotes outside the policy before sending anything, and unknown remotes")
    void refusesRemotes() {
        // the https origin of the credential entry is the contract of the remote policy
        var httpsOnly = new JGitOperations(RemotePolicy.httpsOrigin(URI.create("https://github.com")));
        var generated = GitGenerators.credentials().next();
        var secret = new String(generated.secret());
        var credential = generated;

        assertAll(
                () -> assertEquals(GitOutcome.REMOTE_ORIGIN_MISMATCH,
                        httpsOnly.fetch(new RemoteInput(repo, ORIGIN, List.of(), credential)).outcome(),
                        "a fetch outside the policy is refused"),
                () -> assertEquals(GitOutcome.REMOTE_ORIGIN_MISMATCH, httpsOnly.push(new PushInput(repo, ORIGIN,
                                HEAD, remoteHead, null, credential)).outcome(),
                        "a push outside the policy is refused"),
                () -> assertEquals(GitOutcome.UNKNOWN_REVISION,
                        git.fetch(new RemoteInput(repo, names.next(), List.of(), null)).outcome(),
                        "a fetch from an unknown remote is refused"),
                () -> assertEquals(GitOutcome.UNKNOWN_REVISION, git.push(new PushInput(repo, ORIGIN, names.next(),
                                remoteHead, null, null)).outcome(),
                        "a push of an unknown ref is refused"),
                () -> assertFalse(credential.toString().contains(secret),
                        "the credential's text hides the secret"));
    }

    @Test
    @DisplayName("compares credentials by the content of the secret")
    void credentialEquality() {
        var credential = GitGenerators.credentials().next();
        var same = new GitCredential(credential.username(), credential.secret().clone());
        // the other values extend the generated ones, so they differ whatever was generated
        var otherSecret = (new String(credential.secret()) + text()).toCharArray();
        var otherUser = credential.username() + text();

        assertAll(
                () -> assertEquals(credential, same, "an equal secret in another array is equal"),
                () -> assertEquals(credential.hashCode(), same.hashCode(), "equal credentials share a hash code"),
                () -> assertNotEquals(credential, new GitCredential(credential.username(), otherSecret),
                        "another secret makes another credential"),
                () -> assertNotEquals(credential, new GitCredential(otherUser, credential.secret().clone()),
                        "another user makes another credential"),
                () -> assertNotEquals(credential, (Object) new String(credential.secret()),
                        "a credential never equals its secret text"));
    }
}
