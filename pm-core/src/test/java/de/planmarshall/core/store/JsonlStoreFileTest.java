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
import static org.easymock.EasyMock.newCapture;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
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
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import de.planmarshall.core.log.CoreLogMessages.WARN;
import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.InternalFaultException.Reason;
import de.planmarshall.core.store.JsonlStoreFile.Accepted;
import de.planmarshall.core.store.JsonlStoreFile.Content;
import de.planmarshall.core.store.JsonlStoreFile.Line;
import de.planmarshall.core.store.JsonlStoreFile.Refused;
import de.planmarshall.core.store.StoreRefusal.Outcome;

/**
 * The append path of a JSONL store (PM-IMPL-7, PM-IMPL-2): appended only under the lock of the store, a torn tail
 * repaired at the tail only, with one audit record, and a refused record reported with its line number and never
 * rewritten.
 * <p>
 * A torn tail is what an append stopped mid-write leaves: the file is written here as such a crash leaves it.
 */
@EnableTestLogger
@EnableGeneratorController
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

    private String project;
    private String plan;
    private String otherPlan;
    private Path store;
    private LockKey scopeKey;
    private RecordingLockManager locks;
    private Capture<AuditEvent> auditEvents;
    private AuditSink audit;
    private JsonlStoreFile storeFile;

    @BeforeEach
    void createStore() throws IOException {
        project = "project-" + Generators.longs(1, Long.MAX_VALUE).next();
        plan = "plan-" + Generators.longs(1, Long.MAX_VALUE).next();
        otherPlan = "other-" + Generators.longs(1, Long.MAX_VALUE).next();
        store = Files.createDirectories(base.resolve("plans").resolve("store-substrate").resolve("mcp"))
                .resolve("findings.jsonl");
        scopeKey = LockKey.plan(base, project, plan);
        locks = new RecordingLockManager();
        auditEvents = newCapture(CaptureType.ALL);
        audit = AuditSinkDoubles.capturing(auditEvents);
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
        var fault = assertThrows(InternalFaultException.class, access::run,
                "the access without the lock is refused");

        assertAll("an access without the lock of the store",
                () -> assertEquals(Reason.STORE_LOCK_NOT_HELD, fault.reason(),
                        "the fault names the missing lock"),
                () -> assertEquals(store.toString(), fault.details().get("store"),
                        "the fault names the store path"),
                () -> assertArrayEquals(before, Files.readAllBytes(store), "nothing was written"),
                () -> assertEquals(List.of(), auditEvents.getValues(), "nothing was recorded"));
    }

    @Nested
    @DisplayName("append")
    class Appending {

        @Test
        @DisplayName("a store whose last line is complete is appended to without an audit record, earlier bytes kept")
        void completeTail() throws Exception {
            var before = bytes(FIRST, SECOND);
            Files.write(store, before);

            appendUnderLock(bytes(THIRD));

            var after = Files.readAllBytes(store);
            assertAll("an append to a store with a complete last line",
                    () -> assertArrayEquals(before, Arrays.copyOf(after, before.length), "earlier bytes unchanged"),
                    () -> assertArrayEquals(bytes(FIRST, SECOND, THIRD), after,
                            "the new block is appended"),
                    () -> assertEquals(List.of(), auditEvents.getValues(), "no audit record is written"));
        }

        @Test
        @DisplayName("a torn last line is cut off before the append, with one jsonl_tail_repaired record of its bytes")
        void tornTail() throws Exception {
            Files.write(store, bytes(FIRST, SECOND, TORN));
            var torn = bytes(TORN);

            appendUnderLock(bytes(THIRD));

            assertAll("an append over a torn tail",
                    () -> assertArrayEquals(bytes(FIRST, SECOND, THIRD), Files.readAllBytes(store),
                            "the torn tail is cut before the append"),
                    () -> assertEquals(List.of(AuditSinkDoubles.tailRepairWithoutId(store, torn)),
                            AuditSinkDoubles.withoutTransactionIds(auditEvents.getValues()),
                            "one tail repair records the torn bytes"),
                    () -> assertTrue(TransactionId.ofSuffix("." + auditEvents.getValues().getFirst().details()
                            .get(AuditEvent.DETAIL_TRANSACTION_ID)).isPresent(), "the repair carries an id"),
                    () -> assertFalse(Files.exists(storeFile.auditIntentFile()), "the intent was closed"),
                    () -> assertEquals((long) torn.length,
                            auditEvents.getValues().getFirst().details().get(AuditEvent.DETAIL_TRUNCATED_BYTES),
                            "the record names the truncated byte count"),
                    () -> assertEquals(sha256(torn),
                            auditEvents.getValues().getFirst().details().get(AuditEvent.DETAIL_TRUNCATED_SHA256),
                            "the record names the truncated digest"));
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

            assertAll("an append over a torn tail longer than one chunk",
                    () -> assertArrayEquals(bytes(FIRST, SECOND), Files.readAllBytes(store),
                            "the long torn tail is cut back"),
                    () -> assertEquals(List.of(AuditSinkDoubles.tailRepairWithoutId(store, torn)),
                            AuditSinkDoubles.withoutTransactionIds(auditEvents.getValues()),
                            "the record names the whole torn tail"));
        }

        @Test
        @DisplayName("a store that is only a torn line is cut back to empty")
        void onlyTornLine() throws Exception {
            Files.write(store, bytes(TORN));

            appendUnderLock(bytes(FIRST));

            assertAll("an append to a store that is only a torn line",
                    () -> assertArrayEquals(bytes(FIRST), Files.readAllBytes(store),
                            "the store holds only the new line"),
                    () -> assertEquals(List.of(AuditSinkDoubles.tailRepairWithoutId(store, bytes(TORN))),
                            AuditSinkDoubles.withoutTransactionIds(auditEvents.getValues()),
                            "the record names the torn line"));
        }

        @Test
        @DisplayName("the first append creates the store with mode 0600")
        void createsStore() {
            appendUnderLock(bytes(FIRST, SECOND));

            assertAll("the first append of a store",
                    () -> assertArrayEquals(bytes(FIRST, SECOND), Files.readAllBytes(store),
                            "the store holds both lines"),
                    () -> assertEquals("rw-------",
                            PosixFilePermissions.toString(Files.getPosixFilePermissions(store)),
                            "the store is created with mode 0600"),
                    () -> assertEquals(List.of(), auditEvents.getValues(), "no audit record is written"));
        }

        @Test
        @DisplayName("an append without the lock of the store is refused and writes and repairs nothing")
        void withoutLock() throws Exception {
            var before = bytes(FIRST, TORN);
            Files.write(store, before);

            try (var transaction = locks.openTransaction()) {
                assertRefusedWithoutLock(before, () -> storeFile.append(transaction, bytes(SECOND), audit));
            }
        }

        @Test
        @DisplayName("an append under another key than the key of the store is refused")
        void underAnotherKey() throws Exception {
            var before = bytes(FIRST);
            Files.write(store, before);

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(LockKey.plan(base, project, otherPlan));
                assertRefusedWithoutLock(before, () -> storeFile.append(transaction, bytes(SECOND), audit));
            }
        }

        @Test
        @DisplayName("a block without a trailing newline is refused and nothing is written, not even a repair")
        void blockWithoutNewline() throws Exception {
            var before = bytes(FIRST, TORN);
            Files.write(store, before);
            var block = bytes(SECOND.strip());

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scopeKey);
                assertThrows(IllegalArgumentException.class, () -> storeFile.append(transaction, block, audit),
                        "a block without newline is refused");
            }

            assertAll("a refused block without a trailing newline",
                    () -> assertArrayEquals(before, Files.readAllBytes(store),
                            "nothing is written or repaired"),
                    () -> assertEquals(List.of(), auditEvents.getValues(), "no audit record is written"));
        }

        @Test
        @DisplayName("an empty block is refused and nothing is written")
        void emptyBlock() throws Exception {
            var before = bytes(FIRST);
            Files.write(store, before);
            var empty = new byte[0];

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scopeKey);
                assertThrows(IllegalArgumentException.class, () -> storeFile.append(transaction, empty, audit),
                        "an empty block is refused");
            }

            assertArrayEquals(before, Files.readAllBytes(store), "nothing is written by the refusal");
        }

        @Test
        @DisplayName("a malformed complete line before the tail is kept byte for byte by a repairing append")
        void malformedLineKept() throws Exception {
            var malformed = "{\"format_version\":1,\"n\":\n";
            var kept = bytes(FIRST, malformed, SECOND);
            var stored = new ByteArrayOutputStream();
            stored.writeBytes(kept);
            stored.writeBytes(bytes(TORN));
            Files.write(store, stored.toByteArray());

            appendUnderLock(bytes(THIRD));

            var after = Files.readAllBytes(store);
            assertAll("a repairing append over a malformed complete line",
                    () -> assertArrayEquals(kept, Arrays.copyOf(after, kept.length), "lines before the tail kept"),
                    () -> assertArrayEquals(bytes(FIRST, malformed, SECOND, THIRD), after,
                            "the malformed line is kept and appended"),
                    () -> assertEquals(1, auditEvents.getValues().size(), "one tail repair is recorded"));
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

            assertAll("a repair of an absent store",
                    () -> assertFalse(Files.exists(store), "the store stays absent"),
                    () -> assertEquals(List.of(), auditEvents.getValues(), "no audit record is written"));
        }

        @Test
        @DisplayName("an empty store is left as it is, and nothing is recorded")
        void emptyStore() throws Exception {
            Files.write(store, new byte[0]);

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scopeKey);
                storeFile.repairTail(transaction, audit);
            }

            assertAll("a repair of an empty store",
                    () -> assertEquals(0, Files.size(store), "the store stays empty"),
                    () -> assertEquals(List.of(), auditEvents.getValues(), "no audit record is written"));
        }

        @Test
        @DisplayName("a repair cuts the torn tail only, without appending anything")
        void repairsOnly() throws Exception {
            Files.write(store, bytes(FIRST, TORN));

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scopeKey);
                storeFile.repairTail(transaction, audit);
            }

            assertAll("a repair that cuts the torn tail only",
                    () -> assertArrayEquals(bytes(FIRST), Files.readAllBytes(store),
                            "only the torn tail is cut"),
                    () -> assertEquals(List.of(AuditSinkDoubles.tailRepairWithoutId(store, bytes(TORN))),
                            AuditSinkDoubles.withoutTransactionIds(auditEvents.getValues()),
                            "the record names the torn line"));
        }

        @Test
        @DisplayName("a repair without the lock of the store is refused and cuts nothing")
        void withoutLock() throws Exception {
            var before = bytes(FIRST, TORN);
            Files.write(store, before);

            try (var transaction = locks.openTransaction()) {
                assertRefusedWithoutLock(before, () -> storeFile.repairTail(transaction, audit));
            }
        }
    }

    /** The record of a tail repair is delivered at least once: the intent file beside the store owes it. */
    @Nested
    @DisplayName("audit delivery of a repair")
    class RepairDelivery {

        private void repairWith(AuditSink sink) {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scopeKey);
                storeFile.repairTail(transaction, sink);
            }
        }

        @Test
        @DisplayName("a sink that refuses the record keeps the intent; the next repair delivers it exactly once")
        void sinkRefuses() throws Exception {
            Files.write(store, bytes(FIRST, TORN));
            Capture<AuditEvent> refused = newCapture(CaptureType.ALL);
            var refusing = AuditSinkDoubles.refusing(refused);

            repairWith(refusing);

            assertAll("a sink that refuses the tail repair record",
                    () -> assertArrayEquals(bytes(FIRST), Files.readAllBytes(store), "the cut is done"),
                    () -> assertTrue(Files.exists(storeFile.auditIntentFile()), "the intent survives"),
                    () -> assertEquals(List.of(), refused.getValues(), "the refusing sink recorded nothing"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.AUDIT_RECORD_DEFERRED.resolveIdentifierString()));

            appendUnderLock(bytes(SECOND));

            assertAll("the next repair after the refusal",
                    () -> assertArrayEquals(bytes(FIRST, SECOND), Files.readAllBytes(store),
                            "the cut stays and the append lands"),
                    () -> assertEquals(List.of(AuditSinkDoubles.tailRepairWithoutId(store, bytes(TORN))),
                            AuditSinkDoubles.withoutTransactionIds(auditEvents.getValues()), "delivered exactly once"),
                    () -> assertFalse(Files.exists(storeFile.auditIntentFile()), "the intent was closed"));
        }

        @Test
        @DisplayName("a replay after the record was delivered delivers no second record")
        void noSecondRecord() throws Exception {
            Files.write(store, bytes(FIRST, TORN));
            repairWith(AuditSinkDoubles.refusing());
            repairWith(audit);

            repairWith(audit);

            assertEquals(1, auditEvents.getValues().size(), "the record is delivered exactly once");
        }

        @Test
        @DisplayName("a process stopped between the cut and the append delivers the record at the next repair")
        void stoppedBeforeAppend() throws Exception {
            Files.write(store, bytes(FIRST, TORN));
            var stopping = AuditSinkDoubles.stopping();

            assertThrows(AuditSinkDoubles.ProcessStopped.class, () -> repairWith(stopping),
                    "the repair stops before its append");
            var intentSurvived = Files.exists(storeFile.auditIntentFile());
            repairWith(audit);

            assertAll("a repair stopped before its append",
                    () -> assertTrue(intentSurvived, "the intent survived the stop"),
                    () -> assertArrayEquals(bytes(FIRST), Files.readAllBytes(store),
                            "the cut is done before the stop"),
                    () -> assertEquals(List.of(AuditSinkDoubles.tailRepairWithoutId(store, bytes(TORN))),
                            AuditSinkDoubles.withoutTransactionIds(auditEvents.getValues()),
                            "the record is delivered once at the next repair"));
        }

        @Test
        @DisplayName("a repair stopped between its intent and its cut is completed once, with the id of its intent")
        void stoppedBeforeCut() throws Exception {
            var stopping = AuditSinkDoubles.stopping();
            Files.write(store, bytes(FIRST, TORN));
            assertThrows(AuditSinkDoubles.ProcessStopped.class, () -> repairWith(stopping),
                    "the repair stops before its cut");
            var owedId = auditIntentTransactionId();
            Files.write(store, bytes(FIRST, TORN));

            repairWith(audit);

            assertAll("a repair stopped between its intent and its cut",
                    () -> assertArrayEquals(bytes(FIRST), Files.readAllBytes(store),
                            "the torn tail is cut once"),
                    () -> assertEquals(1, auditEvents.getValues().size(), "one record for one cut"),
                    () -> assertEquals(owedId,
                            auditEvents.getValues().getFirst().details().get(AuditEvent.DETAIL_TRANSACTION_ID),
                            "the record keeps the id of its intent"));
        }

        @Test
        @DisplayName("an unreadable intent file is refused naming it, and nothing is cut")
        void unreadableIntent() throws Exception {
            var before = bytes(FIRST, TORN);
            Files.write(store, before);
            Files.writeString(storeFile.auditIntentFile(), "{\"format_version\":1,");

            var refusal = assertThrows(StoreRefusal.class, () -> repairWith(audit),
                    "the unreadable intent is refused");

            assertAll("an unreadable audit intent",
                    () -> assertEquals(storeFile.auditIntentFile(), refusal.file(),
                            "the refusal names the intent file"),
                    () -> assertArrayEquals(before, Files.readAllBytes(store),
                            "the store is not cut on refusal"),
                    () -> assertEquals(List.of(), auditEvents.getValues(), "nothing is recorded on the refusal"));
        }

        private Object auditIntentTransactionId() throws IOException {
            var intent = Files.readString(storeFile.auditIntentFile());
            var marker = "\"transaction_id\":\"";
            var start = intent.indexOf(marker) + marker.length();
            return intent.substring(start, intent.indexOf('"', start));
        }
    }

    @Nested
    @DisplayName("read and snapshot")
    class Reading {

        @Test
        @DisplayName("a torn tail is reported by its offset and never as a record, and neither read repairs it")
        void tornTailReported() throws Exception {
            var before = bytes(FIRST, SECOND, TORN);
            Files.write(store, before);
            var expected = new Content(store, List.of(new Line(1, bytes(FIRST.strip())),
                    new Line(2, bytes(SECOND.strip()))), OptionalLong.of(bytes(FIRST, SECOND).length));

            var locked = readUnderLock();
            var lockFree = storeFile.snapshot();

            assertAll("a torn tail reported by offset",
                    () -> assertEquals(expected, locked, "the locked read sees two lines"),
                    () -> assertEquals(expected, lockFree, "the lock-free read sees two lines"),
                    () -> assertArrayEquals(before, Files.readAllBytes(store), "nothing was repaired"),
                    () -> assertEquals(List.of(), auditEvents.getValues(), "no audit record is written"));
        }

        @Test
        @DisplayName("a store that ends with a newline has no torn tail")
        void completeStore() throws Exception {
            Files.write(store, bytes(FIRST));

            var content = storeFile.snapshot();

            assertAll("a store that ends with a newline",
                    () -> assertEquals(List.of(new Line(1, bytes(FIRST.strip()))), content.lines(),
                            "the store has one complete line"),
                    () -> assertEquals(OptionalLong.empty(), content.tornTailOffset(),
                            "the store has no torn tail"));
        }

        @Test
        @DisplayName("a store that does not exist reads without lines and without a torn tail")
        void absentStore() {
            var expected = new Content(store, List.of(), OptionalLong.empty());

            assertAll("a store that does not exist",
                    () -> assertEquals(expected, storeFile.snapshot(), "the snapshot has no lines"),
                    () -> assertEquals(expected, readUnderLock(), "the locked read has no lines"));
        }

        @Test
        @DisplayName("a read under the lock is refused without the lock of the store")
        void readWithoutLock() throws Exception {
            var before = bytes(FIRST);
            Files.write(store, before);

            try (var transaction = locks.openTransaction()) {
                assertRefusedWithoutLock(before, () -> storeFile.read(transaction));
            }
        }

        @Test
        @DisplayName("the snapshot takes no lock")
        void snapshotTakesNoLock() throws Exception {
            Files.write(store, bytes(FIRST));

            storeFile.snapshot();

            assertEquals(0, locks.transactions(), "the snapshot takes no lock");
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
        void refusedLine(String name, String line, Outcome outcome, int found) throws Exception {
            var before = bytes(FIRST, line, "\n", THIRD);
            Files.write(store, before);

            var entries = storeFile.snapshot().decode(FORMAT_VERSION, AS_TEXT);

            assertInstanceOf(Refused.class, entries.get(1), "the refused line stays an entry");
            var refused = (Refused<String>) entries.get(1);
            assertAll("a refused line keeps its position and names the refusal",
                    () -> assertEquals(3, entries.size(), "the store yields three entries"),
                    () -> assertEquals(new Accepted<>(1, FIRST.strip()), entries.getFirst(),
                            "the first line is accepted"),
                    () -> assertEquals(2, refused.line(), "the refusal names line two"),
                    () -> assertEquals(outcome, refused.refusal().outcome(),
                            "the refusal carries the expected outcome"),
                    () -> assertEquals(store, refused.refusal().file(), "the refusal names the store"),
                    () -> assertEquals(found < 0 ? OptionalInt.empty() : OptionalInt.of(found),
                            refused.refusal().found(),
                            "the refusal carries the found version"),
                    () -> assertEquals(FORMAT_VERSION, refused.refusal().current(),
                            "the refusal carries the current version"),
                    () -> assertEquals(new Accepted<>(3, THIRD.strip()), entries.get(2),
                            "the third line is accepted"),
                    () -> assertArrayEquals(before, Files.readAllBytes(store), "the store was not rewritten"));
        }

        @Test
        @DisplayName("a record of format_version 2 reads format_newer naming the file")
        void formatNewerNamesFile() throws Exception {
            Files.write(store, bytes("{\"format_version\":2,\"n\":1}\n"));

            var entry = storeFile.snapshot().decode(FORMAT_VERSION, AS_TEXT).getFirst();

            assertInstanceOf(Refused.class, entry, "the newer record is refused");
            var refused = (Refused<String>) entry;
            assertAll("a format version 2 record",
                    () -> assertEquals(Outcome.FORMAT_NEWER, refused.refusal().outcome(),
                            "the refusal is format newer"),
                    () -> assertTrue(refused.refusal().getMessage().contains(store.toString()),
                            "the message names the file"));
        }

        @Test
        @DisplayName("a record the decoder refuses is reported with its line number")
        void decoderRefusal() throws Exception {
            Files.write(store, bytes(FIRST, SECOND));
            var refusal = StoreRefusal.unreadable(store, FORMAT_VERSION, Generators.letterStrings(5, 20).next(),
                    null);

            var entries = storeFile.snapshot().decode(FORMAT_VERSION, recordBytes -> {
                if (AS_TEXT.apply(recordBytes).contains("\"n\":2")) {
                    throw refusal;
                }
                return AS_TEXT.apply(recordBytes);
            });

            assertEquals(List.of(new Accepted<>(1, FIRST.strip()), new Refused<>(2, refusal)), entries,
                    "the decoder refusal names line two");
        }

        @Test
        @DisplayName("a decoder that answers nothing for a record is a fault of the decoder")
        void decoderAnswersNothing() throws Exception {
            Files.write(store, bytes(FIRST));
            var content = storeFile.snapshot();

            assertThrows(NullPointerException.class, () -> content.decode(FORMAT_VERSION, _ -> null),
                    "a decoder answering null is a fault");
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

            assertArrayEquals(bytes(FIRST.strip()), line.content(), "the line hands out a defensive copy");
        }

        @Test
        @DisplayName("two lines are equal by number and bytes")
        void equality() {
            var line = new Line(1, bytes(FIRST.strip()));

            assertAll("two lines compared by number and bytes",
                    () -> assertEquals(new Line(1, bytes(FIRST.strip())), line, "equal lines are equal"),
                    () -> assertEquals(new Line(1, bytes(FIRST.strip())).hashCode(), line.hashCode(),
                            "equal lines share their hash code"),
                    () -> assertNotEquals(new Line(2, bytes(FIRST.strip())), line,
                            "a different number is not equal"),
                    () -> assertNotEquals(new Line(1, bytes(SECOND.strip())), line,
                            "different bytes are not equal"),
                    () -> assertNotEquals(Map.of(), line, "another type is not equal"),
                    () -> assertEquals("Line[number=1, %d bytes]".formatted(FIRST.strip().length()),
                            line.toString(), "the text names the number and length"));
        }

        @Test
        @DisplayName("line numbers count from 1")
        void numberFromOne() {
            var content = bytes(FIRST.strip());

            assertThrows(IllegalArgumentException.class, () -> new Line(0, content),
                    "line numbers count from one");
        }
    }

    @Test
    @DisplayName("the store path is kept absolute and normalized")
    void normalizedStore() {
        var relative = Path.of("plans", "..", "plans", "findings.jsonl");

        var file = new JsonlStoreFile(relative, scopeKey);

        assertEquals(Path.of("plans", "findings.jsonl").toAbsolutePath(), file.store(),
                "the store path is absolute and normalized");
    }

    /** The helpers of this test take the lock through the lock manager, so a test sees which key was asked for. */
    @Test
    @DisplayName("an append under the lock acquires only the key of the store")
    void acquiresStoreKeyOnly() {
        appendUnderLock(bytes(FIRST));

        assertAll("an append under the lock of the store",
                () -> assertEquals(List.of(scopeKey), locks.acquisitions(),
                        "only the store key is acquired"),
                () -> assertEquals(List.of(), locks.held(), "no lock is still held after append"));
    }
}
