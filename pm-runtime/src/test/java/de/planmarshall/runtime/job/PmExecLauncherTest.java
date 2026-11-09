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

import static de.planmarshall.runtime.testing.generators.RuntimeGenerators.words;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
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
 * <p>
 * Contract literals that stay: the launcher path {@code bin/pm-exec}, the separator {@code --}, the launcher's
 * confinement options, the {@code -} that names stdin, the variable {@code HOME} that must stay unset, the fake
 * launcher's tools and the 30 seconds of the sleeping job, which only has to outlive the test. The executables,
 * arguments, payloads, paths and environment of the vectors are generated.
 */
@EnableGeneratorController
@DisplayName("Launch through pm-exec")
class PmExecLauncherTest {

    @TempDir
    Path home;

    @TempDir
    Path work;

    /** @return a generated absolute path */
    private static String absolute() {
        return "/" + words().next() + "/" + words().next();
    }

    @Nested
    @DisplayName("the command line")
    class CommandLine {

        @Test
        @DisplayName("is the launcher's absolute path below PM_MCP_HOME, the separator and the verified vector")
        void argvShape() {
            var launcher = new PmExecLauncher(home);
            var argv = List.of(absolute(), "-C", absolute(), words().next(), "-F", "-");

            var command = launcher.command(argv);

            var expected = new ArrayList<>(List.of(home.resolve("bin/pm-exec").toString(), "--"));
            expected.addAll(argv);
            assertAll(
                    () -> assertEquals(home.resolve("bin/pm-exec"), launcher.launcher(),
                            "the launcher lies below PM_MCP_HOME"),
                    () -> assertTrue(launcher.launcher().isAbsolute(), "the launcher path is absolute"),
                    () -> assertEquals(expected, command, "the command is launcher, separator and vector"));
        }

        @Test
        @DisplayName("an in-memory payload never enters the argument vector")
        void payloadNotInArgv() {
            var marker = Generators.letterStrings(8, 32).next();
            var payload = words().next() + ": " + marker + "\n\n" + Generators.letterStrings(1, 64).next();
            var launch = new Launch(work, List.of(absolute(), words().next(), "-F", "-"), Map.of(),
                    payload, work.resolve("job.log"));

            var launcher = new PmExecLauncher(home);

            var builder = launcher.processBuilder(launch);

            assertAll(
                    () -> assertEquals(launcher.command(launch.argv()), builder.command(),
                            "the builder starts launcher, separator and vector"),
                    () -> assertTrue(builder.command().stream().noneMatch(argument -> argument.contains(marker)),
                            "the payload is in no argument"),
                    () -> assertTrue(builder.environment().values().stream().noneMatch(value -> value.contains(
                            marker)), "the payload is in no environment value"),
                    () -> assertEquals(ProcessBuilder.Redirect.PIPE, builder.redirectInput(),
                            "the payload reaches the child through its stdin"));
        }

        @Test
        @DisplayName("carries the confinement options between the launcher and the separator")
        void confinementArgv() {
            var launcher = new PmExecLauncher(home);
            var write = absolute();
            var read = absolute();
            var denied = absolute();
            var executable = absolute();
            var confinement = new JobConfinement(List.of(Path.of(write)), List.of(Path.of(read)),
                    List.of(Path.of(denied)));
            var launch = new Launch(work, List.of(executable), Map.of(), null, work.resolve("job.log"),
                    confinement);

            assertEquals(List.of(home.resolve("bin/pm-exec").toString(),
                    "--write", write,
                    "--read", read,
                    "--deny-read", denied,
                    "--", executable), launcher.command(launch), "the options precede the separator");
        }

        @Test
        @DisplayName("a missing launcher is not looked up anywhere else: the start fails")
        void missingLauncher() {
            var launcher = new PmExecLauncher(home);
            var launch = new Launch(work, List.of(absolute()), Map.of("PATH", absolute()), null,
                    work.resolve("job.log"));

            assertThrows(IOException.class, () -> launcher.launch(launch), "a missing launcher fails the start");
        }

