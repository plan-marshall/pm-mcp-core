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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import de.planmarshall.core.spi.AwaitedEvent;
import de.planmarshall.core.spi.CycleMeasure;
import de.planmarshall.core.spi.FreeParam;
import de.planmarshall.core.spi.OutcomeClass;
import de.planmarshall.core.spi.Primitive;
import de.planmarshall.core.spi.PrimitiveOutcome;
import de.planmarshall.core.spi.PrimitiveRegistry;
import de.planmarshall.workflow.ast.CallValue;
import de.planmarshall.workflow.ast.LinkAction;
import de.planmarshall.workflow.ast.LinkForm;
import de.planmarshall.workflow.ast.LinkRelation;
import de.planmarshall.workflow.ast.LinkSpec;
import de.planmarshall.workflow.ast.OutcomeTransition;
import de.planmarshall.workflow.ast.ParamDecl;
import de.planmarshall.workflow.ast.ParamSpec;
import de.planmarshall.workflow.ast.ParamType;
import de.planmarshall.workflow.ast.ResultGuard;
import de.planmarshall.workflow.ast.SourceLocation;
import de.planmarshall.workflow.ast.StateBehavior;
import de.planmarshall.workflow.ast.StateMigration;
import de.planmarshall.workflow.ast.StateNode;
import de.planmarshall.workflow.ast.StepCompletion;
import de.planmarshall.workflow.ast.TerminalOutcome;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.ast.WorkflowScope;

/**
 * The static verifier of one workflow unit (PM-IMPL-5, PM-TEST-3): it runs every check of {@link VerificationRule}
 * over a parsed {@link WorkflowDefinition} and returns every violation it finds as a value.
 * <p>
 * The verifier reads nothing on its own: the primitives a unit may name come from the {@link PrimitiveRegistry} and
 * the released state names from the baseline its caller passes. No check stops at the first violation, so the result
 * lists all of them, ordered by the declaration order of their state and then by rule.
 * <p>
 * Reachability is graph reachability over every transition.
 * <p>
 * Rule 9 judges the firings of a unit one step at a time: a firing of a step ends at the next opening of the same
 * step, at {@code finalize.merge-gate} or at a terminal state, and passes the opening of another step without ending,
 * so a step completed again after another step's opening is completed twice in one firing (ADR 021). The cycle
 * measures {@code STEP_LIST} and {@code ARCHIVE_STEPS} are checked as the specification states them (ADR 022).
 *
 * @since 0.1
 */
public final class WorkflowValidator {

    /**
     * The abort terminals. The engine enters them by the operator's abort, a transition no unit declares, so they
     * count as reachable from every state and are exempt from {@link VerificationRule#REACHABILITY}.
     */
    private static final Set<String> ABORT_TERMINALS = Set.of("plan.aborted", "epic.aborted");

    /*
     * The states the specification names for the firing model of Rule 9 (Workflow DSL, Static AST Verification
     * Rules, Rule 9 "Once per firing"). They are the only state ids the firing model names; every other opening is
     * derived from the unit (its start state and the link targets of its STEP_LIST hub), see ADR 007.
     */

    /** The region entry states, each the beginning state of the steps that share it. */
    private static final Set<String> REGION_ENTRY_STATES = Set.of("finalize.review-cap-check",
            "finalize.merge-evidence-wait");

    /** The state of order 70; a firing ends on entry into it. */
    private static final String MERGE_GATE = "finalize.merge-gate";

    /** The states whose entry records every begun step whose firing is still open as {@code failed}. */
    private static final Set<String> IMPLICIT_FAILED_STATES = Set.of("plan.aborted", "plan.return",
            "finalize.hand-back");

    /** The namespace of the remediation units, the only callees whose {@code waived} outcome completes a step. */
    private static final String REMEDIATION_UNIT_PREFIX = "remediation.";

    /**
     * The last segment of the budget re-check of a remediation unit, {@code remediation-<class>.budget-check}; the
     * remediation invariants find the state by the same suffix.
     */
    private static final String BUDGET_CHECK_SUFFIX = ".budget-check";

    /** The primitive the budget re-check of a remediation unit runs. */
    private static final String CHECK_BUDGETS = "remediation.check-budgets";

    /** The control outcome every sub-workflow returns implicitly (Rule 6). */
    private static final String STEPPED_OUT = "stepped_out";

    /** The fallback states of the plan phases, the only targets of {@code stepped_out} in a plan unit (Rule 6). */
    private static final Set<String> PHASE_FALLBACK_STATES = Set.of("plan.return", "finalize.hand-back");

    private static final Set<String> CONDITION_KINDS = Set.of(
            "integrity_breach", "footprint_violation", "job_incomplete",
            "config_drift", "guard_indeterminate", "precondition_unmet");

    private static final List<String> RESERVED_TARGET_SUFFIXES = List.of(
            ".blocked", ".footprint-violation", ".config-drift");

    /** The identifier of the link the engine renders on every link state of a sub-workflow. */
    private static final String STEP_OUT_LINK = "step_out";

    private static final Pattern ROLE_NAME_PATTERN = Pattern.compile("^[a-z][a-z0-9-]{1,31}$");

    private static final Set<String> RESERVED_KEYWORDS = Set.of("workflow", "version", "format", "derived_from",
            "scope", "param", "result", "declare", "decide", "start", "migrate", "state", "terminal", "phase",
            "inherit", "skill", "facts", "auto", "job", "composite_job", "call", "question", "link", "guard",
            "depends_on_config", "do", "llm", "role", "each", "consult", "completes", "as", "and", "or", "not",
            "in_worktree", "has_open_findings", "all_tasks_completed", "predicate", "text", "int", "enum", "schema",
            "list");

    private final PrimitiveRegistry registry;

    private final List<String> releasedStates;

    private final EntityKindRegistry entityKinds;

    private final QuestionClassRegistry questionClasses;

    private final SchemaRegistry schemas;

    private final WorkflowCatalog catalog;

    /**
     * @param registry       the primitives a unit may name
     * @param releasedStates the state names of earlier releases, in the order they are reported
     * @throws NullPointerException if an argument or a released state name is {@code null}
     */
    public WorkflowValidator(PrimitiveRegistry registry, Collection<String> releasedStates) {
        this(registry, releasedStates, EntityKindRegistry.defaults(), QuestionClassRegistry.defaults(),
                SchemaRegistry.defaults(), WorkflowCatalog.empty());
    }

    /**
     * @param registry        the primitives a unit may name
     * @param releasedStates  the state names of earlier releases, in the order they are reported
     * @param entityKinds     the registered entity kinds
     * @param questionClasses the registered question classes
     * @param schemas         the registered named parameter schemas
     * @throws NullPointerException if an argument or a released state name is {@code null}
     */
    public WorkflowValidator(PrimitiveRegistry registry, Collection<String> releasedStates,
            EntityKindRegistry entityKinds, QuestionClassRegistry questionClasses, SchemaRegistry schemas) {
        this(registry, releasedStates, entityKinds, questionClasses, schemas, WorkflowCatalog.empty());
    }

    /**
     * @param registry        the primitives a unit may name
     * @param releasedStates  the state names of earlier releases, in the order they are reported
     * @param entityKinds     the registered entity kinds
     * @param questionClasses the registered question classes
     * @param schemas         the registered named parameter schemas
     * @param catalog         the registered sub-workflow catalog
     * @throws NullPointerException if an argument or a released state name is {@code null}
     */
    public WorkflowValidator(PrimitiveRegistry registry, Collection<String> releasedStates,
            EntityKindRegistry entityKinds, QuestionClassRegistry questionClasses, SchemaRegistry schemas,
            WorkflowCatalog catalog) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.releasedStates = List.copyOf(new LinkedHashSet<>(List.copyOf(releasedStates)));
        this.entityKinds = Objects.requireNonNull(entityKinds, "entityKinds");
        this.questionClasses = Objects.requireNonNull(questionClasses, "questionClasses");
        this.schemas = Objects.requireNonNull(schemas, "schemas");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    /**
     * Runs every check over the unit.
     *
     * @param definition the parsed unit
     * @return every violation of the unit; {@link VerificationResult#passed()} when there is none
     * @throws NullPointerException if the definition is {@code null}
     */
    public VerificationResult verify(WorkflowDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        var violations = new ArrayList<Violation>();
        var graph = UnitGraph.of(definition);
        var defaultLostRoute = routesLostByDefault(definition);
        checkSchemaBinding(definition, violations);
        checkStateIdUniqueness(definition, violations);
        checkStateExists(definition, violations);
        checkLeafTaskConstraint(definition, violations);
        var targetedAbortTerminals = new HashSet<String>();
        for (var state : definition.states().values()) {
            checkPrimitiveBinding(definition, state, violations);
            checkParameterEquivalence(state, violations);
            checkOutcomeCoverage(state, defaultLostRoute, violations);
            checkLinkIdentifiers(definition.scope(), state, violations);
            checkLinkCompatibility(state, violations);
            checkJobStartingOutcomes(definition, state, violations);
            checkAbortTerminalTargeting(state, violations, targetedAbortTerminals);
            checkReservedTargets(state, violations);
        }
        checkSubWorkflowCallContract(definition, violations);
        checkCallGraphDag(definition, violations);
        checkGateProducerCoverage(definition, graph, violations);
        checkStepCompletion(definition, graph, violations);
        checkSessionDrivenLinks(definition, violations);
        checkCycleAwaitedEvent(definition, violations);
        checkReachability(definition, graph, violations);
        checkExitPaths(definition, graph, defaultLostRoute, violations);
        checkReleasedStates(definition, violations);
        return new VerificationResult(ordered(definition, violations));
    }

    /**
     * Rule 2: whether the engine routes the reserved outcome {@code lost} of a task state that declares no
     * {@code lost} transition to the phase's fallback. It does in a plan unit, and in a sub-workflow every caller chain
     * of which through the catalog ends in plan units (Workflow DSL, 02-units-jobs-decisions, Model Job States item 4;
     * ADR 025). A sub-workflow with a caller of another scope, directly or through other sub-workflows, or with no
     * caller at all, routes {@code lost} itself, like every other unit.
     *
     * @param definition the unit being verified
     * @return {@code true} when an undeclared {@code lost} takes the default route
     */
    private boolean routesLostByDefault(WorkflowDefinition definition) {
        return switch (definition.scope()) {
            case PLAN -> true;
            case SUBWORKFLOW -> catalog.calledOnlyFromPlanUnits(definition.id());
            case EPIC, WORKSPACE, ENTITY -> false;
        };
    }

    private void checkSchemaBinding(WorkflowDefinition definition, List<Violation> violations) {
        definition.resultSchema().ifPresent(schemaName -> {
            if (!schemas.contains(schemaName)) {
                violations.add(new Violation(VerificationRule.SCHEMA_BINDING, definition.startState(),
                        definition.location(), "the result schema '%s' is not registered".formatted(schemaName)));
            }
        });
        for (var param : definition.params()) {
            checkParamSchema(param.name(), param.type(), param.location(), definition.startState(), violations);
        }
        for (var state : definition.states().values()) {
            for (var link : UnitGraph.declaredLinks(state.behavior())) {
                for (var param : link.freeParams()) {
                    checkParamSchema(param.name(), param.type(), param.location(), state.id(), violations);
                }
            }
        }
    }

    private static void checkStateIdUniqueness(WorkflowDefinition definition, List<Violation> violations) {
        for (var state : definition.states().values()) {
            if (state.behavior() instanceof StateBehavior.ModelJob modelJob && modelJob.ask()) {
                var askedStateId = state.id() + ".asked";
                if (definition.states().containsKey(askedStateId)) {
                    violations.add(new Violation(VerificationRule.STATE_ID_UNIQUE, state.id(), state.location(),
                            "the engine-made question state '%s' of the state '%s' collides with a declared state"
                                    .formatted(askedStateId, state.id())));
                }
            }
        }
    }

