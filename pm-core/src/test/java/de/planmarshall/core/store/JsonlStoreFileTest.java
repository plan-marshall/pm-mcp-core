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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.Function;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.InternalFaultException.Reason;
import de.planmarshall.core.store.JsonlStoreFile.Accepted;
import de.planmarshall.core.store.JsonlStoreFile.Content;
import de.planmarshall.core.store.JsonlStoreFile.Line;
import de.planmarshall.core.store.JsonlStoreFile.Refused;
import de.planmarshall.core.store.StoreRefusal.Outcome;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The append path of a JSONL store (PM-IMPL-7, PM-IMPL-2): appended only under the lock of the store, a torn tail
 * repaired at the tail only, with one audit record, and a refused record reported with its line number and never
 * rewritten.
 * <p>
 * A torn tail is what an append stopped mid-write leaves: the file is written here as such a crash leaves it.
 */
@DisplayName("JSONL store file")
class JsonlStoreFileTest {

    private static final int FORMAT_VERSION = 1;
    private static final String FIRST = "{\"format_version\":1,\"n\":1}\n";
    private static final String SECOND = "{\"format_version\":1,\"n\":2}\n";
    private static final String THIRD = "{\"format_version\":1,\"n\":3}\n";
    private static final String TORN = "{\"format_version\":1,\"n\":";
    private static final Function<byte[], String> AS_TEXT = bytes -> new String(bytes, UTF_8);

    @TempDir
    Path base;

    private Path store;
    private LockKey scopeKey;
    private RecordingLockManager locks;
    private RecordingAuditSink audit;
    private JsonlStoreFile storeFile;

    @BeforeEach
    void createStore() throws IOException {
        store = Files.createDirectories(base.resolve("plans").resolve("store-substrate").resolve("mcp"))
                .resolve("findings.jsonl");
        scopeKey = LockKey.plan(base, "pm-mcp-core", "store-substrate");
        locks = new RecordingLockManager();
        audit = new RecordingAuditSink();
        storeFile = new JsonlStoreFile(store, scopeKey);
    }

    private static byte[] bytes(String... parts) {
        return String.join("", parts).getBytes(UTF_8);
    }

