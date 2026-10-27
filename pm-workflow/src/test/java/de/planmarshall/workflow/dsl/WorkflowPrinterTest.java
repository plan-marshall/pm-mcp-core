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
import static de.planmarshall.workflow.dsl.WorkflowParserTest.FRONTMATTER_LINES;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
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
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.workflow.ast.GuardExpr;
import de.planmarshall.workflow.ast.GuardSpec;
import de.planmarshall.workflow.ast.LinkAction;
import de.planmarshall.workflow.ast.LinkForm;
import de.planmarshall.workflow.ast.LinkRelation;
import de.planmarshall.workflow.ast.LinkSpec;
import de.planmarshall.workflow.ast.OutcomeTransition;
import de.planmarshall.workflow.ast.ParamSpec;
import de.planmarshall.workflow.ast.ParamType;
import de.planmarshall.workflow.ast.StateBehavior;
import de.planmarshall.workflow.ast.StateNode;
import de.planmarshall.workflow.ast.UnitFrontmatter;
import de.planmarshall.workflow.ast.UnitOrigin;
import de.planmarshall.workflow.ast.UnitType;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.ast.WorkflowScope;

/**
 * The printer of the workflow language (PM-IMPL-5): every valid fixture survives the round trip parse, print, parse
 * with the same tree, source locations aside; canonical source prints to itself; guards carry exactly the parentheses
 * their precedence needs, and every link names its form.
 */
@DisplayName("WorkflowPrinter")
class WorkflowPrinterTest {

    private static final GuardExpr IN_WORKTREE = new GuardExpr.InWorktree();
    private static final GuardExpr OPEN_FINDINGS = new GuardExpr.HasOpenFindings();
    private static final GuardExpr ALL_TASKS = new GuardExpr.AllTasksCompleted();

    private static final LocalDate FIXTURE_DATE = LocalDate.of(2026, 1, 15);

    private static final String THREE_ENTRIES_UNIT = "dsl/valid/three-entries-unit.pmwf";

    /**
     * The frontmatter of {@link #THREE_ENTRIES_UNIT} as the printer writes it: the fields in the order of the field
     * table, each entry and exit followed by its description, the description that holds {@code ": "} double-quoted,
     * and the entry description the fixture double-quotes plain, because a plain scalar can carry it.
     */
    private static final String THREE_ENTRIES_FRONTMATTER = """
            ---
            name: Review and repair
            description: "A sub-workflow written for the parser tests: three entries, each with its description."
            type: subworkflow
            icon: remediation.review
            entry: review
            entry_description: The change is ready for its review.
            entry: repair
            entry_description: The review rejected the change; its findings are recorded as "open".
            entry: recheck
            entry_description: The repair is done and the change is reviewed again.
            exit: done
            exit_description: The change is approved.
            exit: failed
            modification_date: 2026-01-15
            version: 0.1.0
            author: plan-marshall
            signature: c2lnbmF0dXJlLW9mLXRoZS1maWxl
            ---
            workflow review.repair version 1 format 1
            """;

