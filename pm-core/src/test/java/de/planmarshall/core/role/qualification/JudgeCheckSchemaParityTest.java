/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.qualification;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.role.qualification.JudgeCheck.Label;
import de.planmarshall.core.store.SchemaParity;

/**
 * The judge check record keeps parity with {@code judge-check-record.schema.json}: the codec writes the file's members
 * in its order, every required member is written, the label enumeration is the file's, and the bounds are the record's
 * own limits. One drift is pinned: the codec writes {@code tokens_in} and {@code tokens_out} on an item, which the file
 * does not declare; {@code PLAN-V2-56} (qualification-token-split) removes them from the codec.
 */
@DisplayName("Judge check record schema parity")
class JudgeCheckSchemaParityTest {

    private static final String SCHEMA = "de/planmarshall/core/role/qualification/judge-check-record.schema.json";
    private static final String DIGEST = "a".repeat(64);
    private static final Instant CHECKED_AT = Instant.parse("2026-11-14T10:00:00Z");

    private static JudgeCheck.Record sample() {
        return new JudgeCheck.Record(DIGEST, "opencode", "model-1", "corpus-1", BigDecimal.valueOf(90),
                BigDecimal.valueOf(80), BigDecimal.valueOf(50), 0, CHECKED_AT, List.of(new JudgeCheck.ItemScore(
                        "item-1", Label.RIGHT, Grade.scored(BigDecimal.ONE), Grade.scored(BigDecimal.ZERO),
                        TokenTotal.measured(10), 5L)));
    }

    private static byte[] written() {
        return JudgeCheckCodec.write(sample());
    }

    private static Map<?, ?> document() {
        return SchemaParity.document(written());
    }

    @Test
    @DisplayName("the codec writes the file's members in its order")
    void membersAreTheFiles() {
        var schema = SchemaParity.load(SCHEMA);

        assertEquals(List.of(), SchemaParity.memberDiffs(SchemaParity.members(written()), schema),
                "the members should equal the schema's");
    }

    @Test
    @DisplayName("every required member is written")
    void requiredAreWritten() {
        var schema = SchemaParity.load(SCHEMA);

        assertEquals(Set.copyOf(SchemaParity.required(schema)), Set.copyOf(SchemaParity.members(written())),
                "the required members should be written");
    }

    @Test
    @DisplayName("the label enumeration is the file's")
    void labelsAreTheFiles() {
        var schema = SchemaParity.load(SCHEMA);
        var items = SchemaParity.object(SchemaParity.member(schema, "items").get("items"));

        assertEquals(SchemaParity.enumOf(items, "label"),
                SchemaParity.wireNames(Label.values(), Label::wireName), "the labels should equal the file's");
    }

    @Test
    @DisplayName("the codec's item members are the file's, with two drifts pinned")
    void itemMembersArePinned() {
        var schema = SchemaParity.load(SCHEMA);
        var items = SchemaParity.object(SchemaParity.member(schema, "items").get("items"));
        var writtenItem = SchemaParity.object(SchemaParity.array(document().get("items")).getFirst());

        assertEquals(List.of("record-only:tokens_in", "record-only:tokens_out"),
                SchemaParity.memberDiffs(SchemaParity.members(writtenItem), items),
                "the item token split is pinned to PLAN-V2-56");
    }

    @Test
    @DisplayName("the bounds are the record's own limits")
    void boundsAreTheLimits() {
        var schema = SchemaParity.load(SCHEMA);
        var accuracy = SchemaParity.member(schema, "oracle_accuracy");
        var items = sample().items();
        var aboveMaximum = BigDecimal.valueOf(101);
        var scoredOne = Grade.scored(BigDecimal.ONE);
        var scoredZero = Grade.scored(BigDecimal.ZERO);
        var measured = TokenTotal.measured(1L);

        assertAll(
                () -> assertEquals(0, SchemaParity.bound(accuracy, "minimum"),
                        "the accuracy minimum should be zero"),
                () -> assertEquals(100, SchemaParity.bound(accuracy, "maximum"),
                        "the accuracy maximum should be 100"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new JudgeCheck.Record(DIGEST, "opencode", "model-1", "corpus-1",
                                aboveMaximum, BigDecimal.ZERO, BigDecimal.ZERO, 0, CHECKED_AT,
                                items),
                        "the record should refuse above the maximum"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new JudgeCheck.Record(DIGEST, "opencode", "model-1", "corpus-1", BigDecimal.ZERO,
                                BigDecimal.ZERO, BigDecimal.ZERO, -1, CHECKED_AT, items),
                        "the record should refuse a negative count"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new JudgeCheck.ItemScore("item-1", Label.RIGHT, scoredOne,
                                scoredZero, measured, -1L),
                        "the item should refuse a negative duration"));
    }

    @Test
    @DisplayName("the comparison reports a member one side alone has")
    void negativeControls() {
        var schema = SchemaParity.load(SCHEMA);
        var members = SchemaParity.members(written());

        assertAll(
                () -> assertEquals(List.of("file-only:synthetic_member"),
                        SchemaParity.withFileOnly(members, schema, "synthetic_member"),
                        "a file-only member should be reported"),
                () -> assertEquals(List.of("record-only:harness"),
                        SchemaParity.withRecordOnly(members, schema, "harness"),
                        "a record-only member should be reported"));
    }
}
