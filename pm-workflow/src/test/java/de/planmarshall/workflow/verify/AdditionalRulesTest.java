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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.workflow.ast.WorkflowDefinition;

@DisplayName("Additional Rules Coverage")
class AdditionalRulesTest {

    private final WorkflowValidator validator = new WorkflowValidator(
            TestPrimitives.registry(),
            List.of("verify.entry"),
            TestPrimitives.entityKinds(),
            QuestionClassRegistry.defaults(),
            SchemaRegistry.defaults(),
            TestPrimitives.catalog());

    private static final String DONE_TERMINAL = """

            state verify.done terminal done success
                phase verify
            """;

    private static WorkflowDefinition unit(String scope, String states) {
        return VerifyFixtures.parseSource("inline.pmwf", """
                workflow verify.inline version 1 format 1
                scope %s
                start verify.entry

                %s""".formatted(scope, states));
    }

    private List<Violation> violations(WorkflowDefinition def) {
        return validator.verify(def).violations();
    }

    @Test
    @DisplayName("STATE_EXISTS: start state missing in definition")
    void stateExistsMissingStartState() {
        var def = VerifyFixtures.parseSource("missing-start.pmwf", """
                workflow verify.inline version 1 format 1
                scope plan
                start verify.missing

                state verify.entry terminal done success
                    phase verify
                """);
        var found = violations(def);
        assertTrue(found.stream().anyMatch(v -> v.rule() == VerificationRule.STATE_EXISTS
                && v.message().contains("the start state 'verify.missing' does not exist")));
    }

    @Test
    @DisplayName("SCHEMA_BINDING: parameter with unregistered schema")
    void schemaBindingUnregistered() {
        var def = unit("plan", """
                state verify.entry
                    phase verify
                    skill pm/verify/start
                    link submit choose [submit] (note: schema[unregistered-schema])
                        do test.choose
                            chosen -> verify.done
                            declined -> verify.done
                """ + DONE_TERMINAL);
        var found = violations(def);
        assertTrue(found.stream().anyMatch(v -> v.rule() == VerificationRule.SCHEMA_BINDING
                && v.message().contains("unregistered-schema")));
    }

    @Test
    @DisplayName("ENTITY_KIND_BINDING: return evaluation primitive missing")
    void entityKindMissingReturnEvalPrimitive() {
        var customKinds = EntityKindRegistry.defaults().with(new EntityKind("custom", Optional.empty(), "missing.eval"));
        var customVal = new WorkflowValidator(TestPrimitives.registry(), List.of("verify.entry"),
                customKinds, QuestionClassRegistry.defaults(), SchemaRegistry.defaults(), TestPrimitives.catalog());

        var def = unit("plan", """
                state verify.entry
                    phase verify
                    job llm custom role reviewer
                        approved -> verify.done
                        lost -> verify.done
                """ + DONE_TERMINAL);
        var found = customVal.verify(def).violations();
        assertTrue(found.stream().anyMatch(v -> v.rule() == VerificationRule.ENTITY_KIND_BINDING
                && v.message().contains("return-evaluation primitive 'missing.eval'")));
    }

    @Test
    @DisplayName("ENTITY_KIND_BINDING: ask on entity kind without entity unit")
    void entityKindAskWithoutEntityUnit() {
        var customKinds = EntityKindRegistry.defaults().with(new EntityKind("screen", Optional.empty(), "test.evaluate-change"));
        var customVal = new WorkflowValidator(TestPrimitives.registry(), List.of("verify.entry"),
                customKinds, QuestionClassRegistry.defaults(), SchemaRegistry.defaults(), TestPrimitives.catalog());

        var def = unit("plan", """
                state verify.entry
                    phase verify
                    job llm screen role reviewer ask
                        approved -> verify.done
                        lost -> verify.done
                """ + DONE_TERMINAL);
        var found = customVal.verify(def).violations();
        assertTrue(found.stream().anyMatch(v -> v.rule() == VerificationRule.ENTITY_KIND_BINDING
                && v.message().contains("declares 'ask' on the entity kind 'screen'")));
    }

    @Test
    @DisplayName("ENTITY_KIND_BINDING: each clause primitive not registered")
    void entityKindEachPrimitiveMissing() {
        var def = unit("plan", """
                state verify.entry
                    phase verify
                    job llm change role reviewer each test.missing
                        approved -> verify.done
                        lost -> verify.done
                """ + DONE_TERMINAL);
        var found = violations(def);
        assertTrue(found.stream().anyMatch(v -> v.rule() == VerificationRule.ENTITY_KIND_BINDING
                && v.message().contains("the primitive 'test.missing' of the each clause is not registered")));
    }

    @Test
    @DisplayName("ENTITY_KIND_BINDING: role each in unit not adjudicate.decisions")
    void entityKindRoleEachNotInAdjudicateDecisions() {
        var def = unit("plan", """
                state verify.entry
                    phase verify
                    job llm change role each test.list-changes ask
                        approved -> verify.done
                        lost -> verify.done
                """ + DONE_TERMINAL);
        var found = violations(def);
        assertTrue(found.stream().anyMatch(v -> v.rule() == VerificationRule.ENTITY_KIND_BINDING
                && v.message().contains("the form 'role each' is admitted only in the unit 'adjudicate.decisions'")));
        assertTrue(found.stream().anyMatch(v -> v.rule() == VerificationRule.ENTITY_KIND_BINDING
                && v.message().contains("declares 'ask' on a 'role each' state")));
    }

    @Test
    @DisplayName("ROLE_NAME_GRAMMAR: consult role with invalid grammar")
    void roleNameGrammarInvalidConsultRole() {
        var def = unit("plan", """
                state verify.entry
                    phase verify
                    job llm change role reviewer
                    consult role too-long-role-name-that-exceeds-maximum-allowed-length-thirty-two
                        approved -> verify.done
                        lost -> verify.done
                """ + DONE_TERMINAL);
        var found = violations(def);
        assertTrue(found.stream().anyMatch(v -> v.rule() == VerificationRule.ROLE_NAME_GRAMMAR
                && v.message().contains("consulted role 'too-long-role-name-that-exceeds-maximum-allowed-length-thirty-two'")));
    }

