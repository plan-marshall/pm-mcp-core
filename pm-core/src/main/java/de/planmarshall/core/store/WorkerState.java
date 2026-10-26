/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.Builder;
import lombok.With;

/**
 * The {@code worker} member of a job record (PM-WORK-2, PM-WORK-3, PM-EXEC-4): the launch contract, the job-bound
 * connection and the protocol state of one worker generation, typed. It is the one typed shape of the block that
 * {@link JobRecord} keeps as raw text and {@link WorkerStateCodec} reads and writes.
 * <p>
 * A worker's generation is its {@code job_id}, so the {@code generation_id} of this value is the {@link JobId} of the
 * record that carries it; {@link JobRecordStore#writeWorker} refuses a state whose generation is another job.
 * <p>
 * The value refuses at construction every combination the schema of the job record rules out:
 * <ul>
 * <li>a {@code state_at} that precedes the worker's first call ({@code connected_at}), because the protocol cannot
 * advance before the worker connected;</li>
 * <li>a leased {@code task} in a state that holds no task ({@code waiting}, {@code lost}, {@code ended});</li>
 * <li>an {@code end_reason} in a state that is not terminal, and a terminal state ({@code lost}, {@code ended})
 * without one;</li>
 * <li>{@code start_failure} without the end reason {@code start_failure}, a start failure that carries a
 * {@code connected_at}, and a {@code harness_error} outside a start failure;</li>
 * <li>an {@code effort_narrowed_from} without the narrowed {@code effort};</li>
 * <li>a {@code job_token_sha256} of a zero-tool worker, which gets no connection;</li>
 * <li>a {@code workdir} of the kind {@code plan_worktree} without a plan id, and another kind with one.</li>
 * </ul>
 *
 * @param role               the role the worker serves
 * @param configurationDigest the digest over the role's effective values the worker was launched with
 * @param roleClass          the role's class, the member {@code class} of the schema
 * @param form               whether the worker serves one task or many
 * @param harness            the client id of the harness of the role's effective configuration
 * @param harnessVersion     the harness version probed at spawn
 * @param model              the model passed to the harness
 * @param effort             the effort passed after narrowing; {@code null} when none was passed
 * @param effortNarrowedFrom the role's effective effort when it was narrowed; {@code null} otherwise
 * @param generationId       the generation, equal to the job id of the record
 * @param workdir            the working directory fixed at launch
 * @param promptDigest       the SHA-256 of the server-composed launch prompt
 * @param jobTokenSha256     the SHA-256 of the job token; {@code null} for a zero-tool worker
 * @param connectedAt        the first call of the worker's connection; {@code null} while it never called
 * @param state              the state of the task protocol
 * @param stateAt            the instant of the last protocol transition
 * @param task               the queue task leased to this generation; {@code null} while it holds none
 * @param servedTasks        the queue tasks this generation closed, in order
 * @param worktreeMark       the working directory's state at the latest close; {@code null} for a fresh worker
 * @param idleWakeups        the consecutive bounded waits that ended without a task
 * @param contextTokens      the context size of the last turn, or explicitly unmeasured
 * @param deliveredSkills    the skills this generation has received
 * @param endReason          why the worker ended; {@code null} while it runs
 * @param startFailure       whether the worker exited before its first call
 * @param harnessError       the harness's own error text of a start failure; {@code null} otherwise
 * @since 0.1
 */
