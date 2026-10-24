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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;

import de.planmarshall.core.store.JobId;
import de.planmarshall.core.store.ProjectEnrolment;

/**
 * Composes the filesystem confinement of a job from the project's enrolment at job start (PM-SEC-5).
 * <p>
 * The write set is the project root, the enrolment's {@code write_paths}, the system temporary directory and the device
 * nodes; a {@code build} or {@code project_script} job and a {@code git} job that runs repository hooks add the
 * project's daemon store; a {@code ci} job adds the configuration directory of its CLI; a worker adds the sub-paths its
 * client profile declares writable and its own data directory; a probe adds what its kind may write. The write set never
 * contains the runtime base directory, apart from the project's daemon store and a worker's own data directory, and
 * never {@code ~/.plan-marshall/}.
 * <p>
 * The read set is the filesystem enumerated at job start, less the runtime base directory, the session bus socket and,
 * without the
 * container-engine opt-in, the container engine's sockets. The job-private directory of a worker or a probe and the
 * runtime socket of a worker are named as explicit read paths, because they lie inside the denied base directory.
 * <p>
 * The composer builds the values; the launcher {@code pm-exec} applies them. A platform without a ruleset records
 * {@code confinement: unavailable}, which this type neither claims nor suppresses.
 * <p>
 * Specification: {@code doc/specification/job-runtime/02-confinement-and-environment.adoc} (Linux Filesystem
 * Confinement, Build Daemons, Container Engine, Session Bus, Probe Jobs, Worker Egress Proxy).
 *
 * @param base             the runtime base directory {@code <PM_MCP_BASE>}
 * @param systemTemp       the system temporary directory inside the write set
 * @param deviceRoot       the device nodes directory
 * @param sessionBusSocket the user's session bus socket; empty when the platform has none
 * @since 0.1
 */
public record JobConfinementComposer(Path base, Path systemTemp, Path deviceRoot, Optional<Path> sessionBusSocket) {

    /** The directory of the job-private directories below the base. */
    public static final String RUN_JOBS = "run/jobs";

    /** The subdirectory of a job-private directory a harness may write. */
    public static final String DATA = "data";

    /** The runtime socket, a read path of every worker and of no other job. */
    public static final String RUNTIME_SOCKET = "run/runtime.sock";

    /** The per-user runtime directory. */
    private static final String RUN_USER = "/run/user";

    /** The container engine's socket paths that stay out of the read set without the opt-in. */
    private static final List<String> CONTAINER_SOCKETS =
            List.of("/var/run/docker.sock", "/run/docker.sock", "/run/docker/docker.sock");

    /**
     * @throws NullPointerException     if a component is {@code null}
     * @throws IllegalArgumentException if a path is not absolute
     */
    public JobConfinementComposer {
        absolute(base, "base");
        absolute(systemTemp, "systemTemp");
        absolute(deviceRoot, "deviceRoot");
        Objects.requireNonNull(sessionBusSocket, "sessionBusSocket");
        sessionBusSocket = sessionBusSocket.map(path -> absolute(path, "sessionBusSocket"));
    }

    /**
     * The composer of a Linux or macOS runtime: {@code /tmp}, {@code /dev} and the caller's session bus socket.
     *
     * @param base             the runtime base directory
     * @param sessionBusSocket the session bus socket, {@code /run/user/<uid>/bus} on Linux
     * @return the composer
     */
    // java:S5443 does not apply: /tmp is the system temporary directory the specification puts in every job's write
    // set; the paths are passed to the launcher as the write set, not used as a place to create trusted files.
    @SuppressWarnings("java:S5443")
    public static JobConfinementComposer standard(Path base, Optional<Path> sessionBusSocket) {
        return new JobConfinementComposer(base, Path.of("/tmp"), Path.of("/dev"), sessionBusSocket);
    }

    /**
     * The composer of a runtime that derives the session bus socket from the current user: {@code /run/user/<uid>/bus}
     * on Linux, the whole {@code /run/user} when the uid cannot be read, and none on macOS.
     *
     * @param base the runtime base directory
     * @return the composer
     */
    public static JobConfinementComposer standard(Path base) {
        return standard(base, currentBusSocket());
    }

