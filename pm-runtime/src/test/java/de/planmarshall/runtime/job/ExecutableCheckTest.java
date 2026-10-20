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

import static de.planmarshall.runtime.job.JobEnrolmentFixture.PROJECT;
import static de.planmarshall.runtime.job.JobEnrolmentFixture.SCRIPT_BINDING;
import static de.planmarshall.runtime.job.JobEnrolmentFixture.SCRIPT_ID;
import static de.planmarshall.runtime.job.JobEnrolmentFixture.SCRIPT_PATH;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.fail;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.ProjectEnrolment;
import de.planmarshall.core.store.ProjectEnrolment.Binding;
import de.planmarshall.core.store.ProjectEnrolment.HostToolName;
import de.planmarshall.core.store.ProjectEnrolment.WorkerKind;
import de.planmarshall.core.store.ProjectEnrolment.WrapperName;
import de.planmarshall.runtime.job.JobRefusal.Reason;
import de.planmarshall.runtime.job.JobRequest.HostToolBuild;
import de.planmarshall.runtime.job.JobRequest.HostToolCall;
import de.planmarshall.runtime.job.JobRequest.ProjectScriptCall;
import de.planmarshall.runtime.job.JobRequest.WrapperBuild;

/**
 * Stage 2 of the pipeline, the executable and its digest (PM-EXEC-1, PM-EXEC-2, PM-EXEC-3, PM-TEST-5): a host tool,
 * an interpreter or a project script that is not enrolled or does not match its enrolled digest is refused, and a
 * verified argument vector starts with the enrolled absolute path, the interpreter's first where one runs the target.
 * Each refusal has its matched positive control over the same enrolment.
 */
@EnableGeneratorController
@DisplayName("ExecutableCheck")
class ExecutableCheckTest {

    private static final JobRequest.Value NO_ARGUMENTS = JobRequest.Fields.of(Map.of());

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

    private VerifyNotResolvePipeline.Stage<List<String>> check(ProjectEnrolment checked, JobRequest.Target target) {
        return ExecutableCheck.check(PROJECT, checked, fixture.root, target);
    }

    private List<String> passed(ProjectEnrolment checked, JobRequest.Target target) {
        if (check(checked, target) instanceof VerifyNotResolvePipeline.Stage.Passed<List<String>> passed) {
            return passed.value();
        }
        return fail("the executable should pass");
    }

    private JobRefusal refused(ProjectEnrolment checked, JobRequest.Target target) {
        if (check(checked, target) instanceof VerifyNotResolvePipeline.Stage.Failed<List<String>> failed) {
            return failed.refusal();
        }
        return fail("the executable should be refused");
    }

    private String tool(HostToolName name) {
        return fixture.tools.get(name).toString();
    }

    private void drift(HostToolName name) throws IOException {
        Files.write(fixture.tools.get(name), JobEnrolmentFixture.randomContent());
    }

    private static HostToolCall call(HostToolName name) {
        return new HostToolCall(name, List.of("--version"));
    }

    @Nested
    @DisplayName("a host tool")
    class HostTools {

        @Test
        @DisplayName("that is not enrolled is refused as executable_not_enrolled")
        void notEnrolled() {
            var refusal = refused(JobEnrolmentFixture.withoutHostTool(enrolment, HostToolName.GH),
                    call(HostToolName.GH));

            assertAll(
                    () -> assertEquals(Reason.EXECUTABLE_NOT_ENROLLED, refusal.reason()));
        }

        @Test
        @DisplayName("positive control: enrolled and unchanged, it starts as its enrolled absolute path")
        void enrolled() {
            assertEquals(List.of(tool(HostToolName.GH)), passed(enrolment, call(HostToolName.GH)));
        }

        @Test
        @DisplayName("whose binary drifted from its digest is refused as executable_digest_mismatch")
        void digestDrift() throws Exception {
            drift(HostToolName.GIT);

            assertEquals(Reason.EXECUTABLE_DIGEST_MISMATCH, refused(enrolment, call(HostToolName.GIT)).reason());
        }

        @Test
        @DisplayName("replaced by a symbolic link to its unchanged content is refused as executable_digest_mismatch")
        void replacedBySymlink() throws Exception {
            var git = fixture.tools.get(HostToolName.GIT);
            var copy = Files.copy(git, fixture.base.resolve("git-copy"));
            Files.delete(git);
            Files.createSymbolicLink(git, copy);

            assertEquals(Reason.EXECUTABLE_DIGEST_MISMATCH, refused(enrolment, call(HostToolName.GIT)).reason());
        }

        @Test
        @DisplayName("shadowed by a same-named executable earlier on PATH is never started: the enrolled path is used")
        void shadowedOnPath() throws Exception {
            var shadowDirectory = Files.createDirectories(fixture.base.resolve("shadow"));
            var marker = fixture.base.resolve("shadow-started");
            var shadow = shadowDirectory.resolve(HostToolName.GIT.wireName());
            Files.writeString(shadow, "#!/bin/sh\ntouch " + marker + "\n");
            Files.setPosixFilePermissions(shadow, PosixFilePermissions.fromString("rwx------"));

            var argv = passed(enrolment, call(HostToolName.GIT));

            assertAll(
                    () -> assertEquals(List.of(tool(HostToolName.GIT)), argv),
                    () -> assertNotEquals(shadow.toString(), argv.getFirst()),
                    () -> assertFalse(Files.exists(marker), "the shadowing executable must never be started"));
        }

