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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import de.planmarshall.core.spi.OutcomeClass;
import de.planmarshall.core.spi.Primitive;
import de.planmarshall.core.spi.PrimitiveRegistry;
import de.planmarshall.workflow.ast.OutcomeTransition;
import de.planmarshall.workflow.ast.Phase;
import de.planmarshall.workflow.ast.SourceLocation;
import de.planmarshall.workflow.ast.StateBehavior;
import de.planmarshall.workflow.ast.StateNode;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.finalize.FinalizeStepDescriptor;
import de.planmarshall.workflow.finalize.PipelineViolation;

/**
 * Checks a project-owned unit against the compiled invariants of {@link InvariantId} (PM-IMPL-5, PM-WF-16).
 * <p>
 * The checks that apply to a unit follow from its identifier ({@link InvariantId#appliesTo}): the general invariants
 * for every unit, the phase invariants for the merged plan unit {@code plan}, and the unit invariants for
 * {@code adjudicate.decisions}, {@code replan}, {@code git.commit_and_push} and the {@code remediation.*} units. The
 * default unit of the same identifier is the reference: where an invariant says that the unit equals the default unit,
 * or that a state lies on every path, the default unit tells what the unit must keep, and the unit may tighten it but
 * never open a path the default unit does not have. One checker class per phase or unit kind holds the checks; this
 * class holds the path helpers they share and runs them.
 * <p>
 * Two invariants take a result computed elsewhere: {@link InvariantId#INV_GEN_1} the result of the static verifier
 * ({@link WorkflowValidator}, Rules 1-12) of the unit, and {@link InvariantId#INV_FIN_2} the result of the finalize
 * pipeline validator over the project's roster. {@link InvariantId#INV_FIN_3} reports the Rule 9 part of the
 * verifier's result. No configuration switches a check off, and no check stops at the first violation.
 *
 * @since 0.1
 */
public final class CompiledInvariants {

    /** The identifier of the merged plan unit. */
    static final String PLAN_UNIT = InvariantId.PLAN_UNIT;

    /** The namespace of the remediation units. */
    static final String REMEDIATION_PREFIX = "remediation.";

    /** The identifier of the commit sub-workflow. */
    static final String GIT_UNIT = "git.commit_and_push";

    /** The reserved outcome of a task state that the engine routes to the phase's fallback when undeclared. */
    static final String LOST = UnitGraph.LOST_OUTCOME;

    /** The control outcome every sub-workflow returns implicitly. */
    static final String STEPPED_OUT = "stepped_out";

    /** The verdict of a Q-Gate task that calls the Q-Gate remediation. */
    static final String BLOCKING = "blocking";

    /** The remediation unit a {@code blocking} Q-Gate verdict calls. */
    static final String QGATE_REMEDIATION = "remediation.qgate-findings";

    /** The phase value of the states of Phase 4. */
    static final String PHASE_PLAN = "plan";

    /** The phase value of the states of Phase 6. */
    static final String PHASE_FINALIZE = "finalize";

    /** The phase value of the states of Phase 5, the {@code land} sub-workflow it calls included. */
    static final String PHASE_EXECUTE = "execute";

    /** The phase values of the states of Phases 1 to 4. */
    static final Set<String> PHASES_BEFORE_EXECUTE = Set.of("init", "refine", "outline", PHASE_PLAN);

    /** The fallback state of Phases 1 to 4. */
    static final String PLAN_RETURN = "plan.return";

    /** The fallback state of Phase 5, the {@code land} lane and Phase 6 before the merge. */
    static final String HAND_BACK = "finalize.hand-back";

    /** The abort terminal of a plan, entered only by the engine. */
    static final String PLAN_ABORTED = "plan.aborted";

    /** A location for a node the tree does not locate. */
    private static final SourceLocation NOWHERE = new SourceLocation("", 0, 0);

    private static final Map<InvariantId, Check> CHECKS = collectChecks();

    private final PrimitiveRegistry registry;

    private final Map<String, WorkflowDefinition> defaultUnits;

    private final List<FinalizeStepDescriptor> defaultRoster;

    private final Subject subject;

