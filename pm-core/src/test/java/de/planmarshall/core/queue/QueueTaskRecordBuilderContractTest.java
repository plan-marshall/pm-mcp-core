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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builders of {@link QueueTaskRecord} and of its nested records build the values the canonical constructors build,
 * and a missing required component is refused with the same refusal.
 */
@DisplayName("Queue task record builders")
class QueueTaskRecordBuilderContractTest {

    private static final String ID = "qt-0123456789abcdef";

    @Test
    @DisplayName("the record builder builds the constructor's value and refuses alike")
    void shouldBuildRecordLikeTheConstructorAndRefuseAlike() {
        var scope = new QueueTaskRecord.Scope(QueueTaskRecord.ScopeType.PLAN, QueueFixtures.PLAN);

        var canonical = new QueueTaskRecord(ID, QueueTaskRecord.Kind.WORK, "writer",
                QueueTaskRecord.TaskClass.WRITER, scope, "envelope-1", "state-1", false,
                QueueTaskRecord.Status.QUEUED, null, null, List.of(), List.of(), List.of(), null, null, null, null,
                QueueFixtures.AT, null);
        var built = QueueTaskRecord.builder().queueTaskId(canonical.queueTaskId()).kind(canonical.kind())
                .role(canonical.role()).taskClass(canonical.taskClass()).scope(canonical.scope())
                .entity(canonical.entity()).stateId(canonical.stateId()).formDowngraded(canonical.formDowngraded())
                .status(canonical.status()).continuation(canonical.continuation()).lease(canonical.lease())
                .attempts(canonical.attempts()).signals(canonical.signals())
                .consultations(canonical.consultations()).consultationOf(canonical.consultationOf())
                .outcome(canonical.outcome()).reason(canonical.reason()).questionId(canonical.questionId())
                .createdAt(canonical.createdAt()).closedAt(canonical.closedAt()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new QueueTaskRecord(ID, null, "writer", QueueTaskRecord.TaskClass.WRITER, scope,
                                    "envelope-1", "state-1", false, QueueTaskRecord.Status.QUEUED, null, null,
                                    List.of(), List.of(), List.of(), null, null, null, null, QueueFixtures.AT, null),
                            "the constructor refuses a null kind");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> QueueTaskRecord.builder().queueTaskId(ID).role("writer")
                                    .taskClass(QueueTaskRecord.TaskClass.WRITER).scope(scope).entity("envelope-1")
                                    .stateId("state-1").formDowngraded(false)
                                    .status(QueueTaskRecord.Status.QUEUED).attempts(List.of()).signals(List.of())
                                    .consultations(List.of()).createdAt(QueueFixtures.AT).build(),
                            "the builder refuses a missing kind");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent kind with the same message");
                });
    }

    @Test
    @DisplayName("the continuation builder builds the constructor's value and refuses alike")
    void shouldBuildContinuationLikeTheConstructorAndRefuseAlike() {
        var canonical = new QueueTaskRecord.Continuation("Q-001", QueueTaskRecord.ContinuationForm.RESUME, null,
                "session-1", QueueFixtures.AT, QueueFixtures.AT.plusSeconds(3_600), QueueFixtures.DIGEST,
                "0".repeat(40), null);
        var built = QueueTaskRecord.Continuation.builder().questionId(canonical.questionId())
                .form(canonical.form()).holdUntil(canonical.holdUntil()).sessionId(canonical.sessionId())
                .suspendedAt(canonical.suspendedAt()).resumeUntil(canonical.resumeUntil())
                .suspendedDigest(canonical.suspendedDigest()).suspendedHead(canonical.suspendedHead())
                .fallbackReason(canonical.fallbackReason()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new QueueTaskRecord.Continuation(null, QueueTaskRecord.ContinuationForm.RESUME,
                                    null, "session-1", QueueFixtures.AT, QueueFixtures.AT.plusSeconds(3_600),
                                    QueueFixtures.DIGEST, "0".repeat(40), null),
                            "the constructor refuses a null questionId");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> QueueTaskRecord.Continuation.builder()
                                    .form(QueueTaskRecord.ContinuationForm.RESUME).build(),
                            "the builder refuses a missing questionId");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent questionId with the same message");
                });
    }

    @Test
    @DisplayName("the lease builder builds the constructor's value and refuses alike")
    void shouldBuildLeaseLikeTheConstructorAndRefuseAlike() {
        var canonical = new QueueTaskRecord.Lease(QueueFixtures.GENERATION, QueueFixtures.GENERATION,
                QueueFixtures.AT, null);
        var built = QueueTaskRecord.Lease.builder().generationId(canonical.generationId())
                .jobId(canonical.jobId()).leasedAt(canonical.leasedAt())
                .acknowledgedAt(canonical.acknowledgedAt()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new QueueTaskRecord.Lease(QueueFixtures.GENERATION, QueueFixtures.GENERATION,
                                    null, null),
                            "the constructor refuses a null leasedAt");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> QueueTaskRecord.Lease.builder().generationId(QueueFixtures.GENERATION)
                                    .jobId(QueueFixtures.GENERATION).build(),
                            "the builder refuses a missing leasedAt");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent leasedAt with the same message");
                });
    }

    @Test
    @DisplayName("the attempt builder builds the constructor's value and refuses alike")
    void shouldBuildAttemptLikeTheConstructorAndRefuseAlike() {
        var canonical = new QueueTaskRecord.Attempt(1, QueueFixtures.GENERATION, QueueFixtures.GENERATION,
                QueueFixtures.DIGEST, QueueFixtures.AT, QueueFixtures.AT.plusSeconds(1),
                QueueFixtures.AT.plusSeconds(2), QueueTaskRecord.AttemptEnd.CLOSED, false);
        var built = QueueTaskRecord.Attempt.builder().attempt(canonical.attempt())
                .generationId(canonical.generationId()).jobId(canonical.jobId())
                .configurationDigest(canonical.configurationDigest()).offeredAt(canonical.offeredAt())
                .acknowledgedAt(canonical.acknowledgedAt()).endedAt(canonical.endedAt()).end(canonical.end())
                .counts(canonical.counts()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new QueueTaskRecord.Attempt(1, QueueFixtures.GENERATION,
                                    QueueFixtures.GENERATION, QueueFixtures.DIGEST, null, null, null, null, false),
                            "the constructor refuses a null offeredAt");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> QueueTaskRecord.Attempt.builder().attempt(1)
                                    .generationId(QueueFixtures.GENERATION).jobId(QueueFixtures.GENERATION)
                                    .configurationDigest(QueueFixtures.DIGEST).counts(false).build(),
                            "the builder refuses a missing offeredAt");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent offeredAt with the same message");
                });
    }

    @Test
    @DisplayName("the signal builder builds the constructor's value and refuses alike")
    void shouldBuildSignalLikeTheConstructorAndRefuseAlike() {
        var canonical = new QueueTaskRecord.Signal(QueueTaskRecord.SignalName.REPEATING, 1,
                QueueTaskRecord.SignalAction.NUDGE, 1.0, 2.0, QueueFixtures.AT);
        var built = QueueTaskRecord.Signal.builder().signal(canonical.signal()).attempt(canonical.attempt())
                .action(canonical.action()).measurement(canonical.measurement())
                .threshold(canonical.threshold()).at(canonical.at()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new QueueTaskRecord.Signal(null, 1, QueueTaskRecord.SignalAction.NUDGE, 1.0,
                                    2.0, QueueFixtures.AT),
                            "the constructor refuses a null signal");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> QueueTaskRecord.Signal.builder().attempt(1)
                                    .action(QueueTaskRecord.SignalAction.NUDGE).measurement(1.0).threshold(2.0)
                                    .at(QueueFixtures.AT).build(),
                            "the builder refuses a missing signal");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent signal with the same message");
                });
    }

    @Test
    @DisplayName("the consultation builder builds the constructor's value and refuses alike")
    void shouldBuildConsultationLikeTheConstructorAndRefuseAlike() {
        var canonical = new QueueTaskRecord.Consultation(ID, "reviewer", QueueFixtures.GENERATION,
                QueueFixtures.AT, QueueTaskRecord.ConsultationStatus.OPEN, null, null, null, null);
        var built = QueueTaskRecord.Consultation.builder().consultationId(canonical.consultationId())
                .role(canonical.role()).askedBy(canonical.askedBy()).askedAt(canonical.askedAt())
                .status(canonical.status()).answer(canonical.answer()).closedAt(canonical.closedAt())
                .deliveredAt(canonical.deliveredAt()).deliveredTo(canonical.deliveredTo()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new QueueTaskRecord.Consultation(ID, null, QueueFixtures.GENERATION,
                                    QueueFixtures.AT, QueueTaskRecord.ConsultationStatus.OPEN, null, null, null,
                                    null),
                            "the constructor refuses a null role");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> QueueTaskRecord.Consultation.builder().consultationId(ID)
                                    .askedBy(QueueFixtures.GENERATION).askedAt(QueueFixtures.AT)
                                    .status(QueueTaskRecord.ConsultationStatus.OPEN).build(),
                            "the builder refuses a missing role");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent role with the same message");
                });
    }
}
