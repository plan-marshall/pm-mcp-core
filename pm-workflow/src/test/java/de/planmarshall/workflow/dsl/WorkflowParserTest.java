/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.dsl;

import static de.planmarshall.workflow.dsl.AstWithoutLocations.NOWHERE;
import static de.planmarshall.workflow.dsl.AstWithoutLocations.strip;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.spi.OutcomeClass;
import de.planmarshall.workflow.ast.CallValue;
import de.planmarshall.workflow.ast.DecisionDeclaration;
import de.planmarshall.workflow.ast.GuardExpr;
import de.planmarshall.workflow.ast.GuardSpec;
import de.planmarshall.workflow.ast.InstanceHeader;
import de.planmarshall.workflow.ast.InstanceInputs;
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
import de.planmarshall.workflow.ast.StepCompletionStatus;
import de.planmarshall.workflow.ast.TerminalOutcome;
import de.planmarshall.workflow.ast.UnitOrigin;
import de.planmarshall.workflow.ast.ValueDeclaration;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.ast.WorkflowScope;

/**
 * The parser of the workflow language (PM-IMPL-5, PM-WF-16): every valid fixture parses to the tree built here, which
 * together cover every construct of the grammar; focused cases cover source locations, declaration order, default
 * link forms and the positional vocabularies.
 */
@DisplayName("WorkflowParser")
class WorkflowParserTest {

    private static final String PLAN_UNIT = "dsl/valid/plan-unit.pmwf";
    private static final String SUBWORKFLOW_UNIT = "dsl/valid/subworkflow-unit.pmwf";
    private static final String SESSION_UNIT = "dsl/valid/session-unit.pmwf";
    private static final String ADJUDICATE_UNIT = "dsl/valid/adjudicate-unit.pmwf";
    private static final String INSTANCE_UNIT = "dsl/valid/instance-unit.pmwf";

    /** The resource name of a unit written inline in a test. */
    private static final String RESOURCE = "inline.pmwf";

    private static final String CONFIG_HASH = "a".repeat(64);
    private static final String RULES_DIGEST = "b".repeat(64);
    private static final String PLAN_FACTS_DIGEST = "d".repeat(64);
    private static final String COMMIT_DIGEST = "e".repeat(64);
    private static final String REMEDIATION_DIGEST = "0".repeat(64);
    private static final String TEMPLATE_DIGEST = "f".repeat(64);

    private static final String INSTANCE_JSON = "{\"composed_at\":\"2026-10-10T12:00:00Z\",\"event\":\"plan\","
            + "\"format_version\":1,\"inputs\":{\"change_type\":\"feature\",\"composition_rules\":{\"digest\":\""
            + RULES_DIGEST + "\",\"revision\":\"r#3\"},\"config_hash\":\"" + CONFIG_HASH
            + "\",\"plan_facts_digest\":\"" + PLAN_FACTS_DIGEST + "\",\"providers_digest\":null,"
            + "\"sub_units\":[{\"digest\":\"" + COMMIT_DIGEST
            + "\",\"file\":null,\"origin\":\"default\",\"unit\":\"git.commit_and_push\"},{\"digest\":\""
            + REMEDIATION_DIGEST + "\",\"origin\":\"default\",\"unit\":\"remediation.ci-failure\"}],"
            + "\"templates\":[{\"digest\":\"" + TEMPLATE_DIGEST
            + "\",\"file\":\".marshall/config/plan.pmwf\",\"origin\":\"project\",\"unit\":\"plan\"}]},"
            + "\"lane\":\"deep\",\"plan_id\":\"demo-plan\",\"policy_calls\":[{\"args\":{\"retry_budget\":"
            + "{\"source\":\"template\",\"value\":2}},\"call_site\":\"plan.run\",\"unit\":\"remediation.ci-failure\"}],"
            + "\"posture\":\"standard\",\"pruned\":[{\"element\":\"step\",\"id\":\"default:sonar-roundtrip\","
            + "\"reason\":\"provider_unconfigured\",\"source_fact\":\"providers.sonar\"}],"
            + "\"steps\":{\"phase_5_verification\":[\"verify:quality-gate\"],"
            + "\"phase_6\":[\"default:push\",\"default:create-pr\"]},\"version\":4,\"waived_gates\":[]}";

