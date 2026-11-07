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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.ChangeLedgerRecord.ChangeOrigin;
import de.planmarshall.core.store.ChangeLedgerRecord.Expect;
import de.planmarshall.core.store.ChangeLedgerRecord.ExpectResult;
import de.planmarshall.core.store.ChangeLedgerRecord.Kind;
import de.planmarshall.core.store.ChangeLedgerRecord.Outcome;

/**
 * The change-ledger record keeps parity with {@code store-schemas/04}: its required members are the file's, every
 * enumeration is the file's closed set, and the two negative controls prove the comparison reports a member only one
 * side has.
 * <p>
 * The record deviates from its file in exactly two members: the file declares {@code criterion} and {@code red_tests},
 * which no component of the record carries. The test pins that drift so neither side changes it silently; the landing
 * message reports it.
 */
@EnableGeneratorController
@DisplayName("Change ledger record schema parity")
class ChangeLedgerSchemaParityTest {

    private static final String RESOURCE = "de/planmarshall/core/store/change-ledger-record.schema.json";
    private static final String DIGEST = "a".repeat(64);
    private static final String COMMIT = "b".repeat(40);

    private static byte[] written() {
        return ChangeLedgerCodec.write(ChangeLedgerRecord.builder().kind(Kind.CHANGE).worktreeSha(DIGEST)
                .worktreeShaVersion(ChangeLedgerRecord.WORKTREE_SHA_VERSION).changedPaths(List.of("a.txt"))
                .commitSha(COMMIT).changeOrigin(ChangeOrigin.PLAN_CHANGE)
                .recordedAt(Instant.parse("2026-11-07T10:00:00Z")).build());
    }

    @Test
    @DisplayName("the record deviates from its file only in the absent criterion and red_tests")
    void recordMembers() {
        var schema = SchemaParity.load(RESOURCE);
        var diffs = new TreeSet<>(SchemaParity.memberDiffs(SchemaParity.members(written()), schema));

        assertEquals(new TreeSet<>(List.of("file-only:criterion", "file-only:red_tests")), diffs,
                "the record's recorded drift should not change silently");
    }

    @Test
    @DisplayName("the required members of the record are the file's required members")
    void recordRequiredMembers() {
        var schema = SchemaParity.load(RESOURCE);

        assertAll(
                () -> assertFalse(SchemaParity.required(schema).isEmpty(), "the schema declares required members"),
                () -> assertEquals(Set.copyOf(SchemaParity.required(schema)),
                        SchemaParity.requiredByCodec(written(), ChangeLedgerCodec::read),
                        "the required record members should match"));
    }

    @Test
    @DisplayName("the record refuses a duration below the file's minimum")
    void recordLimits() {
        var schema = SchemaParity.load(RESOURCE);
        var minimum = SchemaParity.bound(SchemaParity.member(schema, "duration_ms"), "minimum");

        assertAll(
                () -> assertEquals(0, minimum, "the file's duration minimum stays 0"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ChangeLedgerRecord.builder().kind(Kind.BUILD).worktreeSha(DIGEST)
                                .worktreeShaVersion(ChangeLedgerRecord.WORKTREE_SHA_VERSION).durationMs(-1L)
                                .recordedAt(Instant.parse("2026-11-07T10:00:00Z")).build(),
                        "the record should refuse a negative duration"));
    }

    @Test
    @DisplayName("the enumerations of the record are the file's closed sets")
    void recordEnumerations() {
        var schema = SchemaParity.load(RESOURCE);

        assertAll(
                () -> assertEquals(SchemaParity.enumOf(schema, "kind"),
                        SchemaParity.wireNames(Kind.values(), Kind::wireName),
                        "the kinds should equal the schema enum"),
                () -> assertEquals(SchemaParity.enumOf(schema, "outcome"),
                        SchemaParity.wireNames(Outcome.values(), Outcome::wireName),
                        "the outcomes should equal the schema enum"),
                () -> assertEquals(SchemaParity.enumOf(schema, "change_origin"),
                        SchemaParity.wireNames(ChangeOrigin.values(), ChangeOrigin::wireName),
                        "the change origins should equal the schema enum"),
                () -> assertEquals(SchemaParity.enumOf(schema, "expect"),
                        SchemaParity.wireNames(Expect.values(), Expect::wireName),
                        "the expectations should equal the schema enum"),
                () -> assertEquals(SchemaParity.enumOf(schema, "expect_result"),
                        SchemaParity.wireNames(ExpectResult.values(), ExpectResult::wireName),
                        "the expectation results should equal the schema enum"),
                () -> assertEquals(1,
                        ((Number) SchemaParity.member(schema, "worktree_sha_version").get("const")).intValue(),
                        "the schema's hash version stays 1"),
                () -> assertEquals(ChangeLedgerRecord.WORKTREE_SHA_VERSION, 1,
                        "the record's hash version stays 1"));
    }

    @Test
    @DisplayName("the comparison reports a member the file has and the record lacks")
    void fileOnlyNegativeControl() {
        var diffs = SchemaParity.withFileOnly(SchemaParity.members(written()), SchemaParity.load(RESOURCE),
                "synthetic_member");

        assertTrue(diffs.contains("file-only:synthetic_member"), "the extra file member should be reported");
    }

    @Test
    @DisplayName("the comparison reports a member the record has and the file lacks")
    void recordOnlyNegativeControl() {
        var diffs = SchemaParity.withRecordOnly(SchemaParity.members(written()), SchemaParity.load(RESOURCE), "kind");

        assertTrue(diffs.contains("record-only:kind"), "the missing file member should be reported");
    }
}
