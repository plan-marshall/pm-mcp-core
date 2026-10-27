/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.diagram;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.workflow.ast.GuardExpr;
import de.planmarshall.workflow.ast.LinkRelation;
import de.planmarshall.workflow.ast.LinkSpec;
import de.planmarshall.workflow.ast.OutcomeTransition;
import de.planmarshall.workflow.ast.ResultGuard;
import de.planmarshall.workflow.ast.StateBehavior;
import de.planmarshall.workflow.ast.StateNode;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.ast.WorkflowScope;

/**
 * The edges of a unit and their label parts, shared by the three renderers so that the order of the edges and the
 * wording of their labels are defined once (PM-IMPL-5).
 * <p>
 * The outgoing edges of a state are, in declaration order, the transitions the engine takes on its own (those of
 * {@code auto}, {@code job}, {@code composite_job}, {@code job llm}, {@code call} and the resolution outcomes of a
 * {@code question}) followed by the transitions of its declared links (of a link state and of a question state). The
 * incoming edges of a state are those edges of every state that target it, in the declaration order of their source
 * states. Nothing is sorted: the output of every renderer follows the declaration order of the unit.
 *
 * @since 0.1
 */
final class StateTransitions {

    /** The trigger of the resolution outcomes of a question state. */
    private static final String QUESTION_TRIGGER = "question";

    /** The identifier of the implicit step out of a link state of a sub-workflow. */
    private static final String STEP_OUT = "step_out";

    /** The prefix of the implicit consultation link of a {@code consult role} clause. */
    private static final String CONSULT_PREFIX = "consult:";

    /** The binding strength of {@code or}, the weakest operator. */
    private static final int PRECEDENCE_OR = 1;
    /** The binding strength of {@code and}. */
    private static final int PRECEDENCE_AND = 2;
    /** The binding strength of {@code not}. */
    private static final int PRECEDENCE_NOT = 3;
    /** The context of the operand of {@code not}: only a single guard binds that tightly. */
    private static final int PRECEDENCE_NOT_OPERAND = 4;

    private final Map<String, List<Edge>> outgoing;
    private final Map<String, List<Edge>> incoming;

    private StateTransitions(Map<String, List<Edge>> outgoing, Map<String, List<Edge>> incoming) {
        this.outgoing = outgoing;
        this.incoming = incoming;
    }

    /**
     * Collects the edges of a unit.
     *
     * @param definition the unit
     * @return its edges by source and by target state
     */
    static StateTransitions of(WorkflowDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        var outgoing = new LinkedHashMap<String, List<Edge>>();
        var incoming = new LinkedHashMap<String, List<Edge>>();
        for (var state : definition.states().values()) {
            var edges = new ArrayList<Edge>();
            engineTransitions(state.behavior())
                    .forEach(transition -> edges.add(new Edge(state, Optional.empty(), transition)));
            for (var link : links(state.behavior())) {
                link.transitions().forEach(transition -> edges.add(new Edge(state, Optional.of(link), transition)));
            }
            outgoing.put(state.id(), List.copyOf(edges));
            edges.forEach(edge -> incoming.computeIfAbsent(edge.target(), id -> new ArrayList<>()).add(edge));
        }
        incoming.replaceAll((id, edges) -> List.copyOf(edges));
        return new StateTransitions(Collections.unmodifiableMap(outgoing), Collections.unmodifiableMap(incoming));
    }

    /**
     * @param stateId a state of the unit
     * @return its outgoing edges in declaration order; empty for a terminal or an unknown state
     */
    List<Edge> outgoing(String stateId) {
        return outgoing.getOrDefault(stateId, List.of());
    }

    /**
     * @param stateId a state of the unit
     * @return the edges that target it, in the declaration order of their source states
     */
    List<Edge> incoming(String stateId) {
        return incoming.getOrDefault(stateId, List.of());
    }

