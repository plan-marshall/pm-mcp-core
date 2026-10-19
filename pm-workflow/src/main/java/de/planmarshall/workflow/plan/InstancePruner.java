/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.plan;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import de.planmarshall.workflow.ast.CallValue;
import de.planmarshall.workflow.ast.LinkSpec;
import de.planmarshall.workflow.ast.OutcomeTransition;
import de.planmarshall.workflow.ast.StateBehavior;
import de.planmarshall.workflow.ast.StateNode;
import de.planmarshall.workflow.ast.WorkflowDefinition;

/**
 * Applies the effects of the composition rules, the bound axis variants and the waivers to the merged plan template
 * (PM-IMPL-5, PM-WF-25): it leaves out the pruned elements, binds the arguments of the policy calls and the variant of
 * each axis call site, and names every effect with its provenance. It only ever removes or rebinds what the template
 * declares; it adds no state, link, transition or argument.
 * <p>
 * A prune names its target as a selector, resolved against the template and the plan's step lists:
 * <ul>
 * <li>a step: an exact step id of the Phase 5 or Phase 6 list, or {@code order<n>} for every Phase 6 step below the
 * order {@code n};</li>
 * <li>a state: its id; the state and every transition into it are left out;</li>
 * <li>a link: {@code <state_id>/<link_id>}; the state no longer offers the link;</li>
 * <li>a policy action: {@code <call_site>/<action>}, the call site written as {@code *} for every policy call; the
 * action leaves the {@code actions} the call binds;</li>
 * <li>a call: {@code <call_site>/<subworkflow_id>}, the state that calls the sub-workflow; the call state and every
 * transition into it are left out.</li>
 * </ul>
 * A selector that names nothing the template declares prunes nothing and is not listed; an element two rules prune is
 * listed once, with the reason of the first rule. A binding of a rule replaces the value of an argument the call binds,
 * at every policy call ({@code *}) or at the one call site it names, and a {@code raise} waiver replaces the budget it
 * names at every call of its remediation unit; an argument a call does not bind is never added. A {@code met} waiver
 * marks its gate waived.
 * <p>
 * An axis call site is a kept state that calls a variant of a plan behaviour axis ({@link PlanFacts.Axis}). It calls
 * the variant bound on its axis instead, with the template's entry, arguments and transitions, and every other variant
 * of the axis is listed after the prunes of the rules as a pruned call {@code <call_site>/<variant>} with the reason
 * {@code plan_default} and the level that bound the variant as source fact
 * ({@link PlanFacts.BoundVariant#sourceFact()}).
 */
final class InstancePruner {

    /** The selector of every Phase 6 step below an order. */
    private static final Pattern ORDER_BELOW = Pattern.compile("order<([0-9]{1,9})");

    /** The selector of every policy call site. */
    private static final String EVERY_CALL_SITE = "*";

    /** The argument of a policy call that lists the allowed actions. */
    private static final String ACTIONS = "actions";

    private InstancePruner() {
    }

    /**
     * @param template          the merged plan template
     * @param effects           the effects of the composition rules
     * @param bound             the variant bound on each plan behaviour axis
     * @param waivers           the waivers in force
     * @param verificationSteps the Phase 5 verification step ids
     * @param finalizeSteps     the Phase 6 steps with their order
     * @return the kept states, the effects with their provenance and the kept step lists
     */
    static Pruned prune(WorkflowDefinition template, RuleEffects effects, List<PlanFacts.BoundVariant> bound,
            List<Waiver> waivers, List<String> verificationSteps, List<CompositionRequest.Step> finalizeSteps) {
        var selection = new Selection(template, verificationSteps, finalizeSteps);
        effects.prunes().forEach(selection::resolve);
        Map<PlanFacts.Axis, PlanFacts.BoundVariant> variants = new EnumMap<>(PlanFacts.Axis.class);
        bound.forEach(variant -> variants.put(variant.axis(), variant));
        var states = new LinkedHashMap<String, StateNode>();
        var policyCalls = new ArrayList<PolicyCall>();
        for (var state : template.states().values()) {
            if (!selection.states.contains(state.id())) {
                states.put(state.id(), keep(state, selection, effects, waivers, policyCalls));
            }
        }
        states.replaceAll((id, state) -> bindVariant(state, variants, selection));
        var waivedGates = waivers.stream().filter(waiver -> waiver.mode() == Waiver.Mode.MET)
                .map(waiver -> new WaivedGate(waiver.gate(), waiver.id())).toList();
        var keptVerification = verificationSteps.stream().filter(step -> !selection.steps.contains(step)).toList();
        var keptFinalize = finalizeSteps.stream().map(CompositionRequest.Step::id)
                .filter(step -> !selection.steps.contains(step)).toList();
        return new Pruned(states, selection.pruned, policyCalls, waivedGates,
                new PlanInstanceHeader.Steps(keptVerification, keptFinalize));
    }