    private static final Optional<Integer> UNBOUNDED = Optional.empty();
    private static final GuardExpr IN_WORKTREE = new GuardExpr.InWorktree();
    private static final GuardExpr OPEN_FINDINGS = new GuardExpr.HasOpenFindings();
    private static final GuardExpr ALL_TASKS = new GuardExpr.AllTasksCompleted();

    static String lines(String... lines) {
        return String.join("\n", lines) + "\n";
    }

    /** A unit of the scope whose start state {@code demo.begin} has the given body lines. */
    static String unitWithBody(String scope, String... body) {
        return lines("workflow demo version 1 format 1", "scope " + scope, "start demo.begin", "state demo.begin",
                "    phase setup") + lines(body);
    }

    private static WorkflowDefinition parse(String source) {
        var result = WorkflowParser.parse(RESOURCE, source, UnitOrigin.DEFAULT);
        return assertInstanceOf(ParseResult.Parsed.class, result, () -> "refused: " + result).definition();
    }

    private static StateBehavior startBody(String source) {
        return parse(source).states().get("demo.begin").behavior();
    }

    private static ValueDeclaration declared(CallValue value) {
        return new ValueDeclaration(value, NOWHERE);
    }

    private static OutcomeTransition to(String outcome, String target, StepCompletion... completions) {
        return new OutcomeTransition(outcome, target, Optional.empty(), List.of(completions), NOWHERE);
    }

    private static OutcomeTransition guarded(String outcome, String target, String field, boolean negated) {
        return new OutcomeTransition(outcome, target, Optional.of(new ResultGuard(field, negated)), List.of(),
                NOWHERE);
    }

    private static StepCompletion completes(String stepId, StepCompletionStatus status) {
        return new StepCompletion(stepId, status, NOWHERE);
    }

    private static StateNode state(String id, String phase, StateBehavior behavior) {
        return new StateNode(id, phase, Optional.empty(), Optional.empty(), Optional.empty(), behavior, NOWHERE);
    }

    private static StateNode terminal(String id, String phase, String outcome, OutcomeClass outcomeClass) {
        return new StateNode(id, phase, Optional.of(new TerminalOutcome(outcome, outcomeClass)), Optional.empty(),
                Optional.empty(), new StateBehavior.TerminalState(), NOWHERE);
    }

    private static Map<String, StateNode> states(StateNode... nodes) {
        var states = new LinkedHashMap<String, StateNode>();
        Arrays.stream(nodes).forEach(node -> states.put(node.id(), node));
        return states;
    }

    private static ParamSpec param(String name, ParamType type, boolean optional) {
        return new ParamSpec(name, type, optional, NOWHERE);
    }

    private static Optional<GuardSpec> guard(GuardExpr expr, String... configKeys) {
        return Optional.of(new GuardSpec(expr, List.of(configKeys)));
    }

    private static LinkSpec link(LinkRelation rel, String idPattern, LinkForm form, List<ParamSpec> params,
            Optional<GuardSpec> guard, String primitive, OutcomeTransition... transitions) {
        return new LinkSpec(rel, idPattern, form, params, guard, new LinkAction.ExecutePrimitive(primitive),
                List.of(transitions), NOWHERE);
    }

    private static ParamType.Int intRange(int min, int max) {
        return new ParamType.Int(Optional.of(min), Optional.of(max));
    }

    private static ParamType.EnumOf enumOf(String... values) {
        return new ParamType.EnumOf(List.of(values));
    }

    @Nested
    @DisplayName("Valid fixtures")
    class ValidFixtures {

        static List<String> validFixtures() {
            return DslFixtures.list(DslFixtures.VALID);
        }

