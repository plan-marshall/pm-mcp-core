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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.TransactionId;
import de.planmarshall.runtime.credentials.CredentialStoreChoice.Store;
import de.planmarshall.runtime.credentials.CredentialStoreMoveRecord.Operation;

/**
 * The codec of the record of an open operation keeps parity with the schema text of Store Schemas § Machine State
 * Records (PM-CRED-2): the members it requires are the members the codec writes, the wire names of its two enums are the
 * ones the codec reads, and the form of the transaction id the schema states is the form {@link TransactionId} issues.
 * The schema is read from this repository, whose root is {@code pm.specification.root} where the build sets it and the
 * parent of the module otherwise.
 */
@DisplayName("CredentialStoreMoveRecord schema parity")
class CredentialStoreMoveRecordParityTest {

    private static final String DOCUMENT = "doc/specification/store-schemas/12-machine-stores.adoc";
    private static final String ANCHOR = "[[credential-store-move-record]]";
    private static final Pattern PROPERTY = Pattern.compile("\"([a-z_]+)\": \\{");
    private static final Pattern REQUIRED = Pattern.compile("\"required\": \\[([^]]*)]");
    private static final Pattern TRANSACTION_ID_PATTERN = Pattern.compile("\"pattern\": \"([^\"]+)\"");
    private static final Set<String> NOT_A_PROPERTY = Set.of("properties", "description");
    private static final Instant STARTED = Instant.parse("2026-10-17T15:00:00Z");

    /** @return the {@code [source,json]} block that follows the anchor of the record's schema */
    private static String schema() {
        var file = document();
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("specification document cannot be read: " + file, e);
        }
        var index = 0;
        while (index < lines.size() && !lines.get(index).startsWith(ANCHOR)) {
            index++;
        }
        while (index < lines.size() && !"[source,json]".equals(lines.get(index).trim())) {
            index++;
        }
        index++;
        if (index < lines.size() && "----".equals(lines.get(index).trim())) {
            index++;
        }
        var block = new StringBuilder();
        while (index < lines.size() && !"----".equals(lines.get(index).trim())) {
            block.append(lines.get(index)).append('\n');
            index++;
        }
        if (block.isEmpty()) {
            throw new AssertionError("no JSON schema block after " + ANCHOR + " in " + file);
        }
        return block.toString();
    }

    private static Path document() {
        var root = System.getProperty("pm.specification.root");
        var file = (root == null || root.isBlank() ? Path.of("..") : Path.of(root)).resolve(DOCUMENT);
        if (!Files.isRegularFile(file)) {
            throw new AssertionError("the specification document is absent: " + file.toAbsolutePath());
        }
        return file;
    }

    private static Set<String> properties(String schema) {
        var names = new LinkedHashSet<String>();
        Matcher matcher = PROPERTY.matcher(schema);
        while (matcher.find()) {
            if (!NOT_A_PROPERTY.contains(matcher.group(1))) {
                names.add(matcher.group(1));
            }
        }
        return names;
    }

    private static Set<String> required(String schema) {
        var names = new LinkedHashSet<String>();
        Matcher matcher = REQUIRED.matcher(schema);
        if (matcher.find()) {
            for (var name : matcher.group(1).split(",")) {
                names.add(name.trim().replace("\"", ""));
            }
        }
        return names;
    }

    @Test
    @DisplayName("the schema requires exactly the members the codec writes, and declares no other member")
    void members() {
        var written = new CredentialStoreMoveRecord(Store.OS, Store.FILE, STARTED,
                CredentialStoreMoveRecord.Phase.COPYING, false, Operation.MOVE,
                TransactionId.generate(Clock.fixed(STARTED, ZoneOffset.UTC))).write();
        var schema = schema();
        var declared = properties(schema);

        assertAll(
                () -> assertEquals(Set.of("format_version", "from", "to", "started_at", "phase", "target_held_key",
                        "operation", "transaction_id"), declared),
                () -> assertEquals(declared, required(schema), "every member is required"),
                () -> assertEquals(1, CredentialStoreMoveRecord.FORMAT_VERSION),
                () -> assertTrue(new String(written, StandardCharsets.UTF_8).startsWith("{\"format_version\":1,"),
                        new String(written, StandardCharsets.UTF_8)),
                () -> assertTrue(schema.contains("\"additionalProperties\": false")));
    }

    @Test
    @DisplayName("the wire names of the operations and of the stores are the ones the schema enumerates")
    void wireNames() {
        var schema = schema();

        assertAll(
                () -> {
                    for (var operation : Operation.values()) {
                        assertTrue(schema.contains("\"" + operation.value() + "\""), operation.value());
                    }
                },
                () -> {
                    for (var store : Store.values()) {
                        assertTrue(schema.contains("\"" + store.value() + "\""), store.value());
                    }
                });
    }

    @Test
    @DisplayName("the form of the transaction id the schema states is the one TransactionId issues")
    void transactionIdForm() {
        var schema = schema();
        Matcher matcher = TRANSACTION_ID_PATTERN.matcher(schema);
        assertTrue(matcher.find(), "the schema states the pattern of transaction_id");
        var pattern = Pattern.compile(matcher.group(1));
        var generated = TransactionId.generate(Clock.fixed(STARTED, ZoneOffset.UTC)).value();

        assertAll(
                () -> assertTrue(pattern.matcher(generated).matches(), generated),
                () -> assertFalse(pattern.matcher("018F0000-0000-7000-8000-000000000001").matches(),
                        "upper case is not the one written form"));
    }
}
