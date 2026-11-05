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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.AuditRecord.Category;
import de.planmarshall.core.store.AuditRecord.Outcome;
import de.planmarshall.core.store.AuditRecord.Scope;

/**
 * The production audit sink (PM-SEC-6, PM-IMPL-7): an {@link AuditEvent} becomes one chained record of the audit log
 * with the actor, category, outcome, details and scope it carries; a record that cannot be appended fails as the sink
 * contract states; and the appends of two sinks of one runtime serialize on the in-process audit lock.
 */
@EnableGeneratorController
@DisplayName("Audit log sink")
class AuditLogSinkTest {

    private static final String TRANSACTION_ID = "01890a5d-ac96-774b-bcce-b302099a8057";

    @TempDir
    Path base;

    private Instant at;
    private Clock clock;
    private String principal;
    private String project;
    private String scopeId;
    private RecordingLockManager locks;
    private AuditLog log;
    private AuditLogSink sink;

    @BeforeEach
    void createSink() throws IOException {
        at = Instant.ofEpochSecond(Generators.longs(1_600_000_000L, 1_900_000_000L).next());
        clock = Clock.fixed(at, ZoneOffset.UTC);
        principal = Generators.letterStrings(3, 20).next();
        project = "project-" + Generators.letterStrings(3, 20).next().toLowerCase(Locale.ROOT);
        scopeId = "plan-" + Generators.letterStrings(3, 20).next().toLowerCase(Locale.ROOT);
        Files.createDirectories(base.resolve("logs"));
        locks = new RecordingLockManager();
        log = new AuditLog(base, AuditAnchor.NONE, clock, principal);
        sink = new AuditLogSink(locks, log);
    }

    /** A lock manager with the in-process lock of each key, as one runtime holds it: a key is held by one thread. */
    private static final class InProcessLockManager implements LockManager {

        private final Map<LockKey, ReentrantLock> locks = new ConcurrentHashMap<>();

        @Override
        public LockTransaction openTransaction() {
            return new LockTransaction() {

                private final List<ReentrantLock> held = new ArrayList<>();
                private final Set<LockKey> keys = new HashSet<>();

                @Override
                public void acquire(LockKey key) {
                    if (keys.add(key)) {
                        var lock = locks.computeIfAbsent(key, _ -> new ReentrantLock());
                        lock.lock();
                        held.add(lock);
                    }
                }

                @Override
                public boolean holds(LockKey key) {
                    return keys.contains(key);
                }

                @Override
                public void close() {
                    held.reversed().forEach(ReentrantLock::unlock);
                    held.clear();
                    keys.clear();
                }
            };
        }
    }

    private List<byte[]> lines() {
        return log.snapshot().lines().stream().map(JsonlStoreFile.Line::content).toList();
    }

    private List<AuditRecord> records() {
        return lines().stream().map(line -> AuditCodec.readRecord(log.logFile(), line)).toList();
    }

    @Test
    @DisplayName("an event becomes one chained record with actor, category, outcome, details and its scope")
    void scopedEvent() {
        var first = AuditEvent.enrolmentWritten(project, TRANSACTION_ID);
        var second = AuditEvent.transactionRolledForward("plan", scopeId, TRANSACTION_ID,
                List.of("status.json"));

        sink.append(first);
        sink.append(second);

        var records = records();
        var scoped = records.get(1);
        assertAll("event becomes one scoped chained record",
                () -> assertEquals(2, records.size(), "the sink wrote one line per event"),
                () -> assertEquals(AuditCodec.sha256Hex(lines().getFirst()), scoped.prevHash(),
                        "the record chains to the previous line"),
                () -> assertEquals(AuditEvent.TRANSACTION_ROLLED_FORWARD, scoped.event(),
                        "the event name is kept"),
                () -> assertEquals(Category.STORE, scoped.category(), "the category is that of the event"),
                () -> assertEquals(Outcome.SUCCEEDED, scoped.outcome(), "the outcome is that of the event"),
                () -> assertEquals(principal, scoped.actor(), "the actor is the OS principal"),
                () -> assertEquals(at, scoped.timestamp(), "the timestamp is the clock instant"),
                () -> assertEquals(Scope.PLAN, scoped.scopeType(), "the scope type is that of the event"),
                () -> assertEquals(scopeId, scoped.scopeId(), "the scope id of the event is kept"),
                () -> assertEquals(second.details(), scoped.details(), "the details of the event are kept"),
                () -> assertTrue(AuditRecord.EVENT_ID.matcher(scoped.eventId()).matches(),
                        "the event id is a lower-case UUID of version 7"),
                () -> assertEquals(List.of(log.lockKey(), log.lockKey()), locks.acquisitions(),
                        "the audit lock was acquired once per append"),
                () -> assertEquals(List.of(), locks.held(), "every transaction was closed"));
    }

