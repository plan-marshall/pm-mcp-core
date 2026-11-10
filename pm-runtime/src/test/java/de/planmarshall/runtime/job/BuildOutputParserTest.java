/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.job;

import static de.planmarshall.runtime.testing.generators.RuntimeGenerators.failures;
import static de.planmarshall.runtime.testing.generators.RuntimeGenerators.words;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.store.JobId;
import de.planmarshall.core.store.JobRecord;
import de.planmarshall.core.store.JobRecord.BudgetDerivation;
import de.planmarshall.core.store.JobRecord.Confinement;
import de.planmarshall.core.store.JobRecord.Execution;
import de.planmarshall.core.store.JobRecord.Fate;
import de.planmarshall.core.store.JobRecord.Scope;
import de.planmarshall.core.store.JobRecord.Status;
import de.planmarshall.runtime.job.JobEvidence.Exited;
import de.planmarshall.runtime.job.JobEvidence.LogState;
import de.planmarshall.runtime.job.JobEvidence.ParseResult;
import de.planmarshall.runtime.job.OutcomeDerivation.Outcome;
import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * The adapter from the native build parsers to the runner's parser seam: the budget key selects the build system and
 * the log's markers select among the npm tools, the positive success marker of the parsed build is the only evidence of
 * a success, and a log no parser recognises yields nothing. Each case derives the outcome the runner would record, so a
 * green banner, a failure marker and a log without either are checked against the outcome table rather than against the
 * parser alone.
 * <p>
 * Contract literals that stay: the build logs, which are the golden output formats of Maven, Gradle, npm, tsc,
 * ESLint, Jest, TAP and pytest (the Java file and its line inside a Maven log are generated), the build-system part of
 * a budget key, the build systems without a parser, and the outcome rows of the outcome table. The plan, the job id,
 * the budget, the digest, the instant, the verb and module of a budget key and every failing exit code are generated.
 */
@EnableGeneratorController
@DisplayName("BuildOutputParser")
class BuildOutputParserTest {

    private static final BuildOutputParser PARSER = new BuildOutputParser();

    /** The outcome rows of the outcome table: the contract of the derivation. */
    private static final Outcome SUCCEEDED = new Outcome(Fate.SUCCEEDED, 7);
    private static final Outcome INDETERMINATE = new Outcome(Fate.INDETERMINATE, 8);
    private static final Outcome FAILED = new Outcome(Fate.FAILED, 9);
    private static final Outcome FAILED_UNPARSED = new Outcome(Fate.FAILED_UNPARSED, 10);

    private static String maven() {
        return "maven:" + words().next();
    }

    private static String npm() {
        return "npm:" + words().next() + "@" + words().next();
    }

    /** @return a Maven compiler error line over a generated Java file and position */
    private static String mavenError() {
        return "[ERROR] /p/%s.java:[%d,%d] cannot find symbol".formatted(Generators.letterStrings(1, 16).next(),
                Generators.integers(1, 9999).next(), Generators.integers(1, 120).next());
    }

    @Test
    @DisplayName("a Maven BUILD SUCCESS log decides succeeded")
    void mavenSuccess() {
        var parse = PARSER.parse(job(JobId.Kind.BUILD, maven()), """
                [INFO] BUILD SUCCESS
                [INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
                """);

        assertAll(
                () -> assertTrue(parse.successMarker(), "the Maven success marker was found"),
                () -> assertEquals(0, parse.diagnostics(), "a green Maven log files no diagnostic"),
                () -> assertFalse(parse.contradictingEvidence(), "a green Maven log contradicts nothing"),
                () -> assertEquals(SUCCEEDED, outcome(parse, 0), "a green Maven log decides succeeded"));
    }

    @Test
    @DisplayName("a warning beside a green build contradicts nothing")
    void warningBesideAGreenBuild() {
        var parse = PARSER.parse(job(JobId.Kind.BUILD, maven()), """
                [WARNING] /p/%s.java:[%d,1] deprecated API
                [INFO] BUILD SUCCESS
                """.formatted(Generators.letterStrings(1, 16).next(), Generators.integers(1, 9999).next()));

        assertAll(
                () -> assertEquals(1, parse.diagnostics(), "the warning was located"),
                () -> assertFalse(parse.contradictingEvidence(), "a warning is not a contradiction"),
                () -> assertEquals(SUCCEEDED, outcome(parse, 0), "a warning beside success still succeeds"));
    }

