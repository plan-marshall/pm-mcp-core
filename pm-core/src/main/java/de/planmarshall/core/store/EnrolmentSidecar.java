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
import static de.planmarshall.core.store.StrictJson.nextField;
import static de.planmarshall.core.store.StrictJson.requireFields;
import static de.planmarshall.core.store.StrictJson.string;
import static de.planmarshall.core.store.StrictJson.undeclared;
import static de.planmarshall.core.store.StrictJson.unreadable;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The integrity sidecar of an enrolment file (PM-EXEC-2, PM-SEC-5, PM-IMPL-7),
 * {@code enrolments/<project_id>.json.hmac}: what the runtime recorded when it last wrote or re-baselined the file.
 * <p>
 * The document is {@code {"format_version": 1, "size", "sha256", "hmac"}}: the size and the SHA-256 of the canonical
 * enrolment file, which an operator re-baseline shows as the recorded values against the current ones, and the
 * lowercase-hex HMAC-SHA-256 over the exact bytes of that file under the operator-lane key, which is what proves the
 * file was written by the runtime. This type reads and writes the record and decides nothing; it holds no key and
 * computes no HMAC.
 * <p>
 * The reader accepts exactly one shape through {@link StrictJson}: the current {@code format_version}, every field
 * once, no other field. Every refusal is a {@link StoreRefusal} naming the sidecar file.
 *
 * @param digest the size and SHA-256 of the canonical enrolment file
 * @param hmac   the HMAC-SHA-256 over the canonical enrolment file, 64 lowercase hexadecimal digits
 * @since 0.1
 */
public record EnrolmentSidecar(StoreDigest digest, String hmac) {

    /** The format version of an enrolment sidecar that is read and written. */
    public static final int FORMAT_VERSION = 1;

    /** The suffix the sidecar adds to the name of its enrolment file. */
    public static final String SUFFIX = ".hmac";

    private static final Pattern HMAC = Pattern.compile("[0-9a-f]{64}");
    private static final String FIELD_SIZE = "size";
    private static final String FIELD_SHA256 = "sha256";
    private static final String FIELD_HMAC = "hmac";

    /**
     * @throws NullPointerException     if a component is {@code null}
     * @throws IllegalArgumentException if the HMAC is not 64 lowercase hexadecimal digits
     */
    public EnrolmentSidecar {
        Objects.requireNonNull(digest, "digest");
        if (!HMAC.matcher(Objects.requireNonNull(hmac, FIELD_HMAC)).matches()) {
            throw new IllegalArgumentException("Not an HMAC-SHA-256 in lowercase hexadecimal digits: " + hmac);
        }
    }

    /**
     * @param content the canonical enrolment file
     * @return whether the size and the SHA-256 of the content are the recorded ones
     */
    public boolean matches(byte[] content) {
        return digest.matches(content);
    }

    /** @return the document, UTF-8 encoded */
    public byte[] write() {
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
            generator.writeStartObject();
            generator.writeNumberField(FIELD_FORMAT_VERSION, FORMAT_VERSION);
            generator.writeNumberField(FIELD_SIZE, digest.size());
            generator.writeStringField(FIELD_SHA256, digest.sha256());
            generator.writeStringField(FIELD_HMAC, hmac);
            generator.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException("Enrolment sidecar cannot be written", e);
        }
        return out.toByteArray();
    }

    /**
     * @param file     the sidecar file the document was read from, named by a refusal
     * @param document a sidecar document, UTF-8 encoded
     * @return the sidecar
     * @throws StoreRefusal if the document declares another format version or is unreadable
     */
    public static EnrolmentSidecar read(Path file, byte[] document) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        return StrictJson.read(file, document, FORMAT_VERSION, EnrolmentSidecar::readDocument);
    }

    private static EnrolmentSidecar readDocument(JsonParser parser) throws IOException {
        long size = 0;
        String sha256 = null;
        String hmac = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_FORMAT_VERSION -> parser.skipChildren();
                case FIELD_SIZE -> size = integer(parser, field);
                case FIELD_SHA256 -> sha256 = string(parser, field);
                case FIELD_HMAC -> hmac = string(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "sidecar", FIELD_SIZE, FIELD_SHA256, FIELD_HMAC);
        try {
            return new EnrolmentSidecar(new StoreDigest(size, sha256), hmac);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }
}
