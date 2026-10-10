/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.ast;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.spi.OutcomeClass;

/**
 * The node model of the immutable tree (PM-IMPL-5, PM-WF-16): records copy the collections they are given, maps keep
 * declaration order, and the sealed hierarchies are closed.
 */
@DisplayName("Node model of the tree")
class AstNodeModelTest {

    private static final SourceLocation LOCATION = new SourceLocation("fixture.pmwf", 1, 1);

    @Nested
    @DisplayName("Defensive copies")
    class DefensiveCopies {

        @Test
        @DisplayName("a list passed to a record is copied and cannot be changed through the accessor")
        void listIsCopied() {
            var values = new ArrayList<>(List.of("a", "b"));
            var enumOf = new ParamType.EnumOf(values);

            values.add("c");

            assertAll(
                    () -> assertEquals(List.of("a", "b"), enumOf.values()),
                    () -> assertThrows(UnsupportedOperationException.class, () -> enumOf.values().add("d")));
        }

        @Test
        @DisplayName("every list component of the records is copied")
        void everyListIsCopied() {
            var strings = new ArrayList<>(List.of("x"));
            var transitions = new ArrayList<>(List.of(transition("done", "end")));
            var links = new ArrayList<LinkSpec>();
            var completions = new ArrayList<>(List.of(new StepCompletion("push", StepCompletionStatus.DONE, LOCATION)));
            var params = new ArrayList<ParamSpec>();

            var enumList = new CallValue.EnumList(strings);
            var quorum = new DecisionDeclaration.Quorum(strings, 60);
            var guard = new GuardSpec(new GuardExpr.InWorktree(), strings);
            var interactive = new StateBehavior.InteractiveLinks(links);
            var auto = new StateBehavior.AutoStep("p", transitions);
            var job = new StateBehavior.JobStep("p", true, transitions);
            var modelJob = new StateBehavior.ModelJob("task", Optional.of("writer"), Optional.empty(), Optional.empty(),
                    false, strings, transitions);
            var question = new StateBehavior.QuestionWait("decision", transitions, links);
            var outcome = new OutcomeTransition("done", "end", Optional.empty(), completions, LOCATION);
            var link = new LinkSpec(LinkRelation.NEXT, "go", LinkForm.NOOP, params, Optional.empty(),
                    new LinkAction.ExecutePrimitive("p"), transitions, LOCATION);

            strings.add("y");
            transitions.add(transition("failed", "end"));
            links.add(link);
            completions.clear();
            params.add(new ParamSpec("n", new ParamType.Int(Optional.empty(), Optional.empty()), false, LOCATION));

            assertAll(
                    () -> assertEquals(List.of("x"), enumList.values()),
                    () -> assertEquals(List.of("x"), quorum.roles()),
                    () -> assertEquals(List.of("x"), guard.dependsOnConfig()),
                    () -> assertEquals(List.of(), interactive.links()),
                    () -> assertEquals(1, auto.transitions().size()),
                    () -> assertEquals(1, job.transitions().size()),
                    () -> assertEquals(List.of("x"), modelJob.consultRoles()),
                    () -> assertEquals(1, modelJob.transitions().size()),
                    () -> assertEquals(1, question.transitions().size()),
                    () -> assertEquals(List.of(), question.links()),
                    () -> assertEquals(1, outcome.completions().size()),
                    () -> assertEquals(List.of(), link.freeParams()),
                    () -> assertEquals(1, link.transitions().size()));
        }

        @Test
        @DisplayName("the unit lists of the instance inputs are copied and cannot be changed through the accessor")
        void instanceInputsAreCopied() {
            var unit = new InstanceInputs.UnitRef("plan", Optional.empty(), "sha-1", "default");
            var templates = new ArrayList<>(List.of(unit));
            var subUnits = new ArrayList<InstanceInputs.UnitRef>();
            var inputs = new InstanceInputs("config", templates, subUnits, new InstanceInputs.CompositionRules("1",
                    "sha-2"), "facts", Optional.empty(), Optional.empty(), Optional.empty());
            var header = new InstanceHeader("plan-1", 1, "plan", inputs, "{}");

            templates.clear();
            subUnits.add(unit);

            assertAll(
                    () -> assertEquals(List.of(unit), header.inputs().templates()),
                    () -> assertEquals(List.of(), header.inputs().subUnits()),
                    () -> assertThrows(UnsupportedOperationException.class,
                            () -> header.inputs().templates().add(unit)));
        }

        @Test
        @DisplayName("the collections of the definition are copied")
        void definitionCollectionsAreCopied() {
            var params = new ArrayList<ParamDecl>();
            var migrations = new ArrayList<StateMigration>();
            var states = new LinkedHashMap<String, StateNode>();
            states.put("end", terminalState("end"));
            var definition = definition(params, migrations, states);

            params.add(new ParamDecl("mode", new ParamType.EnumOf(List.of("a")), LOCATION));
            migrations.add(new StateMigration("old", "end", LOCATION));
            states.put("other", terminalState("other"));

            assertAll(
                    () -> assertEquals(List.of(), definition.params()),
                    () -> assertEquals(List.of(), definition.migrations()),
                    () -> assertEquals(List.of("end"), List.copyOf(definition.states().keySet())),
                    () -> assertThrows(UnsupportedOperationException.class, () -> definition.states().clear()),
                    () -> assertThrows(UnsupportedOperationException.class, () -> definition.migrations().clear()));
        }
    }