    private static void checkStateExists(WorkflowDefinition definition, List<Violation> violations) {
        if (!definition.states().containsKey(definition.startState())) {
            violations.add(new Violation(VerificationRule.STATE_EXISTS, definition.startState(), definition.location(),
                    "the start state '%s' does not exist in the unit".formatted(definition.startState())));
        }
        for (var state : definition.states().values()) {
            for (var transition : UnitGraph.allTransitions(state.behavior())) {
                var target = transition.targetStateId();
                if (!isReservedTarget(target) && !isAbortTerminal(target)
                        && !definition.states().containsKey(target)) {
                    violations.add(new Violation(VerificationRule.STATE_EXISTS, state.id(), transition.location(),
                            "the target state '%s' of the transition '%s' does not exist in the unit"
                                    .formatted(target, transition.outcomeName())));
                }
            }
        }
    }

    private void checkParamSchema(String paramName, ParamType type, SourceLocation location, String stateId,
            List<Violation> violations) {
        switch (type) {
            case ParamType.SchemaRef(var schemaName) -> {
                if (!schemas.contains(schemaName)) {
                    violations.add(new Violation(VerificationRule.SCHEMA_BINDING, stateId, location,
                            "the schema '%s' of the parameter '%s' is not registered".formatted(schemaName, paramName)));
                }
            }
            case ParamType.ListOf(var elementType) ->
                checkParamSchema(paramName, elementType, location, stateId, violations);
            case ParamType.Text _, ParamType.Int _, ParamType.EnumOf _ -> {
            }
        }
    }

    private void checkPrimitiveBinding(WorkflowDefinition definition, StateNode state, List<Violation> violations) {
        state.factsPrimitiveId().ifPresent(id -> requireRegistered(state, id, "facts", state.location(), violations));
        switch (state.behavior()) {
            case StateBehavior.AutoStep auto ->
                requireRegistered(state, auto.primitiveId(), "auto", state.location(), violations);
            case StateBehavior.JobStep job -> requireRegistered(state, job.primitiveId(),
                    job.composite() ? "composite_job" : "job", state.location(), violations);
            case StateBehavior.ModelJob modelJob -> checkModelJobBinding(definition, state, modelJob, violations);
            case StateBehavior.QuestionWait question -> checkQuestionBinding(state, question, violations);
            case StateBehavior.InteractiveLinks _, StateBehavior.SubWorkflowCall _, StateBehavior.TerminalState _ -> {
                // no primitive of its own: the links are checked below, the rest is outside these checks
            }
        }
        for (var link : UnitGraph.declaredLinks(state.behavior())) {
            requireRegistered(state, primitiveOf(link), "do of the link '%s'".formatted(link.idPattern()),
                    link.location(), violations);
            link.guard().ifPresent(guard -> UnitGraph.predicatesOf(guard.expr()).forEach(id -> requireRegistered(state, id,
                    "predicate of the link '%s'".formatted(link.idPattern()), link.location(), violations)));
        }
    }

    private void checkModelJobBinding(WorkflowDefinition definition, StateNode state, StateBehavior.ModelJob modelJob,
            List<Violation> violations) {
        var entityKindOpt = entityKinds.find(modelJob.entityKind());
        if (entityKindOpt.isEmpty()) {
            violations.add(new Violation(VerificationRule.ENTITY_KIND_BINDING, state.id(), state.location(),
                    "the entity kind '%s' of the state '%s' is not registered"
                            .formatted(modelJob.entityKind(), state.id())));
        } else {
            var entityKind = entityKindOpt.get();
            if (registry.find(entityKind.returnEvaluationPrimitiveId()).isEmpty()) {
                violations.add(new Violation(VerificationRule.ENTITY_KIND_BINDING, state.id(), state.location(),
                        "the return-evaluation primitive '%s' of the entity kind '%s' is not registered"
                                .formatted(entityKind.returnEvaluationPrimitiveId(), entityKind.name())));
            }
            if (modelJob.ask() && !entityKind.hasEntityUnit()) {
                violations.add(new Violation(VerificationRule.ENTITY_KIND_BINDING, state.id(), state.location(),
                        "the state '%s' declares 'ask' on the entity kind '%s', which has no entity unit"
                                .formatted(state.id(), entityKind.name())));
            }
            entityKind.entityUnit().filter(unit -> !catalog.contains(unit)).ifPresent(unit -> violations.add(
                    new Violation(VerificationRule.ENTITY_KIND_BINDING, state.id(), state.location(),
                            "the entity unit '%s' of the entity kind '%s' is not registered"
                                    .formatted(unit, entityKind.name()))));
        }

        modelJob.fanOutPrimitiveId().ifPresent(primitiveId -> {
            if (registry.find(primitiveId).isEmpty()) {
                violations.add(new Violation(VerificationRule.ENTITY_KIND_BINDING, state.id(), state.location(),
                        "the primitive '%s' of the each clause is not registered".formatted(primitiveId)));
            }
        });

        modelJob.rolesPrimitiveId().ifPresent(primitiveId -> {
            if (!"adjudicate.decisions".equals(definition.id())) {
                violations.add(new Violation(VerificationRule.ENTITY_KIND_BINDING, state.id(), state.location(),
                        "the form 'role each' is admitted only in the unit 'adjudicate.decisions', but found in '%s'"
                                .formatted(definition.id())));
            }
            if (registry.find(primitiveId).isEmpty()) {
                violations.add(new Violation(VerificationRule.ENTITY_KIND_BINDING, state.id(), state.location(),
                        "the primitive '%s' of the role each clause is not registered".formatted(primitiveId)));
            }
            if (modelJob.ask()) {
                violations.add(new Violation(VerificationRule.ENTITY_KIND_BINDING, state.id(), state.location(),
                        "the state '%s' declares 'ask' on a 'role each' state".formatted(state.id())));
            }
        });

        modelJob.roleName().ifPresent(role -> {
            if (!isValidRoleName(role)) {
                violations.add(new Violation(VerificationRule.ROLE_NAME_GRAMMAR, state.id(), state.location(),
                        "the role '%s' of the state '%s' does not match the role-name grammar"
                                .formatted(role, state.id())));
            }
        });

        for (var consultRole : modelJob.consultRoles()) {
            if (!isValidRoleName(consultRole)) {
                violations.add(new Violation(VerificationRule.ROLE_NAME_GRAMMAR, state.id(), state.location(),
                        "the consulted role '%s' of the state '%s' does not match the role-name grammar"
                                .formatted(consultRole, state.id())));
            }
        }
    }

    private void checkQuestionBinding(StateNode state, StateBehavior.QuestionWait question,
            List<Violation> violations) {
        if (questionClasses.find(question.questionClass()).isEmpty()) {
            violations.add(new Violation(VerificationRule.QUESTION_CLASS_BINDING, state.id(), state.location(),
                    "the question class '%s' of the state '%s' is not registered"
                            .formatted(question.questionClass(), state.id())));
        }
    }

    private void checkJobStartingOutcomes(WorkflowDefinition definition, StateNode state, List<Violation> violations) {
        switch (state.behavior()) {
            case StateBehavior.AutoStep auto -> checkPrimitiveJobTransitions(definition, state, auto.primitiveId(),
                    auto.transitions(), violations);
            case StateBehavior.InteractiveLinks links -> {
                for (var link : links.links()) {
                    checkPrimitiveJobTransitions(definition, state, primitiveOf(link), link.transitions(), violations);
                }
            }
            case StateBehavior.QuestionWait question -> {
                for (var link : question.links()) {
                    checkPrimitiveJobTransitions(definition, state, primitiveOf(link), link.transitions(), violations);
                }
            }
            case StateBehavior.JobStep _, StateBehavior.ModelJob _, StateBehavior.SubWorkflowCall _,
                StateBehavior.TerminalState _ -> {
            }
        }
    }

    private void checkPrimitiveJobTransitions(WorkflowDefinition definition, StateNode state, String primitiveId,
            List<OutcomeTransition> transitions, List<Violation> violations) {
        var primitiveOpt = registry.find(primitiveId);
        if (primitiveOpt.isEmpty()) {
            return;
        }
        var primitive = primitiveOpt.get();
        if (!primitive.enqueuesJob()) {
            return;
        }
        var jobStartingOutcomes = new HashSet<String>();
        for (var constant : primitive.outcomeType().getEnumConstants()) {
            var outcome = (PrimitiveOutcome) constant;
            if (outcome.startsJob()) {
                jobStartingOutcomes.add(outcome.wireName());
            }
        }
        if (jobStartingOutcomes.isEmpty()) {
            violations.add(new Violation(VerificationRule.JOB_STARTING_OUTCOME, state.id(), state.location(),
                    "the primitive '%s' declares enqueuesJob() but has no job-starting outcome".formatted(primitiveId)));
            return;
        }
        for (var transition : transitions) {
            var targetState = definition.states().get(transition.targetStateId());
            boolean isJobStarting = jobStartingOutcomes.contains(transition.outcomeName());
            if (isJobStarting) {
                if (targetState != null && !(targetState.behavior() instanceof StateBehavior.JobStep)) {
                    violations.add(new Violation(VerificationRule.JOB_STARTING_OUTCOME, state.id(), transition.location(),
                            "the job-starting outcome '%s' of the primitive '%s' targets '%s', which is not a job state"
                                    .formatted(transition.outcomeName(), primitiveId, transition.targetStateId())));
                }
            } else {
                if (targetState != null && (targetState.behavior() instanceof StateBehavior.JobStep)) {
                    violations.add(new Violation(VerificationRule.JOB_STARTING_OUTCOME, state.id(), transition.location(),
                            "the outcome '%s' of the primitive '%s' does not start a job, but targets the job state '%s'"
                                    .formatted(transition.outcomeName(), primitiveId, transition.targetStateId())));
                }
            }
        }
    }

    private static boolean isValidRoleName(String role) {
        return ROLE_NAME_PATTERN.matcher(role).matches() && !RESERVED_KEYWORDS.contains(role);
    }

    private void requireRegistered(StateNode state, String primitiveId, String site, SourceLocation location,
            List<Violation> violations) {
        if (registry.find(primitiveId).isEmpty()) {
            violations.add(new Violation(VerificationRule.PRIMITIVE_BINDING, state.id(), location,
                    "the primitive '%s' of the %s is not registered".formatted(primitiveId, site)));
        }
    }

    private void checkParameterEquivalence(StateNode state, List<Violation> violations) {
        for (var link : UnitGraph.declaredLinks(state.behavior())) {
            registry.find(primitiveOf(link))
                    .ifPresent(primitive -> compareFreeParameters(state, link, primitive, violations));
        }
    }

    private static void compareFreeParameters(StateNode state, LinkSpec link, Primitive<?, ?> primitive,
            List<Violation> violations) {
        var expected = new LinkedHashMap<String, FreeParam>();
        for (var component : primitive.parameterType().getRecordComponents()) {
            var freeParam = component.getAnnotation(FreeParam.class);
            if (freeParam != null) {
                expected.put(component.getName(), freeParam);
            }
        }
        var declared = new HashSet<String>();
        for (var param : link.freeParams()) {
            declared.add(param.name());
            var freeParam = expected.get(param.name());
            if (freeParam == null) {
                violations.add(parameterViolation(state, param.location(), link, primitive,
                        "declares the free parameter '%s', which the parameter record has not".formatted(param.name())));
            } else {
                compareType(state, link, primitive, param, freeParam, violations);
            }
        }
        expected.keySet().stream().filter(name -> !declared.contains(name))
                .forEach(name -> violations.add(parameterViolation(state, link.location(), link, primitive,
                        "omits the free parameter '%s' of the parameter record".formatted(name))));
    }

    private static void compareType(StateNode state, LinkSpec link, Primitive<?, ?> primitive, ParamSpec param,
            FreeParam freeParam, List<Violation> violations) {
        if (!(param.type() instanceof ParamType.Text(var minLength, var maxLength))) {
            violations.add(parameterViolation(state, param.location(), link, primitive,
                    "declares the free parameter '%s' as %s, but the parameter record declares a text"
                            .formatted(param.name(), param.type())));
            return;
        }
        var min = minLength.orElse(0);
        var max = maxLength.orElse(Integer.MAX_VALUE);
        if (min != freeParam.minLength() || max != freeParam.maxLength()) {
            violations.add(parameterViolation(state, param.location(), link, primitive,
                    "declares the free parameter '%s' with the length %d..%d, but the parameter record declares %d..%d"
                            .formatted(param.name(), min, max, freeParam.minLength(), freeParam.maxLength())));
        }
    }

