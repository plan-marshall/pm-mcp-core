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
import static de.planmarshall.runtime.job.JobEnrolmentFixture.SCRIPT_BINDING;
import static de.planmarshall.runtime.job.JobEnrolmentFixture.SCRIPT_PATH;
import static de.planmarshall.runtime.testing.generators.RuntimeGenerators.words;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.ProjectEnrolment;
import de.planmarshall.core.store.ProjectEnrolment.Argv;
import de.planmarshall.core.store.ProjectEnrolment.BuildSystem;
import de.planmarshall.core.store.ProjectEnrolment.HostToolName;
import de.planmarshall.core.store.ProjectEnrolment.WrapperName;
import de.planmarshall.core.testing.generators.CoreGenerators;
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
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;
import de.planmarshall.runtime.workspace.WorkspaceConfinement;

/**
 * Stages 0 and 1 of the verify-not-resolve pipeline (PM-EXEC-2, PM-SEC-2, PM-TEST-5) over a real enrolment store: a
 * tampered, insecure, unreadable or missing enrolment is refused with its mapped reason before the working directory
 * is looked at, and a working directory outside the workspace is refused as {@code path_traversal_rejected}. Each
 * refusal has its matched positive control in the same configuration, and a fault of the key source is no refusal.
 * The verified job carries the validated arguments of a project script job, and no such arguments for any other job.
 * <p>
 * Contract literals that stay: the tampered enrolment body (a well-formed enrolment file that lacks its fields), the
 * file modes, the log texts of the refusal record, the fixture directories {@code beside} and {@code scripts}, the
 * Maven verb of the defaults and the {@code git status} argument of the fixture. Project ids, link names, member
 * names, values and the unreadable sidecar are generated.
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
        @DisplayName("a tampered enrolment (contract body) is refused as enrolment_tampered, with no changed path")
        void tampered() throws Exception {
            Files.write(fixture.store.file(fixture.project),
                    "{\"format_version\":1}".getBytes(StandardCharsets.UTF_8));

            var refusal = refusedBeforeStageOne();

            assertAll(
                    () -> assertEquals(Reason.ENROLMENT_TAMPERED, refusal.reason(),
                            "a tampered enrolment is enrolment_tampered"),
                    () -> assertTrue(refusal.changedPaths().isEmpty(), "a tampered refusal names no changed path"),
                    () -> assertFalse(refusal.detail().contains("pm-operator"),
                            "the detail does not name the operator verb"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.ENROLMENT_TAMPERED.resolveIdentifierString()),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN,
                            VerifyNotResolvePipeline.class));
        }

        @Test
        @DisplayName("an insecure enrolment is refused as enrolment_insecure and logged once by the check")
        void insecure() throws Exception {
            Files.setPosixFilePermissions(fixture.store.file(fixture.project),
                    PosixFilePermissions.fromString("rw-r--r--"));

            var refusal = refusedBeforeStageOne();

            assertAll(
                    () -> assertEquals(Reason.ENROLMENT_INSECURE, refusal.reason(),
                            "a world-readable enrolment is insecure"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.ENROLMENT_INSECURE.resolveIdentifierString()),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN,
                            VerifyNotResolvePipeline.class));
        }

        @Test
        @DisplayName("an unreadable sidecar is refused as enrolment_invalid and logged once by the check")
        void unreadable() throws Exception {
            Files.write(fixture.store.sidecar(fixture.project),
                    Generators.letterStrings(4, 32).next().getBytes(StandardCharsets.UTF_8));

            var refusal = refusedBeforeStageOne();

            assertAll(
                    () -> assertEquals(Reason.ENROLMENT_INVALID, refusal.reason(),
                            "an unreadable sidecar is enrolment_invalid"),
                    () -> assertTrue(refusal.detail().contains("unreadable"),
                            "the detail names the sidecar as unreadable"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.ENROLMENT_INVALID.resolveIdentifierString()),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN,
                            VerifyNotResolvePipeline.class));
        }

        @Test
        @DisplayName("a missing enrolment is refused as not_enrolled and logged with PM_RUNTIME-122")
        void missing() {
            var other = CoreGenerators.projectEnrolments().next().projectId();
            var request = new JobRequest(other, fixture.beside.toString(), Set.of(), Set.of(),
                    new JobRequest.HostToolCall(ProjectEnrolment.HostToolName.GIT, List.of()));

            var refusal = refused(request);

            assertAll(
                    () -> assertEquals(Reason.NOT_ENROLLED, refusal.reason(),
                            "a project without enrolment is not_enrolled"),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, WorkspaceConfinement.class),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN, JOB_REFUSED),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            "'" + other + "' refused with reason 'not_enrolled'"));
        }

        @Test
        @DisplayName("positive control: the untouched enrolment passes stage 0 and reaches stage 1")
        void untouchedReachesStageOne() {
            var refusal = refused(fixture.gitStatus(fixture.beside.toString()));

            assertAll(
                    () -> assertEquals(Reason.PATH_TRAVERSAL_REJECTED, refusal.reason(),
                            "the untouched enrolment reaches stage 1"),
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

            var fault = assertThrows(SecretStoreException.class, () -> fixture.verify(pipeline, request),
                    "a locked keyring passes through as an exception");

            assertAll(
                    () -> assertEquals(SecretStoreException.Reason.LOCKED, fault.reason(),
                            "the fault carries the locked reason"),
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
                    () -> assertEquals(Reason.PATH_TRAVERSAL_REJECTED, refusal.reason(),
                            "the directory is path_traversal_rejected"),
                    () -> assertTrue(refusal.changedPaths().isEmpty(), "a traversal refusal names no changed path"),
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
            var link = "escape-" + words().next();
            Files.createSymbolicLink(fixture.root.resolve(link), fixture.beside);

            assertTraversalRejected(link);
        }

        @Test
        @DisplayName("a .. traversal out of the tree to the fixture directory beside it is refused")
        void parentTraversal() {
            assertTraversalRejected("../beside");
        }

        @Test
        @DisplayName("positive control: the enrolled root passes as its canonical path")
        void enrolledRoot() {
            var job = verified(fixture.gitStatus(fixture.root.toString()));

            assertAll(
                    () -> assertEquals(fixture.root, job.workingDirectory(),
                            "the working directory is the enrolled root"),
                    () -> assertEquals(fixture.project, job.enrolment().projectId(),
                            "the job carries the enrolled project"),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, VerifyNotResolvePipeline.class));
        }

        @Test
        @DisplayName("positive control: the fixture directory scripts given relative to the enrolled root passes")
        void relativeInsideTree() {
            var job = verified(fixture.gitStatus("scripts"));

            assertEquals(fixture.root.resolve("scripts"), job.workingDirectory(),
                    "the relative directory resolves in the tree");
        }

        @Test
        @DisplayName("positive control: a symbolic link that stays inside the tree passes as its target")
        void symlinkInsideTree() throws Exception {
            var link = "alias-" + words().next();
            Files.createSymbolicLink(fixture.root.resolve(link), fixture.root.resolve("scripts"));

            var job = verified(fixture.gitStatus(link));

            assertEquals(fixture.root.resolve("scripts"), job.workingDirectory(),
                    "the inner link resolves to its target");
        }

        @Test
        @DisplayName("positive control: a live linked worktree of the project passes")
        void liveWorktree() {
            var job = verified(fixture.gitStatus(fixture.worktree.toString()));

            assertEquals(fixture.worktree, job.workingDirectory(), "the live worktree is the working directory");
        }

        @Test
        @DisplayName("a linked worktree the caller did not name as live is refused")
        void worktreeNotLive() {
            var request = new JobRequest(fixture.project, fixture.worktree.toString(), Set.of(), Set.of(),
                    new JobRequest.HostToolCall(ProjectEnrolment.HostToolName.GIT, List.of()));

            var refusal = refused(request);

            assertEquals(Reason.PATH_TRAVERSAL_REJECTED, refusal.reason(),
                    "a worktree not named as live is refused");
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
            var single = "single-" + words().next();
            var list = "list-" + words().next();
            var arguments = Fields.of(Map.of(single, new Text(Generators.letterStrings(3, 10).next()),
                    list, new Items(List.of(new Text(fixture.module)))));

            var job = verified(fixture.request(fixture.root.toString(),
                    new ProjectScriptCall(fixture.scriptId, SCRIPT_BINDING, arguments)));

            assertAll(
                    () -> assertEquals(arguments, job.projectScriptArguments(),
                            "the job carries the validated arguments"),
                    () -> assertEquals(List.of(fixture.tools.get(HostToolName.PYTHON3).toString(),
                                    fixture.root.resolve(SCRIPT_PATH).toString()), job.argv(),
                            "the argv holds no script argument"),
                    () -> assertNull(job.templateSource(),
                            "a project script has no template source"));
        }

        @Test
        @DisplayName("a wrapper build job carries no project script arguments")
        void wrapperBuild() {
            var job = verifiedWithDefaults(new WrapperBuild(WrapperName.MVNW, "verify", null));

            assertAll(
                    () -> assertNull(job.projectScriptArguments(),
                            "a wrapper build carries no script arguments"),
                    () -> assertEquals(fixture.root.resolve(MVNW).toString(), job.argv().getFirst(),
                            "the argv starts with the wrapper"));
        }

        @Test
        @DisplayName("a host tool call carries no project script arguments")
        void hostToolCall() {
            var job = verified(fixture.gitStatus(fixture.root.toString()));

            assertAll(
                    () -> assertNull(job.projectScriptArguments(),
                            "a host tool call carries no script arguments"),
                    () -> assertEquals(List.of(fixture.tools.get(HostToolName.GIT).toString(), "status"),
                            job.argv(), "the argv is git and the status argument"));
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

        @Override
        public List<CredentialAccount> entries() {
            throw new SecretStoreException(SecretStoreException.Reason.LOCKED, "keyring locked");
        }
    }
}
