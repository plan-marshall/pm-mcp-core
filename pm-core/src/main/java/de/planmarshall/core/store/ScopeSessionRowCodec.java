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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.store.ScopeSessionRow.Client;
import de.planmarshall.core.store.ScopeSessionRow.EnteredFrom;
import de.planmarshall.core.store.ScopeSessionRow.EnvironmentMatchTier;
import de.planmarshall.core.store.ScopeSessionRow.ModelFamily;
import de.planmarshall.core.store.ScopeSessionRow.ModelSource;

/**
 * Reads and writes one entry row of the scope session log (PM-IMPL-2): a {@link ScopeSessionRow} as the JSON object of
 * one line of {@code sessions.jsonl}, or as the object a pending entry record embeds as its {@code session}.
 * <p>
 * The reader accepts exactly one shape through {@link StrictJson}: the {@code format_version} first, then every field
 * at most once, only the declared fields, every required field, one JSON type per field, and the value rules of the
 * row. {@code worktree_of} is part of the shape only for an entry from a worktree, where it is required. Whatever does
 * not parse is a {@link StoreRefusal} naming the file; a reader of the log adds the line ({@link ScopeSessionLog}).
 * <p>
 * The writer writes every field in the order of the schema, so the same row always gives the same bytes.
 *
 * @since 0.1
 */
@UtilityClass
public class ScopeSessionRowCodec {

    private static final String FIELD_CLIENT_SESSION_ID = "client_session_id";
    private static final String FIELD_CLIENT = "client";
    private static final String FIELD_CLIENT_DETECTED = "client_detected";
    private static final String FIELD_ENVIRONMENT_MATCH_TIER = "environment_match_tier";
    private static final String FIELD_CLIENT_BUILD = "client_build";
    private static final String FIELD_MODEL_FAMILY = "model_family";
    private static final String FIELD_MODEL_ID = "model_id";
    private static final String FIELD_MODEL_SOURCE = "model_source";
    private static final String FIELD_PM_MCP_VERSION = "pm_mcp_version";
    private static final String FIELD_BUNDLE_VERSION = "bundle_version";
    private static final String FIELD_PROJECT_ID = "project_id";
    private static final String FIELD_ENTERED_FROM = "entered_from";
    private static final String FIELD_WORKTREE_OF = "worktree_of";
    private static final String FIELD_GENERATION_ID = "generation_id";
    private static final String FIELD_ENTERED_AT = "entered_at";

    /** The fields every row has, in the order of the schema; {@code worktree_of} is not among them. */
    static final List<String> REQUIRED_FIELDS = List.of(FIELD_FORMAT_VERSION, FIELD_CLIENT_SESSION_ID, FIELD_CLIENT,
            FIELD_CLIENT_DETECTED, FIELD_ENVIRONMENT_MATCH_TIER, FIELD_CLIENT_BUILD, FIELD_MODEL_FAMILY,
            FIELD_MODEL_ID, FIELD_MODEL_SOURCE, FIELD_PM_MCP_VERSION, FIELD_BUNDLE_VERSION, FIELD_PROJECT_ID,
            FIELD_ENTERED_FROM, FIELD_GENERATION_ID, FIELD_ENTERED_AT);

    /** Every field a row can have: the required ones and {@code worktree_of}. */
    static final List<String> DECLARED_FIELDS = List.of(FIELD_FORMAT_VERSION, FIELD_CLIENT_SESSION_ID, FIELD_CLIENT,
            FIELD_CLIENT_DETECTED, FIELD_ENVIRONMENT_MATCH_TIER, FIELD_CLIENT_BUILD, FIELD_MODEL_FAMILY,
            FIELD_MODEL_ID, FIELD_MODEL_SOURCE, FIELD_PM_MCP_VERSION, FIELD_BUNDLE_VERSION, FIELD_PROJECT_ID,
            FIELD_ENTERED_FROM, FIELD_WORKTREE_OF, FIELD_GENERATION_ID, FIELD_ENTERED_AT);

