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
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import de.planmarshall.core.queue.ActivityEntry;
import de.planmarshall.core.queue.FencedGenerations;
import de.planmarshall.core.queue.QueueStore;
import de.planmarshall.core.queue.QueueTaskRecord;
import de.planmarshall.core.queue.QueueTaskRecord.Attempt;
import de.planmarshall.core.queue.QueueTaskRecord.AttemptEnd;
import de.planmarshall.core.queue.QueueTaskRecord.Continuation;
import de.planmarshall.core.queue.QueueTaskRecord.FallbackReason;
import de.planmarshall.core.queue.QueueTaskRecord.Lease;
import de.planmarshall.core.queue.QueueTaskRecord.LostReason;
import de.planmarshall.core.queue.QueueTaskRecord.Outcome;
import de.planmarshall.core.queue.QueueTaskRecord.ScopeType;
import de.planmarshall.core.queue.QueueTaskRecord.Signal;
import de.planmarshall.core.queue.QueueTaskRecord.SignalAction;
import de.planmarshall.core.queue.QueueTaskRecord.Status;
import de.planmarshall.core.role.RoleSkill;
import de.planmarshall.core.service.LockManager;
import de.planmarshall.workflow.hypermedia.Answers;
import org.jspecify.annotations.Nullable;

/**
 * The task protocol (Model Work § Task Protocol): the offers, the acknowledgement, the lease, the fence, the task
 * outcomes, the re-offers, the off-track signals and the skill delivery of one task queue.
 * <p>
 * Every transition decides and writes inside one {@link QueueStore#updateAll} transaction on the queue lock; the
 * generation fence is written in that same transaction. The audit record and the activity journal are separate stores
 * and follow the transaction. The fence document is read lock-free inside the transaction: the only writer of the
 * fence needs the queue lock, so the read cannot change under the decision, and a generation fenced before the
 * transaction is refused before the lease and every link check (after the job's own end, which the specification orders
 * first).
 * <p>
 * The order of the checks of a call is the one of {@link TaskRefusal}: the job's end, the fence, the lease, the link,
 * the arguments and the delivered skills. The first valid submit closes the attempt in the transaction that validated it
 * and releases the lease; every later call of that generation is refused {@code stale_entity}, so a task is done exactly
 * once. A consultation task is closed through the consultation service's {@code answer}, never through {@code submit} or
 * {@code infeasible}: both refuse it {@code link_not_available}, because it offers only {@code submit:answer}.
 * <p>
 * The protocol decides, the runtime transports: a pending {@code pm_wait} belongs to the transport, and this type
 * answers with an offer, an empty window or the end of the worker. A generation that holds a lease is offered no other
 * task, and a generation whose configuration digest is not the role's current one ends instead of being offered a task.
 *
 * @since 0.1
 */
public final class TaskProtocol {

    private final QueueStore queue;
    private final FencedGenerations fences;
    private final PlanPause pause;
    private final JobState jobState;
    private final SkillDelivery skills;
    private final SecurityAudit audit;
    private final @Nullable JournalProvider journal;

    private static final String ARG_QUEUE_TASK_ID = "queueTaskId";

    /** The answers delivered to a holding or suspended task and not yet acknowledged; see {@link #answer}. */
    private final Map<String, DeliveredAnswer> answers = new ConcurrentHashMap<>();

    private TaskProtocol(Builder builder) {
        this.queue = builder.queue;
        this.fences = builder.fences;
        this.pause = builder.pause;
        this.jobState = builder.jobState;
        this.skills = builder.skills;
        this.audit = builder.audit;
        this.journal = builder.journal;
    }

    /**
     * @param queue the queue the protocol serves
     * @param locks the lock manager of the runtime
     * @return a builder with the queue's own fence store, no pause, no job state, an empty skill record, no audit and
     *         no journal
     */
    public static Builder builder(QueueStore queue, LockManager locks) {
        return new Builder(Objects.requireNonNull(queue, "queue"), Objects.requireNonNull(locks, "locks"));
    }

