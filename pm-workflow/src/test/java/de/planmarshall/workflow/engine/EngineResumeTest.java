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

import static de.planmarshall.workflow.engine.EngineFixtures.OTHER_FATES;
import static de.planmarshall.workflow.engine.EngineFixtures.PLAN_ID;
import static de.planmarshall.workflow.engine.EngineFixtures.T0;
import static de.planmarshall.workflow.engine.EngineFixtures.callingPlan;
import static de.planmarshall.workflow.engine.EngineFixtures.parse;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.spi.AwaitedEvent;
import de.planmarshall.core.spi.QuestionRecordReader.QuestionRead;
import de.planmarshall.core.store.JournalScope;
import de.planmarshall.core.store.JournalScopeType;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.ScopeTransaction;
import de.planmarshall.core.store.WorkflowStatus;
import de.planmarshall.core.store.WorkflowStatusStore;

/**
 * Kill and resume (PM-ARCH-4: zero state loss; PM-ARCH-6; PM-WF-16: a resting state inside a sub-workflow survives a
 * restart with its call stack): a run is stopped after every persisted step, the in-memory engine and its ports are
 * discarded, and a new engine reads the status through {@link WorkflowStatusStore}; it finds the same state and call
 * stack, only waits on a bound job instead of starting it again, and takes the same next step as a run that was never
 * stopped.
 */
@DisplayName("Engine resume")
class EngineResumeTest {

    private static final String PROJECT = "pm-mcp-core";

    private static final String SUB_JOB = """
            ---
            name: sub.worker
            description: A unit written for the tests of pm-workflow.
            type: subworkflow
            icon: workflow.default
            entry: main
            exit: done
            modification_date: 2026-01-15
            version: 0.1.0
            author: plan-marshall
            ---
            workflow sub.worker version 1 format 1
            scope subworkflow
            entry main -> work.run

            state work.run
                job test.fate
                    succeeded -> work.done
                    killed -> work.stepped-out
            %s
            state work.done terminal done success

            state work.stepped-out terminal stepped_out stepped_out
            """.formatted(OTHER_FATES.replace("handed_back", "work.stepped-out"));

    private static final String RESUME_STATES = """
            state execute.invoke
                call sub.worker
                    done -> execute.review
                    stepped_out -> plan.return

            state execute.review
                job llm change role reviewer
                    approved -> execute.ask
                    lost -> handed_back

            state execute.ask
                question clarification
                    answered -> execute.backoff

            state execute.backoff
                composite_job test.backoff
                    succeeded -> completed
                    failed -> handed_back
                    cancelled -> handed_back
            """;

    /** The events that move the scope from one resting state to the next, in order. */
    private static final List<EngineEvent> EVENTS = List.of(
            new EngineEvent.JobSettled("b-0000000000000001", "succeeded"),
            new EngineEvent.TasksSettled(Map.of("change", "submitted"), "approved"),
            new EngineEvent.QuestionResolved("Q-001", "answered"),
            new EngineEvent.TimeElapsed(T0.plusSeconds(30)));

    @TempDir
    Path base;