    /**
     * @param registry      the primitives the units may name; the waivable gates, awaited events and cycle measures
     *                      of their primitives are read from it
     * @param defaultUnits  the verified default units, the reference of the project units of the same identifier
     * @param defaultRoster the descriptors of the default finalize steps, whose mandatory steps a finalize unit
     *                      completes
     * @param subject       what the checked units are: project replacements of their default units, or composed plan
     *                      instances; it decides whether {@link InvariantId#INV_GEN_1} admits the type
     *                      {@code instance} against a template
     * @throws NullPointerException if an argument or one of its elements is {@code null}
     */
    public CompiledInvariants(PrimitiveRegistry registry, Collection<WorkflowDefinition> defaultUnits,
            List<FinalizeStepDescriptor> defaultRoster, Subject subject) {
        this.registry = Objects.requireNonNull(registry, "registry");
        var units = new LinkedHashMap<String, WorkflowDefinition>();
        for (var unit : Objects.requireNonNull(defaultUnits, "defaultUnits")) {
            units.put(Objects.requireNonNull(unit, "defaultUnit").id(), unit);
        }
        this.defaultUnits = Collections.unmodifiableMap(units);
        this.defaultRoster = List.copyOf(Objects.requireNonNull(defaultRoster, "defaultRoster"));
        this.subject = Objects.requireNonNull(subject, "subject");
    }

    /**
     * What the units a {@link CompiledInvariants} checks are; the caller states it when it builds the checker, so no
     * call of {@link #check} can widen it.
     */
    public enum Subject {

        /**
         * A unit a project owns, which replaces the default unit of the same identifier
         * ({@code ProjectUnitLoader}): its type equals that of the default unit, with no exception.
         */
        PROJECT_REPLACEMENT,

        /**
         * A plan instance composed from the plan unit ({@code PlanInstanceComposer}): it declares the type
         * {@code instance} while its template, the default unit, declares {@code workflow} or {@code recipe}, and
         * {@link InvariantId#INV_GEN_1} admits that one difference of type.
         */
        COMPOSED_INSTANCE
    }

    /**
     * Checks one project unit against every invariant that applies to it.
     *
     * @param unit     the project unit; the merged plan unit for the phase files
     * @param rules    the result of {@link WorkflowValidator} for the unit
     * @param pipeline the violations of the finalize pipeline validator over the project's roster; empty for every
     *                 unit but the plan unit
     * @param lane     the wire name of the plan's lane ({@code light}, {@code deep}, {@code land}, {@code adopt});
     *                 empty where the lane is unknown, as for a template. {@link InvariantId#INV_INIT_2} holds per lane:
     *                 it requires the {@code light -> deep} ratchet only for the lane {@code light} or an unknown lane
     * @return every violation, ordered by the declaration order of its state, violations of the unit as a whole after
     *         every state, and within a state by invariant; empty when the unit satisfies every invariant
     * @throws NullPointerException if an argument is {@code null}
     */
    public List<InvariantViolation> check(WorkflowDefinition unit, VerificationResult rules,
            List<PipelineViolation> pipeline, Optional<String> lane) {
        return check(unit, List.of(), rules, pipeline, lane);
    }

    /**
     * Checks one unit against every invariant that applies to it, expanding the calls it makes over the sub-workflows
     * the caller supplies: the invariants of a plan are decided over the call-resolved graph of the composed version
     * (PM-IMPL-5, PM-WF-16).
     *
     * @param unit     the unit under check
     * @param callable the sub-workflows a call of the unit or of a reachable unit may enter; the unit itself and the
     *                 default units are used besides them
     * @param rules    the result of {@link WorkflowValidator} for the unit
     * @param pipeline the violations of the finalize pipeline validator over the project's roster; empty for every
     *                 unit but the plan unit
     * @param lane     the wire name of the plan's lane ({@code light}, {@code deep}, {@code land}, {@code adopt});
     *                 empty where the lane is unknown, as for a template. {@link InvariantId#INV_INIT_2} holds per lane:
     *                 it requires the {@code light -> deep} ratchet only for the lane {@code light} or an unknown lane
     * @return every violation, ordered by the declaration order of its state, violations of the unit as a whole after
     *         every state, and within a state by invariant; empty when the unit satisfies every invariant
     * @throws NullPointerException if an argument is {@code null}
     */
    public List<InvariantViolation> check(WorkflowDefinition unit, Collection<WorkflowDefinition> callable,
            VerificationResult rules, List<PipelineViolation> pipeline, Optional<String> lane) {
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(callable, "callable");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(pipeline, "pipeline");
        Objects.requireNonNull(lane, "lane");
        var reference = defaultUnits.get(unit.id());
        if (reference == null) {
            var found = new ArrayList<>(ruleViolations(unit, rules));
            found.add(new InvariantViolation(InvariantId.INV_GEN_1, unit.id(), unit.location(),
                    "there is no default unit '%s' to compare the unit with".formatted(unit.id())));
            return ordered(unit, found);
        }
        var context = new Context(unit, reference, defaultUnits, callable, rules, List.copyOf(pipeline), registry,
                defaultRoster, subject, lane);
        for (var id : InvariantId.values()) {
            if (id.appliesTo(unit.id())) {
                CHECKS.get(id).run(context);
            }
        }
        return context.ordered();
    }

