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
import static de.planmarshall.core.store.StrictJson.instant;
import static de.planmarshall.core.store.StrictJson.integer;
import static de.planmarshall.core.store.StrictJson.nextField;
import static de.planmarshall.core.store.StrictJson.nullableString;
import static de.planmarshall.core.store.StrictJson.requireFields;
import static de.planmarshall.core.store.StrictJson.string;
import static de.planmarshall.core.store.StrictJson.stringList;
import static de.planmarshall.core.store.StrictJson.undeclared;
import static de.planmarshall.core.store.StrictJson.unreadable;
import static de.planmarshall.core.store.StrictJson.writeInstant;

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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.store.PlanHistoryLine.Event;

/**
 * Reads and writes one line of a plan's history {@code mcp/history.jsonl} (Store Schemas § The Plan's History).
 * <p>
 * One line is one JSON object; the codec reads exactly the declared members, each at most once, and writes every one of
 * them, {@code null} where the line carries no value, so a line this codec wrote is read back to the same record. The
 * file format version is checked by the append-only store before the line is read.
 *
 * @since 0.1
 */
@UtilityClass
public class PlanHistoryCodec {

    private static final String FIELD_SEQ = "seq";
    private static final String FIELD_EVENT = "event";
    private static final String FIELD_NODE = "node";
    private static final String FIELD_CALL_PATH = "call_path";
    private static final String FIELD_PHASE = "phase";
    private static final String FIELD_OUTCOME = "outcome";
    private static final String FIELD_INSTANCE_VERSION = "instance_version";
    private static final String FIELD_AT = "at";

    /**
     * @param line the history line
     * @return the line as one JSON object, UTF-8 encoded, without a newline
     */
    public static byte[] write(PlanHistoryLine line) {
        Objects.requireNonNull(line, "line");
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
            generator.writeStartObject();
            generator.writeNumberField(FIELD_FORMAT_VERSION, PlanHistoryLine.FORMAT_VERSION);
            generator.writeNumberField(FIELD_SEQ, line.seq());
            generator.writeStringField(FIELD_EVENT, line.event().wireName());
            generator.writeStringField(FIELD_NODE, line.node());
            generator.writeArrayFieldStart(FIELD_CALL_PATH);
            for (var frame : line.callPath()) {
                generator.writeString(frame);
            }
            generator.writeEndArray();
            writeNullableString(generator, FIELD_PHASE, line.phase());
            writeNullableString(generator, FIELD_OUTCOME, line.outcome());
            generator.writeNumberField(FIELD_INSTANCE_VERSION, line.instanceVersion());
            writeInstant(generator, FIELD_AT, line.at());
            generator.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException("Plan history line cannot be written", e);
        }
        return out.toByteArray();
    }

    /**
     * @param file     the history file the line was read from, named by a refusal
     * @param document the bytes of one line, UTF-8 encoded
     * @return the line
     * @throws StoreRefusal if the line is unreadable
     */
    public static PlanHistoryLine read(Path file, byte[] document) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        return StrictJson.read(file, document, PlanHistoryLine.FORMAT_VERSION, PlanHistoryCodec::readLine);
    }

    private static void writeNullableString(JsonGenerator generator, String field, String value) throws IOException {
        if (value == null) {
            generator.writeNullField(field);
        } else {
            generator.writeStringField(field, value);
        }
    }

    private static PlanHistoryLine readLine(JsonParser parser) throws IOException {
        Long seq = null;
        Event event = null;
        String node = null;
        List<String> callPath = List.of();
        String phase = null;
        String outcome = null;
        Long instanceVersion = null;
        Instant at = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_FORMAT_VERSION -> parser.skipChildren();
                case FIELD_SEQ -> seq = integer(parser, field);
                case FIELD_EVENT -> event = enumValue(parser, field, name -> {
                    try {
                        return Optional.of(Event.ofWireName(name));
                    } catch (IllegalArgumentException _) {
                        return Optional.empty();
                    }
                });
                case FIELD_NODE -> node = string(parser, field);
                case FIELD_CALL_PATH -> callPath = stringList(parser, field);
                case FIELD_PHASE -> phase = nullableString(parser, field);
                case FIELD_OUTCOME -> outcome = nullableString(parser, field);
                case FIELD_INSTANCE_VERSION -> instanceVersion = integer(parser, field);
                case FIELD_AT -> at = instant(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "plan history line", FIELD_SEQ, FIELD_EVENT, FIELD_NODE, FIELD_CALL_PATH,
                FIELD_PHASE, FIELD_OUTCOME, FIELD_INSTANCE_VERSION, FIELD_AT);
        try {
            if (instanceVersion < 1 || instanceVersion > Integer.MAX_VALUE) {
                throw unreadable("'%s' is not an instance version: %d".formatted(FIELD_INSTANCE_VERSION,
                        instanceVersion));
            }
            return PlanHistoryLine.of(seq, event, node, callPath, phase, outcome, Math.toIntExact(instanceVersion),
                    at);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static <T> T enumValue(JsonParser parser, String field, Function<String, Optional<T>> of)
            throws IOException {
        var name = string(parser, field);
        return of.apply(name)
                .orElseThrow(() -> unreadable("'%s' has the undeclared value '%s'".formatted(field, name)));
    }
}
