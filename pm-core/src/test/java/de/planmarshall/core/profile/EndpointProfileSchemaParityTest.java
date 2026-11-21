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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaException;
import java.io.FileNotFoundException;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.SchemaResource;
import de.planmarshall.core.SchemaValidation;

/**
 * {@link EndpointProfile} and its reader {@link EndpointProfile#of(JsonNode)} keep parity with the schema of Harness
 * Profiles § Endpoint Profile Schema: the {@code endpoint}, {@code identity} and {@code weights_probe} blocks are the
 * projection's records, member for member, and the two constants are the reader's. The file is
 * {@code endpoint-profile.schema.json} in {@code pm-core/src/main/resources/de/planmarshall/core/profile/}, read through
 * {@link SchemaResource}. The file and the validator name the id member {@code harness_id}; a document written with the
 * former {@code client_id} is read beside it and is not schema-valid, which is the compatibility the rename keeps. The
 * {@code icon}, {@code update_evidence} and {@code model_tiers} blocks are the harness profile's definitions
 * of the same names, compared here without their descriptions; the reader additionally accepts a {@code null}
 * {@code update_evidence}, a leniency owned by the endpoint reader's package. The member names are the wire format; they
 * are literals here for that reason.
 */
@DisplayName("Endpoint profile schema parity")
class EndpointProfileSchemaParityTest {

    private static final String SCHEMA = "de/planmarshall/core/profile/endpoint-profile.schema.json";
    private static final String HARNESS = "de/planmarshall/core/profile/harness-profile-v1.schema.json";
    private static final String ID_MEMBER = "harness_id";
    private static final String LEGACY_ID_MEMBER = "client_id";

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
    @DisplayName("the top-level required members are the reader's document")
    void topLevelRequired() {
        var schema = SchemaResource.load(SCHEMA);
        var required = names(SchemaResource.array(schema.get("required")));
        var document = SchemaResource.object(SchemaResource.parse(EndpointProfileFixtures.VALID));

        assertEquals(Set.copyOf(required), Set.copyOf(names(document.keySet())),
                "The file requires exactly the reader's members");
    }

    @Test
    @DisplayName("the shared blocks are the harness profile's definitions")
    void sharedBlocks() {
        var harness = SchemaResource.object(SchemaResource.load(HARNESS).get("properties"));
        var harnessHeadless = SchemaResource.object(SchemaResource.object(harness.get("headless")).get("properties"));

        assertAll(
                () -> assertEquals(withoutDescriptions(SchemaResource.object(harness.get("icon"))),
                        withoutDescriptions(block("icon")), "The icon block is the harness profile's"),
                () -> assertEquals(withoutDescriptions(SchemaResource.object(harnessHeadless.get("update_evidence"))),
                        withoutDescriptions(block("update_evidence")), "The update evidence is the harness profile's"),
                () -> assertEquals(withoutDescriptions(SchemaResource.object(harnessHeadless.get("model_tiers"))),
                        withoutDescriptions(block("model_tiers")), "The model tiers are the harness profile's"));
    }

    @Test
    @DisplayName("the profile kind and the schema version are the reader's constants")
    void constants() {
        var properties = SchemaResource.object(SchemaResource.load(SCHEMA).get("properties"));

        assertAll(
                () -> assertEquals(ProfileValidator.PROFILE_KIND_ENDPOINT,
                        SchemaResource.object(properties.get("profile_kind")).get("const"),
                        "The profile kind is the reader's"),
                () -> assertEquals(ProfileValidator.SCHEMA_VERSION,
                        ((Number) SchemaResource.object(properties.get("schema_version")).get("const")).intValue(),
                        "The schema version is the reader's"));
    }

    @Test
    @DisplayName("the one protocol the file names is the one the reader accepts")
    void protocols() {
        var properties = SchemaResource.object(SchemaResource.load(SCHEMA).get("properties"));
        var endpoint = SchemaResource.object(SchemaResource.object(properties.get("endpoint")).get("properties"));
        var other = EndpointProfileFixtures.variant("\"protocol\": \"openai_chat\"", "\"protocol\": \"system_one\"");
        var fileAcceptsOther = schemaValid(other);

        assertAll(
                () -> assertEquals(Set.of("openai_chat"),
                        Set.copyOf(names(SchemaResource.array(
                                SchemaResource.object(endpoint.get("protocol")).get("enum")))),
                        "The file names the reader's one protocol"),
                () -> assertThrows(ProfileRefusal.class, () -> EndpointProfileFixtures.validate(other),
                        "The reader refuses another protocol"),
                () -> assertFalse(fileAcceptsOther, "The file refuses another protocol"));
    }

