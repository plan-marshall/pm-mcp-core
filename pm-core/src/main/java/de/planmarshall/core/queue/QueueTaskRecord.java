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

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.Builder;
import lombok.With;

/**
 * One queue task record {@code queue/<queue_task_id>.json} (Store Schemas § Queue Task Record): the delivery of one
 * unit of model work, its lease, its attempts, the signals raised on its workers and its consultations.
 * <p>
 * The record is written only by the {@link QueueStore} under the queue lock, as an atomic write-and-rename of the one
 * record a transition changes. The scope store is authoritative for which tasks a model state owns and how they
 * ended; this record is authoritative for the delivery.
 * <p>
 * The record checks each member's own form in its constructor and the combinations the schema rules out in
 * {@link #validate()}, which the store runs before every write and the codec after every read, so no invalid combination
 * is ever persisted or read (see {@code ADR core-queue/005}):
 * <ul>
 * <li>a closed task has an outcome and its closing instant and holds no lease; an open task has neither outcome nor
 * reason nor closing instant;</li>
 * <li>{@code lost} is the only outcome with a reason, and every other outcome has none;</li>
 * <li>the class is {@code null} exactly for a task closed {@code lost} with the reason {@code role_undefined};</li>
 * <li>a consultation task names its asking task, and a work task names none;</li>
 * <li>the question id is set exactly for the outcome {@code asked};</li>
 * <li>an {@code agent_started} signal always took the action {@code restart}, whatever the role artifact names;</li>
 * <li>the attempts are numbered from 1 without a gap, one per offer, in order.</li>
 * </ul>
 *
 * @param queueTaskId    the task, {@code qt-} and 16 hex digits; the name of the file
 * @param kind           whether the task is model work or a consultation
 * @param role           the role whose worker takes the task
 * @param taskClass      the class of the role at creation; {@code null} for a task closed {@code role_undefined}
 * @param scope          the scope of the task
 * @param entity         the entity of the scope the task works on, the key in {@code active_tasks}
 * @param stateId        the state that created the task
 * @param formDowngraded whether the caller ran a {@code warm} role {@code fresh} because only the fresh form qualifies
 * @param status         the status of the delivery
 * @param continuation   the continuation after the task's last question; {@code null} if it never asked or did not
 *                       continue
 * @param lease          the current lease; {@code null} while queued or closed
 * @param attempts       one entry per offer, in order
 * @param signals        the off-track signals raised on the task's workers, with the action taken
 * @param consultations  the consultations this task asked
 * @param consultationOf the asking task of a consultation task; {@code null} otherwise
 * @param outcome        the closed outcome; {@code null} while the task is open
 * @param reason         the reason of a {@code lost} task; {@code null} for every other outcome
 * @param questionId     the question record of an {@code asked} task; {@code null} otherwise
 * @param createdAt      the instant the record was created
 * @param closedAt       the instant the task closed; {@code null} while it is open
 * @since 0.1
 */
