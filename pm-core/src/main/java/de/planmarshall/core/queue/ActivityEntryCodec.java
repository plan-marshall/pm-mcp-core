/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.queue;

import static de.planmarshall.core.store.StrictJson.FIELD_FORMAT_VERSION;
import static de.planmarshall.core.store.StrictJson.bool;
import static de.planmarshall.core.store.StrictJson.instant;
import static de.planmarshall.core.store.StrictJson.integer;
import static de.planmarshall.core.store.StrictJson.nextField;
import static de.planmarshall.core.store.StrictJson.nullableString;
import static de.planmarshall.core.store.StrictJson.requireFields;
import static de.planmarshall.core.store.StrictJson.requireObject;
import static de.planmarshall.core.store.StrictJson.string;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.queue.ActivityEntry.Kind;
import de.planmarshall.core.queue.ActivityEntry.Link;
import de.planmarshall.core.queue.ActivityEntry.LinkRelation;
import de.planmarshall.core.queue.ActivityEntry.Result;
import de.planmarshall.core.queue.ActivityEntry.Signal;
import de.planmarshall.core.queue.ActivityEntry.Source;
import de.planmarshall.core.queue.ActivityEntry.StopReason;
import de.planmarshall.core.queue.ActivityEntry.Tool;
import de.planmarshall.core.queue.QueueTaskRecord.SignalAction;
import de.planmarshall.core.queue.QueueTaskRecord.SignalName;
import de.planmarshall.core.store.StrictJson;

/**
 * Reads and writes one activity entry (Store Schemas § Activity Journal), the JSON object of one line of
 * {@code tasks/activity/<queue_task_id>.jsonl}.
 * <p>
 * The reader accepts exactly one shape through {@code StrictJson}. A {@code usage} object is kept as the raw JSON text
 * it was read with and written back unchanged. Whatever does not parse is a refusal naming the file.
 *
 * @since 0.1
 */
@UtilityClass
public class ActivityEntryCodec {

    private static final String ENTRY = "activity entry";
    private static final String REFUSED_VALUE = "%s: %s";

    private static final String FIELD_SEQ = "seq";
    private static final String FIELD_ATTEMPT = "attempt";
    private static final String FIELD_SOURCE = "source";
    private static final String FIELD_KIND = "kind";
    private static final String FIELD_TEXT = "text";
    private static final String FIELD_TRUNCATED = "truncated";
    private static final String FIELD_TOOL = "tool";
    private static final String FIELD_RESULT = "result";
    private static final String FIELD_PATH = "path";
    private static final String FIELD_STOP_REASON = "stop_reason";
    private static final String FIELD_USAGE = "usage";
    private static final String FIELD_LINK = "link";
    private static final String FIELD_QUESTION_ID = "question_id";
    private static final String FIELD_SIGNAL = "signal";
    private static final String FIELD_END = "end";
    private static final String FIELD_UNMAPPED_LINES = "unmapped_lines";
    private static final String FIELD_AT = "at";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_TARGET = "target";
    private static final String FIELD_BYTES = "bytes";
    private static final String FIELD_FAILED = "failed";
    private static final String FIELD_REL = "rel";
    private static final String FIELD_ACTION = "action";

    /** The members of the entry in the order of the schema. */
    static final List<String> FIELDS = List.of(FIELD_FORMAT_VERSION, FIELD_SEQ, FIELD_ATTEMPT, FIELD_SOURCE,
            FIELD_KIND, FIELD_TEXT, FIELD_TRUNCATED, FIELD_TOOL, FIELD_RESULT, FIELD_PATH, FIELD_STOP_REASON,
            FIELD_USAGE, FIELD_LINK, FIELD_QUESTION_ID, FIELD_SIGNAL, FIELD_END, FIELD_UNMAPPED_LINES, FIELD_AT);

    /** The members every entry requires, in the order of the schema; {@code format_version} is checked first. */
    static final List<String> REQUIRED = List.of(FIELD_FORMAT_VERSION, FIELD_SEQ, FIELD_ATTEMPT, FIELD_SOURCE,
            FIELD_KIND, FIELD_AT);