    @Test
    @DisplayName("JOB_STARTING_OUTCOME: job-starting outcome targets non-job state")
    void jobStartingOutcomeTargetsNonJob() {
        var def = unit("plan", """
                state verify.entry
                    phase verify
                    auto test.enqueue
                        enqueued -> verify.nonjob
                        skipped -> verify.done

                state verify.nonjob
                    phase verify
                    auto test.prepare
                        done -> verify.done
                        skipped -> verify.done
                """ + DONE_TERMINAL);
        var found = violations(def);
        assertTrue(found.stream().anyMatch(v -> v.rule() == VerificationRule.JOB_STARTING_OUTCOME
                && v.message().contains("targets 'verify.nonjob', which is not a job state")));
    }

    @Test
    @DisplayName("JOB_STARTING_OUTCOME: non-job-starting outcome targets job state")
    void jobStartingOutcomeNonJobOutcomeTargetsJob() {
        var def = unit("plan", """
                state verify.entry
                    phase verify
                    auto test.enqueue
                        enqueued -> verify.targetjob
                        skipped -> verify.targetjob

                state verify.targetjob
                    phase verify
                    job test.build
                        succeeded -> verify.done
                        failed -> verify.done
                        cancelled -> verify.done
                """ + DONE_TERMINAL);
        var found = violations(def);
        assertTrue(found.stream().anyMatch(v -> v.rule() == VerificationRule.JOB_STARTING_OUTCOME
                && v.message().contains("does not start a job, but targets the job state 'verify.targetjob'")));
    }

    @Test
    @DisplayName("MODEL_JOB_OUTCOMES: routing unknown outcome, twice, and missing lost")
    void modelJobOutcomesDefects() {
        var def = unit("workspace", """
                state verify.entry
                    phase verify
                    job llm change role reviewer
                        approved -> verify.done
                        approved -> verify.done
                        bogus -> verify.done
                """ + DONE_TERMINAL);
        var found = violations(def);
        assertTrue(found.stream().anyMatch(v -> v.rule() == VerificationRule.MODEL_JOB_OUTCOMES
                && v.message().contains("routes the outcome 'bogus'")));
        assertTrue(found.stream().anyMatch(v -> v.rule() == VerificationRule.MODEL_JOB_OUTCOMES
                && v.message().contains("routes the outcome 'approved' twice")));
        assertTrue(found.stream().anyMatch(v -> v.rule() == VerificationRule.MODEL_JOB_OUTCOMES
                && v.message().contains("does not route the reserved outcome 'lost'")));
    }

    @Test
    @DisplayName("QUESTION_OUTCOMES: routing unknown outcome and twice")
    void questionOutcomesDefects() {
        var def = unit("plan", """
                state verify.entry
                    phase verify
                    question clarification
                        answered -> verify.done
                        answered -> verify.done
                        bogus -> verify.done
                """ + DONE_TERMINAL);
        var found = violations(def);
        assertTrue(found.stream().anyMatch(v -> v.rule() == VerificationRule.QUESTION_OUTCOMES
                && v.message().contains("routes the outcome 'bogus'")));
        assertTrue(found.stream().anyMatch(v -> v.rule() == VerificationRule.QUESTION_OUTCOMES
                && v.message().contains("routes the outcome 'answered' of the question class 'clarification' twice")));
    }

    @Test
    @DisplayName("LINK_COMPATIBILITY: engine owned rel, submit without params, noop with params")
    void linkCompatibilityDefects() {
        var def = unit("plan", """
                state verify.entry
                    phase verify
                    skill pm/verify/start
                    link consult consult:foo ()
                        do test.proceed
                            done -> verify.done
                    link submit proceed [submit] ()
                        do test.proceed
                            done -> verify.done
                    link retry again [noop] (note: text[1..80])
                        do test.choose
                            chosen -> verify.done
                            declined -> verify.done
                    link escalate other [noop] ()
                        do test.proceed
                            done -> verify.done
                """ + DONE_TERMINAL);
        var found = violations(def);
        assertTrue(found.stream().anyMatch(v -> v.rule() == VerificationRule.LINK_COMPATIBILITY
                && v.message().contains("the relation CONSULT is engine-owned")));
        assertTrue(found.stream().anyMatch(v -> v.rule() == VerificationRule.LINK_COMPATIBILITY
                && v.message().contains("has form 'submit' but declares no parameters")));
        assertTrue(found.stream().anyMatch(v -> v.rule() == VerificationRule.LINK_COMPATIBILITY
                && v.message().contains("has form 'noop' but declares parameters")));
        assertTrue(found.stream().anyMatch(v -> v.rule() == VerificationRule.LINK_COMPATIBILITY
                && v.message().contains("must name 'request_waiver'")));
    }

    @Test
    @DisplayName("ABORT_TERMINAL_TARGETING: targeting plan.aborted or epic.aborted improperly")
    void abortTerminalTargetingDefects() {
        var def = unit("plan", """
                state verify.entry
                    phase verify
                    auto test.prepare
                        done -> plan.aborted
                        skipped -> epic.aborted

                state plan.aborted terminal aborted failure
                    phase aborted

                state epic.aborted terminal aborted failure
                    phase aborted
                """ + DONE_TERMINAL);
        var found = violations(def);
        assertTrue(found.stream().anyMatch(v -> v.rule() == VerificationRule.ABORT_TERMINAL_TARGETING
                && v.message().contains("the abort terminal 'plan.aborted' must not be targeted")));
        assertTrue(found.stream().anyMatch(v -> v.rule() == VerificationRule.ABORT_TERMINAL_TARGETING
                && v.message().contains("may only be targeted by 'abort_landed' of 'epic.next-work'")));
    }

