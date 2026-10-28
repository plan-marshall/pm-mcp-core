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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.workflow.ast.UnitType;
import de.planmarshall.workflow.ast.WorkflowDefinition;

/**
 * The static verifier (PM-IMPL-5, PM-TEST-3): every refusing fixture states its expected violations in leading
 * comments and is refused with exactly those; the fixtures are counted against an enumerated checklist of atomic
 * checks, one fixture per check; the unit that uses every construct passes every check; inline units are the matched
 * controls of the exemptions and cover the variants of each check.
 */
@DisplayName("WorkflowValidator")
class WorkflowValidatorTest {

    private static final List<String> RELEASED = List.of("verify.entry");

    private static final String DONE_TERMINAL = """

            state verify.done terminal done success
            """;

    /**
     * One atomic check of the verifier: a statement of the specification's static verification rules (Workflow DSL §
     * Static AST Verification Rules) as {@code static-verifier-remaining-rules.adoc} and
     * {@code static-verifier-rule-gaps.adoc} § What list them, the rule that reports it, and the one refusing fixture
     * that pins it.
     *
     * @param check   what is checked
     * @param rule    the rule that refuses it
     * @param fixture the file name of its refusing fixture below {@code verify/refused}
     */
    private record Check(String check, VerificationRule rule, String fixture) {
    }

    /**
     * An atomic check no refusing fixture pins, with the reason.
     *
     * @param check  what is checked, or what the specification asks and the verifier does not check
     * @param rule   the rule it belongs to
     * @param reason why no fixture pins it, and where it is pinned or named instead
     */
    private record Unpinned(String check, VerificationRule rule, String reason) {
    }

