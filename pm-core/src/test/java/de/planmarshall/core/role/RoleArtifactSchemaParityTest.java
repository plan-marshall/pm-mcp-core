/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.SchemaResource;

/**
 * {@link RoleArtifact} and its reader {@link RoleArtifactValidator} keep parity with the schema of Model Work § Role
 * Artifact Schema: the record's components are the file's members in schema order, the file requires exactly the members
 * the reader refuses to miss, every optional member is accepted by both, and the format version, the enumerations and
 * the bounds are the file's. The schema is the file {@code role-artifact-v1.schema.json} in
 * {@code pm-core/src/main/resources/de/planmarshall/core/role/}, read from the class path through {@link SchemaResource}.
 * The member names and the values of the test documents are the wire format; they are literals here for that reason.
 */
@DisplayName("Role artifact schema parity")
class RoleArtifactSchemaParityTest {

    private static final String SCHEMA = "de/planmarshall/core/role/role-artifact-v1.schema.json";
    private static final String NAME = "parity";
    private static final Map<String, String> VALUES = values();
    private static final List<String> REQUIRED = required();

    @Test
    @DisplayName("the schema members are the record components, in schema order")
    void membersAreTheRecordComponents() {
        var schema = SchemaResource.load(SCHEMA);

        assertAll(
                () -> assertEquals(names(SchemaResource.object(schema.get("properties")).keySet()),
                        componentNames(), "The members are the record's in order"),
                () -> assertEquals(Boolean.FALSE, schema.get("additionalProperties"),
                        "The file closes its member set"));
    }

    @Test
    @DisplayName("the format version, the enumerations and the bounds are the reader's")
    void constantsAreTheReaders() {
        var schema = SchemaResource.load(SCHEMA);
        var properties = SchemaResource.object(schema.get("properties"));

        assertAll(
                () -> assertEquals(RoleArtifactValidator.FORMAT_VERSION,
                        ((Number) SchemaResource.object(properties.get("format_version")).get("const")).intValue(),
                        "The format version is the reader's"),
                () -> assertEquals(Set.copyOf(Arrays.stream(RoleClass.values()).map(RoleClass::wireName).toList()),
                        Set.copyOf(names(SchemaResource.array(
                                SchemaResource.object(properties.get("class")).get("enum")))),
                        "The class values are the type's"),
                () -> assertEquals(Set.copyOf(Arrays.stream(RoleForm.values()).map(RoleForm::wireName).toList()),
                        Set.copyOf(names(SchemaResource.array(
                                SchemaResource.object(properties.get("form")).get("enum")))),
                        "The form values are the type's"),
                () -> assertEquals(Set.copyOf(Arrays.stream(RoleArtifact.OffTrackAction.values())
                                .map(RoleArtifact.OffTrackAction::wireName).toList()),
                        Set.copyOf(names(SchemaResource.array(action(schema)))), "The actions are the type's"),
                () -> assertEquals(1, ((Number) SchemaResource.object(properties.get("count")).get("minimum"))
                        .intValue(), "The lowest count is one"),
                () -> assertEquals(16, ((Number) SchemaResource.object(properties.get("count")).get("maximum"))
                        .intValue(), "The highest count is sixteen"),
                () -> assertEquals(60, ((Number) SchemaResource.object(properties.get("budget_floor_seconds"))
                        .get("minimum")).intValue(), "The lowest budget floor is sixty"),
                () -> assertEquals(86400, ((Number) SchemaResource.object(properties.get("budget_floor_seconds"))
                        .get("maximum")).intValue(), "The highest budget floor is one day"));
        assertAll(
                () -> assertThrows(RoleRefusal.class, () -> read(with(Map.of("count", "0"))),
                        "The reader refuses a count below one"),
                () -> assertThrows(RoleRefusal.class, () -> read(with(Map.of("count", "17"))),
                        "The reader refuses a count above sixteen"),
                () -> assertDoesNotThrow(() -> read(with(Map.of("count", "16"))),
                        "The reader accepts the highest count"),
                () -> assertThrows(RoleRefusal.class, () -> read(with(Map.of("budget_floor_seconds", "59"))),
                        "The reader refuses a budget floor below sixty"),
                () -> assertThrows(RoleRefusal.class, () -> read(with(Map.of("budget_floor_seconds", "86401"))),
                        "The reader refuses a budget floor above a day"));
    }

    @Test
    @DisplayName("every required member is a declared member")
    void requiredMembersAreDeclared() {
        var properties = names(SchemaResource.object(SchemaResource.load(SCHEMA).get("properties")).keySet());

        assertTrue(properties.containsAll(REQUIRED), "The required members are declared members");
    }

