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
import static de.planmarshall.core.store.StrictJson.requireFields;
import static de.planmarshall.core.store.StrictJson.requireObject;
import static de.planmarshall.core.store.StrictJson.string;
import static de.planmarshall.core.store.StrictJson.stringList;
import static de.planmarshall.core.store.StrictJson.undeclared;
import static de.planmarshall.core.store.StrictJson.unreadable;
import static de.planmarshall.core.store.StrictJson.writeInstant;

import com.fasterxml.jackson.core.JsonEncoding;
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
import lombok.experimental.UtilityClass;

/**
 * Reads and writes one line of the waiver consumption log (PM-IMPL-2): a {@link WaiverConsumptionRecord} as the JSON
 * object of one line of {@code waiver-consumptions.jsonl}.
 * <p>
 * The reader accepts exactly one shape through {@link StrictJson}: the {@code format_version} first, then every field
 * exactly once, only the declared fields, one JSON type per field, and the value rules of the record. Whatever does not
 * parse is a {@link StoreRefusal} naming the file; a reader of the log adds the line ({@link WaiverConsumptionLog}).
 * <p>
 * The writer writes every field in the order of the schema, so the same record always gives the same bytes.
 *
 * @since 0.1
 */
@UtilityClass
public class WaiverConsumptionCodec {

    private static final String RECORD = "waiver consumption";

    private static final String FIELD_WAIVER_ID = "waiver_id";
    private static final String FIELD_GATE = "gate";
    private static final String FIELD_AT = "at";
    private static final String FIELD_STATE_ID = "state_id";
    private static final String FIELD_HEAD_SHA = "head_sha";
    private static final String FIELD_COVERED = "covered";

    /**
     * @param consumption a waiver consumption
     * @return the record as one JSON object, UTF-8 encoded, without a newline
     */
    public static byte[] write(WaiverConsumptionRecord consumption) {
        Objects.requireNonNull(consumption, "consumption");
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
            generator.writeStartObject();
            generator.writeNumberField(FIELD_FORMAT_VERSION, WaiverConsumptionRecord.FORMAT_VERSION);
            generator.writeStringField(FIELD_WAIVER_ID, consumption.waiverId());
            generator.writeStringField(FIELD_GATE, consumption.gate());
            writeInstant(generator, FIELD_AT, consumption.at());
            generator.writeStringField(FIELD_STATE_ID, consumption.stateId());
            generator.writeStringField(FIELD_HEAD_SHA, consumption.headSha());
            generator.writeArrayFieldStart(FIELD_COVERED);
            for (var entry : consumption.covered()) {
                generator.writeString(entry);
            }
            generator.writeEndArray();
            generator.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException("Waiver consumption cannot be written", e);
        }
        return out.toByteArray();
    }

    /**
     * @param file     the file the record was read from, named by a refusal
     * @param document one record, UTF-8 encoded
     * @return the record
     * @throws StoreRefusal if the record declares another format version or does not have the one shape of a waiver
     *                      consumption
     */
    public static WaiverConsumptionRecord read(Path file, byte[] document) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        return StrictJson.read(file, document, WaiverConsumptionRecord.FORMAT_VERSION,
                WaiverConsumptionCodec::readObject);
    }

    private static WaiverConsumptionRecord readObject(JsonParser parser) throws IOException {
        requireObject(parser, RECORD);
        String waiverId = null;
        String gate = null;
        Instant at = null;
        String stateId = null;
        String headSha = null;
        List<String> covered = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_FORMAT_VERSION -> integer(parser, field);
                case FIELD_WAIVER_ID -> waiverId = string(parser, field);
                case FIELD_GATE -> gate = string(parser, field);
                case FIELD_AT -> at = instant(parser, field);
                case FIELD_STATE_ID -> stateId = string(parser, field);
                case FIELD_HEAD_SHA -> headSha = string(parser, field);
                case FIELD_COVERED -> covered = stringList(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, RECORD, FIELD_FORMAT_VERSION, FIELD_WAIVER_ID, FIELD_GATE, FIELD_AT, FIELD_STATE_ID,
                FIELD_HEAD_SHA, FIELD_COVERED);
        try {
            return new WaiverConsumptionRecord(waiverId, gate, at, stateId, headSha, covered);
        } catch (IllegalArgumentException e) {
            throw unreadable("%s: %s".formatted(RECORD, e.getMessage()), e);
        }
    }
}