    private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private void appendUnderLock(byte[] block) {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(scopeKey);
            storeFile.append(transaction, block, audit);
        }
    }

    private Content readUnderLock() {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(scopeKey);
            return storeFile.read(transaction);
        }
    }

    private void assertRefusedWithoutLock(byte[] before, Runnable access) {
        var fault = assertThrows(InternalFaultException.class, access::run);

        assertAll(
                () -> assertEquals(Reason.STORE_LOCK_NOT_HELD, fault.reason()),
                () -> assertEquals(store.toString(), fault.details().get("store")),
                () -> assertArrayEquals(before, Files.readAllBytes(store), "nothing was written"),
                () -> assertEquals(List.of(), audit.events(), "nothing was recorded"));
    }

    @Nested
    @DisplayName("append")
    class Appending {

        @Test
        @DisplayName("a store whose last line is complete is appended to without an audit record, earlier bytes kept")
        void completeTail() throws IOException {
            var before = bytes(FIRST, SECOND);
            Files.write(store, before);

            appendUnderLock(bytes(THIRD));

            var after = Files.readAllBytes(store);
            assertAll(
                    () -> assertArrayEquals(before, Arrays.copyOf(after, before.length), "earlier bytes unchanged"),
                    () -> assertArrayEquals(bytes(FIRST, SECOND, THIRD), after),
                    () -> assertEquals(List.of(), audit.events()));
        }

        @Test
        @DisplayName("a torn last line is cut off before the append, with one jsonl_tail_repaired record of its bytes")
        void tornTail() throws Exception {
            Files.write(store, bytes(FIRST, SECOND, TORN));
            var torn = bytes(TORN);

            appendUnderLock(bytes(THIRD));

            assertAll(
                    () -> assertArrayEquals(bytes(FIRST, SECOND, THIRD), Files.readAllBytes(store)),
                    () -> assertEquals(List.of(AuditEvent.jsonlTailRepaired(store, torn.length, sha256(torn))),
                            audit.events()),
                    () -> assertEquals((long) torn.length,
                            audit.events().getFirst().details().get(AuditEvent.DETAIL_TRUNCATED_BYTES)),
                    () -> assertEquals(sha256(torn),
                            audit.events().getFirst().details().get(AuditEvent.DETAIL_TRUNCATED_SHA256)));
        }

        @Test
        @DisplayName("a torn tail longer than one scanned chunk is cut back to the last complete line")
        void longTornTail() throws Exception {
            var torn = new byte[20_000];
            Arrays.fill(torn, (byte) 'x');
            var stored = new ByteArrayOutputStream();
            stored.writeBytes(bytes(FIRST));
            stored.writeBytes(torn);
            Files.write(store, stored.toByteArray());

            appendUnderLock(bytes(SECOND));

            assertAll(
                    () -> assertArrayEquals(bytes(FIRST, SECOND), Files.readAllBytes(store)),
                    () -> assertEquals(List.of(AuditEvent.jsonlTailRepaired(store, torn.length, sha256(torn))),
                            audit.events()));
        }

        @Test
        @DisplayName("a store that is only a torn line is cut back to empty")
        void onlyTornLine() throws Exception {
            Files.write(store, bytes(TORN));

            appendUnderLock(bytes(FIRST));

            assertAll(
                    () -> assertArrayEquals(bytes(FIRST), Files.readAllBytes(store)),
                    () -> assertEquals(List.of(AuditEvent.jsonlTailRepaired(store, TORN.length(),
                            sha256(bytes(TORN)))), audit.events()));
        }

        @Test
        @DisplayName("the first append creates the store with mode 0600")
        void createsStore() throws IOException {
            appendUnderLock(bytes(FIRST, SECOND));

            assertAll(
                    () -> assertArrayEquals(bytes(FIRST, SECOND), Files.readAllBytes(store)),
                    () -> assertEquals("rw-------",
                            PosixFilePermissions.toString(Files.getPosixFilePermissions(store))),
                    () -> assertEquals(List.of(), audit.events()));
        }

        @Test
        @DisplayName("an append without the lock of the store is refused and writes and repairs nothing")
        void withoutLock() throws IOException {
            var before = bytes(FIRST, TORN);
            Files.write(store, before);

            try (var transaction = locks.openTransaction()) {
                assertRefusedWithoutLock(before, () -> storeFile.append(transaction, bytes(SECOND), audit));
            }
        }

        @Test
        @DisplayName("an append under another key than the key of the store is refused")
        void underAnotherKey() throws IOException {
            var before = bytes(FIRST);
            Files.write(store, before);

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(LockKey.plan(base, "pm-mcp-core", "another-plan"));
                assertRefusedWithoutLock(before, () -> storeFile.append(transaction, bytes(SECOND), audit));
            }
        }

        @Test
        @DisplayName("a block without a trailing newline is refused and nothing is written, not even a repair")
        void blockWithoutNewline() throws IOException {
            var before = bytes(FIRST, TORN);
            Files.write(store, before);
            var block = bytes(SECOND.strip());

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scopeKey);
                assertThrows(IllegalArgumentException.class, () -> storeFile.append(transaction, block, audit));
            }

            assertAll(
                    () -> assertArrayEquals(before, Files.readAllBytes(store)),
                    () -> assertEquals(List.of(), audit.events()));
        }

        @Test
        @DisplayName("an empty block is refused and nothing is written")
        void emptyBlock() throws IOException {
            var before = bytes(FIRST);
            Files.write(store, before);
            var empty = new byte[0];

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scopeKey);
                assertThrows(IllegalArgumentException.class, () -> storeFile.append(transaction, empty, audit));
            }

            assertArrayEquals(before, Files.readAllBytes(store));
        }

        @Test
        @DisplayName("a malformed complete line before the tail is kept byte for byte by a repairing append")
        void malformedLineKept() throws IOException {
            var malformed = "{\"format_version\":1,\"n\":\n";
            var kept = bytes(FIRST, malformed, SECOND);
            var stored = new ByteArrayOutputStream();
            stored.writeBytes(kept);
            stored.writeBytes(bytes(TORN));
            Files.write(store, stored.toByteArray());

            appendUnderLock(bytes(THIRD));

            var after = Files.readAllBytes(store);
            assertAll(
                    () -> assertArrayEquals(kept, Arrays.copyOf(after, kept.length), "lines before the tail kept"),
                    () -> assertArrayEquals(bytes(FIRST, malformed, SECOND, THIRD), after),
                    () -> assertEquals(1, audit.events().size()));
        }
    }

    @Nested
    @DisplayName("repairTail")
    class RepairingTail {

        @Test
        @DisplayName("a store that does not exist is left absent, and nothing is recorded")
        void absentStore() {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scopeKey);
                storeFile.repairTail(transaction, audit);
            }

            assertAll(
                    () -> assertFalse(Files.exists(store)),
                    () -> assertEquals(List.of(), audit.events()));
        }

        @Test
        @DisplayName("an empty store is left as it is, and nothing is recorded")
        void emptyStore() throws IOException {
            Files.write(store, new byte[0]);

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scopeKey);
                storeFile.repairTail(transaction, audit);
            }

            assertAll(
                    () -> assertEquals(0, Files.size(store)),
                    () -> assertEquals(List.of(), audit.events()));
        }

        @Test
        @DisplayName("a repair cuts the torn tail only, without appending anything")
        void repairsOnly() throws Exception {
            Files.write(store, bytes(FIRST, TORN));

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scopeKey);
                storeFile.repairTail(transaction, audit);
            }

            assertAll(
                    () -> assertArrayEquals(bytes(FIRST), Files.readAllBytes(store)),
                    () -> assertEquals(List.of(AuditEvent.jsonlTailRepaired(store, TORN.length(),
                            sha256(bytes(TORN)))), audit.events()));
        }

        @Test
        @DisplayName("a repair without the lock of the store is refused and cuts nothing")
        void withoutLock() throws IOException {
            var before = bytes(FIRST, TORN);
            Files.write(store, before);

            try (var transaction = locks.openTransaction()) {
                assertRefusedWithoutLock(before, () -> storeFile.repairTail(transaction, audit));
            }
        }
    }

    @Nested
    @DisplayName("read and snapshot")
    class Reading {

        @Test
        @DisplayName("a torn tail is reported by its offset and never as a record, and neither read repairs it")
        void tornTailReported() throws IOException {
            var before = bytes(FIRST, SECOND, TORN);
            Files.write(store, before);
            var expected = new Content(store, List.of(new Line(1, bytes(FIRST.strip())),
                    new Line(2, bytes(SECOND.strip()))), OptionalLong.of(bytes(FIRST, SECOND).length));

            var locked = readUnderLock();
            var lockFree = storeFile.snapshot();

            assertAll(
                    () -> assertEquals(expected, locked),
                    () -> assertEquals(expected, lockFree),
                    () -> assertArrayEquals(before, Files.readAllBytes(store), "nothing was repaired"),
                    () -> assertEquals(List.of(), audit.events()));
        }

        @Test
        @DisplayName("a store that ends with a newline has no torn tail")
        void completeStore() throws IOException {
            Files.write(store, bytes(FIRST));

            var content = storeFile.snapshot();

            assertAll(
                    () -> assertEquals(List.of(new Line(1, bytes(FIRST.strip()))), content.lines()),
                    () -> assertEquals(OptionalLong.empty(), content.tornTailOffset()));
        }

        @Test
        @DisplayName("a store that does not exist reads without lines and without a torn tail")
        void absentStore() {
            var expected = new Content(store, List.of(), OptionalLong.empty());

            assertAll(
                    () -> assertEquals(expected, storeFile.snapshot()),
                    () -> assertEquals(expected, readUnderLock()));
        }

        @Test
        @DisplayName("a read under the lock is refused without the lock of the store")
        void readWithoutLock() throws IOException {
            var before = bytes(FIRST);
            Files.write(store, before);

            try (var transaction = locks.openTransaction()) {
                assertRefusedWithoutLock(before, () -> storeFile.read(transaction));
            }
        }

        @Test
        @DisplayName("the snapshot takes no lock")
        void snapshotTakesNoLock() throws IOException {
            Files.write(store, bytes(FIRST));

            storeFile.snapshot();

            assertEquals(0, locks.transactions());
        }
    }

    @Nested
    @DisplayName("decode")
    class Decoding {

        /**
         * A refused line stays an entry with its line number, the refusal names the store, and the lines around it
         * are decoded. Nothing is rewritten by the read.
         */
        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', textBlock = """
                malformed complete line      | '{"format_version":1,"n":'  | UNREADABLE      | -1
                no format_version            | '{"n":2}'                   | UNREADABLE      | -1
                blank line                   | ''                          | UNREADABLE      | -1
                format_version 2             | '{"format_version":2,"n":2}' | FORMAT_NEWER    | 2
                format_version 0             | '{"format_version":0,"n":2}' | FORMAT_OUTDATED | 0
                """)
        void refusedLine(String name, String line, Outcome outcome, int found) throws IOException {
            var before = bytes(FIRST, line, "\n", THIRD);
            Files.write(store, before);

            var entries = storeFile.snapshot().decode(FORMAT_VERSION, AS_TEXT);

            assertInstanceOf(Refused.class, entries.get(1));
            var refused = (Refused<String>) entries.get(1);
            assertAll(
                    () -> assertEquals(3, entries.size()),
                    () -> assertEquals(new Accepted<>(1, FIRST.strip()), entries.getFirst()),
                    () -> assertEquals(2, refused.line()),
                    () -> assertEquals(outcome, refused.refusal().outcome()),
                    () -> assertEquals(store, refused.refusal().file()),
                    () -> assertEquals(found < 0 ? OptionalInt.empty() : OptionalInt.of(found),
                            refused.refusal().found()),
                    () -> assertEquals(FORMAT_VERSION, refused.refusal().current()),
                    () -> assertEquals(new Accepted<>(3, THIRD.strip()), entries.get(2)),
                    () -> assertArrayEquals(before, Files.readAllBytes(store), "the store was not rewritten"));
        }

        @Test
        @DisplayName("a record of format_version 2 reads format_newer naming the file")
        void formatNewerNamesFile() throws IOException {
            Files.write(store, bytes("{\"format_version\":2,\"n\":1}\n"));

            var entry = storeFile.snapshot().decode(FORMAT_VERSION, AS_TEXT).getFirst();

            assertInstanceOf(Refused.class, entry);
            var refused = (Refused<String>) entry;
            assertAll(
                    () -> assertEquals(Outcome.FORMAT_NEWER, refused.refusal().outcome()),
                    () -> assertTrue(refused.refusal().getMessage().contains(store.toString()),
                            "the message names the file"));
        }

        @Test
        @DisplayName("a record the decoder refuses is reported with its line number")
        void decoderRefusal() throws IOException {
            Files.write(store, bytes(FIRST, SECOND));
            var refusal = StoreRefusal.unreadable(store, FORMAT_VERSION, "'n' must be odd", null);

            var entries = storeFile.snapshot().decode(FORMAT_VERSION, recordBytes -> {
                if (AS_TEXT.apply(recordBytes).contains("\"n\":2")) {
                    throw refusal;
                }
                return AS_TEXT.apply(recordBytes);
            });

            assertEquals(List.of(new Accepted<>(1, FIRST.strip()), new Refused<>(2, refusal)), entries);
        }

        @Test
        @DisplayName("a decoder that answers nothing for a record is a fault of the decoder")
        void decoderAnswersNothing() throws IOException {
            Files.write(store, bytes(FIRST));
            var content = storeFile.snapshot();

            assertThrows(NullPointerException.class, () -> content.decode(FORMAT_VERSION, _ -> null));
        }
    }

    @Nested
    @DisplayName("line")
    class Lines {

        @Test
        @DisplayName("a line keeps a copy of its bytes and hands out a copy")
        void defensiveCopies() {
            var content = bytes(FIRST.strip());
            var line = new Line(1, content);

            content[0] = 'x';
            line.content()[1] = 'x';

            assertArrayEquals(bytes(FIRST.strip()), line.content());
        }

        @Test
        @DisplayName("two lines are equal by number and bytes")
        void equality() {
            var line = new Line(1, bytes(FIRST.strip()));

            assertAll(
                    () -> assertEquals(new Line(1, bytes(FIRST.strip())), line),
                    () -> assertEquals(new Line(1, bytes(FIRST.strip())).hashCode(), line.hashCode()),
                    () -> assertNotEquals(new Line(2, bytes(FIRST.strip())), line),
                    () -> assertNotEquals(new Line(1, bytes(SECOND.strip())), line),
                    () -> assertNotEquals(Map.of(), line),
                    () -> assertEquals("Line[number=1, %d bytes]".formatted(FIRST.strip().length()),
                            line.toString()));
        }

        @Test
        @DisplayName("line numbers count from 1")
        void numberFromOne() {
            var content = bytes(FIRST.strip());

            assertThrows(IllegalArgumentException.class, () -> new Line(0, content));
        }
    }

    @Test
    @DisplayName("the store path is kept absolute and normalized")
    void normalizedStore() {
        var relative = Path.of("plans", "..", "plans", "findings.jsonl");

        var file = new JsonlStoreFile(relative, scopeKey);

        assertEquals(Path.of("plans", "findings.jsonl").toAbsolutePath(), file.store());
    }

    /** The helpers of this test take the lock through the lock manager, so a test sees which key was asked for. */
    @Test
    @DisplayName("an append under the lock acquires only the key of the store")
    void acquiresStoreKeyOnly() {
        appendUnderLock(bytes(FIRST));

        assertAll(
                () -> assertEquals(List.of(scopeKey), locks.acquisitions()),
                () -> assertEquals(List.of(), locks.held()));
    }
}
