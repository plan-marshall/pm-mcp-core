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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;

import de.planmarshall.core.store.StoreRefusal.Outcome;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The digest sidecar of a scope (PM-IMPL-7, PM-IMPL-2): the size and SHA-256 of each store by its path relative to the
 * path base of the scope, written in a stable order and read in exactly one shape.
 */
@DisplayName("Digest sidecar")
class DigestSidecarTest {

    private static final String SHA_A = "a".repeat(64);
    private static final String SHA_B = "b".repeat(64);
    private static final StoreDigest STATUS = new StoreDigest(42, SHA_A);
    private static final StoreDigest PENDING = new StoreDigest(7, SHA_B);
    private static final String DOCUMENT = "{\"format_version\":1,\"stores\":{"
            + "\"pending/entry-1.json\":{\"size\":7,\"sha256\":\"" + SHA_B + "\"},"
            + "\"workspace/mcp/status.json\":{\"size\":42,\"sha256\":\"" + SHA_A + "\"}}}";

    @TempDir
    Path base;

    private Path file() {
        return base.resolve("digests.json");
    }

    private StoreRefusal assertRefused(Outcome outcome, String document) {
        var file = file();
        var bytes = document.getBytes(UTF_8);
        var refusal = assertThrows(StoreRefusal.class, () -> DigestSidecar.read(file, bytes));
        assertAll(
                () -> assertEquals(outcome, refusal.outcome()),
                () -> assertEquals(file, refusal.file()),
                () -> assertTrue(refusal.getMessage().contains(file.toString()), "the message names the file"));
        return refusal;
    }

    @Nested
    @DisplayName("round trip")
    class RoundTrip {

        @Test
        @DisplayName("the digests are written sorted by path, whatever order they were given in")
        void stableOrder() {
            var given = new LinkedHashMap<String, StoreDigest>();
            given.put("workspace/mcp/status.json", STATUS);
            given.put("pending/entry-1.json", PENDING);

            var written = DigestSidecar.of(given).write();

            assertArrayEquals(DOCUMENT.getBytes(UTF_8), written);
        }

        @Test
        @DisplayName("a written sidecar is read back equal, and written again byte for byte")
        void readBack() {
            var sidecar = DigestSidecar.of(Map.of("workspace/mcp/status.json", STATUS, "pending/entry-1.json",
                    PENDING));

            var read = DigestSidecar.read(file(), sidecar.write());

            assertAll(
                    () -> assertEquals(sidecar, read),
                    () -> assertEquals(List.of("pending/entry-1.json", "workspace/mcp/status.json"),
                            List.copyOf(read.stores().keySet())),
                    () -> assertArrayEquals(sidecar.write(), read.write()));
        }

        @Test
        @DisplayName("a sidecar without a store is written and read as an empty object")
        void empty() {
            var written = DigestSidecar.empty().write();

            assertAll(
                    () -> assertEquals("{\"format_version\":1,\"stores\":{}}", new String(written, UTF_8)),
                    () -> assertEquals(DigestSidecar.empty(), DigestSidecar.read(file(), written)));
        }

        @Test
        @DisplayName("a digest is found by its path, and a recorded one replaced without touching the others")
        void digestAndWith() {
            var sidecar = DigestSidecar.of(Map.of("workspace/mcp/status.json", STATUS));
            var changed = new StoreDigest(43, SHA_B);

            var updated = sidecar.with("workspace/mcp/status.json", changed).with("pending/entry-1.json", PENDING);

            assertAll(
                    () -> assertEquals(Optional.of(STATUS), sidecar.digest("workspace/mcp/status.json")),
                    () -> assertEquals(Optional.empty(), sidecar.digest("pending/entry-1.json")),
                    () -> assertEquals(Optional.of(changed), updated.digest("workspace/mcp/status.json")),
                    () -> assertEquals(Optional.of(PENDING), updated.digest("pending/entry-1.json")));
        }

        @Test
        @DisplayName("the digests of a sidecar cannot be changed in place")
        void unmodifiable() {
            var stores = DigestSidecar.of(Map.of("workspace/mcp/status.json", STATUS)).stores();

            assertThrows(UnsupportedOperationException.class, () -> stores.put("pending/entry-1.json", PENDING));
        }

        @Test
        @DisplayName("a store without a path is rejected")
        void emptyPath() {
            var stores = new TreeMap<String, StoreDigest>(Map.of("", STATUS));

            assertThrows(IllegalArgumentException.class, () -> new DigestSidecar(stores));
        }
    }

    @Nested
    @DisplayName("load")
    class Loading {

        @Test
        @DisplayName("a sidecar that does not exist reads empty")
        void absent() {
            assertEquals(DigestSidecar.empty(), DigestSidecar.load(file()));
        }

        @Test
        @DisplayName("a sidecar file is read as its document")
        void present() throws Exception {
            Files.writeString(file(), DOCUMENT);

            assertEquals(DigestSidecar.of(Map.of("workspace/mcp/status.json", STATUS, "pending/entry-1.json",
                    PENDING)), DigestSidecar.load(file()));
        }

        @Test
        @DisplayName("an empty sidecar file is unreadable, never an empty sidecar")
        void emptyFile() throws Exception {
            var file = Files.write(file(), new byte[0]);

            var refusal = assertThrows(StoreRefusal.class, () -> DigestSidecar.load(file));

            assertAll(
                    () -> assertEquals(Outcome.UNREADABLE, refusal.outcome()),
                    () -> assertEquals(file, refusal.file()));
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @ParameterizedTest(name = "format_version {0}")
        @CsvSource({"2, FORMAT_NEWER", "0, FORMAT_OUTDATED"})
        @DisplayName("a document of another format version is refused as such, with the version found")
        void otherVersion(int version, Outcome outcome) {
            var refusal = assertRefused(outcome, DOCUMENT.replace("\"format_version\":1",
                    "\"format_version\":" + version));

            assertEquals(OptionalInt.of(version), refusal.found());
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', textBlock = """
                undeclared field of the document | "stores":{                   | "note":1,"stores":{
                undeclared field of a digest     | {"size":7,                   | {"size":7,"mtime":1,
                duplicate store                  | "workspace/mcp/status.json"  | "pending/entry-1.json"
                duplicate field of a digest      | {"size":7,                   | {"size":7,"size":7,
                sha256 under another name        | ,"sha256":"bbbb              | ,"sha":"bbbb
                size as string                   | "size":42                    | "size":"42"
                upper-case sha256                | "sha256":"aaaa               | "sha256":"AAAA
                stores as array                  | "stores":{                   | "stores":[],"x":{
                """)
        @DisplayName("a document outside its one shape is refused")
        void shape(String name, String valid, String invalid) {
            assertRefused(Outcome.UNREADABLE, DOCUMENT.replace(valid, invalid));
        }

        @Test
        @DisplayName("a document without its stores is refused")
        void missingStores() {
            assertRefused(Outcome.UNREADABLE, "{\"format_version\":1}");
        }
    }
}