        @Test
        @DisplayName("finds the five valid fixtures")
        void fixturesPresent() {
            assertEquals(List.of(ADJUDICATE_UNIT, INSTANCE_UNIT, PLAN_UNIT, SESSION_UNIT, SUBWORKFLOW_UNIT),
                    validFixtures());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("validFixtures")
        @DisplayName("parses every valid fixture with the format of its header, no part versions "
                + "and no derivedFrom for a default unit")
        void parses(String resource) {
            var definition = DslFixtures.parseValid(resource);

            assertAll(
                    () -> assertEquals(new SourceLocation(resource, definition.location().line(), 1),
                            definition.location()),
                    () -> assertEquals(1, definition.formatVersion()),
                    () -> assertEquals(UnitOrigin.DEFAULT, definition.origin()),
                    () -> assertTrue(definition.partVersions().isEmpty()),
                    () -> assertTrue(definition.derivedFrom().isEmpty()));
        }

        @Test
        @DisplayName("plan unit: header, declare, migrate, auto, job, composite_job, call and terminals")
        void planUnit() {
            var declarations = new LinkedHashMap<String, ValueDeclaration>();
            declarations.put("max_rounds", declared(new CallValue.IntValue(3)));
            declarations.put("mode", declared(new CallValue.EnumValue("strict")));
            declarations.put("channels", declared(new CallValue.EnumList(List.of("Chat", "mail"))));
            declarations.put("floor", declared(new CallValue.IntValue(0)));
            var bindings = new LinkedHashMap<String, CallValue>();
            bindings.put("push", new CallValue.EnumValue("enabled"));
            bindings.put("attempts", new CallValue.IntValue(2));
            bindings.put("targets", new CallValue.EnumList(List.of("origin", "Mirror")));
            var begin = new StateNode("init.begin", "init", Optional.empty(), Optional.of("pm/plan/init"),
                    Optional.of("plan.read-facts"),
                    new StateBehavior.AutoStep("plan.prepare", List.of(
                            to("done", "init.dispatch", completes("init:prepare", StepCompletionStatus.DONE)),
                            to("skipped", "init.dispatch", completes("init:prepare", StepCompletionStatus.SKIPPED),
                                    completes("init:probe-2", StepCompletionStatus.WAIVED)),
                            to("failed", "plan.aborted", completes("init:prepare", StepCompletionStatus.FAILED)))),
                    NOWHERE);
            var expected = new WorkflowDefinition("plan", 3, WorkflowScope.PLAN, List.of(), Optional.empty(),
                    Optional.empty(), declarations, Map.of(), Map.of(), 1, UnitOrigin.DEFAULT, Map.of(), "init.begin",
                    List.of(new StateMigration("init.old", "init.begin", NOWHERE),
                            new StateMigration("review.legacy", "review.check", NOWHERE)),
                    states(begin,
                            state("init.dispatch", "init", new StateBehavior.JobStep("build.compile", false,
                                    List.of(to("done", "init.compose"), to("failed", "plan.aborted")))),
                            state("init.compose", "init", new StateBehavior.JobStep("build.compose-all", true,
                                    List.of(to("done", "review.check")))),
                            state("review.check", "review", new StateBehavior.SubWorkflowCall("git.commit_and_push",
                                    bindings, List.of(guarded("done", "review.cap-check", "pushed", false),
                                            guarded("done", "review.check", "pushed", true),
                                            to("stepped_out", "plan.aborted")))),
                            state("review.cap-check", "review", new StateBehavior.SubWorkflowCall("git.no_args",
                                    Map.of(), List.of(to("done", "review.empty-args")))),
                            state("review.empty-args", "review", new StateBehavior.SubWorkflowCall("git.no_args",
                                    Map.of(), List.of(to("done", "completed")))),
                            terminal("completed", "finalize", "merged", OutcomeClass.SUCCESS),
                            terminal("plan.aborted", "aborted", "aborted", OutcomeClass.FAILURE)),
                    NOWHERE);

            assertEquals(expected, strip(DslFixtures.parseValid(PLAN_UNIT)));
        }

        @Test
        @DisplayName("sub-workflow: param types, result schema, phase inherit, task states and consult roles")
        void subWorkflowUnit() {
            var expected = new WorkflowDefinition("git.commit_and_push", 1, WorkflowScope.SUBWORKFLOW,
                    List.of(new ParamDecl("push", enumOf("enabled", "disabled"), NOWHERE),
                            new ParamDecl("attempts", intRange(1, 5), NOWHERE),
                            new ParamDecl("depth", new ParamType.Int(UNBOUNDED, UNBOUNDED), NOWHERE),
                            new ParamDecl("targets", new ParamType.ListOf(enumOf("origin", "Mirror")), NOWHERE)),
                    Optional.of("commit-result"), Optional.empty(), Map.of(), Map.of(), Map.of(), 1,
                    UnitOrigin.DEFAULT, Map.of(), "git.stage", List.of(),
                    states(
                            state("git.stage", "inherit", new StateBehavior.ModelJob("change",
                                    Optional.of("implementer"), Optional.of("git.list-changes"), Optional.empty(),
                                    true, List.of("reviewer", "architect"),
                                    List.of(to("done", "git.review"), to("lost", "git.stepped-out")))),
                            new StateNode("git.review", "inherit", Optional.empty(), Optional.empty(),
                                    Optional.of("git.read-diff"), new StateBehavior.ModelJob("change",
                                            Optional.of("reviewer"), Optional.empty(), Optional.empty(), false,
                                            List.of(), List.of(to("approved", "git.done"),
                                                    to("rejected", "git.stage"))),
                                    NOWHERE),
                            terminal("git.done", "inherit", "done", OutcomeClass.SUCCESS),
                            terminal("git.skipped", "inherit", "skipped", OutcomeClass.SKIP),
                            terminal("git.stepped-out", "inherit", "stepped_out", OutcomeClass.STEPPED_OUT)),
                    NOWHERE);

            assertEquals(expected, strip(DslFixtures.parseValid(SUBWORKFLOW_UNIT)));
        }

        @Test
        @DisplayName("session unit: every relation, form, id pattern, type spec and guard, and question states")
        void sessionUnit() {
            var text = new ParamType.Text(UNBOUNDED, UNBOUNDED);
            var choose = new StateBehavior.InteractiveLinks(List.of(
                    link(LinkRelation.NEXT, "proceed", LinkForm.NOOP, List.of(), Optional.empty(), "setup.proceed",
                            to("done", "setup.wait")),
                    link(LinkRelation.ALT, "detour:<entity>", LinkForm.SUBMIT,
                            List.of(param("note", text, false),
                                    param("size", new ParamType.Text(Optional.of(1), Optional.of(80)), true)),
                            guard(new GuardExpr.Or(IN_WORKTREE,
                                    new GuardExpr.And(OPEN_FINDINGS, new GuardExpr.Not(ALL_TASKS)))),
                            "setup.detour", to("done", "setup.wait")),
                    link(LinkRelation.RETRY, "retry", LinkForm.NOOP,
                            List.of(param("count", new ParamType.Int(UNBOUNDED, UNBOUNDED), false),
                                    param("level", intRange(0, 5), false)),
                            guard(new GuardExpr.And(new GuardExpr.Or(IN_WORKTREE, OPEN_FINDINGS),
                                            new GuardExpr.CustomPredicate("setup.ready")),
                                    "project.review.currency", "derived.push_remote"),
                            "setup.retry", to("done", "setup.choose")),
                    link(LinkRelation.FIX, "fix:lint", LinkForm.NOOP,
                            List.of(param("choice", enumOf("FIX", "SUPPRESS", "accept"), false),
                                    param("body", new ParamType.SchemaRef("fix-plan"), false)),
                            guard(new GuardExpr.Not(new GuardExpr.And(IN_WORKTREE, new GuardExpr.Not(OPEN_FINDINGS)))),
                            "setup.fix", to("done", "setup.choose")),
                    link(LinkRelation.LOOP_BACK, "back", LinkForm.SUBMIT,
                            List.of(param("items", new ParamType.ListOf(text), false),
                                    param("kinds", new ParamType.ListOf(enumOf("a", "B")), false),
                                    param("plans", new ParamType.ListOf(new ParamType.SchemaRef("plan-item")), false),
                                    param("sizes", new ParamType.ListOf(intRange(0, 9)), false)),
                            Optional.empty(), "setup.back", to("done", "setup.choose")),
                    link(LinkRelation.SUBMIT, "hand_in", LinkForm.SUBMIT, List.of(param("summary", text, true)),
                            guard(new GuardExpr.Or(new GuardExpr.Or(IN_WORKTREE, OPEN_FINDINGS), ALL_TASKS)),
                            "setup.submit", to("submitted", "setup.wait")),
                    link(LinkRelation.ESCALATE, "request_waiver", LinkForm.SUBMIT, List.of(), Optional.empty(),
                            "setup.request-waiver", to("filed", "setup.choose")),
                    link(LinkRelation.CONSULT, "ask_architect", LinkForm.SUBMIT, List.of(), Optional.empty(),
                            "setup.ask-architect", to("answered", "setup.choose")),
                    link(LinkRelation.ABORT, "leave", LinkForm.SUBMIT, List.of(),
                            guard(new GuardExpr.And(new GuardExpr.And(IN_WORKTREE, OPEN_FINDINGS), ALL_TASKS)),
                            "setup.leave", to("left", "setup.left"))));
            var expected = new WorkflowDefinition("setup.session", 2, WorkflowScope.WORKSPACE, List.of(),
                    Optional.empty(), Optional.empty(), Map.of(), Map.of(), Map.of(), 1, UnitOrigin.DEFAULT, Map.of(),
                    "setup.choose", List.of(),
                    states(state("setup.choose", "setup", choose),
                            state("setup.wait", "setup", new StateBehavior.QuestionWait("clarification",
                                    List.of(to("answered", "setup.choose"), to("expired", "setup.left")),
                                    List.of(link(LinkRelation.NEXT, "skip_wait", LinkForm.NOOP, List.of(),
                                            Optional.empty(), "setup.skip", to("done", "setup.choose"))))),
                            state("setup.review", "setup", new StateBehavior.QuestionWait("review",
                                    List.of(to("done", "setup.choose")), List.of())),
                            terminal("setup.left", "setup", "left", OutcomeClass.INDETERMINATE),
                            terminal("setup.held", "setup", "held", OutcomeClass.UNSETTLED)),
                    NOWHERE);

            assertEquals(expected, strip(DslFixtures.parseValid(SESSION_UNIT)));
        }

        @Test
        @DisplayName("adjudication unit: decide ask and quorum between declare lines, and a role-each task state")
        void adjudicateUnit() {
            var declarations = new LinkedHashMap<String, ValueDeclaration>();
            declarations.put("max_rounds", declared(new CallValue.IntValue(2)));
            declarations.put("panel", declared(new CallValue.EnumList(List.of("Lead", "second"))));
            var decisions = new LinkedHashMap<String, DecisionDeclaration>();
            decisions.put("interpretation", new DecisionDeclaration.Ask(NOWHERE));
            decisions.put("approach", new DecisionDeclaration.Quorum(List.of("architect", "reviewer"), 75, NOWHERE));
            decisions.put("scope", new DecisionDeclaration.Quorum(List.of("architect"), 60, NOWHERE));
            var expected = new WorkflowDefinition("adjudicate.decisions", 1, WorkflowScope.ENTITY, List.of(),
                    Optional.empty(), Optional.empty(), declarations, decisions, Map.of(), 1, UnitOrigin.DEFAULT,
                    Map.of(), "adjudicate.vote", List.of(),
                    states(state("adjudicate.vote", "inherit", new StateBehavior.ModelJob("decision",
                            Optional.empty(), Optional.empty(), Optional.of("adjudicate.list-roles"), false, List.of(),
                            List.of(to("decided", "adjudicate.settled"), to("lost", "adjudicate.settled")))),
                            terminal("adjudicate.settled", "inherit", "settled", OutcomeClass.SUCCESS)),
                    NOWHERE);

            assertEquals(expected, strip(DslFixtures.parseValid(ADJUDICATE_UNIT)));
        }

        @Test
        @DisplayName("plan instance: the instance header line with its nested inputs and the JSON as read")
        void instanceUnit() {
            var inputs = new InstanceInputs(CONFIG_HASH,
                    List.of(new InstanceInputs.UnitRef("plan", Optional.of(".marshall/config/plan.pmwf"),
                            TEMPLATE_DIGEST, "project")),
                    List.of(new InstanceInputs.UnitRef("git.commit_and_push", Optional.empty(), COMMIT_DIGEST,
                                    "default"),
                            new InstanceInputs.UnitRef("remediation.ci-failure", Optional.empty(), REMEDIATION_DIGEST,
                                    "default")),
                    new InstanceInputs.CompositionRules("r#3", RULES_DIGEST), PLAN_FACTS_DIGEST, Optional.empty(),
                    Optional.of("feature"), Optional.empty());
            var expected = new WorkflowDefinition("plan", 4, WorkflowScope.PLAN, List.of(), Optional.empty(),
                    Optional.of(new InstanceHeader("demo-plan", 4, "plan", inputs, INSTANCE_JSON)), Map.of(),
                    Map.of(), Map.of(), 1, UnitOrigin.DEFAULT, Map.of(), "plan.run", List.of(),
                    states(state("plan.run", "execute",
                            new StateBehavior.AutoStep("plan.execute", List.of(to("done", "plan.end")))),
                            terminal("plan.end", "finalize", "done", OutcomeClass.SUCCESS)),
                    NOWHERE);

            assertEquals(expected, strip(DslFixtures.parseValid(INSTANCE_UNIT)));
        }
    }

