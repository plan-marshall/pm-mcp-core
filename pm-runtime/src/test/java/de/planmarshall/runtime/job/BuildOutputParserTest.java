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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

/**
 * The adapter from the native build parsers to the runner's parser seam: the budget key selects the build system and
 * the log's markers select among the npm tools, the positive success marker of the parsed build is the only evidence of
 * a success, and a log no parser recognises yields nothing. Each case derives the outcome the runner would record, so a
 * green banner, a failure marker and a log without either are checked against the outcome table rather than against the
 * parser alone.
 */
@DisplayName("BuildOutputParser")
class BuildOutputParserTest {

    private static final String PLAN = "plan-one";
    private static final BuildOutputParser PARSER = new BuildOutputParser();

    @Test
    @DisplayName("a Maven BUILD SUCCESS log decides succeeded")
    void mavenSuccess() {
        var parse = PARSER.parse(job(JobId.Kind.BUILD, "maven:verify"), """
                [INFO] BUILD SUCCESS
                [INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
                """);

        assertAll(
                () -> assertTrue(parse.successMarker(), "the Maven success marker was found"),
                () -> assertEquals(0, parse.diagnostics(), "a green Maven log files no diagnostic"),
                () -> assertFalse(parse.contradictingEvidence(), "a green Maven log contradicts nothing"),
                () -> assertEquals(new Outcome(Fate.SUCCEEDED, 7), outcome(parse, 0)));
    }

    @Test
    @DisplayName("a warning beside a green build contradicts nothing")
    void warningBesideAGreenBuild() {
        var parse = PARSER.parse(job(JobId.Kind.BUILD, "maven:verify"), """
                [WARNING] /p/Foo.java:[1,1] deprecated API
                [INFO] BUILD SUCCESS
                """);

        assertAll(
                () -> assertEquals(1, parse.diagnostics(), "the warning was located"),
                () -> assertFalse(parse.contradictingEvidence(), "a warning is not a contradiction"),
                () -> assertEquals(new Outcome(Fate.SUCCEEDED, 7), outcome(parse, 0)));
    }

    @Test
    @DisplayName("a Maven build with a parsed error decides failed")
    void mavenFailure() {
        var parse = PARSER.parse(job(JobId.Kind.BUILD, "maven:verify"), """
                [ERROR] /p/Foo.java:[7,3] cannot find symbol
                [INFO] BUILD FAILURE
                """);

        assertAll(
                () -> assertEquals(1, parse.diagnostics(), "the compiler error was located"),
                () -> assertTrue(parse.contradictingEvidence(), "the error contradicts a success"),
                () -> assertEquals(new Outcome(Fate.FAILED, 9), outcome(parse, 1)));
    }

    @Test
    @DisplayName("a Gradle BUILD SUCCESSFUL log decides succeeded")
    void gradleSuccess() {
        var parse = PARSER.parse(job(JobId.Kind.BUILD, "gradle:verify"), "BUILD SUCCESSFUL in 3s\n");

        assertEquals(new Outcome(Fate.SUCCEEDED, 7), outcome(parse, 0));
    }

    @Test
    @DisplayName("an npm error log decides failed")
    void npmError() {
        var parse = PARSER.parse(job(JobId.Kind.BUILD, "npm:verify@web"), """
                npm ERR! code E404
                npm ERR! 404 'missing' is not in this registry
                """);

        assertAll(
                () -> assertEquals(1, parse.diagnostics(), "the npm error block was filed once"),
                () -> assertEquals(new Outcome(Fate.FAILED, 9), outcome(parse, 1)));
    }

    @Test
    @DisplayName("a TypeScript log decides failed")
    void typeScript() {
        var parse = PARSER.parse(job(JobId.Kind.BUILD, "npm:verify@web"), """
                src/a.ts(1,2): error TS2304: Cannot find name 'x'.
                """);

        assertAll(
                () -> assertEquals(1, parse.diagnostics(), "the tsc error was located"),
                () -> assertEquals(new Outcome(Fate.FAILED, 9), outcome(parse, 2)));
    }

    @Test
    @DisplayName("an ESLint log decides failed")
    void eslint() {
        var parse = PARSER.parse(job(JobId.Kind.BUILD, "npm:verify@web"), """
                /web/src/a.js
                  1:10  error  Unexpected var  no-var

                ✖ 1 problem (1 error, 0 warnings)
                """);

        assertAll(
                () -> assertEquals(1, parse.diagnostics(), "the ESLint row was located"),
                () -> assertEquals(new Outcome(Fate.FAILED, 9), outcome(parse, 1)));
    }

    @Test
    @DisplayName("an ESLint summary alone is contradicting evidence")
    void eslintSummaryAlone() {
        var parse = PARSER.parse(job(JobId.Kind.BUILD, "npm:verify@web"), """
                /web/src/a.js

                ✖ 1 problem (1 error, 0 warnings)
                """);

        assertAll(
                () -> assertEquals(0, parse.diagnostics(), "no row was placed"),
                () -> assertTrue(parse.contradictingEvidence(), "the summary's error contradicts a success"));
    }

    @Test
    @DisplayName("a Jest log decides failed")
    void jest() {
        var parse = PARSER.parse(job(JobId.Kind.BUILD, "npm:verify@web"), """
                FAIL src/a.test.ts
                  ● adds two numbers
                    expect(received).toBe(expected)

                    at Object.<anonymous> (src/a.test.ts:4:5)

                Tests:       1 failed, 1 total
                """);

        assertAll(
                () -> assertTrue(parse.diagnostics() > 0, "the failed test was located"),
                () -> assertEquals(new Outcome(Fate.FAILED, 9), outcome(parse, 1)));
    }