    private static Violation parameterViolation(StateNode state, SourceLocation location, LinkSpec link,
            Primitive<?, ?> primitive, String detail) {
        return new Violation(VerificationRule.PARAMETER_EQUIVALENCE, state.id(), location,
                "the link '%s' of the primitive '%s' %s".formatted(link.idPattern(), primitive.id(), detail));
    }

    private void checkOutcomeCoverage(StateNode state, boolean defaultLostRoute, List<Violation> violations) {
        switch (state.behavior()) {
            case StateBehavior.AutoStep auto ->
                coverOutcomes(state, auto.primitiveId(), auto.transitions(), state.location(), violations);
            case StateBehavior.JobStep job ->
                coverOutcomes(state, job.primitiveId(), job.transitions(), state.location(), violations);
            case StateBehavior.ModelJob modelJob ->
                coverModelJobOutcomes(state, modelJob, defaultLostRoute, violations);
            case StateBehavior.QuestionWait question ->
                coverQuestionOutcomes(state, question, violations);
            case StateBehavior.InteractiveLinks _, StateBehavior.SubWorkflowCall _,
                StateBehavior.TerminalState _ -> {
                // a call routes outcomes that no primitive of the registry declares
            }
        }
        for (var link : UnitGraph.declaredLinks(state.behavior())) {
            coverOutcomes(state, primitiveOf(link), link.transitions(), link.location(), violations);
        }
    }

    private void coverOutcomes(StateNode state, String primitiveId, List<OutcomeTransition> transitions,
            SourceLocation siteLocation, List<Violation> violations) {
        var primitive = registry.find(primitiveId);
        if (primitive.isEmpty()) {
            return;
        }
        var wireNames = wireNamesOf(primitive.get());
        var routed = new HashSet<String>();
        for (var transition : transitions) {
            var outcome = transition.outcomeName();
            if (!wireNames.contains(outcome)) {
                violations.add(outcomeViolation(state, transition.location(),
                        "routes the outcome '%s', which the primitive '%s' does not have".formatted(outcome,
                                primitiveId)));
            } else if (!routed.add(outcome)) {
                violations.add(outcomeViolation(state, transition.location(),
                        "routes the outcome '%s' of the primitive '%s' twice".formatted(outcome, primitiveId)));
            }
        }
        wireNames.stream().filter(outcome -> !routed.contains(outcome))
                .forEach(outcome -> violations.add(outcomeViolation(state, siteLocation,
                        "does not route the outcome '%s' of the primitive '%s'".formatted(outcome, primitiveId))));
    }

    private void coverModelJobOutcomes(StateNode state, StateBehavior.ModelJob modelJob, boolean defaultLostRoute,
            List<Violation> violations) {
        var entityKindOpt = entityKinds.find(modelJob.entityKind());
        if (entityKindOpt.isEmpty()) {
            return;
        }
        var primitiveOpt = registry.find(entityKindOpt.get().returnEvaluationPrimitiveId());
        if (primitiveOpt.isEmpty()) {
            return;
        }
        var primitive = primitiveOpt.get();
        var wireNames = wireNamesOf(primitive);
        var expectedOutcomes = new LinkedHashSet<>(wireNames);
        expectedOutcomes.add(UnitGraph.LOST_OUTCOME);

        var routed = new HashSet<String>();
        for (var transition : modelJob.transitions()) {
            var outcome = transition.outcomeName();
            if ("asked".equals(outcome)) {
                violations.add(new Violation(VerificationRule.MODEL_JOB_OUTCOMES, state.id(), transition.location(),
                        "the state '%s' declares the outcome 'asked', which is engine-made".formatted(state.id())));
            } else if (!expectedOutcomes.contains(outcome)) {
                violations.add(new Violation(VerificationRule.MODEL_JOB_OUTCOMES, state.id(), transition.location(),
                        "the state '%s' routes the outcome '%s', which the return-evaluation primitive '%s' does not have"
                                .formatted(state.id(), outcome, primitive.id())));
            } else if (!routed.add(outcome)) {
                violations.add(new Violation(VerificationRule.MODEL_JOB_OUTCOMES, state.id(), transition.location(),
                        "the state '%s' routes the outcome '%s' twice".formatted(state.id(), outcome)));
            }
        }

        for (var expected : wireNames) {
            if (!routed.contains(expected)) {
                violations.add(new Violation(VerificationRule.MODEL_JOB_OUTCOMES, state.id(), state.location(),
                        "the state '%s' does not route the outcome '%s' of the return-evaluation primitive '%s'"
                                .formatted(state.id(), expected, primitive.id())));
            }
        }

        if (!defaultLostRoute && !routed.contains(UnitGraph.LOST_OUTCOME)) {
            violations.add(new Violation(VerificationRule.MODEL_JOB_OUTCOMES, state.id(), state.location(),
                    "the state '%s' does not route the reserved outcome 'lost'".formatted(state.id())));
        }
    }

    private void coverQuestionOutcomes(StateNode state, StateBehavior.QuestionWait question,
            List<Violation> violations) {
        var qcOpt = questionClasses.find(question.questionClass());
        if (qcOpt.isEmpty()) {
            return;
        }
        var qc = qcOpt.get();
        var routed = new HashSet<String>();
        for (var transition : question.transitions()) {
            var outcome = transition.outcomeName();
            if (!qc.outcomes().contains(outcome)) {
                violations.add(new Violation(VerificationRule.QUESTION_OUTCOMES, state.id(), transition.location(),
                        "the question state '%s' routes the outcome '%s', which the question class '%s' does not have"
                                .formatted(state.id(), outcome, qc.name())));
            } else if (!routed.add(outcome)) {
                violations.add(new Violation(VerificationRule.QUESTION_OUTCOMES, state.id(), transition.location(),
                        "the question state '%s' routes the outcome '%s' of the question class '%s' twice"
                                .formatted(state.id(), outcome, qc.name())));
            }
        }
        for (var outcome : qc.outcomes()) {
            if (!routed.contains(outcome)) {
                violations.add(new Violation(VerificationRule.QUESTION_OUTCOMES, state.id(), state.location(),
                        "the question state '%s' does not route the outcome '%s' of the question class '%s'"
                                .formatted(state.id(), outcome, qc.name())));
            }
        }
    }

    private static Violation outcomeViolation(StateNode state, SourceLocation location, String detail) {
        return new Violation(VerificationRule.OUTCOME_COVERAGE, state.id(), location,
                "the state '%s' %s".formatted(state.id(), detail));
    }

    private static Set<String> wireNamesOf(Primitive<?, ?> primitive) {
        var wireNames = new LinkedHashSet<String>();
        for (var constant : primitive.outcomeType().getEnumConstants()) {
            wireNames.add(((PrimitiveOutcome) constant).wireName());
        }
        return wireNames;
    }

    private static void checkLinkIdentifiers(WorkflowScope scope, StateNode state, List<Violation> violations) {
        var identifiers = new HashSet<String>();
        for (var link : UnitGraph.declaredLinks(state.behavior())) {
            var pattern = link.idPattern();
            if (UnitGraph.isImplicitLink(scope, state, pattern)) {
                violations.add(new Violation(VerificationRule.LINK_ID_UNIQUE, state.id(), link.location(),
                        "the link '%s' of the state '%s' repeats the identifier of the implicit link".formatted(pattern,
                                state.id())));
            } else if (!identifiers.add(pattern)) {
                violations.add(new Violation(VerificationRule.LINK_ID_UNIQUE, state.id(), link.location(),
                        "the link '%s' of the state '%s' repeats the identifier of another link".formatted(pattern,
                                state.id())));
            }
        }
    }

    private static void checkLinkCompatibility(StateNode state, List<Violation> violations) {
        for (var link : UnitGraph.declaredLinks(state.behavior())) {
            if (link.rel() == LinkRelation.CONSULT || link.rel() == LinkRelation.ABORT) {
                violations.add(new Violation(VerificationRule.LINK_COMPATIBILITY, state.id(), link.location(),
                        "the relation %s is engine-owned and must not be declared in a link clause"
                                .formatted(link.rel())));
            }
            if (!link.rel().allowedForms().contains(link.form())) {
                violations.add(new Violation(VerificationRule.LINK_COMPATIBILITY, state.id(), link.location(),
                        "the link '%s' of relation %s does not admit the form %s"
                                .formatted(link.idPattern(), link.rel(), link.form())));
            }
            if (link.form() == LinkForm.SUBMIT && link.freeParams().isEmpty()) {
                violations.add(new Violation(VerificationRule.LINK_COMPATIBILITY, state.id(), link.location(),
                        "the link '%s' has form 'submit' but declares no parameters"
                                .formatted(link.idPattern())));
            }
            if (link.form() == LinkForm.NOOP && !link.freeParams().isEmpty()) {
                violations.add(new Violation(VerificationRule.LINK_COMPATIBILITY, state.id(), link.location(),
                        "the link '%s' has form 'noop' but declares parameters"
                                .formatted(link.idPattern())));
            }
            if (link.rel() == LinkRelation.ESCALATE && !"request_waiver".equals(link.idPattern())) {
                violations.add(new Violation(VerificationRule.LINK_COMPATIBILITY, state.id(), link.location(),
                        "the link '%s' of relation ESCALATE must name 'request_waiver'"
                                .formatted(link.idPattern())));
            }
        }
    }

    private static void checkReachability(WorkflowDefinition definition, UnitGraph graph,
            List<Violation> violations) {
        var reached = graph.forwardClosure(Set.of(definition.startState()));
        for (var state : definition.states().values()) {
            if (!reached.contains(state.id()) && !ABORT_TERMINALS.contains(state.id())) {
                violations.add(new Violation(VerificationRule.REACHABILITY, state.id(), state.location(),
                        "the state '%s' is not reachable from the start state '%s'".formatted(state.id(),
                                definition.startState())));
            }
        }
    }

    private static void checkAbortTerminalTargeting(StateNode state, List<Violation> violations,
            Set<String> targetedAbortTerminals) {
        for (var transition : UnitGraph.allTransitions(state.behavior())) {
            var target = transition.targetStateId();
            if ("plan.aborted".equals(target)) {
                violations.add(new Violation(VerificationRule.ABORT_TERMINAL_TARGETING, state.id(),
                        transition.location(),
                        "the abort terminal 'plan.aborted' must not be targeted by any declared transition"));
            } else if ("epic.aborted".equals(target)) {
                if (!"epic.next-work".equals(state.id()) || !"abort_landed".equals(transition.outcomeName())) {
                    violations.add(new Violation(VerificationRule.ABORT_TERMINAL_TARGETING, state.id(),
                            transition.location(),
                            "the abort terminal 'epic.aborted' may only be targeted by 'abort_landed' of 'epic.next-work'"));
                } else if (!targetedAbortTerminals.add(target)) {
                    violations.add(new Violation(VerificationRule.ABORT_TERMINAL_TARGETING, state.id(),
                            transition.location(),
                            "the abort terminal 'epic.aborted' must be targeted by at most one declared transition"));
                }
            }
        }
    }

    private static void checkReservedTargets(StateNode state, List<Violation> violations) {
        for (var transition : UnitGraph.allTransitions(state.behavior())) {
            var target = transition.targetStateId();
            if (isReservedTarget(target)) {
                violations.add(new Violation(VerificationRule.TARGET_NOT_RESERVED, state.id(), transition.location(),
                        "the transition '%s' targets '%s', which is a reserved condition or state identifier"
                                .formatted(transition.outcomeName(), target)));
            }
        }
    }