    /**
     * @param state a state of a unit
     * @return whether the state calls a remediation policy unit
     */
    static boolean isPolicyCall(StateNode state) {
        return state.behavior() instanceof StateBehavior.SubWorkflowCall call
                && HeaderSchemaCheck.POLICY_UNITS.contains(call.subWorkflowId());
    }

    /**
     * @return the state unchanged, or for an axis call site whose axis is bound the call of the bound variant, every
     *         other variant of the axis listed as a pruned call
     */
    private static StateNode bindVariant(StateNode state, Map<PlanFacts.Axis, PlanFacts.BoundVariant> variants,
            Selection selection) {
        if (!(state.behavior() instanceof StateBehavior.SubWorkflowCall call)) {
            return state;
        }
        var axis = PlanFacts.Axis.ofVariant(call.subWorkflowId());
        if (axis.isEmpty() || !variants.containsKey(axis.get())) {
            return state;
        }
        var variant = variants.get(axis.get());
        axis.get().variants().stream().filter(other -> !other.equals(variant.variant()))
                .forEach(other -> selection.list(new PrunedElement(PrunedElement.Kind.CALL, state.id() + "/" + other,
                        PruneReason.PLAN_DEFAULT, variant.sourceFact()), state.id() + "/" + other));
        if (variant.variant().equals(call.subWorkflowId())) {
            return state;
        }
        return new StateNode(state.id(), state.phase(), state.terminalOutcome(), state.skillPath(),
                state.factsPrimitiveId(), new StateBehavior.SubWorkflowCall(variant.variant(), call.entry(),
                        call.paramBindings(), call.transitions()),
                state.location());
    }

    private static StateNode keep(StateNode state, Selection selection, RuleEffects effects, List<Waiver> waivers,
            List<PolicyCall> policyCalls) {
        var removed = selection.states;
        var behavior = switch (state.behavior()) {
            case StateBehavior.InteractiveLinks(var links) ->
                new StateBehavior.InteractiveLinks(links(state.id(), links, selection));
            case StateBehavior.AutoStep(var primitiveId, var transitions) ->
                new StateBehavior.AutoStep(primitiveId, transitions(transitions, removed));
            case StateBehavior.JobStep(var primitiveId, var composite, var transitions) ->
                new StateBehavior.JobStep(primitiveId, composite, transitions(transitions, removed));
            case StateBehavior.ModelJob job -> new StateBehavior.ModelJob(job.entityKind(), job.roleName(),
                    job.fanOutPrimitiveId(), job.rolesPrimitiveId(), job.ask(), job.consultRoles(),
                    transitions(job.transitions(), removed));
            case StateBehavior.SubWorkflowCall call -> {
                var bindings = new LinkedHashMap<>(call.paramBindings());
                if (isPolicyCall(state)) {
                    var arguments = bind(state, call, selection, effects, waivers);
                    arguments.forEach((name, argument) -> bindings.put(name, argument.value()));
                    policyCalls.add(PolicyCall.of(state.id(), call.subWorkflowId(), arguments));
                }
                yield new StateBehavior.SubWorkflowCall(call.subWorkflowId(), call.entry(), bindings,
                        transitions(call.transitions(), removed));
            }
            case StateBehavior.QuestionWait(var questionClass, var transitions, var links) ->
                new StateBehavior.QuestionWait(questionClass, transitions(transitions, removed),
                        links(state.id(), links, selection));
            case StateBehavior.TerminalState terminal -> terminal;
        };
        return new StateNode(state.id(), state.phase(), state.terminalOutcome(), state.skillPath(),
                state.factsPrimitiveId(), behavior, state.location());
    }

