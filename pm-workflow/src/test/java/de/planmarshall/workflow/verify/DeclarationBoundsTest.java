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

import static de.planmarshall.workflow.verify.InvariantFixtures.ADJUDICATION_FILE;
import static de.planmarshall.workflow.verify.InvariantFixtures.GIT_FILE;
import static de.planmarshall.workflow.verify.InvariantFixtures.REPLAN_FILE;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.workflow.ast.CallValue;
import de.planmarshall.workflow.ast.DecisionDeclaration;
import de.planmarshall.workflow.ast.UnitOrigin;
import de.planmarshall.workflow.dsl.ParseResult;
import de.planmarshall.workflow.dsl.RefusalRule;
import de.planmarshall.workflow.dsl.WorkflowParser;
import de.planmarshall.workflow.verify.InvariantFixtures.Mutation;

/**
 * The compiled declaration bounds of the adjudication and the re-planning unit (PM-IMPL-5, PM-WF-17, PM-WF-18), over
 * the units of {@code unit-fixtures/defaults/}, which declare the shipped values, and of the method and asking variants
 * of the plan behaviour axes (PM-WF-25), over variant units written in the tests. Every bound has a refusing fixture,
 * named {@code declaration_invalid} at the file and the line of the offending {@code declare} or {@code decide} line,
 * and at the unit header for a missing declaration. A name or a decision kind declared twice, a decision kind outside
 * the closed set and an empty quorum are refused by the parser before the bounds are checked. A value below an
 * integer bound of {@code 0} cannot be written, since the language has no negative integer; the empty list {@code []}
 * can, so the asking variants that wait after no phase declare their shipped {@code wait_after} like every other
 * variant.
 */
@EnableGeneratorController
@DisplayName("DeclarationBounds")
class DeclarationBoundsTest {

    /** The header of the re-planning unit, where a missing declaration is located. */
    private static final String REPLAN_HEADER = "workflow replan version 1 format 1";

    private static List<DeclarationBounds.Finding> check(String file, String source) {
        return DeclarationBounds.check(InvariantFixtures.parse(file, source, UnitOrigin.PROJECT));
    }

    @Nested
    @DisplayName("Shipped values")
    class ShippedValues {

        @Test
        @DisplayName("the adjudication and the re-planning unit with the shipped values pass")
        void shippedValuesPass() {
            assertAll(
                    () -> assertEquals(List.of(),
                            check(ADJUDICATION_FILE, InvariantFixtures.read(ADJUDICATION_FILE))),
                    () -> assertEquals(List.of(), check(REPLAN_FILE, InvariantFixtures.read(REPLAN_FILE))));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.verify.DeclarationBoundsTest#schemaUnits")
        @DisplayName("the fixture declares the shipped value of every name of the schema, which its bound admits")
        void fixtureDeclaresShippedValues(String file) {
            var unit = InvariantFixtures.parse(file, InvariantFixtures.read(file), UnitOrigin.DEFAULT);
            var schema = DeclarationBounds.schema(unit.id()).orElseThrow();

            assertAll(
                    () -> assertEquals(schema.stream().map(DeclarationBounds.Declaration::name).toList(),
                            List.copyOf(unit.declarations().keySet())),
                    () -> schema.forEach(declaration -> assertAll(
                            () -> assertEquals(declaration.shipped(),
                                    unit.declarations().get(declaration.name()).value(), declaration::name),
                            () -> assertTrue(declaration.bound().admits(declaration.shipped()), declaration::name))));
        }

        @Test
        @DisplayName("a unit without a schema and without declarations passes")
        void unitWithoutSchema() {
            assertAll(
                    () -> assertEquals(Optional.empty(), DeclarationBounds.schema("git.commit_and_push")),
                    () -> assertEquals(List.of(), check(GIT_FILE, InvariantFixtures.read(GIT_FILE))));
        }
    }

    static List<String> schemaUnits() {
        return List.of(ADJUDICATION_FILE, REPLAN_FILE);
    }

