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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

import de.planmarshall.core.store.JobId;
import de.planmarshall.core.store.ProjectEnrolment;
import de.planmarshall.core.store.ProjectEnrolment.ContainerEngine;
import de.planmarshall.core.store.ProjectEnrolment.HostTool;

/**
 * Builds every job environment from scratch (PM-SEC-5, PM-EXEC-1, PM-EXEC-5): nothing is inherited from the runtime's
 * own environment, each variable has exactly one provenance, and a variable not listed here is not passed.
 * <p>
 * {@code enrolment} values come from the project's {@code build_environment}, captured by the terminal client from the
 * operator's login environment; {@code runtime} values are computed at job start. No job receives
 * {@code DBUS_SESSION_BUS_ADDRESS} or {@code XDG_RUNTIME_DIR}, and no job receives a credential variable.
 * <p>
 * A {@code git} or {@code ci} job exists only for the CLI variant; a native-variant operation runs in process and has
 * no job environment. The credential variables of the CLI variant ({@code GH_ENTERPRISE_TOKEN},
 * {@code GITLAB_PRIVATE_TOKEN}, {@code GIT_ASKPASS}, {@code SSH_ASKPASS}, {@code GIT_CONFIG_PARAMETERS},
 * {@code GIT_CONFIG_COUNT}) are never passed either.
 * <p>
 * Specification: {@code doc/specification/job-runtime/02-confinement-and-environment.adoc} (Job Environment, Session
 * Bus, Container Engine, Build Daemons).
 *
 * @since 0.1
 */
