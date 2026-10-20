/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.scenario;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import de.planmarshall.core.store.JournalScopeType;
import de.planmarshall.workflow.engine.EngineEvent;

/**
 * One step of a scenario script of the replay harness {@code pm-scenario} (PM-TEST-4): a simulated actor action or a
 * deterministic assertion, each with the line of the script it was read from. The directives are those the workflow
 * engine supports today; the values they carry are validated against the closed sets {@link EngineEvent} states, so a
 * script that names a fate or a task outcome the engine does not know is refused when it is read, not when it runs.
 */
sealed interface ScenarioDirective {

    /** @return the line of the script the directive was read from, counted from 1 */
    int line();

    /**
     * Starts the scope: the runner calls the engine's start for the scope type and the scope id.
     *
     * @param line    the line of the script
     * @param scope   the scope type
     * @param scopeId the scope id, {@code null} exactly when the scope type takes none
     */
    record Start(int line, JournalScopeType scope, String scopeId) implements ScenarioDirective {

        /**
         * @throws NullPointerException     if the scope type is {@code null}
         * @throws IllegalArgumentException if the line is not positive, or the scope id is missing, blank or given
         *                                  against what the scope type requires
         */
        public Start {
            requireLine(line);
            Objects.requireNonNull(scope, "scope");
            if (scope.scopeIdRequired()) {
                if (scopeId == null) {
                    throw new IllegalArgumentException("a %s scope takes a scope id".formatted(scope.wireName()));
                }
                requireToken(scopeId, "scope id");
            } else if (scopeId != null) {
                throw new IllegalArgumentException("a %s scope takes no scope id".formatted(scope.wireName()));
            }
        }
    }

    /**
     * Asserts the state the stored status rests in.
     *
     * @param line    the line of the script
     * @param stateId the expected state id
     */
    record AssertState(int line, String stateId) implements ScenarioDirective {

        /** @throws IllegalArgumentException if the line is not positive or the state id is no token */
        public AssertState {
            requireLine(line);
            requireToken(stateId, "state id");
        }
    }

    /**
     * Asserts the outcome the run ended with.
     *
     * @param line    the line of the script
     * @param outcome the expected terminal outcome
     */
    record AssertOutcome(int line, String outcome) implements ScenarioDirective {

        /** @throws IllegalArgumentException if the line is not positive or the outcome is no token */
        public AssertOutcome {
            requireLine(line);
            requireToken(outcome, "outcome");
        }
    }

    /**
     * Settles a job with its fate.
     *
     * @param line  the line of the script
     * @param jobId the job, {@code ^[bcgp]-[0-9a-f]{16}$}
     * @param fate  a fate of {@link EngineEvent.JobSettled#FATES}
     */
    record CompleteJob(int line, String jobId, String fate) implements ScenarioDirective {

        /**
         * @throws IllegalArgumentException if the line is not positive, the job id has another shape or the fate is
         *                                  no fate
         */
        public CompleteJob {
            requireLine(line);
            Objects.requireNonNull(jobId, "jobId");
            if (!JOB_ID.matcher(jobId).matches()) {
                throw new IllegalArgumentException("job id does not match ^[bcgp]-[0-9a-f]{16}$: " + jobId);
            }
            Objects.requireNonNull(fate, "fate");
            if (!EngineEvent.JobSettled.FATES.contains(fate)) {
                throw new IllegalArgumentException("'%s' is no fate of a job".formatted(fate));
            }
        }
    }

    /**
     * Answers a question record on a named channel.
     *
     * @param line       the line of the script
     * @param questionId the question, {@code ^Q-[0-9]{3,6}$}
     * @param channel    the channel the answer arrives on, such as {@code web}
     * @param outcome    the branch key the answer chooses, which the question state routes
     */
    record AnswerQuestion(int line, String questionId, String channel, String outcome) implements ScenarioDirective {

        /**
         * @throws IllegalArgumentException if the line is not positive, the question id has another shape or a value
         *                                  is no token
         */
        public AnswerQuestion {
            requireLine(line);
            requireQuestionId(questionId);
            requireToken(channel, "channel");
            requireToken(outcome, "branch key");
        }
    }

    /**
     * Asserts the closed outcome the last task of a role ended with.
     *
     * @param line    the line of the script
     * @param role    the role whose scripted worker settled the task
     * @param outcome a task outcome of {@link EngineEvent.TasksSettled#TASK_OUTCOMES}
     */
    record AssertTaskOutcome(int line, String role, String outcome) implements ScenarioDirective {

        /**
         * @throws IllegalArgumentException if the line is not positive, the role is no token or the outcome is not
         *                                  closed
         */
        public AssertTaskOutcome {
            requireLine(line);
            requireToken(role, "role");
            requireTaskOutcome(outcome);
        }
    }

    /**
     * The declaration of the scripted worker of one role: the task outcomes it delivers, one per task it is given, in
     * order. A declaration is not a step; the runner hands its outcomes to the worker of the role.
     *
     * @param line     the line of the script
     * @param role     the role
     * @param outcomes the task outcomes, each of {@link EngineEvent.TasksSettled#TASK_OUTCOMES}; copied
     */
    record Worker(int line, String role, List<String> outcomes) {

        /**
         * @throws IllegalArgumentException if the line is not positive, the role is no token, or no or an unclosed
         *                                  outcome is named
         */
        public Worker {
            requireLine(line);
            requireToken(role, "role");
            outcomes = List.copyOf(outcomes);
            if (outcomes.isEmpty()) {
                throw new IllegalArgumentException("the worker of role %s delivers no task outcome".formatted(role));
            }
            outcomes.forEach(ScenarioDirective::requireTaskOutcome);
        }
    }

    /** The shape of a job id, as {@link EngineEvent.JobSettled} states it. */
    Pattern JOB_ID = Pattern.compile("^[bcgp]-[0-9a-f]{16}$");

    /** The shape of a question id, as {@link EngineEvent.QuestionResolved} states it. */
    Pattern QUESTION_ID = Pattern.compile("^Q-\\d{3,6}$");

    /** A token of the script: no blank, no whitespace. */
    Pattern TOKEN = Pattern.compile("^\\S+$");

    private static void requireLine(int line) {
        if (line < 1) {
            throw new IllegalArgumentException("a line is counted from 1: " + line);
        }
    }

    private static void requireToken(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!TOKEN.matcher(value).matches()) {
            throw new IllegalArgumentException("%s is no token: '%s'".formatted(name, value));
        }
    }

    private static void requireQuestionId(String questionId) {
        Objects.requireNonNull(questionId, "questionId");
        if (!QUESTION_ID.matcher(questionId).matches()) {
            throw new IllegalArgumentException("question id does not match ^Q-[0-9]{3,6}$: " + questionId);
        }
    }

    private static void requireTaskOutcome(String outcome) {
        Objects.requireNonNull(outcome, "outcome");
        if (!EngineEvent.TasksSettled.TASK_OUTCOMES.contains(outcome)) {
            throw new IllegalArgumentException("'%s' is no outcome of a task".formatted(outcome));
        }
    }
}
