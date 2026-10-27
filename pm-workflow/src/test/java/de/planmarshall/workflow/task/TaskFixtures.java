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

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

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

    static final String GENERATION = "l-0000000000000001";
    static final String SUCCESSOR = "l-0000000000000002";
    static final String THIRD = "l-0000000000000003";
    static final String DIGEST = "a".repeat(64);
    static final String OTHER_DIGEST = "b".repeat(64);
    static final String ROLE = "writer";
    static final String REVIEWER = "reviewer";
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
                .entity("envelope-1")
                .stateId("build.execute")
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
                "session-1", AT, AT.plusSeconds(3600), DIGEST, "c".repeat(40), null);
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
     * The audit of a test: it records every {@code agent_started} detection.
     */
    static final class RecordingAudit implements SecurityAudit {

        private final List<Event> events = new CopyOnWriteArrayList<>();

        @Override
        public void agentStarted(Event event) {
            events.add(event);
        }

        List<Event> events() {
            return List.copyOf(events);
        }
    }

    /** A pause state a test changes. */
    static final class Paused implements PlanPause {

        private final Set<String> plans = new HashSet<>();

        @Override
        public boolean isPaused(String planId) {
            return plans.contains(planId);
        }

        void pause(String planId) {
            plans.add(planId);
        }

        void start(String planId) {
            plans.remove(planId);
        }
    }

    /** A copy of the attempts, for a clear assertion. */
    static List<QueueTaskRecord.Attempt> attempts(QueueTaskRecord task) {
        return new ArrayList<>(task.attempts());
    }
}
