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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * A fake of the launcher {@code pm-exec} for the tests of the job runtime, shared through the test JAR of
 * {@code pm-runtime}: a {@code /bin/sh} script the test writes to {@code <PM_MCP_HOME>/bin/pm-exec} of a temporary
 * directory. It runs on Linux only, so every test that starts it carries {@code @EnabledOnOs(OS.LINUX)}.
 * <p>
 * Like the real launcher it accepts the confinement options {@code --write}, {@code --read} and {@code --deny-read},
 * ignores them, and then requires {@code -- <absolute executable> [args…]} (exit {@code 64} otherwise), makes
 * itself the leader of a new process group without changing its PID ({@code setsid}, which leaves the PID alone because
 * the started process is no group leader), writes one launch report line to {@code stderr} with its PID, the start
 * instant and the confinement {@code unavailable}, and replaces itself with the target by {@code exec}. It applies no
 * confinement and verifies nothing. {@link Mode} selects the launcher failures: no report, or exit {@code 64},
 * {@code 126} or {@code 127} without starting the target.
 *
 * @since 0.1
 */
public final class FakePmExec {

    private static final List<Path> TOOL_DIRECTORIES = List.of(Path.of("/usr/bin"), Path.of("/bin"));

    private static final String HEADER = """
            #!/bin/sh
            # FakePmExec: the test launcher of the job runtime
            while [ "$1" = "--write" ] || [ "$1" = "--read" ] || [ "$1" = "--deny-read" ]; do
              shift 2
            done
            if [ "$1" != "--" ]; then
              echo "pm-exec: usage: pm-exec [--write PATH] [--read PATH] [--deny-read PATH] -- <executable> [args]" >&2
              exit 64
            fi
            shift
            """;

    private static final String REPORT_AND_EXEC = """
            exec @SETSID@ /bin/sh -c 'printf "{\\"pid\\":%s,\\"process_started_at\\":\\"%s\\",\\"confinement\\":\
            \\"unavailable\\",\\"landlock_abi\\":null}\\n" "@PID@" "$(@DATE@ -u +%Y-%m-%dT%H:%M:%SZ)" >&2; exec "$@"' \
            pm-exec "$@"
            """;

    private static final String EXEC = """
            exec @SETSID@ "$@"
            """;

    private static final String FAIL = """
            echo "pm-exec: launcher failure @CODE@" >&2
            exit @CODE@
            """;

    private FakePmExec() {
    }

    /**
     * How the fake launcher behaves.
     *
     * @since 0.1
     */
    public enum Mode {
        /** A new process group, the launch report, then the target: the launcher as it works. */
        REPORT(0),
        /** A new process group and the target, but no launch report. */
        NO_REPORT(0),
        /** Exit {@code 64} (usage) without a report and without starting the target. */
        EXIT_64(64),
        /** Exit {@code 126} (confinement could not be applied) without a report and without starting the target. */
        EXIT_126(126),
        /** Exit {@code 127} ({@code execve} failed) without a report and without starting the target. */
        EXIT_127(127);

        private final int exitCode;

        Mode(int exitCode) {
            this.exitCode = exitCode;
        }

        /** @return the exit code of the launcher itself; {@code 0} when it starts the target */
        public int exitCode() {
            return exitCode;
        }
    }

    /**
     * Writes the fake launcher to {@code <home>/bin/pm-exec}.
     *
     * @param home the temporary {@code PM_MCP_HOME}
     * @param mode how the launcher behaves
     * @return the home, for a {@link PmExecLauncher}
     * @throws IOException if the launcher cannot be written
     */
    public static Path install(Path home, Mode mode) throws IOException {
        Objects.requireNonNull(mode, "mode");
        return write(home, body(mode));
    }

    /**
     * Writes the fake launcher of {@link Mode#REPORT}, with a launch report that names a PID other than the one of the
     * process it starts. A broken or hostile launcher can write such a report, and the runtime must not take the PID it
     * reports for the process it started.
     *
     * @param home         the temporary {@code PM_MCP_HOME}
     * @param reportedPid  the PID the report names, not that of the started process
     * @return the home, for a {@link PmExecLauncher}
     * @throws IOException if the launcher cannot be written
     */
    public static Path installReportingPid(Path home, long reportedPid) throws IOException {
        return write(home, body(Mode.REPORT).replace("\"$$\"", Long.toString(reportedPid)));
    }

    private static Path write(Path home, String body) throws IOException {
        var launcher = home.resolve(PmExecLauncher.LAUNCHER);
        Files.createDirectories(launcher.getParent());
        Files.writeString(launcher, HEADER + body);
        Files.setPosixFilePermissions(launcher, PosixFilePermissions.fromString("rwx------"));
        return home;
    }

    /**
     * @param name the name of a tool of the base system, for example {@code sleep}
     * @return its absolute path in {@code /usr/bin} or {@code /bin}
     * @throws IllegalStateException if neither holds it
     */
    public static Path tool(String name) {
        return TOOL_DIRECTORIES.stream().map(directory -> directory.resolve(name)).filter(Files::isExecutable)
                .findFirst().orElseThrow(() -> new IllegalStateException("No " + name + " in " + TOOL_DIRECTORIES));
    }

    /**
     * Waits until the log of a job started through the fake launcher holds the launch report and at least a number of
     * output lines after it.
     *
     * @param log     the job log
     * @param process the process of the job; its end cuts no wait short, because its output may still be read
     * @param lines   how many output lines to wait for
     * @return the read of the log from offset {@code 0}
     * @throws InterruptedException if the waiting thread is interrupted
     * @throws AssertionError       if the log does not hold them within ten seconds
     */
    public static JobLog.Lines awaitLines(JobLog log, Process process, int lines) throws InterruptedException {
        var deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        JobLog.Read read = null;
        while (System.nanoTime() < deadline) {
            read = log.readLines(0);
            if (read instanceof JobLog.Lines found && found.report().isPresent() && found.lines().size() >= lines) {
                return found;
            }
            // Waits on the process, not a fixed sleep: it returns at once when the process has ended
            process.waitFor(20, TimeUnit.MILLISECONDS);
        }
        throw new AssertionError("The job log holds no launch report and %d lines: %s".formatted(lines, read));
    }

    private static String body(Mode mode) {
        return switch (mode) {
            case REPORT -> REPORT_AND_EXEC.replace("@SETSID@", tool("setsid").toString())
                    .replace("@DATE@", tool("date").toString()).replace("@PID@", "\"$$\"");
            case NO_REPORT -> EXEC.replace("@SETSID@", tool("setsid").toString());
            case EXIT_64, EXIT_126, EXIT_127 -> FAIL.replace("@CODE@", Integer.toString(mode.exitCode()));
        };
    }
}
