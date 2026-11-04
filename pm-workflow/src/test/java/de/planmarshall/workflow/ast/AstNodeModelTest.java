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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.core.spi.OutcomeClass;

/**
 * The node model of the immutable tree (PM-IMPL-5, PM-WF-16): records copy the collections they are given, maps keep
 * declaration order, and the sealed hierarchies are closed.
 */
@EnableGeneratorController
@DisplayName("Node model of the tree")
class AstNodeModelTest {

    private static final SourceLocation LOCATION = new SourceLocation(
            "fixture-" + Generators.longs(1, Long.MAX_VALUE).next() + ".pmwf",
            Generators.integers(1, 500).next(), Generators.integers(1, 80).next());

    @Nested
    @DisplayName("Defensive copies")
    class DefensiveCopies {

        @Test
        @DisplayName("a list passed to a record is copied and cannot be changed through the accessor")
        void listIsCopied() {
            var first = Generators.letterStrings(1, 8).next();
            var second = Generators.letterStrings(1, 8).next();
            var values = new ArrayList<>(List.of(first, second));
            var enumOf = new ParamType.EnumOf(values);

            values.add(Generators.letterStrings(1, 8).next());
            var copied = enumOf.values();

            assertAll(
                    () -> assertEquals(List.of(first, second), copied),
                    () -> assertThrows(UnsupportedOperationException.class,
                            () -> copied.add(Generators.letterStrings(1, 8).next())));
        }

        @Test
        @DisplayName("every list component of the records is copied")
        void everyListIsCopied() {
            var value = Generators.letterStrings(1, 8).next();
            var strings = new ArrayList<>(List.of(value));
            var transitions = new ArrayList<>(List.of(transition(
                    Generators.letterStrings(1, 8).next(), Generators.letterStrings(1, 8).next())));
            var links = new ArrayList<LinkSpec>();
            var completions = new ArrayList<>(List.of(new StepCompletion(
                    Generators.letterStrings(1, 8).next(), StepCompletionStatus.DONE, LOCATION)));
            var params = new ArrayList<ParamSpec>();

            var enumList = new CallValue.EnumList(strings);
            var quorum = new DecisionDeclaration.Quorum(strings, Generators.integers(1, 100).next(), LOCATION);
            var guard = new GuardSpec(new GuardExpr.InWorktree(), strings);
            var interactive = new StateBehavior.InteractiveLinks(links);
            var auto = new StateBehavior.AutoStep(Generators.letterStrings(1, 8).next(), transitions);
            var job = new StateBehavior.JobStep(Generators.letterStrings(1, 8).next(), true, transitions);
            var modelJob = new StateBehavior.ModelJob(Generators.letterStrings(1, 8).next(),
                    Optional.of(Generators.letterStrings(1, 8).next()), Optional.empty(), Optional.empty(),
                    StateBehavior.AskForm.NONE, strings, transitions);
            var question = new StateBehavior.QuestionWait(Generators.letterStrings(1, 8).next(), transitions);
            var outcome = new OutcomeTransition(Generators.letterStrings(1, 8).next(),
                    Generators.letterStrings(1, 8).next(), Optional.empty(), completions, LOCATION);
            var link = new LinkSpec(LinkRelation.NEXT, Generators.letterStrings(1, 8).next(), LinkForm.NOOP, params,
                    Optional.empty(), new LinkAction.ExecutePrimitive(Generators.letterStrings(1, 8).next()),
                    transitions, LOCATION);

            strings.add(Generators.letterStrings(1, 8).next());
            transitions.add(transition(Generators.letterStrings(1, 8).next(),
                    Generators.letterStrings(1, 8).next()));
            links.add(link);
            completions.clear();
            params.add(new ParamSpec(Generators.letterStrings(1, 8).next(),
                    new ParamType.Int(Optional.empty(), Optional.empty()), false, LOCATION));

            assertAll(
                    () -> assertEquals(List.of(value), enumList.values()),
                    () -> assertEquals(List.of(value), quorum.roles()),
                    () -> assertEquals(List.of(value), guard.dependsOnConfig()),
                    () -> assertEquals(List.of(), interactive.links()),
                    () -> assertEquals(1, auto.transitions().size()),
                    () -> assertEquals(1, job.transitions().size()),
                    () -> assertEquals(List.of(value), modelJob.consultRoles()),
                    () -> assertEquals(1, modelJob.transitions().size()),
                    () -> assertEquals(1, question.transitions().size()),
                    () -> assertEquals(1, outcome.completions().size()),
                    () -> assertEquals(List.of(), link.freeParams()),
                    () -> assertEquals(1, link.transitions().size()));
        }

