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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import lombok.Builder;
import lombok.With;

/**
 * The status document {@code mcp/status.json} of one plan, epic or workspace scope (PM-ARCH-6: the persisted state
 * machine position; PM-WF-16: the persisted call stack; PM-ARCH-4: every run state lives in the store).
 * <p>
 * The members the workflow engine owns are typed: the position ({@code scope_type}, {@code scope_id}, {@code phase},
 * {@code state}, {@code call_stack}), the loop-back accounting ({@code loop_back_iteration},
 * {@code outline_loop_backs}, {@code policy_counters}), the retry counts ({@code retry_counters}), the bindings of the
 * current state ({@code active_job}, {@code active_jobs}, {@code active_tasks}), the condition overlay
 * ({@code pending_condition}) and {@code updated_at}. Every other member of the closed schema is kept as the raw JSON
 * text it was read with ({@link #retained()}) and written back unchanged, so the engine never rewrites what another
 * component owns; a later component types a member by moving it from the retained set to the typed set, without a
 * format change.
 * <p>
 * The record is immutable; the engine updates it through its {@code with…} copies. Its closed values (the phase, the
 * job kinds and states, the task states and outcomes, the condition kinds, the policy classes) are checked when it is
 * built, so a copy with a value outside the schema cannot be written.
 *
 * @param scopeType          the type of the scope: plan, epic or workspace
 * @param scopeId            the plan or epic id; {@code null} for the workspace scope
 * @param phase              the phase of the state
 * @param state              the fully qualified identifier of the current state
 * @param callStack          the active sub-workflow call frames, bottom to top, at most {@value #MAX_CALL_DEPTH}
 * @param loopBackIteration  the loop-back iterations begun; never reset
 * @param outlineLoopBacks   the {@code loop_back_outline} transitions taken; plan scopes only, 0 otherwise
 * @param policyCounters     the consumed budgets per remediation policy class; plan scopes only, empty otherwise
 * @param retryCounters      the attempts of the server-rule retries and the condition budgets outside a condition
 * @param activeJob          the binding of the current job, composite job or task state; {@code null} when the
 *                           current state is none of them
 * @param activeJobs         the jobs of the current job state by name; empty when there is none
 * @param activeTasks        the queue tasks of the current task state by entity; empty when there is none
 * @param pendingCondition   the condition overlay of the current state; {@code null} when there is none
 * @param updatedAt          the instant of the last write
 * @param retained           every other member of the schema by name, as raw JSON text in its read order
 * @since 0.1
 */
