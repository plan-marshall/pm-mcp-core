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

import static de.planmarshall.core.store.StrictJson.FIELD_FORMAT_VERSION;
import static de.planmarshall.core.store.StrictJson.array;
import static de.planmarshall.core.store.StrictJson.integer;
import static de.planmarshall.core.store.StrictJson.map;
import static de.planmarshall.core.store.StrictJson.nextField;
import static de.planmarshall.core.store.StrictJson.nullableString;
import static de.planmarshall.core.store.StrictJson.requireFields;
import static de.planmarshall.core.store.StrictJson.requireObject;
import static de.planmarshall.core.store.StrictJson.string;
import static de.planmarshall.core.store.StrictJson.undeclared;
import static de.planmarshall.core.store.StrictJson.unreadable;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.id.RoleName;
import de.planmarshall.core.store.PlanOverrides.Axis;
import de.planmarshall.core.store.PlanOverrides.Channel;
import de.planmarshall.core.store.PlanOverrides.Integration;
import de.planmarshall.core.store.PlanOverrides.Posture;
import de.planmarshall.core.store.PlanOverrides.Providers;
import de.planmarshall.core.store.PlanOverrides.Role;
import de.planmarshall.core.store.PlanOverrides.UpdatedBy;

/**
 * Reads and writes a plan's {@code overrides.json} (Store Schemas § Plan Overrides, Workflow DSL § Plan Overrides). The
 * document is closed, so a write replaces it from its model: the revision, the values the plan sets, and the channel and
 * time of the write.
 * <p>
 * The schema is the file {@code plan-overrides.schema.json} beside this codec. The closed values of an axis are checked
 * where the axis binds, not here: this codec checks the schema's own rules (an axis value is named with its axis as
 * prefix, an integration variant is one of the two, a role object sets at least one value).
 *
 * @since 0.1
 */
@UtilityClass
public class PlanOverridesCodec {

    private static final String FIELD_REVISION = "revision";
    private static final String FIELD_ROLES = "roles";
    private static final String FIELD_AXES = "axes";
    private static final String FIELD_POSTURE = "posture";
    private static final String FIELD_BASE_BRANCH = "base_branch";
    private static final String FIELD_INTEGRATIONS = "integrations";
    private static final String FIELD_PROVIDERS = "providers";
    private static final String FIELD_CODE_ANALYSIS = "code_analysis";
    private static final String FIELD_REVIEW_BOTS = "review_bots";
    private static final String FIELD_UPDATED_AT = "updated_at";
    private static final String FIELD_UPDATED_BY = "updated_by";
    private static final String FIELD_HARNESS = "harness";
    private static final String FIELD_MODEL = "model";
    private static final String FIELD_EFFORT = "effort";
    private static final String FIELD_CHANNEL = "channel";
    private static final String FIELD_DEVICE_ID = "device_id";

    /** The length bounds of a model value, from the schema. */
    static final int MODEL_MAX_LENGTH = 128;

    /** The length bounds of an effort value, from the schema. */
    static final int EFFORT_MAX_LENGTH = 32;

    /** The length bounds of a base branch, from the schema. */
    static final int BASE_BRANCH_MAX_LENGTH = 255;

    /** The grammar of a code-analysis provider name, from the schema. */
    static final Pattern CODE_ANALYSIS = Pattern.compile("[a-z0-9][a-z0-9-]{1,62}[a-z0-9]");

    /** The grammar of a review-bot name, from the schema. */
    static final Pattern REVIEW_BOT = Pattern.compile("[a-z0-9][a-z0-9-]{0,31}");

    /** The grammar of a paired device id, from the schema. */
    static final Pattern DEVICE_ID = Pattern.compile("dev-[0-9a-f]{12}");

    /** The grammar of a variant's name after its axis prefix, from the schema. */
    static final Pattern VARIANT_NAME = Pattern.compile("[a-z][a-z0-9-]*");

    /** The form of a refusal that names the member and the reason. */
    private static final String DETAIL_FORMAT = "%s: %s";