    @Test
    @DisplayName("an event without a scope of the audit log is a machine-level record without a scope id")
    void machineEvent() {
        sink.append(AuditEvent.enrolmentTampered(project, Generators.letterStrings(3, 20).next()));
        sink.append(AuditEvent.transactionRolledForward("lessons", null, TRANSACTION_ID, List.of()));

        var records = records();
        assertAll("machine events carry no scope id",
                () -> assertEquals(Scope.MACHINE, records.get(0).scopeType(), "the event has no scope"),
                () -> assertNull(records.get(0).scopeId(), "the record has no scope id"),
                () -> assertEquals(Category.SECURITY, records.get(0).category(), "the category is security"),
                () -> assertEquals(Outcome.REFUSED, records.get(0).outcome(), "the outcome is refused"),
                () -> assertEquals(Scope.MACHINE, records.get(1).scopeType(), "lessons is no scope of the log"),
                () -> assertEquals("lessons", records.get(1).details().get(AuditEvent.DETAIL_SCOPE_TYPE),
                        "the unknown scope type is kept as a detail"));
    }

    @Test
    @DisplayName("an append that fails surfaces as UncheckedIOException and releases the lock")
    void failingAppend() throws Exception {
        Files.delete(base.resolve("logs"));
        var event = AuditEvent.enrolmentWritten(project, TRANSACTION_ID);

        assertThrows(UncheckedIOException.class, () -> sink.append(event),
                "a failed append surfaces as UncheckedIOException");

        assertAll("the failed append left nothing behind",
                () -> assertFalse(Files.exists(log.logFile()), "the log file was not written"),
                () -> assertEquals(List.of(), locks.held(), "the transaction was closed"));
    }

    @Test
    @DisplayName("an event of a category or an outcome outside the audit log is refused")
    void undeclaredValues() {
        var event = Generators.letterStrings(3, 20).next();
        var category = new AuditEvent(event, "elsewhere", AuditEvent.OUTCOME_SUCCEEDED, Map.of());
        var outcome = new AuditEvent(event, AuditEvent.CATEGORY_STORE, "maybe", Map.of());

        assertAll("refused events write nothing",
                () -> assertThrows(IllegalArgumentException.class, () -> sink.append(category),
                        "an undeclared category is refused"),
                () -> assertThrows(IllegalArgumentException.class, () -> sink.append(outcome),
                        "an undeclared outcome is refused"),
                () -> assertFalse(Files.exists(log.logFile()), "the log file was not written"));
    }

    @Test
    @DisplayName("two sinks of one runtime serialize on the in-process audit lock: the chain stays intact")
    void twoSinksSerialize() throws Exception {
        var lockManager = new InProcessLockManager();
        var sinks = List.of(new AuditLogSink(lockManager, new AuditLog(base, AuditAnchor.NONE, clock, principal)),
                new AuditLogSink(lockManager, new AuditLog(base, AuditAnchor.NONE, clock, principal)));
        var perSink = 50;
        var start = new CountDownLatch(1);
        var futures = new ArrayList<Future<?>>();

        try (var executor = Executors.newFixedThreadPool(sinks.size())) {
            for (var each : sinks) {
                futures.add(executor.submit(() -> {
                    start.await();
                    for (var index = 0; index < perSink; index++) {
                        each.append(AuditEvent.enrolmentWritten(project, TRANSACTION_ID));
                    }
                    return null;
                }));
            }
            start.countDown();
            for (var future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        }

        var lines = lines();
        var records = records();
        assertEquals(sinks.size() * perSink, records.size(), "every append wrote one record");
        for (var index = 1; index < records.size(); index++) {
            assertEquals(AuditCodec.sha256Hex(lines.get(index - 1)), records.get(index).prevHash(),
                    "prev_hash of line " + (index + 1));
        }
    }

    @Test
    @DisplayName("a sink is refused without its lock manager or log")
    void nullArguments() {
        assertAll("a sink needs both its lock manager and log",
                () -> assertThrows(NullPointerException.class, () -> new AuditLogSink(null, log),
                        "a missing lock manager is refused"),
                () -> assertThrows(NullPointerException.class, () -> new AuditLogSink(locks, null),
                        "a missing log is refused"));
    }
}
