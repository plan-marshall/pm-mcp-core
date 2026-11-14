/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.icon;

import static de.planmarshall.core.store.StrictJson.FIELD_FORMAT_VERSION;
import static de.planmarshall.core.store.StrictJson.integer;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.id.HarnessId;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.StrictJson;

/**
 * Reads and writes {@code icons/index.json} (Store Schemas § Harness Icon Store): the closed index of the machine's harness
 * icons, one entry per harness id, beside the files it vouches for.
 * <p>
 * The document is read with the strictness of every store of the runtime — every field at most once, only the declared
 * fields, every required field, each with its one JSON type — and written with its entries in the order of their harness
 * ids, so a read and a write of the same index never change its bytes. A harness id that is not a harness id, and a media
 * type outside the closed set of {@link IconFormat}, are refused as unreadable rather than trusted.
 *
 * @since 0.1
 */
@UtilityClass
public class IconIndexCodec {

    /** The one format version of the icon index. */
    public static final int FORMAT_VERSION = 1;

    private static final String FIELD_ICONS = "icons";
    private static final String FIELD_SOURCE = "source";
    private static final String FIELD_SHA256 = "sha256";
    private static final String FIELD_SIZE = "size";
    private static final String FIELD_MEDIA_TYPE = "media_type";
    private static final String FIELD_FETCHED_AT = "fetched_at";

    /** The form of a refusal that names the harness and the reason. */
    private static final String DETAIL_FORMAT = "%s: %s";

    /**
     * @param file     the index's file, named by a refusal
     * @param document the document, UTF-8 encoded
     * @return the entries by harness id, in the order of the document
     * @throws StoreRefusal if the document declares another format version, is unreadable, or carries an entry the schema
     *                      does not allow
     */
    public static Map<String, IconIndexEntry> read(Path file, byte[] document) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        StrictJson.requireCurrentVersion(file, document, FORMAT_VERSION);
        try (var parser = StrictJson.factory().createParser(document)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw unreadable("the icon index is not a JSON object");
            }
            var seen = new HashSet<String>();
            Map<String, IconIndexEntry> icons = Map.of();
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                var field = nextField(parser, seen);
                if (FIELD_FORMAT_VERSION.equals(field)) {
                    requireFormatVersion(parser);
                } else if (FIELD_ICONS.equals(field)) {
                    icons = StrictJson.map(parser, FIELD_ICONS, IconIndexCodec::entry);
                } else {
                    throw undeclared(field);
                }
            }
            requireFields(seen, "icon index", FIELD_FORMAT_VERSION, FIELD_ICONS);
            if (parser.nextToken() != null) {
                throw unreadable("content after the document");
            }
            return icons;
        } catch (IOException | StrictJson.Malformed e) {
            throw StoreRefusal.unreadable(file, FORMAT_VERSION, String.valueOf(e.getMessage()), e);
        }
    }

    /**
     * @param icons the entries by harness id
     * @return the document, UTF-8 encoded, with its entries in the order of their harness ids
     */
    public static byte[] write(Map<String, IconIndexEntry> icons) {
        Objects.requireNonNull(icons, "icons");
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
            generator.writeStartObject();
            generator.writeNumberField(FIELD_FORMAT_VERSION, FORMAT_VERSION);
            generator.writeObjectFieldStart(FIELD_ICONS);
            for (var entry : new TreeMap<>(icons).entrySet()) {
                writeEntry(generator, entry.getKey(), entry.getValue());
            }
            generator.writeEndObject();
            generator.writeEndObject();
        } catch (IOException e) {
            throw new IllegalStateException("The icon index cannot be written", e);
        }
        return out.toByteArray();
    }

    private static void writeEntry(JsonGenerator generator, String harnessId, IconIndexEntry entry) throws IOException {
        generator.writeObjectFieldStart(harnessId);
        if (entry.source() != null) {
            generator.writeStringField(FIELD_SOURCE, entry.source());
        }
        generator.writeStringField(FIELD_SHA256, entry.sha256());
        generator.writeNumberField(FIELD_SIZE, entry.size());
        generator.writeStringField(FIELD_MEDIA_TYPE, entry.mediaType());
        StrictJson.writeInstant(generator, FIELD_FETCHED_AT, entry.fetchedAt());
        generator.writeEndObject();
    }

    private static void requireFormatVersion(JsonParser parser) throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT || parser.getIntValue() != FORMAT_VERSION) {
            throw unreadable("'format_version' is not %d".formatted(FORMAT_VERSION));
        }
    }

    private static IconIndexEntry entry(JsonParser parser, String harnessId) throws IOException {
        if (!HarnessId.matches(harnessId)) {
            throw unreadable("not a harness id: " + harnessId);
        }
        requireObject(parser, harnessId);
        var seen = new HashSet<String>();
        @Nullable String source = null;
        @Nullable String sha256 = null;
        var size = -1L;
        @Nullable String mediaType = null;
        @Nullable Instant fetchedAt = null;
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_SOURCE -> source = nullableString(parser, FIELD_SOURCE).orElse(null);
                case FIELD_SHA256 -> sha256 = string(parser, FIELD_SHA256);
                case FIELD_SIZE -> size = integer(parser, FIELD_SIZE);
                case FIELD_MEDIA_TYPE -> mediaType = string(parser, FIELD_MEDIA_TYPE);
                case FIELD_FETCHED_AT -> fetchedAt = StrictJson.instant(parser, FIELD_FETCHED_AT);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, harnessId, FIELD_SHA256, FIELD_SIZE, FIELD_MEDIA_TYPE, FIELD_FETCHED_AT);
        try {
            return new IconIndexEntry(source, sha256, size, mediaType, fetchedAt);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw unreadable(DETAIL_FORMAT.formatted(harnessId, e.getMessage()), e);
        }
    }
}