    /**
     * @return every invariant that has a check; equal to all constants of {@link InvariantId}
     */
    public static Set<InvariantId> checkedIds() {
        return Collections.unmodifiableSet(EnumSet.copyOf(CHECKS.keySet()));
    }

    /**
     * The budget parameters of the remediation schema ({@link InvariantId#INV_REM_1}): {@code retry_budget},
     * {@code fix_budget} and {@code waiver_wait_seconds}, the parameters whose binding at a call site
     * {@link InvariantId#INV_REM_5} caps. This is the one definition of the set; a consumer outside the package, such
     * as the check of the composition rule file that refuses a rule binding a budget, reads it here rather than
     * repeating the names, so a budget parameter added to the schema is refused there without a second edit.
     *
     * @return the names of the budget parameters, an unmodifiable set
     */
    public static Set<String> remediationBudgetParameters() {
        return RemediationInvariants.BUDGET_CAPS.keySet();
    }

    /**
     * Maps the violations of the static verifier to invariant violations: a Rule 9 violation of the plan unit to
     * {@link InvariantId#INV_FIN_3}, every other one to {@link InvariantId#INV_GEN_1}.
     *
     * @param unit  the unit
     * @param rules the result of the static verifier for the unit
     * @return the mapped violations, in the order of the result
     */
    static List<InvariantViolation> ruleViolations(WorkflowDefinition unit, VerificationResult rules) {
        return rules.violations().stream().map(violation -> {
            var id = PLAN_UNIT.equals(unit.id()) && violation.rule() == VerificationRule.STEP_COMPLETION
                    ? InvariantId.INV_FIN_3 : InvariantId.INV_GEN_1;
            return new InvariantViolation(id, violation.stateId(), violation.location(), "Rule %d (%s): %s".formatted(
                    violation.rule().specificationRule(), violation.rule(), violation.message()));
        }).toList();
    }

    /**
     * Tells whether a violation only re-reports a finding of another check: a violation of the static verifier
     * ({@link InvariantId#INV_GEN_1}, {@link InvariantId#INV_FIN_3}), of the finalize pipeline validator
     * ({@link InvariantId#INV_FIN_2}) or of the declaration bounds ({@link InvariantId#INV_ADJ_1},
     * {@link InvariantId#INV_REPLAN_1}). A caller that lists the findings of those checks itself drops these
     * violations, so no finding is listed twice.
     *
     * @param violation a violation {@link #check} returned for the unit
     * @param unit      the unit
     * @param rules     the result of the static verifier the check was given
     * @return {@code true} when the violation repeats a finding of the verifier, the pipeline validator or the
     *         declaration bounds
     * @throws NullPointerException if an argument is {@code null}
     */
    public static boolean reReports(InvariantViolation violation, WorkflowDefinition unit, VerificationResult rules) {
        Objects.requireNonNull(violation, "violation");
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(rules, "rules");
        return violation.id() == InvariantId.INV_FIN_2 || ruleViolations(unit, rules).contains(violation)
                || declarationViolations(violation.id(), unit).contains(violation);
    }

    /**
     * Maps the findings of {@link DeclarationBounds} to violations of {@link InvariantId#INV_ADJ_1} or
     * {@link InvariantId#INV_REPLAN_1}, each at the line of its declaration.
     *
     * @param id   the invariant the findings are reported under
     * @param unit the unit
     * @return the mapped findings in the order of the check; empty for any other invariant
     */
    static List<InvariantViolation> declarationViolations(InvariantId id, WorkflowDefinition unit) {
        if (id != InvariantId.INV_ADJ_1 && id != InvariantId.INV_REPLAN_1) {
            return List.of();
        }
        return DeclarationBounds.check(unit).stream()
                .map(finding -> new InvariantViolation(id, unit.id(), finding.location(),
                        "%s '%s' (bound %s): %s".formatted(finding.code(), finding.name(), finding.bound(),
                                finding.message())))
                .toList();
    }

    private static Map<InvariantId, Check> collectChecks() {
        var checks = new EnumMap<InvariantId, Check>(InvariantId.class);
        Stream.of(GeneralInvariants.checks(), InitInvariants.checks(), RefineInvariants.checks(),
                OutlineInvariants.checks(), PlanInvariants.checks(), ExecuteInvariants.checks(),
                FinalizeInvariants.checks(), SubWorkflowInvariants.checks(), RemediationInvariants.checks())
                .forEach(group -> group.forEach((id, check) -> {
                    if (checks.putIfAbsent(id, check) != null) {
                        throw new IllegalStateException("the invariant %s has two checks".formatted(id));
                    }
                }));
        var missing = EnumSet.complementOf(EnumSet.copyOf(checks.keySet()));
        if (!missing.isEmpty()) {
            throw new IllegalStateException("the invariants %s have no check".formatted(missing));
        }
        return Collections.unmodifiableMap(checks);
    }

