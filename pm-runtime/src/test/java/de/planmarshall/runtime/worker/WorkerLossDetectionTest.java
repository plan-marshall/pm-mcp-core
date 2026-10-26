/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.worker;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.WorkerParameters;
import de.planmarshall.core.role.RoleClass;

/**
 * The three deadlines of one worker generation (PM-WORK-3, Detection): an acknowledgement and a silence signal fire
 * exactly at their deadline and never a millisecond earlier, an informational acknowledgement and an open wait suppress
 * their signal, an exit before the first call is a start failure for every role but {@code zero_tool}, whose exit alone
 * decides and is never a start failure, and a {@code zero_tool} worker is exempt from the acknowledgement and silence
 * signals.
 */
@DisplayName("Worker loss detection")
class WorkerLossDetectionTest {

    private static final Instant SPAWN = Instant.parse("2026-10-25T10:00:00Z");

    private static final String GENERATION = "l-0123456789abcdef";

    private static final String TASK = "qt-0123456789abcdef";

    private static final int ACK_SECONDS = 10;

    private static final int BOUNDED_WAIT_SECONDS = 30;

    private static final int GRACE_SECONDS = 10;

    private static final int SILENCE_SECONDS = BOUNDED_WAIT_SECONDS + GRACE_SECONDS;

    private final MutableClock clock = new MutableClock(SPAWN);

    private final ManualTimers timers = new ManualTimers(clock);

    private final List<LossSignal> signals = new CopyOnWriteArrayList<>();

    @Test
    @DisplayName("the acknowledgement signal fires at its deadline and never earlier")
    void acknowledgementFiresAtItsDeadline() {
        var detection = detection(RoleClass.WRITER, parameters(OptionalInt.of(ACK_SECONDS), false));
        detection.started(SPAWN);
        detection.offered(TASK, SPAWN);
        var deadline = SPAWN.plusSeconds(ACK_SECONDS);

        var before = detection.due(deadline.minusMillis(1));
        var at = detection.due(deadline);
        var after = detection.due(deadline.plusMillis(1));

        assertAll(
                () -> assertTrue(before.isEmpty(), "a millisecond before the deadline nothing has fired"),
                () -> assertEquals(LossReason.ACK_MISSED, at.orElseThrow().reason()),
                () -> assertEquals(deadline, at.orElseThrow().deadline()),
                () -> assertEquals(LossReason.ACK_MISSED, after.orElseThrow().reason()));
    }

    @Test
    @DisplayName("the silence signal fires at the bounded wait plus the grace and never earlier")
    void silenceFiresAtItsDeadline() {
        var detection = detection(RoleClass.WRITER, parameters(OptionalInt.empty(), false));
        detection.started(SPAWN);
        detection.firstCall(SPAWN);
        var deadline = SPAWN.plusSeconds(SILENCE_SECONDS);

        var before = detection.due(deadline.minusMillis(1));
        var at = detection.due(deadline);
        var after = detection.due(deadline.plusMillis(1));

        assertAll(
                () -> assertTrue(before.isEmpty(), "a millisecond before the deadline nothing has fired"),
                () -> assertEquals(LossReason.SILENCE, at.orElseThrow().reason()),
                () -> assertEquals(deadline, at.orElseThrow().deadline()),
                () -> assertEquals(LossReason.SILENCE, after.orElseThrow().reason()));
    }

    @Test
    @DisplayName("the fired signal reaches the consumer when the timer runs at the deadline")
    void timerFiresAtTheDeadline() {
        var detection = detection(RoleClass.WRITER, parameters(OptionalInt.of(ACK_SECONDS), false));
        detection.started(SPAWN);
        detection.offered(TASK, SPAWN);
        var deadline = SPAWN.plusSeconds(ACK_SECONDS);

        clock.set(deadline.minusMillis(1));
        timers.runDue();
        assertTrue(signals.isEmpty(), "the timer runs nothing before the deadline");

        clock.set(deadline);
        timers.runDue();

        assertAll(
                () -> assertEquals(1, signals.size(), "exactly one signal fires"),
                () -> assertEquals(LossReason.ACK_MISSED, signals.getFirst().reason()),
                () -> assertEquals(TASK, signals.getFirst().leasedTask()));
    }

