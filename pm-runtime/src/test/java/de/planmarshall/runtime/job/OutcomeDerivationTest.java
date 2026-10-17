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
 */
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
            assertEquals(new Outcome(fate, row), OutcomeDerivation.derive(evidence));
        }

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
                            new Exited(0), LogState.READABLE, SUCCESS))),
                    () -> assertEquals(expected, OutcomeDerivation.derive(
                            new InProcessEnded(Optional.of(termination), InProcessResult.DOMAIN))));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(NeverStarted.Cause.class)
        @DisplayName("a job that never got an observed end is killed, without a signal")
        void neverStarted(NeverStarted.Cause cause) {
            assertEquals(new Outcome(Fate.KILLED, 4), OutcomeDerivation.derive(new NeverStarted(cause)));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = LogState.class, names = {"MISSING", "TRUNCATED", "UNREADABLE"})
        @DisplayName("a log that cannot be read is indeterminate, also for exit code 0 and a success marker")
        void unreadableLog(LogState log) {
            assertEquals(new Outcome(Fate.INDETERMINATE, 5),
                    OutcomeDerivation.derive(process(Kind.BUILD, null, new Exited(0), log, SUCCESS)));
        }
    }

    @Nested
    @DisplayName("first match")
    class FirstMatch {

        @Test
        @DisplayName("a cancel decides over a non-zero exit with diagnostics")
        void cancelOverFailure() {
            var evidence = process(Kind.BUILD, Termination.ENGINE_CANCEL, new Exited(1), LogState.READABLE,
                    DIAGNOSTICS);

            assertEquals(new Outcome(Fate.CANCELLED, 2), OutcomeDerivation.derive(evidence));
        }

        @Test
        @DisplayName("a budget expiry decides over a crash signal")
        void budgetOverCrash() {
            var evidence = process(Kind.BUILD, Termination.BUDGET_EXPIRED, new Signalled("SIGSEGV"),
                    LogState.READABLE, NO_MARKER);

            assertEquals(new Outcome(Fate.TIMED_OUT, 3), OutcomeDerivation.derive(evidence));
        }

        @Test
        @DisplayName("a missing log decides over exit code 0 with a success marker")
        void missingLogOverSuccess() {
            var evidence = process(Kind.BUILD, null, new Exited(0), LogState.MISSING, SUCCESS);

            assertEquals(new Outcome(Fate.INDETERMINATE, 5), OutcomeDerivation.derive(evidence));
        }

        @Test
        @DisplayName("a self-reported success never decides: the contradiction makes the job indeterminate")
        void selfReportNeverAdopted() {
            var evidence = exited(Kind.PROJECT_SCRIPT, 0, CONTRADICTED);

            assertEquals(new Outcome(Fate.INDETERMINATE, 6), OutcomeDerivation.derive(evidence));
        }

        @Test
        @DisplayName("contradicting evidence keeps exit code 0 with a marker out of succeeded")
        void contradictingEvidence() {
            var evidence = exited(Kind.BUILD, 0, new ParseResult(true, 0, true, Optional.empty()));

            assertEquals(new Outcome(Fate.INDETERMINATE, 8), OutcomeDerivation.derive(evidence));
        }
    }

    @Nested
    @DisplayName("success markers per kind")
    class Markers {

        @Test
        @DisplayName("a probe succeeds with an exit code it declares, and not with another")
        void probe() {
            var declared = new ProcessEnded(Kind.PROBE, Optional.empty(), new Exited(2), LogState.READABLE,
                    NO_MARKER, Set.of(0, 2), true);
            var undeclared = new ProcessEnded(Kind.PROBE, Optional.empty(), new Exited(3), LogState.READABLE,
                    NO_MARKER, Set.of(0, 2), true);
            var zeroUndeclared = new ProcessEnded(Kind.PROBE, Optional.empty(), new Exited(0), LogState.READABLE,
                    SUCCESS, Set.of(2), true);

            assertAll(
                    () -> assertEquals(new Outcome(Fate.SUCCEEDED, 7), OutcomeDerivation.derive(declared)),
                    () -> assertEquals(new Outcome(Fate.FAILED, 10), OutcomeDerivation.derive(undeclared)),
                    () -> assertEquals(new Outcome(Fate.INDETERMINATE, 8), OutcomeDerivation.derive(zeroUndeclared)));
        }

        @Test
        @DisplayName("a worker with an unreliable exit code is decided by its final result event")
        void unreliableWorker() {
            var withResult = new ProcessEnded(Kind.LLM, Optional.empty(), new Exited(1), LogState.READABLE, SUCCESS,
                    Set.of(), false);
            var withoutResult = new ProcessEnded(Kind.LLM, Optional.empty(), new Exited(0), LogState.READABLE,
                    NO_MARKER, Set.of(), false);

            assertAll(
                    () -> assertEquals(new Outcome(Fate.SUCCEEDED, 7), OutcomeDerivation.derive(withResult)),
                    () -> assertEquals(new Outcome(Fate.FAILED, 10), OutcomeDerivation.derive(withoutResult)));
        }

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
            assertEquals(new Outcome(fate, 10), OutcomeDerivation.derive(exited(kind, 1, NO_MARKER)));
        }

        @Test
        @DisplayName("a contradiction counts for a project script only")
        void contradictionOfOtherKinds() {
            assertEquals(new Outcome(Fate.SUCCEEDED, 7), OutcomeDerivation.derive(exited(Kind.CI, 0, CONTRADICTED)));
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @Test
        @DisplayName("evidence outside its kind's shape is refused")
        void invalidEvidence() {
            assertAll(
                    () -> assertThrows(NullPointerException.class, () -> OutcomeDerivation.derive(null)),
                    () -> assertThrows(IllegalArgumentException.class, () -> new ProcessEnded(Kind.BUILD,
                            Optional.empty(), new Exited(0), LogState.READABLE, SUCCESS, Set.of(0), true)),
                    () -> assertThrows(IllegalArgumentException.class, () -> new ProcessEnded(Kind.BUILD,
                            Optional.empty(), new Exited(0), LogState.READABLE, SUCCESS, Set.of(), false)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new ParseResult(false, -1, false, Optional.empty())),
                    () -> assertThrows(IllegalArgumentException.class, () -> new Outcome(Fate.FAILED, 14)));
        }
    }
}
