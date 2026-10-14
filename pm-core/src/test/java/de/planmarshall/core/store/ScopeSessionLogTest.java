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

import java.io.IOException;
import java.io.Serial;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.store.ScopeSessionRow.Client;
import de.planmarshall.core.store.ScopeTransaction.Step;

/**
 * The scope session log (PM-ARCH-4, PM-CLIENT-3): one row per entry, appended through the transaction of its scope and
 * never changed, a torn tail and a refused line reported by a read without the lock, and the
 * {@code entry_client_changed} record owed by the very transaction that appends a row of another client.
 */
@DisplayName("Scope session log")
class ScopeSessionLogTest {

    private static final String PROJECT = "pm-mcp-core";
    private static final String PLAN = "a-plan";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-13T09:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path base;

    private JournalScope scope;
    private ScopeSessionLog log;
    private RecordingLockManager locks;
    private RecordingAuditSink audit;

    /** What the step hook throws to stop a commit at a boundary, as a killed process stops it. */
    private static final class StopWrite extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        StopWrite(Step step) {
            super("stopped after " + step);
        }
    }

    @BeforeEach
    void createScope() throws IOException {
        scope = JournalScope.plan(base, PROJECT, PLAN);
        Files.createDirectories(scope.journalFile().getParent());
        log = new ScopeSessionLog(scope);
        locks = new RecordingLockManager();
        audit = new RecordingAuditSink();
    }

    private static ScopeSessionRow row(Client client, String sessionId, String generation) {
        return ScopeSessionRowCodecTest.row().toBuilder().client(client).clientDetected(client)
                .clientSessionId(sessionId).generationId("sg-" + generation.repeat(16 / generation.length())).build();
    }

    /** Appends a row in a transaction of its own and commits it. */
    private Optional<AuditEvent> append(ScopeSessionRow row) {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(scope.lockKey());
            var write = ScopeTransaction.open(transaction, scope, audit, CLOCK);
            var owed = log.append(write, row);
            write.commit();
            return owed;
        }
    }

    private JournalRecovery.Outcome recover() {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(scope.lockKey());
            return JournalRecovery.recover(transaction, scope, audit);
        }
    }

    private static byte[] line(ScopeSessionRow row) {
        return (new String(ScopeSessionRowCodec.write(row), UTF_8) + "\n").getBytes(UTF_8);
    }

    @Nested
    @DisplayName("an append")
    class Appending {

        @Test
        @DisplayName("writes one row as one line through the transaction, covered by the sidecar, owing nothing first")
        void firstRow() {
            var row = row(Client.CLAUDE, "sess-1", "1");

            var owed = append(row);

            assertAll(
                    () -> assertEquals(Optional.empty(), owed, "the first row of a log owes no client change"),
                    () -> assertArrayEquals(line(row), Files.readAllBytes(log.store())),
                    () -> assertEquals(List.of(row), log.read().rows()),
                    () -> assertEquals(Optional.of(StoreDigest.of(line(row))),
                            DigestSidecar.load(scope.sidecarFile()).digest(ScopeSessionLog.FILE_NAME)),
                    () -> assertFalse(Files.exists(scope.journalFile())),
                    () -> assertEquals(List.of(), audit.events()));
        }

        @Test
        @DisplayName("keeps every earlier line byte for byte: the log only grows")
        void appendOnly() throws Exception {
            var first = row(Client.CLAUDE, "sess-1", "1");
            var second = row(Client.CLAUDE, "sess-2", "2");
            var third = row(Client.CLAUDE, "sess-3", "3");
            append(first);
            var afterFirst = Files.readAllBytes(log.store());
            append(second);
            var afterSecond = Files.readAllBytes(log.store());

            append(third);

            var afterThird = Files.readAllBytes(log.store());
            assertAll(
                    () -> assertArrayEquals(afterFirst, Arrays.copyOf(afterSecond, afterFirst.length)),
                    () -> assertArrayEquals(afterSecond, Arrays.copyOf(afterThird, afterSecond.length)),
                    () -> assertEquals(List.of(first, second, third), log.read().rows()),
                    () -> assertEquals(Optional.of(third), log.latest()));
        }

        @Test
        @DisplayName("of another client owes exactly one entry_client_changed, under an id of its own")
        void clientChange() {
            append(row(Client.CLAUDE, "sess-1", "1"));
            AuditEvent owed;
            String transactionId;
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = ScopeTransaction.open(transaction, scope, audit, CLOCK);
                owed = log.append(write, row(Client.OPENCODE, "sess-2", "2")).orElseThrow();
                transactionId = write.transactionId().value();
                write.commit();
            }
            var recordId = String.valueOf(owed.details().get(AuditEvent.DETAIL_TRANSACTION_ID));

            assertAll(
                    () -> assertEquals(AuditEvent.entryClientChanged("claude", "opencode", "sess-1", "sess-2",
                            recordId), owed),
                    () -> assertTrue(TransactionId.ofSuffix("." + recordId).isPresent(), "the record id is an id"),
                    () -> assertNotEquals(transactionId, recordId, "the record does not carry the row's transaction"),
                    () -> assertEquals(List.of(owed), audit.events(), "handed over once, with the row"));
        }

        @Test
        @DisplayName("of the same client in another session owes nothing")
        void sameClient() {
            append(row(Client.CLAUDE, "sess-1", "1"));

            var owed = append(row(Client.CLAUDE, "sess-2", "2"));

            assertAll(
                    () -> assertEquals(Optional.empty(), owed),
                    () -> assertEquals(List.of(), audit.events()));
        }

        @Test
        @DisplayName("owes the change in its own journal, before any store is touched")
        void owedInJournal() {
            append(row(Client.CLAUDE, "sess-1", "1"));
            ScopeTransaction.StepHook hook = (step, target) -> {
                if (step == Step.JOURNAL) {
                    throw new StopWrite(step);
                }
            };
            AuditEvent owed;
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = ScopeTransaction.open(transaction, scope, audit, CLOCK, hook);
                owed = log.append(write, row(Client.CODEX, null, "2")).orElseThrow();
                assertThrows(StopWrite.class, write::commit);
            }

            var journal = TransactionJournal.read(scope.journalFile(),
                    StoreSnapshot.read(scope.journalFile()).content().orElseThrow());

            assertAll(
                    () -> assertEquals(List.of(owed), journal.owedEvents()),
                    () -> assertEquals(List.of(ScopeSessionLog.FILE_NAME),
                            journal.targets().stream().map(TransactionJournal.Target::path).toList()),
                    () -> assertEquals(List.of(), audit.events(), "nothing handed over before the row is applied"));
        }

        @ParameterizedTest(name = "stopped after {0}")
        @EnumSource(Step.class)
        @DisplayName("stopped at a step boundary leaves no row before its journal, and the row and its record after")
        void crashPoint(Step stop) {
            var first = row(Client.CLAUDE, "sess-1", "1");
            var second = row(Client.ANTIGRAVITY, "sess-2", "2");
            append(first);
            ScopeTransaction.StepHook hook = (step, target) -> {
                if (step == stop) {
                    throw new StopWrite(step);
                }
            };
            AuditEvent owed;
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = ScopeTransaction.open(transaction, scope, audit, CLOCK, hook);
                owed = log.append(write, second).orElseThrow();
                assertThrows(StopWrite.class, write::commit);
            }

            recover();

            var names = audit.events().stream().map(AuditEvent::event).toList();
            if (stop == Step.STAGE) {
                assertAll(
                        () -> assertEquals(List.of(first), log.read().rows(), "the row was never journaled"),
                        () -> assertEquals(List.of(), names, "no record of a change that did not happen"));
                return;
            }
            assertAll(
                    () -> assertEquals(List.of(first, second), log.read().rows(), "the row appended exactly once"),
                    () -> assertEquals(List.of(AuditEvent.TRANSACTION_ROLLED_FORWARD, AuditEvent.ENTRY_CLIENT_CHANGED),
                            names),
                    () -> assertEquals(owed, audit.events().getLast()),
                    () -> assertFalse(Files.exists(scope.journalFile())));
        }

        @Test
        @DisplayName("is refused for a transaction of another scope, and the log is not touched")
        void otherScope() throws Exception {
            var other = JournalScope.plan(base, PROJECT, "other-plan");
            Files.createDirectories(other.journalFile().getParent());
            var row = row(Client.CLAUDE, "sess-1", "1");
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(other.lockKey());
                var write = ScopeTransaction.open(transaction, other, audit, CLOCK);

                assertThrows(IllegalArgumentException.class, () -> log.append(write, row));
            }
            assertFalse(Files.exists(log.store()));
        }

        @Test
        @DisplayName("is refused a second time in one transaction")
        void twiceInOneTransaction() {
            var second = row(Client.CODEX, "sess-2", "2");
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = ScopeTransaction.open(transaction, scope, audit, CLOCK);
                log.append(write, row(Client.CLAUDE, "sess-1", "1"));

                assertThrows(IllegalArgumentException.class, () -> log.append(write, second));
            }
        }
    }

    @Nested
    @DisplayName("a read without the lock")
    class Reading {

        @Test
        @DisplayName("of a log that does not exist has no row, no refusal and no torn tail")
        void absent() {
            var rows = log.read();

            assertAll(
                    () -> assertEquals(List.of(), rows.entries()),
                    () -> assertEquals(OptionalLong.empty(), rows.tornTailOffset()),
                    () -> assertEquals(Optional.empty(), log.latest()));
        }

        @Test
        @DisplayName("reports a torn tail at its offset, never as a row, and keeps every earlier row")
        void tornTail() throws Exception {
            var first = row(Client.CLAUDE, "sess-1", "1");
            var second = row(Client.CLAUDE, "sess-2", "2");
            append(first);
            append(second);
            var size = Files.size(log.store());
            var torn = Arrays.copyOf(line(row(Client.CODEX, "sess-3", "3")), 20);
            Files.write(log.store(), torn, StandardOpenOption.APPEND);

            var rows = log.read();

            assertAll(
                    () -> assertEquals(List.of(first, second), rows.rows()),
                    () -> assertEquals(OptionalLong.of(size), rows.tornTailOffset()),
                    () -> assertEquals(List.of(), rows.refused()),
                    () -> assertEquals(Optional.of(second), rows.latest()),
                    () -> assertEquals(size + torn.length, Files.size(log.store()), "a read repairs nothing"));
        }

        @Test
        @DisplayName("of a torn tail is repaired by the next append, with a record of the repair")
        void tornTailRepairedByAppend() throws Exception {
            var first = row(Client.CLAUDE, "sess-1", "1");
            var next = row(Client.CLAUDE, "sess-2", "2");
            append(first);
            Files.write(log.store(), "{\"format_version\":1,".getBytes(UTF_8), StandardOpenOption.APPEND);

            append(next);

            assertAll(
                    () -> assertEquals(List.of(first, next), log.read().rows()),
                    () -> assertEquals(OptionalLong.empty(), log.read().tornTailOffset()),
                    () -> assertEquals(List.of(AuditEvent.JSONL_TAIL_REPAIRED),
                            audit.events().stream().map(AuditEvent::event).toList()));
        }

        @Test
        @DisplayName("reports a refused line with its line number and reads the rows around it")
        void refusedLine() throws Exception {
            var first = row(Client.CLAUDE, "sess-1", "1");
            var third = row(Client.OPENCODE, "sess-3", "3");
            Files.write(log.store(), line(first));
            Files.write(log.store(), "{\"format_version\":1,\"client\":\"cursor\"}\n".getBytes(UTF_8),
                    StandardOpenOption.APPEND);
            Files.write(log.store(), "{\"format_version\":2}\n".getBytes(UTF_8), StandardOpenOption.APPEND);
            Files.write(log.store(), line(third), StandardOpenOption.APPEND);

            var rows = log.read();

            var refused = rows.refused();
            assertAll(
                    () -> assertEquals(List.of(first, third), rows.rows()),
                    () -> assertEquals(List.of(2L, 3L), refused.stream().map(JsonlStoreFile.Refused::line).toList()),
                    () -> assertEquals(StoreRefusal.Outcome.UNREADABLE, refused.getFirst().refusal().outcome()),
                    () -> assertEquals(StoreRefusal.Outcome.FORMAT_NEWER, refused.getLast().refusal().outcome()),
                    () -> assertEquals(log.store(), refused.getFirst().refusal().file()),
                    () -> assertEquals(Optional.of(third), rows.latest()),
                    () -> assertInstanceOf(JsonlStoreFile.Accepted.class, rows.entries().getLast()));
        }
    }

    @Test
    @DisplayName("lies at the path base of a plan or epic and below workspace/ of the workspace; the lesson store has none")
    void location() {
        var epic = JournalScope.epic(base, PROJECT, "an-epic");
        var workspace = JournalScope.workspace(base, PROJECT);
        var lessons = JournalScope.lessons(base, PROJECT);

        assertAll(
                () -> assertEquals(scope.pathBase().resolve("sessions.jsonl"), log.store()),
                () -> assertEquals("sessions.jsonl", new ScopeSessionLog(epic).storePath()),
                () -> assertEquals(epic.pathBase().resolve("sessions.jsonl"), new ScopeSessionLog(epic).store()),
                () -> assertEquals("workspace/sessions.jsonl", new ScopeSessionLog(workspace).storePath()),
                () -> assertTrue(new ScopeSessionLog(workspace).store().startsWith(workspace.pathBase()
                        .resolve("workspace"))),
                () -> assertThrows(IllegalArgumentException.class, () -> new ScopeSessionLog(lessons)));
    }
}