    @Nested
    @DisplayName("Source locations and order")
    class LocationsAndOrder {

        @Test
        @DisplayName("records the resource, line and column of the header, migrations, states and transitions")
        void planLocations() {
            var definition = DslFixtures.parseValid(PLAN_UNIT);
            var begin = definition.states().get("init.begin");
            var transition = ((StateBehavior.AutoStep) begin.behavior()).transitions().getFirst();

            assertAll(
                    () -> assertEquals(new SourceLocation(PLAN_UNIT, 4, 1), definition.location()),
                    () -> assertEquals(new SourceLocation(PLAN_UNIT, 11, 1), definition.migrations().get(0).location()),
                    () -> assertEquals(new SourceLocation(PLAN_UNIT, 14, 1), begin.location()),
                    () -> assertEquals(new SourceLocation(PLAN_UNIT, 19, 9), transition.location()),
                    () -> assertEquals(new SourceLocation(PLAN_UNIT, 20, 13),
                            transition.completions().getFirst().location()));
        }

        @Test
        @DisplayName("records the line of every declare and decide line")
        void declareAndDecideLocations() {
            var plan = DslFixtures.parseValid(PLAN_UNIT);
            var adjudicate = DslFixtures.parseValid(ADJUDICATE_UNIT);

            assertAll(
                    () -> assertEquals(new SourceLocation(PLAN_UNIT, 6, 1),
                            plan.declarations().get("max_rounds").location()),
                    () -> assertEquals(new SourceLocation(PLAN_UNIT, 9, 1), plan.declarations().get("floor").location()),
                    () -> assertEquals(new SourceLocation(ADJUDICATE_UNIT, 5, 1),
                            adjudicate.declarations().get("max_rounds").location()),
                    () -> assertEquals(new SourceLocation(ADJUDICATE_UNIT, 6, 1),
                            adjudicate.decisions().get("interpretation").location()),
                    () -> assertEquals(new SourceLocation(ADJUDICATE_UNIT, 7, 1),
                            adjudicate.decisions().get("approach").location()),
                    () -> assertEquals(new SourceLocation(ADJUDICATE_UNIT, 9, 1),
                            adjudicate.declarations().get("panel").location()));
        }

