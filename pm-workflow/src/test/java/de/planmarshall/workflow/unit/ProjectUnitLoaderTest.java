/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.unit;

import static de.planmarshall.workflow.log.WorkflowLogMessages.INFO;
import static de.planmarshall.workflow.log.WorkflowLogMessages.WARN;
import static de.planmarshall.workflow.unit.UnitFixtures.configPath;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.planmarshall.core.store.ConfinementOutcome;
import de.planmarshall.workflow.ast.UnitOrigin;
import de.planmarshall.workflow.dsl.ParseResult;
import de.planmarshall.workflow.dsl.RefusalRule;
import de.planmarshall.workflow.dsl.UnitFrontmatterReader;
import de.planmarshall.workflow.dsl.WorkflowParser;
import de.planmarshall.workflow.finalize.PipelineRule;
import de.planmarshall.workflow.verify.InvariantFixtures;
import de.planmarshall.workflow.verify.InvariantId;

/**
 * The loader of the project-owned units (PM-IMPL-5) over fixture projects whose {@code .marshall/config/} holds the
 * units of {@code unit-fixtures/defaults/}, verified against the same units read as defaults. Every load either
 * returns every verified project unit or refuses the plan lifecycle with {@code config_invalid}, naming the file and
 * the line, and logs WARN {@code PROJECT_UNIT_REFUSED}; a refused project unit is never replaced by its default unit.
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("ProjectUnitLoader")
class ProjectUnitLoaderTest {

    @TempDir
    Path temp;

    private Path project;

    private String projectId;

    @BeforeEach
    void project() throws IOException {
        project = Files.createDirectories(temp.toRealPath().resolve("project"));
        projectId = Generators.letterStrings(4, 12).next();
    }

    private UnitLoadResult load(UnitFixtures.Mutation... mutations) {
        UnitFixtures.writeProject(project, mutations);
        return UnitFixtures.load(projectId, project, List.of(), Set.of());
    }

    /**
     * Asserts that the load is refused, that it carries the finding at the file and line, and that the refusal of
     * the lifecycle and the WARN record name the file of its first finding.
     */
    private static UnitFinding assertRefused(UnitLoadResult result, UnitFinding.Kind kind, String code, String file,
            int line) {
        var finding = UnitFixtures.find(result.findings(), kind, code);
        var refusal = result.refusal();
        assertAll("refused load",
                () -> assertFalse(result.isVerified()),
                () -> assertEquals(Map.of(), result.units(), "a refused load carries no unit, no default either"),
                () -> assertTrue(finding.isPresent(), () -> "%s %s expected in %s".formatted(kind, code,
                        result.findings())),
                () -> assertEquals(file, finding.orElseThrow().file()),
                () -> assertEquals(line, finding.orElseThrow().line(), finding.orElseThrow()::message),
                () -> assertEquals(UnitLoadResult.CONFIG_INVALID, refusal.orElseThrow().code()),
                () -> assertEquals(result.findings().getFirst().file(), refusal.orElseThrow().file()),
                () -> assertEquals(result.findings().getFirst().line(), refusal.orElseThrow().line()),
                () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                        WARN.PROJECT_UNIT_REFUSED.resolveIdentifierString()),
                () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, file),
                () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO, ProjectUnitLoader.class));
        return finding.orElseThrow();
    }

    @Nested
    @DisplayName("Passing project")
    class PassingProject {

        @Test
        @DisplayName("verifies every project unit, the plan unit first, and logs PROJECT_UNITS_VERIFIED")
        void verifies() {
            var result = load();

            assertAll(
                    () -> assertTrue(result.isVerified(), result.findings()::toString),
                    () -> assertEquals(List.of(PlanUnitMerger.PLAN_UNIT, "adjudicate.decisions", "replan",
                                    "git.commit_and_push", "remediation.qgate-findings"),
                            List.copyOf(result.units().keySet())),
                    () -> assertEquals(Optional.empty(), result.refusal()),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                            INFO.PROJECT_UNITS_VERIFIED.resolveIdentifierString()),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO, projectId),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, ProjectUnitLoader.class));
        }

        @Test
        @DisplayName("parses every unit as a project unit, named by its path below the project")
        void projectOrigin() {
            var units = load().units();
            var plan = units.get(PlanUnitMerger.PLAN_UNIT);

            assertAll(
                    () -> units.values().forEach(unit -> assertEquals(UnitOrigin.PROJECT, unit.origin(), unit::id)),
                    () -> assertEquals(configPath("init.pmwf"), plan.location().resourceName()),
                    () -> assertEquals(configPath(InvariantFixtures.REPLAN_FILE),
                            units.get("replan").location().resourceName()));
        }
    }

    @Nested
    @DisplayName("Author check")
    class AuthorCheck {

        @Test
        @DisplayName("a project unit naming the author plan-marshall is refused with author_reserved at its author line")
        void reservedAuthor() {
            var mutation = UnitFixtures.RESERVED_AUTHOR;

            var result = load(mutation);

            var finding = assertRefused(result, UnitFinding.Kind.RULE, UnitFinding.AUTHOR_RESERVED,
                    configPath(mutation.file()),
                    UnitFixtures.lineOf(mutation.file(), "author: " + UnitFixtures.DEFAULT_AUTHOR));
            assertAll(
                    () -> assertTrue(finding.message().contains(ProjectUnitLoader.RESERVED_AUTHOR), finding::message),
                    () -> assertEquals(1, result.findings().stream()
                                    .filter(other -> UnitFinding.AUTHOR_RESERVED.equals(other.code())).count(),
                            "only the unit naming the reserved author is refused for it"));
        }

        @Test
        @DisplayName("a project unit naming a person as its author loads")
        void personAuthor() {
            var result = load(UnitFixtures.OTHER_PERSON);

            assertAll(
                    () -> assertTrue(result.isVerified(), result.findings()::toString),
                    () -> assertEquals("Max Mustermann", result.units().get("replan").frontmatter().author()));
        }

        static Stream<Arguments> reservedForms() {
            return Stream.of(
                    Arguments.of("the exact reserved author", "plan-marshall"),
                    Arguments.of("an upper-case initial", "Plan-Marshall"),
                    Arguments.of("an upper-case author", "PLAN-MARSHALL"),
                    Arguments.of("a zero width space inside", "plan\u200B-marshall"),
                    Arguments.of("a full-width form NFKC folds to the reserved author",
                            "\uFF50\uFF4C\uFF41\uFF4E\uFF0D\uFF4D\uFF41\uFF52\uFF53\uFF48\uFF41\uFF4C\uFF4C"),
                    Arguments.of("a trailing no-break space NFKC folds to a blank", "plan-marshall\u00A0"),
                    Arguments.of("a trailing figure space", "plan-marshall\u2007"),
                    Arguments.of("a trailing narrow no-break space", "plan-marshall\u202F"),
                    Arguments.of("a long s the case folding turns into s", "plan-mar\u017Fhall"),
                    Arguments.of("a double-quoted form", "\"plan-marshall\""),
                    Arguments.of("a quoted form with surrounding whitespace", "\"  plan-marshall  \""));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("reservedForms")
        @DisplayName("refuses every form that equals the reserved author after stripping, NFKC and case folding")
        void reservedForm(String name, String author) {
            var mutation = new UnitFixtures.Mutation(name, InvariantFixtures.REPLAN_FILE,
                    UnitFixtures.PROJECT_AUTHOR_LINE, "author: " + author);

            var result = load(mutation);

            var finding = assertRefused(result, UnitFinding.Kind.RULE, UnitFinding.AUTHOR_RESERVED,
                    configPath(mutation.file()),
                    UnitFixtures.lineOf(mutation.file(), "author: " + UnitFixtures.DEFAULT_AUTHOR));
            assertAll(
                    () -> assertTrue(finding.message().contains(ProjectUnitLoader.RESERVED_AUTHOR),
                            finding::message),
                    () -> assertEquals(1, result.findings().stream()
                                    .filter(other -> UnitFinding.AUTHOR_RESERVED.equals(other.code())).count(),
                            "only the unit naming the reserved author is refused for it"));
        }

        static Stream<String> nearMisses() {
            return Stream.of("plan-marshall-x", "plan-marshal", "plan-marshaller", "Plan-Marshaller");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("nearMisses")
        @DisplayName("loads an author that only resembles the reserved one")
        void nearMiss(String author) {
            var mutation = new UnitFixtures.Mutation("a near miss", InvariantFixtures.REPLAN_FILE,
                    UnitFixtures.PROJECT_AUTHOR_LINE, "author: " + author);

            var result = load(mutation);

            assertAll(
                    () -> assertTrue(result.isVerified(), result.findings()::toString),
                    () -> assertEquals(author, result.units().get("replan").frontmatter().author()));
        }
    }

    @Nested
    @DisplayName("File size bound")
    class FileSizeBound {

        private static final String REPLAN = InvariantFixtures.REPLAN_FILE;

        /** Pads the file after its frontmatter with one comment line to exactly the given number of bytes. */
        private void padTo(int bytes) throws IOException {
            var path = project.resolve(configPath(REPLAN));
            var base = Files.readString(path);
            var padding = bytes - base.length() - 3;
            assertTrue(padding > 0, () -> "the fixture is longer than " + bytes + " bytes");
            Files.writeString(path, base + "\n# " + "x".repeat(padding));
        }

        @Test
        @DisplayName("reads a unit file at the size cap")
        void acceptsAtCap() throws Exception {
            UnitFixtures.writeProject(project);
            padTo(ProjectUnitLoader.MAX_UNIT_FILE_BYTES);

            var result = UnitFixtures.load(projectId, project, List.of(), Set.of());

            assertAll(
                    () -> assertEquals(ProjectUnitLoader.MAX_UNIT_FILE_BYTES,
                            Files.size(project.resolve(configPath(REPLAN)))),
                    () -> assertTrue(result.isVerified(), result.findings()::toString));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("aboveTheCap")
        @DisplayName("refuses a unit file above the size cap before it is read whole")
        void refusesAboveCap(String name, int bytes) throws Exception {
            UnitFixtures.writeProject(project);
            padTo(bytes);

            var result = UnitFixtures.load(projectId, project, List.of(), Set.of());

            var finding = assertRefused(result, UnitFinding.Kind.MISSING, UnitFinding.UNIT_TOO_LARGE,
                    configPath(REPLAN), 0);
            assertAll(
                    () -> assertTrue(finding.message().contains(Integer.toString(ProjectUnitLoader.MAX_UNIT_FILE_BYTES)),
                            finding::message),
                    () -> assertEquals(bytes, Files.size(project.resolve(configPath(REPLAN)))));
        }

        static Stream<Arguments> aboveTheCap() {
            return Stream.of(
                    Arguments.of("one byte past the cap", ProjectUnitLoader.MAX_UNIT_FILE_BYTES + 1),
                    Arguments.of("four times the cap", ProjectUnitLoader.MAX_UNIT_FILE_BYTES * 4));
        }

        @Test
        @DisplayName("does not report an author below the frontmatter line cap")
        void authorBelowTheLineCap() throws Exception {
            UnitFixtures.writeProject(project);
            var source = new StringBuilder("---\n");
            for (var index = 1; index <= UnitFrontmatterReader.MAX_FRONTMATTER_LINES; index++) {
                source.append("entry: e").append(index).append('\n');
            }
            source.append("author: ").append(ProjectUnitLoader.RESERVED_AUTHOR).append("\n---\n")
                    .append("workflow replan version 1 format 1\nscope subworkflow\n");
            Files.writeString(project.resolve(configPath(REPLAN)), source);

            var result = UnitFixtures.load(projectId, project, List.of(), Set.of());

            assertAll(
                    () -> assertFalse(result.isVerified()),
                    () -> assertTrue(UnitFixtures.find(result.findings(), UnitFinding.Kind.PARSE,
                            RefusalRule.FRONTMATTER.name()).isPresent(), result.findings()::toString),
                    () -> assertTrue(UnitFixtures.find(result.findings(), UnitFinding.Kind.RULE,
                                    UnitFinding.AUTHOR_RESERVED).isEmpty(),
                            "the author below the cap is not read"));
        }
    }

    @Nested
    @DisplayName("Verification refusals")
    class VerificationRefusals {

        @Test
        @DisplayName("a unit violating an invariant refuses the lifecycle with config_invalid, the file and the line")
        void invariantViolation() {
            var mutation = UnitFixtures.INVARIANT_VIOLATION;

            var result = load(mutation);

            assertRefused(result, UnitFinding.Kind.INVARIANT, InvariantId.INV_GEN_1.wireId(),
                    configPath(mutation.file()), mutation.lineOf("state returned terminal"));
        }

        @Test
        @DisplayName("a plan unit whose light gate lacks the light -> deep ratchet is refused with INV-INIT-2, since a"
                + " template's lane is unknown")
        void ratchetOwedForUnknownLane() {
            var ratchet = InvariantFixtures.WITHOUT_RATCHET;
            var mutation = new UnitFixtures.Mutation(ratchet.name(), ratchet.file(), ratchet.find(),
                    ratchet.replace());

            var result = load(mutation);

            assertRefused(result, UnitFinding.Kind.INVARIANT, InvariantId.INV_INIT_2.wireId(),
                    configPath(mutation.file()), mutation.lineOf("state init.light-gate"));
        }

        @Test
        @DisplayName("a project replacement of the type instance for a workflow default is refused with INV-GEN-1"
                + " at its header, naming both types")
        void instanceReplacement() {
            var file = InvariantFixtures.REPLAN_FILE;
            var mutation = new UnitFixtures.Mutation("the re-planning unit declares the type instance", file,
                    "type: subworkflow", "type: instance");
            UnitFixtures.writeProject(project, mutation);
            var workflowDefault = new UnitFixtures.Mutation("the default re-planning unit is a workflow", file,
                    "type: subworkflow", "type: workflow");
            var parsed = WorkflowParser.parse(file, workflowDefault.apply(InvariantFixtures.read(file)),
                    UnitOrigin.DEFAULT);
            var defaults = new LinkedHashMap<>(UnitFixtures.defaultUnits());
            defaults.put("replan", assertInstanceOf(ParseResult.Parsed.class, parsed).definition());
            var loader = new ProjectUnitLoader(UnitFixtures.context(), defaults, InvariantFixtures.defaultRoster(),
                    UnitFixtures.UNIT_FILES);

            var result = loader.load(projectId, UnitFixtures.roots(project), List.of(), Set.of());

            var expected = "the type 'instance' differs from the type 'workflow' of the default unit";
            var matching = result.findings().stream()
                    .filter(finding -> finding.kind() == UnitFinding.Kind.INVARIANT
                            && InvariantId.INV_GEN_1.wireId().equals(finding.code())
                            && finding.message().contains(expected))
                    .toList();
            assertAll(
                    () -> assertFalse(result.isVerified()),
                    () -> assertEquals(1, matching.size(), result.findings()::toString),
                    () -> assertEquals(configPath(file), matching.getFirst().file()),
                    () -> assertEquals(mutation.lineOf("workflow replan"), matching.getFirst().line()));
        }

        @Test
        @DisplayName("a unit violating a rule of Rules 1-12 is refused at the offending transition")
        void ruleViolation() {
            var mutation = UnitFixtures.RULE_VIOLATION;
            var line = mutation.lineOf("merged -> finalize.nowhere");

            var result = load(mutation);

            assertTrue(result.findings().stream().anyMatch(finding -> finding.kind() == UnitFinding.Kind.RULE
                            && configPath(mutation.file()).equals(finding.file()) && finding.line() == line),
                    result.findings()::toString);
            assertFalse(result.isVerified());
        }

        @Test
        @DisplayName("a declaration out of its bounds is refused with declaration_invalid at the declare line")
        void declarationOutOfBounds() {
            var mutation = UnitFixtures.DECLARATION_OUT_OF_BOUNDS;

            var result = load(mutation);

            var finding = assertRefused(result, UnitFinding.Kind.DECLARATION, "declaration_invalid",
                    configPath(mutation.file()), mutation.lineOf("declare vote_wait_seconds = 30"));
            assertAll(
                    () -> assertTrue(finding.message().contains("vote_wait_seconds"), finding::message),
                    () -> assertTrue(result.findings().stream().noneMatch(
                                    other -> other.kind() == UnitFinding.Kind.INVARIANT
                                            && configPath(mutation.file()).equals(other.file())
                                            && other.line() == finding.line()),
                            "the invariant check does not report the declaration finding again"));
        }

        @Test
        @DisplayName("a project step that breaks the finalize pipeline is refused at the state completing it")
        void pipelineViolation() {
            var mutation = new UnitFixtures.Mutation("the merge state completes the project step",
                    UnitFixtures.FINALIZE_FILE, "            completes default:branch-cleanup as done",
                    "            completes default:branch-cleanup as done\n            completes "
                            + UnitFixtures.PROJECT_STEP + " as done");
            UnitFixtures.writeProject(project, mutation);

            var result = UnitFixtures.load(projectId, project,
                    List.of(UnitFixtures.projectStep(UnitFixtures.PROJECT_STEP, 70)), Set.of(UnitFixtures.PROJECT_STEP));

            assertRefused(result, UnitFinding.Kind.PIPELINE, PipelineRule.ORDER_UNIQUE.name(),
                    configPath(mutation.file()), mutation.lineOf("state finalize.merge\n"));
        }
    }

    @Nested
    @DisplayName("Duplicate unit identifiers")
    class DuplicateUnitIds {

        @Test
        @DisplayName("a file declaring the id of an earlier unit is refused at its header and replaces nothing")
        void twoFilesOneId() {
            var mutation = UnitFixtures.DUPLICATE_UNIT_ID;

            var result = load(mutation);

            var finding = assertRefused(result, UnitFinding.Kind.RULE, UnitFinding.DUPLICATE_UNIT_ID,
                    configPath(mutation.file()), mutation.lineOf("workflow adjudicate.decisions"));
            assertAll(
                    () -> assertTrue(finding.message().contains("adjudicate.decisions"), finding::message),
                    () -> assertTrue(finding.message().contains(configPath(InvariantFixtures.ADJUDICATION_FILE)),
                            finding::message),
                    () -> assertTrue(finding.message().contains(configPath(mutation.file())), finding::message));
        }

        @Test
        @DisplayName("a file declaring the id plan is refused at its header and never stands in for the plan unit")
        void planId() {
            var mutation = UnitFixtures.PLAN_UNIT_ID;

            var result = load(mutation);

            var finding = assertRefused(result, UnitFinding.Kind.RULE, UnitFinding.DUPLICATE_UNIT_ID,
                    configPath(mutation.file()), mutation.lineOf(UnitFixtures.PLAN_HEADER));
            assertAll(
                    () -> assertTrue(finding.message().contains("'" + PlanUnitMerger.PLAN_UNIT + "'"),
                            finding::message),
                    () -> assertTrue(finding.message().contains(configPath("init.pmwf")), finding::message),
                    () -> assertTrue(finding.message().contains(configPath(mutation.file())), finding::message));
        }
    }

    @Nested
    @DisplayName("Format refusals")
    class FormatRefusals {

        /**
         * The compiled format is the first one, and the grammar admits no format below 1, so a unit older than the
         * compiled format is refused by the parser at its header; {@code format_outdated} is reached once a later
         * release compiles format 2.
         */
        @Test
        @DisplayName("a unit of a format older than the compiled first one is refused by the parser at its header")
        void olderFormat() {
            var mutation = UnitFixtures.OLDER_FORMAT;

            var result = load(mutation);

            var finding = assertRefused(result, UnitFinding.Kind.PARSE, RefusalRule.STRUCTURE.name(),
                    configPath(mutation.file()), mutation.lineOf("workflow replan"));
            assertAll(
                    () -> assertEquals(1, ProjectUnitLoader.COMPILED_FORMAT_VERSION),
                    () -> assertTrue(finding.message().contains("'0'"), finding::message));
        }

        @Test
        @DisplayName("a unit of a newer format is refused with format_newer at its header")
        void newerFormat() {
            var mutation = UnitFixtures.NEWER_FORMAT;

            var result = load(mutation);

            assertRefused(result, UnitFinding.Kind.FORMAT, UnitFinding.FORMAT_NEWER, configPath(mutation.file()),
                    mutation.lineOf("workflow replan"));
        }

        @Test
        @DisplayName("a phase file of a newer format refuses the plan unit, which is not merged from the rest")
        void newerPhaseFormat() {
            var mutation = new UnitFixtures.Mutation("a newer phase file", "refine.pmwf", UnitFixtures.PLAN_HEADER,
                    "workflow plan version 1 format 2");

            var result = load(mutation);

            assertRefused(result, UnitFinding.Kind.FORMAT, UnitFinding.FORMAT_NEWER, configPath(mutation.file()),
                    mutation.lineOf("workflow plan"));
        }
    }

    @Nested
    @DisplayName("Unreadable units")
    class UnreadableUnits {

        @Test
        @DisplayName("a missing unit is refused and never replaced by the default unit")
        void missingUnit() throws Exception {
            UnitFixtures.writeProject(project);
            Files.delete(project.resolve(configPath(InvariantFixtures.REPLAN_FILE)));

            var result = UnitFixtures.load(projectId, project, List.of(), Set.of());

            assertRefused(result, UnitFinding.Kind.MISSING, UnitFinding.UNIT_MISSING,
                    configPath(InvariantFixtures.REPLAN_FILE), 0);
            assertFalse(result.units().containsKey("replan"));
        }

        @Test
        @DisplayName("an unparsable unit is refused with the parser's rule and line, never replaced by the default")
        void unparsableUnit() {
            var mutation = UnitFixtures.UNPARSABLE;

            var result = load(mutation);

            assertRefused(result, UnitFinding.Kind.PARSE, RefusalRule.STRUCTURE.name(), configPath(mutation.file()),
                    mutation.lineOf("workflow replan revision"));
        }

        @Test
        @DisplayName("a unit that is not UTF-8 is refused with unit_unreadable")
        void notUtf8() throws Exception {
            UnitFixtures.writeProject(project);
            Files.write(project.resolve(configPath(InvariantFixtures.REPLAN_FILE)),
                    new byte[]{'-', '-', '-', '\n', (byte) 0xC3, 0x28});

            var result = UnitFixtures.load(projectId, project, List.of(), Set.of());

            assertRefused(result, UnitFinding.Kind.MISSING, UnitFinding.UNIT_UNREADABLE,
                    configPath(InvariantFixtures.REPLAN_FILE), 0);
        }

        @Test
        @DisplayName("does not report the reserved author when the opening delimiter is not exact")
        void authorBehindAnIndentedDelimiter() throws Exception {
            UnitFixtures.writeProject(project);
            var path = project.resolve(configPath(InvariantFixtures.REPLAN_FILE));
            Files.writeString(path, " " + Files.readString(path));

            var result = UnitFixtures.load(projectId, project, List.of(), Set.of());

            assertAll(
                    () -> assertFalse(result.isVerified()),
                    () -> assertTrue(UnitFixtures.find(result.findings(), UnitFinding.Kind.PARSE,
                            RefusalRule.FRONTMATTER.name()).isPresent(), result.findings()::toString),
                    () -> assertTrue(UnitFixtures.find(result.findings(), UnitFinding.Kind.RULE,
                                    UnitFinding.AUTHOR_RESERVED).isEmpty(),
                            "the author behind a non-exact opening delimiter is not read"));
        }

        @Test
        @DisabledOnOs(value = OS.WINDOWS, disabledReason = "creating a symbolic link needs a privilege there")
        @DisplayName("a unit linked to a file outside the project is refused with path_traversal_rejected")
        void symlinkOutsideRoot() throws Exception {
            UnitFixtures.writeProject(project);
            var outside = Files.createDirectories(temp.toRealPath().resolve("outside"));
            var target = Files.writeString(outside.resolve(InvariantFixtures.REPLAN_FILE),
                    InvariantFixtures.read(InvariantFixtures.REPLAN_FILE));
            var unit = project.resolve(configPath(InvariantFixtures.REPLAN_FILE));
            Files.delete(unit);
            Files.createSymbolicLink(unit, target);

            var result = UnitFixtures.load(projectId, project, List.of(), Set.of());

            assertRefused(result, UnitFinding.Kind.CONFINEMENT, ConfinementOutcome.PATH_TRAVERSAL_REJECTED,
                    configPath(InvariantFixtures.REPLAN_FILE), 0);
        }
    }

    @Test
    @DisplayName("refuses a null argument")
    void nullArguments() {
        var loader = UnitFixtures.projectLoader();
        var roots = UnitFixtures.roots(project);
        var context = UnitFixtures.context();
        var defaults = UnitFixtures.defaultUnits();
        var roster = InvariantFixtures.defaultRoster();
        var files = UnitFixtures.UNIT_FILES;

        assertAll(
                () -> assertThrows(NullPointerException.class, () -> loader.load(null, roots, List.of(), Set.of())),
                () -> assertThrows(NullPointerException.class, () -> loader.load(projectId, null, List.of(), Set.of())),
                () -> assertThrows(NullPointerException.class, () -> loader.load(projectId, roots, null, Set.of())),
                () -> assertThrows(NullPointerException.class, () -> loader.load(projectId, roots, List.of(), null)),
                () -> assertThrows(NullPointerException.class,
                        () -> new ProjectUnitLoader(null, defaults, roster, files)),
                () -> assertThrows(NullPointerException.class,
                        () -> new ProjectUnitLoader(context, null, roster, files)),
                () -> assertThrows(NullPointerException.class,
                        () -> new ProjectUnitLoader(context, defaults, null, files)),
                () -> assertThrows(NullPointerException.class,
                        () -> new ProjectUnitLoader(context, defaults, roster, null)));
    }
}
