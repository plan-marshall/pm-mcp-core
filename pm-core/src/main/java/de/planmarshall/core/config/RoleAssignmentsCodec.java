/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.config;

import static de.planmarshall.core.store.StrictJson.FIELD_FORMAT_VERSION;
import static de.planmarshall.core.store.StrictJson.integer;
import static de.planmarshall.core.store.StrictJson.map;
import static de.planmarshall.core.store.StrictJson.nextField;
import static de.planmarshall.core.store.StrictJson.nullableString;
import static de.planmarshall.core.store.StrictJson.requireFields;
import static de.planmarshall.core.store.StrictJson.requireObject;
import static de.planmarshall.core.store.StrictJson.string;
import static de.planmarshall.core.store.StrictJson.undeclared;
import static de.planmarshall.core.store.StrictJson.unreadable;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.role.EffortValue;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.StrictJson;

/**
 * Reads and writes {@code role-assignments.json} of the machine or of one project (Store Schemas § Role Assignments,
 * PM-WORK-1): per role name its harness, its model, the effort and the optional escalation.
 * <p>
 * The document is closed, so a write replaces it from its model: the assignments per role and the document's
 * {@code revision}. The effort is written as the schema distinguishes it — absent when the level names none, {@code null}
 * when it clears the effort, and the value otherwise.
 *
 * @since 0.1
 */
@UtilityClass
public class RoleAssignmentsCodec {

    /** The format version of the role assignment store. */
    public static final int FORMAT_VERSION = 1;

    private static final Pattern ROLE_NAME = Pattern.compile("[a-z][a-z0-9-]{1,31}");

    private static final String FIELD_REVISION = "revision";
    private static final String FIELD_ROLES = "roles";
    private static final String FIELD_HARNESS = "harness";
    private static final String FIELD_MODEL = "model";
    private static final String FIELD_EFFORT = "effort";
    private static final String FIELD_ESCALATION = "escalation";

    /**
     * What a read of the document found.
     *
     * @param roles    the assignments by role name
     * @param revision the {@code revision} of the document
     * @since 0.1
     */
    public record Document(Map<String, RoleAssignmentValue> roles, long revision) {

        /** Keeps the map unmodifiable. */
        public Document {
            roles = Collections.unmodifiableMap(new LinkedHashMap<>(roles));
        }
    }