public final class JobEnvironmentComposer implements SubprocessJobRunner.Environment,
        SubprocessJobRunner.ProbeEnvironment {

    /** The variable naming the system temporary directory. */
    public static final String TMPDIR = "TMPDIR";

    /** The variable naming the job id and the worker's generation. */
    public static final String PM_MCP_JOB_ID = "PM_MCP_JOB_ID";

    /** The variable carrying the job token of a worker. */
    public static final String PM_MCP_JOB_TOKEN = "PM_MCP_JOB_TOKEN";

    /** The variable naming the worker's egress proxy. */
    public static final String HTTPS_PROXY = "HTTPS_PROXY";

    /** The variable naming the worker's egress proxy. */
    public static final String HTTP_PROXY = "HTTP_PROXY";

    /** The variable that must stay empty for a worker. */
    public static final String NO_PROXY = "NO_PROXY";

    /** The default of {@code LC_ALL} when the enrolment captured none. */
    public static final String DEFAULT_LC_ALL = "C.UTF-8";

    /** The variable of the locale override. */
    private static final String LC_ALL = "LC_ALL";

    /** The prefixes of enrolment variables a {@code ci} job receives. */
    private static final List<String> TESTCONTAINERS_PREFIX = List.of("TESTCONTAINERS_");

    private final Path base;
    private final Path systemTemp;
    private final Path deviceRoot;
    private final Path runtimeSocket;
    private final String userName;
    private final String userHome;

    /**
     * @param base       the runtime base directory {@code <PM_MCP_BASE>}
     * @param systemTemp the system temporary directory
     */
    public JobEnvironmentComposer(Path base, Path systemTemp) {
        this(base, systemTemp, Path.of("/dev"));
    }

    /**
     * @param base       the runtime base directory {@code <PM_MCP_BASE>}
     * @param systemTemp the system temporary directory
     * @param deviceRoot the device nodes directory
     */
    public JobEnvironmentComposer(Path base, Path systemTemp, Path deviceRoot) {
        this.base = Objects.requireNonNull(base, "base").toAbsolutePath().normalize();
        this.systemTemp = Objects.requireNonNull(systemTemp, "systemTemp").toAbsolutePath().normalize();
        this.deviceRoot = Objects.requireNonNull(deviceRoot, "deviceRoot").toAbsolutePath().normalize();
        this.runtimeSocket = this.base.resolve(JobConfinementComposer.RUNTIME_SOCKET);
        this.userName = System.getProperty("user.name", "");
        this.userHome = System.getProperty("user.home", "");
    }

    /**
     * {@inheritDoc}
     *
     * <p>A worker's token, data home, egress proxy and harness switches are supplied by the caller that runs a worker;
     * this implementation of the seam passes none of them.
     */
    @Override
    public Map<String, String> of(VerifiedJob job, JobId jobId) {
        return forJob(job.enrolment(), jobId, Map.of(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), false);
    }

    /**
     * {@inheritDoc}
     *
     * <p>A probe's captured enrolment rows are empty when the caller supplies none.
     */
    @Override
    public Map<String, String> of(ProbeCandidate candidate, JobId jobId) {
        return forProbe(candidate.executable().path(), candidate.interpreter().map(ProbeCandidate.DigestedFile::path),
                candidate.kind() == ProbeCandidate.Kind.LOGIN, new TreeMap<>());
    }

    /**
     * The environment of a process job.
     *
     * @param enrolment          the verified enrolment
     * @param jobId              the id of the job
     * @param harnessSwitches    the harness switches a worker's profile declares; ignored for other kinds
     * @param jobToken           the job token of a worker; empty for other kinds
     * @param workerDataVariable the data-home variable of a worker's harness; empty for other kinds
     * @param egressProxy        the loopback URL of a worker's egress proxy; empty for other kinds
     * @param sshAuthSock        the SSH agent socket of a {@code git} job, when the effective remote needs it
     * @param committerName      the committer name of a commit-creating {@code git} job; empty otherwise
     * @param committerEmail     the committer email of a commit-creating {@code git} job; empty otherwise
     * @param runRepositoryHooks whether a {@code git} job runs repository hooks
     * @return the complete environment
     */
    // java:S107 does not apply: the method carries the rows of the job-environment table, one parameter per variable
    // group; grouping them into a holder would hide which row a caller sets and where its value comes from.
    @SuppressWarnings("java:S107")
    public Map<String, String> forJob(ProjectEnrolment enrolment, JobId jobId,
            Map<String, String> harnessSwitches, Optional<String> jobToken, Optional<String> workerDataVariable,
            Optional<String> egressProxy, Optional<String> sshAuthSock, Optional<String> committerName,
            Optional<String> committerEmail, boolean runRepositoryHooks) {
        Objects.requireNonNull(enrolment, "enrolment");
        Objects.requireNonNull(jobId, "jobId");
        var environment = new TreeMap<String, String>();
        environment.put("PATH", path(enrolment));
        environment.put("HOME", userHome);
        environment.put("USER", userName);
        environment.put("LOGNAME", userName);
        locale(environment, enrolment.buildEnvironment());
        environment.put(TMPDIR, systemTemp.toString());
        switch (jobId.kind()) {
            case BUILD, PROJECT_SCRIPT -> buildToolchain(environment, enrolment,
                    jobId.kind() == JobId.Kind.BUILD && enrolment.containerEngine() == ContainerEngine.DOCKER);
            case GIT -> {
                git(environment, sshAuthSock, committerName, committerEmail);
                if (runRepositoryHooks) {
                    buildToolchain(environment, enrolment, false);
                }
            }
            case CI -> ci(environment, enrolment);
            case LLM -> worker(environment, jobId, harnessSwitches, jobToken, workerDataVariable, egressProxy);
            case PROBE -> {
                // a probe never reaches this method; forProbe composes its environment
            }
            default -> {
                // the set is closed; no other kind has an environment here
            }
        }
        refuseCredential(environment);
        return Map.copyOf(environment);
    }

    /**
     * The probe environment.
     *
     * @param executable  the probed executable
     * @param interpreter its interpreter, for a script
     * @param loginProbe  whether the probe needs the harness's login environment
     * @param captured    the captured enrolment rows of the machine
     * @return the complete environment
     */
    public Map<String, String> forProbe(Path executable, Optional<Path> interpreter, boolean loginProbe,
            SortedMap<String, String> captured) {
        Objects.requireNonNull(executable, "executable");
        var environment = new TreeMap<String, String>();
        var directories = new ArrayList<String>();
        interpreter.map(Path::getParent).ifPresent(parent -> directories.add(parent.toString()));
        directories.add(executable.getParent().toString());
        environment.put("PATH", String.join(":", directories));
        environment.put("HOME", userHome);
        environment.put("USER", userName);
        environment.put("LOGNAME", userName);
        locale(environment, Objects.requireNonNull(captured, "captured"));
        environment.put(TMPDIR, systemTemp.toString());
        if (loginProbe) {
            environment.put("NO_COLOR", "1");
        }
        refuseCredential(environment);
        return Map.copyOf(environment);
    }

    private void buildToolchain(Map<String, String> environment, ProjectEnrolment enrolment,
            boolean withContainerEngine) {
        copy(environment, enrolment.buildEnvironment(), List.of("JAVA_HOME", "GRADLE_USER_HOME"));
        environment.put("MVND_DAEMON_STORAGE", BuildDaemonStore.mvndDirectory(daemonStore(enrolment)).toString());
        if (withContainerEngine) {
            copy(environment, enrolment.buildEnvironment(), List.of("DOCKER_HOST", "DOCKER_CONFIG"));
            copyPrefixed(environment, enrolment.buildEnvironment(), TESTCONTAINERS_PREFIX);
        }
    }

    /**
     * The daemon store over the same write set the confinement composes, before the store itself is added, so the
     * environment and the write set name one directory.
     */
    private Path daemonStore(ProjectEnrolment enrolment) {
        return JobConfinementComposer.daemonStore(base, systemTemp, deviceRoot, enrolment);
    }

    private void git(Map<String, String> environment, Optional<String> sshAuthSock, Optional<String> committerName,
            Optional<String> committerEmail) {
        sshAuthSock.ifPresent(socket -> environment.put("SSH_AUTH_SOCK", socket));
        environment.put("GIT_TERMINAL_PROMPT", "0");
        environment.put("GCM_INTERACTIVE", "never");
        environment.put("GIT_OPTIONAL_LOCKS", "0");
        committerName.ifPresent(name -> environment.put("GIT_COMMITTER_NAME", name));
        committerEmail.ifPresent(email -> environment.put("GIT_COMMITTER_EMAIL", email));
    }

    private void ci(Map<String, String> environment, ProjectEnrolment enrolment) {
        copy(environment, enrolment.buildEnvironment(), List.of("GH_CONFIG_DIR", "GLAB_CONFIG_DIR", "XDG_CONFIG_HOME"));
        environment.put("GH_PROMPT_DISABLED", "1");
        environment.put("GH_NO_UPDATE_NOTIFIER", "1");
        environment.put("NO_COLOR", "1");
    }

    private void worker(Map<String, String> environment, JobId jobId, Map<String, String> harnessSwitches,
            Optional<String> jobToken, Optional<String> workerDataVariable, Optional<String> egressProxy) {
        jobToken.ifPresent(token -> environment.put(PM_MCP_JOB_TOKEN, token));
        environment.put(PM_MCP_JOB_ID, jobId.value());
        harnessSwitches.forEach(environment::put);
        var data = base.resolve(JobConfinementComposer.RUN_JOBS).resolve(jobId.value())
                .resolve(JobConfinementComposer.DATA);
        workerDataVariable.ifPresent(variable -> environment.put(variable, data.toString()));
        egressProxy.ifPresent(proxy -> {
            environment.put(HTTPS_PROXY, proxy);
            environment.put(HTTP_PROXY, proxy);
            environment.put(NO_PROXY, "");
        });
    }

    private static void locale(Map<String, String> environment, SortedMap<String, String> captured) {
        var lcAll = captured.get(LC_ALL);
        environment.put(LC_ALL, lcAll == null || lcAll.isEmpty() ? DEFAULT_LC_ALL : lcAll);
        var lang = captured.get("LANG");
        if (lang != null && !lang.isEmpty()) {
            environment.put("LANG", lang);
        }
        for (var entry : captured.entrySet()) {
            if (entry.getKey().startsWith("LC_") && !LC_ALL.equals(entry.getKey()) && !entry.getValue().isEmpty()) {
                environment.put(entry.getKey(), entry.getValue());
            }
        }
    }

    private static void copy(Map<String, String> environment, SortedMap<String, String> captured, List<String> names) {
        for (var name : names) {
            var value = captured.get(name);
            if (value != null && !value.isEmpty()) {
                environment.put(name, value);
            }
        }
    }

    private static void copyPrefixed(Map<String, String> environment, SortedMap<String, String> captured,
            List<String> prefixes) {
        for (var entry : captured.entrySet()) {
            if (prefixes.stream().anyMatch(entry.getKey()::startsWith) && !entry.getValue().isEmpty()) {
                environment.put(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * @param name a variable name
     * @return whether the name names a credential the runtime never passes
     */
    public static boolean isCredential(String name) {
        if (PM_MCP_JOB_TOKEN.equals(name) || PM_MCP_JOB_ID.equals(name)) {
            return false;
        }
        return "GH_TOKEN".equals(name) || "GITHUB_TOKEN".equals(name) || "GITLAB_TOKEN".equals(name)
                || "GH_ENTERPRISE_TOKEN".equals(name) || "GITLAB_PRIVATE_TOKEN".equals(name)
                || "GIT_ASKPASS".equals(name) || "SSH_ASKPASS".equals(name)
                || "GIT_CONFIG_PARAMETERS".equals(name) || "GIT_CONFIG_COUNT".equals(name)
                || name.endsWith("_TOKEN") || name.endsWith("_PASSWORD") || name.endsWith("_SECRET")
                || name.endsWith("_KEY") || name.endsWith("_API_KEY");
    }

    private static void refuseCredential(Map<String, String> environment) {
        for (var name : environment.keySet()) {
            if (isCredential(name)) {
                throw new IllegalArgumentException("A job environment never carries a credential: " + name);
            }
        }
    }

    private String path(ProjectEnrolment enrolment) {
        var directories = new LinkedHashSet<String>();
        for (HostTool tool : enrolment.hostTools()) {
            directories.add(Path.of(tool.executablePath()).getParent().toString());
            if (tool.interpreter() != null) {
                var interpreter = tool.interpreter().wireName();
                enrolment.hostTools().stream().filter(candidate -> candidate.name().wireName().equals(interpreter))
                        .map(candidate -> Path.of(candidate.executablePath()).getParent().toString())
                        .forEach(directories::add);
            }
        }
        return String.join(":", directories);
    }

    /** @return the path of the runtime socket, a read path of every worker */
    public Path runtimeSocket() {
        return runtimeSocket;
    }

    /** @return the directory of the job-private directories */
    public Path jobPrivateDirectories() {
        return base.resolve(JobConfinementComposer.RUN_JOBS);
    }
}
