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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.store.JobId;
import de.planmarshall.core.store.ProjectEnrolment;
import de.planmarshall.core.store.ProjectEnrolment.ContainerEngine;

/**
 * The job environment (PM-SEC-5, PM-EXEC-1, PM-EXEC-5): built from scratch, one provenance per variable, no session bus
 * and no credential variable on any job type, the container variables only under the opt-in, and a probe's
 * {@code PATH} of the probed executable and its interpreter only.
 */
@DisplayName("Job environment composer")
class JobEnvironmentComposerTest {

    @TempDir
    Path base;

    private JobEnvironmentComposer composer() {
        return new JobEnvironmentComposer(base, Path.of("/tmp"));
    }

    private ProjectEnrolment enrolment(ContainerEngine engine, Map<String, String> variables) {
        return ConfinementFixture.enrolment(Path.of("/work/project"), engine, List.of(), variables,
                List.of(ConfinementFixture.tool("/usr/bin/git")));
    }

    @Nested
    @DisplayName("a build job")
    class Build {

        private final Map<String, String> captured = Map.of(
                "JAVA_HOME", "/opt/java",
                "GRADLE_USER_HOME", "/home/operator/.gradle",
                "MAVEN_OPTS", "-Xmx1g",
                "GRADLE_OPTS", "-Dfoo=bar",
                "GITHUB_TOKEN", "secret",
                "LANG", "de_DE.UTF-8",
                "LC_ALL", "");

        @Test
        @DisplayName("the MVND daemon store is a write path of the job's confinement")
        void daemonStoreIsWritable() {
            var enrolment = enrolment(null, Map.of());
            var job = JobId.random(JobId.Kind.BUILD);

            var environment = composer().forJob(enrolment, job, Map.of(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), false);
            var store = Path.of(environment.get("MVND_DAEMON_STORAGE")).getParent();
            var confinement = new JobConfinementComposer(base, Path.of("/tmp"), Path.of("/dev"), Optional.empty())
                    .forJob(enrolment, job, List.of(), false);

            assertAll(
                    () -> assertTrue(confinement.writePaths().contains(store),
                            () -> "the store " + store + " is not in " + confinement.writePaths()),
                    () -> assertEquals(BuildDaemonStore.mvndDirectory(store), Path.of(environment.get("MVND_DAEMON_STORAGE"))));
        }

        @Test
        @DisplayName("has the enrolled toolchain, a PATH of the host tools and no flag injector")
        void toolchain() {
            var environment = composer().forJob(enrolment(null, captured), JobId.random(JobId.Kind.BUILD), Map.of(),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), false);

            assertAll(
                    () -> assertEquals("/usr/bin", environment.get("PATH")),
                    () -> assertEquals("/opt/java", environment.get("JAVA_HOME")),
                    () -> assertEquals("/home/operator/.gradle", environment.get("GRADLE_USER_HOME")),
                    () -> assertEquals("/tmp", environment.get("TMPDIR")),
                    () -> assertEquals("de_DE.UTF-8", environment.get("LANG")),
                    () -> assertEquals(JobEnvironmentComposer.DEFAULT_LC_ALL, environment.get("LC_ALL")),
                    () -> assertTrue(environment.containsKey("MVND_DAEMON_STORAGE")),
                    () -> assertFalse(environment.containsKey("MAVEN_OPTS")),
                    () -> assertFalse(environment.containsKey("GRADLE_OPTS")));
        }

        @Test
        @DisplayName("carries no session bus, no runtime dir and no credential variable")
        void sanitised() {
            var environment = composer().forJob(enrolment(null, captured), JobId.random(JobId.Kind.BUILD), Map.of(),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), false);