    /**
     * Tells whether an identifier names a condition kind or a reserved state identifier
     * ({@code <phase>.blocked}, {@code <phase>.footprint-violation}, {@code <phase>.config-drift}); Rule 3 and the
     * compiled invariant {@link InvariantId#INV_GEN_3} share this one vocabulary.
     *
     * @param target a state identifier or a transition target
     * @return {@code true} when no unit may declare or target it
     */
    static boolean isReservedTarget(String target) {
        if (CONDITION_KINDS.contains(target)) {
            return true;
        }
        for (var suffix : RESERVED_TARGET_SUFFIXES) {
            if (target.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAbortTerminal(String target) {
        return ABORT_TERMINALS.contains(target);
    }

    private static void checkLeafTaskConstraint(WorkflowDefinition definition, List<Violation> violations) {
        boolean isWorkerUnit = definition.scope() == WorkflowScope.ENTITY || "worker-task".equals(definition.id());
        if (isWorkerUnit) {
            for (var state : definition.states().values()) {
                if (state.behavior() instanceof StateBehavior.ModelJob) {
                    violations.add(new Violation(VerificationRule.LEAF_TASK_CONSTRAINT, state.id(), state.location(),
                            "a worker unit must not contain a model job state"));
                }
                for (var link : UnitGraph.declaredLinks(state.behavior())) {
                    for (var transition : link.transitions()) {
                        var target = definition.states().get(transition.targetStateId());
                        if (target != null && target.behavior() instanceof StateBehavior.ModelJob) {
                            violations.add(new Violation(VerificationRule.LEAF_TASK_CONSTRAINT, state.id(),
                                    transition.location(), "a worker unit link transition must not enter a model job state"));
                        }
                    }
                }
            }
        }
        boolean isConsultation = definition.id().contains("consult") || "consultation".equals(definition.id());
        if (isConsultation) {
            for (var state : definition.states().values()) {
                for (var link : UnitGraph.declaredLinks(state.behavior())) {
                    if (link.rel() == LinkRelation.CONSULT || link.idPattern().startsWith("consult:")) {
                        violations.add(new Violation(VerificationRule.LEAF_TASK_CONSTRAINT, state.id(), link.location(),
                                "a consultation task must not offer a consult link"));
                    }
                }
                if (state.behavior() instanceof StateBehavior.ModelJob modelJob && !modelJob.consultRoles().isEmpty()) {
                    violations.add(new Violation(VerificationRule.LEAF_TASK_CONSTRAINT, state.id(), state.location(),
                            "a consultation task must not consult further roles"));
                }
            }
        }
    }

    private void checkSubWorkflowCallContract(WorkflowDefinition definition, List<Violation> violations) {
        for (var state : definition.states().values()) {
            state.terminalOutcome()
                    .filter(outcome -> STEPPED_OUT.equals(outcome.id())
                            && outcome.outcomeClass() != OutcomeClass.STEPPED_OUT)
                    .ifPresent(outcome -> violations.add(new Violation(VerificationRule.SUBWORKFLOW_CALL_CONTRACT,
                            state.id(), state.location(),
                            "the terminal '%s' declares the outcome 'stepped_out' with the class %s; only a terminal of class STEPPED_OUT declares it"
                                    .formatted(state.id(), outcome.outcomeClass()))));

            if ("inherit".equals(state.phase()) && definition.scope() != WorkflowScope.SUBWORKFLOW
                    && definition.scope() != WorkflowScope.ENTITY) {
                violations.add(new Violation(VerificationRule.SUBWORKFLOW_CALL_CONTRACT, state.id(), state.location(),
                        "the state '%s' declares 'phase inherit', which is admitted only in sub-workflow and entity units"
                                .formatted(state.id())));
            }

            if (definition.scope() == WorkflowScope.SUBWORKFLOW
                    && state.behavior() instanceof StateBehavior.InteractiveLinks links && !links.links().isEmpty()
                    && state.skillPath().isEmpty()) {
                violations.add(new Violation(VerificationRule.SUBWORKFLOW_CALL_CONTRACT, state.id(), state.location(),
                        "the resting link state '%s' of a sub-workflow must declare a skill".formatted(state.id())));
            }

            if (!(state.behavior() instanceof StateBehavior.SubWorkflowCall)) {
                checkResultGuardOutsideCall(state, violations);
            }

            if (state.behavior() instanceof StateBehavior.SubWorkflowCall call) {
                var calleeOpt = catalog.find(call.subWorkflowId());
                if (calleeOpt.isEmpty()) {
                    violations.add(new Violation(VerificationRule.SUBWORKFLOW_CALL_CONTRACT, state.id(),
                            state.location(),
                            "the called sub-workflow '%s' is not registered".formatted(call.subWorkflowId())));
                    continue;
                }
                var callee = calleeOpt.get();
                if (callee.scope() != WorkflowScope.SUBWORKFLOW) {
                    violations.add(new Violation(VerificationRule.SUBWORKFLOW_CALL_CONTRACT, state.id(),
                            state.location(),
                            "the called workflow '%s' has scope %s, expected SUBWORKFLOW"
                                    .formatted(call.subWorkflowId(), callee.scope())));
                }

                var contractOutcomes = new LinkedHashSet<String>();
                for (var calleeState : callee.states().values()) {
                    if (calleeState.behavior() instanceof StateBehavior.TerminalState
                            && calleeState.terminalOutcome().isPresent()) {
                        contractOutcomes.add(calleeState.terminalOutcome().get().id());
                    }
                }
                // Rule 6: the exit contract of every sub-workflow is its terminals' outcomes plus the control
                // outcome stepped_out, which is part of it implicitly. Every call routes it, whatever the callee's
                // states are.
                contractOutcomes.add(STEPPED_OUT);

                // Rule 6: a result guard reads a field of the callee's result, so the callee declares a result
                // schema. Whether the field is a boolean or optional field of it is not checked (ADR 010).
                if (callee.resultSchema().isEmpty()) {
                    call.transitions().stream().filter(transition -> transition.resultGuard().isPresent())
                            .findFirst().ifPresent(transition -> violations.add(new Violation(
                            VerificationRule.SUBWORKFLOW_CALL_CONTRACT, state.id(), transition.location(),
                            "the call guards the outcome '%s' on the result of '%s', which declares no result schema"
                                    .formatted(transition.outcomeName(), call.subWorkflowId()))));
                }

                var outcomeCounts = new HashMap<String, Integer>();
                var resultGuardsOnDone = new ArrayList<ResultGuard>();
                for (var transition : call.transitions()) {
                    var outcome = transition.outcomeName();
                    outcomeCounts.put(outcome, outcomeCounts.getOrDefault(outcome, 0) + 1);
                    if (STEPPED_OUT.equals(outcome)) {
                        checkSteppedOutTarget(definition, state, transition, violations);
                    }
                    if (transition.resultGuard().isPresent()) {
                        if (!"done".equals(outcome)) {
                            violations.add(new Violation(VerificationRule.SUBWORKFLOW_CALL_CONTRACT, state.id(),
                                    transition.location(),
                                    "a result guard is admitted only on the outcome 'done', but found on '%s'"
                                            .formatted(outcome)));
                        } else {
                            resultGuardsOnDone.add(transition.resultGuard().get());
                        }
                    }
                    if (!contractOutcomes.contains(outcome)) {
                        violations.add(new Violation(VerificationRule.SUBWORKFLOW_CALL_CONTRACT, state.id(),
                                transition.location(),
                                "the call routes the outcome '%s', which is not in the exit contract of '%s'"
                                        .formatted(outcome, call.subWorkflowId())));
                    }
                }

                for (var contractOutcome : contractOutcomes) {
                    int count = outcomeCounts.getOrDefault(contractOutcome, 0);
                    if (count == 0) {
                        violations.add(new Violation(VerificationRule.SUBWORKFLOW_CALL_CONTRACT, state.id(),
                                state.location(),
                                "the call does not route the exit outcome '%s' of '%s'"
                                        .formatted(contractOutcome, call.subWorkflowId())));
                    } else if (count == 1 && !resultGuardsOnDone.isEmpty() && "done".equals(contractOutcome)) {
                        violations.add(new Violation(VerificationRule.SUBWORKFLOW_CALL_CONTRACT, state.id(),
                                state.location(),
                                "the call routes the exit outcome 'done' of '%s' once under a result guard; a guarded 'done' is admitted only as the complementary pair"
                                        .formatted(call.subWorkflowId())));
                    } else if (count == 2) {
                        if (!"done".equals(contractOutcome) || resultGuardsOnDone.size() != 2
                                || !areComplementary(resultGuardsOnDone.get(0), resultGuardsOnDone.get(1))) {
                            violations.add(new Violation(VerificationRule.SUBWORKFLOW_CALL_CONTRACT, state.id(),
                                    state.location(),
                                    "the call routes the exit outcome '%s' of '%s' multiple times"
                                            .formatted(contractOutcome, call.subWorkflowId())));
                        }
                    } else if (count > 2 || (count > 1 && !"done".equals(contractOutcome))) {
                        violations.add(new Violation(VerificationRule.SUBWORKFLOW_CALL_CONTRACT, state.id(),
                                state.location(),
                                "the call routes the exit outcome '%s' of '%s' multiple times"
                                        .formatted(contractOutcome, call.subWorkflowId())));
                    }
                }

                var declaredParams = new LinkedHashMap<String, ParamDecl>();
                for (var param : callee.params()) {
                    declaredParams.put(param.name(), param);
                }
                for (var entry : call.paramBindings().entrySet()) {
                    var paramName = entry.getKey();
                    var callValue = entry.getValue();
                    var paramDecl = declaredParams.get(paramName);
                    if (paramDecl == null) {
                        violations.add(new Violation(VerificationRule.SUBWORKFLOW_CALL_CONTRACT, state.id(),
                                state.location(),
                                "the parameter binding '%s' names an undeclared parameter of '%s'"
                                        .formatted(paramName, call.subWorkflowId())));
                    } else {
                        checkParamValueMatch(state, call.subWorkflowId(), paramDecl, callValue, violations);
                    }
                }
                for (var paramName : declaredParams.keySet()) {
                    if (!call.paramBindings().containsKey(paramName)) {
                        violations.add(new Violation(VerificationRule.SUBWORKFLOW_CALL_CONTRACT, state.id(),
                                state.location(),
                                "the call omits the required parameter '%s' of '%s'"
                                        .formatted(paramName, call.subWorkflowId())));
                    }
                }
            }
        }
    }

    /**
     * Rule 6: a result guard reads the result of a called sub-workflow, so it sits only on the {@code done} of a call;
     * on the transition of any other state, its declared links included, it is refused.
     */
    private static void checkResultGuardOutsideCall(StateNode state, List<Violation> violations) {
        for (var transition : UnitGraph.allTransitions(state.behavior())) {
            if (transition.resultGuard().isPresent()) {
                violations.add(new Violation(VerificationRule.SUBWORKFLOW_CALL_CONTRACT, state.id(),
                        transition.location(),
                        "a result guard is admitted only on the outcome 'done' of a call, but found on the outcome '%s' of the state '%s', which is no call"
                                .formatted(transition.outcomeName(), state.id())));
            }
        }
    }

    /**
     * Rule 6, where the {@code stepped_out} route of a call may lead. A terminal of class {@code SUCCESS} is never
     * admitted. In a plan unit the target is the phase's fallback state, {@code plan.return} or
     * {@code finalize.hand-back} (which of the two belongs to the calling phase is not checked); in an epic, workspace
     * or entity unit (the bootstrap units are entity units) a resting state of the calling unit; in a sub-workflow a
     * state that continues the unwinding or handles the failure: a terminal of class {@code STEPPED_OUT} or
     * {@code FAILURE}, or a link state whose own {@code step_out} continues it. A remediation unit may route it to its
     * budget re-check as well: the state {@code remediation-<class>.budget-check} that runs
     * {@code remediation.check-budgets} (INV-REM-4, ADR 024); any other target is refused there as in every
     * sub-workflow. A target that is not a state of the unit is refused by {@link VerificationRule#STATE_EXISTS}.
     */
    private static void checkSteppedOutTarget(WorkflowDefinition definition, StateNode state,
            OutcomeTransition transition, List<Violation> violations) {
        var target = definition.states().get(transition.targetStateId());
        if (target == null) {
            return;
        }
        var targetClass = target.terminalOutcome().map(TerminalOutcome::outcomeClass);
        String detail;
        if (targetClass.isPresent() && targetClass.get() == OutcomeClass.SUCCESS) {
            detail = "a terminal state of class SUCCESS";
        } else {
            detail = switch (definition.scope()) {
                case PLAN -> PHASE_FALLBACK_STATES.contains(target.id()) ? null
                        : "not the phase's fallback state 'plan.return' or 'finalize.hand-back'";
                case EPIC, WORKSPACE, ENTITY -> isRestingState(target) ? null : "not a resting state of the unit";
                case SUBWORKFLOW -> subWorkflowSteppedOutDetail(definition, target, targetClass);
            };
        }
        if (detail != null) {
            violations.add(new Violation(VerificationRule.SUBWORKFLOW_CALL_CONTRACT, state.id(), transition.location(),
                    "the call routes 'stepped_out' to '%s', which is %s".formatted(target.id(), detail)));
        }
    }

    /**
     * @return {@code null} when a sub-workflow may route {@code stepped_out} to the target, otherwise what the target
     *         is not
     */
    private static String subWorkflowSteppedOutDetail(WorkflowDefinition definition, StateNode target,
            Optional<OutcomeClass> targetClass) {
        var continuesStepOut = target.behavior() instanceof StateBehavior.InteractiveLinks
                || targetClass.filter(outcomeClass -> outcomeClass == OutcomeClass.STEPPED_OUT
                || outcomeClass == OutcomeClass.FAILURE).isPresent();
        if (continuesStepOut) {
            return null;
        }
        if (!definition.id().startsWith(REMEDIATION_UNIT_PREFIX)) {
            return "neither a terminal of class STEPPED_OUT or FAILURE nor a link state that continues the step-out";
        }
        return isBudgetCheck(target) ? null
                : "neither a terminal of class STEPPED_OUT or FAILURE, nor a link state that continues the step-out, nor the budget re-check 'remediation-<class>%s' running '%s'"
                .formatted(BUDGET_CHECK_SUFFIX, CHECK_BUDGETS);
    }

    /**
     * The budget re-check of a remediation unit, told by its state id and its primitive together: the state's last
     * segment is {@code budget-check}, as the remediation invariants find it, and it is
     * {@code auto remediation.check-budgets}.
     */
    private static boolean isBudgetCheck(StateNode state) {
        return state.id().endsWith(BUDGET_CHECK_SUFFIX)
                && state.behavior() instanceof StateBehavior.AutoStep auto
                && CHECK_BUDGETS.equals(auto.primitiveId());
    }

    /** A state that rests between events: a link, question, {@code job}, {@code composite_job} or {@code job llm}. */
    private static boolean isRestingState(StateNode state) {
        return switch (state.behavior()) {
            case StateBehavior.InteractiveLinks _, StateBehavior.QuestionWait _, StateBehavior.JobStep _,
                StateBehavior.ModelJob _ -> true;
            case StateBehavior.AutoStep _, StateBehavior.SubWorkflowCall _, StateBehavior.TerminalState _ -> false;
        };
    }

    private static boolean areComplementary(ResultGuard g1, ResultGuard g2) {
        return g1.field().equals(g2.field()) && g1.negated() != g2.negated();
    }

    private static void checkParamValueMatch(StateNode state, String subWorkflowId, ParamDecl param,
            CallValue value, List<Violation> violations) {
        switch (param.type()) {
            case ParamType.EnumOf(var allowedValues) -> {
                if (value instanceof CallValue.EnumValue(var enumVal)) {
                    if (!allowedValues.contains(enumVal)) {
                        violations.add(new Violation(VerificationRule.SUBWORKFLOW_CALL_CONTRACT, state.id(),
                                state.location(),
                                "the value '%s' of parameter '%s' is not in the allowed enum values %s of '%s'"
                                        .formatted(enumVal, param.name(), allowedValues, subWorkflowId)));
                    }
                } else {
                    violations.add(new Violation(VerificationRule.SUBWORKFLOW_CALL_CONTRACT, state.id(),
                            state.location(),
                            "the parameter '%s' of '%s' expects an enum value, but found %s"
                                    .formatted(param.name(), subWorkflowId, value)));
                }
            }
            case ParamType.Int(var min, var max) -> {
                if (value instanceof CallValue.IntValue(var intVal)) {
                    var minVal = min.orElse(Integer.MIN_VALUE);
                    var maxVal = max.orElse(Integer.MAX_VALUE);
                    if (intVal < minVal || intVal > maxVal) {
                        violations.add(new Violation(VerificationRule.SUBWORKFLOW_CALL_CONTRACT, state.id(),
                                state.location(),
                                "the integer value %d of parameter '%s' is out of range [%d..%d] for '%s'"
                                        .formatted(intVal, param.name(), minVal, maxVal, subWorkflowId)));
                    }
                } else {
                    violations.add(new Violation(VerificationRule.SUBWORKFLOW_CALL_CONTRACT, state.id(),
                            state.location(),
                            "the parameter '%s' of '%s' expects an int value, but found %s"
                                    .formatted(param.name(), subWorkflowId, value)));
                }
            }
            case ParamType.ListOf(var elementType) -> {
                if (value instanceof CallValue.EnumList(var listVals)) {
                    if (listVals.isEmpty()) {
                        violations.add(new Violation(VerificationRule.SUBWORKFLOW_CALL_CONTRACT, state.id(),
                                state.location(),
                                "the parameter '%s' of '%s' must not be an empty list"
                                        .formatted(param.name(), subWorkflowId)));
                    } else if (elementType instanceof ParamType.EnumOf(var allowedValues)) {
                        for (var elem : listVals) {
                            if (!allowedValues.contains(elem)) {
                                violations.add(new Violation(VerificationRule.SUBWORKFLOW_CALL_CONTRACT, state.id(),
                                        state.location(),
                                        "the list value '%s' of parameter '%s' is not in the allowed enum values %s of '%s'"
                                                .formatted(elem, param.name(), allowedValues, subWorkflowId)));
                            }
                        }
                    }
                } else {
                    violations.add(new Violation(VerificationRule.SUBWORKFLOW_CALL_CONTRACT, state.id(),
                            state.location(),
                            "the parameter '%s' of '%s' expects a list value, but found %s"
                                    .formatted(param.name(), subWorkflowId, value)));
                }
            }
            case ParamType.Text _, ParamType.SchemaRef _ -> {
            }
        }
    }

    private void checkCallGraphDag(WorkflowDefinition definition, List<Violation> violations) {
        for (var state : definition.states().values()) {
            if (state.behavior() instanceof StateBehavior.SubWorkflowCall call) {
                var callStack = new ArrayList<String>();
                callStack.add(definition.id());
                checkDagDfs(state, call.subWorkflowId(), callStack, violations);
            }
        }
    }

    private void checkDagDfs(StateNode rootState, String calleeId, List<String> callStack,
            List<Violation> violations) {
        if (callStack.contains(calleeId)) {
            var cyclePath = new ArrayList<>(callStack);
            cyclePath.add(calleeId);
            violations.add(new Violation(VerificationRule.CALL_GRAPH_DAG, rootState.id(), rootState.location(),
                    "the sub-workflow call graph contains a cycle: %s".formatted(String.join(" -> ", cyclePath))));
            return;
        }
        if (callStack.size() >= 5) {
            var depthPath = new ArrayList<>(callStack);
            depthPath.add(calleeId);
            violations.add(new Violation(VerificationRule.CALL_GRAPH_DAG, rootState.id(), rootState.location(),
                    "the sub-workflow call stack exceeds the maximum depth 4: %s".formatted(String.join(" -> ", depthPath))));
            return;
        }
        var calleeOpt = catalog.find(calleeId);
        if (calleeOpt.isPresent()) {
            callStack.add(calleeId);
            for (var state : calleeOpt.get().states().values()) {
                if (state.behavior() instanceof StateBehavior.SubWorkflowCall nextCall) {
                    checkDagDfs(rootState, nextCall.subWorkflowId(), callStack, violations);
                }
            }
            callStack.removeLast();
        }
    }

    private void checkGateProducerCoverage(WorkflowDefinition definition, UnitGraph graph,
            List<Violation> violations) {
        var producersByArtifact = new HashMap<String, Set<String>>();
        for (var state : definition.states().values()) {
            for (var artifact : producedArtifactsOf(state)) {
                producersByArtifact.computeIfAbsent(artifact, _ -> new LinkedHashSet<>()).add(state.id());
            }
        }

        for (var state : definition.states().values()) {
            for (var artifact : requiredArtifactsOf(state)) {
                checkProducerCoverage(definition, graph, state, state.location(), artifact,
                        producersByArtifact.getOrDefault(artifact, Set.of()), violations);
            }
            for (var link : UnitGraph.declaredLinks(state.behavior())) {
                for (var artifact : requiredArtifactsOf(link)) {
                    checkProducerCoverage(definition, graph, state, link.location(), artifact,
                            producersByArtifact.getOrDefault(artifact, Set.of()), violations);
                }
            }
        }
    }

    private void checkProducerCoverage(WorkflowDefinition definition, UnitGraph graph, StateNode state,
            SourceLocation location, String artifact, Set<String> producers, List<Violation> violations) {
        var reachableWithout = graph.reachableWithoutProducers(definition.startState(), producers);
        if (reachableWithout.contains(state.id())) {
            violations.add(new Violation(VerificationRule.GATE_PRODUCER_COVERAGE, state.id(), location,
                    "the state '%s' requires the artifact '%s', but there is a path from the start state without a producer"
                            .formatted(state.id(), artifact)));
        }
    }

    private Set<String> producedArtifactsOf(StateNode state) {
        var produced = new LinkedHashSet<String>();
        switch (state.behavior()) {
            case StateBehavior.AutoStep auto ->
                registry.find(auto.primitiveId()).ifPresent(p -> produced.addAll(p.producedArtifacts()));
            case StateBehavior.JobStep job ->
                registry.find(job.primitiveId()).ifPresent(p -> produced.addAll(p.producedArtifacts()));
            default -> {
            }
        }
        state.factsPrimitiveId().ifPresent(factsId ->
                registry.find(factsId).ifPresent(p -> produced.addAll(p.producedArtifacts())));
        return produced;
    }

    private Set<String> requiredArtifactsOf(StateNode state) {
        var required = new LinkedHashSet<String>();
        switch (state.behavior()) {
            case StateBehavior.AutoStep auto ->
                registry.find(auto.primitiveId()).ifPresent(p -> required.addAll(p.requiredArtifacts()));
            case StateBehavior.JobStep job ->
                registry.find(job.primitiveId()).ifPresent(p -> required.addAll(p.requiredArtifacts()));
            default -> {
            }
        }
        state.factsPrimitiveId().ifPresent(factsId ->
                registry.find(factsId).ifPresent(p -> required.addAll(p.requiredArtifacts())));
        return required;
    }

    private Set<String> requiredArtifactsOf(LinkSpec link) {
        var required = new LinkedHashSet<String>();
        link.guard().ifPresent(guard ->
                UnitGraph.predicatesOf(guard.expr()).forEach(predId ->
                        registry.find(predId).ifPresent(p -> required.addAll(p.requiredArtifacts()))));
        if (link.action() instanceof LinkAction.ExecutePrimitive(var primitiveId)) {
            registry.find(primitiveId).ifPresent(p -> required.addAll(p.requiredArtifacts()));
        }
        return required;
    }

    /**
     * Rule 9: every completion is admitted by the class of the outcome it is declared on, every firing completes each
     * of its steps exactly once on every path, and every cycle measure sits where the specification admits it.
     */
    private void checkStepCompletion(WorkflowDefinition definition, UnitGraph graph, List<Violation> violations) {
        var outcomeClasses = new HashMap<OutcomeTransition, Optional<OutcomeClass>>();
        for (var state : definition.states().values()) {
            for (var transition : UnitGraph.allTransitions(state.behavior())) {
                var outcomeClass = outcomeClassOf(definition, state, transition);
                outcomeClasses.put(transition, outcomeClass);
                var seenStepsOnTransition = new HashSet<String>();
                for (var completion : transition.completions()) {
                    if (!seenStepsOnTransition.add(completion.stepId())) {
                        violations.add(new Violation(VerificationRule.STEP_COMPLETION, state.id(),
                                completion.location(), "the step '%s' is completed twice on the same transition"
                                .formatted(completion.stepId())));
                    }
                    checkCompletionAdmission(state, transition, completion, outcomeClass, violations);
                }
            }
        }
        for (var state : definition.states().values()) {
            checkStateCycleMeasures(definition, graph, state, outcomeClasses, violations);
        }
        checkFirings(definition, graph, outcomeClasses, violations);
    }

    /**
     * Checks that the class of the outcome admits the status a completion settles the step with (Rule 9, "Class
     * admits the completion"). An outcome whose class cannot be established admits no completion.
     */
    private static void checkCompletionAdmission(StateNode state, OutcomeTransition transition,
            StepCompletion completion, Optional<OutcomeClass> outcomeClassOpt, List<Violation> violations) {
        var stepId = completion.stepId();
        if (outcomeClassOpt.isEmpty()) {
            violations.add(new Violation(VerificationRule.STEP_COMPLETION, state.id(), completion.location(),
                    "the step '%s' is completed on the outcome '%s', whose class cannot be established"
                            .formatted(stepId, transition.outcomeName())));
            return;
        }
        var outcomeClass = outcomeClassOpt.get();
        var detail = switch (completion.status()) {
            case DONE -> outcomeClass == OutcomeClass.SUCCESS ? null
                    : "is completed as done on an outcome of class %s, which is not SUCCESS".formatted(outcomeClass);
            // Whether a lost or infeasible route is the declared conservative route of an optional step is not
            // checked: the AST declares no optional step and no conservative route (ADR 002, named seam).
            case SKIPPED -> outcomeClass == OutcomeClass.SUCCESS || outcomeClass == OutcomeClass.SKIP
                    || (outcomeClass == OutcomeClass.INDETERMINATE
                    && UnitGraph.LOST_OUTCOME.equals(transition.outcomeName()))
                    || (outcomeClass == OutcomeClass.FAILURE && "infeasible".equals(transition.outcomeName()))
                    ? null : "is completed as skipped on an outcome of class %s".formatted(outcomeClass);
            case FAILED -> outcomeClass != OutcomeClass.UNSETTLED && outcomeClass != OutcomeClass.STEPPED_OUT ? null
                    : "is completed as failed on an outcome of class %s".formatted(outcomeClass);
            case WAIVED -> isRemediationWaiver(state, transition) && outcomeClass == OutcomeClass.SUCCESS ? null
                    : "is completed as waived on a transition that is not the 'waived' outcome of a call to a"
                    + " remediation unit";
        };
        if (detail != null) {
            violations.add(new Violation(VerificationRule.STEP_COMPLETION, state.id(), completion.location(),
                    "the step '%s' %s".formatted(stepId, detail)));
        }
    }

    private static boolean isRemediationWaiver(StateNode state, OutcomeTransition transition) {
        return state.behavior() instanceof StateBehavior.SubWorkflowCall call
                && call.subWorkflowId().startsWith(REMEDIATION_UNIT_PREFIX)
                && "waived".equals(transition.outcomeName());
    }

    /**
     * Checks every firing of the unit (Rule 9, "Once per firing"), one step at a time. A firing opens at an opening:
     * the start state, a named region entry state, or a link target of a {@link CycleMeasure#STEP_LIST} hub. An
     * opening begins the steps completed on a path from it before another opening is reached, but for a step
     * completed on a transition into it from outside that reach, which the opening follows rather than begins; a step
     * completed on a loop-back into the opening from inside the reach stays begun. A firing of a step ends at the
     * next opening of the same step once the step is completed (a re-entry into one of its openings while it is open
     * continues the firing), at {@link #MERGE_GATE}, or at a terminal state; it passes the opening of another step
     * without ending. Every such path completes the step exactly once. A path ends early, without judgement, on an
     * outcome of class {@code UNSETTLED} or {@code STEPPED_OUT}; entering one of {@link #IMPLICIT_FAILED_STATES}
     * completes the open step as {@code failed} (ADR 021).
     */
    private void checkFirings(WorkflowDefinition definition, UnitGraph graph,
            Map<OutcomeTransition, Optional<OutcomeClass>> outcomeClasses, List<Violation> violations) {
        var openings = openingsOf(definition);
        var stepsByOpening = new LinkedHashMap<String, Set<String>>();
        for (var opening : openings) {
            stepsByOpening.put(opening, stepsBegunAt(definition, graph, openings, opening, outcomeClasses));
        }
        var reported = new HashSet<Violation>();
        stepsByOpening.forEach((opening, steps) -> steps.forEach(stepId -> {
            var openingsOfStep = new HashSet<String>();
            stepsByOpening.forEach((candidate, begun) -> {
                if (begun.contains(stepId)) {
                    openingsOfStep.add(candidate);
                }
            });
            walkFiring(definition, openingsOfStep, opening, stepId, outcomeClasses, reported);
        }));
        reported.stream()
                .sorted(Comparator.comparing((Violation violation) -> violation.location().line())
                        .thenComparing(Violation::message))
                .forEach(violations::add);
    }

    /**
     * @return the steps an opening begins: those completed on a path from it before another opening is reached, less
     *         every step completed on a transition into it from a state outside that reach; a completion on a
     *         loop-back from inside the reach into the opening settles a firing of the opening itself and keeps the
     *         step
     */
    private static Set<String> stepsBegunAt(WorkflowDefinition definition, UnitGraph graph, Set<String> openings,
            String opening, Map<OutcomeTransition, Optional<OutcomeClass>> outcomeClasses) {
        var reach = firingReach(definition, openings, opening, outcomeClasses);
        var steps = stepsCompletedIn(definition, reach);
        for (var source : graph.predecessors(opening)) {
            var sourceState = definition.states().get(source);
            if (sourceState == null || reach.contains(source)) {
                continue;
            }
            UnitGraph.allTransitions(sourceState.behavior()).stream()
                    .filter(transition -> opening.equals(transition.targetStateId()))
                    .flatMap(transition -> transition.completions().stream())
                    .forEach(completion -> steps.remove(completion.stepId()));
        }
        return steps;
    }

    private Set<String> openingsOf(WorkflowDefinition definition) {
        var openings = new LinkedHashSet<String>();
        openings.add(definition.startState());
        definition.states().keySet().stream().filter(REGION_ENTRY_STATES::contains).forEach(openings::add);
        for (var state : definition.states().values()) {
            if (cycleMeasureOf(state) == CycleMeasure.STEP_LIST) {
                for (var transition : UnitGraph.allTransitions(state.behavior())) {
                    var target = definition.states().get(transition.targetStateId());
                    if (target != null && !isTerminal(target) && !MERGE_GATE.equals(target.id())
                            && !IMPLICIT_FAILED_STATES.contains(target.id())) {
                        openings.add(target.id());
                    }
                }
            }
        }
        return openings;
    }

    /**
     * @return the states a path from the opening reaches before it ends or another opening is reached, the opening
     *         included
     */
    private static Set<String> firingReach(WorkflowDefinition definition, Set<String> openings, String opening,
            Map<OutcomeTransition, Optional<OutcomeClass>> outcomeClasses) {
        var seen = new LinkedHashSet<>(Set.of(opening));
        var queue = new ArrayDeque<>(List.of(opening));
        while (!queue.isEmpty()) {
            var state = definition.states().get(queue.poll());
            if (state == null) {
                continue;
            }
            for (var transition : UnitGraph.allTransitions(state.behavior())) {
                var target = transition.targetStateId();
                var targetState = definition.states().get(target);
                boolean ends = endsEarly(transition, outcomeClasses) || IMPLICIT_FAILED_STATES.contains(target)
                        || MERGE_GATE.equals(target) || targetState == null || isTerminal(targetState)
                        || (openings.contains(target) && !target.equals(opening));
                if (!ends && seen.add(target)) {
                    queue.add(target);
                }
            }
        }
        return seen;
    }

    /** @return the steps completed on a transition that leaves one of the states */
    private static Set<String> stepsCompletedIn(WorkflowDefinition definition, Set<String> stateIds) {
        var steps = new LinkedHashSet<String>();
        stateIds.stream().map(definition.states()::get).filter(Objects::nonNull)
                .flatMap(state -> UnitGraph.allTransitions(state.behavior()).stream())
                .flatMap(transition -> transition.completions().stream())
                .forEach(completion -> steps.add(completion.stepId()));
        return steps;
    }

    /** A position of a firing path: the state reached and whether the step has been completed on the way. */
    private record FiringPosition(String stateId, boolean completed) {
    }

    /**
     * Walks every path of a firing of one step from its opening and reports a second completion, a path that ends
     * without one, and a completion on the transition into an implicit-failed state.
     *
     * @param openingsOfStep the openings that begin the step; entering one ends the path once the step is completed
     */
    private void walkFiring(WorkflowDefinition definition, Set<String> openingsOfStep, String opening, String stepId,
            Map<OutcomeTransition, Optional<OutcomeClass>> outcomeClasses, Set<Violation> reported) {
        var start = new FiringPosition(opening, false);
        var seen = new HashSet<>(Set.of(start));
        var stack = new ArrayDeque<>(List.of(start));
        while (!stack.isEmpty()) {
            var position = stack.pop();
            var state = definition.states().get(position.stateId());
            if (state == null) {
                continue;
            }
            for (var transition : UnitGraph.allTransitions(state.behavior())) {
                var completion = transition.completions().stream()
                        .filter(candidate -> candidate.stepId().equals(stepId)).findFirst();
                if (completion.isPresent() && position.completed()) {
                    reported.add(new Violation(VerificationRule.STEP_COMPLETION, state.id(),
                            completion.get().location(), "the step '%s' is completed twice in one firing from '%s'"
                            .formatted(stepId, opening)));
                    continue;
                }
                var completed = position.completed() || completion.isPresent();
                var target = transition.targetStateId();
                if (endsEarly(transition, outcomeClasses)) {
                    continue;
                }
                if (IMPLICIT_FAILED_STATES.contains(target)) {
                    if (completion.isPresent()) {
                        reported.add(new Violation(VerificationRule.STEP_COMPLETION, state.id(),
                                completion.get().location(),
                                "the step '%s' is completed on the transition into '%s', whose entry records the open firing as failed"
                                        .formatted(stepId, target)));
                    }
                    continue;
                }
                var targetState = definition.states().get(target);
                // The next opening of the same step ends the firing once the step is completed; entering it while
                // the step is open continues the firing, and the opening of another step does not end it.
                boolean ends = MERGE_GATE.equals(target) || (targetState != null && isTerminal(targetState))
                        || (completed && openingsOfStep.contains(target));
                if (ends) {
                    if (!completed) {
                        reported.add(new Violation(VerificationRule.STEP_COMPLETION, state.id(), transition.location(),
                                "the step '%s' is not completed on a firing path from '%s' that ends with the outcome '%s' into '%s'"
                                        .formatted(stepId, opening, transition.outcomeName(), target)));
                    }
                } else if (targetState != null) {
                    var next = new FiringPosition(target, completed);
                    if (seen.add(next)) {
                        stack.push(next);
                    }
                }
            }
        }
    }

    private static boolean endsEarly(OutcomeTransition transition,
            Map<OutcomeTransition, Optional<OutcomeClass>> outcomeClasses) {
        return outcomeClasses.getOrDefault(transition, Optional.empty())
                .filter(outcomeClass -> outcomeClass == OutcomeClass.UNSETTLED
                        || outcomeClass == OutcomeClass.STEPPED_OUT)
                .isPresent();
    }

    private CycleMeasure cycleMeasureOf(StateNode state) {
        return primitiveOfState(state).map(Primitive::cycleMeasure).orElse(CycleMeasure.NONE);
    }

    private Optional<Primitive<?, ?>> primitiveOfState(StateNode state) {
        return switch (state.behavior()) {
            case StateBehavior.AutoStep auto -> registry.find(auto.primitiveId());
            case StateBehavior.JobStep job -> registry.find(job.primitiveId());
            case StateBehavior.ModelJob _, StateBehavior.QuestionWait _, StateBehavior.InteractiveLinks _,
                StateBehavior.SubWorkflowCall _, StateBehavior.TerminalState _ -> Optional.empty();
        };
    }

    /**
     * Checks where a primitive's cycle measure may sit (Rule 9, "Cycle measures"), naming no state id: a
     * {@code STEP_LIST} hub belongs to a unit with a step list (a unit that completes steps), and every pass of it
     * settles exactly one step; an {@code ARCHIVE_STEPS} state belongs to an epic unit, with an archive path from it
     * to a terminal state that commits and lands (ADR 022); the transition of the exhaustion outcome of an
     * {@code ATTEMPT_CAP} primitive leaves every cycle through the state (that the outcome exists and has the class
     * {@code FAILURE} is refused at registration by {@code PrimitiveRegistry}).
     */
    private void checkStateCycleMeasures(WorkflowDefinition definition, UnitGraph graph, StateNode state,
            Map<OutcomeTransition, Optional<OutcomeClass>> outcomeClasses, List<Violation> violations) {
        var primitiveOpt = primitiveOfState(state);
        if (primitiveOpt.isEmpty()) {
            return;
        }
        var primitive = primitiveOpt.get();
        switch (primitive.cycleMeasure()) {
            case STEP_LIST -> {
                if (!hasStepList(definition)) {
                    violations.add(new Violation(VerificationRule.STEP_COMPLETION, state.id(), state.location(),
                            "the cycle measure STEP_LIST of the state '%s' is admitted only on the dispatch hub of a unit with a step list, but the unit '%s' completes no step"
                                    .formatted(state.id(), definition.id())));
                } else {
                    checkStepListPasses(definition, state, outcomeClasses, violations);
                }
            }
            case ARCHIVE_STEPS -> {
                if (definition.scope() != WorkflowScope.EPIC) {
                    violations.add(new Violation(VerificationRule.STEP_COMPLETION, state.id(), state.location(),
                            "the cycle measure ARCHIVE_STEPS of the state '%s' is admitted only in an epic unit, but the unit has scope %s"
                                    .formatted(state.id(), definition.scope())));
                } else if (!reachesTerminal(definition, graph, state)) {
                    violations.add(new Violation(VerificationRule.STEP_COMPLETION, state.id(), state.location(),
                            "the cycle measure ARCHIVE_STEPS of the state '%s' is admitted only on the archive path, but no path leads from it to a terminal state"
                                    .formatted(state.id())));
                } else {
                    checkArchiveCommitAndLand(definition, graph, state, violations);
                }
            }
            case ATTEMPT_CAP -> checkAttemptCap(graph, state, primitive, violations);
            case NONE -> {
                // no measure, nothing to place
            }
        }
    }

    private void checkAttemptCap(UnitGraph graph, StateNode state, Primitive<?, ?> primitive,
            List<Violation> violations) {
        // PrimitiveRegistry refuses to register an ATTEMPT_CAP primitive without an exhaustion outcome among its
        // outcomes of class FAILURE (ATTEMPT_CAP_WITHOUT_FAILURE_EXHAUSTION), so a registered primitive always has
        // one; a primitive that has none is still refused here rather than passed.
        var exhaustionOpt = primitive.exhaustionOutcome();
        if (exhaustionOpt.isEmpty()) {
            violations.add(new Violation(VerificationRule.STEP_COMPLETION, state.id(), state.location(),
                    "the primitive '%s' of the state '%s' declares the cycle measure ATTEMPT_CAP but no exhaustion outcome"
                            .formatted(primitive.id(), state.id())));
            return;
        }
        var exhaustion = exhaustionOpt.get();
        for (var transition : UnitGraph.allTransitions(state.behavior())) {
            if (exhaustion.equals(transition.outcomeName())
                    && graph.forwardClosure(Set.of(transition.targetStateId())).contains(state.id())) {
                violations.add(new Violation(VerificationRule.STEP_COMPLETION, state.id(), transition.location(),
                        "the exhaustion transition '%s' of the attempt cap of the state '%s' does not leave the cycle"
                                .formatted(exhaustion, state.id())));
            }
        }
    }

    /**
     * A position of a pass of a dispatch hub: the state reached, the distinct steps settled on the way (at most two)
     * and the first of them completed a second time on the way ({@code null} while none is).
     */
    private record PassPosition(String stateId, Set<String> settled, String repeated) {
    }

    /**
     * What the passes that leave a dispatch hub with one transition settle: the number of distinct steps of each pass
     * (at most two) and the steps that some pass completes more than once.
     */
    private record PassSettlements(Set<Integer> distinctCounts, Set<String> repeatedSteps) {
    }

    /**
     * Rule 9, {@code STEP_LIST}: every pass of the dispatch hub fires and settles exactly one completion of one step.
     * A pass is a path that leaves the hub on one of its outcomes and returns to it; a path that enters a terminal
     * state, {@link #MERGE_GATE} or an implicit-failed state leaves the step list instead, and one that ends early on
     * an {@code UNSETTLED} or {@code STEPPED_OUT} outcome hands its step back. Each outcome of the hub with a pass that
     * settles no step, or more than one distinct step, is refused at its transition, and so is each step that a pass
     * of it completes more than once, named as completed twice whether or not the firing check also refuses the
     * second completion (ADR 022).
     */
    private static void checkStepListPasses(WorkflowDefinition definition, StateNode hub,
            Map<OutcomeTransition, Optional<OutcomeClass>> outcomeClasses, List<Violation> violations) {
        for (var start : UnitGraph.allTransitions(hub.behavior())) {
            var settlements = passSettlements(definition, hub.id(), start, outcomeClasses);
            if (settlements.distinctCounts().contains(0)) {
                violations.add(new Violation(VerificationRule.STEP_COMPLETION, hub.id(), start.location(),
                        "the cycle measure STEP_LIST of the state '%s' requires every pass to settle one step, but a pass that leaves it with the outcome '%s' settles none"
                                .formatted(hub.id(), start.outcomeName())));
            }
            if (settlements.distinctCounts().contains(2)) {
                violations.add(new Violation(VerificationRule.STEP_COMPLETION, hub.id(), start.location(),
                        "the cycle measure STEP_LIST of the state '%s' requires every pass to settle one step, but a pass that leaves it with the outcome '%s' settles more than one"
                                .formatted(hub.id(), start.outcomeName())));
            }
            for (var stepId : settlements.repeatedSteps()) {
                violations.add(new Violation(VerificationRule.STEP_COMPLETION, hub.id(), start.location(),
                        "the cycle measure STEP_LIST of the state '%s' requires every pass to settle one step once, but a pass that leaves it with the outcome '%s' completes the step '%s' twice"
                                .formatted(hub.id(), start.outcomeName(), stepId)));
            }
        }
    }

    /**
     * @return the number of distinct steps settled, at most two, on each pass that leaves the hub with the transition
     *         and returns to it, and the steps completed more than once on such a pass, in name order; both empty when
     *         no path of it returns
     */
    private static PassSettlements passSettlements(WorkflowDefinition definition, String hubId,
            OutcomeTransition start, Map<OutcomeTransition, Optional<OutcomeClass>> outcomeClasses) {
        var settlements = new PassSettlements(new HashSet<>(), new TreeSet<>());
        var seen = new HashSet<PassPosition>();
        var stack = new ArrayDeque<PassPosition>();
        advancePass(definition, hubId, start, new PassPosition(hubId, Set.of(), null), outcomeClasses, settlements,
                seen, stack);
        while (!stack.isEmpty()) {
            var position = stack.pop();
            var state = definition.states().get(position.stateId());
            for (var transition : UnitGraph.allTransitions(state.behavior())) {
                advancePass(definition, hubId, transition, position, outcomeClasses, settlements, seen, stack);
            }
        }
        return settlements;
    }

    private static void advancePass(WorkflowDefinition definition, String hubId, OutcomeTransition transition,
            PassPosition before, Map<OutcomeTransition, Optional<OutcomeClass>> outcomeClasses,
            PassSettlements settlements, Set<PassPosition> seen, ArrayDeque<PassPosition> stack) {
        if (endsEarly(transition, outcomeClasses)) {
            return;
        }
        var settledSteps = new LinkedHashSet<>(before.settled());
        var repeated = before.repeated();
        for (var completion : transition.completions()) {
            var stepId = completion.stepId();
            if (settledSteps.contains(stepId)) {
                if (repeated == null) {
                    repeated = stepId;
                }
            } else if (settledSteps.size() < 2) {
                settledSteps.add(stepId);
            }
        }
        var settled = Set.copyOf(settledSteps);
        var target = transition.targetStateId();
        if (hubId.equals(target)) {
            settlements.distinctCounts().add(settled.size());
            if (repeated != null) {
                settlements.repeatedSteps().add(repeated);
            }
            return;
        }
        var targetState = definition.states().get(target);
        if (targetState == null || isTerminal(targetState) || MERGE_GATE.equals(target)
                || IMPLICIT_FAILED_STATES.contains(target)) {
            return;
        }
        var next = new PassPosition(target, settled, repeated);
        if (seen.add(next)) {
            stack.push(next);
        }
    }

    /**
     * Rule 9, {@code ARCHIVE_STEPS}: the archive path of the state commits and lands before it ends in the terminal
     * (the remaining archive steps commit, land, archived). A cycle through the state passes a call of the commit
     * sub-workflow {@code git.commit_and_push}, and a cycle through it passes a {@code job} or {@code composite_job}
     * state, the landing; each part that is missing is refused at the state (ADR 022).
     */
    private static void checkArchiveCommitAndLand(WorkflowDefinition definition, UnitGraph graph, StateNode state,
            List<Violation> violations) {
        var onCycle = new LinkedHashSet<>(graph.forwardClosure(graph.successors(state.id())));
        onCycle.retainAll(graph.backwardClosure(Set.of(state.id())));
        var cycleStates = onCycle.stream().map(definition.states()::get).filter(Objects::nonNull).toList();
        var commits = cycleStates.stream().anyMatch(candidate -> candidate.behavior()
                instanceof StateBehavior.SubWorkflowCall call && CompiledInvariants.GIT_UNIT.equals(call.subWorkflowId()));
        var lands = cycleStates.stream().anyMatch(candidate -> candidate.behavior() instanceof StateBehavior.JobStep);
        if (!commits) {
            violations.add(new Violation(VerificationRule.STEP_COMPLETION, state.id(), state.location(),
                    "the cycle measure ARCHIVE_STEPS of the state '%s' is admitted only on an archive path that commits, but no cycle through it passes a call of '%s'"
                            .formatted(state.id(), CompiledInvariants.GIT_UNIT)));
        }
        if (!lands) {
            violations.add(new Violation(VerificationRule.STEP_COMPLETION, state.id(), state.location(),
                    "the cycle measure ARCHIVE_STEPS of the state '%s' is admitted only on an archive path that lands, but no cycle through it passes a job state"
                            .formatted(state.id())));
        }
    }

    private static boolean hasStepList(WorkflowDefinition definition) {
        return definition.states().values().stream()
                .flatMap(state -> UnitGraph.allTransitions(state.behavior()).stream())
                .anyMatch(transition -> !transition.completions().isEmpty());
    }

    private static boolean reachesTerminal(WorkflowDefinition definition, UnitGraph graph, StateNode state) {
        return graph.forwardClosure(Set.of(state.id())).stream()
                .map(definition.states()::get)
                .anyMatch(reached -> reached != null && isTerminal(reached));
    }

    private Optional<OutcomeClass> outcomeClassOf(WorkflowDefinition definition, StateNode state,
            OutcomeTransition transition) {
        var outcomeName = transition.outcomeName();
        switch (state.behavior()) {
            case StateBehavior.AutoStep auto -> {
                var primitive = registry.find(auto.primitiveId());
                if (primitive.isPresent()) {
                    for (var outcome : primitive.get().outcomeType().getEnumConstants()) {
                        var po = (PrimitiveOutcome) outcome;
                        if (po.wireName().equals(outcomeName)) {
                            return Optional.of(po.outcomeClass());
                        }
                    }
                }
            }
            case StateBehavior.JobStep job -> {
                var primitive = registry.find(job.primitiveId());
                if (primitive.isPresent()) {
                    for (var outcome : primitive.get().outcomeType().getEnumConstants()) {
                        var po = (PrimitiveOutcome) outcome;
                        if (po.wireName().equals(outcomeName)) {
                            return Optional.of(po.outcomeClass());
                        }
                    }
                }
            }
            case StateBehavior.ModelJob modelJob -> {
                if (UnitGraph.LOST_OUTCOME.equals(outcomeName)) {
                    return Optional.of(OutcomeClass.INDETERMINATE);
                }
                var entityKind = entityKinds.find(modelJob.entityKind());
                if (entityKind.isPresent()) {
                    var primitive = registry.find(entityKind.get().returnEvaluationPrimitiveId());
                    if (primitive.isPresent()) {
                        for (var outcome : primitive.get().outcomeType().getEnumConstants()) {
                            var po = (PrimitiveOutcome) outcome;
                            if (po.wireName().equals(outcomeName)) {
                                return Optional.of(po.outcomeClass());
                            }
                        }
                    }
                }
            }
            case StateBehavior.SubWorkflowCall call -> {
                if ("stepped_out".equals(outcomeName)) {
                    return Optional.of(OutcomeClass.STEPPED_OUT);
                }
                var callee = catalog.find(call.subWorkflowId());
                if (callee.isPresent()) {
                    for (var calleeState : callee.get().states().values()) {
                        if (calleeState.terminalOutcome().isPresent()
                                && calleeState.terminalOutcome().get().id().equals(outcomeName)) {
                            return Optional.of(calleeState.terminalOutcome().get().outcomeClass());
                        }
                    }
                }
            }
            case StateBehavior.QuestionWait question -> {
                // The resolution outcomes of a question class carry no outcome class: the specification gives each
                // class its closed outcome set and no class per outcome (Workflow DSL, Question States), and every
                // resolution enters a re-entrant state that decides. A completion on one is therefore refused.
                if (question.transitions().contains(transition)) {
                    return Optional.empty();
                }
                for (var link : question.links()) {
                    if (link.transitions().contains(transition)
                            && link.action() instanceof LinkAction.ExecutePrimitive(var primId)) {
                        var primitive = registry.find(primId);
                        if (primitive.isPresent()) {
                            for (var outcome : primitive.get().outcomeType().getEnumConstants()) {
                                var po = (PrimitiveOutcome) outcome;
                                if (po.wireName().equals(outcomeName)) {
                                    return Optional.of(po.outcomeClass());
                                }
                            }
                        }
                    }
                }
                return Optional.empty();
            }
            case StateBehavior.InteractiveLinks _ -> {
                for (var link : UnitGraph.declaredLinks(state.behavior())) {
                    if (link.transitions().contains(transition)
                            && link.action() instanceof LinkAction.ExecutePrimitive(var primId)) {
                        var primitive = registry.find(primId);
                        if (primitive.isPresent()) {
                            for (var outcome : primitive.get().outcomeType().getEnumConstants()) {
                                var po = (PrimitiveOutcome) outcome;
                                if (po.wireName().equals(outcomeName)) {
                                    return Optional.of(po.outcomeClass());
                                }
                            }
                        }
                    }
                }
                return Optional.empty();
            }
            case StateBehavior.TerminalState _ -> {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private void checkSessionDrivenLinks(WorkflowDefinition definition, List<Violation> violations) {
        if (definition.scope() != WorkflowScope.PLAN && definition.scope() != WorkflowScope.EPIC) {
            return;
        }
        for (var state : definition.states().values()) {
            if (state.behavior() instanceof StateBehavior.InteractiveLinks) {
                violations.add(new Violation(VerificationRule.SESSION_DRIVEN_LINKS, state.id(), state.location(),
                        "the resting link state '%s' is not admitted in a unit of scope %s"
                                .formatted(state.id(), definition.scope())));
            } else if (state.behavior() instanceof StateBehavior.QuestionWait question
                    && !question.links().isEmpty()) {
                violations.add(new Violation(VerificationRule.SESSION_DRIVEN_LINKS, state.id(), state.location(),
                        "declared links on the question state '%s' are not admitted in a unit of scope %s"
                                .formatted(state.id(), definition.scope())));
            } else if (state.behavior() instanceof StateBehavior.SubWorkflowCall call) {
                firstCalleeWithLinks(call.subWorkflowId()).ifPresent(callee -> violations.add(new Violation(
                        VerificationRule.SESSION_DRIVEN_LINKS, state.id(), state.location(),
                        "the sub-workflow '%s' reached by the call contains resting links, which are not admitted in a unit of scope %s"
                                .formatted(callee, definition.scope()))));
            }
        }
    }

    /**
     * Walks the sub-workflows a call reaches, the callee and every unit it calls in turn (Rule 10 covers every
     * sub-workflow a plan or epic unit calls, ADR 011). Each unit is visited once, so a call cycle, which Rule 7
     * refuses, does not loop; an unregistered callee is refused by Rule 6 and contributes nothing.
     *
     * @param calleeId the unit the call names
     * @return the first reached unit, in call order, with a resting link state or a question state with declared links
     */
    private Optional<String> firstCalleeWithLinks(String calleeId) {
        var visited = new HashSet<String>();
        var queue = new ArrayDeque<>(List.of(calleeId));
        while (!queue.isEmpty()) {
            var id = queue.poll();
            if (!visited.add(id)) {
                continue;
            }
            var calleeOpt = catalog.find(id);
            if (calleeOpt.isEmpty()) {
                continue;
            }
            for (var calleeState : calleeOpt.get().states().values()) {
                switch (calleeState.behavior()) {
                    case StateBehavior.InteractiveLinks _ -> {
                        return Optional.of(id);
                    }
                    case StateBehavior.QuestionWait question when !question.links().isEmpty() -> {
                        return Optional.of(id);
                    }
                    case StateBehavior.SubWorkflowCall nested -> queue.add(nested.subWorkflowId());
                    default -> {
                        // no link and no further call
                    }
                }
            }
        }
        return Optional.empty();
    }

    private void checkCycleAwaitedEvent(WorkflowDefinition definition, List<Violation> violations) {
        var cycles = UnitGraph.findSimpleCycles(definition);
        var reportedCycles = new HashSet<String>();
        for (var cycle : cycles) {
            var cycleKey = String.join(" -> ", cycle);
            if (!reportedCycles.add(cycleKey)) {
                continue;
            }
            if (isEngineChainedCycle(definition, cycle)) {
                if (!hasCycleMeasure(definition, cycle)) {
                    var firstState = definition.states().get(cycle.getFirst());
                    var location = firstState != null ? firstState.location() : definition.location();
                    violations.add(new Violation(VerificationRule.CYCLE_AWAITED_EVENT, cycle.getFirst(), location,
                            "the engine-chained cycle '%s' has no cycle measure".formatted(cycleKey)));
                }
            } else {
                if (!hasAwaitedEvent(definition, cycle)) {
                    var firstState = definition.states().get(cycle.getFirst());
                    var location = firstState != null ? firstState.location() : definition.location();
                    violations.add(new Violation(VerificationRule.CYCLE_AWAITED_EVENT, cycle.getFirst(), location,
                            "the cycle '%s' has no awaited event".formatted(cycleKey)));
                }
            }
        }
    }

    private static boolean isEngineChainedCycle(WorkflowDefinition definition, List<String> cycle) {
        for (int i = 0; i < cycle.size() - 1; i++) {
            var state = definition.states().get(cycle.get(i));
            if (state == null) {
                return false;
            }
            if (!(state.behavior() instanceof StateBehavior.AutoStep
                    || state.behavior() instanceof StateBehavior.SubWorkflowCall)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasCycleMeasure(WorkflowDefinition definition, List<String> cycle) {
        for (int i = 0; i < cycle.size() - 1; i++) {
            var state = definition.states().get(cycle.get(i));
            if (state != null && state.behavior() instanceof StateBehavior.AutoStep auto) {
                var primitive = registry.find(auto.primitiveId());
                if (primitive.isPresent() && primitive.get().cycleMeasure() != CycleMeasure.NONE) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasAwaitedEvent(WorkflowDefinition definition, List<String> cycle) {
        for (int i = 0; i < cycle.size() - 1; i++) {
            var fromId = cycle.get(i);
            var toId = cycle.get(i + 1);
            if (stepAwaitsEvent(definition, fromId, toId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Decides whether the edge {@code fromId -> toId} waits for an event (Rule 11b). A {@code job},
     * {@code composite_job} or {@code job llm} state awaits its job or task on every edge. A question state awaits
     * the answer or its deadline on the edges of its own resolution outcomes only; an edge of one of its declared links
     * is judged by that link like the edge of a link state.
     */
    private boolean stepAwaitsEvent(WorkflowDefinition definition, String fromId, String toId) {
        var state = definition.states().get(fromId);
        if (state == null) {
            return false;
        }
        return switch (state.behavior()) {
            case StateBehavior.JobStep _, StateBehavior.ModelJob _ -> true;
            case StateBehavior.QuestionWait question -> question.transitions().stream()
                    .anyMatch(transition -> toId.equals(transition.targetStateId()))
                    || traversedLinkAwaits(state, toId);
            case StateBehavior.InteractiveLinks _ -> traversedLinkAwaits(state, toId);
            case StateBehavior.AutoStep auto -> registry.find(auto.primitiveId())
                    .map(p -> p.awaitedEvent() != AwaitedEvent.NONE).orElse(false);
            case StateBehavior.SubWorkflowCall _, StateBehavior.TerminalState _ -> false;
        };
    }

    /**
     * Decides whether the edge {@code fromId -> toId} through a declared link of a link or question state waits for
     * an external event. A traversed link awaits when its form takes a submission (a {@code submit} link waits for
     * the model) or its primitive declares an awaited event; a {@code noop} link over a primitive with no awaited
     * event does not satisfy the cycle.
     */
    private boolean traversedLinkAwaits(StateNode state, String toId) {
        for (var link : UnitGraph.declaredLinks(state.behavior())) {
            boolean traverses = link.transitions().stream().anyMatch(t -> toId.equals(t.targetStateId()));
            if (!traverses) {
                continue;
            }
            if (link.form() == LinkForm.SUBMIT) {
                return true;
            }
            var primitive = registry.find(primitiveOf(link));
            if (primitive.isPresent() && primitive.get().awaitedEvent() != AwaitedEvent.NONE) {
                return true;
            }
        }
        return false;
    }

    private static void checkExitPaths(WorkflowDefinition definition, UnitGraph graph, boolean defaultLostRoute,
            List<Violation> violations) {
        if (definition.scope() == WorkflowScope.WORKSPACE) {
            var returning = graph.backwardClosure(Set.of(definition.startState()));
            definition.states().values().stream()
                    .filter(state -> !isTerminal(state) && !returning.contains(state.id()))
                    .forEach(state -> violations.add(new Violation(VerificationRule.EXIT_PATH, state.id(),
                            state.location(), "the state '%s' has no path back to the start state '%s'"
                            .formatted(state.id(), definition.startState()))));
            return;
        }
        var exits = new LinkedHashSet<String>();
        for (var state : definition.states().values()) {
            if (isTerminal(state) || UnitGraph.hasImplicitExit(definition.scope(), state, defaultLostRoute)) {
                exits.add(state.id());
            }
        }
        var leaving = graph.backwardClosure(exits);
        definition.states().values().stream().filter(state -> !leaving.contains(state.id()))
                .forEach(state -> violations.add(new Violation(VerificationRule.EXIT_PATH, state.id(),
                        state.location(), "the state '%s' has no path to a terminal state".formatted(state.id()))));
    }

    private void checkReleasedStates(WorkflowDefinition definition, List<Violation> violations) {
        for (var released : releasedStates) {
            if (definition.states().containsKey(released)) {
                continue;
            }
            var migrations = definition.migrations().stream()
                    .filter(migration -> migration.oldStateId().equals(released)).toList();
            if (migrations.stream().anyMatch(migration -> definition.states().containsKey(migration.newStateId()))) {
                continue;
            }
            var location = migrations.stream().findFirst().map(StateMigration::location)
                    .orElse(definition.location());
            var detail = migrations.isEmpty() ? "no migrate declaration names it"
                    : "its migrate declaration targets '%s', which is not a state"
                    .formatted(migrations.getFirst().newStateId());
            violations.add(new Violation(VerificationRule.RELEASED_STATE, released, location,
                    "the released state '%s' is not a state of the unit and %s".formatted(released, detail)));
        }
    }

    private static boolean isTerminal(StateNode state) {
        return state.behavior() instanceof StateBehavior.TerminalState;
    }

    private static String primitiveOf(LinkSpec link) {
        return switch (link.action()) {
            case LinkAction.ExecutePrimitive execute -> execute.primitiveId();
        };
    }

    /**
     * Orders the violations by the declaration order of their state, released names after every state, and within a
     * state by rule; the sort is stable, so violations of one state and rule keep the order they were found in.
     */
    private static List<Violation> ordered(WorkflowDefinition definition, List<Violation> violations) {
        Map<String, Integer> declarationIndex = new HashMap<>();
        for (var stateId : definition.states().keySet()) {
            declarationIndex.put(stateId, declarationIndex.size());
        }
        var afterEveryState = declarationIndex.size();
        var sorted = new ArrayList<>(violations);
        sorted.sort(Comparator
                .comparingInt((Violation violation) -> Optional.ofNullable(declarationIndex.get(violation.stateId()))
                        .orElse(afterEveryState))
                .thenComparing(Violation::rule));
        return sorted;
    }
}
