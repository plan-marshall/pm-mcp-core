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

import java.util.ArrayDeque;
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
import java.util.stream.Collectors;

import de.planmarshall.workflow.ast.CallValue;
import de.planmarshall.workflow.ast.LinkSpec;
import de.planmarshall.workflow.ast.OutcomeTransition;
import de.planmarshall.workflow.ast.StateBehavior;
import de.planmarshall.workflow.ast.StateNode;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.verify.LaneEntryIds;

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
 * <p>
 * From the {@code outline} event on, the lane is known, and the states of the other lane are pruned after the prunes
 * of the rules, each a pruned state with the reason {@code other_lane} and the source fact {@code lane}: the lanes
 * split at the two exits of {@value #LANE_SPLIT}, the deep one entering {@link LaneEntryIds#DEEP_ENTRY} and the light
 * one {@link LaneEntryIds#LIGHT_ENTRY}; pruned are exactly the states reachable from the entry of the other lane and not
 * from the entry of the chosen lane, both over all transitions, escalation included. The shared states before the split, those
 * reachable from a starting state without taking a lane exit of {@value #LANE_SPLIT}, belong to no lane and are never
 * pruned by lane. A light version keeps the deep route, the target of its escalation, and so prunes nothing by lane;
 * a deep version holds no state of the light route.
 */
final class InstancePruner {

    /** The selector of every Phase 6 step below an order. */
    private static final Pattern ORDER_BELOW = Pattern.compile("order<([0-9]{1,9})");

    /** The selector of every policy call site. */
    private static final String EVERY_CALL_SITE = "*";

    /** The argument of a policy call that lists the allowed actions. */
    private static final String ACTIONS = "actions";

    /** The state whose two exits split the lanes. */
    static final String LANE_SPLIT = "init.ready";

    /** The source fact of a prune by lane. */
    private static final String LANE_FACT = "lane";

    private InstancePruner() {
    }

    /**
     * @param template          the merged plan template
     * @param effects           the effects of the composition rules
     * @param bound             the variant bound on each plan behaviour axis
     * @param waivers           the waivers in force
     * @param verificationSteps the Phase 5 verification step ids
     * @param finalizeSteps     the Phase 6 steps with their order
     * @param lane              the lane whose other lane is pruned; empty where nothing is pruned by lane (the
     *                          {@code start} event)
     * @return the kept states, the effects with their provenance and the kept step lists
     */
    static Pruned prune(WorkflowDefinition template, RuleEffects effects, List<PlanFacts.BoundVariant> bound,
            List<Waiver> waivers, List<String> verificationSteps, List<CompositionRequest.Step> finalizeSteps,
            Optional<PlanInstanceHeader.Lane> lane) {
        var selection = new Selection(template, verificationSteps, finalizeSteps);
        effects.prunes().forEach(selection::resolve);
        otherLane(template, lane).forEach(state -> selection.resolve(new PrunedElement(PrunedElement.Kind.STATE,
                state, PruneReason.OTHER_LANE, LANE_FACT)));
        Map<PlanFacts.Axis, PlanFacts.BoundVariant> variants = new EnumMap<>(PlanFacts.Axis.class);
        bound.forEach(variant -> variants.put(variant.axis(), variant));
        var states = new LinkedHashMap<String, StateNode>();
        var policyCalls = new ArrayList<PolicyCall>();
        var settled = new LinkedHashMap<String, Set<String>>();
        for (var state : template.states().values()) {
            if (!selection.states.contains(state.id())) {
                states.put(state.id(), keep(state, selection, effects, waivers, policyCalls));
                var outcomes = settledOutcomes(state, selection.states);
                if (!outcomes.isEmpty()) {
                    settled.put(state.id(), outcomes);
                }
            }
        }
        states.replaceAll((id, state) -> bindVariant(state, variants, selection));
        var waivedGates = waivers.stream().filter(waiver -> waiver.mode() == Waiver.Mode.MET)
                .map(waiver -> new WaivedGate(waiver.gate(), waiver.id())).toList();
        var keptVerification = verificationSteps.stream().filter(step -> !selection.steps.contains(step)).toList();
        var keptFinalize = finalizeSteps.stream().map(CompositionRequest.Step::id)
                .filter(step -> !selection.steps.contains(step)).toList();
        return new Pruned(states, selection.pruned, policyCalls, waivedGates,
                new PlanInstanceHeader.Steps(keptVerification, keptFinalize), settled);
    }

    /**
     * @param state   a kept state of the template
     * @param removed the states pruned with a prune record
     * @return the outcomes of the state every transition of which leads into a pruned state, so the prune record
     *         settles them; an outcome with a kept target is routed and not listed
     */
    private static Set<String> settledOutcomes(StateNode state, Set<String> removed) {
        var transitions = transitions(state.behavior());
        var kept = transitions.stream().filter(transition -> !removed.contains(transition.targetStateId()))
                .map(OutcomeTransition::outcomeName).collect(Collectors.toSet());
        return transitions.stream().filter(transition -> removed.contains(transition.targetStateId()))
                .map(OutcomeTransition::outcomeName).filter(outcome -> !kept.contains(outcome))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * The states of the lane the plan did not choose: reachable from the entry of the other lane and not from the
     * entry of the chosen one, both over all transitions, escalation included, and not shared, that is not reachable
     * from a starting state of the unit without taking a lane exit of {@value #LANE_SPLIT}. A light lane reaches the
     * deep route through its escalation, so it prunes nothing; a lane other than light or deep, and a template without
     * both lane entries, prune nothing either.
     *
     * @return the states to prune with the reason {@code other_lane}, in declaration order
     */
    private static List<String> otherLane(WorkflowDefinition template, Optional<PlanInstanceHeader.Lane> lane) {
        var chosen = lane.filter(value -> value == PlanInstanceHeader.Lane.LIGHT
                || value == PlanInstanceHeader.Lane.DEEP);
        if (chosen.isEmpty() || !template.states().containsKey(LaneEntryIds.DEEP_ENTRY)
                || !template.states().containsKey(LaneEntryIds.LIGHT_ENTRY)) {
            return List.of();
        }
        var deep = chosen.get() == PlanInstanceHeader.Lane.DEEP;
        var kept = reachable(template, List.of(deep ? LaneEntryIds.DEEP_ENTRY : LaneEntryIds.LIGHT_ENTRY), false);
        var shared = reachable(template, template.startingStates(), true);
        var other = reachable(template, List.of(deep ? LaneEntryIds.LIGHT_ENTRY : LaneEntryIds.DEEP_ENTRY), false);
        return template.states().keySet().stream()
                .filter(state -> other.contains(state) && !kept.contains(state) && !shared.contains(state)).toList();
    }

    /**
     * @param template       the unit
     * @param starts         the states to start from
     * @param stopAtLaneExit whether the lane exits of {@value #LANE_SPLIT} are not taken
     * @return the states reachable from the starts over all transitions, the starts included
     */
    private static Set<String> reachable(WorkflowDefinition template, List<String> starts, boolean stopAtLaneExit) {
        var reached = new HashSet<String>();
        var queue = new ArrayDeque<>(starts);
        while (!queue.isEmpty()) {
            var id = queue.poll();
            var state = template.states().get(id);
            if (state == null || !reached.add(id)) {
                continue;
            }
            for (var target : targets(state.behavior())) {
                var laneExit = LANE_SPLIT.equals(id) && (LaneEntryIds.DEEP_ENTRY.equals(target)
                        || LaneEntryIds.LIGHT_ENTRY.equals(target));
                if (!(stopAtLaneExit && laneExit)) {
                    queue.add(target);
                }
            }
        }
        return reached;
    }

    /** @return the target of every transition of the behavior, those of its links among them */
    private static List<String> targets(StateBehavior behavior) {
        return transitions(behavior).stream().map(OutcomeTransition::targetStateId).toList();
    }

    /** @return every transition of the behavior, those of its links among them */
    private static List<OutcomeTransition> transitions(StateBehavior behavior) {
        var transitions = new ArrayList<OutcomeTransition>();
        switch (behavior) {
            case StateBehavior.InteractiveLinks(var links) -> links.forEach(link -> transitions.addAll(
                    link.transitions()));
            case StateBehavior.AutoStep step -> transitions.addAll(step.transitions());
            case StateBehavior.JobStep job -> transitions.addAll(job.transitions());
            case StateBehavior.ModelJob job -> transitions.addAll(job.transitions());
            case StateBehavior.SubWorkflowCall call -> transitions.addAll(call.transitions());
            case StateBehavior.QuestionWait question -> transitions.addAll(question.transitions());
            case StateBehavior.TerminalState _ -> {
                // a terminal state has no transition
            }
        }
        return transitions;
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
            case StateBehavior.QuestionWait(var questionClass, var transitions) ->
                new StateBehavior.QuestionWait(questionClass, transitions(transitions, removed));
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
     * @param settled     by kept state, the outcomes whose transitions all led into a state pruned with a prune
     *                    record, which the record settles (Rule 2)
     */
    record Pruned(Map<String, StateNode> states, List<PrunedElement> pruned, List<PolicyCall> policyCalls,
    List<WaivedGate> waivedGates, PlanInstanceHeader.Steps steps, Map<String, Set<String>> settled) {
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
                case StateBehavior.AutoStep _, StateBehavior.JobStep _, StateBehavior.ModelJob _,
                    StateBehavior.QuestionWait _, StateBehavior.SubWorkflowCall _, StateBehavior.TerminalState _ ->
                    List.<LinkSpec>of();
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