    @Nested
    @DisplayName("Refusing fixtures")
    class RefusingFixtures {

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.verify.DeclarationBoundsTest#refusingCases")
        @DisplayName("every bound refuses with declaration_invalid at the file and line of the declaration")
        void refuses(BoundCase refusing) {
            var source = refusing.mutation().applied();
            var file = refusing.mutation().file();

            var findings = check(file, source);

            assertAll(
                    () -> assertEquals(List.of(new Located(refusing.name(), file,
                                    InvariantFixtures.lineOf(source, refusing.anchor()))),
                            findings.stream().map(Located::of).toList()),
                    () -> assertEquals(DeclarationBounds.DECLARATION_INVALID, findings.getFirst().code()),
                    () -> assertTrue(findings.getFirst().bound().contains(refusing.bound()),
                            () -> findings.getFirst().bound()));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.verify.DeclarationBoundsTest#parserRefusals")
        @DisplayName("a duplicate, an unknown decision kind and an empty quorum are refused by the parser")
        void refusedByParser(ParserCase refusing) {
            var source = refusing.mutation().applied();

            var result = WorkflowParser.parse(refusing.mutation().file(), source, UnitOrigin.PROJECT);

            var refused = assertInstanceOf(ParseResult.Refused.class, result);
            assertAll(
                    () -> assertEquals(refusing.rule(), refused.diagnostic().rule()),
                    () -> assertEquals(InvariantFixtures.lineOf(source, refusing.anchor()),
                            refused.diagnostic().line()));
        }

        @Test
        @DisplayName("the adjudication invariant reports a bound violation at the line of the declaration")
        void invariantReportsDeclarationLine() {
            var mutation = new Mutation("the vote wait above its bound", ADJUDICATION_FILE,
                    "declare vote_wait_seconds = 900", "declare vote_wait_seconds = 1801");
            var unit = InvariantFixtures.mutatedUnit(mutation);

            var found = InvariantFixtures.invariants().check(unit, InvariantFixtures.validator().verify(unit),
                    List.of(), Optional.empty());

            assertEquals(List.of(new Located("INV-ADJ-1", ADJUDICATION_FILE,
                            InvariantFixtures.lineOf(mutation.applied(), "declare vote_wait_seconds"))),
                    found.stream().map(violation -> new Located(violation.id().wireId(),
                            violation.location().resourceName(), violation.location().line())).toList());
        }
    }

    /**
     * The parser and the bound of a decision-kind declaration read the one closed set
     * {@link DecisionDeclaration#DECISION_KINDS}, so a kind added to it is admitted by both, and neither admits a
     * kind outside it.
     */
    @Nested
    @DisplayName("Shared decision kinds")
    class SharedDecisionKinds {

        private static ParseResult parseDecide(String kind) {
            var source = """
                    ---
                    name: adjudicate.decisions
                    description: A unit written for the tests of pm-workflow.
                    type: subworkflow
                    exit: done
                    modification_date: 2026-01-15
                    version: 0.1.0
                    author: plan-marshall
                    ---
                    workflow adjudicate.decisions version 1 format 1
                    scope subworkflow
                    decide %s -> ask
                    start demo.end

                    state demo.end terminal done success
                        phase inherit
                    """.formatted(kind);
            return WorkflowParser.parse("kinds.pmwf", source, UnitOrigin.PROJECT);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.verify.DeclarationBoundsTest#decisionKinds")
        @DisplayName("every kind of the shared set is admitted by the parser and by the decision-kind bound")
        void sharedKindAdmittedByBoth(String kind) {
            var result = parseDecide(kind);

            var parsed = assertInstanceOf(ParseResult.Parsed.class, result, result::toString);
            assertAll(
                    () -> assertEquals(List.of(kind), List.copyOf(parsed.definition().decisions().keySet())),
                    () -> assertTrue(new DeclarationBounds.DecisionKind().admits(new CallValue.EnumValue(kind))));
        }

        @Test
        @DisplayName("a kind outside the shared set is refused by the parser and by the decision-kind bound")
        void otherKindRefusedByBoth() {
            var kind = "not_" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);

            var result = parseDecide(kind);

            assertAll(kind,
                    () -> assertInstanceOf(ParseResult.Refused.class, result),
                    () -> assertFalse(new DeclarationBounds.DecisionKind().admits(new CallValue.EnumValue(kind))));
        }

