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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.PlanHistoryLine.Event;

/**
 * The plan's history (Store Schemas § The Plan's History): an append-only log of the states entered and left, the
 * sub-workflows called and returned and the instance versions taking force, written in the transaction of the
 * transition it records, covered by the plan's digest sidecar, never rewritten.
 */
@DisplayName("PlanHistory")
class PlanHistoryTest {

    private static final String PROJECT = "pm-mcp-core";

    private static final String PLAN = "plan-history-fixture";

    private static final Instant T0 = Instant.parse("2026-10-29T08:00:00Z");

    @TempDir
    Path base;

    private JournalScope scope;

    private PlanHistory history;

    /** A lock transaction that holds every key: the test is the only writer of its scope. */
    private static final class HoldingEverything implements LockTransaction {

        @Override
        public void acquire(LockKey key) {
            // every key is held already
        }

        @Override
        public boolean holds(LockKey key) {
            return true;
        }

        @Override
        public void close() {
            // nothing to release
        }
    }

    @BeforeEach
    void createScope() throws IOException {
        scope = JournalScope.plan(base, PROJECT, PLAN);
        Files.createDirectories(scope.journalFile().getParent());
        history = new PlanHistory(scope);
    }

    /** Runs in one transaction of the plan and commits it. */
    private void committed(Consumer<ScopeTransaction> writing) {
        try (var lock = new HoldingEverything()) {
            var transaction = ScopeTransaction.open(lock, scope, event -> {
                // no audit record is owed
            }, Clock.fixed(T0, ZoneOffset.UTC));
            writing.accept(transaction);
            transaction.commit();
        }
    }

    private static PlanHistoryLine entered(long seq, String node, String phase, String outcome) {
        return PlanHistoryLine.of(seq, Event.ENTERED, node, List.of(), phase, outcome, 1, T0.plusSeconds(seq));
    }

    private static PlanHistoryLine left(long seq, String node, String outcome) {
        return PlanHistoryLine.of(seq, Event.LEFT, node, List.of("phase-5-execute"), "phase-5-execute", outcome, 1,
                T0.plusSeconds(seq));
    }

    @Test
    @DisplayName("the lines of one transition are one append target, covered by the digest sidecar")
    void writtenInOneTransaction() {
        committed(transaction -> history.append(transaction, List.of(entered(1, "execute.first", "phase-5-execute",
                null), left(2, "execute.first", "done"))));

        var lines = history.read().records();
        var sidecar = DigestSidecar.load(scope.sidecarFile());
        assertAll(
                () -> assertEquals(2, lines.size()),
                () -> assertEquals(List.of(1L, 2L), lines.stream().map(PlanHistoryLine::seq).toList()),
                () -> assertEquals(Event.LEFT, lines.getLast().event()),
                () -> assertEquals("done", lines.getLast().outcome()),
                () -> assertTrue(sidecar.digest(PlanHistory.FILE_NAME).isPresent(),
                        sidecar::toString));
    }

    @Test
    @DisplayName("a line without a phase or an outcome round-trips as that line")
    void roundTrip() {
        var line = PlanHistoryLine.of(1, Event.CALLED, "sub.commit", List.of("plan", "phase-5-execute"), null, null,
                2, T0);
        committed(transaction -> history.append(transaction, line));

        assertEquals(line, history.read().records().getFirst());
    }

    @Test
    @DisplayName("the next sequence number is one above the last readable line")
    void nextSeq() {
        committed(transaction -> history.append(transaction, List.of(entered(1, "a", null, null))));

        assertEquals(2, history.nextSeq());
    }

    @Test
    @DisplayName("an unreadable line is reported with its number and the other lines stay readable")
    void refusedLine() throws Exception {
        committed(transaction -> history.append(transaction, List.of(entered(1, "a", null, null))));
        Files.writeString(history.store(), "{\"format_version\":1}\n", StandardCharsets.UTF_8,
                StandardOpenOption.APPEND);

        var read = history.read();
        assertAll(
                () -> assertEquals(1, read.records().size()),
                () -> assertEquals(1, read.refused().size()),
                () -> assertEquals(2, read.refused().getFirst().line()));
    }

    @Test
    @DisplayName("the history is append-only: a second commit adds its lines and keeps the earlier bytes")
    void appendedNotRewritten() throws Exception {
        committed(transaction -> history.append(transaction, entered(1, "a", null, null)));
        var first = Files.readString(history.store(), StandardCharsets.UTF_8);
        committed(transaction -> history.append(transaction, left(2, "a", "done")));

        assertAll(
                () -> assertTrue(Files.readString(history.store(), StandardCharsets.UTF_8).startsWith(first)),
                () -> assertEquals(2, history.read().records().size()));
    }

    @Test
    @DisplayName("an empty append and a line of another scope are refused, nothing written")
    void refusals() throws Exception {
        var other = new PlanHistory(JournalScope.plan(base, PROJECT, "another-plan"));
        Files.createDirectories(other.store().getParent());

        try (var lock = new HoldingEverything()) {
            var transaction = ScopeTransaction.open(lock, scope, event -> {
                // no audit record is owed
            }, Clock.fixed(T0, ZoneOffset.UTC));
            var otherLine = entered(1, "a", null, null);
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> history.append(transaction, List.of())),
                    () -> assertFalse(Files.exists(history.store())),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> other.append(transaction, otherLine)),
                    () -> assertThrows(IllegalStateException.class, transaction::commit));
        }
    }

    @Test
    @DisplayName("lives only in a plan scope")
    void planScopeOnly() {
        var epic = JournalScope.epic(base, PROJECT, "epic-fixture");

        assertThrows(IllegalArgumentException.class, () -> new PlanHistory(epic));
    }

    @Test
    @DisplayName("a line that does not fit the schema is refused at construction")
    void lineValidation() {
        var sixFrames = List.of("a", "b", "c", "d", "e", "f");
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PlanHistoryLine(2, 1, Event.ENTERED, "a", List.of(), null, null, 1, T0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> PlanHistoryLine.of(0, Event.ENTERED, "a", List.of(), null, null, 1, T0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> PlanHistoryLine.of(1, Event.ENTERED, "a", sixFrames, null, null, 1, T0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> PlanHistoryLine.of(1, Event.LEFT, "a", List.of(), null, "Done", 1, T0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> PlanHistoryLine.of(1, Event.LEFT, "a", List.of(), null, null, 0, T0)),
                () -> assertThrows(IllegalArgumentException.class, () -> Event.ofWireName("skipped")));
    }
}