    private static final List<Check> CHECKLIST = List.of(
            new Check("Rule 1: every primitive a state, link or predicate guard names is registered",
                    VerificationRule.PRIMITIVE_BINDING, "primitive-binding.pmwf"),
            new Check("Rule 1: the free parameters of a link equal the @FreeParam components of its primitive",
                    VerificationRule.PARAMETER_EQUIVALENCE, "parameter-equivalence.pmwf"),
            new Check("Rule 1: the entity kind of a job llm state is registered",
                    VerificationRule.ENTITY_KIND_BINDING, "entity-kind-unregistered.pmwf"),
            new Check("Rule 1: the entity unit of an entity kind is registered in the catalog",
                    VerificationRule.ENTITY_KIND_BINDING, "entity-unit-unresolved.pmwf"),
            new Check("Rule 1: the return-evaluation primitive of an entity kind is registered",
                    VerificationRule.ENTITY_KIND_BINDING, "entity-kind-evaluation-unregistered.pmwf"),
            new Check("Rule 1: the primitive of an each clause is registered",
                    VerificationRule.ENTITY_KIND_BINDING, "each-primitive-unregistered.pmwf"),
            new Check("Rule 1: role each is admitted only in adjudicate.decisions",
                    VerificationRule.ENTITY_KIND_BINDING, "role-each-outside-adjudicate.pmwf"),
            new Check("Rule 1: the primitive of a role each clause is registered",
                    VerificationRule.ENTITY_KIND_BINDING, "role-each-primitive-unregistered.pmwf"),
            new Check("Rule 1: ask is refused on a role each state",
                    VerificationRule.ENTITY_KIND_BINDING, "role-each-ask.pmwf"),
            new Check("Rule 1: ask is refused on a state whose entity kind has no entity unit",
                    VerificationRule.ENTITY_KIND_BINDING, "ask-without-entity-unit.pmwf"),
            new Check("Rule 1: every role name of a job llm state and of a consult role clause matches the grammar",
                    VerificationRule.ROLE_NAME_GRAMMAR, "role-name-grammar.pmwf"),
            new Check("Rule 1: every schema[...] names a registered schema",
                    VerificationRule.SCHEMA_BINDING, "schema-unregistered.pmwf"),
            new Check("Rule 1: only the job-starting outcomes of an enqueuing primitive target a job state",
                    VerificationRule.JOB_STARTING_OUTCOME, "job-starting-outcome.pmwf"),
            new Check("Rule 2: an auto, job or link action routes every outcome of its primitive",
                    VerificationRule.OUTCOME_COVERAGE, "outcome-missing.pmwf"),
            new Check("Rule 2: an auto, job or link action routes no outcome its primitive has not",
                    VerificationRule.OUTCOME_COVERAGE, "outcome-unknown.pmwf"),
            new Check("Rule 2: an auto, job or link action routes each outcome once",
                    VerificationRule.OUTCOME_COVERAGE, "outcome-twice.pmwf"),
            new Check("Rule 2: a job llm state routes every outcome of its return-evaluation primitive",
                    VerificationRule.MODEL_JOB_OUTCOMES, "model-job-outcome-missing.pmwf"),
            new Check("Rule 2: a job llm state routes no outcome its return-evaluation primitive has not",
                    VerificationRule.MODEL_JOB_OUTCOMES, "model-job-outcome-unknown.pmwf"),
            new Check("Rule 2: a job llm state routes each outcome once",
                    VerificationRule.MODEL_JOB_OUTCOMES, "model-job-outcome-twice.pmwf"),
            new Check("Rule 2: lost stays undeclared only in a plan unit or a sub-workflow every caller chain of which"
                    + " ends in plan units", VerificationRule.MODEL_JOB_OUTCOMES, "model-job-lost-undeclared.pmwf"),
            new Check("Rule 2: a sub-workflow with a non-plan caller routes lost",
                    VerificationRule.MODEL_JOB_OUTCOMES, "model-job-lost-mixed-callers.pmwf"),
            new Check("Rule 2: asked is engine-made and refused when declared",
                    VerificationRule.MODEL_JOB_OUTCOMES, "model-job-asked-declared.pmwf"),
            new Check("Rule 2: a question clause routes every resolution outcome of its class",
                    VerificationRule.QUESTION_OUTCOMES, "question-outcome-missing.pmwf"),
            new Check("Rule 2: a question clause routes no outcome its class has not",
                    VerificationRule.QUESTION_OUTCOMES, "question-outcome-unknown.pmwf"),
            new Check("Rule 2: a question clause routes each outcome once",
                    VerificationRule.QUESTION_OUTCOMES, "question-outcome-twice.pmwf"),
            new Check("Rule 3: the engine-made <state>.asked is unique among the state ids",
                    VerificationRule.STATE_ID_UNIQUE, "state-id-duplicate.pmwf"),
            new Check("Rule 3: the start state exists",
                    VerificationRule.STATE_EXISTS, "state-start-missing.pmwf"),
            new Check("Rule 3: every transition target exists",
                    VerificationRule.STATE_EXISTS, "state-not-found.pmwf"),
            new Check("Rule 3: every state is reachable from the start state",
                    VerificationRule.REACHABILITY, "unreachable-state.pmwf"),
            new Check("Rule 3: plan.aborted is never targeted",
                    VerificationRule.ABORT_TERMINAL_TARGETING, "abort-terminal-targeted.pmwf"),
            new Check("Rule 3: epic.aborted is targeted only by abort_landed of epic.next-work",
                    VerificationRule.ABORT_TERMINAL_TARGETING, "abort-epic-wrong-source.pmwf"),
            new Check("Rule 3: epic.aborted is targeted by at most one transition",
                    VerificationRule.ABORT_TERMINAL_TARGETING, "abort-epic-twice.pmwf"),
            new Check("Rule 3: no transition targets a condition kind or a reserved state id",
                    VerificationRule.TARGET_NOT_RESERVED, "reserved-target.pmwf"),
            new Check("Rule 3: every state has a path to a terminal state",
                    VerificationRule.EXIT_PATH, "no-exit-path.pmwf"),
            new Check("Rule 3: every state of a workspace unit has a path back to its start state",
                    VerificationRule.EXIT_PATH, "exit-path-workspace.pmwf"),
            new Check("Rule 3: the link identifiers of a state are unique",
                    VerificationRule.LINK_ID_UNIQUE, "duplicate-link-id.pmwf"),
            new Check("Rule 3: a declared link repeats no implicit link identifier (ack, consult:, answer:, step_out)",
                    VerificationRule.LINK_ID_UNIQUE, "link-id-implicit.pmwf"),
            new Check("Interface rule 1: a unit whose frontmatter declares entries declares no start state",
                    VerificationRule.INTERFACE_START_OR_ENTRIES, "interface-start-with-entries.pmwf"),
            new Check("Interface rule 1: every declared entry is bound to a state",
                    VerificationRule.INTERFACE_START_OR_ENTRIES, "interface-entry-unbound.pmwf"),
            new Check("Interface rule 1: no binding names an entry the frontmatter does not declare",
                    VerificationRule.INTERFACE_START_OR_ENTRIES, "interface-entry-undeclared.pmwf"),
            new Check("Interface rule 1: every entry binding targets an existing state",
                    VerificationRule.STATE_EXISTS, "interface-entry-state-missing.pmwf"),
            new Check("Interface rule 2: a unit without exits declares a terminal state",
                    VerificationRule.INTERFACE_STOP_POINTS_OR_EXITS, "interface-no-terminal.pmwf"),
            new Check("Interface rule 2: every terminal of a unit with exits reports one of its exits",
                    VerificationRule.INTERFACE_STOP_POINTS_OR_EXITS, "interface-terminal-not-exit.pmwf"),
            new Check("Interface rule 2: every exit is the outcome of a terminal state",
                    VerificationRule.INTERFACE_STOP_POINTS_OR_EXITS, "interface-exit-without-terminal.pmwf"),
            new Check("Interface rule 2: a landing variant declares no terminal state",
                    VerificationRule.INTERFACE_STOP_POINTS_OR_EXITS, "interface-landing-variant-terminal.pmwf"),
            new Check("Interface rule 3: a unit with entries declares an exit",
                    VerificationRule.INTERFACE_CALLED_UNIT, "interface-entries-without-exit.pmwf"),
            new Check("Interface rule 3: only a unit with entries is the target of a call",
                    VerificationRule.INTERFACE_CALLED_UNIT, "interface-call-target-without-entries.pmwf"),
            new Check("Interface rule 4: every state is reachable from at least one entry",
                    VerificationRule.REACHABILITY, "interface-state-unreachable-from-entries.pmwf"),
            new Check("Interface rule 4: every exit is reached from at least one entry",
                    VerificationRule.INTERFACE_EXIT_REACHED, "interface-exit-unreached-from-entry.pmwf"),
            new Check("Interface rule 5: a call names an entry its callee declares",
                    VerificationRule.INTERFACE_CALL_ENTRY, "interface-call-entry-unknown.pmwf"),
            new Check("Interface rule 5: a call omits the entry only for a callee with exactly one",
                    VerificationRule.INTERFACE_CALL_ENTRY, "interface-call-entry-omitted.pmwf"),
            new Check("Interface rule 5: a call routes every exit its callee declares",
                    VerificationRule.SUBWORKFLOW_CALL_CONTRACT, "interface-call-exit-unrouted.pmwf"),
            new Check("Interface rule 6: a workflow unit has the scope plan or epic",
                    VerificationRule.TYPE_FITS, "type-workflow-wrong-scope.pmwf"),
            new Check("Interface rule 6: a workflow unit declares neither entries nor exits",
                    VerificationRule.TYPE_FITS, "type-workflow-with-entries.pmwf"),
            new Check("Interface rule 6: a recipe unit has the scope plan",
                    VerificationRule.TYPE_FITS, "type-recipe-wrong-scope.pmwf"),
            new Check("Interface rule 6: a subworkflow unit has the scope subworkflow",
                    VerificationRule.TYPE_FITS, "type-subworkflow-wrong-scope.pmwf"),
            new Check("Interface rule 6: a started system unit has the scope workspace or entity",
                    VerificationRule.TYPE_FITS, "type-system-wrong-scope.pmwf"),
            new Check("Interface rule 6: a task unit declares no entry",
                    VerificationRule.TYPE_FITS, "type-task-with-entries.pmwf"),
            new Check("Interface rule 6: an instance unit has the scope plan",
                    VerificationRule.TYPE_FITS, "type-instance-wrong-scope.pmwf"),
            new Check("Rule 4: a worker unit holds no job llm state and no link into one",
                    VerificationRule.LEAF_TASK_CONSTRAINT, "leaf-task-constraint.pmwf"),
            new Check("Rule 4: a consultation task offers no consult link",
                    VerificationRule.LEAF_TASK_CONSTRAINT, "leaf-consult-link.pmwf"),
            new Check("Rule 4: a consultation task consults no further role",
                    VerificationRule.LEAF_TASK_CONSTRAINT, "leaf-consult-roles.pmwf"),
            new Check("Rule 5: every released state name is a state or migrated directly to one",
                    VerificationRule.RELEASED_STATE, "released-state-dropped.pmwf"),
            new Check("Rule 6: the called unit is registered",
                    VerificationRule.SUBWORKFLOW_CALL_CONTRACT, "subworkflow-contract.pmwf"),
            new Check("Rule 6: the called unit is a sub-workflow",
                    VerificationRule.SUBWORKFLOW_CALL_CONTRACT, "subworkflow-callee-scope.pmwf"),
            new Check("Rule 6: a call routes every exit outcome, the implicit stepped_out included",
                    VerificationRule.SUBWORKFLOW_CALL_CONTRACT, "subworkflow-stepped-out-unrouted.pmwf"),
            new Check("Rule 6: a call routes no outcome outside the exit contract",
                    VerificationRule.SUBWORKFLOW_CALL_CONTRACT, "subworkflow-exit-unknown.pmwf"),
            new Check("Rule 6: a call routes each exit outcome once, a guarded done pair aside",
                    VerificationRule.SUBWORKFLOW_CALL_CONTRACT, "subworkflow-exit-twice.pmwf"),
            new Check("Rule 6: a result guard sits only on the outcome done",
                    VerificationRule.SUBWORKFLOW_CALL_CONTRACT, "result-guard-not-done.pmwf"),
            new Check("Rule 6: a result guard sits only on a call's done, on no other transition",
                    VerificationRule.SUBWORKFLOW_CALL_CONTRACT, "result-guard-outside-call.pmwf"),
            new Check("Rule 6: a result guard requires a callee that declares a result schema",
                    VerificationRule.SUBWORKFLOW_CALL_CONTRACT, "result-guard-callee-without-schema.pmwf"),
            new Check("Rule 6: a guarded done is admitted only as the complementary pair",
                    VerificationRule.SUBWORKFLOW_CALL_CONTRACT, "result-guard-single-done.pmwf"),
            new Check("Rule 6: the parameter bindings of a call match the callee's parameters",
                    VerificationRule.SUBWORKFLOW_CALL_CONTRACT, "subworkflow-param-binding.pmwf"),
            new Check("Rule 6: a resting link state of a sub-workflow declares a skill",
                    VerificationRule.SUBWORKFLOW_CALL_CONTRACT, "subworkflow-link-without-skill.pmwf"),
            new Check("Rule 6: stepped_out never targets a success terminal and fits the scope's target rule",
                    VerificationRule.SUBWORKFLOW_CALL_CONTRACT, "stepped-out-target.pmwf"),
            new Check("Rule 6: inside a remediation unit stepped_out targets the budget re-check",
                    VerificationRule.SUBWORKFLOW_CALL_CONTRACT, "stepped-out-remediation-budget-check.pmwf"),
            new Check("Rule 6: stepped_out is declared only by a terminal of class stepped_out",
                    VerificationRule.SUBWORKFLOW_CALL_CONTRACT, "stepped-out-terminal-class.pmwf"),
            new Check("Rule 7: the call graph is acyclic",
                    VerificationRule.CALL_GRAPH_DAG, "call-graph-cycle.pmwf"),
            new Check("Rule 7: the call graph is at most five frames deep",
                    VerificationRule.CALL_GRAPH_DAG, "call-graph-depth.pmwf"),
            new Check("Rule 8: every path to a state requiring an artifact passes a producer",
                    VerificationRule.GATE_PRODUCER_COVERAGE, "gate-producer-missing.pmwf"),
            new Check("Rule 9: a transition completes a step at most once",
                    VerificationRule.STEP_COMPLETION, "step-completion-same-transition.pmwf"),
            new Check("Rule 9: a firing completes a step at most once on every path",
                    VerificationRule.STEP_COMPLETION, "step-completion-firing.pmwf"),
            new Check("Rule 9: a firing of a step ends at the next opening of the same step, not at another step's"
                    + " opening", VerificationRule.STEP_COMPLETION, "step-completion-firing-per-step.pmwf"),
            new Check("Rule 9: a step completed on a loop-back into its own opening stays begun there",
                    VerificationRule.STEP_COMPLETION, "step-completion-loop-back-opening.pmwf"),
            new Check("Rule 9: a firing completes each of its steps on every path",
                    VerificationRule.STEP_COMPLETION, "step-completion-no-completion.pmwf"),
            new Check("Rule 9: entering an implicit-failed state completes the open step, no transition into it does",
                    VerificationRule.STEP_COMPLETION, "step-completion-implicit-failed.pmwf"),
            new Check("Rule 9: an outcome whose class cannot be established admits no completion",
                    VerificationRule.STEP_COMPLETION, "step-completion-question-class.pmwf"),
            new Check("Rule 9: done is admitted only on an outcome of class SUCCESS",
                    VerificationRule.STEP_COMPLETION, "step-completion-outcome-class.pmwf"),
            new Check("Rule 9: skipped is admitted on SUCCESS and SKIP, and on lost and infeasible by name",
                    VerificationRule.STEP_COMPLETION, "step-completion-skipped-route.pmwf"),
            new Check("Rule 9: failed is refused on an outcome of class UNSETTLED or STEPPED_OUT",
                    VerificationRule.STEP_COMPLETION, "step-completion-failed-class.pmwf"),
            new Check("Rule 9: waived is admitted only on the waived outcome of a call to a remediation unit",
                    VerificationRule.STEP_COMPLETION, "step-completion-waived-guard.pmwf"),
            new Check("Rule 9: STEP_LIST sits on the hub of a unit with a step list",
                    VerificationRule.STEP_COMPLETION, "cycle-measure-step-list.pmwf"),
            new Check("Rule 9: every pass of the STEP_LIST hub settles one step",
                    VerificationRule.STEP_COMPLETION, "cycle-measure-step-list-settle.pmwf"),
            new Check("Rule 9: a pass of the STEP_LIST hub that completes one step twice is refused, whatever its"
                    + " openings", VerificationRule.STEP_COMPLETION, "cycle-measure-step-list-twice.pmwf"),
            new Check("Rule 9: ARCHIVE_STEPS sits in an epic unit",
                    VerificationRule.STEP_COMPLETION, "cycle-measure-archive-steps.pmwf"),
            new Check("Rule 9: ARCHIVE_STEPS sits on an archive path that ends in a terminal state",
                    VerificationRule.STEP_COMPLETION, "cycle-measure-archive-path.pmwf"),
            new Check("Rule 9: ARCHIVE_STEPS sits on an archive path that commits and lands",
                    VerificationRule.STEP_COMPLETION, "cycle-measure-archive-commit-path.pmwf"),
            new Check("Rule 9: the exhaustion transition of an ATTEMPT_CAP leaves every cycle through its state",
                    VerificationRule.STEP_COMPLETION, "cycle-measure-attempt-cap.pmwf"),
            new Check("Rule 10: a worker unit declares no resting link state",
                    VerificationRule.OPERATOR_LINKS_APART, "operator-link-in-worker.pmwf"),
            new Check("Rule 11a: an engine-chained cycle carries a cycle measure",
                    VerificationRule.CYCLE_AWAITED_EVENT, "cycle-measure-missing.pmwf"),
            new Check("Rule 12: the engine-owned relations consult and abort are never declared",
                    VerificationRule.LINK_COMPATIBILITY, "link-engine-owned.pmwf"),
            new Check("Rule 12: the form of a link is one its relation admits",
                    VerificationRule.LINK_COMPATIBILITY, "link-form-not-admitted.pmwf"),
            new Check("Rule 12: a submit link declares parameters",
                    VerificationRule.LINK_COMPATIBILITY, "link-submit-without-params.pmwf"),
            new Check("Rule 12: a noop link declares none",
                    VerificationRule.LINK_COMPATIBILITY, "link-noop-with-params.pmwf"),
            new Check("Rule 12: an escalate link is request_waiver",
                    VerificationRule.LINK_COMPATIBILITY, "link-escalate-id.pmwf"));