    @Test
    @DisplayName("a Maven build with a parsed error decides failed")
    void mavenFailure() {
        var parse = PARSER.parse(job(JobId.Kind.BUILD, maven()), mavenError() + "\n[INFO] BUILD FAILURE\n");

        assertAll(
                () -> assertEquals(1, parse.diagnostics(), "the compiler error was located"),
                () -> assertTrue(parse.contradictingEvidence(), "the error contradicts a success"),
                () -> assertEquals(FAILED, outcome(parse, failures().next()), "a parsed Maven error decides failed"));
    }

    @Test
    @DisplayName("a Gradle BUILD SUCCESSFUL log decides succeeded")
    void gradleSuccess() {
        var parse = PARSER.parse(job(JobId.Kind.BUILD, "gradle:" + words().next()),
                "BUILD SUCCESSFUL in " + Generators.integers(1, 999).next() + "s\n");

        assertEquals(SUCCEEDED, outcome(parse, 0), "a Gradle success log decides succeeded");
    }

    @Test
    @DisplayName("an npm error log decides failed")
    void npmError() {
        var parse = PARSER.parse(job(JobId.Kind.BUILD, npm()), """
                npm ERR! code E404
                npm ERR! 404 'missing' is not in this registry
                """);

        assertAll(
                () -> assertEquals(1, parse.diagnostics(), "the npm error block was filed once"),
                () -> assertEquals(FAILED, outcome(parse, failures().next()), "an npm error log decides failed"));
    }

    @Test
    @DisplayName("a TypeScript log decides failed")
    void typeScript() {
        var parse = PARSER.parse(job(JobId.Kind.BUILD, npm()), """
                src/a.ts(1,2): error TS2304: Cannot find name 'x'.
                """);

        assertAll(
                () -> assertEquals(1, parse.diagnostics(), "the tsc error was located"),
                () -> assertEquals(FAILED, outcome(parse, failures().next()), "a TypeScript error decides failed"));
    }

    @Test
    @DisplayName("an ESLint log decides failed")
    void eslint() {
        var parse = PARSER.parse(job(JobId.Kind.BUILD, npm()), """
                /web/src/a.js
                  1:10  error  Unexpected var  no-var

                ✖ 1 problem (1 error, 0 warnings)
                """);

        assertAll(
                () -> assertEquals(1, parse.diagnostics(), "the ESLint row was located"),
                () -> assertEquals(FAILED, outcome(parse, failures().next()), "an ESLint error decides failed"));
    }

    @Test
    @DisplayName("an ESLint summary alone is contradicting evidence")
    void eslintSummaryAlone() {
        var parse = PARSER.parse(job(JobId.Kind.BUILD, npm()), """
                /web/src/a.js

                ✖ 1 problem (1 error, 0 warnings)
                """);

        assertAll(
                () -> assertEquals(0, parse.diagnostics(), "no row was placed in the parse result"),
                () -> assertTrue(parse.contradictingEvidence(), "the summary's error contradicts a success"));
    }

    @Test
    @DisplayName("a Jest log decides failed")
    void jest() {
        var parse = PARSER.parse(job(JobId.Kind.BUILD, npm()), """
                FAIL src/a.test.ts
                  ● adds two numbers
                    expect(received).toBe(expected)

                    at Object.<anonymous> (src/a.test.ts:4:5)

                Tests:       1 failed, 1 total
                """);

        assertAll(
                () -> assertTrue(parse.diagnostics() > 0, "the failed test was located"),
                () -> assertEquals(FAILED, outcome(parse, failures().next()), "a failed Jest test decides failed"));
    }

    @Test
    @DisplayName("a TAP log decides failed")
    void tap() {
        var parse = PARSER.parse(job(JobId.Kind.BUILD, npm()), """
                TAP version 13
                1..1
                not ok 1 - adds
                  ---
                  message: boom
                  ...
                # tests 1
                # fail 1
                """);

        assertAll(
                () -> assertTrue(parse.diagnostics() > 0, "the failed test was located"),
                () -> assertEquals(FAILED, outcome(parse, failures().next()), "a failed TAP test decides failed"));
    }

    @Test
    @DisplayName("a pyproject failure decides failed and a passed summary decides succeeded")
    void pyproject() {
        var budgetKey = "pyproject:" + words().next();
        var failure = PARSER.parse(job(JobId.Kind.BUILD, budgetKey), """
                FAILED tests/test_a.py::test_x - AssertionError: x
                1 failed in 0.10s
                """);
        var success = PARSER.parse(job(JobId.Kind.BUILD, budgetKey), "===== 1 passed in 0.10s =====\n");

        assertAll(
                () -> assertEquals(FAILED, outcome(failure, failures().next()), "a pytest failure decides failed"),
                () -> assertTrue(success.successMarker(), "the pytest passed summary is the positive marker"),
                () -> assertEquals(SUCCEEDED, outcome(success, 0), "a pytest passed summary succeeds"));
    }

