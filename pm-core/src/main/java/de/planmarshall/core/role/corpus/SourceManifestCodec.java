/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.corpus;

import static de.planmarshall.core.store.StrictJson.FIELD_FORMAT_VERSION;
import static de.planmarshall.core.store.StrictJson.array;
import static de.planmarshall.core.store.StrictJson.bool;
import static de.planmarshall.core.store.StrictJson.nextField;
import static de.planmarshall.core.store.StrictJson.requireFields;
import static de.planmarshall.core.store.StrictJson.requireObject;
import static de.planmarshall.core.store.StrictJson.string;
import static de.planmarshall.core.store.StrictJson.undeclared;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.role.corpus.SourceManifest.SourceFile;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.StrictJson;

/**
 * Reads and writes the manifest of a corpus source, {@code sources/<name>/manifest.json} (PM-WORK-6):
 * {@code {"format_version": 1, "name", "repository", "commit", "licence", "files": [{"path", "sha256", "lfs"}]}},
 * the closed schema {@code source-manifest.schema.json} of this package.
 * <p>
 * The reader accepts exactly one shape through {@link StrictJson}: the current {@code format_version} first, then
 * every declared field exactly once and no undeclared field. Every refusal is a {@link StoreRefusal} that names the
 * file.
 *
 * @since 0.1
 */
@UtilityClass
public class SourceManifestCodec {

    /** The format version of a source manifest that is read and written. */
    public static final int FORMAT_VERSION = 1;

    private static final String FIELD_NAME = "name";
    private static final String FIELD_REPOSITORY = "repository";
    private static final String FIELD_COMMIT = "commit";
    private static final String FIELD_LICENCE = "licence";
    private static final String FIELD_FILES = "files";
    private static final String FIELD_PATH = "path";
    private static final String FIELD_SHA256 = "sha256";
    private static final String FIELD_LFS = "lfs";
    private static final String MANIFEST = "manifest";

    /**
     * @param file     the manifest file the document was read from, named by a refusal
     * @param document a source manifest, UTF-8 encoded
     * @return the manifest
     * @throws StoreRefusal if the document declares another format version or is not a manifest
     */
    public static SourceManifest read(Path file, byte[] document) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        return StrictJson.read(file, document, FORMAT_VERSION, SourceManifestCodec::readDocument);
    }

    /**
     * @param manifest the manifest
     * @return the document, UTF-8 encoded, its members in the order of the schema
     */
    public static byte[] write(SourceManifest manifest) {
        Objects.requireNonNull(manifest, MANIFEST);
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
            generator.useDefaultPrettyPrinter();
            generator.writeStartObject();
            generator.writeNumberField(FIELD_FORMAT_VERSION, FORMAT_VERSION);
            generator.writeStringField(FIELD_NAME, manifest.name());
            generator.writeStringField(FIELD_REPOSITORY, manifest.repository().toString());
            generator.writeStringField(FIELD_COMMIT, manifest.commit());
            generator.writeStringField(FIELD_LICENCE, manifest.licence());
            generator.writeArrayFieldStart(FIELD_FILES);
            for (var source : manifest.files()) {
                generator.writeStartObject();
                generator.writeStringField(FIELD_PATH, source.path());
                generator.writeStringField(FIELD_SHA256, source.sha256());
                generator.writeBooleanField(FIELD_LFS, source.lfs());
                generator.writeEndObject();
            }
            generator.writeEndArray();
            generator.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException("Source manifest cannot be written", e);
        }
        return out.toByteArray();
    }

    private static SourceManifest readDocument(JsonParser parser) throws IOException {
        requireObject(parser, MANIFEST);
        String name = null;
        String repository = null;
        String commit = null;
        String licence = null;
        List<SourceFile> files = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_FORMAT_VERSION -> parser.skipChildren();
                case FIELD_NAME -> name = string(parser, field);
                case FIELD_REPOSITORY -> repository = string(parser, field);
                case FIELD_COMMIT -> commit = string(parser, field);
                case FIELD_LICENCE -> licence = string(parser, field);
                case FIELD_FILES -> files = array(parser, field, SourceManifestCodec::readFile);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, MANIFEST, FIELD_NAME, FIELD_REPOSITORY, FIELD_COMMIT, FIELD_LICENCE, FIELD_FILES);
        var sourceName = name;
        var sourceRepository = repository;
        var sourceCommit = commit;
        var sourceLicence = licence;
        var sourceFiles = files;
        return CorpusJson.construct(() -> new SourceManifest(sourceName, URI.create(sourceRepository), sourceCommit,
                sourceLicence, sourceFiles));
    }

    private static SourceFile readFile(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        String path = null;
        String sha256 = null;
        var lfs = false;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_PATH -> path = string(parser, field);
                case FIELD_SHA256 -> sha256 = string(parser, field);
                case FIELD_LFS -> lfs = bool(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, FIELD_PATH, FIELD_SHA256, FIELD_LFS);
        var filePath = path;
        var fileHash = sha256;
        var fileLfs = lfs;
        return CorpusJson.construct(() -> new SourceFile(filePath, fileHash, fileLfs));
    }
}
