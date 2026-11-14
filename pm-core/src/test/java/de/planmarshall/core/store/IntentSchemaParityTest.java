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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.SchemaResource;
import de.planmarshall.core.store.IntentRecord.AdrRef;
import de.planmarshall.core.store.IntentRecord.AdrRelation;
import de.planmarshall.core.store.IntentRecord.AssumptionStatus;
import de.planmarshall.core.store.IntentRecord.ConstraintSource;
import de.planmarshall.core.store.IntentRecord.CriterionKind;
import de.planmarshall.core.store.IntentRecord.Decider;
import de.planmarshall.core.store.IntentRecord.Decision;
import de.planmarshall.core.store.IntentRecord.VerifiedBy;
import de.planmarshall.core.store.IntentStore.Revision;

/**
 * The intent store and the intent record keep parity with the schema files of {@code store-schemas/06}: the store's
 * members and required members are the file's, the record's top-level members and the members of its nested items
 * ({@code criteria[]}, {@code choices[]} and its {@code adr}) are the file's, every enumeration of the record equals
 * the file's, and every record the codec writes validates against the file.
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

    private static byte[] writtenWithAdr() {
        var choice = new Decision("DC-1", Generators.letterStrings(10, 60).next(),
                List.of(Generators.letterStrings(2, 20).next()), Decider.OPERATOR, "q-1", null,
                new AdrRef("core-store-1", AdrRelation.FOLLOWS));
        return IntentCodec.write(IntentStore.first(
                new Revision(1, IntentFixtures.AT, "TASK-001", IntentFixtures.withDecisions(List.of(choice)))));
    }

    private static Map<?, ?> recordDocument(byte[] document) {
        var store = SchemaParity.document(document);
        return SchemaParity.object(SchemaParity.object(store.get("current")).get("record"));
    }

    private static Map<?, ?> recordDocument() {
        return recordDocument(written());
    }

    private static Map<?, ?> firstItem(Map<?, ?> record, String member) {
        return SchemaParity.object(SchemaParity.array(record.get(member)).getFirst());
    }

    private static Map<?, ?> itemSchema(Map<?, ?> schema, String member) {
        return SchemaParity.object(SchemaParity.member(schema, member).get("items"));
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
                () -> assertEquals(1, ((Number) SchemaParity.document(document).get("format_version")).intValue(),
                        "the codec's written format version stays 1"));
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
    @DisplayName("the store's record reference names the record file's id")
    void recordReference() {
        var store = SchemaParity.load(STORE);
        var revision = SchemaParity.object(SchemaParity.object(store.get("$defs")).get("revision"));
        var recordMember = SchemaParity.object(SchemaParity.object(revision.get("properties")).get("record"));
        var recordId = SchemaParity.load(RECORD).get("$id");

        assertAll(
                () -> assertInstanceOf(String.class, recordId, "the record file should carry its id"),
                () -> assertEquals(recordId, recordMember.get("$ref"),
                        "the store should reference the record file"));
    }

    @Test
    @DisplayName("the record's members are the file's")
    void recordMembers() {
        var schema = SchemaParity.load(RECORD);
        var diffs = SchemaParity.memberDiffs(SchemaParity.members(recordDocument()), schema);

        assertEquals(List.of(), diffs, "the record members should equal the schema");
    }

    @Test
    @DisplayName("the record carries every required member its file declares")
    void recordRequiredMembers() {
        var schema = SchemaParity.load(RECORD);
        var present = new TreeSet<>(SchemaParity.members(recordDocument()));
        var required = new TreeSet<>(SchemaParity.required(schema));

        assertEquals(required, present, "the record carries every required member its file declares");
    }

    @Test
    @DisplayName("the members of the record's nested items are the file's")
    void recordItemMembers() {
        var schema = SchemaParity.load(RECORD);
        var record = recordDocument(writtenWithAdr());
        var criterion = firstItem(record, "criteria");
        var choice = firstItem(record, "choices");
        var adr = SchemaParity.object(choice.get("adr"));
        var choicesItem = itemSchema(schema, "choices");
        var adrSchema = SchemaParity.object(SchemaParity.object(choicesItem.get("properties")).get("adr"));

        assertAll(
                () -> assertEquals(List.of(),
                        SchemaParity.memberDiffs(SchemaParity.members(criterion), itemSchema(schema, "criteria")),
                        "the criterion items should be the file's members"),
                () -> assertEquals(List.of(),
                        SchemaParity.memberDiffs(SchemaParity.members(choice), choicesItem),
                        "the choice items should be the file's members"),
                () -> assertEquals(List.of(),
                        SchemaParity.memberDiffs(SchemaParity.members(adr), adrSchema),
                        "the adr members should be the file's members"));
    }

    @Test
    @DisplayName("the written record validates against its file")
    void writtenRecordIsValid() {
        var validator = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(SchemaResource.text(RECORD));
        var record = SchemaParity.text(recordDocument(writtenWithAdr()));

        assertEquals(Set.of(), validator.validate(record, InputFormat.JSON),
                "the written record should validate against its file");
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
                                fixture.currentBehaviour(), fixture.repairs(), fixture.nonGoals(),
                                fixture.constraints(), fixture.choices(), fixture.assumptions(),
                                fixture.openQuestions(), fixture.deferred(), fixture.sourceClaims()),
                        "the record should refuse a why below its minimum"));
    }

    @Test
    @DisplayName("the record's enumerations are the file's closed sets")
    void recordEnumerations() {
        var schema = SchemaParity.load(RECORD);
        var criteria = itemSchema(schema, "criteria");
        var choices = itemSchema(schema, "choices");
        var adr = SchemaParity.object(SchemaParity.object(choices.get("properties")).get("adr"));
        var constraints = itemSchema(schema, "constraints");
        var assumptions = itemSchema(schema, "assumptions");

        assertAll(
                () -> assertEquals(SchemaParity.enumOf(criteria, "kind"),
                        SchemaParity.wireNames(CriterionKind.values(), CriterionKind::wireName),
                        "the criterion kinds should equal the schema enum"),
                () -> assertEquals(SchemaParity.enumOf(criteria, "verified_by"),
                        SchemaParity.wireNames(VerifiedBy.values(), VerifiedBy::wireName),
                        "the verified_by values should equal the schema"),
                () -> assertEquals(SchemaParity.enumOf(choices, "decider"),
                        SchemaParity.wireNames(Decider.values(), Decider::wireName),
                        "the decider kinds should equal the schema enum"),
                () -> assertEquals(SchemaParity.enumOf(adr, "relation"),
                        SchemaParity.wireNames(AdrRelation.values(), AdrRelation::wireName),
                        "the adr relations should equal the schema enum"),
                () -> assertEquals(SchemaParity.enumOf(constraints, "source"),
                        SchemaParity.wireNames(ConstraintSource.values(), ConstraintSource::wireName),
                        "the constraint sources should equal the schema"),
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
