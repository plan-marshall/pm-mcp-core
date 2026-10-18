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

import de.cuioss.tools.logging.CuiLogger;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Starts a verified job through the launcher {@code pm-exec} (PM-EXEC-4, PM-EXEC-1, PM-EXEC-5): the process
 * {@code <PM_MCP_HOME>/bin/pm-exec -- <verified argv…>}, started by its absolute path in the runtime's own installation
 * directory and never through {@code PATH}. The launcher makes itself the leader of a new process group, writes its
 * launch report ({@link LaunchReport}) as the first line of the job log and replaces itself with the target, so the PID
 * this class answers is both the job PID and its process-group id.
 * <p>
 * The launch is a plain {@link ProcessBuilder} start: the working directory is the verified one, the environment is
 * cleared and filled from exactly the allowlist the caller supplies (its content belongs to the job's confinement and
 * environment), {@code stdout} and {@code stderr} go to the job log, and {@code stdin} is {@code /dev/null} unless the
 * job carries an in-memory payload. A payload is written as UTF-8 by a virtual thread that closes the stream right
 * after flushing, so the child sees EOF and never waits for interactive input; it never enters the argument vector, a
 * file or a shell.
 * <p>
 * {@code pm-runtime} depends on no native {@code pm-exec} artifact: the launcher is a binary of the release, found only
 * at its path below {@code <PM_MCP_HOME>}.
 * <p>
 * Specification: {@code doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc} (Process-Group Creation via
 * the {@code pm-exec} Launcher, runtime side; In-Memory {@code stdin} Streaming).
 *
 * @since 0.1
 */
public final class PmExecLauncher {

    private static final CuiLogger LOGGER = new CuiLogger(PmExecLauncher.class);

    /** The path of the launcher below {@code <PM_MCP_HOME>}. */
    static final String LAUNCHER = "bin/pm-exec";

    /** The separator between the launcher and the verified argument vector. */
    static final String SEPARATOR = "--";

    private static final File NO_INPUT = new File("/dev/null");

    /** {@code rw-------}: the mode of a job log. */
    private static final FileAttribute<Set<PosixFilePermission>> OWNER_ONLY =
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"));

    private final Path launcher;

    /**
     * @param pmMcpHome the runtime's installation directory {@code PM_MCP_HOME}
     * @throws NullPointerException     if the directory is {@code null}
     * @throws IllegalArgumentException if the directory is not absolute
     */
    public PmExecLauncher(Path pmMcpHome) {
        Objects.requireNonNull(pmMcpHome, "pmMcpHome");
        if (!pmMcpHome.isAbsolute()) {
            throw new IllegalArgumentException("PM_MCP_HOME must be absolute: " + pmMcpHome);
        }
        this.launcher = pmMcpHome.resolve(LAUNCHER).normalize();
    }

    /** @return the absolute path of the launcher this instance starts */
    public Path launcher() {
        return launcher;
    }

    /**
     * What to launch: a job that passed its verification, with the environment and the input it runs with.
     *
     * @param workingDirectory the verified working directory
     * @param argv             the verified argument vector; its first element is the verified absolute executable
     * @param environment      the complete environment of the job, the allowlist of its confinement; nothing of the
     *                         runtime's own environment is added
     * @param stdin            the in-memory input of the job, written as UTF-8; empty for {@code /dev/null}
     * @param log              the job log, which receives {@code stdout} and {@code stderr}
     * @since 0.1
     */
    public record Launch(Path workingDirectory, List<String> argv, Map<String, String> environment,
    Optional<String> stdin, Path log) {

        /**
         * @throws NullPointerException     if a component or an element is {@code null}
         * @throws IllegalArgumentException if the argument vector is empty or its executable is not an absolute path
         */
        public Launch {
            Objects.requireNonNull(workingDirectory, "workingDirectory");
            argv = List.copyOf(Objects.requireNonNull(argv, "argv"));
            if (argv.isEmpty()) {
                throw new IllegalArgumentException("A launched argument vector has at least the executable");
            }
            if (!Path.of(argv.getFirst()).isAbsolute()) {
                throw new IllegalArgumentException("The executable must be an absolute path: " + argv.getFirst());
            }
            environment = Map.copyOf(Objects.requireNonNull(environment, "environment"));
            Objects.requireNonNull(stdin, "stdin");
            Objects.requireNonNull(log, "log");
        }

        /**
         * @param job         the verified job
         * @param environment the complete environment of the job
         * @param stdin       the in-memory input of the job; empty for {@code /dev/null}
         * @param log         the job log
         * @return the launch of the verified job
         */
        public static Launch of(VerifiedJob job, Map<String, String> environment, Optional<String> stdin, Path log) {
            Objects.requireNonNull(job, "job");
            return new Launch(job.workingDirectory(), job.argv(), environment, stdin, log);
        }
    }

