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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
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
@EnableGeneratorController
@SuppressWarnings("java:S5778")
// the assertion table names one throwing builder call per case, by design
@DisplayName("Queue task record validation")
class QueueTaskRecordValidationTest {

    private static String taskId() {
        return "qt-" + "%016x".formatted(Generators.longs().next());
    }

    private static QueueTaskRecord.QueueTaskRecordBuilder base() {
        return QueueFixtures.queued(taskId());
    }

    @Test
    @DisplayName("a closed task without its outcome or closing instant is refused")
    void closedWithoutOutcome() {
        assertThrows(IllegalArgumentException.class,
                () -> base().status(Status.CLOSED).build().validate(),
                "a closed task without an outcome is refused");
        assertThrows(IllegalArgumentException.class, () -> base().outcome(Outcome.SUBMITTED).build().validate(),
                "an outcome on an unclosed task is refused");
        assertThrows(IllegalArgumentException.class,
                () -> base().closedAt(QueueFixtures.AT.plusSeconds(1)).build().validate(),
                "a closing instant without an outcome is refused");
    }

    @Test
    @DisplayName("a closed task holding a lease is refused")
    void closedWithLease() {
        assertThrows(IllegalArgumentException.class, () -> QueueFixtures.closedSubmitted(taskId())
                        .withLease(QueueFixtures.lease(QueueFixtures.GENERATION)).validate(),
                "a closed task holding a lease is refused");
    }

    @Test
    @DisplayName("an offered task without a lease is refused")
    void offeredWithoutLease() {
        assertThrows(IllegalArgumentException.class,
                () -> base().status(Status.OFFERED).build().validate(),
                "an offered task without a lease is refused");
    }

    @Test
    @DisplayName("lost is the only outcome with a reason")
    void reasons() {
        assertThrows(IllegalArgumentException.class, () -> QueueFixtures.lostAttemptsSpent(taskId())
                .withReason(null).validate(), "a lost task without its reason is refused");
        assertThrows(IllegalArgumentException.class, () -> QueueFixtures.closedSubmitted(taskId())
                        .withReason(LostReason.WITHDRAWN).validate(),
                "a submitted task carrying a lost reason is refused");
    }

    @Test
    @DisplayName("the class is absent exactly for a task lost role_undefined")
    void taskClass() {
        assertThrows(IllegalArgumentException.class, () -> base().status(Status.CLOSED)
                        .outcome(Outcome.LOST).reason(LostReason.ATTEMPTS_SPENT).closedAt(QueueFixtures.AT.plusSeconds(1))
                        .taskClass(null).build().validate(),
                "a lost task that is not role_undefined needs a class");
        assertThrows(IllegalArgumentException.class, () -> base().status(Status.CLOSED)
                .outcome(Outcome.LOST).reason(LostReason.ROLE_UNDEFINED).closedAt(QueueFixtures.AT.plusSeconds(1))
                .build().validate(), "role_undefined carries no task class");
        assertDoesNotThrow(() -> QueueFixtures.lostRoleUndefined(taskId()).validate(),
                "a role_undefined task validates without a class");
    }

    @Test
    @DisplayName("a consultation task names its asking task and its own entity")
    void consultation() {
        assertThrows(IllegalArgumentException.class,
                () -> base().kind(Kind.CONSULTATION).build().validate(),
                "a consultation task without its asking task is refused");
        assertThrows(IllegalArgumentException.class, () -> base().consultationOf(taskId()).build()
                .validate(), "an asking task outside a consultation is refused");
        assertThrows(IllegalArgumentException.class, () -> QueueFixtures
                .consultation(taskId(), taskId()).withEntity("envelope-1")
                .validate(), "a consultation task names its own entity");
    }

    @Test
    @DisplayName("the question id is set exactly for the outcome asked")
    void question() {
        assertThrows(IllegalArgumentException.class, () -> base().status(Status.CLOSED)
                        .outcome(Outcome.ASKED).closedAt(QueueFixtures.AT.plusSeconds(1)).build().validate(),
                "an asked outcome without a question id is refused");
        assertThrows(IllegalArgumentException.class, () -> QueueFixtures.closedSubmitted(taskId())
                        .withQuestionId("Q-001").validate(),
                "a submitted task carrying a question id is refused");
    }

    @Test
    @DisplayName("the attempts are numbered from 1 without a gap")
    void attemptNumbers() {
        var task = QueueFixtures.queued(taskId())
                .attempts(List.of(QueueFixtures.attempt(2, QueueFixtures.GENERATION, QueueFixtures.AT))).build();

        assertThrows(IllegalArgumentException.class, task::validate,
                "attempts are numbered from 1 without a gap");
    }

