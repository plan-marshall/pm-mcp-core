/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.verify;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import de.planmarshall.core.spi.CycleMeasure;
import de.planmarshall.core.spi.OutcomeClass;
import de.planmarshall.workflow.ast.LinkSpec;
import de.planmarshall.workflow.ast.OutcomeTransition;
import de.planmarshall.workflow.ast.ParamSpec;
import de.planmarshall.workflow.ast.StateBehavior;
import de.planmarshall.workflow.ast.StateNode;
import de.planmarshall.workflow.ast.StepCompletion;
import de.planmarshall.workflow.ast.UnitFrontmatter;
import de.planmarshall.workflow.verify.CompiledInvariants.Check;
import de.planmarshall.workflow.verify.CompiledInvariants.Context;

/**
 * The unit-level invariants of the project-owned sub-workflows that are not remediation units (PM-IMPL-5, PM-WF-16):
 * {@code INV-ADJ-1}, {@code INV-REPLAN-1} and {@code INV-GIT-1}.
 * <ul>
 * <li>{@link InvariantId#INV_ADJ_1}: the states of {@code adjudicate.decisions}, with their primitives, transitions
 * and terminals, equal those of the default unit, source locations aside; its declarations and decision declarations
 * lie within their bounds ({@link DeclarationBounds}). The quorum checks against the effective role set run at
 * composition.</li>
 * <li>{@link InvariantId#INV_REPLAN_1}: the states of {@code replan} equal those of the default unit except the role
 * of a {@code job llm} state; its declarations lie within their bounds; and it has no question state and no task state
 * that declares {@code ask}. That the role is {@code read_only} is checked at composition.</li>
 * <li>{@link InvariantId#INV_GIT_1}: every state that lies on every path of the default unit into its success
 * terminals (the six stages) is a state of the unit and lies on every such path of the unit, in the default unit's
 * order; its exit contract, the exits its frontmatter declares plus {@code stepped_out}, is {@code done} and
 * {@code stepped_out}, and its terminals are exactly {@code done} of class {@code success} and, where declared,
 * {@code stepped_out} of class {@code stepped_out}, and the result schema is the default unit's; an outcome {@code push_state_unknown} ends
 * in a terminal of class {@code stepped_out}; every cycle through a task state passes a state whose primitive declares
 * an attempt cap; the unit has no question state, no link state and no task state the default unit does not have (no
 * override state); and it pushes only with {@code git.execute-push} and {@code git.push-with-lease}, the lease push
 * entered only from the states the default unit enters it from.</li>
 * </ul>
 *
 * @since 0.1
 */
final class SubWorkflowInvariants {

    /** The success outcome of the commit sub-workflow. */
    static final String DONE = "done";

    /** The outcome of the commit sub-workflow when the remote state is unknown. */
    static final String PUSH_STATE_UNKNOWN = "push_state_unknown";

    /** The namespace of the git primitives. */
    static final String GIT_NAMESPACE = "git.";

    /** The lease push of the plan's own branch after a server rebase. */
    static final String LEASE_PUSH = "git.push-with-lease";

    private SubWorkflowInvariants() {
    }

    static Map<InvariantId, Check> checks() {
        return Map.of(
                InvariantId.INV_ADJ_1, SubWorkflowInvariants::adjudication,
                InvariantId.INV_REPLAN_1, SubWorkflowInvariants::replanning,
                InvariantId.INV_GIT_1, SubWorkflowInvariants::commitAndPush);
    }

    private static void adjudication(Context context) {
        var id = InvariantId.INV_ADJ_1;
        requireEqualStates(context, id, false);
        reportDeclarations(context, id);
    }

    private static void replanning(Context context) {
        var id = InvariantId.INV_REPLAN_1;
        requireEqualStates(context, id, true);
        reportDeclarations(context, id);
        for (var state : context.states()) {
            if (state.behavior() instanceof StateBehavior.QuestionWait) {
                context.reportState(id, state, "the re-planning unit has the question state '%s'".formatted(state.id()));
            }
            if (state.behavior() instanceof StateBehavior.ModelJob modelJob && modelJob.ask()) {
                context.reportState(id, state, "the re-planning task state '%s' declares ask".formatted(state.id()));
            }
        }
    }

    private static void reportDeclarations(Context context, InvariantId id) {
        context.found().addAll(CompiledInvariants.declarationViolations(id, context.unit()));
    }

