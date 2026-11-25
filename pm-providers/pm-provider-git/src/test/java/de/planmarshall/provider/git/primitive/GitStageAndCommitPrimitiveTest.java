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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.ScopeType;
import de.planmarshall.provider.git.primitive.GitStageAndCommitPrimitive.Outcome;
import de.planmarshall.provider.git.primitive.GitStageAndCommitPrimitive.Params;
import de.planmarshall.provider.git.primitive.GitStageAndCommitPrimitive.Reason;

/**
 * {@code git.stage-and-commit}: the staging set per scope, the footprint check before any git call, the narrowing of
 * the bound files and the clean-tree route.
 */
@EnableGeneratorController
@DisplayName("GitStageAndCommitPrimitive: the staging set")
class GitStageAndCommitPrimitiveTest extends GitStageAndCommitPrimitiveFixtures {

    @Test
    @DisplayName("a plan commits exactly the listed files and leaves an unrelated dirty file")
    void planCommitsListedFiles() throws Exception {
        var listed = file();
        var unrelated = file();
        write(listed);
        write(unrelated);
        stub(ScopeType.PLAN, false, List.of(listed, unrelated), Optional.of(agent));

        var result = run(new GitStageAndCommitPrimitive(git, site), new Params(messages.next(), List.of(listed)));

        var commit = committed(result);
        assertAll("plan commit",
                () -> assertTrue(inCommit(commit, listed), "the listed file is committed"),
                () -> assertFalse(inCommit(commit, unrelated), "the unrelated file is not committed"),
                () -> assertTrue(untracked().contains(unrelated), "the unrelated file stays unstaged"));
    }

    @Test
    @DisplayName("an epic stages the archived epics while it archives")
    void epicArchiving() throws Exception {
        var epicRecord = EPICS + file();
        var archived = ARCHIVED_EPICS + file();
        write(epicRecord);
        write(archived);
        stub(ScopeType.EPIC, true, List.of(epicRecord, archived), Optional.of(agent));

        var commit = committed(run(new GitStageAndCommitPrimitive(git, site), new Params(messages.next(), null)));

        assertAll("archiving epic commit",
                () -> assertTrue(inCommit(commit, epicRecord), "the epic record is committed"),
                () -> assertTrue(inCommit(commit, archived), "the archived record is committed"));
    }

    @Test
    @DisplayName("an epic that does not archive refuses an archived epic before any git call")
    void epicNotArchiving() throws Exception {
        var epicRecord = EPICS + file();
        var archived = ARCHIVED_EPICS + file();
        write(epicRecord);
        write(archived);
        stub(ScopeType.EPIC, false, List.of(epicRecord, archived), Optional.of(agent));

        var result = run(new GitStageAndCommitPrimitive(untouchedGit(), site), new Params(messages.next(), null));

        assertFailed(result, Reason.FOOTPRINT_VIOLATION,
                Map.of(GitStageAndCommitPrimitive.OFFENDING_FILES_FACT, List.of(archived),
                        GitStageAndCommitPrimitive.OFFENDING_COUNT_FACT, 1));
    }

    @Test
    @DisplayName("a path outside the worktree is a footprint violation before any staging")
    void pathOutsideTheWorktree() throws Exception {
        var inside = file();
        write(inside);
        // a parent-relative name is the contract's example of a path outside the worktree
        var outside = "../" + file();
        stub(ScopeType.PLAN, false, List.of(inside, outside), Optional.of(agent));

        var result = run(new GitStageAndCommitPrimitive(untouchedGit(), site), new Params(messages.next(), null));

        assertAll("footprint violation",
                () -> assertFailed(result, Reason.FOOTPRINT_VIOLATION,
                        Map.of(GitStageAndCommitPrimitive.OFFENDING_FILES_FACT, List.of(outside),
                                GitStageAndCommitPrimitive.OFFENDING_COUNT_FACT, 1)),
                () -> assertTrue(untracked().contains(inside), "nothing inside the worktree is staged"));
    }

    @Test
    @DisplayName("files that name nothing of the staging set are nothing to commit without a git call")
    void nothingToCommit() throws Exception {
        var dirty = file();
        write(dirty);
        stub(ScopeType.PLAN, false, List.of(dirty), Optional.of(agent));

        var result = run(new GitStageAndCommitPrimitive(untouchedGit(), site),
                new Params(messages.next(), List.of(file())));

        assertAll("nothing to commit",
                () -> assertEquals(Outcome.NOTHING_TO_COMMIT, result.outcome(), "an empty set commits nothing"),
                () -> assertNull(result.reason(), "nothing_to_commit carries no reason"));
    }

    @Test
    @DisplayName("a clean tree is nothing to commit before the message is read")
    void cleanTreeCommitsNothing() {
        // the clean-tree route: none_assert_clean resolves ready without a message, so none is bound here
        stub(ScopeType.PLAN, false, List.of(), Optional.of(agent));

        var result = run(new GitStageAndCommitPrimitive(untouchedGit(), site), new Params(null, null));

        assertAll("clean tree",
                () -> assertEquals(Outcome.NOTHING_TO_COMMIT, result.outcome(),
                        "A clean staging set commits nothing"),
                () -> assertNull(result.reason(), "nothing_to_commit carries no reason"),
                () -> assertTrue(result.updatedFacts().isEmpty(), "nothing_to_commit carries no facts"));
    }

    @Test
    @DisplayName("a non-empty staging set without a message fails as commit_failed")
    void missingMessageFails() throws Exception {
        var dirty = file();
        write(dirty);
        stub(ScopeType.PLAN, false, List.of(dirty), Optional.of(agent));

        var result = run(new GitStageAndCommitPrimitive(untouchedGit(), site), new Params(null, null));

        assertAll("missing message",
                () -> assertEquals(Outcome.FAILED, result.outcome(), "A missing message fails the commit"),
                () -> assertEquals(Reason.COMMIT_FAILED, result.reason(), "The reason is commit_failed"),
                () -> assertEquals("no commit message is resolved",
                        result.updatedFacts().get(GitStageAndCommitPrimitive.DETAIL_FACT),
                        "The detail names the missing message"));
    }

    @Test
    @DisplayName("a bound path outside the staging set is narrowed away, never refused")
    void boundPathOutsideTheSetIsDropped() throws Exception {
        var listed = file();
        var beside = file();
        write(listed);
        write(beside);
        stub(ScopeType.PLAN, false, List.of(listed, beside), Optional.of(agent));
        // the typo names nothing of the checkout: the bind would refuse it, this primitive only narrows
        var typo = file();

        var result = run(new GitStageAndCommitPrimitive(git, site), new Params(messages.next(),
                List.of(listed, typo)));

        var commit = committed(result);
        assertAll("narrowed commit",
                () -> assertTrue(inCommit(commit, listed), "the bound path inside the set is committed"),
                () -> assertFalse(inCommit(commit, beside), "a path outside files is not committed"));
    }
}
