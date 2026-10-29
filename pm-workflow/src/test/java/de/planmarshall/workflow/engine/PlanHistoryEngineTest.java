/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.engine;

import static de.planmarshall.workflow.engine.EngineFixtures.EXECUTE;
import static de.planmarshall.workflow.engine.EngineFixtures.PLAN_ID;
import static de.planmarshall.workflow.engine.EngineFixtures.T0;
import static de.planmarshall.workflow.engine.EngineFixtures.plan;
import static de.planmarshall.workflow.engine.EngineFixtures.planStatus;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Consumer;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.JournalScope;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.PlanHistory;
import de.planmarshall.core.store.PlanHistoryLine;
import de.planmarshall.core.store.ScopeTransaction;
import de.planmarshall.core.store.WorkflowStatus;
import de.planmarshall.core.store.WorkflowStatusStore;

/**
 * The plan's history written by one advance (Store Schemas § The Plan's History): the lines of a transition are targets
 * of the transaction that writes the status, so the state change and its history are one journaled write, and the lines
 * name the states entered and left, the sub-workflows called and returned and the instance version in force.
 */
@DisplayName("Plan history of one advance")
class PlanHistoryEngineTest {

    private static final String CHAIN = """
            state execute.first
                auto test.step
                    done -> execute.second
                    failed -> handed_back

            state execute.second
                auto test.mark
                    done -> completed
                    failed -> handed_back
            """;

    private static final int INSTANCE_VERSION = 3;

    @TempDir
    Path base;

    private final EngineFixtures fixtures = new EngineFixtures();

    private JournalScope scope;

    private PlanHistory history;

    private WorkflowStatusStore store;

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
        scope = JournalScope.plan(base, "pm-mcp-core", PLAN_ID);
        Files.createDirectories(scope.journalFile().getParent());
        history = new PlanHistory(scope);
        store = new WorkflowStatusStore(scope);
    }

    /** Runs in one transaction of the plan and commits it, as one locked call of the runtime does. */
    private void committed(Consumer<ScopeTransaction> writing) {
        try (var lock = new HoldingEverything()) {
            var transaction = ScopeTransaction.open(lock, scope, event -> {
                // no audit record is owed
            }, Clock.fixed(T0, ZoneOffset.UTC));
            writing.accept(transaction);
            transaction.commit();
        }
    }

    @Test
    @DisplayName("the lines of the transition are written in the transaction of the status, naming the states entered "
            + "and left and the instance version in force")
    void linesOfOneAdvance() {
        var engine = fixtures.engine(plan("execute.first", CHAIN));

        committed(transaction -> engine.advance(planStatus(EXECUTE, "execute.first"), store, transaction, history,
                INSTANCE_VERSION));

        var lines = history.read().records();
        assertAll(
                () -> assertEquals(List.of("entered", "left", "entered", "left", "entered"),
                        lines.stream().map(line -> line.event().wireName()).toList(), lines::toString),
                () -> assertEquals(List.of("execute.first", "execute.first", "execute.second", "execute.second",
                        "completed"), lines.stream().map(PlanHistoryLine::node).toList()),
                () -> assertEquals(List.of("done", "done"), lines.stream().filter(line -> line.event()
                        == PlanHistoryLine.Event.LEFT).map(PlanHistoryLine::outcome).toList()),
                () -> assertEquals(List.of(1L, 2L, 3L, 4L, 5L), lines.stream().map(PlanHistoryLine::seq).toList()),
                () -> assertTrue(lines.stream().allMatch(line -> line.instanceVersion() == INSTANCE_VERSION)),
                () -> assertTrue(lines.stream().allMatch(line -> EXECUTE.equals(line.phase()))),
                () -> assertEquals("completed", store.read().orElseThrow().state()));
    }

    @Test
    @DisplayName("a second advance continues the sequence, one line number per line")
    void sequenceContinues() {
        var engine = fixtures.engine(plan("execute.first", CHAIN));
        committed(transaction -> engine.advance(planStatus(EXECUTE, "execute.first"), store, transaction, history,
                INSTANCE_VERSION));
        committed(transaction -> engine.advance(planStatus(EXECUTE, "execute.second"), store, transaction, history,
                INSTANCE_VERSION));

        var lines = history.read().records();
        assertAll(
                () -> assertEquals("completed", store.read().orElseThrow().state()),
                () -> assertEquals(lines.size(), lines.stream().map(PlanHistoryLine::seq).distinct().count()),
                () -> assertEquals(1L, lines.getFirst().seq()),
                () -> assertTrue(lines.size() > 5, lines::toString));
    }

    @Test
    @DisplayName("an event-resume advance writes its lines too: the left line of the settled state and the entered"
            + " line of its target, in the same transaction as the status")
    void eventResumeWritesHistory() {
        var states = """
                state execute.run
                    job test.fate
                        succeeded -> completed
                        killed -> handed_back
                """ + EngineFixtures.OTHER_FATES;
        var engine = fixtures.engine(plan("execute.run", states));
        var resting = planStatus(EXECUTE, "execute.run").toBuilder()
                .activeJob(new WorkflowStatus.ActiveJob("execute.run",
                        "b-0000000000000001"))
                .build();

        committed(transaction -> engine.advance(resting,
                new EngineEvent.JobSettled("b-0000000000000001", "succeeded"), store, transaction, history,
                INSTANCE_VERSION));

        var lines = history.read().records();
        assertAll(
                () -> assertTrue(lines.stream().anyMatch(line -> line.event() == PlanHistoryLine.Event.LEFT
                        && "execute.run".equals(line.node()) && "succeeded".equals(line.outcome())), lines::toString),
                () -> assertTrue(lines.stream().anyMatch(line -> line.event() == PlanHistoryLine.Event.ENTERED
                        && "completed".equals(line.node())), lines::toString),
                () -> assertTrue(lines.stream().allMatch(line -> line.instanceVersion() == INSTANCE_VERSION)),
                () -> assertEquals("completed", store.read().orElseThrow().state()));
    }

    @Test
    @DisplayName("a call and its return are both recorded, with the call path of the stack at the line")
    void callAndReturn() {
        var sub = EngineFixtures.parse("sub.commit.pmwf", EngineFixtures.SUB_COMMIT);
        var caller = plan("execute.commit", """
                state execute.commit
                    call sub.commit entry main
                        done -> completed
                        stepped_out -> plan.return
                """ + EngineFixtures.PLAN_RETURN);
        var engine = fixtures.engine(caller, sub);

        committed(transaction -> engine.advance(planStatus(EXECUTE, "execute.commit"), store, transaction, history,
                INSTANCE_VERSION));

        var lines = history.read().records();
        assertAll(
                () -> assertTrue(lines.stream().anyMatch(line -> line.event() == PlanHistoryLine.Event.CALLED
                        && "sub.commit".equals(line.node())), lines::toString),
                () -> assertTrue(lines.stream().anyMatch(line -> line.event() == PlanHistoryLine.Event.RETURNED
                        && "sub.commit".equals(line.node())), lines::toString),
                () -> assertTrue(lines.stream().anyMatch(line -> line.event() == PlanHistoryLine.Event.LEFT
                        && "sub.done".equals(line.node()) && "done".equals(line.outcome())), lines::toString),
                () -> assertEquals(List.of(), lines.stream()
                        .filter(line -> line.event() == PlanHistoryLine.Event.RETURNED).findFirst().orElseThrow()
                        .callPath(), "the returned line names the stack without the popped frame"));
    }
}