    @Test
    @DisplayName("LEAF_TASK_CONSTRAINT: worker unit transitions and consultation constraints")
    void leafTaskConstraints() {
        var workerDef = VerifyFixtures.parseSource("worker.pmwf", """
                workflow worker-task version 1 format 1
                scope entity
                start verify.entry

                state verify.entry
                    phase verify
                    skill pm/verify/start
                    link next proceed ()
                        do test.proceed
                            done -> verify.model

                state verify.model
                    phase verify
                    job llm change role reviewer
                        approved -> verify.done
                        lost -> verify.done

                state verify.done terminal done success
                    phase verify
                """);
        var workerFound = violations(workerDef);
        assertTrue(workerFound.stream().anyMatch(v -> v.rule() == VerificationRule.LEAF_TASK_CONSTRAINT
                && v.message().contains("a worker unit must not contain a model job state")));
        assertTrue(workerFound.stream().anyMatch(v -> v.rule() == VerificationRule.LEAF_TASK_CONSTRAINT
                && v.message().contains("a worker unit link transition must not enter a model job state")));

        var consultDef = VerifyFixtures.parseSource("consultation.pmwf", """
                workflow consultation version 1 format 1
                scope plan
                start verify.entry

                state verify.entry
                    phase verify
                    skill pm/verify/start
                    link next consult:review ()
                        do test.proceed
                            done -> verify.target

                state verify.target
                    phase verify
                    job llm change role reviewer
                    consult role auditor
                        approved -> verify.done
                        lost -> verify.done
                """ + DONE_TERMINAL);
        var consultFound = violations(consultDef);
        assertTrue(consultFound.stream().anyMatch(v -> v.rule() == VerificationRule.LEAF_TASK_CONSTRAINT
                && v.message().contains("a consultation task must not offer a consult link")));
        assertTrue(consultFound.stream().anyMatch(v -> v.rule() == VerificationRule.LEAF_TASK_CONSTRAINT
                && v.message().contains("a consultation task must not consult further roles")));
    }

    @Test
    @DisplayName("SUBWORKFLOW_CALL_CONTRACT: resting link missing skill and phase inherit in plan scope")
    void subWorkflowRestingLinkAndInherit() {
        var subwfDef = VerifyFixtures.parseSource("subwf.pmwf", """
                workflow subwf.test version 1 format 1
                scope subworkflow
                start verify.entry

                state verify.entry
                    phase inherit
                    link next proceed ()
                        do test.proceed
                            done -> verify.done

                state verify.done terminal done success
                    phase inherit
                """);
        var subwfFound = violations(subwfDef);
        assertTrue(subwfFound.stream().anyMatch(v -> v.rule() == VerificationRule.SUBWORKFLOW_CALL_CONTRACT
                && v.message().contains("the resting link state 'verify.entry' of a sub-workflow must declare a skill")));

        var planDef = unit("plan", """
                state verify.entry
                    phase inherit
                    auto test.prepare
                        done -> verify.done
                        skipped -> verify.done
                """ + DONE_TERMINAL);
        var planFound = violations(planDef);
        assertTrue(planFound.stream().anyMatch(v -> v.rule() == VerificationRule.SUBWORKFLOW_CALL_CONTRACT
                && v.message().contains("declares 'phase inherit', which is admitted only in sub-workflow and entity units")));
    }

    @Test
    @DisplayName("SUBWORKFLOW_CALL_CONTRACT: unregistered callee and scope mismatch")
    void subWorkflowCalleeRegistrationAndScope() {
        var unregisteredDef = unit("plan", """
                state verify.entry
                    phase verify
                    call missing.subwf()
                        done -> verify.done
                """ + DONE_TERMINAL);
        var unregFound = violations(unregisteredDef);
        assertTrue(unregFound.stream().anyMatch(v -> v.rule() == VerificationRule.SUBWORKFLOW_CALL_CONTRACT
                && v.message().contains("the called sub-workflow 'missing.subwf' is not registered")));

        var workspaceUnit = VerifyFixtures.parseSource("callee.workspace.pmwf", """
                workflow callee.workspace version 1 format 1
                scope workspace
                start ws.entry

                state ws.entry terminal done success
                    phase verify
                """);
        var catalogWithWs = WorkflowCatalog.of(workspaceUnit);
        var customVal = new WorkflowValidator(TestPrimitives.registry(), List.of("verify.entry"),
                EntityKindRegistry.defaults(), QuestionClassRegistry.defaults(), SchemaRegistry.defaults(), catalogWithWs);

        var callWsDef = unit("plan", """
                state verify.entry
                    phase verify
                    call callee.workspace()
                        done -> verify.done
                        stepped_out -> verify.done
                """ + DONE_TERMINAL);
        var wsFound = customVal.verify(callWsDef).violations();
        assertTrue(wsFound.stream().anyMatch(v -> v.rule() == VerificationRule.SUBWORKFLOW_CALL_CONTRACT
                && v.message().contains("has scope WORKSPACE, expected SUBWORKFLOW")));
    }

    @Test
    @DisplayName("SUBWORKFLOW_CALL_CONTRACT: parameter mismatches for enum, int and list")
    void subWorkflowParamMismatches() {
        var def = unit("plan", """
                state verify.entry
                    phase verify
                    call git.commit_and_push(push = invalid_val, attempts = 99, targets = [invalid_target], extra = true_val)
                        done -> verify.done
                        stepped_out -> verify.done
                """ + DONE_TERMINAL);
        var found = violations(def);
        assertTrue(found.stream().anyMatch(v -> v.rule() == VerificationRule.SUBWORKFLOW_CALL_CONTRACT
                && v.message().contains("not in the allowed enum values")));
        assertTrue(found.stream().anyMatch(v -> v.rule() == VerificationRule.SUBWORKFLOW_CALL_CONTRACT
                && v.message().contains("out of range [1..5]")));
        assertTrue(found.stream().anyMatch(v -> v.rule() == VerificationRule.SUBWORKFLOW_CALL_CONTRACT
                && v.message().contains("the list value 'invalid_target'")));
        assertTrue(found.stream().anyMatch(v -> v.rule() == VerificationRule.SUBWORKFLOW_CALL_CONTRACT
                && v.message().contains("names an undeclared parameter of 'git.commit_and_push'")));
    }

    @Test
    @DisplayName("SUBWORKFLOW_CALL_CONTRACT: missing required params and exit outcomes")
    void subWorkflowMissingParamsAndOutcomes() {
        var def = unit("plan", """
                state verify.entry
                    phase verify
                    call git.commit_and_push()
                        bogus -> verify.done
                """ + DONE_TERMINAL);
        var found = violations(def);
        assertTrue(found.stream().anyMatch(v -> v.rule() == VerificationRule.SUBWORKFLOW_CALL_CONTRACT
                && v.message().contains("omits the required parameter 'push'")));
        assertTrue(found.stream().anyMatch(v -> v.rule() == VerificationRule.SUBWORKFLOW_CALL_CONTRACT
                && v.message().contains("routes the outcome 'bogus', which is not in the exit contract")));
        assertTrue(found.stream().anyMatch(v -> v.rule() == VerificationRule.SUBWORKFLOW_CALL_CONTRACT
                && v.message().contains("does not route the exit outcome 'done'")));
        assertTrue(found.stream().anyMatch(v -> v.rule() == VerificationRule.SUBWORKFLOW_CALL_CONTRACT
                && v.message().contains("does not route the exit outcome 'stepped_out'")));
    }

