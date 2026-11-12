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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.OutlineRecord.ChangeType;
import de.planmarshall.core.store.OutlineRecord.ExecutionMode;
import de.planmarshall.core.store.OutlineRecord.FileIntent;
import de.planmarshall.core.store.OutlineRecord.Profile;
import de.planmarshall.core.store.OutlineRecord.ScopeEstimate;
import de.planmarshall.core.store.OutlineStore.DeliveryStatus;
import de.planmarshall.core.store.OutlineStore.Revision;

/**
 * The outline store and the outline record keep parity with the schema files of {@code store-schemas/06}: their members,
 * the members of a deliverable and every enumeration are the files', every required member a record shares with its
 * file is carried, and the two
 * negative controls prove the comparison reports a member only one side has.
 */
@EnableGeneratorController
@SuppressWarnings("java:S5778")
// each assertAll entry builds its value under test in the assertion, by design
@DisplayName("Outline store and record schema parity")
class OutlineSchemaParityTest {

    private static final String STORE = "de/planmarshall/core/store/outline.schema.json";
    private static final String RECORD = "de/planmarshall/core/store/outline-record-v1.schema.json";

    private static byte[] written() {
        return OutlineCodec.write(OutlineStore.first(
                new Revision(1, OutlineFixtures.AT, "TASK-001", OutlineFixtures.valid())));
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
                        SchemaParity.requiredByCodec(document, OutlineCodec::read),
                        "the required store members should match"),
                () -> assertEquals(1,
                        ((Number) SchemaParity.member(schema, "format_version").get("const")).intValue(),
                        "the schema's format version stays 1"),
                () -> assertEquals(1, ((Number) SchemaParity.document(document).get("format_version")).intValue(),
                        "the codec's written format version stays 1"));
    }

    @Test
    @DisplayName("the members and required members of the record are the file's")
    void recordMembers() {
        var schema = SchemaParity.load(RECORD);
        var document = recordDocument();
        var members = SchemaParity.members(document);

        assertAll(
                () -> assertEquals(List.of(), SchemaParity.memberDiffs(members, schema),
                        "the record members should equal the schema"),
                () -> assertTrue(Set.copyOf(members).containsAll(SchemaParity.required(schema)),
                        "the record should carry every required member"));
    }

    @Test
    @DisplayName("the members and required members of a deliverable are the file's")
    void deliverableMembers() {
        var schema = SchemaParity.load(RECORD);
        var declaration = SchemaParity.object(SchemaParity.member(schema, "deliverables").get("items"));
        var written = SchemaParity.object(SchemaParity.array(recordDocument().get("deliverables")).getFirst());
        var members = SchemaParity.members(written);

        assertAll(
                () -> assertEquals(List.of(), SchemaParity.memberDiffs(members, declaration),
                        "the deliverable members should equal the schema"),
                () -> assertTrue(Set.copyOf(members).containsAll(SchemaParity.required(declaration)),
                        "the deliverable should carry every required member"));
    }

    @Test
    @DisplayName("the record refuses a summary below the file's minimum length")
    void recordLimits() {
        var schema = SchemaParity.load(RECORD);
        var minimum = SchemaParity.bound(SchemaParity.member(schema, "summary"), "minLength");
        var fixture = OutlineFixtures.valid();
        var tooShort = "x".repeat(minimum - 1);

        assertAll(
                () -> assertEquals(20, minimum, "the file's summary minimum stays 20"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new OutlineRecord(tooShort, fixture.overview(), fixture.scopeEstimate(),
                                fixture.deliverables()),
                        "the record should refuse a summary below its minimum"));
    }

    @Test
    @DisplayName("the enumerations of the record are the file's closed sets")
    void recordEnumerations() {
        var schema = SchemaParity.load(RECORD);
        var deliverable = SchemaParity.object(SchemaParity.member(schema, "deliverables").get("items"));
        var affectedFile = SchemaParity.object(SchemaParity.member(deliverable, "affected_files").get("items"));
        var status = SchemaParity.object(SchemaParity.member(SchemaParity.load(STORE), "deliverable_status")
                .get("additionalProperties"));

        assertAll(
                () -> assertEquals(SchemaParity.enumOf(schema, "scope_estimate"),
                        SchemaParity.wireNames(ScopeEstimate.values(), ScopeEstimate::wireName),
                        "the scope estimates should equal the schema enum"),
                () -> assertEquals(SchemaParity.enumOf(deliverable, "change_type"),
                        SchemaParity.wireNames(ChangeType.values(), ChangeType::wireName),
                        "the change types should equal the schema enum"),
                () -> assertEquals(SchemaParity.enumOf(deliverable, "execution_mode"),
                        SchemaParity.wireNames(ExecutionMode.values(), ExecutionMode::wireName),
                        "the execution modes should equal the schema enum"),
                () -> assertEquals(SchemaParity.values(SchemaParity.member(deliverable, "profiles").get("items")),
                        SchemaParity.wireNames(Profile.values(), Profile::wireName),
                        "the profiles should equal the schema enum"),
                () -> assertEquals(SchemaParity.enumOf(affectedFile, "intent"),
                        SchemaParity.wireNames(FileIntent.values(), FileIntent::wireName),
                        "the file intents should equal the schema enum"),
                () -> assertEquals(SchemaParity.values(status),
                        SchemaParity.wireNames(DeliveryStatus.values(), DeliveryStatus::wireName),
                        "the delivery statuses should equal the schema enum"));
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
        var diffs = SchemaParity.withRecordOnly(SchemaParity.members(recordDocument()), SchemaParity.load(RECORD),
                "summary");

        assertTrue(diffs.contains("record-only:summary"), "the missing file member should be reported");
    }
}