    @Nested
    @DisplayName("Declaration order of maps")
    class MapOrder {

        @Test
        @DisplayName("the states of a definition keep the order in which they were declared")
        void statesKeepOrder() {
            var states = new LinkedHashMap<String, StateNode>();
            for (var id : List.of("zeta", "alpha", "mu", "beta")) {
                states.put(id, terminalState(id));
            }

            var definition = definition(List.of(), List.of(), states);

            assertEquals(List.of("zeta", "alpha", "mu", "beta"), List.copyOf(definition.states().keySet()));
        }

        @Test
        @DisplayName("the parameter bindings of a call and the bound arguments of a frame keep their order")
        void bindingsKeepOrder() {
            var bindings = new LinkedHashMap<String, CallValue>();
            bindings.put("z", new CallValue.IntValue(1));
            bindings.put("a", new CallValue.EnumValue("x"));
            bindings.put("m", new CallValue.EnumList(List.of("p", "q")));

            var call = new StateBehavior.SubWorkflowCall("git.commit_and_push", bindings, List.of());
            var frame = new CallFrame("plan", "execute.commit", "execute", "git.commit_and_push", bindings,
                    "2026-01-01T00:00:00Z");

            assertAll(
                    () -> assertEquals(List.of("z", "a", "m"), List.copyOf(call.paramBindings().keySet())),
                    () -> assertEquals(List.of("z", "a", "m"), List.copyOf(frame.boundArgs().keySet())),
                    () -> assertThrows(UnsupportedOperationException.class, () -> frame.boundArgs().clear()));
        }
    }

    @Nested
    @DisplayName("Closed hierarchies")
    class ClosedHierarchies {

        @Test
        @DisplayName("a switch over every behaviour of a state needs no default")
        void stateBehaviorIsClosed() {
            List<StateBehavior> behaviors = List.of(
                    new StateBehavior.InteractiveLinks(List.of()),
                    new StateBehavior.AutoStep("p", List.of()),
                    new StateBehavior.JobStep("p", false, List.of()),
                    new StateBehavior.ModelJob("task", Optional.empty(), Optional.empty(), Optional.of("roles"), false,
                            List.of(), List.of()),
                    new StateBehavior.SubWorkflowCall("sub", Map.of(), List.of()),
                    new StateBehavior.QuestionWait("decision", List.of(), List.of()),
                    new StateBehavior.TerminalState());

            var kinds = behaviors.stream().map(AstNodeModelTest::kind).toList();

            assertEquals(List.of("links", "auto", "job", "llm", "call", "question", "terminal"), kinds);
        }

        @Test
        @DisplayName("a switch over every guard expression needs no default")
        void guardExprIsClosed() {
            var expr = new GuardExpr.Or(
                    new GuardExpr.And(new GuardExpr.InWorktree(), new GuardExpr.Not(new GuardExpr.HasOpenFindings())),
                    new GuardExpr.And(new GuardExpr.AllTasksCompleted(), new GuardExpr.CustomPredicate("ready")));

            assertEquals("((in_worktree and not has_open_findings) or (all_tasks_completed and predicate:ready))",
                    render(expr));
        }

        @Test
        @DisplayName("a switch over every parameter type needs no default")
        void paramTypeIsClosed() {
            List<ParamType> types = List.of(
                    new ParamType.Text(Optional.of(1), Optional.of(80)),
                    new ParamType.Int(Optional.of(0), Optional.empty()),
                    new ParamType.EnumOf(List.of("a", "b")),
                    new ParamType.SchemaRef("review"),
                    new ParamType.ListOf(new ParamType.EnumOf(List.of("a"))));

            var names = types.stream().map(AstNodeModelTest::name).toList();

            assertEquals(List.of("text", "int", "enum", "schema[review]", "list<enum>"), names);
        }

        @Test
        @DisplayName("a switch over every call value needs no default")
        void callValueIsClosed() {
            List<CallValue> values = List.of(new CallValue.EnumValue("x"), new CallValue.IntValue(3),
                    new CallValue.EnumList(List.of("a", "b")));

            var rendered = values.stream().map(AstNodeModelTest::render).toList();

            assertEquals(List.of("x", "3", "[a, b]"), rendered);
        }

        @Test
        @DisplayName("a switch over every decision declaration needs no default")
        void decisionDeclarationIsClosed() {
            List<DecisionDeclaration> declarations = List.of(new DecisionDeclaration.Ask(),
                    new DecisionDeclaration.Quorum(List.of("reviewer", "auditor"), 75));

            var rendered = declarations.stream().map(AstNodeModelTest::render).toList();

            assertEquals(List.of("ask", "quorum(reviewer, auditor, 75)"), rendered);
        }