    @Test
    @DisplayName("an informational acknowledgement suppresses the acknowledgement signal")
    void informationalAcknowledgementIsSuppressed() {
        var detection = detection(RoleClass.WRITER, parameters(OptionalInt.of(ACK_SECONDS), true));
        detection.started(SPAWN);
        detection.offered(TASK, SPAWN);

        var at = detection.due(SPAWN.plusSeconds(ACK_SECONDS));

        assertTrue(at.isEmpty(), "only silence alone decides an informational acknowledgement");
    }

    @Test
    @DisplayName("an open wait suppresses the silence signal until it is answered")
    void openWaitSuppressesSilence() {
        var detection = detection(RoleClass.WRITER, parameters(OptionalInt.empty(), false));
        detection.started(SPAWN);
        detection.firstCall(SPAWN);

        detection.waitOpened(SPAWN);
        var open = detection.due(SPAWN.plusSeconds(SILENCE_SECONDS));
        detection.waitClosed(SPAWN);
        var closed = detection.due(SPAWN.plusSeconds(SILENCE_SECONDS));

        assertAll(
                () -> assertTrue(open.isEmpty(), "a held wait is an open call"),
                () -> assertEquals(LossReason.SILENCE, closed.orElseThrow().reason()),
                () -> assertEquals(SPAWN.plusSeconds(SILENCE_SECONDS), closed.orElseThrow().deadline()));
    }

    @Test
    @DisplayName("an exit after the first call is the exit loss")
    void exitAfterTheFirstCallIsALoss() {
        var detection = detection(RoleClass.WRITER, parameters(OptionalInt.empty(), false));
        detection.started(SPAWN);
        detection.firstCall(SPAWN);
        detection.offered(TASK, SPAWN);
        detection.acknowledged(SPAWN);
        var observed = SPAWN.plusSeconds(5);

        var loss = detection.exit(observed);

        assertAll(
                () -> assertEquals(LossReason.EXIT, loss.orElseThrow().reason()),
                () -> assertEquals(observed, loss.orElseThrow().measuredAt()),
                () -> assertEquals(observed, loss.orElseThrow().deadline()),
                () -> assertTrue(loss.orElseThrow().firstCallMade()),
                () -> assertEquals(TASK, loss.orElseThrow().leasedTask()),
                () -> assertTrue(detection.due(SPAWN.plusSeconds(600)).isEmpty(),
                        "a settled worker produces no further signal"));
    }

    @Test
    @DisplayName("an exit before the first call is a start failure and not a loss")
    void exitBeforeTheFirstCallIsAStartFailure() {
        var detection = detection(RoleClass.WRITER, parameters(OptionalInt.empty(), false));
        detection.started(SPAWN);

        var loss = detection.exit(SPAWN.plusMillis(500));

        assertAll(
                () -> assertTrue(loss.isEmpty(), "a start failure is the guard's, not a loss"),
                () -> assertTrue(detection.startFailure()),
                () -> assertFalse(detection.firstCallMade()),
                () -> assertTrue(detection.due(SPAWN.plusSeconds(SILENCE_SECONDS)).isEmpty()));
    }

