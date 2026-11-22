/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.git.primitive;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;
import de.cuioss.test.generator.domain.EmailGenerator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.spi.PrimitiveResult;
import de.planmarshall.core.spi.WorkflowExecutionContext;
import de.planmarshall.core.store.ScopeType;
import de.planmarshall.provider.git.GitOperations;
import de.planmarshall.provider.git.GitOperations.CommitInfo;
import de.planmarshall.provider.git.GitOperations.CommitInput;
import de.planmarshall.provider.git.GitOperations.Identity;
import de.planmarshall.provider.git.JGitOperations;
import de.planmarshall.provider.git.RemotePolicy;
import de.planmarshall.provider.git.primitive.GitStageAndCommitPrimitive.Outcome;
import de.planmarshall.provider.git.primitive.GitStageAndCommitPrimitive.Params;
import de.planmarshall.provider.git.primitive.GitStageAndCommitPrimitive.Reason;
import de.planmarshall.provider.git.testing.generators.GitGenerators;

/**
 * The fixture the {@code git.stage-and-commit} tests share: an EasyMock {@link CommitSite} over the real
 * {@link JGitOperations} on a temporary repository, with the commit read-back helpers.
 */
abstract class GitStageAndCommitPrimitiveFixtures {

    /** The directory of the epic records, a path of the staging-set contract. */
    static final String EPICS = ".marshall/epics/";

    /** The directory of the archived epic records, a path of the staging-set contract. */
    static final String ARCHIVED_EPICS = ".marshall/archived-epics/";

    @TempDir
    Path temp;

    final TypedGenerator<String> names =
            Generators.uniqueValues(Generators.strings("ghijklmnopqrstuvwxyz", 4, 12));

    final TypedGenerator<String> messages = GitGenerators.commitMessages();

    JGitOperations git;

    Path repo;

    Identity operator;

    Identity agent;

    CommitSite site;

    WorkflowExecutionContext context;

    @BeforeEach
    void initRepository() throws IOException {
        git = new JGitOperations(RemotePolicy.localOnly());
        // fixture path of the test repository
        repo = temp.resolve("repo");
        assertTrue(git.init(repo, names.next()).isOk(), "the test repository is initialised");
        operator = identity();
        agent = identity();
        write("README.md");
        assertTrue(git.commit(CommitInput.builder().root(repo).message(messages.next()).author(operator)
                .committer(agent).build()).isOk(), "the fixture commit is recorded");
        site = createMock(CommitSite.class);
        context = createMock(WorkflowExecutionContext.class);
    }

    void stub(ScopeType scope, boolean archiving, List<String> dirty, Optional<Identity> agentIdentity) {
        expectScope(scope, archiving, dirty);
        expect(site.commitIdentity(context)).andReturn(Optional.of(operator)).anyTimes();
        expect(site.agentIdentity(context)).andReturn(agentIdentity).anyTimes();
        expectTrailers();
    }

    void expectScope(ScopeType scope, boolean archiving, List<String> dirty) {
        expect(site.worktreeRoot(context)).andReturn(repo).anyTimes();
        expect(site.scopeType(context)).andReturn(scope).anyTimes();
        expect(site.archiving(context)).andReturn(archiving).anyTimes();
        expect(site.dirtyPaths(context)).andReturn(dirty).anyTimes();
        expect(site.pinnedFilesChanged(context)).andReturn(List.of()).anyTimes();
        expect(site.mainCheckoutBreaches(context)).andReturn(List.of()).anyTimes();
    }

    void expectTrailers() {
        expect(site.localTrailer(context)).andReturn(Optional.empty()).anyTimes();
        expect(site.machineTrailer(context)).andReturn(Optional.empty()).anyTimes();
    }

    PrimitiveResult<Outcome> run(GitStageAndCommitPrimitive primitive, Params parameters) {
        replay(site, context);
        return primitive.execute(context, parameters);
    }

    /**
     * A strict double the primitive must not call: a refusal before any git call leaves the repository untouched.
     */
    static GitOperations untouchedGit() {
        GitOperations untouched = createMock(GitOperations.class);
        replay(untouched);
        return untouched;
    }

    static String committed(PrimitiveResult<Outcome> result) {
        assertEquals(Outcome.COMMITTED, result.outcome(), "the primitive commits the staging set");
        return (String) result.updatedFacts().get(GitStageAndCommitPrimitive.COMMIT_FACT);
    }

    static void assertFailed(PrimitiveResult<Outcome> result, Reason reason, Map<String, Object> facts) {
        assertEquals(Outcome.FAILED, result.outcome(), "the commit is refused as failed");
        assertEquals(reason, result.reason(), "the reason names the refusal");
        assertEquals(facts, result.updatedFacts(), "the facts name what was refused");
    }

    CommitInfo head(String commit) {
        var history = git.log(repo, commit, 1);
        assertTrue(history.isOk(), "the history of the commit is read");
        return history.value().getFirst();
    }

    static long trailerLines(String message) {
        // the trailer key is the line the specification counts
        return message.lines().filter(line -> line.toLowerCase(Locale.ROOT).startsWith("co-authored-by:")).count();
    }

    boolean inCommit(String commit, String path) throws IOException {
        try (var jgit = Git.open(repo.toFile()); var walk = new RevWalk(jgit.getRepository())) {
            var tree = walk.parseCommit(jgit.getRepository().resolve(commit)).getTree();
            try (@Nullable TreeWalk found = TreeWalk.forPath(jgit.getRepository(), path, tree)) {
                return found != null;
            }
        }
    }

    Set<String> untracked() throws IOException, GitAPIException {
        try (var jgit = Git.open(repo.toFile())) {
            return jgit.status().call().getUntracked();
        }
    }

    String file() {
        return names.next() + ".txt";
    }

    void write(String path) throws IOException {
        var file = repo.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, Generators.letterStrings(1, 40).next(), StandardCharsets.UTF_8);
    }

    static Identity identity() {
        return new Identity(Generators.letterStrings(3, 12).next(), new EmailGenerator().next());
    }

    CommitInfo commitInfo() {
        var now = Instant.now();
        return CommitInfo.builder().id(names.next()).parents(List.of()).author(operator).authoredAt(now)
                .committer(agent).committedAt(now).message(messages.next()).build();
    }
}
