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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.store.JobId;
import de.planmarshall.core.store.ProjectEnrolment;
import de.planmarshall.core.store.ProjectEnrolment.ContainerEngine;

/**
 * The composition of a job's confinement (PM-SEC-5): the write set holds the project root, the enrolled caches, the
 * temporary directory and the device nodes; the runtime base directory is denied and appears in the write set only as
 * the project's daemon store or a worker's data directory; the session bus and, without the opt-in, the container
 * engine's sockets are denied.
 */
@DisplayName("Job confinement composer")
class JobConfinementComposerTest {

    private static final String BUS = "/run/user/1000/bus";

    @TempDir
    Path base;

    @TempDir
    Path project;

    private JobConfinementComposer composer() {
        return new JobConfinementComposer(base, Path.of("/tmp"), Path.of("/dev"), Optional.of(Path.of(BUS)));
    }

    private ProjectEnrolment enrolment(ContainerEngine engine, Map<String, String> variables) {
        return ConfinementFixture.enrolment(project, engine, List.of("/home/operator/.m2/repository"), variables,
                List.of());
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
            var job = JobId.random(JobId.Kind.BUILD);

            var confinement = composer().forJob(enrolment(null, Map.of()), job, List.of(), false);

            var daemonStore = BuildDaemonStore.path(base, "job-project", List.of(project,
                    Path.of("/home/operator/.m2/repository"), Path.of("/tmp"), Path.of("/dev")));
            assertAll(
                    () -> assertTrue(confinement.writePaths().contains(project)),
                    () -> assertTrue(confinement.writePaths().contains(Path.of("/home/operator/.m2/repository"))),
                    () -> assertTrue(confinement.writePaths().contains(Path.of("/tmp"))),
                    () -> assertTrue(confinement.writePaths().contains(Path.of("/dev"))),
                    () -> assertTrue(confinement.writePaths().contains(daemonStore), confinement.writePaths().toString()),
                    () -> assertTrue(confinement.deniedPaths().contains(base)),
                    () -> assertTrue(confinement.deniedPaths().contains(Path.of(BUS)),
                            "the session bus socket is denied"),
                    () -> assertEquals(List.of(), confinement.readPaths()));
        }

        @Test
        @DisplayName("the base directory appears only as the daemon store, never as the root or a cache")
        void baseNeverWritable() {
            var job = JobId.random(JobId.Kind.BUILD);

            var confinement = composer().forJob(enrolment(null, Map.of()), job, List.of(), false);

            assertAll(
                    () -> assertTrue(confinement.writePaths().stream()
                            .filter(path -> under(base, path))
                            .allMatch(path -> path.startsWith(base.resolve("projects/job-project/daemons")))),
                    () -> assertFalse(confinement.writePaths().stream()
                            .anyMatch(path -> path.equals(Path.of(System.getProperty("user.home"),
                                    ".plan-marshall")))),
                    () -> assertFalse(confinement.writePaths().contains(base)));
        }

        @Test
        @DisplayName("a probe of a checkout's script writes its checkout and reads its job-private directory")
        void probeOfScript() {
            var job = JobId.random(JobId.Kind.PROBE);

            var confinement = composer().forProbe(job, Optional.of(project), List.of(Path.of("/home/operator/.m2")),
                    false, List.of());

            assertAll(
                    () -> assertTrue(confinement.writePaths().contains(project)),
                    () -> assertTrue(confinement.writePaths().contains(Path.of("/home/operator/.m2"))),
                    () -> assertEquals(List.of(base.resolve("run/jobs").resolve(job.value())), confinement.readPaths()));
        }