    /**
     * @param file     the document's file, named by a refusal
     * @param document the document, UTF-8 encoded
     * @return the plan's own values
     * @throws StoreRefusal if the document declares another format version or is unreadable
     */
    public static PlanOverrides read(Path file, byte[] document) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        return StrictJson.read(file, document, PlanOverrides.FORMAT_VERSION, PlanOverridesCodec::document);
    }

    /**
     * @param overrides the plan's own values to write
     * @return the document, UTF-8 encoded
     * @throws IllegalArgumentException if a value is not one the schema admits
     */
    public static byte[] write(PlanOverrides overrides) {
        Objects.requireNonNull(overrides, "overrides");
        if (overrides.revision() < 1) {
            throw new IllegalArgumentException("A written plan overrides document is at revision 1 or more: "
                    + overrides.revision());
        }
        if (overrides.updatedAt() == null || overrides.updatedBy() == null) {
            throw new IllegalArgumentException("A written plan overrides document carries its write instant and channel");
        }
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
            generator.writeStartObject();
            generator.writeNumberField(FIELD_FORMAT_VERSION, PlanOverrides.FORMAT_VERSION);
            generator.writeNumberField(FIELD_REVISION, overrides.revision());
            writeRoles(generator, overrides.roles());
            writeAxes(generator, overrides.axes());
            if (overrides.posture() != null) {
                generator.writeStringField(FIELD_POSTURE, overrides.posture().wireName());
            }
            if (overrides.baseBranch() != null) {
                generator.writeStringField(FIELD_BASE_BRANCH, requireBaseBranch(overrides.baseBranch()));
            }
            writeIntegrations(generator, overrides.integrations());
            writeProviders(generator, overrides.providers());
            StrictJson.writeInstant(generator, FIELD_UPDATED_AT, overrides.updatedAt());
            writeUpdatedBy(generator, overrides.updatedBy());
            generator.writeEndObject();
        } catch (IOException e) {
            throw new IllegalStateException("The plan overrides cannot be written", e);
        }
        return out.toByteArray();
    }

    private static void writeRoles(JsonGenerator generator, Map<String, Role> roles) throws IOException {
        generator.writeObjectFieldStart(FIELD_ROLES);
        for (var entry : roles.entrySet()) {
            requireRole(entry.getKey());
            generator.writeObjectFieldStart(entry.getKey());
            var role = entry.getValue();
            if (role.harness() != null) {
                generator.writeStringField(FIELD_HARNESS, requireRole(role.harness()));
            }
            if (role.model() != null) {
                generator.writeStringField(FIELD_MODEL, requireBounded(role.model(), MODEL_MAX_LENGTH, FIELD_MODEL));
            }
            if (role.effort() != null) {
                generator.writeStringField(FIELD_EFFORT,
                        requireBounded(role.effort(), EFFORT_MAX_LENGTH, FIELD_EFFORT));
            }
            generator.writeEndObject();
        }
        generator.writeEndObject();
    }

    private static void writeAxes(JsonGenerator generator, Map<Axis, String> axes) throws IOException {
        generator.writeObjectFieldStart(FIELD_AXES);
        for (var entry : axes.entrySet()) {
            generator.writeStringField(entry.getKey().key(), requireVariant(entry.getKey(), entry.getValue()));
        }
        generator.writeEndObject();
    }

    private static void writeIntegrations(JsonGenerator generator, Map<String, Integration> integrations)
            throws IOException {
        generator.writeObjectFieldStart(FIELD_INTEGRATIONS);
        for (var entry : integrations.entrySet()) {
            requireRole(entry.getKey());
            generator.writeStringField(entry.getKey(), entry.getValue().wireName());
        }
        generator.writeEndObject();
    }

    private static void writeProviders(JsonGenerator generator, Providers providers) throws IOException {
        generator.writeObjectFieldStart(FIELD_PROVIDERS);
        writeNames(generator, FIELD_CODE_ANALYSIS, providers.codeAnalysis(), CODE_ANALYSIS);
        writeNames(generator, FIELD_REVIEW_BOTS, providers.reviewBots(), REVIEW_BOT);
        generator.writeEndObject();
    }

    private static void writeNames(JsonGenerator generator, String field, List<String> names,
            Pattern pattern) throws IOException {
        if (names.isEmpty()) {
            return;
        }
        generator.writeArrayFieldStart(field);
        var unique = new HashSet<String>();
        for (var name : names) {
            if (!pattern.matcher(name).matches()) {
                throw new IllegalArgumentException("Not a %s name: %s".formatted(field, name));
            }
            if (!unique.add(name)) {
                throw new IllegalArgumentException("A %s name is named twice: %s".formatted(field, name));
            }
            generator.writeString(name);
        }
        generator.writeEndArray();
    }

    private static void writeUpdatedBy(JsonGenerator generator, UpdatedBy updatedBy) throws IOException {
        generator.writeObjectFieldStart(FIELD_UPDATED_BY);
        generator.writeStringField(FIELD_CHANNEL, updatedBy.channel().wireName());
        if (updatedBy.deviceId() == null) {
            generator.writeNullField(FIELD_DEVICE_ID);
        } else {
            var deviceId = updatedBy.deviceId();
            if (!DEVICE_ID.matcher(deviceId).matches()) {
                throw new IllegalArgumentException("Not a paired device id: " + deviceId);
            }
            generator.writeStringField(FIELD_DEVICE_ID, deviceId);
        }
        generator.writeEndObject();
    }

    private static PlanOverrides document(JsonParser parser) throws IOException {
        var revision = -1L;
        Map<String, Role> roles = Map.of();
        Map<Axis, String> axes = Map.of();
        @Nullable Posture posture = null;
        @Nullable String baseBranch = null;
        Map<String, Integration> integrations = Map.of();
        Providers providers = Providers.none();
        @Nullable Instant updatedAt = null;
        @Nullable UpdatedBy updatedBy = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_FORMAT_VERSION -> version(parser);
                case FIELD_REVISION -> revision = revision(parser);
                case FIELD_ROLES -> roles = map(parser, FIELD_ROLES, PlanOverridesCodec::role);
                case FIELD_AXES -> axes = axes(parser);
                case FIELD_POSTURE -> posture = posture(parser);
                case FIELD_BASE_BRANCH -> baseBranch = baseBranch(parser);
                case FIELD_INTEGRATIONS -> integrations = map(parser, FIELD_INTEGRATIONS,
                        PlanOverridesCodec::integration);
                case FIELD_PROVIDERS -> providers = providers(parser);
                case FIELD_UPDATED_AT -> updatedAt = StrictJson.instant(parser, FIELD_UPDATED_AT);
                case FIELD_UPDATED_BY -> updatedBy = updatedBy(parser);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "plan overrides", FIELD_FORMAT_VERSION, FIELD_REVISION, FIELD_ROLES, FIELD_AXES,
                FIELD_INTEGRATIONS, FIELD_PROVIDERS, FIELD_UPDATED_AT, FIELD_UPDATED_BY);
        try {
            return PlanOverrides.builder()
                    .revision(revision)
                    .roles(roles)
                    .axes(axes)
                    .posture(posture)
                    .baseBranch(baseBranch)
                    .integrations(integrations)
                    .providers(providers)
                    .updatedAt(updatedAt)
                    .updatedBy(updatedBy)
                    .build();
        } catch (IllegalArgumentException | NullPointerException e) {
            throw unreadable("plan overrides: " + e.getMessage(), e);
        }
    }

    private static void version(JsonParser parser) throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT
                || parser.getIntValue() != PlanOverrides.FORMAT_VERSION) {
            throw unreadable("'format_version' is not %d".formatted(PlanOverrides.FORMAT_VERSION));
        }
    }

    private static long revision(JsonParser parser) throws IOException {
        var revision = integer(parser, FIELD_REVISION);
        if (revision < 1) {
            throw unreadable("'revision' must be 1 or more: " + revision);
        }
        return revision;
    }

    private static Role role(JsonParser parser, String name) throws IOException {
        requireRoleName(name);
        requireObject(parser, name);
        var seen = new HashSet<String>();
        @Nullable String harness = null;
        @Nullable String model = null;
        @Nullable String effort = null;
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            try {
                switch (field) {
                    case FIELD_HARNESS -> harness = requireRole(string(parser, field));
                    case FIELD_MODEL -> model = requireBounded(string(parser, field), MODEL_MAX_LENGTH, FIELD_MODEL);
                    case FIELD_EFFORT -> effort = requireBounded(string(parser, field), EFFORT_MAX_LENGTH,
                            FIELD_EFFORT);
                    default -> throw undeclared(field);
                }
            } catch (IllegalArgumentException e) {
                throw unreadable(DETAIL_FORMAT.formatted(name, e.getMessage()), e);
            }
        }
        try {
            return new Role(harness, model, effort);
        } catch (IllegalArgumentException e) {
            throw unreadable(DETAIL_FORMAT.formatted(name, e.getMessage()), e);
        }
    }

    private static Map<Axis, String> axes(JsonParser parser) throws IOException {
        requireObject(parser, FIELD_AXES);
        var axes = new LinkedHashMap<Axis, String>();
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            var axis = Axis.of(field)
                    .orElseThrow(() -> unreadable("not a plan behaviour axis: " + field));
            var value = string(parser, field);
            try {
                axes.put(axis, requireVariant(axis, value));
            } catch (IllegalArgumentException e) {
                throw unreadable(DETAIL_FORMAT.formatted(field, e.getMessage()), e);
            }
        }
        return axes;
    }

    private static Posture posture(JsonParser parser) throws IOException {
        var wireName = string(parser, FIELD_POSTURE);
        return Posture.of(wireName).orElseThrow(() -> unreadable("not a posture: " + wireName));
    }

    private static String baseBranch(JsonParser parser) throws IOException {
        try {
            return requireBaseBranch(string(parser, FIELD_BASE_BRANCH));
        } catch (IllegalArgumentException e) {
            throw unreadable(DETAIL_FORMAT.formatted(FIELD_BASE_BRANCH, e.getMessage()), e);
        }
    }

    private static Integration integration(JsonParser parser, String name) throws IOException {
        requireRoleName(name);
        var wireName = string(parser, name);
        return Integration.of(wireName)
                .orElseThrow(() -> unreadable("not an integration variant: " + wireName));
    }

    private static Providers providers(JsonParser parser) throws IOException {
        requireObject(parser, FIELD_PROVIDERS);
        var seen = new HashSet<String>();
        var codeAnalysis = new ArrayList<String>();
        var reviewBots = new ArrayList<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_CODE_ANALYSIS -> codeAnalysis.addAll(
                        array(parser, FIELD_CODE_ANALYSIS, (reader, element) -> name(reader, element, CODE_ANALYSIS)));
                case FIELD_REVIEW_BOTS -> reviewBots.addAll(
                        array(parser, FIELD_REVIEW_BOTS, (reader, element) -> name(reader, element, REVIEW_BOT)));
                default -> throw undeclared(field);
            }
        }
        requireUnique(FIELD_CODE_ANALYSIS, codeAnalysis);
        requireUnique(FIELD_REVIEW_BOTS, reviewBots);
        return new Providers(codeAnalysis, reviewBots);
    }

    private static String name(JsonParser parser, String field, Pattern pattern) throws IOException {
        var value = string(parser, field);
        if (!pattern.matcher(value).matches()) {
            throw unreadable("not a %s name: %s".formatted(field, value));
        }
        return value;
    }

    private static UpdatedBy updatedBy(JsonParser parser) throws IOException {
        requireObject(parser, FIELD_UPDATED_BY);
        var seen = new HashSet<String>();
        @Nullable Channel channel = null;
        Optional<String> deviceId = Optional.empty();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_CHANNEL -> channel = channel(parser);
                case FIELD_DEVICE_ID -> deviceId = nullableString(parser, FIELD_DEVICE_ID);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, FIELD_UPDATED_BY, FIELD_CHANNEL, FIELD_DEVICE_ID);
        var device = deviceId.orElse(null);
        if (device != null && !DEVICE_ID.matcher(device).matches()) {
            throw unreadable("not a paired device id: " + device);
        }
        return new UpdatedBy(Objects.requireNonNull(channel), device);
    }

    private static Channel channel(JsonParser parser) throws IOException {
        var wireName = string(parser, FIELD_CHANNEL);
        return Channel.of(wireName).orElseThrow(() -> unreadable("not a channel: " + wireName));
    }

    private static void requireUnique(String field, List<String> names) {
        if (new LinkedHashSet<>(names).size() != names.size()) {
            throw unreadable("a '%s' name is named twice".formatted(field));
        }
    }

    private static void requireRoleName(String name) {
        if (!RoleName.matches(name)) {
            throw unreadable("not an identifier of the document: " + name);
        }
    }

    static String requireRole(String name) {
        if (!RoleName.matches(name)) {
            throw new IllegalArgumentException("Not an identifier of the document: " + name);
        }
        return name;
    }

    static String requireBounded(String value, int maxLength, String field) {
        if (value.isEmpty() || value.length() > maxLength) {
            throw new IllegalArgumentException("'%s' is 1 to %d characters: %d"
                    .formatted(field, maxLength, value.length()));
        }
        return value;
    }

    static String requireBaseBranch(String value) {
        if (value.isEmpty() || value.length() > BASE_BRANCH_MAX_LENGTH) {
            throw new IllegalArgumentException("A base branch is 1 to %d characters: %d"
                    .formatted(BASE_BRANCH_MAX_LENGTH, value.length()));
        }
        return value;
    }

    static String requireVariant(Axis axis, String value) {
        var prefix = axis.prefix() + ".";
        if (!axis.names(value) || !VARIANT_NAME.matcher(value.substring(prefix.length())).matches()) {
            throw new IllegalArgumentException("A %s variant is named '%s' and a lower-case name: %s"
                    .formatted(axis.key(), prefix, value));
        }
        return value;
    }
}
