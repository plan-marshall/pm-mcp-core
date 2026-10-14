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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.log.CoreLogMessages.WARN;
import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.InternalFaultException.Reason;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.AuditRecord.Category;
import de.planmarshall.core.store.AuditRecord.Outcome;
import de.planmarshall.core.store.AuditRecord.Scope;
import de.planmarshall.core.store.JsonlStoreFile.Accepted;

/**
 * The audit log as a hash chain (PM-SEC-6): every line chains to the SHA-256 of the line before it by the one
 * predecessor rule, is appended only under the audit lock, and calls the anchor around every append; the torn tail of
 * the log itself is cut and its record chained into the log, at least once across a stop on either side of the
 * in-log write and never lost.
 * <p>
 * A torn tail is what an append stopped mid-write leaves: the file is written here as such a crash leaves it.
 */
@EnableTestLogger
@DisplayName("Audit log")
class AuditLogTest {

    private static final Instant AT = Instant.parse("2026-10-14T09:30:15.250Z");
    private static final Clock CLOCK = Clock.fixed(AT, ZoneOffset.UTC);
    private static final String PRINCIPAL = "oliver";
    private static final String ANCHORED = "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210";
    private static final byte[] TORN = "{\"format_version\":1,\"event_id\":\"0189".getBytes(UTF_8);

    @TempDir
    Path base;

    private LockKey auditKey;
    private RecordingLockManager locks;
    private RecordingAnchor anchor;
    private AuditLog log;

    @BeforeEach
    void createLog() throws IOException {
        Files.createDirectories(base.resolve("logs"));
        auditKey = LockKey.auditLog(base);
        locks = new RecordingLockManager();
        anchor = new RecordingAnchor(Optional.empty());
        log = new AuditLog(base, anchor, CLOCK, PRINCIPAL);
    }

    /**
     * The anchor of the tests: it records its calls and the last line of the log it sees before an append, and
     * answers a predecessor it was given.
     */
    private static final class RecordingAnchor implements AuditAnchor {

        private final List<String> calls = new ArrayList<>();
        private final Optional<String> predecessor;
        private RuntimeException failure;
        private AuditRecord lastBefore;
        private AuditRecord appended;
        private String lineHash;

        private RecordingAnchor(Optional<String> predecessor) {
            this.predecessor = predecessor;
        }

        @Override
        public void beforeAppend(LockTransaction transaction, AuditLog auditLog) {
            calls.add("beforeAppend");
            var lines = auditLog.read(transaction).lines();
            lastBefore = lines.isEmpty() ? null
                    : AuditCodec.readRecord(auditLog.logFile(), lines.getLast().content());
            if (failure != null) {
                throw failure;
            }
        }

        @Override
        public Optional<String> emptyLogPredecessor() {
            calls.add("emptyLogPredecessor");
            return predecessor;
        }

        @Override
        public void afterAppend(LockTransaction transaction, AuditLog auditLog, AuditRecord auditRecord, String hash) {
            calls.add("afterAppend");
            appended = auditRecord;
            lineHash = hash;
        }
    }

    /** The anchor whose predecessor cannot be read, so the in-log write of a repair record fails. */
    private static final class FailingPredecessorAnchor implements AuditAnchor {

        @Override
        public void beforeAppend(LockTransaction transaction, AuditLog auditLog) {
            // nothing to anchor
        }

        @Override
        public Optional<String> emptyLogPredecessor() {
            throw new UncheckedIOException(new IOException("the audit index cannot be read"));
        }

        @Override
        public void afterAppend(LockTransaction transaction, AuditLog auditLog, AuditRecord auditRecord, String hash) {
            // nothing to anchor
        }
    }

    private static AuditRecord template(String event) {
        return AuditRecord.builder()
                .eventId(TransactionId.generate(CLOCK).value())
                .prevHash(AuditCodec.GENESIS_HASH)
                .timestamp(AT)
                .category(Category.OPERATOR)
                .event(event)
                .scopeType(Scope.MACHINE)
                .actor(PRINCIPAL)
                .outcome(Outcome.SUCCEEDED)
                .details(Map.of("os_principal", PRINCIPAL))
                .build();
    }

    private static AuditRecord checkpoint() {
        return template(AuditRecord.EVENT_CHECKPOINT).withCategory(Category.SECURITY);
    }

