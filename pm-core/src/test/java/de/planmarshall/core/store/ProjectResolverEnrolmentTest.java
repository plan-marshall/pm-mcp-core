/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import de.planmarshall.core.store.ProjectResolution.EnrolmentRefused;
import de.planmarshall.core.store.ProjectResolution.NotEnrolled;
import de.planmarshall.core.store.ProjectResolution.RootConflict;

/**
 * The main checkout against the enrolment files (PM-SEC-2, PM-ARCH-1): one readable match resolves only when every
 * enrolment file was read; two readable matches are a root conflict; a refused or empty file refuses the resolution
 * even beside a match, because it may name the same root; a removed enrolment leaves no project.
 */
@DisplayName("ProjectResolver: the enrolment files")
class ProjectResolverEnrolmentTest extends ProjectResolverFixture {

    @Test
    @DisplayName("refuses a main checkout that two enrolments name as their project root")
    void rootConflict() throws Exception {
        enrol("pm-mcp-core-copy", main);

        var conflict = assertInstanceOf(RootConflict.class, resolver.resolve(main.resolve("README.md")),
                "Two enrolments of one root should be a conflict");

        assertAll(
                () -> assertEquals(ProjectResolution.ENROLMENT_ROOT_CONFLICT, conflict.code(),
                        "The conflict should carry its outcome code"),
                () -> assertEquals(new TreeSet<>(List.of(PROJECT, "pm-mcp-core-copy")), conflict.projectIds(),
                        "The conflict should name both projects"));
    }

    @Test
    @DisplayName("refuses a worktree of a main checkout that two enrolments name")
    void rootConflictOfWorktree() throws Exception {
        enrol("pm-mcp-core-copy", main);
        var worktree = worktreeOfMain(PLAN);

        assertInstanceOf(RootConflict.class, resolver.resolve(worktree.resolve("src/Main.java")));
    }

    @Test
    @DisplayName("keeps the root conflict of two readable enrolments, beside a refused file")
    void rootConflictBesideRefusedFile() throws Exception {
        enrol("pm-mcp-core-copy", main);
        Files.writeString(new EnrolmentStore(base).file("broken"), "{not json");

        var conflict = assertInstanceOf(RootConflict.class, resolver.resolve(main.resolve("README.md")),
                "Two readable matches decide the conflict before a refused file is reported");

        assertEquals(new TreeSet<>(List.of(PROJECT, "pm-mcp-core-copy")), conflict.projectIds());
    }

    @Test
    @DisplayName("refuses with the reason of an unreadable enrolment file when no readable one names the root")
    void unreadableEnrolmentWithoutMatch() throws Exception {
        var store = new EnrolmentStore(base);
        Files.delete(store.file(PROJECT));
        Files.writeString(store.file("broken"), "{not json");

        var refused = assertInstanceOf(EnrolmentRefused.class, resolver.resolve(main.resolve("README.md")),
                "An unreadable enrolment should not count as no enrolment");

        assertAll(
                () -> assertEquals(StoreRefusal.Outcome.UNREADABLE, refused.refusal().outcome(),
                        "The refusal should be that of the file"),
                () -> assertEquals(store.file("broken"), refused.refusal().file(),
                        "The refusal should name the file"));
    }

    @Test
    @DisplayName("refuses an enrolment file whose project id is not the name of its file")
    void enrolmentOfAnotherName() throws Exception {
        var store = new EnrolmentStore(base);
        Files.move(store.file(PROJECT), store.file("renamed"));

        var refused = assertInstanceOf(EnrolmentRefused.class, resolver.resolve(main.resolve("README.md")));

        assertEquals(store.file("renamed"), refused.refusal().file(), "The refusal should name the renamed file");
    }

    @Test
    @DisplayName("refuses the main checkout beside an unreadable enrolment file of another project, which may name"
            + " the same root")
    void unreadableEnrolmentBesideMatch() throws Exception {
        var store = new EnrolmentStore(base);
        Files.writeString(store.file("broken"), "{not json");

        var refused = assertInstanceOf(EnrolmentRefused.class, resolver.resolve(main.resolve("README.md")),
                "An unreadable enrolment may be a second enrolment of the same root");

        assertEquals(store.file("broken"), refused.refusal().file(), "The refusal should name the file");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    @DisplayName("refuses the main checkout beside a file that exists and cannot be read")
    void unreadableFileBesideMatch() throws Exception {
        var store = new EnrolmentStore(base);
        var broken = store.file("broken");
        Files.writeString(broken, "{not json");
        Files.setPosixFilePermissions(broken, PosixFilePermissions.fromString("---------"));
        try {
            assumeFalse(Files.isReadable(broken), "the test runs with a privilege that reads the file anyway");

            var refused = assertInstanceOf(EnrolmentRefused.class, resolver.resolve(main.resolve("README.md")),
                    "A file that exists and cannot be read may be a second enrolment of the same root");

            assertAll(
                    () -> assertEquals(broken, refused.refusal().file(), "The refusal should name the file"),
                    () -> assertEquals(StoreRefusal.Outcome.UNREADABLE, refused.refusal().outcome(),
                            "A read failure is refused as unreadable"));
        } finally {
            Files.setPosixFilePermissions(broken, PosixFilePermissions.fromString("rw-------"));
        }
    }

    @Test
    @DisplayName("refuses the main checkout beside an empty enrolment file, and a sidecar without its file is no"
            + " enrolment")
    void emptyEnrolmentAndSidecar() throws Exception {
        var store = new EnrolmentStore(base);
        Files.write(store.file("empty"), new byte[0]);
        Files.writeString(store.sidecar("other"), "sidecar");

        var refused = assertInstanceOf(EnrolmentRefused.class, resolver.resolve(main.resolve("README.md")),
                "An empty enrolment file was not read, so the match cannot be shown to be unique");
        Files.delete(store.file("empty"));

        assertAll(
                () -> assertEquals(store.file("empty"), refused.refusal().file(),
                        "The refusal should name the empty file"),
                () -> assertMainCheckout(resolver.resolve(main.resolve("README.md"))),
                () -> assertFalse(Files.exists(store.file("other")),
                        "A sidecar without its file is no enrolment"));
    }

    @Test
    @DisplayName("resolves to no project when the enrolment of the project was removed")
    void removedEnrolment() throws Exception {
        Files.delete(new EnrolmentStore(base).file(PROJECT));

        assertNotEnrolled(NotEnrolled.Reason.NO_ENROLMENT, resolver.resolve(main.resolve("README.md")));
    }
}
