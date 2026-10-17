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

import java.time.Instant;
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
 * with the launcher's own exit code is a launcher failure (PM-WATCH-JOB-37).
 */
@DisplayName("Launch report")
class LaunchReportTest {

    private static final String REPORT =
            "{\"pid\":4242,\"process_started_at\":\"2026-10-17T10:00:05.123Z\",\"confinement\":\"confined\","
                    + "\"landlock_abi\":5}";

    @Nested
    @DisplayName("a report")
    class Reports {

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
            var log = REPORT + "\n" + firstJobLine + "\nmore of " + kind + "\n";

            var reading = assertInstanceOf(Reported.class, LaunchReport.read(log));

            assertAll(
                    () -> assertEquals(new LaunchReport(4242, Instant.parse("2026-10-17T10:00:05.123Z"),
                            Confinement.CONFINED, 5), reading.report()),
                    () -> assertEquals(firstJobLine + "\nmore of " + kind + "\n", reading.jobOutput()),
                    () -> assertTrue(reading.jobOutput().startsWith(firstJobLine)));
        }

        @Test
        @DisplayName("a report-shaped first line of the job is kept: exactly one line is stripped")
        void reportShapedJobLine() {
            var jobLine = "{\"pid\":1,\"process_started_at\":\"2026-10-17T10:00:00Z\","
                    + "\"confinement\":\"unavailable\",\"landlock_abi\":null}";
            var log = REPORT + "\n" + jobLine + "\n";

            var reading = assertInstanceOf(Reported.class, LaunchReport.read(log));

            assertAll(
                    () -> assertEquals(4242, reading.report().pid()),
                    () -> assertEquals(jobLine + "\n", reading.jobOutput()));
        }

        @Test
        @DisplayName("a report without a ruleset has no Landlock ABI, and an empty job output stays empty")
        void withoutRuleset() {
            var log = "{\"pid\":7,\"process_started_at\":\"2026-10-17T10:00:00Z\","
                    + "\"confinement\":\"partial(abi=1)\",\"landlock_abi\":null}\n";

            var reading = assertInstanceOf(Reported.class, LaunchReport.read(log));

            assertAll(
                    () -> assertEquals(Confinement.PARTIAL_ABI_1, reading.report().confinement()),
                    () -> assertNull(reading.report().landlockAbi()),
                    () -> assertEquals("", reading.jobOutput()));
        }
    }

    @Nested
    @DisplayName("no report")
    class NoReport {

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
            var reading = assertInstanceOf(Unreported.class, LaunchReport.read(log));

            assertAll(
                    () -> assertEquals(log, reading.jobOutput()),
                    () -> assertFalse(reading.detail().isBlank()));
        }

        @ParameterizedTest(name = "exit {0}")
        @ValueSource(ints = {64, 126, 127})
        @DisplayName("without a report, the launcher's own exit codes are a launcher failure")
        void launcherFailure(int exitCode) {
            var reading = LaunchReport.read("pm-exec: execve failed\n");

            assertTrue(LaunchReport.isLauncherFailure(reading, exitCode));
        }

        @Test
        @DisplayName("another exit code, or a report present, is no launcher failure")
        void noLauncherFailure() {
            var unreported = LaunchReport.read("pm-exec: execve failed\n");
            var reported = LaunchReport.read(REPORT + "\n");

            assertAll(
                    () -> assertFalse(LaunchReport.isLauncherFailure(unreported, 1)),
                    () -> assertFalse(LaunchReport.isLauncherFailure(reported, 127)),
                    () -> assertThrows(NullPointerException.class, () -> LaunchReport.read(null)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new LaunchReport(0, Instant.EPOCH, Confinement.CONFINED, null)));
        }
    }
}
