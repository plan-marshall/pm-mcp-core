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

import static de.planmarshall.runtime.testing.generators.RuntimeGenerators.jobIds;
import static de.planmarshall.runtime.testing.generators.RuntimeGenerators.words;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
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
 * <p>
 * Contract literals that stay: the variable names, the fixed values the composer sets ({@code 0}, {@code 1},
 * {@code never}, the empty {@code NO_PROXY}), the empty {@code LC_ALL} that falls back to the default, the host
 * directory {@code /tmp}, the credential name {@code GIT_ASKPASS} and the non-credential names. Every value the
 * enrolment captures or the caller passes, every path, the credential name prefixes and the job ids are generated.
 */
@EnableGeneratorController
@DisplayName("Job environment composer")
class JobEnvironmentComposerTest {

    private static final Path TMP = Path.of("/tmp");

    @TempDir
    Path base;

    private Path toolDirectory;

    @BeforeEach
    void drawTool() {
        toolDirectory = absolute();
    }

    /** @return a generated absolute path */
    private static Path absolute() {
        return Path.of("/" + words().next(), words().next(), words().next());
    }

    private JobEnvironmentComposer composer() {
        return new JobEnvironmentComposer(base, TMP);
    }

    private ProjectEnrolment enrolment(ContainerEngine engine, Map<String, String> variables) {
        return ConfinementFixture.enrolment(absolute(), engine, List.of(), variables,
                List.of(ConfinementFixture.tool(toolDirectory.resolve("git").toString())));
    }

    private Map<String, String> buildJob(ProjectEnrolment enrolment) {
        return composer().forJob(enrolment, new JobId(jobIds(JobId.Kind.BUILD).next()), Map.of(), null,
                null, null, null, null, null, false);
    }

    @Nested
    @DisplayName("a build job")
    class Build {

        private String javaHome;
        private String gradleHome;
        private String lang;
        private Map<String, String> captured;

        @BeforeEach
        void capture() {
            javaHome = absolute().toString();
            gradleHome = absolute().toString();
            lang = words().next() + ".UTF-8";
            captured = Map.of(
                    "JAVA_HOME", javaHome,
                    "GRADLE_USER_HOME", gradleHome,
                    "MAVEN_OPTS", "-X" + words().next(),
                    "GRADLE_OPTS", "-D" + words().next() + "=" + words().next(),
                    "GITHUB_TOKEN", Generators.letterStrings(8, 32).next(),
                    "LANG", lang,
                    "LC_ALL", "");
        }

        @Test
        @DisplayName("the MVND daemon store is a write path of the job's confinement")
        void daemonStoreIsWritable() {
            var enrolment = enrolment(null, Map.of());
            var job = new JobId(jobIds(JobId.Kind.BUILD).next());

            var environment = composer().forJob(enrolment, job, Map.of(), null, null,
                    null, null, null, null, false);
            var store = Path.of(environment.get("MVND_DAEMON_STORAGE")).getParent();
            var confinement = new JobConfinementComposer(base, TMP, Path.of("/dev"), null)
                    .forJob(enrolment, job, List.of(), false);

            assertAll(
                    () -> assertTrue(confinement.writePaths().contains(store),
                            "the daemon store is a write path of the job"),
                    () -> assertEquals(BuildDaemonStore.mvndDirectory(store),
                            Path.of(environment.get("MVND_DAEMON_STORAGE")),
                            "MVND_DAEMON_STORAGE is the store's mvnd directory"));
        }

        @Test
        @DisplayName("has the enrolled toolchain, a PATH of the host tools and no flag injector")
        void toolchain() {
            var environment = buildJob(enrolment(null, captured));

            assertAll(
                    () -> assertEquals(toolDirectory.toString(), environment.get("PATH"),
                            "PATH is the directory of the host tools"),
                    () -> assertEquals(javaHome, environment.get("JAVA_HOME"), "JAVA_HOME is the captured one"),
                    () -> assertEquals(gradleHome, environment.get("GRADLE_USER_HOME"),
                            "GRADLE_USER_HOME is the captured one"),
                    () -> assertEquals(TMP.toString(), environment.get("TMPDIR"), "TMPDIR is the composer's /tmp"),
                    () -> assertEquals(lang, environment.get("LANG"), "LANG is the captured locale"),
                    () -> assertEquals(JobEnvironmentComposer.DEFAULT_LC_ALL, environment.get("LC_ALL"),
                            "an empty LC_ALL falls back to the default"),
                    () -> assertTrue(environment.containsKey("MVND_DAEMON_STORAGE"),
                            "the build job has an MVND daemon store"),
                    () -> assertFalse(environment.containsKey("MAVEN_OPTS"), "MAVEN_OPTS is never passed on"),
                    () -> assertFalse(environment.containsKey("GRADLE_OPTS"), "GRADLE_OPTS is never passed on"));
        }