    @Test
    @DisplayName("SUBWORKFLOW_CALL_CONTRACT: stepped_out is owed by a call to a sub-workflow without a resting link")
    void subWorkflowSteppedOutImplicitWithoutRestingLink() {
        var def = unit("plan", """
                state verify.entry
                    phase verify
                    call test.depth5()
                        done -> verify.done
                """ + DONE_TERMINAL);

        var found = violations(def);

        assertEquals(List.of(new RuleStateMessage(VerificationRule.SUBWORKFLOW_CALL_CONTRACT, "verify.entry",
                        "the call does not route the exit outcome 'stepped_out' of 'test.depth5'")),
                found.stream().map(RuleStateMessage::of).toList());
    }

    @Test
    @DisplayName("SUBWORKFLOW_CALL_CONTRACT: routing stepped_out to a sub-workflow without a resting link passes")
    void subWorkflowSteppedOutRoutedWithoutRestingLink() {
        var def = unit("plan", """
                state verify.entry
                    phase verify
                    call test.depth5()
                        done -> verify.done
                        stepped_out -> plan.return

                state plan.return terminal returned failure
                    phase verify
                """ + DONE_TERMINAL);

        assertEquals(List.of(), violations(def));
    }

    /** The rule, state and message of a violation: everything of it but the source location. */
    private record RuleStateMessage(VerificationRule rule, String stateId, String message) {

        static RuleStateMessage of(Violation violation) {
            return new RuleStateMessage(violation.rule(), violation.stateId(), violation.message());
        }
    }

    @Test
    @DisplayName("SUBWORKFLOW_CALL_CONTRACT: stepped_out owed by a call to a stepping-out sub-workflow but not routed")
    void subWorkflowSteppedOutMissing() {
        var def = unit("subworkflow", """
                state verify.entry
                    phase verify
                    call git.commit_and_push(push = enabled, attempts = 2, targets = [origin])
                        done -> verify.done
                """ + DONE_TERMINAL);

        var found = violations(def);

        assertEquals(List.of(new RuleStateMessage(VerificationRule.SUBWORKFLOW_CALL_CONTRACT, "verify.entry",
                        "the call does not route the exit outcome 'stepped_out' of 'git.commit_and_push'")),
                found.stream().map(RuleStateMessage::of).toList());
    }

    @Test
    @DisplayName("QUESTION_CLASS_BINDING: question class not registered")
    void questionClassUnregistered() {
        var customQuestions = new QuestionClassRegistry(List.of(
                new QuestionClass("init", Set.of("answered", "assumed", "returned")),
                new QuestionClass("clarification", Set.of("answered", "assumed")),
                new QuestionClass("review", Set.of("approve", "revise", "returned")),
                new QuestionClass("task", Set.of("answered", "assumed")),
                new QuestionClass("setup", Set.of("answered", "assumed"))));
        var customVal = new WorkflowValidator(TestPrimitives.registry(), List.of("verify.entry"),
                EntityKindRegistry.defaults(), customQuestions, SchemaRegistry.defaults(), TestPrimitives.catalog());

        var def = unit("workspace", """
                state verify.entry
                    phase verify
                    question ask
                        answered -> verify.done
                        assumed -> verify.done
                """ + DONE_TERMINAL);
        var found = customVal.verify(def).violations();
        assertEquals(1, found.size(), () -> "expected exactly the unregistered class refusal: " + found);
        assertEquals(VerificationRule.QUESTION_CLASS_BINDING, found.getFirst().rule());
        assertTrue(found.getFirst().message().contains(
                        "the question class 'ask' of the state 'verify.entry' is not registered"),
                () -> "missing unregistered class refusal: " + found);
    }

    @Test
    @DisplayName("QUESTION_OUTCOMES: ask class bijection and missing outcome")
    void questionAskOutcomes() {
        var passing = unit("workspace", """
                state verify.entry
                    phase verify
                    question ask
                        answered -> verify.done
                        assumed -> verify.done
                """ + DONE_TERMINAL);
        assertTrue(violations(passing).isEmpty(), () -> "ask bijection should pass: " + violations(passing));

        var missing = unit("workspace", """
                state verify.entry
                    phase verify
                    question ask
                        answered -> verify.done
                """ + DONE_TERMINAL);
        var found = violations(missing);
        assertEquals(1, found.size(), () -> "expected exactly the missing assumed refusal: " + found);
        assertEquals(VerificationRule.QUESTION_OUTCOMES, found.getFirst().rule());
        assertTrue(found.getFirst().message().contains(
                        "does not route the outcome 'assumed' of the question class 'ask'"),
                () -> "missing ask assumed refusal: " + found);
    }

    private static List<RuleStateMessage> stepCompletion(String stateId, String... messages) {
        return Arrays.stream(messages)
                .map(message -> new RuleStateMessage(VerificationRule.STEP_COMPLETION, stateId, message))
                .toList();
    }

    private List<RuleStateMessage> found(WorkflowDefinition definition) {
        return violations(definition).stream().map(RuleStateMessage::of).toList();
    }

    @Test
    @DisplayName("STEP_COMPLETION: a duplicate in one firing and waived outside a remediation call")
    void stepCompletionDefects() {
        var def = unit("plan", """
                state verify.entry
                    phase verify
                    auto test.prepare
                        done -> verify.second
                            completes step:x as done
                        skipped -> verify.done
                            completes step:x as waived

                state verify.second
                    phase verify
                    auto test.prepare
                        done -> verify.done
                            completes step:x as done
                        skipped -> verify.done
                """ + DONE_TERMINAL);

        assertEquals(List.of(
                new RuleStateMessage(VerificationRule.STEP_COMPLETION, "verify.entry",
                        "the step 'step:x' is completed as waived on a transition that is not the 'waived' outcome of a call to a remediation unit"),
                new RuleStateMessage(VerificationRule.STEP_COMPLETION, "verify.second",
                        "the step 'step:x' is completed twice in one firing from 'verify.entry'")),
                found(def));
    }