    @Test
    @DisplayName("the id member is the file's and the reader's, and the former name is read beside it")
    void idMemberParity() {
        var properties = names(SchemaResource.object(SchemaResource.load(SCHEMA).get("properties")).keySet());
        var projected = components(EndpointProfile.class);
        var expected = new ArrayList<>(projected);
        expected.remove(ID_MEMBER);
        var canonical = EndpointProfileFixtures.VALID;
        var legacy = legacy(canonical);
        var profile = EndpointProfileFixtures.valid();

        assertAll(
                () -> assertTrue(properties.contains(ID_MEMBER), "The file names the id as the harness does"),
                () -> assertFalse(properties.contains(LEGACY_ID_MEMBER),
                        "The file does not name the former member"),
                () -> assertTrue(properties.containsAll(expected), "The projection's members are the file's"),
                () -> assertTrue(schemaValid(canonical), "The file accepts the harness-named document"),
                () -> assertEquals("local-runner", EndpointProfile.of(profile).harnessId(),
                        "The reader accepts the harness-named document"),
                () -> assertFalse(schemaValid(legacy), "The file refuses the former member"),
                () -> assertDoesNotThrow(() -> EndpointProfileFixtures.validate(legacy),
                        "The reader still reads the former member"));
    }

    @Test
    @DisplayName("an undeclared member is refused by the file and the reader")
    void unknownMember() {
        var harnessDocument = legacy(EndpointProfileFixtures.VALID);
        var fileRefusesHarness = !schemaValid(undeclared(harnessDocument));
        var readerDocument = undeclared(EndpointProfileFixtures.VALID);

        assertAll(
                () -> assertTrue(fileRefusesHarness, "The file refuses the undeclared member"),
                () -> assertThrows(ProfileRefusal.class, () -> EndpointProfileFixtures.validate(readerDocument),
                        "The reader refuses the undeclared member"));
    }

    /**
     * Pins how the 3.0.9 registry treats the one reference of {@code harness-profile-v1.schema.json} that leaves the
     * file: {@code headless.weights_probe} names {@code endpoint-profile.json#/$defs/weights_probe}, resolved against the
     * harness file's {@code $id} to the endpoint file's {@code $id}. The registry of {@link SchemaValidation} fetches no
     * remote resource and knows no other compiled schema, so the reference is refused when a document reaches it: the
     * validation throws, it neither skips the reference nor reports an error item. The other seven references of the
     * file point into its own {@code $defs}. The document is a wire-format fixture that only reaches the reference.
     */
    @Test
    @DisplayName("the harness profile's reference to the endpoint profile is refused, never fetched or skipped")
    void harnessReferenceToEndpointIsRefused() {
        var harness = SchemaValidation.compile(SchemaResource.text(HARNESS));
        var endpointId = String.valueOf(SchemaResource.load(SCHEMA).get("$id"));

        var refusal = assertThrows(SchemaException.class,
                () -> SchemaValidation.validate(harness, "{\"headless\": {\"weights_probe\": null}}"),
                "the unresolvable reference throws, not skipped");

        assertAll(
                () -> assertInstanceOf(FileNotFoundException.class, refusal.getCause(),
                        "the referenced schema is not found to load"),
                () -> assertTrue(refusal.getMessage().contains(endpointId),
                        "the refusal names the endpoint schema's id"));
    }

    private static Map<?, ?> block(String name) {
        var properties = SchemaResource.object(SchemaResource.load(SCHEMA).get("properties"));
        return SchemaResource.object(properties.get(name));
    }

    private static Object withoutDescriptions(Object value) {
        return switch (value) {
            case Map<?, ?> map -> {
                var copy = new LinkedHashMap<String, Object>();
                map.forEach((name, child) -> {
                    if (!"description".equals(String.valueOf(name))) {
                        copy.put(String.valueOf(name), withoutDescriptions(child));
                    }
                });
                yield copy;
            }
            case List<?> list -> list.stream().map(EndpointProfileSchemaParityTest::withoutDescriptions).toList();
            default -> value;
        };
    }

    private static String legacy(String document) {
        return document.replace("\"" + ID_MEMBER + "\":", "\"" + LEGACY_ID_MEMBER + "\":");
    }

    private static String undeclared(String document) {
        return document.replaceFirst("\\{", "{\"undeclared\": 1,");
    }

    private static boolean schemaValid(String document) {
        return SchemaValidation.validate(validator(), document).isEmpty();
    }

    private static Schema validator() {
        return SchemaValidation.compile(SchemaResource.text(SCHEMA));
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