    /**
     * @param behavior what a state does
     * @return the links the state declares; empty for every behaviour but link and question states
     */
    static List<LinkSpec> links(StateBehavior behavior) {
        return switch (behavior) {
            case StateBehavior.InteractiveLinks(var links) -> links;
            case StateBehavior.AutoStep _, StateBehavior.JobStep _, StateBehavior.ModelJob _,
                StateBehavior.QuestionWait _, StateBehavior.SubWorkflowCall _, StateBehavior.TerminalState _ ->
                List.of();
        };
    }

    /**
     * @param behavior what a state does
     * @return the transitions the engine takes on its own, outside of any declared link; empty for link and terminal
     *         states
     */
    static List<OutcomeTransition> engineTransitions(StateBehavior behavior) {
        return switch (behavior) {
            case StateBehavior.AutoStep(var _, var transitions) -> transitions;
            case StateBehavior.JobStep(var _, var _, var transitions) -> transitions;
            case StateBehavior.ModelJob job -> job.transitions();
            case StateBehavior.SubWorkflowCall(var _, var _, var _, var transitions) -> transitions;
            case StateBehavior.QuestionWait(var _, var transitions) -> transitions;
            case StateBehavior.InteractiveLinks _, StateBehavior.TerminalState _ -> List.of();
        };
    }

    /**
     * Lists the links a state offers without declaring them: the {@code step_out} of a link state of a sub-workflow,
     * and one {@code consult:<role>} per {@code consult role} clause of a task state. The links the runtime adds per
     * record ({@code ack} of a task, {@code answer:<question_id>} of a question) belong to no state and are not
     * listed.
     *
     * @param scope the scope of the unit
     * @param state a state of the unit
     * @return the implicit links of the state in source order
     */
    static List<ImplicitLink> implicitLinks(WorkflowScope scope, StateNode state) {
        return switch (state.behavior()) {
            case StateBehavior.InteractiveLinks _ -> scope == WorkflowScope.SUBWORKFLOW
                    ? List.of(new ImplicitLink(LinkRelation.ABORT, STEP_OUT))
                    : List.of();
            case StateBehavior.ModelJob job -> job.consultRoles().stream()
                    .map(role -> new ImplicitLink(LinkRelation.CONSULT, CONSULT_PREFIX + role)).toList();
            case StateBehavior.AutoStep _, StateBehavior.JobStep _, StateBehavior.SubWorkflowCall _,
                StateBehavior.QuestionWait _, StateBehavior.TerminalState _ -> List.of();
        };
    }

    /**
     * Describes a state as {@code <kind> <target>}: the primitive of an {@code auto} or job state, the called
     * sub-workflow, the question class, or the outcome of a terminal state; a link state is described by its kind
     * alone, and a task state by its task label (see {@link #taskLabel(StateBehavior.ModelJob)}).
     *
     * @param state a state
     * @return its description
     */
    static String description(StateNode state) {
        var kind = StateKind.of(state.behavior()).label();
        return switch (state.behavior()) {
            case StateBehavior.InteractiveLinks _ -> kind;
            case StateBehavior.AutoStep(var primitiveId, var _) -> kind + " " + primitiveId;
            case StateBehavior.JobStep(var primitiveId, var _, var _) -> kind + " " + primitiveId;
            case StateBehavior.ModelJob job -> taskLabel(job);
            case StateBehavior.SubWorkflowCall(var subWorkflowId, var _, var _, var _) -> kind + " " + subWorkflowId;
            case StateBehavior.QuestionWait(var questionClass, var _) -> kind + " " + questionClass;
            case StateBehavior.TerminalState _ -> kind + state.terminalOutcome().map(outcome -> " " + outcome.id())
                    .orElse("");
        };
    }

    /**
     * Labels the tasks of a task state: {@code task <role>}, {@code task <role> each <primitive>} for a fan-out, or
     * {@code task role each <primitive>} when a primitive returns the roles.
     *
     * @param job a task state's behaviour
     * @return its task label
     */
    static String taskLabel(StateBehavior.ModelJob job) {
        var label = new StringBuilder("task");
        job.roleName().ifPresent(role -> label.append(' ').append(role));
        job.fanOutPrimitiveId().ifPresent(primitive -> label.append(" each ").append(primitive));
        job.rolesPrimitiveId().ifPresent(primitive -> label.append(" role each ").append(primitive));
        return label.toString();
    }

