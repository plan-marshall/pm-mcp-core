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
import java.util.Locale;
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
 * <p>
 * Contract literals that stay: the fixture paths of the wrapper and its pinned files, the attack payloads of PM-TEST-5
 * (the steered distribution URL and the {@code maven.config} executable), the {@code ..} that leaves the tree and the
 * {@code .git} a plain tree lacks. The tampered contents, the directory and file names the tests create and the other
 * wrapper file name are generated.
 */
@EnableGeneratorController
@DisplayName("WrapperVerification")
class WrapperVerificationTest {

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

    private static String word() {
        return Generators.letterStrings(1, 16).next().toLowerCase(Locale.ROOT);
    }

    private VerifyNotResolvePipeline.Stage<Path> verify(ProjectEnrolment checked, Path tree, WrapperName name) {
        return WrapperVerification.verify(checked, tree, name);
    }

    private Path passed(ProjectEnrolment checked, Path tree) {
        if (verify(checked, tree, WrapperName.MVNW) instanceof VerifyNotResolvePipeline.Stage.Passed<Path> passed) {
            return passed.value();
        }
        return fail("the wrapper should pass the check");
    }

    private JobRefusal refused(ProjectEnrolment checked, WrapperName name) {
        if (verify(checked, fixture.root, name) instanceof VerifyNotResolvePipeline.Stage.Failed<Path> failed) {
            return failed.refusal();
        }
        return fail("the wrapper should be refused by the check");
    }

    private void assertChanged(List<String> changed, JobRefusal refusal) {
        assertAll(
                () -> assertEquals(Reason.BUILD_CONFIG_CHANGED, refusal.reason(),
                        "the change is build_config_changed"),
                () -> assertEquals(changed, refusal.changedPaths(), "the refusal names the changed files"));
    }

    @Test
    @DisplayName("positive control: an unchanged wrapper with unchanged pinned files passes at the root of the tree")
    void unchanged() {
        assertEquals(fixture.root.resolve(MVNW), passed(enrolment, fixture.root),
                "the unchanged wrapper passes at the root");
    }

    @Nested
    @DisplayName("a changed build configuration is refused as build_config_changed, naming the changed files")
    class Changed {

        @Test
        @DisplayName("a tampered wrapper")
        void tamperedWrapper() throws Exception {
            Files.write(fixture.root.resolve(MVNW), JobEnrolmentFixture.randomContent());

            assertChanged(List.of(MVNW), refused(enrolment, WrapperName.MVNW));
        }

        @Test
        @DisplayName("a pinned file changed to a steered distribution URL (attack payload)")
        void changedPinnedFile() throws Exception {
            Files.writeString(fixture.root.resolve(WRAPPER_PROPERTIES), "distributionUrl=https://evil.invalid/\n");

            assertChanged(List.of(WRAPPER_PROPERTIES), refused(enrolment, WrapperName.MVNW));
        }

        @Test
        @DisplayName("a pinned file that appeared after the enrolment with an executable (attack payload)")
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
            var moved = Files.move(fixture.root.resolve(".mvn/wrapper"), fixture.base.resolve("moved-" + word()));
            Files.createSymbolicLink(fixture.root.resolve(".mvn/wrapper"), moved);

            assertChanged(List.of(WRAPPER_PROPERTIES), refused(enrolment, WrapperName.MVNW));
        }

        @Test
        @DisplayName("every changed file is named, sorted, not only the first")
        void everyChangedFile() throws Exception {
            Files.write(fixture.root.resolve(MVNW), JobEnrolmentFixture.randomContent());
            Files.write(fixture.root.resolve(WRAPPER_PROPERTIES), JobEnrolmentFixture.randomContent());
            Files.writeString(fixture.root.resolve(MAVEN_CONFIG), "-" + Generators.letterStrings(1, 16).next() + "\n");

            var refusal = refused(enrolment, WrapperName.MVNW);

            assertChanged(List.of(MAVEN_CONFIG, WRAPPER_PROPERTIES, MVNW), refusal);
        }

        @Test
        @DisplayName("a wrapper replaced by a symbolic link to its unchanged content")
        void replacedBySymlink() throws Exception {
            var copy = Files.copy(fixture.root.resolve(MVNW), fixture.base.resolve("copy-" + word()));
            Files.delete(fixture.root.resolve(MVNW));
            Files.createSymbolicLink(fixture.root.resolve(MVNW), copy);

            assertChanged(List.of(MVNW), refused(enrolment, WrapperName.MVNW));
        }

        @Test
        @DisplayName("a wrapper enrolled outside the root of the tree, even with a matching digest")
        void outsideTreeRoot() throws Exception {
            var directory = "nested-" + word();
            var nested = directory + "/" + MVNW;
            Files.createDirectories(fixture.root.resolve(directory));
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

            assertEquals(fixture.root.resolve(MVNW), passed(named, fixture.root),
                    "the wrapper under its own name passes");
        }
    }

    @Test
    @DisplayName("a wrapper name that is not enrolled is refused as executable_not_enrolled")
    void notEnrolledName() {
        var refusal = refused(enrolment, WrapperName.GRADLEW);

        assertAll(
                () -> assertEquals(Reason.EXECUTABLE_NOT_ENROLLED, refusal.reason(),
                        "an unenrolled wrapper is not enrolled"),
                () -> assertEquals(List.of(), refusal.changedPaths(), "the refusal names no changed path"));
    }

    @Test
    @DisplayName("positive control: a wrapper not tracked by git, in a tree without git, passes when it matches")
    void notTrackedByGit() throws Exception {
        var plain = Files.createDirectories(fixture.base.resolve("plain-" + word()));
        Files.copy(fixture.root.resolve(MVNW), plain.resolve(MVNW));
        Files.createDirectories(plain.resolve(".mvn/wrapper"));
        Files.copy(fixture.root.resolve(WRAPPER_PROPERTIES), plain.resolve(WRAPPER_PROPERTIES));

        var wrapper = passed(enrolment, plain);

        assertAll(
                () -> assertEquals(plain.resolve(MVNW), wrapper, "the wrapper of the plain tree passes"),
                () -> assertFalse(Files.exists(plain.resolve(".git")), "the plain tree has no git"));
    }
}
