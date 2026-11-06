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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import de.planmarshall.core.store.ProjectResolution.EnrolmentRefused;
import de.planmarshall.core.store.ProjectResolution.NotEnrolled;
import de.planmarshall.core.store.ProjectResolution.RootConflict;
import de.planmarshall.core.testing.generators.CoreGenerators;

/**
 * The main checkout against the enrolment files (PM-SEC-2, PM-ARCH-1): one readable match resolves only when every
 * enrolment file was read; two readable matches are a root conflict; a refused or empty file refuses the resolution
 * even beside a match, because it may name the same root; a removed enrolment leaves no project. The torn document
 * {@code {not json} and the POSIX modes are contract fixtures; project ids and file contents are generated.
 */
@EnableGeneratorController
@DisplayName("ProjectResolver: the enrolment files")
class ProjectResolverEnrolmentTest extends ProjectResolverFixture {

    // Torn wire document: an enrolment file that does not parse.
    private static final String TORN = "{not json";

    /** @return a project id of the enrolment grammar that differs from the enrolled project */
    private static String otherProject() {
        String id;
        do {
            id = CoreGenerators.projectEnrolments().next().projectId();
        } while (PROJECT.equals(id));
        return id;
    }

    /** @return a lowercase name for an enrolment file that names no enrolled project */
    private static String fileName() {
        return "file-" + Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }

    @Test
    @DisplayName("refuses a main checkout that two enrolments name as their project root")
    void rootConflict() throws Exception {
        var copy = otherProject();
        enrol(copy, main);

        var conflict = assertInstanceOf(RootConflict.class, resolver.resolve(main.resolve("README.md")),
                "Two enrolments of one root should be a conflict");

        assertAll(
                () -> assertEquals(ProjectResolution.ENROLMENT_ROOT_CONFLICT, conflict.code(),
                        "The conflict should carry its outcome code"),
                () -> assertEquals(new TreeSet<>(List.of(PROJECT, copy)), conflict.projectIds(),
                        "The conflict should name both projects"));
    }

    @Test
    @DisplayName("refuses a worktree of a main checkout that two enrolments name")
    void rootConflictOfWorktree() throws Exception {
        enrol(otherProject(), main);
        var worktree = worktreeOfMain(PLAN);

        assertInstanceOf(RootConflict.class, resolver.resolve(worktree.resolve("src/Main.java")),
                "The worktree should share the root conflict");
    }

    @Test
    @DisplayName("keeps the root conflict of two readable enrolments, beside a refused file")
    void rootConflictBesideRefusedFile() throws Exception {
        var copy = otherProject();
        enrol(copy, main);
        Files.writeString(new EnrolmentStore(base).file(fileName()), TORN);

        var conflict = assertInstanceOf(RootConflict.class, resolver.resolve(main.resolve("README.md")),
                "Two readable matches should decide the conflict");

        assertEquals(new TreeSet<>(List.of(PROJECT, copy)), conflict.projectIds(),
                "The conflict should name both projects");
    }

    @Test
    @DisplayName("refuses with the reason of an unreadable enrolment file when no readable one names the root")
    void unreadableEnrolmentWithoutMatch() throws Exception {
        var store = new EnrolmentStore(base);
        var broken = fileName();
        Files.delete(store.file(PROJECT));
        Files.writeString(store.file(broken), TORN);

        var refused = assertInstanceOf(EnrolmentRefused.class, resolver.resolve(main.resolve("README.md")),
                "An unreadable enrolment should not count as no enrolment");

        assertAll(
                () -> assertEquals(StoreRefusal.Outcome.UNREADABLE, refused.refusal().outcome(),
                        "The refusal should be that of the file"),
                () -> assertEquals(store.file(broken), refused.refusal().file(),
                        "The refusal should name the file"));
    }

    @Test
    @DisplayName("refuses an enrolment file whose project id is not the name of its file")
    void enrolmentOfAnotherName() throws Exception {
        var store = new EnrolmentStore(base);
        var renamed = fileName();
        Files.move(store.file(PROJECT), store.file(renamed));

        var refused = assertInstanceOf(EnrolmentRefused.class, resolver.resolve(main.resolve("README.md")),
                "A renamed enrolment file should be refused");

        assertEquals(store.file(renamed), refused.refusal().file(), "The refusal should name the renamed file");
    }

    @Test
    @DisplayName("refuses the main checkout beside an unreadable enrolment file of another project, which may name"
            + " the same root")
    void unreadableEnrolmentBesideMatch() throws Exception {
        var store = new EnrolmentStore(base);
        var broken = fileName();
        Files.writeString(store.file(broken), TORN);

        var refused = assertInstanceOf(EnrolmentRefused.class, resolver.resolve(main.resolve("README.md")),
                "An unreadable enrolment may enrol the same root");

        assertEquals(store.file(broken), refused.refusal().file(), "The refusal should name the file");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    @DisplayName("refuses the main checkout beside a file that exists and cannot be read (POSIX modes are contract)")
    void unreadableFileBesideMatch() throws Exception {
        var store = new EnrolmentStore(base);
        var broken = store.file(fileName());
        Files.writeString(broken, TORN);
        Files.setPosixFilePermissions(broken, PosixFilePermissions.fromString("---------"));
        try {
            assumeFalse(Files.isReadable(broken), "the test runs with a privilege that reads the file anyway");

            var refused = assertInstanceOf(EnrolmentRefused.class, resolver.resolve(main.resolve("README.md")),
                    "An unreadable file may enrol the same root");

            assertAll(
                    () -> assertEquals(broken, refused.refusal().file(), "The refusal should name the file"),
                    () -> assertEquals(StoreRefusal.Outcome.UNREADABLE, refused.refusal().outcome(),
                            "A read failure should be refused as unreadable"));
        } finally {
            Files.setPosixFilePermissions(broken, PosixFilePermissions.fromString("rw-------"));
        }
    }

    @Test
    @DisplayName("refuses the main checkout beside an empty enrolment file, and a sidecar without its file is no"
            + " enrolment")
    void emptyEnrolmentAndSidecar() throws Exception {
        var store = new EnrolmentStore(base);
        var empty = fileName();
        var other = fileName();
        Files.write(store.file(empty), new byte[0]);
        Files.writeString(store.sidecar(other), Generators.letterStrings(1, 40).next());

        var refused = assertInstanceOf(EnrolmentRefused.class, resolver.resolve(main.resolve("README.md")),
                "An empty enrolment file should refuse the match");
        Files.delete(store.file(empty));

        assertAll(
                () -> assertEquals(store.file(empty), refused.refusal().file(),
                        "The refusal should name the empty file"),
                () -> assertMainCheckout(resolver.resolve(main.resolve("README.md"))),
                () -> assertFalse(Files.exists(store.file(other)),
                        "A sidecar without its file is no enrolment"));
    }

    @Test
    @DisplayName("resolves to no project when the enrolment of the project was removed")
    void removedEnrolment() throws Exception {
        Files.delete(new EnrolmentStore(base).file(PROJECT));

        assertNotEnrolled(NotEnrolled.Reason.NO_ENROLMENT, resolver.resolve(main.resolve("README.md")));
    }
}
