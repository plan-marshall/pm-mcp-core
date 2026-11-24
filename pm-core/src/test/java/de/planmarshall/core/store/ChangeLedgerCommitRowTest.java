/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.ChangeLedgerRecord.ChangeOrigin;
import de.planmarshall.core.store.ChangeLedgerRecord.Kind;

/**
 * The {@code commit} row of the change ledger (Store Schemas § Change Ledger Record): it carries the commit, the paths
 * it carried and the digest of the tree it was made on, taken before the commit so that it is the digest that tree's
 * build rows carry. The {@code git.pre-commit-verify-freshness} verb reads no {@code commit} row; the row attributes
 * the commit to the build that licensed it. The digest shapes are the wire contract; the paths are generated.
 */
@EnableGeneratorController
@DisplayName("Change ledger commit row")
class ChangeLedgerCommitRowTest {

    private static final String TREE = "a".repeat(64);
    private static final String COMMIT = "b".repeat(40);
    private static final Instant RECORDED_AT = Instant.parse("2026-11-23T10:00:00Z");

    private static String path() {
        return Generators.letterStrings(1, 12).next().toLowerCase() + ".txt";
    }

    /** A commit row with no commit, the refusal the kind carries; one builder call for an `assertThrows` lambda. */
    private static ChangeLedgerRecord commitRowWithoutSha(String path) {
        return ChangeLedgerRecord.builder().kind(Kind.COMMIT).worktreeSha(TREE)
                .worktreeShaVersion(ChangeLedgerRecord.WORKTREE_SHA_VERSION).changedPaths(List.of(path))
                .recordedAt(RECORDED_AT).build();
    }

    @Test
    @DisplayName("a commit row round-trips with its commit, paths and origin")
    void commitRowRoundTrips() {
        var row = ChangeLedgerRecord.builder().kind(Kind.COMMIT).worktreeSha(TREE)
                .worktreeShaVersion(ChangeLedgerRecord.WORKTREE_SHA_VERSION).commitSha(COMMIT)
                .changedPaths(List.of(path(), path())).changeOrigin(ChangeOrigin.PLAN_CHANGE)
                .recordedAt(RECORDED_AT).build();

        var read = ChangeLedgerCodec.read(Path.of("change-ledger.jsonl"), ChangeLedgerCodec.write(row));

        assertAll(
                () -> assertEquals(Kind.COMMIT, read.kind(), "the commit kind should round-trip"),
                () -> assertEquals(row, read, "the commit row should round-trip unchanged"));
    }

    @Test
    @DisplayName("a commit row without its commit sha is refused")
    void commitRowNeedsCommitSha() {
        var changedPath = path();

        var refused = assertThrows(IllegalArgumentException.class,
                () -> commitRowWithoutSha(changedPath), "a commit row without a commit sha is refused");

        assertTrue(refused.getMessage().contains("commit_sha"), "the refusal should name the commit sha");
    }

    @Test
    @DisplayName("a build row may omit the commit sha")
    void buildRowMayOmitCommitSha() {
        var row = ChangeLedgerRecord.builder().kind(Kind.BUILD).worktreeSha(TREE)
                .worktreeShaVersion(ChangeLedgerRecord.WORKTREE_SHA_VERSION).recordedAt(RECORDED_AT).build();

        assertNull(row.commitSha(), "a build row carries no commit sha");
    }

    @Test
    @DisplayName("the commit kind names itself commit on the wire")
    void commitKindWireName() {
        assertAll(
                () -> assertEquals("commit", Kind.COMMIT.wireName(), "the commit kind writes commit"),
                () -> assertEquals(Optional.of(Kind.COMMIT), Kind.fromWireName("commit"),
                        "the wire name reads back to the kind"));
    }
}