    /**
     * Builds a protocol. Every collaborator has a default.
     *
     * @since 0.1
     */
    public static final class Builder {

        private final QueueStore queue;
        private final LockManager locks;
        private @Nullable FencedGenerations fences;
        private PlanPause pause = PlanPause.NEVER;
        private JobState jobState = JobState.NEVER;
        private SkillDelivery skills = new SkillDelivery();
        private SecurityAudit audit = SecurityAudit.NONE;
        private @Nullable JournalProvider journal;

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
         * @param value whether a generation's job ended
         * @return this builder
         */
        public Builder jobState(JobState value) {
            this.jobState = Objects.requireNonNull(value, "jobState");
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
         * @param value where the journal of a task's scope is found, or {@code null} for none
         * @return this builder
         */
        public Builder journal(@Nullable JournalProvider value) {
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
        var answer = new AtomicReference<WaitAnswer>();
        queue.updateAll(records -> {
            pruneDeliveries(records);
            if (jobState.hasEnded(request.generationId())) {
                answer.set(new WaitAnswer.End("job_ended"));
                return records;
            }
            if (fenced().contains(request.generationId())) {
                answer.set(new WaitAnswer.End("fenced"));
                return records;
            }
            if (!request.generationDigest().equals(request.roleDigest())) {
                answer.set(new WaitAnswer.End("recycled"));
                return records;
            }
            var held = heldBy(records, request.generationId());
            if (held.isPresent()) {
                return answerHeld(records, held.get(), request, at, answer);
            }
            var selected = select(records, request);
            if (selected.isEmpty()) {
                answer.set(new WaitAnswer.WaitAgain("no_task"));
                return records;
            }
            var offered = lease(selected.get(), request, at);
            var delivered = answers.get(selected.get().queueTaskId());
            answer.set(delivered != null
                    ? new WaitAnswer.Answered(offered, delivered.answers())
                    : new WaitAnswer.Offered(offered));
            return replace(records, offered);
        });
        return answer.get();
    }

    private Set<String> fenced() {
        return fences.fenced();
    }

    private List<QueueTaskRecord> answerHeld(List<QueueTaskRecord> records, QueueTaskRecord held, WaitRequest request,
            Instant at, AtomicReference<WaitAnswer> answer) {
        if (isPaused(held)) {
            answer.set(new WaitAnswer.WaitAgain("paused"));
            return records;
        }
        switch (held.status()) {
            case OFFERED -> {
                var delivered = answers.get(held.queueTaskId());
                answer.set(delivered != null
                        ? new WaitAnswer.Answered(held, delivered.answers())
                        : new WaitAnswer.Offered(held));
            }
            case CONSULTING -> answer.set(new WaitAnswer.WaitAgain("consulting"));
            case AWAITING_ANSWER -> {
                var delivered = answers.get(held.queueTaskId());
                if (delivered != null) {
                    answer.set(new WaitAnswer.Answered(held, delivered.answers()));
                } else {
                    answer.set(new WaitAnswer.WaitAgain("awaiting_answer"));
                }
            }
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

    private Optional<QueueTaskRecord> select(List<QueueTaskRecord> records, WaitRequest request) {
        return records.stream()
                .filter(task -> task.status() == Status.QUEUED || resumable(task))
                .filter(request::matchesRole)
                .filter(task -> !isPaused(task))
                .filter(task -> writerAllowed(records, task))
                .min(OFFER_ORDER);
    }

    /**
     * @param task the task
     * @return whether the task is a suspension whose answer arrived: its resume is offered with that answer
     */
    private boolean resumable(QueueTaskRecord task) {
        return task.status() == Status.SUSPENDED && answers.containsKey(task.queueTaskId());
    }

    /**
     * Forgets the deliveries of tasks that are no longer waiting for an answer, so a delivery never outlives the hold or
     * suspension it was made to.
     *
     * @param records the queue as the transaction read it
     */
    private void pruneDeliveries(List<QueueTaskRecord> records) {
        if (answers.isEmpty()) {
            return;
        }
        answers.keySet().removeIf(id -> records.stream().noneMatch(task ->
                task.queueTaskId().equals(id)
                        && (task.status() == Status.AWAITING_ANSWER || task.status() == Status.SUSPENDED
                        || task.continuation() != null && !task.isClosed())));
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
     * Closes the task with the outcome {@code submitted} in the transaction that validates the submit, after checking
     * that every skill the task requires was delivered to the generation (Model Work § Loaded before Submit).
     *
     * @param call     the call
     * @param required the skills the task requires
     * @param at       the instant of the submit
     * @return the closed task, or the refusal
     */
    public CallResult submit(TaskCall call, List<RoleSkill> required, Instant at) {
        Objects.requireNonNull(required, "required");
        Objects.requireNonNull(at, "at");
        return onLeasedTask(call, task -> {
            if (task.status() != Status.EXECUTING) {
                return new CallResult.Refused(TaskRefusal.LINK_NOT_AVAILABLE, task);
            }
            if (task.kind() == QueueTaskRecord.Kind.CONSULTATION) {
                return new CallResult.Refused(TaskRefusal.LINK_NOT_AVAILABLE, task);
            }
            var missing = skills.missing(call.generationId(), required);
            if (!missing.isEmpty()) {
                return new CallResult.Refused(TaskRefusal.SKILLS_NOT_LOADED, task,
                        missing.stream().map(RoleSkill::uri).toList());
            }
            return new CallResult.Accepted(close(task, Outcome.SUBMITTED, null, AttemptEnd.CLOSED, at));
        });
    }

    /**
     * @param call the call
     * @param at   the instant of the submit
     * @return the closed task, or the refusal
     * @see #submit(TaskCall, List, Instant)
     * @implNote this form is for a task that requires no skill; a task whose role requires skills uses
     *           {@link #submit(TaskCall, List, Instant)}
     */
    public CallResult submit(TaskCall call, Instant at) {
        return submit(call, List.of(), at);
    }

    /**
     * Closes the task with the outcome {@code infeasible}, through the entity's {@code infeasible} link.
     *
     * @param call the call
     * @param at   the instant of the report
     * @return the closed task, or the refusal
     */
    public CallResult infeasible(TaskCall call, Instant at) {
        Objects.requireNonNull(at, "at");
        return onLeasedTask(call, task -> task.status() == Status.EXECUTING
                && task.kind() != QueueTaskRecord.Kind.CONSULTATION
                ? new CallResult.Accepted(close(task, Outcome.INFEASIBLE, null, AttemptEnd.CLOSED, at))
                : new CallResult.Refused(TaskRefusal.LINK_NOT_AVAILABLE, task));
    }

    /**
     * Ends the attempt of a task without a result and re-offers the task within the budget of its state.
     *
     * @param call   the call
     * @param end    how the attempt ended; one of {@code worker_lost}, {@code exited_without_submit},
     *               {@code waited_again}, {@code budget}, {@code start_failure}, {@code restart}, {@code drain},
     *               {@code withdrawn} or {@code deadline_expired}
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
        if (!REPORTABLE.contains(end)) {
            return onLeasedTask(call, task -> new CallResult.Refused(TaskRefusal.LINK_NOT_AVAILABLE, task));
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

    private static final Set<AttemptEnd> REPORTABLE = EnumSet.of(AttemptEnd.WORKER_LOST,
            AttemptEnd.EXITED_WITHOUT_SUBMIT, AttemptEnd.WAITED_AGAIN, AttemptEnd.BUDGET, AttemptEnd.START_FAILURE,
            AttemptEnd.RESTART, AttemptEnd.DRAIN, AttemptEnd.WITHDRAWN, AttemptEnd.DEADLINE_EXPIRED);

    /**
     * Holds the task for the answer to its question (Model Work § Continuing after a Question, hold form): the task
     * stays leased to the asking generation, moves to {@code awaiting_answer}, and offers nothing but {@code pm_wait}
     * until the answer is acknowledged. The attempt is not ended, so the hold spends no re-offer.
     *
     * @param call         the call
     * @param continuation the continuation the question left, of the form {@code hold}
     * @param at           the instant of the ask
     * @return the task in {@code awaiting_answer}, or the refusal
     */
    public CallResult hold(TaskCall call, Continuation continuation, Instant at) {
        Objects.requireNonNull(continuation, "continuation");
        Objects.requireNonNull(at, "at");
        return onLeasedTask(call, task -> {
            if (task.status() != Status.EXECUTING
                    || continuation.form() != QueueTaskRecord.ContinuationForm.HOLD) {
                return new CallResult.Refused(TaskRefusal.LINK_NOT_AVAILABLE, task);
            }
            return new CallResult.Accepted(task.withStatus(Status.AWAITING_ANSWER).withContinuation(continuation));
        });
    }

    /**
     * Delivers the answer of a closed question to the task that asked it (Model Work § Continuing after a Question,
     * answer acknowledgement): a holding task receives it at its pending or next {@code pm_wait}, a suspended task at
     * the offer of its resumed generation. The answer is held until it is acknowledged, which is what re-opens the
     * task's links; a question no open task of this queue holds is not delivered.
     *
     * @param questionId the closed question
     * @param answers    the answer's items, one per answered part
     * @param at         the instant of the delivery
     * @return the task the answer was delivered to, or empty when no holding or suspended task asked that question
     */
    public Optional<QueueTaskRecord> answer(String questionId, Answers answers, Instant at) {
        return answer(null, questionId, answers, at);
    }

    /**
     * Delivers the answer of a closed question to the task of one scope that asked it. One project queue serves several
     * plan and epic scopes, and a question id is unique only within its scope, so a caller that knows the scope names it;
     * without a scope the answer is delivered only when exactly one open task of the queue holds that question.
     *
     * @param scope      the scope of the question, or {@code null} when the caller does not know it
     * @param questionId the closed question
     * @param answers    the answer's items, one per answered part
     * @param at         the instant of the delivery
     * @return the task the answer was delivered to, or empty when no open task, or more than one, holds that question
     */
    public Optional<QueueTaskRecord> answer(QueueTaskRecord.@Nullable Scope scope, String questionId, Answers answers,
            Instant at) {
        Objects.requireNonNull(questionId, "questionId");
        Objects.requireNonNull(answers, "answers");
        Objects.requireNonNull(at, "at");
        var target = new AtomicReference<QueueTaskRecord>();
        var ambiguous = new AtomicBoolean();
        queue.updateAll(records -> {
            var candidates = records.stream()
                    .filter(task -> scope == null || task.scope().equals(scope))
                    .filter(task -> holdsQuestion(task, questionId))
                    .toList();
            if (candidates.size() > 1) {
                ambiguous.set(true);
                return records;
            }
            if (candidates.size() == 1) {
                var task = candidates.getFirst();
                this.answers.put(task.queueTaskId(), new DeliveredAnswer(answers, at));
                target.set(task);
            }
            return records;
        });
        if (ambiguous.get()) {
            return Optional.empty();
        }
        return Optional.ofNullable(target.get());
    }

    private static boolean holdsQuestion(QueueTaskRecord task, String questionId) {
        var continuation = task.continuation();
        return continuation != null
                && continuation.questionId().equals(questionId)
                && (task.status() == Status.AWAITING_ANSWER || task.status() == Status.SUSPENDED
                || task.status() == Status.QUEUED || task.status() == Status.OFFERED);
    }

    /**
     * @param queueTaskId the task
     * @return the instant the answer was delivered to the task, empty when none is waiting
     */
    public Optional<Instant> answerDeliveredAt(String queueTaskId) {
        var delivered = answers.get(Objects.requireNonNull(queueTaskId, ARG_QUEUE_TASK_ID));
        return Optional.ofNullable(delivered == null ? null : delivered.deliveredAt());
    }

    /**
     * @param queueTaskId the task
     * @return the answer delivered to the task and not yet acknowledged, empty when none is waiting
     */
    public Optional<Answers> deliveredAnswers(String queueTaskId) {
        var delivered = answers.get(Objects.requireNonNull(queueTaskId, ARG_QUEUE_TASK_ID));
        return Optional.ofNullable(delivered == null ? null : delivered.answers());
    }

    /**
     * Acknowledges the answer of a holding task through the {@code ack} link of the representation that carried it: the
     * task returns to {@code executing} and its links are offered again (Model Work § Answer acknowledgement). Only an
     * answer that was delivered can be acknowledged, and only by the generation that holds the task.
     *
     * @param call the call
     * @param at   the instant of the acknowledgement
     * @return the task in {@code executing}, or the refusal
     */
    public CallResult acknowledgeAnswer(TaskCall call, Instant at) {
        Objects.requireNonNull(at, "at");
        var acknowledged = onLeasedTask(call, task -> {
            if (!answers.containsKey(task.queueTaskId())) {
                return new CallResult.Refused(TaskRefusal.LINK_NOT_AVAILABLE, task);
            }
            if (task.status() == Status.AWAITING_ANSWER) {
                return new CallResult.Accepted(task.withStatus(Status.EXECUTING));
            }
            if (task.status() == Status.OFFERED) {
                var lease = new Lease(task.lease().generationId(), task.lease().jobId(),
                        task.lease().leasedAt(), at);
                return new CallResult.Accepted(task.withStatus(Status.EXECUTING).withLease(lease)
                        .withAttempts(acknowledged(task.attempts(), at)));
            }
            return new CallResult.Refused(TaskRefusal.LINK_NOT_AVAILABLE, task);
        });
        if (acknowledged instanceof CallResult.Accepted(QueueTaskRecord acceptedTask)) {
            answers.remove(acceptedTask.queueTaskId());
        }
        return acknowledged;
    }

    /**
     * Falls a continuation back (Model Work § Continuing after a Question, fallback): the task closes {@code asked}
     * carrying its question, the continuation records the reason, and the stored session is dropped. The work
     * continues in a new task that carries the answer in its {@code facts.answers}.
     *
     * @param call   the call
     * @param reason why the continuation fell back
     * @param at     the instant of the fallback
     * @return the closed task, or the refusal
     */
    public CallResult fallback(TaskCall call, FallbackReason reason, Instant at) {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(at, "at");
        var closed = onLeasedTask(call, task -> canFallBack(task)
                ? new CallResult.Accepted(applyFallback(task, reason, at))
                : new CallResult.Refused(TaskRefusal.LINK_NOT_AVAILABLE, task));
        if (closed instanceof CallResult.Accepted(QueueTaskRecord acceptedTask)) {
            answers.remove(acceptedTask.queueTaskId());
        }
        return closed;
    }

    /**
     * Falls a continuation that holds no lease back, for the server: the stored session is dropped because its worktree
     * head, its configuration digest, its harness version or the session itself no longer fits (Model Work § Resume
     * refused), or because the hold's limit passed; the task closes {@code asked} and the next task carries the answer.
     *
     * @param queueTaskId the holding or suspended task
     * @param reason      why the continuation fell back
     * @param at          the instant of the fallback
     * @return the closed task, empty when the task is not a continuation the server may end
     */
    public Optional<QueueTaskRecord> fallback(String queueTaskId, FallbackReason reason, Instant at) {
        Objects.requireNonNull(queueTaskId, ARG_QUEUE_TASK_ID);
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(at, "at");
        var closed = new AtomicReference<QueueTaskRecord>();
        queue.updateAll(records -> {
            var after = new ArrayList<QueueTaskRecord>(records.size());
            for (var task : records) {
                if (task.queueTaskId().equals(queueTaskId) && canFallBack(task)) {
                    var settled = applyFallback(task, reason, at);
                    closed.set(settled);
                    after.add(settled);
                } else {
                    after.add(task);
                }
            }
            return after;
        });
        if (closed.get() != null) {
            answers.remove(queueTaskId);
        }
        return Optional.ofNullable(closed.get());
    }

    private static boolean canFallBack(QueueTaskRecord task) {
        return task.continuation() != null
                && (task.status() == Status.AWAITING_ANSWER || task.status() == Status.SUSPENDED);
    }

    private static QueueTaskRecord applyFallback(QueueTaskRecord task, FallbackReason reason, Instant at) {
        var continuation = task.continuation();
        var dropped = new Continuation(continuation.questionId(), continuation.form(), continuation.holdUntil(),
                null, continuation.suspendedAt(), continuation.resumeUntil(), continuation.suspendedDigest(),
                continuation.suspendedHead(), reason);
        var asked = task.withContinuation(dropped).withQuestionId(continuation.questionId());
        var attemptsEnded = asked.attempts().isEmpty() || asked.currentAttempt().orElseThrow().end() != null
                ? asked : asked.withAttempts(ended(asked.attempts(), AttemptEnd.CLOSED, at));
        return attemptsEnded.withLease(null)
                .withStatus(Status.CLOSED)
                .withOutcome(Outcome.ASKED)
                .withClosedAt(at);
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
        return onLeasedTask(call, task -> {
            if (task.status() != Status.AWAITING_ANSWER
                    || continuation.form() != QueueTaskRecord.ContinuationForm.RESUME) {
                return new CallResult.Refused(TaskRefusal.LINK_NOT_AVAILABLE, task);
            }
            return new CallResult.Accepted(task
                    .withAttempts(ended(task.attempts(), AttemptEnd.SUSPENDED, at))
                    .withLease(null)
                    .withStatus(Status.SUSPENDED)
                    .withContinuation(continuation));
        });
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
        requireBudget(budget);
        return signal(call, signal, budget, at, CallResult.Accepted::new, task -> {
        });
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
        requireBudget(budget);
        return signal(call, OffTrackSignal.agentStarted(event.executable()), budget, event.at(), task -> {
            if (!event.generationId().equals(call.generationId()) || !event.queueTaskId().equals(call.queueTaskId())) {
                throw new IllegalArgumentException("The detection names another worker or task than the call: " + event);
            }
            var attempt = task.currentAttempt().orElseThrow().attempt();
            if (event.attempt() != attempt) {
                throw new IllegalArgumentException(
                        "The detection names the attempt %d, the task runs the attempt %d: %s"
                                .formatted(event.attempt(), attempt, event));
            }
            return new CallResult.Accepted(task);
        }, task -> audit.agentStarted(event));
    }

    private CallResult signal(TaskCall call, OffTrackSignal signal, int budget, Instant at,
            Function<QueueTaskRecord, CallResult> guard,
            Consumer<QueueTaskRecord> acceptedAction) {
        var recorded = onLeasedTask(call, task -> {
            var guarded = guard.apply(task);
            if (guarded instanceof CallResult.Refused) {
                return guarded;
            }
            var attempt = task.currentAttempt().orElseThrow();
            var held = task.signals().stream().filter(existing -> existing.signal() == signal.signal()
                    && existing.attempt() == attempt.attempt()).findFirst();
            if (held.isPresent()) {
                if (held.get().action() != signal.action()) {
                    return new CallResult.Refused(TaskRefusal.INVALID_LINK_ARGS, task);
                }
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
        if (recorded instanceof CallResult.Accepted(QueueTaskRecord acceptedTask)) {
            appendMissingSignalEntry(acceptedTask, signal, at);
            acceptedAction.accept(acceptedTask);
        }
        return recorded;
    }

    /**
     * Pauses a plan now: every open attempt of its tasks ends with {@code end: paused} without spending a re-offer, its
     * lease is released, and the task is queued again without its stale continuation.
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
                            .withContinuation(null)
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
        var freed = new AtomicReference<List<String>>(List.of());
        queue.updateAll(records -> {
            var ids = new ArrayList<String>();
            var after = new ArrayList<QueueTaskRecord>(records.size());
            for (var task : records) {
                if (task.lease() != null && task.lease().generationId().equals(generationId)) {
                    // a fenced hold keeps its continuation, so the answer reaches the successor once the record closes
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
        }, transaction -> fences.fence(transaction, generationId, at));
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

    @SuppressWarnings("java:S3776") // the refusal sequence is the specification's check order, read top to bottom
    private CallResult onLeasedTask(TaskCall call, Function<QueueTaskRecord, CallResult> action) {
        Objects.requireNonNull(call, "call");
        var result = new AtomicReference<CallResult>();
        queue.updateAll(records -> {
            if (jobState.hasEnded(call.generationId())) {
                result.set(new CallResult.Refused(TaskRefusal.JOB_ENDED, taskOf(records, call).orElse(null)));
                return records;
            }
            if (fenced().contains(call.generationId())) {
                result.set(new CallResult.Refused(TaskRefusal.GENERATION_FENCED, taskOf(records, call).orElse(null)));
                return records;
            }
            var target = records.stream().filter(task -> task.queueTaskId().equals(call.queueTaskId())).findFirst();
            if (target.isEmpty()) {
                result.set(new CallResult.Refused(TaskRefusal.JOB_SCOPE_MISMATCH, null));
                return records;
            }
            var task = target.get();
            if (task.isClosed()) {
                var holder = task.currentAttempt().map(Attempt::generationId).orElse(null);
                result.set(new CallResult.Refused(call.generationId().equals(holder)
                        ? TaskRefusal.STALE_ENTITY : TaskRefusal.JOB_SCOPE_MISMATCH, task));
                return records;
            }
            if (task.lease() == null || !task.lease().generationId().equals(call.generationId())) {
                result.set(new CallResult.Refused(TaskRefusal.JOB_SCOPE_MISMATCH, task));
                return records;
            }
            var decided = action.apply(task);
            result.set(decided);
            return decided instanceof CallResult.Accepted(QueueTaskRecord acceptedTask)
                    ? replace(records, acceptedTask) : records;
        });
        return result.get();
    }

    private static Optional<QueueTaskRecord> taskOf(List<QueueTaskRecord> records, TaskCall call) {
        return records.stream().filter(task -> task.queueTaskId().equals(call.queueTaskId())).findFirst();
    }

    private static void requireBudget(int budget) {
        if (budget < 0) {
            throw new IllegalArgumentException("A re-offer budget is not negative: " + budget);
        }
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

    private static QueueTaskRecord endAttempt(QueueTaskRecord task, AttemptEnd end, int budget, Instant at) {
        var endedTask = task.withAttempts(ended(task.attempts(), end, at)).withLease(null);
        if (end.counts() && endedTask.spentReOffers() > budget) {
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

    private void appendMissingSignalEntry(QueueTaskRecord task, OffTrackSignal signal, Instant at) {
        if (journal == null) {
            return;
        }
        var target = journal.journalFor(task.scope());
        if (target.isEmpty()) {
            return;
        }
        var activityJournal = target.get();
        var attempt = task.currentAttempt().map(Attempt::attempt).orElse(1);
        var present = activityJournal.entries(task.queueTaskId()).stream().anyMatch(entry ->
                entry.kind() == ActivityEntry.Kind.SIGNAL
                        && entry.attempt() == attempt
                        && entry.signal() != null
                        && entry.signal().signal() == signal.signal()
                        && entry.signal().action() == signal.action());
        if (present) {
            return;
        }
        activityJournal.appendNext(task.queueTaskId(), seq -> ActivityEntry.builder()
                .seq(seq)
                .attempt(attempt)
                .source(ActivityEntry.Source.SERVER)
                .kind(ActivityEntry.Kind.SIGNAL)
                .signal(new ActivityEntry.Signal(signal.signal(), signal.action()))
                .at(at)
                .build());
    }

    /**
     * An answer delivered to a task and not yet acknowledged.
     *
     * @param answers     the answer's items
     * @param deliveredAt the instant of the delivery
     */
    private record DeliveredAnswer(Answers answers, Instant deliveredAt) {
    }
}