    /**
     * The session bus socket of the current user: {@code /run/user/<uid>/bus} on Linux, the whole {@code /run/user}
     * when the uid cannot be read, and none on macOS.
     */
    // java:S5443 does not apply: /run/user is denied, never written; the path locates a socket outside the read set.
    @SuppressWarnings("java:S5443")
    private static Optional<Path> currentBusSocket() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("linux")) {
            return Optional.empty();
        }
        try (var lines = Files.lines(Path.of("/proc/self/status"))) {
            var uid = lines.filter(line -> line.startsWith("Uid:")).findFirst()
                    .map(line -> line.split("\\s+")[1]).orElse(null);
            return Optional.of(uid == null ? Path.of(RUN_USER) : Path.of(RUN_USER, uid, "bus"));
        } catch (IOException _) {
            return Optional.of(Path.of(RUN_USER));
        }
    }

    /**
     * Composes the confinement of a job.
     *
     * @param enrolment           the verified enrolment of the job's project
     * @param jobId               the id of the job
     * @param harnessWritablePaths the sub-paths a worker's client profile declares writable; empty for other kinds
     * @param runRepositoryHooks  whether a {@code git} job runs repository hooks
     * @return the confinement
     */
    // java:S6916 does not apply: the switch is a statement switch over the enum JobId.Kind, where a `when` guard on a
    // constant label is not expressible; the branch condition is the caller's hook flag.
    @SuppressWarnings("java:S6916")
    public JobConfinement forJob(ProjectEnrolment enrolment, JobId jobId, List<Path> harnessWritablePaths,
            boolean runRepositoryHooks) {
        Objects.requireNonNull(enrolment, "enrolment");
        Objects.requireNonNull(jobId, "jobId");
        var write = new ArrayList<Path>();
        var planMarshall = Path.of(System.getProperty("user.home"), ".plan-marshall").toAbsolutePath().normalize();
        var projectRoot = Path.of(enrolment.projectRoot()).toAbsolutePath().normalize();
        requireOutside(projectRoot, planMarshall, "project_root");
        if (base.toAbsolutePath().normalize().startsWith(projectRoot)) {
            throw new IllegalArgumentException(
                    "A project_root entry contains the protected runtime base directory: " + projectRoot);
        }
        write.add(projectRoot);
        for (var path : enrolment.writePaths()) {
            var candidate = Path.of(path).toAbsolutePath().normalize();
            requireOutside(candidate, base, "write_paths");
            requireOutside(candidate, planMarshall, "write_paths");
            write.add(candidate);
        }
        write.add(systemTemp);
        write.add(deviceRoot);
        var read = new ArrayList<Path>();
        var jobPrivate = jobPrivate(jobId);
        switch (jobId.kind()) {
            case BUILD, PROJECT_SCRIPT -> addDaemonStore(write, enrolment);
            case GIT -> {
                if (runRepositoryHooks) {
                    addDaemonStore(write, enrolment);
                }
            }
            case CI -> write.addAll(cliConfigDirectories(enrolment.buildEnvironment()));
            case LLM -> {
                write.addAll(harnessWritablePaths);
                write.add(jobPrivate.resolve(DATA));
                read.add(jobPrivate);
                read.add(base.resolve(RUNTIME_SOCKET));
            }
            case PROBE -> {
                write.addAll(harnessWritablePaths);
                read.add(jobPrivate);
            }
        }
        return new JobConfinement(write, read, denied(enrolment));
    }

    /**
     * Adds the project's daemon store to the write set and creates it. The digest is taken over the write set before
     * the store is added, so the path is not self-referential.
     */
    private void addDaemonStore(List<Path> write, ProjectEnrolment enrolment) {
        var store = daemonStore(base, systemTemp, deviceRoot, enrolment);
        BuildDaemonStore.ensure(store);
        write.add(store);
    }

    /**
     * The project's daemon store over the write set the confinement composes, before the store itself is added. The
     * environment and the write set name one directory through this one computation.
     *
     * @param base       the runtime base directory
     * @param systemTemp the system temporary directory
     * @param deviceRoot the device nodes directory
     * @param enrolment  the verified enrolment
     * @return the daemon store of the project and write set
     */
    public static Path daemonStore(Path base, Path systemTemp, Path deviceRoot, ProjectEnrolment enrolment) {
        var write = new ArrayList<Path>();
        write.add(Path.of(enrolment.projectRoot()));
        for (var path : enrolment.writePaths()) {
            write.add(Path.of(path));
        }
        write.add(systemTemp);
        write.add(deviceRoot);
        return BuildDaemonStore.path(base, enrolment.projectId(), write);
    }

    /**
     * @param path  a write path
     * @param root  a protected runtime tree
     * @param name  the name of the entry in the message
     * @throws IllegalArgumentException if the path and the tree contain one another
     */
    private static void requireOutside(Path path, Path root, String name) {
        var normalizedPath = path.toAbsolutePath().normalize();
        var normalizedRoot = root.toAbsolutePath().normalize();
        if (normalizedPath.startsWith(normalizedRoot) || normalizedRoot.startsWith(normalizedPath)) {
            throw new IllegalArgumentException(
                    "A " + name + " entry overlaps a protected runtime tree: " + path);
        }
    }

    /**
     * Composes the confinement of a probe, which is verified against a candidate record and not against an enrolment.
     *
     * @param jobId                the id of the probe job
     * @param checkoutRoot         the checkout root of a checkout's script; empty for a probe of the machine
     * @param toolchainCaches      the build write set of a checkout's script; empty otherwise
     * @param harnessProbe         whether the probe is a harness probe
     * @param harnessWritablePaths the sub-paths the harness's client profile declares writable
     * @return the confinement
     */
    public JobConfinement forProbe(JobId jobId, Optional<Path> checkoutRoot, List<Path> toolchainCaches,
            boolean harnessProbe, List<Path> harnessWritablePaths) {
        Objects.requireNonNull(jobId, "jobId");
        var write = new ArrayList<Path>();
        write.add(systemTemp);
        write.add(deviceRoot);
        var jobPrivate = jobPrivate(jobId);
        checkoutRoot.ifPresent(root -> {
            write.add(root);
            write.addAll(toolchainCaches);
        });
        if (harnessProbe) {
            write.addAll(harnessWritablePaths);
            var data = jobPrivate.resolve(DATA);
            ensureOwnerDirectory(data);
            write.add(data);
        }
        return new JobConfinement(write, List.of(jobPrivate), denied(null));
    }

    /** Creates a directory only its owner may enter, as the job-private directories are created. */
    private static void ensureOwnerDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create the job-private directory " + directory, e);
        }
    }

    /** @return the job-private directory of a job */
    public Path jobPrivate(JobId jobId) {
        return base.resolve(RUN_JOBS).resolve(jobId.value());
    }

    /**
     * The paths excluded from the read set: the base directory, the session bus socket and, without the opt-in, the
     * container engine's sockets.
     */
    private List<Path> denied(ProjectEnrolment enrolment) {
        var denied = new ArrayList<Path>();
        denied.add(base);
        sessionBusSocket.ifPresent(denied::add);
        if (enrolment == null || enrolment.containerEngine() == null) {
            for (var socket : CONTAINER_SOCKETS) {
                denied.add(Path.of(socket));
            }
            sessionBusSocket.map(Path::getParent)
                    .ifPresent(parent -> denied.add(parent.resolve("docker.sock")));
            if (enrolment != null) {
                unixDockerHost(enrolment.buildEnvironment()).ifPresent(denied::add);
            }
        }
        return denied;
    }

    private static Optional<Path> unixDockerHost(SortedMap<String, String> environment) {
        var value = environment.get("DOCKER_HOST");
        if (value == null || !value.startsWith("unix:")) {
            return Optional.empty();
        }
        var path = value.substring("unix:".length());
        if (path.startsWith("//")) {
            path = path.substring(2);
        }
        return path.isEmpty() ? Optional.empty() : JobConfinementComposer.optionalAbsolute(path);
    }

    private List<Path> cliConfigDirectories(SortedMap<String, String> environment) {
        var directories = new ArrayList<Path>();
        addIfAbsolute(directories, environment.get("GH_CONFIG_DIR"));
        addIfAbsolute(directories, environment.get("GLAB_CONFIG_DIR"));
        var xdg = environment.get("XDG_CONFIG_HOME");
        if (xdg != null && !xdg.isEmpty()) {
            directories.add(Path.of(xdg, "gh"));
            directories.add(Path.of(xdg, "glab"));
        }
        return directories;
    }

    private static void addIfAbsolute(List<Path> directories, String value) {
        if (value != null && !value.isEmpty()) {
            var path = Path.of(value);
            if (path.isAbsolute()) {
                directories.add(path);
            }
        }
    }

    private static Optional<Path> optionalAbsolute(String value) {
        var path = Path.of(value);
        return path.isAbsolute() ? Optional.of(path) : Optional.empty();
    }

    private static Path absolute(Path path, String name) {
        Objects.requireNonNull(path, name);
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException(name + " must be absolute: " + path);
        }
        return path;
    }
}
