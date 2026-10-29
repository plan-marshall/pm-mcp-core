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

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import de.planmarshall.workflow.ast.CallValue;
import de.planmarshall.workflow.ast.InstanceHeader;
import de.planmarshall.workflow.ast.InstanceInputs;
import de.planmarshall.workflow.ast.StateBehavior;
import de.planmarshall.workflow.ast.StateNode;
import de.planmarshall.workflow.ast.UnitFrontmatter;
import de.planmarshall.workflow.ast.UnitType;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.dsl.WorkflowPrinter;
import de.planmarshall.workflow.finalize.FinalizePipelineValidator;
import de.planmarshall.workflow.finalize.FinalizeStepDescriptor;
import de.planmarshall.workflow.unit.VerificationContext;
import de.planmarshall.workflow.verify.CompiledInvariants;
import de.planmarshall.workflow.verify.DeclarationBounds;
import de.planmarshall.workflow.verify.WorkflowCatalog;
import de.planmarshall.workflow.verify.WorkflowValidator;

/**
 * Composes one version of a plan instance and verifies it before anyone may write it (PM-IMPL-5, PM-WF-1, PM-EXT-7,
 * PM-WF-25).
 * <p>
 * A composition binds the variant of each plan behaviour axis from the facts of the request (the plan's own value,
 * else the project's, else the machine's, else the shipped plan default, {@link PlanFacts#bound(PlanFacts.Axis)}),
 * passes the declarations of every axis variant among the request's sub-units into the facts, evaluates the
 * composition rules against them, applies their prunes and bindings, the bound variants and the waivers in force to
 * the plan template ({@link InstancePruner}: each axis call site calls its bound variant, every other variant
 * of the axis is a pruned call with the reason {@code plan_default} and the level as source fact; from every event
 * after {@code start} on, the lane is known and the states of the other lane are pruned with the reason
 * {@code other_lane}), and builds the
 * instance header: the version after the one in force, the event, every input by digest (among the sub-units those
 * a call of the version reaches and the adjudication unit, which the engine runs for the decisions of the version
 * without a call site), the lane and posture, the
 * kept step lists, every
 * pruned element with its reason and source fact, every policy call with its effective arguments and their provenance,
 * and the waived gates. The composed unit is the template's states under the id {@code plan}, with the version as its
 * revision, the header as its {@code instance} line, the template's frontmatter with the type {@code instance}, and the
 * template's start state.
 * <p>
 * The version is kept only if it passes every check, and is otherwise refused as a {@link CompositionResult.Refused}
 * value, the version in force staying:
 * <ol>
 * <li>the position rule: the version contains the current state and the calling state of every call-stack frame,
 * else {@code position_not_contained};</li>
 * <li>the header schema ({@link HeaderSchemaCheck}), the static verification Rules 1-12 ({@link WorkflowValidator},
 * over a catalog of the compiled units and the request's sub-units, without a released baseline; an outcome whose
 * transitions all led into a state pruned with a prune record is settled by the record, so Rule 2 counts it as
 * routed, such as the {@code light} exit of {@code init.ready} in a {@code deep} version), the finalize
 * pipeline over the default descriptors of the kept steps, and the compiled invariants ({@link CompiledInvariants},
 * against the default units, as a {@link CompiledInvariants.Subject#COMPOSED_INSTANCE}, whose type {@code instance}
 * INV-GEN-1 admits against its template, with the lane the version is composed for, unknown for {@code start}, so
 * INV-INIT-2 holds per lane), else {@code invariant_failed} with every violation found.</li>
 * </ol>
 * Rules 13 and 14, the resolution of roles and skills, are left to the packages that own them. Every event composes
 * the same way, the operator's {@code override} of one of the plan's own values among them, so an override whose
 * version does not contain the plan's position is refused {@code position_not_contained}. The composer reads no store
 * and writes nothing; a refused input is never an exception.
 *
 * @since 0.1
 */
public final class PlanInstanceComposer {

    /** The id of every plan instance unit. */
    public static final String PLAN_UNIT = "plan";

    /** The sub-units the header binds by digest rather than as composed files of the version. */
    private static final Set<String> BOUND_SUB_UNITS = Set.of("git.commit_and_push", "replan",
            DeclarationBounds.ADJUDICATION_UNIT);

    private final VerificationContext context;

    private final CompiledInvariants invariants;

    private final List<FinalizeStepDescriptor> defaultRoster;

    private final CompositionRules rules;

