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
import static de.planmarshall.core.store.StrictJson.nullableInstant;
import static de.planmarshall.core.store.StrictJson.requireFields;
import static de.planmarshall.core.store.StrictJson.requireObject;
import static de.planmarshall.core.store.StrictJson.string;
import static de.planmarshall.core.store.StrictJson.undeclared;
import static de.planmarshall.core.store.StrictJson.unreadable;
import static de.planmarshall.core.store.StrictJson.writeInstant;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import lombok.experimental.UtilityClass;

/**
 * Reads and writes the identity record {@code store.json} of a per-project machine store (PM-IMPL-2): a
 * {@link ProjectStoreIdentity} as one JSON object.
 * <p>
 * The reader accepts exactly one shape through {@link StrictJson}: the {@code format_version} first, then every field
 * at most once, only the declared fields, every field of the schema (the unenrolment as an instant or {@code null}),
 * one JSON type per field, and the value rules of the record. Whatever does not parse is a {@link StoreRefusal}
 * naming the file.
 * <p>
 * The writer writes every field in the order of the schema, the unenrolment as {@code null} while the project is
 * enrolled, so the same record always gives the same bytes.
 *
 * @since 0.1
 */
@UtilityClass
public class ProjectStoreIdentityCodec {

    private static final String RECORD = "store identity";

    private static final String FIELD_PROJECT_ID = "project_id";
    private static final String FIELD_PROJECT_ROOT = "project_root";
    private static final String FIELD_DISPLAY_NAME = "display_name";
    private static final String FIELD_CREATED_AT = "created_at";
    private static final String FIELD_UNENROLLED_AT = "unenrolled_at";

    /**
     * @param identity a store identity
     * @return the record as one JSON object, UTF-8 encoded
     */
    public static byte[] write(ProjectStoreIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
            generator.writeStartObject();
            generator.writeNumberField(FIELD_FORMAT_VERSION, ProjectStoreIdentity.FORMAT_VERSION);
            generator.writeStringField(FIELD_PROJECT_ID, identity.projectId());
            generator.writeStringField(FIELD_PROJECT_ROOT, identity.projectRoot());
            generator.writeStringField(FIELD_DISPLAY_NAME, identity.displayName());
            writeInstant(generator, FIELD_CREATED_AT, identity.createdAt());
            writeInstant(generator, FIELD_UNENROLLED_AT, identity.unenrolledAt());
            generator.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException("Store identity cannot be written", e);
        }
        return out.toByteArray();
    }

    /**
     * @param file     the file the record was read from, named by a refusal
     * @param document the record, UTF-8 encoded
     * @return the record
     * @throws StoreRefusal if the record declares another format version or does not have the one shape of a store
     *                      identity
     */
    public static ProjectStoreIdentity read(Path file, byte[] document) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        return StrictJson.read(file, document, ProjectStoreIdentity.FORMAT_VERSION,
                ProjectStoreIdentityCodec::readObject);
    }

    private static ProjectStoreIdentity readObject(JsonParser parser) throws IOException {
        requireObject(parser, RECORD);
        @Nullable String projectId = null;
        @Nullable String projectRoot = null;
        @Nullable String displayName = null;
        @Nullable Instant createdAt = null;
        @Nullable Instant unenrolledAt = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_FORMAT_VERSION -> integer(parser, field);
                case FIELD_PROJECT_ID -> projectId = string(parser, field);
                case FIELD_PROJECT_ROOT -> projectRoot = string(parser, field);
                case FIELD_DISPLAY_NAME -> displayName = string(parser, field);
                case FIELD_CREATED_AT -> createdAt = instant(parser, field);
                case FIELD_UNENROLLED_AT -> unenrolledAt = nullableInstant(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, RECORD, FIELD_FORMAT_VERSION, FIELD_PROJECT_ID, FIELD_PROJECT_ROOT, FIELD_DISPLAY_NAME,
                FIELD_CREATED_AT, FIELD_UNENROLLED_AT);
        try {
            return new ProjectStoreIdentity(projectId, projectRoot, displayName, createdAt, unenrolledAt);
        } catch (IllegalArgumentException e) {
            throw unreadable("%s: %s".formatted(RECORD, e.getMessage()), e);
        }
    }
}