    @Test
    @DisplayName("a passing Jest run decides succeeded")
    void jestPassingRun() {
        var parse = PARSER.parse(job(JobId.Kind.BUILD, npm()), """
                PASS src/a.test.ts
                Tests:       5 passed, 5 total
                """);

        assertAll(
                () -> assertTrue(parse.successMarker(), "the Jest passing summary is the marker"),
                () -> assertEquals(SUCCEEDED, outcome(parse, 0), "a passing Jest run decides succeeded"));
    }

    @Test
    @DisplayName("a passing TAP run decides succeeded")
    void tapPassingRun() {
        var parse = PARSER.parse(job(JobId.Kind.BUILD, npm()), """
                TAP version 13
                1..5
                ok 1 - a
                ok 2 - b
                ok 3 - c
                ok 4 - d
                ok 5 - e
                # tests 5
                # pass 5
                # fail 0
                """);

        assertAll(
                () -> assertTrue(parse.successMarker(), "the TAP passing summary is the marker"),
                () -> assertEquals(SUCCEEDED, outcome(parse, 0), "a passing TAP run decides succeeded"));
    }

    @Test
    @DisplayName("an unmeasured Jest summary stays indeterminate")
    void jestUnmeasuredSummary() {
        var parse = PARSER.parse(job(JobId.Kind.BUILD, npm()), """
                PASS src/a.test.ts
                Tests:       7 total
                """);

        assertAll(
                () -> assertFalse(parse.successMarker(), "an unmeasured summary is no marker"),
                () -> assertEquals(INDETERMINATE, outcome(parse, 0), "an unmeasured summary stays indeterminate"));
    }

    @Test
    @DisplayName("a measured TAP zero stays indeterminate")
    void tapZeroCount() {
        var parse = PARSER.parse(job(JobId.Kind.BUILD, npm()), """
                TAP version 13
                1..0
                # tests 0
                # pass 0
                # fail 0
                # skipped 0
                """);

        assertAll(
                () -> assertFalse(parse.successMarker(), "a measured zero is no marker"),
                () -> assertEquals(INDETERMINATE, outcome(parse, 0), "a measured zero stays indeterminate"));
    }

    @Test
    @DisplayName("an all-skipped Jest run stays indeterminate")
    void jestAllSkipped() {
        var parse = PARSER.parse(job(JobId.Kind.BUILD, npm()), """
                PASS src/a.test.ts
                Tests:       3 skipped, 3 total
                """);

        assertAll(
                () -> assertFalse(parse.successMarker(), "a skipped-only run is no marker"),
                () -> assertEquals(INDETERMINATE, outcome(parse, 0), "an all-skipped run stays indeterminate"));
    }

    @Test
    @DisplayName("a zero count alone is not a success marker")
    void zeroCountIsNoMarker() {
        var parse = PARSER.parse(job(JobId.Kind.BUILD, npm()), """
                PASS src/a.test.ts
                Tests:       0 total
                """);

        assertAll(
                () -> assertFalse(parse.successMarker(), "Jest prints no positive success marker"),
                () -> assertEquals(INDETERMINATE, outcome(parse, 0), "a zero count decides indeterminate"));
    }

    @Test
    @DisplayName("a success banner over an error is not a success")
    void successBannerOverAnError() {
        var parse = PARSER.parse(job(JobId.Kind.BUILD, maven()), mavenError() + "\n[INFO] BUILD SUCCESS\n");

        assertAll(
                () -> assertTrue(parse.successMarker(), "the success banner is present"),
                () -> assertTrue(parse.contradictingEvidence(), "the error contradicts the banner"),
                () -> assertEquals(INDETERMINATE, outcome(parse, 0), "a contradicted banner is indeterminate"));
    }

    @Test
    @DisplayName("an install completion line decides succeeded")
    void installSummary() {
        var parse = PARSER.parse(job(JobId.Kind.BUILD, npm()), "up to date, audited 5 packages\n");

        assertAll(
                () -> assertTrue(parse.successMarker(), "the install completion line is the marker"),
                () -> assertEquals(SUCCEEDED, outcome(parse, 0), "a clean install decides succeeded"));
    }