        @Test
        @DisplayName("records the location of a sub-workflow parameter, a link and the name of a link parameter")
        void declarationLocations() {
            var param = DslFixtures.parseValid(SUBWORKFLOW_UNIT).params().getFirst();
            var choose = (StateBehavior.InteractiveLinks) DslFixtures.parseValid(SESSION_UNIT).states()
                    .get("setup.choose").behavior();
            var detour = choose.links().get(1);

            assertAll(
                    () -> assertEquals(new SourceLocation(SUBWORKFLOW_UNIT, 5, 1), param.location()),
                    () -> assertEquals(new SourceLocation(SESSION_UNIT, 13, 5), detour.location()),
                    () -> assertEquals(new SourceLocation(SESSION_UNIT, 13, 40), detour.freeParams().get(0).location()),
                    () -> assertEquals(new SourceLocation(SESSION_UNIT, 13, 52),
                            detour.freeParams().get(1).location()));
        }

        @Test
        @DisplayName("keeps the declaration order of states, declarations, decisions, bindings and sub-units")
        void order() {
            var plan = DslFixtures.parseValid(PLAN_UNIT);
            var call = (StateBehavior.SubWorkflowCall) plan.states().get("review.check").behavior();
            var adjudicate = DslFixtures.parseValid(ADJUDICATE_UNIT);
            var instance = DslFixtures.parseValid(INSTANCE_UNIT).instance().orElseThrow();

            assertAll(
                    () -> assertEquals(List.of("init.begin", "init.dispatch", "init.compose", "review.check",
                                    "review.cap-check", "review.empty-args", "completed", "plan.aborted"),
                            List.copyOf(plan.states().keySet())),
                    () -> assertEquals(List.of("max_rounds", "mode", "channels", "floor"),
                            List.copyOf(plan.declarations().keySet())),
                    () -> assertEquals(List.of("push", "attempts", "targets"),
                            List.copyOf(call.paramBindings().keySet())),
                    () -> assertEquals(List.of("interpretation", "approach", "scope"),
                            List.copyOf(adjudicate.decisions().keySet())),
                    () -> assertEquals(List.of("git.commit_and_push", "remediation.ci-failure"),
                            instance.inputs().subUnits().stream().map(InstanceInputs.UnitRef::unit).toList()));
        }
    }