    /**
     * A started launcher process.
     *
     * @param process the process, which is the launcher until it replaces itself with the target
     * @param pid     the PID of the process, which is the job PID and its process-group id
     * @since 0.1
     */
    public record Launched(Process process, long pid) {

        /** @throws NullPointerException if the process is {@code null} */
        public Launched {
            Objects.requireNonNull(process, "process");
        }
    }

    /**
     * Starts the launcher with the verified argument vector.
     *
     * @param launch what to launch
     * @return the started process and its PID
     * @throws IOException if the process cannot be started, for example because the launcher is missing
     */
    public Launched launch(Launch launch) throws IOException {
        Objects.requireNonNull(launch, "launch");
        var builder = processBuilder(launch);
        createLog(launch.log());
        var process = builder.start();
        launch.stdin().ifPresent(payload -> writeInput(process, payload.getBytes(StandardCharsets.UTF_8)));
        return new Launched(process, process.pid());
    }

    /**
     * The process builder {@link #launch(Launch)} starts: the command line, the working directory, exactly the
     * environment of the launch, {@code stdin} from {@code /dev/null} or a pipe for the payload, and {@code stdout}
     * and {@code stderr} appended to the job log. The payload is not part of it; it is written to the started process.
     *
     * @param launch what to launch
     * @return the configured builder, not started
     * @throws IllegalArgumentException if the operating system refuses a name or a value of the environment
     */
    ProcessBuilder processBuilder(Launch launch) {
        var builder = new ProcessBuilder(command(launch.argv()));
        builder.directory(launch.workingDirectory().toFile());
        var environment = builder.environment();
        environment.clear();
        environment.putAll(launch.environment());
        builder.redirectInput(launch.stdin().isPresent() ? ProcessBuilder.Redirect.PIPE
                : ProcessBuilder.Redirect.from(NO_INPUT));
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(launch.log().toFile()));
        builder.redirectErrorStream(true);
        return builder;
    }

    /** @return the command line of the launcher: its absolute path, the separator and the verified vector */
    List<String> command(List<String> argv) {
        var command = new ArrayList<String>(argv.size() + 2);
        command.add(launcher.toString());
        command.add(SEPARATOR);
        command.addAll(argv);
        return command;
    }

    /**
     * Creates the job log readable and writable by its owner only, before the child inherits it: it may hold what the
     * job printed, secrets included. The child writes it opened for appending, so a job cannot seek back and overwrite
     * the launch report of {@code pm-exec} in the first line; a cut of the log is caught by the length check of
     * {@link JobLog}.
     */
    private static void createLog(Path log) throws IOException {
        Files.newByteChannel(log, Set.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE), OWNER_ONLY).close();
    }

    private static void writeInput(Process process, byte[] payload) {
        Thread.startVirtualThread(() -> {
            try (var input = process.getOutputStream()) {
                input.write(payload);
                input.flush();
            } catch (IOException e) {
                // The child ended before it read its input; its exit and the outcome derivation decide the fate
                LOGGER.debug(e, "The input of process %s was not delivered completely", process.pid());
            }
        });
    }
}