@With
@Builder(toBuilder = true)
public record QueueTaskRecord(String queueTaskId, Kind kind, String role, @Nullable TaskClass taskClass, Scope scope,
String entity, String stateId, boolean formDowngraded, Status status, @Nullable Continuation continuation,
@Nullable Lease lease, List<Attempt> attempts, List<Signal> signals, List<Consultation> consultations,
@Nullable String consultationOf, @Nullable Outcome outcome, @Nullable LostReason reason, @Nullable String questionId,
Instant createdAt, @Nullable Instant closedAt) {

    /** The format version of a queue task record that is read and written. */
    public static final int FORMAT_VERSION = 1;

    private static final Pattern ROLE_PATTERN = Pattern.compile("[a-z][a-z0-9-]{1,31}");
    private static final Pattern ENTITY_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,63}");
    private static final Pattern QUESTION_ID = Pattern.compile("Q-\\d{3,6}");
    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern HEAD = Pattern.compile("[0-9a-f]{40}");
    private static final Pattern GENERATION = Pattern.compile("l-[0-9a-f]{16}");

    /**
     * Whether a task is model work or a consultation.
     *
     * @since 0.1
     */
    public enum Kind {
        /** Model work of a task state. */
        WORK("work"),
        /** A consultation a task asked. */
        CONSULTATION("consultation");

        private final String wireName;

        Kind(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a record */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a record
         * @return the value of that name; empty if no value has it
         */
        public static Optional<Kind> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * The class of the role at creation.
     *
     * @since 0.1
     */
    public enum TaskClass {
        /** A role that writes. */
        WRITER("writer"),
        /** A role that reads. */
        READ_ONLY("read_only"),
        /** A role without a tool. */
        ZERO_TOOL("zero_tool");

        private final String wireName;

        TaskClass(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a record */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a record
         * @return the value of that name; empty if no value has it
         */
        public static Optional<TaskClass> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * The type of the scope a task belongs to.
     *
     * @since 0.1
     */
    public enum ScopeType {
        /** A plan, named by its plan id. */
        PLAN("plan", true),
        /** An epic, named by its epic id. */
        EPIC("epic", true),
        /** The workspace scope of the project. */
        WORKSPACE("workspace", false),
        /** The machine scope, which has no project. */
        MACHINE("machine", false);

        private final String wireName;
        private final boolean named;

        ScopeType(String wireName, boolean named) {
            this.wireName = wireName;
            this.named = named;
        }

        /** @return the name of the value in a record */
        public String wireName() {
            return wireName;
        }

        /** @return whether a scope of this type carries an id */
        public boolean isNamed() {
            return named;
        }

        /**
         * @param wireName the name of a value in a record
         * @return the value of that name; empty if no value has it
         */
        public static Optional<ScopeType> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * The scope of a task.
     *
     * @param type the type of the scope
     * @param id   the plan or epic id; {@code null} for the workspace and the machine scope
     * @since 0.1
     */
    public record Scope(ScopeType type, @Nullable String id) {

        private static final Pattern SCOPE_ID = Pattern.compile("[a-z0-9][a-z0-9-]{1,62}[a-z0-9]");

        /**
         * @throws NullPointerException     if the type is {@code null}
         * @throws IllegalArgumentException if a plan or epic has no id, another scope has one, or the id breaks the
         *                                  identifier grammar
         */
        public Scope {
            Objects.requireNonNull(type, "type");
            if (type.isNamed() != (id != null)) {
                throw new IllegalArgumentException("A scope of type '%s' %s an id".formatted(type.wireName(),
                        type.isNamed() ? "requires" : "carries no"));
            }
            if (id != null && !SCOPE_ID.matcher(id).matches()) {
                throw new IllegalArgumentException("'scope.id' is not an identifier: " + id);
            }
        }
    }

    /**
     * The status of a delivery.
     *
     * @since 0.1
     */
    public enum Status {
        /** Waiting for a generation of the role with the role's current configuration digest. */
        QUEUED("queued"),
        /** Leased, not yet acknowledged. */
        OFFERED("offered"),
        /** Acknowledged, from the acknowledgement on. */
        EXECUTING("executing"),
        /** An own consultation is open. */
        CONSULTING("consulting"),
        /** The generation holds the task for the answer to its question. */
        AWAITING_ANSWER("awaiting_answer"),
        /** No lease; the asking worker ended with its session stored. */
        SUSPENDED("suspended"),
        /** The task closed with its outcome. */
        CLOSED("closed");

        private final String wireName;

        Status(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a record */
        public String wireName() {
            return wireName;
        }

        /** @return whether a task of this status holds a lease */
        public boolean holdsLease() {
            return this == OFFERED || this == EXECUTING || this == CONSULTING || this == AWAITING_ANSWER;
        }

        /**
         * @param wireName the name of a value in a record
         * @return the value of that name; empty if no value has it
         */
        public static Optional<Status> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * A closed outcome of a task.
     *
     * @since 0.1
     */
    public enum Outcome {
        /** The entity recorded its required submission. */
        SUBMITTED("submitted"),
        /** The worker asked and did not continue after its question. */
        ASKED("asked"),
        /** The worker reported the task infeasible. */
        INFEASIBLE("infeasible"),
        /** No attempt delivered a result. */
        LOST("lost");

        private final String wireName;

        Outcome(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a record */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a record
         * @return the value of that name; empty if no value has it
         */
        public static Optional<Outcome> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * The reason a task closed {@code lost}.
     *
     * @since 0.1
     */
    public enum LostReason {
        /** The task's re-offers are spent. */
        ATTEMPTS_SPENT("attempts_spent"),
        /** The role is absent or invalid. */
        ROLE_UNDEFINED("role_undefined"),
        /** The role's class does not fit the entity kind, or a consulted role is {@code zero_tool}. */
        CLASS_MISMATCH("class_mismatch"),
        /** No valid qualification record covers the role. */
        ROLE_UNQUALIFIED("role_unqualified"),
        /** The model caller closed the task before any attempt. */
        ROLE_UNAVAILABLE("role_unavailable"),
        /** A vote task was still open at the vote wait's expiry. */
        DEADLINE_EXPIRED("deadline_expired"),
        /** The task's state or scope ended while the task was open. */
        WITHDRAWN("withdrawn");

        private final String wireName;

        LostReason(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a record */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a record
         * @return the value of that name; empty if no value has it
         */
        public static Optional<LostReason> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * How an attempt ended.
     *
     * @since 0.1
     */
    public enum AttemptEnd {
        /** The attempt closed the task. */
        CLOSED("closed"),
        /** The worker was lost while holding the task. */
        WORKER_LOST("worker_lost"),
        /** A fresh worker exited without the required submission. */
        EXITED_WITHOUT_SUBMIT("exited_without_submit"),
        /** The worker waited again while holding an acknowledged task. */
        WAITED_AGAIN("waited_again"),
        /** The worker exceeded its budget. */
        BUDGET("budget"),
        /** A fresh worker could not be started. */
        START_FAILURE("start_failure"),
        /** A runtime restart released the lease. */
        RESTART("restart"),
        /** The worker was drained. */
        DRAIN("drain"),
        /** The operator paused the plan now. */
        PAUSED("paused"),
        /** The server restarted a worker off track. */
        OFF_TRACK("off_track"),
        /** The task was withdrawn while the attempt held it. */
        WITHDRAWN("withdrawn"),
        /** A vote task expired while the attempt held it. */
        DEADLINE_EXPIRED("deadline_expired"),
        /** The worker ended with its session stored and the task waits for its answer. */
        SUSPENDED("suspended");

        private final String wireName;

        AttemptEnd(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a record */
        public String wireName() {
            return wireName;
        }

        /** @return whether the attempt spends a re-offer of the state's budget */
        public boolean counts() {
            return switch (this) {
                case CLOSED, START_FAILURE, RESTART, DRAIN, PAUSED, SUSPENDED, WITHDRAWN, DEADLINE_EXPIRED -> false;
                case WORKER_LOST, EXITED_WITHOUT_SUBMIT, WAITED_AGAIN, BUDGET, OFF_TRACK -> true;
            };
        }

        /**
         * @param wireName the name of a value in a record
         * @return the value of that name; empty if no value has it
         */
        public static Optional<AttemptEnd> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * The off-track signals a worker raises.
     *
     * @since 0.1
     */
    public enum SignalName {
        /** The same tool call with the same target, {@code N} times in a row. */
        REPEATING("repeating"),
        /** {@code N} turns without following a link of the task. */
        NO_PROGRESS("no_progress"),
        /** Far more than the predicted cost of the current plan task. */
        OVER_SIZE("over_size"),
        /** Unplanned changes beyond the declared footprint of the current plan task. */
        OUTSIDE_FOOTPRINT("outside_footprint"),
        /** A process of the worker's process tree runs an enrolled harness executable or {@code pm-operator}. */
        AGENT_STARTED("agent_started");

        private final String wireName;

        SignalName(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a record */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a record
         * @return the value of that name; empty if no value has it
         */
        public static Optional<SignalName> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * The action the server took for an off-track signal.
     *
     * @since 0.1
     */
    public enum SignalAction {
        /** The task and its plan show the signal. */
        SHOW("show"),
        /** The next tool result carries a server note naming the signal. */
        NUDGE("nudge"),
        /** The next call is answered with the end of the attempt. */
        YIELD("yield"),
        /** The worker is ended and its task re-offered. */
        RESTART("restart");

        private final String wireName;

        SignalAction(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a record */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a record
         * @return the value of that name; empty if no value has it
         */
        public static Optional<SignalAction> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * The form in force for a continuation after a question.
     *
     * @since 0.1
     */
    public enum ContinuationForm {
        /** The worker holds its task for the answer. */
        HOLD("hold"),
        /** The worker ends and resumes from its stored session. */
        RESUME("resume");

        private final String wireName;

        ContinuationForm(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a record */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a record
         * @return the value of that name; empty if no value has it
         */
        public static Optional<ContinuationForm> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * Why a continuation fell back instead of holding or resuming.
     *
     * @since 0.1
     */
    public enum FallbackReason {
        /** The role artifact does not allow the form. */
        NOT_ALLOWED("not_allowed"),
        /** The hold limit expired. */
        HOLD_EXPIRED("hold_expired"),
        /** The resume limit expired. */
        RESUME_EXPIRED("resume_expired"),
        /** The stored harness session is missing. */
        SESSION_MISSING("session_missing"),
        /** The configuration digest changed. */
        CONFIGURATION_CHANGED("configuration_changed"),
        /** The harness changed. */
        HARNESS_CHANGED("harness_changed"),
        /** The worktree moved. */
        WORKTREE_MOVED("worktree_moved"),
        /** The resumed generation did not acknowledge the answer. */
        RESUME_UNACKNOWLEDGED("resume_unacknowledged");

        private final String wireName;

        FallbackReason(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a record */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a record
         * @return the value of that name; empty if no value has it
         */
        public static Optional<FallbackReason> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * The status of a consultation.
     *
     * @since 0.1
     */
    public enum ConsultationStatus {
        /** The consultation is open. */
        OPEN("open"),
        /** The consultation was answered. */
        ANSWERED("answered"),
        /** The consultation closed lost. */
        UNAVAILABLE("unavailable");

        private final String wireName;

        ConsultationStatus(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a record */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a record
         * @return the value of that name; empty if no value has it
         */
        public static Optional<ConsultationStatus> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * Where a consultation answer was delivered.
     *
     * @since 0.1
     */
    public enum DeliveredTo {
        /** The asking generation's next wait. */
        WAIT("wait"),
        /** The successor's offer after the asker was fenced. */
        FACTS("facts");

        private final String wireName;

        DeliveredTo(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a record */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a record
         * @return the value of that name; empty if no value has it
         */
        public static Optional<DeliveredTo> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * The continuation after the task's last question.
     *
     * @param questionId      the question the task filed
     * @param form            the form in force
     * @param holdUntil       when the hold ends; {@code null} for a resume
     * @param sessionId       the harness session id read at the suspension; {@code null} when unknown
     * @param suspendedAt     when the worker suspended; {@code null} when unknown
     * @param resumeUntil     when the resume limit ends; {@code null} when unknown
     * @param suspendedDigest the configuration digest at the suspension; {@code null} when unknown
     * @param suspendedHead   the worktree head at the suspension; {@code null} when unknown
     * @param fallbackReason  why the continuation fell back; {@code null} when it did not
     * @since 0.1
     */
    public record Continuation(String questionId, ContinuationForm form, @Nullable Instant holdUntil,
    @Nullable String sessionId, @Nullable Instant suspendedAt, @Nullable Instant resumeUntil,
    @Nullable String suspendedDigest, @Nullable String suspendedHead, @Nullable FallbackReason fallbackReason) {

        /**
         * @throws NullPointerException     if the question id or the form is {@code null}
         * @throws IllegalArgumentException if a value breaks its form or a length
         */
        public Continuation {
            requireMatch(QUESTION_ID, Objects.requireNonNull(questionId, "questionId"), "question id");
            Objects.requireNonNull(form, "form");
            requireMaxLength(sessionId, "session_id", 256);
            if (suspendedDigest != null && !DIGEST.matcher(suspendedDigest).matches()) {
                throw new IllegalArgumentException("'suspended_digest' is not a SHA-256: " + suspendedDigest);
            }
            if (suspendedHead != null && !HEAD.matcher(suspendedHead).matches()) {
                throw new IllegalArgumentException("'suspended_head' is not a commit id: " + suspendedHead);
            }
        }
    }

    /**
     * The current lease of a task.
     *
     * @param generationId   the generation the task is leased to
     * @param jobId          the worker holding the lease
     * @param leasedAt       the instant of the offer
     * @param acknowledgedAt the instant the {@code ack} link was followed; {@code null} until then
     * @since 0.1
     */
    public record Lease(String generationId, String jobId, Instant leasedAt, @Nullable Instant acknowledgedAt) {

        /**
         * @throws NullPointerException     if a required component is {@code null}
         * @throws IllegalArgumentException if an id breaks its form, or the acknowledgement precedes the lease
         */
        public Lease {
            requireGeneration(generationId, "lease.generation_id");
            requireGeneration(jobId, "lease.job_id");
            Objects.requireNonNull(leasedAt, "leasedAt");
            if (acknowledgedAt != null && acknowledgedAt.isBefore(leasedAt)) {
                throw new IllegalArgumentException("A lease is acknowledged after it was taken: " + acknowledgedAt);
            }
        }
    }

    /**
     * One entry of the attempts, one per offer.
     *
     * @param attempt             the number of the attempt, from 1
     * @param generationId        the generation offered the task
     * @param jobId               the worker of the generation
     * @param configurationDigest the digest the generation was started with
     * @param offeredAt           the instant of the offer
     * @param acknowledgedAt      the instant of the acknowledgement; {@code null} if it never acknowledged
     * @param endedAt             the instant the attempt ended; {@code null} while it runs
     * @param end                 how the attempt ended; {@code null} while it runs
     * @param counts              whether the attempt spent a re-offer
     * @since 0.1
     */
    public record Attempt(int attempt, String generationId, String jobId, String configurationDigest,
    Instant offeredAt, @Nullable Instant acknowledgedAt, @Nullable Instant endedAt, @Nullable AttemptEnd end,
    boolean counts) {

        /**
         * @throws NullPointerException     if a required component is {@code null}
         * @throws IllegalArgumentException if the attempt is below 1, an id or digest breaks its form, an instant
         *                                  precedes another, or {@code counts} disagrees with the end
         */
        public Attempt {
            if (attempt < 1) {
                throw new IllegalArgumentException("An attempt counts from 1: " + attempt);
            }
            requireGeneration(generationId, "attempts.generation_id");
            requireGeneration(jobId, "attempts.job_id");
            if (configurationDigest == null || !DIGEST.matcher(configurationDigest).matches()) {
                throw new IllegalArgumentException("'attempts.configuration_digest' is not a SHA-256");
            }
            Objects.requireNonNull(offeredAt, "offeredAt");
            requireOrder(offeredAt, acknowledgedAt, "acknowledged_at");
            requireOrder(offeredAt, endedAt, "ended_at");
            if ((end == null) != (endedAt == null)) {
                throw new IllegalArgumentException("An attempt has its end and the instant it ended together");
            }
            if (end != null && end.counts() != counts) {
                throw new IllegalArgumentException(
                        "The attempt end '%s' %s a re-offer, but the attempt says counts=%s"
                                .formatted(end.wireName(), end.counts() ? "spends" : "spends no", counts));
            }
        }

        private static void requireOrder(Instant first, @Nullable Instant second, String name) {
            if (second != null && second.isBefore(first)) {
                throw new IllegalArgumentException("'%s' precedes the offer: %s".formatted(name, second));
            }
        }
    }

    /**
     * One off-track signal, with the action the server took.
     *
     * @param signal      the signal
     * @param attempt     the attempt of the task the worker served
     * @param action      the action taken
     * @param measurement the measured value
     * @param threshold   the threshold in force
     * @param at          when the signal was raised
     * @since 0.1
     */
    public record Signal(SignalName signal, int attempt, SignalAction action, double measurement, double threshold,
    Instant at) {

        /**
         * @throws NullPointerException     if a required component is {@code null}
         * @throws IllegalArgumentException if the attempt is below 1, a value is negative or not finite, or an
         *                                  {@code agent_started} signal does not take the action {@code restart}
         */
        public Signal {
            Objects.requireNonNull(signal, "signal");
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(at, "at");
            if (attempt < 1) {
                throw new IllegalArgumentException("A signal names its attempt from 1: " + attempt);
            }
            if (measurement < 0 || threshold < 0 || !Double.isFinite(measurement) || !Double.isFinite(threshold)) {
                throw new IllegalArgumentException(
                        "A signal's measurement and threshold are finite and not negative");
            }
            if (signal == SignalName.AGENT_STARTED && action != SignalAction.RESTART) {
                throw new IllegalArgumentException(
                        "An 'agent_started' signal always takes the action 'restart', not '%s'"
                                .formatted(action.wireName()));
            }
        }
    }

    /**
     * One consultation this task asked.
     *
     * @param consultationId the consultation task, equal to its {@code queue_task_id}
     * @param role           the consulted role
     * @param askedBy        the generation that asked
     * @param askedAt        the instant of the ask
     * @param status         the status of the consultation
     * @param answer         the answer, at most 4000 characters; {@code null} while open
     * @param closedAt       the instant the consultation closed; {@code null} while open
     * @param deliveredAt    the instant the answer was delivered; {@code null} until then
     * @param deliveredTo    where the answer was delivered; {@code null} until then
     * @since 0.1
     */
    public record Consultation(String consultationId, String role, String askedBy, Instant askedAt,
    ConsultationStatus status, @Nullable String answer, @Nullable Instant closedAt, @Nullable Instant deliveredAt,
    @Nullable DeliveredTo deliveredTo) {

        /**
         * @throws NullPointerException     if a required component is {@code null}
         * @throws IllegalArgumentException if an id or role breaks its form, the answer is too long, or an answer is
         *                                  recorded on an open consultation
         */
        public Consultation {
            if (QueueTaskId.parse(consultationId).isEmpty()) {
                throw new IllegalArgumentException("Not a queue task id: " + consultationId);
            }
            requireMatch(ROLE_PATTERN, Objects.requireNonNull(role, "role"), "role");
            requireGeneration(askedBy, "consultations.asked_by");
            Objects.requireNonNull(askedAt, "askedAt");
            Objects.requireNonNull(status, "status");
            requireMaxLength(answer, "answer", 4000);
            if ((status == ConsultationStatus.OPEN) != (closedAt == null)) {
                throw new IllegalArgumentException("A consultation closes when it leaves the status 'open'");
            }
            if (status == ConsultationStatus.OPEN && answer != null) {
                throw new IllegalArgumentException("An open consultation has no answer");
            }
            if ((deliveredAt == null) != (deliveredTo == null)) {
                throw new IllegalArgumentException("An answer is delivered at an instant and to one place together");
            }
        }
    }

    /**
     * @throws NullPointerException     if a required component is {@code null}
     * @throws IllegalArgumentException if a member breaks its own form
     */
    public QueueTaskRecord {
        var id = QueueTaskId.parse(queueTaskId);
        if (id.isEmpty()) {
            throw new IllegalArgumentException("Not a queue task id: " + queueTaskId);
        }
        Objects.requireNonNull(kind, "kind");
        requireMatch(ROLE_PATTERN, Objects.requireNonNull(role, "role"), "role");
        Objects.requireNonNull(scope, "scope");
        requireMatch(ENTITY_PATTERN, Objects.requireNonNull(entity, "entity"), "entity");
        Objects.requireNonNull(stateId, "stateId");
        Objects.requireNonNull(status, "status");
        attempts = List.copyOf(Objects.requireNonNull(attempts, "attempts"));
        signals = List.copyOf(Objects.requireNonNull(signals, "signals"));
        consultations = List.copyOf(Objects.requireNonNull(consultations, "consultations"));
        Objects.requireNonNull(createdAt, "createdAt");
        if (questionId != null) {
            requireMatch(QUESTION_ID, questionId, "question id");
        }
    }

    /**
     * Checks the combinations the schema rules out: a record is built member by member through its {@code with}
     * methods, so the combinations are checked where a record is written or read, never on every intermediate value.
     *
     * @throws IllegalArgumentException if a combination breaks a rule of the record
     */
    public void validate() {
        requireAttemptNumbers(attempts);
        requireSettlement(status, lease, outcome, reason, closedAt, createdAt);
        requireClass(taskClass, status, outcome, reason);
        requireConsultation(kind, consultationOf, entity, queueTaskId);
        requireQuestion(outcome, questionId);
    }

    /** @return whether the task closed */
    public boolean isClosed() {
        return status == Status.CLOSED;
    }

    /** @return the current attempt; empty if the task was never offered */
    public Optional<Attempt> currentAttempt() {
        return attempts.isEmpty() ? Optional.empty() : Optional.of(attempts.getLast());
    }

    /** @return the number of attempts that spent a re-offer */
    public long spentReOffers() {
        return attempts.stream().filter(Attempt::counts).count();
    }

    private static void requireSettlement(Status status, @Nullable Lease lease, @Nullable Outcome outcome,
            @Nullable LostReason reason, @Nullable Instant closedAt, Instant createdAt) {
        var closed = status == Status.CLOSED;
        if (closed != (outcome != null) || closed != (closedAt != null)) {
            throw new IllegalArgumentException(
                    "A closed task has its outcome and closing instant, and an open task neither");
        }
        if (closed && lease != null) {
            throw new IllegalArgumentException("A closed task holds no lease");
        }
        if (!closed && lease == null && status.holdsLease()) {
            throw new IllegalArgumentException("A task in the status '%s' holds no lease"
                    .formatted(status.wireName()));
        }
        if (!closed && lease != null && !status.holdsLease()) {
            throw new IllegalArgumentException("A task in the status '%s' holds a lease"
                    .formatted(status.wireName()));
        }
        var lost = outcome == Outcome.LOST;
        if (lost != (reason != null)) {
            throw new IllegalArgumentException("'lost' is the only outcome with a reason, and it has one");
        }
        if (closedAt != null && closedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("A task closes after it was created: " + closedAt);
        }
    }

    private static void requireClass(@Nullable TaskClass taskClass, Status status, Outcome outcome,
            LostReason reason) {
        var undefined = outcome == Outcome.LOST && reason == LostReason.ROLE_UNDEFINED;
        if (undefined != (taskClass == null)) {
            throw new IllegalArgumentException(
                    "The class is absent exactly for a task closed 'lost' with the reason 'role_undefined'");
        }
        if (taskClass == null && status != Status.CLOSED) {
            throw new IllegalArgumentException("Only a closed task has no class");
        }
    }

    private static void requireConsultation(Kind kind, @Nullable String consultationOf, String entity,
            String queueTaskId) {
        if (kind == Kind.CONSULTATION) {
            if (consultationOf == null || QueueTaskId.parse(consultationOf).isEmpty()) {
                throw new IllegalArgumentException("A consultation task names its asking task");
            }
            if (!entity.equals(queueTaskId)) {
                throw new IllegalArgumentException(
                        "The entity of a consultation task is its consultation id: " + entity);
            }
        } else if (consultationOf != null) {
            throw new IllegalArgumentException("Only a consultation task names its asking task");
        }
    }

    private static void requireQuestion(Outcome outcome, String questionId) {
        if ((outcome == Outcome.ASKED) != (questionId != null)) {
            throw new IllegalArgumentException("The question id is set exactly for the outcome 'asked'");
        }
    }

    private static void requireAttemptNumbers(List<Attempt> attempts) {
        for (var index = 0; index < attempts.size(); index++) {
            if (attempts.get(index).attempt() != index + 1) {
                throw new IllegalArgumentException(
                        "The attempts are numbered from 1 without a gap: %d at position %d"
                                .formatted(attempts.get(index).attempt(), index));
            }
        }
    }

    private static void requireGeneration(@Nullable String generation, String name) {
        if (generation == null || !GENERATION.matcher(generation).matches()) {
            throw new IllegalArgumentException("'%s' is not a generation (l- and 16 hex digits): %s".formatted(name,
                    generation));
        }
    }

    private static void requireMatch(Pattern pattern, String value, String name) {
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException("Not a %s: %s".formatted(name, value));
        }
    }

    private static void requireMaxLength(@Nullable String value, String name, int max) {
        if (value != null && value.codePointCount(0, value.length()) > max) {
            throw new IllegalArgumentException("'%s' has at most %d characters".formatted(name, max));
        }
    }
}