    @Test
    @DisplayName("a log no parser recognises yields nothing")
    void unrecognisedLog() {
        var parse = PARSER.parse(job(JobId.Kind.BUILD, npm()), "compiling something\n");

        assertAll(
                () -> assertEquals(ParseResult.NOTHING, parse, "an unrecognised log parses to nothing"),
                () -> assertEquals(INDETERMINATE, outcome(parse, 0), "nothing with exit 0 is indeterminate"),
                () -> assertEquals(FAILED_UNPARSED, outcome(parse, failures().next()),
                        "nothing with a failing exit is unparsed"));
    }

    @Test
    @DisplayName("a clean tsc or ESLint log prints nothing, so exit 0 stays indeterminate")
    void cleanSyntaxToolLogs() {
        var typeScript = PARSER.parse(job(JobId.Kind.BUILD, npm()), "");
        var eslint = PARSER.parse(job(JobId.Kind.BUILD, npm()), "");

        assertAll(
                () -> assertEquals(ParseResult.NOTHING, typeScript, "a clean tsc run prints no marker"),
                () -> assertEquals(INDETERMINATE, outcome(typeScript, 0), "a clean tsc run stays indeterminate"),
                () -> assertEquals(ParseResult.NOTHING, eslint, "a clean ESLint run prints no marker"),
                () -> assertEquals(INDETERMINATE, outcome(eslint, 0), "a clean ESLint run stays indeterminate"));
    }

    // Build systems the runtime knows no parser for: contract literals of the budget key.
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"cargo", "make"})
    @DisplayName("a key of an unknown build system yields nothing")
    void unknownBuildSystem(String buildSystem) {
        var parse = PARSER.parse(job(JobId.Kind.BUILD, buildSystem + ":" + words().next()), mavenError() + "\n");

        assertEquals(ParseResult.NOTHING, parse, "an unknown build system parses to nothing");
    }

    @Test
    @DisplayName("a job of another kind yields nothing")
    void otherKind() {
        var parse = PARSER.parse(job(JobId.Kind.GIT, maven()), "BUILD SUCCESS\n");

        assertEquals(ParseResult.NOTHING, parse, "a git job parses to nothing");
    }

    private static Outcome outcome(ParseResult parse, int exitCode) {
        return OutcomeDerivation.derive(new JobEvidence.ProcessEnded(JobId.Kind.BUILD, Optional.empty(),
                new Exited(exitCode), LogState.READABLE, parse, Set.of(), true));
    }

    private static JobRecord job(JobId.Kind kind, String budgetKey) {
        return JobRecord.builder().jobId(new JobId(RuntimeGenerators.jobIds(kind).next()))
                .execution(Execution.PROCESS).scope(new Scope(Scope.Type.PLAN, "plan-" + words().next()))
                .budgetKey(budgetKey).budgetSeconds(Generators.integers(10, 3600).next())
                .budgetDerivation(new BudgetDerivation(BudgetDerivation.Kind.FLOOR_DEFAULT, null))
                .argvDigest(RuntimeGenerators.sha256Digests().next()).status(Status.RUNNING)
                .confinement(Confinement.CONFINED)
                .createdAt(Instant.ofEpochSecond(Generators.longs(0, 4_000_000_000L).next()))
                .clampedValues(List.of()).build();
    }

    @Nested
    @DisplayName("the npm tool markers")
    class NpmMarkers {

        @Test
        @DisplayName("select the TypeScript scanner from the TS code before the npm error parser")
        void typeScriptBeforeNpmErrors() {
            var parse = PARSER.parse(job(JobId.Kind.BUILD, npm()), """
                    npm ERR! code ELIFECYCLE
                    npm ERR! code ENOENT
                    src/a.ts(1,2): error TS2304: Cannot find name 'x'.
                    """);

            assertAll(
                    () -> assertEquals(1, parse.diagnostics(), "only the tsc error is filed, not the npm blocks"),
                    () -> assertTrue(parse.contradictingEvidence(), "the tsc error contradicts a success"));
        }

        @Test
        @DisplayName("select the npm parser from a colour-coded log")
        void colourCodedNpmErrors() {
            var parse = PARSER.parse(job(JobId.Kind.BUILD, npm()), """
                    \u001B[31mnpm ERR!\u001B[39m code E404
                    \u001B[31mnpm ERR!\u001B[39m 404 'missing' is not in this registry
                    """);

            assertAll(
                    () -> assertEquals(1, parse.diagnostics(), "the stripped npm error block is filed"),
                    () -> assertEquals(FAILED, outcome(parse, failures().next()), "a colour-coded npm error fails"));
        }
    }
}
