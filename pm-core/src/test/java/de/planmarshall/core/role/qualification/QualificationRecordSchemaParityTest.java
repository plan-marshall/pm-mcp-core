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
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.store.SchemaParity;

/**
 * The qualification record keeps parity with {@code qualification-record.schema.json}: the codec writes the file's
 * members in its order, every required member is written, the closed enumerations are the file's, and the bounds are
 * the record's own limits. Two drifts are pinned: the file declares an optional {@code judge_check} member the codec
 * neither writes nor reads (owner: the work package {@code qualification-evidence}), and the codec writes
 * {@code tokens_in}/{@code tokens_out} on a form and an item where the file declares only {@code total_tokens}
 * (owner: {@code PLAN-V2-56}, qualification-token-split).
 */
@DisplayName("Qualification record schema parity")
class QualificationRecordSchemaParityTest {

    private static final String SCHEMA = "de/planmarshall/core/role/qualification/qualification-record.schema.json";

    private static QualificationRecord sample() {
        return QualificationFixtures.qualification(Map.of(Topic.OWN, BigDecimal.valueOf(90)));
    }

    private static byte[] written() {
        return QualificationRecordCodec.write(sample());
    }

    private static Map<?, ?> document() {
        return SchemaParity.document(written());
    }

    @Test
    @DisplayName("the codec writes the file's members, with the optional member pinned")
    void membersAreTheFiles() {
        var schema = SchemaParity.load(SCHEMA);

        assertEquals(List.of("file-only:judge_check"),
                SchemaParity.memberDiffs(SchemaParity.members(written()), schema),
                "the member drift is pinned to qualification-evidence");
    }

    @Test
    @DisplayName("every required member is written")
    void requiredAreWritten() {
        var schema = SchemaParity.load(SCHEMA);

        assertEquals(Set.copyOf(SchemaParity.required(schema)), Set.copyOf(SchemaParity.members(written())),
                "the required members should be written");
    }

    @Test
    @DisplayName("the closed enumerations are the file's")
    void enumerationsAreTheFiles() {
        var schema = SchemaParity.load(SCHEMA);
        var configuration = SchemaParity.member(schema, "configuration");
        var item = SchemaParity.object(SchemaParity.member(schema, "items").get("items"));

        assertAll(
                () -> assertEquals(SchemaParity.enumOf(configuration, "class"),
                        SchemaParity.wireNames(RoleClass.values(), RoleClass::wireName),
                        "the role classes should equal the file's"),
                () -> assertEquals(SchemaParity.enumOf(item, "form"),
                        SchemaParity.wireNames(RoleForm.values(), RoleForm::wireName),
                        "the role forms should equal the file's"),
                () -> assertEquals(SchemaParity.enumOf(item, "checkout"),
                        SchemaParity.wireNames(Checkout.values(), Checkout::wireName),
                        "the checkouts should equal the file's"));
    }

    @Test
    @DisplayName("the codec's form members are pinned to PLAN-V2-56")
    void tokenSplitIsPinned() {
        var schema = SchemaParity.load(SCHEMA);
        var form = SchemaParity.object(SchemaParity.object(schema.get("$defs")).get("formResult"));
        var writtenForm = SchemaParity.object(SchemaParity.object(document().get("forms")).get("fresh"));

        assertEquals(List.of("record-only:tokens_in", "record-only:tokens_out"),
                SchemaParity.memberDiffs(SchemaParity.members(writtenForm), form),
                "the form token split is pinned to PLAN-V2-56");
    }

    @Test
    @DisplayName("every required name of the item is one of its properties")
    void itemRequiredAreProperties() {
        var schema = SchemaParity.load(SCHEMA);
        var item = SchemaParity.object(SchemaParity.member(schema, "items").get("items"));

        assertEquals(Set.copyOf(SchemaParity.properties(item)), Set.copyOf(SchemaParity.required(item)),
                "the item's required names should be properties");
    }

    @Test
    @DisplayName("the bounds are the record's own limits")
    void boundsAreTheLimits() {
        var schema = SchemaParity.load(SCHEMA);
        var ungraded = SchemaParity.member(schema, "ungraded");
        var base = sample();

        assertAll(
                () -> assertEquals(0, SchemaParity.bound(ungraded, "minimum"),
                        "the ungraded minimum should be zero"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new QualificationRecord(base.role(), base.qualificationDigest(), base.configuration(),
                                base.corpusRevision(), base.missedSources(), base.forms(), base.warmFreshGap(),
                                -1, base.gradedAt(), base.items()),
                        "the record should refuse a negative count"));
    }

    @Test
    @DisplayName("the comparison reports a member one side alone has")
    void negativeControls() {
        var schema = SchemaParity.load(SCHEMA);
        var members = SchemaParity.members(written());

        assertAll(
                () -> assertEquals(List.of("file-only:judge_check", "file-only:synthetic_member"),
                        SchemaParity.withFileOnly(members, schema, "synthetic_member"),
                        "a file-only member should be reported"),
                () -> assertEquals(List.of("file-only:judge_check", "record-only:role"),
                        SchemaParity.withRecordOnly(members, schema, "role"),
                        "a record-only member should be reported"));
    }
}
