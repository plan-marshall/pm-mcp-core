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

import static de.planmarshall.runtime.job.JobEnrolmentFixture.MVNW;
import static de.planmarshall.runtime.job.JobEnrolmentFixture.PROJECT;
import static de.planmarshall.runtime.job.JobEnrolmentFixture.SCRIPT_BINDING;
import static de.planmarshall.runtime.job.JobEnrolmentFixture.SCRIPT_ID;
import static de.planmarshall.runtime.job.JobEnrolmentFixture.SCRIPT_PATH;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;


import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.planmarshall.core.log.PmMcpLogMessages.WARN;
import de.planmarshall.core.store.ProjectEnrolment;
import de.planmarshall.core.store.ProjectEnrolment.Argv;
import de.planmarshall.core.store.ProjectEnrolment.BuildSystem;
import de.planmarshall.core.store.ProjectEnrolment.HostToolName;
import de.planmarshall.core.store.ProjectEnrolment.WrapperName;
import de.planmarshall.runtime.credentials.CredentialAccount;
import de.planmarshall.runtime.credentials.SecretStore;
import de.planmarshall.runtime.credentials.SecretStoreException;
import de.planmarshall.runtime.enrolment.EnrolmentCheck;
import de.planmarshall.runtime.enrolment.EnrolmentFileCheck;
import de.planmarshall.runtime.enrolment.EnrolmentKey;
import de.planmarshall.runtime.job.JobRefusal.Reason;
import de.planmarshall.runtime.job.JobRequest.Fields;
import de.planmarshall.runtime.job.JobRequest.Items;
import de.planmarshall.runtime.job.JobRequest.ProjectScriptCall;
import de.planmarshall.runtime.job.JobRequest.Text;
import de.planmarshall.runtime.job.JobRequest.WrapperBuild;
import de.planmarshall.runtime.workspace.WorkspaceConfinement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Stages 0 and 1 of the verify-not-resolve pipeline (PM-EXEC-2, PM-SEC-2, PM-TEST-5) over a real enrolment store: a
 * tampered, insecure, unreadable or missing enrolment is refused with its mapped reason before the working directory
 * is looked at, and a working directory outside the workspace is refused as {@code path_traversal_rejected}. Each
 * refusal has its matched positive control in the same configuration, and a fault of the key source is no refusal.
 * The verified job carries the validated arguments of a project script job, and no such arguments for any other job.
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("VerifyNotResolvePipeline")
class VerifyNotResolvePipelineTest {

    private static final String JOB_REFUSED = WARN.JOB_REFUSED.resolveIdentifierString();

    private JobEnrolmentFixture fixture;

    @BeforeEach
    void enrol() throws IOException {
        fixture = new JobEnrolmentFixture();
        fixture.enrol();
    }

    @AfterEach
    void delete() {
        fixture.close();
    }

    private JobRefusal refused(JobRequest request) {
        var outcome = fixture.verify(request);
        return assertInstanceOf(JobVerification.Refused.class, outcome, "the job should be refused").refusal();
    }

    private VerifiedJob verified(JobRequest request) {
        var outcome = fixture.verify(request);
        return assertInstanceOf(JobVerification.Verified.class, outcome, "the job should be verified").job();
    }

    /**
     * Stage 0 is checked with a working directory that stage 1 would refuse: a refusal of stage 0 that stage 1
     * did not follow carries the reason of stage 0 and leaves no record of the confinement.
     */
    @Nested
    @DisplayName("stage 0, the enrolment check, refuses before the working directory is looked at")
    class EnrolmentStage {

        private JobRefusal refusedBeforeStageOne() {
            var refusal = refused(fixture.gitStatus(fixture.beside.toString()));
            LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, WorkspaceConfinement.class);
            return refusal;
        }