    @Nested
    @DisplayName("Keywords of the enumerations")
    class EnumKeywords {

        /** The keyword of a constant as the printer writes it. */
        private static String keyword(Enum<?> constant) {
            return constant.name().toLowerCase(Locale.ROOT);
        }

        @ParameterizedTest(name = "scope {0}")
        @EnumSource(WorkflowScope.class)
        @DisplayName("reads every scope from its keyword")
        void scopes(WorkflowScope scope) {
            var source = unitWithBody(keyword(scope), "auto demo.run", "done -> demo.begin");

            assertEquals(scope, parse(source).scope());
        }

        @ParameterizedTest(name = "relation {0}")
        @EnumSource(LinkRelation.class)
        @DisplayName("reads every link relation from its keyword")
        void linkRelations(LinkRelation relation) {
            var source = unitWithBody("workspace", "link " + keyword(relation) + " go ()", "do demo.go",
                    "done -> demo.begin");

            var links = (StateBehavior.InteractiveLinks) startBody(source);

            assertEquals(relation, links.links().getFirst().rel());
        }

        @ParameterizedTest(name = "form {0}")
        @EnumSource(LinkForm.class)
        @DisplayName("reads every link form from its keyword")
        void linkForms(LinkForm form) {
            var source = unitWithBody("workspace", "link next go [" + keyword(form) + "] ()", "do demo.go",
                    "done -> demo.begin");

            var links = (StateBehavior.InteractiveLinks) startBody(source);

            assertEquals(form, links.links().getFirst().form());
        }