@With
@Builder(toBuilder = true)
public record WorkflowStatus(
JournalScopeType scopeType,
String scopeId,
String phase,
String state,
List<CallFrame> callStack,
long loopBackIteration,
long outlineLoopBacks,
Map<String, PolicyCounter> policyCounters,
RetryCounters retryCounters,
ActiveJob activeJob,
Map<String, JobEntry> activeJobs,
Map<String, TaskEntry> activeTasks,
PendingCondition pendingCondition,
Instant updatedAt,
Map<String, String> retained) {

    /** The deepest call stack the schema admits. */
    public static final int MAX_CALL_DEPTH = 4;

    /** The phases of the schema. */
    public static final Set<String> PHASES = Set.of("phase-1-init", "phase-2-refine", "phase-3-outline",
            "phase-4-plan", "phase-5-execute", "phase-6-finalize", "completed", "aborted", "epic", "workspace");

    /** The remediation policy classes that key {@code policy_counters}. */
    public static final Set<String> POLICY_CLASSES = Set.of("ci-failure", "review-findings", "build-failure",
            "merge-conflict", "qgate-findings", "analysis");

    private static final Pattern SCOPE_ID = Pattern.compile("^[a-z0-9][a-z0-9-]{1,62}[a-z0-9]$");
    private static final Pattern SCOPE_JOB_ID = Pattern.compile("^[bcgp]-[0-9a-f]{16}$");
    private static final Pattern BOUND_JOB_ID = Pattern
            .compile("^(?:[bcgp]-[0-9a-f]{16}|[a-z][a-z0-9_-]{0,31}+(?:\\.[a-z][a-z0-9_-]{0,31}+)*+)$");
    private static final Pattern QUEUE_TASK_ID = Pattern.compile("^qt-[0-9a-f]{16}$");
    private static final Pattern ROLE = Pattern.compile("^[a-z][a-z0-9-]{1,31}$");
    private static final Pattern REPLAN_ID = Pattern.compile("^RP-\\d+$");
    private static final Pattern FINGERPRINT = Pattern.compile("^[0-9a-f]{64}$");
    private static final Set<String> JOB_KINDS = Set.of("build", "git", "ci", "project_script");
    private static final Set<String> JOB_STATUSES = Set.of("queued", "running", "succeeded", "failed",
            "failed_unparsed", "refused", "cancelled", "timed_out", "killed", "indeterminate");
    private static final Set<String> TASK_STATES = Set.of("queued", "leased", "ended");
    private static final Set<String> TASK_OUTCOMES = Set.of("submitted", "asked", "infeasible", "lost");
    private static final Set<String> TASK_REASONS = Set.of("attempts_spent", "role_undefined", "class_mismatch",
            "role_unqualified", "role_unavailable", "deadline_expired", "withdrawn");
    private static final String INTEGRITY_BREACH = "integrity_breach";
    private static final Set<String> CONDITION_KINDS = Set.of(INTEGRITY_BREACH, "footprint_violation",
            "job_incomplete", "config_drift", "guard_indeterminate", "precondition_unmet");
    private static final Set<String> INTEGRITY_KINDS = Set.of("store_tampered", "enrolment_tampered");
    private static final Set<String> RETRY_REASONS = Set.of("flaky", "infra", "timeout");

    /**
     * @throws NullPointerException     if a required component is {@code null}
     * @throws IllegalArgumentException if a value lies outside the schema: the lesson store as scope type, a scope id
     *                                  missing for a plan or epic or present for the workspace, a phase outside the
     *                                  schema, a blank state, more than {@value #MAX_CALL_DEPTH} frames, a negative
     *                                  counter, loop-back accounting outside a plan scope, an unknown policy class, or
     *                                  a binding of another state than the current one
     */
    public WorkflowStatus {
        Objects.requireNonNull(scopeType, "scopeType");
        if (scopeType == JournalScopeType.LESSONS) {
            throw new IllegalArgumentException("The lesson store has no status document");
        }
        if (scopeType.scopeIdRequired()) {
            if (scopeId == null) {
                throw new IllegalArgumentException(
                        "'scope_id' must be set for a %s scope".formatted(scopeType.wireName()));
            }
            requireMatch(SCOPE_ID, scopeId, "scope_id");
        } else if (scopeId != null) {
            throw new IllegalArgumentException("'scope_id' must be null for the workspace scope: " + scopeId);
        }
        requireOneOf(PHASES, phase, "phase");
        requireNonBlank(state, "state");
        callStack = List.copyOf(Objects.requireNonNull(callStack, "callStack"));
        if (callStack.size() > MAX_CALL_DEPTH) {
            throw new IllegalArgumentException("'call_stack' has %d frames, at most %d are allowed"
                    .formatted(callStack.size(), MAX_CALL_DEPTH));
        }
        requireNotNegative(loopBackIteration, "loop_back_iteration");
        requireNotNegative(outlineLoopBacks, "outline_loop_backs");
        policyCounters = orderedCopy(policyCounters, "policyCounters",
                (policyClass, counter) -> requireOneOf(POLICY_CLASSES, policyClass, "policy_counters key"));
        if (scopeType != JournalScopeType.PLAN && (outlineLoopBacks != 0 || !policyCounters.isEmpty())) {
            throw new IllegalArgumentException(
                    "'outline_loop_backs' and 'policy_counters' are kept for plan scopes only");
        }
        Objects.requireNonNull(retryCounters, "retryCounters");
        if (activeJob != null && !activeJob.stateId().equals(state)) {
            throw new IllegalArgumentException("'active_job' is bound to '%s', not to the current state '%s'"
                    .formatted(activeJob.stateId(), state));
        }
        activeJobs = orderedCopy(activeJobs, "activeJobs", (name, job) -> requireNonBlank(name, "active_jobs key"));
        activeTasks = orderedCopy(activeTasks, "activeTasks",
                (entity, task) -> requireNonBlank(entity, "active_tasks key"));
        Objects.requireNonNull(updatedAt, "updatedAt");
        retained = orderedCopy(retained, "retained", (member, raw) -> requireNonBlank(raw, member));
    }

    /** @return the binding of the current state to its job; empty when the current state is no job or task state */
    public Optional<ActiveJob> activeJobBinding() {
        return Optional.ofNullable(activeJob);
    }

    /** @return the condition overlay of the current state; empty when there is none */
    public Optional<PendingCondition> condition() {
        return Optional.ofNullable(pendingCondition);
    }

    /**
     * One active sub-workflow call frame.
     *
     * @param callerWorkflowId the workflow that called
     * @param callerStateId    the calling state, whose transition the return selects
     * @param callerPhase      the phase of the calling state
     * @param subworkflowId    the called sub-workflow
     * @param entry            the entry of the called sub-workflow the call went through (PM-WF-16); for a phase
     *                         sub-workflow the point at which the phase is entered
     * @param boundArgs        the arguments bound at the push, by parameter name, in their order
     * @param pushedAt         the instant of the push
     * @since 0.1
     */
    public record CallFrame(String callerWorkflowId, String callerStateId, String callerPhase, String subworkflowId,
    String entry, Map<String, BoundArg> boundArgs, Instant pushedAt) {

        /**
         * @throws NullPointerException     if a component, a parameter name or an argument is {@code null}
         * @throws IllegalArgumentException if an identifier or the entry is blank
         */
        public CallFrame {
            requireNonBlank(callerWorkflowId, "caller_workflow_id");
            requireNonBlank(callerStateId, "caller_state_id");
            requireNonBlank(callerPhase, "caller_phase");
            requireNonBlank(subworkflowId, "subworkflow_id");
            requireNonBlank(entry, "entry");
            boundArgs = orderedCopy(boundArgs, "boundArgs", (name, arg) -> requireNonBlank(name, "bound_args key"));
            Objects.requireNonNull(pushedAt, "pushedAt");
        }
    }

    /**
     * A call argument bound at a push: a string, an integer, or a set of strings.
     *
     * @since 0.1
     */
    public sealed interface BoundArg permits BoundArg.StringArg, BoundArg.IntegerArg, BoundArg.StringSetArg {

        /**
         * A string argument.
         *
         * @param value the value
         * @since 0.1
         */
        record StringArg(String value) implements BoundArg {

            /** @throws NullPointerException if the value is {@code null} */
            public StringArg {
                Objects.requireNonNull(value, "value");
            }
        }

        /**
         * An integer argument.
         *
         * @param value the value
         * @since 0.1
         */
        record IntegerArg(long value) implements BoundArg {
        }

        /**
         * A set of strings, in the order it was bound.
         *
         * @param values the strings, each once
         * @since 0.1
         */
        record StringSetArg(SequencedSet<String> values) implements BoundArg {

            /**
             * @throws NullPointerException if the set or a string is {@code null}
             */
            public StringSetArg {
                Objects.requireNonNull(values, "values");
                var copy = new LinkedHashSet<String>();
                values.forEach(value -> copy.add(Objects.requireNonNull(value, "value")));
                values = Collections.unmodifiableSequencedSet(copy);
            }
        }
    }

    /**
     * The binding of a job, composite job or task state to its job: the {@code job_id} of a job state, the state's
     * own identifier for a composite job or task state.
     *
     * @param stateId the bound state, the current state
     * @param jobId   the bound job id or the state identifier
     * @since 0.1
     */
    public record ActiveJob(String stateId, String jobId) {

        /**
         * @throws NullPointerException     if a component is {@code null}
         * @throws IllegalArgumentException if the state is blank or the job id has neither shape
         */
        public ActiveJob {
            requireNonBlank(stateId, "state_id");
            requireMatch(BOUND_JOB_ID, jobId, "active_job.job_id");
        }
    }

    /**
     * A job of the current job state.
     *
     * @param jobId     the job, {@code ^[bcgp]-[0-9a-f]{16}$}
     * @param kind      {@code build}, {@code git}, {@code ci} or {@code project_script}
     * @param status    the job record's status while queued or running, else its fate
     * @param startedAt the instant the job was started
     * @since 0.1
     */
    public record JobEntry(String jobId, String kind, String status, Instant startedAt) {

        /**
         * @throws NullPointerException     if a component is {@code null}
         * @throws IllegalArgumentException if a value lies outside the schema
         */
        public JobEntry {
            requireMatch(SCOPE_JOB_ID, jobId, "active_jobs.job_id");
            requireOneOf(JOB_KINDS, kind, "active_jobs.kind");
            requireOneOf(JOB_STATUSES, status, "active_jobs.status");
            Objects.requireNonNull(startedAt, "startedAt");
        }
    }

    /**
     * A queue task of the current task state.
     *
     * @param queueTaskId the queue task, {@code ^qt-[0-9a-f]{16}$}
     * @param role        the role of the task
     * @param state       {@code queued}, {@code leased} or {@code ended}
     * @param outcome     the closed task outcome; {@code null} until the task ended
     * @param reason      why a {@code lost} task ended without a result; {@code null} otherwise
     * @since 0.1
     */
    public record TaskEntry(String queueTaskId, String role, String state, String outcome, String reason) {

        /**
         * @throws NullPointerException     if a required component is {@code null}
         * @throws IllegalArgumentException if a value lies outside the schema
         */
        public TaskEntry {
            requireMatch(QUEUE_TASK_ID, queueTaskId, "active_tasks.queue_task_id");
            requireMatch(ROLE, role, "active_tasks.role");
            requireOneOf(TASK_STATES, state, "active_tasks.state");
            if (outcome != null) {
                requireOneOf(TASK_OUTCOMES, outcome, "active_tasks.outcome");
            }
            if (reason != null) {
                requireOneOf(TASK_REASONS, reason, "active_tasks.reason");
            }
        }
    }

    /**
     * The condition overlay of the current state; while it is set, state, phase and call stack are unchanged.
     *
     * @param kind           the condition kind
     * @param remedyAttempts the server retries of this condition instance
     * @param integrity      the breached store; required for {@code integrity_breach}, {@code null} otherwise
     * @param facts          the facts of the condition, a JSON object as raw text, kept unchanged
     * @param raisedAt       the instant the condition was raised
     * @since 0.1
     */
    public record PendingCondition(String kind, long remedyAttempts, Integrity integrity, String facts,
    Instant raisedAt) {

        /**
         * @throws NullPointerException     if a required component is {@code null}
         * @throws IllegalArgumentException if the kind lies outside the schema, the attempts are negative, the
         *                                  integrity is missing for {@code integrity_breach} or present for another
         *                                  kind, or the facts are no JSON object
         */
        public PendingCondition {
            requireOneOf(CONDITION_KINDS, kind, "pending_condition.kind");
            requireNotNegative(remedyAttempts, "pending_condition.remedy_attempts");
            if (INTEGRITY_BREACH.equals(kind) != (integrity != null)) {
                throw new IllegalArgumentException(
                        "'pending_condition.integrity' is required for integrity_breach and only for it");
            }
            Objects.requireNonNull(facts, "facts");
            if (!facts.startsWith("{") || !facts.endsWith("}")) {
                throw new IllegalArgumentException("'pending_condition.facts' must be a JSON object: " + facts);
            }
            Objects.requireNonNull(raisedAt, "raisedAt");
        }
    }

    /**
     * The breached store of an {@code integrity_breach} condition.
     *
     * @param kind           {@code store_tampered} or {@code enrolment_tampered}
     * @param store          the relative store path, or the enrolment file of the project
     * @param recordedDigest the recorded digest
     * @param currentDigest  the current digest; {@code null} when the file is missing
     * @since 0.1
     */
    public record Integrity(String kind, String store, String recordedDigest, String currentDigest) {

        /**
         * @throws NullPointerException     if a required component is {@code null}
         * @throws IllegalArgumentException if the kind lies outside the schema
         */
        public Integrity {
            requireOneOf(INTEGRITY_KINDS, kind, "integrity.kind");
            Objects.requireNonNull(store, "store");
            Objects.requireNonNull(recordedDigest, "recordedDigest");
        }
    }

    /**
     * The consumed budgets of one remediation policy class, never reset by re-entering its unit.
     *
     * @param retryUsed   the retries charged
     * @param fixUsed     the fixes charged
     * @param renewedFrom the counter values at the last re-planning that re-entered execution; {@code null} before
     *                    any renewal
     * @param lastRetry   the last retry and the failure fingerprints it answered; {@code null} before the first
     * @since 0.1
     */
    public record PolicyCounter(long retryUsed, long fixUsed, Renewal renewedFrom, LastRetry lastRetry) {

        /** @throws IllegalArgumentException if a count is negative */
        public PolicyCounter {
            requireNotNegative(retryUsed, "retry_used");
            requireNotNegative(fixUsed, "fix_used");
        }
    }

    /**
     * The counter values of a policy class at a re-planning that re-entered execution.
     *
     * @param retryUsed the retries charged then
     * @param fixUsed   the fixes charged then
     * @param replanId  the re-planning, {@code ^RP-[0-9]+$}
     * @since 0.1
     */
    public record Renewal(long retryUsed, long fixUsed, String replanId) {

        /**
         * @throws NullPointerException     if the re-planning is {@code null}
         * @throws IllegalArgumentException if a count is negative or the re-planning id has another shape
         */
        public Renewal {
            requireNotNegative(retryUsed, "renewed_from.retry_used");
            requireNotNegative(fixUsed, "renewed_from.fix_used");
            requireMatch(REPLAN_ID, replanId, "renewed_from.replan_id");
        }
    }

    /**
     * The last retry of a policy class.
     *
     * @param reason       {@code flaky}, {@code infra} or {@code timeout}
     * @param fingerprints the failure fingerprints it answered, each 64 hexadecimal digits
     * @since 0.1
     */
    public record LastRetry(String reason, List<String> fingerprints) {

        /**
         * @throws NullPointerException     if a component or a fingerprint is {@code null}
         * @throws IllegalArgumentException if the reason lies outside the schema or a fingerprint has another shape
         */
        public LastRetry {
            requireOneOf(RETRY_REASONS, reason, "last_retry.reason");
            fingerprints = List.copyOf(Objects.requireNonNull(fingerprints, "fingerprints"));
            fingerprints.forEach(fingerprint -> requireMatch(FINGERPRINT, fingerprint, "last_retry.fingerprints"));
        }
    }

    private static <V> Map<String, V> orderedCopy(Map<String, V> map, String name, BiConsumer<String, V> check) {
        Objects.requireNonNull(map, name);
        var copy = new LinkedHashMap<String, V>();
        map.forEach((key, value) -> {
            Objects.requireNonNull(key, name + " key");
            Objects.requireNonNull(value, name + " value");
            check.accept(key, value);
            copy.put(key, value);
        });
        return Collections.unmodifiableMap(copy);
    }

    private static void requireOneOf(Set<String> allowed, String value, String name) {
        Objects.requireNonNull(value, name);
        if (!allowed.contains(value)) {
            throw new IllegalArgumentException("'%s' has the undeclared value '%s'".formatted(name, value));
        }
    }

    private static void requireMatch(Pattern pattern, String value, String name) {
        Objects.requireNonNull(value, name);
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException("'%s' does not match %s: %s".formatted(name, pattern, value));
        }
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException("'%s' must not be blank".formatted(name));
        }
    }

    private static void requireNotNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException("'%s' must be at least 0: %d".formatted(name, value));
        }
    }
}