    private AuditRecord append(AuditLog auditLog, AuditRecord auditRecord) {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(auditKey);
            return auditLog.append(transaction, auditRecord);
        }
    }

    private AuditRecord append(AuditRecord auditRecord) {
        return append(log, auditRecord);
    }

    private void tearTail() throws IOException {
        Files.write(log.logFile(), TORN, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private List<byte[]> lines() {
        return log.snapshot().lines().stream().map(JsonlStoreFile.Line::content).toList();
    }

    private List<AuditRecord> records() {
        return lines().stream().map(line -> AuditCodec.readRecord(log.logFile(), line)).toList();
    }

    /** Asserts that every line chains to the line before it, the first to the given hash. */
    private void assertChained(String first) {
        var lines = lines();
        var records = records();
        for (var index = 0; index < lines.size(); index++) {
            var expected = index == 0 ? first : AuditCodec.sha256Hex(lines.get(index - 1));
            assertEquals(expected, records.get(index).prevHash(), "prev_hash of line " + (index + 1));
        }
    }

    /** Stops a repair of the log's own tail after the cut and before its record reached any sink. */
    private void stopAfterCut() {
        var store = new JsonlStoreFile(log.logFile(), auditKey);
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(auditKey);
            var stopping = FailingAuditSink.stopping(1);
            assertThrows(FailingAuditSink.ProcessStopped.class, () -> store.repairTail(transaction, stopping));
        }
    }

    private Path intentFile() {
        return new JsonlStoreFile(log.logFile(), auditKey).auditIntentFile();
    }

    private static Object transactionId(AuditRecord auditRecord) {
        return auditRecord.details().get(AuditEvent.DETAIL_TRANSACTION_ID);
    }

    @Nested
    @DisplayName("chain")
    class Chain {

        @Test
        @DisplayName("the first record of a log that never rotated chains to genesis")
        void genesis() {
            var appended = append(template("first"));

            assertAll(
                    () -> assertEquals(AuditCodec.GENESIS_HASH, appended.prevHash()),
                    () -> assertEquals(List.of(appended), records()));
        }

        @Test
        @DisplayName("every record names the SHA-256 of the previous canonical line")
        void chained() {
            var first = append(template("first"));
            var second = append(template("second"));
            var third = append(template("third"));

            assertAll(
                    () -> assertEquals(List.of(first, second, third), records()),
                    () -> assertEquals(AuditCodec.sha256Hex(lines().get(0)), second.prevHash()),
                    () -> assertEquals(AuditCodec.sha256Hex(lines().get(1)), third.prevHash()),
                    () -> assertChained(AuditCodec.GENESIS_HASH));
        }

        @Test
        @DisplayName("an append without the audit lock is refused and writes nothing")
        void withoutLock() {
            var auditRecord = template("first");

            InternalFaultException fault;
            try (var transaction = locks.openTransaction()) {
                fault = assertThrows(InternalFaultException.class, () -> log.append(transaction, auditRecord));
            }

            assertAll(
                    () -> assertEquals(Reason.STORE_LOCK_NOT_HELD, fault.reason()),
                    () -> assertFalse(Files.exists(log.logFile()), "nothing was written"),
                    () -> assertEquals(List.of(), anchor.calls, "the anchor was not called"));
        }

        @Test
        @DisplayName("the log is the file below logs/ that the leaf key of the audit lock names")
        void logFile() {
            assertAll(
                    () -> assertEquals(base.toAbsolutePath().normalize().resolve("logs").resolve("audit.jsonl"),
                            log.logFile()),
                    () -> assertEquals(auditKey, log.lockKey()),
                    () -> assertSame(CLOCK, log.clock()),
                    () -> assertEquals(PRINCIPAL, log.osPrincipal()));
        }

        @Test
        @DisplayName("a blank OS principal is refused")
        void blankPrincipal() {
            assertThrows(IllegalArgumentException.class, () -> new AuditLog(base, AuditAnchor.NONE, CLOCK, " "));
        }
    }

    @Nested
    @DisplayName("anchor")
    class Anchor {

        @Test
        @DisplayName("is called before and after every append, with the record and the hash of its line")
        void calledInOrder() {
            append(template("first"));
            var second = append(template("second"));

            assertAll(
                    () -> assertEquals(List.of("beforeAppend", "emptyLogPredecessor", "afterAppend", "beforeAppend",
                            "afterAppend"), anchor.calls),
                    () -> assertEquals(second, anchor.appended),
                    () -> assertEquals(AuditCodec.sha256Hex(lines().get(1)), anchor.lineHash));
        }

        @Test
        @DisplayName("an empty log chains its first record to the anchor's predecessor when it has one")
        void emptyLogPredecessor() {
            var anchored = new AuditLog(base, new RecordingAnchor(Optional.of(ANCHORED)), CLOCK, PRINCIPAL);

            var first = append(anchored, template("first"));
            var second = append(anchored, template("second"));

            assertAll(
                    () -> assertEquals(ANCHORED, first.prevHash()),
                    () -> assertEquals(AuditCodec.sha256Hex(lines().getFirst()), second.prevHash()));
        }

        @Test
        @DisplayName("the anchor NONE writes nothing and names no predecessor, so the chain starts at genesis")
        void none() {
            var plain = new AuditLog(base, AuditAnchor.NONE, CLOCK, PRINCIPAL);

            var first = append(plain, template("first"));

            assertAll(
                    () -> assertEquals(AuditCodec.GENESIS_HASH, first.prevHash()),
                    () -> assertEquals(Optional.empty(), AuditAnchor.NONE.emptyLogPredecessor()),
                    () -> assertEquals(List.of("audit.jsonl"), fileNames()));
        }

        private List<String> fileNames() throws IOException {
            try (var files = Files.list(base.resolve("logs"))) {
                return files.map(file -> file.getFileName().toString()).sorted().toList();
            }
        }
    }

    @Nested
    @DisplayName("torn tail")
    class TornTail {

        @Test
        @DisplayName("is cut by the next append and its record chained into the log before the new record")
        void repairedIntoLog() throws Exception {
            var first = append(template("first"));
            tearTail();

            var second = append(template("second"));

            var records = records();
            var repair = records.get(1);
            assertAll(
                    () -> assertEquals(3, records.size()),
                    () -> assertEquals(first, records.get(0)),
                    () -> assertEquals(second, records.get(2)),
                    () -> assertEquals(AuditEvent.JSONL_TAIL_REPAIRED, repair.event()),
                    () -> assertEquals(Category.STORE, repair.category()),
                    () -> assertEquals(Outcome.SUCCEEDED, repair.outcome()),
                    () -> assertEquals(Scope.MACHINE, repair.scopeType()),
                    () -> assertEquals(PRINCIPAL, repair.actor()),
                    () -> assertEquals((long) TORN.length, repair.details().get(AuditEvent.DETAIL_TRUNCATED_BYTES)),
                    () -> assertEquals(StoreSnapshot.sha256(TORN),
                            repair.details().get(AuditEvent.DETAIL_TRUNCATED_SHA256)),
                    () -> assertInstanceOf(String.class, transactionId(repair)),
                    () -> assertFalse(Files.exists(intentFile()), "the intent was closed"),
                    () -> assertChained(AuditCodec.GENESIS_HASH));
        }

        @Test
        @DisplayName("a stop after the cut and before the in-log write re-delivers the record with its id")
        void stopBeforeInLogWrite() throws Exception {
            var first = append(template("first"));
            tearTail();
            stopAfterCut();
            var owed = AuditIntentFile.beside(log.logFile(), auditKey).read();

            var second = append(template("second"));

            var records = records();
            assertAll(
                    () -> assertEquals(1, owed.size(), "the stop left the record owed"),
                    () -> assertEquals(List.of(first.eventId(), records.get(1).eventId(), second.eventId()),
                            records.stream().map(AuditRecord::eventId).toList()),
                    () -> assertEquals(AuditEvent.JSONL_TAIL_REPAIRED, records.get(1).event()),
                    () -> assertEquals(owed.getFirst().details().get(AuditEvent.DETAIL_TRANSACTION_ID),
                            transactionId(records.get(1)), "the record keeps the id it was owed with"),
                    () -> assertFalse(Files.exists(intentFile()), "the intent was closed"),
                    () -> assertChained(AuditCodec.GENESIS_HASH));
        }

        @Test
        @DisplayName("a stop after the in-log write and before the intent is deleted yields a duplicate, never a loss")
        void stopAfterInLogWrite() throws Exception {
            append(template("first"));
            tearTail();
            stopAfterCut();
            var intent = Files.readAllBytes(intentFile());
            var stopping = new RecordingAnchor(Optional.empty());
            stopping.failure = new FailingAuditSink.ProcessStopped();
            var stopped = new AuditLog(base, stopping, CLOCK, PRINCIPAL);
            var stoppedRecord = template("stopped");
            assertThrows(FailingAuditSink.ProcessStopped.class, () -> append(stopped, stoppedRecord));
            Files.write(intentFile(), intent);

            var last = append(template("last"));

            var records = records();
            var repairs = records.stream().filter(entry -> AuditEvent.JSONL_TAIL_REPAIRED.equals(entry.event()))
                    .toList();
            assertAll(
                    () -> assertEquals(2, repairs.size(), "the record is in the log twice"),
                    () -> assertEquals(transactionId(repairs.get(0)), transactionId(repairs.get(1)),
                            "the duplicate carries the same transaction id"),
                    () -> assertEquals(last, records.getLast()),
                    () -> assertTrue(records.stream().noneMatch(entry -> "stopped".equals(entry.event())),
                            "the stopped append wrote no record"),
                    () -> assertFalse(Files.exists(intentFile()), "the intent was closed"),
                    () -> assertChained(AuditCodec.GENESIS_HASH));
        }

        @Test
        @DisplayName("a torn first line of an empty log chains its repair record to the anchor's predecessor")
        void tornFirstLineAfterRotation() throws Exception {
            var anchored = new AuditLog(base, new RecordingAnchor(Optional.of(ANCHORED)), CLOCK, PRINCIPAL);
            tearTail();

            var first = append(anchored, template("first"));

            var records = records();
            assertAll(
                    () -> assertEquals(AuditEvent.JSONL_TAIL_REPAIRED, records.get(0).event()),
                    () -> assertEquals(ANCHORED, records.get(0).prevHash()),
                    () -> assertEquals(first, records.get(1)),
                    () -> assertChained(ANCHORED));
        }

        @Test
        @DisplayName("behind a checkpoint is cut and its record follows the checkpoint before the anchor is called")
        void behindCheckpoint() throws Exception {
            append(template("first"));
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(auditKey);
                log.appendCheckpoint(transaction, checkpoint());
            }
            tearTail();

            var second = append(template("second"));

            var records = records();
            assertAll(
                    () -> assertEquals(List.of("first", AuditRecord.EVENT_CHECKPOINT, AuditEvent.JSONL_TAIL_REPAIRED,
                            "second"), records.stream().map(AuditRecord::event).toList()),
                    () -> assertEquals(records.get(2), anchor.lastBefore,
                            "the anchor saw the repair record, not the checkpoint, as the last line"),
                    () -> assertEquals(second, records.getLast()),
                    () -> assertFalse(Files.exists(intentFile()), "the intent was closed"),
                    () -> assertChained(AuditCodec.GENESIS_HASH));
        }

        @Test
        @DisplayName("a repair record that cannot be written into the log stays owed and the append is not carried out")
        void inLogWriteFails() throws Exception {
            var failing = new AuditLog(base, new FailingPredecessorAnchor(), CLOCK, PRINCIPAL);
            tearTail();
            var auditRecord = template("first");

            var failure = assertThrows(UncheckedIOException.class, () -> append(failing, auditRecord));

            assertAll(
                    () -> assertTrue(failure.getMessage().contains(intentFile().toString()), failure::getMessage),
                    () -> assertTrue(Files.exists(intentFile()), "the record stays owed"),
                    () -> assertEquals(0, Files.size(log.logFile()), "the torn tail was cut, nothing appended"),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.AUDIT_RECORD_DEFERRED.resolveIdentifierString()));
        }
    }

    @Nested
    @DisplayName("checkpoint")
    class Checkpoint {

        @Test
        @DisplayName("is chained, returns the hash of its written line, and never calls the anchor")
        void appended() {
            append(template("first"));
            anchor.calls.clear();

            String hash;
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(auditKey);
                hash = log.appendCheckpoint(transaction, checkpoint());
            }

            var written = hash;
            assertAll(
                    () -> assertEquals(AuditCodec.sha256Hex(lines().getLast()), written),
                    () -> assertTrue(records().getLast().isCheckpoint()),
                    () -> assertEquals(List.of(), anchor.calls, "the anchor was not called"),
                    () -> assertChained(AuditCodec.GENESIS_HASH));
        }

        @Test
        @DisplayName("is refused without the audit lock")
        void withoutLock() {
            var checkpoint = checkpoint();

            try (var transaction = locks.openTransaction()) {
                assertThrows(IllegalStateException.class, () -> log.appendCheckpoint(transaction, checkpoint));
            }
            assertFalse(Files.exists(log.logFile()), "nothing was written");
        }

        @Test
        @DisplayName("is refused for a record of another event")
        void otherEvent() {
            var auditRecord = template("first");

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(auditKey);
                assertThrows(IllegalArgumentException.class, () -> log.appendCheckpoint(transaction, auditRecord));
            }
            assertFalse(Files.exists(log.logFile()), "nothing was written");
        }
    }

    @Nested
    @DisplayName("owed repair")
    class OwedRepair {

        private AuditEvent owed() {
            return AuditEvent.jsonlTailRepaired(base.resolve("logs").resolve("audit-index.jsonl"), TORN.length,
                    StoreSnapshot.sha256(TORN), TransactionId.generate(CLOCK).value());
        }

        private AuditRecord appendOwed(AuditEvent event) {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(auditKey);
                return log.appendOwedRepair(transaction, event);
            }
        }

        @Test
        @DisplayName("is formed as the sink forms it, chained to the anchor's predecessor, and never calls the anchor")
        void appended() {
            var anchored = new RecordingAnchor(Optional.of(ANCHORED));
            log = new AuditLog(base, anchored, CLOCK, PRINCIPAL);
            var event = owed();

            var appended = appendOwed(event);

            var expected = AuditLogSink.toRecord(event, CLOCK, PRINCIPAL);
            assertAll(
                    () -> assertEquals(List.of(appended), records()),
                    () -> assertEquals(ANCHORED, appended.prevHash()),
                    () -> assertEquals(AuditEvent.JSONL_TAIL_REPAIRED, appended.event()),
                    () -> assertEquals(expected.category(), appended.category()),
                    () -> assertEquals(expected.outcome(), appended.outcome()),
                    () -> assertEquals(expected.scopeType(), appended.scopeType()),
                    () -> assertEquals(PRINCIPAL, appended.actor()),
                    () -> assertEquals(event.details(), appended.details()),
                    () -> assertEquals(List.of("emptyLogPredecessor"), anchored.calls,
                            "the anchor was asked for the predecessor only, never before or after an append"));
        }

        @Test
        @DisplayName("chains to the last line of a log that holds lines, after cutting its torn tail")
        void chainedAfterRepair() throws Exception {
            append(template("first"));
            tearTail();

            var appended = appendOwed(owed());

            var records = records();
            assertAll(
                    () -> assertEquals(3, records.size()),
                    () -> assertEquals(AuditEvent.JSONL_TAIL_REPAIRED, records.get(1).event()),
                    () -> assertEquals(appended, records.getLast()),
                    () -> assertChained(AuditCodec.GENESIS_HASH));
        }

        @Test
        @DisplayName("is refused without the audit lock")
        void withoutLock() {
            var event = owed();

            try (var transaction = locks.openTransaction()) {
                assertThrows(IllegalStateException.class, () -> log.appendOwedRepair(transaction, event));
            }
            assertFalse(Files.exists(log.logFile()), "nothing was written");
        }

        @Test
        @DisplayName("is refused for a record of another event, and for an unknown category as the sink refuses it")
        void otherRecords() {
            var otherEvent = new AuditEvent("first", AuditEvent.CATEGORY_STORE, AuditEvent.OUTCOME_SUCCEEDED,
                    Map.of());
            var unknownCategory = new AuditEvent(AuditEvent.JSONL_TAIL_REPAIRED, "unknown",
                    AuditEvent.OUTCOME_SUCCEEDED, Map.of());

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> appendOwed(otherEvent)),
                    () -> assertThrows(IllegalArgumentException.class, () -> appendOwed(unknownCategory)),
                    () -> assertFalse(Files.exists(log.logFile()), "nothing was written"));
        }
    }

    @Nested
    @DisplayName("read")
    class Reading {

        @Test
        @DisplayName("a lock-free read reports a torn tail and every earlier record intact")
        void snapshotWithTornTail() throws Exception {
            var first = append(template("first"));
            var second = append(template("second"));
            var size = Files.size(log.logFile());
            tearTail();

            var content = log.snapshot();

            var decoded = content.decode(AuditRecord.FORMAT_VERSION,
                    line -> AuditCodec.readRecord(log.logFile(), line));
            assertAll(
                    () -> assertEquals(OptionalLong.of(size), content.tornTailOffset()),
                    () -> assertEquals(List.of(new Accepted<>(1, first), new Accepted<>(2, second)), decoded));
        }

        @Test
        @DisplayName("a read under the lock and the record count see the complete lines only")
        void underLock() throws Exception {
            append(template("first"));
            append(template("second"));
            tearTail();

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(auditKey);
                assertAll(
                        () -> assertEquals(2, log.read(transaction).lines().size()),
                        () -> assertEquals(2, log.recordCount(transaction)));
            }
        }

        @Test
        @DisplayName("a log that does not exist has no record")
        void absent() {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(auditKey);
                assertEquals(0, log.recordCount(transaction));
            }
        }

        @Test
        @DisplayName("the record count is refused without the audit lock")
        void countWithoutLock() {
            try (var transaction = locks.openTransaction()) {
                assertThrows(InternalFaultException.class, () -> log.recordCount(transaction));
            }
        }
    }
}
