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

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.Builder;
import lombok.With;

/**
 * The job record {@code jobs/<job_id>.json} (PM-EXEC-4, PM-ARCH-7): the write-ahead journal of one job, written at its
 * fixed points (queued, write-ahead, post-spawn, fate) by {@link JobRecordStore}.
 * <p>
 * Every member this package writes is typed. The two members other components own are kept as the raw JSON text they
 * were read with and written back unchanged, as the status document keeps the members the engine does not own (ADR
 * 001 of {@code core.store}): {@code worker}, the launch contract and protocol state of a worker, and
 * {@code gate_facts}, the gate coverage facts of a build job. The record parses each of them at construction and
 * refuses a text that is not exactly one JSON value of the member's type, so what is written is always read back.
 * <p>
 * The record refuses at construction every combination the schema of the job record rules out:
 * <ul>
 * <li>{@code fate} and {@code outcome_row} are {@code null} exactly while the job is {@code queued} or
 * {@code running}; the status {@code refused} goes with the fate {@code refused} and no other;</li>
 * <li>{@code refusal_reason} is set exactly for the fate {@code refused};</li>
 * <li>a worker ({@code llm}) carries {@code worker}, runs as a process and belongs to a project or the machine; every
 * other kind carries no {@code worker}; a probe runs as a process and belongs to the machine or an onboarding
 * entry;</li>
 * <li>an {@code in_process} operation has no argument digest, PID, process start, spawn instant, exit code or signal,
 * and its confinement is {@code not_applicable};</li>
 * <li>{@code pid} and {@code process_started_at} are set together, by the post-spawn replace: a job counts as running
 * only once its record carries both (PM-ARCH-7, bullet 8); a queued or refused job was never spawned;</li>
 * <li>{@code child_wall_ms} is {@code null} for an {@code in_process} operation, a refused job and while the job is
 * queued or running, and never greater than the job's wall time from {@code spawned_at} to {@code ended_at}: a child
 * wall time is recorded only for an exit that was observed (PM-EXEC-4, bullet 7).</li>
 * </ul>
 *
 * @param jobId                   the id of the job, which also names its kind
 * @param execution               whether the job runs as a process or as an in-process operation
 * @param scope                   the scope that owns the job
 * @param worker                  the worker block, a JSON object as raw text, kept unchanged; {@code null} for every
 *                                kind but {@code llm}
 * @param budgetKey               the budget key of the job
 * @param budgetSeconds           the budget of the job in seconds, at least 1
 * @param budgetDerivation        how the budget was derived
 * @param argvDigest              the SHA-256 of the verified argument vector; {@code null} for an in-process operation
 * @param status                  the status of the job
 * @param fate                    the fate; {@code null} while the job is queued or running
 * @param pid                     the PID of the job, equal to its process-group id; {@code null} before the post-spawn
 *                                replace, for a refused job and for an in-process operation
 * @param processStartedAt        the start instant of the process, set with the PID
 * @param confinement             the confinement applied to the job
 * @param createdAt               the instant the record was created
 * @param spawnedAt               the instant the job was spawned; {@code null} while queued
 * @param endedAt                 the instant the job ended; {@code null} while it runs
 * @param childWallMs             the wall time of the child process in milliseconds; {@code null} when no exit was
 *                                observed
 * @param exitCode                the exit code; {@code null} when none was observed
 * @param signal                  the terminating signal; {@code null} when none
 * @param landlockAbi             the Landlock ABI version of the applied ruleset, at least 2; {@code null} when none was
 *                                applied
 * @param outcomeRow              the row 1 to 13 of the outcome derivation that decided the fate; {@code null} while
 *                                the job is queued or running
 * @param refusalReason           the refusal reason; set exactly for the fate {@code refused}
 * @param cancelBeforeGroup       whether a termination signal met {@code ESRCH} before the group existed
 * @param selfReportContradiction the contradiction of outcome row 6 of a project script job; {@code null} otherwise
 * @param clampedValues           the configured values the job used clamped; empty when none
 * @param gateFacts               the gate coverage facts, a JSON object as raw text, kept unchanged; {@code null} for
 *                                every job but a build job with a gate result
 * @since 0.1
 */
