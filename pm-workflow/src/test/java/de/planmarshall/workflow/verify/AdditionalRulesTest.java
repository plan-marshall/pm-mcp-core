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

    /** The rule, state and message of a violation: everything of it but the source location. */
    private record RuleStateMessage(VerificationRule rule, String stateId, String message) {

        static RuleStateMessage of(Violation violation) {
            return new RuleStateMessage(violation.rule(), violation.stateId(), violation.message());
        }
    }

    private static List<RuleStateMessage> violationsOf(VerificationRule rule, String stateId, String... messages) {
        return Arrays.stream(messages).map(message -> new RuleStateMessage(rule, stateId, message)).toList();
    }

    private static List<RuleStateMessage> found(WorkflowValidator verifier, WorkflowDefinition definition) {
        return verifier.verify(definition).violations().stream().map(RuleStateMessage::of).toList();
    }

    private List<RuleStateMessage> found(WorkflowDefinition definition) {
        return found(validator, definition);
    }

    @Test
    @DisplayName("STATE_EXISTS: start state missing in definition, so no state is reachable")
    void stateExistsMissingStartState() {
        var def = VerifyFixtures.parseSource("missing-start.pmwf", """
                workflow verify.inline version 1 format 1
                scope plan
                start verify.missing

                state verify.entry terminal done success
                    phase verify
                """);

        assertEquals(List.of(
                        new RuleStateMessage(VerificationRule.REACHABILITY, "verify.entry",
                                "the state 'verify.entry' is not reachable from the start state 'verify.missing'"),
                        new RuleStateMessage(VerificationRule.STATE_EXISTS, "verify.missing",
                                "the start state 'verify.missing' does not exist in the unit")),
                found(def));
    }

    @Test
    @DisplayName("SCHEMA_BINDING: link parameter with unregistered schema")
    void schemaBindingUnregistered() {
        // A schema-typed free parameter never equals the text component of a parameter record, so Rule 1's
        // parameter equivalence refuses it as well; the plan scope of ebe82b3 is a workspace unit here because a
        // plan unit admits no resting link state (Rule 10).
        var def = unit("workspace", """
                state verify.entry
                    phase verify
                    link submit choose [submit] (note: schema[unregistered-schema], comment: text)
                        do test.choose
                            chosen -> verify.done
                            declined -> verify.done
                """ + DONE_TERMINAL);

        assertEquals(List.of(
                        new RuleStateMessage(VerificationRule.PARAMETER_EQUIVALENCE, "verify.entry",
                                "the link 'choose' of the primitive 'test.choose' declares the free parameter 'note' as"
                                        + " SchemaRef[schemaName=unregistered-schema], but the parameter record"
                                        + " declares a text"),
                        new RuleStateMessage(VerificationRule.SCHEMA_BINDING, "verify.entry",
                                "the schema 'unregistered-schema' of the parameter 'note' is not registered")),
                found(def));
    }

    @Test
    @DisplayName("ENTITY_KIND_BINDING: return evaluation primitive missing")
    void entityKindMissingReturnEvalPrimitive() {
        var customKinds = TestPrimitives.entityKinds().with(new EntityKind("custom", Optional.empty(), "missing.eval"));
        var customVal = new WorkflowValidator(TestPrimitives.registry(), List.of("verify.entry"),
                customKinds, QuestionClassRegistry.defaults(), SchemaRegistry.defaults(), TestPrimitives.catalog());

        var def = unit("plan", """
                state verify.entry
                    phase verify
                    job llm custom role reviewer
                        approved -> verify.done
                        lost -> verify.done
                """ + DONE_TERMINAL);

        assertEquals(violationsOf(VerificationRule.ENTITY_KIND_BINDING, "verify.entry",
                "the return-evaluation primitive 'missing.eval' of the entity kind 'custom' is not registered"),
                found(customVal, def));
    }

    @Test
    @DisplayName("ENTITY_KIND_BINDING: ask on entity kind without entity unit")
    void entityKindAskWithoutEntityUnit() {
        var customKinds = TestPrimitives.entityKinds()
                .with(new EntityKind("screen", Optional.empty(), "test.evaluate-change"));
        var customVal = new WorkflowValidator(TestPrimitives.registry(), List.of("verify.entry"),
                customKinds, QuestionClassRegistry.defaults(), SchemaRegistry.defaults(), TestPrimitives.catalog());

        var def = unit("plan", """
                state verify.entry
                    phase verify
                    job llm screen role reviewer ask
                        approved -> verify.done
                        lost -> verify.done
                """ + DONE_TERMINAL);

        assertEquals(violationsOf(VerificationRule.ENTITY_KIND_BINDING, "verify.entry",
                "the state 'verify.entry' declares 'ask' on the entity kind 'screen', which has no entity unit"),
                found(customVal, def));
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

        assertEquals(violationsOf(VerificationRule.ENTITY_KIND_BINDING, "verify.entry",
                "the primitive 'test.missing' of the each clause is not registered"),
                found(def));
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

        assertEquals(violationsOf(VerificationRule.ENTITY_KIND_BINDING, "verify.entry",
                "the form 'role each' is admitted only in the unit 'adjudicate.decisions', but found in"
                        + " 'verify.inline'",
                "the state 'verify.entry' declares 'ask' on a 'role each' state"),
                found(def));
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

        assertEquals(violationsOf(VerificationRule.ROLE_NAME_GRAMMAR, "verify.entry",
                "the consulted role 'too-long-role-name-that-exceeds-maximum-allowed-length-thirty-two' of the"
                        + " state 'verify.entry' does not match the role-name grammar"),
                found(def));
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

        assertEquals(violationsOf(VerificationRule.JOB_STARTING_OUTCOME, "verify.entry",
                "the job-starting outcome 'enqueued' of the primitive 'test.enqueue' targets 'verify.nonjob',"
                        + " which is not a job state"),
                found(def));
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

        assertEquals(violationsOf(VerificationRule.JOB_STARTING_OUTCOME, "verify.entry",
                "the outcome 'skipped' of the primitive 'test.enqueue' does not start a job, but targets the"
                        + " job state 'verify.targetjob'"),
                found(def));
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

        assertEquals(violationsOf(VerificationRule.MODEL_JOB_OUTCOMES, "verify.entry",
                "the state 'verify.entry' routes the outcome 'approved' twice",
                "the state 'verify.entry' routes the outcome 'bogus', which the return-evaluation primitive"
                        + " 'test.evaluate-change' does not have",
                "the state 'verify.entry' does not route the reserved outcome 'lost'"),
                found(def));
    }

    /** A unit of the given scope whose one state calls {@code callee}; it is read only as a caller, never verified. */
    private static WorkflowDefinition caller(String id, String scope, String callee) {
        return VerifyFixtures.parseSource(id + ".pmwf", """
                workflow %s version 1 format 1
                scope %s
                start caller.invoke

                state caller.invoke
                    phase verify
                    call %s()
                        done -> caller.done
                        stepped_out -> caller.done

                state caller.done terminal done success
                    phase verify
                """.formatted(id, scope, callee));
    }

    /**
     * The sub-workflow {@code verify.reviewed}: its task state routes {@code approved} back to itself and leaves
     * {@code lost} undeclared, so the default {@code lost} route is its only exit.
     */
    private static final WorkflowDefinition REVIEWED = VerifyFixtures.parseSource("reviewed.pmwf", """
            workflow verify.reviewed version 1 format 1
            scope subworkflow
            start verify.entry

            state verify.entry
                phase inherit
                job llm change role reviewer
                    approved -> verify.entry
            """);

    /** Verifies {@link #REVIEWED} against a catalog of the callers and the entity unit of its task state. */
    private static List<RuleStateMessage> foundWithCallers(WorkflowDefinition... callers) {
        var units = new ArrayList<>(List.of(callers));
        units.add(TestPrimitives.catalog().find("entity").orElseThrow());
        var verifier = new WorkflowValidator(TestPrimitives.registry(), List.of("verify.entry"),
                TestPrimitives.entityKinds(), QuestionClassRegistry.defaults(), SchemaRegistry.defaults(),
                new WorkflowCatalog(units));
        return found(verifier, REVIEWED);
    }

    private static final List<RuleStateMessage> LOST_ROUTED_ITSELF = List.of(
            new RuleStateMessage(VerificationRule.MODEL_JOB_OUTCOMES, "verify.entry",
                    "the state 'verify.entry' does not route the reserved outcome 'lost'"),
            new RuleStateMessage(VerificationRule.EXIT_PATH, "verify.entry",
                    "the state 'verify.entry' has no path to a terminal state"));

    @Test
    @DisplayName("MODEL_JOB_OUTCOMES and EXIT_PATH: a sub-workflow reached only from plan units takes the default lost route (ADR 025)")
    void lostOmittedUnderPlanUnitsOnly() {
        assertEquals(List.of(), foundWithCallers(caller("verify.plan-a", "plan", "verify.reviewed")));
        assertEquals(List.of(), foundWithCallers(caller("verify.plan-a", "plan", "verify.middle"),
                caller("verify.plan-b", "plan", "verify.reviewed"),
                caller("verify.middle", "subworkflow", "verify.reviewed")));
    }

    @Test
    @DisplayName("MODEL_JOB_OUTCOMES and EXIT_PATH: a sub-workflow with a caller chain outside the plan units routes lost (ADR 025)")
    void lostOmittedOutsidePlanUnitsOnly() {
        assertEquals(LOST_ROUTED_ITSELF, foundWithCallers(), "no caller");
        assertEquals(LOST_ROUTED_ITSELF, foundWithCallers(caller("verify.plan-a", "plan", "verify.reviewed"),
                caller("verify.epic-a", "epic", "verify.reviewed")), "a direct epic caller");
        assertEquals(LOST_ROUTED_ITSELF, foundWithCallers(caller("verify.plan-a", "plan", "verify.middle"),
                caller("verify.middle", "subworkflow", "verify.reviewed"),
                caller("verify.workspace-a", "workspace", "verify.middle")), "a workspace caller through a sub-workflow");
        assertEquals(LOST_ROUTED_ITSELF, foundWithCallers(caller("verify.middle", "subworkflow", "verify.reviewed")),
                "a sub-workflow caller without a caller of its own");
        assertEquals(LOST_ROUTED_ITSELF, foundWithCallers(caller("verify.entity-a", "entity", "verify.reviewed")),
                "an entity caller");
    }

    @Test
    @DisplayName("QUESTION_OUTCOMES: routing unknown outcome, twice, and missing outcome")
    void questionOutcomesDefects() {
        var def = unit("plan", """
                state verify.entry
                    phase verify
                    question clarification
                        answered -> verify.done
                        answered -> verify.done
                        bogus -> verify.done
                """ + DONE_TERMINAL);

        assertEquals(violationsOf(VerificationRule.QUESTION_OUTCOMES, "verify.entry",
                "the question state 'verify.entry' routes the outcome 'answered' of the question class"
                        + " 'clarification' twice",
                "the question state 'verify.entry' routes the outcome 'bogus', which the question class"
                        + " 'clarification' does not have",
                "the question state 'verify.entry' does not route the outcome 'assumed' of the question class"
                        + " 'clarification'"),
                found(def));
    }

    @Test
    @DisplayName("LINK_COMPATIBILITY: engine-owned relation, inadmissible form, parameters and the escalate identifier")
    void linkCompatibilityDefects() {
        var def = unit("workspace", """
                state verify.entry
                    phase verify
                    link consult other (note: text[1..80], comment: text)
                        do test.choose
                            chosen -> verify.done
                            declined -> verify.done
                    link submit pick [noop] ()
                        do test.proceed
                            done -> verify.done
                    link submit proceed [submit] ()
                        do test.proceed
                            done -> verify.done
                    link retry again [noop] (note: text[1..80], comment: text)
                        do test.choose
                            chosen -> verify.done
                            declined -> verify.done
                    link escalate appeal (note: text[1..80], comment: text)
                        do test.choose
                            chosen -> verify.done
                            declined -> verify.done
                """ + DONE_TERMINAL);

        assertEquals(violationsOf(VerificationRule.LINK_COMPATIBILITY, "verify.entry",
                "the relation CONSULT is engine-owned and must not be declared in a link clause",
                "the link 'pick' of relation SUBMIT does not admit the form NOOP",
                "the link 'proceed' has form 'submit' but declares no parameters",
                "the link 'again' has form 'noop' but declares parameters",
                "the link 'appeal' of relation ESCALATE must name 'request_waiver'"),
                found(def));
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
                """);

        assertEquals(violationsOf(VerificationRule.ABORT_TERMINAL_TARGETING, "verify.entry",
                "the abort terminal 'plan.aborted' must not be targeted by any declared transition",
                "the abort terminal 'epic.aborted' may only be targeted by 'abort_landed' of 'epic.next-work'"),
                found(def));
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
        assertEquals(List.of(
                        new RuleStateMessage(VerificationRule.LEAF_TASK_CONSTRAINT, "verify.entry",
                                "a worker unit link transition must not enter a model job state"),
                        new RuleStateMessage(VerificationRule.LEAF_TASK_CONSTRAINT, "verify.model",
                                "a worker unit must not contain a model job state")),
                found(workerDef));

        var consultDef = VerifyFixtures.parseSource("consultation.pmwf", """
                workflow consultation version 1 format 1
                scope subworkflow
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
        assertEquals(List.of(
                        new RuleStateMessage(VerificationRule.LINK_ID_UNIQUE, "verify.entry",
                                "the link 'consult:review' of the state 'verify.entry' repeats the identifier of the"
                                        + " implicit link"),
                        new RuleStateMessage(VerificationRule.LEAF_TASK_CONSTRAINT, "verify.entry",
                                "a consultation task must not offer a consult link"),
                        new RuleStateMessage(VerificationRule.LEAF_TASK_CONSTRAINT, "verify.target",
                                "a consultation task must not consult further roles")),
                found(consultDef));
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
        assertEquals(violationsOf(VerificationRule.SUBWORKFLOW_CALL_CONTRACT, "verify.entry",
                "the resting link state 'verify.entry' of a sub-workflow must declare a skill"),
                found(subwfDef));

        var planDef = unit("plan", """
                state verify.entry
                    phase inherit
                    auto test.prepare
                        done -> verify.done
                        skipped -> verify.done
                """ + DONE_TERMINAL);
        assertEquals(violationsOf(VerificationRule.SUBWORKFLOW_CALL_CONTRACT, "verify.entry",
                "the state 'verify.entry' declares 'phase inherit', which is admitted only in sub-workflow and"
                        + " entity units"),
                found(planDef));
    }

    private static final String PLAN_RETURN = """

            state plan.return terminal returned failure
                phase verify
            """;

    @Test
    @DisplayName("SUBWORKFLOW_CALL_CONTRACT: unregistered callee and scope mismatch")
    void subWorkflowCalleeRegistrationAndScope() {
        var unregisteredDef = unit("plan", """
                state verify.entry
                    phase verify
                    call missing.subwf()
                        done -> verify.done
                """ + DONE_TERMINAL);
        assertEquals(violationsOf(VerificationRule.SUBWORKFLOW_CALL_CONTRACT, "verify.entry",
                "the called sub-workflow 'missing.subwf' is not registered"),
                found(unregisteredDef));

        var callWsDef = unit("plan", """
                state verify.entry
                    phase verify
                    call test.workspace-unit()
                        done -> verify.done
                        stepped_out -> plan.return
                """ + PLAN_RETURN + DONE_TERMINAL);
        assertEquals(violationsOf(VerificationRule.SUBWORKFLOW_CALL_CONTRACT, "verify.entry",
                "the called workflow 'test.workspace-unit' has scope WORKSPACE, expected SUBWORKFLOW"),
                found(callWsDef));
    }

    @Test
    @DisplayName("SUBWORKFLOW_CALL_CONTRACT: parameter mismatches for enum, int and list")
    void subWorkflowParamMismatches() {
        var def = unit("plan", """
                state verify.entry
                    phase verify
                    call git.commit_and_push(push = invalid_val, attempts = 99, targets = [invalid_target], extra = true_val)
                        done -> verify.done
                        stepped_out -> plan.return
                """ + PLAN_RETURN + DONE_TERMINAL);

        assertEquals(violationsOf(VerificationRule.SUBWORKFLOW_CALL_CONTRACT, "verify.entry",
                "the value 'invalid_val' of parameter 'push' is not in the allowed enum values [enabled,"
                        + " disabled] of 'git.commit_and_push'",
                "the integer value 99 of parameter 'attempts' is out of range [1..5] for 'git.commit_and_push'",
                "the list value 'invalid_target' of parameter 'targets' is not in the allowed enum values"
                        + " [origin, mirror] of 'git.commit_and_push'",
                "the parameter binding 'extra' names an undeclared parameter of 'git.commit_and_push'"),
                found(def));
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

        assertEquals(violationsOf(VerificationRule.SUBWORKFLOW_CALL_CONTRACT, "verify.entry",
                "the call routes the outcome 'bogus', which is not in the exit contract of"
                        + " 'git.commit_and_push'",
                "the call does not route the exit outcome 'done' of 'git.commit_and_push'",
                "the call does not route the exit outcome 'stepped_out' of 'git.commit_and_push'",
                "the call omits the required parameter 'push' of 'git.commit_and_push'",
                "the call omits the required parameter 'attempts' of 'git.commit_and_push'",
                "the call omits the required parameter 'targets' of 'git.commit_and_push'"),
                found(def));
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
                TestPrimitives.entityKinds(), customQuestions, SchemaRegistry.defaults(), TestPrimitives.catalog());

        var def = unit("workspace", """
                state verify.entry
                    phase verify
                    question ask
                        answered -> verify.done
                        assumed -> verify.done
                """ + DONE_TERMINAL);

        assertEquals(violationsOf(VerificationRule.QUESTION_CLASS_BINDING, "verify.entry",
                "the question class 'ask' of the state 'verify.entry' is not registered"),
                found(customVal, def));
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
        assertEquals(List.of(), violations(passing));

        var missing = unit("workspace", """
                state verify.entry
                    phase verify
                    question ask
                        answered -> verify.done
                """ + DONE_TERMINAL);
        assertEquals(violationsOf(VerificationRule.QUESTION_OUTCOMES, "verify.entry",
                "the question state 'verify.entry' does not route the outcome 'assumed' of the question class"
                        + " 'ask'"),
                found(missing));
    }

    private static List<RuleStateMessage> stepCompletion(String stateId, String... messages) {
        return violationsOf(VerificationRule.STEP_COMPLETION, stateId, messages);
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
    @DisplayName("Cycle measure ARCHIVE_STEPS: admitted in an epic unit on the archive path that commits and lands (ADR 022)")
    void archiveStepsInEpicUnit() {
        var def = unit("epic", """
                state verify.entry
                    phase verify
                    auto test.next-work
                        archive_uncommitted -> verify.commit
                        archived -> verify.done

                state verify.commit
                    phase verify
                    call git.commit_and_push(push = enabled, attempts = 2, targets = [origin])
                        done -> verify.land
                        stepped_out -> verify.land

                state verify.land
                    phase verify
                    job test.build
                        succeeded -> verify.entry
                        failed -> verify.entry
                        cancelled -> verify.entry
                """ + DONE_TERMINAL);

        assertEquals(List.of(), violations(def));
    }

    @Test
    @DisplayName("Cycle measure ARCHIVE_STEPS: refused on an archive path that neither commits nor lands (ADR 022)")
    void archiveStepsWithoutCommitAndLanding() {
        var def = unit("epic", """
                state verify.entry
                    phase verify
                    auto test.next-work
                        archive_uncommitted -> verify.done
                        archived -> verify.done
                """ + DONE_TERMINAL);

        assertEquals(stepCompletion("verify.entry",
                "the cycle measure ARCHIVE_STEPS of the state 'verify.entry' is admitted only on an archive path that commits, but no cycle through it passes a call of 'git.commit_and_push'",
                "the cycle measure ARCHIVE_STEPS of the state 'verify.entry' is admitted only on an archive path that lands, but no cycle through it passes a job state"),
                found(def));
    }

    @Test
    @DisplayName("Cycle measure ARCHIVE_STEPS: a commit call off every cycle through the state does not count (ADR 022)")
    void archiveStepsWithCommitOffTheCycle() {
        var def = unit("epic", """
                state verify.entry
                    phase verify
                    auto test.next-work
                        archive_uncommitted -> verify.land
                        archived -> verify.commit

                state verify.land
                    phase verify
                    job test.build
                        succeeded -> verify.entry
                        failed -> verify.entry
                        cancelled -> verify.entry

                state verify.commit
                    phase verify
                    call git.commit_and_push(push = enabled, attempts = 2, targets = [origin])
                        done -> verify.done
                        stepped_out -> verify.wait

                state verify.wait
                    phase verify
                    job test.build
                        succeeded -> verify.done
                        failed -> verify.done
                        cancelled -> verify.done
                """ + DONE_TERMINAL);

        assertEquals(stepCompletion("verify.entry",
                "the cycle measure ARCHIVE_STEPS of the state 'verify.entry' is admitted only on an archive path that commits, but no cycle through it passes a call of 'git.commit_and_push'"),
                found(def));
    }

    @Test
    @DisplayName("Cycle measure STEP_LIST: a pass that settles two steps is refused at the hub's outcome (ADR 022)")
    void stepListPassSettlingTwoSteps() {
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
                            completes step:other as done
                        skipped -> verify.entry
                            completes step:one as skipped
                            completes step:other as skipped

                state verify.two
                    phase verify
                    auto test.prepare
                        done -> verify.entry
                            completes step:two as done
                        skipped -> verify.entry
                            completes step:two as skipped
                """ + DONE_TERMINAL);

        assertEquals(stepCompletion("verify.entry",
                "the cycle measure STEP_LIST of the state 'verify.entry' requires every pass to settle one step, but a pass that leaves it with the outcome 'first' settles more than one"),
                found(def));
    }

    @Test
    @DisplayName("Cycle measure STEP_LIST: a path that hands its step back or leaves the step list is no pass")
    void stepListPathsThatAreNoPass() {
        var def = unit("plan", """
                state verify.entry
                    phase verify
                    auto test.next-step
                        first -> verify.one
                        second -> verify.fix
                        settled -> verify.done

                state verify.one
                    phase verify
                    auto test.prepare
                        done -> verify.entry
                            completes step:one as done
                        skipped -> plan.return

                state verify.fix
                    phase verify
                    auto test.fix
                        fixed -> verify.entry
                            completes step:fix as done
                        fix_requested -> verify.entry

                state plan.return terminal returned failure
                    phase verify
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

    /**
     * A remediation unit whose call routes {@code stepped_out} to {@code target}, a state with the given id that runs
     * the given {@code auto} primitive, routing every outcome of it onwards.
     */
    private static WorkflowDefinition remediationUnit(String unitId, String target, String auto) {
        var outcomes = TestPrimitives.CHECK_BUDGETS.equals(auto) ? """
                        budget_left -> verify.done
                        exhausted_wait -> verify.stepped-out
                        exhausted -> verify.stepped-out
                """ : """
                        done -> verify.done
                        skipped -> verify.stepped-out
                """;
        return VerifyFixtures.parseSource("remediation.pmwf", """
                workflow %1$s version 1 format 1
                scope subworkflow
                start verify.entry

                state verify.entry
                    phase inherit
                    call test.depth5()
                        done -> verify.done
                        stepped_out -> %2$s

                state %2$s
                    phase inherit
                    auto %3$s
                """.formatted(unitId, target, auto) + outcomes + STEPPED_OUT_TERMINAL + DONE_TERMINAL);
    }

    private static final String NOT_THE_BUDGET_RE_CHECK = "which is neither a terminal of class STEPPED_OUT or"
            + " FAILURE, nor a link state that continues the step-out, nor the budget re-check"
            + " 'remediation-<class>.budget-check' running 'remediation.check-budgets'";

    @Test
    @DisplayName("Rule 6 stepped_out target: a remediation unit may route it to its budget re-check (ADR 024)")
    void steppedOutInRemediationUnit() {
        var budgetCheck = remediationUnit("remediation.inline", "remediation-inline.budget-check",
                TestPrimitives.CHECK_BUDGETS);
        assertEquals(List.of(), violations(budgetCheck));

        var unmarked = remediationUnit("remediation.inline", "verify.recheck", TestPrimitives.PREPARE);
        assertEquals(callContract("verify.entry",
                "the call routes 'stepped_out' to 'verify.recheck', " + NOT_THE_BUDGET_RE_CHECK),
                found(unmarked));
    }

    @Test
    @DisplayName("Rule 6 stepped_out target: the budget re-check is told by its state id and its primitive together")
    void steppedOutToBudgetCheckByIdAndPrimitive() {
        var wrongPrimitive = remediationUnit("remediation.inline", "remediation-inline.budget-check",
                TestPrimitives.PREPARE);
        assertEquals(callContract("verify.entry",
                "the call routes 'stepped_out' to 'remediation-inline.budget-check', " + NOT_THE_BUDGET_RE_CHECK),
                found(wrongPrimitive));

        var wrongId = remediationUnit("remediation.inline", "remediation-inline.recheck",
                TestPrimitives.CHECK_BUDGETS);
        assertEquals(callContract("verify.entry",
                "the call routes 'stepped_out' to 'remediation-inline.recheck', " + NOT_THE_BUDGET_RE_CHECK),
                found(wrongId));
    }

    @Test
    @DisplayName("Rule 6 stepped_out target: the budget re-check is admitted only inside a remediation unit")
    void steppedOutToBudgetCheckOutsideRemediationUnit() {
        var def = remediationUnit("verify.inline", "remediation-inline.budget-check", TestPrimitives.CHECK_BUDGETS);

        assertEquals(callContract("verify.entry",
                "the call routes 'stepped_out' to 'remediation-inline.budget-check', which is neither a terminal of"
                        + " class STEPPED_OUT or FAILURE nor a link state that continues the step-out"),
                found(def));
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
    @DisplayName("Rule 6: a result guard on a link of a state that is no call is refused")
    void resultGuardOnLink() {
        var def = unit("workspace", """
                state verify.entry
                    phase verify
                    link next proceed ()
                        do test.proceed
                            done -> verify.done
                                guard not result.pushed
                """ + DONE_TERMINAL);

        assertEquals(callContract("verify.entry",
                "a result guard is admitted only on the outcome 'done' of a call, but found on the outcome 'done' of the state 'verify.entry', which is no call"),
                found(def));
    }

    @Test
    @DisplayName("Rule 6: a guarded done on a callee without a result schema is refused once per call")
    void resultGuardOnCalleeWithoutSchema() {
        var def = unit("subworkflow", """
                state verify.entry
                    phase inherit
                    call test.depth5()
                        done -> verify.done
                            guard result.pushed
                        done -> verify.done
                            guard not result.pushed
                        stepped_out -> verify.stepped-out
                """ + STEPPED_OUT_TERMINAL + DONE_TERMINAL);

        assertEquals(callContract("verify.entry",
                "the call guards the outcome 'done' on the result of 'test.depth5', which declares no result schema"),
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