        @Test
        @DisplayName("the unit lists of the instance inputs are copied and cannot be changed through the accessor")
        void instanceInputsAreCopied() {
            var unit = new InstanceInputs.UnitRef(Generators.letterStrings(1, 8).next(), Optional.empty(),
                    Generators.letterStrings(1, 8).next(), "default");
            var templates = new ArrayList<>(List.of(unit));
            var subUnits = new ArrayList<InstanceInputs.UnitRef>();
            var inputs = new InstanceInputs(Generators.letterStrings(1, 8).next(), templates, subUnits,
                    new InstanceInputs.CompositionRules(Generators.letterStrings(1, 8).next(),
                            Generators.letterStrings(1, 8).next()),
                    Generators.letterStrings(1, 8).next(), Optional.empty(), Optional.empty(), Optional.empty());
            // "default" is the origin wire name, "plan" the composition event and "{}" the header JSON document
            var header = new InstanceHeader(Generators.letterStrings(1, 8).next(),
                    Generators.integers(1, 100_000).next(), "plan", inputs, "{}");

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
            var stateId = Generators.letterStrings(1, 8).next();
            var params = new ArrayList<ParamDecl>();
            var migrations = new ArrayList<StateMigration>();
            var states = new LinkedHashMap<String, StateNode>();
            states.put(stateId, terminalState(stateId));
            var definition = definition(params, migrations, states);

            params.add(new ParamDecl(Generators.letterStrings(1, 8).next(),
                    new ParamType.EnumOf(List.of(Generators.letterStrings(1, 8).next())), LOCATION));
            migrations.add(new StateMigration(Generators.letterStrings(1, 8).next(), stateId, LOCATION));
            states.put(Generators.letterStrings(1, 8).next(),
                    terminalState(Generators.letterStrings(1, 8).next()));
            var copiedStates = definition.states();
            var copiedMigrations = definition.migrations();

            assertAll(
                    () -> assertEquals(List.of(), definition.params()),
                    () -> assertEquals(List.of(), copiedMigrations),
                    () -> assertEquals(List.of(stateId), List.copyOf(copiedStates.keySet())),
                    () -> assertThrows(UnsupportedOperationException.class, copiedStates::clear),
                    () -> assertThrows(UnsupportedOperationException.class, copiedMigrations::clear));
        }

        @Test
        @DisplayName("the entries and exits of the frontmatter are copied and keep their order")
        void frontmatterListsAreCopied() {
            var review = new UnitFrontmatter.InterfaceElement(Generators.letterStrings(1, 8).next(),
                    Optional.of(Generators.letterStrings(1, 40).next()));
            var repair = new UnitFrontmatter.InterfaceElement(Generators.letterStrings(1, 8).next(), Optional.empty());
            var done = new UnitFrontmatter.InterfaceElement(Generators.letterStrings(1, 8).next(), Optional.empty());
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
            var reviewState = Generators.letterStrings(1, 8).next();
            var repairState = Generators.letterStrings(1, 8).next();
            var states = new LinkedHashMap<String, StateNode>();
            states.put(reviewState, terminalState(reviewState));
            states.put(repairState, terminalState(repairState));
            var bindings = new LinkedHashMap<String, WorkflowDefinition.EntryBinding>();
            var second = Generators.letterStrings(1, 8).next();
            var first = Generators.letterStrings(1, 8).next();
            bindings.put(second, new WorkflowDefinition.EntryBinding(repairState, LOCATION));
            bindings.put(first, new WorkflowDefinition.EntryBinding(reviewState, LOCATION));
            var definition = definition(bindings, states);

            bindings.put(Generators.letterStrings(1, 8).next(),
                    new WorkflowDefinition.EntryBinding(reviewState, LOCATION));
            var copied = definition.entryBindings();

            assertAll(
                    () -> assertEquals(List.of(second, first), List.copyOf(copied.keySet())),
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
            var stateId = Generators.letterStrings(1, 8).next();
            var states = new LinkedHashMap<String, StateNode>();
            states.put(stateId, terminalState(stateId));

            var definition = definition(List.of(), List.of(), states);

            assertEquals(List.of(stateId), definition.startingStates());
        }

        @Test
        @DisplayName("a unit with entries starts at every entry-bound state once, in binding order")
        void entryBoundStates() {
            var reviewState = Generators.letterStrings(1, 8).next();
            var repairState = Generators.letterStrings(1, 8).next();
            var states = new LinkedHashMap<String, StateNode>();
            states.put(reviewState, terminalState(reviewState));
            states.put(repairState, terminalState(repairState));
            var bindings = new LinkedHashMap<String, WorkflowDefinition.EntryBinding>();
            bindings.put(Generators.letterStrings(1, 8).next(),
                    new WorkflowDefinition.EntryBinding(repairState, LOCATION));
            bindings.put(Generators.letterStrings(1, 8).next(),
                    new WorkflowDefinition.EntryBinding(reviewState, LOCATION));
            bindings.put(Generators.letterStrings(1, 8).next(),
                    new WorkflowDefinition.EntryBinding(repairState, LOCATION));

            var definition = definition(bindings, states);

            assertEquals(List.of(repairState, reviewState), definition.startingStates());
        }
    }

