/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.profile;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.SchemaResource;

/**
 * {@link ProfileValidator} keeps parity with the schema of Harness Profiles § Normative Harness Profile JSON Schema
 * where the two are built: the document the validator accepts is the file's required member set, the schema version is
 * the validator's, and the validator's usage figures are the file's minus the two members it does not implement. The
 * file is {@code harness-profile-v1.schema.json} in {@code pm-core/src/main/resources/de/planmarshall/core/profile/},
 * read through {@link SchemaResource}. Three deviations are pinned, each with its owner: the id member
 * ({@code harness_id} in the file, {@code client_id} in the validator, owned by the staged harness-rename packages);
 * {@code headless.inline_settings}, {@code headless.loaded} and {@code headless.readable_paths} (owned by the
 * profile-verification and job-confinement packages); and {@code headless.usage.model} (owned by PLAN-V2-135) with
 * {@code headless.usage.accumulation}. The comparison is structural: the file's relative {@code $ref} to
 * {@code endpoint-profile.json} is not resolved here. The member names are the wire format; they are literals here for
 * that reason.
 */
@DisplayName("Harness profile schema parity")
class HarnessProfileSchemaParityTest {

    private static final String SCHEMA = "de/planmarshall/core/profile/harness-profile-v1.schema.json";
    private static final String ID_MEMBER = "harness_id";
    private static final String READER_ID_MEMBER = "client_id";
    private static final Set<String> UNIMPLEMENTED_HEADLESS = Set.of("inline_settings", "loaded", "readable_paths");
    private static final Set<String> UNIMPLEMENTED_USAGE = Set.of("model", "accumulation");

    @Test
    @DisplayName("the file's required members are the document the validator accepts")
    void membersAreTheValidatedDocument() {
        var schema = SchemaResource.load(SCHEMA);
        var required = names(SchemaResource.array(schema.get("required")));
        var expected = required.stream().map(member -> ID_MEMBER.equals(member) ? READER_ID_MEMBER : member).toList();
        var document = document();

        assertAll(
                () -> assertEquals(Set.copyOf(expected), Set.copyOf(names(document.keySet())),
                        "The document's members are the file's, with the reader's id name"),
                () -> assertDoesNotThrow(() -> validate(),
                        "The validator accepts its own document"));
    }

    @Test
    @DisplayName("the id member is pinned: the file names the harness, the validator the client")
    void idMemberDrift() {
        var properties = SchemaResource.object(SchemaResource.load(SCHEMA).get("properties"));

        assertAll(
                () -> assertTrue(properties.containsKey(ID_MEMBER), "The file names the id as the harness does"),
                () -> assertFalse(properties.containsKey(READER_ID_MEMBER), "The file does not name the reader's id"));
    }

    @Test
    @DisplayName("the schema version is the validator's")
    void schemaVersion() {
        var properties = SchemaResource.object(SchemaResource.load(SCHEMA).get("properties"));

        assertEquals(ProfileValidator.SCHEMA_VERSION,
                ((Number) SchemaResource.object(properties.get("schema_version")).get("const")).intValue(),
                "The schema version is the validator's");
    }

    @Test
    @DisplayName("the headless members the validator does not implement are pinned")
    void headlessDrift() {
        var headless = SchemaResource.object(SchemaResource.object(SchemaResource.load(SCHEMA).get("properties"))
                .get("headless"));
        var declared = SchemaResource.object(headless.get("properties"));
        var fixture = SchemaResource.object(document().get("headless"));
        var missing = new TreeSet<>(names(SchemaResource.array(headless.get("required"))));
        missing.removeAll(names(fixture.keySet()));
        var undeclared = new TreeSet<>(names(fixture.keySet()));
        undeclared.removeAll(names(declared.keySet()));

        assertAll(
                () -> assertEquals(UNIMPLEMENTED_HEADLESS, Set.copyOf(missing),
                        "The unimplemented headless members are the pinned three"),
                () -> assertEquals(Set.of(), undeclared, "Every fixture member is declared in the file"));
    }

    @Test
    @DisplayName("the usage figures the validator implements are the file's minus the pinned two")
    void usageDrift() {
        var headless = SchemaResource.object(SchemaResource.object(SchemaResource.load(SCHEMA).get("properties"))
                .get("headless"));
        var usage = SchemaResource.object(SchemaResource.object(headless.get("properties")).get("usage"));
        var required = new TreeSet<>(names(SchemaResource.array(usage.get("required"))));
        required.removeAll(ProfileStages.USAGE_FIGURES);
        var fixtureUsage = SchemaResource.object(SchemaResource.object(document().get("headless")).get("usage"));

        assertAll(
                () -> assertEquals(Set.copyOf(ProfileStages.USAGE_FIGURES), Set.copyOf(names(fixtureUsage.keySet())),
                        "The fixture names the reader's figures"),
                () -> assertEquals(UNIMPLEMENTED_USAGE, Set.copyOf(required),
                        "The unimplemented usage members are the pinned two"));
    }

    private static Map<?, ?> document() {
        return SchemaResource.object(SchemaResource.parse(new String(ProfileFixtures.validBytes(), UTF_8)));
    }

    private static void validate() {
        ProfileValidator.validate(ProfileFixtures.validBytes(), "profiles/acme-cli.json", ProfileRules.none());
    }

    private static List<String> names(Iterable<?> values) {
        var names = new ArrayList<String>();
        values.forEach(value -> names.add(String.valueOf(value)));
        return names;
    }
}
