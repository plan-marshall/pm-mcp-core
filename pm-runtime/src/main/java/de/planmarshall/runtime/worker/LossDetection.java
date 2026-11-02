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

import java.time.Instant;
import java.time.InstantSource;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.profile.WorkerParameters;
import de.planmarshall.core.role.RoleClass;

/**
 * The three deadlines of one worker generation (PM-WORK-3, Detection): the exit observed by the per-worker watcher, the
 * missing acknowledgement of an offer, and silence past the bounded wait plus the host's silence grace.
 * <p>
 * Every value comes from the harness's {@link WorkerParameters} and the injected clock; the detector keeps no timing
 * literal. It arms and re-arms one {@link WorkerTimers.Cancellable} at the earliest deadline still open and hands the
 * signal that fired to {@code onLost}, so a deadline never ends a worker earlier than the specification says:
 * {@link #due(Instant)} asks whether a deadline has been crossed, and only a crossed deadline produces a signal.
 * <p>
 * A {@code zero_tool} worker has no MCP connection: it receives no offer and is never silent, so it produces neither an
 * acknowledgement nor a silence signal, and its exit alone decides. An exit before the first call is a start failure for
 * every other role, not a loss: {@link #exit(Instant)} answers empty and {@link #startFailure()} says why. A
 * {@code zero_tool} worker makes no call by design, so its exit is always the {@link LossReason#EXIT} loss, whose task
 * the caller closes — submitted for a valid structured result and lost for every other exit — and never a start failure.
 *
 * @since 0.1
 */
public final class LossDetection {

    private final String generationId;
    private final RoleClass roleClass;
    private final WorkerParameters parameters;
    private final InstantSource clock;
    private final WorkerTimers timers;
    private final Consumer<LossSignal> onLost;
    private @Nullable Instant lastCall;
    private Instant ackFrom = Instant.EPOCH;
    private boolean ackArmed;
    private boolean waitOpen;
    private boolean firstCallMade;
    private boolean startFailure;
    private boolean done;
    private @Nullable String leasedTask;
    private WorkerTimers.@Nullable Cancellable timer;

    /**
     * @param generationId the worker's generation
     * @param roleClass    the class of the role, which exempts a {@code zero_tool} worker
     * @param parameters   the worker values of the harness's client profile
     * @param clock        the clock every deadline is measured against
     * @param timers       the timers the next deadline is scheduled through
     * @param onLost       where a fired signal is handed over
     * @throws NullPointerException if a required collaborator is {@code null}
     */
    public LossDetection(String generationId, RoleClass roleClass, WorkerParameters parameters, InstantSource clock,
            WorkerTimers timers, Consumer<LossSignal> onLost) {
        this.generationId = Objects.requireNonNull(generationId, "generationId");
        this.roleClass = Objects.requireNonNull(roleClass, "roleClass");
        this.parameters = Objects.requireNonNull(parameters, "parameters");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.timers = Objects.requireNonNull(timers, "timers");
        this.onLost = Objects.requireNonNull(onLost, "onLost");
    }

    /**
     * Arms the silence deadline from the spawn; a worker that has not made its first call counts as silent from here.
     *
     * @param spawn the spawn instant of the generation
     */
    public synchronized void started(Instant spawn) {
        Objects.requireNonNull(spawn, "spawn");
        lastCall = spawn;
        firstCallMade = false;
        reschedule();
    }

    /**
     * Records the first call: from here a fresh worker owes its acknowledgement within the host's deadline.
     *
     * @param at the instant of the call
     */
    public synchronized void firstCall(Instant at) {
        Objects.requireNonNull(at, "at");
        firstCallMade = true;
        lastCall = at;
        armAck(at);
        reschedule();
    }

    /**
     * Records the offer of a task to a warm worker.
     *
     * @param queueTaskId the offered task
     * @param at          the instant of the offer
     */
    public synchronized void offered(String queueTaskId, Instant at) {
        Objects.requireNonNull(queueTaskId, "queueTaskId");
        Objects.requireNonNull(at, "at");
        leasedTask = queueTaskId;
        lastCall = at;
        armAck(at);
        reschedule();
    }

    /**
     * Records the acknowledgement of an offer or of a fresh worker's launch, which clears the acknowledgement deadline.
     *
     * @param at the instant of the acknowledgement
     */
    public synchronized void acknowledged(Instant at) {
        Objects.requireNonNull(at, "at");
        ackArmed = false;
        lastCall = at;
        reschedule();
    }

    /**
     * Records the delivery of the answer to a question the generation holds (Model Work § Answer acknowledgement): the
     * answer's acknowledgement is due within the host's deadline from here, by silence alone where the host's
     * acknowledgement is informational, and the silence limit counts again from the answer.
     *
     * @param at the instant the answer was delivered
     */
    public synchronized void answerDelivered(Instant at) {
        Objects.requireNonNull(at, "at");
        armAck(at);
        lastCall = at;
        reschedule();
    }