    /** The checks a fixture cannot plant: the language or the primitive registry refuses the input first. */
    private static final List<Unpinned> UNPLANTABLE = List.of(
            new Unpinned("Rule 1: every question clause names a registered question class",
                    VerificationRule.QUESTION_CLASS_BINDING,
                    "the grammar closes the question-class vocabulary and the default registry registers every class;"
                            + " pinned by AdditionalRulesTest#questionClassUnregistered with a narrower registry"),
            new Unpinned("Rule 1: a primitive declaring enqueuesJob() declares a job-starting outcome",
                    VerificationRule.JOB_STARTING_OUTCOME,
                    "PrimitiveRegistry refuses such a primitive at registration"),
            new Unpinned("Rule 9: an ATTEMPT_CAP primitive names an exhaustion outcome of class FAILURE",
                    VerificationRule.STEP_COMPLETION,
                    "PrimitiveRegistry refuses such a primitive at registration"));

    /** What the specification asks and the verifier does not check: the seams, each named by its ADR. */
    private static final List<Unpinned> SEAMS = List.of(
            new Unpinned("Rule 3: configuration reachability over the depends_on_config values (no activation rule;"
                    + " removed)", VerificationRule.REACHABILITY,
                    "ADR 006: the specification defines no rule by which a depends_on_config value activates a"
                            + " guard, so the check and its advisories are removed (ADR 004)"),
            new Unpinned("Rule 1: each runs over a read-only list primitive, role each over one returning role names",
                    VerificationRule.ENTITY_KIND_BINDING,
                    "ADR 010: the primitive SPI cannot express either; only the registration is checked"),
            new Unpinned("Rule 4: the sub-workflows a worker unit calls hold no job llm state and no link into one",
                    VerificationRule.LEAF_TASK_CONSTRAINT,
                    "ADR 001: the callees of a worker unit are not walked; only its own states are checked"),
            new Unpinned("Rule 4: a consultation unit is recognised as one",
                    VerificationRule.LEAF_TASK_CONSTRAINT,
                    "ADR 001: the language marks no consultation unit; the identifier is a heuristic"),
            new Unpinned("Rule 4: a consult role clause names no zero_tool role",
                    VerificationRule.LEAF_TASK_CONSTRAINT,
                    "ADR 016: checked with the role's existence by Rule 13, which is not part of the verifier"),
            new Unpinned("Rule 6: the guarded field is a boolean or optional field of the callee's result schema",
                    VerificationRule.SUBWORKFLOW_CALL_CONTRACT,
                    "ADR 010: SchemaRegistry registers schema names only"),
            new Unpinned("Rule 6: in a plan unit stepped_out targets the fallback state of the calling phase",
                    VerificationRule.SUBWORKFLOW_CALL_CONTRACT,
                    "ADR 001: plan.return and finalize.hand-back are both admitted, whatever the calling phase"),
            new Unpinned("Rule 9: skipped on lost or infeasible only on the declared conservative route of an optional"
                    + " step", VerificationRule.STEP_COMPLETION,
                    "ADR 002: the AST declares no optional step and no conservative route"),
            new Unpinned("Rule 11: a cycle through a call counts the measure or awaited event of a state in the callee",
                    VerificationRule.CYCLE_AWAITED_EVENT,
                    "ADR 001: the callee is not entered; a call counts as engine-chained and awaiting no event"),
            new Unpinned("Rule 12: an escalate link is one whose primitive files a waiver request and never waits",
                    VerificationRule.LINK_COMPATIBILITY,
                    "ADR 001: the primitive SPI declares no waiver request; the link is told by its identifier"
                            + " request_waiver"));

