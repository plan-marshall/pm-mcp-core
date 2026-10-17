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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.runtime.job.PmExecLauncher.Launch;
import de.planmarshall.runtime.job.PmExecLauncher.Launched;

/**
 * The launch of a job through {@code pm-exec} (PM-EXEC-4, PM-EXEC-1, PM-EXEC-5): the launcher is started by its
 * absolute path below {@code PM_MCP_HOME} with {@code --} before the verified vector, {@code stdin} is
 * {@code /dev/null} unless an in-memory payload is given, a payload reaches the child and is closed so the child sees
 * EOF, the output lands in the job log, and the job PID is its process-group id.
 */
@DisplayName("Launch through pm-exec")
class PmExecLauncherTest {

    @TempDir
    Path home;

    @TempDir
    Path work;

    @Nested
    @DisplayName("the command line")
    class CommandLine {

        @Test
        @DisplayName("is the launcher's absolute path below PM_MCP_HOME, the separator and the verified vector")
        void argvShape() {
            var launcher = new PmExecLauncher(home);

            var command = launcher.command(List.of("/usr/bin/git", "-C", "/tree", "commit", "-F", "-"));

            assertAll(
                    () -> assertEquals(home.resolve("bin/pm-exec"), launcher.launcher()),
                    () -> assertTrue(launcher.launcher().isAbsolute()),
                    () -> assertEquals(List.of(home.resolve("bin/pm-exec").toString(), "--", "/usr/bin/git", "-C",
                            "/tree", "commit", "-F", "-"), command));
        }

        @Test
        @DisplayName("an in-memory payload never enters the argument vector")
        void payloadNotInArgv() {
            var payload = "fix: the message\n\nwith a body";
            var launch = new Launch(work, List.of("/usr/bin/git", "commit", "-F", "-"), Map.of(),
                    Optional.of(payload), work.resolve("job.log"));

            var command = new PmExecLauncher(home).command(launch.argv());

            assertTrue(command.stream().noneMatch(argument -> argument.contains("the message")));
        }

        @Test
        @DisplayName("a missing launcher is not looked up anywhere else: the start fails")
        void missingLauncher() {
            var launcher = new PmExecLauncher(home);
            var launch = new Launch(work, List.of("/bin/true"), Map.of("PATH", "/usr/bin:/bin"), Optional.empty(),
                    work.resolve("job.log"));

            assertThrows(IOException.class, () -> launcher.launch(launch));
        }

        @Test
        @DisplayName("a relative home, an empty vector and a relative executable are refused")
        void refusals() {
            var log = work.resolve("job.log");
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> new PmExecLauncher(Path.of("home"))),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new Launch(work, List.of(), Map.of(), Optional.empty(), log)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new Launch(work, List.of("git", "status"), Map.of(), Optional.empty(), log)),
                    () -> assertThrows(NullPointerException.class,
                            () -> new Launch(work, List.of("/bin/true"), Map.of(), null, log)));
        }
    }

    @Nested
    @EnabledOnOs(OS.LINUX)
    @DisplayName("a started job")
    class Started {

        private Launched start(List<String> argv, Map<String, String> environment, Optional<String> stdin)
                throws IOException {
            FakePmExec.install(home, FakePmExec.Mode.REPORT);
            return new PmExecLauncher(home).launch(new Launch(work, argv, environment, stdin, work.resolve("job.log")));
        }

        private JobLog.Complete awaitEnd(Launched launched) throws InterruptedException {
            assertTrue(launched.process().waitFor(10, TimeUnit.SECONDS), "the job should have ended");
            return assertInstanceOf(JobLog.Complete.class, new JobLog(work.resolve("job.log")).readWhole(0));
        }

        @Test
        @DisplayName("reads /dev/null without a payload, so a reader of stdin ends at once")
        void devNullByDefault() throws Exception {
            var launched = start(List.of(FakePmExec.tool("cat").toString()), Map.of(), Optional.empty());

            var log = awaitEnd(launched);

            var reading = assertInstanceOf(LaunchReport.Reported.class, log.reading());
            assertAll(
                    () -> assertEquals(0, launched.process().exitValue()),
                    () -> assertEquals("", reading.jobOutput()));
        }

        @Test
        @DisplayName("receives an in-memory payload as UTF-8 and sees EOF after it")
        void inMemoryStdin() throws Exception {
            var payload = "erste Zeile\nzweite Zeile mit Ümlaut\n";
            var launched = start(List.of(FakePmExec.tool("cat").toString()), Map.of(), Optional.of(payload));

            var log = awaitEnd(launched);

            var reading = assertInstanceOf(LaunchReport.Reported.class, log.reading());
            assertAll(
                    () -> assertEquals(0, launched.process().exitValue()),
                    () -> assertEquals(payload, reading.jobOutput()));
        }

        @Test
        @DisplayName("runs with exactly the supplied environment and writes its output to the log")
        void environmentAndOutput() throws Exception {
            var launched = start(List.of(FakePmExec.tool("env").toString()), Map.of("PM_JOB_TEST", "allowed"),
                    Optional.empty());

            var log = awaitEnd(launched);

            var output = assertInstanceOf(LaunchReport.Reported.class, log.reading()).jobOutput();
            assertAll(
                    () -> assertTrue(output.lines().anyMatch("PM_JOB_TEST=allowed"::equals), output),
                    () -> assertFalse(output.lines().anyMatch(line -> line.startsWith("HOME=")), output));
        }

        @Test
        @DisplayName("has a PID that is its process-group id and the PID of the launch report")
        void pidIsProcessGroup() throws Exception {
            var launched = start(List.of(FakePmExec.tool("sleep").toString(), "30"), Map.of(), Optional.empty());
            try {
                var lines = FakePmExec.awaitLines(new JobLog(work.resolve("job.log")), launched.process(), 0);

                assertAll(
                        () -> assertEquals(launched.process().pid(), launched.pid()),
                        () -> assertEquals(launched.pid(), lines.report().orElseThrow().pid()),
                        () -> assertEquals(launched.pid(), processGroup(launched.pid())));
            } finally {
                launched.process().destroyForcibly();
                launched.process().waitFor(10, TimeUnit.SECONDS);
            }
        }

        /** @return the process-group id of a process: field 5 of {@code /proc/<pid>/stat} */
        private static long processGroup(long pid) throws IOException {
            var stat = Files.readString(Path.of("/proc", Long.toString(pid), "stat"));
            var fields = stat.substring(stat.lastIndexOf(')') + 2).split(" ");
            return Long.parseLong(fields[2]);
        }
    }
}