    @Nested
    @DisplayName("Invariants")
    class Invariants {

        private static final Optional<Integer> LOW = Optional.of(Generators.integers(0, 100).next());
        private static final Optional<Integer> HIGH = Optional.of(Generators.integers(101, 200).next());
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
            var duplicate = Generators.letterStrings(1, 8).next();
            var other = Generators.letterStrings(1, 8).next();
            var values = List.of(duplicate, other, duplicate);

            var thrown = assertThrows(IllegalArgumentException.class, () -> new CallValue.EnumList(values));

            assertTrue(thrown.getMessage().contains("'" + duplicate + "'"), thrown.getMessage());
        }

        @Test
        @DisplayName("an enum type refuses a value that occurs twice and names it")
        void enumOfRefusesDuplicate() {
            var duplicate = Generators.letterStrings(1, 8).next();
            var other = Generators.letterStrings(1, 8).next();
            var values = List.of(duplicate, other, duplicate);

            var thrown = assertThrows(IllegalArgumentException.class, () -> new ParamType.EnumOf(values));

            assertTrue(thrown.getMessage().contains("'" + duplicate + "'"), thrown.getMessage());
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
                    () -> assertEquals(LOW, boundedText.minLength()),
                    () -> assertEquals(HIGH, boundedText.maxLength()),
                    () -> assertEquals(Optional.empty(), freeText.minLength()),
                    () -> assertEquals(Optional.empty(), freeText.maxLength()),
                    () -> assertEquals(LOW, boundedInt.min()),
                    () -> assertEquals(HIGH, boundedInt.max()),
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
            var ids = List.of(Generators.letterStrings(1, 8).next(), Generators.letterStrings(1, 8).next(),
                    Generators.letterStrings(1, 8).next(), Generators.letterStrings(1, 8).next());
            var states = new LinkedHashMap<String, StateNode>();
            for (var id : ids) {
                states.put(id, terminalState(id));
            }

            var definition = definition(List.of(), List.of(), states);

            assertEquals(ids, List.copyOf(definition.states().keySet()));
        }

