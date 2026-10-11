/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.start;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;


import de.cuioss.tools.logging.CuiLogger;
import de.planmarshall.api.MachinePaths;
import de.planmarshall.runtime.test.TestBases;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * The JVM guard of {@code PM-TECH-3}: steps 1 to 5 of the startup sequence do work whose cost does not grow with the
 * number of enrolled projects. The steps run, with the {@link AuditChainVerification#NONE} seam, on fresh bases with
 * {@value #NO_PROJECTS} and with {@value #SEEDED_PROJECTS} enrolled projects, each with an enrolment file and a
 * per-project machine store directory. The fixture data is written by the test for the test.
 * <p>
 * Every run starts on a clean base, and the populations alternate run by run so that a drift of the JVM (JIT, GC)
 * meets both alike. After {@value #WARM_UP_RUNS} warm-up runs of each population, the medians of
 * {@value #MEASURED_RUNS} measured runs each must agree within {@link #RELATIVE_TOLERANCE} of the larger median plus
 * {@link #ABSOLUTE_TOLERANCE}. The JVM figure is logged for the documentation.
 * <p>
 * A matched control makes the pass non-vacuous: after a start on a seeded base no seeded file has been read (step 4
 * reads no project file), shown by the access times of the seeded files, while a file read on purpose afterwards
 * does show the read.
 */
@Timeout(value = 300, unit = TimeUnit.SECONDS)
@DisplayName("JVM cold start of startup steps 1 to 5")
class StartupColdStartIT {

    private static final CuiLogger LOGGER = new CuiLogger(StartupColdStartIT.class);

    /** The population of the empty base. */
    static final int NO_PROJECTS = 0;

    /** The population of the seeded base: a few hundred enrolled projects. */
    static final int SEEDED_PROJECTS = 300;

    /** Runs of each population before the measurement, for class loading and JIT. */
    static final int WARM_UP_RUNS = 5;

    /** Measured runs of each population. */
    static final int MEASURED_RUNS = 15;

    /** How far the two medians may lie apart, relative to the larger one. */
    static final double RELATIVE_TOLERANCE = 0.5;

    /** How far the two medians may lie apart besides the relative tolerance: noise of the file system. */
    static final Duration ABSOLUTE_TOLERANCE = Duration.ofMillis(5);

    private static final String USER = System.getProperty("user.name");

    /** The modification time of the seeded files; their access time lies before it, so a read advances it. */
    private static final Instant SEEDED_MODIFIED = Instant.now().minus(Duration.ofHours(1));
    private static final Instant SEEDED_ACCESSED = SEEDED_MODIFIED.minus(Duration.ofHours(1));

    private final List<Path> bases = new ArrayList<>();

    @AfterEach
    void deleteBases() {
        bases.forEach(TestBases::delete);
    }

    /**
     * Creates a fresh base with the given number of enrolled projects.
     *
     * @return the seeded files: the enrolment file and the store identity of every project
     */
    private List<Path> seed(Path base, int projects) throws IOException {
        bases.add(base);
        Files.createDirectory(base, PosixModes.directoryAttribute());
        var enrolments = Files.createDirectory(base.resolve(BaseDirectoryCheck.ENROLMENTS),
                PosixModes.directoryAttribute());
        var seeded = new ArrayList<Path>();
        for (var i = 0; i < projects; i++) {
            var projectId = "project-%03d".formatted(i);
            seeded.add(Files.writeString(enrolments.resolve(projectId + ".json"),
                    "{\"project_id\":\"" + projectId + "\",\"root\":\"/work/" + projectId + "\"}"));
            var store = Files.createDirectories(base.resolve("projects").resolve(projectId));
            seeded.add(Files.writeString(store.resolve("store.json"), "{\"project_id\":\"" + projectId + "\"}"));
        }
        for (var file : seeded) {
            Files.getFileAttributeView(file, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS)
                    .setTimes(FileTime.from(SEEDED_MODIFIED), FileTime.from(SEEDED_ACCESSED), null);
        }
        return seeded;
    }

    /** Runs steps 1 to 5 on the base, closes the lock and returns the time of the steps alone. */
    private static long runSteps(Path base) throws IOException {
        var sequence = new StartupSequence(new MachinePaths(base, MachinePaths.Os.current()), USER,
                AuditChainVerification.NONE, _ -> {
                });
        var random = new SecureRandom();
        var started = System.nanoTime();
        var outcome = sequence.run(random);
        var elapsed = System.nanoTime() - started;
        var ready = assertInstanceOf(StartupSequence.Outcome.Ready.class, outcome, () -> "base " + base);
        ready.lock().close();
        return elapsed;
    }

    private long coldStart(int projects) throws IOException {
        var base = TestBases.create("pmc");
        seed(base, projects);
        try {
            return runSteps(base);
        } finally {
            TestBases.delete(base);
            bases.remove(base);
        }
    }

    private static long median(List<Long> samples) {
        var sorted = samples.stream().mapToLong(Long::longValue).sorted().toArray();
        var middle = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2;
    }

    private static Instant accessed(Path file) throws IOException {
        return Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).lastAccessTime()
                .toInstant();
    }

    @Test
    @DisplayName("the median of steps 1 to 5 with 300 enrolled projects agrees with the median with none")
    void shouldNotGrowWithEnrolledProjects() throws Exception {
        for (var i = 0; i < WARM_UP_RUNS; i++) {
            coldStart(NO_PROJECTS);
            coldStart(SEEDED_PROJECTS);
        }
        var empty = new ArrayList<Long>();
        var seeded = new ArrayList<Long>();
        for (var i = 0; i < MEASURED_RUNS; i++) {
            empty.add(coldStart(NO_PROJECTS));
            seeded.add(coldStart(SEEDED_PROJECTS));
        }

        var emptyMedian = median(empty);
        var seededMedian = median(seeded);
        var allowed = (long) (RELATIVE_TOLERANCE * Math.max(emptyMedian, seededMedian))
                + ABSOLUTE_TOLERANCE.toNanos();
        LOGGER.info("JVM cold start, steps 1 to 5, median of %s runs: %s us with %s projects, %s us with %s projects",
                MEASURED_RUNS, TimeUnit.NANOSECONDS.toMicros(emptyMedian), NO_PROJECTS,
                TimeUnit.NANOSECONDS.toMicros(seededMedian), SEEDED_PROJECTS);

        assertTrue(Math.abs(seededMedian - emptyMedian) <= allowed,
                () -> "medians %d ns (%d projects) and %d ns (%d projects) differ by more than %d ns".formatted(
                        emptyMedian, NO_PROJECTS, seededMedian, SEEDED_PROJECTS, allowed));
    }

    @Test
    @DisplayName("control: a start on a seeded base reads no seeded file, while a read on purpose shows")
    void shouldLeaveSeededFilesUnread() throws Exception {
        var base = TestBases.create("pmc");
        var seededFiles = seed(base, SEEDED_PROJECTS);
        var before = new HashMap<Path, Instant>();
        for (var file : seededFiles) {
            before.put(file, accessed(file));
        }

        runSteps(base);
        var readAfterStart = new ArrayList<Path>();
        for (var file : seededFiles) {
            if (!accessed(file).equals(before.get(file))) {
                readAfterStart.add(file);
            }
        }
        var control = seededFiles.getFirst();
        Files.readString(control);

        assertAll(
                () -> assertEquals(2 * SEEDED_PROJECTS, seededFiles.size(), "an enrolment and a store per project"),
                () -> assertEquals(List.of(), readAfterStart, "seeded files read by steps 1 to 5"),
                () -> assertTrue(accessed(control).isAfter(SEEDED_ACCESSED),
                        "the file system records a read, so an unread file is evidence"));
    }
}
