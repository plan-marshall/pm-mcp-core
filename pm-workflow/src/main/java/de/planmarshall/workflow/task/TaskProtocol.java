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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import de.planmarshall.core.queue.ActivityEntry;
import de.planmarshall.core.queue.ActivityJournal;
import de.planmarshall.core.queue.FencedGenerations;
import de.planmarshall.core.queue.QueueStore;
import de.planmarshall.core.queue.QueueTaskRecord;
import de.planmarshall.core.queue.QueueTaskRecord.Attempt;
import de.planmarshall.core.queue.QueueTaskRecord.AttemptEnd;
import de.planmarshall.core.queue.QueueTaskRecord.Continuation;
import de.planmarshall.core.queue.QueueTaskRecord.Lease;
import de.planmarshall.core.queue.QueueTaskRecord.LostReason;
import de.planmarshall.core.queue.QueueTaskRecord.Outcome;
import de.planmarshall.core.queue.QueueTaskRecord.ScopeType;
import de.planmarshall.core.queue.QueueTaskRecord.Signal;
import de.planmarshall.core.queue.QueueTaskRecord.SignalAction;
import de.planmarshall.core.queue.QueueTaskRecord.Status;
import de.planmarshall.core.role.RoleSkill;
import de.planmarshall.core.service.LockManager;

/**
 * The task protocol (Model Work § Task Protocol): the offers, the acknowledgement, the lease, the fence, the task
 * outcomes, the re-offers, the off-track signals and the skill delivery of one task queue.
 * <p>
 * Every method is one transaction of the {@link QueueStore}, under the queue lock: it reads the records, decides, and
 * writes the records it changes, so no second writer comes between the decision and the write. The fence is persisted
 * by {@link FencedGenerations} beside the queue, so a late call of a replaced generation is refused after a restart,
 * before any other check.
 * <p>
 * The order of the checks of a call is the one of {@link TaskRefusal}: the fence, the lease, the link, the arguments
 * and the delivered skills. The first valid submit closes the attempt in the transaction that validated it and
 * releases the lease; every later call of that generation is refused {@code stale_entity}, so a task is done exactly
 * once.
 * <p>
 * The protocol decides, the runtime transports: a pending {@code pm_wait} belongs to the transport, and this type
 * answers with an offer, an empty window or the end of the worker. A generation that holds a lease is offered no other
 * task, and a generation whose configuration digest is not the role's current one ends instead of being offered a
 * task.
 *
 * @since 0.1
 */
public final class TaskProtocol {

    private final QueueStore queue;
    private final FencedGenerations fences;
    private final PlanPause pause;
    private final SkillDelivery skills;
    private final SecurityAudit audit;
    private final ActivityJournal journal;

    private TaskProtocol(Builder builder) {
        this.queue = builder.queue;
        this.fences = builder.fences;
        this.pause = builder.pause;
        this.skills = builder.skills;
        this.audit = builder.audit;
        this.journal = builder.journal;
    }

    /**
     * @param queue the queue the protocol serves
     * @param locks the lock manager of the runtime
     * @return a builder with the queue's own fence store, no pause, an empty skill record, no audit and no journal
     */
    public static Builder builder(QueueStore queue, LockManager locks) {
        return new Builder(Objects.requireNonNull(queue, "queue"), Objects.requireNonNull(locks, "locks"));
    }

    /**
     * Builds a protocol. Every collaborator has a default; a builder without a queue or a lock manager creates none.
     *
     * @since 0.1
     */
    public static final class Builder {

        private final QueueStore queue;
        private final LockManager locks;
        private FencedGenerations fences;
        private PlanPause pause = PlanPause.NEVER;
        private SkillDelivery skills = new SkillDelivery();
        private SecurityAudit audit = SecurityAudit.NONE;
        private ActivityJournal journal;

        private Builder(QueueStore queue, LockManager locks) {
            this.queue = queue;
            this.locks = locks;
        }

        /**
         * @param value the fence store; the queue's own when never set
         * @return this builder
         */
        public Builder fences(FencedGenerations value) {
            this.fences = Objects.requireNonNull(value, "fences");
            return this;
        }

        /**
         * @param value whether the plan of a task is paused
         * @return this builder
         */
        public Builder pause(PlanPause value) {
            this.pause = Objects.requireNonNull(value, "pause");
            return this;
        }

        /**
         * @param value the record of the skills delivered to each generation
         * @return this builder
         */
        public Builder skills(SkillDelivery value) {
            this.skills = Objects.requireNonNull(value, "skills");
            return this;
        }