@With
@Builder(toBuilder = true)
public record WorkerState(String role, String configurationDigest, RoleClass roleClass, Form form, String harness,
String harnessVersion, String model, String effort, String effortNarrowedFrom, JobId generationId,
Workdir workdir, String promptDigest, String jobTokenSha256, Instant connectedAt, State state,
Instant stateAt, String task, List<String> servedTasks, WorktreeMark worktreeMark, long idleWakeups,
ContextTokens contextTokens, List<DeliveredSkill> deliveredSkills, EndReason endReason,
boolean startFailure, String harnessError) {

    /** The longest model name the schema admits. */
    public static final int MAX_MODEL = 128;

    /** The longest harness error text the schema admits, in characters. */
    public static final int MAX_HARNESS_ERROR = 2048;

    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9-]{1,31}");
    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern PLAN_ID = Pattern.compile("[a-z0-9][a-z0-9-]{1,62}[a-z0-9]");
    private static final Pattern TASK_ID = Pattern.compile("qt-[0-9a-f]{16}");
    private static final Pattern HEAD = Pattern.compile("[0-9a-f]{40,64}");
    private static final String SKILL_URI_PREFIX = "skill://pm/";

    /** The states in which the generation holds the leased queue task. */
    private static final Set<State> LEASED_STATES =
            Set.of(State.OFFERED, State.ACKNOWLEDGED, State.EXECUTING, State.CONSULTING, State.SUBMITTED);

    /**
     * The class of a role: its PM-MCP and harness tool sets.
     *
     * @since 0.1
     */
    public enum RoleClass {
        /** A writer works in one plan's worktree, at most one per plan at a time. */
        WRITER("writer"),
        /** A read-only worker reads and reports without changing a workspace. */
        READ_ONLY("read_only"),
        /** A zero-tool worker has no tool set and gets no connection. */
        ZERO_TOOL("zero_tool");

        private final String wireName;

        RoleClass(String wireName) {
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
        public static Optional<RoleClass> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * Whether a worker serves one task or many.
     *
     * @since 0.1
     */
    public enum Form {
        /** One task, then the job ends. */
        FRESH("fresh"),
        /** Tasks until the supervisor recycles or ends the worker. */
        WARM("warm");

        private final String wireName;

        Form(String wireName) {
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
        public static Optional<Form> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * The state of the task protocol of a worker, written at every protocol transition.
     *
     * @since 0.1
     */
    public enum State {
        /** Before the first offer and after a submit. */
        WAITING("waiting"),
        /** A task was offered to the worker. */
        OFFERED("offered"),
        /** The offer was acknowledged. */
        ACKNOWLEDGED("acknowledged"),
        /** The worker executes the task. */
        EXECUTING("executing"),
        /** The worker consults the server. */
        CONSULTING("consulting"),
        /** The worker submitted the task. */
        SUBMITTED("submitted"),
        /** The worker is lost. */
        LOST("lost"),
        /** The worker ended. */
        ENDED("ended");

        private final String wireName;

        State(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a record */
        public String wireName() {
            return wireName;
        }

        /** @return whether a worker of this state is terminal */
        public boolean isTerminal() {
            return this == LOST || this == ENDED;
        }

        /**
         * @param wireName the name of a value in a record
         * @return the value of that name; empty if no value has it
         */
        public static Optional<State> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * Why a worker ended: an instructed end, a detection signal, the budget or a start failure.
     *
     * @since 0.1
     */
    public enum EndReason {
        /** A fresh worker served its task. */
        TASK_DONE("task_done"),
        /** A warm worker was recycled. */
        IDLE_RECYCLE("idle_recycle"),
        /** A worker yielded its slot while idle. */
        IDLE_YIELD("idle_yield"),
        /** The token budget of the role was reached. */
        TOKEN_BUDGET("token_budget"),
        /** The worker reached the tasks per generation. */
        TASKS_PER_GENERATION("tasks_per_generation"),
        /** The configuration artifact changed. */
        ARTIFACT_CHANGED("artifact_changed"),
        /** The runtime drained. */
        DRAIN("drain"),
        /** The process exited. */
        EXITED("exited"),
        /** The acknowledgement of an offer was missed. */
        ACK_MISSED("ack_missed"),
        /** The worker was silent past its deadline. */
        SILENCE("silence"),
        /** The budget of the job was exceeded. */
        BUDGET("budget"),
        /** The worker exited before its first call. */
        START_FAILURE("start_failure"),
        /** The leased task of a fresh worker was withdrawn. */
        WITHDRAWN("withdrawn");

        private final String wireName;

        EndReason(String wireName) {
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
        public static Optional<EndReason> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * The working directory fixed at launch. A warm worker serves only tasks that run there.
     *
     * @param kind   the kind of the location
     * @param planId the plan of a plan worktree; {@code null} otherwise
     * @since 0.1
     */
    public record Workdir(Kind kind, String planId) {

        /**
         * The kind of a working directory.
         *
         * @since 0.1
         */
        public enum Kind {
            /** The worktree of one plan. */
            PLAN_WORKTREE("plan_worktree"),
            /** The project checkout. */
            PROJECT_CHECKOUT("project_checkout"),
            /** The empty {@code cwd/} of the job-private directory. */
            JOB_PRIVATE("job_private");

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
         * @throws NullPointerException     if the kind is {@code null}
         * @throws IllegalArgumentException if a plan worktree carries no plan id, another kind carries one, or the id
         *                                  breaks the identifier grammar
         */
        public Workdir {
            Objects.requireNonNull(kind, "kind");
            if ((kind == Kind.PLAN_WORKTREE) != (planId != null)) {
                throw new IllegalArgumentException("A working directory of the kind '%s' %s a plan id".formatted(
                        kind.wireName(), kind == Kind.PLAN_WORKTREE ? "requires" : "must not carry"));
            }
            if (planId != null && !PLAN_ID.matcher(planId).matches()) {
                throw new IllegalArgumentException("'worker.workdir.plan_id' is not an identifier: " + planId);
            }
        }
    }

    /**
     * The working directory's state when a warm generation closed its latest task.
     *
     * @param head         the commit of the working directory
     * @param changedPaths the paths changed since the serving start
     * @param markedAt     the instant the mark was taken
     * @since 0.1
     */
    public record WorktreeMark(String head, List<String> changedPaths, Instant markedAt) {

        /**
         * @throws NullPointerException     if the head, the list or the instant is {@code null}
         * @throws IllegalArgumentException if the head is no SHA-1 or SHA-256 or a path is empty
         */
        public WorktreeMark {
            Objects.requireNonNull(head, "head");
            if (!HEAD.matcher(head).matches()) {
                throw new IllegalArgumentException("'worker.worktree_mark.head' is not a SHA-1 or SHA-256: " + head);
            }
            changedPaths = List.copyOf(Objects.requireNonNull(changedPaths, "changedPaths"));
            for (var path : changedPaths) {
                requireNonEmpty(path, "worker.worktree_mark.changed_paths");
            }
            Objects.requireNonNull(markedAt, "markedAt");
        }
    }

    /**
     * One skill a generation has received.
     *
     * @param uri         the skill of the marketplace
     * @param digest      the SHA-256 of the delivered skill
     * @param deliveredAt the instant the skill was delivered
     * @since 0.1
     */
    public record DeliveredSkill(String uri, String digest, Instant deliveredAt) {

        /**
         * @throws NullPointerException     if the uri, the digest or the instant is {@code null}
         * @throws IllegalArgumentException if the uri is no skill of the marketplace or the digest is no SHA-256
         */
        public DeliveredSkill {
            Objects.requireNonNull(uri, "uri");
            if (!uri.startsWith(SKILL_URI_PREFIX)) {
                throw new IllegalArgumentException("'worker.delivered_skills.uri' is a skill of the marketplace: "
                        + uri);
            }
            requireDigest(digest, "worker.delivered_skills.digest");
            Objects.requireNonNull(deliveredAt, "deliveredAt");
        }
    }

    /**
     * The context size of the worker's last turn: a non-negative count, or the explicit {@code unmeasured} of a
     * harness whose per-turn usage the server did not read.
     *
     * @param tokens    the context size in tokens; {@code null} exactly when unmeasured
     * @param unmeasured whether the size was not measured
     * @since 0.1
     */
    public record ContextTokens(Long tokens, boolean unmeasured) {

        /** The one unmeasured value. */
        public static final ContextTokens UNMEASURED = new ContextTokens(null, true);

        /**
         * @param tokens the measured context size
         * @return the measured value
         * @throws IllegalArgumentException if the count is negative
         */
        public static ContextTokens measured(long tokens) {
            return new ContextTokens(tokens, false);
        }

        /**
         * @throws IllegalArgumentException if the count and the unmeasured flag do not exclude each other, or the
         *                                  count is negative
         */
        public ContextTokens {
            if (unmeasured == (tokens != null)) {
                throw new IllegalArgumentException(
                        "'worker.context_tokens' is a non-negative count or 'unmeasured', not both");
            }
            if (tokens != null && tokens < 0) {
                throw new IllegalArgumentException("'worker.context_tokens' must not be negative: " + tokens);
            }
        }
    }

    /**
     * @throws NullPointerException     if a required component is {@code null}
     * @throws IllegalArgumentException if a value breaks its form or the combination breaks a rule of the block
     */
    public WorkerState {
        requireName(role, "worker.role");
        requireDigest(configurationDigest, "worker.configuration_digest");
        Objects.requireNonNull(roleClass, "roleClass");
        Objects.requireNonNull(form, "form");
        requireName(harness, "worker.harness");
        requireNonEmpty(harnessVersion, "worker.harness_version");
        requireBounded(model, "worker.model", MAX_MODEL);
        requireNarrowing(effort, effortNarrowedFrom);
        Objects.requireNonNull(generationId, "generationId");
        if (generationId.kind() != JobId.Kind.LLM) {
            throw new IllegalArgumentException("'worker.generation_id' is the id of a worker: " + generationId);
        }
        Objects.requireNonNull(workdir, "workdir");
        requireDigest(promptDigest, "worker.prompt_digest");
        if (jobTokenSha256 != null) {
            requireDigest(jobTokenSha256, "worker.job_token_sha256");
        }
        if (roleClass == RoleClass.ZERO_TOOL && jobTokenSha256 != null) {
            throw new IllegalArgumentException("A zero-tool worker gets no connection and holds no job token");
        }
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(stateAt, "worker.state_at");
        if (connectedAt != null && stateAt.isBefore(connectedAt)) {
            throw new IllegalArgumentException("'worker.state_at' precedes the worker's first call: " + stateAt);
        }
        requireLeasedTask(state, task);
        servedTasks = List.copyOf(Objects.requireNonNull(servedTasks, "servedTasks"));
        for (var served : servedTasks) {
            requireTaskId(served, "worker.served_tasks");
        }
        Objects.requireNonNull(contextTokens, "contextTokens");
        deliveredSkills = List.copyOf(Objects.requireNonNull(deliveredSkills, "deliveredSkills"));
        if (idleWakeups < 0) {
            throw new IllegalArgumentException("'worker.idle_wakeups' must not be negative: " + idleWakeups);
        }
        requireTerminal(state, endReason);
        if (startFailure != (endReason == EndReason.START_FAILURE)) {
            throw new IllegalArgumentException(
                    "'worker.start_failure' is set exactly for the end reason 'start_failure'");
        }
        if (startFailure && connectedAt != null) {
            throw new IllegalArgumentException("A start failure exited before its first call");
        }
        requireHarnessError(startFailure, harnessError);
    }

    private static void requireName(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!NAME.matcher(value).matches()) {
            throw new IllegalArgumentException("'%s' is not a name: %s".formatted(name, value));
        }
    }

    private static void requireDigest(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!DIGEST.matcher(value).matches()) {
            throw new IllegalArgumentException("'%s' is not a SHA-256: %s".formatted(name, value));
        }
    }

    private static void requireNonEmpty(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("'%s' must not be empty".formatted(name));
        }
    }

    private static void requireBounded(String value, String name, int max) {
        Objects.requireNonNull(value, name);
        if (value.isEmpty() || value.length() > max) {
            throw new IllegalArgumentException("'%s' is 1 to %d characters".formatted(name, max));
        }
    }

    private static void requireNarrowing(String effort, String effortNarrowedFrom) {
        if (effortNarrowedFrom != null && effort == null) {
            throw new IllegalArgumentException("'worker.effort_narrowed_from' requires the narrowed 'worker.effort'");
        }
    }

    private static void requireLeasedTask(State state, String task) {
        if (task == null) {
            return;
        }
        requireTaskId(task, "worker.task");
        if (!LEASED_STATES.contains(state)) {
            throw new IllegalArgumentException("A task is leased in %s, not in the state '%s'".formatted(
                    LEASED_STATES, state.wireName()));
        }
    }

    private static void requireTaskId(String value, String name) {
        if (!TASK_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("'%s' is not a queue task id: %s".formatted(name, value));
        }
    }

    private static void requireTerminal(State state, EndReason endReason) {
        if (state.isTerminal() != (endReason != null)) {
            throw new IllegalArgumentException(
                    "'worker.end_reason' is set exactly in the terminal states 'lost' and 'ended', not in '%s'"
                            .formatted(state.wireName()));
        }
    }

    private static void requireHarnessError(boolean startFailure, String harnessError) {
        if (harnessError == null) {
            return;
        }
        if (!startFailure) {
            throw new IllegalArgumentException("'worker.harness_error' is the error text of a start failure");
        }
        if (harnessError.isEmpty() || harnessError.length() > MAX_HARNESS_ERROR) {
            throw new IllegalArgumentException("'worker.harness_error' is 1 to %d characters".formatted(
                    MAX_HARNESS_ERROR));
        }
    }
}