        @Test
        @DisplayName("the parameter bindings of a call and the bound arguments of a frame keep their order")
        void bindingsKeepOrder() {
            var z = Generators.letterStrings(1, 8).next();
            var a = Generators.letterStrings(1, 8).next();
            var m = Generators.letterStrings(1, 8).next();
            var entry = Generators.letterStrings(1, 8).next();
            var bindings = new LinkedHashMap<String, CallValue>();
            bindings.put(z, new CallValue.IntValue(Generators.integers(0, 1_000).next()));
            bindings.put(a, new CallValue.EnumValue(Generators.letterStrings(1, 8).next()));
            bindings.put(m, new CallValue.EnumList(List.of(Generators.letterStrings(1, 8).next(),
                    Generators.letterStrings(1, 8).next())));

            var call = new StateBehavior.SubWorkflowCall(Generators.letterStrings(1, 8).next(), Optional.empty(),
                    bindings, List.of());
            var frame = new CallFrame(Generators.letterStrings(1, 8).next(), Generators.letterStrings(1, 8).next(),
                    Generators.letterStrings(1, 8).next(), entry, bindings, "2026-01-01T00:00:00Z");
            var boundArgs = frame.boundArgs();

            assertAll(
                    () -> assertEquals(entry, frame.entry()),
                    () -> assertEquals(List.of(z, a, m), List.copyOf(call.paramBindings().keySet())),
                    () -> assertEquals(List.of(z, a, m), List.copyOf(boundArgs.keySet())),
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
                    new StateBehavior.AutoStep(Generators.letterStrings(1, 8).next(), List.of()),
                    new StateBehavior.JobStep(Generators.letterStrings(1, 8).next(), false, List.of()),
                    new StateBehavior.ModelJob(Generators.letterStrings(1, 8).next(), Optional.empty(),
                            Optional.empty(), Optional.of(Generators.letterStrings(1, 8).next()),
                            StateBehavior.AskForm.NONE, List.of(), List.of()),
                    new StateBehavior.SubWorkflowCall(Generators.letterStrings(1, 8).next(),
                            Optional.of(Generators.letterStrings(1, 8).next()), Map.of(), List.of()),
                    new StateBehavior.QuestionWait(Generators.letterStrings(1, 8).next(), List.of()),
                    new StateBehavior.TerminalState());

            var kinds = behaviors.stream().map(AstNodeModelTest::kind).toList();

            // the labels are this switch's own vocabulary, not product data
            assertEquals(List.of("links", "auto", "job", "llm", "call", "question", "terminal"), kinds);
        }

        @Test
        @DisplayName("a switch over every guard expression needs no default")
        void guardExprIsClosed() {
            var predicate = Generators.letterStrings(1, 8).next();
            var expr = new GuardExpr.Or(
                    new GuardExpr.And(new GuardExpr.InWorktree(), new GuardExpr.Not(new GuardExpr.HasOpenFindings())),
                    new GuardExpr.And(new GuardExpr.AllTasksCompleted(), new GuardExpr.CustomPredicate(predicate)));

            assertEquals("((in_worktree and not has_open_findings) or (all_tasks_completed and predicate:" + predicate
                            + "))",
                    render(expr));
        }

        @Test
        @DisplayName("a switch over every parameter type needs no default")
        void paramTypeIsClosed() {
            var schemaName = Generators.letterStrings(1, 8).next();
            List<ParamType> types = List.of(
                    new ParamType.Text(Optional.of(Generators.integers(0, 10).next()),
                            Optional.of(Generators.integers(11, 100).next())),
                    new ParamType.Int(Optional.of(Generators.integers(0, 10).next()),
                            Optional.of(Generators.integers(11, 100).next())),
                    new ParamType.EnumOf(List.of(Generators.letterStrings(1, 8).next(),
                            Generators.letterStrings(1, 8).next())),
                    new ParamType.SchemaRef(schemaName),
                    new ParamType.ListOf(new ParamType.EnumOf(List.of(Generators.letterStrings(1, 8).next()))));

            var names = types.stream().map(AstNodeModelTest::name).toList();

            assertEquals(List.of("text", "int", "enum", "schema[" + schemaName + "]", "list<enum>"), names);
        }

        @Test
        @DisplayName("a switch over every call value needs no default")
        void callValueIsClosed() {
            var value = Generators.letterStrings(1, 8).next();
            var number = Generators.integers(0, 1_000).next();
            var first = Generators.letterStrings(1, 8).next();
            var second = Generators.letterStrings(1, 8).next();
            List<CallValue> values = List.of(new CallValue.EnumValue(value), new CallValue.IntValue(number),
                    new CallValue.EnumList(List.of(first, second)));

            var rendered = values.stream().map(AstNodeModelTest::render).toList();

            assertEquals(List.of(value, Integer.toString(number), "[" + first + ", " + second + "]"), rendered);
        }

        @Test
        @DisplayName("a switch over every decision declaration needs no default")
        void decisionDeclarationIsClosed() {
            var firstRole = Generators.letterStrings(1, 8).next();
            var secondRole = Generators.letterStrings(1, 8).next();
            var score = Generators.integers(1, 100).next();
            List<DecisionDeclaration> declarations = List.of(new DecisionDeclaration.Ask(LOCATION),
                    new DecisionDeclaration.Quorum(List.of(firstRole, secondRole), score, LOCATION));

            var rendered = declarations.stream().map(AstNodeModelTest::render).toList();

            assertAll(
                    () -> assertEquals(
                            List.of("ask", "quorum(" + firstRole + ", " + secondRole + ", " + score + ")"), rendered),
                    () -> assertEquals(List.of(LOCATION, LOCATION),
                            declarations.stream().map(DecisionDeclaration::location).toList()));
        }

