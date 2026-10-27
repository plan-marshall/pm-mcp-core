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
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Pattern;
import lombok.Builder;
import lombok.With;

import de.planmarshall.core.queue.QueueTaskRecord;
import de.planmarshall.core.store.HarnessUsage.UnmeasuredReason;

/**
 * One task row of a {@code metrics.json} (Store Schemas § Metrics &amp; Quality Report, {@code taskRow}; Model Work §
 * Worker Result &amp; Usage Parsing): the usage of one fresh worker's whole run (population {@code model_job}) or of one
 * task a warm worker served (population {@code model_task}), with every column the specification requires.
 * <p>
 * The row keeps each usage figure as it was measured ({@link HarnessUsage}); the total is derived from the five token
 * components and never parsed. Beside the figures it carries what they depend on — role, harness, the harness version
 * probed at the worker's spawn, model, effort, form, phase — and {@code attempt_end}, the end of the attempt the queue
 * task record holds, so the share of attempts that ended cleanly without a submission is read per role, harness
 * version, model and effort.
 * <p>
 * A task closed without an attempt yields a row whose every usage column is {@code unmeasured}, whose
 * {@code cause_class} is {@code refused}, and whose {@code attempt}, {@code attempt_end}, {@code job_id},
 * {@code generation_id}, {@code harness}, {@code harness_version}, {@code model}, {@code form} and
 * {@code duration_basis} are {@code null}; {@link #refused} builds it.
 *
 * @param queueTaskId       the queue task the row measures
 * @param attempt           the attempt, at least {@code 1}; {@code null} only for a task closed without an attempt
 * @param role              the role of the task
 * @param jobId             the worker that served the attempt; {@code null} only for a refused task
 * @param generationId      the generation of the worker; {@code null} only for a refused task
 * @param harness           the worker's harness; {@code null} only for a refused task
 * @param harnessVersion    the harness version probed at the worker's spawn; {@code null} only for a refused task
 * @param model             the model passed to the harness; {@code null} only for a refused task
 * @param effort            the effort passed after narrowing; {@code null} when none was passed
 * @param form              the worker's form; {@code null} only for a refused task
 * @param phase             the phase of the creating state; {@code null} for the epic and workspace scopes
 * @param stepId            the step of the creating state, or {@code null}
 * @param envelopeId        the envelope of an envelope task, or {@code null}
 * @param predictedCost     the summed predicted cost of the envelope, or {@code null}
 * @param yieldReason       the early-end exit of an envelope task, or {@code null}
 * @param outcome           the outcome of the task
 * @param attemptEnd        how the attempt ended; {@code null} only for a refused task
 * @param continuation      the form of the continuation the row's usage belongs to; {@code null} when the task never
 *                          continued
 * @param causeClass        the cause class of the task
 * @param spendClass        what the tokens were spent on
 * @param population        the population of the figures
 * @param usage             the usage figures of the attempt
 * @param durationMs        the measured duration, or {@code null}
 * @param durationBasis     what the duration measures; {@code null} only for a refused task
 * @param queuedMs          the time the task waited queued, or {@code null} when it never waited
 * @param consultationOf    the asking task of a consultation, or {@code null}
 * @param recordedAt        the instant the row was recorded
 * @since 0.1
 */