    private JournalScope scope;

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
        scope = JournalScope.plan(base, PROJECT, PLAN_ID);
        Files.createDirectories(scope.journalFile().getParent());
    }

    /** @return a fresh engine with fresh ports, as a restarted runtime builds it */
    private static Restarted restart() {
        var fixtures = new EngineFixtures();
        fixtures.questionReader.answer = new QuestionRead.Resolved("answered");
        return new Restarted(fixtures, fixtures.engine(callingPlan("execute.invoke", RESUME_STATES),
                parse("sub.pmwf", SUB_JOB)));
    }

    private record Restarted(EngineFixtures fixtures, WorkflowEngine engine) {
    }

    /** Runs one advance in a transaction of the scope and commits it, as one locked call of the runtime does. */
    private EngineStep committed(Function<ScopeTransaction, EngineStep> advance) {
        try (var lock = new HoldingEverything()) {
            var transaction = ScopeTransaction.open(lock, scope, event -> {
                // no audit record is owed by a status write
            }, Clock.fixed(T0, ZoneOffset.UTC));
            var step = advance.apply(transaction);
            transaction.commit();
            return step;
        }
    }

    private WorkflowStatus read() {
        return new WorkflowStatusStore(scope).read().orElseThrow();
    }

    /** @return the status after the start and after each event, from one run that is never stopped */
    private static List<WorkflowStatus> uninterrupted() {
        var run = restart();
        var statuses = new ArrayList<WorkflowStatus>();
        var status = run.engine().start(JournalScopeType.PLAN, PLAN_ID).status();
        statuses.add(status);
        for (var event : EVENTS) {
            status = run.engine().advance(status, event).status();
            statuses.add(status);
        }
        return statuses;
    }

    @Test
    @DisplayName("Should continue at the same state and call stack with the same next step after every stop")
    void shouldResumeAfterEveryPersistedStep() {
        var expected = uninterrupted();
        var first = restart().engine().start(JournalScopeType.PLAN, PLAN_ID).status();
        committed(transaction -> persist(transaction, first));

        for (var index = 0; index < EVENTS.size(); index++) {
            var stored = read();
            assertEquals(expected.get(index), stored, "the store holds step " + index);
            var restarted = restart();
            var event = EVENTS.get(index);

            var step = committed(transaction -> restarted.engine().advance(stored, event,
                    new WorkflowStatusStore(scope), transaction));

            assertEquals(expected.get(index + 1), step.status(), "the next step after a stop at " + index);
            assertTrue(restarted.fixtures().jobStarter.requests.isEmpty(), "no job started again at " + index);
        }
        assertEquals(expected.getLast(), read());
    }

    @Test
    @DisplayName("Should keep the call stack of a resting state inside a sub-workflow across a stop")
    void shouldKeepCallStackAcrossStop() {
        var first = restart().engine().start(JournalScopeType.PLAN, PLAN_ID).status();
        committed(transaction -> persist(transaction, first));

        var stored = read();

        assertAll(
                () -> assertEquals("work.run", stored.state()),
                () -> assertEquals(first.callStack(), stored.callStack()),
                () -> assertEquals("sub.worker", stored.callStack().getFirst().subworkflowId()),
                () -> assertEquals("main", stored.callStack().getFirst().entry()),
                () -> assertEquals("execute.invoke", stored.callStack().getFirst().callerStateId()),
                () -> assertEquals("b-0000000000000001", stored.activeJobBinding().orElseThrow().jobId()));
    }

    @Test
    @DisplayName("Should keep the entry of a stored frame across a restart and return into the caller as before")
    void shouldKeepEntryAndReturnAfterRestart() {
        var first = restart().engine().start(JournalScopeType.PLAN, PLAN_ID).status();
        committed(transaction -> persist(transaction, first));
        var stored = read();
        var restarted = restart();

        var waited = restarted.engine().advance(stored);
        var returned = committed(transaction -> restarted.engine().advance(stored, EVENTS.getFirst(),
                new WorkflowStatusStore(scope), transaction));

        assertAll(
                () -> assertEquals("main", stored.callStack().getFirst().entry()),
                () -> assertEquals(stored.callStack(), waited.status().callStack()),
                () -> assertEquals("execute.review", returned.status().state()),
                () -> assertTrue(returned.status().callStack().isEmpty()),
                () -> assertEquals(returned.status(), read()));
    }

    @Test
    @DisplayName("Should only wait on a bound job after a restart and write nothing for it")
    void shouldOnlyWaitOnBoundJob() {
        var first = restart().engine().start(JournalScopeType.PLAN, PLAN_ID).status();
        committed(transaction -> persist(transaction, first));
        var stored = read();
        var restarted = restart();

        try (var lock = new HoldingEverything()) {
            var transaction = ScopeTransaction.open(lock, scope, event -> {
                // no audit record is owed
            }, Clock.fixed(T0, ZoneOffset.UTC));

            var waited = restarted.engine().advance(stored, new WorkflowStatusStore(scope), transaction);

            assertAll(
                    () -> assertEquals(stored, assertInstanceOf(EngineStep.Resting.class, waited).status()),
                    () -> assertTrue(restarted.fixtures().jobStarter.requests.isEmpty()),
                    () -> assertThrows(IllegalStateException.class, transaction::commit,
                            "a status the advance did not change is not written"));
        }
    }

    /** Writes a status in a transaction and returns the step of a resting status, for {@link #committed}. */
    private EngineStep persist(ScopeTransaction transaction, WorkflowStatus status) {
        new WorkflowStatusStore(scope).write(transaction, status);
        return new EngineStep.Resting(status, status.state(), AwaitedEvent.JOB_SETTLED);
    }
}