        @ParameterizedTest(name = "completion {0}")
        @EnumSource(StepCompletionStatus.class)
        @DisplayName("reads every step completion from its keyword")
        void stepCompletions(StepCompletionStatus status) {
            var source = unitWithBody("plan", "auto demo.run", "done -> demo.begin",
                    "completes init:x as " + keyword(status));

            var auto = (StateBehavior.AutoStep) startBody(source);

            assertEquals(status, auto.transitions().getFirst().completions().getFirst().status());
        }

        @ParameterizedTest(name = "outcome class {0}")
        @EnumSource(OutcomeClass.class)
        @DisplayName("reads every outcome class from its keyword")
        void outcomeClasses(OutcomeClass outcomeClass) {
            var source = lines("workflow demo version 1 format 1", "scope plan", "start demo.end",
                    "state demo.end terminal done " + keyword(outcomeClass), "phase init");

            var terminal = parse(source).states().get("demo.end").terminalOutcome().orElseThrow();

            assertEquals(outcomeClass, terminal.outcomeClass());
        }
    }

    @Nested
    @DisplayName("Focused constructs")
    class FocusedConstructs {

        @ParameterizedTest(name = "{0}")
        @EnumSource(LinkRelation.class)
        @DisplayName("gives a link without a form the default form of its relation")
        void defaultLinkForm(LinkRelation relation) {
            var name = relation.name().toLowerCase(Locale.ROOT);
            var source = unitWithBody("workspace", "link " + name + " go ()", "do demo.go", "done -> demo.begin");

            var links = (StateBehavior.InteractiveLinks) startBody(source);

            assertAll(
                    () -> assertEquals(relation, links.links().getFirst().rel()),
                    () -> assertEquals(relation.defaultForm(), links.links().getFirst().form()));
        }

        @ParameterizedTest(name = "question {0}")
        @ValueSource(strings = {"init", "clarification", "review", "task", "setup", "ask"})
        @DisplayName("reads every question class")
        void questionClasses(String questionClass) {
            var source = unitWithBody("plan", "question " + questionClass, "answered -> demo.begin");

            var question = (StateBehavior.QuestionWait) startBody(source);

            assertEquals(questionClass, question.questionClass());
        }

        @ParameterizedTest(name = "decide {0}")
        @ValueSource(strings = {"interpretation", "approach", "granularity", "placement", "test_strategy", "scope",
                "product_behaviour", "assurance", "external_fact"})
        @DisplayName("reads every decision kind")
        void decisionKinds(String kind) {
            var source = lines("workflow demo version 1 format 1", "scope entity", "decide " + kind + " -> ask",
                    "start demo.end", "state demo.end terminal done success", "phase inherit");

            assertEquals(Map.of(kind, new DecisionDeclaration.Ask(new SourceLocation(RESOURCE, 3, 1))),
                    parse(source).decisions());
        }