@With
@Builder(toBuilder = true)
public record MetricsRow(String queueTaskId, Integer attempt, String role, String jobId, String generationId,
String harness, String harnessVersion, String model, String effort, Form form, Phase phase, String stepId,
String envelopeId, PredictedCost predictedCost, YieldReason yieldReason, Outcome outcome, AttemptEnd attemptEnd,
QueueTaskRecord.ContinuationForm continuation, CauseClass causeClass, SpendClass spendClass, Population population,
HarnessUsage usage, Long durationMs, DurationBasis durationBasis, Long queuedMs, String consultationOf,
Instant recordedAt) {

    /** The kind of every row this record writes. */
    public static final String KIND = "task";

    private static final Pattern QUEUE_TASK_ID_PATTERN = Pattern.compile("qt-[0-9a-f]{16}");
    private static final Pattern JOB_ID_PATTERN = Pattern.compile("l-[0-9a-f]{16}");
    private static final Pattern ROLE_PATTERN = Pattern.compile("[a-z][a-z0-9-]{1,31}");
    private static final Pattern HARNESS_PATTERN = Pattern.compile("[a-z][a-z0-9-]{1,31}");
    private static final Pattern ENVELOPE_ID_PATTERN = Pattern.compile("envelope-\\d+");
    private static final int MAX_HARNESS_VERSION = 64;
    private static final int MAX_MODEL = 128;

    /** The form of the worker that produced the row. */
    public enum Form {
        /** A supervised worker that takes task after task. */
        WARM("warm"),
        /** One worker per task. */
        FRESH("fresh");

        private final String wireName;

        Form(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a store */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a store
         * @return the value of that name; empty if no value has it
         */
        public static Optional<Form> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /** The phase of a plan the rows of a metrics store are grouped by. */
    public enum Phase {
        /** The init phase. */
        PHASE_1_INIT("phase-1-init"),
        /** The refine phase. */
        PHASE_2_REFINE("phase-2-refine"),
        /** The outline phase. */
        PHASE_3_OUTLINE("phase-3-outline"),
        /** The plan phase. */
        PHASE_4_PLAN("phase-4-plan"),
        /** The execute phase. */
        PHASE_5_EXECUTE("phase-5-execute"),
        /** The finalize phase. */
        PHASE_6_FINALIZE("phase-6-finalize");

        private final String wireName;

        Phase(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a store */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a store
         * @return the value of that name; empty if no value has it
         */
        public static Optional<Phase> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /** What the task's entity recorded. */
    public enum Outcome {
        /** The required submission was recorded. */
        SUBMITTED("submitted"),
        /** The task asked a question. */
        ASKED("asked"),
        /** The task reported itself infeasible with evidence. */
        INFEASIBLE("infeasible"),
        /** The attempt was offered again. */
        LOST("lost");

        private final String wireName;

        Outcome(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a store */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a store
         * @return the value of that name; empty if no value has it
         */
        public static Optional<Outcome> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /** The cause class of the task, a total function over its outcome and its worker's fate. */
    public enum CauseClass {
        /** The required submission, a question or an infeasibility without a finding. */
        BENIGN("benign"),
        /** The task filed at least one finding. */
        RETURNED_WITH_FINDINGS("returned_with_findings"),
        /** The task ended lost and was offered again. */
        RETRYABLE("retryable"),
        /** The worker ended timed out without the submission. */
        STALL("stall"),
        /** The task failed, was killed, cancelled or indeterminate without the submission. */
        TERMINAL_ERROR("terminal_error"),
        /** The task was closed without an attempt, or its launch was refused. */
        REFUSED("refused");

        private final String wireName;

        CauseClass(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a store */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a store
         * @return the value of that name; empty if no value has it
         */
        public static Optional<CauseClass> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /** What the tokens of the task were spent on. */
    public enum SpendClass {
        /** The entity recorded its required submission. */
        SUBMITTED("submitted"),
        /** The task produced findings only. */
        FINDINGS_ONLY("findings_only"),
        /** The task produced nothing. */
        NO_PRODUCT("no_product"),
        /** What the task produced cannot be read. */
        UNMEASURED("unmeasured");

        private final String wireName;

        SpendClass(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a store */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a store
         * @return the value of that name; empty if no value has it
         */
        public static Optional<SpendClass> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /** The population the figures count. */
    public enum Population {
        /** A fresh worker's whole run. */
        MODEL_JOB("model_job"),
        /** One task a warm worker served. */
        MODEL_TASK("model_task");

        private final String wireName;

        Population(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a store */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a store
         * @return the value of that name; empty if no value has it
         */
        public static Optional<Population> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /** What the measured duration covers. */
    public enum DurationBasis {
        /** Spawn to exit of a fresh worker's job. */
        PROCESS("process"),
        /** Offer to the attempt's end for a warm worker. */
        TASK("task");

        private final String wireName;

        DurationBasis(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a store */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a store
         * @return the value of that name; empty if no value has it
         */
        public static Optional<DurationBasis> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /** The early-end exit of an envelope task. */
    public enum YieldReason {
        /** The envelope's chain ran to its tail. */
        CHAIN_TAIL("chain_tail"),
        /** The task asked a question. */
        QUESTION("question"),
        /** The context was exhausted. */
        CONTEXT("context"),
        /** The server took the yield on an off-track signal. */
        OFF_TRACK("off_track");

        private final String wireName;

        YieldReason(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a store */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a store
         * @return the value of that name; empty if no value has it
         */
        public static Optional<YieldReason> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * The summed predicted cost of an envelope task's envelope.
     *
     * @param tokens the summed {@code predicted_cost_tokens}
     * @param basis  whether the figure was calibrated
     * @since 0.1
     */
    public record PredictedCost(long tokens, Basis basis) {

        /** The basis of a predicted cost. */
        public enum Basis {
            /** A figure before any calibration. */
            UNCALIBRATED("uncalibrated"),
            /** A calibrated figure. */
            CALIBRATED("calibrated");

            private final String wireName;

            Basis(String wireName) {
                this.wireName = wireName;
            }

            /** @return the name of the value in a store */
            public String wireName() {
                return wireName;
            }

            /**
             * @param wireName the name of a value in a store
             * @return the value of that name; empty if no value has it
             */
            public static Optional<Basis> fromWireName(String wireName) {
                return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
            }
        }

        /**
         * @throws NullPointerException     if the basis is {@code null}
         * @throws IllegalArgumentException if the tokens are negative
         */
        public PredictedCost {
            Objects.requireNonNull(basis, "basis");
            if (tokens < 0) {
                throw new IllegalArgumentException("'predicted_cost.tokens' must not be negative: " + tokens);
            }
        }
    }

    /**
     * @throws NullPointerException     if a required component is {@code null}
     * @throws IllegalArgumentException if a value breaks its form or the combination breaks a rule of the schema
     */
    public MetricsRow {
        requirePattern(queueTaskId, QUEUE_TASK_ID_PATTERN, "queue_task_id");
        requirePattern(role, ROLE_PATTERN, "role");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(causeClass, "cause_class");
        Objects.requireNonNull(spendClass, "spend_class");
        Objects.requireNonNull(population, "population");
        Objects.requireNonNull(usage, "usage");
        Objects.requireNonNull(recordedAt, "recorded_at");
        requireAttempt(attempt, attemptEnd, causeClass);
        requireRefusedColumns(causeClass, jobId, generationId, harness, harnessVersion, model, form, durationBasis);
        requireOptionalPattern(jobId, JOB_ID_PATTERN, "job_id");
        requireOptionalPattern(generationId, JOB_ID_PATTERN, "generation_id");
        requireOptionalPattern(harness, HARNESS_PATTERN, "harness");
        requireOptionalPattern(stepId, null, "step_id");
        requireOptionalPattern(envelopeId, ENVELOPE_ID_PATTERN, "envelope_id");
        requireOptionalPattern(consultationOf, QUEUE_TASK_ID_PATTERN, "consultation_of");
        requireLength(harnessVersion, MAX_HARNESS_VERSION, "harness_version");
        requireLength(model, MAX_MODEL, "model");
        requireNonNegative(durationMs, "duration_ms");
        requireNonNegative(queuedMs, "queued_ms");
    }

    /** @return the total tokens of the row, empty where the store records {@code unmeasured} */
    public OptionalLong totalTokens() {
        return usage.totalTokens();
    }

    /** @return why a figure of the row is unmeasured, empty when every applicable figure is measured */
    public Optional<UnmeasuredReason> unmeasuredReason() {
        return usage.unmeasuredReason();
    }

    /**
     * @param queueTaskId    the queue task
     * @param role           the role
     * @param phase          the phase, or {@code null} outside a plan
     * @param outcome        the outcome of the task
     * @param spendClass     what it spent its tokens on
     * @param population     the population
     * @param recordedAt     the instant of the record
     * @return the row of a task closed without an attempt: every usage column unmeasured, no attempt and no worker
     */
    public static MetricsRow refused(String queueTaskId, String role, Phase phase, Outcome outcome,
            SpendClass spendClass, Population population, Instant recordedAt) {
        return MetricsRow.builder().queueTaskId(queueTaskId).role(role).phase(phase).outcome(outcome)
                .causeClass(CauseClass.REFUSED).spendClass(spendClass).population(population).usage(HarnessUsage.none())
                .recordedAt(recordedAt).build();
    }

    private static void requireAttempt(Integer attempt, AttemptEnd attemptEnd, CauseClass causeClass) {
        var refused = causeClass == CauseClass.REFUSED;
        if (refused != (attempt == null) || refused != (attemptEnd == null)) {
            throw new IllegalArgumentException(
                    "'attempt' and 'attempt_end' are null exactly for a task closed without an attempt");
        }
        if (attempt != null && attempt < 1) {
            throw new IllegalArgumentException("'attempt' must be at least 1: " + attempt);
        }
    }

    private static void requireRefusedColumns(CauseClass causeClass, String jobId, String generationId,
            String harness, String harnessVersion, String model, Form form, DurationBasis durationBasis) {
        if (causeClass != CauseClass.REFUSED) {
            if (jobId == null || generationId == null || harness == null || harnessVersion == null || model == null
                    || form == null || durationBasis == null) {
                throw new IllegalArgumentException(
                        "A row of an attempt carries its worker, harness, harness version, model, form and duration "
                                + "basis");
            }
        } else if (jobId != null || generationId != null || harness != null || harnessVersion != null || model != null
                || form != null || durationBasis != null) {
            throw new IllegalArgumentException(
                    "A task closed without an attempt carries no worker, harness, harness version, model, form or "
                            + "duration basis");
        }
    }

    private static void requirePattern(String value, Pattern pattern, String name) {
        Objects.requireNonNull(value, name);
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException("'%s' is not an identifier: %s".formatted(name, value));
        }
    }

    private static void requireOptionalPattern(String value, Pattern pattern, String name) {
        if (value == null) {
            return;
        }
        if (pattern == null) {
            if (value.isBlank()) {
                throw new IllegalArgumentException("'%s' must not be blank".formatted(name));
            }
            return;
        }
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException("'%s' is not an identifier: %s".formatted(name, value));
        }
    }

    private static void requireLength(String value, int max, String name) {
        if (value != null && value.length() > max) {
            throw new IllegalArgumentException("'%s' is longer than %d characters".formatted(name, max));
        }
    }

    private static void requireNonNegative(Long value, String name) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException("'%s' must not be negative: %d".formatted(name, value));
        }
    }
}
