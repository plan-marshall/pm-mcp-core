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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.store.JobId;
import de.planmarshall.core.store.ProjectEnrolment;
import de.planmarshall.core.store.ProjectEnrolment.ContainerEngine;
import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * The composition of a job's confinement (PM-SEC-5): the write set holds the project root, the enrolled caches, the
 * temporary directory and the device nodes; the runtime base directory is denied and appears in the write set only as
 * the project's daemon store or a worker's data directory; the session bus and, without the opt-in, the container
 * engine's sockets are denied.
 * <p>
 * Contract literals that stay: the host directories {@code /tmp} and {@code /dev}, the well-known Docker sockets, the
 * layout of the runtime base ({@code projects}, {@code daemons}, {@code run/jobs}, {@code data},
 * {@code run/runtime.sock}), the protected tree {@code ~/.plan-marshall}, the variable names {@code GH_CONFIG_DIR} and
 * {@code DOCKER_HOST} with the {@code unix://} scheme. The user id of the session paths, the caches, the CLI and
 * harness directories, the job ids and the cache names below the base are generated.
 */
@EnableGeneratorController
@DisplayName("Job confinement composer")
class JobConfinementComposerTest {

    private static final Path TMP = Path.of("/tmp");
    private static final Path DEV = Path.of("/dev");

    @TempDir
    Path base;

    @TempDir
    Path project;

    private int uid;
    private Path bus;
    private Path cache;

    @BeforeEach
    void drawHost() {
        uid = Generators.integers(1000, 60000).next();
        bus = Path.of("/run/user/" + uid + "/bus");
        cache = absolute();
    }

    /** @return a generated absolute path outside the runtime base and the project */
    private static Path absolute() {
        return Path.of("/home", words().next(), "." + words().next(), words().next());
    }

    /** @return a generated job id of that kind */
    private static JobId jobId(JobId.Kind kind) {
        return new JobId(kind.prefix() + "-" + RuntimeGenerators.sha256Digests().next().substring(0, 16));
    }

    private JobConfinementComposer composer() {
        return new JobConfinementComposer(base, TMP, DEV, Optional.of(bus));
    }

    private ProjectEnrolment enrolment(ContainerEngine engine, Map<String, String> variables) {
        return ConfinementFixture.enrolment(project, engine, List.of(cache.toString()), variables, List.of());
    }

    private static boolean under(Path root, Path path) {
        return path.toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize());
    }

    @Nested
    @DisplayName("a build job")
    class Build {

        @Test
        @DisplayName("writes its project root, its caches, /tmp, /dev and its daemon store, and denies the base")
        void writeSet() {
            var job = jobId(JobId.Kind.BUILD);

            var enrolment = enrolment(null, Map.of());
            var confinement = composer().forJob(enrolment, job, List.of(), false);

            var daemonStore = BuildDaemonStore.path(base, enrolment.projectId(), List.of(project, cache, TMP, DEV));
            assertAll(
                    () -> assertTrue(confinement.writePaths().contains(project), "the project root is writable"),
                    () -> assertTrue(confinement.writePaths().contains(cache), "the enrolled cache is writable"),
                    () -> assertTrue(confinement.writePaths().contains(TMP), "the temporary directory is writable"),
                    () -> assertTrue(confinement.writePaths().contains(DEV), "the device nodes are writable"),
                    () -> assertTrue(confinement.writePaths().contains(daemonStore),
                            "the project's daemon store is writable"),
                    () -> assertTrue(confinement.deniedPaths().contains(base), "the runtime base is denied"),
                    () -> assertTrue(confinement.deniedPaths().contains(bus), "the session bus socket is denied"),
                    () -> assertEquals(List.of(), confinement.readPaths(), "a build job has no extra read path"));
        }

        @Test
        @DisplayName("the base directory appears only as the daemon store, never as the root or a cache")
        void baseNeverWritable() {
            var job = jobId(JobId.Kind.BUILD);

            var enrolment = enrolment(null, Map.of());
            var confinement = composer().forJob(enrolment, job, List.of(), false);

            assertAll(
                    () -> assertTrue(confinement.writePaths().stream()
                            .filter(path -> under(base, path))
                            .allMatch(path -> path.startsWith(base.resolve("projects").resolve(enrolment.projectId())
                                    .resolve("daemons"))), "below the base only the daemon store is writable"),
                    () -> assertFalse(confinement.writePaths().stream()
                            .anyMatch(path -> path.equals(Path.of(System.getProperty("user.home"),
                                    ".plan-marshall"))), "the ~/.plan-marshall tree is never writable"),
                    () -> assertFalse(confinement.writePaths().contains(base), "the base itself is never writable"));
        }

        @Test
        @DisplayName("a probe of a checkout's script writes its checkout and reads its job-private directory")
        void probeOfScript() {
            var job = jobId(JobId.Kind.PROBE);

            var confinement = composer().forProbe(job, Optional.of(project), List.of(cache), false, List.of());

            assertAll(
                    () -> assertTrue(confinement.writePaths().contains(project), "the probed checkout is writable"),
                    () -> assertTrue(confinement.writePaths().contains(cache), "the probe's cache is writable"),
                    () -> assertEquals(List.of(base.resolve("run/jobs").resolve(job.value())),
                            confinement.readPaths(), "the probe reads its job-private directory"));
        }

        @Test
        @DisplayName("a harness probe gets a data directory that exists and is writable")
        void harnessProbeData() {
            var job = jobId(JobId.Kind.PROBE);

            var confinement = composer().forProbe(job, Optional.empty(), List.of(), true, List.of());

            var data = base.resolve("run/jobs").resolve(job.value()).resolve("data");
            assertAll(
                    () -> assertTrue(Files.isDirectory(data), "the data directory of the probe exists"),
                    () -> assertTrue(confinement.writePaths().contains(data), "the data directory is writable"));
        }
    }

    @Nested
    @DisplayName("the job kinds differ")
    class Kinds {

        @Test
        @DisplayName("a ci job writes its CLI configuration directory and no daemon store")
        void ciJob() {
            var job = jobId(JobId.Kind.CI);
            var configDirectory = absolute();

            var confinement = composer().forJob(
                    enrolment(null, Map.of("GH_CONFIG_DIR", configDirectory.toString())), job, List.of(), false);

            assertAll(
                    () -> assertTrue(confinement.writePaths().contains(configDirectory),
                            "the CLI configuration directory is writable"),
                    () -> assertTrue(confinement.writePaths().stream()
                            .noneMatch(path -> path.startsWith(base.resolve("projects"))),
                            "a ci job writes no daemon store"));
        }

        @Test
        @DisplayName("a git job writes the daemon store only when it runs repository hooks")
        void gitJob() {
            var job = jobId(JobId.Kind.GIT);
            var enrolment = enrolment(null, Map.of());

            var withoutHooks = composer().forJob(enrolment, job, List.of(), false);
            var withHooks = composer().forJob(enrolment, job, List.of(), true);

            assertAll(
                    () -> assertFalse(withoutHooks.writePaths().stream()
                            .anyMatch(path -> path.startsWith(base.resolve("projects"))),
                            "without hooks no daemon store is writable"),
                    () -> assertTrue(withHooks.writePaths().stream()
                            .anyMatch(path -> path.startsWith(base.resolve("projects"))),
                            "with hooks the daemon store is writable"));
        }

        @Test
        @DisplayName("a worker reads its job-private directory and the runtime socket and writes its data directory")
        void worker() {
            var job = jobId(JobId.Kind.LLM);
            var harnessPath = absolute();

            var confinement = composer().forJob(enrolment(null, Map.of()), job, List.of(harnessPath), false);

            var jobPrivate = base.resolve("run/jobs").resolve(job.value());
            assertAll(
                    () -> assertTrue(confinement.writePaths().contains(jobPrivate.resolve("data")),
                            "the worker's data directory is writable"),
                    () -> assertTrue(confinement.writePaths().contains(harnessPath),
                            "the harness path is writable"),
                    () -> assertTrue(confinement.readPaths().contains(jobPrivate),
                            "the job-private directory is readable"),
                    () -> assertTrue(confinement.readPaths().contains(base.resolve("run/runtime.sock")),
                            "the runtime socket is readable"));
        }
    }

    @Nested
    @DisplayName("the container engine")
    class Container {

        @Test
        @DisplayName("without the opt-in its sockets are denied, the captured unix DOCKER_HOST included")
        void withoutOptIn() {
            var job = jobId(JobId.Kind.BUILD);
            var rootless = Path.of("/run/user/" + uid + "/docker.sock");

            var confinement = composer().forJob(
                    enrolment(null, Map.of("DOCKER_HOST", "unix://" + rootless)), job, List.of(), false);

            assertAll(
                    () -> assertTrue(confinement.deniedPaths().contains(Path.of("/var/run/docker.sock")),
                            "the /var/run socket is denied"),
                    () -> assertTrue(confinement.deniedPaths().contains(Path.of("/run/docker.sock")),
                            "the /run socket is denied"),
                    () -> assertTrue(confinement.deniedPaths().contains(rootless),
                            "the captured DOCKER_HOST socket is denied"));
        }

        @Test
        @DisplayName("with the opt-in its sockets stay in the read set")
        void withOptIn() {
            var job = jobId(JobId.Kind.BUILD);

            var confinement = composer().forJob(enrolment(ContainerEngine.DOCKER, Map.of()), job, List.of(), false);

            assertAll(
                    () -> assertFalse(confinement.deniedPaths().contains(Path.of("/var/run/docker.sock")),
                            "the /var/run socket stays readable"),
                    () -> assertFalse(confinement.deniedPaths().contains(Path.of("/run/docker.sock")),
                            "the /run socket stays readable"),
                    () -> assertFalse(confinement.deniedPaths().contains(Path.of("/run/user/" + uid + "/docker.sock")),
                            "the rootless socket stays in the read set under the opt-in"));
        }
    }

    @Nested
    @DisplayName("the protected runtime trees")
    class ProtectedRoots {

        @Test
        @DisplayName("a write path under the runtime base is refused")
        void writePathUnderBase() {
            var job = jobId(JobId.Kind.BUILD);
            var enrolment = ConfinementFixture.enrolment(project, null,
                    List.of(base.resolve(words().next()).toString()), Map.of(), List.of());
            var confinementComposer = composer();

            assertThrows(IllegalArgumentException.class,
                    () -> confinementComposer.forJob(enrolment, job, List.of(), false),
                    "a write path under the base is refused");
        }

        @Test
        @DisplayName("a write path under ~/.plan-marshall is refused")
        void writePathUnderPlanMarshall() {
            var job = jobId(JobId.Kind.BUILD);
            var protectedPath = Path.of(System.getProperty("user.home"), ".plan-marshall", words().next()).toString();
            var enrolment = ConfinementFixture.enrolment(project, null, List.of(protectedPath), Map.of(), List.of());
            var confinementComposer = composer();

            assertThrows(IllegalArgumentException.class,
                    () -> confinementComposer.forJob(enrolment, job, List.of(), false),
                    "a write path under ~/.plan-marshall is refused");
        }

        @Test
        @DisplayName("a base reached through a parent segment does not hide the protected tree")
        void traversedBase() {
            var traversed = base.resolve("..").resolve(base.getFileName());
            var confinementComposer = new JobConfinementComposer(traversed, TMP, DEV, Optional.empty());
            var job = jobId(JobId.Kind.BUILD);
            var enrolment = ConfinementFixture.enrolment(project, null,
                    List.of(base.resolve(words().next()).toString()), Map.of(), List.of());

            assertThrows(IllegalArgumentException.class,
                    () -> confinementComposer.forJob(enrolment, job, List.of(), false),
                    "a traversed base still protects its tree");
        }

        @Test
        @DisplayName("a project_root that contains the runtime base is refused")
        void projectRootContainingBase() {
            var job = jobId(JobId.Kind.BUILD);
            var containingRoot = base.getParent();
            var enrolment = ConfinementFixture.enrolment(containingRoot, null, List.of(), Map.of(), List.of());
            var confinementComposer = composer();

            assertThrows(IllegalArgumentException.class,
                    () -> confinementComposer.forJob(enrolment, job, List.of(), false),
                    "a root containing the base is refused");
        }
    }
}
