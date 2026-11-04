/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.queue;

import de.cuioss.test.generator.Generators;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import de.planmarshall.core.queue.QueueTaskRecord.Attempt;
import de.planmarshall.core.queue.QueueTaskRecord.AttemptEnd;
import de.planmarshall.core.queue.QueueTaskRecord.Consultation;
import de.planmarshall.core.queue.QueueTaskRecord.ConsultationStatus;
import de.planmarshall.core.queue.QueueTaskRecord.Continuation;
import de.planmarshall.core.queue.QueueTaskRecord.ContinuationForm;
import de.planmarshall.core.queue.QueueTaskRecord.DeliveredTo;
import de.planmarshall.core.queue.QueueTaskRecord.Kind;
import de.planmarshall.core.queue.QueueTaskRecord.Lease;
import de.planmarshall.core.queue.QueueTaskRecord.LostReason;
import de.planmarshall.core.queue.QueueTaskRecord.Outcome;
import de.planmarshall.core.queue.QueueTaskRecord.Scope;
import de.planmarshall.core.queue.QueueTaskRecord.ScopeType;
import de.planmarshall.core.queue.QueueTaskRecord.Signal;
import de.planmarshall.core.queue.QueueTaskRecord.SignalAction;
import de.planmarshall.core.queue.QueueTaskRecord.SignalName;
import de.planmarshall.core.queue.QueueTaskRecord.Status;
import de.planmarshall.core.queue.QueueTaskRecord.TaskClass;

/**
 * The records the tests of the task queue write and read. They are written for the tests, never taken from the
 * content of the product.
 */
final class QueueFixtures {

    static final String GENERATION = "l-" + "%016x".formatted(Generators.longs().next());
    static final String OTHER_GENERATION = "l-" + "%016x".formatted(Generators.longs().next());
    static final String DIGEST = "%064x".formatted(Generators.longs().next());
    static final String OTHER_DIGEST = "%064x".formatted(Generators.longs().next());
    static final Instant AT =
            Instant.ofEpochSecond(Generators.longs(1_600_000_000L, 1_900_000_000L).next());
    static final String PLAN = "plan-a";

    private QueueFixtures() {
    }

    static Scope planScope() {
        return new Scope(ScopeType.PLAN, PLAN);
    }

    static QueueTaskRecord.QueueTaskRecordBuilder queued(String id) {
        return QueueTaskRecord.builder()
                .queueTaskId(id)
                .kind(Kind.WORK)
                .role("writer")
                .taskClass(TaskClass.WRITER)
                .scope(planScope())
                .entity("envelope-1")
                .stateId(Generators.letterStrings(3, 12).next())
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

    static QueueTaskRecord.QueueTaskRecordBuilder queued(String id, String role) {
        return queued(id).role(role);
    }

    static Attempt attempt(int number, String generation, Instant offeredAt) {
        return new Attempt(number, generation, generation, DIGEST, offeredAt, null, null, null, false);
    }

    static Attempt endedAttempt(int number, String generation, AttemptEnd end) {
        return new Attempt(number, generation, generation, DIGEST, AT, AT.plusSeconds(1), AT.plusSeconds(2), end,
                end.counts());
    }

    static Lease lease(String generation) {
        return new Lease(generation, generation, AT, null);
    }

    static Continuation continuation() {
        return new Continuation("Q-001", ContinuationForm.RESUME, null,
                "s-" + Generators.longs(1, Long.MAX_VALUE).next(), AT.plusSeconds(3),
                AT.plusSeconds(3600), DIGEST, "%040x".formatted(Generators.longs().next()), null);
    }

    static Signal agentStarted(int attempt, Instant at) {
        return new Signal(SignalName.AGENT_STARTED, attempt, SignalAction.RESTART, 1, 1, at);
    }

    static Consultation answeredConsultation(String consultationId) {
        return new Consultation(consultationId, "reviewer", GENERATION, AT, ConsultationStatus.ANSWERED,
                Generators.letterStrings(3, 20).next(), AT.plusSeconds(5), AT.plusSeconds(6), DeliveredTo.WAIT);
    }

    static QueueTaskRecord withAttempts(QueueTaskRecord task, Attempt... attempts) {
        var list = new ArrayList<Attempt>();
        for (var attempt : attempts) {
            list.add(attempt);
        }
        return task.withAttempts(list);
    }

    static QueueTaskRecord closedSubmitted(String id) {
        return queued(id)
                .status(Status.CLOSED)
                .lease(null)
                .attempts(List.of(endedAttempt(1, GENERATION, AttemptEnd.CLOSED)))
                .outcome(Outcome.SUBMITTED)
                .closedAt(AT.plusSeconds(10))
                .build();
    }

    static QueueTaskRecord lostAttemptsSpent(String id) {
        return queued(id)
                .status(Status.CLOSED)
                .attempts(List.of(endedAttempt(1, GENERATION, AttemptEnd.WORKER_LOST)))
                .outcome(Outcome.LOST)
                .reason(LostReason.ATTEMPTS_SPENT)
                .closedAt(AT.plusSeconds(10))
                .build();
    }

    static QueueTaskRecord lostRoleUndefined(String id) {
        return queued(id)
                .taskClass(null)
                .status(Status.CLOSED)
                .outcome(Outcome.LOST)
                .reason(LostReason.ROLE_UNDEFINED)
                .closedAt(AT.plusSeconds(10))
                .build();
    }

    static QueueTaskRecord suspended(String id) {
        return queued(id)
                .status(Status.SUSPENDED)
                .continuation(continuation())
                .attempts(List.of(endedAttempt(1, GENERATION, AttemptEnd.SUSPENDED)))
                .build();
    }

    static QueueTaskRecord consultation(String id, String askingTask) {
        return queued(id)
                .kind(Kind.CONSULTATION)
                .role("reviewer")
                .taskClass(TaskClass.READ_ONLY)
                .entity(id)
                .consultationOf(askingTask)
                .status(Status.CLOSED)
                .outcome(Outcome.ASKED)
                .questionId("Q-002")
                .closedAt(AT.plusSeconds(10))
                .build();
    }
}