    private static List<InvariantViolation> ordered(WorkflowDefinition unit, List<InvariantViolation> found) {
        return ordered(unit.states().values(), found);
    }

    private static List<InvariantViolation> ordered(Collection<StateNode> states, List<InvariantViolation> found) {
        Map<String, Integer> declarationIndex = new HashMap<>();
        for (var state : states) {
            declarationIndex.put(state.id(), declarationIndex.size());
        }
        var afterEveryState = declarationIndex.size();
        var sorted = new ArrayList<>(found);
        sorted.sort(Comparator
                .comparingInt((InvariantViolation violation) -> declarationIndex.getOrDefault(violation.stateId(),
                        afterEveryState))
                .thenComparing(InvariantViolation::id));
        return List.copyOf(sorted);
    }

    /**
     * The check of one invariant over a unit and its reference.
     */
    @FunctionalInterface
    interface Check {

        /**
         * Runs the check and reports every violation to the context.
         *
         * @param context the unit, its reference and the inputs of the checks
         */
        void run(Context context);
    }

    /**
     * The unit under check, its default unit as reference, the inputs of the checks, the violations found so far, and
     * the path helpers the checkers share. A path is a path of the transition graph from a starting state, the start
     * state or an entry-bound state; a state <em>dominates</em> another when every path from a starting state to the
     * other passes it.
     */
    static final class Context {

        private final WorkflowDefinition unit;

        private final WorkflowDefinition reference;

        private final CallGraphExpansion expansion;

        private final CallGraphExpansion referenceExpansion;

        private final UnitGraph graph;

        private final UnitGraph referenceGraph;

        private final VerificationResult rules;

        private final List<PipelineViolation> pipeline;

        private final PrimitiveRegistry registry;

        private final List<FinalizeStepDescriptor> defaultRoster;

        private final Subject subject;

        private final Optional<String> lane;

        private final List<InvariantViolation> found = new ArrayList<>();

        Context(WorkflowDefinition unit, WorkflowDefinition reference, Map<String, WorkflowDefinition> defaults,
                Collection<WorkflowDefinition> callable, VerificationResult rules, List<PipelineViolation> pipeline,
                PrimitiveRegistry registry, List<FinalizeStepDescriptor> defaultRoster, Subject subject,
                Optional<String> lane) {
            this.unit = unit;
            this.reference = reference;
            var catalog = new LinkedHashMap<String, WorkflowDefinition>();
            defaults.forEach(catalog::putIfAbsent);
            callable.forEach(definition -> catalog.putIfAbsent(definition.id(), definition));
            catalog.put(unit.id(), unit);
            this.expansion = expands(unit.id()) ? CallGraphExpansion.of(unit, catalog) : CallGraphExpansion.single(unit);
            this.referenceExpansion = expands(reference.id()) ? CallGraphExpansion.of(reference, catalog)
                    : CallGraphExpansion.single(reference);
            this.graph = expansion.graph();
            this.referenceGraph = referenceExpansion.graph();
            this.rules = rules;
            this.pipeline = pipeline;
            this.registry = registry;
            this.defaultRoster = defaultRoster;
            this.subject = subject;
            this.lane = lane;
        }

        private static boolean expands(String unitId) {
            return Phase.PLAN_UNIT.equals(unitId);
        }

        /**
         * @return {@code true} when the unit is a composed plan instance, not a project replacement
         */
        boolean composedInstance() {
            return subject == Subject.COMPOSED_INSTANCE;
        }

        /**
         * @return the wire name of the plan's lane; empty where the lane is unknown
         */
        Optional<String> lane() {
            return lane;
        }

        WorkflowDefinition unit() {
            return unit;
        }

        WorkflowDefinition reference() {
            return reference;
        }

        UnitGraph graph() {
            return graph;
        }

        UnitGraph referenceGraph() {
            return referenceGraph;
        }

        VerificationResult rules() {
            return rules;
        }

        List<PipelineViolation> pipeline() {
            return pipeline;
        }

        List<FinalizeStepDescriptor> defaultRoster() {
            return defaultRoster;
        }

        List<InvariantViolation> found() {
            return found;
        }

        // ----- reporting

        void report(InvariantId id, String stateId, SourceLocation location, String message) {
            found.add(new InvariantViolation(id, stateId, location, message));
        }

        void reportState(InvariantId id, StateNode state, String message) {
            report(id, state.id(), state.location(), message);
        }

        void reportTransition(InvariantId id, StateNode state, OutcomeTransition transition, String message) {
            report(id, state.id(), transition.location(), message);
        }

        void reportUnit(InvariantId id, String message) {
            report(id, unit.id(), unit.location(), message);
        }

        // ----- states

