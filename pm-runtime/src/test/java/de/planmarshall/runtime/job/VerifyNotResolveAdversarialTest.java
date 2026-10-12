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
import static de.planmarshall.runtime.job.JobEnrolmentFixture.SCRIPT_BINDING;
import static de.planmarshall.runtime.job.JobEnrolmentFixture.SCRIPT_ID;
import static de.planmarshall.runtime.job.JobEnrolmentFixture.SCRIPT_PATH;
import static de.planmarshall.runtime.job.JobEnrolmentFixture.WRAPPER_PROPERTIES;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;


import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.planmarshall.core.store.ProjectEnrolment.ArgumentSchema;
import de.planmarshall.core.store.ProjectEnrolment.Argv;
import de.planmarshall.core.store.ProjectEnrolment.HostToolName;
import de.planmarshall.core.store.ProjectEnrolment.SchemaType;
import de.planmarshall.core.store.ProjectEnrolment.WrapperName;
import de.planmarshall.runtime.job.JobRefusal.Reason;
import de.planmarshall.runtime.job.JobRequest.Fields;
import de.planmarshall.runtime.job.JobRequest.HostToolCall;
import de.planmarshall.runtime.job.JobRequest.ProjectScriptCall;
import de.planmarshall.runtime.job.JobRequest.Text;
import de.planmarshall.runtime.job.JobRequest.WrapperBuild;
import de.planmarshall.runtime.job.VerifiedJob.TemplateSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The adversarial confinement suite of the verify-not-resolve pipeline (PM-TEST-5, PM-EXEC-1). The hazard: a job
 * whose executable, build configuration, arguments or working directory a model or a repository change can steer
 * runs something the operator never enrolled, with the operator's credentials, outside the project's tree. Every
 * case runs the whole pipeline over a real enrolment store, key file and project tree, with no test double; each
 * group is one fixture of PM-TEST-5 that belongs to the pipeline (a tampered wrapper, an un-enrolled host tool,
 * unapproved arguments, an out-of-tree working directory), plus a tampered enrolment file. A refused case asserts the
 * outcome {@code Refused} and the exact reason code, for {@code build_config_changed} also the named file; each group
 * carries its matched positive control, verified in the same configuration.
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("VerifyNotResolvePipeline, adversarial confinement")
class VerifyNotResolveAdversarialTest {

    private static final String MODULE = "core";

    /** The caller's defaults: Maven {@code verify} with a module-argument group; nothing else. */
    private static final DefaultTemplates DEFAULTS = (buildSystem, verb) -> switch (buildSystem) {
        case MAVEN -> "verify".equals(verb)
                ? Optional.of(new Argv(List.of(new Argv.Literal("verify"),
                        new Argv.ModuleGroup(List.of("-pl", "{module}")))))
                : Optional.empty();
        default -> Optional.empty();
    };

    private JobEnrolmentFixture fixture;

    @BeforeEach
    void createFixture() throws IOException {
        fixture = new JobEnrolmentFixture();
    }

    @AfterEach
    void delete() {
        fixture.close();
    }

    private JobVerification verify(JobRequest.Target target) {
        return verify(fixture.root.toString(), target);
    }

    private JobVerification verify(String workingDirectory, JobRequest.Target target) {
        return fixture.verify(fixture.pipeline(DEFAULTS), fixture.request(workingDirectory, target));
    }

    private static JobRefusal refused(JobVerification outcome) {
        return assertInstanceOf(JobVerification.Refused.class, outcome, "the job should be refused").refusal();
    }

    private static VerifiedJob verified(JobVerification outcome) {
        return assertInstanceOf(JobVerification.Verified.class, outcome, "the job should be verified").job();
    }

    private static WrapperBuild maven(String module) {
        return new WrapperBuild(WrapperName.MVNW, "verify", Optional.ofNullable(module));
    }

    private static HostToolCall git(String... arguments) {
        return new HostToolCall(HostToolName.GIT, List.of(arguments));
    }

    @Nested
    @DisplayName("a tampered wrapper is refused as build_config_changed, naming the changed file")
    class TamperedWrapper {

        @BeforeEach
        void enrol() {
            fixture.enrol();
        }

