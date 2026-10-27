/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.engine;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * An event that resumes a resting state (PM-WF-2: waiting on a job is an engine event, never a link). The engine
 * accepts an event only when it matches what the resting state waits for ({@link RestingWait}); the event names the
 * settled thing, and the engine routes its outcome at the state.
 *
 * @since 0.1
 */
public sealed interface EngineEvent
        permits EngineEvent.JobSettled, EngineEvent.TasksSettled, EngineEvent.QuestionResolved,
        EngineEvent.OperatorAct, EngineEvent.TimeElapsed {

    /**
     * A job ended with its fate, which the job state routes as its outcome.
     *
     * @param jobId the job, {@code ^[bcgp]-[0-9a-f]{16}$}
     * @param fate  the fate of the job record: {@code succeeded}, {@code failed}, {@code failed_unparsed},
     *              {@code refused}, {@code cancelled}, {@code timed_out}, {@code killed} or {@code indeterminate}
     * @since 0.1
     */
    record JobSettled(String jobId, String fate) implements EngineEvent {

        private static final Pattern JOB_ID = Pattern.compile("^[bcgp]-[0-9a-f]{16}$");

        /** The fates a job record ends with. */
        public static final Set<String> FATES = Set.of("succeeded", "failed", "failed_unparsed", "refused",
                "cancelled", "timed_out", "killed", "indeterminate");

        /**
         * @throws NullPointerException     if a component is {@code null}
         * @throws IllegalArgumentException if the job id has another shape or the fate is no fate
         */
        public JobSettled {
            Objects.requireNonNull(jobId, "jobId");
            if (!JOB_ID.matcher(jobId).matches()) {
                throw new IllegalArgumentException("job_id does not match ^[bcgp]-[0-9a-f]{16}$: " + jobId);
            }
            Objects.requireNonNull(fate, "fate");
            if (!FATES.contains(fate)) {
                throw new IllegalArgumentException("'%s' is no fate of a job".formatted(fate));
            }
        }
    }

    /**
     * Every task of a task state ended; the return evaluation of the state's entity kind ran over them.
     *
     * @param taskOutcomes      the closed outcome of each task by entity: {@code submitted}, {@code asked},
     *                          {@code infeasible} or {@code lost}; copied in their order
     * @param evaluatedOutcome  the outcome the state takes: an outcome of the return-evaluation primitive, or
     *                          {@code lost}
     * @since 0.1
     */
    record TasksSettled(Map<String, String> taskOutcomes, String evaluatedOutcome) implements EngineEvent {

        /** The closed outcomes of a task. */
        public static final Set<String> TASK_OUTCOMES = Set.of("submitted", "asked", "infeasible", "lost");

        /**
         * @throws NullPointerException     if a component, an entity or an outcome is {@code null}
         * @throws IllegalArgumentException if no task is named, a task outcome is not closed, or the evaluated
         *                                  outcome is blank
         */
        public TasksSettled {
            Objects.requireNonNull(taskOutcomes, "taskOutcomes");
            var copy = new LinkedHashMap<String, String>();
            taskOutcomes.forEach((entity, outcome) -> {
                Objects.requireNonNull(entity, "entity");
                Objects.requireNonNull(outcome, "outcome");
                if (!TASK_OUTCOMES.contains(outcome)) {
                    throw new IllegalArgumentException("'%s' is no outcome of a task".formatted(outcome));
                }
                copy.put(entity, outcome);
            });
            if (copy.isEmpty()) {
                throw new IllegalArgumentException("a settlement names at least one task");
            }
            taskOutcomes = Collections.unmodifiableMap(copy);
            requireNonBlank(evaluatedOutcome, "evaluatedOutcome");
        }
    }

    /**
     * A question record was resolved: an answer from either client, a withdrawal by the server, or the end of its
     * scope.
     *
     * @param questionId the question, {@code ^Q-[0-9]{3,6}$}
     * @param outcome    the closed resolution outcome of its class
     * @since 0.1
     */
    record QuestionResolved(String questionId, String outcome) implements EngineEvent {

        private static final Pattern QUESTION_ID = Pattern.compile("^Q-\\d{3,6}$");

        /**
         * @throws NullPointerException     if a component is {@code null}
         * @throws IllegalArgumentException if the question id has another shape or the outcome is blank
         */
        public QuestionResolved {
            Objects.requireNonNull(questionId, "questionId");
            if (!QUESTION_ID.matcher(questionId).matches()) {
                throw new IllegalArgumentException("question_id does not match ^Q-[0-9]{3,6}$: " + questionId);
            }
            requireNonBlank(outcome, "outcome");
        }
    }

    /**
     * The operator acted through one of the links of an operator wait: the state runs the link's primitive and routes
     * its outcome.
     *
     * @param stateId    the operator-wait state the act was made in
     * @param linkId     the identifier pattern of the link the operator followed
     * @param parameters the operator's submission for the link's free parameters, by name; empty for a link without one
     * @since 0.1
     */
    record OperatorAct(String stateId, String linkId, Map<String, String> parameters) implements EngineEvent {

        /**
         * @throws NullPointerException     if a component is {@code null}
         * @throws IllegalArgumentException if a component is blank
         */
        public OperatorAct {
            requireNonBlank(stateId, "stateId");
            requireNonBlank(linkId, "linkId");
            Objects.requireNonNull(parameters, "parameters");
            parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
        }

        /**
         * @param stateId the operator-wait state the act was made in
         * @param linkId  the identifier pattern of the link the operator followed
         */
        public OperatorAct(String stateId, String linkId) {
            this(stateId, linkId, Map.of());
        }
    }

    /**
     * Time passed: the event of a composite job state that waits for time, such as a retry backoff.
     *
     * @param at the instant the time passed at
     * @since 0.1
     */
    record TimeElapsed(Instant at) implements EngineEvent {

        /** @throws NullPointerException if the instant is {@code null} */
        public TimeElapsed {
            Objects.requireNonNull(at, "at");
        }
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