        @Test
        @DisplayName("a relative home, an empty vector and a relative executable are refused")
        void refusals() {
            var log = work.resolve("job.log");
            var relativeHome = Path.of(words().next());
            List<String> emptyVector = List.of();
            List<String> relativeVector = List.of(words().next(), words().next());
            List<String> absoluteVector = List.of(absolute());
            Map<String, String> noEnvironment = Map.of();
            String noInput = null;

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> new PmExecLauncher(relativeHome),
                            "a relative home is refused"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new Launch(work, emptyVector, noEnvironment, noInput, log),
                            "an empty vector is refused"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new Launch(work, relativeVector, noEnvironment, noInput, log),
                            "a relative executable is refused"),
                    () -> assertDoesNotThrow(
                            () -> new Launch(work, absoluteVector, noEnvironment, null, log),
                            "a null stdin is the /dev/null launch"));
        }
    }

    @Nested
    @EnabledOnOs(OS.LINUX)
    @DisplayName("a started job")
    class Started {

        private Launched start(List<String> argv, Map<String, String> environment, @Nullable String stdin)
                throws IOException {
            FakePmExec.install(home, FakePmExec.Mode.REPORT);
            return new PmExecLauncher(home).launch(new Launch(work, argv, environment, stdin, work.resolve("job.log")));
        }

        private JobLog.Complete awaitEnd(Launched launched) throws InterruptedException {
            assertTrue(launched.process().waitFor(10, TimeUnit.SECONDS), "the job should have ended");
            return assertInstanceOf(JobLog.Complete.class, new JobLog(work.resolve("job.log")).readWhole(0),
                    "the ended job's log is complete");
        }

        @Test
        @DisplayName("reads /dev/null without a payload, so a reader of stdin ends at once")
        void devNullByDefault() throws Exception {
            var launched = start(List.of(FakePmExec.tool("cat").toString()), Map.of(), null);

            var log = awaitEnd(launched);

            var reading = assertInstanceOf(LaunchReport.Reported.class, log.reading(), "the log carries a report");
            assertAll(
                    () -> assertEquals(0, launched.process().exitValue(), "the reader of stdin exits 0"),
                    () -> assertEquals("", reading.jobOutput(), "the reader of /dev/null prints nothing"));
        }

        @Test
        @DisplayName("receives an in-memory payload as UTF-8 and sees EOF after it")
        void inMemoryStdin() throws Exception {
            var payload = Generators.letterStrings(1, 32).next() + "\n" + Generators.letterStrings(1, 32).next()
                    + " Ü " + Generators.letterStrings(1, 32).next() + "\n";
            var launched = start(List.of(FakePmExec.tool("cat").toString()), Map.of(), payload);

            var log = awaitEnd(launched);

            var reading = assertInstanceOf(LaunchReport.Reported.class, log.reading(), "the log carries a report");
            assertAll(
                    () -> assertEquals(0, launched.process().exitValue(), "the reader exits 0 after EOF"),
                    () -> assertEquals(payload, reading.jobOutput(), "the payload arrives as UTF-8"));
        }

        @Test
        @DisplayName("runs with exactly the supplied environment and writes its output to the log")
        void environmentAndOutput() throws Exception {
            var name = "PM_JOB_" + Generators.letterStrings(1, 16).next().toUpperCase(Locale.ROOT);
            var value = words().next();
            var launched = start(List.of(FakePmExec.tool("env").toString()), Map.of(name, value), null);

            var log = awaitEnd(launched);

            var output = assertInstanceOf(LaunchReport.Reported.class, log.reading(), "the log carries a report")
                    .jobOutput();
            assertAll(
                    () -> assertTrue(output.lines().anyMatch((name + "=" + value)::equals),
                            "the supplied variable reaches the job"),
                    () -> assertFalse(output.lines().anyMatch(line -> line.startsWith("HOME=")),
                            "no unsupplied HOME reaches the job"));
        }

        @Test
        @DisplayName("has a PID that is its process-group id and the PID of the launch report")
        void pidIsProcessGroup() throws Exception {
            var launched = start(List.of(FakePmExec.tool("sleep").toString(), "30"), Map.of(), null);
            try {
                var lines = FakePmExec.awaitLines(new JobLog(work.resolve("job.log")), launched.process(), 0);

                assertAll(
                        () -> assertEquals(launched.process().pid(), launched.pid(),
                                "the job PID is the process PID"),
                        () -> assertEquals(launched.pid(), lines.report().pid(),
                                "the job PID is the reported PID"),
                        () -> assertEquals(launched.pid(), processGroup(launched.pid()),
                                "the job PID is its process-group id"));
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