        @Test
        @DisplayName("a tampered enrolment is refused as enrolment_tampered, naming the accept-edit command")
        void tampered() throws Exception {
            Files.write(fixture.store.file(PROJECT), "{\"format_version\":1}".getBytes(StandardCharsets.UTF_8));

            var refusal = refusedBeforeStageOne();

            assertAll(
                    () -> assertEquals(Reason.ENROLMENT_TAMPERED, refusal.reason()),
                    () -> assertEquals(Optional.of("pm-operator accept-edit enrolment " + PROJECT),
                            refusal.operatorCommand()),
                    () -> assertTrue(refusal.changedPaths().isEmpty()),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.ENROLMENT_TAMPERED.resolveIdentifierString()),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN,
                            VerifyNotResolvePipeline.class));
        }

        @Test
        @DisplayName("an insecure enrolment is refused as enrolment_insecure and logged once by the check")
        void insecure() throws Exception {
            Files.setPosixFilePermissions(fixture.store.file(PROJECT), PosixFilePermissions.fromString("rw-r--r--"));

            var refusal = refusedBeforeStageOne();

            assertAll(
                    () -> assertEquals(Reason.ENROLMENT_INSECURE, refusal.reason()),
                    () -> assertEquals(Optional.empty(), refusal.operatorCommand()),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.ENROLMENT_INSECURE.resolveIdentifierString()),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN,
                            VerifyNotResolvePipeline.class));
        }

        @Test
        @DisplayName("an unreadable sidecar is refused as enrolment_invalid and logged once by the check")
        void unreadable() throws Exception {
            Files.write(fixture.store.sidecar(PROJECT), "not a record".getBytes(StandardCharsets.UTF_8));

            var refusal = refusedBeforeStageOne();

            assertAll(
                    () -> assertEquals(Reason.ENROLMENT_INVALID, refusal.reason()),
                    () -> assertTrue(refusal.detail().contains("unreadable"), refusal.detail()),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.ENROLMENT_INVALID.resolveIdentifierString()),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN,
                            VerifyNotResolvePipeline.class));
        }

        @Test
        @DisplayName("a missing enrolment is refused as not_enrolled and logged with PM_MCP-180")
        void missing() {
            var request = new JobRequest("other-project", fixture.beside.toString(), Set.of(), Set.of(), new JobRequest.HostToolCall(ProjectEnrolment.HostToolName.GIT, List.of()));

            var refusal = refused(request);

            assertAll(
                    () -> assertEquals(Reason.NOT_ENROLLED, refusal.reason()),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, WorkspaceConfinement.class),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN, JOB_REFUSED),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            "'other-project' refused with reason 'not_enrolled'"));
        }

        @Test
        @DisplayName("positive control: the untouched enrolment passes stage 0 and reaches stage 1")
        void untouchedReachesStageOne() {
            var refusal = refused(fixture.gitStatus(fixture.beside.toString()));

            assertAll(
                    () -> assertEquals(Reason.PATH_TRAVERSAL_REJECTED, refusal.reason()),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, EnrolmentCheck.class));
        }

        @Test
        @DisplayName("a fault of the key source passes through as the exception it is, not as a refusal")
        void keySourceFault() {
            var lockedKey = new EnrolmentKey(new LockedKeyring(), fixture.base.resolve("state"),
                    JobEnrolmentFixture.USER, new SecureRandom());
            var pipeline = new VerifyNotResolvePipeline(new EnrolmentCheck(fixture.store, lockedKey,
                    new EnrolmentFileCheck(JobEnrolmentFixture.USER), fixture.sink), JobEnrolmentFixture.NO_DEFAULTS);
            var request = fixture.gitStatus(fixture.root.toString());

            var fault = assertThrows(SecretStoreException.class, () -> fixture.verify(pipeline, request));

            assertAll(
                    () -> assertEquals(SecretStoreException.Reason.LOCKED, fault.reason()),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN,
                            VerifyNotResolvePipeline.class));
        }
    }

    @Nested
    @DisplayName("stage 1 confines the working directory to the workspace")
    class WorkingDirectoryStage {

        private void assertTraversalRejected(String workingDirectory) {
            var refusal = refused(fixture.gitStatus(workingDirectory));

            assertAll(
                    () -> assertEquals(Reason.PATH_TRAVERSAL_REJECTED, refusal.reason()),
                    () -> assertEquals(Optional.empty(), refusal.operatorCommand()),
                    () -> assertTrue(refusal.changedPaths().isEmpty()),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN, JOB_REFUSED),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            "refused with reason 'path_traversal_rejected'"));
        }

        @Test
        @DisplayName("a working directory outside the tree is refused as path_traversal_rejected")
        void outsideTree() {
            assertTraversalRejected(fixture.beside.toString());
        }

        @Test
        @DisplayName("a symbolic link from the tree to a directory outside it is refused")
        void symlinkEscape() throws Exception {
            Files.createSymbolicLink(fixture.root.resolve("escape"), fixture.beside);

            assertTraversalRejected("escape");
        }

        @Test
        @DisplayName("a .. traversal out of the tree is refused")
        void parentTraversal() {
            assertTraversalRejected("../beside");
        }

        @Test
        @DisplayName("positive control: the enrolled root passes as its canonical path")
        void enrolledRoot() {
            var job = verified(fixture.gitStatus(fixture.root.toString()));

            assertAll(
                    () -> assertEquals(fixture.root, job.workingDirectory()),
                    () -> assertEquals(PROJECT, job.enrolment().projectId()),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, VerifyNotResolvePipeline.class));
        }

        @Test
        @DisplayName("positive control: a directory of the tree given relative to the enrolled root passes")
        void relativeInsideTree() {
            var job = verified(fixture.gitStatus("scripts"));

            assertEquals(fixture.root.resolve("scripts"), job.workingDirectory());
        }

        @Test
        @DisplayName("positive control: a symbolic link that stays inside the tree passes as its target")
        void symlinkInsideTree() throws Exception {
            Files.createSymbolicLink(fixture.root.resolve("alias"), fixture.root.resolve("scripts"));

            var job = verified(fixture.gitStatus("alias"));

            assertEquals(fixture.root.resolve("scripts"), job.workingDirectory());
        }

        @Test
        @DisplayName("positive control: a live linked worktree of the project passes")
        void liveWorktree() {
            var job = verified(fixture.gitStatus(fixture.worktree.toString()));

            assertEquals(fixture.worktree, job.workingDirectory());
        }

        @Test
        @DisplayName("a linked worktree the caller did not name as live is refused")
        void worktreeNotLive() {
            var request = new JobRequest(PROJECT, fixture.worktree.toString(), Set.of(), Set.of(), new JobRequest.HostToolCall(ProjectEnrolment.HostToolName.GIT, List.of()));

            var refusal = refused(request);

            assertEquals(Reason.PATH_TRAVERSAL_REJECTED, refusal.reason());
        }
    }

    @Nested
    @DisplayName("the verified job carries the validated arguments of a project script, and only of one")
    class ProjectScriptArguments {

        /** The caller's defaults: Maven {@code verify}; nothing else. */
        private static final DefaultTemplates DEFAULTS = (buildSystem, verb) ->
                buildSystem == BuildSystem.MAVEN && "verify".equals(verb) ? Optional.of(Argv.of("verify"))
                        : Optional.empty();

        private VerifiedJob verifiedWithDefaults(JobRequest.Target target) {
            var outcome = fixture.verify(fixture.pipeline(DEFAULTS), fixture.request(fixture.root.toString(), target));
            return assertInstanceOf(JobVerification.Verified.class, outcome, "the job should be verified").job();
        }

        @Test
        @DisplayName("a project script job carries exactly the validated arguments, and an argv without them")
        void projectScript() {
            var goal = Generators.letterStrings(3, 10).next();
            var arguments = Fields.of(Map.of("goal", new Text(goal),
                    "modules", new Items(List.of(new Text("core")))));

            var job = verified(fixture.request(fixture.root.toString(),
                    new ProjectScriptCall(SCRIPT_ID, SCRIPT_BINDING, arguments)));

            assertAll(
                    () -> assertEquals(Optional.of(arguments), job.projectScriptArguments()),
                    () -> assertEquals(List.of(fixture.tools.get(HostToolName.PYTHON3).toString(),
                            fixture.root.resolve(SCRIPT_PATH).toString()), job.argv()),
                    () -> assertEquals(Optional.empty(), job.templateSource()));
        }

        @Test
        @DisplayName("a wrapper build job carries no project script arguments")
        void wrapperBuild() {
            var job = verifiedWithDefaults(new WrapperBuild(WrapperName.MVNW, "verify", Optional.empty()));

            assertAll(
                    () -> assertEquals(Optional.empty(), job.projectScriptArguments()),
                    () -> assertEquals(fixture.root.resolve(MVNW).toString(), job.argv().getFirst()));
        }

        @Test
        @DisplayName("a host tool call carries no project script arguments")
        void hostToolCall() {
            var job = verified(fixture.gitStatus(fixture.root.toString()));

            assertAll(
                    () -> assertEquals(Optional.empty(), job.projectScriptArguments()),
                    () -> assertEquals(List.of(fixture.tools.get(HostToolName.GIT).toString(), "status"),
                            job.argv()));
        }
    }

    /** A keyring whose every read fails as locked. */
    private static final class LockedKeyring implements SecretStore {

        @Override
        public String name() {
            return SecretStore.KEYCHAIN;
        }

        @Override
        public void put(CredentialAccount account, String secret) {
            throw new SecretStoreException(SecretStoreException.Reason.LOCKED, "keyring locked");
        }

        @Override
        public Optional<String> get(CredentialAccount account) {
            throw new SecretStoreException(SecretStoreException.Reason.LOCKED, "keyring locked");
        }

        @Override
        public boolean delete(CredentialAccount account) {
            return false;
        }
    }
}