        @Test
        @DisplayName("a declaration carries its value and the line it was read at")
        void valueDeclarationCarriesLocation() {
            var value = Generators.integers(0, 100_000).next();
            var declaration = new ValueDeclaration(new CallValue.IntValue(value), LOCATION);

            assertAll(
                    () -> assertEquals(new CallValue.IntValue(value), declaration.value()),
                    () -> assertEquals(LOCATION, declaration.location()));
        }

        @Test
        @DisplayName("a switch over every guard result needs no default and keeps the reason")
        void guardResultIsClosed() {
            var reason = Generators.letterStrings(1, 40).next();
            List<GuardResult> results = List.of(new GuardResult.Held(), new GuardResult.NotHeld(),
                    new GuardResult.CouldNotEvaluate(reason));

            var rendered = results.stream().map(AstNodeModelTest::render).toList();

            assertEquals(List.of("held", "not_held", "could_not_evaluate(" + reason + ")"), rendered);
        }

        @Test
        @DisplayName("the link action is a primitive run")
        void linkActionIsClosed() {
            var primitiveId = Generators.letterStrings(1, 8).next();
            LinkAction action = new LinkAction.ExecutePrimitive(primitiveId);

            var primitive = switch (action) {
                case LinkAction.ExecutePrimitive execute -> execute.primitiveId();
            };

            assertEquals(primitiveId, primitive);
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
            var outcomeId = Generators.letterStrings(1, 8).next();
            var outcome = new TerminalOutcome(outcomeId, OutcomeClass.STEPPED_OUT);

            assertAll(
                    () -> assertEquals(outcomeId, outcome.id()),
                    () -> assertEquals(OutcomeClass.STEPPED_OUT, outcome.outcomeClass()));
        }
    }

    private static OutcomeTransition transition(String outcome, String target) {
        return new OutcomeTransition(outcome, target, Optional.empty(), List.of(), LOCATION);
    }

    private static StateNode terminalState(String id) {
        return new StateNode(id, Optional.of(new TerminalOutcome(id, OutcomeClass.SUCCESS)),
                Optional.empty(), Optional.empty(), new StateBehavior.TerminalState(), LOCATION);
    }

    private static UnitFrontmatter frontmatter(List<UnitFrontmatter.InterfaceElement> entries,
            List<UnitFrontmatter.InterfaceElement> exits) {
        // "workflow.default" is a product icon id, "0.1.0" a release version, "plan-marshall" the compiled-in author
        return new UnitFrontmatter(Generators.letterStrings(3, 100).next(),
                Generators.letterStrings(1, 100).next(), UnitType.WORKFLOW, "workflow.default", entries, exits,
                LocalDate.of(Generators.integers(2000, 2030).next(), Generators.integers(1, 12).next(),
                        Generators.integers(1, 28).next()),
                "0.1.0", "plan-marshall", Optional.empty(), LOCATION);
    }

    private static WorkflowDefinition definition(List<ParamDecl> params, List<StateMigration> migrations,
            Map<String, StateNode> states) {
        return new WorkflowDefinition(frontmatter(List.of(), List.of()),
                Generators.letterStrings(1, 8).next(), Generators.integers(1, 100).next(), WorkflowScope.PLAN, params,
                Optional.empty(), Optional.empty(), Map.of(), Map.of(), 1, UnitOrigin.DEFAULT, Map.of(),
                Optional.of(states.keySet().iterator().next()), Map.of(), migrations, states, LOCATION);
    }

    private static WorkflowDefinition definition(Map<String, WorkflowDefinition.EntryBinding> entryBindings,
            Map<String, StateNode> states) {
        return new WorkflowDefinition(frontmatter(List.of(), List.of()),
                Generators.letterStrings(1, 8).next(), Generators.integers(1, 100).next(), WorkflowScope.SUBWORKFLOW,
                List.of(), Optional.empty(), Optional.empty(), Map.of(), Map.of(), 1, UnitOrigin.DEFAULT,
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
