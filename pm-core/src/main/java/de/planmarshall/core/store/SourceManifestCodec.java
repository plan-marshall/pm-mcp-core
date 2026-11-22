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
import static de.planmarshall.core.store.StrictJson.array;
import static de.planmarshall.core.store.StrictJson.integer;
import static de.planmarshall.core.store.StrictJson.instant;
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
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.store.SourceManifest.Kind;
import de.planmarshall.core.store.SourceManifest.Source;
import de.planmarshall.core.store.SourceManifest.SourceFile;
import de.planmarshall.core.store.SourceManifest.Trust;
import de.planmarshall.core.store.SourceManifest.Verdict;

/**
 * Reads and writes the source manifest {@code sources/sources.json} of an entry's source directory or of a plan
 * (Filesystem Layout § The Source Directory).
 * <p>
 * The reader checks the {@code format_version} first, then reads every member strictly: each at most once, only the
 * declared members, every required member, one JSON type per member, and the rules of the manifest. Whatever does not
 * parse is a {@link StoreRefusal} naming the file.
 *
 * @since 0.1
 */
@UtilityClass
public class SourceManifestCodec {

    /** The name of the manifest inside the source directory. */
    public static final String FILE_NAME = "sources.json";

    private static final String REVISION = "revision";
    private static final String SOURCES = "sources";
    private static final String SOURCE_ID = "source_id";
    private static final String KIND = "kind";
    private static final String REFERENCE = "reference";
    private static final String ORIGIN = "origin";
    private static final String TRUST = "trust";
    private static final String FILES = "files";
    private static final String ADDED_AT = "added_at";
    private static final String VERDICT = "verdict";
    private static final String VERDICT_REASON = "verdict_reason";
    private static final String PATH = "path";
    private static final String SIZE = "size";
    private static final String SHA256 = "sha256";

    /** The one refusal message of a closed value outside its enum. */
    private static final String UNDECLARED_VALUE = "'%s' has the undeclared value '%s'";

    /** The members of the manifest, in the order of the document. */
    static final List<String> MEMBERS = List.of(FIELD_FORMAT_VERSION, REVISION, SOURCES);

    /** The members of one source, in the order of the document. */
    static final List<String> SOURCE_MEMBERS = List.of(SOURCE_ID, KIND, REFERENCE, ORIGIN, TRUST, FILES, ADDED_AT,
            VERDICT, VERDICT_REASON);

    /** The members of one stored file, in the order of the document. */
    static final List<String> FILE_MEMBERS = List.of(PATH, SIZE, SHA256);

    /**
     * @param manifest a source manifest
     * @return the document, UTF-8 encoded
     */
    public static byte[] write(SourceManifest manifest) {
        Objects.requireNonNull(manifest, "manifest");
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
            generator.writeStartObject();
            generator.writeNumberField(FIELD_FORMAT_VERSION, SourceManifest.FORMAT_VERSION);
            generator.writeNumberField(REVISION, manifest.revision());
            generator.writeArrayFieldStart(SOURCES);
            for (var source : manifest.sources()) {
                writeSource(generator, source);
            }
            generator.writeEndArray();
            generator.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException("The source manifest cannot be written", e);
        }
        return out.toByteArray();
    }

    /**
     * @param file     the manifest file the document was read from, named by a refusal
     * @param document a source manifest, UTF-8 encoded
     * @return the manifest
     * @throws StoreRefusal if the document declares another format version or does not have the one shape of a source
     *                      manifest
     */
    public static SourceManifest read(Path file, byte[] document) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        return StrictJson.read(file, document, SourceManifest.FORMAT_VERSION, parser -> {
            try {
                return readDocument(parser);
            } catch (IllegalArgumentException e) {
                throw unreadable(e.getMessage(), e);
            }
        });
    }

    private static void writeSource(JsonGenerator generator, Source source) throws IOException {
        generator.writeStartObject();
        generator.writeStringField(SOURCE_ID, source.sourceId());
        generator.writeStringField(KIND, source.kind().wireName());
        generator.writeStringField(REFERENCE, source.reference());
        generator.writeStringField(ORIGIN, source.origin());
        generator.writeStringField(TRUST, source.trust().wireName());
        generator.writeArrayFieldStart(FILES);
        for (var stored : source.files()) {
            generator.writeStartObject();
            generator.writeStringField(PATH, stored.path());
            generator.writeNumberField(SIZE, stored.size());
            generator.writeStringField(SHA256, stored.sha256());
            generator.writeEndObject();
        }
        generator.writeEndArray();
        writeInstant(generator, ADDED_AT, source.addedAt());
        writeNullable(generator, VERDICT, source.verdict() == null ? null : source.verdict().wireName());
        writeNullable(generator, VERDICT_REASON, source.verdictReason());
        generator.writeEndObject();
    }

    private static SourceManifest readDocument(JsonParser parser) throws IOException {
        var manifest = SourceManifest.builder();
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_FORMAT_VERSION -> parser.skipChildren();
                case REVISION -> manifest.revision(integer(parser, field));
                case SOURCES -> manifest.sources(array(parser, field, SourceManifestCodec::readSource));
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "source manifest", MEMBERS.toArray(String[]::new));
        return manifest.build();
    }

    private static Source readSource(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        var source = Source.builder();
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case SOURCE_ID -> source.sourceId(string(parser, field));
                case KIND -> source.kind(kind(string(parser, field)));
                case REFERENCE -> source.reference(string(parser, field));
                case ORIGIN -> source.origin(string(parser, field));
                case TRUST -> source.trust(trust(string(parser, field)));
                case FILES -> source.files(array(parser, field, SourceManifestCodec::readFile));
                case ADDED_AT -> source.addedAt(instant(parser, field));
                case VERDICT -> source.verdict(isNull(parser) ? null : verdict(string(parser, field)));
                case VERDICT_REASON -> source.verdictReason(nullableString(parser, field).orElse(null));
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "source", SOURCE_MEMBERS.toArray(String[]::new));
        return source.build();
    }

    private static SourceFile readFile(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        String path = null;
        long size = 0;
        String sha256 = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case PATH -> path = string(parser, field);
                case SIZE -> size = integer(parser, field);
                case SHA256 -> sha256 = string(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "source file", FILE_MEMBERS.toArray(String[]::new));
        return new SourceFile(path, size, sha256);
    }

    private static Kind kind(String wireName) {
        return Kind.fromWireName(wireName)
                .orElseThrow(() -> unreadable(UNDECLARED_VALUE.formatted(KIND, wireName)));
    }

    private static Trust trust(String wireName) {
        return Trust.fromWireName(wireName)
                .orElseThrow(() -> unreadable(UNDECLARED_VALUE.formatted(TRUST, wireName)));
    }

    private static Verdict verdict(String wireName) {
        return Verdict.fromWireName(wireName)
                .orElseThrow(() -> unreadable(UNDECLARED_VALUE.formatted(VERDICT, wireName)));
    }

    private static boolean isNull(JsonParser parser) {
        return parser.currentToken() == JsonToken.VALUE_NULL;
    }

    private static void writeNullable(JsonGenerator generator, String field, @Nullable String value) throws IOException {
        if (value == null) {
            generator.writeNullField(field);
        } else {
            generator.writeStringField(field, value);
        }
    }
}
