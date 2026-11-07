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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.store.JobRecord.Confinement;
import de.planmarshall.runtime.job.LaunchReport.Reported;
import de.planmarshall.runtime.job.LaunchReport.Unreported;

/**
 * The launch report of {@code pm-exec} (PM-EXEC-4): its fields are taken from exactly the first line of the job log,
 * the job's own output follows it unchanged, a report-shaped first line of the job is kept, and a log without a report
 * with the launcher's own exit code is a launcher failure.
 * <p>
 * Contract literals that stay: the wire format of the report (member names, the confinement values, the Landlock ABI),
 * the first lines of each job kind's own output, the malformed first lines that are no report (each breaks one rule of
 * the format), the launcher's own exit codes and its error line. The PID, the start instant and the Landlock ABI of the
 * well-formed report and every other exit code are generated.
 */
@EnableGeneratorController
@DisplayName("Launch report")
class LaunchReportTest {

    private long pid;
    private Instant startedAt;
    private int abi;
    private String report;

    @BeforeEach
    void drawReport() {
        pid = Generators.integers(2, 4_000_000).next();
        startedAt = Instant.ofEpochMilli(Generators.longs(0, 4_000_000_000_000L).next());
        // A recorded ruleset has at least ABI 2: ABI 1 is the confinement partial(abi=1), not a confined report.
        abi = Generators.integers(2, 5).next();
        report = "{\"pid\":" + pid + ",\"process_started_at\":\"" + startedAt + "\",\"confinement\":\"confined\","
                + "\"landlock_abi\":" + abi + "}";
    }

    @Nested
    @DisplayName("a report")
    class Reports {

        // The first line of each job kind's own output: golden samples of the kinds' output formats.
        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', value = {
                "build | [INFO] BUILD SUCCESS",
                "git | ok",
                "ci | {\"state\":\"green\"}",
                "project_script | {\"status\":\"passed\"}",
                "llm | {\"type\":\"result\"}",
                "probe | 1.2.3"})
        @DisplayName("its fields are taken and the stored log starts with the job's own first line")
        void fieldsAndOutput(String kind, String firstJobLine) {
            var more = Generators.letterStrings(1, 32).next() + " " + kind;
            var log = report + "\n" + firstJobLine + "\n" + more + "\n";

            var reading = assertInstanceOf(Reported.class, LaunchReport.read(log), "the log carries a report");

            assertAll(
                    () -> assertEquals(new LaunchReport(pid, startedAt, Confinement.CONFINED, abi), reading.report(),
                            "the report fields are taken from line one"),
                    () -> assertEquals(firstJobLine + "\n" + more + "\n", reading.jobOutput(),
                            "the job output follows the report unchanged"),
                    () -> assertTrue(reading.jobOutput().startsWith(firstJobLine),
                            "the job output starts with the job's line"));
        }

        @Test
        @DisplayName("a report-shaped first line of the job is kept: exactly one line is stripped")
        void reportShapedJobLine() {
            var jobLine = "{\"pid\":" + Generators.integers(2, 4_000_000).next() + ",\"process_started_at\":\""
                    + Instant.ofEpochSecond(Generators.longs(0, 4_000_000_000L).next())
                    + "\",\"confinement\":\"unavailable\",\"landlock_abi\":null}";
            var log = report + "\n" + jobLine + "\n";

            var reading = assertInstanceOf(Reported.class, LaunchReport.read(log), "the log carries a report");

            assertAll(
                    () -> assertEquals(pid, reading.report().pid(), "the first line is the report"),
                    () -> assertEquals(jobLine + "\n", reading.jobOutput(), "the report-shaped job line is kept"));
        }

        @Test
        @DisplayName("a report without a ruleset has no Landlock ABI, and an empty job output stays empty")
        void withoutRuleset() {
            var log = "{\"pid\":" + pid + ",\"process_started_at\":\"" + startedAt + "\","
                    + "\"confinement\":\"partial(abi=1)\",\"landlock_abi\":null}\n";

            var reading = assertInstanceOf(Reported.class, LaunchReport.read(log), "the log carries a report");

            assertAll(
                    () -> assertEquals(Confinement.PARTIAL_ABI_1, reading.report().confinement(),
                            "the confinement is partial(abi=1)"),
                    () -> assertNull(reading.report().landlockAbi(), "a report without ruleset has no ABI"),
                    () -> assertEquals("", reading.jobOutput(), "an empty job output stays empty"));
        }
    }

    @Nested
    @DisplayName("no report")
    class NoReport {

        // Each first line breaks one rule of the report format: contract boundary literals.
        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {
                "",
                "pm-exec: usage: pm-exec -- <executable> [args]\n",
                "[INFO] Scanning for projects\n",
                "{\"pid\":4242}\n",
                "{\"pid\":0,\"process_started_at\":\"2026-10-17T10:00:00Z\","
                        + "\"confinement\":\"confined\",\"landlock_abi\":5}\n",
                "{\"pid\":1,\"process_started_at\":\"yesterday\","
                        + "\"confinement\":\"confined\",\"landlock_abi\":5}\n",
                "{\"pid\":1,\"process_started_at\":\"2026-10-17T10:00:00Z\","
                        + "\"confinement\":\"sandboxed\",\"landlock_abi\":5}\n",
                "{\"pid\":1,\"process_started_at\":\"2026-10-17T10:00:00Z\","
                        + "\"confinement\":\"confined\",\"landlock_abi\":5,\"x\":1}\n",
                "{\"pid\":1,\"process_started_at\":\"2026-10-17T10:00:00Z\","
                        + "\"confinement\":\"confined\",\"landlock_abi\":0}\n",
                "{\"pid\":1,\"process_started_at\":\"2026-10-17T10:00:00Z\","
                        + "\"confinement\":\"confined\",\"landlock_abi\":5}"})
        @DisplayName("a first line that is no complete report strips nothing")
        void unreported(String log) {
            var reading = assertInstanceOf(Unreported.class, LaunchReport.read(log), "the log carries no report");

            assertAll(
                    () -> assertEquals(log, reading.jobOutput(), "nothing is stripped from the log"),
                    () -> assertFalse(reading.detail().isBlank(), "the missing report carries a detail"));
        }

        // The launcher's own exit codes: contract literals.
        @ParameterizedTest(name = "exit {0}")
        @ValueSource(ints = {64, 126, 127})
        @DisplayName("without a report, the launcher's own exit codes are a launcher failure")
        void launcherFailure(int exitCode) {
            var reading = LaunchReport.read("pm-exec: execve failed\n");

            assertTrue(LaunchReport.isLauncherFailure(reading, exitCode),
                    "the launcher's exit code is a launcher failure");
        }

        @Test
        @DisplayName("another exit code, or a report present, is no launcher failure")
        void noLauncherFailure() {
            var unreported = LaunchReport.read("pm-exec: execve failed\n");
            var reported = LaunchReport.read(report + "\n");
            var otherExitCode = Generators.integers(1, 63).next();

            assertAll(
                    () -> assertFalse(LaunchReport.isLauncherFailure(unreported, otherExitCode),
                            "another exit code is no launcher failure"),
                    () -> assertFalse(LaunchReport.isLauncherFailure(reported, 127),
                            "a reported launch is no launcher failure"),
                    () -> assertThrows(NullPointerException.class, () -> LaunchReport.read(null),
                            "a null log is refused"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new LaunchReport(0, Instant.EPOCH, Confinement.CONFINED, null),
                            "a PID of 0 is refused"));
        }
    }
}