        /**
         * @param stateId the identifier of a state of the expansion: the unit under check, or a sub-workflow it reaches
         * @return the state, when the expansion declares it
         */
        Optional<StateNode> state(String stateId) {
            return Optional.ofNullable(expansion.states().get(stateId));
        }

        /**
         * @param stateId the identifier of a state the unit under check declares itself
         * @return the state, when the unit declares it
         */
        Optional<StateNode> ownState(String stateId) {
            return Optional.ofNullable(unit.states().get(stateId));
        }

        /**
         * @param stateId the identifier of a state of the reference's expansion
         * @return the state of the default unit or of a sub-workflow it reaches
         */
        Optional<StateNode> referenceState(String stateId) {
            return Optional.ofNullable(referenceExpansion.states().get(stateId));
        }

        /**
         * @param stateId the identifier of a state the default unit declares itself
         * @return the state, when the default unit declares it
         */
        Optional<StateNode> referenceOwnState(String stateId) {
            return Optional.ofNullable(reference.states().get(stateId));
        }

        /** @return every state of the expansion, the unit under check first, then its callees in discovery order */
        Collection<StateNode> states() {
            return expansion.states().values();
        }

        /** @return the states the unit under check declares itself */
        Collection<StateNode> ownStates() {
            return unit.states().values();
        }

        /** @return the identifier of the unit that declares each state of the expansion */
        Map<String, String> owners() {
            return expansion.owners();
        }

        /** @return the units of the expansion by identifier, the unit under check first */
        Map<String, WorkflowDefinition> expandedUnits() {
            return expansion.units();
        }

        /**
         * @param stateId the identifier of a state
         * @return the identifier of the unit that declares the state; empty when the expansion has no such state
         */
        Optional<String> ownerOf(String stateId) {
            return Optional.ofNullable(expansion.owners().get(stateId));
        }

        /**
         * Decides the call-resolved source of an edge: a transition out of a call site is the callee's return, so the
         * state that carries it is the callee terminal that reports the outcome.
         *
         * @param source     the state the edge leaves
         * @param transition the transition
         * @param stateId    the state the returned path must pass, the terminal itself included
         * @return {@code true} when the edge leaves a call site and every terminal of the callee that reports the
         *         outcome is the given state or is dominated by it
         */
        boolean returnsThrough(StateNode source, OutcomeTransition transition, String stateId) {
            if (!(source.behavior() instanceof StateBehavior.SubWorkflowCall call)) {
                return false;
            }
            var callee = expansion.units().get(call.subWorkflowId());
            if (callee == null) {
                return false;
            }
            var terminals = callee.states().values().stream()
                    .filter(state -> state.terminalOutcome()
                            .map(outcome -> outcome.id().equals(transition.outcomeName())).orElse(false))
                    .toList();
            return !terminals.isEmpty() && terminals.stream()
                    .allMatch(state -> stateId.equals(state.id()) || dominates(stateId, state.id()));
        }

        /** @return every state of the expansion by identifier, the unit under check first */
        Map<String, StateNode> stateMap() {
            return expansion.states();
        }

        /** @return every state of the reference's expansion by identifier */
        Map<String, StateNode> referenceStateMap() {
            return referenceExpansion.states();
        }

        /** @return every state of the reference's expansion */
        Collection<StateNode> referenceStates() {
            return referenceExpansion.states().values();
        }

        /** @return the states the reference declares itself */
        Collection<StateNode> referenceOwnStates() {
            return reference.states().values();
        }

        /**
         * The phase of a state (PM-WF-16): the phase sub-workflow on its call path declares it, so a state of a phase
         * unit carries that phase, and a state of a unit called within a phase carries the phase of its frame.
         *
         * @param state a state
         * @return the wire name of the phase; empty for a unit that carries none, such as a workspace or epic unit
         */
        Optional<String> phaseOf(StateNode state) {
            return phaseOf(state.id());
        }

        /**
         * @param stateId the identifier of a state
         * @return the short name of the phase of the state, as {@code init} to {@code finalize}; empty for a unit that
         *         carries none
         */
        Optional<String> phaseOf(String stateId) {
            var owner = expansion.owners().get(stateId);
            var phase = owner == null ? null : expansion.phases().get(owner);
            if (phase != null) {
                return Optional.of(phase.name().toLowerCase(java.util.Locale.ROOT));
            }
            return Phase.ofCallState(stateId).map(found -> found.name().toLowerCase(java.util.Locale.ROOT));
        }

        /**
         * @param state a state
         * @param phase the short name of a phase
         * @return {@code true} when the state lies in that phase
         */
        boolean inPhase(StateNode state, String phase) {
            return phaseOf(state).filter(phase::equals).isPresent();
        }