    private final WorkflowValidator validator = new WorkflowValidator(TestPrimitives.registry(), RELEASED,
            TestPrimitives.entityKinds(), QuestionClassRegistry.defaults(), SchemaRegistry.defaults(),
            TestPrimitives.catalog());

    static List<String> refusedFixtures() {
        return VerifyFixtures.list(VerifyFixtures.REFUSED);
    }

    static List<String> passingFixtures() {
        return VerifyFixtures.list(VerifyFixtures.PASSING);
    }

    private static WorkflowDefinition unit(String scope, String states) {
        return unit("verify.inline", scope, states);
    }

    private static WorkflowDefinition unit(String id, String scope, String states) {
        return VerifyFixtures.parseSource("inline.pmwf", VerifyFixtures.inlineUnit(id, scope, states));
    }

    private List<Violation> violations(WorkflowDefinition definition) {
        return validator.verify(definition).violations();
    }

    private static String fileName(String resource) {
        return resource.substring(resource.lastIndexOf('/') + 1);
    }

    @Nested
    @DisplayName("Fixtures")
    class Fixtures {

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.verify.WorkflowValidatorTest#refusedFixtures")
        @DisplayName("Should refuse each fixture with exactly the violations it expects")
        void shouldRefuseWithExactlyTheExpectedViolations(String resource) {
            var found = violations(VerifyFixtures.parse(resource));

            VerifyFixtures.assertRefusedExactly(resource, found);
        }

