/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.credentials;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.StrictJson;
import de.planmarshall.runtime.credentials.CredentialStoreChoice.Store;

/**
 * The record of the open move between the credential stores (PM-CRED-2; Store Schemas, Machine State Records),
 * {@code <PM_MCP_BASE>/state/credential-store-move.json}: from which store to which the move runs, when it started, and
 * whether {@code credentials.store} was switched already. The runtime writes it under {@code locks/credentials.lock}
 * with {@link Phase#COPYING} at the start of a move, rewrites it once with {@link Phase#SWITCHED} in the step that
 * switches the store, and deletes it when the move ends; its absence means that no move is open. It names stores only
 * and holds nothing secret.
 * <p>
 * The document is {@code {"format_version": 1, "from", "to", "started_at", "phase"}}, written by the shared generator
 * of {@link StrictJson}; the store writes it with mode {@code 0600} in one atomic rename. The reader accepts exactly
 * one shape through {@link StrictJson}: the current {@code format_version}, every field once, no other field, the two
 * stores different. Every refusal is a {@link StoreRefusal} naming the file.
 *
 * @param from      the store recorded in {@code credentials.store} when the move started
 * @param to        the store the move switches to; never {@code from}
 * @param startedAt when the move started
 * @param phase     {@link Phase#COPYING} until {@code credentials.store} names {@code to}, {@link Phase#SWITCHED} from
 *                  the step that switches it
 * @since 0.1
 */
public record CredentialStoreMoveRecord(Store from, Store to, Instant startedAt, Phase phase) {

    /** The name of the record below the state directory. */
    public static final String FILE_NAME = "credential-store-move.json";

    /** The format version of the record that is read and written. */
    public static final int FORMAT_VERSION = 1;

    private static final String FIELD_FROM = "from";
    private static final String FIELD_TO = "to";
    private static final String FIELD_STARTED_AT = "started_at";
    private static final String FIELD_PHASE = "phase";

    /** How far the move came. */
    public enum Phase {
        /** The entries are being copied; {@code credentials.store} still names the old store. */
        COPYING("copying"),
        /** {@code credentials.store} names the new store; the entries are being deleted from the old one. */
        SWITCHED("switched");

        private final String value;

        Phase(String value) {
            this.value = value;
        }

        /** @return the value of {@code phase} */
        public String value() {
            return value;
        }

        static Optional<Phase> of(String value) {
            return Arrays.stream(values()).filter(phase -> phase.value.equals(value)).findFirst();
        }
    }

    /**
     * @throws NullPointerException     if a component is {@code null}
     * @throws IllegalArgumentException if both stores are the same
     */
    public CredentialStoreMoveRecord {
        Objects.requireNonNull(from, FIELD_FROM);
        Objects.requireNonNull(to, FIELD_TO);
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(phase, FIELD_PHASE);
        if (from == to) {
            throw new IllegalArgumentException("A move runs between two different stores, not from and to " + from);
        }
    }

    /**
     * @param base the base directory of the runtime
     * @return the record {@code <base>/state/credential-store-move.json}
     */
    public static Path file(Path base) {
        return Objects.requireNonNull(base, "base").toAbsolutePath().normalize().resolve("state").resolve(FILE_NAME);
    }

    /** @return this record in the phase {@link Phase#SWITCHED} */
    public CredentialStoreMoveRecord switched() {
        return new CredentialStoreMoveRecord(from, to, startedAt, Phase.SWITCHED);
    }

    /** @return the document, UTF-8 encoded */
    public byte[] write() {
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
            generator.writeStartObject();
            generator.writeNumberField(StrictJson.FIELD_FORMAT_VERSION, FORMAT_VERSION);
            generator.writeStringField(FIELD_FROM, from.value());
            generator.writeStringField(FIELD_TO, to.value());
            StrictJson.writeInstant(generator, FIELD_STARTED_AT, startedAt);
            generator.writeStringField(FIELD_PHASE, phase.value());
            generator.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException("Credential store move record cannot be written", e);
        }
        out.write('\n');
        return out.toByteArray();
    }

    /**
     * @param file     the file the document was read from, named by a refusal
     * @param document a record document, UTF-8 encoded
     * @return the record
     * @throws StoreRefusal if the document declares another format version or is unreadable
     */
    public static CredentialStoreMoveRecord read(Path file, byte[] document) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        return StrictJson.read(file, document, FORMAT_VERSION, CredentialStoreMoveRecord::readDocument);
    }

    private static CredentialStoreMoveRecord readDocument(JsonParser parser) throws IOException {
        StrictJson.requireObject(parser, FILE_NAME);
        Store from = null;
        Store to = null;
        Instant startedAt = null;
        Phase phase = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = StrictJson.nextField(parser, seen);
            switch (field) {
                case StrictJson.FIELD_FORMAT_VERSION -> parser.skipChildren();
                case FIELD_FROM -> from = store(parser, field);
                case FIELD_TO -> to = store(parser, field);
                case FIELD_STARTED_AT -> startedAt = StrictJson.instant(parser, field);
                case FIELD_PHASE -> phase = phase(parser);
                default -> throw StrictJson.undeclared(field);
            }
        }
        StrictJson.requireFields(seen, "credential store move record", FIELD_FROM, FIELD_TO, FIELD_STARTED_AT,
                FIELD_PHASE);
        try {
            return new CredentialStoreMoveRecord(from, to, startedAt, phase);
        } catch (IllegalArgumentException e) {
            throw StrictJson.unreadable(e.getMessage(), e);
        }
    }

    private static Store store(JsonParser parser, String field) throws IOException {
        var value = StrictJson.string(parser, field);
        return Store.of(value).orElseThrow(
                () -> StrictJson.unreadable("'%s' must be 'os' or 'file': %s".formatted(field, value)));
    }

    private static Phase phase(JsonParser parser) throws IOException {
        var value = StrictJson.string(parser, FIELD_PHASE);
        return Phase.of(value).orElseThrow(() -> StrictJson.unreadable(
                "'%s' must be 'copying' or 'switched': %s".formatted(FIELD_PHASE, value)));
    }
}
