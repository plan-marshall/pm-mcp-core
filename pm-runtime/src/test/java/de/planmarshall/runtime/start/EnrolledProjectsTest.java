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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;


import de.planmarshall.core.store.EnrolmentStore;
import de.planmarshall.core.store.TransactionId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The enrolled projects as the enrolment store lists them: the population of the recovery and the periodic sweep.
 */
@DisplayName("Enrolled projects")
class EnrolledProjectsTest {

    private static final String JSON = "{}";
    private static final String ENROLLED = "enrolled-project";
    private static final String ORPHAN = "orphan-project";

    @TempDir
    Path base;

    private EnrolmentStore store;
    private Path directory;

    @BeforeEach
    void create() throws IOException {
        directory = Files.createDirectories(base.resolve(EnrolmentStore.DIRECTORY_NAME));
        store = new EnrolmentStore(base);
    }

    private void file(String name) throws IOException {
        Files.writeString(directory.resolve(name), JSON);
    }

    @Test
    @DisplayName("lists the enrolment files in the order of their ids, not sidecars, intents or staged files")
    void listsEnrolmentFilesOnly() throws Exception {
        file("b-project.json");
        file("a-project.json");
        file("c-project.json.hmac");
        file("d-project.json.audit-intent");
        file("e-project.json.tmp." + TransactionId.generate(Clock.systemUTC()).value());

        assertEquals(List.of("a-project", "b-project"), EnrolledProjects.of(store).projectIds());
    }

    @Test
    @DisplayName("returns a copy; a later enrolment shows in the next call")
    void copyPerCall() throws Exception {
        file("a-project.json");
        var enrolled = EnrolledProjects.of(store);
        var first = enrolled.projectIds();

        file("b-project.json");

        assertAll(
                () -> assertEquals(List.of("a-project"), first),
                () -> assertEquals(List.of("a-project", "b-project"), enrolled.projectIds()));
    }

    @Test
    @DisplayName("a missing enrolments directory lists none, and so does the seam NONE")
    void none() {
        assertAll(
                () -> assertEquals(List.of(), EnrolledProjects.of(new EnrolmentStore(base.resolve("elsewhere")))
                        .projectIds()),
                () -> assertEquals(List.of(), EnrolledProjects.NONE.projectIds()));
    }

    @Test
    @DisplayName("a project an interrupted unenrol left with an orphan sidecar and an intent is not listed")
    void orphanSidecarIsNotListed() throws Exception {
        var fixture = new EnrolmentFixture(base);
        fixture.enrol(ENROLLED, false);
        fixture.interruptUnenrol(ORPHAN);

        assertAll(
                () -> assertEquals(List.of(ENROLLED), EnrolledProjects.of(fixture.store).projectIds()),
                () -> assertTrue(Files.exists(fixture.store.sidecar(ORPHAN))),
                () -> assertTrue(Files.exists(fixture.store.auditIntent(ORPHAN))));
    }

    @Test
    @DisplayName("a null store is refused")
    void nullStore() {
        assertThrows(NullPointerException.class, () -> EnrolledProjects.of(null));
    }
}
