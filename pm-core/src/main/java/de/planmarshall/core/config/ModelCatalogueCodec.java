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
import static de.planmarshall.core.store.StrictJson.array;
import static de.planmarshall.core.store.StrictJson.instant;
import static de.planmarshall.core.store.StrictJson.integer;
import static de.planmarshall.core.store.StrictJson.map;
import static de.planmarshall.core.store.StrictJson.nextField;
import static de.planmarshall.core.store.StrictJson.requireFields;
import static de.planmarshall.core.store.StrictJson.requireObject;
import static de.planmarshall.core.store.StrictJson.string;
import static de.planmarshall.core.store.StrictJson.undeclared;
import static de.planmarshall.core.store.StrictJson.unreadable;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.role.ModelCatalogue;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.StrictJson;

/**
 * Reads and writes {@code model-catalogue.json} of the machine or of a project (Store Schemas § Model Catalogue, PM-WORK-1):
 * per enrolled harness the models the plans may use, each with its source and when it was recorded.
 * <p>
 * The document is closed, so a write replaces it from its model: the allowed models per harness and the document's
 * {@code revision}. A harness key must be a client id, and an entry of a harness that is no longer enrolled is dropped by
 * the next write.
 *
 * @since 0.1
 */
@UtilityClass
public class ModelCatalogueCodec {

    /** The format version of the model catalogue. */
    public static final int FORMAT_VERSION = 1;

    private static final Pattern CLIENT_ID = Pattern.compile("[a-z][a-z0-9-]{1,31}");

    private static final String FIELD_REVISION = "revision";
    private static final String FIELD_HARNESSES = "harnesses";
    private static final String FIELD_MODELS = "models";
    private static final String FIELD_MODEL = "model";
    private static final String FIELD_SOURCE = "source";
    private static final String FIELD_RECORDED_AT = "recorded_at";

    /**
     * What a read of the document found.
     *
     * @param harnesses the allowed models per client id of an enrolled harness, in the order of the document
     * @param revision  the {@code revision} of the document
     * @since 0.1
     */
    public record Document(Map<String, List<CatalogueModel>> harnesses, long revision) {

        /** Keeps the map and its lists unmodifiable. */
        public Document {
            harnesses = unmodifiable(harnesses);
        }

        private static Map<String, List<CatalogueModel>> unmodifiable(Map<String, List<CatalogueModel>> harnesses) {
            var copy = new LinkedHashMap<String, List<CatalogueModel>>();
            harnesses.forEach((harness, models) -> copy.put(harness, List.copyOf(new ArrayList<>(models))));
            return Collections.unmodifiableMap(copy);
        }

        /**
         * @return the read projection the role package validates a model against
         */
        public ModelCatalogue catalogue() {
            var allowed = new LinkedHashMap<String, Set<String>>();
            harnesses.forEach((harness, models) -> allowed.put(harness,
                    models.stream().map(CatalogueModel::model).collect(Collectors.toSet())));
            return new ModelCatalogue(allowed);
        }
    }

    /**
     * @param file     the document's file, named by a refusal
     * @param document the document, UTF-8 encoded
     * @return the allowed models and the revision
     * @throws StoreRefusal if the document declares another format version or is unreadable
     */
    public static Document read(Path file, byte[] document) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        return StrictJson.read(file, document, FORMAT_VERSION, ModelCatalogueCodec::document);
    }

    /**
     * @param harnesses the allowed models per harness to write
     * @param revision  the revision to write
     * @return the document, UTF-8 encoded
     */
    public static byte[] write(Map<String, List<CatalogueModel>> harnesses, long revision) {
        Objects.requireNonNull(harnesses, FIELD_HARNESSES);
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
            generator.writeStartObject();
            generator.writeNumberField(FIELD_FORMAT_VERSION, FORMAT_VERSION);
            generator.writeNumberField(FIELD_REVISION, revision);
            generator.writeObjectFieldStart(FIELD_HARNESSES);
            for (var entry : harnesses.entrySet()) {
                requireClientId(entry.getKey());
                generator.writeObjectFieldStart(entry.getKey());
                generator.writeArrayFieldStart(FIELD_MODELS);
                for (var model : entry.getValue()) {
                    generator.writeStartObject();
                    generator.writeStringField(FIELD_MODEL, model.model());
                    generator.writeStringField(FIELD_SOURCE, model.source().wireName());
                    generator.writeStringField(FIELD_RECORDED_AT, model.recordedAt().toString());
                    generator.writeEndObject();
                }
                generator.writeEndArray();
                generator.writeEndObject();
            }
            generator.writeEndObject();
            generator.writeEndObject();
        } catch (IOException e) {
            throw new IllegalStateException("The model catalogue cannot be written", e);
        }
        return out.toByteArray();
    }

    private static Document document(JsonParser parser) throws IOException {
        Map<String, List<CatalogueModel>> harnesses = Map.of();
        var revision = -1L;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_FORMAT_VERSION -> version(parser);
                case FIELD_REVISION -> revision = revision(parser);
                case FIELD_HARNESSES -> harnesses = map(parser, FIELD_HARNESSES, ModelCatalogueCodec::models);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "model catalogue", FIELD_FORMAT_VERSION, FIELD_REVISION, FIELD_HARNESSES);
        return new Document(harnesses, revision);
    }

    private static void version(JsonParser parser) throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT || parser.getIntValue() != FORMAT_VERSION) {
            throw unreadable("'format_version' is not %d".formatted(FORMAT_VERSION));
        }
    }

    private static long revision(JsonParser parser) throws IOException {
        var revision = integer(parser, FIELD_REVISION);
        if (revision < 0) {
            throw unreadable("'revision' must not be negative: " + revision);
        }
        return revision;
    }

    private static List<CatalogueModel> models(JsonParser parser, String harness) throws IOException {
        try {
            requireClientId(harness);
        } catch (IllegalArgumentException e) {
            throw unreadable("not a client id of an enrolled harness: " + harness, e);
        }
        requireObject(parser, harness);
        var seen = new HashSet<String>();
        List<CatalogueModel> models = List.of();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            if (FIELD_MODELS.equals(field)) {
                models = array(parser, field, ModelCatalogueCodec::model);
            } else {
                throw undeclared(field);
            }
        }
        requireFields(seen, "harness catalogue", FIELD_MODELS);
        return models;
    }

    private static CatalogueModel model(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        var seen = new HashSet<String>();
        @Nullable String model = null;
        @Nullable ModelSource source = null;
        @Nullable Instant recordedAt = null;
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_MODEL -> model = string(parser, field);
                case FIELD_SOURCE -> source = source(parser);
                case FIELD_RECORDED_AT -> recordedAt = instant(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "catalogue model", FIELD_MODEL, FIELD_SOURCE, FIELD_RECORDED_AT);
        try {
            return new CatalogueModel(model, source, recordedAt);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw unreadable("%s: %s".formatted(name, e.getMessage()), e);
        }
    }

    private static ModelSource source(JsonParser parser) throws IOException {
        var wireName = string(parser, FIELD_SOURCE);
        return ModelSource.of(wireName).orElseThrow(() -> unreadable("not a model source: " + wireName));
    }

    static void requireClientId(String harness) {
        if (!CLIENT_ID.matcher(harness).matches()) {
            throw new IllegalArgumentException("Not a client id of an enrolled harness: " + harness);
        }
    }
}