    @Test
    @DisplayName("a TAP log decides failed")
    void tap() {
        var parse = PARSER.parse(job(JobId.Kind.BUILD, "npm:verify@web"), """
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
                () -> assertEquals(new Outcome(Fate.FAILED, 9), outcome(parse, 1)));
    }

    @Test
    @DisplayName("a pyproject failure decides failed and a passed summary decides succeeded")
    void pyproject() {
        var failure = PARSER.parse(job(JobId.Kind.BUILD, "pyproject:verify"), """
                FAILED tests/test_a.py::test_x - AssertionError: x
                1 failed in 0.10s
                """);
        var success = PARSER.parse(job(JobId.Kind.BUILD, "pyproject:verify"), "===== 1 passed in 0.10s =====\n");

        assertAll(
                () -> assertEquals(new Outcome(Fate.FAILED, 9), outcome(failure, 1)),
                () -> assertTrue(success.successMarker(), "the pytest passed summary is the positive marker"),
                () -> assertEquals(new Outcome(Fate.SUCCEEDED, 7), outcome(success, 0)));
    }

    @Test
    @DisplayName("a zero count alone is not a success marker")
    void zeroCountIsNoMarker() {
        var parse = PARSER.parse(job(JobId.Kind.BUILD, "npm:verify@web"), """
                PASS src/a.test.ts
                Tests:       0 total
                """);

        assertAll(
                () -> assertFalse(parse.successMarker(), "Jest prints no positive success marker"),
                () -> assertEquals(new Outcome(Fate.INDETERMINATE, 8), outcome(parse, 0)));
    }

    @Test
    @DisplayName("a success banner over an error is not a success")
    void successBannerOverAnError() {
        var parse = PARSER.parse(job(JobId.Kind.BUILD, "maven:verify"), """
                [ERROR] /p/Foo.java:[7,3] cannot find symbol
                [INFO] BUILD SUCCESS
                """);

        assertAll(
                () -> assertTrue(parse.successMarker(), "the success banner is present"),
                () -> assertTrue(parse.contradictingEvidence(), "the error contradicts the banner"),
                () -> assertEquals(new Outcome(Fate.INDETERMINATE, 8), outcome(parse, 0)));
    }

    @Test
    @DisplayName("a log no parser recognises yields nothing")
    void unrecognisedLog() {
        var parse = PARSER.parse(job(JobId.Kind.BUILD, "npm:verify@web"), "up to date, audited 5 packages\n");

        assertAll(
                () -> assertEquals(ParseResult.NOTHING, parse),
                () -> assertEquals(new Outcome(Fate.INDETERMINATE, 8), outcome(parse, 0)),
                () -> assertEquals(new Outcome(Fate.FAILED_UNPARSED, 10), outcome(parse, 1)));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"cargo:build", "make:verify"})
    @DisplayName("a key of an unknown build system yields nothing")
    void unknownBuildSystem(String budgetKey) {
        var parse = PARSER.parse(job(JobId.Kind.BUILD, budgetKey), "[ERROR] /p/Foo.java:[7,3] cannot find symbol\n");

        assertEquals(ParseResult.NOTHING, parse);
    }

    @Test
    @DisplayName("a job of another kind yields nothing")
    void otherKind() {
        var parse = PARSER.parse(job(JobId.Kind.GIT, "maven:verify"), "BUILD SUCCESS\n");

        assertEquals(ParseResult.NOTHING, parse);
    }

    private static Outcome outcome(ParseResult parse, int exitCode) {
        return OutcomeDerivation.derive(new JobEvidence.ProcessEnded(JobId.Kind.BUILD, Optional.empty(),
                new Exited(exitCode), LogState.READABLE, parse, Set.of(), true));
    }

    private static JobRecord job(JobId.Kind kind, String budgetKey) {
        return JobRecord.builder().jobId(new JobId(kind.prefix() + "-0123456789abcdef")).execution(Execution.PROCESS)
                .scope(new Scope(Scope.Type.PLAN, PLAN)).budgetKey(budgetKey).budgetSeconds(300)
                .budgetDerivation(new BudgetDerivation(BudgetDerivation.Kind.FLOOR_DEFAULT, null))
                .argvDigest("ab".repeat(32)).status(Status.RUNNING).confinement(Confinement.CONFINED)
                .createdAt(Instant.parse("2026-10-17T10:00:00Z")).clampedValues(List.of()).build();
    }

    @Nested
    @DisplayName("the npm tool markers")
    class NpmMarkers {

        @Test
        @DisplayName("select the TypeScript scanner from the TS code before the npm error parser")
        void typeScriptBeforeNpmErrors() {
            var parse = PARSER.parse(job(JobId.Kind.BUILD, "npm:verify@web"), """
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
            var parse = PARSER.parse(job(JobId.Kind.BUILD, "npm:verify@web"), """
                    \u001B[31mnpm ERR!\u001B[39m code E404
                    \u001B[31mnpm ERR!\u001B[39m 404 'missing' is not in this registry
                    """);

            assertAll(
                    () -> assertEquals(1, parse.diagnostics(), "the stripped npm error block is filed"),
                    () -> assertEquals(new Outcome(Fate.FAILED, 9), outcome(parse, 1)));
        }
    }
}
