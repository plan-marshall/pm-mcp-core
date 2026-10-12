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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.workflow.ast.WorkflowDefinition;

@DisplayName("Additional Rules Coverage")
class AdditionalRulesTest {

    private final WorkflowValidator validator = new WorkflowValidator(
            TestPrimitives.registry(),
            List.of("verify.entry"),
            EntityKindRegistry.defaults(),
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
    void testStateExistsMissingStartState() {
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
    void testSchemaBindingUnregistered() {
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
    void testEntityKindMissingReturnEvalPrimitive() {
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
    void testEntityKindAskWithoutEntityUnit() {
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
    void testEntityKindEachPrimitiveMissing() {
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
    void testEntityKindRoleEachNotInAdjudicateDecisions() {
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
    void testRoleNameGrammarInvalidConsultRole() {
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
    void testJobStartingOutcomeTargetsNonJob() {
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
    void testJobStartingOutcomeNonJobOutcomeTargetsJob() {
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
    void testModelJobOutcomesDefects() {
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
    void testQuestionOutcomesDefects() {
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
    void testLinkCompatibilityDefects() {
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
    void testAbortTerminalTargetingDefects() {
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
    void testLeafTaskConstraints() {
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
    void testSubWorkflowRestingLinkAndInherit() {
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
    void testSubWorkflowCalleeRegistrationAndScope() {
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
    void testSubWorkflowParamMismatches() {
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
    void testSubWorkflowMissingParamsAndOutcomes() {
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
    @DisplayName("STEP_COMPLETION: conflicting outcome classes and completion to abort terminal")
    void testStepCompletionDefects() {
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
        var found = violations(def);
        assertTrue(found.stream().anyMatch(v -> v.rule() == VerificationRule.STEP_COMPLETION
                && v.message().contains("completed twice on a path without an intervening dispatch")));
        assertTrue(found.stream().anyMatch(v -> v.rule() == VerificationRule.STEP_COMPLETION
                && v.message().contains("completed as waived on a transition that is not the 'waived' outcome of a remediation call")));
    }

    @Test
    @DisplayName("STEP_COMPLETION: outcomeClassOf for ModelJob, SubWorkflowCall, QuestionWait")
    void testStepCompletionOutcomeClasses() {
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

                state verify.invocation
                    phase inherit
                    call git.commit_and_push(push = enabled, attempts = 2, targets = [origin])
                        done -> verify.ask_step
                            completes verify:call as done
                        stepped_out -> verify.done
                            completes verify:call as skipped

                state verify.ask_step
                    phase inherit
                    question clarification
                        answered -> verify.done
                            completes verify:q as done
                        assumed -> verify.done
                            completes verify:q as done
                    link next proceed ()
                        do test.proceed
                            done -> verify.done
                                completes verify:q as done

                state verify.done terminal done success
                    phase inherit
                """);
        var found = violations(def);
        // All completions should match valid outcome classes without contradictions
        assertTrue(found.stream().noneMatch(v -> v.rule() == VerificationRule.STEP_COMPLETION
                && v.message().contains("contradicts")));
    }
}