    /**
     * Requires the states of the unit to equal those of the reference, source locations aside.
     *
     * @param context    the unit under check
     * @param id         the invariant the check belongs to
     * @param ignoreRole whether the role of a {@code job llm} state may differ
     */
    private static void requireEqualStates(Context context, InvariantId id, boolean ignoreRole) {
        for (var counterpart : context.reference().states().values()) {
            if (context.state(counterpart.id()).isEmpty()) {
                context.reportUnit(id, "the state '%s' of the default unit is missing".formatted(counterpart.id()));
            }
        }
        for (var state : context.states()) {
            var counterpart = context.referenceState(state.id());
            if (counterpart.isEmpty()) {
                context.reportState(id, state, "the state '%s' is not a state of the default unit".formatted(state.id()));
            } else if (!shape(state, ignoreRole).equals(shape(counterpart.get(), ignoreRole))) {
                context.reportState(id, state, "the state '%s' differs from the state of the default unit"
                        .formatted(state.id()));
            }
        }
    }

    /**
     * @return the state without source locations, and with the role of a {@code job llm} state removed when asked
     */
    private static StateNode shape(StateNode state, boolean ignoreRole) {
        var behavior = switch (state.behavior()) {
            case StateBehavior.InteractiveLinks links -> new StateBehavior.InteractiveLinks(
                    links.links().stream().map(SubWorkflowInvariants::shape).toList());
            case StateBehavior.AutoStep auto -> new StateBehavior.AutoStep(auto.primitiveId(), shape(auto.transitions()));
            case StateBehavior.JobStep job -> new StateBehavior.JobStep(job.primitiveId(), job.composite(),
                    shape(job.transitions()));
            case StateBehavior.ModelJob job -> new StateBehavior.ModelJob(job.entityKind(),
                    ignoreRole ? Optional.empty() : job.roleName(), job.fanOutPrimitiveId(), job.rolesPrimitiveId(),
                    job.ask(), job.consultRoles(), shape(job.transitions()));
            case StateBehavior.SubWorkflowCall call -> new StateBehavior.SubWorkflowCall(call.subWorkflowId(),
                    call.entry(), call.paramBindings(), shape(call.transitions()));
            case StateBehavior.QuestionWait question -> new StateBehavior.QuestionWait(question.questionClass(),
                    shape(question.transitions()), question.links().stream().map(SubWorkflowInvariants::shape).toList());
            case StateBehavior.TerminalState terminal -> terminal;
        };
        return new StateNode(state.id(), state.phase(), state.terminalOutcome(), state.skillPath(),
                state.factsPrimitiveId(), behavior, Context.nowhere());
    }

    private static LinkSpec shape(LinkSpec link) {
        return new LinkSpec(link.rel(), link.idPattern(), link.form(),
                link.freeParams().stream().map(param -> new ParamSpec(param.name(), param.type(), param.optional(),
                        Context.nowhere())).toList(),
                link.guard(), link.action(), shape(link.transitions()), Context.nowhere());
    }

    private static List<OutcomeTransition> shape(List<OutcomeTransition> transitions) {
        return transitions.stream().map(transition -> new OutcomeTransition(transition.outcomeName(),
                transition.targetStateId(), transition.resultGuard(),
                transition.completions().stream().map(completion -> new StepCompletion(completion.stepId(),
                        completion.status(), Context.nowhere())).toList(),
                Context.nowhere())).toList();
    }

    private static void commitAndPush(Context context) {
        var id = InvariantId.INV_GIT_1;
        stageOrder(context, id);
        exitContract(context, id);
        cappedModelLoops(context, id);
        for (var state : context.states()) {
            switch (state.behavior()) {
                case StateBehavior.QuestionWait _, StateBehavior.InteractiveLinks _ -> context.reportState(id, state,
                        "the state '%s' waits for an override decision".formatted(state.id()));
                case StateBehavior.ModelJob _ when context.referenceState(state.id())
                        .filter(counterpart -> counterpart.behavior() instanceof StateBehavior.ModelJob).isEmpty() ->
                    context.reportState(id, state, "the task state '%s' is not a task state of the default unit"
                            .formatted(state.id()));
                default -> {
                    // the stages are checked by their order and the push primitives below
                }
            }
            Context.primitiveId(state)
                    .filter(primitive -> primitive.startsWith(GIT_NAMESPACE) && primitive.contains("push")
                            && !FinalizeInvariants.PUSH_PRIMITIVES.contains(primitive))
                    .ifPresent(primitive -> context.reportState(id, state, "the state '%s' pushes with '%s'"
                            .formatted(state.id(), primitive)));
        }
        leasePushEntries(context, id);
    }

    private static void stageOrder(Context context, InvariantId id) {
        var stages = context.requireDominatorsOf(id, state -> Context.isTerminalOfClass(state, OutcomeClass.SUCCESS),
                state -> !Context.isTerminal(state));
        for (var earlier : stages) {
            for (var later : stages) {
                if (earlier.equals(later) || !context.referenceDominates(earlier, later)
                        || context.state(earlier).isEmpty() || context.state(later).isEmpty()) {
                    continue;
                }
                if (!context.dominates(earlier, later)) {
                    context.reportState(id, context.state(later).orElseThrow(),
                            "the stage '%s' runs on a path that has not passed the stage '%s'".formatted(later, earlier));
                }
            }
        }
    }