        /**
         * @param state  a state
         * @param phases the wire names of a set of phases
         * @return {@code true} when the state lies in one of them
         */
        boolean inAnyPhase(StateNode state, Set<String> phases) {
            return phaseOf(state).filter(phases::contains).isPresent();
        }

        /**
         * @param definition a unit
         * @param suffix     the last segment of a state identifier, for example {@code decide}
         * @return the states whose identifier ends with a dot and the suffix, in declaration order
         */
        static List<StateNode> statesWithSuffix(WorkflowDefinition definition, String suffix) {
            return definition.states().values().stream().filter(state -> state.id().endsWith("." + suffix)).toList();
        }

        Optional<StateNode> stateWithSuffix(String suffix) {
            return statesWithSuffix(unit, suffix).stream().findFirst();
        }

        Optional<StateNode> referenceStateWithSuffix(String suffix) {
            return statesWithSuffix(reference, suffix).stream().findFirst();
        }

        /**
         * @param state a state
         * @return the primitive the state runs as {@code auto}, {@code job} or {@code composite_job}, when registered
         */
        Optional<Primitive<?, ?>> primitive(StateNode state) {
            return primitiveId(state).flatMap(registry::find);
        }

        /**
         * @param state a state
         * @return the primitive identifier of an {@code auto}, {@code job} or {@code composite_job} state
         */
        static Optional<String> primitiveId(StateNode state) {
            return switch (state.behavior()) {
                case StateBehavior.AutoStep auto -> Optional.of(auto.primitiveId());
                case StateBehavior.JobStep job -> Optional.of(job.primitiveId());
                case StateBehavior.InteractiveLinks _, StateBehavior.ModelJob _, StateBehavior.SubWorkflowCall _,
                    StateBehavior.QuestionWait _, StateBehavior.TerminalState _ -> Optional.empty();
            };
        }

        /**
         * @param state a state
         * @return the identifier of the sub-workflow a {@code call} state calls
         */
        static Optional<String> callee(StateNode state) {
            return state.behavior() instanceof StateBehavior.SubWorkflowCall call ? Optional.of(call.subWorkflowId())
                    : Optional.empty();
        }

        static boolean isTerminal(StateNode state) {
            return state.behavior() instanceof StateBehavior.TerminalState;
        }

        static boolean isTerminalOfClass(StateNode state, OutcomeClass outcomeClass) {
            return isTerminal(state) && state.terminalOutcome().map(outcome -> outcome.outcomeClass() == outcomeClass)
                    .orElse(false);
        }

        /**
         * @param state a state
         * @return its transitions, those of its declared links included, in declaration order
         */
        static List<OutcomeTransition> transitions(StateNode state) {
            return UnitGraph.allTransitions(state.behavior());
        }

        static List<OutcomeTransition> transitionsOn(StateNode state, String outcome) {
            return transitions(state).stream().filter(transition -> outcome.equals(transition.outcomeName())).toList();
        }

        /**
         * @param definition a unit
         * @return every transition of the unit as its source state and the transition, in declaration order
         */
        static List<Edge> edges(WorkflowDefinition definition) {
            var edges = new ArrayList<Edge>();
            for (var state : definition.states().values()) {
                for (var transition : transitions(state)) {
                    edges.add(new Edge(state, transition));
                }
            }
            return edges;
        }

        /**
         * @return every transition of the expansion, each with its source state, the unit under check's first
         */
        List<Edge> allEdges() {
            return expansion.edges();
        }

        /**
         * @return every transition of the reference's expansion
         */
        List<Edge> referenceEdges() {
            return referenceExpansion.edges();
        }

        /**
         * @return {@code true} when the expansion has a transition from the source on the outcome into the target
         */
        boolean hasTransition(String source, String outcome, String target) {
            return expansion.hasTransition(source, outcome, target);
        }

        /**
         * @return {@code true} when the reference's expansion has a transition from the source on the outcome into the
         *         target
         */
        boolean referenceHasTransition(String source, String outcome, String target) {
            return referenceExpansion.hasTransition(source, outcome, target);
        }

        /**
         * @param definition a unit
         * @param source     the source state
         * @param outcome    the outcome
         * @param target     the target state
         * @return {@code true} when the unit has a transition from the source on the outcome into the target
         */
        static boolean hasTransition(WorkflowDefinition definition, String source, String outcome, String target) {
            var state = definition.states().get(source);
            return state != null && transitions(state).stream()
                    .anyMatch(transition -> outcome.equals(transition.outcomeName())
                            && target.equals(transition.targetStateId()));
        }

        // ----- paths

        /**
         * @param graph  the graph of a unit
         * @param start  the state to start from
         * @param avoid  the states a path may reach but not pass
         * @return every state reachable from the start on a path that passes none of the avoided states; an avoided
         *         state that such a path reaches is included, but not expanded
         */
        static Set<String> avoiding(UnitGraph graph, String start, Set<String> avoid) {
            return avoiding(graph, List.of(start), avoid);
        }