    @Test
    @DisplayName("STEP_COMPLETION: done on a non-SUCCESS class and on an unresolvable outcome")
    void stepCompletionDoneAdmission() {
        var classDef = unit("plan", """
                state verify.entry
                    phase verify
                    job test.build
                        succeeded -> verify.done
                            completes step:y as done
                        failed -> verify.done
                            completes step:y as done
                        cancelled -> verify.done
                            completes step:y as failed
                """ + DONE_TERMINAL);
        assertEquals(stepCompletion("verify.entry",
                        "the step 'step:y' is completed as done on an outcome of class FAILURE, which is not SUCCESS"),
                found(classDef));

        var unknownDef = unit("plan", """
                state verify.entry
                    phase verify
                    auto test.missing
                        done -> verify.done
                            completes step:z as done
                """ + DONE_TERMINAL);
        assertEquals(List.of(
                new RuleStateMessage(VerificationRule.PRIMITIVE_BINDING, "verify.entry",
                        "the primitive 'test.missing' of the auto is not registered"),
                new RuleStateMessage(VerificationRule.STEP_COMPLETION, "verify.entry",
                        "the step 'step:z' is completed on the outcome 'done', whose class cannot be established")),
                found(unknownDef));
    }

    @Test
    @DisplayName("STEP_COMPLETION: the classes of a task outcome, a call outcome and stepped_out admit completions")
    void stepCompletionOutcomeClasses() {
        var def = VerifyFixtures.parseSource("classify.pmwf", """
                workflow verify.classify version 1 format 1
                scope subworkflow
                start verify.entry

                state verify.entry
                    phase inherit
                    job llm change role reviewer
                        approved -> verify.invocation
                            completes verify:model as done
                        lost -> verify.done
                            completes verify:model as failed
                            completes verify:call as skipped

                state verify.invocation
                    phase inherit
                    call git.commit_and_push(push = enabled, attempts = 2, targets = [origin])
                        done -> verify.done
                            completes verify:call as done
                        stepped_out -> verify.stepped-out

                state verify.done terminal done success
                    phase inherit

                state verify.stepped-out terminal stepped_out stepped_out
                    phase inherit
                """);

        assertEquals(List.of(), violations(def));
    }

    @Test
    @DisplayName("STEP_COMPLETION: a question resolution outcome has no class and admits no completion")
    void stepCompletionQuestionOutcomeHasNoClass() {
        var def = unit("plan", """
                state verify.entry
                    phase verify
                    question clarification
                        answered -> verify.done
                            completes verify:q as done
                        assumed -> verify.done
                            completes verify:q as done
                """ + DONE_TERMINAL);

        assertEquals(stepCompletion("verify.entry",
                        "the step 'verify:q' is completed on the outcome 'answered', whose class cannot be established",
                        "the step 'verify:q' is completed on the outcome 'assumed', whose class cannot be established"),
                found(def));
    }

    @Test
    @DisplayName("STEP_COMPLETION: a firing path that completes no step is refused")
    void stepCompletionNoCompletionOnAPath() {
        var def = unit("plan", """
                state verify.entry
                    phase verify
                    auto test.prepare
                        done -> verify.done
                            completes step:s as done
                        skipped -> verify.done
                """ + DONE_TERMINAL);

        assertEquals(stepCompletion("verify.entry",
                        "the step 'step:s' is not completed on a firing path from 'verify.entry' that ends with the outcome 'skipped' into 'verify.done'"),
                found(def));
    }

    @Test
    @DisplayName("STEP_COMPLETION: an UNSETTLED outcome ends the firing early without a completion")
    void stepCompletionEarlyEndOnUnsettled() {
        var def = unit("plan", """
                state verify.entry
                    phase verify
                    auto test.fix
                        fixed -> verify.done
                            completes step:s as done
                        fix_requested -> verify.done
                """ + DONE_TERMINAL);

        assertEquals(List.of(), violations(def));
    }

    @Test
    @DisplayName("STEP_COMPLETION: entering an implicit-failed state completes the open step as failed")
    void stepCompletionImplicitFailed() {
        var passing = unit("plan", """
                state verify.entry
                    phase verify
                    auto test.prepare
                        done -> verify.done
                            completes step:s as done
                        skipped -> plan.return

                state plan.return terminal returned failure
                    phase verify
                """ + DONE_TERMINAL);
        assertEquals(List.of(), violations(passing));

        var completing = unit("plan", """
                state verify.entry
                    phase verify
                    auto test.prepare
                        done -> verify.done
                            completes step:s as done
                        skipped -> plan.return
                            completes step:s as failed

                state plan.return terminal returned failure
                    phase verify
                """ + DONE_TERMINAL);
        assertEquals(stepCompletion("verify.entry",
                        "the step 'step:s' is completed on the transition into 'plan.return', whose entry records the open firing as failed"),
                found(completing));
    }

    @Test
    @DisplayName("STEP_COMPLETION: skipped is refused on a FAILURE outcome other than a task's infeasible")
    void stepCompletionSkippedAdmission() {
        var def = unit("plan", """
                state verify.entry
                    phase verify
                    job test.build
                        succeeded -> verify.done
                            completes step:s as done
                        failed -> verify.done
                            completes step:s as skipped
                        cancelled -> verify.done
                            completes step:s as failed
                """ + DONE_TERMINAL);

        assertEquals(stepCompletion("verify.entry",
                        "the step 'step:s' is completed as skipped on an outcome of class FAILURE"),
                found(def));
    }

    @Test
    @DisplayName("STEP_COMPLETION: waived is admitted on the waived outcome of a call to a remediation unit")
    void stepCompletionWaivedOnRemediationCall() {
        var def = unit("plan", """
                state verify.entry
                    phase verify
                    call remediation.test-waiver()
                        done -> verify.done
                            completes step:s as done
                        waived -> verify.done
                            completes step:s as waived
                        stepped_out -> plan.return

                state plan.return terminal returned failure
                    phase verify
                """ + DONE_TERMINAL);

        assertEquals(List.of(), violations(def));
    }

