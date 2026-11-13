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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.SchemaResource;

/**
 * {@link EndpointProfile} and its reader {@link EndpointProfile#of(JsonNode)} keep parity with the schema of Harness
 * Profiles § Endpoint Profile Schema: the {@code endpoint}, {@code identity} and {@code weights_probe} blocks are the
 * projection's records, member for member, and the two constants are the reader's. The file is
 * {@code endpoint-profile.schema.json} in {@code pm-core/src/main/resources/de/planmarshall/core/profile/}, read through
 * {@link SchemaResource}. The file requires {@code harness_id} while the validator reads {@code client_id}; the drift is
 * pinned here and owned by the staged harness-rename packages (the profiles follow the file's name once that package
 * lands). The {@code update_evidence} and {@code model_tiers} blocks carry only a cross-reference in the file, so this
 * test does not compare them. The member names are the wire format; they are literals here for that reason.
 */
@DisplayName("Endpoint profile schema parity")
class EndpointProfileSchemaParityTest {

    private static final String SCHEMA = "de/planmarshall/core/profile/endpoint-profile.schema.json";
    private static final String ID_MEMBER = "harness_id";
    private static final String READER_ID_MEMBER = "client_id";

    @Test
    @DisplayName("the endpoint block is the Endpoint record, member for member")
    void endpointBlock() {
        var endpoint = block("endpoint");

        assertAll(
                () -> assertEquals(components(EndpointProfile.Endpoint.class),
                        names(SchemaResource.object(endpoint.get("properties")).keySet()),
                        "The endpoint members are the record's in order"),
                () -> assertEquals(components(EndpointProfile.Endpoint.class),
                        names(SchemaResource.array(endpoint.get("required"))),
                        "Every endpoint member is required"),
                () -> assertEquals(Boolean.FALSE, endpoint.get("additionalProperties"),
                        "The endpoint block closes its member set"));
    }

    @Test
    @DisplayName("the identity block is the Identity record, member for member")
    void identityBlock() {
        var identity = block("identity");

        assertAll(
                () -> assertEquals(components(EndpointProfile.Identity.class),
                        names(SchemaResource.object(identity.get("properties")).keySet()),
                        "The identity members are the record's in order"),
                () -> assertEquals(components(EndpointProfile.Identity.class),
                        names(SchemaResource.array(identity.get("required"))),
                        "Every identity member is required"));
    }

    @Test
    @DisplayName("the weights probe definition is the WeightsProbe record, member for member")
    void weightsProbeBlock() {
        var weights = SchemaResource.object(SchemaResource.object(SchemaResource.load(SCHEMA).get("$defs"))
                .get("weights_probe"));

        assertAll(
                () -> assertEquals(components(EndpointProfile.WeightsProbe.class),
                        names(SchemaResource.object(weights.get("properties")).keySet()),
                        "The weights-probe members are the record's in order"),
                () -> assertEquals(components(EndpointProfile.WeightsProbe.class),
                        names(SchemaResource.array(weights.get("required"))),
                        "Every weights-probe member is required"));
    }

    @Test
    @DisplayName("the profile kind, the schema version and the protocol are the reader's constants")
    void constants() {
        var properties = SchemaResource.object(SchemaResource.load(SCHEMA).get("properties"));
        var endpoint = SchemaResource.object(properties.get("endpoint"));
        var protocol = SchemaResource.object(SchemaResource.object(endpoint.get("properties")).get("protocol"));

        assertAll(
                () -> assertEquals(ProfileValidator.PROFILE_KIND_ENDPOINT,
                        SchemaResource.object(properties.get("profile_kind")).get("const"),
                        "The profile kind is the reader's"),
                () -> assertEquals(ProfileValidator.SCHEMA_VERSION,
                        ((Number) SchemaResource.object(properties.get("schema_version")).get("const")).intValue(),
                        "The schema version is the reader's"),
                () -> assertEquals(Set.of("openai_chat"),
                        Set.copyOf(names(SchemaResource.array(protocol.get("enum")))),
                        "The one protocol is the reader's (no type constant exists)"));
    }

    @Test
    @DisplayName("the id member is the one pinned difference between the file and the reader")
    void idMemberDrift() {
        var properties = names(SchemaResource.object(SchemaResource.load(SCHEMA).get("properties")).keySet());
        var projected = components(EndpointProfile.class);
        var expected = new ArrayList<>(projected);
        expected.remove(READER_ID_MEMBER);

        assertAll(
                () -> assertTrue(properties.contains(ID_MEMBER), "The file names the id as the harness does"),
                () -> assertFalse(properties.contains(READER_ID_MEMBER), "The file does not name the reader's id"),
                () -> assertTrue(properties.containsAll(expected), "The projection's members are the file's"),
                () -> assertFalse(schemaValid(EndpointProfileFixtures.VALID),
                        "The file refuses the reader's document (pinned drift)"),
                () -> assertEquals("local-runner",
                        EndpointProfile.of(EndpointProfileFixtures.valid()).clientId(),
                        "The reader accepts the reader's document"),
                () -> assertTrue(schemaValid(renamed(EndpointProfileFixtures.VALID)),
                        "The file accepts the harness-named document"),
                () -> assertThrows(ProfileRefusal.class,
                        () -> EndpointProfileFixtures.validate(renamed(EndpointProfileFixtures.VALID)),
                        "The reader refuses the harness-named document (pinned drift)"));
    }

    @Test
    @DisplayName("an undeclared member is refused by the file and the reader")
    void unknownMember() {
        assertAll(
                () -> assertFalse(schemaValid(undeclared(renamed(EndpointProfileFixtures.VALID))),
                        "The file refuses the undeclared member"),
                () -> assertThrows(ProfileRefusal.class,
                        () -> EndpointProfileFixtures.validate(undeclared(EndpointProfileFixtures.VALID)),
                        "The reader refuses the undeclared member"));
    }

    private static Map<?, ?> block(String name) {
        var properties = SchemaResource.object(SchemaResource.load(SCHEMA).get("properties"));
        return SchemaResource.object(properties.get(name));
    }

    private static String renamed(String document) {
        return document.replace("\"" + READER_ID_MEMBER + "\":", "\"" + ID_MEMBER + "\":");
    }

    private static String undeclared(String document) {
        return document.replaceFirst("\\{", "{\"undeclared\": 1,");
    }

    private static boolean schemaValid(String document) {
        return validator().validate(document, InputFormat.JSON).isEmpty();
    }

    private static JsonSchema validator() {
        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(SchemaResource.text(SCHEMA));
    }

    private static List<String> components(Class<? extends Record> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName)
                .map(EndpointProfileSchemaParityTest::snake).toList();
    }

    private static String snake(String name) {
        return name.replaceAll("([A-Z])", "_$1").toLowerCase(Locale.ROOT);
    }

    private static List<String> names(Iterable<?> values) {
        var names = new ArrayList<String>();
        values.forEach(value -> names.add(String.valueOf(value)));
        return names;
    }
}