    private final Clock clock;

    /**
     * @param context       the registries and compiled units the verification reads
     * @param defaultUnits  the verified default units, the reference of the compiled invariants; the plan unit among
     *                      them
     * @param defaultRoster the descriptors of the default finalize steps
     * @param rules         the composition rules
     * @param clock         the clock of {@code composed_at}
     * @throws NullPointerException if an argument or an element is {@code null}
     */
    public PlanInstanceComposer(VerificationContext context, Collection<WorkflowDefinition> defaultUnits,
            List<FinalizeStepDescriptor> defaultRoster, CompositionRules rules, Clock clock) {
        this.context = Objects.requireNonNull(context, "context");
        this.defaultRoster = List.copyOf(defaultRoster);
        this.invariants = new CompiledInvariants(context.registry(), defaultUnits, this.defaultRoster,
                CompiledInvariants.Subject.COMPOSED_INSTANCE);
        this.rules = Objects.requireNonNull(rules, "rules");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Composes and verifies the version the request asks for.
     *
     * @param request what the composition reads
     * @return the composed version, or its refusal
     * @throws NullPointerException if the request is {@code null}
     */
    public CompositionResult compose(CompositionRequest request) {
        Objects.requireNonNull(request, "request");
        var facts = withVariantDeclarations(request.facts(), request.subUnits());
        var effects = CompositionRuleEvaluator.evaluate(rules, facts);
        var bound = Arrays.stream(PlanFacts.Axis.values()).map(facts::bound).toList();
        var lanePruned = request.event() == CompositionEvent.START ? Optional.<PlanInstanceHeader.Lane>empty()
                : facts.lane();
        var pruned = InstancePruner.prune(request.template(), effects, bound, request.waivers(),
                request.verificationSteps(), request.finalizeSteps(), lanePruned);
        var version = request.nextVersion();
        var composed = composedUnits(request, pruned.states(), effects, bound, lanePruned, version);
        var positionStates = new LinkedHashSet<>(pruned.states().keySet());
        positionStates.addAll(composed.positionStates());
        var missing = request.position().missingFrom(positionStates);
        if (!missing.isEmpty()) {
            return new CompositionResult.Refused(CompositionResult.Refusal.POSITION_NOT_CONTAINED, missing.stream()
                    .map(state -> "position: version %d does not contain the state '%s'".formatted(version, state))
                    .toList());
        }
        var header = new PlanInstanceHeader(request.planId(), version, request.event(), clock.instant(),
                inputs(request, composed, version), facts.lane(), facts.posture(),
                pruned.steps(), pruned.pruned(), pruned.policyCalls(), pruned.waivedGates());
        var unit = unit(request.template(), header, pruned.states());
        var violations = verify(request, header, unit, lanePruned, pruned.settled());
        if (!violations.isEmpty()) {
            return new CompositionResult.Refused(CompositionResult.Refusal.INVARIANT_FAILED, violations);
        }
        return new CompositionResult.Composed(new PlanInstance(version, header, unit, WorkflowPrinter.print(unit),
                composed.units()));
    }

    /**
     * @param id a sub-unit id
     * @return whether the version persists the unit as a composed file of its own: every sub-workflow a kept call
     *         reaches except the units the header binds by digest ({@code git.commit_and_push}, the
     *         {@code remediation.*} units, {@code replan} and the adjudication unit the engine runs)
     */
    static boolean isComposedUnit(String id) {
        return !id.startsWith("remediation.") && !BOUND_SUB_UNITS.contains(id);
    }

    /**
     * The composed files of the version and the states the position rule reads: one file per sub-workflow a kept call
     * reaches, walked over the pruned states of the composed units themselves, so a call pruned inside a composed unit
     * reaches nothing. The units the version only binds by digest ({@code git.commit_and_push}, the
     * {@code remediation.*} units, {@code replan}, {@code adjudicate.decisions}) stay content-addressed, and their
     * states are positions.
     *
     * @return the composed units in the order of the request's sub-units, and every state of them and of the reached
     *         digest-bound units
     */
    private static Composed composedUnits(CompositionRequest request, Map<String, StateNode> keptStates,
            RuleEffects effects, List<PlanFacts.BoundVariant> boundVariants, Optional<PlanInstanceHeader.Lane> lane,
            int version) {
        var byId = new LinkedHashMap<String, CompositionRequest.SubUnit>();
        request.subUnits().forEach(subUnit -> byId.put(subUnit.definition().id(), subUnit));
        var units = new LinkedHashMap<String, ComposedUnit>();
        var states = new LinkedHashSet<String>();
        var bound = new LinkedHashSet<String>();
        var visited = new HashSet<String>();
        var queue = new ArrayDeque<String>();
        keptStates.values().forEach(state -> callee(state).ifPresent(queue::add));
        while (!queue.isEmpty()) {
            var id = queue.poll();
            if (!visited.add(id) || !byId.containsKey(id)) {
                continue;
            }
            if (isComposedUnit(id)) {
                var composed = composeUnit(byId.get(id).definition(), request, effects, boundVariants, lane, version);
                units.put(id, composed);
                states.addAll(composed.definition().states().keySet());
                composed.definition().states().values().forEach(state -> callee(state).ifPresent(queue::add));
            } else {
                bound.add(id);
                var definition = byId.get(id).definition();
                states.addAll(definition.states().keySet());
                definition.states().values().forEach(state -> callee(state).ifPresent(queue::add));
            }
        }
        var ordered = request.subUnits().stream().map(subUnit -> subUnit.definition().id())
                .filter(units::containsKey).distinct().map(units::get).toList();
        return new Composed(ordered, states, bound);
    }

    /**
     * The composed files of a version, the states its position rule reads and the digest-bound units its kept calls
     * reach.
     *
     * @param units         the composed files, in the order of the request's sub-units
     * @param positionStates every state of the composed files and of the reached digest-bound units
     * @param subUnits      the ids of the digest-bound units the kept calls reach
     */
    private record Composed(List<ComposedUnit> units, Set<String> positionStates, Set<String> subUnits) {
    }

    /**
     * Composes the file of one phase sub-workflow or bound axis variant: the prunes of the composition that fall inside
     * it applied, the axis call sites it holds bound, and the version repeated, the frontmatter, id, type, entries and
     * exits of its template kept and no {@code instance} line added.
     */
    private static ComposedUnit composeUnit(WorkflowDefinition definition, CompositionRequest request,
            RuleEffects effects, List<PlanFacts.BoundVariant> bound, Optional<PlanInstanceHeader.Lane> lane, int version) {
        var pruned = InstancePruner.prune(definition, effects, bound, request.waivers(), request.verificationSteps(),
                request.finalizeSteps(), lane);
        var composed = new WorkflowDefinition(definition.frontmatter(), definition.id(), version, definition.scope(),
                definition.params(), definition.resultSchema(), Optional.empty(), definition.declarations(),
                definition.decisions(), definition.formatVersion(), definition.origin(), definition.derivedFrom(),
                definition.startState(), definition.entryBindings(), definition.migrations(), pruned.states(),
                definition.location());
        return ComposedUnit.of(definition.id(), composed);
    }

    /**
     * @return the facts with the declarations of every axis variant among the sub-units, as its tree declares them
     */
    private static PlanFacts withVariantDeclarations(PlanFacts facts, List<CompositionRequest.SubUnit> subUnits) {
        var read = facts;
        for (var subUnit : subUnits) {
            var definition = subUnit.definition();
            if (PlanFacts.Axis.ofVariant(definition.id()).isPresent()) {
                var declared = new LinkedHashMap<String, CallValue>();
                definition.declarations().forEach((name, declaration) -> declared.put(name, declaration.value()));
                read = read.withDeclarations(definition.id(), declared);
            }
        }
        return read;
    }

    /**
     * @return the inputs of the header: the composed files of the version as its templates, the digest-bound sub-units
     *         the kept calls reach, and the adjudication unit {@value DeclarationBounds#ADJUDICATION_UNIT} whenever the
     *         request supplies it, which no call site calls because the engine runs it for the decisions of the version
     */
    private CompositionInputs inputs(CompositionRequest request, Composed composed, int version) {
        var composedIds = new LinkedHashSet<String>();
        composed.units().forEach(unit -> composedIds.add(unit.unit()));
        var templates = new ArrayList<UnitReference>();
        for (var reference : request.templates()) {
            templates.add(new UnitReference(reference.unit(), Optional.of(PlanInstanceStore.versionPath(version)),
                    reference.digest(), reference.origin()));
        }
        for (var unit : composed.units()) {
            var source = request.subUnits().stream()
                    .filter(subUnit -> subUnit.definition().id().equals(unit.unit())).findFirst().orElseThrow();
            templates.add(new UnitReference(unit.unit(), Optional.of(unit.file()), unit.digest(),
                    source.reference().origin()));
        }
        var subUnits = request.subUnits().stream().map(CompositionRequest.SubUnit::reference)
                .filter(reference -> !composedIds.contains(reference.unit()))
                .filter(reference -> composed.subUnits().contains(reference.unit())
                        || DeclarationBounds.ADJUDICATION_UNIT.equals(reference.unit()))
                .toList();
        return new CompositionInputs(request.configHash(), templates, subUnits, rules.revision(),
                rules.digest(), request.facts().digest(), request.realizedFootprintDigest(),
                request.facts().changeType(), request.providersDigest());
    }

    private static Optional<String> callee(StateNode state) {
        return state.behavior() instanceof StateBehavior.SubWorkflowCall call ? Optional.of(call.subWorkflowId())
                : Optional.empty();
    }

    private static WorkflowDefinition unit(WorkflowDefinition template, PlanInstanceHeader header,
            Map<String, StateNode> states) {
        var inputs = header.inputs();
        var instance = new InstanceHeader(header.planId(), header.version(), header.event().wireName(),
                new InstanceInputs(inputs.configHash(), unitRefs(inputs.templates()), unitRefs(inputs.subUnits()),
                        new InstanceInputs.CompositionRules(inputs.compositionRulesRevision(),
                                inputs.compositionRulesDigest()), inputs.planFactsDigest(),
                        inputs.realizedFootprintDigest(), inputs.changeType(), inputs.providersDigest()),
                HeaderJson.write(header));
        return new WorkflowDefinition(instanceFrontmatter(template.frontmatter()), PLAN_UNIT, header.version(),
                template.scope(), template.params(), template.resultSchema(), Optional.of(instance),
                template.declarations(), template.decisions(), template.formatVersion(), template.origin(),
                Map.<String, Integer>of(), template.startState(), template.entryBindings(), template.migrations(),
                states, template.location());
    }

    /** The frontmatter of a composed version: the template's, with the type {@link UnitType#INSTANCE}. */
    private static UnitFrontmatter instanceFrontmatter(UnitFrontmatter template) {
        return new UnitFrontmatter(template.name(), template.description(), UnitType.INSTANCE, template.icon(),
                template.entries(), template.exits(), template.modificationDate(), template.version(),
                template.author(), template.signature(), template.location());
    }

    private static List<InstanceInputs.UnitRef> unitRefs(List<UnitReference> references) {
        return references.stream().map(reference -> new InstanceInputs.UnitRef(reference.unit(), reference.file(),
                reference.digest(), UnitReference.wireName(reference.origin()))).toList();
    }

    /**
     * @return every violation of the header schema, the static rules, the finalize pipeline and the compiled
     *         invariants, each listed once
     */
    private List<String> verify(CompositionRequest request, PlanInstanceHeader header, WorkflowDefinition unit,
            Optional<PlanInstanceHeader.Lane> lane, Map<String, Set<String>> settled) {
        var found = new ArrayList<String>();
        HeaderSchemaCheck.check(header).forEach(violation -> found.add("header '%s': %s"
                .formatted(violation.pointer(), violation.reason())));
        var catalog = new ArrayList<>(context.compiledUnits());
        request.subUnits().forEach(subUnit -> catalog.add(subUnit.definition()));
        var validator = new WorkflowValidator(context.registry(), List.of(), context.entityKinds(),
                context.questionClasses(), context.schemas(), new WorkflowCatalog(catalog));
        var result = validator.verify(unit, settled);
        result.violations().forEach(violation -> found.add("Rule %d (%s) in '%s': %s".formatted(
                violation.rule().specificationRule(), violation.rule(), violation.stateId(), violation.message())));
        var keptSteps = header.steps().phase6();
        var pipeline = FinalizePipelineValidator.validate(defaultRoster.stream()
                .filter(step -> keptSteps.contains(step.name())).toList());
        pipeline.forEach(violation -> found.add("pipeline step '%s' (%s): %s".formatted(violation.stepName(),
                violation.rule(), violation.message())));
        invariants.check(unit, catalog, result, pipeline, lane.map(PlanInstanceHeader.Lane::wireName)).stream()
                .filter(violation -> !CompiledInvariants.reReports(violation, unit, result))
                .forEach(violation -> found.add("%s in '%s': %s".formatted(violation.id().wireId(),
                        violation.stateId(), violation.message())));
        return found;
    }
}
