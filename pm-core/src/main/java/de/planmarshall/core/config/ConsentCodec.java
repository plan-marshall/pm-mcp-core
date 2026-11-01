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
import static de.planmarshall.core.store.StrictJson.nextField;
import static de.planmarshall.core.store.StrictJson.nullableString;
import static de.planmarshall.core.store.StrictJson.requireFields;
import static de.planmarshall.core.store.StrictJson.requireObject;
import static de.planmarshall.core.store.StrictJson.string;
import static de.planmarshall.core.store.StrictJson.undeclared;
import static de.planmarshall.core.store.StrictJson.unreadable;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.StrictJson;

/**
 * Reads and writes {@code <PM_MCP_BASE>/consents.json} (PM-SEC-8, Store Schemas § Consent Before Acting).
 * <p>
 * The document is closed: {@code format_version} 1, the {@code revision} of the settings and the {@code consents} array,
 * in which at most one entry exists per (kind, project). Every field of an entry is read in the shape the schema gives
 * it, and a kind, a channel or a project that does not belong to the entry refuses the document — never a tolerated
 * shape.
 *
 * @since 0.1
 */
@UtilityClass
public class ConsentCodec {

    /** The format version of the consent store. */
    public static final int FORMAT_VERSION = 1;

    private static final String FIELD_REVISION = "revision";
    private static final String FIELD_CONSENTS = "consents";
    private static final String FIELD_KIND = "kind";
    private static final String FIELD_PROJECT_ID = "project_id";
    private static final String FIELD_SCRIPTS = "scripts";
    private static final String FIELD_GRANTED_AT = "granted_at";
    private static final String FIELD_CHANNEL = "channel";
    private static final String FIELD_DEVICE_ID = "device_id";
    private static final String FIELD_PATH = "path";
    private static final String FIELD_SHA256 = "sha256";

    /**
     * What a read of the store found: the entries and the revision the document carries.
     *
     * @param consents the entries, in the order of the document
     * @param revision the {@code revision} of the document
     * @since 0.1
     */
    public record Document(List<Consent> consents, long revision) {

        /** Keeps the entries unmodifiable. */
        public Document {
            consents = List.copyOf(consents);
        }
    }