        @Test
        @DisplayName("passes the origin of the caller through")
        void origin() {
            var source = DslFixtures.read(PLAN_UNIT);

            var result = WorkflowParser.parse(PLAN_UNIT, source, UnitOrigin.PROJECT);

            assertEquals(UnitOrigin.PROJECT, assertInstanceOf(ParseResult.Parsed.class, result).definition().origin());
        }

        @Test
        @DisplayName("records the derived_from revision of a project unit under the unit's id")
        void derivedFromOfProjectUnit() {
            var source = lines("workflow demo version 4 format 1 derived_from 3", "scope plan", "start demo.end",
                    "state demo.end terminal done success", "phase setup");

            var result = WorkflowParser.parse("inline.pmwf", source, UnitOrigin.PROJECT);

            assertEquals(Map.of("demo", 3),
                    assertInstanceOf(ParseResult.Parsed.class, result).definition().derivedFrom());
        }

        @Test
        @DisplayName("records no derived_from for a project unit without the clause")
        void noDerivedFromOfProjectUnit() {
            var source = lines("workflow demo version 4 format 1", "scope plan", "start demo.end",
                    "state demo.end terminal done success", "phase setup");

            var result = WorkflowParser.parse("inline.pmwf", source, UnitOrigin.PROJECT);

            assertEquals(Map.of(), assertInstanceOf(ParseResult.Parsed.class, result).definition().derivedFrom());
        }

        @Test
        @DisplayName("admits positional vocabulary as action names and as identifier segments")
        void positionalVocabulary() {
            var source = unitWithBody("workspace", "link retry retry [noop] ()", "do plan.allocate-remediation-tasks",
                    "done -> plan.success", "link escalate state [submit] ()", "do demo.waive", "filed -> done.epic");

            var links = (StateBehavior.InteractiveLinks) startBody(source);

            assertAll(
                    () -> assertEquals("retry", links.links().get(0).idPattern()),
                    () -> assertEquals(new LinkAction.ExecutePrimitive("plan.allocate-remediation-tasks"),
                            links.links().get(0).action()),
                    () -> assertEquals("plan.success", links.links().get(0).transitions().getFirst().targetStateId()),
                    () -> assertEquals("state", links.links().get(1).idPattern()));
        }

        @Test
        @DisplayName("reads a primitive of a job whose name starts with llm as a model job only without a dot")
        void modelJobOrPrimitive() {
            var source = unitWithBody("plan", "job llm task role writer", "done -> demo.begin");

            var job = assertInstanceOf(StateBehavior.ModelJob.class, startBody(source));

            assertAll(
                    () -> assertEquals("task", job.entityKind()),
                    () -> assertEquals(Optional.of("writer"), job.roleName()),
                    () -> assertEquals(List.of(), job.consultRoles()));
        }

        @Test
        @DisplayName("accepts a guard nested as deep as the bound")
        void guardAtDepthBound() {
            var source = unitWithBody("workspace", "link next go ()",
                    "guard " + "(".repeat(32) + "in_worktree" + ")".repeat(32), "do demo.go", "done -> demo.begin");

            var links = (StateBehavior.InteractiveLinks) startBody(source);

            assertEquals(IN_WORKTREE, links.links().getFirst().guard().orElseThrow().expr());
        }

        @Test
        @DisplayName("reads an arrow written without spaces and words that differ only by a hyphen")
        void compactArrow() {
            var source = unitWithBody("plan", "auto demo.run-fast", "done->demo.begin");

            var auto = (StateBehavior.AutoStep) startBody(source);

            assertAll(
                    () -> assertEquals("demo.run-fast", auto.primitiveId()),
                    () -> assertEquals("demo.begin", auto.transitions().getFirst().targetStateId()));
        }

        @Test
        @DisplayName("rejects null arguments before reading anything")
        void nullArguments() {
            assertAll(
                    () -> assertThrows(NullPointerException.class,
                            () -> WorkflowParser.parse(null, "", UnitOrigin.DEFAULT)),
                    () -> assertThrows(NullPointerException.class,
                            () -> WorkflowParser.parse("x.pmwf", null, UnitOrigin.DEFAULT)),
                    () -> assertThrows(NullPointerException.class, () -> WorkflowParser.parse("x.pmwf", "", null)));
        }
    }
}
