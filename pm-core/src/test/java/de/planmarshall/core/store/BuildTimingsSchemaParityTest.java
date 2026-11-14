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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The build timings store keeps parity with {@code build-timings.schema.json}: the codec writes the file's members in
 * its order, every required member is written, the members of a key's timings and of a baseline are the file's, and the
 * bounds are the types' own limits.
 */
@DisplayName("Build timings store schema parity")
class BuildTimingsSchemaParityTest {

    private static final String SCHEMA = "de/planmarshall/core/store/build-timings.schema.json";

    private static byte[] written() {
        var keys = new LinkedHashMap<String, BuildTimings.KeyTimings>();
        keys.put("maven:verify", BuildTimings.KeyTimings.empty());
        return BuildTimingsCodec.write(new BuildTimings(keys));
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
    @DisplayName("the members of a key's timings and of a baseline are the file's")
    void nestedMembersAreTheFiles() {
        var schema = SchemaParity.load(SCHEMA);
        var defs = SchemaParity.object(schema.get("$defs"));
        var keyTimings = SchemaParity.object(defs.get("keyTimings"));
        var baseline = SchemaParity.object(defs.get("baseline"));
        var writtenKey = SchemaParity.object(SchemaParity.object(document().get("keys")).get("maven:verify"));
        var writtenBaseline = SchemaParity.object(writtenKey.get("clean"));

        assertAll(
                () -> assertEquals(List.of(),
                        SchemaParity.memberDiffs(SchemaParity.members(writtenKey), keyTimings),
                        "the key timings members should equal the schema's"),
                () -> assertEquals(List.of(),
                        SchemaParity.memberDiffs(SchemaParity.members(writtenBaseline), baseline),
                        "the baseline members should equal the schema's"));
    }

    @Test
    @DisplayName("the bounds are the types' own limits")
    void boundsAreTheLimits() {
        var schema = SchemaParity.load(SCHEMA);
        var defs = SchemaParity.object(schema.get("$defs"));
        var baseline = SchemaParity.object(defs.get("baseline"));
        var keyTimings = SchemaParity.object(defs.get("keyTimings"));
        var empty = BuildTimings.Baseline.empty();

        assertAll(
                () -> assertEquals(1, SchemaParity.bound(SchemaParity.member(baseline, "learned_seconds"), "minimum"),
                        "the learned minimum should be one"),
                () -> assertEquals(0, SchemaParity.bound(SchemaParity.member(baseline, "observations"), "minimum"),
                        "the observation minimum should be zero"),
                () -> assertEquals(1,
                        SchemaParity.bound(SchemaParity.member(keyTimings, "escalation_seconds"), "minimum"),
                        "the escalation minimum should be one"),
                () -> assertThrows(IllegalArgumentException.class, () -> new BuildTimings.Baseline(0L, 1),
                        "a baseline below the learned minimum is refused"),
                () -> assertThrows(IllegalArgumentException.class, () -> new BuildTimings.Baseline(null, -1),
                        "a negative observation count is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new BuildTimings.KeyTimings(empty, empty, empty, 0L, null),
                        "an escalation below the minimum is refused"));
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
                () -> assertEquals(List.of("record-only:keys"),
                        SchemaParity.withRecordOnly(members, schema, "keys"),
                        "a record-only member should be reported"));
    }
}
