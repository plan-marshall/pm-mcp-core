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
import de.planmarshall.core.store.TransactionId;
import de.planmarshall.runtime.credentials.CredentialStoreChoice.Store;

/**
 * The durable record of an operation that switches {@code credentials.store} and moves or deletes the integrity key
 * (PM-CRED-2; Store Schemas, Machine State Records), {@code <PM_MCP_BASE>/state/credential-store-move.json}: which
 * operation it is, from which store to which it runs, when it started, whether {@code credentials.store} was switched
 * already, whether the target held the kept integrity key before the operation, and the transaction id the operation's
 * audit record carries. The runtime writes it under {@code locks/credentials.lock} with {@link Phase#COPYING} at the
 * start of the operation, rewrites it once with {@link Phase#SWITCHED} right after the write that switches the store,
 * and deletes it last, when the operation ends; its absence means that no operation is open. While it exists only the
 * operation records a store. It names stores only and holds nothing secret.
 * <p>
 * {@link Operation#MOVE} moves every Token Lane entry and the key between the two stores and deletes the old store.
 * {@link Operation#FIRST_RECORDING} is the credential-store aspect's first recording of a store: it switches to the
 * store the operator chose, brings the integrity key the sidecars are signed under into it, and deletes the key from
 * the store that is not recorded; it copies no entry, since no credential can be written before a store is recorded.
 * A stop is finished by the next start from this record, for either operation.
 * <p>
 * The document is {@code {"format_version": 1, "from", "to", "started_at", "phase", "target_held_key", "operation",
 * "transaction_id"}}, written by the shared generator of {@link StrictJson}; the store writes it with mode {@code 0600}
 * in one atomic rename. The reader accepts exactly one shape through {@link StrictJson}: the current
 * {@code format_version}, every field once, no other field, the two stores different, an operation and a transaction
 * id of the one written form. Every refusal is a {@link StoreRefusal} naming the file.
 *
 * @param from          the store the operation gives the key up in: for a move the store recorded in
 *                      {@code credentials.store} when it started, for the first recording the store that is not
 *                      being recorded
 * @param to            the store the operation switches to; never {@code from}
 * @param startedAt     when the operation started
 * @param phase         {@link Phase#COPYING} until the record is rewritten after the switch, {@link Phase#SWITCHED}
 *                      from then on; between the switch and the rewrite the record says {@code copying} while
 *                      {@code credentials.store} names {@code to} already
 * @param targetHeldKey whether {@code to} held the integrity key the operation keeps when it started, before its first
 *                      copy; the clean-up of a failed operation, and the resume of an operation a stop cut before the
 *                      switch, delete the key from {@code to} only where it did not, since only then is the key there a
 *                      copy of the operation
 * @param operation     which of the two operations the record drives
 * @param transactionId the id of the operation, issued once and kept in the record, so a re-delivered audit record of
 *                      it carries the same id and the reader of the audit log drops the duplicate
 * @since 0.1
 */
public record CredentialStoreMoveRecord(Store from, Store to, Instant startedAt, Phase phase, boolean targetHeldKey,
Operation operation, TransactionId transactionId) {

    /** The name of the record below the state directory. */
    public static final String FILE_NAME = "credential-store-move.json";

    /** The format version of the record that is read and written. */
    public static final int FORMAT_VERSION = 1;

    private static final String FIELD_FROM = "from";
    private static final String FIELD_TO = "to";
    private static final String FIELD_STARTED_AT = "started_at";
    private static final String FIELD_PHASE = "phase";
    private static final String FIELD_TARGET_HELD_KEY = "target_held_key";
    private static final String FIELD_OPERATION = "operation";
    private static final String FIELD_TRANSACTION_ID = "transaction_id";

    /** How far the move came. */
    public enum Phase {
        /**
         * The entries are being copied, and {@code credentials.store} names the old store; from the switch until the
         * record is rewritten it names the new store already, which the resume reads as a move that has switched.
         */
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

    /** The operation a record drives; both switch {@code credentials.store} and end by deleting the key from a store. */
    public enum Operation {
        /** Every entry and the key move from the recorded store into the other one, and the old store is emptied. */
        MOVE("move"),
        /**
         * The credential-store aspect records a store the first time: the key the sidecars are signed under is
         * brought into the store being recorded, and the key of the store that is not recorded is deleted.
         */
        FIRST_RECORDING("first_recording");

        private final String value;

        Operation(String value) {
            this.value = value;
        }

        /** @return the value of {@code operation} */
        public String value() {
            return value;
        }

        static Optional<Operation> of(String value) {
            return Arrays.stream(values()).filter(operation -> operation.value.equals(value)).findFirst();
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
        Objects.requireNonNull(operation, FIELD_OPERATION);
        Objects.requireNonNull(transactionId, FIELD_TRANSACTION_ID);
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
        return new CredentialStoreMoveRecord(from, to, startedAt, Phase.SWITCHED, targetHeldKey, operation,
                transactionId);
    }

    /** @return this record as the operation starts in {@link Phase#COPYING} */
    public static CredentialStoreMoveRecord copying(Operation operation, Store from, Store to, Instant startedAt,
            boolean targetHeldKey, TransactionId transactionId) {
        return new CredentialStoreMoveRecord(from, to, startedAt, Phase.COPYING, targetHeldKey, operation,
                transactionId);
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
            generator.writeBooleanField(FIELD_TARGET_HELD_KEY, targetHeldKey);
            generator.writeStringField(FIELD_OPERATION, operation.value());
            generator.writeStringField(FIELD_TRANSACTION_ID, transactionId.value());
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
        var targetHeldKey = false;
        Operation operation = null;
        TransactionId transactionId = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = StrictJson.nextField(parser, seen);
            switch (field) {
                case StrictJson.FIELD_FORMAT_VERSION -> parser.skipChildren();
                case FIELD_FROM -> from = store(parser, field);
                case FIELD_TO -> to = store(parser, field);
                case FIELD_STARTED_AT -> startedAt = StrictJson.instant(parser, field);
                case FIELD_PHASE -> phase = phase(parser);
                case FIELD_TARGET_HELD_KEY -> targetHeldKey = StrictJson.bool(parser, field);
                case FIELD_OPERATION -> operation = operation(parser);
                case FIELD_TRANSACTION_ID -> transactionId = transactionId(parser);
                default -> throw StrictJson.undeclared(field);
            }
        }
        StrictJson.requireFields(seen, "credential store move record", FIELD_FROM, FIELD_TO, FIELD_STARTED_AT,
                FIELD_PHASE, FIELD_TARGET_HELD_KEY, FIELD_OPERATION, FIELD_TRANSACTION_ID);
        try {
            return new CredentialStoreMoveRecord(from, to, startedAt, phase, targetHeldKey, operation,
                    transactionId);
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

    private static Operation operation(JsonParser parser) throws IOException {
        var value = StrictJson.string(parser, FIELD_OPERATION);
        return Operation.of(value).orElseThrow(() -> StrictJson.unreadable(
                "'%s' must be 'move' or 'first_recording': %s".formatted(FIELD_OPERATION, value)));
    }

    private static TransactionId transactionId(JsonParser parser) throws IOException {
        var value = StrictJson.string(parser, FIELD_TRANSACTION_ID);
        try {
            return new TransactionId(value);
        } catch (IllegalArgumentException e) {
            throw StrictJson.unreadable("'%s' must be a lower-case UUID of version 7: %s"
                    .formatted(FIELD_TRANSACTION_ID, value), e);
        }
    }
}