    /**
     * @param file     the document's file, named by a refusal
     * @param document the document, UTF-8 encoded
     * @return the entries and the revision
     * @throws StoreRefusal if the document declares the wrong format version, is unreadable, or holds an entry of a
     *                      shape the schema does not allow
     */
    public static Document read(Path file, byte[] document) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        return StrictJson.read(file, document, FORMAT_VERSION, ConsentCodec::document);
    }

    /**
     * Writes the whole document: every entry of the store and the revision it carries, in one shape.
     *
     * @param consents the entries, in the order of the document
     * @param revision the revision to write
     * @return the document, UTF-8 encoded
     */
    public static byte[] write(List<Consent> consents, long revision) {
        Objects.requireNonNull(consents, FIELD_CONSENTS);
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
            generator.writeStartObject();
            generator.writeNumberField(FIELD_FORMAT_VERSION, FORMAT_VERSION);
            generator.writeNumberField(FIELD_REVISION, revision);
            generator.writeArrayFieldStart(FIELD_CONSENTS);
            for (var consent : consents) {
                writeConsent(generator, consent);
            }
            generator.writeEndArray();
            generator.writeEndObject();
        } catch (IOException e) {
            throw new IllegalStateException("The consent store cannot be written", e);
        }
        return out.toByteArray();
    }

    private static void writeConsent(JsonGenerator generator, Consent consent) throws IOException {
        generator.writeStartObject();
        generator.writeStringField(FIELD_KIND, consent.kind().wireName());
        if (consent.projectId().isPresent()) {
            generator.writeStringField(FIELD_PROJECT_ID, consent.projectId().get());
        }
        if (consent.kind().scriptScoped()) {
            generator.writeArrayFieldStart(FIELD_SCRIPTS);
            for (var script : consent.scripts()) {
                generator.writeStartObject();
                generator.writeStringField(FIELD_PATH, script.path());
                generator.writeStringField(FIELD_SHA256, script.sha256());
                generator.writeEndObject();
            }
            generator.writeEndArray();
        }
        generator.writeStringField(FIELD_GRANTED_AT, consent.grantedAt().toString());
        generator.writeStringField(FIELD_CHANNEL, consent.channel().wireName());
        consent.deviceId().ifPresent(device -> {
            try {
                generator.writeStringField(FIELD_DEVICE_ID, device);
            } catch (IOException e) {
                throw new IllegalStateException("The consent store cannot be written", e);
            }
        });
        generator.writeEndObject();
    }

    private static Document document(JsonParser parser) throws IOException {
        var consents = new ArrayList<Consent>();
        var revision = -1L;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_FORMAT_VERSION -> version(parser);
                case FIELD_REVISION -> revision = revision(parser);
                case FIELD_CONSENTS -> consents.addAll(array(parser, FIELD_CONSENTS, ConsentCodec::consent));
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "consent store", FIELD_FORMAT_VERSION, FIELD_REVISION, FIELD_CONSENTS);
        if (revision < 0) {
            throw unreadable("'revision' must not be negative: " + revision);
        }
        requireUnique(consents);
        return new Document(consents, revision);
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

    /** At most one entry per (kind, project): a second one is not a shape of the store. */
    private static void requireUnique(List<Consent> consents) {
        var keys = new HashSet<String>();
        for (var consent : consents) {
            var key = consent.kind().wireName() + "|" + consent.projectId().orElse("");
            if (!keys.add(key)) {
                throw unreadable("the consent '%s' is recorded twice".formatted(key));
            }
        }
    }

    private static Consent consent(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        var seen = new HashSet<String>();
        @Nullable ConsentKind kind = null;
        Optional<String> projectId = Optional.empty();
        var scripts = List.<ConsentScript>of();
        @Nullable Instant grantedAt = null;
        Consent.@Nullable Channel channel = null;
        Optional<String> deviceId = Optional.empty();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_KIND -> kind = kind(parser);
                case FIELD_PROJECT_ID -> projectId = Optional.of(string(parser, field));
                case FIELD_SCRIPTS -> scripts = array(parser, field, ConsentCodec::script);
                case FIELD_GRANTED_AT -> grantedAt = instant(parser, field);
                case FIELD_CHANNEL -> channel = channel(parser);
                case FIELD_DEVICE_ID -> deviceId = nullableString(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "consent entry", FIELD_KIND, FIELD_GRANTED_AT, FIELD_CHANNEL);
        requireOptionalMember(kind, seen);
        try {
            return new Consent(kind, projectId, scripts, grantedAt, channel, deviceId);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw unreadable("%s: %s".formatted(name, e.getMessage()), e);
        }
    }

    /**
     * The schema requires {@code scripts} for {@code run_checkout_scripts} and forbids it for every other kind, which the
     * typed entry cannot express (an empty list and an absent member read alike).
     */
    private static void requireOptionalMember(@Nullable ConsentKind kind, HashSet<String> seen) {
        if (kind == null) {
            return;
        }
        if (kind.scriptScoped() && !seen.contains(FIELD_SCRIPTS)) {
            throw unreadable("the consent '%s' does not list its scripts".formatted(kind.wireName()));
        }
        if (!kind.scriptScoped() && seen.contains(FIELD_SCRIPTS)) {
            throw unreadable("only run_checkout_scripts lists scripts, and '%s' does".formatted(kind.wireName()));
        }
    }

    private static ConsentKind kind(JsonParser parser) throws IOException {
        var wireName = string(parser, FIELD_KIND);
        return ConsentKind.of(wireName)
                .orElseThrow(() -> unreadable("not a consent kind: " + wireName));
    }

    private static Consent.Channel channel(JsonParser parser) throws IOException {
        var wireName = string(parser, FIELD_CHANNEL);
        return Consent.Channel.of(wireName)
                .orElseThrow(() -> unreadable("not a consent channel: " + wireName));
    }

    private static ConsentScript script(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        var seen = new HashSet<String>();
        @Nullable String path = null;
        @Nullable String sha256 = null;
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_PATH -> path = string(parser, field);
                case FIELD_SHA256 -> sha256 = string(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "consent script", FIELD_PATH, FIELD_SHA256);
        try {
            return new ConsentScript(path, sha256);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw unreadable("%s: %s".formatted(name, e.getMessage()), e);
        }
    }
}