    @Test
    @DisplayName("STEP_COMPLETION: waived is refused on a call to a unit that is no remediation unit")
    void stepCompletionWaivedOutsideRemediation() {
        var def = unit("plan", """
                state verify.entry
                    phase verify
                    call git.commit_and_push(push = enabled, attempts = 2, targets = [origin])
                        done -> verify.done
                            completes step:s as waived
                        stepped_out -> plan.return

                state plan.return terminal returned failure
                    phase verify
                """ + DONE_TERMINAL);

        assertEquals(stepCompletion("verify.entry",
                        "the step 'step:s' is completed as waived on a transition that is not the 'waived' outcome of a call to a remediation unit"),
                found(def));
    }

    @Test
    @DisplayName("Cycle measure STEP_LIST: a hub of a unit with a step list passes, and each firing completes its step")
    void stepListOnHubOfUnitWithStepList() {
        var def = unit("plan", """
                state verify.entry
                    phase verify
                    auto test.next-step
                        first -> verify.one
                        second -> verify.two
                        settled -> verify.done

                state verify.one
                    phase verify
                    auto test.prepare
                        done -> verify.entry
                            completes step:one as done
                        skipped -> verify.entry
                            completes step:one as skipped

                state verify.two
                    phase verify
                    auto test.prepare
                        done -> verify.entry
                            completes step:two as done
                        skipped -> verify.entry
                            completes step:two as skipped
                """ + DONE_TERMINAL);

        assertEquals(List.of(), violations(def));
    }

    @Test
    @DisplayName("Cycle measure STEP_LIST: a hub of a unit without a step list is refused")
    void stepListOnUnitWithoutStepList() {
        var def = unit("plan", """
                state verify.entry
                    phase verify
                    auto test.next-step
                        first -> verify.done
                        second -> verify.done
                        settled -> verify.done
                """ + DONE_TERMINAL);

        assertEquals(stepCompletion("verify.entry",
                        "the cycle measure STEP_LIST of the state 'verify.entry' is admitted only on the dispatch hub of a unit with a step list, but the unit 'verify.inline' completes no step"),
                found(def));
    }

    @Test
    @DisplayName("Cycle measure ARCHIVE_STEPS: admitted in an epic unit on the archive path")
    void archiveStepsInEpicUnit() {
        var def = unit("epic", """
                state verify.entry
                    phase verify
                    auto test.next-work
                        archive_uncommitted -> verify.done
                        archived -> verify.done
                """ + DONE_TERMINAL);

        assertEquals(List.of(), violations(def));
    }

    @Test
    @DisplayName("Cycle measure ARCHIVE_STEPS: refused outside an epic unit")
    void archiveStepsOutsideEpicUnit() {
        var def = unit("plan", """
                state verify.entry
                    phase verify
                    auto test.next-work
                        archive_uncommitted -> verify.done
                        archived -> verify.done
                """ + DONE_TERMINAL);

        assertEquals(stepCompletion("verify.entry",
                        "the cycle measure ARCHIVE_STEPS of the state 'verify.entry' is admitted only in an epic unit, but the unit has scope PLAN"),
                found(def));
    }

    @Test
    @DisplayName("Cycle measure ATTEMPT_CAP: a FAILURE exhaustion outcome that leaves the cycle passes")
    void attemptCapLeavingTheCycle() {
        var def = unit("plan", """
                state verify.entry
                    phase verify
                    auto test.capped
                        retry -> verify.entry
                        exhausted -> verify.done
                """ + DONE_TERMINAL);

        assertEquals(List.of(), violations(def));
    }

    @Test
    @DisplayName("Cycle measure ATTEMPT_CAP: refused when the exhaustion transition does not leave the cycle")
    void attemptCapNotLeavingTheCycle() {
        var def = unit("plan", """
                state verify.entry
                    phase verify
                    auto test.capped
                        retry -> verify.done
                        exhausted -> verify.entry
                """ + DONE_TERMINAL);

        assertEquals(stepCompletion("verify.entry",
                        "the exhaustion transition 'exhausted' of the attempt cap of the state 'verify.entry' does not leave the cycle"),
                found(def));
    }

    /** A workspace unit whose state {@code verify.conditional} only the link needing {@code feature.flag} enters. */
    private static final String CONDITIONAL_STATES = """
            state verify.entry
                phase verify
                link next choose [submit] (note: text[1..80], comment: text)
                    guard in_worktree
                    depends_on_config [feature.flag]
                    do test.choose
                        chosen -> verify.conditional
                        declined -> verify.done
                link next fallback ()
                    do test.proceed
                        done -> verify.done

            state verify.conditional
                phase verify
                auto test.prepare
                    done -> verify.done
                    skipped -> verify.entry
            """;

    private static final String ONLY_WITH_FLAG = "only under configuration [feature.flag=true], not under [feature.flag=false]";

    private static List<RuleStateMessage> configAdvisories(String... stateAndMessage) {
        var advisories = new ArrayList<RuleStateMessage>();
        for (int i = 0; i < stateAndMessage.length; i += 2) {
            advisories.add(new RuleStateMessage(VerificationRule.CONFIG_REACHABILITY, stateAndMessage[i],
                    stateAndMessage[i + 1]));
        }
        return advisories;
    }

    @Test
    @DisplayName("CONFIG_REACHABILITY: a state and transitions some combinations do not reach are advisories, not violations")
    void configReachabilityPartialIsAdvisory() {
        var def = unit("workspace", CONDITIONAL_STATES + DONE_TERMINAL);

        var result = validator.verify(def);

        assertEquals(List.of(), result.violations());
        assertTrue(result.passed());
        assertEquals(configAdvisories(
                        "verify.entry", "the transition 'chosen' of the state 'verify.entry' into 'verify.conditional' is reached "
                                + ONLY_WITH_FLAG,
                        "verify.entry", "the transition 'declined' of the state 'verify.entry' into 'verify.done' is reached "
                                + ONLY_WITH_FLAG,
                        "verify.conditional", "the state 'verify.conditional' is reached from the start state 'verify.entry' "
                                + ONLY_WITH_FLAG,
                        "verify.conditional", "the transition 'done' of the state 'verify.conditional' into 'verify.done' is reached "
                                + ONLY_WITH_FLAG,
                        "verify.conditional", "the transition 'skipped' of the state 'verify.conditional' into 'verify.entry' is reached "
                                + ONLY_WITH_FLAG),
                result.advisories().stream().map(RuleStateMessage::of).toList());
        assertEquals(List.of(11, 12, 17, 20, 21),
                result.advisories().stream().map(advisory -> advisory.location().line()).toList());
    }

