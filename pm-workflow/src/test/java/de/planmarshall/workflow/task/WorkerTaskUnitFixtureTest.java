/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.task;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.queue.QueueStore;
import de.planmarshall.core.queue.QueueTaskRecord;
import de.planmarshall.core.spi.OutcomeClass;
import de.planmarshall.workflow.ast.OutcomeTransition;
import de.planmarshall.workflow.ast.StateBehavior;
import de.planmarshall.workflow.ast.UnitFrontmatter;
import de.planmarshall.workflow.ast.UnitOrigin;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.dsl.ParseResult;
import de.planmarshall.workflow.dsl.WorkflowParser;

/**
 * The task protocol is proved against a fixture {@code worker-task} unit: the unit compiles with the protocol's states
 * and terminals, and the protocol's own transitions end at the terminal the fixture names (Model Work § The
 * {@code worker-task} Sub-Workflow).
 */
// Every datum in this class is a contract: the fixture resource, its DSL state and exit names, and the outcome classes.
@SuppressWarnings("java:S5778")
// the fixture test calls one failing parse per case, by design
@DisplayName("Worker-task fixture unit")
class WorkerTaskUnitFixtureTest {

    private static final String RESOURCE = "task/worker-task-fixture.pmwf";

    private static WorkflowDefinition fixture() throws IOException {
        try (InputStream stream = WorkerTaskUnitFixtureTest.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            var source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            var parsed = WorkflowParser.parse(RESOURCE, source, UnitOrigin.DEFAULT);
            return assertInstanceOf(ParseResult.Parsed.class, parsed,
                    () -> "the fixture unit is refused: " + parsed).definition();
        }
    }

    /** The outcome class of a terminal state of the unit; a state without a terminal outcome fails the test. */
    private static OutcomeClass outcomeClassOf(WorkflowDefinition unit, String stateId) {
        return Objects.requireNonNull(unit.states().get(stateId).terminalOutcome(), stateId).outcomeClass();
    }

    @Test
    @DisplayName("the fixture unit compiles with the protocol's states and terminals")
    void fixtureCompiles() throws Exception {
        var unit = fixture();

        assertAll(
                () -> assertEquals("worker-task", unit.id()),
                () -> assertEquals(List.of("task.queued"), unit.startingStates()),
                () -> assertEquals(List.of("submitted", "asked", "infeasible", "lost"),
                        unit.frontmatter().exits().stream().map(UnitFrontmatter.InterfaceElement::name).toList()),
                () -> assertEquals(List.of("task.queued", "task.waiting", "task.offered", "task.executing",
                                "task.consulting", "task.awaiting_answer", "task.suspended", "task.submitted",
                                "task.asked", "task.infeasible", "task.lost"),
                        List.copyOf(unit.states().keySet())),
                () -> assertEquals(OutcomeClass.SUCCESS, outcomeClassOf(unit, "task.submitted")),
                () -> assertEquals(OutcomeClass.UNSETTLED, outcomeClassOf(unit, "task.asked")),
                () -> assertEquals(OutcomeClass.FAILURE, outcomeClassOf(unit, "task.infeasible")),
                () -> assertEquals(OutcomeClass.INDETERMINATE, outcomeClassOf(unit, "task.lost")),
                () -> assertInstanceOf(StateBehavior.TerminalState.class,
                        unit.states().get("task.lost").behavior()));
    }

    @Test
    @DisplayName("the offer, the acknowledgement and the submission follow the fixture's transitions")
    void protocolFollowsFixture(@TempDir Path base) throws Exception {
        var unit = fixture();
        var locks = new TestLocks();
        var queue = QueueStore.ofProject(base, "project-a", locks);
        queue.create(TaskFixtures.queued("qt-0000000000000001", TaskFixtures.ROLE).build());
        var protocol = TaskProtocol.builder(queue, locks).build();

        var offered = TaskFixtures.task(protocol.wait(TaskFixtures.waitFor(TaskFixtures.GENERATION),
                TaskFixtures.AT));
        var acknowledged = assertInstanceOf(CallResult.Accepted.class,
                protocol.acknowledge(TaskFixtures.call(offered), TaskFixtures.AT.plusSeconds(1)));
        var submitted = assertInstanceOf(CallResult.Accepted.class,
                protocol.submit(TaskFixtures.call(offered), TaskFixtures.AT.plusSeconds(2)));

        assertAll(
                () -> assertEquals("task.offered", targetOf(unit, "task.waiting", "offered")),
                () -> assertEquals("task.executing", targetOf(unit, "task.offered", "acknowledged")),
                () -> assertEquals("task.submitted", targetOf(unit, "task.executing", "submitted")),
                () -> assertEquals(QueueTaskRecord.Status.EXECUTING, acknowledged.task().status()),
                () -> assertEquals(QueueTaskRecord.Outcome.SUBMITTED, submitted.task().outcome()));
    }

    private static String targetOf(WorkflowDefinition unit, String stateId, String outcome) {
        var behavior = unit.states().get(stateId).behavior();
        var transitions = switch (behavior) {
            case StateBehavior.JobStep job -> job.transitions();
            case StateBehavior.AutoStep auto -> auto.transitions();
            default -> List.<OutcomeTransition>of();
        };
        return transitions.stream().filter(transition -> transition.outcomeName().equals(outcome))
                .map(OutcomeTransition::targetStateId).findFirst().orElse(null);
    }
}