            assertAll(
                    () -> assertFalse(environment.containsKey("DBUS_SESSION_BUS_ADDRESS")),
                    () -> assertFalse(environment.containsKey("XDG_RUNTIME_DIR")),
                    () -> assertFalse(environment.containsKey("GITHUB_TOKEN")),
                    () -> assertTrue(environment.keySet().stream()
                            .noneMatch(JobEnvironmentComposer::isCredential), environment.keySet().toString()));
        }

        @Test
        @DisplayName("receives the container variables only under the opt-in")
        void containerEngine() {
            var withEngine = Map.of("DOCKER_HOST", "unix:///run/user/1000/docker.sock", "DOCKER_CONFIG", "/home/op/.docker",
                    "TESTCONTAINERS_RYUK_DISABLED", "true");

            var without = composer().forJob(enrolment(null, withEngine), JobId.random(JobId.Kind.BUILD), Map.of(),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), false);
            var with = composer().forJob(enrolment(ContainerEngine.DOCKER, withEngine),
                    JobId.random(JobId.Kind.BUILD), Map.of(), Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(), false);

            assertAll(
                    () -> assertFalse(without.containsKey("DOCKER_HOST")),
                    () -> assertFalse(without.containsKey("TESTCONTAINERS_RYUK_DISABLED")),
                    () -> assertEquals("unix:///run/user/1000/docker.sock", with.get("DOCKER_HOST")),
                    () -> assertEquals("/home/op/.docker", with.get("DOCKER_CONFIG")),
                    () -> assertEquals("true", with.get("TESTCONTAINERS_RYUK_DISABLED")));
        }
    }

    @Nested
    @DisplayName("a worker")
    class Worker {

        @Test
        @DisplayName("receives its token, id, data home and egress proxy, with NO_PROXY empty")
        void worker() {
            var job = JobId.random(JobId.Kind.LLM);

            var environment = composer().forJob(enrolment(null, Map.of()), job,
                    Map.of("CLAUDE_CODE_DISABLE_AUTO_MEMORY", "1"), Optional.of("job-token"),
                    Optional.of("XDG_DATA_HOME"), Optional.of("http://127.0.0.1:41234"), Optional.empty(),
                    Optional.empty(), Optional.empty(), false);

            var data = base.resolve("run/jobs").resolve(job.value()).resolve("data");
            assertAll(
                    () -> assertEquals("job-token", environment.get("PM_MCP_JOB_TOKEN")),
                    () -> assertEquals(job.value(), environment.get("PM_MCP_JOB_ID")),
                    () -> assertEquals("1", environment.get("CLAUDE_CODE_DISABLE_AUTO_MEMORY")),
                    () -> assertEquals(data.toString(), environment.get("XDG_DATA_HOME")),
                    () -> assertEquals("http://127.0.0.1:41234", environment.get("HTTPS_PROXY")),
                    () -> assertEquals("http://127.0.0.1:41234", environment.get("HTTP_PROXY")),
                    () -> assertEquals("", environment.get("NO_PROXY")));
        }
    }

    @Nested
    @DisplayName("a CLI-variant job")
    class Cli {

        @Test
        @DisplayName("a ci job finds the operator's login and parses a stable output")
        void ci() {
            var environment = composer().forJob(
                    enrolment(null, Map.of("GH_CONFIG_DIR", "/home/op/.config/gh", "GLAB_CONFIG_DIR",
                            "/home/op/.config/glab", "XDG_CONFIG_HOME", "/home/op/.config")),
                    JobId.random(JobId.Kind.CI), Map.of(), Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(), false);

            assertAll(
                    () -> assertEquals("/home/op/.config/gh", environment.get("GH_CONFIG_DIR")),
                    () -> assertEquals("/home/op/.config/glab", environment.get("GLAB_CONFIG_DIR")),
                    () -> assertEquals("/home/op/.config", environment.get("XDG_CONFIG_HOME")),
                    () -> assertEquals("1", environment.get("GH_PROMPT_DISABLED")),
                    () -> assertEquals("1", environment.get("NO_COLOR")));
        }

        @Test
        @DisplayName("a git job never prompts, keeps the index unlocked and names its committer")
        void git() {
            var environment = composer().forJob(enrolment(null, Map.of()), JobId.random(JobId.Kind.GIT), Map.of(),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.of("/run/ssh-agent"),
                    Optional.of("pm-mcp"), Optional.of("noreply@example.test"), false);

            assertAll(
                    () -> assertEquals("0", environment.get("GIT_TERMINAL_PROMPT")),
                    () -> assertEquals("never", environment.get("GCM_INTERACTIVE")),
                    () -> assertEquals("0", environment.get("GIT_OPTIONAL_LOCKS")),
                    () -> assertEquals("/run/ssh-agent", environment.get("SSH_AUTH_SOCK")),
                    () -> assertEquals("pm-mcp", environment.get("GIT_COMMITTER_NAME")),
                    () -> assertEquals("noreply@example.test", environment.get("GIT_COMMITTER_EMAIL")));
        }
    }

    @Nested
    @DisplayName("a probe")
    class Probe {

        @Test
        @DisplayName("has a PATH of the probed executable and its interpreter only")
        void path() {
            var environment = composer().forProbe(Path.of("/opt/tool/bin/tool"),
                    Optional.of(Path.of("/usr/bin/python3")), false, new TreeMap<>());

            assertAll(
                    () -> assertEquals("/usr/bin:/opt/tool/bin", environment.get("PATH")),
                    () -> assertEquals("/tmp", environment.get("TMPDIR")),
                    () -> assertFalse(environment.containsKey("DBUS_SESSION_BUS_ADDRESS")),
                    () -> assertFalse(environment.containsKey("PM_MCP_JOB_TOKEN")));
        }
    }

    @Test
    @DisplayName("the credential vocabulary covers the documented patterns")
    void credentialVocabulary() {
        assertAll(
                () -> assertTrue(JobEnvironmentComposer.isCredential("GITHUB_TOKEN")),
                () -> assertTrue(JobEnvironmentComposer.isCredential("ANTHROPIC_API_KEY")),
                () -> assertTrue(JobEnvironmentComposer.isCredential("DB_PASSWORD")),
                () -> assertTrue(JobEnvironmentComposer.isCredential("SIGNING_KEY")),
                () -> assertTrue(JobEnvironmentComposer.isCredential("GIT_ASKPASS")),
                () -> assertFalse(JobEnvironmentComposer.isCredential(JobEnvironmentComposer.PM_MCP_JOB_TOKEN)),
                () -> assertFalse(JobEnvironmentComposer.isCredential("JAVA_HOME")),
                () -> assertFalse(JobEnvironmentComposer.isCredential("XDG_DATA_HOME")));
    }
}
