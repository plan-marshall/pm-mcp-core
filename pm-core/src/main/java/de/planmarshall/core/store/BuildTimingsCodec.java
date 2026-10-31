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
import static de.planmarshall.core.store.StrictJson.integer;
import static de.planmarshall.core.store.StrictJson.nextField;
import static de.planmarshall.core.store.StrictJson.nullableInstant;
import static de.planmarshall.core.store.StrictJson.requireFields;
import static de.planmarshall.core.store.StrictJson.requireObject;
import static de.planmarshall.core.store.StrictJson.undeclared;
import static de.planmarshall.core.store.StrictJson.unreadable;
import static de.planmarshall.core.store.StrictJson.writeInstant;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.store.BuildTimings.Baseline;
import de.planmarshall.core.store.BuildTimings.KeyTimings;

/**
 * Reads and writes {@code build-timings.json} (Store Schemas § Build Timings Store):
 * {@code {"format_version": 1, "keys": {"<budget key>": <key timings>}}}, where key timings carry the three baselines,
 * the one-shot escalation and the instant of the last observation.
 * <p>
 * The reader accepts exactly one shape: the current format version, only the declared fields, each at most once, every
 * required field, and a budget key that matches the grammar of the store. Anything else is unreadable, never an empty
 * store, and the refusal names the file.
 *
 * @since 0.1
 */
@UtilityClass
public class BuildTimingsCodec {

    private static final String FIELD_KEYS = "keys";
    private static final String FIELD_CLEAN = "clean";
    private static final String FIELD_POISONED = "poisoned";
    private static final String FIELD_COLD = "cold";
    private static final String FIELD_ESCALATION_SECONDS = "escalation_seconds";
    private static final String FIELD_LAST_OBSERVED_AT = "last_observed_at";
    private static final String FIELD_LEARNED_SECONDS = "learned_seconds";
    private static final String FIELD_OBSERVATIONS = "observations";

    /**
     * @param timings the store
     * @return the document, UTF-8 encoded
     */
    public static byte[] write(BuildTimings timings) {
        Objects.requireNonNull(timings, "timings");
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
            generator.writeStartObject();
            generator.writeNumberField(FIELD_FORMAT_VERSION, BuildTimings.FORMAT_VERSION);
            generator.writeObjectFieldStart(FIELD_KEYS);
            for (var entry : timings.keys().entrySet()) {
                if (!BuildTimings.BUDGET_KEY.matcher(entry.getKey()).matches()) {
                    throw new IllegalArgumentException("Not a budget key: " + entry.getKey());
                }
                generator.writeFieldName(entry.getKey());
                writeKeyTimings(generator, entry.getValue());
            }
            generator.writeEndObject();
            generator.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException("Build-timings store document cannot be written", e);
        }
        return out.toByteArray();
    }

    /**
     * @param file     the store file the document was read from, named by a refusal
     * @param document a build-timings store document, UTF-8 encoded
     * @return the store
     * @throws StoreRefusal if the document declares another format version or is unreadable
     */
    public static BuildTimings read(Path file, byte[] document) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        return StrictJson.read(file, document, BuildTimings.FORMAT_VERSION, BuildTimingsCodec::readDocument);
    }

    private static void writeKeyTimings(JsonGenerator generator, KeyTimings timings) throws IOException {
        generator.writeStartObject();
        generator.writeFieldName(FIELD_CLEAN);
        writeBaseline(generator, timings.clean());
        generator.writeFieldName(FIELD_POISONED);
        writeBaseline(generator, timings.poisoned());
        generator.writeFieldName(FIELD_COLD);
        writeBaseline(generator, timings.cold());
        if (timings.escalationSeconds() == null) {
            generator.writeNullField(FIELD_ESCALATION_SECONDS);
        } else {
            generator.writeNumberField(FIELD_ESCALATION_SECONDS, timings.escalationSeconds());
        }
        writeInstant(generator, FIELD_LAST_OBSERVED_AT, timings.lastObservedAt());
        generator.writeEndObject();
    }

    private static void writeBaseline(JsonGenerator generator, Baseline baseline) throws IOException {
        generator.writeStartObject();
        if (baseline.learnedSeconds() == null) {
            generator.writeNullField(FIELD_LEARNED_SECONDS);
        } else {
            generator.writeNumberField(FIELD_LEARNED_SECONDS, baseline.learnedSeconds());
        }
        generator.writeNumberField(FIELD_OBSERVATIONS, baseline.observations());
        generator.writeEndObject();
    }

    private static BuildTimings readDocument(JsonParser parser) throws IOException {
        @Nullable Map<String, KeyTimings> keys = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_FORMAT_VERSION -> parser.skipChildren();
                case FIELD_KEYS -> keys = readKeys(parser);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "document", FIELD_KEYS);
        try {
            return new BuildTimings(keys);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static Map<String, KeyTimings> readKeys(JsonParser parser) throws IOException {
        requireObject(parser, FIELD_KEYS);
        var keys = new LinkedHashMap<String, KeyTimings>();
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var key = nextField(parser, seen);
            if (!BuildTimings.BUDGET_KEY.matcher(key).matches()) {
                throw unreadable("'%s' is not a budget key".formatted(key));
            }
            keys.put(key, readKeyTimings(parser, key));
        }
        return keys;
    }

    private static KeyTimings readKeyTimings(JsonParser parser, String key) throws IOException {
        requireObject(parser, key);
        @Nullable Baseline clean = null;
        @Nullable Baseline poisoned = null;
        @Nullable Baseline cold = null;
        @Nullable Long escalationSeconds = null;
        @Nullable Instant lastObservedAt = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_CLEAN -> clean = readBaseline(parser, key + ".clean");
                case FIELD_POISONED -> poisoned = readBaseline(parser, key + ".poisoned");
                case FIELD_COLD -> cold = readBaseline(parser, key + ".cold");
                case FIELD_ESCALATION_SECONDS -> escalationSeconds =
                        parser.currentToken() == JsonToken.VALUE_NULL ? null : integer(parser, field);
                case FIELD_LAST_OBSERVED_AT -> lastObservedAt = nullableInstant(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, key, FIELD_CLEAN, FIELD_POISONED, FIELD_COLD, FIELD_ESCALATION_SECONDS,
                FIELD_LAST_OBSERVED_AT);
        try {
            return new KeyTimings(clean, poisoned, cold, escalationSeconds, lastObservedAt);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static Baseline readBaseline(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        @Nullable Long learnedSeconds = null;
        @Nullable Long observations = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_LEARNED_SECONDS ->
                    learnedSeconds = parser.currentToken() == JsonToken.VALUE_NULL ? null : integer(parser, field);
                case FIELD_OBSERVATIONS -> observations = integer(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, FIELD_LEARNED_SECONDS, FIELD_OBSERVATIONS);
        try {
            return new Baseline(learnedSeconds, Math.toIntExact(observations));
        } catch (IllegalArgumentException | ArithmeticException e) {
            throw unreadable(e.getMessage(), e);
        }
    }
}
