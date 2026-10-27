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

import java.time.LocalDate;
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
import de.planmarshall.workflow.ast.UnitFrontmatter;
import de.planmarshall.workflow.ast.UnitFrontmatter.InterfaceElement;
import de.planmarshall.workflow.ast.UnitOrigin;
import de.planmarshall.workflow.ast.UnitType;
import de.planmarshall.workflow.ast.ValueDeclaration;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.ast.WorkflowScope;

/**
 * The parser of the workflow language (PM-IMPL-5, PM-WF-16): every valid fixture parses to the tree built here, its
 * frontmatter included, which together cover every construct of the grammar; focused cases cover source locations,
 * declaration order, default link forms, the positional vocabularies, the entry bindings and the call entry. A unit
 * with declared entries and a start line, or one binding an undeclared entry, parses: the verifier refuses it. Every
 * inline unit opens with its frontmatter of {@link #FRONTMATTER_LINES} lines.
 */
@DisplayName("WorkflowParser")
class WorkflowParserTest {

    private static final String PLAN_UNIT = "dsl/valid/plan-unit.pmwf";
    private static final String SUBWORKFLOW_UNIT = "dsl/valid/subworkflow-unit.pmwf";
    private static final String SESSION_UNIT = "dsl/valid/session-unit.pmwf";
    private static final String ADJUDICATE_UNIT = "dsl/valid/adjudicate-unit.pmwf";
    private static final String ASK_FRESH_UNIT = "dsl/valid/ask-fresh-unit.pmwf";
    private static final String INSTANCE_UNIT = "dsl/valid/instance-unit.pmwf";
    private static final String THREE_ENTRIES_UNIT = "dsl/valid/three-entries-unit.pmwf";

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

    /** The modification date every fixture of the parser tests declares. */
    private static final LocalDate FIXTURE_DATE = LocalDate.of(2026, 1, 15);

    private static final Optional<Integer> UNBOUNDED = Optional.empty();
    private static final GuardExpr IN_WORKTREE = new GuardExpr.InWorktree();
    private static final GuardExpr OPEN_FINDINGS = new GuardExpr.HasOpenFindings();
    private static final GuardExpr ALL_TASKS = new GuardExpr.AllTasksCompleted();

    /** The number of lines of the frontmatter {@link #frontmatter(UnitType)} writes, both {@code ---} lines included. */
    static final int FRONTMATTER_LINES = 9;

    static String lines(String... lines) {
        return String.join("\n", lines) + "\n";
    }

    /**
     * The frontmatter an inline unit of the parser tests opens with: a default unit of the given type, without entries
     * and exits, in {@link #FRONTMATTER_LINES} lines.
     */
    static String frontmatter(UnitType type) {
        return lines("---", "name: Demo unit", "description: A unit written for the parser tests.",
                "type: " + type.wireName(), "icon: workflow.default", "modification_date: 2026-01-15", "version: 0.1.0",
                "author: plan-marshall", "---");
    }

    /** The type a unit of the scope declares in its frontmatter, as the Unit Types table assigns it. */
    static UnitType typeOf(String scope) {
        return switch (scope) {
            case "plan", "epic" -> UnitType.WORKFLOW;
            case "subworkflow" -> UnitType.SUBWORKFLOW;
            default -> UnitType.SYSTEM;
        };
    }

    /** A unit of the plan scope: the frontmatter of a workflow, then the given lines as its body. */
    static String unit(String... lines) {
        return frontmatter(UnitType.WORKFLOW) + lines(lines);
    }

    /**
     * A project unit of the plan scope: the frontmatter of a workflow a person wrote, then the given lines as its body,
     * which starts after {@link #FRONTMATTER_LINES} lines as well.
     */
    static String projectUnit(String... lines) {
        return lines("---", "name: Demo unit", "description: A project unit written for the parser tests.",
                "type: workflow", "icon: workflow.default", "modification_date: 2026-01-15", "version: 0.1.0",
                "author: Erika Mustermann", "---")
                + lines(lines);
    }

