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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.IntentRecord.AssumptionStatus;
import de.planmarshall.core.store.IntentRecord.CriterionKind;
import de.planmarshall.core.store.IntentRecord.Decider;
import de.planmarshall.core.store.IntentStore.Revision;

/**
 * The intent store and the intent record keep parity with the schema files of {@code store-schemas/06}: the store's
 * members and required members are the file's, the record's members are the file's, and every enumeration of the
 * record equals the file's.
 * <p>
 * The intent record deviates from its file in exactly two members and one enumeration value: the codec writes
 * {@code decisions} where the schema declares {@code choices}, it carries no {@code repairs} member, and its decider
 * enumeration has {@code model} where the file has {@code role}. The test pins that drift so neither side changes it
 * silently; the plan that owns the correction fixes the codec, and the landing message reports the drift.
 */
@EnableGeneratorController
@SuppressWarnings("java:S5778")
// each assertAll entry builds its value under test in the assertion, by design
@DisplayName("Intent store and record schema parity")
class IntentSchemaParityTest {

    private static final String STORE = "de/planmarshall/core/store/intent.schema.json";
    private static final String RECORD = "de/planmarshall/core/store/intent-record-v1.schema.json";

    private static byte[] written() {
        return IntentCodec.write(IntentStore.first(
                new Revision(1, IntentFixtures.AT, "TASK-001", IntentFixtures.valid())));
    }

    private static Map<?, ?> recordDocument() {
        var store = SchemaParity.document(written());
        return SchemaParity.object(SchemaParity.object(store.get("current")).get("record"));
    }

    @Test
    @DisplayName("the members and required members of the store are the file's")
    void storeMembers() {
        var schema = SchemaParity.load(STORE);
        var document = written();

        assertAll(
                () -> assertFalse(SchemaParity.properties(schema).isEmpty(), "the schema declares members"),
                () -> assertEquals(List.of(), SchemaParity.memberDiffs(SchemaParity.members(document), schema),
                        "the store members should equal the schema"),
                () -> assertEquals(Set.copyOf(SchemaParity.required(schema)),
                        SchemaParity.requiredByCodec(document, IntentCodec::read),
                        "the required store members should match"),
                () -> assertEquals(1,
                        ((Number) SchemaParity.member(schema, "format_version").get("const")).intValue(),
                        "the schema's format version stays 1"),
                () -> assertEquals(1, IntentStore.FORMAT_VERSION, "the codec's format version stays 1"));
    }

    @Test
    @DisplayName("the comparison reports a member the file has and the record lacks")
    void fileOnlyNegativeControl() {
        var diffs = SchemaParity.withFileOnly(SchemaParity.members(written()), SchemaParity.load(STORE),
                "synthetic_member");

        assertEquals(List.of("file-only:synthetic_member"), diffs, "the extra file member should be reported");
    }

    @Test
    @DisplayName("the comparison reports a member the record has and the file lacks")
    void recordOnlyNegativeControl() {
        var diffs = SchemaParity.withRecordOnly(SchemaParity.members(written()), SchemaParity.load(STORE), "history");

        assertEquals(List.of("record-only:history"), diffs, "the missing file member should be reported");
    }

    @Test
    @DisplayName("the record deviates from its file only in choices-to-decisions and the absent repairs")
    void recordMembers() {
        var schema = SchemaParity.load(RECORD);
        var diffs = new TreeSet<>(SchemaParity.memberDiffs(SchemaParity.members(recordDocument()), schema));

        assertEquals(new TreeSet<>(List.of("file-only:choices", "file-only:repairs", "record-only:decisions")), diffs,
                "the record's recorded drift should not change silently");
    }

    @Test
    @DisplayName("the record carries every required member its file and the codec share")
    void recordRequiredMembers() {
        var schema = SchemaParity.load(RECORD);
        var present = new TreeSet<>(SchemaParity.members(recordDocument()));
        var required = new TreeSet<>(SchemaParity.required(schema));
        required.removeAll(Set.of("choices", "repairs"));

        assertTrue(present.containsAll(required), "the record should carry its shared required members");
    }

    @Test
    @DisplayName("the record refuses a why below the file's minimum length")
    void recordLimits() {
        var schema = SchemaParity.load(RECORD);
        var minimum = SchemaParity.bound(SchemaParity.member(schema, "why"), "minLength");
        var fixture = IntentFixtures.valid();
        var tooShort = "x".repeat(minimum - 1);

        assertAll(
                () -> assertEquals(20, minimum, "the file's why minimum stays 20"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new IntentRecord(tooShort, fixture.goal(), fixture.criteria(),
                                fixture.currentBehaviour(), fixture.nonGoals(), fixture.constraints(),
                                fixture.decisions(), fixture.assumptions(), fixture.openQuestions(),
                                fixture.deferred(), fixture.sourceClaims()),
                        "the record should refuse a why below its minimum"));
    }

    @Test
    @DisplayName("the record's enumerations are the file's closed sets, except the recorded decider drift")
    void recordEnumerations() {
        var schema = SchemaParity.load(RECORD);
        var criteria = SchemaParity.object(SchemaParity.member(schema, "criteria").get("items"));
        var choices = SchemaParity.object(SchemaParity.member(schema, "choices").get("items"));
        var assumptions = SchemaParity.object(SchemaParity.member(schema, "assumptions").get("items"));
        var fileDeciders = new TreeSet<>(SchemaParity.enumOf(choices, "decider"));
        var recordDeciders = new TreeSet<>(SchemaParity.wireNames(Decider.values(), Decider::wireName));
        var fileOnly = new TreeSet<>(fileDeciders);
        fileOnly.removeAll(recordDeciders);
        var recordOnly = new TreeSet<>(recordDeciders);
        recordOnly.removeAll(fileDeciders);

        assertAll(
                () -> assertEquals(SchemaParity.enumOf(criteria, "kind"),
                        SchemaParity.wireNames(CriterionKind.values(), CriterionKind::wireName),
                        "the criterion kinds should equal the schema enum"),
                () -> assertEquals(Set.of("role"), fileOnly, "the file's decider the record does not carry"),
                () -> assertEquals(Set.of("model"), recordOnly, "the record's decider the file does not carry"),
                () -> assertEquals(SchemaParity.enumOf(assumptions, "status"),
                        SchemaParity.wireNames(AssumptionStatus.values(), AssumptionStatus::wireName),
                        "the assumption statuses should equal the schema enum"));
    }

    @Test
    @DisplayName("the record comparison reports a member the file has and the record lacks")
    void recordFileOnlyNegativeControl() {
        var diffs = SchemaParity.withFileOnly(SchemaParity.members(recordDocument()), SchemaParity.load(RECORD),
                "synthetic_member");

        assertTrue(diffs.contains("file-only:synthetic_member"), "the extra file member should be reported");
    }

    @Test
    @DisplayName("the record comparison reports a member the record has and the file lacks")
    void recordRecordOnlyNegativeControl() {
        var diffs = SchemaParity.withRecordOnly(SchemaParity.members(recordDocument()), SchemaParity.load(RECORD), "why");

        assertTrue(diffs.contains("record-only:why"), "the missing file member should be reported");
    }
}