        @Test
        @DisplayName("the decision-kind bound names exactly the shared set, in its order")
        void boundNamesSharedSet() {
            assertEquals("enum[%s]".formatted(String.join("|", DecisionDeclaration.DECISION_KINDS)),
                    new DeclarationBounds.DecisionKind().describe());
        }
    }

    static List<String> decisionKinds() {
        return DecisionDeclaration.DECISION_KINDS;
    }

    @Nested
    @DisplayName("Generated values")
    class GeneratedValues {

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.verify.DeclarationBoundsTest#intDeclarations")
        @DisplayName("any value within an integer bound passes, any value above it is refused")
        void withinAndAbove(String name) {
            var file = fileOf(name);
            var unitId = InvariantFixtures.parse(file, InvariantFixtures.read(file), UnitOrigin.DEFAULT).id();
            var declaration = DeclarationBounds.schema(unitId).orElseThrow().stream()
                    .filter(candidate -> candidate.name().equals(name)).findFirst().orElseThrow();
            var range = (DeclarationBounds.IntRange) declaration.bound();
            var shipped = ((CallValue.IntValue) declaration.shipped()).value();
            var within = Generators.integers(range.min(), range.max()).next();
            var above = Generators.integers(range.max() + 1, range.max() + 1_000_000).next();

            var withinFindings = check(file, replaceValue(file, name, shipped, within));
            var aboveFindings = check(file, replaceValue(file, name, shipped, above));

            assertAll(
                    () -> assertEquals(List.of(), withinFindings, () -> name + " = " + within),
                    () -> assertEquals(List.of(name), aboveFindings.stream().map(DeclarationBounds.Finding::name)
                            .toList(), () -> name + " = " + above));
        }

        private static String fileOf(String name) {
            return DeclarationBounds.schema(DeclarationBounds.ADJUDICATION_UNIT).orElseThrow().stream()
                    .anyMatch(declaration -> declaration.name().equals(name)) ? ADJUDICATION_FILE : REPLAN_FILE;
        }

        private static String replaceValue(String file, String name, int shipped, int value) {
            return new Mutation(name, file, "declare %s = %d".formatted(name, shipped),
                    "declare %s = %d".formatted(name, value)).applied();
        }
    }

    static List<String> intDeclarations() {
        return Stream.of(DeclarationBounds.ADJUDICATION_UNIT, DeclarationBounds.REPLAN_UNIT)
                .flatMap(unit -> DeclarationBounds.schema(unit).orElseThrow().stream())
                .filter(declaration -> declaration.bound() instanceof DeclarationBounds.IntRange)
                .map(DeclarationBounds.Declaration::name).toList();
    }

    /** The file name of every variant unit written in these tests. */
    private static final String VARIANT_FILE = "variant.pmwf";

    /**
     * A variant unit written for the tests, never copied from the product: one pass state and the {@code continue}
     * terminal, with the given {@code declare} lines.
     */
    private static String variant(String id, String... declarations) {
        var prefix = id.replace('.', '-');
        return """
                ---
                name: %1$s
                description: A unit written for the tests of pm-workflow.
                type: subworkflow
                entry: main
                exit: continue
                modification_date: 2026-01-15
                version: 0.1.0
                author: plan-marshall
                ---
                workflow %1$s version 1 format 1
                scope subworkflow
                %3$s
                entry main -> %2$s.pass

                state %2$s.pass
                    phase inherit
                    auto test.proceed
                        done -> %2$s.done

                state %2$s.done terminal continue success
                    phase inherit
                """.formatted(id, prefix, String.join("\n", declarations));
    }

    /** The asking variant that waits after every phase but the outline, with its shipped declarations. */
    private static String everyPhase() {
        return variant(DeclarationBounds.ASK_EVERY_PHASE, "declare wait_after = [init, refine, plan, execute]",
                "declare outline_review = true", "declare self_merge = false");
    }

    private static String invalidValue() {
        return "not_" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
    }

