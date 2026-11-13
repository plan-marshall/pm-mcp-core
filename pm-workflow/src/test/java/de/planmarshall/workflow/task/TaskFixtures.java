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

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.getCurrentArguments;
import static org.easymock.EasyMock.replay;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import de.cuioss.test.generator.Generators;
import de.planmarshall.core.queue.QueueTaskRecord;
import de.planmarshall.core.queue.QueueTaskRecord.Kind;
import de.planmarshall.core.queue.QueueTaskRecord.Scope;
import de.planmarshall.core.queue.QueueTaskRecord.ScopeType;
import de.planmarshall.core.queue.QueueTaskRecord.Status;
import de.planmarshall.core.queue.QueueTaskRecord.TaskClass;

/**
 * The records and collaborators the tests of the task protocol use. They are written for the tests, never taken from
 * the content of the product.
 */
final class TaskFixtures {

    // The queue and generation ids are the wire format, the digests, plan and timestamps are the fixture's contract;
    // the role names, the entity, the state and the session id are generated.
    static final String GENERATION = "l-0000000000000001";
    static final String SUCCESSOR = "l-0000000000000002";
    static final String THIRD = "l-0000000000000003";
    static final String DIGEST = "a".repeat(64);
    static final String OTHER_DIGEST = "b".repeat(64);
    static final String ROLE = "writer-" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
    static final String REVIEWER = "reviewer-" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
    static final String SESSION = "session-" + Generators.letterStrings(3, 10).next();
    static final String ENTITY = "envelope-" + Generators.integers(1, 999).next();
    static final String STATE = "state." + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
    static final String PLAN = "plan-a";
    static final Instant AT = Instant.parse("2026-10-23T10:00:00Z");

    private TaskFixtures() {
    }

    static Scope planScope() {
        return new Scope(ScopeType.PLAN, PLAN);
    }

    static QueueTaskRecord.QueueTaskRecordBuilder queued(String id, String role) {
        return QueueTaskRecord.builder()
                .queueTaskId(id)
                .kind(Kind.WORK)
                .role(role)
                .taskClass(TaskClass.WRITER)
                .scope(planScope())
                .entity(ENTITY)
                .stateId(STATE)
                .formDowngraded(false)
                .status(Status.QUEUED)
                .continuation(null)
                .lease(null)
                .attempts(List.of())
                .signals(List.of())
                .consultations(List.of())
                .consultationOf(null)
                .outcome(null)
                .reason(null)
                .questionId(null)
                .createdAt(AT)
                .closedAt(null);
    }

    static QueueTaskRecord consultation(String id, String askingTask) {
        return queued(id, REVIEWER)
                .kind(Kind.CONSULTATION)
                .taskClass(TaskClass.READ_ONLY)
                .entity(id)
                .consultationOf(askingTask)
                .build();
    }

    static WaitRequest waitFor(String generation) {
        return new WaitRequest(generation, ROLE, DIGEST, DIGEST, 3);
    }

    static WaitRequest waitFor(String generation, String role) {
        return new WaitRequest(generation, role, DIGEST, DIGEST, 3);
    }

    static TaskCall call(QueueTaskRecord task, String generation) {
        return new TaskCall(task.queueTaskId(), generation);
    }

    static QueueTaskRecord.Continuation continuation() {
        return new QueueTaskRecord.Continuation("Q-001", QueueTaskRecord.ContinuationForm.RESUME, null,
                SESSION, AT, AT.plusSeconds(3600), DIGEST, "c".repeat(40), null);
    }

    static QueueTaskRecord.Continuation holdContinuation() {
        return new QueueTaskRecord.Continuation("Q-001", QueueTaskRecord.ContinuationForm.HOLD,
                AT.plusSeconds(600), null, null, null, null, null, null);
    }

    static TaskCall call(QueueTaskRecord task) {
        return call(task, GENERATION);
    }

    static List<String> offeredIds(WaitAnswer answer) {
        return answer instanceof WaitAnswer.Offered offered ? List.of(offered.task().queueTaskId()) : List.of();
    }

    static QueueTaskRecord task(WaitAnswer answer) {
        return ((WaitAnswer.Offered) answer).task();
    }

    /**
     * @param paused the plans that are paused; the test adds and removes a plan id, the mock answers from the set
     * @return an EasyMock pause port that answers each read by the plan id it is asked about
     */
    static PlanPause pause(Set<String> paused) {
        PlanPause pause = createMock(PlanPause.class);
        expect(pause.isPaused(anyObject(String.class)))
                .andAnswer(() -> paused.contains((String) getCurrentArguments()[0])).anyTimes();
        replay(pause);
        return pause;
    }

    /** A copy of the attempts, for a clear assertion. */
    static List<QueueTaskRecord.Attempt> attempts(QueueTaskRecord task) {
        return new ArrayList<>(task.attempts());
    }
}
