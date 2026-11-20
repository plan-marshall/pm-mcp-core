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
import de.planmarshall.workflow.ast.DecisionDeclaration;
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
import de.planmarshall.workflow.model.CompositionRefusal;
import de.planmarshall.workflow.model.ModelIdentity;
import de.planmarshall.workflow.model.RoleComposition;
import de.planmarshall.workflow.model.RoleUse;
import de.planmarshall.workflow.model.SkillCatalogue;
import de.planmarshall.workflow.unit.VerificationContext;
import de.planmarshall.workflow.verify.CompiledInvariants;
import de.planmarshall.workflow.verify.DeclarationBounds;
import de.planmarshall.workflow.verify.WorkflowCatalog;
import de.planmarshall.workflow.verify.WorkflowValidator;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

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
 * A caller that names what the project provides has its bound landing variant validated before anything is composed
 * ({@link LandingVariantValidation}), and a variant that does not fit the project is refused with the reason; a caller
 * with no project known composes without that check.
 * <p>
 * The version is kept only if it passes every check, and is otherwise refused as a {@link CompositionResult.Refused}
 * value, the version in force staying:
 * <ol>
 * <li>the position rule: the version contains, per unit, the state the plan is in and the calling state of every
 * call-stack frame, else {@code position_not_contained};</li>
 * <li>the header schema ({@link HeaderSchemaCheck}), the static verification Rules 1-12 ({@link WorkflowValidator},
 * over a catalog of the compiled units and the request's sub-units, without a released baseline; an outcome whose
 * transitions all led into a state pruned with a prune record is settled by the record, so Rule 2 counts it as
 * routed, such as the {@code light} exit of {@code init.ready} in a {@code deep} version), the finalize
 * pipeline over the default descriptors of the kept steps, and the compiled invariants ({@link CompiledInvariants},
 * against the default units, as a {@link CompiledInvariants.Subject#COMPOSED_INSTANCE}, whose type {@code instance}
 * INV-GEN-1 admits against its template, with the lane the version is composed for, unknown for {@code start}, so
 * INV-INIT-2 holds per lane), else {@code invariant_failed} with every violation found.</li>
 * </ol>
 * Rules 13 and 14 — the resolution of every role a model state or a consultation names in the effective role set, the
 * quorum's model identity and every skill a {@code skill} clause names in the project's catalogue — run last over the
 * composed plan unit and every unit it calls, and refuse the version with their reason where one does not resolve
 * ({@link CompositionChecks}). Every event composes
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

    private final CompositionChecks checks;

    private final Clock clock;

    /**
     * @param context       the registries and compiled units the verification reads
     * @param defaultUnits  the verified default units, the reference of the compiled invariants; the plan unit among
     *                      them
     * @param defaultRoster the descriptors of the default finalize steps
     * @param rules         the composition rules
     * @param checks        the effective role set and the project's skill catalogue of Rules 13 and 14
     * @param clock         the clock of {@code composed_at}
     * @throws NullPointerException if an argument or an element is {@code null}
     */
    public PlanInstanceComposer(VerificationContext context, Collection<WorkflowDefinition> defaultUnits,
            List<FinalizeStepDescriptor> defaultRoster, CompositionRules rules, CompositionChecks checks, Clock clock) {
        this.context = Objects.requireNonNull(context, "context");
        this.defaultRoster = List.copyOf(defaultRoster);
        this.invariants = new CompiledInvariants(context.registry(), defaultUnits, this.defaultRoster,
                CompiledInvariants.Subject.COMPOSED_INSTANCE);
        this.rules = Objects.requireNonNull(rules, "rules");
        this.checks = Objects.requireNonNull(checks, "checks");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    private static @Nullable String landingViolation(PlanFacts facts, LandingVariantValidation.@Nullable Project project,
            CompositionEvent event) {
        if (project == null || event == CompositionEvent.FALLBACK || event == CompositionEvent.EMPTY_HARVEST) {
            return null;
        }
        var landing = facts.bound(PlanFacts.Axis.LANDING).variant();
        var verdict = LandingVariantValidation.validate(landing, project);
        return verdict.valid() ? null
                : "landing: the variant '%s' does not fit the project: %s".formatted(landing, verdict.reason());
    }

    /**
     * Composes and verifies the version the request asks for, without a project's landing check. This is the preview
     * form: the version 1 the operator sees before a plan exists, and the machine's own composition, whose landing
     * default is validated against what the machine provides
     * ({@code MachineDefaultsValidation} of {@code pm-runtime}). A plan's own composition goes through
     * {@link #compose(CompositionRequest, LandingVariantValidation.Project)} with the project it belongs to: this
     * overload is the opt-in skip that ADR {@code workflow-plan/021} Alternative 3 rejects for the setup code, and no
     * production caller of this repository uses it for a plan.
     *
     * @param request what the composition reads
     * @return the composed version, or its refusal
     * @throws NullPointerException if the request is {@code null}
     */
    public CompositionResult compose(CompositionRequest request) {
        return compose(request, null);
    }

    /**
     * Composes and verifies the version the request asks for, validating the bound landing variant against the project
     * where one is known (Workflow DSL § Plan Behaviour Axes, item 3).
     *
     * @param request what the composition reads
     * @param project what the project provides for the landing check; {@code null} where no project is known, which
     *                composes without the check
     * @return the composed version, or its refusal
     * @throws NullPointerException if the request is {@code null}
     */
    public CompositionResult compose(CompositionRequest request,
            LandingVariantValidation.@Nullable Project project) {
        Objects.requireNonNull(request, "request");
        var facts = withVariantDeclarations(request.facts(), request.subUnits());
        var effects = CompositionRuleEvaluator.evaluate(rules, facts);
        var bound = Arrays.stream(PlanFacts.Axis.values()).map(facts::bound).toList();
        var landingViolation = landingViolation(facts, project, request.event());
        if (landingViolation != null) {
            return new CompositionResult.Refused(CompositionResult.Refusal.INVARIANT_FAILED,
                    List.of(landingViolation));
        }
        PlanInstanceHeader.@Nullable Lane lanePruned = request.event() == CompositionEvent.START ? null : facts.lane();
        var pruned = InstancePruner.prune(request.template(), effects, bound, request.waivers(),
                request.verificationSteps(), request.finalizeSteps(), lanePruned);
        var version = request.nextVersion();
        var composed = composedUnits(request, pruned.states(), effects, bound, lanePruned, version);
        if (!composed.unresolved().isEmpty()) {
            return new CompositionResult.Refused(CompositionResult.Refusal.INVARIANT_FAILED,
                    composed.unresolved().stream()
                            .map("the call of '%s' names no sub-unit of the request"::formatted).toList());
        }
        var positionStates = new LinkedHashMap<String, Set<String>>();
        positionStates.put(PLAN_UNIT, pruned.states().keySet());
        positionStates.putAll(composed.positionStates());
        var missing = request.position().missingFrom(positionStates);
        if (!missing.isEmpty()) {
            return new CompositionResult.Refused(CompositionResult.Refusal.POSITION_NOT_CONTAINED, missing.stream()
                    .map(position -> "position: version %d does not contain the state '%s' of the unit '%s'"
                            .formatted(version, position.state(), position.unit()))
                    .toList());
        }
        var header = PlanInstanceHeader.builder()
                .planId(request.planId())
                .version(version)
                .event(request.event())
                .composedAt(clock.instant())
                .inputs(inputs(request, composed, version))
                .lane(facts.lane())
                .posture(facts.posture())
                .steps(pruned.steps())
                .pruned(prunedElements(pruned.pruned(), composed.pruned()))
                .policyCalls(policyCalls(pruned.policyCalls(), composed.policyCalls()))
                .waivedGates(pruned.waivedGates())
                .build();
        var unit = unit(request.template(), header, pruned.states());
        var violations = verify(request, header, unit, lanePruned, pruned.settled());
        if (!violations.isEmpty()) {
            return new CompositionResult.Refused(CompositionResult.Refusal.INVARIANT_FAILED, violations);
        }
        var refusal = checkRules(unit, composed, request);
        if (refusal.isPresent()) {
            return refusal.get();
        }
        var text = WorkflowPrinter.print(unit);
        return new CompositionResult.Composed(PlanInstance.builder()
                .version(version)
                .header(header)
                .unit(unit)
                .text(text)
                .units(composed.units())
                .verified(Verified.of(version, PlanFacts.sha256(text)))
                .build());
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
     *         digest-bound units by the unit that declares it
     */
    private static Composed composedUnits(CompositionRequest request, Map<String, StateNode> keptStates,
            RuleEffects effects, List<PlanFacts.BoundVariant> boundVariants, PlanInstanceHeader.@Nullable Lane lane,
            int version) {
        var byId = new LinkedHashMap<String, CompositionRequest.SubUnit>();
        request.subUnits().forEach(subUnit -> byId.put(subUnit.definition().id(), subUnit));
        var compositions = new LinkedHashMap<String, UnitComposition>();
        var positionStates = new LinkedHashMap<String, Set<String>>();
        var bound = new LinkedHashSet<String>();
        var visited = new HashSet<String>();
        var unresolved = new LinkedHashSet<String>();
        var prunes = new ArrayList<PrunedElement>();
        var queue = new ArrayDeque<String>();
        keptStates.values().forEach(state -> callee(state).ifPresent(queue::add));
        while (!queue.isEmpty()) {
            var id = queue.poll();
            if (visited.add(id)) {
                var subUnit = byId.get(id);
                if (subUnit == null) {
                    unresolved.add(id);
                } else if (isComposedUnit(id)) {
                    var composition = composeUnit(subUnit.definition(), request, effects, boundVariants, lane,
                            version);
                    compositions.put(id, composition);
                    prunes.addAll(composition.pruned());
                    positionStates.put(id, composition.file().definition().states().keySet());
                    composition.file().definition().states().values()
                            .forEach(state -> callee(state).ifPresent(queue::add));
                } else {
                    bound.add(id);
                    positionStates.put(id, subUnit.definition().states().keySet());
                    subUnit.definition().states().values()
                            .forEach(state -> callee(state).ifPresent(queue::add));
                }
            }
        }
        var ordered = request.subUnits().stream().map(subUnit -> subUnit.definition().id())
                .filter(compositions::containsKey).distinct().map(compositions::get).toList();
        return Composed.builder()
                .units(ordered.stream().map(UnitComposition::file).toList())
                .positionStates(positionStates)
                .policyCalls(ordered.stream().flatMap(composition -> composition.policyCalls().stream()).toList())
                .subUnits(bound)
                .pruned(prunes)
                .unresolved(unresolved)
                .build();
    }

    /**
     * @param ofThePlan   the elements the composition of the plan unit left out
     * @param ofTheComposed the elements the compositions of the composed units left out
     * @return every element left out, once, the plan unit's first: an id two compositions prune is listed with the
     *         reason of the first
     */
    private static List<PrunedElement> prunedElements(List<PrunedElement> ofThePlan,
            List<PrunedElement> ofTheComposed) {
        var seen = new LinkedHashSet<String>();
        var pruned = new ArrayList<PrunedElement>();
        for (var element : ofThePlan) {
            if (seen.add(element.element().wireName() + " " + element.id())) {
                pruned.add(element);
            }
        }
        for (var element : ofTheComposed) {
            if (seen.add(element.element().wireName() + " " + element.id())) {
                pruned.add(element);
            }
        }
        return List.copyOf(pruned);
    }

    /**
     * The composed files of a version, the states its position rule reads and the digest-bound units its kept calls
     * reach.
     *
     * @param units          the composed files, in the order of the request's sub-units
     * @param positionStates every state of the composed files and of the reached digest-bound units, by the unit that
     *                       declares it
     * @param policyCalls    every policy call of the composed files, each with the unit that holds it, in the order of
     *                       the request's sub-units
     * @param subUnits       the ids of the digest-bound units the kept calls reach
     * @param pruned         every element a composed unit's composition left out
     * @param unresolved     the ids a kept call names that the request supplies no sub-unit for
     */
    @Builder
    private record Composed(List<ComposedUnit> units, Map<String, Set<String>> positionStates,
    List<PolicyCall> policyCalls, Set<String> subUnits, List<PrunedElement> pruned,
    Set<String> unresolved) {
    }

    /**
     * One composed file and the elements its composition left out.
     *
     * @param file        the composed file
     * @param pruned      the elements the composition of the unit left out
     * @param policyCalls the policy calls the unit's own pruning kept, each held in the unit
     */
    private record UnitComposition(ComposedUnit file, List<PrunedElement> pruned, List<PolicyCall> policyCalls) {
    }

    /**
     * Composes the file of one phase sub-workflow or bound axis variant: the prunes of the composition that fall inside
     * it applied, the axis call sites it holds bound, and the version repeated, the frontmatter, id, type, entries and
     * exits of its template kept and no {@code instance} line added.
     */
    private static UnitComposition composeUnit(WorkflowDefinition definition, CompositionRequest request,
            RuleEffects effects, List<PlanFacts.BoundVariant> bound, PlanInstanceHeader.@Nullable Lane lane,
            int version) {
        var pruned = InstancePruner.prune(definition, effects, bound, request.waivers(), request.verificationSteps(),
                request.finalizeSteps(), lane);
        var composed = WorkflowDefinition.builder()
                .frontmatter(definition.frontmatter())
                .id(definition.id())
                .version(version)
                .scope(definition.scope())
                .resultSchema(definition.resultSchema())
                .instance(null)
                .declarations(definition.declarations())
                .decisions(definition.decisions())
                .formatVersion(definition.formatVersion())
                .origin(definition.origin())
                .derivedFrom(definition.derivedFrom())
                .startState(definition.startState())
                .entryBindings(definition.entryBindings())
                .migrations(definition.migrations())
                .states(pruned.states())
                .location(definition.location())
                .build();
        return new UnitComposition(ComposedUnit.of(definition.id(), composed), pruned.pruned(),
                pruned.policyCalls().stream().map(call -> call.withInUnit(definition.id())).toList());
    }

    /**
     * @param ofThePlan the plan unit's policy calls, without a unit
     * @param ofTheComposed the composed files' policy calls, each with the unit that holds it
     * @return every policy call, the plan unit's first and then those of the composed units in the order they were
     *         composed
     */
    static List<PolicyCall> policyCalls(List<PolicyCall> ofThePlan, List<PolicyCall> ofTheComposed) {
        var calls = new ArrayList<>(ofThePlan);
        calls.addAll(ofTheComposed);
        return List.copyOf(calls);
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
            templates.add(UnitReference.builder()
                    .unit(reference.unit())
                    .file(PlanInstanceStore.versionPath(version))
                    .digest(reference.digest())
                    .origin(reference.origin())
                    .build());
        }
        for (var unit : composed.units()) {
            var source = request.subUnits().stream()
                    .filter(subUnit -> subUnit.definition().id().equals(unit.unit())).findFirst().orElseThrow();
            templates.add(UnitReference.builder()
                    .unit(unit.unit())
                    .file(unit.file())
                    .digest(unit.digest())
                    .origin(source.reference().origin())
                    .build());
        }
        var subUnits = request.subUnits().stream().map(CompositionRequest.SubUnit::reference)
                .filter(reference -> !composedIds.contains(reference.unit()))
                .filter(reference -> composed.subUnits().contains(reference.unit())
                        || DeclarationBounds.ADJUDICATION_UNIT.equals(reference.unit()))
                .toList();
        return CompositionInputs.builder()
                .configHash(request.configHash())
                .templates(templates)
                .subUnits(subUnits)
                .compositionRulesRevision(rules.revision())
                .compositionRulesDigest(rules.digest())
                .planFactsDigest(request.facts().digest())
                .realizedFootprintDigest(request.realizedFootprintDigest())
                .changeType(request.facts().changeType())
                .providersDigest(request.providersDigest())
                .build();
    }

    private static Optional<String> callee(StateNode state) {
        return state.behavior() instanceof StateBehavior.SubWorkflowCall call ? Optional.of(call.subWorkflowId())
                : Optional.empty();
    }

    private static WorkflowDefinition unit(WorkflowDefinition template, PlanInstanceHeader header,
            Map<String, StateNode> states) {
        var inputs = header.inputs();
        var instance = InstanceHeader.builder()
                .planId(header.planId())
                .version(header.version())
                .event(header.event().wireName())
                .inputs(InstanceInputs.builder()
                        .configHash(inputs.configHash())
                        .templates(unitRefs(inputs.templates()))
                        .subUnits(unitRefs(inputs.subUnits()))
                        .compositionRules(new InstanceInputs.CompositionRules(inputs.compositionRulesRevision(),
                                inputs.compositionRulesDigest()))
                        .planFactsDigest(inputs.planFactsDigest())
                        .realizedFootprintDigest(inputs.realizedFootprintDigest())
                        .changeType(inputs.changeType())
                        .providersDigest(inputs.providersDigest())
                        .build())
                .json(HeaderJson.write(header))
                .build();
        return WorkflowDefinition.builder()
                .frontmatter(instanceFrontmatter(template.frontmatter()))
                .id(PLAN_UNIT)
                .version(header.version())
                .scope(template.scope())
                .resultSchema(template.resultSchema())
                .instance(instance)
                .declarations(template.declarations())
                .decisions(template.decisions())
                .formatVersion(template.formatVersion())
                .origin(template.origin())
                .derivedFrom(Map.<String, Integer>of())
                .startState(template.startState())
                .entryBindings(template.entryBindings())
                .migrations(template.migrations())
                .states(states)
                .location(template.location())
                .build();
    }

    /** The frontmatter of a composed version: the template's, with the type {@link UnitType#INSTANCE}. */
    private static UnitFrontmatter instanceFrontmatter(UnitFrontmatter template) {
        return UnitFrontmatter.builder()
                .name(template.name())
                .description(template.description())
                .type(UnitType.INSTANCE)
                .icon(template.icon())
                .entries(template.entries())
                .exits(template.exits())
                .readsConfig(template.readsConfig())
                .readsFact(template.readsFact())
                .inputs(template.inputs())
                .slots(template.slots())
                .modificationDate(template.modificationDate())
                .version(template.version())
                .author(template.author())
                .signature(template.signature())
                .location(template.location())
                .build();
    }

    private static List<InstanceInputs.UnitRef> unitRefs(List<UnitReference> references) {
        return references.stream().map(reference -> InstanceInputs.UnitRef.builder()
                .unit(reference.unit())
                .file(reference.file())
                .digest(reference.digest())
                .origin(UnitReference.wireName(reference.origin()))
                .build())
                .toList();
    }

    /**
     * Runs the checks beyond the tree (Workflow DSL § Static AST Verification Rules, Rules 13 and 14) over the composed
     * plan unit and every unit it calls: every role a {@code job llm} state or a {@code consult role} clause names
     * resolves in the effective role set and fits its use, every quorum declaration lists defined {@code read_only}
     * roles on pairwise different models by their model identity, and every skill a {@code skill} clause names resolves
     * in the project's skill catalogue.
     *
     * @param planUnit the composed plan unit
     * @param composed the composed files and the digest-bound units the version reaches
     * @return the refusal of the first check that fails, empty when every check passes or no check is wired
     */
    private Optional<CompositionResult.Refused> checkRules(WorkflowDefinition planUnit, Composed composed,
            CompositionRequest request) {
        var reachable = reachableUnits(planUnit, composed, request);
        try {
            if (checks.roles() != null) {
                new RoleComposition(checks.roles()).check(roleUses(reachable));
                for (var unit : reachable) {
                    ModelIdentity.check(checks.roles(), quorumsOf(unit), unit.id());
                }
            }
            if (checks.skills() != null) {
                checkSkills(reachable, checks.skills());
            }
        } catch (CompositionRefusal refusal) {
            return Optional.of(new CompositionResult.Refused(refusalOf(refusal.reason()),
                    List.of(refusal.getMessage())));
        }
        return Optional.empty();
    }

    /**
     * @return the composed plan unit, the composed files of the version and the digest-bound sub-units its kept calls
     *         reach
     */
    private static List<WorkflowDefinition> reachableUnits(WorkflowDefinition planUnit, Composed composed,
            CompositionRequest request) {
        var reachable = new ArrayList<WorkflowDefinition>();
        reachable.add(planUnit);
        composed.units().forEach(unit -> reachable.add(unit.definition()));
        request.subUnits().stream()
                .filter(subUnit -> composed.subUnits().contains(subUnit.definition().id())
                        || DeclarationBounds.ADJUDICATION_UNIT.equals(subUnit.definition().id()))
                .forEach(subUnit -> reachable.add(subUnit.definition()));
        return reachable;
    }

    /** @return every role the units name, a model state's own role and the roles it consults */
    private static List<RoleUse> roleUses(List<WorkflowDefinition> units) {
        var uses = new ArrayList<RoleUse>();
        for (var unit : units) {
            for (var state : unit.states().values()) {
                if (state.behavior() instanceof StateBehavior.ModelJob job) {
                    if (job.roleName() != null) {
                        uses.add(RoleUse.forModelJob(state.id(), job.entityKind(), job.roleName()));
                    }
                    job.consultRoles().forEach(role -> uses.add(RoleUse.forConsultation(state.id(), role)));
                }
            }
        }
        return uses;
    }

    /** @return the quorum declarations of a unit, in the order of their decision kinds */
    private static List<ModelIdentity.ListedQuorum> quorumsOf(WorkflowDefinition unit) {
        var quorums = new ArrayList<ModelIdentity.ListedQuorum>();
        unit.decisions().forEach((decisionKind, declaration) -> {
            if (declaration instanceof DecisionDeclaration.Quorum quorum) {
                quorums.add(new ModelIdentity.ListedQuorum(decisionKind, quorum.roles()));
            }
        });
        return quorums;
    }

    /**
     * @throws CompositionRefusal {@code skill_unresolved} at the first {@code skill} clause the project's catalogue
     *                             does not resolve
     */
    private static void checkSkills(List<WorkflowDefinition> units, SkillCatalogue catalogue) {
        for (var unit : units) {
            for (var state : unit.states().values()) {
                var skill = state.skillPath();
                if (skill != null && !catalogue.resolves(skill)) {
                    throw CompositionRefusal.skillUnresolved(skill, state.id());
                }
            }
        }
    }

    private static CompositionResult.Refusal refusalOf(CompositionRefusal.Reason reason) {
        return switch (reason) {
            case ROLE_UNDEFINED -> CompositionResult.Refusal.ROLE_UNDEFINED;
            case CLASS_MISMATCH -> CompositionResult.Refusal.CLASS_MISMATCH;
            case QUORUM_INVALID -> CompositionResult.Refusal.QUORUM_INVALID;
            case QUORUM_NOT_DIVERSE -> CompositionResult.Refusal.QUORUM_NOT_DIVERSE;
            case SKILL_UNRESOLVED -> CompositionResult.Refusal.SKILL_UNRESOLVED;
        };
    }

    /**
     * @return every violation of the header schema, the static rules, the finalize pipeline and the compiled
     *         invariants, each listed once
     */
    private List<String> verify(CompositionRequest request, PlanInstanceHeader header, WorkflowDefinition unit,
            PlanInstanceHeader.@Nullable Lane lane, Map<String, Set<String>> settled) {
        var found = new ArrayList<String>();
        HeaderSchemaCheck.check(header).forEach(violation -> found.add("header '%s': %s"
                .formatted(violation.pointer(), violation.reason())));
        var catalog = new ArrayList<>(context.compiledUnits());
        request.subUnits().forEach(subUnit -> catalog.add(subUnit.definition()));
        var validator = new WorkflowValidator(context.registry(), List.of(), context.entityKinds(),
                context.questionClasses(), context.schemas(), new WorkflowCatalog(catalog), context.configKeys(),
                context.factKinds());
        var result = validator.verify(unit, settled);
        result.violations().forEach(violation -> found.add("Rule %d (%s) in '%s': %s".formatted(
                violation.rule().specificationRule(), violation.rule(), violation.stateId(), violation.message())));
        var keptSteps = header.steps().phase6();
        var pipeline = FinalizePipelineValidator.validate(defaultRoster.stream()
                .filter(step -> keptSteps.contains(step.name())).toList());
        pipeline.forEach(violation -> found.add("pipeline step '%s' (%s): %s".formatted(violation.stepName(),
                violation.rule(), violation.message())));
        invariants.check(unit, catalog, result, pipeline, lane == null ? null : lane.wireName()).stream()
                .filter(violation -> !CompiledInvariants.reReports(violation, unit, result))
                .forEach(violation -> found.add("%s in '%s': %s".formatted(violation.id().wireId(),
                        violation.stateId(), violation.message())));
        return found;
    }
}
