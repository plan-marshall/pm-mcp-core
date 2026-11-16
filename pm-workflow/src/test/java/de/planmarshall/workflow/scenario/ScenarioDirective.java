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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import de.planmarshall.core.id.RoleName;
import de.planmarshall.core.store.JournalScopeType;
import de.planmarshall.workflow.engine.EngineEvent;
import de.planmarshall.workflow.task.TaskRefusal;
import org.jspecify.annotations.Nullable;

/**
 * One step of a scenario script of the replay harness {@code pm-scenario} (PM-TEST-4): a simulated actor action or a
 * deterministic assertion, each with the line of the script it was read from. The directives are those the workflow
 * engine and the task protocol support today; the values they carry are validated against the closed sets
 * {@link EngineEvent} states, {@link TaskRefusal} and the states of a worker's task, so a script that names a fate, a
 * task outcome or a refusal the engine does not know is refused when it is read, not when it runs.
 */
// Every datum in this class is a contract: the wire shapes, closed sets and tokens of the scenario script.
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
     * An operator act at an operator wait: the operator follows one offered link of the resting state, on one of the two
     * operator channels.
     *
     * @param line       the line of the script
     * @param link       the link the operator follows, as the state offers it
     * @param channel    the operator's channel, {@code web} or {@code cli}
     * @param parameters the free parameters of the link, empty for a link that takes none; copied
     */
    record OperatorDo(int line, String link, String channel, Map<String, String> parameters) implements ScenarioDirective {

        /** The operator's channels: the web app and the terminal client. */
        static final List<String> CHANNELS = List.of("web", "cli");

        /**
         * @throws IllegalArgumentException if the line is not positive, the link is no token, the channel is neither
         *                                  {@code web} nor {@code cli}, or a parameter is no token
         */
        public OperatorDo {
            requireLine(line);
            requireToken(link, "link");
            Objects.requireNonNull(channel, "channel");
            if (!CHANNELS.contains(channel)) {
                throw new IllegalArgumentException("'%s' is no operator channel: web or cli".formatted(channel));
            }
            Objects.requireNonNull(parameters, "parameters");
            parameters.forEach((name, value) -> {
                requireToken(name, "parameter name");
                Objects.requireNonNull(value, name);
            });
            parameters = Map.copyOf(parameters);
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

    /**
     * A wait of the scripted protocol worker of one role: the role's generation calls the task protocol's
     * {@code wait} and is offered its task. A role declared with this directive is a protocol worker; its calls come
     * with {@code worker_do}, and no role uses this form and {@link Worker} together.
     *
     * @param line the line of the script
     * @param role the role
     */
    record WorkerWait(int line, String role) implements ScenarioDirective {

        /** @throws IllegalArgumentException if the line is not positive or the role is no role name */
        public WorkerWait {
            requireLine(line);
            requireRole(role);
        }
    }

    /**
     * A call of a scripted protocol worker on its leased task entity: the acknowledgement, the submit with its kind, the
     * infeasible report, or the consultation of another role.
     *
     * @param line the line of the script
     * @param call the call: its target worker and the action with the scalar members of its JSON-shaped argument
     */
    record WorkerDo(int line, Call call) implements ScenarioDirective {

        /**
         * @throws IllegalArgumentException if the line is not positive or the call is {@code null}
         */
        public WorkerDo {
            requireLine(line);
            Objects.requireNonNull(call, "call");
        }
    }

    /**
     * The call of a {@link WorkerDo}: its target and its action.
     *
     * @param role      the role of the calling worker, or {@code null} for the worker of the script's most recent
     *                  {@link WorkerWait}, which is the form the specification's example writes
     * @param action    the call
     * @param arguments the scalar members of the JSON-shaped argument, empty for a call that takes none; copied
     */
    record Call(@Nullable String role, Action action, Map<String, String> arguments) {

        /**
         * @throws IllegalArgumentException if the role is neither {@code null} nor a role name, the action is
         *                                  {@code null}, an argument name is no token, or a call that requires an
         *                                  argument does not name it
         */
        public Call {
            if (role != null) {
                requireRole(role);
            }
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(arguments, "arguments");
            arguments.forEach((name, value) -> {
                requireToken(name, "argument name");
                Objects.requireNonNull(value, name);
            });
            arguments = Map.copyOf(arguments);
            if (action instanceof Action.Submit submit && "answer".equals(submit.kind())
                    && !arguments.containsKey("answer")) {
                throw new IllegalArgumentException("missing argument: submit:answer takes {\"answer\":\"…\"}");
            }
            if (action instanceof Action.Consult consult && !arguments.containsKey("question")) {
                throw new IllegalArgumentException("missing argument: consult:%s takes {\"question\":\"…\"}"
                        .formatted(consult.role()));
            }
        }
    }

    /**
     * The call a {@link WorkerDo} makes: the task protocol's acknowledgement, submit and infeasible report, and the
     * consultation of another role through the shipped consultation service.
     */
    sealed interface Action {

        /**
         * Acknowledges the offer through the {@code ack} link.
         */
        record Ack() implements Action {
        }

        /**
         * Closes the task with the outcome {@code submitted}, through the {@code submit:<kind>} link of its entity.
         *
         * @param kind the kind the link names: the entity kind of a work task, {@code answer} for a consultation task
         */
        record Submit(String kind) implements Action {

            /** @throws IllegalArgumentException if the kind is no token */
            public Submit {
                requireToken(kind, "submit kind");
            }
        }

        /**
         * Closes the task with the outcome {@code infeasible}, through the entity's {@code infeasible} link.
         */
        record Infeasible() implements Action {
        }

        /**
         * Follows the {@code consult:<role>} link: the task is consulted by the role and waits for the answer.
         *
         * @param role the consulted role
         */
        record Consult(String role) implements Action {

            /** @throws IllegalArgumentException if the role is no role name */
            public Consult {
                requireRole(role);
            }
        }
    }

    /**
     * A worker fault of the supervision scenarios (Model Work § Supervision &amp; Recycling, Detection): the role's
     * protocol worker is lost between its offer and its acknowledgement, and the attempt ends with the fact the
     * acknowledgement deadline reports.
     *
     * @param line the line of the script
     * @param role the role whose protocol worker is lost
     */
    record KillWorker(int line, String role) implements ScenarioDirective {

        /** @throws IllegalArgumentException if the line is not positive or the role is no role name */
        public KillWorker {
            requireLine(line);
            requireRole(role);
        }
    }

    /**
     * A worker fault: the role's protocol worker ends its turn without a submit, which is the exit the supervisor
     * observes after the worker's first call.
     *
     * @param line the line of the script
     * @param role the role whose protocol worker ends its turn
     */
    record EndTurn(int line, String role) implements ScenarioDirective {

        /** @throws IllegalArgumentException if the line is not positive or the role is no role name */
        public EndTurn {
            requireLine(line);
            requireRole(role);
        }
    }

    /**
     * A worker fault: the relay of the role's protocol worker is killed under the wait the acknowledged worker holds.
     *
     * @param line the line of the script
     * @param role the role whose relay is killed
     */
    record KillRelay(int line, String role) implements ScenarioDirective {

        /** @throws IllegalArgumentException if the line is not positive or the role is no role name */
        public KillRelay {
            requireLine(line);
            requireRole(role);
        }
    }

    /**
     * A worker fault: the role's protocol worker makes no call for the given time, and is lost when that time reaches
     * the limit its {@link de.planmarshall.core.profile.WorkerParameters} compile for it.
     *
     * @param line    the line of the script
     * @param role    the role whose protocol worker stalls
     * @param seconds the time the worker makes no call
     */
    record StallWorker(int line, String role, int seconds) implements ScenarioDirective {

        /**
         * @throws IllegalArgumentException if the line is not positive, the role is no role name or the time is not
         *                                  positive
         */
        public StallWorker {
            requireLine(line);
            requireRole(role);
            if (seconds < 1) {
                throw new IllegalArgumentException("a stall is counted from 1 second: " + seconds);
            }
        }
    }

    /**
     * The runtime restart of the supervision scenarios: the engine, the task protocol and the stores' objects are
     * rebuilt over the same directory of the replay, and the scenario continues.
     *
     * @param line the line of the script
     */
    record RestartRuntime(int line) implements ScenarioDirective {

        /** @throws IllegalArgumentException if the line is not positive */
        public RestartRuntime {
            requireLine(line);
        }
    }

    /**
     * Asserts the state of the role's scripted worker's task, read from the queue record.
     *
     * @param line  the line of the script
     * @param role  the role whose protocol worker holds the task
     * @param state one of {@link #WORKER_STATES}: {@code offered}, {@code acknowledged}, {@code closed}, {@code queued}
     *              or {@code fenced}
     */
    record AssertWorkerState(int line, String role, String state) implements ScenarioDirective {

        /**
         * @throws IllegalArgumentException if the line is not positive, the role is no role name or the state is not
         *                                  one of the states a worker's task takes
         */
        public AssertWorkerState {
            requireLine(line);
            requireRole(role);
            Objects.requireNonNull(state, "state");
            if (!WORKER_STATES.contains(state)) {
                throw new IllegalArgumentException("'%s' is no state of a worker's task: %s"
                        .formatted(state, WORKER_STATES));
            }
        }
    }

    /**
     * Asserts that the last {@code worker_do} of a replaced or closed task was refused with the reason: the wire name of
     * one constant of {@link TaskRefusal}, which is the closed set of the assertion.
     *
     * @param line   the line of the script
     * @param reason the refusal, a wire name of {@link TaskRefusal}
     */
    record AssertRefused(int line, String reason) implements ScenarioDirective {

        /**
         * @throws IllegalArgumentException if the line is not positive or the reason is no refusal of the task protocol
         */
        public AssertRefused {
            requireLine(line);
            Objects.requireNonNull(reason, "reason");
            if (!REFUSALS.contains(reason)) {
                throw new IllegalArgumentException("'%s' is no refusal of a task: %s".formatted(reason, REFUSALS));
            }
        }
    }

    /** The states of the task of a protocol worker. */
    List<String> WORKER_STATES = List.of("offered", "acknowledged", "closed", "queued", "fenced");

    /** The refusals {@code assert_refused} accepts: the wire names of {@link TaskRefusal}, derived from the enum. */
    List<String> REFUSALS = Arrays.stream(TaskRefusal.values()).map(TaskRefusal::wireName).toList();

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

    private static void requireRole(String role) {
        Objects.requireNonNull(role, "role");
        if (!RoleName.matches(role)) {
            throw new IllegalArgumentException("role is no role name: '%s'".formatted(role));
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