        @Test
        @DisplayName("Should hold exactly one refusing fixture for every atomic check of the checklist")
        void shouldCoverEveryCheck() {
            var fixtures = refusedFixtures().stream().map(WorkflowValidatorTest::fileName)
                    .collect(Collectors.toCollection(TreeSet::new));
            var named = CHECKLIST.stream().map(Check::fixture).toList();
            var checks = Stream.of(CHECKLIST.stream().map(Check::check), UNPLANTABLE.stream().map(Unpinned::check),
                    SEAMS.stream().map(Unpinned::check)).flatMap(stream -> stream).toList();

            assertAll(() -> assertEquals(named.size(), new HashSet<>(named).size(), "a fixture pins two checks"),
                    () -> assertEquals(checks.size(), new HashSet<>(checks).size(), "a check is listed twice"),
                    () -> assertEquals(new TreeSet<>(named), fixtures,
                            "the fixtures and the checklist name different files"));
            for (var check : CHECKLIST) {
                var rules = VerifyFixtures.expectations(VerifyFixtures.REFUSED + "/" + check.fixture()).stream()
                        .map(VerifyFixtures.Expectation::rule).toList();
                assertTrue(rules.contains(check.rule()), () -> check + " is refused with " + rules);
            }
        }

        @Test
        @DisplayName("Should name every rule in the checklist, as unplantable, or as a seam")
        void shouldAccountForEveryRule() {
            var accounted = Stream.of(CHECKLIST.stream().map(Check::rule), UNPLANTABLE.stream().map(Unpinned::rule),
                    SEAMS.stream().map(Unpinned::rule)).flatMap(stream -> stream).collect(Collectors.toSet());

            assertAll(() -> assertEquals(32, VerificationRule.values().length),
                    () -> assertEquals(Set.of(VerificationRule.values()), accounted));
        }

        @Test
        @DisplayName("Should pass the unit that uses every construct")
        void shouldPassEveryConstruct() {
            var result = validator.verify(VerifyFixtures.parse(VerifyFixtures.PASSING + "/every-construct.pmwf"));

            assertAll(() -> assertTrue(result.passed(), result::toString),
                    () -> assertEquals(List.of(), result.violations(), result::toString));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.verify.WorkflowValidatorTest#passingFixtures")
        @DisplayName("Should pass every passing control: the called unit with two entries and two exits, and one unit"
                + " per row of the Unit Types table")
        void shouldPassEveryControl(String resource) {
            var result = validator.verify(VerifyFixtures.parse(resource));

            assertEquals(List.of(), result.violations(), resource);
        }

        @Test
        @DisplayName("Should hold a passing control for the interface rules and for every row of the Unit Types table")
        void shouldHoldEveryPassingControl() {
            var controls = passingFixtures().stream().map(WorkflowValidatorTest::fileName)
                    .collect(Collectors.toCollection(TreeSet::new));

            var expected = new TreeSet<>(List.of("every-construct.pmwf", "interface-entries-and-exits.pmwf"));
            Arrays.stream(UnitType.values()).map(type -> "type-" + type.wireName() + ".pmwf").forEach(expected::add);

            assertEquals(expected, controls);
        }
    }

    @Nested
    @DisplayName("Exemptions")
    class Exemptions {