        @Test
        @DisplayName("a switch over every guard result needs no default and keeps the reason")
        void guardResultIsClosed() {
            List<GuardResult> results = List.of(new GuardResult.Held(), new GuardResult.NotHeld(),
                    new GuardResult.CouldNotEvaluate("config unreadable"));

            var rendered = results.stream().map(AstNodeModelTest::render).toList();

            assertEquals(List.of("held", "not_held", "could_not_evaluate(config unreadable)"), rendered);
        }

        @Test
        @DisplayName("the link action is a primitive run")
        void linkActionIsClosed() {
            LinkAction action = new LinkAction.ExecutePrimitive("plan.submit");

            var primitive = switch (action) {
                case LinkAction.ExecutePrimitive execute -> execute.primitiveId();
            };

            assertEquals("plan.submit", primitive);
        }
    }

    @Nested
    @DisplayName("Enumerations")
    class Enumerations {

        @Test
        @DisplayName("the origins of a unit are default and project")
        void unitOrigins() {
            var names = Arrays.stream(UnitOrigin.values()).map(Enum::name).toList();

            assertEquals(List.of("DEFAULT", "PROJECT"), names);
        }

        @Test
        @DisplayName("a step is completed as done, skipped, failed or waived")
        void stepCompletionStatuses() {
            var names = Arrays.stream(StepCompletionStatus.values()).map(Enum::name).toList();

            assertEquals(List.of("DONE", "SKIPPED", "FAILED", "WAIVED"), names);
        }

        @Test
        @DisplayName("a terminal outcome carries the outcome class of pm-core")
        void terminalOutcomeCarriesClass() {
            var outcome = new TerminalOutcome("stepped_out", OutcomeClass.STEPPED_OUT);

            assertAll(
                    () -> assertEquals("stepped_out", outcome.id()),
                    () -> assertEquals(OutcomeClass.STEPPED_OUT, outcome.outcomeClass()));
        }
    }

    private static OutcomeTransition transition(String outcome, String target) {
        return new OutcomeTransition(outcome, target, Optional.empty(), List.of(), LOCATION);
    }

    private static StateNode terminalState(String id) {
        return new StateNode(id, "finalize", Optional.of(new TerminalOutcome(id, OutcomeClass.SUCCESS)),
                Optional.empty(), Optional.empty(), new StateBehavior.TerminalState(), LOCATION);
    }

    private static WorkflowDefinition definition(List<ParamDecl> params, List<StateMigration> migrations,
            Map<String, StateNode> states) {
        return new WorkflowDefinition("fixture", 1, WorkflowScope.PLAN, params, Optional.empty(), Optional.empty(),
                Map.of(), Map.of(), Map.of(), 1, UnitOrigin.DEFAULT, Map.of(), states.keySet().iterator().next(),
                migrations, states, LOCATION);
    }

    private static String kind(StateBehavior behavior) {
        return switch (behavior) {
            case StateBehavior.InteractiveLinks _ -> "links";
            case StateBehavior.AutoStep _ -> "auto";
            case StateBehavior.JobStep _ -> "job";
            case StateBehavior.ModelJob _ -> "llm";
            case StateBehavior.SubWorkflowCall _ -> "call";
            case StateBehavior.QuestionWait _ -> "question";
            case StateBehavior.TerminalState _ -> "terminal";
        };
    }

    private static String render(GuardExpr expr) {
        return switch (expr) {
            case GuardExpr.InWorktree _ -> "in_worktree";
            case GuardExpr.HasOpenFindings _ -> "has_open_findings";
            case GuardExpr.AllTasksCompleted _ -> "all_tasks_completed";
            case GuardExpr.CustomPredicate predicate -> "predicate:" + predicate.primitiveId();
            case GuardExpr.And and -> "(" + render(and.left()) + " and " + render(and.right()) + ")";
            case GuardExpr.Or or -> "(" + render(or.left()) + " or " + render(or.right()) + ")";
            case GuardExpr.Not not -> "not " + render(not.operand());
        };
    }

    private static String name(ParamType type) {
        return switch (type) {
            case ParamType.Text _ -> "text";
            case ParamType.Int _ -> "int";
            case ParamType.EnumOf _ -> "enum";
            case ParamType.SchemaRef schema -> "schema[" + schema.schemaName() + "]";
            case ParamType.ListOf list -> "list<" + name(list.elementType()) + ">";
        };
    }

    private static String render(CallValue value) {
        return switch (value) {
            case CallValue.EnumValue enumValue -> enumValue.value();
            case CallValue.IntValue intValue -> Integer.toString(intValue.value());
            case CallValue.EnumList list -> list.values().toString();
        };
    }

    private static String render(DecisionDeclaration declaration) {
        return switch (declaration) {
            case DecisionDeclaration.Ask _ -> "ask";
            case DecisionDeclaration.Quorum quorum ->
                "quorum(" + String.join(", ", quorum.roles()) + ", " + quorum.score() + ")";
        };
    }

    private static String render(GuardResult result) {
        return switch (result) {
            case GuardResult.Held _ -> "held";
            case GuardResult.NotHeld _ -> "not_held";
            case GuardResult.CouldNotEvaluate couldNot -> "could_not_evaluate(" + couldNot.reason() + ")";
        };
    }
}
