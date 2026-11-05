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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.store.EnrolmentStore;
import de.planmarshall.core.store.TransactionId;

/**
 * The enrolled projects as the enrolment store lists them: the population of the recovery and the periodic sweep.
 */
@EnableGeneratorController
@DisplayName("Enrolled projects")
class EnrolledProjectsTest {

    /** Contract literal: the smallest JSON content of a file; the listing reads names only. */
    private static final String JSON = "{}";

    @TempDir
    Path base;

    private EnrolmentStore store;
    private Path directory;

    @BeforeEach
    void create() throws IOException {
        directory = Files.createDirectories(base.resolve(EnrolmentStore.DIRECTORY_NAME));
        store = new EnrolmentStore(base);
    }

    /** @return generated project ids, distinct by their prefixes, in the order of the ids */
    private static List<String> projectIds(int count) {
        return Stream.iterate(0, i -> i + 1).limit(count)
                .map(i -> (char) ('a' + i) + "-" + Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT))
                .toList();
    }

    private void file(String name) throws IOException {
        Files.writeString(directory.resolve(name), JSON);
    }

    @Test
    @DisplayName("lists the enrolment files in the order of their ids, not sidecars, intents or staged files "
            + "(the file suffixes are the store layout)")
    void listsEnrolmentFilesOnly() throws Exception {
        var ids = projectIds(5);
        var instant = Instant.ofEpochSecond(Generators.longs(1_700_000_000L, 2_000_000_000L).next());
        file(ids.get(1) + ".json");
        file(ids.get(0) + ".json");
        file(ids.get(2) + ".json.hmac");
        file(ids.get(3) + ".json.audit-intent");
        file(ids.get(4) + ".json.tmp." + TransactionId.generate(Clock.fixed(instant, ZoneOffset.UTC)).value());

        assertEquals(List.of(ids.get(0), ids.get(1)), EnrolledProjects.of(store).projectIds(),
                "only the enrolment files are listed, in order");
    }

    @Test
    @DisplayName("returns a copy; a later enrolment shows in the next call")
    void copyPerCall() throws Exception {
        var ids = projectIds(2);
        file(ids.get(0) + ".json");
        var enrolled = EnrolledProjects.of(store);
        var first = enrolled.projectIds();

        file(ids.get(1) + ".json");

        assertAll(
                () -> assertEquals(List.of(ids.get(0)), first, "the first call must not see a later enrolment"),
                () -> assertEquals(ids, enrolled.projectIds(), "the next call must show the later enrolment"));
    }

    @Test
    @DisplayName("a missing enrolments directory lists none, and so does the seam NONE")
    void none() {
        var elsewhere = base.resolve(Generators.letterStrings(3, 12).next());

        assertAll(
                () -> assertEquals(List.of(), EnrolledProjects.of(new EnrolmentStore(elsewhere)).projectIds(),
                        "a missing directory must list no project"),
                () -> assertEquals(List.of(), EnrolledProjects.NONE.projectIds(),
                        "the seam NONE must list no project"));
    }

    @Test
    @DisplayName("a project an interrupted unenrol left with an orphan sidecar and an intent is not listed")
    void orphanSidecarIsNotListed() throws Exception {
        var ids = projectIds(2);
        var enrolledId = ids.get(0);
        var orphan = ids.get(1);
        var fixture = new EnrolmentFixture(base);
        fixture.enrol(enrolledId, false);
        fixture.interruptUnenrol(orphan);

        assertAll(
                () -> assertEquals(List.of(enrolledId), EnrolledProjects.of(fixture.store).projectIds(),
                        "only the enrolled project must be listed"),
                () -> assertTrue(Files.exists(fixture.store.sidecar(orphan)),
                        "the orphan sidecar must be left in place"),
                () -> assertTrue(Files.exists(fixture.store.auditIntent(orphan)),
                        "the audit intent must be left in place"));
    }

    @Test
    @DisplayName("a null store is refused")
    void nullStore() {
        assertThrows(NullPointerException.class, () -> EnrolledProjects.of(null), "a null store must be refused");
    }
}