    /** Canonical source covering the header clauses and every state body form, as the printer writes it. */
    private static final String CANONICAL = """
            ---
            name: git.flow
            description: A unit written for the tests of pm-workflow.
            type: instance
            icon: unit.plan
            modification_date: 2026-01-15
            version: 0.1.0
            author: plan-marshall
            ---
            workflow git.flow version 2 format 1
            instance {"plan_id": "p", "version": 1, "event": "plan", "inputs": {"config_hash": "c", \
            "templates": [{"unit": "git.flow", "digest": "t", "origin": "default"}], "sub_units": [], \
            "composition_rules": {"revision": "r", "digest": "d"}, "plan_facts_digest": "f"}}
            scope subworkflow
            param mode : enum[fast|Slow]
            param tries : int[1..3]
            param picks : list<enum[a|b]>
            result schema[flow-result]
            declare rounds = 2
            declare flavours = [Sweet, sour]
            decide interpretation -> ask
            decide approach -> quorum(architect, reviewer, 70)
            start git.work
            migrate git.old -> git.work

            state git.work
                phase inherit
                skill pm/git/work
                facts git.read-state
                job llm change role writer each git.items ask
                consult role reviewer
                    done -> git.vote
                    lost -> git.end

            state git.vote
                phase inherit
                job llm decision role each git.roles
                    decided -> git.build

            state git.build
                phase inherit
                composite_job git.build-all
                    done -> git.invoke
                        completes git:build as done
                    failed -> git.end
                        completes git:build as failed

            state git.invoke
                phase inherit
                call git.push(mode = fast, tries = 2, picks = [a, b])
                    done -> git.ask
                        guard result.pushed
                    done -> git.end
                        guard not result.pushed

            state git.ask
                phase inherit
                question review
                    approve -> git.choose
                    revise -> git.choose

            state git.choose
                phase inherit
                link alt pick:<entity> [submit] (note: text[1..80]?, size: int, kinds: list<schema[kind]>)
                    guard (in_worktree or has_open_findings) and not predicate:git.ready
                    depends_on_config [project.review.currency]
                    do git.pick
                        done -> git.end

            state git.end terminal done success
                phase inherit
            """;

    private static WorkflowDefinition parse(String resource, String source) {
        return parse(resource, source, UnitOrigin.DEFAULT);
    }

    private static WorkflowDefinition parseProject(String resource, String source) {
        return parse(resource, source, UnitOrigin.PROJECT);
    }

    private static WorkflowDefinition parse(String resource, String source, UnitOrigin origin) {
        var result = WorkflowParser.parse(resource, source, origin);
        return assertInstanceOf(ParseResult.Parsed.class, result, () -> resource + " was refused: " + result)
                .definition();
    }

    /** A workspace unit whose only state rests on the given links. */
    private static WorkflowDefinition unitWithLinks(LinkSpec... links) {
        var frontmatter = new UnitFrontmatter("Demo unit", "A unit written for the printer tests.", UnitType.SYSTEM,
                "workflow.default", List.of(), List.of(), FIXTURE_DATE, "0.1.0", "plan-marshall", Optional.empty(),
                NOWHERE);
        return unitWith(frontmatter, links);
    }

    /** A workspace unit with the given frontmatter whose only state rests on the given links. */
    private static WorkflowDefinition unitWith(UnitFrontmatter frontmatter, LinkSpec... links) {
        var state = new StateNode("demo.begin", "setup", Optional.empty(), Optional.empty(), Optional.empty(),
                new StateBehavior.InteractiveLinks(List.of(links)), NOWHERE);
        return new WorkflowDefinition(frontmatter, "demo", 1, WorkflowScope.WORKSPACE, List.of(), Optional.empty(),
                Optional.empty(), Map.of(), Map.of(), Map.of(), 1, UnitOrigin.DEFAULT, Map.of(),
                Optional.of("demo.begin"), Map.of(), List.of(), Map.of("demo.begin", state), NOWHERE);
    }

    /** The line of the printed source that opens with the given text. */
    private static String lineStartingWith(String printed, String prefix) {
        return printed.lines().filter(line -> line.startsWith(prefix)).findFirst().orElseThrow();
    }

    private static LinkSpec link(LinkRelation rel, LinkForm form, Optional<GuardSpec> guard, ParamSpec... params) {
        return new LinkSpec(rel, "go", form, List.of(params), guard, new LinkAction.ExecutePrimitive("demo.go"),
                List.of(new OutcomeTransition("done", "demo.begin", Optional.empty(), List.of(), NOWHERE)), NOWHERE);
    }

    /** The printed line of the first link's guard, without its indentation. */
    private static String guardLine(String printed) {
        return printed.lines().map(String::strip).filter(line -> line.startsWith("guard ")).findFirst()
                .orElseThrow();
    }

    @Nested
    @DisplayName("Round trip")
    class RoundTrip {

        static List<String> validFixtures() {
            return DslFixtures.list(DslFixtures.VALID);
        }

