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

import static de.planmarshall.core.store.QuestionRecordCodecTest.text;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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
 * waiver its current state, and a torn tail reported by a read without the lock instead of read as a version. The
 * store file name and the torn and refused JSONL lines are wire contracts; the project, the plans, the waiver ids, the
 * gates, the revocation reason and the instants are generated within their grammar.
 */
@EnableGeneratorController
@DisplayName("Waiver log")
class WaiverLogTest {

    private static final String PROJECT = "project-" + lower();
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochSecond(Generators.longs(0, 4_000_000_000L).next()),
            ZoneOffset.UTC);

    /** The id of the waiver of {@link WaiverRecordCodecTest#met()}. */
    private static final String FIRST = WaiverRecordCodecTest.WAIVER_ID;
    private static final String SECOND = "W-" + (WaiverRecordCodecTest.WAIVER_NUMBER + 1);
    private static final String ABSENT = "W-" + (WaiverRecordCodecTest.WAIVER_NUMBER + 2);

    private static final String REVOKE_REASON = text(2);
    private static final Instant REVOKED_AT = Instant.ofEpochSecond(Generators.longs(0, 4_000_000_000L).next());

    @TempDir
    Path base;

    private JournalScope scope;
    private WaiverLog log;
    private RecordingLockManager locks;
    private RecordingAuditSink audit;

    @BeforeEach
    void createScope() throws IOException {
        scope = JournalScope.plan(base, PROJECT, "plan-" + lower());
        Files.createDirectories(scope.journalFile().getParent());
        log = new WaiverLog(scope);
        locks = new RecordingLockManager();
        audit = new RecordingAuditSink();
    }

    private static String lower() {
        return Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }

    private static WaiverRecord revocation(String waiverId) {
        return WaiverRecord.builder().event(Event.REVOKED).waiverId(waiverId).reason(REVOKE_REASON)
                .operator(new Operator(Channel.CLI, null, WaiverRecordCodecTest.PRINCIPAL)).at(REVOKED_AT).build();
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
                    () -> assertArrayEquals(line(waiver), Files.readAllBytes(log.store()),
                            "the log should hold exactly the one line"),
                    () -> assertEquals(List.of(waiver), log.read().records(), "the log should read the one waiver"),
                    () -> assertEquals(Optional.of(StoreDigest.of(line(waiver))),
                            DigestSidecar.load(scope.sidecarFile()).digest(WaiverLog.FILE_NAME),
                            "the sidecar should cover the written line"),
                    () -> assertFalse(Files.exists(scope.journalFile()), "the journal should be gone after commit"));
        }

        @Test
        @DisplayName("of a revocation is a new line, and the waived line before it stays byte for byte")
        void revocationIsNewLine() throws Exception {
            var waiver = WaiverRecordCodecTest.met();
            append(waiver);
            var before = Files.readAllBytes(log.store());

            append(revocation(FIRST));

            var after = Files.readAllBytes(log.store());
            assertAll(
                    () -> assertArrayEquals(before, Arrays.copyOf(after, before.length),
                            "the waived line should stay byte for byte"),
                    () -> assertArrayEquals(line(revocation(FIRST)), Arrays.copyOfRange(after, before.length,
                            after.length), "the revocation should be a new line"),
                    () -> assertEquals(List.of(waiver, revocation(FIRST)), log.read().records(),
                            "the log should read both lines in order"));
        }

        @Test
        @DisplayName("is refused for a transaction of another scope, and the log is not touched")
        void otherScope() throws Exception {
            var other = JournalScope.plan(base, PROJECT, "other-" + lower());
            Files.createDirectories(other.journalFile().getParent());
            var waiver = WaiverRecordCodecTest.met();
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(other.lockKey());
                var write = ScopeTransaction.open(transaction, other, audit, CLOCK);

                assertThrows(IllegalArgumentException.class, () -> log.append(write, waiver),
                        "a write of another scope should be refused");
            }
            assertFalse(Files.exists(log.store()), "the refused write should leave no log");
        }

        @Test
        @DisplayName("is refused a second time in one transaction")
        void twiceInOneTransaction() {
            var revocation = revocation(FIRST);
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = ScopeTransaction.open(transaction, scope, audit, CLOCK);
                log.append(write, WaiverRecordCodecTest.met());

                assertThrows(IllegalArgumentException.class, () -> log.append(write, revocation),
                        "a second append in one transaction is refused");
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
            var other = first.toBuilder().waiverId(SECOND).gate(lower() + "-" + lower()).requestId(null).build();
            append(first);
            append(other);
            append(revocation(FIRST));

            var lines = log.read();

            assertAll(
                    () -> assertEquals(Optional.of(revocation(FIRST)), lines.current(FIRST),
                            "the revocation should be the current line"),
                    () -> assertEquals(Optional.of(other), lines.current(SECOND),
                            "the other waiver should be its own line"),
                    () -> assertEquals(Optional.empty(), lines.current(ABSENT),
                            "an absent waiver should have no line"),
                    () -> assertEquals(Map.of(FIRST, revocation(FIRST), SECOND, other), lines.currentWaivers(),
                            "the current waivers should be keyed by id"),
                    () -> assertEquals(List.of(FIRST, SECOND), List.copyOf(lines.currentWaivers().keySet()),
                            "the ids should keep their first occurrence"));
        }

        @Test
        @DisplayName("of a repeated waiver id is the last readable line, taken whole, the earlier line kept")
        void repeatedIdTakenWhole() {
            var first = WaiverRecordCodecTest.met();
            var gate = lower() + "-" + lower();
            var secondWaived = first.toBuilder().gate(gate).requestId(null).build();
            append(first);
            append(secondWaived);

            var lines = log.read();

            assertAll(
                    () -> assertEquals(List.of(first, secondWaived), lines.records(),
                            "the log keeps both lines of the id"),
                    () -> assertEquals(Optional.of(secondWaived), lines.current(FIRST),
                            "the last readable line is the current one, whole"),
                    () -> assertEquals(gate, lines.currentWaivers().get(FIRST).gate(),
                            "the current waiver should carry the new gate"));
        }

        @Test
        @DisplayName("falls back to the previous version when the last line is torn, which is reported with its offset")
        void tornTailFallsBack() throws Exception {
            var waiver = WaiverRecordCodecTest.met();
            append(waiver);
            var size = Files.size(log.store());
            var torn = Arrays.copyOf(line(revocation(FIRST)), 30);
            Files.write(log.store(), torn, StandardOpenOption.APPEND);

            var lines = log.read();

            assertAll(
                    () -> assertEquals(Optional.of(waiver), lines.current(FIRST),
                            "the line before the torn tail is current"),
                    () -> assertEquals(OptionalLong.of(size), lines.tornTailOffset(),
                            "the torn tail should report its offset"),
                    () -> assertEquals(List.of(), lines.refused(), "a torn tail should be no refused line"),
                    () -> assertEquals(size + torn.length, Files.size(log.store()), "a read repairs nothing"));
        }

        @Test
        @DisplayName("is read past a refused line, which is reported with its line number (JSONL wire line)")
        void refusedLine() throws Exception {
            var waiver = WaiverRecordCodecTest.met();
            Files.write(log.store(), line(waiver));
            Files.write(log.store(), "{\"format_version\":1,\"event\":\"expired\"}\n".getBytes(UTF_8),
                    StandardOpenOption.APPEND);

            var lines = log.read();

            assertAll(
                    () -> assertEquals(Optional.of(waiver), lines.current(FIRST),
                            "the readable line should stay current"),
                    () -> assertEquals(List.of(2L), lines.refused().stream().map(JsonlStoreFile.Refused::line)
                            .toList(), "the second line should be reported refused"),
                    () -> assertEquals(StoreRefusal.Outcome.UNREADABLE, lines.refused().getFirst().refusal()
                            .outcome(), "the refused line should be unreadable"));
        }

        @Test
        @DisplayName("of a torn tail is repaired by the next append, with a record of the repair (JSONL wire line)")
        void tornTailRepairedByAppend() throws Exception {
            var waiver = WaiverRecordCodecTest.met();
            append(waiver);
            Files.write(log.store(), "{\"format_version\":1,".getBytes(UTF_8), StandardOpenOption.APPEND);

            append(revocation(FIRST));

            assertAll(
                    () -> assertEquals(List.of(waiver, revocation(FIRST)), log.read().records(),
                            "the append should follow the repaired line"),
                    () -> assertEquals(OptionalLong.empty(), log.read().tornTailOffset(),
                            "the torn tail should have been repaired"),
                    () -> assertEquals(List.of(AuditEvent.JSONL_TAIL_REPAIRED),
                            audit.events().stream().map(AuditEvent::event).toList(),
                            "the repair should be recorded in the audit"));
        }

        @Test
        @DisplayName("of a log that does not exist holds no waiver")
        void absent() {
            var lines = log.read();

            assertAll(
                    () -> assertEquals(List.of(), lines.entries(), "an absent log should hold no entry"),
                    () -> assertEquals(OptionalLong.empty(), lines.tornTailOffset(),
                            "an absent log should have no torn tail"),
                    () -> assertTrue(lines.currentWaivers().isEmpty(), "an absent log should hold no waiver"));
        }
    }

    @Test
    @DisplayName("lies at the path base of a plan; an epic, the workspace and the lesson store have none (layout)")
    void location() {
        var epic = JournalScope.epic(base, PROJECT, "epic-" + lower());
        var workspace = JournalScope.workspace(base, PROJECT);
        var lessons = JournalScope.lessons(base, PROJECT);

        assertAll(
                () -> assertEquals(scope.pathBase().resolve("waivers.jsonl"), log.store(),
                        "the log should lie at the plan path base"),
                () -> assertEquals("waivers.jsonl", log.storePath(), "the store path should be the file name"),
                () -> assertThrows(IllegalArgumentException.class, () -> new WaiverLog(epic),
                        "an epic should have no waiver log"),
                () -> assertThrows(IllegalArgumentException.class, () -> new WaiverLog(workspace),
                        "the workspace should have no waiver log"),
                () -> assertThrows(IllegalArgumentException.class, () -> new WaiverLog(lessons),
                        "the lesson store should have no waiver log"));
    }
}
