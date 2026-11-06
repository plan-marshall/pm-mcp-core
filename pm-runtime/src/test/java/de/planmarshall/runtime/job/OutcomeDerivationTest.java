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
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.store.JobId.Kind;
import de.planmarshall.core.store.JobRecord.Fate;
import de.planmarshall.core.store.JobRecord.SelfReportContradiction;
import de.planmarshall.runtime.job.JobEvidence.Exited;
import de.planmarshall.runtime.job.JobEvidence.InProcessEnded;
import de.planmarshall.runtime.job.JobEvidence.InProcessResult;
import de.planmarshall.runtime.job.JobEvidence.LogState;
import de.planmarshall.runtime.job.JobEvidence.NeverStarted;
import de.planmarshall.runtime.job.JobEvidence.ParseResult;
import de.planmarshall.runtime.job.JobEvidence.ProcessEnded;
import de.planmarshall.runtime.job.JobEvidence.RefusedBeforeSpawn;
import de.planmarshall.runtime.job.JobEvidence.Signalled;
import de.planmarshall.runtime.job.JobEvidence.Termination;
import de.planmarshall.runtime.job.JobEvidence.WaitStatus;
import de.planmarshall.runtime.job.OutcomeDerivation.Outcome;

/**
 * The outcome derivation of a job (PM-EXEC-4): one case per row of the closed table, the first-match precedence
 * between rows, and the success marker of each kind.
 * <p>
 * The hazard these cases guard: a job reports a status about itself (a project script's {@code status}, a worker's
 * final message). Adopting it as the outcome would let a job that failed report itself successful, so a self-reported
 * status is evidence the derivation checks and never the fate (PM-WATCH-JOB-1).
 * <p>
 * Contract literals that stay: the row numbers and fates of the closed table, its one example evidence per row
 * ({@code rows}, with the exit code 143 of a terminated process and the signal names), the reason code of a refusal,
 * the exit code 0 and the bound 13 of the row numbers. The failing exit codes, the declared probe codes, the
 * diagnostic counts and the self-reported statuses of the cases outside the table are generated.
 */
@EnableGeneratorController
@DisplayName("Outcome derivation")
class OutcomeDerivationTest {

    private static final ParseResult SUCCESS = new ParseResult(true, 0, false, Optional.empty());
    private static final ParseResult NO_MARKER = new ParseResult(false, 0, false, Optional.empty());
    private static final ParseResult DIAGNOSTICS = new ParseResult(false, 2, true, Optional.empty());
    private static final ParseResult CONTRADICTED = new ParseResult(true, 0, false,
            Optional.of(new SelfReportContradiction("passed", 1L, "failed")));

    private static ProcessEnded process(Kind kind, Termination termination, WaitStatus wait, LogState log,
            ParseResult parse) {
        return new ProcessEnded(kind, Optional.ofNullable(termination), wait, log, parse, Set.of(), true);
    }

    private static ProcessEnded exited(Kind kind, int code, ParseResult parse) {
        return process(kind, null, new Exited(code), LogState.READABLE, parse);
    }

    /** @return a generated non-zero exit code */
    private static int failing() {
        return Generators.integers(1, 255).next();
    }

    /** @return a parse result with a generated, positive number of diagnostics and contradicting evidence */
    private static ParseResult diagnostics() {
        return new ParseResult(false, Generators.integers(1, 100).next(), true, Optional.empty());
    }

    /** @return a parse result with a success marker and a generated self-report contradiction */
    private static ParseResult contradicted() {
        return new ParseResult(true, 0, false, Optional.of(new SelfReportContradiction(
                Generators.letterStrings(1, 16).next(), Generators.longs(1, 10_000).next(),
                Generators.letterStrings(1, 16).next())));
    }

    /** The closed table: one example evidence per row, the contract of the derivation. */
    static Stream<Arguments> rows() {
        return Stream.of(
                Arguments.of(1, Fate.REFUSED, new RefusedBeforeSpawn("executable_digest_mismatch")),
                Arguments.of(2, Fate.CANCELLED, process(Kind.BUILD, Termination.ENGINE_CANCEL, new Exited(143),
                        LogState.READABLE, NO_MARKER)),
                Arguments.of(3, Fate.TIMED_OUT, process(Kind.BUILD, Termination.BUDGET_EXPIRED,
                        new Signalled("SIGKILL"), LogState.READABLE, NO_MARKER)),
                Arguments.of(4, Fate.KILLED, process(Kind.BUILD, null, new Signalled("SIGSEGV"), LogState.READABLE,
                        NO_MARKER)),
                Arguments.of(5, Fate.INDETERMINATE, process(Kind.BUILD, null, new Exited(0), LogState.TRUNCATED,
                        SUCCESS)),
                Arguments.of(6, Fate.INDETERMINATE, exited(Kind.PROJECT_SCRIPT, 0, CONTRADICTED)),
                Arguments.of(7, Fate.SUCCEEDED, exited(Kind.BUILD, 0, SUCCESS)),
                Arguments.of(8, Fate.INDETERMINATE, exited(Kind.BUILD, 0, NO_MARKER)),
                Arguments.of(9, Fate.FAILED, exited(Kind.BUILD, 1, DIAGNOSTICS)),
                Arguments.of(10, Fate.FAILED_UNPARSED, exited(Kind.BUILD, 1, NO_MARKER)),
                Arguments.of(11, Fate.SUCCEEDED, new InProcessEnded(Optional.empty(), InProcessResult.DOMAIN)),
                Arguments.of(12, Fate.FAILED, new InProcessEnded(Optional.empty(), InProcessResult.TRANSPORT)),
                Arguments.of(13, Fate.INDETERMINATE,
                        new InProcessEnded(Optional.empty(), InProcessResult.OUT_OF_CONTRACT)));
    }