    /**
     * Names what fires the transitions the engine takes on its own: the kind of an {@code auto}, job or call state,
     * {@code question} for the resolution outcomes of a question state, and the task label of a task state.
     *
     * @param behavior what a state does
     * @return the trigger of its engine transitions
     */
    static String engineTrigger(StateBehavior behavior) {
        return switch (behavior) {
            case StateBehavior.ModelJob job -> taskLabel(job);
            case StateBehavior.QuestionWait _ -> QUESTION_TRIGGER;
            case StateBehavior.InteractiveLinks _, StateBehavior.AutoStep _, StateBehavior.JobStep _,
                StateBehavior.SubWorkflowCall _, StateBehavior.TerminalState _ -> StateKind.of(behavior).label();
        };
    }

    /**
     * Prints a guard expression as the workflow language writes it, with exactly the parentheses its precedence
     * ({@code not} before {@code and} before {@code or}, both operators left-associative) needs.
     *
     * @param expr the guard expression
     * @return its source form
     */
    static String guard(GuardExpr expr) {
        return guard(expr, PRECEDENCE_OR);
    }

    /**
     * @param guard the result guard of a call-site transition
     * @return its source form, {@code result.<field>} or {@code not result.<field>}
     */
    static String resultGuard(ResultGuard guard) {
        return (guard.negated() ? "not " : "") + "result." + guard.field();
    }

    /**
     * @param value a constant of the tree
     * @return its lower-case name, as the workflow language writes it
     */
    static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static String guard(GuardExpr expr, int context) {
        return switch (expr) {
            case GuardExpr.InWorktree _ -> "in_worktree";
            case GuardExpr.HasOpenFindings _ -> "has_open_findings";
            case GuardExpr.AllTasksCompleted _ -> "all_tasks_completed";
            case GuardExpr.CustomPredicate(var primitiveId) -> "predicate:" + primitiveId;
            case GuardExpr.Or(var left, var right) -> parenthesise(guard(left, PRECEDENCE_OR) + " or "
                    + guard(right, PRECEDENCE_AND), PRECEDENCE_OR, context);
            case GuardExpr.And(var left, var right) -> parenthesise(guard(left, PRECEDENCE_AND) + " and "
                    + guard(right, PRECEDENCE_NOT), PRECEDENCE_AND, context);
            case GuardExpr.Not(var operand) -> parenthesise("not " + guard(operand, PRECEDENCE_NOT_OPERAND),
                    PRECEDENCE_NOT, context);
        };
    }

    private static String parenthesise(String text, int precedence, int context) {
        return precedence < context ? "(" + text + ")" : text;
    }

    /**
     * One transition of a state: an engine transition, or a transition of one of its declared links.
     *
     * @param source     the state the transition leaves
     * @param link       the declared link whose action the transition routes; empty for an engine transition
     * @param transition the transition
     */
    record Edge(StateNode source, Optional<LinkSpec> link, OutcomeTransition transition) {

        /**
         * @return the outcome the transition is taken on
         */
        String outcome() {
            return transition.outcomeName();
        }

        /**
         * @return the identifier of the target state
         */
        String target() {
            return transition.targetStateId();
        }

        /**
         * @return the source form of the guard: of the link for a link transition, of the result guard for an engine
         *         transition; empty when there is none
         */
        Optional<String> guard() {
            if (link.isPresent()) {
                return link.orElseThrow().guard().map(spec -> StateTransitions.guard(spec.expr()));
            }
            return transition.resultGuard().map(StateTransitions::resultGuard);
        }
    }

    /**
     * A link a state offers without declaring it.
     *
     * @param rel the relation of the link
     * @param id  the identifier of the link
     */
    record ImplicitLink(LinkRelation rel, String id) {
    }
}