        @Test
        @DisplayName("carries no session bus, no runtime dir and no credential variable")
        void sanitised() {
            var environment = buildJob(enrolment(null, captured));

            assertAll(
                    () -> assertFalse(environment.containsKey("DBUS_SESSION_BUS_ADDRESS"),
                            "the session bus is never passed on"),
                    () -> assertFalse(environment.containsKey("XDG_RUNTIME_DIR"),
                            "the runtime dir is never passed on"),
                    () -> assertFalse(environment.containsKey("GITHUB_TOKEN"), "a captured token is dropped"),
                    () -> assertTrue(environment.keySet().stream()
                            .noneMatch(JobEnvironmentComposer::isCredential), "no credential variable is passed"));
        }

        @Test
        @DisplayName("receives the container variables only under the opt-in")
        void containerEngine() {
            var dockerHost = "unix://" + absolute();
            var dockerConfig = absolute().toString();
            var ryuk = String.valueOf(Generators.booleans().next());
            var withEngine = Map.of("DOCKER_HOST", dockerHost, "DOCKER_CONFIG", dockerConfig,
                    "TESTCONTAINERS_RYUK_DISABLED", ryuk);

            var without = buildJob(enrolment(null, withEngine));
            var with = buildJob(enrolment(ContainerEngine.DOCKER, withEngine));

            assertAll(
                    () -> assertFalse(without.containsKey("DOCKER_HOST"), "without opt-in DOCKER_HOST is dropped"),
                    () -> assertFalse(without.containsKey("TESTCONTAINERS_RYUK_DISABLED"),
                            "without opt-in the Ryuk flag is dropped"),
                    () -> assertEquals(dockerHost, with.get("DOCKER_HOST"), "with opt-in DOCKER_HOST is kept"),
                    () -> assertEquals(dockerConfig, with.get("DOCKER_CONFIG"), "with opt-in DOCKER_CONFIG is kept"),
                    () -> assertEquals(ryuk, with.get("TESTCONTAINERS_RYUK_DISABLED"),
                            "with opt-in the Ryuk flag is kept"));
        }
    }

    @Nested
    @DisplayName("a worker")
    class Worker {

        @Test
        @DisplayName("receives its token, id, data home and egress proxy, with NO_PROXY empty")
        void worker() {
            var job = new JobId(jobIds(JobId.Kind.LLM).next());
            var harnessVariable = "HARNESS_SETTING_" + Generators.integers(0, 9999).next();
            var harnessValue = words().next();
            var token = Generators.letterStrings(16, 32).next();
            var proxy = "http://127.0.0.1:" + Generators.integers(1024, 65535).next();

            var environment = composer().forJob(enrolment(null, Map.of()), job, Map.of(harnessVariable, harnessValue),
                    token, "XDG_DATA_HOME", proxy, null,
                    null, null, false);

            var data = base.resolve("run/jobs").resolve(job.value()).resolve("data");
            assertAll(
                    () -> assertEquals(token, environment.get("PM_MCP_JOB_TOKEN"), "the worker receives its token"),
                    () -> assertEquals(job.value(), environment.get("PM_MCP_JOB_ID"), "the worker receives its id"),
                    () -> assertEquals(harnessValue, environment.get(harnessVariable),
                            "the harness variable is passed on"),
                    () -> assertEquals(data.toString(), environment.get("XDG_DATA_HOME"),
                            "the data home is the job's data directory"),
                    () -> assertEquals(proxy, environment.get("HTTPS_PROXY"), "HTTPS_PROXY is the egress proxy"),
                    () -> assertEquals(proxy, environment.get("HTTP_PROXY"), "HTTP_PROXY is the egress proxy"),
                    () -> assertEquals("", environment.get("NO_PROXY"), "NO_PROXY is empty for a worker"));
        }
    }

    @Nested
    @DisplayName("a CLI-variant job")
    class Cli {