    private static List<LinkSpec> links(String stateId, List<LinkSpec> links, Selection selection) {
        var prunedLinks = selection.links.getOrDefault(stateId, Set.of());
        return links.stream().filter(link -> !prunedLinks.contains(link.idPattern()))
                .map(link -> new LinkSpec(link.rel(), link.idPattern(), link.form(), link.freeParams(), link.guard(),
                        link.action(), transitions(link.transitions(), selection.states), link.location()))
                .toList();
    }

    private static List<OutcomeTransition> transitions(List<OutcomeTransition> transitions, Set<String> removed) {
        return transitions.stream().filter(transition -> !removed.contains(transition.targetStateId())).toList();
    }

    /**
     * @return the effective arguments of a policy call: the template's values, the actions a prune left out, the
     *         bindings of the rules and the raises of the waivers, in this order of precedence
     */
    private static Map<String, BoundArgument> bind(StateNode state, StateBehavior.SubWorkflowCall call,
            Selection selection, RuleEffects effects, List<Waiver> waivers) {
        var file = state.location().resourceName();
        var arguments = new LinkedHashMap<String, BoundArgument>();
        call.paramBindings().forEach((name, value) -> arguments.put(name, BoundArgument.fromTemplate(value, file)));
        var prunedActions = selection.actions.getOrDefault(state.id(), Set.of());
        if (!prunedActions.isEmpty() && call.paramBindings().get(ACTIONS) instanceof CallValue.EnumList(var values)) {
            arguments.put(ACTIONS, BoundArgument.fromCompositionRule(new CallValue.EnumList(values.stream()
                    .filter(action -> !prunedActions.contains(action)).toList())));
        }
        for (var binding : effects.bindings()) {
            if ((EVERY_CALL_SITE.equals(binding.callSite()) || binding.callSite().equals(state.id()))
                    && arguments.containsKey(binding.argument())) {
                arguments.put(binding.argument(), BoundArgument.fromCompositionRule(binding.value()));
            }
        }
        for (var waiver : waivers) {
            var argument = waiver.raisedArgument();
            if (argument.isPresent() && waiver.raisedUnit().filter(call.subWorkflowId()::equals).isPresent()
                    && arguments.containsKey(argument.get())) {
                arguments.put(argument.get(), BoundArgument.fromWaiver(
                        new CallValue.IntValue(waiver.value().orElseThrow()), waiver.id()));
            }
        }
        return arguments;
    }

    /**
     * The template after the rules and waivers applied.
     *
     * @param states      the kept states in declaration order, rebound
     * @param pruned      every template element left out, once, in the order of the rules
     * @param policyCalls every policy call of the kept states with its effective arguments
     * @param waivedGates every gate a met waiver marks waived
     * @param steps       the kept step lists
     */
    record Pruned(Map<String, StateNode> states, List<PrunedElement> pruned, List<PolicyCall> policyCalls,
    List<WaivedGate> waivedGates, PlanInstanceHeader.Steps steps) {
    }

    /** Resolves the selectors of the prunes against the template and collects what they leave out. */
    private static final class Selection {

        private final WorkflowDefinition template;
        private final List<String> verificationSteps;
        private final List<CompositionRequest.Step> finalizeSteps;
        private final List<PrunedElement> pruned = new ArrayList<>();
        private final Set<String> listed = new HashSet<>();
        private final Set<String> steps = new LinkedHashSet<>();
        private final Set<String> states = new LinkedHashSet<>();
        private final Map<String, Set<String>> links = new HashMap<>();
        private final Map<String, Set<String>> actions = new HashMap<>();

