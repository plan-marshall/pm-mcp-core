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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;
import de.cuioss.test.generator.domain.EmailGenerator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.provider.git.GitOperations.CommitInfo;
import de.planmarshall.provider.git.GitOperations.CommitInput;
import de.planmarshall.provider.git.GitOperations.Identity;
import de.planmarshall.provider.git.GitOperations.WorktreeSha;

/**
 * The fixture the {@link JGitOperations} test classes share: one initialised repository with its README commit, the
 * generated names and identities, and the file helpers.
 */
abstract class JGitOperationsFixtures {

    /** The revision git resolves to the checked-out commit: part of git's revision grammar. */
    static final String HEAD = "HEAD";

    /** The remote name git gives the source of a clone: git's own default. */
    static final String ORIGIN = "origin";

    @TempDir
    Path temp;

    JGitOperations git;

    Path repo;

    Identity author;

    Identity agent;

    String branch;

    String readme;

    TypedGenerator<String> names;

    @BeforeEach
    void initRepository() throws IOException {
        // names from lower-case letters outside the hex digits: valid ref and path names, never a commit id, ordered
        // as the administration directories are
        names = Generators.uniqueValues(Generators.strings("ghijklmnopqrstuvwxyz", 4, 12));
        author = new Identity(Generators.letterStrings(3, 12).next(), new EmailGenerator().next());
        agent = new Identity(Generators.letterStrings(3, 12).next(), new EmailGenerator().next());
        branch = names.next();
        readme = text();
        git = new JGitOperations(RemotePolicy.localOnly());
        // fixture path of the test repository
        repo = temp.resolve("repo");
        assertTrue(git.init(repo, branch).isOk(), "the test repository is initialised");
        write("README.md", readme);
        commit(text());
    }

    static String text() {
        return Generators.letterStrings(1, 40).next();
    }

    String file() {
        return names.next() + ".txt";
    }

    void write(String path, String content) throws IOException {
        Path file = repo.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    CommitInfo commit(String message) {
        return commitIn(repo, message);
    }

    CommitInfo commitIn(Path root, String message) {
        GitResult<CommitInfo> result = git.commit(new CommitInput(root, message, author, agent, List.of()));
        assertEquals(GitOutcome.OK, result.outcome(), "the fixture commit is recorded");
        return result.value();
    }

    String sha(Path root) {
        GitResult<WorktreeSha> result = git.worktreeSha(root);
        assertEquals(GitOutcome.OK, result.outcome(), "the working-tree digest is computed");
        // contract literal: the worktree-sha hash version pinned by the specification is 1
        assertEquals(1, result.value().version(),
                "the digest carries hash version 1");
        return result.value().digest();
    }
}
