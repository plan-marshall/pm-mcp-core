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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.SchemaResource;
import de.planmarshall.core.store.TransactionId;
import de.planmarshall.runtime.credentials.CredentialStoreChoice.Store;
import de.planmarshall.runtime.credentials.CredentialStoreMoveRecord.Operation;

/**
 * The codec of the record of an open operation keeps parity with the schema of Store Schemas § Machine State Records
 * (PM-CRED-2): the members it requires are the members the codec writes, the wire names of its two enums are the ones
 * the codec reads, and the form of the transaction id the schema states is the form {@link TransactionId} issues. The
 * schema is the file {@code credential-store-move.schema.json} beside the codec in
 * {@code pm-runtime/src/main/resources/de/planmarshall/runtime/credentials/}, read from the class path through
 * {@link SchemaResource}.
 */
@DisplayName("CredentialStoreMoveRecord schema parity")
class CredentialStoreMoveRecordParityTest {

    private static final String SCHEMA = "de/planmarshall/runtime/credentials/credential-store-move.schema.json";
    private static final Instant STARTED = Instant.parse("2026-10-17T15:00:00Z");

    @Test
    @DisplayName("the schema requires exactly the members the codec writes, and declares no other member")
    void members() {
        var schema = SchemaResource.load(SCHEMA);
        var written = new CredentialStoreMoveRecord(Store.OS, Store.FILE, STARTED,
                CredentialStoreMoveRecord.Phase.COPYING, false, Operation.MOVE,
                TransactionId.generate(Clock.fixed(STARTED, ZoneOffset.UTC))).write();
        var declared = properties(schema);

        assertAll(
                () -> assertEquals(Set.of("format_version", "from", "to", "started_at", "phase", "target_held_key",
                        "operation", "transaction_id"), declared),
                () -> assertEquals(declared, required(schema), "every member is required"),
                () -> assertEquals(declared, SchemaResource.object(
                                SchemaResource.parse(new String(written, StandardCharsets.UTF_8))).keySet(),
                        "the written members are the schema's"),
                () -> assertEquals(1, CredentialStoreMoveRecord.FORMAT_VERSION),
                () -> assertTrue(new String(written, StandardCharsets.UTF_8).startsWith("{\"format_version\":1,"),
                        new String(written, StandardCharsets.UTF_8)),
                () -> assertTrue(SchemaResource.text(SCHEMA).contains("\"additionalProperties\": false")));
    }

    @Test
    @DisplayName("the wire names of the operations and of the stores are the ones the schema enumerates")
    void wireNames() {
        var schema = SchemaResource.load(SCHEMA);
        var properties = SchemaResource.object(schema.get("properties"));
        var stores = Arrays.stream(Store.values()).map(Store::value).toList();
        var operations = Set.copyOf(Arrays.stream(Operation.values()).map(Operation::value).toList());

        assertAll(
                () -> assertEquals(operations, Set.copyOf(names(SchemaResource.array(
                                SchemaResource.object(properties.get("operation")).get("enum")))),
                        "The operation values are the schema's"),
                () -> assertEquals(stores, names(SchemaResource.array(
                        SchemaResource.object(properties.get("from")).get("enum"))),
                        "The from values are the store's values"),
                () -> assertEquals(stores, names(SchemaResource.array(
                        SchemaResource.object(properties.get("to")).get("enum"))),
                        "The to values are the store's values"));
    }

    @Test
    @DisplayName("the form of the transaction id the schema states is the one TransactionId issues")
    void transactionIdForm() {
        var schema = SchemaResource.load(SCHEMA);
        var declared = String.valueOf(SchemaResource.object(
                SchemaResource.object(schema.get("properties")).get("transaction_id")).get("pattern"));
        assertNotEquals("null", declared, "the schema states the pattern of transaction_id");
        var pattern = Pattern.compile(declared);
        var generated = TransactionId.generate(Clock.fixed(STARTED, ZoneOffset.UTC)).value();

        assertAll(
                () -> assertTrue(pattern.matcher(generated).matches(), generated),
                () -> assertFalse(pattern.matcher("018F0000-0000-7000-8000-000000000001").matches(),
                        "upper case is not the one written form"));
    }

    /** @return the member names of the schema's {@code properties} */
    private static Set<String> properties(Map<?, ?> schema) {
        return Set.copyOf(SchemaResource.object(schema.get("properties")).keySet().stream().map(String::valueOf)
                .toList());
    }

    /** @return the member names of the schema's {@code required} */
    private static Set<String> required(Map<?, ?> schema) {
        return Set.copyOf(SchemaResource.array(schema.get("required")).stream().map(String::valueOf).toList());
    }

    private static List<String> names(Collection<?> values) {
        return values.stream().map(String::valueOf).toList();
    }
}