    @Test
    @DisplayName("a zero_tool worker's exit alone decides and is never a start failure")
    void zeroToolExitIsDecidedByItsResult() {
        var detection = detection(RoleClass.ZERO_TOOL, parameters(OptionalInt.of(ACK_SECONDS), false));
        detection.started(SPAWN);

        var due = detection.due(SPAWN.plusSeconds(10 * SILENCE_SECONDS));
        var exit = detection.exit(SPAWN.plusSeconds(5));

        assertAll(
                () -> assertTrue(due.isEmpty(), "its exit and its budget alone decide"),
                () -> assertEquals(LossReason.EXIT, exit.orElseThrow().reason()),
                () -> assertFalse(exit.orElseThrow().firstCallMade(), "a zero_tool worker makes no call"),
                () -> assertFalse(detection.startFailure(), "a zero_tool exit is never a start failure"),
                () -> assertTrue(detection.due(SPAWN.plusSeconds(600)).isEmpty(),
                        "a settled worker produces no further signal"));
    }

    @Test
    @DisplayName("a timer that wakes before its deadline re-arms instead of losing the signal")
    void anEarlyTimerReArms() {
        var detection = detection(RoleClass.WRITER, parameters(OptionalInt.of(ACK_SECONDS), false));
        detection.started(SPAWN);
        detection.offered(TASK, SPAWN);

        timers.runEarly();
        var firedEarly = List.copyOf(signals);
        clock.set(SPAWN.plusSeconds(ACK_SECONDS));
        timers.runDue();

        assertAll(
                () -> assertTrue(firedEarly.isEmpty(), "an early wake-up reports nothing"),
                () -> assertEquals(1, signals.size(), "the re-armed timer fires at the deadline"),
                () -> assertEquals(LossReason.ACK_MISSED, signals.getFirst().reason()));
    }

    @Test
    @DisplayName("a cancelled detection schedules nothing more")
    void cancelledSchedulesNothing() {
        var detection = detection(RoleClass.WRITER, parameters(OptionalInt.of(ACK_SECONDS), false));
        detection.started(SPAWN);
        detection.offered(TASK, SPAWN);

        detection.cancelled();
        clock.set(SPAWN.plusSeconds(ACK_SECONDS));
        timers.runDue();

        assertAll(
                () -> assertTrue(signals.isEmpty()),
                () -> assertTrue(detection.due(SPAWN.plusSeconds(600)).isEmpty()),
                () -> assertEquals(TASK, detection.leasedTask(), "the cancellation keeps the observed task"));
    }

    private LossDetection detection(RoleClass roleClass, WorkerParameters parameters) {
        return new LossDetection(GENERATION, roleClass, parameters, clock, timers, signals::add);
    }

    private static WorkerParameters parameters(OptionalInt ackDeadline, boolean informational) {
        return new WorkerParameters(true, ackDeadline, informational, GRACE_SECONDS, 3, BOUNDED_WAIT_SECONDS, true);
    }

    /** A clock a test moves to the deadline it asserts. */
    private static final class MutableClock implements InstantSource {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void set(Instant at) {
            now = at;
        }
    }

    /** The timers of the test: nothing runs until {@link #runDue()} is called at a moved clock. */
    private static final class ManualTimers implements WorkerTimers {

        private final InstantSource clock;

        private final List<Entry> entries = new ArrayList<>();

        ManualTimers(InstantSource clock) {
            this.clock = clock;
        }

        @Override
        public Cancellable schedule(Instant at, Runnable action) {
            var entry = new Entry(at, action);
            entries.add(entry);
            return () -> entry.cancelled = true;
        }

        void runDue() {
            var now = clock.instant();
            for (var entry : new ArrayList<>(entries)) {
                if (!entry.cancelled && !entry.at.isAfter(now)) {
                    entry.cancelled = true;
                    entry.action.run();
                }
            }
        }

        /** Runs the actions before the clock reaches them, as a timer that woke early does. */
        void runEarly() {
            for (var entry : new ArrayList<>(entries)) {
                if (!entry.cancelled) {
                    entry.cancelled = true;
                    entry.action.run();
                }
            }
        }

        private static final class Entry {

            private final Instant at;

            private final Runnable action;

            private boolean cancelled;

            Entry(Instant at, Runnable action) {
                this.at = at;
                this.action = action;
            }
        }
    }
}