        private void assertChanged(String changed) {
            var refusal = refused(verify(maven(MODULE)));

            assertAll(
                    () -> assertEquals(Reason.BUILD_CONFIG_CHANGED, refusal.reason()),
                    () -> assertEquals(List.of(changed), refusal.changedPaths()),
                    () -> assertEquals(Optional.of("pm-operator enrol " + PROJECT), refusal.operatorCommand()));
        }

        @Test
        @DisplayName("the bytes of the wrapper changed")
        void wrapperBytes() throws IOException {
            Files.write(fixture.root.resolve(MVNW), JobEnrolmentFixture.randomContent());

            assertChanged(MVNW);
        }

        @Test
        @DisplayName("a pinned file changed")
        void pinnedFileChanged() throws IOException {
            Files.writeString(fixture.root.resolve(WRAPPER_PROPERTIES), "distributionUrl=https://evil.invalid/\n");

            assertChanged(WRAPPER_PROPERTIES);
        }

        @Test
        @DisplayName("a pinned file that must stay absent appeared")
        void pinnedFileAppeared() throws IOException {
            Files.writeString(fixture.root.resolve(MAVEN_CONFIG), "-Dexec.executable=/bin/sh\n");

            assertChanged(MAVEN_CONFIG);
        }

        @Test
        @DisplayName("positive control: the unchanged wrapper is verified and runs the template after itself")
        void unchanged() {
            var job = verified(verify(maven(MODULE)));

            assertAll(
                    () -> assertEquals(List.of(fixture.root.resolve(MVNW).toString(), "verify", "-pl", MODULE),
                            job.argv()),
                    () -> assertEquals(Optional.of(TemplateSource.COMPILED_DEFAULT), job.templateSource()));
        }
    }

    @Nested
    @DisplayName("a host tool that is not enrolled, or not as enrolled, is refused")
    class UnenrolledHostTool {

        @BeforeEach
        void enrol() {
            fixture.enrol(JobEnrolmentFixture.withoutHostTool(fixture.enrolment(), HostToolName.GH));
        }

        @Test
        @DisplayName("a host tool missing from the enrolment is refused as executable_not_enrolled")
        void notEnrolled() {
            var refusal = refused(verify(new HostToolCall(HostToolName.GH, List.of("auth", "status"))));

            assertEquals(Reason.EXECUTABLE_NOT_ENROLLED, refusal.reason());
        }

        @Test
        @DisplayName("an enrolled host tool whose binary was replaced is refused as executable_digest_mismatch")
        void replacedBinary() throws IOException {
            Files.write(fixture.tools.get(HostToolName.GIT), JobEnrolmentFixture.randomContent());

            assertEquals(Reason.EXECUTABLE_DIGEST_MISMATCH, refused(verify(git("status"))).reason());
        }

        @Test
        @DisplayName("positive control: an enrolled, unchanged host tool of the same enrolment is verified")
        void enrolledTool() {
            var job = verified(verify(git("status")));

            assertEquals(List.of(fixture.tools.get(HostToolName.GIT).toString(), "status"), job.argv());
        }
    }

    @Nested
    @DisplayName("unapproved arguments are refused as argument_rejected")
    class UnapprovedArguments {

        @BeforeEach
        void enrol() {
            var properties = new TreeMap<String, ArgumentSchema>();
            properties.put("goal", new ArgumentSchema(SchemaType.STRING, null, null, false, null, "^[a-z-]+$", null,
                    null, 32L));
            fixture.enrol(JobEnrolmentFixture.withScriptSchema(fixture.enrolment(), new ArgumentSchema(
                    SchemaType.OBJECT, properties, List.of("goal"), true, null, null, null, null, null)));
        }

        private ProjectScriptCall script(JobRequest.Value arguments) {
            return new ProjectScriptCall(SCRIPT_ID, SCRIPT_BINDING, arguments);
        }

        @Test
        @DisplayName("a forbidden free flag in a build job")
        void forbiddenFlag() {
            assertEquals(Reason.ARGUMENT_REJECTED, refused(verify(maven("-Dexec.executable=/bin/sh"))).reason());
        }

        @Test
        @DisplayName("a shell metacharacter in a host tool call")
        void metacharacter() {
            assertEquals(Reason.ARGUMENT_REJECTED, refused(verify(git("log", "a;id"))).reason());
        }