        static Stream<Arguments> controls() {
            return Stream.of(
                    Arguments.of("epic.aborted targeted by abort_landed of epic.next-work, matched with "
                            + "abort-epic-wrong-source.pmwf", "verify.inline", "plan", """
                            state verify.entry
                                auto test.prepare
                                    done -> epic.next-work
                                    skipped -> verify.done

                            state epic.next-work
                                auto test.land-work
                                    landed -> verify.done
                                    abort_landed -> epic.aborted

                            state epic.aborted terminal aborted failure
                            """ + DONE_TERMINAL),
                    Arguments.of("a call graph exactly four calls deep, matched with call-graph-depth.pmwf",
                            "verify.inline", "plan", """
                            state verify.entry
                                call test.depth2()
                                    done -> verify.done
                                    stepped_out -> plan.return

                            state plan.return terminal returned failure
                            """ + DONE_TERMINAL),
                    Arguments.of("a producer on every path to the consumer, matched with gate-producer-missing.pmwf",
                            "verify.inline", "plan", """
                            state verify.entry
                                auto test.produce
                                    done -> verify.consume

                            state verify.consume
                                auto test.consume
                                    done -> verify.done
                            """ + DONE_TERMINAL),
                    Arguments.of("the escalate link request_waiver, matched with link-escalate-id.pmwf",
                            "verify.inline", "workspace", """
                            state verify.entry
                                link escalate request_waiver (note: text[1..80], comment: text)
                                    do test.choose
                                        chosen -> verify.done
                                        declined -> verify.done
                            """ + DONE_TERMINAL),
                    Arguments.of("a job-starting outcome into a job state, matched with job-starting-outcome.pmwf",
                            "verify.inline", "plan", """
                            state verify.entry
                                auto test.enqueue
                                    enqueued -> verify.build
                                    skipped -> verify.done

                            state verify.build
                                job test.build
                                    succeeded -> verify.done
                                    failed -> verify.done
                                    cancelled -> verify.done
                            """ + DONE_TERMINAL),
                    Arguments.of("role each in adjudicate.decisions, matched with role-each-outside-adjudicate.pmwf",
                            "adjudicate.decisions", "plan", """
                            state verify.entry
                                job llm change role each test.list-roles
                                    approved -> verify.done
                                    lost -> verify.done
                            """ + DONE_TERMINAL),
                    Arguments.of("the default lost route of a plan unit, matched with model-job-lost-undeclared.pmwf",
                            "verify.inline", "plan", """
                            state verify.entry
                                job llm change role reviewer
                                    approved -> verify.done
                            """ + DONE_TERMINAL),
                    Arguments.of("the default lost route of a sub-workflow reached only from a plan unit through"
                            + " another sub-workflow, matched with model-job-lost-mixed-callers.pmwf",
                            TestPrimitives.PLAN_ONLY_INNER, "subworkflow", """
                            state verify.entry
                                job llm change role reviewer
                                    approved -> verify.done
                            """ + DONE_TERMINAL),
                    Arguments.of("a guarded complementary done pair on a call to a callee with a result schema,"
                            + " matched with result-guard-callee-without-schema.pmwf", "verify.inline", "subworkflow", """
                            state verify.entry
                                call git.commit_and_push(push = enabled, attempts = 2, targets = [origin])
                                    done -> verify.done
                                        guard result.pushed
                                    done -> verify.done
                                        guard not result.pushed
                                    stepped_out -> verify.stepped-out

                            state verify.stepped-out terminal stepped_out stepped_out
                            """ + DONE_TERMINAL),
                    Arguments.of("stepped_out of a call in a remediation unit routed to its budget re-check, matched"
                            + " with stepped-out-remediation-budget-check.pmwf", "remediation.inline", "subworkflow", """
                            state verify.entry
                                call git.commit_and_push(push = enabled, attempts = 2, targets = [origin])
                                    done -> verify.done
                                    stepped_out -> remediation-inline.budget-check

                            state remediation-inline.budget-check
                                auto remediation.check-budgets
                                    budget_left -> verify.done
                                    exhausted_wait -> verify.exhausted
                                    exhausted -> verify.exhausted

                            state verify.exhausted terminal exhausted failure
                            """ + DONE_TERMINAL),
                    Arguments.of("a firing that passes another step's opening before it completes its step, matched"
                            + " with step-completion-firing-per-step.pmwf", "verify.inline", "plan", """
                            state verify.entry
                                auto test.prepare
                                    done -> finalize.review-cap-check
                                    skipped -> verify.done
                                        completes step:a as skipped

                            state finalize.review-cap-check
                                auto test.prepare
                                    done -> verify.after
                                        completes step:b as done
                                    skipped -> verify.after
                                        completes step:b as skipped

                            state verify.after
                                auto test.proceed
                                    done -> verify.done
                                        completes step:a as done
                            """ + DONE_TERMINAL),
                    Arguments.of("a re-entry into the opening that continues the open firing, matched with"
                            + " step-completion-firing-per-step.pmwf", "verify.inline", "plan", """
                            state verify.entry
                                auto test.prepare
                                    done -> verify.work
                                    skipped -> verify.done
                                        completes step:s as skipped

                            state verify.work
                                job test.build
                                    succeeded -> verify.done
                                        completes step:s as done
                                    failed -> verify.entry
                                    cancelled -> verify.done
                                        completes step:s as failed
                            """ + DONE_TERMINAL),
                    Arguments.of("a completion on the loop-back into the opening and on every other path of its"
                            + " firing, matched with step-completion-loop-back-opening.pmwf", "verify.inline", "plan", """
                            state verify.entry
                                auto test.prepare
                                    done -> verify.work
                                    skipped -> verify.done
                                        completes step:s as skipped

                            state verify.work
                                job test.build
                                    succeeded -> verify.done
                                        completes step:s as done
                                    failed -> verify.entry
                                        completes step:s as failed
                                    cancelled -> verify.done
                                        completes step:s as failed
                            """ + DONE_TERMINAL),
                    Arguments.of("skipped on a task's infeasible and on lost, matched with"
                            + " step-completion-skipped-route.pmwf", "verify.inline", "plan", """
                            state verify.entry
                                job llm repair role implementer
                                    repaired -> verify.done
                                        completes step:s as done
                                    infeasible -> verify.done
                                        completes step:s as skipped
                                    lost -> verify.done
                                        completes step:s as skipped
                            """ + DONE_TERMINAL),
                    Arguments.of("a dispatch hub every pass of which settles one step, matched with"
                            + " cycle-measure-step-list-settle.pmwf", "verify.inline", "plan", """
                            state verify.entry
                                auto test.next-step
                                    first -> verify.one
                                    second -> verify.two
                                    settled -> verify.done

                            state verify.one
                                auto test.prepare
                                    done -> verify.entry
                                        completes step:one as done
                                    skipped -> verify.entry
                                        completes step:one as skipped

                            state verify.two
                                auto test.prepare
                                    done -> verify.entry
                                        completes step:two as done
                                    skipped -> verify.entry
                                        completes step:two as skipped
                            """ + DONE_TERMINAL),
                    Arguments.of("ARCHIVE_STEPS on an archive path that commits and lands, matched with"
                            + " cycle-measure-archive-commit-path.pmwf", "verify.inline", "epic", """
                            state verify.entry
                                auto test.next-work
                                    archive_uncommitted -> verify.commit
                                    archived -> verify.done

                            state verify.commit
                                call git.commit_and_push(push = enabled, attempts = 2, targets = [origin])
                                    done -> verify.land
                                    stepped_out -> verify.land

                            state verify.land
                                job test.build
                                    succeeded -> verify.entry
                                    failed -> verify.entry
                                    cancelled -> verify.entry
                            """ + DONE_TERMINAL),
                    Arguments.of("the engine-made asked of an ask state, matched with model-job-asked-declared.pmwf",
                            "verify.inline", "plan", """
                            state verify.entry
                                job llm change role reviewer ask
                                    approved -> verify.done
                                    lost -> verify.done
                            """ + DONE_TERMINAL));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.verify.WorkflowValidatorTest$Exemptions#controls")
        @DisplayName("Should pass the matched control of each exemption")
        void shouldPassTheMatchedControl(String exemption, String id, String scope, String states) {
            var result = validator.verify(unit(id, scope, states));

            assertEquals(List.of(), result.violations(), exemption);
        }
    }

    @Nested
    @DisplayName("Primitive binding")
    class PrimitiveBinding {

        @Test
        @DisplayName("Should report every unregistered site and check nothing else of it")
        void shouldReportEveryUnregisteredSite() {
            var definition = unit("workspace", """
                    state verify.entry
                        facts test.missing
                        link next proceed ()
                            guard in_worktree and predicate:test.missing
                            do test.missing
                                done -> verify.waiting

                    state verify.waiting
                        job test.missing
                            done -> verify.composite

                    state verify.composite
                        composite_job test.missing
                            done -> verify.done
                            failed -> verify.entry
                    """ + DONE_TERMINAL);

            var found = violations(definition);

            assertAll(() -> assertEquals(5, found.size(), found::toString),
                    () -> assertTrue(found.stream().allMatch(v -> v.rule() == VerificationRule.PRIMITIVE_BINDING
                            && v.message().contains(TestPrimitives.MISSING)), found::toString));
        }
    }

    @Nested
    @DisplayName("Parameter equivalence")
    class ParameterEquivalence {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = ';', quoteCharacter = '"', value = {
                "(note: text[1..80], comment: text?);0;",
                "(note: text[1..80], comment: text);0;",
                "(note: text[1..80]);1;omits the free parameter 'comment'",
                "(note: text[1..80], comment: text, extra: text);1;'extra', which the parameter record has not",
                "(note: int[1..80], comment: text);1;but the parameter record declares a text",
                "(note: text, comment: text);1;length 0..2147483647"
        })
        @DisplayName("Should compare names, type and bounds, but not optionality")
        void shouldCompareFreeParameters(String params, int count, String fragment) {
            var definition = unit("workspace", """
                    state verify.entry
                        link submit choose [submit] %s
                            do test.choose
                                chosen -> verify.done
                                declined -> verify.entry
                    """.formatted(params) + DONE_TERMINAL);

            var found = violations(definition);

            assertEquals(count, found.size(), found::toString);
            found.forEach(violation -> assertAll(
                    () -> assertEquals(VerificationRule.PARAMETER_EQUIVALENCE, violation.rule()),
                    () -> assertTrue(violation.message().contains(fragment), violation::message)));
        }
    }

