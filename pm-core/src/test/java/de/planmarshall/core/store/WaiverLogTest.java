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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.store.WaiverRecord.Channel;
import de.planmarshall.core.store.WaiverRecord.Event;
import de.planmarshall.core.store.WaiverRecord.Operator;

/**
 * The waiver log of a plan (PM-SEC-3, PM-IMPL-2, PM-IMPL-7): one line per act, appended through the transaction of the
 * plan and never changed, a revocation a new line that leaves the earlier one as it was, the last readable line of a
 * waiver its current state, and a torn tail reported by a read without the lock instead of read as a version.
 */
@DisplayName("Waiver log")
class WaiverLogTest {

    private static final String PROJECT = "pm-mcp-core";
    private static final String PLAN = "a-plan";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-17T09:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path base;

    private JournalScope scope;
    private WaiverLog log;
    private RecordingLockManager locks;
    private RecordingAuditSink audit;

    @BeforeEach
    void createScope() throws IOException {
        scope = JournalScope.plan(base, PROJECT, PLAN);
        Files.createDirectories(scope.journalFile().getParent());
        log = new WaiverLog(scope);
        locks = new RecordingLockManager();
        audit = new RecordingAuditSink();
    }

    private static WaiverRecord revocation(String waiverId) {
        return WaiverRecord.builder().event(Event.REVOKED).waiverId(waiverId).reason("gate is fixed")
                .operator(new Operator(Channel.CLI, null, "oliver")).at(Instant.parse("2026-10-17T10:00:00Z"))
                .build();
    }