    @Test
    @DisplayName("CONFIG_REACHABILITY: a unit without depends_on_config keys has no advisory")
    void configReachabilityWithoutKeys() {
        var def = unit("workspace", """
                state verify.entry
                    phase verify
                    link next fallback ()
                        do test.proceed
                            done -> verify.done
                """ + DONE_TERMINAL);

        var result = validator.verify(def);

        assertEquals(List.of(), result.violations());
        assertEquals(List.of(), result.advisories());
    }

    @Test
    @DisplayName("CONFIG_REACHABILITY: a state no combination reaches is refused by REACHABILITY, not as CONFIG_REACHABILITY (ADR 006)")
    void configReachabilityNoCombination() {
        var def = unit("workspace", CONDITIONAL_STATES + """

                state verify.orphan
                    phase verify
                    auto test.prepare
                        done -> verify.done
                        skipped -> verify.entry
                """ + DONE_TERMINAL);

        var result = validator.verify(def);

        assertEquals(List.of(new RuleStateMessage(VerificationRule.REACHABILITY, "verify.orphan",
                        "the state 'verify.orphan' is not reachable from the start state 'verify.entry'")),
                result.violations().stream().map(RuleStateMessage::of).toList());
        assertEquals(List.of(23), result.violations().stream().map(violation -> violation.location().line()).toList());
        assertEquals(List.of("verify.entry", "verify.entry", "verify.conditional", "verify.conditional",
                        "verify.conditional"),
                result.advisories().stream().map(Violation::stateId).toList(),
                "the unreachable state and its transitions are no advisories");
    }

    private static List<RuleStateMessage> callContract(String stateId, String... messages) {
        return Arrays.stream(messages)
                .map(message -> new RuleStateMessage(VerificationRule.SUBWORKFLOW_CALL_CONTRACT, stateId, message))
                .toList();
    }

    private static final String STEPPED_OUT_TERMINAL = """

            state verify.stepped-out terminal stepped_out stepped_out
                phase inherit
            """;

    @Test
    @DisplayName("Rule 6 stepped_out target: a success terminal is refused in every scope")
    void steppedOutToSuccessTerminal() {
        var def = unit("plan", """
                state verify.entry
                    phase verify
                    call test.depth5()
                        done -> verify.done
                        stepped_out -> verify.done
                """ + DONE_TERMINAL);

        assertEquals(callContract("verify.entry",
                        "the call routes 'stepped_out' to 'verify.done', which is a terminal state of class SUCCESS"),
                found(def));
    }

    @Test
    @DisplayName("Rule 6 stepped_out target: a plan unit routes it to the phase's fallback state only")
    void steppedOutInPlanUnit() {
        var handBack = unit("plan", """
                state verify.entry
                    phase verify
                    call test.depth5()
                        done -> verify.done
                        stepped_out -> finalize.hand-back

                state finalize.hand-back terminal handed_back failure
                    phase verify
                """ + DONE_TERMINAL);
        assertEquals(List.of(), violations(handBack));

        var failureTerminal = unit("plan", """
                state verify.entry
                    phase verify
                    call test.depth5()
                        done -> verify.done
                        stepped_out -> verify.failed

                state verify.failed terminal failed failure
                    phase verify
                """ + DONE_TERMINAL);
        assertEquals(callContract("verify.entry",
                        "the call routes 'stepped_out' to 'verify.failed', which is not the phase's fallback state 'plan.return' or 'finalize.hand-back'"),
                found(failureTerminal));
    }

    @Test
    @DisplayName("Rule 6 stepped_out target: an epic unit routes it to a resting state of the unit")
    void steppedOutInEpicUnit() {
        var resting = unit("epic", """
                state verify.entry
                    phase verify
                    call test.depth5()
                        done -> verify.done
                        stepped_out -> verify.rest

                state verify.rest
                    phase verify
                    job test.build
                        succeeded -> verify.done
                        failed -> verify.done
                        cancelled -> verify.done
                """ + DONE_TERMINAL);
        assertEquals(List.of(), violations(resting));

        var chained = unit("epic", """
                state verify.entry
                    phase verify
                    call test.depth5()
                        done -> verify.done
                        stepped_out -> verify.retry

                state verify.retry
                    phase verify
                    auto test.prepare
                        done -> verify.done
                        skipped -> verify.done
                """ + DONE_TERMINAL);
        assertEquals(callContract("verify.entry",
                        "the call routes 'stepped_out' to 'verify.retry', which is not a resting state of the unit"),
                found(chained));
    }

    @Test
    @DisplayName("Rule 6 stepped_out target: a sub-workflow continues the step-out or handles the failure")
    void steppedOutInSubWorkflow() {
        var continuing = unit("subworkflow", """
                state verify.entry
                    phase inherit
                    call test.depth5()
                        done -> verify.done
                        stepped_out -> verify.stepped-out
                """ + STEPPED_OUT_TERMINAL + DONE_TERMINAL);
        assertEquals(List.of(), violations(continuing));

        var chained = unit("subworkflow", """
                state verify.entry
                    phase inherit
                    call test.depth5()
                        done -> verify.done
                        stepped_out -> verify.retry

                state verify.retry
                    phase inherit
                    auto test.prepare
                        done -> verify.done
                        skipped -> verify.stepped-out
                """ + STEPPED_OUT_TERMINAL + DONE_TERMINAL);
        assertEquals(callContract("verify.entry",
                        "the call routes 'stepped_out' to 'verify.retry', which is neither a terminal of class STEPPED_OUT or FAILURE nor a link state that continues the step-out"),
                found(chained));
    }

