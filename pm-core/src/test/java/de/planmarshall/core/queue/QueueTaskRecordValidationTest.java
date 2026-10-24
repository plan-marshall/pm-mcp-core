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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.queue.QueueTaskRecord.AttemptEnd;
import de.planmarshall.core.queue.QueueTaskRecord.Consultation;
import de.planmarshall.core.queue.QueueTaskRecord.ConsultationStatus;
import de.planmarshall.core.queue.QueueTaskRecord.ContinuationForm;
import de.planmarshall.core.queue.QueueTaskRecord.Kind;
import de.planmarshall.core.queue.QueueTaskRecord.LostReason;
import de.planmarshall.core.queue.QueueTaskRecord.Outcome;
import de.planmarshall.core.queue.QueueTaskRecord.Scope;
import de.planmarshall.core.queue.QueueTaskRecord.ScopeType;
import de.planmarshall.core.queue.QueueTaskRecord.Status;
import de.planmarshall.core.queue.QueueTaskRecord.TaskClass;

/**
 * {@link QueueTaskRecord#validate()} refuses every combination the schema rules out, and each nested record refuses its
 * own broken form. The record is built member by member, so these are the checks of the write and read boundary.
 */
@SuppressWarnings("java:S5778")
// the assertion table names one throwing builder call per case, by design
@DisplayName("Queue task record validation")
class QueueTaskRecordValidationTest {

    private static QueueTaskRecord.QueueTaskRecordBuilder base() {
        return QueueFixtures.queued("qt-0000000000000001");
    }

    @Test
    @DisplayName("a closed task without its outcome or closing instant is refused")
    void closedWithoutOutcome() {
        assertThrows(IllegalArgumentException.class,
                () -> base().status(Status.CLOSED).build().validate());
        assertThrows(IllegalArgumentException.class, () -> base().outcome(Outcome.SUBMITTED).build().validate());
        assertThrows(IllegalArgumentException.class,
                () -> base().closedAt(QueueFixtures.AT.plusSeconds(1)).build().validate());
    }

    @Test
    @DisplayName("a closed task holding a lease is refused")
    void closedWithLease() {
        assertThrows(IllegalArgumentException.class, () -> QueueFixtures.closedSubmitted("qt-0000000000000001")
                .withLease(QueueFixtures.lease(QueueFixtures.GENERATION)).validate());
    }

    @Test
    @DisplayName("an offered task without a lease is refused")
    void offeredWithoutLease() {
        assertThrows(IllegalArgumentException.class,
                () -> base().status(Status.OFFERED).build().validate());
    }

    @Test
    @DisplayName("lost is the only outcome with a reason")
    void reasons() {
        assertThrows(IllegalArgumentException.class, () -> QueueFixtures.lostAttemptsSpent("qt-0000000000000002")
                .withReason(null).validate());
        assertThrows(IllegalArgumentException.class, () -> QueueFixtures.closedSubmitted("qt-0000000000000003")
                .withReason(LostReason.WITHDRAWN).validate());
    }

    @Test
    @DisplayName("the class is absent exactly for a task lost role_undefined")
    void taskClass() {
        assertThrows(IllegalArgumentException.class, () -> base().status(Status.CLOSED)
                .outcome(Outcome.LOST).reason(LostReason.ATTEMPTS_SPENT).closedAt(QueueFixtures.AT.plusSeconds(1))
                .taskClass(null).build().validate());
        assertThrows(IllegalArgumentException.class, () -> base().status(Status.CLOSED)
                .outcome(Outcome.LOST).reason(LostReason.ROLE_UNDEFINED).closedAt(QueueFixtures.AT.plusSeconds(1))
                .build().validate());
        assertDoesNotThrow(() -> QueueFixtures.lostRoleUndefined("qt-0000000000000004").validate());
    }

    @Test
    @DisplayName("a consultation task names its asking task and its own entity")
    void consultation() {
        assertThrows(IllegalArgumentException.class,
                () -> base().kind(Kind.CONSULTATION).build().validate());
        assertThrows(IllegalArgumentException.class, () -> base().consultationOf("qt-0000000000000002").build()
                .validate());
        assertThrows(IllegalArgumentException.class, () -> QueueFixtures
                .consultation("qt-0000000000000005", "qt-0000000000000001").withEntity("envelope-1")
                .validate());
    }