        @Test
        @DisplayName("a ci job finds the operator's login and parses a stable output")
        void ci() {
            var gh = absolute().toString();
            var glab = absolute().toString();
            var xdg = absolute().toString();

            var environment = composer().forJob(
                    enrolment(null, Map.of("GH_CONFIG_DIR", gh, "GLAB_CONFIG_DIR", glab, "XDG_CONFIG_HOME", xdg)),
                    new JobId(jobIds(JobId.Kind.CI).next()), Map.of(), null, null,
                    null, null, null, null, false);

            assertAll(
                    () -> assertEquals(gh, environment.get("GH_CONFIG_DIR"), "GH_CONFIG_DIR is the captured one"),
                    () -> assertEquals(glab, environment.get("GLAB_CONFIG_DIR"),
                            "GLAB_CONFIG_DIR is the captured one"),
                    () -> assertEquals(xdg, environment.get("XDG_CONFIG_HOME"), "XDG_CONFIG_HOME is the captured one"),
                    () -> assertEquals("1", environment.get("GH_PROMPT_DISABLED"), "the gh prompt is disabled"),
                    () -> assertEquals("1", environment.get("NO_COLOR"), "the output carries no colour"));
        }

        @Test
        @DisplayName("a git job never prompts, keeps the index unlocked and names its committer")
        void git() {
            var agent = absolute().toString();
            var committer = words().next();
            var email = words().next() + "@" + words().next() + ".test";

            var environment = composer().forJob(enrolment(null, Map.of()), new JobId(jobIds(JobId.Kind.GIT).next()),
                    Map.of(), null, null, null, agent,
                    committer, email, false);

            assertAll(
                    () -> assertEquals("0", environment.get("GIT_TERMINAL_PROMPT"), "git never prompts for a password"),
                    () -> assertEquals("never", environment.get("GCM_INTERACTIVE"),
                            "the credential manager never prompts"),
                    () -> assertEquals("0", environment.get("GIT_OPTIONAL_LOCKS"), "git takes no optional lock"),
                    () -> assertEquals(agent, environment.get("SSH_AUTH_SOCK"), "the ssh agent socket is passed"),
                    () -> assertEquals(committer, environment.get("GIT_COMMITTER_NAME"),
                            "the committer name is passed"),
                    () -> assertEquals(email, environment.get("GIT_COMMITTER_EMAIL"),
                            "the committer email is passed"));
        }
    }

    @Nested
    @DisplayName("a probe")
    class Probe {

        @Test
        @DisplayName("has a PATH of the probed executable and its interpreter only")
        void path() {
            var executableDirectory = absolute();
            var interpreterDirectory = absolute();

            var environment = composer().forProbe(executableDirectory.resolve(words().next()),
                    interpreterDirectory.resolve("python3"), false, new TreeMap<>());

            assertAll(
                    () -> assertEquals(interpreterDirectory + ":" + executableDirectory, environment.get("PATH"),
                            "PATH is the interpreter and the executable"),
                    () -> assertEquals(TMP.toString(), environment.get("TMPDIR"), "TMPDIR is the composer's /tmp"),
                    () -> assertFalse(environment.containsKey("DBUS_SESSION_BUS_ADDRESS"),
                            "a probe carries no session bus"),
                    () -> assertFalse(environment.containsKey("PM_MCP_JOB_TOKEN"), "a probe carries no job token"));
        }
    }

    @Test
    @DisplayName("the credential vocabulary covers the documented patterns over generated name prefixes")
    void credentialVocabulary() {
        var prefix = Generators.letterStrings(1, 8).next().toUpperCase(Locale.ROOT) + "_";

        assertAll(
                () -> assertTrue(JobEnvironmentComposer.isCredential(prefix + "TOKEN"), "a _TOKEN is a credential"),
                () -> assertTrue(JobEnvironmentComposer.isCredential(prefix + "API_KEY"),
                        "an _API_KEY is a credential"),
                () -> assertTrue(JobEnvironmentComposer.isCredential(prefix + "PASSWORD"),
                        "a _PASSWORD is a credential"),
                () -> assertTrue(JobEnvironmentComposer.isCredential(prefix + "KEY"), "a _KEY is a credential"),
                () -> assertTrue(JobEnvironmentComposer.isCredential("GIT_ASKPASS"), "GIT_ASKPASS is a credential"),
                () -> assertFalse(JobEnvironmentComposer.isCredential(JobEnvironmentComposer.PM_MCP_JOB_TOKEN),
                        "the job token is no credential variable"),
                () -> assertFalse(JobEnvironmentComposer.isCredential("JAVA_HOME"), "JAVA_HOME is no credential"),
                () -> assertFalse(JobEnvironmentComposer.isCredential("XDG_DATA_HOME"),
                        "XDG_DATA_HOME is no credential"));
    }
}
