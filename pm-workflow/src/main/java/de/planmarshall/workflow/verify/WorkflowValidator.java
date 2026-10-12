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

import de.planmarshall.core.spi.FreeParam;
import de.planmarshall.core.spi.Primitive;
import de.planmarshall.core.spi.PrimitiveOutcome;
import de.planmarshall.core.spi.PrimitiveRegistry;
import de.planmarshall.workflow.ast.LinkAction;
import de.planmarshall.workflow.ast.LinkSpec;
import de.planmarshall.workflow.ast.OutcomeTransition;
import de.planmarshall.workflow.ast.ParamSpec;
import de.planmarshall.workflow.ast.ParamType;
import de.planmarshall.workflow.ast.SourceLocation;
import de.planmarshall.workflow.ast.StateBehavior;
import de.planmarshall.workflow.ast.StateMigration;
import de.planmarshall.workflow.ast.StateNode;
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
 * Reachability is graph reachability over every transition: a guard's configuration keys are known to the tree, but
 * not the values they can take, so configuration combinations are not enumerated here.
 *
 * @since 0.1
 */
public final class WorkflowValidator {

    /**
     * The abort terminals. The engine enters them by the operator's abort, a transition no unit declares, so they
     * count as reachable from every state and are exempt from {@link VerificationRule#REACHABILITY}.
     */
    private static final Set<String> ABORT_TERMINALS = Set.of("plan.aborted", "epic.aborted");

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

    /**
     * @param registry       the primitives a unit may name
     * @param releasedStates the state names of earlier releases, in the order they are reported
     * @throws NullPointerException if an argument or a released state name is {@code null}
     */
    public WorkflowValidator(PrimitiveRegistry registry, Collection<String> releasedStates) {
        this(registry, releasedStates, EntityKindRegistry.defaults(), QuestionClassRegistry.defaults(),
                SchemaRegistry.defaults());
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
        this.registry = Objects.requireNonNull(registry, "registry");
        this.releasedStates = List.copyOf(new LinkedHashSet<>(List.copyOf(releasedStates)));
        this.entityKinds = Objects.requireNonNull(entityKinds, "entityKinds");
        this.questionClasses = Objects.requireNonNull(questionClasses, "questionClasses");
        this.schemas = Objects.requireNonNull(schemas, "schemas");
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
        for (var state : definition.states().values()) {
            checkPrimitiveBinding(definition, state, violations);
            checkParameterEquivalence(state, violations);
            checkOutcomeCoverage(definition, state, violations);
            checkLinkIdentifiers(definition.scope(), state, violations);
            checkJobStartingOutcomes(definition, state, violations);
        }
        checkReachability(definition, graph, violations);
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
        if (UnitGraph.hasImplicitExit(scope, state) && state.behavior() instanceof StateBehavior.InteractiveLinks) {
            identifiers.add(STEP_OUT_LINK);
        }
        for (var link : UnitGraph.declaredLinks(state.behavior())) {
            if (!identifiers.add(link.idPattern())) {
                var implicit = STEP_OUT_LINK.equals(link.idPattern()) ? " the implicit" : " another";
                violations.add(new Violation(VerificationRule.LINK_ID_UNIQUE, state.id(), link.location(),
                        "the link '%s' of the state '%s' repeats the identifier of%s link".formatted(link.idPattern(),
                                state.id(), implicit)));
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
