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
import static de.planmarshall.core.store.StrictJson.instant;
import static de.planmarshall.core.store.StrictJson.map;
import static de.planmarshall.core.store.StrictJson.nextField;
import static de.planmarshall.core.store.StrictJson.requireFields;
import static de.planmarshall.core.store.StrictJson.requireObject;
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
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * Reads and writes {@code <PM_MCP_BASE>/explorer.json}, the operator's projects (Store Schemas § The Operator's
 * Projects): the favourites in the operator's order and, per project, the last act and the count of acts per UTC day
 * within the activity window. The document is the closed contract of {@code explorer.schema.json}; a member the schema
 * does not name, a project id outside the identifier grammar, a day that is no date, and a count below one are refused
 * rather than read as an empty store.
 *
 * @since 0.1
 */
@UtilityClass
final class ExplorerCodec {

    private static final String FIELD_REVISION = "revision";
    private static final String FIELD_FAVOURITES = "favourites";
    private static final String FIELD_ACTIVITY = "activity";
    private static final String FIELD_LAST_AT = "last_at";
    private static final String FIELD_DAYS = "days";

    /** The grammar of a counted UTC day, a four-digit year included. */
    private static final Pattern DAY = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    /**
     * @param file     the document's file, named by a refusal
     * @param document the document, UTF-8 encoded
     * @return the snapshot the document holds
     * @throws StoreRefusal if the document declares another format version (outdated or newer), carries content after
     *                      it, or is unreadable
     */
    static ExplorerStore.Snapshot read(Path file, byte[] document) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        return StrictJson.read(file, document, ExplorerStore.FORMAT_VERSION, ExplorerCodec::readDocument);
    }

    private static ExplorerStore.Snapshot readDocument(JsonParser parser) throws IOException {
        requireObject(parser, "explorer");
        long revision = -1;
        List<String> favourites = List.of();
        Map<String, ExplorerStore.Activity> activity = Map.of();
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_FORMAT_VERSION -> requireVersion(parser);
                case FIELD_REVISION -> revision = revision(parser);
                case FIELD_FAVOURITES -> favourites = favourites(parser);
                case FIELD_ACTIVITY -> activity = map(parser, field, ExplorerCodec::activity);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "explorer", FIELD_FORMAT_VERSION, FIELD_REVISION, FIELD_FAVOURITES, FIELD_ACTIVITY);
        return new ExplorerStore.Snapshot(favourites, activity, revision);
    }

    /**
     * @param file     the document's file, named by a refusal
     * @param snapshot the favourites in the operator's order, the activity by project and the revision to write
     * @return the document, UTF-8 encoded; the days of a project and the projects are written in a stable order
     * @throws UncheckedIOException if the document cannot be composed
     */
    static byte[] write(Path file, ExplorerStore.Snapshot snapshot) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(snapshot, "snapshot");
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
            generator.writeStartObject();
            generator.writeNumberField(FIELD_FORMAT_VERSION, ExplorerStore.FORMAT_VERSION);
            generator.writeNumberField(FIELD_REVISION, snapshot.revision());
            generator.writeArrayFieldStart(FIELD_FAVOURITES);
            for (var projectId : snapshot.favourites()) {
                generator.writeString(projectId);
            }
            generator.writeEndArray();
            generator.writeObjectFieldStart(FIELD_ACTIVITY);
            for (var entry : new TreeMap<>(snapshot.activity()).entrySet()) {
                generator.writeObjectFieldStart(entry.getKey());
                writeInstant(generator, FIELD_LAST_AT, entry.getValue().lastAt());
                generator.writeObjectFieldStart(FIELD_DAYS);
                for (var day : new TreeMap<>(entry.getValue().days()).entrySet()) {
                    generator.writeNumberField(day.getKey(), day.getValue());
                }
                generator.writeEndObject();
                generator.writeEndObject();
            }
            generator.writeEndObject();
            generator.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException("The operator's projects cannot be composed: " + file, e);
        }
        return out.toByteArray();
    }

    private static void requireVersion(JsonParser parser) throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT
                || parser.getIntValue() != ExplorerStore.FORMAT_VERSION) {
            throw unreadable("'format_version' is not " + ExplorerStore.FORMAT_VERSION);
        }
    }

    private static long revision(JsonParser parser) throws IOException {
        var revision = integer(parser, FIELD_REVISION);
        if (revision < 0) {
            throw unreadable("'revision' is not negative: " + revision);
        }
        return revision;
    }

    private static List<String> favourites(JsonParser parser) throws IOException {
        var favourites = stringList(parser, FIELD_FAVOURITES);
        var unique = new HashSet<String>();
        for (var projectId : favourites) {
            requireProjectId(projectId, FIELD_FAVOURITES);
            if (!unique.add(projectId)) {
                throw unreadable("'favourites' names a project once: " + projectId);
            }
        }
        return favourites;
    }

    private static ExplorerStore.Activity activity(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        requireProjectId(name, FIELD_ACTIVITY);
        Instant lastAt = null;
        Map<String, Long> days = Map.of();
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_LAST_AT -> lastAt = instant(parser, name + "." + FIELD_LAST_AT);
                case FIELD_DAYS -> days = map(parser, name + "." + FIELD_DAYS, ExplorerCodec::day);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, FIELD_LAST_AT, FIELD_DAYS);
        return new ExplorerStore.Activity(lastAt, days);
    }

    private static Long day(JsonParser parser, String key) throws IOException {
        if (!DAY.matcher(key).matches()) {
            throw unreadable("A day of the activity is a UTC date: " + key);
        }
        try {
            LocalDate.parse(key);
        } catch (DateTimeParseException e) {
            throw unreadable("A day of the activity is a UTC date: " + key, e);
        }
        var count = integer(parser, key);
        if (count < 1) {
            throw unreadable("An act count is one or more, and not '%s': %d".formatted(key, count));
        }
        return count;
    }

    private static void requireProjectId(String projectId, String field) {
        if (!LockKey.isIdentifier(projectId)) {
            throw unreadable("'%s' names a project id outside the grammar: %s".formatted(field, projectId));
        }
    }
}
