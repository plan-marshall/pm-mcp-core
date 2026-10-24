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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.queue.QueueStore;
import de.planmarshall.core.queue.QueueTaskRecord;
import de.planmarshall.core.queue.QueueTaskRecord.AttemptEnd;
import de.planmarshall.core.queue.QueueTaskRecord.Outcome;
import de.planmarshall.core.queue.QueueTaskRecord.SignalAction;
import de.planmarshall.core.queue.QueueTaskRecord.Status;
import de.planmarshall.core.role.RoleSkill;

/**
 * The checks of a call the specification orders before the lease's own link: a job that ended, the skills a submit
 * requires, the ends that are no report, and the {@code infeasible} outcome.
 */
@SuppressWarnings("java:S5778")
// the refusal cases call one throwing method per case, by design
@DisplayName("Task protocol refusals")
class TaskProtocolRefusalTest {

    private record Harness(QueueStore queue, TaskProtocol protocol) {
    }

    private static Harness harness(Path base, Consumer<TaskProtocol.Builder> customise) {
        var locks = new TestLocks();
        var queue = QueueStore.ofProject(base, "project-a", locks);
        queue.create(TaskFixtures.queued("qt-0000000000000001", TaskFixtures.ROLE).build());
        var builder = TaskProtocol.builder(queue, locks);
        customise.accept(builder);
        return new Harness(queue, builder.build());
    }

    private static Harness harness(Path base) {
        return harness(base, builder -> {
        });
    }

    private static TaskCall acknowledged(Harness harness) {
        var offered = TaskFixtures.task(harness.protocol().wait(TaskFixtures.waitFor(TaskFixtures.GENERATION),
                TaskFixtures.AT));
        harness.protocol().acknowledge(TaskFixtures.call(offered), TaskFixtures.AT.plusSeconds(1));
        return TaskFixtures.call(offered);
    }

    @Test
    @DisplayName("a wait of a generation whose job ended is answered end")
    void waitJobEnded(@TempDir Path base) {
        var harness = harness(base, builder -> builder.jobState(TaskFixtures.GENERATION::equals));

        var answer = harness.protocol().wait(TaskFixtures.waitFor(TaskFixtures.GENERATION), TaskFixtures.AT);

        assertEquals(new WaitAnswer.End("job_ended"), answer);
    }

    @Test
    @DisplayName("a held task of a paused plan is re-offered to no worker")
    void heldTaskOfPausedPlan(@TempDir Path base) {
        var paused = new TaskFixtures.Paused();
        var harness = harness(base, builder -> builder.pause(paused));
        var call = acknowledged(harness);

        paused.pause(TaskFixtures.PLAN);
        var answer = harness.protocol().wait(TaskFixtures.waitFor(TaskFixtures.GENERATION),
                TaskFixtures.AT.plusSeconds(2));

        assertAll(
                () -> assertEquals(new WaitAnswer.WaitAgain("paused"), answer),
                () -> assertEquals(1, harness.queue().read(call.queueTaskId()).orElseThrow().attempts().size()));
    }