        /**
         * @param value where an {@code agent_started} detection is audited
         * @return this builder
         */
        public Builder audit(SecurityAudit value) {
            this.audit = Objects.requireNonNull(value, "audit");
            return this;
        }

        /**
         * @param value the journal of a task, or {@code null} for none
         * @return this builder
         */
        public Builder journal(ActivityJournal value) {
            this.journal = value;
            return this;
        }

        /** @return the protocol */
        public TaskProtocol build() {
            if (fences == null) {
                fences = new FencedGenerations(queue.directory(), queue.lockKey(), locks);
            }
            return new TaskProtocol(this);
        }
    }

    /**
     * Answers a worker's wait with the first task of its role the worker may take, under the offer rule.
     *
     * @param request what the worker asks for
     * @param at      the instant of the wait
     * @return an offer, an empty window or the end of the worker
     */
    public WaitAnswer wait(WaitRequest request, Instant at) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(at, "at");
        if (fences.isFenced(request.generationId())) {
            return new WaitAnswer.End("fenced");
        }
        if (!request.generationDigest().equals(request.roleDigest())) {
            return new WaitAnswer.End("recycled");
        }
        var answer = new AtomicReference<WaitAnswer>();
        queue.updateAll(records -> {
            var held = heldBy(records, request.generationId());
            if (held.isPresent()) {
                return answerHeld(records, held.get(), request, at, answer);
            }
            var selected = select(records, request);
            if (selected == null) {
                answer.set(new WaitAnswer.WaitAgain("no_task"));
                return records;
            }
            var offered = lease(selected, request, at);
            answer.set(new WaitAnswer.Offered(offered));
            return replace(records, offered);
        });
        return answer.get();
    }

    private List<QueueTaskRecord> answerHeld(List<QueueTaskRecord> records, QueueTaskRecord held, WaitRequest request,
            Instant at, AtomicReference<WaitAnswer> answer) {
        switch (held.status()) {
            case OFFERED -> answer.set(new WaitAnswer.Offered(held));
            case CONSULTING -> answer.set(new WaitAnswer.WaitAgain("consulting"));
            case AWAITING_ANSWER -> answer.set(new WaitAnswer.WaitAgain("awaiting_answer"));
            case EXECUTING -> {
                var returned = endAttempt(held, AttemptEnd.WAITED_AGAIN, request.reOfferBudget(), at);
                if (returned.isClosed()) {
                    answer.set(new WaitAnswer.WaitAgain("attempts_spent"));
                    return replace(records, returned);
                }
                var reoffered = lease(returned, request, at);
                answer.set(new WaitAnswer.Offered(reoffered));
                return replace(records, reoffered);
            }
            default -> answer.set(new WaitAnswer.WaitAgain("no_task"));
        }
        return records;
    }

    private static Optional<QueueTaskRecord> heldBy(List<QueueTaskRecord> records, String generationId) {
        return records.stream()
                .filter(task -> task.lease() != null && task.lease().generationId().equals(generationId))
                .findFirst();
    }

    private QueueTaskRecord select(List<QueueTaskRecord> records, WaitRequest request) {
        return records.stream()
                .filter(task -> task.status() == Status.QUEUED)
                .filter(request::matchesRole)
                .filter(task -> !isPaused(task))
                .filter(task -> writerAllowed(records, task))
                .min(OFFER_ORDER)
                .orElse(null);
    }

    /** Consultation tasks first, then work tasks in creation order. */
    private static final Comparator<QueueTaskRecord> OFFER_ORDER = Comparator
            .comparing((QueueTaskRecord task) -> task.kind() == QueueTaskRecord.Kind.CONSULTATION ? 0 : 1)
            .thenComparing(QueueTaskRecord::createdAt)
            .thenComparing(QueueTaskRecord::queueTaskId);

    private boolean isPaused(QueueTaskRecord task) {
        return task.scope().type() == ScopeType.PLAN && pause.isPaused(task.scope().id());
    }

    private static boolean writerAllowed(List<QueueTaskRecord> records, QueueTaskRecord task) {
        if (task.taskClass() != QueueTaskRecord.TaskClass.WRITER || task.scope().type() != ScopeType.PLAN) {
            return true;
        }
        return records.stream().noneMatch(other -> other != task
                && other.scope().equals(task.scope())
                && other.taskClass() == QueueTaskRecord.TaskClass.WRITER
                && other.lease() != null);
    }

    private static QueueTaskRecord lease(QueueTaskRecord task, WaitRequest request, Instant at) {
        var attempt = new Attempt(task.attempts().size() + 1, request.generationId(), request.generationId(),
                request.generationDigest(), at, null, null, null, false);
        var attempts = new ArrayList<>(task.attempts());
        attempts.add(attempt);
        return task.withStatus(Status.OFFERED)
                .withLease(new Lease(request.generationId(), request.generationId(), at, null))
                .withAttempts(attempts);
    }

    /**
     * Acknowledges an offer through the {@code ack} link.
     *
     * @param call the call
     * @param at   the instant of the acknowledgement
     * @return the task in {@code executing}, or the refusal
     */
    public CallResult acknowledge(TaskCall call, Instant at) {
        Objects.requireNonNull(at, "at");
        return onLeasedTask(call, task -> {
            if (task.status() != Status.OFFERED) {
                return new CallResult.Refused(TaskRefusal.LINK_NOT_AVAILABLE, task);
            }
            var attempts = acknowledged(task.attempts(), at);
            var lease = new Lease(task.lease().generationId(), task.lease().jobId(), task.lease().leasedAt(), at);
            return new CallResult.Accepted(task.withStatus(Status.EXECUTING).withLease(lease).withAttempts(attempts));
        });
    }

    /**
     * Closes the task with the outcome {@code submitted} in the transaction that validates the submit.
     *
     * @param call the call
     * @param at   the instant of the submit
     * @return the closed task, or the refusal
     */
    public CallResult submit(TaskCall call, Instant at) {
        Objects.requireNonNull(at, "at");
        return onLeasedTask(call, task -> task.status() == Status.EXECUTING
                ? new CallResult.Accepted(close(task, Outcome.SUBMITTED, null, AttemptEnd.CLOSED, at))
                : new CallResult.Refused(TaskRefusal.LINK_NOT_AVAILABLE, task));
    }

    /**
     * Ends the attempt of a task without a result and re-offers the task within the budget of its state.
     *
     * @param call   the call
     * @param end    how the attempt ended; never {@code closed} and never {@code suspended}
     * @param budget the re-offer budget of the state
     * @param at     the instant the attempt ended
     * @return the task as it stands after the call, or the refusal
     */
    public CallResult report(TaskCall call, AttemptEnd end, int budget, Instant at) {
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(at, "at");
        if (budget < 0) {
            throw new IllegalArgumentException("A re-offer budget is not negative: " + budget);
        }
        if (end == AttemptEnd.CLOSED || end == AttemptEnd.SUSPENDED) {
            return new CallResult.Refused(TaskRefusal.INVALID_LINK_ARGS, taskOf(call));
        }
        return onLeasedTask(call, task -> {
            if (end == AttemptEnd.WITHDRAWN) {
                return new CallResult.Accepted(close(task, Outcome.LOST, LostReason.WITHDRAWN, end, at));
            }
            if (end == AttemptEnd.DEADLINE_EXPIRED) {
                return new CallResult.Accepted(close(task, Outcome.LOST, LostReason.DEADLINE_EXPIRED, end, at));
            }
            return new CallResult.Accepted(endAttempt(task, end, budget, at));
        });
    }

    /**
     * Suspends the task: the attempting worker ended with its session stored and the task waits for its answer.
     *
     * @param call         the call
     * @param continuation the continuation the question left
     * @param at           the instant of the suspension
     * @return the task in {@code suspended}, or the refusal
     */
    public CallResult suspend(TaskCall call, Continuation continuation, Instant at) {
        Objects.requireNonNull(continuation, "continuation");
        Objects.requireNonNull(at, "at");
        return onLeasedTask(call, task -> new CallResult.Accepted(task
                .withAttempts(ended(task.attempts(), AttemptEnd.SUSPENDED, at))
                .withLease(null)
                .withStatus(Status.SUSPENDED)
                .withContinuation(continuation)));
    }

    /**
     * Records an off-track signal and takes its action. The signal is recorded at most once per attempt; a
     * {@code restart} (and a {@code yield}, which acts as a restart for every task but an envelope) ends the attempt
     * with {@code end: off_track} and re-offers the task within its budget.
     *
     * @param call   the call
     * @param signal the signal and the action the role artifact chose
     * @param budget the re-offer budget of the state
     * @param at     the instant the signal was raised
     * @return the task as it stands after the call, or the refusal
     */
    public CallResult offTrack(TaskCall call, OffTrackSignal signal, int budget, Instant at) {
        Objects.requireNonNull(signal, "signal");
        Objects.requireNonNull(at, "at");
        var recorded = onLeasedTask(call, task -> {
            var attempt = task.currentAttempt().orElseThrow();
            var alreadyHeld = task.signals().stream().anyMatch(existing -> existing.signal() == signal.signal()
                    && existing.attempt() == attempt.attempt());
            if (alreadyHeld) {
                return new CallResult.Accepted(task);
            }
            var signals = new ArrayList<>(task.signals());
            signals.add(new Signal(signal.signal(), attempt.attempt(), signal.action(), signal.measurement(),
                    signal.threshold(), at));
            var recordedTask = task.withSignals(signals);
            if (signal.action() != SignalAction.RESTART && signal.action() != SignalAction.YIELD) {
                return new CallResult.Accepted(recordedTask);
            }
            return new CallResult.Accepted(endAttempt(recordedTask, AttemptEnd.OFF_TRACK, budget, at));
        });
        if (recorded instanceof CallResult.Accepted accepted && journal != null) {
            appendSignalEntry(accepted.task(), signal, at);
        }
        return recorded;
    }

    /**
     * Records the supervisor's {@code agent_started} detection: its {@code security} audit record naming the
     * executable, the signal with the action {@code restart}, the end of the attempt {@code off_track} and the
     * re-offer within the budget.
     *
     * @param call   the call
     * @param event  the detection
     * @param budget the re-offer budget of the state
     * @return the task as it stands after the call, or the refusal
     */
    public CallResult agentStarted(TaskCall call, SecurityAudit.Event event, int budget) {
        Objects.requireNonNull(event, "event");
        if (!event.generationId().equals(call.generationId()) || !event.queueTaskId().equals(call.queueTaskId())) {
            throw new IllegalArgumentException("The detection names another worker or task than the call: " + event);
        }
        var result = offTrack(call, OffTrackSignal.agentStarted(event.executable()), budget, event.at());
        if (result instanceof CallResult.Accepted) {
            audit.agentStarted(event);
        }
        return result;
    }

    /**
     * Pauses a plan now: every open attempt of its tasks ends with {@code end: paused} without spending a re-offer,
     * its lease is released, and the task is queued again.
     *
     * @param planId the plan
     * @param at     the instant of the pause
     * @return the number of attempts the pause ended
     */
    public int pausePlan(String planId, Instant at) {
        Objects.requireNonNull(planId, "planId");
        Objects.requireNonNull(at, "at");
        var ended = new AtomicReference<>(0);
        queue.updateAll(records -> {
            var count = 0;
            var after = new ArrayList<QueueTaskRecord>(records.size());
            for (var task : records) {
                if (task.lease() != null && task.scope().type() == ScopeType.PLAN
                        && planId.equals(task.scope().id())) {
                    after.add(task.withAttempts(ended(task.attempts(), AttemptEnd.PAUSED, at))
                            .withLease(null)
                            .withStatus(Status.QUEUED));
                    count++;
                } else {
                    after.add(task);
                }
            }
            ended.set(count);
            return after;
        });
        return ended.get();
    }

    /**
     * Fences a generation: the fence is persisted, its lease is released and its task is queued again with
     * {@code end: restart}, which spends no re-offer.
     *
     * @param generationId the generation
     * @param at           the instant of the fence
     * @return the tasks the fence freed, in id order
     */
    public List<String> fence(String generationId, Instant at) {
        Objects.requireNonNull(generationId, "generationId");
        Objects.requireNonNull(at, "at");
        fences.fence(generationId, at);
        var freed = new AtomicReference<List<String>>(List.of());
        queue.updateAll(records -> {
            var ids = new ArrayList<String>();
            var after = new ArrayList<QueueTaskRecord>(records.size());
            for (var task : records) {
                if (task.lease() != null && task.lease().generationId().equals(generationId)) {
                    after.add(task.withAttempts(ended(task.attempts(), AttemptEnd.RESTART, at))
                            .withLease(null)
                            .withStatus(Status.QUEUED));
                    ids.add(task.queueTaskId());
                } else {
                    after.add(task);
                }
            }
            freed.set(List.copyOf(ids));
            return after;
        });
        skills.forget(generationId);
        return freed.get();
    }

    /**
     * Records the delivery of the skills a task requires to its generation.
     *
     * @param generationId the generation
     * @param required     the skills the task requires
     * @return the skills to deliver in-band: those the generation has not received, or whose digest changed
     */
    public List<RoleSkill> deliverSkills(String generationId, List<RoleSkill> required) {
        return skills.deliver(generationId, required);
    }

    /**
     * @param generationId the generation
     * @return the skills the generation has received, by URI and digest
     */
    public Map<String, String> deliveredSkills(String generationId) {
        return skills.recordOf(generationId);
    }

    private CallResult onLeasedTask(TaskCall call, Function<QueueTaskRecord, CallResult> action) {
        Objects.requireNonNull(call, "call");
        if (fences.isFenced(call.generationId())) {
            return new CallResult.Refused(TaskRefusal.GENERATION_FENCED, taskOf(call));
        }
        var result = new AtomicReference<CallResult>();
        queue.updateAll(records -> {
            var target = records.stream().filter(task -> task.queueTaskId().equals(call.queueTaskId())).findFirst();
            if (target.isEmpty()) {
                result.set(new CallResult.Refused(TaskRefusal.JOB_SCOPE_MISMATCH, null));
                return records;
            }
            var task = target.get();
            if (task.isClosed()) {
                result.set(new CallResult.Refused(TaskRefusal.STALE_ENTITY, task));
                return records;
            }
            if (task.lease() == null || !task.lease().generationId().equals(call.generationId())) {
                result.set(new CallResult.Refused(TaskRefusal.JOB_SCOPE_MISMATCH, task));
                return records;
            }
            var decided = action.apply(task);
            result.set(decided);
            return decided instanceof CallResult.Accepted accepted ? replace(records, accepted.task()) : records;
        });
        return result.get();
    }

    private QueueTaskRecord taskOf(TaskCall call) {
        return queue.read(call.queueTaskId()).orElse(null);
    }

    private static List<Attempt> acknowledged(List<Attempt> attempts, Instant at) {
        if (attempts.isEmpty()) {
            return attempts;
        }
        var updated = new ArrayList<>(attempts);
        var last = updated.getLast();
        updated.set(updated.size() - 1, new Attempt(last.attempt(), last.generationId(), last.jobId(),
                last.configurationDigest(), last.offeredAt(), at, last.endedAt(), last.end(), last.counts()));
        return updated;
    }

    private static List<Attempt> ended(List<Attempt> attempts, AttemptEnd end, Instant at) {
        if (attempts.isEmpty()) {
            return attempts;
        }
        var updated = new ArrayList<>(attempts);
        var last = updated.getLast();
        updated.set(updated.size() - 1, new Attempt(last.attempt(), last.generationId(), last.jobId(),
                last.configurationDigest(), last.offeredAt(), last.acknowledgedAt(), at, end, end.counts()));
        return updated;
    }

    private static QueueTaskRecord endAttempt(QueueTaskRecord task, AttemptEnd end, Instant at) {
        return requeue(task.withAttempts(ended(task.attempts(), end, at)).withLease(null));
    }

    private static QueueTaskRecord endAttempt(QueueTaskRecord task, AttemptEnd end, int budget, Instant at) {
        var endedTask = task.withAttempts(ended(task.attempts(), end, at)).withLease(null);
        if (end.counts() && endedTask.spentReOffers() >= budget) {
            return settle(endedTask, Outcome.LOST, LostReason.ATTEMPTS_SPENT, at);
        }
        return requeue(endedTask);
    }

    private static QueueTaskRecord requeue(QueueTaskRecord task) {
        return task.withStatus(Status.QUEUED);
    }

    private static QueueTaskRecord close(QueueTaskRecord task, Outcome outcome, LostReason reason, AttemptEnd end,
            Instant at) {
        return settle(task.withAttempts(ended(task.attempts(), end, at)), outcome, reason, at);
    }

    private static QueueTaskRecord settle(QueueTaskRecord task, Outcome outcome, LostReason reason, Instant at) {
        return task.withLease(null)
                .withStatus(Status.CLOSED)
                .withOutcome(outcome)
                .withReason(reason)
                .withClosedAt(at);
    }

    private static List<QueueTaskRecord> replace(List<QueueTaskRecord> records, QueueTaskRecord replacement) {
        var after = new ArrayList<QueueTaskRecord>(records.size());
        for (var task : records) {
            after.add(task.queueTaskId().equals(replacement.queueTaskId()) ? replacement : task);
        }
        return after;
    }

    private void appendSignalEntry(QueueTaskRecord task, OffTrackSignal signal, Instant at) {
        var entries = journal.entries(task.queueTaskId());
        var entry = ActivityEntry.builder()
                .seq(entries.size() + 1)
                .attempt(task.currentAttempt().map(Attempt::attempt).orElse(1))
                .source(ActivityEntry.Source.SERVER)
                .kind(ActivityEntry.Kind.SIGNAL)
                .signal(new ActivityEntry.Signal(signal.signal(), signal.action()))
                .at(at)
                .build();
        journal.append(task.queueTaskId(), entry);
    }
}
