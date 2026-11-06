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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.PlanHistoryLine.Event;

/**
 * The plan's history (Store Schemas § The Plan's History): an append-only log of the states entered and left, the
 * sub-workflows called and returned and the instance versions taking force, written in the transaction of the
 * transition it records, covered by the plan's digest sidecar, never rewritten. The phase name and the event wire
 * names are contracts; plan ids, nodes, outcomes and instants are generated.
 */
@EnableGeneratorController
@DisplayName("PlanHistory")
class PlanHistoryTest {

    private static final String PROJECT = "pm-mcp-core";

    // Phase wire name of the workflow DSL.
    private static final String PHASE = "phase-5-execute";

    @TempDir
    Path base;

    private Instant t0;

    private String node;

    private String outcome;

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
        t0 = Instant.ofEpochSecond(Generators.longs(1_700_000_000L, 1_800_000_000L).next());
        node = lowerName() + "." + lowerName();
        outcome = lowerName();
        scope = JournalScope.plan(base, PROJECT, "plan-" + lowerName());
        Files.createDirectories(scope.journalFile().getParent());
        history = new PlanHistory(scope);
    }

    private static String lowerName() {
        return Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }

    /** Runs in one transaction of the plan and commits it. */
    private void committed(Consumer<ScopeTransaction> writing) {
        try (var lock = new HoldingEverything()) {
            var transaction = ScopeTransaction.open(lock, scope, event -> {
                // no audit record is owed
            }, Clock.fixed(t0, ZoneOffset.UTC));
            writing.accept(transaction);
            transaction.commit();
        }
    }

    private PlanHistoryLine entered(long seq, String stateNode, String phase, String lineOutcome) {
        return PlanHistoryLine.of(seq, Event.ENTERED, stateNode, List.of(), phase, lineOutcome, 1, t0.plusSeconds(seq));
    }

    private PlanHistoryLine left(long seq, String stateNode, String lineOutcome) {
        return PlanHistoryLine.of(seq, Event.LEFT, stateNode, List.of(PHASE), PHASE, lineOutcome, 1,
                t0.plusSeconds(seq));
    }

    @Test
    @DisplayName("the lines of one transition are one append target, covered by the digest sidecar")
    void writtenInOneTransaction() {
        committed(transaction -> history.append(transaction, List.of(entered(1, node, PHASE, null),
                left(2, node, outcome))));

        var lines = history.read().records();
        var sidecar = DigestSidecar.load(scope.sidecarFile());
        assertAll(
                () -> assertEquals(2, lines.size(), "both lines of the transition should be read"),
                () -> assertEquals(List.of(1L, 2L), lines.stream().map(PlanHistoryLine::seq).toList(),
                        "the lines should keep their sequence"),
                () -> assertEquals(Event.LEFT, lines.getLast().event(), "the last line should be the left event"),
                () -> assertEquals(outcome, lines.getLast().outcome(), "the outcome of the line should be read"),
                () -> assertTrue(sidecar.digest(PlanHistory.FILE_NAME).isPresent(),
                        "the sidecar should cover the history"));
    }

    @Test
    @DisplayName("a line without a phase or an outcome round-trips as that line")
    void roundTrip() {
        var line = PlanHistoryLine.of(1, Event.CALLED, node, List.of(lowerName(), PHASE), null, null,
                Generators.integers(1, 99).next(), t0);
        committed(transaction -> history.append(transaction, line));

        assertEquals(line, history.read().records().getFirst(), "the line should round-trip unchanged");
    }

    @Test
    @DisplayName("the next sequence number is one above the last readable line")
    void nextSeq() {
        var count = Generators.integers(1, 5).next();
        var lines = LongStream.rangeClosed(1, count).mapToObj(seq -> entered(seq, node, null, null))
                .toList();
        committed(transaction -> history.append(transaction, lines));

        assertEquals(count + 1, history.nextSeq(), "the next sequence should follow the last");
    }

    @Test
    @DisplayName("an unreadable line is reported with its number and the other lines stay readable")
    void refusedLine() throws Exception {
        committed(transaction -> history.append(transaction, List.of(entered(1, node, null, null))));
        // A torn wire line: a JSON object without the members of a history line.
        Files.writeString(history.store(), "{\"format_version\":1}\n", StandardCharsets.UTF_8,
                StandardOpenOption.APPEND);

        var read = history.read();
        assertAll(
                () -> assertEquals(1, read.records().size(), "the good line should stay readable"),
                () -> assertEquals(1, read.refused().size(), "the torn line should be refused"),
                () -> assertEquals(2, read.refused().getFirst().line(), "the refusal should name line two"));
    }

    @Test
    @DisplayName("the history is append-only: a second commit adds its lines and keeps the earlier bytes")
    void appendedNotRewritten() throws Exception {
        committed(transaction -> history.append(transaction, entered(1, node, null, null)));
        var first = Files.readString(history.store(), StandardCharsets.UTF_8);
        committed(transaction -> history.append(transaction, left(2, node, outcome)));

        assertAll(
                () -> assertTrue(Files.readString(history.store(), StandardCharsets.UTF_8).startsWith(first),
                        "the earlier bytes should be kept"),
                () -> assertEquals(2, history.read().records().size(), "the second line should be appended"));
    }

    @Test
    @DisplayName("an empty append and a line of another scope are refused, nothing written")
    void refusals() throws Exception {
        var other = new PlanHistory(JournalScope.plan(base, PROJECT, "other-" + lowerName()));
        Files.createDirectories(other.store().getParent());

        try (var lock = new HoldingEverything()) {
            var transaction = ScopeTransaction.open(lock, scope, event -> {
                // no audit record is owed
            }, Clock.fixed(t0, ZoneOffset.UTC));
            var otherLine = entered(1, node, null, null);
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> history.append(transaction, List.of()), "an empty append should be refused"),
                    () -> assertFalse(Files.exists(history.store()), "nothing should have been written"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> other.append(transaction, otherLine), "another scope's line should be refused"),
                    () -> assertThrows(IllegalStateException.class, transaction::commit,
                            "the refused transaction should not commit"));
        }
    }

    @Test
    @DisplayName("lives only in a plan scope")
    void planScopeOnly() {
        var epic = JournalScope.epic(base, PROJECT, "epic-" + lowerName());

        assertThrows(IllegalArgumentException.class, () -> new PlanHistory(epic),
                "an epic scope should have no history");
    }

    @Test
    @DisplayName("a line that does not fit the schema is refused at construction (schema boundaries)")
    void lineValidation() {
        var sixFrames = List.of(lowerName(), lowerName(), lowerName(), lowerName(), lowerName(), lowerName());
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PlanHistoryLine(2, 1, Event.ENTERED, node, List.of(), null, null, 1, t0),
                        "another format version should be refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> PlanHistoryLine.of(0, Event.ENTERED, node, List.of(), null, null, 1, t0),
                        "a sequence of zero should be refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> PlanHistoryLine.of(1, Event.ENTERED, node, sixFrames, null, null, 1, t0),
                        "a stack of six frames should be refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> PlanHistoryLine.of(1, Event.LEFT, node, List.of(), null, "Done", 1, t0),
                        "an outcome of another form should fail"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> PlanHistoryLine.of(1, Event.LEFT, node, List.of(), null, null, 0, t0),
                        "an instance version of zero should fail"),
                () -> assertThrows(IllegalArgumentException.class, () -> Event.ofWireName("skipped"),
                        "an unknown event wire name should fail"));
    }
}
