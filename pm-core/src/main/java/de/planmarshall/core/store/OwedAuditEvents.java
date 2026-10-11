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

import static de.planmarshall.core.store.StrictJson.nextField;
import static de.planmarshall.core.store.StrictJson.requireFields;
import static de.planmarshall.core.store.StrictJson.requireObject;
import static de.planmarshall.core.store.StrictJson.string;
import static de.planmarshall.core.store.StrictJson.undeclared;
import static de.planmarshall.core.store.StrictJson.unreadable;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import de.cuioss.tools.logging.CuiLogger;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.log.PmMcpLogMessages.WARN;
import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;

/**
 * The audit records a completed store mutation owes (PM-IMPL-7): how they are kept in a durable intent, the
 * transaction journal or the intent file of a JSONL store, and how they are handed over to the {@link AuditSink}.
 * <p>
 * A record is owed from before the mutation that makes its cause disappear until the sink accepted it; an intent
 * that holds it is written durably first and removed only after the hand-over. The hand-over is at least once: a
 * record handed over again after a failure or a stop carries the same transaction id, by which a reader drops the
 * duplicate.
 * <p>
 * An owed record is the object {@code {"event", "category", "outcome", "details": {<name>: <value>}}}; a detail value
 * is a string, an integer or an array of strings, and the details are written in the sorted order of their names.
 *
 * @since 0.1
 */
@UtilityClass
class OwedAuditEvents {

    private static final CuiLogger LOGGER = new CuiLogger(OwedAuditEvents.class);

    private static final String FIELD_EVENT = "event";
    private static final String FIELD_CATEGORY = "category";
    private static final String FIELD_OUTCOME = "outcome";
    private static final String FIELD_DETAILS = "details";

    /**
     * Hands the owed records over in their order and stops at the first the sink refuses.
     *
     * @param owed   the owed records, in their order
     * @param sink   the audit sink
     * @param intent the durable intent that holds the records, named by the warning of a refusal
     * @return the records that were not handed over, in their order; empty if every one was
     */
    static List<AuditEvent> deliver(List<AuditEvent> owed, AuditSink sink, Path intent) {
        Objects.requireNonNull(sink, "sink");
        for (var index = 0; index < owed.size(); index++) {
            var event = owed.get(index);
            try {
                sink.append(event);
            } catch (UncheckedIOException e) {
                LOGGER.warn(e, WARN.AUDIT_RECORD_DEFERRED, event.event(),
                        event.details().get(AuditEvent.DETAIL_TRANSACTION_ID), intent, e.getMessage());
                return List.copyOf(owed.subList(index, owed.size()));
            }
        }
        return List.of();
    }

    /**
     * @param event a record to owe
     * @return the record
     * @throws IllegalArgumentException if a detail value is not a string, an integer or a list of strings
     */
    static AuditEvent requireWritable(AuditEvent event) {
        Objects.requireNonNull(event, "event");
        for (var detail : event.details().entrySet()) {
            var writable = switch (detail.getValue()) {
                case String _, Long _, Integer _ -> true;
                case List<?> items -> items.stream().allMatch(String.class::isInstance);
                default -> false;
            };
            if (!writable) {
                throw new IllegalArgumentException(
                        "Detail '%s' of an owed record is not a string, an integer or a list of strings: %s"
                                .formatted(detail.getKey(), detail.getValue()));
            }
        }
        return event;
    }

    /**
     * @param generator the generator, positioned where a value is written
     * @param event     the owed record
     * @throws IOException              if the generator cannot write
     * @throws IllegalArgumentException if a detail value is not a string, an integer or a list of strings
     */
    static void write(JsonGenerator generator, AuditEvent event) throws IOException {
        generator.writeStartObject();
        generator.writeStringField(FIELD_EVENT, event.event());
        generator.writeStringField(FIELD_CATEGORY, event.category());
        generator.writeStringField(FIELD_OUTCOME, event.outcome());
        generator.writeObjectFieldStart(FIELD_DETAILS);
        for (var detail : new TreeMap<>(event.details()).entrySet()) {
            writeDetail(generator, detail.getKey(), detail.getValue());
        }
        generator.writeEndObject();
        generator.writeEndObject();
    }

    /**
     * @param parser the parser, positioned on the start of an owed record
     * @return the owed record
     * @throws IOException if the document does not parse as JSON
     */
    static AuditEvent read(JsonParser parser) throws IOException {
        requireObject(parser, "owed event");
        String event = null;
        String category = null;
        String outcome = null;
        Map<String, Object> details = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_EVENT -> event = string(parser, field);
                case FIELD_CATEGORY -> category = string(parser, field);
                case FIELD_OUTCOME -> outcome = string(parser, field);
                case FIELD_DETAILS -> details = readDetails(parser);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "owed event", FIELD_EVENT, FIELD_CATEGORY, FIELD_OUTCOME, FIELD_DETAILS);
        return new AuditEvent(event, category, outcome, details);
    }

    private static void writeDetail(JsonGenerator generator, String name, Object value) throws IOException {
        switch (value) {
            case String text -> generator.writeStringField(name, text);
            case Long number -> generator.writeNumberField(name, number);
            case Integer number -> generator.writeNumberField(name, number);
            case List<?> items -> {
                generator.writeArrayFieldStart(name);
                for (var item : items) {
                    if (!(item instanceof String text)) {
                        throw new IllegalArgumentException(("Detail '%s' of an owed record lists a value that is not "
                                + "a string: %s").formatted(name, item));
                    }
                    generator.writeString(text);
                }
                generator.writeEndArray();
            }
            default -> throw new IllegalArgumentException(
                    "Detail '%s' of an owed record is not a string, an integer or a list of strings: %s"
                            .formatted(name, value));
        }
    }

    private static Map<String, Object> readDetails(JsonParser parser) throws IOException {
        requireObject(parser, FIELD_DETAILS);
        var details = new HashMap<String, Object>();
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var name = nextField(parser, seen);
            details.put(name, switch (parser.currentToken()) {
                case VALUE_STRING -> parser.getText();
                case VALUE_NUMBER_INT -> parser.getLongValue();
                case START_ARRAY -> readStrings(parser, name);
                default -> throw unreadable("detail '%s' is not a string, an integer or an array".formatted(name));
            });
        }
        return details;
    }

    private static List<String> readStrings(JsonParser parser, String name) throws IOException {
        var items = new ArrayList<String>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            items.add(string(parser, name));
        }
        return List.copyOf(items);
    }
}