    /**
     * @param row a session row
     * @return the row as one JSON object, UTF-8 encoded, without a newline
     */
    public static byte[] write(ScopeSessionRow row) {
        Objects.requireNonNull(row, "row");
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
            write(generator, row);
        } catch (IOException e) {
            throw new UncheckedIOException("Session row cannot be written", e);
        }
        return out.toByteArray();
    }

    /**
     * @param file     the file the row was read from, named by a refusal
     * @param document one row, UTF-8 encoded
     * @return the row
     * @throws StoreRefusal if the row declares another format version or does not have the one shape of a row
     */
    public static ScopeSessionRow read(Path file, byte[] document) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        return StrictJson.read(file, document, ScopeSessionRow.FORMAT_VERSION,
                parser -> readObject(parser, "session row"));
    }

    /**
     * Writes the row as an object at the current position of the generator, as a line of the log or as a member of
     * another document.
     *
     * @param generator the generator
     * @param row       the row
     * @throws IOException if the generator cannot write
     */
    static void write(JsonGenerator generator, ScopeSessionRow row) throws IOException {
        generator.writeStartObject();
        generator.writeNumberField(FIELD_FORMAT_VERSION, ScopeSessionRow.FORMAT_VERSION);
        writeNullable(generator, FIELD_CLIENT_SESSION_ID, row.clientSessionId());
        generator.writeStringField(FIELD_CLIENT, row.client().wireName());
        writeNullable(generator, FIELD_CLIENT_DETECTED,
                row.clientDetected() == null ? null : row.clientDetected().wireName());
        generator.writeStringField(FIELD_ENVIRONMENT_MATCH_TIER, row.environmentMatchTier().wireName());
        writeNullable(generator, FIELD_CLIENT_BUILD, row.clientBuild());
        writeNullable(generator, FIELD_MODEL_FAMILY, row.modelFamily() == null ? null : row.modelFamily().wireName());
        writeNullable(generator, FIELD_MODEL_ID, row.modelId());
        writeNullable(generator, FIELD_MODEL_SOURCE, row.modelSource() == null ? null : row.modelSource().wireName());
        generator.writeStringField(FIELD_PM_MCP_VERSION, row.pmMcpVersion());
        writeNullable(generator, FIELD_BUNDLE_VERSION, row.bundleVersion());
        generator.writeStringField(FIELD_PROJECT_ID, row.projectId());
        generator.writeStringField(FIELD_ENTERED_FROM, row.enteredFrom().wireName());
        if (row.worktreeOf() != null) {
            generator.writeStringField(FIELD_WORKTREE_OF, row.worktreeOf());
        }
        generator.writeStringField(FIELD_GENERATION_ID, row.generationId());
        writeInstant(generator, FIELD_ENTERED_AT, row.enteredAt());
        generator.writeEndObject();
    }

    /**
     * Reads a row object at the current position of the parser, the object of a line or a member of another
     * document. Its {@code format_version} is read as a field of its own and must be the current one.
     *
     * @param parser the parser, positioned on the start of the object
     * @param name   the name of the object in a refusal
     * @return the row
     * @throws IOException if the document does not parse as JSON
     */
    static ScopeSessionRow readObject(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        var row = ScopeSessionRow.builder();
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_FORMAT_VERSION -> requireVersion(integer(parser, field), name);
                case FIELD_CLIENT_SESSION_ID -> row.clientSessionId(nullableString(parser, field));
                case FIELD_CLIENT -> row.client(enumValue(string(parser, field), Client::fromWireName, field));
                case FIELD_CLIENT_DETECTED -> row.clientDetected(nullableEnum(parser, field, Client::fromWireName));
                case FIELD_ENVIRONMENT_MATCH_TIER -> row.environmentMatchTier(
                        enumValue(string(parser, field), EnvironmentMatchTier::fromWireName, field));
                case FIELD_CLIENT_BUILD -> row.clientBuild(nullableString(parser, field));
                case FIELD_MODEL_FAMILY -> row.modelFamily(nullableEnum(parser, field, ModelFamily::fromWireName));
                case FIELD_MODEL_ID -> row.modelId(nullableString(parser, field));
                case FIELD_MODEL_SOURCE -> row.modelSource(nullableEnum(parser, field, ModelSource::fromWireName));
                case FIELD_PM_MCP_VERSION -> row.pmMcpVersion(string(parser, field));
                case FIELD_BUNDLE_VERSION -> row.bundleVersion(nullableString(parser, field));
                case FIELD_PROJECT_ID -> row.projectId(string(parser, field));
                case FIELD_ENTERED_FROM -> row.enteredFrom(
                        enumValue(string(parser, field), EnteredFrom::fromWireName, field));
                case FIELD_WORKTREE_OF -> row.worktreeOf(string(parser, field));
                case FIELD_GENERATION_ID -> row.generationId(string(parser, field));
                case FIELD_ENTERED_AT -> row.enteredAt(instant(parser, field));
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, REQUIRED_FIELDS.toArray(String[]::new));
        try {
            return row.build();
        } catch (IllegalArgumentException e) {
            throw unreadable("%s: %s".formatted(name, e.getMessage()), e);
        }
    }

    /** A row embedded in another document carries its own version, which the version check of the file never saw. */
    private static void requireVersion(long found, String name) {
        if (found != ScopeSessionRow.FORMAT_VERSION) {
            throw unreadable("'%s' of the %s is %d, not %d".formatted(FIELD_FORMAT_VERSION, name, found,
                    ScopeSessionRow.FORMAT_VERSION));
        }
    }

    private static void writeNullable(JsonGenerator generator, String field, String value) throws IOException {
        if (value == null) {
            generator.writeNullField(field);
        } else {
            generator.writeStringField(field, value);
        }
    }

    private static <E extends Enum<E>> E nullableEnum(JsonParser parser, String field,
            Function<String, Optional<E>> byWireName) throws IOException {
        var text = nullableString(parser, field);
        return text == null ? null : enumValue(text, byWireName, field);
    }

    private static <E extends Enum<E>> E enumValue(String text, Function<String, Optional<E>> byWireName,
            String field) {
        return byWireName.apply(text)
                .orElseThrow(() -> unreadable("'%s' has the undeclared value '%s'".formatted(field, text)));
    }
}