    /** Appends a line in a transaction of its own and commits it. */
    private void append(WaiverRecord waiver) {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(scope.lockKey());
            var write = ScopeTransaction.open(transaction, scope, audit, CLOCK);
            log.append(write, waiver);
            write.commit();
        }
    }

    private static byte[] line(WaiverRecord waiver) {
        return (new String(WaiverRecordCodec.write(waiver), UTF_8) + "\n").getBytes(UTF_8);
    }

    @Nested
    @DisplayName("an append")
    class Appending {

        @Test
        @DisplayName("is written only when the caller commits its transaction, as one line covered by the sidecar")
        void writtenAtCommit() {
            var waiver = WaiverRecordCodecTest.met();
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = ScopeTransaction.open(transaction, scope, audit, CLOCK);
                log.append(write, waiver);

                assertFalse(Files.exists(log.store()), "an append target is not written before the commit");

                write.commit();
            }

            assertAll(
                    () -> assertArrayEquals(line(waiver), Files.readAllBytes(log.store())),
                    () -> assertEquals(List.of(waiver), log.read().records()),
                    () -> assertEquals(Optional.of(StoreDigest.of(line(waiver))),
                            DigestSidecar.load(scope.sidecarFile()).digest(WaiverLog.FILE_NAME)),
                    () -> assertFalse(Files.exists(scope.journalFile())));
        }

        @Test
        @DisplayName("of a revocation is a new line, and the waived line before it stays byte for byte")
        void revocationIsNewLine() throws Exception {
            var waiver = WaiverRecordCodecTest.met();
            append(waiver);
            var before = Files.readAllBytes(log.store());

            append(revocation("W-1"));

            var after = Files.readAllBytes(log.store());
            assertAll(
                    () -> assertArrayEquals(before, Arrays.copyOf(after, before.length)),
                    () -> assertArrayEquals(line(revocation("W-1")), Arrays.copyOfRange(after, before.length,
                            after.length)),
                    () -> assertEquals(List.of(waiver, revocation("W-1")), log.read().records()));
        }

        @Test
        @DisplayName("is refused for a transaction of another scope, and the log is not touched")
        void otherScope() throws Exception {
            var other = JournalScope.plan(base, PROJECT, "other-plan");
            Files.createDirectories(other.journalFile().getParent());
            var waiver = WaiverRecordCodecTest.met();
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(other.lockKey());
                var write = ScopeTransaction.open(transaction, other, audit, CLOCK);

                assertThrows(IllegalArgumentException.class, () -> log.append(write, waiver));
            }
            assertFalse(Files.exists(log.store()));
        }

        @Test
        @DisplayName("is refused a second time in one transaction")
        void twiceInOneTransaction() {
            var revocation = revocation("W-1");
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = ScopeTransaction.open(transaction, scope, audit, CLOCK);
                log.append(write, WaiverRecordCodecTest.met());

                assertThrows(IllegalArgumentException.class, () -> log.append(write, revocation));
            }
        }
    }

    @Nested
    @DisplayName("the current state")
    class CurrentState {

        @Test
        @DisplayName("of a waiver is its last readable line, taken whole")
        void lastLineIsCurrent() {
            var first = WaiverRecordCodecTest.met();
            var other = first.toBuilder().waiverId("W-2").gate("pre-push-quality-gate").requestId(null).build();
            append(first);
            append(other);
            append(revocation("W-1"));

            var lines = log.read();

            assertAll(
                    () -> assertEquals(Optional.of(revocation("W-1")), lines.current("W-1")),
                    () -> assertEquals(Optional.of(other), lines.current("W-2")),
                    () -> assertEquals(Optional.empty(), lines.current("W-3")),
                    () -> assertEquals(Map.of("W-1", revocation("W-1"), "W-2", other), lines.currentWaivers()),
                    () -> assertEquals(List.of("W-1", "W-2"), List.copyOf(lines.currentWaivers().keySet())));
        }

        @Test
        @DisplayName("falls back to the previous version when the last line is torn, which is reported with its offset")
        void tornTailFallsBack() throws Exception {
            var waiver = WaiverRecordCodecTest.met();
            append(waiver);
            var size = Files.size(log.store());
            var torn = Arrays.copyOf(line(revocation("W-1")), 30);
            Files.write(log.store(), torn, StandardOpenOption.APPEND);

            var lines = log.read();

            assertAll(
                    () -> assertEquals(Optional.of(waiver), lines.current("W-1")),
                    () -> assertEquals(OptionalLong.of(size), lines.tornTailOffset()),
                    () -> assertEquals(List.of(), lines.refused()),
                    () -> assertEquals(size + torn.length, Files.size(log.store()), "a read repairs nothing"));
        }

        @Test
        @DisplayName("is read past a refused line, which is reported with its line number")
        void refusedLine() throws Exception {
            var waiver = WaiverRecordCodecTest.met();
            Files.write(log.store(), line(waiver));
            Files.write(log.store(), "{\"format_version\":1,\"event\":\"expired\"}\n".getBytes(UTF_8),
                    StandardOpenOption.APPEND);

            var lines = log.read();

            assertAll(
                    () -> assertEquals(Optional.of(waiver), lines.current("W-1")),
                    () -> assertEquals(List.of(2L), lines.refused().stream().map(JsonlStoreFile.Refused::line)
                            .toList()),
                    () -> assertEquals(StoreRefusal.Outcome.UNREADABLE, lines.refused().getFirst().refusal()
                            .outcome()));
        }

        @Test
        @DisplayName("of a torn tail is repaired by the next append, with a record of the repair")
        void tornTailRepairedByAppend() throws Exception {
            var waiver = WaiverRecordCodecTest.met();
            append(waiver);
            Files.write(log.store(), "{\"format_version\":1,".getBytes(UTF_8), StandardOpenOption.APPEND);

            append(revocation("W-1"));

            assertAll(
                    () -> assertEquals(List.of(waiver, revocation("W-1")), log.read().records()),
                    () -> assertEquals(OptionalLong.empty(), log.read().tornTailOffset()),
                    () -> assertEquals(List.of(AuditEvent.JSONL_TAIL_REPAIRED),
                            audit.events().stream().map(AuditEvent::event).toList()));
        }

        @Test
        @DisplayName("of a log that does not exist holds no waiver")
        void absent() {
            var lines = log.read();

            assertAll(
                    () -> assertEquals(List.of(), lines.entries()),
                    () -> assertEquals(OptionalLong.empty(), lines.tornTailOffset()),
                    () -> assertTrue(lines.currentWaivers().isEmpty()));
        }
    }

    @Test
    @DisplayName("lies at the path base of a plan; an epic, the workspace and the lesson store have none")
    void location() {
        assertAll(
                () -> assertEquals(scope.pathBase().resolve("waivers.jsonl"), log.store()),
                () -> assertEquals("waivers.jsonl", log.storePath()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new WaiverLog(JournalScope.epic(base, PROJECT, "an-epic"))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new WaiverLog(JournalScope.workspace(base, PROJECT))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new WaiverLog(JournalScope.lessons(base, PROJECT))));
    }
}