    @Test
    @DisplayName("a task closes after it was created")
    void closedBeforeCreated() {
        assertThrows(IllegalArgumentException.class, () -> QueueFixtures.closedSubmitted(taskId())
                        .withClosedAt(QueueFixtures.AT.minusSeconds(1)).validate(),
                "a task closes only after it was created");
    }

    @Test
    @DisplayName("a scope of a named type carries an id, and an unnamed scope none")
    void scope() {
        assertThrows(IllegalArgumentException.class, () -> new Scope(ScopeType.PLAN, null),
                "a named scope needs an id");
        assertThrows(IllegalArgumentException.class, () -> new Scope(ScopeType.WORKSPACE, "plan-a"),
                "an unnamed workspace scope carries no id");
        assertThrows(IllegalArgumentException.class, () -> new Scope(ScopeType.PLAN, "Plan A"),
                "a plan id that is not a slug is refused");
    }

    @Test
    @DisplayName("a lease is acknowledged after it is taken")
    void lease() {
        assertThrows(IllegalArgumentException.class, () -> new QueueTaskRecord.Lease("not-a-generation",
                        QueueFixtures.GENERATION, QueueFixtures.AT, null),
                "a lease generation that is no id is refused");
        assertThrows(IllegalArgumentException.class, () -> new QueueTaskRecord.Lease(QueueFixtures.GENERATION,
                        QueueFixtures.GENERATION, QueueFixtures.AT, QueueFixtures.AT.minusSeconds(1)),
                "a lease acknowledged before it is taken is refused");
    }

    @Test
    @DisplayName("an attempt refuses a broken number, digest, ordering or counts")
    void attempt() {
        assertThrows(IllegalArgumentException.class, () -> new QueueTaskRecord.Attempt(0,
                QueueFixtures.GENERATION, QueueFixtures.GENERATION, QueueFixtures.DIGEST, QueueFixtures.AT,
                null, null, null, false), "an attempt number of zero is refused");
        assertThrows(IllegalArgumentException.class, () -> new QueueTaskRecord.Attempt(1,
                        QueueFixtures.GENERATION, QueueFixtures.GENERATION, "bad", QueueFixtures.AT, null, null, null, false),
                "an attempt digest that is no digest is refused");
        assertThrows(IllegalArgumentException.class, () -> new QueueTaskRecord.Attempt(1,
                        QueueFixtures.GENERATION, QueueFixtures.GENERATION, QueueFixtures.DIGEST, QueueFixtures.AT,
                        QueueFixtures.AT.minusSeconds(1), null, null, false),
                "a started attempt before its take is refused");
        assertThrows(IllegalArgumentException.class, () -> new QueueTaskRecord.Attempt(1,
                        QueueFixtures.GENERATION, QueueFixtures.GENERATION, QueueFixtures.DIGEST, QueueFixtures.AT,
                        null, null, AttemptEnd.WORKER_LOST, false),
                "an attempt end without counts is refused");
        assertThrows(IllegalArgumentException.class, () -> new QueueTaskRecord.Attempt(1,
                QueueFixtures.GENERATION, QueueFixtures.GENERATION, QueueFixtures.DIGEST, QueueFixtures.AT,
                null, QueueFixtures.AT, null, false), "an attempt counted without an end is refused");
    }

    @Test
    @DisplayName("a signal refuses a broken attempt and a non-finite value")
    void signal() {
        assertThrows(IllegalArgumentException.class, () -> new QueueTaskRecord.Signal(
                        QueueTaskRecord.SignalName.REPEATING, 0, QueueTaskRecord.SignalAction.SHOW, 1, 1, QueueFixtures.AT),
                "a signal attempt of zero is refused");
        assertThrows(IllegalArgumentException.class, () -> new QueueTaskRecord.Signal(
                        QueueTaskRecord.SignalName.REPEATING, 1, QueueTaskRecord.SignalAction.SHOW, -1, 1, QueueFixtures.AT),
                "a signal measurement below zero is refused");
        assertThrows(IllegalArgumentException.class, () -> new QueueTaskRecord.Signal(
                QueueTaskRecord.SignalName.REPEATING, 1, QueueTaskRecord.SignalAction.SHOW, Double.NaN, 1,
                QueueFixtures.AT), "a NaN signal measurement is refused");
    }