        /**
         * @param graph  the graph of a unit
         * @param starts the states to start from: a unit's start state or every entry-bound state
         * @param avoid  the states a path may reach but not pass
         * @return every state reachable from one of the starts on a path that passes none of the avoided states; an
         *         avoided state that such a path reaches, a start among them, is included, but not expanded
         */
        static Set<String> avoiding(UnitGraph graph, Collection<String> starts, Set<String> avoid) {
            var seen = new LinkedHashSet<>(starts);
            var queue = new ArrayDeque<String>();
            starts.stream().filter(start -> !avoid.contains(start)).forEach(queue::add);
            while (!queue.isEmpty()) {
                var current = queue.poll();
                for (var next : graph.successors(current)) {
                    if (seen.add(next) && !avoid.contains(next)) {
                        queue.add(next);
                    }
                }
            }
            return seen;
        }

        /**
         * @return {@code true} when every path of the unit from its starting states to the target passes the
         *         dominator
         */
        boolean dominates(String dominator, String target) {
            return dominates(graph, unit.startingStates(), dominator, target);
        }

        /**
         * @return {@code true} when every path of the reference from its starting states to the target passes the
         *         dominator
         */
        boolean referenceDominates(String dominator, String target) {
            return dominates(referenceGraph, reference.startingStates(), dominator, target);
        }

        static boolean dominates(UnitGraph graph, String start, String dominator, String target) {
            return dominates(graph, List.of(start), dominator, target);
        }

        static boolean dominates(UnitGraph graph, Collection<String> starts, String dominator, String target) {
            return dominator.equals(target) || !avoiding(graph, starts, Set.of(dominator)).contains(target);
        }

        /**
         * @return {@code true} when every path of the graph from the start to one of the targets passes the dominator
         */
        static boolean dominatesAll(UnitGraph graph, String start, String dominator, Set<String> targets) {
            return dominatesAll(graph, List.of(start), dominator, targets);
        }

        /**
         * @return {@code true} when every path of the graph from one of the starts to one of the targets passes the
         *         dominator
         */
        static boolean dominatesAll(UnitGraph graph, Collection<String> starts, String dominator,
                Set<String> targets) {
            var reached = avoiding(graph, starts, Set.of(dominator));
            return targets.stream().noneMatch(target -> !target.equals(dominator) && reached.contains(target));
        }

        /**
         * @return the states of the unit reachable from its starting states, its start state or every entry-bound
         *         state
         */
        Set<String> reachable() {
            return graph.forwardClosure(Set.copyOf(unit.startingStates()));
        }

        // ----- shared checks against the reference

        /**
         * Requires a state of the reference to be a state of the unit.
         *
         * @param id      the invariant the check belongs to
         * @param stateId the state
         * @return {@code true} when the state is present in the unit or absent from the reference
         */
        boolean requirePresent(InvariantId id, String stateId) {
            if (referenceExpansion.states().containsKey(stateId) && !expansion.states().containsKey(stateId)) {
                reportUnit(id, "the state '%s' of the default unit is missing".formatted(stateId));
                return false;
            }
            return true;
        }

        /**
         * Requires a dominance of the reference to hold in the unit: when every path of the reference into the target
         * passes the dominator, the dominator is a state of the unit and every path of the unit into the target passes
         * it.
         *
         * @param id        the invariant the check belongs to
         * @param dominator the state that lies on every path
         * @param target    the state the paths lead into
         */
        void requireDominance(InvariantId id, String dominator, String target) {
            if (!referenceExpansion.states().containsKey(dominator)
                    || !referenceExpansion.states().containsKey(target) || !referenceDominates(dominator, target)) {
                return;
            }
            if (!requirePresent(id, dominator)) {
                return;
            }
            state(target).filter(targetState -> !dominates(dominator, targetState.id()))
                    .ifPresent(targetState -> reportState(id, targetState,
                            "a path reaches '%s' without passing '%s', which every path of the default unit passes"
                                    .formatted(target, dominator)));
        }

