/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.worker;

import de.cuioss.tools.logging.CuiLogger;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import de.planmarshall.runtime.job.ProcessGroupSignals;
import de.planmarshall.runtime.job.ProcessTreeTracker;
import de.planmarshall.runtime.launch.HarnessEnrolment;
import de.planmarshall.runtime.launch.HarnessVerifier;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;

/**
 * Detects that a worker started a second agent (PM-WORK-7, the {@code agent_started} signal): it refreshes the
 * generation's recorded process tree, reads each member's executable path and digest, and compares them with every
 * enrolled harness executable and with the installed operator binary.
 * <p>
 * The comparison is by path <em>and</em> digest, never by name: a binary that carries the enrolled executable's name
 * with other content is not the enrolled executable, and a process of the tree that runs a program the supervisor never
 * enrolled is not reported. The tree is the one {@link ProcessTreeTracker} records for the worker's process, so a member
 * that leaves the process group or starts a new session is still examined — that detachment is what a started agent
 * does.
 * <p>
 * The pass reads each member's executable path first and hashes the file only when that path can match, so an ordinary
 * tree of {@code sh}, {@code git} and {@code node} processes costs no digest of a harness binary. Paths are compared in
 * their real form, because the operating system answers the target of a symlinked executable, and the operator binary's
 * digest is read at the pass that needs it, so a replaced operator binary is not judged by a stale digest.
 * <p>
 * At the first match the detector reports the fact to the task protocol and ends the worker; the protocol stays the
 * owner of the signal entry, the activity entry, the {@code security} audit record and the re-offer, and the one end
 * path stays {@link WorkerEnding}. The signal's threshold is {@code 1} and its action {@code restart}: the first
 * matching process raises it and the worker is restarted, whatever a role artifact names for its other off-track
 * signals — this detector reads no artifact, which is how the two fixed values hold.
 *
 * @since 0.1
 */
public final class AgentStartedDetector {

    private static final CuiLogger LOGGER = new CuiLogger(AgentStartedDetector.class);

    /** The operator binary of the installation, below the runtime home. */
    private static final String OPERATOR_BINARY = "bin/pm-operator";

    private final SupervisorEnvironment environment;
    private final ProcessExecutables executables;
    private final Supplier<List<HarnessEnrolment>> enrolments;
    private final WorkerEnding ending;
    private final ProcessGroupSignals signals;
    private final Path operator;
    private final Map<String, ProcessTreeTracker> trees = new HashMap<>();
    private final Set<String> reported = new HashSet<>();

    /**
     * @param environment the collaborators of the project; its protocol, its clock and its runtime installation are
     *                    read here
     * @param executables where a process's executable and digest are read
     * @param enrolments  every enrolled harness executable, read at each pass
     * @param ending      the one end path of a generation
     * @throws NullPointerException if an argument is {@code null}
     */
    public AgentStartedDetector(SupervisorEnvironment environment, ProcessExecutables executables,
            Supplier<List<HarnessEnrolment>> enrolments, WorkerEnding ending) {
        this(environment, executables, enrolments, ending, ProcessGroupSignals.system());
    }

    /**
     * @param environment the collaborators of the project
     * @param executables where a process's executable and digest are read
     * @param enrolments  every enrolled harness executable, read at each pass
     * @param ending      the one end path of a generation
     * @param signals     the signalling primitive the tree tracker is built with
     * @throws NullPointerException if an argument is {@code null}
     */
    AgentStartedDetector(SupervisorEnvironment environment, ProcessExecutables executables,
            Supplier<List<HarnessEnrolment>> enrolments, WorkerEnding ending, ProcessGroupSignals signals) {
        this.environment = Objects.requireNonNull(environment, "environment");
        this.executables = Objects.requireNonNull(executables, "executables");
        this.enrolments = Objects.requireNonNull(enrolments, "enrolments");
        this.ending = Objects.requireNonNull(ending, "ending");
        this.signals = Objects.requireNonNull(signals, "signals");
        operator = environment.runtimeHome().resolve(OPERATOR_BINARY);
    }