@With
@Builder(toBuilder = true)
public record JobRecord(JobId jobId, Execution execution, Scope scope, @Nullable String worker,
@Nullable String budgetKey, long budgetSeconds, BudgetDerivation budgetDerivation, @Nullable String argvDigest,
Status status, @Nullable Fate fate, @Nullable Long pid, @Nullable Instant processStartedAt, Confinement confinement,
Instant createdAt, @Nullable Instant spawnedAt, @Nullable Instant endedAt, @Nullable Long childWallMs,
@Nullable Long exitCode, @Nullable String signal, @Nullable Integer landlockAbi, @Nullable Integer outcomeRow,
@Nullable String refusalReason, boolean cancelBeforeGroup,
@Nullable SelfReportContradiction selfReportContradiction, List<ClampedValue> clampedValues,
@Nullable String gateFacts) {

    /** The format version of a job record that is read and written. */
    public static final int FORMAT_VERSION = 1;

    /** The first row of the outcome derivation table. */
    public static final int FIRST_OUTCOME_ROW = 1;

    /** The last row of the outcome derivation table. */
    public static final int LAST_OUTCOME_ROW = 13;

    /** The longest budget key the schema admits. */
    public static final int MAX_BUDGET_KEY = 155;

    private static final Pattern BUDGET_KEY = Pattern.compile(
            "[a-z][a-z0-9_]{0,31}:[a-z][a-z0-9.-]{0,63}(@[A-Za-z0-9][A-Za-z0-9_.-]{0,56})?");
    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern REASON = Pattern.compile("[a-z][a-z0-9_]*");
    private static final Pattern SCOPE_ID = Pattern.compile("[a-z0-9][a-z0-9-]{1,62}[a-z0-9]");
    private static final int MIN_LANDLOCK_ABI = 2;

    /**
     * Whether a job runs as a process or as an in-process operation.
     *
     * @since 0.1
     */
    public enum Execution {
        /** A process started through the launcher. */
        PROCESS("process"),
        /** A git or CI operation of the native variant, run inside the runtime. */
        IN_PROCESS("in_process");

        private final String wireName;

        Execution(String wireName) {
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
        public static Optional<Execution> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * The status of a job. {@code queued} and {@code running} are never fates.
     *
     * @since 0.1
     */
    public enum Status {
        /** Waiting for a build slot or, for a worker, a model slot; not spawned, the budget not started. */
        QUEUED("queued"),
        /** Refused before it was spawned. */
        REFUSED("refused"),
        /** Spawned, or about to be spawned in the write-ahead record. */
        RUNNING("running"),
        /** Ended with a fate. */
        ENDED("ended");

        private final String wireName;

        Status(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a record */
        public String wireName() {
            return wireName;
        }

        /** @return whether a job of this status has a fate */
        public boolean isSettled() {
            return this == REFUSED || this == ENDED;
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
     * The fate of a settled job, derived by the runtime from the evidence it observed.
     *
     * @since 0.1
     */
    public enum Fate {
        /** The job succeeded. */
        SUCCEEDED("succeeded"),
        /** The job failed. */
        FAILED("failed"),
        /** A build job failed and its log could not be parsed. */
        FAILED_UNPARSED("failed_unparsed"),
        /** The job exceeded its budget. */
        TIMED_OUT("timed_out"),
        /** The job was cancelled. */
        CANCELLED("cancelled"),
        /** The job was killed. */
        KILLED("killed"),
        /** The evidence decides no other fate. */
        INDETERMINATE("indeterminate"),
        /** The job was refused before it was spawned. */
        REFUSED("refused");

        private final String wireName;

        Fate(String wireName) {
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
        public static Optional<Fate> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * The confinement applied to a job, as the launch report of {@code pm-exec} names it.
     *
     * @since 0.1
     */
    public enum Confinement {
        /** The full ruleset was applied. */
        CONFINED("confined"),
        /** Only the ruleset of Landlock ABI 1 was applied. */
        PARTIAL_ABI_1("partial(abi=1)"),
        /** The ruleset was applied, and the container engine the job reaches writes outside it. */
        PARTIAL_CONTAINER_ENGINE("partial(container_engine)"),
        /** The job was confined by the macOS Seatbelt profile. */
        SEATBELT("seatbelt"),
        /** No ruleset could be applied. */
        UNAVAILABLE("unavailable"),
        /** No ruleset applies: an in-process operation. */
        NOT_APPLICABLE("not_applicable");

        private final String wireName;

        Confinement(String wireName) {
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
        public static Optional<Confinement> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * The scope that owns a job. Its vocabulary is the job record's own: it adds the project of a worker, the machine
     * and an onboarding entry to the scopes of the workflow.
     *
     * @param type the type of the scope
     * @param id   the plan, epic or entry id; {@code null} for the workspace, a project and the machine
     * @since 0.1
     */
    public record Scope(Type type, @Nullable String id) {

        /**
         * The type of the scope that owns a job.
         *
         * @since 0.1
         */
        public enum Type {
            /** A plan, named by its plan id. */
            PLAN("plan", true),
            /** An epic, named by its epic id. */
            EPIC("epic", true),
            /** The workspace scope of the project. */
            WORKSPACE("workspace", false),
            /** The project of a worker, which serves the project's task queue. */
            PROJECT("project", false),
            /** The machine scope. */
            MACHINE("machine", false),
            /** An onboarding entry of a project before its enrolment, named by its entry id. */
            ENTRY("entry", true);

            private final String wireName;
            private final boolean named;

            Type(String wireName, boolean named) {
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
            public static Optional<Type> fromWireName(String wireName) {
                return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
            }
        }

        /**
         * @throws NullPointerException     if the type is {@code null}
         * @throws IllegalArgumentException if a plan, epic or entry has no id, another type has one, or the id breaks
         *                                  the identifier grammar
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
     * How the budget of a job was derived.
     *
     * @param kind         the derivation
     * @param observations the number of observations a learned budget rests on; {@code null} for every other kind
     * @since 0.1
     */
    public record BudgetDerivation(Kind kind, @Nullable Long observations) {

        /**
         * The derivation of a budget.
         *
         * @since 0.1
         */
        public enum Kind {
            /** Learned from observed durations. */
            LEARNED("learned"),
            /** Escalated after a timeout. */
            ESCALATED("escalated"),
            /** Set by the operator, for a project script its enrolled maximum. */
            OPERATOR("operator"),
            /** The floor default. */
            FLOOR_DEFAULT("floor_default");

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
         * @throws IllegalArgumentException if observations are negative or given for a budget that was not learned
         */
        public BudgetDerivation {
            Objects.requireNonNull(kind, "kind");
            if (observations != null && (kind != Kind.LEARNED || observations < 0)) {
                throw new IllegalArgumentException(
                        "'budget_derivation.observations' is a non-negative count of a learned budget only");
            }
        }
    }

    /**
     * The contradiction of outcome row 6 between the self-reported status of a project script job and the evidence the
     * runtime observed.
     *
     * @param reportedStatus the status the job reported
     * @param exitCode       the observed exit code; {@code null} when none was observed
     * @param parseResult    the parser's verdict on the job log
     * @since 0.1
     */
    public record SelfReportContradiction(String reportedStatus, @Nullable Long exitCode, String parseResult) {

        /**
         * @throws NullPointerException     if the reported status or the parse result is {@code null}
         * @throws IllegalArgumentException if the reported status or the parse result is empty
         */
        public SelfReportContradiction {
            requireNonEmpty(reportedStatus, "self_report_contradiction.reported_status");
            requireNonEmpty(parseResult, "self_report_contradiction.parse_result");
        }
    }

    /**
     * A configured value the job used that lay outside its floor or cap and was clamped.
     *
     * @param key        the configuration key
     * @param configured the configured value as the JSON text of a number or a string; {@code null} when its source is
     *                   unreadable
     * @param used       the value used
     * @param source     where the configured value came from
     * @since 0.1
     */
    public record ClampedValue(String key, @Nullable String configured, BigDecimal used, Source source) {

        /**
         * Where a configured value came from.
         *
         * @since 0.1
         */
        public enum Source {
            /** The machine configuration. */
            MACHINE_CONFIG("machine_config"),
            /** The local configuration of the project. */
            LOCAL_CONFIG("local_config"),
            /** The enrolment of the project. */
            ENROLMENT("enrolment"),
            /** A profile override. */
            PROFILE_OVERRIDE("profile_override"),
            /** The compiled default. */
            COMPILED_DEFAULT("compiled_default"),
            /** A value that is invalid. */
            INVALID("invalid"),
            /** A source that could not be read. */
            UNREADABLE("unreadable");

            private final String wireName;

            Source(String wireName) {
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
            public static Optional<Source> fromWireName(String wireName) {
                return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
            }
        }

        /**
         * @throws NullPointerException     if the key, the used value or the source is {@code null}
         * @throws IllegalArgumentException if the key is empty or the configured value is no JSON number or string
         */
        public ClampedValue {
            requireNonEmpty(key, "clamped_values.key");
            Objects.requireNonNull(used, "used");
            Objects.requireNonNull(source, "source");
            requireSingleJsonValue(configured, "clamped_values.configured", "number or string",
                    EnumSet.of(JsonToken.VALUE_STRING, JsonToken.VALUE_NUMBER_INT, JsonToken.VALUE_NUMBER_FLOAT));
        }
    }

    /**
     * @throws NullPointerException     if a required component is {@code null}
     * @throws IllegalArgumentException if a value breaks its form or the combination breaks a rule of the record
     */
    public JobRecord {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(execution, "execution");
        Objects.requireNonNull(scope, "scope");
        requireBudget(budgetKey, budgetSeconds);
        Objects.requireNonNull(budgetDerivation, "budgetDerivation");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(confinement, "confinement");
        Objects.requireNonNull(createdAt, "createdAt");
        clampedValues = List.copyOf(Objects.requireNonNull(clampedValues, "clampedValues"));
        requireForms(argvDigest, pid, landlockAbi, refusalReason, childWallMs);
        requireRawObject(worker, "worker");
        requireRawObject(gateFacts, "gate_facts");
        requireKindRules(jobId.kind(), execution, scope, worker, selfReportContradiction, gateFacts);
        requireSettlement(status, fate, outcomeRow, refusalReason);
        requireProcessFields(execution, argvDigest, pid, processStartedAt, spawnedAt, exitCode, signal, confinement);
        if ((pid == null) != (processStartedAt == null)) {
            throw new IllegalArgumentException("'pid' and 'process_started_at' are set together");
        }
        if ((status == Status.QUEUED || status == Status.REFUSED) && (spawnedAt != null || pid != null)) {
            throw new IllegalArgumentException("A %s job was never spawned".formatted(status.wireName()));
        }
        requireChildWall(childWallMs, execution, status, spawnedAt, endedAt);
    }

    /** @return the kind of the job, named by its id */
    public JobId.Kind kind() {
        return jobId.kind();
    }

    /** @return the wall time of the job from its spawn to its end; empty unless both instants are recorded */
    public Optional<Duration> jobWallTime() {
        return spawnedAt == null || endedAt == null ? Optional.empty()
                : Optional.of(Duration.between(spawnedAt, endedAt));
    }

    /** @return whether the job counts as running: its record carries the PID and the process start instant */
    public boolean isSpawnRecorded() {
        return pid != null;
    }

    private static void requireBudget(String budgetKey, long budgetSeconds) {
        Objects.requireNonNull(budgetKey, "budgetKey");
        if (budgetKey.length() > MAX_BUDGET_KEY || !BUDGET_KEY.matcher(budgetKey).matches()) {
            throw new IllegalArgumentException("'budget_key' is not a budget key: " + budgetKey);
        }
        if (budgetSeconds < 1) {
            throw new IllegalArgumentException("'budget_seconds' must be at least 1: " + budgetSeconds);
        }
    }

    private static void requireForms(String argvDigest, Long pid, Integer landlockAbi, String refusalReason,
            Long childWallMs) {
        if (argvDigest != null && !DIGEST.matcher(argvDigest).matches()) {
            throw new IllegalArgumentException("'argv_digest' is not a SHA-256: " + argvDigest);
        }
        if (pid != null && pid < 1) {
            throw new IllegalArgumentException("'pid' must be at least 1: " + pid);
        }
        if (landlockAbi != null && landlockAbi < MIN_LANDLOCK_ABI) {
            throw new IllegalArgumentException("'landlock_abi' must be at least 2: " + landlockAbi);
        }
        if (refusalReason != null && !REASON.matcher(refusalReason).matches()) {
            throw new IllegalArgumentException("'refusal_reason' is not a reason: " + refusalReason);
        }
        if (childWallMs != null && childWallMs < 0) {
            throw new IllegalArgumentException("'child_wall_ms' must not be negative: " + childWallMs);
        }
    }

    private static void requireKindRules(JobId.Kind kind, Execution execution, Scope scope, String worker,
            SelfReportContradiction contradiction, String gateFacts) {
        if (kind == JobId.Kind.LLM) {
            if (worker == null || execution != Execution.PROCESS
                    || !Set.of(Scope.Type.PROJECT, Scope.Type.MACHINE).contains(scope.type())) {
                throw new IllegalArgumentException(
                        "A worker carries its worker block, runs as a process and belongs to a project or the machine");
            }
        } else if (worker != null) {
            throw new IllegalArgumentException("Only a worker carries a worker block, not a job of kind "
                    + kind.wireName());
        }
        if (kind == JobId.Kind.PROBE && (execution != Execution.PROCESS
                || !Set.of(Scope.Type.MACHINE, Scope.Type.ENTRY).contains(scope.type()))) {
            throw new IllegalArgumentException(
                    "A probe runs as a process and belongs to the machine or an onboarding entry");
        }
        if (contradiction != null && kind != JobId.Kind.PROJECT_SCRIPT) {
            throw new IllegalArgumentException("Only a project script job records a self-report contradiction");
        }
        if (gateFacts != null && kind != JobId.Kind.BUILD) {
            throw new IllegalArgumentException("Only a build job records gate facts");
        }
    }

    private static void requireSettlement(Status status, Fate fate, Integer outcomeRow, String refusalReason) {
        var settled = status.isSettled();
        if (settled != (fate != null) || settled != (outcomeRow != null)) {
            throw new IllegalArgumentException(
                    "'fate' and 'outcome_row' are set exactly when the job is settled, not while it is "
                            + status.wireName());
        }
        if (outcomeRow != null && (outcomeRow < FIRST_OUTCOME_ROW || outcomeRow > LAST_OUTCOME_ROW)) {
            throw new IllegalArgumentException("'outcome_row' lies outside 1 to 13: " + outcomeRow);
        }
        if ((status == Status.REFUSED) != (fate == Fate.REFUSED)) {
            throw new IllegalArgumentException("The status 'refused' goes with the fate 'refused' and no other");
        }
        if ((fate == Fate.REFUSED) != (refusalReason != null)) {
            throw new IllegalArgumentException("'refusal_reason' is set exactly for the fate 'refused'");
        }
    }

    private static void requireProcessFields(Execution execution, String argvDigest, Long pid,
            Instant processStartedAt, Instant spawnedAt, Long exitCode, String signal, Confinement confinement) {
        if (execution != Execution.IN_PROCESS) {
            return;
        }
        if (argvDigest != null || pid != null || processStartedAt != null || spawnedAt != null || exitCode != null
                || signal != null) {
            throw new IllegalArgumentException("An in-process operation has no process fields");
        }
        if (confinement != Confinement.NOT_APPLICABLE) {
            throw new IllegalArgumentException("An in-process operation has the confinement 'not_applicable'");
        }
    }

    private static void requireChildWall(Long childWallMs, Execution execution, Status status, Instant spawnedAt,
            Instant endedAt) {
        if (childWallMs == null) {
            return;
        }
        if (execution == Execution.IN_PROCESS || status != Status.ENDED) {
            throw new IllegalArgumentException(
                    "'child_wall_ms' is recorded only for an ended process job, not for an in-process operation, a "
                            + "refused job or a job that is queued or running");
        }
        if (spawnedAt == null || endedAt == null) {
            throw new IllegalArgumentException("'child_wall_ms' requires 'spawned_at' and 'ended_at'");
        }
        var wall = Duration.between(spawnedAt, endedAt).toMillis();
        if (childWallMs > wall) {
            throw new IllegalArgumentException("'child_wall_ms' %d exceeds the job's wall time of %d ms"
                    .formatted(childWallMs, wall));
        }
    }

    private static void requireRawObject(String raw, String name) {
        requireSingleJsonValue(raw, name, "object", EnumSet.of(JsonToken.START_OBJECT));
    }

    /**
     * A raw member is written back verbatim, so it must parse as exactly one JSON value of the expected type: a text
     * that merely starts and ends like one, such as {@code {"a":1},"fate":"succeeded","x":{}}, would be written into
     * the record and refuse the whole store when it is read.
     */
    private static void requireSingleJsonValue(String raw, String name, String expected, Set<JsonToken> accepted) {
        if (raw == null) {
            return;
        }
        try (var parser = StrictJson.factory().createParser(raw)) {
            if (!accepted.contains(parser.nextToken())) {
                throw new IllegalArgumentException("'%s' must be a JSON %s: %s".formatted(name, expected, raw));
            }
            parser.skipChildren();
            if (parser.nextToken() != null) {
                throw new IllegalArgumentException(
                        "'%s' must be exactly one JSON %s, but content follows it: %s".formatted(name, expected, raw));
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("'%s' is no JSON %s: %s".formatted(name, expected, raw), e);
        }
    }

    private static void requireNonEmpty(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("'%s' must not be empty".formatted(name));
        }
    }
}