        Selection(WorkflowDefinition template, List<String> verificationSteps,
                List<CompositionRequest.Step> finalizeSteps) {
            this.template = template;
            this.verificationSteps = verificationSteps;
            this.finalizeSteps = finalizeSteps;
        }

        void resolve(PrunedElement prune) {
            switch (prune.element()) {
                case STEP -> stepIds(prune.id()).forEach(id -> {
                    if (list(prune, id)) {
                        steps.add(id);
                    }
                });
                case STATE -> {
                    if (template.states().containsKey(prune.id()) && list(prune, prune.id())) {
                        states.add(prune.id());
                    }
                }
                case LINK -> split(prune.id()).filter(parts -> declaresLink(parts[0], parts[1]))
                        .filter(parts -> list(prune, prune.id()))
                        .ifPresent(parts -> links.computeIfAbsent(parts[0], key -> new HashSet<>()).add(parts[1]));
                case POLICY_ACTION -> split(prune.id()).ifPresent(parts -> callSites(parts[0]).stream()
                        .filter(site -> allows(site, parts[1]))
                        .filter(site -> list(prune, site.id() + "/" + parts[1]))
                        .forEach(site -> actions.computeIfAbsent(site.id(), key -> new HashSet<>()).add(parts[1])));
                case CALL -> split(prune.id()).filter(parts -> calls(parts[0], parts[1]))
                        .filter(parts -> list(prune, prune.id()))
                        .ifPresent(parts -> states.add(parts[0]));
            }
        }

        private boolean calls(String stateId, String subWorkflowId) {
            return template.states().get(stateId) instanceof StateNode state
                    && state.behavior() instanceof StateBehavior.SubWorkflowCall call
                    && call.subWorkflowId().equals(subWorkflowId);
        }

        private List<String> stepIds(String selector) {
            var orderBelow = ORDER_BELOW.matcher(selector);
            if (orderBelow.matches()) {
                var bound = Integer.parseInt(orderBelow.group(1));
                return finalizeSteps.stream().filter(step -> step.order() < bound).map(CompositionRequest.Step::id)
                        .toList();
            }
            var known = verificationSteps.contains(selector)
                    || finalizeSteps.stream().anyMatch(step -> step.id().equals(selector));
            return known ? List.of(selector) : List.of();
        }

        private boolean declaresLink(String stateId, String linkId) {
            var state = template.states().get(stateId);
            if (state == null) {
                return false;
            }
            var declared = switch (state.behavior()) {
                case StateBehavior.InteractiveLinks(var stateLinks) -> stateLinks;
                case StateBehavior.QuestionWait question -> question.links();
                case StateBehavior.AutoStep _, StateBehavior.JobStep _, StateBehavior.ModelJob _,
                    StateBehavior.SubWorkflowCall _, StateBehavior.TerminalState _ -> List.<LinkSpec>of();
            };
            return declared.stream().anyMatch(link -> link.idPattern().equals(linkId));
        }

        private List<StateNode> callSites(String selector) {
            return template.states().values().stream().filter(InstancePruner::isPolicyCall)
                    .filter(state -> EVERY_CALL_SITE.equals(selector) || state.id().equals(selector)).toList();
        }

        private static boolean allows(StateNode site, String action) {
            return site.behavior() instanceof StateBehavior.SubWorkflowCall call
                    && call.paramBindings().get(ACTIONS) instanceof CallValue.EnumList(var values)
                    && values.contains(action);
        }

        private static Optional<String[]> split(String selector) {
            var slash = selector.indexOf('/');
            return slash > 0 && slash < selector.length() - 1
                    ? Optional.of(new String[]{selector.substring(0, slash), selector.substring(slash + 1)})
                    : Optional.empty();
        }

        /** Lists an element once; returns whether it was not listed before. */
        private boolean list(PrunedElement prune, String id) {
            if (!listed.add(prune.element().wireName() + " " + id)) {
                return false;
            }
            pruned.add(new PrunedElement(prune.element(), id, prune.reason(), prune.sourceFact()));
            return true;
        }
    }
}
