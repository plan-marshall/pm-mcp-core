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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
 * Reachability is graph reachability over every transition, enumerated under every
 * configuration combination the guards' {@code depends_on_config} keys admit (Rule 3
 * configuration reachability).
 *
 * @since 0.1
 */
public final class WorkflowValidator {

    /**
     * The abort terminals. The engine enters them by the operator's abort, a transition no unit declares, so they
     * count as reachable from every state and are exempt from {@link VerificationRule#REACHABILITY}.
     */
    private static final Set<String> ABORT_TERMINALS = Set.of("plan.aborted", "epic.aborted");

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
        checkSchemaBinding(definition, violations);
        checkStateIdUniqueness(definition, violations);
        checkStateExists(definition, violations);
        checkLeafTaskConstraint(definition, violations);
        var targetedAbortTerminals = new HashSet<String>();
        for (var state : definition.states().values()) {
            checkPrimitiveBinding(definition, state, violations);
            checkParameterEquivalence(state, violations);
            checkOutcomeCoverage(definition, state, violations);
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
        checkConfigReachability(definition, violations);
        checkExitPaths(definition, graph, violations);
        checkReleasedStates(definition, violations);
        return new VerificationResult(ordered(definition, violations));
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

    private void checkOutcomeCoverage(WorkflowDefinition definition, StateNode state, List<Violation> violations) {
        switch (state.behavior()) {
            case StateBehavior.AutoStep auto ->
                coverOutcomes(state, auto.primitiveId(), auto.transitions(), state.location(), violations);
            case StateBehavior.JobStep job ->
                coverOutcomes(state, job.primitiveId(), job.transitions(), state.location(), violations);
            case StateBehavior.ModelJob modelJob ->
                coverModelJobOutcomes(definition, state, modelJob, violations);
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

    private void coverModelJobOutcomes(WorkflowDefinition definition, StateNode state, StateBehavior.ModelJob modelJob,
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

        if (definition.scope() != WorkflowScope.PLAN && !routed.contains(UnitGraph.LOST_OUTCOME)) {
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

    private static void checkConfigReachability(WorkflowDefinition definition, List<Violation> violations) {
        var configKeys = UnitGraph.configKeysOf(definition);
        if (configKeys.isEmpty()) {
            return;
        }
        var combinations = UnitGraph.configCombinations(configKeys);
        var baseGraph = UnitGraph.of(definition);
        var baseReached = baseGraph.forwardClosure(Set.of(definition.startState()));

        // One active graph per combination. The reached set feeds the state-level report below, so the 2^N
        // enumeration is built once per combination rather than once per state per combination. There is no
        // transition-level report: configCombinations always includes the all-true assignment, under which
        // every link is active, so every base-reachable transition is reachable under at least that
        // combination and a not-reachable-under-any-combination predicate is unsatisfiable.
        var reachedByCombination = new ArrayList<Set<String>>(combinations.size());
        for (var combination : combinations) {
            var graph = UnitGraph.of(definition, combination);
            var reached = graph.forwardClosure(Set.of(definition.startState()));
            reachedByCombination.add(reached);
        }

        for (var state : definition.states().values()) {
            if (ABORT_TERMINALS.contains(state.id()) || !baseReached.contains(state.id())) {
                continue;
            }
            var unreachedCombinations = new ArrayList<Map<String, Boolean>>();
            for (int i = 0; i < combinations.size(); i++) {
                if (!reachedByCombination.get(i).contains(state.id())) {
                    unreachedCombinations.add(combinations.get(i));
                }
            }
            if (!unreachedCombinations.isEmpty()) {
                violations.add(new Violation(VerificationRule.CONFIG_REACHABILITY, state.id(), state.location(),
                        "the state '%s' is not reachable from the start state '%s' under configuration %s"
                                .formatted(state.id(), definition.startState(),
                                        formatCombinations(unreachedCombinations))));
            }
        }
    }

    private static boolean isReservedTarget(String target) {
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

    private static String formatCombinations(List<Map<String, Boolean>> combinations) {
        return combinations.stream()
                .map(WorkflowValidator::formatCombination)
                .collect(Collectors.joining(", "));
    }

    private static String formatCombination(Map<String, Boolean> combination) {
        return combination.entrySet().stream()
                .map(e -> "%s=%s".formatted(e.getKey(), e.getValue()))
                .collect(Collectors.joining(", ", "[", "]"));
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
                contractOutcomes.add("stepped_out");

                var outcomeCounts = new HashMap<String, Integer>();
                var resultGuardsOnDone = new ArrayList<ResultGuard>();
                for (var transition : call.transitions()) {
                    var outcome = transition.outcomeName();
                    outcomeCounts.put(outcome, outcomeCounts.getOrDefault(outcome, 0) + 1);
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

    private record StepCompletionSite(String stateId, OutcomeTransition transition, StepCompletion completion) {
    }

    private void checkStepCompletion(WorkflowDefinition definition, UnitGraph graph, List<Violation> violations) {
        var completionsByStep = new LinkedHashMap<String, List<StepCompletionSite>>();

        for (var state : definition.states().values()) {
            for (var transition : UnitGraph.allTransitions(state.behavior())) {
                var seenStepsOnTransition = new HashSet<String>();
                for (var comp : transition.completions()) {
                    if (!seenStepsOnTransition.add(comp.stepId())) {
                        violations.add(new Violation(VerificationRule.STEP_COMPLETION, state.id(), comp.location(),
                                "the step '%s' is completed twice on the same transition".formatted(comp.stepId())));
                    }
                    completionsByStep.computeIfAbsent(comp.stepId(), _ -> new ArrayList<>())
                            .add(new StepCompletionSite(state.id(), transition, comp));

                    var outcomeClassOpt = outcomeClassOf(definition, state, transition);
                    if (outcomeClassOpt.isEmpty()) {
                        violations.add(new Violation(VerificationRule.STEP_COMPLETION, state.id(),
                                comp.location(),
                                "the step '%s' is completed on the outcome '%s', whose class cannot be established"
                                        .formatted(comp.stepId(), transition.outcomeName())));
                        continue;
                    }
                    var outcomeClass = outcomeClassOpt.get();
                    var status = comp.status();
                    switch (status) {
                        case DONE -> {
                            if (outcomeClass != OutcomeClass.SUCCESS) {
                                violations.add(new Violation(VerificationRule.STEP_COMPLETION, state.id(),
                                        comp.location(),
                                        "the step '%s' is completed as done on an outcome of class %s, which is not SUCCESS"
                                                .formatted(comp.stepId(), outcomeClass)));
                            }
                        }
                        case SKIPPED -> {
                            boolean admitted = outcomeClass == OutcomeClass.SUCCESS
                                    || outcomeClass == OutcomeClass.SKIP
                                    || (outcomeClass == OutcomeClass.INDETERMINATE
                                    && "lost".equals(transition.outcomeName()))
                                    || (outcomeClass == OutcomeClass.FAILURE
                                    && "infeasible".equals(transition.outcomeName()));
                            if (!admitted) {
                                violations.add(new Violation(VerificationRule.STEP_COMPLETION, state.id(),
                                        comp.location(),
                                        "the step '%s' is completed as skipped on an outcome of class %s"
                                                .formatted(comp.stepId(), outcomeClass)));
                            }
                        }
                        case FAILED -> {
                            if (outcomeClass == OutcomeClass.UNSETTLED || outcomeClass == OutcomeClass.STEPPED_OUT) {
                                violations.add(new Violation(VerificationRule.STEP_COMPLETION, state.id(),
                                        comp.location(),
                                        "the step '%s' is completed as failed on an outcome of class %s"
                                                .formatted(comp.stepId(), outcomeClass)));
                            }
                        }
                        case WAIVED -> {
                            boolean admitted = state.behavior() instanceof StateBehavior.SubWorkflowCall
                                    && "waived".equals(transition.outcomeName());
                            if (!admitted) {
                                violations.add(new Violation(VerificationRule.STEP_COMPLETION, state.id(),
                                        comp.location(),
                                        "the step '%s' is completed as waived on a transition that is not the 'waived' outcome of a remediation call"
                                                .formatted(comp.stepId())));
                            }
                        }
                    }
                }
            }

            checkStateCycleMeasures(graph, state, violations);
        }

        var barriers = Set.of("finalize.next-step", definition.startState());
        for (var entry : completionsByStep.entrySet()) {
            var stepId = entry.getKey();
            var sites = entry.getValue();
            if (sites.size() > 1) {
                for (int i = 0; i < sites.size(); i++) {
                    for (int j = 0; j < sites.size(); j++) {
                        if (i != j) {
                            var site1 = sites.get(i);
                            var site2 = sites.get(j);
                            if (graph.hasPathAvoiding(site1.transition().targetStateId(), site2.stateId(), barriers)) {
                                violations.add(new Violation(VerificationRule.STEP_COMPLETION, site2.stateId(),
                                        site2.completion().location(),
                                        "the step '%s' is completed twice on a path without an intervening dispatch"
                                                .formatted(stepId)));
                            }
                        }
                    }
                }
            }
        }
    }

    private void checkStateCycleMeasures(UnitGraph graph, StateNode state, List<Violation> violations) {
        String primitiveId = switch (state.behavior()) {
            case StateBehavior.AutoStep auto -> auto.primitiveId();
            case StateBehavior.JobStep job -> job.primitiveId();
            default -> null;
        };
        if (primitiveId == null) {
            return;
        }
        var primOpt = registry.find(primitiveId);
        if (primOpt.isEmpty()) {
            return;
        }
        var prim = primOpt.get();
        var measure = prim.cycleMeasure();
        if (measure == CycleMeasure.STEP_LIST && !"finalize.next-step".equals(state.id())) {
            violations.add(new Violation(VerificationRule.STEP_COMPLETION, state.id(), state.location(),
                    "the cycle measure STEP_LIST is admitted only on the dispatch hub 'finalize.next-step', but declared on '%s'"
                            .formatted(state.id())));
        } else if (measure == CycleMeasure.ARCHIVE_STEPS && !"epic.next-work".equals(state.id())) {
            violations.add(new Violation(VerificationRule.STEP_COMPLETION, state.id(), state.location(),
                    "the cycle measure ARCHIVE_STEPS is admitted only on 'epic.next-work', but declared on '%s'"
                            .formatted(state.id())));
        } else if (measure == CycleMeasure.ATTEMPT_CAP) {
            prim.exhaustionOutcome().ifPresent(exhaustion -> {
                for (var t : UnitGraph.allTransitions(state.behavior())) {
                    if (exhaustion.equals(t.outcomeName())) {
                        if (graph.forwardClosure(Set.of(t.targetStateId())).contains(state.id())) {
                            violations.add(new Violation(VerificationRule.STEP_COMPLETION, state.id(), t.location(),
                                    "the exhaustion transition of the attempt cap on state '%s' does not leave the cycle"
                                            .formatted(state.id())));
                        }
                    }
                }
            });
        }
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
                if (question.transitions().contains(transition)) {
                    return Optional.of(OutcomeClass.SUCCESS);
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
                catalog.find(call.subWorkflowId()).ifPresent(callee -> {
                    for (var calleeState : callee.states().values()) {
                        if (calleeState.behavior() instanceof StateBehavior.InteractiveLinks
                                || (calleeState.behavior() instanceof StateBehavior.QuestionWait qw
                                && !qw.links().isEmpty())) {
                            violations.add(new Violation(VerificationRule.SESSION_DRIVEN_LINKS, state.id(),
                                    state.location(),
                                    "the called sub-workflow '%s' contains resting links, which are not admitted in a unit of scope %s"
                                            .formatted(callee.id(), definition.scope())));
                            break;
                        }
                    }
                });
            }
        }
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

    private boolean stepAwaitsEvent(WorkflowDefinition definition, String fromId, String toId) {
        var state = definition.states().get(fromId);
        if (state == null) {
            return false;
        }
        return switch (state.behavior()) {
            case StateBehavior.JobStep job -> true;
            case StateBehavior.ModelJob modelJob -> true;
            case StateBehavior.QuestionWait question -> true;
            case StateBehavior.InteractiveLinks links -> traversedLinkAwaits(state, toId);
            case StateBehavior.AutoStep auto -> registry.find(auto.primitiveId())
                    .map(p -> p.awaitedEvent() != AwaitedEvent.NONE).orElse(false);
            case StateBehavior.SubWorkflowCall call -> false;
            case StateBehavior.TerminalState _ -> false;
        };
    }

    /**
     * Decides whether the edge {@code fromId -> toId} out of a resting link state waits for an external event.
     * A traversed link awaits when its form takes a submission (a {@code submit} link waits for the operator)
     * or its primitive declares an awaited event; a {@code noop} link over a primitive with no awaited event
     * does not satisfy the cycle.
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

    private static void checkExitPaths(WorkflowDefinition definition, UnitGraph graph, List<Violation> violations) {
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
            if (isTerminal(state) || UnitGraph.hasImplicitExit(definition.scope(), state)) {
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
