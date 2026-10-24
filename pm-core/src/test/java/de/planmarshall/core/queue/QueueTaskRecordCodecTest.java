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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.queue.QueueTaskRecord.AttemptEnd;
import de.planmarshall.core.queue.QueueTaskRecord.Signal;
import de.planmarshall.core.queue.QueueTaskRecord.SignalAction;
import de.planmarshall.core.queue.QueueTaskRecord.SignalName;

/**
 * The queue task record round-trips (Store Schemas § Queue Task Record): a record with a continuation, a
 * {@code suspended} attempt end and {@code signals[]} entries survives a write and a read, and an
 * {@code agent_started} signal with any action but {@code restart} is refused.
 */
@SuppressWarnings("java:S5778")
// the assertion table names one throwing builder call per case, by design
@DisplayName("Queue task record codec")
class QueueTaskRecordCodecTest {

    private static QueueTaskRecord roundTrip(QueueTaskRecord task) {
        return QueueTaskRecordCodec.read(Path.of("queue", task.queueTaskId() + ".json"),
                QueueTaskRecordCodec.write(task));
    }

    @Nested
    @DisplayName("round-trips")
    class RoundTrips {

        @Test
        @DisplayName("a queued task")
        void queued() {
            var task = QueueFixtures.queued("qt-0000000000000001").build();

            assertEquals(task, roundTrip(task));
        }

        @Test
        @DisplayName("a record with a continuation, a suspended attempt end and signals[]")
        void suspendedWithContinuationAndSignals() {
            var task = QueueFixtures.suspended("qt-0000000000000002").toBuilder()
                    .signals(List.of(new Signal(SignalName.REPEATING, 1, SignalAction.NUDGE, 4, 3,
                            QueueFixtures.AT.plusSeconds(7))))
                    .build();

            var read = roundTrip(task);

            assertAll(
                    () -> assertEquals(task, read),
                    () -> assertEquals(AttemptEnd.SUSPENDED, read.currentAttempt().orElseThrow().end()),
                    () -> assertEquals("Q-001", read.continuation().questionId()),
                    () -> assertEquals(1, read.signals().size()));
        }

        @Test
        @DisplayName("an agent_started signal with the action restart")
        void agentStarted() {
            var task = QueueFixtures.queued("qt-0000000000000003")
                    .status(QueueTaskRecord.Status.QUEUED)
                    .attempts(List.of(QueueFixtures.endedAttempt(1, QueueFixtures.GENERATION,
                            AttemptEnd.OFF_TRACK)))
                    .signals(List.of(QueueFixtures.agentStarted(1, QueueFixtures.AT.plusSeconds(9))))
                    .build();

            var read = roundTrip(task);

            assertAll(
                    () -> assertEquals(task, read),
                    () -> assertEquals(SignalName.AGENT_STARTED, read.signals().getFirst().signal()),
                    () -> assertEquals(SignalAction.RESTART, read.signals().getFirst().action()),
                    () -> assertEquals(1, read.signals().getFirst().attempt()),
                    () -> assertEquals(QueueFixtures.AT.plusSeconds(9), read.signals().getFirst().at()));
        }

        @Test
        @DisplayName("a closed submitted task")
        void submitted() {
            var task = QueueFixtures.closedSubmitted("qt-0000000000000004");

            assertEquals(task, roundTrip(task));
        }

        @Test
        @DisplayName("a lost task with its reason")
        void lost() {
            var task = QueueFixtures.lostAttemptsSpent("qt-0000000000000005");

            assertEquals(task, roundTrip(task));
        }

        @Test
        @DisplayName("a consultation task with its consultations")
        void consultation() {
            var asking = QueueFixtures.queued("qt-0000000000000006")
                    .consultations(List.of(QueueFixtures.answeredConsultation("qt-0000000000000007")))
                    .build();
            var consultation = QueueFixtures.consultation("qt-0000000000000007", "qt-0000000000000006");

            assertAll(
                    () -> assertEquals(asking, roundTrip(asking)),
                    () -> assertEquals(consultation, roundTrip(consultation)));
        }
    }

    @Nested
    @DisplayName("refuses")
    class Refusals {

        @Test
        @DisplayName("an agent_started signal with any action but restart")
        void agentStartedWithAnotherAction() {
            var refusal = assertThrows(IllegalArgumentException.class,
                    () -> new Signal(SignalName.AGENT_STARTED, 1, SignalAction.NUDGE, 1, 1, Instant.EPOCH));

            assertEquals("An 'agent_started' signal always takes the action 'restart', not 'nudge'",
                    refusal.getMessage());
        }

        @Test
        @DisplayName("an attempt whose counts disagrees with its end")
        void inconsistentCounts() {
            assertThrows(IllegalArgumentException.class, () -> new QueueTaskRecord.Attempt(1,
                    QueueFixtures.GENERATION, QueueFixtures.GENERATION, QueueFixtures.DIGEST, QueueFixtures.AT,
                    null, QueueFixtures.AT, AttemptEnd.RESTART, true));
        }

        @Test
        @DisplayName("a withdrawal and an expired vote spend no re-offer")
        void closingEndsSpendNothing() {
            assertAll(
                    () -> assertFalse(AttemptEnd.WITHDRAWN.counts()),
                    () -> assertFalse(AttemptEnd.DEADLINE_EXPIRED.counts()),
                    () -> assertTrue(AttemptEnd.WORKER_LOST.counts()));
        }

        @Test
        @DisplayName("a non-finite measurement is refused")
        void nonFiniteMeasurement() {
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new Signal(SignalName.REPEATING, 1, SignalAction.SHOW, Double.NaN, 1,
                                    QueueFixtures.AT)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new Signal(SignalName.REPEATING, 1, SignalAction.SHOW, 1,
                                    Double.POSITIVE_INFINITY, QueueFixtures.AT)));
        }

        @Test
        @DisplayName("a generation in upper case is refused")
        void upperCaseGeneration() {
            assertThrows(IllegalArgumentException.class,
                    () -> new FencedGenerations.Fence("l-0123456789ABCDEF", QueueFixtures.AT));
        }
    }
}