    @Test
    @DisplayName("an off-track signal with a non-finite measurement is refused")
    void nonFiniteSignal() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new OffTrackSignal(QueueTaskRecord.SignalName.REPEATING, SignalAction.SHOW,
                                Double.NaN, 1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new OffTrackSignal(QueueTaskRecord.SignalName.REPEATING, SignalAction.SHOW, 1,
                                Double.POSITIVE_INFINITY)));
    }

    @Test
    @DisplayName("a negative re-offer budget is refused")
    void negativeBudget(@TempDir Path base) {
        var harness = harness(base);
        var call = acknowledged(harness);

        assertThrows(IllegalArgumentException.class,
                () -> harness.protocol().offTrack(call,
                        new OffTrackSignal(QueueTaskRecord.SignalName.REPEATING, SignalAction.SHOW, 1, 1), -1,
                        TaskFixtures.AT.plusSeconds(2)));
    }

    @Test
    @DisplayName("a call after the worker's job ended is refused job_ended, before the lease")
    void jobEnded(@TempDir Path base) {
        var harness = harness(base, builder -> builder.jobState(TaskFixtures.GENERATION::equals));
        var offered = TaskFixtures.task(harness.protocol().wait(TaskFixtures.waitFor(TaskFixtures.SUCCESSOR),
                TaskFixtures.AT));

        var refused = assertInstanceOf(CallResult.Refused.class,
                harness.protocol().acknowledge(TaskFixtures.call(offered, TaskFixtures.GENERATION),
                        TaskFixtures.AT.plusSeconds(1)));

        assertEquals(TaskRefusal.JOB_ENDED, refused.refusal());
    }

    @Test
    @DisplayName("a submit while a required skill is not delivered is refused skills_not_loaded")
    void skillsNotLoaded(@TempDir Path base) {
        var harness = harness(base);
        var call = acknowledged(harness);
        var first = new RoleSkill("skill://pm/documents/doc-review/SKILL.md", "a".repeat(64));
        var second = new RoleSkill("skill://pm/documents/ref-asciidoc/SKILL.md", "b".repeat(64));
        harness.protocol().deliverSkills(TaskFixtures.GENERATION, List.of(first));

        var refused = assertInstanceOf(CallResult.Refused.class,
                harness.protocol().submit(call, List.of(first, second), TaskFixtures.AT.plusSeconds(2)));

        assertAll(
                () -> assertEquals(TaskRefusal.SKILLS_NOT_LOADED, refused.refusal()),
                () -> assertEquals(Status.EXECUTING, refused.task().status()),
                () -> assertEquals(List.of(second.uri()), refused.missingSkills()));
    }

    @Test
    @DisplayName("after the missing skill is delivered the submit closes the task")
    void submitAfterDelivery(@TempDir Path base) {
        var harness = harness(base);
        var call = acknowledged(harness);
        var skill = new RoleSkill("skill://pm/documents/doc-review/SKILL.md", "a".repeat(64));
        harness.protocol().deliverSkills(TaskFixtures.GENERATION, List.of(skill));

        var accepted = assertInstanceOf(CallResult.Accepted.class,
                harness.protocol().submit(call, List.of(skill), TaskFixtures.AT.plusSeconds(2)));

        assertEquals(Outcome.SUBMITTED, accepted.task().outcome());
    }

    @Test
    @DisplayName("the infeasible link closes the task with the outcome infeasible")
    void infeasible(@TempDir Path base) {
        var harness = harness(base);
        var call = acknowledged(harness);

        var accepted = assertInstanceOf(CallResult.Accepted.class,
                harness.protocol().infeasible(call, TaskFixtures.AT.plusSeconds(2)));

        assertAll(
                () -> assertEquals(Status.CLOSED, accepted.task().status()),
                () -> assertEquals(Outcome.INFEASIBLE, accepted.task().outcome()),
                () -> assertEquals(AttemptEnd.CLOSED, accepted.task().currentAttempt().orElseThrow().end()));
    }

    @Test
    @DisplayName("a pause and an off-track end are no report")
    void reportRefusesEndsOfOtherTransitions(@TempDir Path base) {
        var harness = harness(base);
        var call = acknowledged(harness);

        var paused = assertInstanceOf(CallResult.Refused.class,
                harness.protocol().report(call, AttemptEnd.PAUSED, 3, TaskFixtures.AT.plusSeconds(2)));
        var offTrack = assertInstanceOf(CallResult.Refused.class,
                harness.protocol().report(call, AttemptEnd.OFF_TRACK, 3, TaskFixtures.AT.plusSeconds(3)));

        assertAll(
                () -> assertEquals(TaskRefusal.LINK_NOT_AVAILABLE, paused.refusal()),
                () -> assertEquals(TaskRefusal.LINK_NOT_AVAILABLE, offTrack.refusal()),
                () -> assertEquals(Status.EXECUTING, harness.queue().read("qt-0000000000000001").orElseThrow()
                        .status()));
    }

    @Test
    @DisplayName("a closed task stays closed exactly once through the infeasible link")
    void infeasibleExactlyOnce(@TempDir Path base) {
        var harness = harness(base);
        var call = acknowledged(harness);
        harness.protocol().infeasible(call, TaskFixtures.AT.plusSeconds(2));

        var second = assertInstanceOf(CallResult.Refused.class,
                harness.protocol().infeasible(call, TaskFixtures.AT.plusSeconds(3)));

        assertAll(
                () -> assertEquals(TaskRefusal.STALE_ENTITY, second.refusal()),
                () -> assertEquals(Outcome.INFEASIBLE,
                        harness.queue().read("qt-0000000000000001").orElseThrow().outcome()),
                () -> assertEquals(1, harness.queue().records().stream()
                        .filter(QueueTaskRecord::isClosed).count()));
    }
}