        @Test
        @DisplayName("that is a script whose interpreter drifted is refused as executable_digest_mismatch")
        void scriptInterpreterDrift() throws Exception {
            drift(HostToolName.RUBY);

            var refusal = refused(enrolment, call(HostToolName.ASCIIDOCTOR));

            assertAll(
                    () -> assertEquals(Reason.EXECUTABLE_DIGEST_MISMATCH, refusal.reason()),
                    () -> assertEquals("the host tool ruby differs from its enrolled digest", refusal.detail()));
        }

        @Test
        @DisplayName("that is a script whose interpreter is not enrolled is refused as executable_not_enrolled")
        void scriptInterpreterNotEnrolled() {
            var refusal = refused(JobEnrolmentFixture.withoutHostTool(enrolment, HostToolName.RUBY),
                    call(HostToolName.ASCIIDOCTOR));

            assertEquals(Reason.EXECUTABLE_NOT_ENROLLED, refusal.reason());
        }

        @Test
        @DisplayName("positive control: a script host tool starts as its interpreter followed by the script")
        void scriptHostTool() {
            assertEquals(List.of(tool(HostToolName.RUBY), tool(HostToolName.ASCIIDOCTOR)),
                    passed(enrolment, call(HostToolName.ASCIIDOCTOR)));
        }

        @Test
        @DisplayName("an enrolled interpreter called as a host tool is verified here; stage 3 refuses the call")
        void interpreterVerifiedHere() {
            assertAll(
                    () -> assertEquals(List.of(tool(HostToolName.PYTHON3)),
                            passed(enrolment, call(HostToolName.PYTHON3))),
                    () -> assertEquals(List.of(tool(HostToolName.RUBY)), passed(enrolment, call(HostToolName.RUBY))));
        }

        @Test
        @DisplayName("positive control: a build job of npm runs the enrolled npm")
        void npmBuild() {
            assertEquals(List.of(tool(HostToolName.NPM)),
                    passed(enrolment, new HostToolBuild("verify", Optional.empty())));
        }
    }

    @Nested
    @DisplayName("a project script")
    class ProjectScripts {

        private ProjectScriptCall call(String scriptId, Binding binding) {
            return new ProjectScriptCall(scriptId, binding, NO_ARGUMENTS);
        }

        @Test
        @DisplayName("positive control: enrolled and unchanged, it starts as python3 followed by the script")
        void enrolled() {
            assertEquals(List.of(tool(HostToolName.PYTHON3), fixture.root.resolve(SCRIPT_PATH).toString()),
                    passed(enrolment, call(SCRIPT_ID, SCRIPT_BINDING)));
        }

        @Test
        @DisplayName("that drifted from its digest is refused as executable_digest_mismatch")
        void scriptDrift() throws Exception {
            Files.writeString(fixture.root.resolve(SCRIPT_PATH), "import os\nos.system('id')\n");

            assertEquals(Reason.EXECUTABLE_DIGEST_MISMATCH, refused(enrolment, call(SCRIPT_ID, SCRIPT_BINDING))
                    .reason());
        }

        @Test
        @DisplayName("whose interpreter drifted is refused as executable_digest_mismatch")
        void interpreterDrift() throws Exception {
            drift(HostToolName.PYTHON3);

            var refusal = refused(enrolment, call(SCRIPT_ID, SCRIPT_BINDING));

            assertAll(
                    () -> assertEquals(Reason.EXECUTABLE_DIGEST_MISMATCH, refusal.reason()),
                    () -> assertEquals("the host tool python3 differs from its enrolled digest", refusal.detail()));
        }

        @Test
        @DisplayName("used for another binding than its enrolled one is refused as executable_not_enrolled")
        void bindingMismatch() {
            var refusal = refused(enrolment, call(SCRIPT_ID, new Binding(WorkerKind.LOG_PARSER, "gradle")));

            assertEquals(Reason.EXECUTABLE_NOT_ENROLLED, refusal.reason());
        }

        @Test
        @DisplayName("used for another worker kind than its enrolled one is refused as executable_not_enrolled")
        void workerKindMismatch() {
            var refusal = refused(enrolment, call(SCRIPT_ID, new Binding(WorkerKind.FINALIZE_STEP, "maven")));

            assertEquals(Reason.EXECUTABLE_NOT_ENROLLED, refusal.reason());
        }

        @Test
        @DisplayName("that is not enrolled is refused as executable_not_enrolled")
        void notEnrolled() {
            assertEquals(Reason.EXECUTABLE_NOT_ENROLLED,
                    refused(enrolment, call("other-script", SCRIPT_BINDING)).reason());
        }
    }

    @Nested
    @DisplayName("a wrapper job")
    class Wrappers {

        @Test
        @DisplayName("positive control: mvnw starts as the verified wrapper at the root of the tree")
        void mvnw() {
            assertEquals(List.of(fixture.root.resolve(JobEnrolmentFixture.MVNW).toString()),
                    passed(enrolment, new WrapperBuild(WrapperName.MVNW, "verify", Optional.empty())));
        }

        @Test
        @DisplayName("positive control: pw runs through the enrolled python3 with the wrapper as first argument")
        void pw() {
            assertEquals(List.of(tool(HostToolName.PYTHON3), fixture.root.resolve(JobEnrolmentFixture.PW).toString()),
                    passed(enrolment, new WrapperBuild(WrapperName.PW, "verify", Optional.empty())));
        }

        @Test
        @DisplayName("pw whose python3 drifted is refused as executable_digest_mismatch")
        void pwInterpreterDrift() throws Exception {
            drift(HostToolName.PYTHON3);

            assertEquals(Reason.EXECUTABLE_DIGEST_MISMATCH,
                    refused(enrolment, new WrapperBuild(WrapperName.PW, "verify", Optional.empty())).reason());
        }
    }
}