    @Nested
    @DisplayName("rows")
    class Rows {

        @ParameterizedTest(name = "row {0}: {1}")
        @MethodSource("de.planmarshall.runtime.job.OutcomeDerivationTest#rows")
        @DisplayName("each row of the table derives its fate")
        void row(int row, Fate fate, JobEvidence evidence) {
            assertEquals(new Outcome(fate, row), OutcomeDerivation.derive(evidence),
                    "the row's evidence derives the row's fate");
        }

        // The termination causes with their rows of the closed table: contract literals.
        @ParameterizedTest(name = "{0}")
        @CsvSource({
                "ENGINE_CANCEL, CANCELLED, 2",
                "PAUSE_NOW, CANCELLED, 2",
                "SUPERVISOR_END, CANCELLED, 2",
                "BUDGET_EXPIRED, TIMED_OUT, 3",
                "DRAIN, KILLED, 4",
                "ORPHAN, KILLED, 4"})
        @DisplayName("every termination decides by its row, for a process and for an in-process operation")
        void terminations(Termination termination, Fate fate, int row) {
            var expected = new Outcome(fate, row);

            assertAll(
                    () -> assertEquals(expected, OutcomeDerivation.derive(process(Kind.GIT, termination,
                            new Exited(0), LogState.READABLE, SUCCESS)), "a terminated process decides by its row"),
                    () -> assertEquals(expected, OutcomeDerivation.derive(
                            new InProcessEnded(Optional.of(termination), InProcessResult.DOMAIN)),
                            "a terminated operation decides by its row"));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(NeverStarted.Cause.class)
        @DisplayName("a job that never got an observed end is killed, without a signal")
        void neverStarted(NeverStarted.Cause cause) {
            assertEquals(new Outcome(Fate.KILLED, 4), OutcomeDerivation.derive(new NeverStarted(cause)),
                    "a job never seen to end is killed");
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = LogState.class, names = {"MISSING", "TRUNCATED", "UNREADABLE"})
        @DisplayName("a log that cannot be read is indeterminate, also for exit code 0 and a success marker")
        void unreadableLog(LogState log) {
            assertEquals(new Outcome(Fate.INDETERMINATE, 5),
                    OutcomeDerivation.derive(process(Kind.BUILD, null, new Exited(0), log, SUCCESS)),
                    "an unreadable log is indeterminate");
        }
    }

    @Nested
    @DisplayName("first match")
    class FirstMatch {

        @Test
        @DisplayName("a cancel decides over a non-zero exit with diagnostics")
        void cancelOverFailure() {
            var evidence = process(Kind.BUILD, Termination.ENGINE_CANCEL, new Exited(failing()), LogState.READABLE,
                    diagnostics());

            assertEquals(new Outcome(Fate.CANCELLED, 2), OutcomeDerivation.derive(evidence),
                    "the cancel decides over the failure");
        }

        @Test
        @DisplayName("a budget expiry decides over a crash signal")
        void budgetOverCrash() {
            var evidence = process(Kind.BUILD, Termination.BUDGET_EXPIRED, new Signalled("SIGSEGV"),
                    LogState.READABLE, NO_MARKER);

            assertEquals(new Outcome(Fate.TIMED_OUT, 3), OutcomeDerivation.derive(evidence),
                    "the budget expiry decides over the crash");
        }

        @Test
        @DisplayName("a missing log decides over exit code 0 with a success marker")
        void missingLogOverSuccess() {
            var evidence = process(Kind.BUILD, null, new Exited(0), LogState.MISSING, SUCCESS);

            assertEquals(new Outcome(Fate.INDETERMINATE, 5), OutcomeDerivation.derive(evidence),
                    "the missing log decides over the success");
        }

        @Test
        @DisplayName("a self-reported success never decides: the contradiction makes the job indeterminate")
        void selfReportNeverAdopted() {
            var evidence = exited(Kind.PROJECT_SCRIPT, 0, contradicted());

            assertEquals(new Outcome(Fate.INDETERMINATE, 6), OutcomeDerivation.derive(evidence),
                    "a contradicted self-report is indeterminate");
        }