    /** A unit of the scope whose start state {@code demo.begin} has the given body lines. */
    static String unitWithBody(String scope, String... body) {
        return frontmatter(typeOf(scope)) + lines("workflow demo version 1 format 1", "scope " + scope,
                "start demo.begin", "state demo.begin", "    phase setup") + lines(body);
    }

    private static WorkflowDefinition parse(String source) {
        var result = WorkflowParser.parse(RESOURCE, source, UnitOrigin.DEFAULT);
        return assertInstanceOf(ParseResult.Parsed.class, result, () -> "refused: " + result).definition();
    }

    private static StateBehavior startBody(String source) {
        return parse(source).states().get("demo.begin").behavior();
    }

    /** The frontmatter of a valid fixture without entries and exits, as stripped of its location. */
    private static UnitFrontmatter frontmatter(String name, String description, UnitType type, String icon) {
        return frontmatter(name, description, type, icon, List.of(), List.of());
    }

    /** The frontmatter of a valid fixture, as stripped of its location. */
    private static UnitFrontmatter frontmatter(String name, String description, UnitType type, String icon,
            List<InterfaceElement> entries, List<InterfaceElement> exits) {
        return new UnitFrontmatter(name, description, type, icon, entries, exits, FIXTURE_DATE, "0.1.0",
                "plan-marshall", Optional.empty(), NOWHERE);
    }

    private static InterfaceElement element(String name) {
        return new InterfaceElement(name, Optional.empty());
    }

    private static InterfaceElement element(String name, String description) {
        return new InterfaceElement(name, Optional.of(description));
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
        assertPhaseName(phase);
        return new StateNode(id, Optional.empty(), Optional.empty(), Optional.empty(), behavior, NOWHERE);
    }

    private static StateNode terminal(String id, String phase, String outcome, OutcomeClass outcomeClass) {
        assertPhaseName(phase);
        return new StateNode(id, Optional.of(new TerminalOutcome(outcome, outcomeClass)), Optional.empty(),
                Optional.empty(), new StateBehavior.TerminalState(), NOWHERE);
    }