        @Test
        @DisplayName("a harness probe gets a data directory that exists and is writable")
        void harnessProbeData() {
            var job = JobId.random(JobId.Kind.PROBE);

            var confinement = composer().forProbe(job, Optional.empty(), List.of(), true, List.of());

            var data = base.resolve("run/jobs").resolve(job.value()).resolve("data");
            assertAll(
                    () -> assertTrue(Files.isDirectory(data)),
                    () -> assertTrue(confinement.writePaths().contains(data)));
        }
    }

    @Nested
    @DisplayName("the job kinds differ")
    class Kinds {

        @Test
        @DisplayName("a ci job writes its CLI configuration directory and no daemon store")
        void ciJob() {
            var job = JobId.random(JobId.Kind.CI);

            var confinement = composer().forJob(
                    enrolment(null, Map.of("GH_CONFIG_DIR", "/home/operator/.config/gh")), job, List.of(), false);

            assertAll(
                    () -> assertTrue(confinement.writePaths().contains(Path.of("/home/operator/.config/gh"))),
                    () -> assertTrue(confinement.writePaths().stream()
                            .noneMatch(path -> path.startsWith(base.resolve("projects")))));
        }

        @Test
        @DisplayName("a git job writes the daemon store only when it runs repository hooks")
        void gitJob() {
            var job = JobId.random(JobId.Kind.GIT);
            var enrolment = enrolment(null, Map.of());

            var withoutHooks = composer().forJob(enrolment, job, List.of(), false);
            var withHooks = composer().forJob(enrolment, job, List.of(), true);

            assertAll(
                    () -> assertFalse(withoutHooks.writePaths().stream()
                            .anyMatch(path -> path.startsWith(base.resolve("projects")))),
                    () -> assertTrue(withHooks.writePaths().stream()
                            .anyMatch(path -> path.startsWith(base.resolve("projects")))));
        }

        @Test
        @DisplayName("a worker reads its job-private directory and the runtime socket and writes its data directory")
        void worker() {
            var job = JobId.random(JobId.Kind.LLM);
            var harnessPath = Path.of("/home/operator/.claude/session");

            var confinement = composer().forJob(enrolment(null, Map.of()), job, List.of(harnessPath), false);

            var jobPrivate = base.resolve("run/jobs").resolve(job.value());
            assertAll(
                    () -> assertTrue(confinement.writePaths().contains(jobPrivate.resolve("data"))),
                    () -> assertTrue(confinement.writePaths().contains(harnessPath)),
                    () -> assertTrue(confinement.readPaths().contains(jobPrivate)),
                    () -> assertTrue(confinement.readPaths().contains(base.resolve("run/runtime.sock"))));
        }
    }

    @Nested
    @DisplayName("the container engine")
    class Container {

        @Test
        @DisplayName("without the opt-in its sockets are denied, the captured unix DOCKER_HOST included")
        void withoutOptIn() {
            var job = JobId.random(JobId.Kind.BUILD);

            var confinement = composer().forJob(
                    enrolment(null, Map.of("DOCKER_HOST", "unix:///run/user/1000/docker.sock")), job, List.of(), false);

            assertAll(
                    () -> assertTrue(confinement.deniedPaths().contains(Path.of("/var/run/docker.sock"))),
                    () -> assertTrue(confinement.deniedPaths().contains(Path.of("/run/docker.sock"))),
                    () -> assertTrue(confinement.deniedPaths().contains(Path.of("/run/user/1000/docker.sock"))));
        }

        @Test
        @DisplayName("with the opt-in its sockets stay in the read set")
        void withOptIn() {
            var job = JobId.random(JobId.Kind.BUILD);

            var confinement = composer().forJob(enrolment(ContainerEngine.DOCKER, Map.of()), job, List.of(), false);

            assertAll(
                    () -> assertFalse(confinement.deniedPaths().contains(Path.of("/var/run/docker.sock"))),
                    () -> assertFalse(confinement.deniedPaths().contains(Path.of("/run/docker.sock"))),
                    () -> assertFalse(confinement.deniedPaths().contains(Path.of("/run/user/1000/docker.sock")),
                            "the rootless socket stays in the read set under the opt-in"));
        }
    }

    @Nested
    @DisplayName("the protected runtime trees")
    class ProtectedRoots {

        @Test
        @DisplayName("a write path under the runtime base is refused")
        void writePathUnderBase() {
            var job = JobId.random(JobId.Kind.BUILD);
            var enrolment = ConfinementFixture.enrolment(project, null,
                    List.of(base.resolve("cache").toString()), Map.of(), List.of());

            assertThrows(IllegalArgumentException.class, () -> composer().forJob(enrolment, job, List.of(), false));
        }

        @Test
        @DisplayName("a write path under ~/.plan-marshall is refused")
        void writePathUnderPlanMarshall() {
            var job = JobId.random(JobId.Kind.BUILD);
            var protectedPath = Path.of(System.getProperty("user.home"), ".plan-marshall", "cache").toString();
            var enrolment = ConfinementFixture.enrolment(project, null, List.of(protectedPath), Map.of(), List.of());

            assertThrows(IllegalArgumentException.class, () -> composer().forJob(enrolment, job, List.of(), false));
        }
    }
}
