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
import static de.planmarshall.core.store.StrictJson.bool;
import static de.planmarshall.core.store.StrictJson.integer;
import static de.planmarshall.core.store.StrictJson.nextField;
import static de.planmarshall.core.store.StrictJson.requireObject;
import static de.planmarshall.core.store.StrictJson.stringList;
import static de.planmarshall.core.store.StrictJson.undeclared;
import static de.planmarshall.core.store.StrictJson.unreadable;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;

/**
 * Reads and writes the {@code settings.qualification} member of a project's {@code local-config.json} (Store Schemas §
 * Operator Local Configuration).
 * <p>
 * The file is the operator's document and holds far more than these four values, so this codec never rewrites the
 * whole file from its own model: it reads the qualification block in place, and a write replaces that one member while
 * every other member of the document is copied through untouched. The document's {@code revision} is incremented by
 * one on every write, as every operator file of the runtime is, and a document of another {@code format_version} is
 * refused before anything is read.
 *
 * @since 0.1
 */
@UtilityClass
public class QualificationSettingsCodec {

    private static final String FIELD_REVISION = "revision";
    private static final String FIELD_SETTINGS = "settings";
    private static final String FIELD_QUALIFICATION = "qualification";
    private static final String FIELD_AGREEMENT_LEVEL = "agreement_level";
    private static final String FIELD_ALLOWED_GAP = "allowed_gap";
    private static final String FIELD_SKIP_ROLES = "skip_roles";
    private static final String FIELD_SKIP_DECISIONS = "skip_decisions";