    /**
     * @param entry an activity entry
     * @return the entry as one JSON object, UTF-8 encoded, without a newline
     */
    public static byte[] write(ActivityEntry entry) {
        Objects.requireNonNull(entry, "entry");
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
            write(generator, entry);
        } catch (IOException e) {
            throw new UncheckedIOException("Activity entry cannot be written", e);
        }
        return out.toByteArray();
    }

    /**
     * @param file     the file the entry was read from, named by a refusal
     * @param document one entry, UTF-8 encoded
     * @return the entry
     * @throws de.planmarshall.core.store.StoreRefusal if the entry declares another format version or does not have
     *                                                the one shape of an activity entry
     */
    public static ActivityEntry read(Path file, byte[] document) {
        return StrictJson.read(file, document, ActivityEntry.FORMAT_VERSION,
                parser -> readObject(parser, document));
    }

    private static void write(JsonGenerator generator, ActivityEntry entry) throws IOException {
        generator.writeStartObject();
        generator.writeNumberField(FIELD_FORMAT_VERSION, ActivityEntry.FORMAT_VERSION);
        generator.writeNumberField(FIELD_SEQ, entry.seq());
        generator.writeNumberField(FIELD_ATTEMPT, entry.attempt());
        generator.writeStringField(FIELD_SOURCE, entry.source().wireName());
        generator.writeStringField(FIELD_KIND, entry.kind().wireName());
        writeNullable(generator, FIELD_TEXT, entry.text());
        generator.writeBooleanField(FIELD_TRUNCATED, entry.truncated());
        if (entry.tool() == null) {
            generator.writeNullField(FIELD_TOOL);
        } else {
            generator.writeObjectFieldStart(FIELD_TOOL);
            generator.writeStringField(FIELD_NAME, entry.tool().name());
            writeNullable(generator, FIELD_TARGET, entry.tool().target());
            generator.writeEndObject();
        }
        if (entry.result() == null) {
            generator.writeNullField(FIELD_RESULT);
        } else {
            generator.writeObjectFieldStart(FIELD_RESULT);
            generator.writeNumberField(FIELD_BYTES, entry.result().bytes());
            generator.writeBooleanField(FIELD_FAILED, entry.result().failed());
            generator.writeEndObject();
        }
        writeNullable(generator, FIELD_PATH, entry.path());
        writeNullable(generator, FIELD_STOP_REASON,
                entry.stopReason() == null ? null : entry.stopReason().wireName());
        if (entry.usage() == null) {
            generator.writeNullField(FIELD_USAGE);
        } else {
            generator.writeFieldName(FIELD_USAGE);
            generator.writeRawValue(entry.usage());
        }
        if (entry.link() == null) {
            generator.writeNullField(FIELD_LINK);
        } else {
            generator.writeObjectFieldStart(FIELD_LINK);
            generator.writeStringField(FIELD_REL, entry.link().rel().wireName());
            writeNullable(generator, FIELD_TARGET, entry.link().target());
            generator.writeEndObject();
        }
        writeNullable(generator, FIELD_QUESTION_ID, entry.questionId());
        if (entry.signal() == null) {
            generator.writeNullField(FIELD_SIGNAL);
        } else {
            generator.writeObjectFieldStart(FIELD_SIGNAL);
            generator.writeStringField(FIELD_SIGNAL, entry.signal().signal().wireName());
            generator.writeStringField(FIELD_ACTION, entry.signal().action().wireName());
            generator.writeEndObject();
        }
        writeNullable(generator, FIELD_END, entry.end());
        if (entry.unmappedLines() == null) {
            generator.writeNullField(FIELD_UNMAPPED_LINES);
        } else {
            generator.writeNumberField(FIELD_UNMAPPED_LINES, entry.unmappedLines());
        }
        writeInstant(generator, FIELD_AT, entry.at());
        generator.writeEndObject();
    }

    private static void writeNullable(JsonGenerator generator, String field, @Nullable String value) throws IOException {
        if (value == null) {
            generator.writeNullField(field);
        } else {
            generator.writeStringField(field, value);
        }
    }

    @SuppressWarnings("java:S3776") // one case per schema field, in schema order, by design
    private static ActivityEntry readObject(JsonParser parser, byte[] document) throws IOException {
        requireObject(parser, ENTRY);
        var builder = ActivityEntry.builder();
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_FORMAT_VERSION -> integer(parser, field);
                case FIELD_SEQ -> builder.seq(Math.toIntExact(integer(parser, field)));
                case FIELD_ATTEMPT -> builder.attempt(Math.toIntExact(integer(parser, field)));
                case FIELD_SOURCE -> builder.source(closed(parser, field, Source::fromWireName));
                case FIELD_KIND -> builder.kind(closed(parser, field, Kind::fromWireName));
                case FIELD_TEXT -> builder.text(nullableString(parser, field));
                case FIELD_TRUNCATED -> builder.truncated(bool(parser, field));
                case FIELD_TOOL -> builder.tool(parser.currentToken() == JsonToken.VALUE_NULL ? null
                        : readTool(parser));
                case FIELD_RESULT -> builder.result(parser.currentToken() == JsonToken.VALUE_NULL ? null
                        : readResult(parser));
                case FIELD_PATH -> builder.path(nullableString(parser, field));
                case FIELD_STOP_REASON -> builder.stopReason(parser.currentToken() == JsonToken.VALUE_NULL ? null
                        : closed(parser, field, StopReason::fromWireName));
                case FIELD_USAGE -> builder.usage(parser.currentToken() == JsonToken.VALUE_NULL ? null
                        : raw(parser, document));
                case FIELD_LINK -> builder.link(parser.currentToken() == JsonToken.VALUE_NULL ? null
                        : readLink(parser));
                case FIELD_QUESTION_ID -> builder.questionId(nullableString(parser, field));
                case FIELD_SIGNAL -> builder.signal(parser.currentToken() == JsonToken.VALUE_NULL ? null
                        : readSignal(parser));
                case FIELD_END -> builder.end(nullableString(parser, field));
                case FIELD_UNMAPPED_LINES -> builder.unmappedLines(parser.currentToken() == JsonToken.VALUE_NULL
                        ? null : Math.toIntExact(integer(parser, field)));
                case FIELD_AT -> builder.at(instant(parser, field));
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, ENTRY, FIELD_FORMAT_VERSION);
        requireFields(seen, ENTRY, REQUIRED.toArray(String[]::new));
        try {
            return builder.build();
        } catch (IllegalArgumentException e) {
            throw unreadable(REFUSED_VALUE.formatted(ENTRY, e.getMessage()), e);
        }
    }

    private static Tool readTool(JsonParser parser) throws IOException {
        requireObject(parser, FIELD_TOOL);
        @Nullable String name = null;
        @Nullable String target = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_NAME -> name = string(parser, field);
                case FIELD_TARGET -> target = nullableString(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, FIELD_TOOL, FIELD_NAME, FIELD_TARGET);
        try {
            return new Tool(name, target);
        } catch (IllegalArgumentException e) {
            throw unreadable(REFUSED_VALUE.formatted(FIELD_TOOL, e.getMessage()), e);
        }
    }

    private static Result readResult(JsonParser parser) throws IOException {
        requireObject(parser, FIELD_RESULT);
        var bytes = 0;
        var failed = false;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_BYTES -> bytes = Math.toIntExact(integer(parser, field));
                case FIELD_FAILED -> failed = bool(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, FIELD_RESULT, FIELD_BYTES, FIELD_FAILED);
        try {
            return new Result(bytes, failed);
        } catch (IllegalArgumentException e) {
            throw unreadable(REFUSED_VALUE.formatted(FIELD_RESULT, e.getMessage()), e);
        }
    }

    private static Link readLink(JsonParser parser) throws IOException {
        requireObject(parser, FIELD_LINK);
        @Nullable LinkRelation rel = null;
        @Nullable String target = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_REL -> rel = closed(parser, field, LinkRelation::fromWireName);
                case FIELD_TARGET -> target = nullableString(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, FIELD_LINK, FIELD_REL, FIELD_TARGET);
        try {
            return new Link(rel, target);
        } catch (IllegalArgumentException e) {
            throw unreadable(REFUSED_VALUE.formatted(FIELD_LINK, e.getMessage()), e);
        }
    }

    private static Signal readSignal(JsonParser parser) throws IOException {
        requireObject(parser, FIELD_SIGNAL);
        @Nullable SignalName signal = null;
        @Nullable SignalAction action = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_SIGNAL -> signal = closed(parser, field, SignalName::fromWireName);
                case FIELD_ACTION -> action = closed(parser, field, SignalAction::fromWireName);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "signal entry", FIELD_SIGNAL, FIELD_ACTION);
        try {
            return new Signal(signal, action);
        } catch (IllegalArgumentException e) {
            throw unreadable(REFUSED_VALUE.formatted(FIELD_SIGNAL, e.getMessage()), e);
        }
    }

    private static <E> E closed(JsonParser parser, String field, Function<String, Optional<E>> values)
            throws IOException {
        var name = string(parser, field);
        return values.apply(name).orElseThrow(() -> unreadable("'%s' has the unknown value '%s'".formatted(field,
                name)));
    }

    private static String raw(JsonParser parser, byte[] document) throws IOException {
        requireObject(parser, FIELD_USAGE);
        var start = parser.currentTokenLocation().getByteOffset();
        parser.skipChildren();
        parser.finishToken();
        var end = parser.currentLocation().getByteOffset();
        return new String(document, Math.toIntExact(start), Math.toIntExact(end - start), StandardCharsets.UTF_8)
                .strip();
    }
}