    @Test
    @DisplayName("the question id is set exactly for the outcome asked")
    void question() {
        assertThrows(IllegalArgumentException.class, () -> base().status(Status.CLOSED)
                .outcome(Outcome.ASKED).closedAt(QueueFixtures.AT.plusSeconds(1)).build().validate());
        assertThrows(IllegalArgumentException.class, () -> QueueFixtures.closedSubmitted("qt-0000000000000006")
                .withQuestionId("Q-001").validate());
    }

    @Test
    @DisplayName("the attempts are numbered from 1 without a gap")
    void attemptNumbers() {
        var task = QueueFixtures.queued("qt-0000000000000007")
                .attempts(List.of(QueueFixtures.attempt(2, QueueFixtures.GENERATION, QueueFixtures.AT))).build();

        assertThrows(IllegalArgumentException.class, task::validate);
    }

    @Test
    @DisplayName("a task closes after it was created")
    void closedBeforeCreated() {
        assertThrows(IllegalArgumentException.class, () -> QueueFixtures.closedSubmitted("qt-0000000000000008")
                .withClosedAt(QueueFixtures.AT.minusSeconds(1)).validate());
    }

    @Test
    @DisplayName("a scope of a named type carries an id, and an unnamed scope none")
    void scope() {
        assertThrows(IllegalArgumentException.class, () -> new Scope(ScopeType.PLAN, null));
        assertThrows(IllegalArgumentException.class, () -> new Scope(ScopeType.WORKSPACE, "plan-a"));
        assertThrows(IllegalArgumentException.class, () -> new Scope(ScopeType.PLAN, "Plan A"));
    }

    @Test
    @DisplayName("a lease is acknowledged after it is taken")
    void lease() {
        assertThrows(IllegalArgumentException.class, () -> new QueueTaskRecord.Lease("not-a-generation",
                QueueFixtures.GENERATION, QueueFixtures.AT, null));
        assertThrows(IllegalArgumentException.class, () -> new QueueTaskRecord.Lease(QueueFixtures.GENERATION,
                QueueFixtures.GENERATION, QueueFixtures.AT, QueueFixtures.AT.minusSeconds(1)));
    }

    @Test
    @DisplayName("an attempt refuses a broken number, digest, ordering or counts")
    void attempt() {
        assertThrows(IllegalArgumentException.class, () -> new QueueTaskRecord.Attempt(0,
                QueueFixtures.GENERATION, QueueFixtures.GENERATION, QueueFixtures.DIGEST, QueueFixtures.AT,
                null, null, null, false));
        assertThrows(IllegalArgumentException.class, () -> new QueueTaskRecord.Attempt(1,
                QueueFixtures.GENERATION, QueueFixtures.GENERATION, "bad", QueueFixtures.AT, null, null, null, false));
        assertThrows(IllegalArgumentException.class, () -> new QueueTaskRecord.Attempt(1,
                QueueFixtures.GENERATION, QueueFixtures.GENERATION, QueueFixtures.DIGEST, QueueFixtures.AT,
                QueueFixtures.AT.minusSeconds(1), null, null, false));
        assertThrows(IllegalArgumentException.class, () -> new QueueTaskRecord.Attempt(1,
                QueueFixtures.GENERATION, QueueFixtures.GENERATION, QueueFixtures.DIGEST, QueueFixtures.AT,
                null, null, AttemptEnd.WORKER_LOST, false));
        assertThrows(IllegalArgumentException.class, () -> new QueueTaskRecord.Attempt(1,
                QueueFixtures.GENERATION, QueueFixtures.GENERATION, QueueFixtures.DIGEST, QueueFixtures.AT,
                null, QueueFixtures.AT, null, false));
    }

