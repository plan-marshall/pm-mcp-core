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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
            var copied = enumOf.values();

            assertAll(
                    () -> assertEquals(List.of("a", "b"), copied),
                    () -> assertThrows(UnsupportedOperationException.class, () -> copied.add("d")));
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
            var quorum = new DecisionDeclaration.Quorum(strings, 60, LOCATION);
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
            var copiedTemplates = header.inputs().templates();

            assertAll(
                    () -> assertEquals(List.of(unit), copiedTemplates),
                    () -> assertEquals(List.of(), header.inputs().subUnits()),
                    () -> assertThrows(UnsupportedOperationException.class, () -> copiedTemplates.add(unit)));
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
            var copiedStates = definition.states();
            var copiedMigrations = definition.migrations();

            assertAll(
                    () -> assertEquals(List.of(), definition.params()),
                    () -> assertEquals(List.of(), copiedMigrations),
                    () -> assertEquals(List.of("end"), List.copyOf(copiedStates.keySet())),
                    () -> assertThrows(UnsupportedOperationException.class, copiedStates::clear),
                    () -> assertThrows(UnsupportedOperationException.class, copiedMigrations::clear));
        }

        @Test
        @DisplayName("the entries and exits of the frontmatter are copied and keep their order")
        void frontmatterListsAreCopied() {
            var review = new UnitFrontmatter.InterfaceElement("review", Optional.of("The change is ready."));
            var repair = new UnitFrontmatter.InterfaceElement("repair", Optional.empty());
            var done = new UnitFrontmatter.InterfaceElement("done", Optional.empty());
            var entries = new ArrayList<>(List.of(review, repair));
            var exits = new ArrayList<>(List.of(done));
            var frontmatter = frontmatter(entries, exits);

            entries.clear();
            exits.add(repair);
            var copiedEntries = frontmatter.entries();
            var copiedExits = frontmatter.exits();

            assertAll(
                    () -> assertEquals(List.of(review, repair), copiedEntries),
                    () -> assertEquals(List.of(done), copiedExits),
                    () -> assertThrows(UnsupportedOperationException.class, copiedEntries::clear),
                    () -> assertThrows(UnsupportedOperationException.class, () -> copiedExits.add(done)));
        }

        @Test
        @DisplayName("the entry bindings of the definition are copied and keep their order")
        void entryBindingsAreCopied() {
            var states = new LinkedHashMap<String, StateNode>();
            states.put("review", terminalState("review"));
            states.put("repair", terminalState("repair"));
            var bindings = new LinkedHashMap<String, WorkflowDefinition.EntryBinding>();
            bindings.put("second", new WorkflowDefinition.EntryBinding("repair", LOCATION));
            bindings.put("first", new WorkflowDefinition.EntryBinding("review", LOCATION));
            var definition = definition(bindings, states);

            bindings.put("third", new WorkflowDefinition.EntryBinding("review", LOCATION));
            var copied = definition.entryBindings();

            assertAll(
                    () -> assertEquals(List.of("second", "first"), List.copyOf(copied.keySet())),
                    () -> assertEquals(Optional.empty(), definition.startState()),
                    () -> assertThrows(UnsupportedOperationException.class, copied::clear));
        }
    }

    @Nested
    @DisplayName("Starting states")
    class StartingStates {

        @Test
        @DisplayName("a unit with a start state starts at it alone")
        void startStateAlone() {
            var states = new LinkedHashMap<String, StateNode>();
            states.put("end", terminalState("end"));

            var definition = definition(List.of(), List.of(), states);

            assertEquals(List.of("end"), definition.startingStates());
        }

        @Test
        @DisplayName("a unit with entries starts at every entry-bound state once, in binding order")
        void entryBoundStates() {
            var states = new LinkedHashMap<String, StateNode>();
            states.put("review", terminalState("review"));
            states.put("repair", terminalState("repair"));
            var bindings = new LinkedHashMap<String, WorkflowDefinition.EntryBinding>();
            bindings.put("fix", new WorkflowDefinition.EntryBinding("repair", LOCATION));
            bindings.put("check", new WorkflowDefinition.EntryBinding("review", LOCATION));
            bindings.put("recheck", new WorkflowDefinition.EntryBinding("repair", LOCATION));

            var definition = definition(bindings, states);

            assertEquals(List.of("repair", "review"), definition.startingStates());
        }
    }

    @Nested
    @DisplayName("Invariants")
    class Invariants {

        private static final Optional<Integer> LOW = Optional.of(1);
        private static final Optional<Integer> HIGH = Optional.of(9);
        private static final Optional<Integer> NONE = Optional.empty();

        static Stream<Arguments> singleBounds() {
            return Stream.of(
                    Arguments.of("text with the low bound alone", (Executable) () -> new ParamType.Text(LOW, NONE)),
                    Arguments.of("text with the high bound alone", (Executable) () -> new ParamType.Text(NONE, HIGH)),
                    Arguments.of("int with the low bound alone", (Executable) () -> new ParamType.Int(LOW, NONE)),
                    Arguments.of("int with the high bound alone", (Executable) () -> new ParamType.Int(NONE, HIGH)));
        }

        @Test
        @DisplayName("a call-site list refuses a value that occurs twice and names it")
        void enumListRefusesDuplicate() {
            var values = List.of("a", "b", "a");

            var thrown = assertThrows(IllegalArgumentException.class, () -> new CallValue.EnumList(values));

            assertTrue(thrown.getMessage().contains("'a'"), thrown.getMessage());
        }

        @Test
        @DisplayName("an enum type refuses a value that occurs twice and names it")
        void enumOfRefusesDuplicate() {
            var values = List.of("fast", "slow", "slow");

            var thrown = assertThrows(IllegalArgumentException.class, () -> new ParamType.EnumOf(values));

            assertTrue(thrown.getMessage().contains("'slow'"), thrown.getMessage());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("singleBounds")
        @DisplayName("a text or int type refuses a single bound")
        void singleBoundRefused(String shape, Executable construction) {
            assertThrows(IllegalArgumentException.class, construction, shape);
        }

        @Test
        @DisplayName("a text or int type takes both bounds or none")
        void pairedBoundsAccepted() {
            var boundedText = new ParamType.Text(LOW, HIGH);
            var freeText = new ParamType.Text(NONE, NONE);
            var boundedInt = new ParamType.Int(LOW, HIGH);
            var freeInt = new ParamType.Int(NONE, NONE);

            assertAll(
                    () -> assertEquals(Optional.of(1), boundedText.minLength()),
                    () -> assertEquals(Optional.of(9), boundedText.maxLength()),
                    () -> assertEquals(Optional.empty(), freeText.minLength()),
                    () -> assertEquals(Optional.empty(), freeText.maxLength()),
                    () -> assertEquals(Optional.of(1), boundedInt.min()),
                    () -> assertEquals(Optional.of(9), boundedInt.max()),
                    () -> assertEquals(Optional.empty(), freeInt.min()),
                    () -> assertEquals(Optional.empty(), freeInt.max()));
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

            var call = new StateBehavior.SubWorkflowCall("git.commit_and_push", Optional.empty(), bindings, List.of());
            var frame = new CallFrame("plan", "execute.commit", "execute", "git.commit_and_push", "commit", bindings,
                    "2026-01-01T00:00:00Z");
            var boundArgs = frame.boundArgs();

            assertAll(
                    () -> assertEquals("commit", frame.entry()),
                    () -> assertEquals(List.of("z", "a", "m"), List.copyOf(call.paramBindings().keySet())),
                    () -> assertEquals(List.of("z", "a", "m"), List.copyOf(boundArgs.keySet())),
                    () -> assertThrows(UnsupportedOperationException.class, boundArgs::clear));
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
                    new StateBehavior.SubWorkflowCall("sub", Optional.of("main"), Map.of(), List.of()),
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
                    new ParamType.Int(Optional.of(0), Optional.of(9)),
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
            List<DecisionDeclaration> declarations = List.of(new DecisionDeclaration.Ask(LOCATION),
                    new DecisionDeclaration.Quorum(List.of("reviewer", "auditor"), 75, LOCATION));

            var rendered = declarations.stream().map(AstNodeModelTest::render).toList();

            assertAll(
                    () -> assertEquals(List.of("ask", "quorum(reviewer, auditor, 75)"), rendered),
                    () -> assertEquals(List.of(LOCATION, LOCATION),
                            declarations.stream().map(DecisionDeclaration::location).toList()));
        }

        @Test
        @DisplayName("a declaration carries its value and the line it was read at")
        void valueDeclarationCarriesLocation() {
            var declaration = new ValueDeclaration(new CallValue.IntValue(900), LOCATION);

            assertAll(
                    () -> assertEquals(new CallValue.IntValue(900), declaration.value()),
                    () -> assertEquals(LOCATION, declaration.location()));
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

    private static UnitFrontmatter frontmatter(List<UnitFrontmatter.InterfaceElement> entries,
            List<UnitFrontmatter.InterfaceElement> exits) {
        return new UnitFrontmatter("Fixture unit", "A unit written for the node model test.", UnitType.WORKFLOW,
                "workflow.default", entries, exits, LocalDate.of(2026, 1, 15), "0.1.0", "plan-marshall",
                Optional.empty(), LOCATION);
    }

    private static WorkflowDefinition definition(List<ParamDecl> params, List<StateMigration> migrations,
            Map<String, StateNode> states) {
        return new WorkflowDefinition(frontmatter(List.of(), List.of()), "fixture", 1, WorkflowScope.PLAN, params,
                Optional.empty(), Optional.empty(), Map.of(), Map.of(), Map.of(), 1, UnitOrigin.DEFAULT, Map.of(),
                Optional.of(states.keySet().iterator().next()), Map.of(), migrations, states, LOCATION);
    }

    private static WorkflowDefinition definition(Map<String, WorkflowDefinition.EntryBinding> entryBindings,
            Map<String, StateNode> states) {
        return new WorkflowDefinition(frontmatter(List.of(), List.of()), "fixture", 1, WorkflowScope.SUBWORKFLOW,
                List.of(), Optional.empty(), Optional.empty(), Map.of(), Map.of(), Map.of(), 1, UnitOrigin.DEFAULT,
                Map.of(), Optional.empty(), entryBindings, List.of(), states, LOCATION);
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
