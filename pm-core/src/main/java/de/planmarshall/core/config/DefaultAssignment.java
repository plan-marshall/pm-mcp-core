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
import static de.planmarshall.core.store.StrictJson.map;
import static de.planmarshall.core.store.StrictJson.nextField;
import static de.planmarshall.core.store.StrictJson.nullableString;
import static de.planmarshall.core.store.StrictJson.requireFields;
import static de.planmarshall.core.store.StrictJson.requireObject;
import static de.planmarshall.core.store.StrictJson.string;
import static de.planmarshall.core.store.StrictJson.undeclared;
import static de.planmarshall.core.store.StrictJson.unreadable;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import de.planmarshall.core.role.EffortValue;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.StrictJson;

/**
 * The release's default role assignment, {@code classpath:/roles/default-assignment.json} (Model Work § Default Role
 * Assignment): per role the model tier, the effort, the harness the release graded the role on and the advisory that is
 * shown beside the value.
 * <p>
 * The default assignment is data of the release, read beside the shipped role set, and it is the proposal
 * {@code roles.default} applies to the catalogue before the operator confirms or changes it in {@code roles.write}. The
 * steward is in the document like every other role, but no assignment names it: its configuration is the machine's
 * default harness and that harness's tier {@code standard}.
 *
 * @param roles the defaults by role name
 * @since 0.1
 */
public record DefaultAssignment(Map<String, DefaultRole> roles) {

    /** The resource of the release's default assignment. */
    public static final String RESOURCE = "roles/default-assignment.json";

    /** The format version of the default assignment. */
    public static final int FORMAT_VERSION = 1;

    private static final String FIELD_ROLES = "roles";
    private static final String FIELD_TIER = "tier";
    private static final String FIELD_EFFORT = "effort";
    private static final String FIELD_PREFERRED_HARNESS = "preferred_harness";
    private static final String FIELD_ADVISORY = "advisory";

    /** Copies the map. */
    public DefaultAssignment {
        roles = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(roles, "roles")));
    }

    /**
     * One role's default.
     *
     * @param tier             the model tier of the closed list {@code frontier}, {@code standard}, {@code fast}
     * @param effort           the effort, or empty for the profile's default effort
     * @param preferredHarness the client id of the harness the release graded the role on
     * @param advisory         the message key of the advisory shown beside the value
     * @since 0.1
     */
    public record DefaultRole(String tier, Optional<EffortValue> effort, String preferredHarness, String advisory) {

        private static final Set<String> TIERS = Set.of("frontier", "standard", "fast");

        /** @throws IllegalArgumentException if a value is missing or the tier is outside the closed list */
        public DefaultRole {
            Objects.requireNonNull(tier, "tier");
            Objects.requireNonNull(effort, "effort");
            Objects.requireNonNull(preferredHarness, "preferredHarness");
            Objects.requireNonNull(advisory, "advisory");
            if (!TIERS.contains(tier)) {
                throw new IllegalArgumentException("Not a model tier: " + tier);
            }
            if (preferredHarness.isEmpty() || advisory.isEmpty()) {
                throw new IllegalArgumentException("A default assignment names its preferred harness and its advisory");
            }
        }
    }

    /**
     * @param role a role name
     * @return the role's default, or empty when the release's default does not name it
     */
    public Optional<DefaultRole> of(String role) {
        return Optional.ofNullable(roles.get(role));
    }

    /**
     * Reads the default assignment of the release this classpath carries.
     *
     * @param loader the loader of the resources
     * @return the default assignment; empty when the release ships none
     * @throws UncheckedIOException if the resource exists and cannot be read
     * @throws StoreRefusal         if the resource is not in its format
     */
    public static Optional<DefaultAssignment> load(ClassLoader loader) {
        try (InputStream stream = loader.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                return Optional.empty();
            }
            return Optional.of(read(Path.of(RESOURCE), stream.readAllBytes()));
        } catch (IOException e) {
            throw new UncheckedIOException("The default assignment cannot be read", e);
        }
    }

    /**
     * @return the default assignment of this classpath
     */
    public static Optional<DefaultAssignment> load() {
        return load(DefaultAssignment.class.getClassLoader());
    }

    /**
     * @param file     the document's file, named by a refusal
     * @param document the document, UTF-8 encoded
     * @return the defaults
     * @throws StoreRefusal if the document declares another format version or is unreadable
     */
    public static DefaultAssignment read(Path file, byte[] document) {
        return StrictJson.read(file, document, FORMAT_VERSION, DefaultAssignment::document);
    }

    private static DefaultAssignment document(JsonParser parser) throws IOException {
        Map<String, DefaultRole> roles = Map.of();
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_FORMAT_VERSION -> version(parser);
                case FIELD_ROLES -> roles = map(parser, FIELD_ROLES, DefaultAssignment::role);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "default assignment", FIELD_FORMAT_VERSION, FIELD_ROLES);
        return new DefaultAssignment(roles);
    }

    private static void version(JsonParser parser) throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT || parser.getIntValue() != FORMAT_VERSION) {
            throw unreadable("'format_version' is not %d".formatted(FORMAT_VERSION));
        }
    }

    private static DefaultRole role(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        var seen = new HashSet<String>();
        @Nullable String tier = null;
        Optional<EffortValue> effort = Optional.empty();
        @Nullable String preferredHarness = null;
        @Nullable String advisory = null;
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_TIER -> tier = string(parser, field);
                case FIELD_EFFORT -> effort = nullableString(parser, field).map(EffortValue::new);
                case FIELD_PREFERRED_HARNESS -> preferredHarness = string(parser, field);
                case FIELD_ADVISORY -> advisory = string(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "default role", FIELD_TIER, FIELD_EFFORT, FIELD_PREFERRED_HARNESS, FIELD_ADVISORY);
        try {
            return new DefaultRole(tier, effort, preferredHarness, advisory);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw unreadable("%s: %s".formatted(name, e.getMessage()), e);
        }
    }
}
