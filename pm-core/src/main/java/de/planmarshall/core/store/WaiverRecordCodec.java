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
import static de.planmarshall.core.store.StrictJson.requireObject;
import static de.planmarshall.core.store.StrictJson.string;
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
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.store.WaiverRecord.Channel;
import de.planmarshall.core.store.WaiverRecord.Event;
import de.planmarshall.core.store.WaiverRecord.Mode;
import de.planmarshall.core.store.WaiverRecord.Operator;
import de.planmarshall.core.store.WaiverRecord.ReasonClass;

/**
 * Reads and writes one line of the waiver log (PM-IMPL-2): a {@link WaiverRecord} as the JSON object of one line of
 * {@code waivers.jsonl}.
 * <p>
 * The reader accepts exactly one shape through {@link StrictJson}: the {@code format_version} first, then every field
 * at most once, only the declared fields, every required field, one JSON type per field, and the value rules of the
 * record, the conditional members of a waived line and of a raise among them. Whatever does not parse is a
 * {@link StoreRefusal} naming the file; a reader of the log adds the line ({@link WaiverLog}).
 * <p>
 * The writer writes every field in the order of the schema and leaves out a member the record does not hold, so the
 * same record always gives the same bytes.
 *
 * @since 0.1
 */
@UtilityClass
public class WaiverRecordCodec {

    private static final String RECORD = "waiver record";
    private static final String OPERATOR = "operator";

    private static final String FIELD_EVENT = "event";
    private static final String FIELD_WAIVER_ID = "waiver_id";
    private static final String FIELD_GATE = "gate";
    private static final String FIELD_MODE = "mode";
    private static final String FIELD_VALUE = "value";
    private static final String FIELD_REASON = "reason";
    private static final String FIELD_REASON_CLASS = "reason_class";
    private static final String FIELD_REQUEST_ID = "request_id";
    private static final String FIELD_OPERATOR = OPERATOR;
    private static final String FIELD_AT = "at";
    private static final String FIELD_CHANNEL = "channel";
    private static final String FIELD_DEVICE_ID = "device_id";
    private static final String FIELD_OS_PRINCIPAL = "os_principal";

    /**
     * @param waiver a waiver record
     * @return the record as one JSON object, UTF-8 encoded, without a newline
     */
    public static byte[] write(WaiverRecord waiver) {
        Objects.requireNonNull(waiver, "waiver");
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
            write(generator, waiver);
        } catch (IOException e) {
            throw new UncheckedIOException("Waiver record cannot be written", e);
        }
        return out.toByteArray();
    }

    /**
     * @param file     the file the record was read from, named by a refusal
     * @param document one record, UTF-8 encoded
     * @return the record
     * @throws StoreRefusal if the record declares another format version or does not have the one shape of a waiver
     *                      record
     */
    public static WaiverRecord read(Path file, byte[] document) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        return StrictJson.read(file, document, WaiverRecord.FORMAT_VERSION, WaiverRecordCodec::readObject);
    }

    private static void write(JsonGenerator generator, WaiverRecord waiver) throws IOException {
        generator.writeStartObject();
        generator.writeNumberField(FIELD_FORMAT_VERSION, WaiverRecord.FORMAT_VERSION);
        generator.writeStringField(FIELD_EVENT, waiver.event().wireName());
        generator.writeStringField(FIELD_WAIVER_ID, waiver.waiverId());
        writeIfPresent(generator, FIELD_GATE, waiver.gate());
        writeIfPresent(generator, FIELD_MODE, waiver.mode() == null ? null : waiver.mode().wireName());
        if (waiver.value() != null) {
            generator.writeNumberField(FIELD_VALUE, waiver.value());
        }
        generator.writeStringField(FIELD_REASON, waiver.reason());
        writeIfPresent(generator, FIELD_REASON_CLASS,
                waiver.reasonClass() == null ? null : waiver.reasonClass().wireName());
        writeIfPresent(generator, FIELD_REQUEST_ID, waiver.requestId());
        generator.writeObjectFieldStart(FIELD_OPERATOR);
        generator.writeStringField(FIELD_CHANNEL, waiver.operator().channel().wireName());
        writeIfPresent(generator, FIELD_DEVICE_ID, waiver.operator().deviceId());
        generator.writeStringField(FIELD_OS_PRINCIPAL, waiver.operator().osPrincipal());
        generator.writeEndObject();
        writeInstant(generator, FIELD_AT, waiver.at());
        generator.writeEndObject();
    }

    private static WaiverRecord readObject(JsonParser parser) throws IOException {
        requireObject(parser, RECORD);
        var waiver = WaiverRecord.builder();
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_FORMAT_VERSION -> integer(parser, field);
                case FIELD_EVENT -> waiver.event(enumValue(string(parser, field), Event::fromWireName, field));
                case FIELD_WAIVER_ID -> waiver.waiverId(string(parser, field));
                case FIELD_GATE -> waiver.gate(string(parser, field));
                case FIELD_MODE -> waiver.mode(enumValue(string(parser, field), Mode::fromWireName, field));
                case FIELD_VALUE -> waiver.value(nullableInteger(parser, field));
                case FIELD_REASON -> waiver.reason(string(parser, field));
                case FIELD_REASON_CLASS -> waiver.reasonClass(
                        enumValue(string(parser, field), ReasonClass::fromWireName, field));
                case FIELD_REQUEST_ID -> waiver.requestId(nullableString(parser, field));
                case FIELD_OPERATOR -> waiver.operator(readOperator(parser));
                case FIELD_AT -> waiver.at(instant(parser, field));
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, RECORD, FIELD_FORMAT_VERSION, FIELD_EVENT, FIELD_WAIVER_ID, FIELD_REASON, FIELD_OPERATOR,
                FIELD_AT);
        try {
            return waiver.build();
        } catch (IllegalArgumentException e) {
            throw unreadable("%s: %s".formatted(RECORD, e.getMessage()), e);
        }
    }

    private static Operator readOperator(JsonParser parser) throws IOException {
        requireObject(parser, OPERATOR);
        Channel channel = null;
        String deviceId = null;
        String osPrincipal = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_CHANNEL -> channel = enumValue(string(parser, field), Channel::fromWireName, field);
                case FIELD_DEVICE_ID -> deviceId = nullableString(parser, field);
                case FIELD_OS_PRINCIPAL -> osPrincipal = string(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, OPERATOR, FIELD_CHANNEL, FIELD_OS_PRINCIPAL);
        try {
            return new Operator(channel, deviceId, osPrincipal);
        } catch (IllegalArgumentException e) {
            throw unreadable("%s: %s".formatted(OPERATOR, e.getMessage()), e);
        }
    }

    private static Long nullableInteger(JsonParser parser, String field) throws IOException {
        return parser.currentToken() == JsonToken.VALUE_NULL ? null : integer(parser, field);
    }

    private static void writeIfPresent(JsonGenerator generator, String field, String value) throws IOException {
        if (value != null) {
            generator.writeStringField(field, value);
        }
    }

    /**
     * @param text       the name of a value in a record
     * @param byWireName finds the value of a name
     * @param field      the field the value was read from, named by a refusal
     * @param <E>        the enum
     * @return the value of that name
     */
    static <E extends Enum<E>> E enumValue(String text, Function<String, Optional<E>> byWireName, String field) {
        return byWireName.apply(text)
                .orElseThrow(() -> unreadable("'%s' has the undeclared value '%s'".formatted(field, text)));
    }
}