    @Nested
    @DisplayName("Outcome coverage")
    class OutcomeCoverage {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = ';', quoteCharacter = '"', value = {
                "a job state without cancelled;job test.build|succeeded -> verify.done|failed -> verify.done;'cancelled'",
                "an auto state routing an outcome twice;"
                        + "auto test.prepare|done -> verify.done|skipped -> verify.done|done -> verify.done;twice",
                "a link routing one of two outcomes;link submit choose [submit] (note: text[1..80], comment: text)"
                        + "|do test.choose|chosen -> verify.done;'declined'"
        })
        @DisplayName("Should refuse every gap of the bijection with the outcome enum")
        void shouldRefuseGaps(String description, String body, String fragment) {
            var states = "state verify.entry\n" + body.replace('|', '\n') + "\n";

            var found = violations(unit("workspace", states + DONE_TERMINAL));

            assertAll(description, () -> assertEquals(1, found.size(), found::toString),
                    () -> assertEquals(VerificationRule.OUTCOME_COVERAGE, found.getFirst().rule()),
                    () -> assertTrue(found.getFirst().message().contains(fragment), found::toString));
        }

        /** A job state whose {@code cancelled} led into a state a composition pruned, so the transition is gone. */
        private static WorkflowDefinition withoutCancelled() {
            return unit("workspace", "state verify.entry\njob test.build\n"
                    + "succeeded -> verify.done\nfailed -> verify.done\n" + DONE_TERMINAL);
        }

        @Test
        @DisplayName("Should count an outcome a prune record settles as routed")
        void shouldAdmitOutcomeSettledByPrune() {
            var found = validator.verify(withoutCancelled(), Map.of("verify.entry", Set.of("cancelled")))
                    .violations();

            assertEquals(List.of(), found);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.verify.WorkflowValidatorTest#unsettledPrunes")
        @DisplayName("Should still refuse an unrouted outcome no prune record settles")
        void shouldRefuseOutcomeWithoutPruneRecord(String description, Map<String, Set<String>> settled) {
            var found = validator.verify(withoutCancelled(), settled).violations();

            assertAll(description, () -> assertEquals(1, found.size(), found::toString),
                    () -> assertEquals(VerificationRule.OUTCOME_COVERAGE, found.getFirst().rule()),
                    () -> assertTrue(found.getFirst().message().contains("'cancelled'"), found::toString));
        }
    }

    static Stream<Arguments> unsettledPrunes() {
        return Stream.of(
                Arguments.of("a unit verified without prune records", Map.of()),
                Arguments.of("a record of another state", Map.of("verify.done", Set.of("cancelled"))),
                Arguments.of("a record of another outcome", Map.of("verify.entry", Set.of("failed"))));
    }

    @Nested
    @DisplayName("Reachability")
    class Reachability {

        @Test
        @DisplayName("Should count the abort terminals as reachable although nothing targets them")
        void shouldExemptAbortTerminals() {
            var definition = unit("plan", """
                    state verify.entry
                        auto test.prepare
                            done -> verify.done
                            skipped -> verify.done

                    state plan.aborted terminal aborted failure

                    state epic.aborted terminal aborted failure
                    """ + DONE_TERMINAL);

            assertEquals(List.of(), violations(definition));
        }
    }

    @Nested
    @DisplayName("Exit path")
    class ExitPath {

        /*
         * The starting state branches to a terminal state, which every unit declares (interface rule 2), and to the
         * state under test, which has no declared path to it.
         */
        private static final String BRANCH = """
                state verify.entry
                    auto test.prepare
                        done -> verify.rest
                        skipped -> verify.done

                """;

        /*
         * A link state that returns to itself through a submission, so the cycle awaits an event. A plan unit admits
         * no resting link state (Rule 10), so the matched control of the sub-workflow is an entity unit, whose link
         * states have no implicit step_out either.
         */
        private static final String SELF_LINK = BRANCH + """
                state verify.rest
                    skill pm/verify/start
                    link submit choose [submit] (note: text[1..80], comment: text)
                        do test.choose
                            chosen -> verify.rest
                            declined -> verify.rest
                """ + DONE_TERMINAL;

        @ParameterizedTest(name = "{0}")
        @CsvSource({"subworkflow, 0", "entity, 1"})
        @DisplayName("Should let a link state of a sub-workflow exit through the implicit step_out")
        void shouldExitThroughStepOut(String scope, int count) {
            var found = violations(unit(scope, SELF_LINK));

            assertEquals(count == 0 ? List.of() : List.of("verify.rest EXIT_PATH"),
                    found.stream().map(violation -> violation.stateId() + " " + violation.rule()).toList());
        }

        @ParameterizedTest(name = "lost declared: {0}")
        @CsvSource({"false, 0", "true, 1"})
        @DisplayName("Should let a task state exit through the default lost route unless it declares lost")
        void shouldExitThroughDefaultLost(boolean declaresLost, int count) {
            var definition = unit("plan", BRANCH + """
                    state verify.rest
                        job llm change role reviewer
                            approved -> verify.rest
                    """ + (declaresLost ? "        lost -> verify.rest\n" : "") + DONE_TERMINAL);

            var found = violations(definition);

            assertEquals(count == 0 ? List.of() : List.of("verify.rest EXIT_PATH"),
                    found.stream().map(violation -> violation.stateId() + " " + violation.rule()).toList());
        }

        @ParameterizedTest(name = "done -> {0}")
        @CsvSource({"verify.entry, 0", "verify.done, 1"})
        @DisplayName("Should require every state of a workspace unit to return to the start state")
        void shouldReturnToStartInWorkspace(String doneTarget, int count) {
            var definition = unit("workspace", """
                    state verify.entry
                        link submit work [submit] (note: text[1..80], comment: text)
                            do test.choose
                                chosen -> verify.stuck
                                declined -> verify.done

                    state verify.stuck
                        auto test.prepare
                            done -> %s
                            skipped -> %s
                    """.formatted(doneTarget, doneTarget) + DONE_TERMINAL);

            var found = violations(definition);

            assertEquals(count == 0 ? List.of() : List.of("verify.stuck EXIT_PATH"),
                    found.stream().map(violation -> violation.stateId() + " " + violation.rule()).toList());
        }

        @Test
        @DisplayName("Should not require the terminal states of a workspace unit to return to the start state")
        void shouldExemptTerminalStatesInWorkspace() {
            var definition = unit("workspace", """
                    state verify.entry
                        link submit work [submit] (note: text[1..80], comment: text)
                            do test.choose
                                chosen -> verify.stuck
                                declined -> verify.stuck

                    state verify.stuck
                        auto test.prepare
                            done -> verify.entry
                            skipped -> verify.done

                    state plan.aborted terminal aborted failure
                    """ + DONE_TERMINAL);

            assertEquals(List.of(), violations(definition));
        }
    }