    @Test
    @DisplayName("a continuation refuses a broken question id, digest and head")
    void continuation() {
        assertThrows(IllegalArgumentException.class, () -> new QueueTaskRecord.Continuation("Q-",
                        ContinuationForm.RESUME, null, null, null, null, null, null, null),
                "a continuation question id of Q- is refused");
        assertThrows(IllegalArgumentException.class, () -> new QueueTaskRecord.Continuation("Q-001",
                        ContinuationForm.RESUME, null, null, null, null, "bad", null, null),
                "a continuation digest that is no digest is refused");
        assertThrows(IllegalArgumentException.class, () -> new QueueTaskRecord.Continuation("Q-001",
                        ContinuationForm.RESUME, null, null, null, null, null, "bad", null),
                "a continuation head that is no generation is refused");
        assertThrows(IllegalArgumentException.class, () -> new QueueTaskRecord.Continuation("Q-001",
                        ContinuationForm.RESUME, null, "s".repeat(257), null, null, null, null, null),
                "a continuation prompt beyond its bound is refused");
    }

    @Test
    @DisplayName("a consultation refuses its broken forms")
    void consultationRecord() {
        var id = taskId();
        var answer = Generators.letterStrings(3, 20).next();
        assertThrows(IllegalArgumentException.class, () -> new Consultation("bad", "reviewer",
                        QueueFixtures.GENERATION, QueueFixtures.AT, ConsultationStatus.OPEN, null, null, null, null),
                "a consultation record id of bad is refused");
        assertThrows(IllegalArgumentException.class, () -> new Consultation(id, "Reviewer",
                        QueueFixtures.GENERATION, QueueFixtures.AT, ConsultationStatus.OPEN, null, null, null, null),
                "a consultation role that is not a slug is refused");
        assertThrows(IllegalArgumentException.class, () -> new Consultation(id, "reviewer",
                        QueueFixtures.GENERATION, QueueFixtures.AT, ConsultationStatus.OPEN, answer, null, null, null),
                "an open consultation carrying an answer is refused");
        assertThrows(IllegalArgumentException.class, () -> new Consultation(id, "reviewer",
                        QueueFixtures.GENERATION, QueueFixtures.AT, ConsultationStatus.OPEN, null,
                        QueueFixtures.AT.plusSeconds(1), null, null),
                "an open consultation carrying a closing instant is refused");
        assertThrows(IllegalArgumentException.class, () -> new Consultation(id, "reviewer",
                        QueueFixtures.GENERATION, QueueFixtures.AT, ConsultationStatus.ANSWERED, answer,
                        QueueFixtures.AT.plusSeconds(1), QueueFixtures.AT.plusSeconds(2), null),
                "a delivered answer without a place is refused");
        assertThrows(IllegalArgumentException.class, () -> new Consultation(id, "reviewer",
                        QueueFixtures.GENERATION, QueueFixtures.AT, ConsultationStatus.ANSWERED, "a".repeat(4001),
                        QueueFixtures.AT.plusSeconds(1), null, null),
                "an answer beyond the 4000 character bound is refused");
    }

    @Test
    @DisplayName("a task class and entity of a broken form are refused at construction")
    void recordForms() {
        assertThrows(IllegalArgumentException.class, () -> base().queueTaskId("bad").build(),
                "a queue task id of bad is refused");
        assertThrows(IllegalArgumentException.class, () -> base().role("Writer").build(),
                "a role that is not lower case is refused");
        assertThrows(IllegalArgumentException.class, () -> base().entity("").build(),
                "an empty entity is refused");
        assertThrows(IllegalArgumentException.class, () -> base().questionId("Q-1").build(),
                "a question id of Q-1 is refused");
    }

    @Test
    @DisplayName("the valid fixtures pass the validation")
    void valid() {
        assertDoesNotThrow(() -> QueueFixtures.queued(taskId()).build().validate(),
                "a queued fixture passes validation");
        assertDoesNotThrow(() -> QueueFixtures.suspended(taskId()).validate(),
                "a suspended fixture passes validation");
        assertDoesNotThrow(() -> QueueFixtures.closedSubmitted(taskId()).validate(),
                "a closed submitted fixture passes validation");
        assertDoesNotThrow(() -> QueueFixtures.consultation(taskId(), taskId()).validate(),
                "a consultation fixture passes validation");
        assertDoesNotThrow(() -> QueueFixtures.queued(taskId())
                        .status(Status.SUSPENDED).continuation(QueueFixtures.continuation()).build().validate(),
                "a suspended fixture with a continuation validates");
        assertDoesNotThrow(() -> QueueFixtures.queued(taskId())
                        .taskClass(TaskClass.READ_ONLY).build().validate(),
                "a read-only task class is accepted");
    }
}