    /**
     * Refreshes the generation's recorded process tree and reports the first process of it that runs an enrolled
     * harness executable or the installed operator binary. A generation is reported at most once: a later pass answers
     * nothing without rebuilding the tree, so one started agent never ends a worker twice.
     *
     * @param generation the generation whose recorded tree is refreshed
     * @param rootPid    the PID of the worker's process, the root of the tree
     * @param task       the queue task the generation holds, which the report names
     * @return the process that matched, or empty when none did
     * @throws NullPointerException if an argument is {@code null}
     */
    public synchronized Optional<ProcessExecutable> detect(WorkerGeneration generation, long rootPid, HeldTask task) {
        Objects.requireNonNull(generation, "generation");
        Objects.requireNonNull(task, "task");
        var generationId = generation.generationId();
        if (reported.contains(generationId)) {
            return Optional.empty();
        }
        var tree = trees.computeIfAbsent(generationId, id -> new ProcessTreeTracker(rootPid, signals));
        tree.refresh();
        var operatorPath = realPath(operator);
        var enrolled = enrolments.get().stream()
                .filter(enrolment -> !enrolment.isEndpoint())
                .map(enrolment -> new EnrolledExecutable(realPath(enrolment.executablePath()), enrolment.sha256()))
                .toList();
        for (var pid : tree.members().stream().sorted().toList()) {
            var path = executables.path(pid);
            if (path.isEmpty() || !isCandidate(path.get(), operatorPath, enrolled)) {
                continue;
            }
            if (executables.read(pid) instanceof ProcessExecutables.Found(ProcessExecutable executable)
                    && matches(executable, operatorPath, enrolled)) {
                report(generation, task, executable, pid);
                return Optional.of(executable);
            }
        }
        return Optional.empty();
    }

    /**
     * @param path         the executable path of a process
     * @param operatorPath the real path of the installed operator binary
     * @param enrolled     every enrolled harness executable, by real path and digest
     * @return whether the path can match, so the file is worth digesting
     */
    private static boolean isCandidate(Path path, Path operatorPath, List<EnrolledExecutable> enrolled) {
        return path.equals(operatorPath)
                || enrolled.stream().anyMatch(executable -> executable.path().equals(path));
    }

    /**
     * @param executable   the process's executable
     * @param operatorPath the real path of the installed operator binary
     * @param enrolled     every enrolled harness executable, by real path and digest
     * @return whether the process runs an enrolled harness executable or the installed operator binary, by path and
     *         digest
     */
    private boolean matches(ProcessExecutable executable, Path operatorPath, List<EnrolledExecutable> enrolled) {
        if (executable.path().equals(operatorPath)) {
            return HarnessVerifier.digestExecutable(operator)
                    .map(sha256 -> ProcessExecutable.DIGEST_PREFIX + sha256)
                    .filter(executable.digest()::equals).isPresent();
        }
        return enrolled.stream().anyMatch(candidate -> candidate.path().equals(executable.path())
                && candidate.sha256().equals(executable.sha256()));
    }

    /**
     * @param path a path the operating system reports
     * @return the path in its real form, or its absolute normalized form when it does not resolve
     */
    private static Path realPath(Path path) {
        var absolute = path.toAbsolutePath().normalize();
        try {
            return absolute.toRealPath();
        } catch (IOException _) {
            return absolute;
        }
    }

    /** Reports the match to the task protocol at the instant the process started, then ends the worker. */
    private void report(WorkerGeneration generation, HeldTask task, ProcessExecutable executable, long pid) {
        var at = ProcessHandle.of(pid).flatMap(handle -> handle.info().startInstant())
                .orElseGet(() -> environment.clock().instant());
        reported.add(generation.generationId());
        trees.remove(generation.generationId());
        LOGGER.warn(WARN.AGENT_STARTED_DETECTED, generation.generationId(), generation.role(),
                executable.path().toString(), executable.digest());
        try {
            environment.protocol().agentStarted(generation.generationId(), task.queueTaskId(), task.attempt(),
                    executable.path().toString(), at);
        } finally {
            ending.end(generation, EndCause.OFF_TRACK);
        }
    }

    /**
     * One enrolled harness executable, resolved once per pass: the path the operating system answers for a process is
     * the real path of the file it runs, so the enrolment is compared in that form.
     *
     * @param path   the real path of the enrolled executable
     * @param sha256 the digest the enrolment binds
     */
    private record EnrolledExecutable(Path path, String sha256) {
    }

    /**
     * The queue task a generation holds while its tree is examined (PM-WORK-7): the task the signal names and the
     * attempt it is at, the two facts the generation does not carry.
     *
     * @param queueTaskId the queue task the generation holds
     * @param attempt     the attempt of that task, from 1
     * @since 0.1
     */
    public record HeldTask(String queueTaskId, int attempt) {

        /**
         * @throws NullPointerException     if the queue task id is {@code null}
         * @throws IllegalArgumentException if the task id is blank or the attempt is below 1
         */
        public HeldTask {
            Objects.requireNonNull(queueTaskId, "queueTaskId");
            if (queueTaskId.isBlank()) {
                throw new IllegalArgumentException("A held task names its queue task");
            }
            if (attempt < 1) {
                throw new IllegalArgumentException("An attempt counts from 1: " + attempt);
            }
        }
    }
}