    /**
     * Reads the qualification settings of a local configuration document.
     *
     * @param file     the document's file, named by a refusal
     * @param document the document, UTF-8 encoded
     * @return the settings; the defaults where the document sets none
     * @throws StoreRefusal if the document declares another format version or is unreadable
     */
    public static QualificationSettings read(Path file, byte[] document) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        var reader = new DocumentReader(file);
        reader.read(document, null);
        return reader.settings == null ? QualificationSettings.defaults() : reader.settings;
    }

    /**
     * Replaces the qualification settings of a local configuration document, keeping every other member untouched.
     *
     * @param file     the document's file, named by a refusal
     * @param document the document, UTF-8 encoded
     * @param settings the settings to write
     * @return the document with the new settings and the next revision, UTF-8 encoded
     * @throws StoreRefusal if the document declares another format version or is unreadable
     */
    public static byte[] write(Path file, byte[] document, QualificationSettings settings) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(settings, "settings");
        var reader = new DocumentReader(file);
        reader.read(document, settings);
        return reader.rewritten;
    }

    /**
     * One pass over the document: it reads the qualification settings and, when a write is asked for, composes the
     * document with the replaced block and the incremented revision.
     */
    private static final class DocumentReader {

        private final Path file;
        private QualificationSettings settings;
        private byte[] rewritten;

        DocumentReader(Path file) {
            this.file = file;
        }

        void read(byte[] document, QualificationSettings replacement) {
            try (var parser = StrictJson.factory().createParser(document)) {
                if (parser.nextToken() != JsonToken.START_OBJECT) {
                    throw unreadable("the local configuration is not a JSON object");
                }
                var seen = new HashSet<String>();
                var out = new ByteArrayOutputStream();
                try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
                    generator.writeStartObject();
                    var settingsSeen = false;
                    Long revision = null;
                    while (parser.nextToken() == JsonToken.FIELD_NAME) {
                        var field = nextField(parser, seen);
                        switch (field) {
                            case FIELD_FORMAT_VERSION -> {
                                if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT
                                        || parser.getIntValue() != 1) {
                                    throw unreadable("'format_version' is not 1");
                                }
                                generator.writeNumberField(FIELD_FORMAT_VERSION, 1);
                            }
                            case FIELD_REVISION -> {
                                revision = integer(parser, field);
                                generator.writeNumberField(FIELD_REVISION,
                                        replacement == null ? revision : Math.addExact(revision, 1));
                            }
                            case FIELD_SETTINGS -> {
                                settingsSeen = true;
                                generator.writeFieldName(FIELD_SETTINGS);
                                readSettings(parser, generator, replacement);
                            }
                            default -> {
                                generator.writeFieldName(field);
                                generator.copyCurrentStructure(parser);
                            }
                        }
                    }
                    if (!seen.contains(FIELD_FORMAT_VERSION)) {
                        throw unreadable("'format_version' is missing");
                    }
                    if (!seen.contains(FIELD_REVISION)) {
                        throw unreadable("'revision' is missing");
                    }
                    if (replacement != null && !settingsSeen) {
                        generator.writeObjectFieldStart(FIELD_SETTINGS);
                        writeQualification(generator, replacement);
                        generator.writeEndObject();
                    }
                    generator.writeEndObject();
                }
                if (replacement == null) {
                    return;
                }
                rewritten = out.toByteArray();
            } catch (IOException e) {
                throw StoreRefusal.unreadable(file, 1, String.valueOf(e.getMessage()), e);
            }
        }

        private void readSettings(JsonParser parser, JsonGenerator generator, QualificationSettings replacement)
                throws IOException {
            requireObject(parser, FIELD_SETTINGS);
            generator.writeStartObject();
            var seen = new HashSet<String>();
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                var field = nextField(parser, seen);
                if (FIELD_QUALIFICATION.equals(field)) {
                    readQualification(parser);
                    writeQualification(generator, replacement == null ? settings : replacement);
                    continue;
                }
                generator.writeFieldName(field);
                generator.copyCurrentStructure(parser);
            }
            if (!seen.contains(FIELD_QUALIFICATION)) {
                writeQualification(generator, replacement == null ? QualificationSettings.defaults() : replacement);
            }
            generator.writeEndObject();
        }

        private void readQualification(JsonParser parser) throws IOException {
            requireObject(parser, FIELD_QUALIFICATION);
            var agreement = QualificationSettings.DEFAULT_AGREEMENT_LEVEL;
            var gap = QualificationSettings.DEFAULT_ALLOWED_GAP;
            List<String> skipRoles = List.of();
            var skipDecisions = false;
            var seen = new HashSet<String>();
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                var field = nextField(parser, seen);
                switch (field) {
                    case FIELD_AGREEMENT_LEVEL -> agreement = Math.toIntExact(integer(parser, field));
                    case FIELD_ALLOWED_GAP -> gap = Math.toIntExact(integer(parser, field));
                    case FIELD_SKIP_ROLES -> skipRoles = stringList(parser, field);
                    case FIELD_SKIP_DECISIONS -> skipDecisions = bool(parser, field);
                    default -> throw undeclared(field);
                }
            }
            try {
                settings = new QualificationSettings(agreement, gap, skipRoles, skipDecisions);
            } catch (IllegalArgumentException e) {
                throw unreadable(e.getMessage(), e);
            }
        }

        private static void writeQualification(JsonGenerator generator, QualificationSettings settings)
                throws IOException {
            generator.writeObjectFieldStart(FIELD_QUALIFICATION);
            if (settings.agreementLevel() != QualificationSettings.DEFAULT_AGREEMENT_LEVEL) {
                generator.writeNumberField(FIELD_AGREEMENT_LEVEL, settings.agreementLevel());
            }
            if (settings.allowedGap() != QualificationSettings.DEFAULT_ALLOWED_GAP) {
                generator.writeNumberField(FIELD_ALLOWED_GAP, settings.allowedGap());
            }
            if (!settings.skipRoles().isEmpty()) {
                generator.writeArrayFieldStart(FIELD_SKIP_ROLES);
                for (var role : settings.skipRoles()) {
                    generator.writeString(role);
                }
                generator.writeEndArray();
            }
            if (settings.skipDecisions()) {
                generator.writeBooleanField(FIELD_SKIP_DECISIONS, true);
            }
            generator.writeEndObject();
        }
    }
}