    /**
     * @param file     the document's file, named by a refusal
     * @param document the document, UTF-8 encoded
     * @return the assignments and the revision
     * @throws StoreRefusal if the document declares another format version or is unreadable
     */
    public static Document read(Path file, byte[] document) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        return StrictJson.read(file, document, FORMAT_VERSION, RoleAssignmentsCodec::document);
    }

    /**
     * @param roles    the assignments to write
     * @param revision the revision to write
     * @return the document, UTF-8 encoded
     */
    public static byte[] write(Map<String, RoleAssignmentValue> roles, long revision) {
        Objects.requireNonNull(roles, FIELD_ROLES);
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
            generator.writeStartObject();
            generator.writeNumberField(FIELD_FORMAT_VERSION, FORMAT_VERSION);
            generator.writeNumberField(FIELD_REVISION, revision);
            generator.writeObjectFieldStart(FIELD_ROLES);
            for (var entry : roles.entrySet()) {
                requireRoleName(entry.getKey());
                generator.writeObjectFieldStart(entry.getKey());
                writeAssignment(generator, entry.getValue());
                generator.writeEndObject();
            }
            generator.writeEndObject();
            generator.writeEndObject();
        } catch (IOException e) {
            throw new IllegalStateException("The role assignments cannot be written", e);
        }
        return out.toByteArray();
    }

    private static void writeAssignment(JsonGenerator generator, RoleAssignmentValue assignment) throws IOException {
        generator.writeStringField(FIELD_HARNESS, assignment.harness());
        generator.writeStringField(FIELD_MODEL, assignment.model());
        if (assignment.effort() == EffortSetting.VALUE) {
            generator.writeStringField(FIELD_EFFORT, assignment.effortValue().orElseThrow().value());
        } else if (assignment.effort() == EffortSetting.DEFAULT) {
            generator.writeNullField(FIELD_EFFORT);
        }
        if (assignment.escalation().isPresent()) {
            var escalation = assignment.escalation().get();
            generator.writeObjectFieldStart(FIELD_ESCALATION);
            generator.writeStringField(FIELD_HARNESS, escalation.harness());
            generator.writeStringField(FIELD_MODEL, escalation.model());
            if (escalation.effort().isPresent()) {
                generator.writeStringField(FIELD_EFFORT, escalation.effort().get().value());
            } else {
                generator.writeNullField(FIELD_EFFORT);
            }
            generator.writeEndObject();
        }
    }

    private static Document document(JsonParser parser) throws IOException {
        Map<String, RoleAssignmentValue> roles = Map.of();
        var revision = -1L;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_FORMAT_VERSION -> version(parser);
                case FIELD_REVISION -> revision = revision(parser);
                case FIELD_ROLES -> roles = map(parser, FIELD_ROLES, RoleAssignmentsCodec::assignment);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "role assignment store", FIELD_FORMAT_VERSION, FIELD_REVISION, FIELD_ROLES);
        return new Document(roles, revision);
    }

    private static void version(JsonParser parser) throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT || parser.getIntValue() != FORMAT_VERSION) {
            throw unreadable("'format_version' is not %d".formatted(FORMAT_VERSION));
        }
    }

    private static long revision(JsonParser parser) throws IOException {
        var revision = integer(parser, FIELD_REVISION);
        if (revision < 0) {
            throw unreadable("'revision' must not be negative: " + revision);
        }
        return revision;
    }

    private static RoleAssignmentValue assignment(JsonParser parser, String role) throws IOException {
        try {
            requireRoleName(role);
        } catch (IllegalArgumentException e) {
            throw unreadable("not a role name of an assignment: " + role, e);
        }
        requireObject(parser, role);
        var seen = new HashSet<String>();
        @Nullable String harness = null;
        @Nullable String model = null;
        var effort = EffortSetting.INHERIT;
        Optional<EffortValue> effortValue = Optional.empty();
        Optional<Escalation> escalation = Optional.empty();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_HARNESS -> harness = string(parser, field);
                case FIELD_MODEL -> model = string(parser, field);
                case FIELD_EFFORT -> {
                    var value = nullableString(parser, field).orElse(null);
                    if (value == null) {
                        effort = EffortSetting.DEFAULT;
                    } else {
                        effort = EffortSetting.VALUE;
                        effortValue = Optional.of(new EffortValue(value));
                    }
                }
                case FIELD_ESCALATION -> escalation = escalation(parser);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "role assignment", FIELD_HARNESS, FIELD_MODEL);
        try {
            return new RoleAssignmentValue(harness, model, effort, effortValue, escalation);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw unreadable("%s: %s".formatted(role, e.getMessage()), e);
        }
    }

    private static Optional<Escalation> escalation(JsonParser parser) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NULL) {
            return Optional.empty();
        }
        requireObject(parser, FIELD_ESCALATION);
        var seen = new HashSet<String>();
        @Nullable String harness = null;
        @Nullable String model = null;
        Optional<EffortValue> effort = Optional.empty();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_HARNESS -> harness = string(parser, field);
                case FIELD_MODEL -> model = string(parser, field);
                case FIELD_EFFORT -> {
                    var value = nullableString(parser, field).orElse(null);
                    effort = value == null ? Optional.empty() : Optional.of(new EffortValue(value));
                }
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "escalation assignment", FIELD_HARNESS, FIELD_MODEL, FIELD_EFFORT);
        try {
            return Optional.of(new Escalation(harness, model, effort));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw unreadable("escalation: %s".formatted(e.getMessage()), e);
        }
    }

    static void requireRoleName(String role) {
        if (!ROLE_NAME.matcher(role).matches()) {
            throw new IllegalArgumentException("Not a role name of an assignment: " + role);
        }
    }
}