    /**
     * The exit contract of the commit sub-workflow: the exits its frontmatter declares plus the implicit control exit
     * {@code stepped_out}, never derived from its terminals. The declared exits are exactly {@code done}; a terminal
     * reports {@code done} of class {@code success} or {@code stepped_out} of class {@code stepped_out}; and a terminal
     * {@code done} of class {@code success} exists.
     */
    private static void exitContract(Context context, InvariantId id) {
        var unit = context.unit();
        var exits = unit.frontmatter().exits().stream().map(UnitFrontmatter.InterfaceElement::name).toList();
        var contract = new LinkedHashSet<>(exits);
        contract.add(CompiledInvariants.STEPPED_OUT);
        if (!exits.equals(List.of(DONE))) {
            context.reportUnit(id, "the unit declares the exits %s; the exit contract of the default unit is 'done'"
                    .formatted(exits));
        }
        var hasDone = false;
        for (var state : context.states()) {
            if (!Context.isTerminal(state)) {
                continue;
            }
            var outcome = state.terminalOutcome();
            var inContract = outcome.filter(terminal -> contract.contains(terminal.id())).isPresent();
            var isDone = inContract && outcome.filter(terminal -> DONE.equals(terminal.id())
                    && terminal.outcomeClass() == OutcomeClass.SUCCESS).isPresent();
            var isSteppedOut = inContract && outcome.filter(terminal -> CompiledInvariants.STEPPED_OUT
                    .equals(terminal.id()) && terminal.outcomeClass() == OutcomeClass.STEPPED_OUT).isPresent();
            hasDone |= isDone;
            if (!isDone && !isSteppedOut) {
                context.reportState(id, state, "the terminal '%s' is outside the exit contract %s"
                        .formatted(state.id(), contract));
            }
        }
        if (!hasDone) {
            context.reportUnit(id, "the unit has no terminal 'done' of class SUCCESS");
        }
        if (!unit.resultSchema().equals(context.reference().resultSchema())) {
            context.reportUnit(id, "the result schema %s differs from the default unit's %s".formatted(
                    unit.resultSchema().orElse("none"), context.reference().resultSchema().orElse("none")));
        }
        for (var edge : Context.edges(unit)) {
            if (!PUSH_STATE_UNKNOWN.equals(edge.transition().outcomeName())) {
                continue;
            }
            var stepsOut = context.state(edge.transition().targetStateId())
                    .filter(target -> Context.isTerminalOfClass(target, OutcomeClass.STEPPED_OUT)).isPresent();
            if (!stepsOut) {
                context.reportTransition(id, edge.source(), edge.transition(),
                        "the outcome '%s' enters '%s', which is no terminal of class STEPPED_OUT".formatted(
                                PUSH_STATE_UNKNOWN, edge.transition().targetStateId()));
            }
        }
    }

    private static void cappedModelLoops(Context context, InvariantId id) {
        var unit = context.unit();
        var reported = new HashSet<String>();
        for (var cycle : UnitGraph.findSimpleCycles(unit)) {
            var states = cycle.stream().map(unit.states()::get).filter(Objects::nonNull).toList();
            var taskState = states.stream().filter(state -> state.behavior() instanceof StateBehavior.ModelJob)
                    .findFirst();
            if (taskState.isEmpty()) {
                continue;
            }
            var capped = states.stream().anyMatch(state -> context.primitive(state)
                    .filter(primitive -> primitive.cycleMeasure() == CycleMeasure.ATTEMPT_CAP).isPresent());
            if (!capped && reported.add(taskState.get().id())) {
                context.reportState(id, taskState.get(), "the cycle %s through the task state '%s' has no attempt cap"
                        .formatted(cycle, taskState.get().id()));
            }
        }
    }

    private static void leasePushEntries(Context context, InvariantId id) {
        var referenceEntries = new HashSet<String>();
        for (var state : context.reference().states().values()) {
            if (Context.primitiveId(state).filter(LEASE_PUSH::equals).isPresent()) {
                referenceEntries.addAll(context.referenceGraph().predecessors(state.id()));
            }
        }
        for (var state : context.states()) {
            if (Context.primitiveId(state).filter(LEASE_PUSH::equals).isEmpty()) {
                continue;
            }
            Set<String> entries = context.graph().predecessors(state.id());
            entries.stream().filter(entry -> !referenceEntries.contains(entry))
                    .forEach(entry -> context.reportState(id, state,
                            "the lease push '%s' is entered from '%s', which does not enter it in the default unit"
                                    .formatted(state.id(), entry)));
        }
    }
}