    @Test
    @DisplayName("Rule 6 stepped_out target: a remediation unit may route it to a state the language does not mark (ADR 010)")
    void steppedOutInRemediationUnit() {
        var def = VerifyFixtures.parseSource("remediation.pmwf", """
                workflow remediation.inline version 1 format 1
                scope subworkflow
                start verify.entry

                state verify.entry
                    phase inherit
                    call test.depth5()
                        done -> verify.done
                        stepped_out -> verify.recheck

                state verify.recheck
                    phase inherit
                    auto test.prepare
                        done -> verify.done
                        skipped -> verify.stepped-out
                """ + STEPPED_OUT_TERMINAL + DONE_TERMINAL);

        assertEquals(List.of(), violations(def));
    }

    @Test
    @DisplayName("Rule 6: the outcome stepped_out is declared only by a terminal of class STEPPED_OUT")
    void steppedOutDeclaredByWrongClass() {
        var def = unit("subworkflow", """
                state verify.entry
                    phase inherit
                    auto test.prepare
                        done -> verify.done
                        skipped -> verify.out

                state verify.out terminal stepped_out failure
                    phase inherit
                """ + DONE_TERMINAL);

        assertEquals(callContract("verify.out",
                        "the terminal 'verify.out' declares the outcome 'stepped_out' with the class FAILURE; only a terminal of class STEPPED_OUT declares it"),
                found(def));
    }

    @Test
    @DisplayName("Rule 6: a single guarded done is refused, the complementary pair is admitted")
    void singleGuardedDone() {
        var def = unit("subworkflow", """
                state verify.entry
                    phase inherit
                    call git.commit_and_push(push = enabled, attempts = 2, targets = [origin])
                        done -> verify.done
                            guard result.pushed
                        stepped_out -> verify.stepped-out
                """ + STEPPED_OUT_TERMINAL + DONE_TERMINAL);

        assertEquals(callContract("verify.entry",
                        "the call routes the exit outcome 'done' of 'git.commit_and_push' once under a result guard; a guarded 'done' is admitted only as the complementary pair"),
                found(def));
    }

    @Test
    @DisplayName("ENTITY_KIND_BINDING: the entity unit of a kind is resolved against the catalog")
    void entityUnitResolvedAgainstCatalog() {
        var unresolved = unit("plan", """
                state verify.entry
                    phase verify
                    job llm unresolved role reviewer
                        approved -> verify.done
                        lost -> verify.done
                """ + DONE_TERMINAL);
        assertEquals(List.of(new RuleStateMessage(VerificationRule.ENTITY_KIND_BINDING, "verify.entry",
                        "the entity unit 'test.no-such-unit' of the entity kind 'unresolved' is not registered")),
                found(unresolved));

        var resolved = unit("plan", """
                state verify.entry
                    phase verify
                    job llm change role reviewer
                        approved -> verify.done
                        lost -> verify.done
                """ + DONE_TERMINAL);
        assertEquals(List.of(), violations(resolved));
    }

    @Test
    @DisplayName("SESSION_DRIVEN_LINKS: a resting link is refused at any depth of a plan unit's calls (ADR 011)")
    void sessionDrivenLinksTransitive() {
        var nested = unit("plan", """
                state verify.entry
                    phase verify
                    call test.links-outer()
                        done -> verify.done
                        stepped_out -> plan.return

                state plan.return terminal returned failure
                    phase verify
                """ + DONE_TERMINAL);
        assertEquals(List.of(new RuleStateMessage(VerificationRule.SESSION_DRIVEN_LINKS, "verify.entry",
                        "the sub-workflow 'test.links-inner' reached by the call contains resting links, which are not admitted in a unit of scope PLAN")),
                found(nested));

        var direct = unit("plan", """
                state verify.entry
                    phase verify
                    call test.links-inner()
                        done -> verify.done
                        stepped_out -> plan.return

                state plan.return terminal returned failure
                    phase verify
                """ + DONE_TERMINAL);
        assertEquals(List.of(new RuleStateMessage(VerificationRule.SESSION_DRIVEN_LINKS, "verify.entry",
                        "the sub-workflow 'test.links-inner' reached by the call contains resting links, which are not admitted in a unit of scope PLAN")),
                found(direct));

        var linkFree = unit("plan", """
                state verify.entry
                    phase verify
                    call test.depth3()
                        done -> verify.done
                        stepped_out -> plan.return

                state plan.return terminal returned failure
                    phase verify
                """ + DONE_TERMINAL);
        assertEquals(List.of(), violations(linkFree));
    }

    @Test
    @DisplayName("CYCLE_AWAITED_EVENT 11b: a question state's link is judged by its primitive, its own outcomes await the answer")
    void questionLinkCycle() {
        var noEvent = unit("workspace", """
                state verify.entry
                    phase verify
                    question ask
                        answered -> verify.done
                        assumed -> verify.done
                    link next again ()
                        do test.proceed
                            done -> verify.entry
                """ + DONE_TERMINAL);
        assertEquals(List.of(new RuleStateMessage(VerificationRule.CYCLE_AWAITED_EVENT, "verify.entry",
                        "the cycle 'verify.entry -> verify.entry' has no awaited event")),
                found(noEvent));

        var submitting = unit("workspace", """
                state verify.entry
                    phase verify
                    question ask
                        answered -> verify.done
                        assumed -> verify.entry
                    link next choose [submit] (note: text[1..80], comment: text)
                        do test.choose
                            chosen -> verify.entry
                            declined -> verify.done
                """ + DONE_TERMINAL);
        assertEquals(List.of(), violations(submitting));
    }

    @Test
    @DisplayName("CYCLE_AWAITED_EVENT 11a: a measured engine-chained cycle passes, an unmeasured one is refused")
    void engineChainedCycleMeasure() {
        var measured = unit("plan", """
                state verify.entry
                    phase verify
                    auto test.capped
                        retry -> verify.entry
                        exhausted -> verify.done
                """ + DONE_TERMINAL);
        assertEquals(List.of(), violations(measured));

        var unmeasured = unit("plan", """
                state verify.entry
                    phase verify
                    auto test.prepare
                        done -> verify.entry
                        skipped -> verify.done
                """ + DONE_TERMINAL);
        assertEquals(List.of(new RuleStateMessage(VerificationRule.CYCLE_AWAITED_EVENT, "verify.entry",
                        "the engine-chained cycle 'verify.entry -> verify.entry' has no cycle measure")),
                found(unmeasured));
    }
}