        /**
         * Requires the states that dominate every target in the reference to dominate every target in the unit: each
         * candidate of the reference that lies on every path of the reference into its targets is a state of the unit
         * and lies on every path of the unit into its targets. A candidate the unit misses is reported at the unit
         * header, a target reached without it at the first such target in declaration order.
         *
         * @param id         the invariant the check belongs to
         * @param targets    the targets, chosen in the unit and in the reference alike
         * @param candidates the states considered as dominators
         * @return the dominators of the reference, in its declaration order
         */
        List<String> requireDominatorsOf(InvariantId id, Predicate<StateNode> targets,
                Predicate<StateNode> candidates) {
            var referenceStarts = reference.startingStates();
            var referenceReach = referenceGraph.forwardClosure(Set.copyOf(referenceStarts));
            var referenceTargets = idsOf(referenceExpansion.states().values(),
                    state -> targets.test(state) && referenceReach.contains(state.id()));
            if (referenceTargets.isEmpty()) {
                return List.of();
            }
            var unitTargets = idsOf(expansion.states().values(), targets);
            var dominators = new ArrayList<String>();
            for (var candidate : referenceExpansion.states().values()) {
                if (referenceStarts.contains(candidate.id()) || referenceTargets.contains(candidate.id())
                        || !candidates.test(candidate) || !referenceReach.contains(candidate.id())
                        || !dominatesAll(referenceGraph, referenceStarts, candidate.id(), referenceTargets)) {
                    continue;
                }
                dominators.add(candidate.id());
                if (!requirePresent(id, candidate.id())) {
                    continue;
                }
                var reached = avoiding(graph, unit.startingStates(), Set.of(candidate.id()));
                unitTargets.stream().filter(target -> !target.equals(candidate.id()) && reached.contains(target))
                        .findFirst()
                        .ifPresent(target -> reportState(id, expansion.states().get(target),
                                "a path reaches '%s' without passing '%s', which every path of the default unit passes"
                                        .formatted(target, candidate.id())));
            }
            return dominators;
        }

        /**
         * Requires the routes around a gate to be those of the reference: every transition of a path from the start
         * state into the target that passes no gate state is a transition of the reference on such a path too. A
         * unit may drop a route around the gate, never add one.
         *
         * @param id     the invariant the check belongs to
         * @param gates  the gate states; a path that passes one of them is not a route around the gates
         * @param target the state the routes lead into
         */
        void requireNoNewBypass(InvariantId id, Set<String> gates, String target) {
            if (!expansion.states().containsKey(target)) {
                return;
            }
            var around = avoiding(graph, unit.startingStates(), gates);
            if (!around.contains(target) || gates.contains(target)) {
                return;
            }
            var leadsIn = backwardWithin(graph, target, around, gates);
            var referenceAround = avoiding(referenceGraph, reference.startingStates(), gates);
            for (var sourceId : leadsIn) {
                if (sourceId.equals(target)) {
                    continue;
                }
                var source = expansion.states().get(sourceId);
                if (source == null) {
                    continue;
                }
                for (var transition : transitions(source)) {
                    var next = transition.targetStateId();
                    if (!leadsIn.contains(next)) {
                        continue;
                    }
                    var known = referenceAround.contains(sourceId)
                            && referenceHasTransition(sourceId, transition.outcomeName(), next);
                    if (!known) {
                        reportTransition(id, source, transition,
                                "the transition '%s' -> '%s' opens a route into '%s' that passes none of %s; the default"
                                        .formatted(transition.outcomeName(), next, target, sorted(gates))
                                        + " unit has no such route");
                    }
                }
            }
        }

        /**
         * @return the states of the region from which the target is reachable inside the region, the target included;
         *         a gate state is never part of a path
         */
        private static Set<String> backwardWithin(UnitGraph graph, String target, Set<String> region,
                Set<String> gates) {
            var seen = new LinkedHashSet<String>();
            seen.add(target);
            var queue = new ArrayDeque<String>();
            queue.add(target);
            while (!queue.isEmpty()) {
                var current = queue.poll();
                for (var previous : graph.predecessors(current)) {
                    if (region.contains(previous) && !gates.contains(previous) && seen.add(previous)) {
                        queue.add(previous);
                    }
                }
            }
            return seen;
        }

        static Set<String> idsOf(WorkflowDefinition definition, Predicate<StateNode> filter) {
            var ids = new LinkedHashSet<String>();
            definition.states().values().stream().filter(filter).forEach(state -> ids.add(state.id()));
            return ids;
        }

        static Set<String> idsOf(Collection<StateNode> states, Predicate<StateNode> filter) {
            var ids = new LinkedHashSet<String>();
            states.stream().filter(filter).forEach(state -> ids.add(state.id()));
            return ids;
        }

        static List<String> sorted(Set<String> ids) {
            return ids.stream().sorted().toList();
        }

        static SourceLocation nowhere() {
            return NOWHERE;
        }

        /**
         * @return the violations found so far, in the declaration order of the expanded graph
         */
        List<InvariantViolation> ordered() {
            return CompiledInvariants.ordered(states(), found);
        }
    }

    /**
     * One transition of a unit with its source state.
     *
     * @param source     the state the transition leaves
     * @param transition the transition
     */
    record Edge(StateNode source, OutcomeTransition transition) {

        String key() {
            return source.id() + " " + transition.outcomeName() + " -> " + transition.targetStateId();
        }
    }
}