    /**
     * Records a call; the silence limit counts from the latest call while no wait is open.
     *
     * @param at the instant of the call
     */
    public synchronized void called(Instant at) {
        Objects.requireNonNull(at, "at");
        lastCall = at;
        reschedule();
    }

    /**
     * Records an opened bounded wait; a held wait is an open call and suppresses the silence signal.
     *
     * @param at the instant the wait was opened
     */
    public synchronized void waitOpened(Instant at) {
        Objects.requireNonNull(at, "at");
        waitOpen = true;
        lastCall = at;
        reschedule();
    }

    /**
     * Records the answer to an opened wait; the silence limit counts again from the answer.
     *
     * @param at the instant the wait was answered
     */
    public synchronized void waitClosed(Instant at) {
        Objects.requireNonNull(at, "at");
        waitOpen = false;
        lastCall = at;
        reschedule();
    }

    /**
     * The worker's process exited. For every role but {@code zero_tool} an exit after the first call is the loss
     * {@link LossReason#EXIT}; an exit before it is a start failure, which the start-failure guard counts and which
     * answers no loss. A {@code zero_tool} worker makes no call by design, so its exit is always the exit loss and never
     * a start failure: the caller decides the task from its structured result.
     *
     * @param at the instant the exit was observed
     * @return the loss, or empty when the exit was a start failure
     */
    public synchronized Optional<LossSignal> exit(Instant at) {
        Objects.requireNonNull(at, "at");
        if (done) {
            return Optional.empty();
        }
        done = true;
        cancel();
        if (!firstCallMade && roleClass != RoleClass.ZERO_TOOL) {
            startFailure = true;
            return Optional.empty();
        }
        return Optional.of(new LossSignal(LossReason.EXIT, generationId, at, at, firstCallMade, leasedTask));
    }

    /**
     * @param now an instant
     * @return the loss whose deadline lies at or before {@code now}; empty before every deadline and after the worker
     *         settled
     */
    public synchronized Optional<LossSignal> due(Instant now) {
        Objects.requireNonNull(now, "now");
        if (done) {
            return Optional.empty();
        }
        var ack = ackDeadline().filter(deadline -> !now.isBefore(deadline));
        var silence = silenceDeadline().filter(deadline -> !now.isBefore(deadline));
        var earliest = Stream.of(ack, silence).flatMap(Optional::stream).min(Instant::compareTo);
        if (earliest.isEmpty()) {
            return Optional.empty();
        }
        var reason = ack.isPresent() && ack.get().equals(earliest.get()) ? LossReason.ACK_MISSED : LossReason.SILENCE;
        return Optional.of(new LossSignal(reason, generationId, now, earliest.get(), firstCallMade, leasedTask));
    }

    /** @return whether the worker had made its first call */
    public synchronized boolean firstCallMade() {
        return firstCallMade;
    }

    /** @return whether the worker exited before its first call */
    public synchronized boolean startFailure() {
        return startFailure;
    }

    /** @return the queue task the worker holds; empty while it holds none */
    public synchronized Optional<String> leasedTask() {
        return Optional.ofNullable(leasedTask);
    }

    /** Stops every deadline; the generation settled by another cause. */
    public synchronized void cancelled() {
        done = true;
        cancel();
    }

    private void armAck(Instant at) {
        ackFrom = at;
        ackArmed = true;
    }

    private Optional<Instant> ackDeadline() {
        if (roleClass == RoleClass.ZERO_TOOL || parameters.ackInformational() || !ackArmed
                || parameters.ackDeadlineSeconds().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ackFrom.plusSeconds(parameters.ackDeadlineSeconds().getAsInt()));
    }

    private Optional<Instant> silenceDeadline() {
        if (roleClass == RoleClass.ZERO_TOOL || waitOpen || lastCall == null) {
            return Optional.empty();
        }
        return Optional.of(lastCall.plusSeconds(parameters.silenceLimitSeconds()));
    }

    private void reschedule() {
        cancel();
        if (done) {
            return;
        }
        Stream.of(ackDeadline(), silenceDeadline()).flatMap(Optional::stream).min(Instant::compareTo)
                .ifPresent(deadline -> timer = timers.schedule(deadline, this::fire));
    }

    /**
     * Runs on the timer thread: decides under the monitor whether a deadline is actually due, so an acknowledgement or
     * an exit that lands between the look and the effect still wins, and re-arms the timer when the action ran before
     * its deadline (a timer may wake early). The consumer is called outside the monitor, so it never runs under the
     * detector's lock.
     */
    private void fire() {
        Optional<LossSignal> signal;
        synchronized (this) {
            signal = due(clock.instant());
            if (signal.isEmpty()) {
                reschedule();
                return;
            }
            done = true;
            cancel();
        }
        onLost.accept(signal.get());
    }

    private void cancel() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }
}