        @Test
        @DisplayName("a module that is not a discovered module of the project")
        void unknownModule() {
            var module = "absent-" + Generators.letterStrings(3, 10).next();

            assertEquals(Reason.ARGUMENT_REJECTED, refused(verify(maven(module))).reason());
        }

        @Test
        @DisplayName("project script arguments that violate the enrolled schema")
        void schemaViolation() {
            var refusal = refused(verify(script(Fields.of(Map.of("goal", new Text("Not Allowed"))))));

            assertAll(
                    () -> assertEquals(Reason.ARGUMENT_REJECTED, refusal.reason()),
                    () -> assertEquals("the arguments violate the argument schema: pattern at $.goal",
                            refusal.detail()));
        }

        @Test
        @DisplayName("positive control: a discovered module in a build job is verified")
        void discoveredModule() {
            assertEquals(List.of(fixture.root.resolve(MVNW).toString(), "verify", "-pl", MODULE),
                    verified(verify(maven(MODULE))).argv());
        }

        @Test
        @DisplayName("positive control: a host tool call with ordinary flags is verified")
        void ordinaryHostToolArguments() {
            assertEquals(List.of(fixture.tools.get(HostToolName.GIT).toString(), "status", "-sb"),
                    verified(verify(git("status", "-sb"))).argv());
        }

        @Test
        @DisplayName("positive control: project script arguments that conform to the schema are verified")
        void conformingScriptArguments() {
            var job = verified(verify(script(Fields.of(Map.of("goal", new Text("module-tests"))))));

            assertEquals(List.of(fixture.tools.get(HostToolName.PYTHON3).toString(),
                    fixture.root.resolve(SCRIPT_PATH).toString()), job.argv());
        }
    }

    @Nested
    @DisplayName("a working directory outside the tree is refused as path_traversal_rejected")
    class OutOfTreeWorkingDirectory {

        @BeforeEach
        void enrol() {
            fixture.enrol();
        }

        private void assertTraversalRejected(String workingDirectory) {
            assertEquals(Reason.PATH_TRAVERSAL_REJECTED, refused(verify(workingDirectory, git("status"))).reason());
        }

        @Test
        @DisplayName("an absolute path beside the tree")
        void outsidePath() {
            assertTraversalRejected(fixture.beside.toString());
        }

        @Test
        @DisplayName("a symbolic link from the tree to a directory outside it")
        void symlinkEscape() throws IOException {
            Files.createSymbolicLink(fixture.root.resolve("escape"), fixture.beside);

            assertTraversalRejected("escape");
        }

        @Test
        @DisplayName("a .. traversal out of the tree")
        void parentTraversal() {
            assertTraversalRejected("../beside");
        }

        @Test
        @DisplayName("positive control: the enrolled root is verified as the working directory")
        void enrolledRoot() {
            assertEquals(fixture.root, verified(verify(git("status"))).workingDirectory());
        }

        @Test
        @DisplayName("positive control: a live linked worktree of the project is verified as the working directory")
        void liveWorktree() {
            assertEquals(fixture.worktree,
                    verified(verify(fixture.worktree.toString(), git("status"))).workingDirectory());
        }
    }

    @Nested
    @DisplayName("a tampered enrolment file is refused as enrolment_tampered")
    class TamperedEnrolment {

        @BeforeEach
        void enrol() {
            fixture.enrol();
        }

        @Test
        @DisplayName("the enrolment file was rewritten outside the operator path")
        void rewritten() throws IOException {
            Files.write(fixture.store.file(PROJECT), "{\"format_version\":1}".getBytes(StandardCharsets.UTF_8));

            var refusal = refused(verify(git("status")));

            assertAll(
                    () -> assertEquals(Reason.ENROLMENT_TAMPERED, refusal.reason()),
                    () -> assertEquals(Optional.of("pm-operator accept-edit enrolment " + PROJECT),
                            refusal.operatorCommand()));
        }

        @Test
        @DisplayName("positive control: the untouched enrolment file verifies the same job")
        void untouched() {
            assertEquals(fixture.root, verified(verify(git("status"))).workingDirectory());
        }
    }
}