        @Test
        @DisplayName("contradicting evidence keeps exit code 0 with a marker out of succeeded")
        void contradictingEvidence() {
            var evidence = exited(Kind.BUILD, 0, new ParseResult(true, Generators.integers(0, 100).next(), true,
                    Optional.empty()));

            assertEquals(new Outcome(Fate.INDETERMINATE, 8), OutcomeDerivation.derive(evidence),
                    "contradicting evidence keeps it indeterminate");
        }
    }

    @Nested
    @DisplayName("success markers per kind")
    class Markers {

        @Test
        @DisplayName("a probe succeeds with an exit code it declares, and not with another")
        void probe() {
            var declaredCode = Generators.integers(1, 100).next();
            var undeclaredCode = declaredCode + Generators.integers(1, 100).next();
            var declared = new ProcessEnded(Kind.PROBE, Optional.empty(), new Exited(declaredCode),
                    LogState.READABLE, NO_MARKER, Set.of(0, declaredCode), true);
            var undeclared = new ProcessEnded(Kind.PROBE, Optional.empty(), new Exited(undeclaredCode),
                    LogState.READABLE, NO_MARKER, Set.of(0, declaredCode), true);
            var zeroUndeclared = new ProcessEnded(Kind.PROBE, Optional.empty(), new Exited(0), LogState.READABLE,
                    SUCCESS, Set.of(declaredCode), true);

            assertAll(
                    () -> assertEquals(new Outcome(Fate.SUCCEEDED, 7), OutcomeDerivation.derive(declared),
                            "a declared exit code succeeds"),
                    () -> assertEquals(new Outcome(Fate.FAILED, 10), OutcomeDerivation.derive(undeclared),
                            "an undeclared exit code fails"),
                    () -> assertEquals(new Outcome(Fate.INDETERMINATE, 8), OutcomeDerivation.derive(zeroUndeclared),
                            "an undeclared zero is indeterminate"));
        }

        @Test
        @DisplayName("a worker with an unreliable exit code is decided by its final result event")
        void unreliableWorker() {
            var withResult = new ProcessEnded(Kind.LLM, Optional.empty(), new Exited(failing()), LogState.READABLE,
                    SUCCESS, Set.of(), false);
            var withoutResult = new ProcessEnded(Kind.LLM, Optional.empty(), new Exited(0), LogState.READABLE,
                    NO_MARKER, Set.of(), false);

            assertAll(
                    () -> assertEquals(new Outcome(Fate.SUCCEEDED, 7), OutcomeDerivation.derive(withResult),
                            "a final result event succeeds"),
                    () -> assertEquals(new Outcome(Fate.FAILED, 10), OutcomeDerivation.derive(withoutResult),
                            "no final result event fails"));
        }

        // Each job kind with its fate for a non-zero exit without a diagnostic: contract of row 10.
        @ParameterizedTest(name = "{0}")
        @CsvSource({
                "BUILD, FAILED_UNPARSED",
                "CI, FAILED",
                "GIT, FAILED",
                "PROJECT_SCRIPT, FAILED",
                "LLM, FAILED",
                "PROBE, FAILED"})
        @DisplayName("a non-zero exit without a diagnostic is failed_unparsed for a build job only")
        void failedUnparsed(Kind kind, Fate fate) {
            assertEquals(new Outcome(fate, 10), OutcomeDerivation.derive(exited(kind, failing(), NO_MARKER)),
                    "a bare non-zero exit decides row 10");
        }

        @Test
        @DisplayName("a contradiction counts for a project script only")
        void contradictionOfOtherKinds() {
            assertEquals(new Outcome(Fate.SUCCEEDED, 7), OutcomeDerivation.derive(exited(Kind.CI, 0, contradicted())),
                    "a ci job ignores a self-report");
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @Test
        @DisplayName("evidence outside its kind's shape is refused")
        void invalidEvidence() {
            Optional<Termination> noTermination = Optional.empty();
            var exitedZero = new Exited(0);
            var declaredZero = Set.of(0);
            var noDeclaredCodes = Set.<Integer>of();
            Optional<SelfReportContradiction> noContradiction = Optional.empty();
            var negative = -Generators.integers(1, 100).next();

            assertAll(
                    () -> assertThrows(NullPointerException.class, () -> OutcomeDerivation.derive(null),
                            "null evidence is refused"),
                    () -> assertThrows(IllegalArgumentException.class, () -> new ProcessEnded(Kind.BUILD,
                            noTermination, exitedZero, LogState.READABLE, SUCCESS, declaredZero, true),
                            "declared codes outside a probe are refused"),
                    () -> assertThrows(IllegalArgumentException.class, () -> new ProcessEnded(Kind.BUILD,
                            noTermination, exitedZero, LogState.READABLE, SUCCESS, noDeclaredCodes, false),
                            "an unreliable exit outside a worker is refused"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new ParseResult(false, negative, false, noContradiction),
                            "a negative diagnostic count is refused"),
                    () -> assertThrows(IllegalArgumentException.class, () -> new Outcome(Fate.FAILED, 14),
                            "a row beyond the table is refused"));
        }
    }
}