    @Nested
    @DisplayName("Link identifiers")
    class LinkIdentifiers {

        @ParameterizedTest(name = "{0}")
        @CsvSource({"subworkflow, 1", "workspace, 0"})
        @DisplayName("Should refuse a declared step_out only where the engine renders the implicit one")
        void shouldRefuseDeclaredStepOut(String scope, int count) {
            var definition = unit(scope, """
                    state verify.entry
                        skill pm/verify/start
                        link next step_out ()
                            do test.proceed
                                done -> verify.done
                    """ + DONE_TERMINAL);

            var found = violations(definition);

            assertEquals(count == 0 ? List.of() : List.of("verify.entry LINK_ID_UNIQUE"),
                    found.stream().map(violation -> violation.stateId() + " " + violation.rule()).toList());
        }
    }

    @Nested
    @DisplayName("Cycle awaited event")
    class CycleAwaitedEvent {

        @Test
        @DisplayName("Should pass a cycle whose traversed link submits to the operator")
        void shouldPassSubmitLinkWithEvent() {
            var definition = unit("subworkflow", """
                    state verify.entry
                        skill pm/verify/start
                        link submit choose [submit] (note: text[1..80], comment: text)
                            do test.choose
                                chosen -> verify.entry
                                declined -> verify.done
                    """ + DONE_TERMINAL);

            assertEquals(List.of(), violations(definition));
        }

        @Test
        @DisplayName("Should refuse an engine-chained cycle with no cycle measure")
        void shouldRefuseEngineChainedCycleWithoutMeasure() {
            var definition = unit("subworkflow", """
                    state verify.entry
                        auto test.prepare
                            done -> verify.second
                            skipped -> verify.done

                    state verify.second
                        auto test.prepare
                            done -> verify.entry
                            skipped -> verify.done
                    """ + DONE_TERMINAL);

            var found = violations(definition);

            assertEquals(1, found.size(), found::toString);
            var violation = found.getFirst();
            assertAll(() -> assertEquals(VerificationRule.CYCLE_AWAITED_EVENT, violation.rule()),
                    () -> assertEquals("verify.entry", violation.stateId()),
                    () -> assertEquals("the engine-chained cycle 'verify.entry -> verify.second -> verify.entry' has"
                            + " no cycle measure", violation.message()));
        }
    }

    @Nested
    @DisplayName("Released states")
    class ReleasedStates {

        private final WorkflowValidator retiring = new WorkflowValidator(TestPrimitives.registry(),
                List.of("verify.entry", "verify.old"), TestPrimitives.entityKinds(), QuestionClassRegistry.defaults(),
                SchemaRegistry.defaults(), TestPrimitives.catalog());

        private List<Violation> verify(String migration) {
            return retiring.verify(VerifyFixtures.parseSource("inline.pmwf", """
                    ---
                    name: verify.inline
                    description: A unit written for the tests of pm-workflow.
                    type: workflow
                    icon: workflow.default
                    modification_date: 2026-01-15
                    version: 0.1.0
                    author: plan-marshall
                    ---
                    workflow verify.inline version 1 format 1
                    scope plan
                    start verify.entry
                    %s
                    state verify.entry
                        auto test.prepare
                            done -> verify.done
                            skipped -> verify.done
                    """.formatted(migration) + DONE_TERMINAL)).violations();
        }

        @Test
        @DisplayName("Should accept a released name migrated to a state")
        void shouldAcceptMigrationToLiveState() {
            assertEquals(List.of(), verify("migrate verify.old -> verify.entry\n"));
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = ';', quoteCharacter = '"', value = {
                "migrate verify.old -> verify.gone;13;targets 'verify.gone'",
                "\"# no migration\";10;no migrate declaration names it"
        })
        @DisplayName("Should refuse a released name that is neither a state nor migrated to one, at the migration or "
                + "at the unit header below the nine lines of the frontmatter")
        void shouldRefuseDroppedName(String migration, int line, String fragment) {
            var found = verify(migration + "\n");

            assertEquals(1, found.size(), found::toString);
            var violation = found.getFirst();
            assertAll(() -> assertEquals(VerificationRule.RELEASED_STATE, violation.rule()),
                    () -> assertEquals("verify.old", violation.stateId()),
                    () -> assertEquals(line, violation.location().line()),
                    () -> assertTrue(violation.message().contains(fragment), violation::message));
        }
    }

    @Nested
    @DisplayName("Result")
    class Result {

        @Test
        @DisplayName("Should report every defect of a unit, ordered by state and then by rule")
        void shouldReportEveryDefectInOrder() {
            var definition = unit("workspace", """
                    state verify.entry
                        auto test.missing
                            done -> verify.done

                    state verify.orphan
                        link next proceed ()
                            do test.proceed
                                done -> verify.orphan
                        link alt proceed ()
                            do test.proceed
                                done -> verify.orphan
                    """ + DONE_TERMINAL);
            var withRetiredName = new WorkflowValidator(TestPrimitives.registry(),
                    List.of("verify.retired", "verify.entry"), TestPrimitives.entityKinds(),
                    QuestionClassRegistry.defaults(), SchemaRegistry.defaults(), TestPrimitives.catalog());

            var found = withRetiredName.verify(definition).violations();

            var stateAndRule = found.stream().map(violation -> violation.stateId() + " " + violation.rule()).toList();
            assertEquals(List.of("verify.entry PRIMITIVE_BINDING", "verify.orphan REACHABILITY",
                            "verify.orphan EXIT_PATH", "verify.orphan LINK_ID_UNIQUE",
                            "verify.retired RELEASED_STATE"),
                    stateAndRule);
        }

        @Test
        @DisplayName("Should refuse a null argument")
        void shouldRefuseNull() {
            var registry = TestPrimitives.registry();
            Map<String, Set<String>> noEntries = Map.of();
            var workspace = unit("workspace", DONE_TERMINAL);
            assertAll(() -> assertThrows(NullPointerException.class, () -> new WorkflowValidator(null, RELEASED)),
                    () -> assertThrows(NullPointerException.class, () -> new WorkflowValidator(registry, null)),
                    () -> assertThrows(NullPointerException.class, () -> validator.verify(null)),
                    () -> assertThrows(NullPointerException.class, () -> validator.verify(null, noEntries)),
                    () -> assertThrows(NullPointerException.class, () -> validator.verify(workspace, null)));
        }
    }
}