        @Test
        @DisplayName("runs over the six valid fixtures")
        void fixturesPresent() {
            assertEquals(6, validFixtures().size());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("validFixtures")
        @DisplayName("parses the printed source of every valid fixture to the tree of the fixture")
        void roundTrip(String resource) {
            var parsed = DslFixtures.parseValid(resource);

            var reparsed = parse("printed-" + resource, WorkflowPrinter.print(parsed));

            assertEquals(strip(parsed), strip(reparsed));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("validFixtures")
        @DisplayName("prints the reparsed tree of every valid fixture to the same source")
        void fixedPoint(String resource) {
            var printed = WorkflowPrinter.print(DslFixtures.parseValid(resource));

            var reprinted = WorkflowPrinter.print(parse("printed-" + resource, printed));

            assertEquals(printed, reprinted);
        }

        @Test
        @DisplayName("prints canonical source to itself")
        void canonicalSource() {
            var definition = parse("canonical.pmwf", CANONICAL);

            assertEquals(CANONICAL, WorkflowPrinter.print(definition));
        }
    }

    @Nested
    @DisplayName("Frontmatter and entries")
    class FrontmatterAndEntries {

        @Test
        @DisplayName("prints the frontmatter of three described entries in the order of the field table, a value "
                + "double-quoted only where a plain scalar cannot carry it, and reads it back to the same tree")
        void frontmatterInFieldTableOrder() {
            var definition = DslFixtures.parseValid(THREE_ENTRIES_UNIT);

            var printed = WorkflowPrinter.print(definition);

            assertAll(
                    () -> assertTrue(printed.startsWith(THREE_ENTRIES_FRONTMATTER), printed),
                    () -> assertEquals(strip(definition), strip(parse("printed-three-entries.pmwf", printed))));
        }

        @Test
        @DisplayName("prints the entry bindings in source order in place of the start line, and a call's entry")
        void entryBindingsAndCallEntry() {
            var printed = WorkflowPrinter.print(DslFixtures.parseValid(THREE_ENTRIES_UNIT));

            assertAll(
                    () -> assertTrue(printed.contains("""

                            scope subworkflow
                            entry review -> review.check
                            entry repair -> review.fix
                            entry recheck -> review.check

                            state\s"""), printed),
                    () -> assertTrue(printed.lines().noneMatch(line -> line.startsWith("start ")), printed),
                    () -> assertTrue(printed.contains("    call git.commit_and_push entry stage(push = enabled)\n"),
                            printed));
        }

        @Test
        @DisplayName("escapes a quote and a backslash in a double-quoted value and quotes a value that opens with an "
                + "indicator, and reads both back")
        void quotedValues() {
            var frontmatter = new UnitFrontmatter("-leading dash", "Says: \"quoted\" and a \\ backslash.",
                    UnitType.SYSTEM, "workflow.default", List.of(), List.of(), FIXTURE_DATE, "0.1.0", "plan-marshall",
                    Optional.of("sig-1"), NOWHERE);
            var definition = unitWith(frontmatter, link(LinkRelation.NEXT, LinkForm.NOOP, Optional.empty()));

            var printed = WorkflowPrinter.print(definition);

            assertAll(
                    () -> assertEquals("name: \"-leading dash\"", lineStartingWith(printed, "name: ")),
                    () -> assertEquals("description: \"Says: \\\"quoted\\\" and a \\\\ backslash.\"",
                            lineStartingWith(printed, "description: ")),
                    () -> assertEquals("signature: sig-1", lineStartingWith(printed, "signature: ")),
                    () -> assertEquals(strip(definition), strip(parse("quoted.pmwf", printed))));
        }

        static Stream<String> valuesPlainRefuses() {
            var indicators = UnitFrontmatterReader.INDICATORS.chars().mapToObj(character ->
                    (char) character + "value");
            return Stream.concat(indicators, Stream.of("a: b", "a #b", " leading", "trailing "));
        }

        @ParameterizedTest(name = "[{0}]")
        @MethodSource("valuesPlainRefuses")
        @DisplayName("quotes every value the reader's isPlain refuses and reads it back unchanged, so the printer "
                + "never emits a value the reader refuses")
        void quotesEveryValuePlainRefuses(String value) {
            var frontmatter = new UnitFrontmatter("Demo unit", value, UnitType.SYSTEM, "workflow.default", List.of(),
                    List.of(), FIXTURE_DATE, "0.1.0", "plan-marshall", Optional.empty(), NOWHERE);
            var definition = unitWith(frontmatter, link(LinkRelation.NEXT, LinkForm.NOOP, Optional.empty()));

            var printed = WorkflowPrinter.print(definition);
            var reparsed = parse("quoted-value.pmwf", printed);

            assertAll(
                    () -> assertFalse(UnitFrontmatterReader.isPlain(value), "isPlain accepted: " + value),
                    () -> assertTrue(printed.contains("description: \""), printed),
                    () -> assertEquals(value, reparsed.frontmatter().description()));
        }
    }

    @Nested
    @DisplayName("Guard precedence")
    class GuardPrecedence {

        static Stream<Arguments> guards() {
            return Stream.of(
                    Arguments.of(new GuardExpr.Or(IN_WORKTREE, new GuardExpr.And(OPEN_FINDINGS,
                                    new GuardExpr.Not(ALL_TASKS))),
                            "in_worktree or has_open_findings and not all_tasks_completed"),
                    Arguments.of(new GuardExpr.And(new GuardExpr.Or(IN_WORKTREE, OPEN_FINDINGS), ALL_TASKS),
                            "(in_worktree or has_open_findings) and all_tasks_completed"),
                    Arguments.of(new GuardExpr.Or(new GuardExpr.Or(IN_WORKTREE, OPEN_FINDINGS), ALL_TASKS),
                            "in_worktree or has_open_findings or all_tasks_completed"),
                    Arguments.of(new GuardExpr.Or(IN_WORKTREE, new GuardExpr.Or(OPEN_FINDINGS, ALL_TASKS)),
                            "in_worktree or (has_open_findings or all_tasks_completed)"),
                    Arguments.of(new GuardExpr.And(new GuardExpr.And(IN_WORKTREE, OPEN_FINDINGS), ALL_TASKS),
                            "in_worktree and has_open_findings and all_tasks_completed"),
                    Arguments.of(new GuardExpr.And(IN_WORKTREE, new GuardExpr.And(OPEN_FINDINGS, ALL_TASKS)),
                            "in_worktree and (has_open_findings and all_tasks_completed)"),
                    Arguments.of(new GuardExpr.Not(new GuardExpr.Not(IN_WORKTREE)), "not (not in_worktree)"),
                    Arguments.of(new GuardExpr.Not(new GuardExpr.And(IN_WORKTREE, OPEN_FINDINGS)),
                            "not (in_worktree and has_open_findings)"),
                    Arguments.of(new GuardExpr.Not(new GuardExpr.Or(IN_WORKTREE, OPEN_FINDINGS)),
                            "not (in_worktree or has_open_findings)"),
                    Arguments.of(new GuardExpr.Not(new GuardExpr.CustomPredicate("demo.ready")),
                            "not predicate:demo.ready"),
                    Arguments.of(new GuardExpr.And(new GuardExpr.Not(IN_WORKTREE),
                                    new GuardExpr.Or(OPEN_FINDINGS, ALL_TASKS)),
                            "not in_worktree and (has_open_findings or all_tasks_completed)"));
        }

        @ParameterizedTest(name = "{1}")
        @MethodSource("guards")
        @DisplayName("prints a guard with exactly the parentheses its precedence needs, and reads it back")
        void precedence(GuardExpr expr, String expected) {
            var guard = Optional.of(new GuardSpec(expr, List.of()));
            var definition = unitWithLinks(link(LinkRelation.NEXT, LinkForm.NOOP, guard));

            var printed = WorkflowPrinter.print(definition);

            assertAll(
                    () -> assertEquals("guard " + expected, guardLine(printed)),
                    () -> assertEquals(strip(definition), strip(parse("guard.pmwf", printed))));
        }
    }

    @Nested
    @DisplayName("Focused output")
    class FocusedOutput {

        @ParameterizedTest(name = "{0}")
        @EnumSource(LinkRelation.class)
        @DisplayName("names the form of every link, the default form included")
        void explicitLinkForm(LinkRelation relation) {
            var definition = unitWithLinks(link(relation, relation.defaultForm(), Optional.empty()));
            var expected = "    link %s go [%s] ()".formatted(relation.name().toLowerCase(Locale.ROOT),
                    relation.defaultForm().name().toLowerCase(Locale.ROOT));

            var printed = WorkflowPrinter.print(definition);

            assertAll(
                    () -> assertTrue(printed.lines().anyMatch(expected::equals), printed),
                    () -> assertEquals(strip(definition), strip(parse("form.pmwf", printed))));
        }

        @Test
        @DisplayName("prints the instance line second, with the header JSON exactly as it was read")
        void instanceLine() {
            var definition = DslFixtures.parseValid("dsl/valid/instance-unit.pmwf");
            var header = definition.instance().orElseThrow();

            var lines = WorkflowPrinter.print(definition).lines().toList();

            assertAll(
                    () -> assertEquals("workflow plan version 4 format 1", lines.get(FRONTMATTER_LINES)),
                    () -> assertEquals("instance " + header.json(), lines.get(FRONTMATTER_LINES + 1)),
                    () -> assertTrue(header.json().contains("\"inputs\":{"), header::json),
                    () -> assertEquals(header, parse("printed-instance.pmwf", String.join("\n", lines))
                            .instance().orElseThrow()));
        }

        @Test
        @DisplayName("prints a range with both of its bounds, and no range for an unbounded type")
        void ranges() {
            var definition = unitWithLinks(link(LinkRelation.NEXT, LinkForm.NOOP, Optional.empty(),
                    new ParamSpec("bounded", new ParamType.Text(Optional.of(1), Optional.of(9)), false, NOWHERE),
                    new ParamSpec("free", new ParamType.Int(Optional.empty(), Optional.empty()), true, NOWHERE)));

            var printed = WorkflowPrinter.print(definition);

            assertAll(
                    () -> assertTrue(printed.contains("    link next go [noop] (bounded: text[1..9], free: int?)\n"),
                            printed),
                    () -> assertEquals(strip(definition), strip(parse("range.pmwf", printed))));
        }

        @Test
        @DisplayName("prints no parameter list for a call without bindings and no derived_from in the header")
        void bareCallAndHeader() {
            var definition = DslFixtures.parseValid("dsl/valid/plan-unit.pmwf");

            var printed = WorkflowPrinter.print(definition);

            assertAll(
                    () -> assertEquals("workflow plan version 3 format 1", lineStartingWith(printed, "workflow ")),
                    () -> assertTrue(printed.contains("    call git.no_args\n"), printed));
        }

        @Test
        @DisplayName("round-trips the derived_from revision of a project unit")
        void derivedFromRoundTrip() {
            var source = """
                    ---
                    name: demo
                    description: A project unit written for the printer tests.
                    type: workflow
                    icon: workflow.default
                    modification_date: 2026-01-15
                    version: 0.1.0
                    author: Erika Mustermann
                    ---
                    workflow demo version 4 format 1 derived_from 3
                    scope plan
                    start demo.end

                    state demo.end terminal done success
                        phase setup
                    """;
            var definition = parseProject("derived.pmwf", source);

            var printed = WorkflowPrinter.print(definition);

            assertAll(
                    () -> assertEquals("workflow demo version 4 format 1 derived_from 3",
                            lineStartingWith(printed, "workflow ")),
                    () -> assertEquals(source, printed),
                    () -> assertEquals(strip(definition), strip(parseProject("printed.pmwf", printed))));
        }

        @Test
        @DisplayName("rejects a null definition")
        void nullDefinition() {
            assertThrows(NullPointerException.class, () -> WorkflowPrinter.print(null));
        }
    }
}
