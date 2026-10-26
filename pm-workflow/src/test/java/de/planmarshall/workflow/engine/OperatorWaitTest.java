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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Map;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.spi.AwaitedEvent;
import de.planmarshall.core.store.JournalScopeType;
import de.planmarshall.core.store.WorkflowStatus;

/**
 * The operator wait of the engine (Workflow DSL, Waiting for the Operator; PM-WF-20): a resting link state awaits the
 * operator's act, opens no task, binds no job, holds no worker and no model slot, and nothing decides it by time. One
 * plan waiting does not hold another one back.
 */
@DisplayName("Operator wait")
class OperatorWaitTest {

    private static final String STATES = """
            state execute.wait
                phase execute
                link next proceed ()
                    do test.proceed
                        done -> execute.build
                        failed -> handed_back

            state execute.build
                phase execute
                job test.build
                    succeeded -> completed
                    failed -> handed_back
                    cancelled -> handed_back
            """;

    private final EngineFixtures fixtures = new EngineFixtures();

    @Test
    @DisplayName("Should rest in the operator wait without a task, a job, a worker or a slot, and take no time")
    void restsWithoutWork() {
        var engine = fixtures.engine(EngineFixtures.plan("execute.wait", STATES));

        var started = assertInstanceOf(EngineStep.Resting.class,
                engine.start(JournalScopeType.PLAN, "waiting-plan"));
        var status = started.status();
        var wait = engine.restingWait(status).orElseThrow();
        assertAll(
                () -> assertEquals("execute.wait", wait.stateId()),
                () -> assertEquals(AwaitedEvent.OPERATOR_ACT, wait.awaitedEvent()),
                () -> assertTrue(status.activeJobBinding().isEmpty(), "no job is bound"),
                () -> assertTrue(status.activeJobs().isEmpty(), "no job runs"),
                () -> assertTrue(status.activeTasks().isEmpty(), "no task is open"),
                () -> assertTrue(fixtures.taskCreator.requests.isEmpty(), "no task is offered"),
                () -> assertEquals(JournalScopeType.PLAN, status.scopeType()));

        assertThrows(IllegalArgumentException.class,
                () -> engine.advance(status, new EngineEvent.TimeElapsed(EngineFixtures.T0.plus(Duration.ofDays(30)))),
                "time does not end an operator wait");
        assertEquals(started, engine.advance(status), "the wait is unchanged");

        assertThrows(IllegalArgumentException.class,
                () -> engine.advance(status, new EngineEvent.OperatorAct("execute.wait", "unknown")),
                "only a link the state offers is accepted");

        var acted = assertInstanceOf(EngineStep.Resting.class,
                engine.advance(status, new EngineEvent.OperatorAct("execute.wait", "proceed")));
        assertAll(
                () -> assertEquals("execute.build", acted.stateId()),
                () -> assertEquals(AwaitedEvent.JOB_SETTLED, acted.awaitedEvent()),
                () -> assertEquals(1, fixtures.jobStarter.requests.size(), "the link's transition started the job"));
    }

    @Test
    @DisplayName("Should let another plan run on while one plan waits")
    void otherPlansRunOn() {
        var engine = fixtures.engine(EngineFixtures.plan("execute.wait", STATES));
        var waiting = assertInstanceOf(EngineStep.Resting.class,
                engine.start(JournalScopeType.PLAN, "waiting-plan")).status();
        var running = fixtures.planStatus(EngineFixtures.EXECUTE, "execute.build").toBuilder()
                .activeJob(new WorkflowStatus.ActiveJob("execute.build", "b-0000000000000001"))
                .activeJobs(Map.of("test.build",
                        new WorkflowStatus.JobEntry("b-0000000000000001", "build", "running", EngineFixtures.T0)))
                .build();

        var settled = engine.advance(running, new EngineEvent.JobSettled("b-0000000000000001", "succeeded"));

        assertAll(
                () -> assertEquals("merged", assertInstanceOf(EngineStep.Ended.class, settled).outcomeId()),
                () -> assertEquals(AwaitedEvent.OPERATOR_ACT, engine.restingWait(waiting).orElseThrow().awaitedEvent()),
                () -> assertTrue(waiting.activeTasks().isEmpty(), "the waiting plan still holds nothing"));
    }
}