    /**
     * The fixtures still name the phase a state belonged to; the phase of a state is derived from the unit that declares
     * it now (PM-WF-16), so the name is checked and not stored.
     */
    private static void assertPhaseName(String phase) {
        if (phase == null || phase.isBlank()) {
            throw new IllegalArgumentException("a fixture state names no phase");
        }
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
        @DisplayName("finds the seven valid fixtures")
        void fixturesPresent() {
            assertEquals(List.of(ADJUDICATE_UNIT, ASK_FRESH_UNIT, INSTANCE_UNIT, PLAN_UNIT, SESSION_UNIT,
                    SUBWORKFLOW_UNIT, THREE_ENTRIES_UNIT), validFixtures());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("validFixtures")
        @DisplayName("parses every valid fixture with the format of its header and no derivedFrom for a default unit")
        void parses(String resource) {
            var definition = DslFixtures.parseValid(resource);

            assertAll(
                    () -> assertEquals(new SourceLocation(resource, definition.location().line(), 1),
                            definition.location()),
                    () -> assertEquals(1, definition.formatVersion()),
                    () -> assertEquals(UnitOrigin.DEFAULT, definition.origin()),
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
            var begin = new StateNode("init.begin", Optional.empty(), Optional.of("pm/plan/init"),
                    Optional.of("plan.read-facts"),
                    new StateBehavior.AutoStep("plan.prepare", List.of(
                            to("done", "init.dispatch", completes("init:prepare", StepCompletionStatus.DONE)),
                            to("skipped", "init.dispatch", completes("init:prepare", StepCompletionStatus.SKIPPED),
                                    completes("init:probe-2", StepCompletionStatus.WAIVED)),
                            to("failed", "plan.aborted", completes("init:prepare", StepCompletionStatus.FAILED)))),
                    NOWHERE);
            var expected = new WorkflowDefinition(
                    frontmatter("Parser plan unit", "A plan unit written for the parser tests.", UnitType.WORKFLOW,
                            "unit.plan"),
                    "plan", 3, WorkflowScope.PLAN, List.of(), Optional.empty(), Optional.empty(), declarations,
                    Map.of(), 1, UnitOrigin.DEFAULT, Map.of(), Optional.of("init.begin"), Map.of(),
                    List.of(new StateMigration("init.old", "init.begin", NOWHERE),
                            new StateMigration("review.legacy", "review.check", NOWHERE)),
                    states(begin,
                            state("init.dispatch", "init", new StateBehavior.JobStep("build.compile", false,
                                    List.of(to("done", "init.compose"), to("failed", "plan.aborted")))),
                            state("init.compose", "init", new StateBehavior.JobStep("build.compose-all", true,
                                    List.of(to("done", "review.check")))),
                            state("review.check", "review", new StateBehavior.SubWorkflowCall("git.commit_and_push",
                                    Optional.empty(), bindings,
                                    List.of(guarded("done", "review.cap-check", "pushed", false),
                                            guarded("done", "review.check", "pushed", true),
                                            to("stepped_out", "plan.aborted")))),
                            state("review.cap-check", "review", new StateBehavior.SubWorkflowCall("git.no_args",
                                    Optional.empty(), Map.of(), List.of(to("done", "review.empty-args")))),
                            state("review.empty-args", "review", new StateBehavior.SubWorkflowCall("git.no_args",
                                    Optional.empty(), Map.of(), List.of(to("done", "completed")))),
                            terminal("completed", "finalize", "merged", OutcomeClass.SUCCESS),
                            terminal("plan.aborted", "aborted", "aborted", OutcomeClass.FAILURE)),
                    NOWHERE);

            assertEquals(expected, strip(DslFixtures.parseValid(PLAN_UNIT)));
        }

        @Test
        @DisplayName("sub-workflow: param types, result schema, phase inherit, task states and consult roles")
        void subWorkflowUnit() {
            var frontmatter = frontmatter("Commit and push", "A sub-workflow written for the parser tests.",
                    UnitType.SUBWORKFLOW, "git.commit", List.of(element("stage", "The change is ready to be staged.")),
                    List.of(element("done", "The change is committed and pushed."), element("skipped")));
            var expected = new WorkflowDefinition(frontmatter, "git.commit_and_push", 1, WorkflowScope.SUBWORKFLOW,
                    List.of(new ParamDecl("push", enumOf("enabled", "disabled"), NOWHERE),
                            new ParamDecl("attempts", intRange(1, 5), NOWHERE),
                            new ParamDecl("depth", new ParamType.Int(UNBOUNDED, UNBOUNDED), NOWHERE),
                            new ParamDecl("targets", new ParamType.ListOf(enumOf("origin", "Mirror")), NOWHERE)),
                    Optional.of("commit-result"), Optional.empty(), Map.of(), Map.of(), 1,
                    UnitOrigin.DEFAULT, Map.of(), Optional.empty(),
                    Map.of("stage", new WorkflowDefinition.EntryBinding("git.stage", NOWHERE)), List.of(),
                    states(
                            state("git.stage", "inherit", new StateBehavior.ModelJob("change",
                                    Optional.of("implementer"), Optional.of("git.list-changes"), Optional.empty(),
                                    StateBehavior.AskForm.ASK, List.of("reviewer", "architect"),
                                    List.of(to("done", "git.review"), to("lost", "git.stepped-out")))),
                            new StateNode("git.review", Optional.empty(), Optional.empty(),
                                    Optional.of("git.read-diff"), new StateBehavior.ModelJob("change",
                                            Optional.of("reviewer"), Optional.empty(), Optional.empty(),
                                            StateBehavior.AskForm.NONE,
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
            var expected = new WorkflowDefinition(frontmatter("Parser session unit",
                    "A session-driven workspace unit written for the parser tests.", UnitType.SYSTEM, "unit.start"),
                    "setup.session",
                    2, WorkflowScope.WORKSPACE, List.of(), Optional.empty(), Optional.empty(), Map.of(), Map.of(),
                    1, UnitOrigin.DEFAULT, Map.of(), Optional.of("setup.choose"), Map.of(), List.of(),
                    states(state("setup.choose", "setup", choose),
                            state("setup.wait", "setup", new StateBehavior.QuestionWait("clarification",
                                    List.of(to("answered", "setup.choose")))),
                            state("setup.review", "setup", new StateBehavior.QuestionWait("review",
                                    List.of(to("approve", "setup.choose"), to("revise", "setup.choose")))),
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
            var expected = new WorkflowDefinition(frontmatter("Parser adjudication unit",
                    "An adjudication unit written for the parser tests.", UnitType.SYSTEM, "flow.adjudicate"),
                    "adjudicate.decisions", 1,
                    WorkflowScope.ENTITY, List.of(), Optional.empty(), Optional.empty(), declarations, decisions,
                    1, UnitOrigin.DEFAULT, Map.of(), Optional.of("adjudicate.vote"), Map.of(), List.of(),
                    states(state("adjudicate.vote", "inherit", new StateBehavior.ModelJob("decision",
                            Optional.empty(), Optional.empty(), Optional.of("adjudicate.list-roles"),
                            StateBehavior.AskForm.NONE, List.of(),
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
            var expected = new WorkflowDefinition(frontmatter("Parser plan instance",
                    "A plan instance written for the parser tests.", UnitType.INSTANCE, "unit.plan"), "plan", 4,
                    WorkflowScope.PLAN,
                    List.of(), Optional.empty(),
                    Optional.of(new InstanceHeader("demo-plan", 4, "plan", inputs, INSTANCE_JSON)), Map.of(),
                    Map.of(), 1, UnitOrigin.DEFAULT, Map.of(), Optional.of("plan.run"), Map.of(), List.of(),
                    states(state("plan.run", "execute",
                            new StateBehavior.AutoStep("plan.execute", List.of(to("done", "plan.end")))),
                            terminal("plan.end", "finalize", "done", OutcomeClass.SUCCESS)),
                    NOWHERE);

            assertEquals(expected, strip(DslFixtures.parseValid(INSTANCE_UNIT)));
        }

        @Test
        @DisplayName("three entries: the frontmatter with its described entries, quoted values and signature, the "
                + "entry bindings and a call through a named entry")
        void threeEntriesUnit() {
            var frontmatter = new UnitFrontmatter("Review and repair",
                    "A sub-workflow written for the parser tests: three entries, each with its description.",
                    UnitType.SUBWORKFLOW, "remediation.review",
                    List.of(element("review", "The change is ready for its review."),
                            element("repair", "The review rejected the change; its findings are recorded as "
                                    + "\"open\"."),
                            element("recheck", "The repair is done and the change is reviewed again.")),
                    List.of(element("done", "The change is approved."), element("failed")), FIXTURE_DATE, "0.1.0",
                    "plan-marshall", Optional.of("c2lnbmF0dXJlLW9mLXRoZS1maWxl"), NOWHERE);
            var entryBindings = new LinkedHashMap<String, WorkflowDefinition.EntryBinding>();
            entryBindings.put("review", new WorkflowDefinition.EntryBinding("review.check", NOWHERE));
            entryBindings.put("repair", new WorkflowDefinition.EntryBinding("review.fix", NOWHERE));
            entryBindings.put("recheck", new WorkflowDefinition.EntryBinding("review.check", NOWHERE));
            var expected = new WorkflowDefinition(frontmatter, "review.repair", 1, WorkflowScope.SUBWORKFLOW,
                    List.of(), Optional.empty(), Optional.empty(), Map.of(), Map.of(), 1,
                    UnitOrigin.DEFAULT, Map.of(), Optional.empty(), entryBindings, List.of(),
                    states(state("review.check", "inherit", new StateBehavior.ModelJob("change",
                            Optional.of("reviewer"), Optional.empty(), Optional.empty(),
                            StateBehavior.AskForm.NONE, List.of(),
                            List.of(to("approved", "review.done"), to("rejected", "review.fix"),
                                    to("lost", "review.failed")))),
                            state("review.fix", "inherit", new StateBehavior.SubWorkflowCall("git.commit_and_push",
                                    Optional.of("stage"), Map.of("push", new CallValue.EnumValue("enabled")),
                                    List.of(to("done", "review.check"), to("stepped_out", "review.stepped-out")))),
                            terminal("review.done", "inherit", "done", OutcomeClass.SUCCESS),
                            terminal("review.failed", "inherit", "failed", OutcomeClass.FAILURE),
                            terminal("review.stepped-out", "inherit", "stepped_out", OutcomeClass.STEPPED_OUT)),
                    NOWHERE);

            var parsed = DslFixtures.parseValid(THREE_ENTRIES_UNIT);

            assertAll(
                    () -> assertEquals(expected, strip(parsed)),
                    () -> assertEquals(List.of("review", "repair", "recheck"),
                            parsed.frontmatter().entries().stream().map(InterfaceElement::name).toList()),
                    () -> assertEquals(List.of("review.check", "review.fix"), parsed.startingStates()));
        }
    }

    @Nested
    @DisplayName("Frontmatter and entries")
    class FrontmatterAndEntries {

        @Test
        @DisplayName("reads the frontmatter into the tree, located at its opening line, and the body after it")
        void frontmatterInTree() {
            var definition = DslFixtures.parseValid(PLAN_UNIT);

            assertAll(
                    () -> assertEquals("Parser plan unit", definition.frontmatter().name()),
                    () -> assertEquals(UnitType.WORKFLOW, definition.frontmatter().type()),
                    () -> assertEquals(FIXTURE_DATE, definition.frontmatter().modificationDate()),
                    () -> assertEquals(new SourceLocation(PLAN_UNIT, 1, 1), definition.frontmatter().location()),
                    () -> assertEquals(new SourceLocation(PLAN_UNIT, FRONTMATTER_LINES + 4, 1),
                            definition.location()));
        }

        @Test
        @DisplayName("records the location of every entry binding, in source order")
        void entryBindingLocations() {
            var definition = DslFixtures.parseValid(THREE_ENTRIES_UNIT);

            assertEquals(List.of(new SourceLocation(THREE_ENTRIES_UNIT, 24, 1),
                            new SourceLocation(THREE_ENTRIES_UNIT, 25, 1), new SourceLocation(THREE_ENTRIES_UNIT, 26, 1)),
                    definition.entryBindings().values().stream().map(WorkflowDefinition.EntryBinding::location)
                            .toList());
        }

        @Test
        @DisplayName("reads a call without an entry as empty and a call through a named entry with its name")
        void callEntry() {
            var plain = (StateBehavior.SubWorkflowCall) startBody(unitWithBody("plan", "call demo.sub",
                    "done -> demo.begin"));
            var named = (StateBehavior.SubWorkflowCall) startBody(unitWithBody("plan", "call demo.sub entry review()",
                    "done -> demo.begin"));

            assertAll(
                    () -> assertEquals(Optional.empty(), plain.entry()),
                    () -> assertEquals(Optional.of("review"), named.entry()),
                    () -> assertEquals(Map.of(), named.paramBindings()));
        }

        @Test
        @DisplayName("keeps 'entry' a positional keyword: a state segment named entry is a state identifier")
        void entryIsPositional() {
            var source = unitWithBody("plan", "auto demo.run", "done -> setup.entry") + lines("state setup.entry",
                    "phase setup", "auto demo.run", "done -> demo.begin");

            var definition = parse(source);

            assertTrue(definition.states().containsKey("setup.entry"));
        }

        @Test
        @DisplayName("parses a unit with declared entries and a start line without refusal; the verifier refuses it")
        void declaredEntriesWithStart() {
            var source = frontmatterWithEntry("review") + lines("workflow demo version 1 format 1",
                    "scope subworkflow", "start demo.end", "state demo.end terminal done success", "phase inherit");

            var definition = parse(source);

            assertAll(
                    () -> assertEquals(Optional.of("demo.end"), definition.startState()),
                    () -> assertEquals(Map.of(), definition.entryBindings()),
                    () -> assertEquals(List.of("review"),
                            definition.frontmatter().entries().stream().map(InterfaceElement::name).toList()));
        }

        @Test
        @DisplayName("parses a unit that binds an undeclared entry without refusal; the verifier refuses it")
        void undeclaredEntryBound() {
            var source = frontmatterWithEntry("review") + lines("workflow demo version 1 format 1",
                    "scope subworkflow", "entry repair -> demo.end", "state demo.end terminal done success",
                    "phase inherit");

            var definition = parse(source);

            assertAll(
                    () -> assertEquals(Optional.empty(), definition.startState()),
                    () -> assertEquals(List.of("repair"), List.copyOf(definition.entryBindings().keySet())),
                    () -> assertEquals(List.of("demo.end"), definition.startingStates()));
        }

        /** The frontmatter of a sub-workflow that declares one entry and the exit {@code done}. */
        private static String frontmatterWithEntry(String entry) {
            return lines("---", "name: Demo unit", "description: A unit written for the parser tests.",
                    "type: subworkflow", "icon: workflow.default", "entry: " + entry, "exit: done",
                    "modification_date: 2026-01-15", "version: 0.1.0", "author: plan-marshall", "---");
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
                    () -> assertEquals(new SourceLocation(PLAN_UNIT, 13, 1), definition.location()),
                    () -> assertEquals(new SourceLocation(PLAN_UNIT, 20, 1), definition.migrations().get(0).location()),
                    () -> assertEquals(new SourceLocation(PLAN_UNIT, 23, 1), begin.location()),
                    () -> assertEquals(new SourceLocation(PLAN_UNIT, 28, 9), transition.location()),
                    () -> assertEquals(new SourceLocation(PLAN_UNIT, 29, 13),
                            transition.completions().getFirst().location()));
        }

        @Test
        @DisplayName("records the line of every declare and decide line")
        void declareAndDecideLocations() {
            var plan = DslFixtures.parseValid(PLAN_UNIT);
            var adjudicate = DslFixtures.parseValid(ADJUDICATE_UNIT);

            assertAll(
                    () -> assertEquals(new SourceLocation(PLAN_UNIT, 15, 1),
                            plan.declarations().get("max_rounds").location()),
                    () -> assertEquals(new SourceLocation(PLAN_UNIT, 18, 1), plan.declarations().get("floor").location()),
                    () -> assertEquals(new SourceLocation(ADJUDICATE_UNIT, 14, 1),
                            adjudicate.declarations().get("max_rounds").location()),
                    () -> assertEquals(new SourceLocation(ADJUDICATE_UNIT, 15, 1),
                            adjudicate.decisions().get("interpretation").location()),
                    () -> assertEquals(new SourceLocation(ADJUDICATE_UNIT, 16, 1),
                            adjudicate.decisions().get("approach").location()),
                    () -> assertEquals(new SourceLocation(ADJUDICATE_UNIT, 18, 1),
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
                    () -> assertEquals(new SourceLocation(SUBWORKFLOW_UNIT, 19, 1), param.location()),
                    () -> assertEquals(new SourceLocation(SESSION_UNIT, 22, 5), detour.location()),
                    () -> assertEquals(new SourceLocation(SESSION_UNIT, 22, 40), detour.freeParams().get(0).location()),
                    () -> assertEquals(new SourceLocation(SESSION_UNIT, 22, 52),
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
            var source = unit("workflow demo version 1 format 1", "scope plan", "start demo.end",
                    "state demo.end terminal done " + keyword(outcomeClass), "phase init");

            var terminal = parse(source).states().get("demo.end").terminalOutcome().orElseThrow();

            assertEquals(outcomeClass, terminal.outcomeClass());
        }
    }

    @Nested
    @DisplayName("Empty list")
    class EmptyList {

        private static final CallValue EMPTY = new CallValue.EnumList(List.of());

        /** A plan unit declaring the list and binding it as the argument of a call. */
        private static String withList(String list) {
            return frontmatter(UnitType.WORKFLOW) + lines("workflow demo version 1 format 1", "scope plan",
                    "declare wait_after = " + list, "start demo.begin", "state demo.begin", "    phase setup",
                    "    call demo.sub(actions = " + list + ")", "        done -> demo.end",
                    "state demo.end terminal done success", "    phase setup");
        }

        private static CallValue declared(WorkflowDefinition definition) {
            return definition.declarations().get("wait_after").value();
        }

        private static CallValue bound(WorkflowDefinition definition) {
            return ((StateBehavior.SubWorkflowCall) definition.states().get("demo.begin").behavior()).paramBindings()
                    .get("actions");
        }

        @Test
        @DisplayName("parses [] as the list without an element, declared and bound")
        void parsesEmpty() {
            var definition = parse(withList("[]"));

            assertAll(
                    () -> assertEquals(EMPTY, declared(definition)),
                    () -> assertEquals(EMPTY, bound(definition)));
        }

        @Test
        @DisplayName("parses [] with blanks inside the brackets as the empty list")
        void parsesEmptyWithBlank() {
            assertEquals(EMPTY, declared(parse(withList("[ ]"))));
        }

        @Test
        @DisplayName("still parses a list with elements, in source order")
        void parsesNonEmpty() {
            var expected = new CallValue.EnumList(List.of("outline", "execute"));
            var definition = parse(withList("[outline, execute]"));

            assertAll(
                    () -> assertEquals(expected, declared(definition)),
                    () -> assertEquals(expected, bound(definition)));
        }

        @Test
        @DisplayName("prints the empty list as [] and reads the printed text back equal")
        void printerRoundTrip() {
            var definition = parse(withList("[]"));

            var printed = WorkflowPrinter.print(definition);
            var reread = parse(printed);

            assertAll(
                    () -> assertTrue(printed.contains("declare wait_after = []"), printed),
                    () -> assertEquals(EMPTY, declared(reread)),
                    () -> assertEquals(EMPTY, bound(reread)));
        }

        @Test
        @DisplayName("parses the empty list of a text with CRLF line ends like that of a text with LF")
        void crlfTwin() {
            var definition = parse(withList("[]").replace("\n", "\r\n"));

            assertAll(
                    () -> assertEquals(EMPTY, declared(definition)),
                    () -> assertEquals(EMPTY, bound(definition)));
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
            var source = frontmatter(UnitType.SYSTEM) + lines("workflow demo version 1 format 1", "scope entity",
                    "decide " + kind + " -> ask", "start demo.end", "state demo.end terminal done success",
                    "phase inherit");

            assertEquals(Map.of(kind,
                            new DecisionDeclaration.Ask(new SourceLocation(RESOURCE, FRONTMATTER_LINES + 3, 1))),
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
            var source = projectUnit("workflow demo version 4 format 1 derived_from 3", "scope plan",
                    "start demo.end", "state demo.end terminal done success", "phase setup");

            var result = WorkflowParser.parse("inline.pmwf", source, UnitOrigin.PROJECT);

            assertEquals(Map.of("demo", 3),
                    assertInstanceOf(ParseResult.Parsed.class, result).definition().derivedFrom());
        }

        @Test
        @DisplayName("records no derived_from for a project unit without the clause")
        void noDerivedFromOfProjectUnit() {
            var source = projectUnit("workflow demo version 4 format 1", "scope plan", "start demo.end",
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