    /**
     * The declaration schemas of the method and asking variants (PM-WF-25): each choice an enumeration value or a
     * list of them, never an integer, {@code true} and {@code false} two values of an enumeration; the shipped values
     * are those the specification states for each variant.
     */
    @Nested
    @DisplayName("Axis variants")
    class AxisVariants {

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.verify.DeclarationBoundsTest#variantSchemas")
        @DisplayName("each variant has its schema with the shipped values the specification states")
        void shippedValues(String variant, Map<String, CallValue> shipped) {
            var schema = DeclarationBounds.schema(variant).orElseThrow();

            assertAll(variant,
                    () -> assertEquals(List.copyOf(shipped.keySet()),
                            schema.stream().map(DeclarationBounds.Declaration::name).toList()),
                    () -> schema.forEach(declaration -> assertEquals(shipped.get(declaration.name()),
                            declaration.shipped(), declaration::name)),
                    () -> schema.forEach(declaration -> assertTrue(declaration.bound().admits(declaration.shipped()),
                            declaration::name)),
                    () -> assertTrue(schema.stream().allMatch(declaration ->
                                    declaration.bound() instanceof DeclarationBounds.EnumOf
                                            || declaration.bound() instanceof DeclarationBounds.EnumListOf),
                            schema::toString));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.verify.DeclarationBoundsTest#writableVariants")
        @DisplayName("a variant unit that declares its shipped values passes")
        void variantUnitPasses(String name, String source) {
            assertEquals(List.of(), check(VARIANT_FILE, source), name);
        }

        @Test
        @DisplayName("an asking variant declares the empty wait_after, which its compiled list bound admits")
        void emptyWaitAfterCompiles() {
            var source = everyPhase().replace("[init, refine, plan, execute]", "[]");
            var unit = InvariantFixtures.parse(VARIANT_FILE, source, UnitOrigin.PROJECT);

            assertAll(
                    () -> assertEquals(new CallValue.EnumList(List.of()),
                            unit.declarations().get(DeclarationBounds.WAIT_AFTER).value()),
                    () -> assertEquals(List.of(), DeclarationBounds.check(unit)));
        }

        @Test
        @DisplayName("an asking variant may change a choice to another value of its enumeration")
        void designedChoicePasses() {
            var designed = everyPhase().replace("declare self_merge = false", "declare self_merge = true")
                    .replace("[init, refine, plan, execute]", "[outline]");

            assertEquals(List.of(), check(VARIANT_FILE, designed));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.verify.DeclarationBoundsTest#variantRefusals")
        @DisplayName("a value outside its enumeration, of another kind, or missing is refused declaration_invalid")
        void refused(String description, String source, String name, String anchor, String boundText) {
            var findings = check(VARIANT_FILE, source);

            assertAll(description,
                    () -> assertEquals(List.of(new Located(name, VARIANT_FILE,
                            InvariantFixtures.lineOf(source, anchor))), findings.stream().map(Located::of).toList()),
                    () -> assertEquals(boundText, findings.getFirst().bound()));
        }

        @Test
        @DisplayName("the enumeration bounds describe themselves and admit only their kind and values")
        void enumBounds() {
            var choice = new DeclarationBounds.EnumOf(List.of("true", "false"));
            var phases = new DeclarationBounds.EnumListOf(List.of("init", "refine"));

            assertAll(
                    () -> assertEquals("enum[true|false]", choice.describe()),
                    () -> assertTrue(choice.admits(new CallValue.EnumValue("false"))),
                    () -> assertFalse(choice.admits(new CallValue.EnumValue(invalidValue()))),
                    () -> assertFalse(choice.admits(new CallValue.EnumList(List.of("true")))),
                    () -> assertFalse(choice.admits(new CallValue.IntValue(1))),
                    () -> assertEquals("list<enum[init|refine]>", phases.describe()),
                    () -> assertTrue(phases.admits(new CallValue.EnumList(List.of()))),
                    () -> assertTrue(phases.admits(new CallValue.EnumList(List.of("refine", "init")))),
                    () -> assertFalse(phases.admits(new CallValue.EnumList(List.of("init", "plan")))),
                    () -> assertFalse(phases.admits(new CallValue.EnumValue("init"))));
        }
    }

    static Stream<Arguments> variantSchemas() {
        return Stream.of(
                Arguments.of("method.test-first", Map.of("order", new CallValue.EnumValue("tests_first"))),
                Arguments.of("method.tests-after", Map.of("order", new CallValue.EnumValue("tests_after"))),
                Arguments.of("ask.every-phase", asking(List.of("init", "refine", "plan", "execute"), "true",
                        "false")),
                Arguments.of("ask.key-points", asking(List.of(), "true", "false")),
                Arguments.of("ask.questions-only", asking(List.of(), "false", "true")));
    }

    private static Map<String, CallValue> asking(List<String> waitAfter, String outlineReview, String selfMerge) {
        var declarations = new LinkedHashMap<String, CallValue>();
        declarations.put("wait_after", new CallValue.EnumList(waitAfter));
        declarations.put("outline_review", new CallValue.EnumValue(outlineReview));
        declarations.put("self_merge", new CallValue.EnumValue(selfMerge));
        return declarations;
    }

    static Stream<Arguments> writableVariants() {
        return Stream.of(
                Arguments.of("method.test-first", variant("method.test-first", "declare order = tests_first")),
                Arguments.of("method.tests-after", variant("method.tests-after", "declare order = tests_after")),
                Arguments.of("ask.every-phase", everyPhase()),
                Arguments.of(DeclarationBounds.ASK_KEY_POINTS, variant(DeclarationBounds.ASK_KEY_POINTS,
                        "declare wait_after = []", "declare outline_review = true", "declare self_merge = false")),
                Arguments.of(DeclarationBounds.ASK_QUESTIONS_ONLY, variant(DeclarationBounds.ASK_QUESTIONS_ONLY,
                        "declare wait_after = []", "declare outline_review = false", "declare self_merge = true")));
    }

    static Stream<Arguments> variantRefusals() {
        var order = invalidValue();
        var choice = invalidValue();
        var phase = invalidValue();
        return Stream.of(
                Arguments.of("an order outside its enumeration",
                        variant("method.test-first", "declare order = " + order), "order", "declare order",
                        "enum[tests_first|tests_after]"),
                Arguments.of("an order given as an integer", variant("method.tests-after", "declare order = 1"),
                        "order", "declare order", "enum[tests_first|tests_after]"),
                Arguments.of("self_merge outside true and false",
                        everyPhase().replace("declare self_merge = false", "declare self_merge = " + choice),
                        "self_merge", "declare self_merge", "enum[true|false]"),
                Arguments.of("outline_review given as a list",
                        everyPhase().replace("declare outline_review = true", "declare outline_review = [true]"),
                        "outline_review", "declare outline_review", "enum[true|false]"),
                Arguments.of("wait_after with a phase outside the phases",
                        everyPhase().replace("[init, refine, plan, execute]", "[init, " + phase + "]"),
                        "wait_after", "declare wait_after", "list<enum[init|refine|outline|plan|execute]>"),
                Arguments.of("wait_after given as one value",
                        everyPhase().replace("[init, refine, plan, execute]", "init"),
                        "wait_after", "declare wait_after", "list<enum[init|refine|outline|plan|execute]>"),
                Arguments.of("a missing self_merge",
                        everyPhase().replace("declare self_merge = false", ""),
                        "self_merge", "workflow ask.every-phase", "enum[true|false]"));
    }

    @Test
    @DisplayName("the bounds describe themselves and admit only their type")
    void bounds() {
        var range = new DeclarationBounds.IntRange(60, 1800);
        var kind = new DeclarationBounds.DecisionKind();

        assertAll(
                () -> assertEquals("int[60..1800]", range.describe()),
                () -> assertFalse(range.admits(new CallValue.EnumValue("approach"))),
                () -> assertTrue(kind.describe().startsWith("enum[interpretation|")),
                () -> assertFalse(kind.admits(new CallValue.IntValue(1))),
                () -> assertFalse(kind.admits(new CallValue.EnumList(List.of("approach")))));
    }

    @Test
    @DisplayName("refuses a missing unit, declaration component and finding component")
    void nullArguments() {
        var location = InvariantFixtures.parse(REPLAN_FILE, InvariantFixtures.read(REPLAN_FILE), UnitOrigin.DEFAULT)
                .location();
        var kind = new DeclarationBounds.DecisionKind();
        var scope = new CallValue.EnumValue("scope");

        assertAll(
                () -> assertThrows(NullPointerException.class, () -> DeclarationBounds.check(null)),
                () -> assertThrows(NullPointerException.class,
                        () -> new DeclarationBounds.Declaration(null, kind, scope)),
                () -> assertThrows(NullPointerException.class,
                        () -> new DeclarationBounds.Finding("name", location, null, "message")));
    }

    static List<BoundCase> refusingCases() {
        return List.of(
                bound("vote_wait_seconds below its bound", ADJUDICATION_FILE, "declare vote_wait_seconds = 900",
                        "declare vote_wait_seconds = 59", "vote_wait_seconds", "int[60..1800]"),
                bound("vote_wait_seconds above its bound", ADJUDICATION_FILE, "declare vote_wait_seconds = 900",
                        "declare vote_wait_seconds = 1801", "vote_wait_seconds", "int[60..1800]"),
                bound("max_false_auto_percent above its bound", ADJUDICATION_FILE,
                        "declare max_false_auto_percent = 5", "declare max_false_auto_percent = 11",
                        "max_false_auto_percent", "int[0..10]"),
                bound("replan_budget above its bound", REPLAN_FILE, "declare replan_budget = 3",
                        "declare replan_budget = 6", "replan_budget", "int[0..5]"),
                bound("base_overlap_min_files below its bound", REPLAN_FILE, "declare base_overlap_min_files = 5",
                        "declare base_overlap_min_files = 0", "base_overlap_min_files", "int[1..50]"),
                bound("base_overlap_min_files above its bound", REPLAN_FILE, "declare base_overlap_min_files = 5",
                        "declare base_overlap_min_files = 51", "base_overlap_min_files", "int[1..50]"),
                bound("conflict_min_files below its bound", REPLAN_FILE, "declare conflict_min_files = 10",
                        "declare conflict_min_files = 0", "conflict_min_files", "int[1..50]"),
                bound("conflict_min_files above its bound", REPLAN_FILE, "declare conflict_min_files = 10",
                        "declare conflict_min_files = 51", "conflict_min_files", "int[1..50]"),
                bound("prerequisite_wait_seconds below its bound", REPLAN_FILE,
                        "declare prerequisite_wait_seconds = 604800", "declare prerequisite_wait_seconds = 3599",
                        "prerequisite_wait_seconds", "int[3600..604800]"),
                bound("prerequisite_wait_seconds above its bound", REPLAN_FILE,
                        "declare prerequisite_wait_seconds = 604800", "declare prerequisite_wait_seconds = 604801",
                        "prerequisite_wait_seconds", "int[3600..604800]"),
                bound("an integer declaration with an enum value", ADJUDICATION_FILE,
                        "declare vote_wait_seconds = 900", "declare vote_wait_seconds = approach",
                        "vote_wait_seconds", "int[60..1800]"),
                bound("kind_recipe_candidate outside the decision kinds", ADJUDICATION_FILE,
                        "declare kind_recipe_candidate = approach", "declare kind_recipe_candidate = sideways",
                        "kind_recipe_candidate", "enum[interpretation|"),
                bound("kind_sibling_collision outside the decision kinds", ADJUDICATION_FILE,
                        "declare kind_sibling_collision = scope", "declare kind_sibling_collision = sideways",
                        "kind_sibling_collision", "enum[interpretation|"),
                bound("kind_domain_choice outside the decision kinds", ADJUDICATION_FILE,
                        "declare kind_domain_choice = placement", "declare kind_domain_choice = sideways",
                        "kind_domain_choice", "enum[interpretation|"),
                bound("kind_outline_review outside the decision kinds", ADJUDICATION_FILE,
                        "declare kind_outline_review = scope", "declare kind_outline_review = sideways",
                        "kind_outline_review", "enum[interpretation|"),
                bound("a decision kind declaration with an integer", ADJUDICATION_FILE,
                        "declare kind_domain_choice = placement", "declare kind_domain_choice = 3",
                        "kind_domain_choice", "enum[interpretation|"),
                new BoundCase(new Mutation("a missing declaration", REPLAN_FILE, "declare conflict_min_files = 10\n",
                        ""), "conflict_min_files", REPLAN_HEADER, "int[1..50]"),
                new BoundCase(new Mutation("an unknown declaration", REPLAN_FILE, "declare replan_budget = 3\n",
                                "declare replan_budget = 3\ndeclare replan_rounds = 2\n"), "replan_rounds",
                        "declare replan_rounds", "the schema of 'replan'"),
                new BoundCase(new Mutation("a declaration in a unit without a schema", GIT_FILE,
                                "param push_mode : enum[push|no_push]\n",
                                "param push_mode : enum[push|no_push]\ndeclare attempts = 2\n"), "attempts",
                        "declare attempts", "no declaration schema"),
                quorum("a quorum of four roles", "quorum(architect, reviewer, auditor, tester, 75)"),
                quorum("a quorum that lists a role twice", "quorum(architect, architect, 75)"),
                quorum("a quorum score of 49", "quorum(architect, reviewer, 49)"),
                quorum("a quorum score of 101", "quorum(architect, reviewer, 101)"),
                new BoundCase(new Mutation("a decide line outside the adjudication unit", REPLAN_FILE,
                                "declare prerequisite_wait_seconds = 604800\n",
                                "declare prerequisite_wait_seconds = 604800\ndecide approach -> ask\n"), "decide approach",
                        "decide approach", "decide lines only in adjudicate.decisions"));
    }

    static List<ParserCase> parserRefusals() {
        return List.of(
                new ParserCase(new Mutation("a declaration declared twice", REPLAN_FILE, "declare replan_budget = 3\n",
                                "declare replan_budget = 3\ndeclare replan_budget = 4\n"), RefusalRule.DUPLICATE_ID,
                        "declare replan_budget = 4"),
                new ParserCase(new Mutation("a decision kind declared twice", ADJUDICATION_FILE,
                                "decide interpretation -> ask\n",
                                "decide interpretation -> ask\ndecide interpretation -> ask\n"), RefusalRule.DUPLICATE_ID,
                        "decide interpretation -> ask\ndecide approach"),
                new ParserCase(new Mutation("a decision kind outside the closed set", ADJUDICATION_FILE,
                                "decide interpretation -> ask", "decide sideways -> ask"), RefusalRule.STRUCTURE,
                        "decide sideways"),
                new ParserCase(new Mutation("a quorum of no role", ADJUDICATION_FILE,
                        "quorum(architect, reviewer, 75)", "quorum(75)"), RefusalRule.STRUCTURE, "quorum(75)"));
    }

    private static BoundCase bound(String description, String file, String find, String replace, String name,
            String boundText) {
        return new BoundCase(new Mutation(description, file, find, replace), name, replace, boundText);
    }

    private static BoundCase quorum(String description, String replace) {
        return new BoundCase(new Mutation(description, ADJUDICATION_FILE, "quorum(architect, reviewer, 75)", replace),
                "decide approach", "decide approach", "quorum of 1 to 3 distinct roles with a score of 50 to 100");
    }

    /**
     * One refusing fixture of a bound.
     *
     * @param mutation the change of a passing fixture
     * @param name     the name the finding names
     * @param anchor   a text that occurs once in the changed file, on the line the finding names
     * @param bound    a text the finding's bound contains
     */
    record BoundCase(Mutation mutation, String name, String anchor, String bound) {

        @Override
        public String toString() {
            return mutation.name();
        }
    }

    /**
     * One fixture the parser refuses before the bounds are checked.
     *
     * @param mutation the change of a passing fixture
     * @param rule     the refusal rule
     * @param anchor   a text that occurs once in the changed file, on the line of the refusal
     */
    record ParserCase(Mutation mutation, RefusalRule rule, String anchor) {

        @Override
        public String toString() {
            return mutation.name();
        }
    }

    /**
     * A finding or violation by its name and location alone.
     *
     * @param name the declared name or the invariant
     * @param file the file
     * @param line the line
     */
    record Located(String name, String file, int line) {

        static Located of(DeclarationBounds.Finding finding) {
            return new Located(finding.name(), finding.location().resourceName(), finding.location().line());
        }
    }
}