    @Test
    @DisplayName("a signal refuses a broken attempt and a non-finite value")
    void signal() {
        assertThrows(IllegalArgumentException.class, () -> new QueueTaskRecord.Signal(
                QueueTaskRecord.SignalName.REPEATING, 0, QueueTaskRecord.SignalAction.SHOW, 1, 1, QueueFixtures.AT));
        assertThrows(IllegalArgumentException.class, () -> new QueueTaskRecord.Signal(
                QueueTaskRecord.SignalName.REPEATING, 1, QueueTaskRecord.SignalAction.SHOW, -1, 1, QueueFixtures.AT));
        assertThrows(IllegalArgumentException.class, () -> new QueueTaskRecord.Signal(
                QueueTaskRecord.SignalName.REPEATING, 1, QueueTaskRecord.SignalAction.SHOW, Double.NaN, 1,
                QueueFixtures.AT));
    }

    @Test
    @DisplayName("a continuation refuses a broken question id, digest and head")
    void continuation() {
        assertThrows(IllegalArgumentException.class, () -> new QueueTaskRecord.Continuation("Q-",
                ContinuationForm.RESUME, null, null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new QueueTaskRecord.Continuation("Q-001",
                ContinuationForm.RESUME, null, null, null, null, "bad", null, null));
        assertThrows(IllegalArgumentException.class, () -> new QueueTaskRecord.Continuation("Q-001",
                ContinuationForm.RESUME, null, null, null, null, null, "bad", null));
        assertThrows(IllegalArgumentException.class, () -> new QueueTaskRecord.Continuation("Q-001",
                ContinuationForm.RESUME, null, "s".repeat(257), null, null, null, null, null));
    }

    @Test
    @DisplayName("a consultation refuses its broken forms")
    void consultationRecord() {
        assertThrows(IllegalArgumentException.class, () -> new Consultation("bad", "reviewer",
                QueueFixtures.GENERATION, QueueFixtures.AT, ConsultationStatus.OPEN, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Consultation("qt-0000000000000001", "Reviewer",
                QueueFixtures.GENERATION, QueueFixtures.AT, ConsultationStatus.OPEN, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Consultation("qt-0000000000000001", "reviewer",
                QueueFixtures.GENERATION, QueueFixtures.AT, ConsultationStatus.OPEN, "an answer", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Consultation("qt-0000000000000001", "reviewer",
                QueueFixtures.GENERATION, QueueFixtures.AT, ConsultationStatus.OPEN, null,
                QueueFixtures.AT.plusSeconds(1), null, null));
        assertThrows(IllegalArgumentException.class, () -> new Consultation("qt-0000000000000001", "reviewer",
                QueueFixtures.GENERATION, QueueFixtures.AT, ConsultationStatus.ANSWERED, "answer",
                QueueFixtures.AT.plusSeconds(1), QueueFixtures.AT.plusSeconds(2), null));
        assertThrows(IllegalArgumentException.class, () -> new Consultation("qt-0000000000000001", "reviewer",
                QueueFixtures.GENERATION, QueueFixtures.AT, ConsultationStatus.ANSWERED, "a".repeat(4001),
                QueueFixtures.AT.plusSeconds(1), null, null));
    }

    @Test
    @DisplayName("a task class and entity of a broken form are refused at construction")
    void recordForms() {
        assertThrows(IllegalArgumentException.class, () -> base().queueTaskId("bad").build());
        assertThrows(IllegalArgumentException.class, () -> base().role("Writer").build());
        assertThrows(IllegalArgumentException.class, () -> base().entity("").build());
        assertThrows(IllegalArgumentException.class, () -> base().questionId("Q-1").build());
    }

    @Test
    @DisplayName("the valid fixtures pass the validation")
    void valid() {
        assertDoesNotThrow(() -> QueueFixtures.queued("qt-0000000000000009").build().validate());
        assertDoesNotThrow(() -> QueueFixtures.suspended("qt-0000000000000010").validate());
        assertDoesNotThrow(() -> QueueFixtures.closedSubmitted("qt-0000000000000011").validate());
        assertDoesNotThrow(() -> QueueFixtures.consultation("qt-0000000000000012", "qt-0000000000000001").validate());
        assertDoesNotThrow(() -> QueueFixtures.queued("qt-0000000000000013")
                .status(Status.SUSPENDED).continuation(QueueFixtures.continuation()).build().validate());
        assertDoesNotThrow(() -> QueueFixtures.queued("qt-0000000000000014")
                .taskClass(TaskClass.READ_ONLY).build().validate());
    }
}