    @ParameterizedTest(name = "without {0}")
    @MethodSource("requiredMemberNames")
    @DisplayName("the reader demands every member the file requires")
    void requiredMember(String required) {
        var members = new LinkedHashMap<>(VALUES);
        members.remove(required);
        var document = document(members);

        assertAll(
                () -> assertFalse(validator().validate(document, InputFormat.JSON).isEmpty(),
                        "The file refuses the absent member"),
                () -> assertThrows(RoleRefusal.class, () -> read(document),
                        "The reader refuses the absent member"));
    }

    @ParameterizedTest(name = "without {0}")
    @MethodSource("optionalMemberNames")
    @DisplayName("both accept every absent optional member")
    void optionalMember(String optional) {
        var members = new LinkedHashMap<>(VALUES);
        members.remove(optional);
        if ("recycling".equals(optional)) {
            members.put("form", "\"fresh\"");
        }
        var document = document(members);

        assertAll(
                () -> assertEquals(Set.of(), validator().validate(document, InputFormat.JSON),
                        "The file accepts the absent optional member"),
                () -> assertDoesNotThrow(() -> read(document), "The reader accepts the absent optional member"));
    }

    @Test
    @DisplayName("the all-member document is valid for the file and the reader")
    void matchedPositiveControl() {
        var document = document(VALUES);

        assertAll(
                () -> assertEquals(Set.of(), validator().validate(document, InputFormat.JSON),
                        "The file accepts the unchanged document"),
                () -> assertDoesNotThrow(() -> read(document), "The reader accepts the unchanged document"));
    }

    @Test
    @DisplayName("an undeclared member is refused by the file and the reader")
    void unknownMember() {
        var document = document(VALUES).replaceFirst("\\{", "{\"undeclared\": 1,");

        assertAll(
                () -> assertFalse(validator().validate(document, InputFormat.JSON).isEmpty(),
                        "The file refuses the undeclared member"),
                () -> assertThrows(RoleRefusal.class, () -> read(document),
                        "The reader refuses the undeclared member"));
    }

    private static Stream<String> requiredMemberNames() {
        return REQUIRED.stream();
    }

    private static Stream<String> optionalMemberNames() {
        return names(SchemaResource.object(SchemaResource.load(SCHEMA).get("properties")).keySet()).stream()
                .filter(member -> !REQUIRED.contains(member));
    }

    private static String document(Map<String, String> members) {
        var entries = new ArrayList<String>();
        members.forEach((member, value) -> entries.add("\"%s\": %s".formatted(member, value)));
        return "{" + String.join(", ", entries) + "}";
    }

    private static String with(Map<String, String> overrides) {
        var members = new LinkedHashMap<>(VALUES);
        members.putAll(overrides);
        return document(members);
    }

    private static RoleArtifact read(String document) {
        return RoleArtifactValidator.validate(document.getBytes(UTF_8), NAME);
    }

    private static Object action(Map<?, ?> schema) {
        var signal = SchemaResource.object(SchemaResource.object(schema.get("$defs")).get("off_track_signal"));
        return SchemaResource.object(SchemaResource.object(signal.get("properties")).get("action")).get("enum");
    }

    private static Map<String, String> values() {
        var values = new LinkedHashMap<String, String>();
        values.put("format_version", "1");
        values.put("name", "\"" + NAME + "\"");
        values.put("description", "\"A role for the schema parity test.\"");
        values.put("class", "\"read_only\"");
        values.put("tools", "[\"pm_wait\"]");
        values.put("harness", "\"acme-cli\"");
        values.put("model", "\"acme-standard\"");
        values.put("effort", "null");
        values.put("form", "\"warm\"");
        values.put("count", "2");
        values.put("skills", "[\"skill://pm/role/parity/SKILL.md\"]");
        values.put("recycling", "{\"idle_wakeups\": null, \"token_budget\": 10000, \"tasks_per_generation\": null}");
        values.put("budget_floor_seconds", "300");
        values.put("context_budget_tokens", "10000");
        values.put("questions", "{\"hold_seconds\": 0, \"resume_within_seconds\": 0}");
        values.put("off_track", "{\"repeating\": {\"action\": \"show\"}}");
        return Collections.unmodifiableMap(values);
    }

    private static List<String> required() {
        return names(SchemaResource.array(SchemaResource.load(SCHEMA).get("required")));
    }

    private static List<String> componentNames() {
        return Arrays.stream(RoleArtifact.class.getRecordComponents()).map(RecordComponent::getName)
                .map(RoleArtifactSchemaParityTest::snake).toList();
    }

    private static String snake(String name) {
        var mapped = "roleClass".equals(name) ? "class" : name;
        return mapped.replaceAll("([A-Z])", "_$1").toLowerCase(Locale.ROOT);
    }

    private static JsonSchema validator() {
        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(SchemaResource.text(SCHEMA));
    }

    private static List<String> names(Iterable<?> values) {
        var names = new ArrayList<String>();
        values.forEach(value -> names.add(String.valueOf(value)));
        return names;
    }
}
