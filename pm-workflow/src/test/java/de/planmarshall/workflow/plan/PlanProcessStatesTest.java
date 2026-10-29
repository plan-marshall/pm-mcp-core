/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.plan;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.JournalScope;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.PlanHistory;
import de.planmarshall.core.store.PlanHistoryLine;
import de.planmarshall.core.store.PlanHistoryLine.Event;
import de.planmarshall.core.store.ScopeTransaction;
import de.planmarshall.workflow.plan.PlanProcessStates.NodeState;
import de.planmarshall.workflow.plan.PlanProcessStates.State;

/**
 * The node states derived from a plan's history (Store Schemas § The Plan's History): done, running, waiting for the
 * operator, failed with its outcome, planned, not taken, and not in this plan with the prune reason of a pruned phase
 * call; derived from the log alone, so a runtime that restarts derives the same states.
 */
@DisplayName("PlanProcessStates")
class PlanProcessStatesTest {

    private static final Instant T0 = Instant.parse("2026-10-29T08:00:00Z");

    private static final InstancePosition AT_INIT = InstancePosition.at("init.ready");

    private static PlanInstance outline(PlanInstanceHeader.Lane lane) {
        var start = PlanFixtures.perLaneComposer().compose(PlanFixtures.perLaneRequest(CompositionEvent.START,
                PlanFixtures.facts().lane(lane).build(), Optional.empty(), AT_INIT));
        var first = assertInstanceOf(CompositionResult.Composed.class, start, start::toString).instance();
        var outline = PlanFixtures.perLaneComposer().compose(PlanFixtures.perLaneRequest(
                CompositionEvent.OUTLINE, PlanFixtures.facts().lane(lane).build(), Optional.of(first),
                InstancePosition.at(lane == PlanInstanceHeader.Lane.DEEP ? "outline.deep" : "outline.light")));
        return assertInstanceOf(CompositionResult.Composed.class, outline, outline::toString).instance();
    }

    private static PlanHistoryLine line(long seq, Event event, String node, String outcome) {
        return PlanHistoryLine.of(seq, event, node, List.of(), "phase-3-outline", outcome, 2, T0.plusSeconds(seq));
    }

    private static NodeState node(List<NodeState> states, String node) {
        return states.stream().filter(state -> state.node().equals(node)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("a state entered and left is done, one entered and not left is running, one never entered is planned, "
            + "and a pruned phase call is not in this plan with its reason")
    void doneRunningPlannedPruned() {
        var instance = outline(PlanInstanceHeader.Lane.DEEP);
        var states = PlanProcessStates.derive(instance, List.of(
                line(1, Event.ENTERED, "init.ready", null),
                line(2, Event.LEFT, "init.ready", "deep"),
                line(3, Event.ENTERED, "phases.plan", null)));

        assertAll(
                () -> assertEquals(State.DONE, node(states, "init.ready").state()),
                () -> assertEquals(Optional.of("deep"), node(states, "init.ready").outcome()),
                () -> assertEquals(State.RUNNING, node(states, "phases.plan").state()),
                () -> assertEquals(State.PLANNED, node(states, "phases.execute").state()),
                () -> assertEquals(State.NOT_IN_THIS_PLAN, node(states, "phases.plan-light").state()),
                () -> assertEquals(Optional.of("other_lane"), node(states, "phases.plan-light").reason()),
                () -> assertTrue(states.stream().anyMatch(state -> state.node().startsWith("phase-plan.")),
                        states::toString));
    }

    @Test
    @DisplayName("a state the plan rests in at a question wait waits for the operator")
    void waitingForOperator() {
        var instance = outline(PlanInstanceHeader.Lane.LIGHT);
        var states = PlanProcessStates.derive(instance, List.of(
                line(1, Event.ENTERED, "outline.light-approve", null)));

        assertEquals(State.WAITING_FOR_OPERATOR, node(states, "outline.light-approve").state());
    }

    @Test
    @DisplayName("a terminal state of a failure outcome is failed with its outcome, and a state never entered after the "
            + "plan ended is not taken")
    void failedAndNotTaken() {
        var instance = outline(PlanInstanceHeader.Lane.DEEP);
        var states = PlanProcessStates.derive(instance, List.of(
                line(1, Event.ENTERED, "phases.plan", null),
                line(2, Event.LEFT, "phases.plan", "returned"),
                line(3, Event.ENTERED, "plan.return", null),
                line(4, Event.LEFT, "plan.return", "done"),
                line(5, Event.ENTERED, "handed_back", null)));

        assertAll(
                () -> assertEquals(State.FAILED, node(states, "handed_back").state()),
                () -> assertEquals(Optional.of("handed_back"), node(states, "handed_back").outcome()),
                () -> assertEquals(State.DONE, node(states, "phases.plan").state()),
                () -> assertEquals(State.NOT_TAKEN, node(states, "phases.execute").state()));
    }

    @Test
    @DisplayName("the states are derived from the stored log alone, so a restart reads the same states")
    void afterRestart() throws Exception {
        var instance = outline(PlanInstanceHeader.Lane.DEEP);
        var lines = List.of(
                line(1, Event.ENTERED, "init.ready", null),
                line(2, Event.LEFT, "init.ready", "deep"),
                line(3, Event.ENTERED, "phases.plan", null));
        var base = Files.createTempDirectory("plan-history");
        try {
            var scope = JournalScope.plan(base, "pm-mcp-core", "plan-history-fixture");
            Files.createDirectories(scope.journalFile().getParent());
            var history = new PlanHistory(scope);
            committed(scope, transaction -> history.append(transaction, lines));

            var reread = new PlanHistory(scope).read().records();
            assertEquals(PlanProcessStates.derive(instance, lines), PlanProcessStates.derive(instance, reread));
        } finally {
            deleteRecursively(base);
        }
    }

    private static void committed(JournalScope scope, Consumer<ScopeTransaction> writing) {
        try (var lock = new HoldingEverything()) {
            var transaction = ScopeTransaction.open(lock, scope, event -> {
                // no audit record is owed
            }, Clock.fixed(T0, ZoneOffset.UTC));
            writing.accept(transaction);
            transaction.commit();
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

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
}
