/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.enrolment;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.service.LiveWork;
import de.planmarshall.core.store.EnrolmentCodec;
import de.planmarshall.core.store.EnrolmentSidecar;
import de.planmarshall.core.store.EnrolmentStore;
import de.planmarshall.core.store.ProjectEnrolment;
import de.planmarshall.core.store.ProjectEnrolment.Build;
import de.planmarshall.core.store.StoreDigest;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.StoreRefusal.Outcome;
import de.planmarshall.runtime.credentials.FileSecretStore;
import de.planmarshall.runtime.lock.FileLockManager;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;
import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * The enrolment check (PM-EXEC-2, PM-SEC-4, PM-SEC-5): an untouched enrolment passes and reads typed values; a
 * tampered or insecure one is refused, logged and audited, and blocks only its own project.
 */
@EnableGeneratorController
@EnableTestLogger
@DisplayName("Enrolment check")
class EnrolmentCheckTest {

    private static final String PROJECT = "pm-mcp-core";
    private static final String USER = System.getProperty("user.name");
    private static final String COMMAND = "pm-operator accept-edit enrolment " + PROJECT;

    @TempDir
    Path base;

    private final List<AuditEvent> events = new ArrayList<>();
    private EnrolmentStore store;
    private EnrolmentKey key;
    private FileLockManager locks;
    private EnrolmentCheck check;
    private String otherProject;

    @BeforeEach
    void enrolTwoProjects() throws IOException {
        otherProject = RuntimeGenerators.projectIds().next();
        Files.createDirectories(base.resolve(EnrolmentStore.DIRECTORY_NAME));
        var state = Files.createDirectories(base.resolve("state"));
        store = new EnrolmentStore(base);
        key = new EnrolmentKey(new FileSecretStore(base.resolve("credentials")), state, USER, new SecureRandom());
        AuditSink sink = events::add;
        locks = new FileLockManager(sink);
        var fileCheck = new EnrolmentFileCheck(USER);
        check = new EnrolmentCheck(store, key, fileCheck, sink);
        var enrolments = new Enrolments(store, key, fileCheck, locks, projectId -> List.<LiveWork.Item>of(), sink);
        enrolments.enrol(enrolment(PROJECT, false), true);
        enrolments.enrol(enrolment(otherProject, false), false);
        events.clear();
    }

    static ProjectEnrolment enrolment(String projectId, boolean pilot) {
        return new ProjectEnrolment(projectId, "/home/dev/git/" + projectId, Instant.parse("2026-10-11T08:00:00Z"),
                pilot, null, null, List.of(), List.of(), Build.defaults(), Map.of(), new TreeMap<>(),
                new TreeMap<>(), new TreeMap<>(), List.of(), new TreeMap<>());
    }

    private ProjectEnrolment verify(String projectId) {
        try (var transaction = locks.openTransaction()) {
            return check.verify(transaction, projectId);
        }
    }

    private StoreRefusal refused(String projectId) {
        return assertThrows(StoreRefusal.class, () -> verify(projectId), "a refused verify throws StoreRefusal");
    }

    private void assertTampered(StoreRefusal refusal, Path file, String reason) {
        assertAll(
                () -> assertEquals(Outcome.ENROLMENT_TAMPERED, refusal.outcome(),
                        "the refusal outcome is enrolment tampered"),
                () -> assertEquals(file, refusal.file(), "the refusal names the tampered file"),
                () -> assertTrue(refusal.getMessage().contains(reason), refusal.getMessage()),
                () -> assertTrue(refusal.getMessage().contains(COMMAND), refusal.getMessage()),
                () -> assertEquals(List.of(AuditEvent.enrolmentTampered(PROJECT, reason)), events,
                        "one tampered event names the reason"),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                        WARN.ENROLMENT_TAMPERED.resolveIdentifierString()));
    }

    @Test
    @DisplayName("an untouched enrolment passes, reads typed values and records nothing")
    void untouched() {
        var enrolment = verify(PROJECT);

        assertAll(
                () -> assertEquals(PROJECT, enrolment.projectId(), "the enrolment names the requested project"),
                () -> assertTrue(enrolment.pilot(), "the enrolment carries the pilot flag"),
                () -> assertEquals("/home/dev/git/" + PROJECT, enrolment.projectRoot(),
                        "the project root is the enrolled one"),
                () -> assertTrue(events.isEmpty(), "a passing check records no audit event"),
                () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, EnrolmentCheck.class));
    }

    @Nested
    @DisplayName("a tampered enrolment is refused as enrolment_tampered, naming the accept-edit command")
    class Tampered {

        @Test
        @DisplayName("a file edited without updating its sidecar: its size or SHA-256 differs")
        void editedFile() throws Exception {
            Files.write(store.file(PROJECT), EnrolmentCodec.write(enrolment(PROJECT, false)));

            assertTampered(refused(PROJECT), store.file(PROJECT), EnrolmentCheck.DIGEST_MISMATCH);
        }

        @Test
        @DisplayName("a file edited to pilot: true with a forged size and SHA-256: its HMAC differs")
        void forgedDigest() throws Exception {
            var oldSidecar = EnrolmentSidecar.read(store.sidecar(otherProject), Files.readAllBytes(store.sidecar(otherProject)));
            var edited = EnrolmentCodec.write(enrolment(otherProject, true));
            Files.write(store.file(otherProject), edited);
            Files.write(store.sidecar(otherProject), new EnrolmentSidecar(StoreDigest.of(edited), oldSidecar.hmac()).write());

            var refusal = refused(otherProject);

            assertAll(
                    () -> assertEquals(Outcome.ENROLMENT_TAMPERED, refusal.outcome(),
                            "the refusal outcome is enrolment tampered"),
                    () -> assertTrue(refusal.getMessage().contains(EnrolmentCheck.HMAC_MISMATCH),
                            "the refusal names the HMAC mismatch"),
                    () -> assertTrue(refusal.getMessage().contains("pm-operator accept-edit enrolment " + otherProject),
                            "the refusal names the accept-edit command"),
                    () -> assertEquals(List.of(AuditEvent.enrolmentTampered(otherProject, EnrolmentCheck.HMAC_MISMATCH)),
                            events, "one tampered event for the forged file"));
        }

        @Test
        @DisplayName("a sidecar record whose size and SHA-256 are not those of the file")
        void sidecarDigest() throws Exception {
            var sidecar = EnrolmentSidecar.read(store.sidecar(PROJECT), Files.readAllBytes(store.sidecar(PROJECT)));
            Files.write(store.sidecar(PROJECT),
                    new EnrolmentSidecar(new StoreDigest(1, "0".repeat(64)), sidecar.hmac()).write());

            assertTampered(refused(PROJECT), store.file(PROJECT), EnrolmentCheck.DIGEST_MISMATCH);
        }

        @Test
        @DisplayName("a sidecar without its file")
        void sidecarWithoutFile() throws Exception {
            Files.delete(store.file(PROJECT));

            assertTampered(refused(PROJECT), store.sidecar(PROJECT), EnrolmentCheck.SIDECAR_WITHOUT_FILE);
        }

        @Test
        @DisplayName("a file without its sidecar")
        void fileWithoutSidecar() throws Exception {
            Files.delete(store.sidecar(PROJECT));

            assertTampered(refused(PROJECT), store.file(PROJECT), EnrolmentCheck.FILE_WITHOUT_SIDECAR);
        }

        @Test
        @DisplayName("a file whose integrity key no longer exists")
        void keyAbsent() throws Exception {
            Files.delete(key.keyFile());

            assertTampered(refused(PROJECT), store.file(PROJECT), EnrolmentCheck.KEY_ABSENT);
        }

        @Test
        @DisplayName("a file and sidecar whose key is absent from the active key source after a switch to the keyring")
        void keyAbsentAfterKeySourceSwitch() {
            var keyringKey = new EnrolmentKey(new InMemoryKeyring(), base.resolve("state"), USER, new SecureRandom());
            var keyringCheck = new EnrolmentCheck(store, keyringKey, new EnrolmentFileCheck(USER), events::add);

            StoreRefusal refusal;
            try (var transaction = locks.openTransaction()) {
                refusal = assertThrows(StoreRefusal.class, () -> keyringCheck.verify(transaction, PROJECT),
                        "a key absent from the key source is refused");
            }

            assertTampered(refusal, store.file(PROJECT), EnrolmentCheck.KEY_ABSENT);
            assertTrue(refusal.getMessage().contains("key source"), refusal.getMessage());
        }

        @Test
        @DisplayName("the same file and sidecar pass while the key exists in the active key source")
        void keyPresentControl() {
            var enrolment = verify(PROJECT);

            assertAll(
                    () -> assertEquals(PROJECT, enrolment.projectId(), "the enrolment names the requested project"),
                    () -> assertTrue(events.isEmpty(), "a passing check records no audit event"));
        }

        @Test
        @DisplayName("a refusal stands when the audit sink fails, and carries the failure")
        void failingSink() throws Exception {
            Files.delete(store.sidecar(PROJECT));
            var failure = new UncheckedIOException(new IOException("audit log full"));
            var failingCheck = new EnrolmentCheck(store, key, new EnrolmentFileCheck(USER), event -> {
                throw failure;
            });

            StoreRefusal refusal;
            try (var transaction = locks.openTransaction()) {
                refusal = assertThrows(StoreRefusal.class, () -> failingCheck.verify(transaction, PROJECT),
                        "the refusal stands when the audit sink fails");
            }

            assertAll(
                    () -> assertEquals(Outcome.ENROLMENT_TAMPERED, refusal.outcome(),
                            "the refusal outcome is enrolment tampered"),
                    () -> assertEquals(List.of(failure), List.of(refusal.getSuppressed()),
                            "the sink failure is attached to the refusal"));
        }
    }

    @Nested
    @DisplayName("an insecure enrolment is refused as enrolment_insecure")
    class Insecure {

        @Test
        @DisplayName("a file with a mode broader than 0600, logged and audited")
        void insecureFile() throws Exception {
            Files.setPosixFilePermissions(store.file(PROJECT), PosixFilePermissions.fromString("rw-r--r--"));

            var refusal = refused(PROJECT);

            assertAll(
                    () -> assertEquals(Outcome.ENROLMENT_INSECURE, refusal.outcome(),
                            "the refusal outcome is enrolment insecure"),
                    () -> assertEquals(store.file(PROJECT), refusal.file(), "the refusal names the enrolment file"),
                    () -> assertEquals(List.of(AuditEvent.enrolmentInsecure(PROJECT, "mode 0644 broader than 0600")),
                            events, "one insecure event for the broad mode"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.ENROLMENT_INSECURE.resolveIdentifierString()));
        }

        @Test
        @DisplayName("a sidecar that is a symbolic link")
        void linkedSidecar() throws Exception {
            var target = Files.move(store.sidecar(PROJECT), base.resolve("elsewhere.hmac"));
            Files.createSymbolicLink(store.sidecar(PROJECT), target);

            var refusal = refused(PROJECT);

            assertAll(
                    () -> assertEquals(Outcome.ENROLMENT_INSECURE, refusal.outcome(),
                            "the refusal outcome is enrolment insecure"),
                    () -> assertEquals(store.sidecar(PROJECT), refusal.file(), "the refusal names the sidecar"),
                    () -> assertEquals(List.of(AuditEvent.enrolmentInsecure(PROJECT, EnrolmentFileCheck.SYMBOLIC_LINK)),
                            events, "one insecure event for the symbolic link"));
        }

        @Test
        @DisplayName("an enrolment file that is a symbolic link is refused even when its target is the recorded file")
        void symlinkedEnrolmentFileIsRefusedEvenWhenItsTargetIsValid() throws Exception {
            var target = Files.move(store.file(PROJECT), base.resolve("elsewhere.json"));
            Files.createSymbolicLink(store.file(PROJECT), target);

            var refusal = refused(PROJECT);
            var other = verify(otherProject);

            assertAll(
                    () -> assertEquals(Outcome.ENROLMENT_INSECURE, refusal.outcome(),
                            "the refusal outcome is enrolment insecure"),
                    () -> assertEquals(store.file(PROJECT), refusal.file(), "the refusal names the enrolment file"),
                    () -> assertTrue(refusal.getMessage().contains(EnrolmentFileCheck.SYMBOLIC_LINK),
                            refusal.getMessage()),
                    () -> assertEquals(List.of(AuditEvent.enrolmentInsecure(PROJECT, EnrolmentFileCheck.SYMBOLIC_LINK)),
                            events, "one insecure event for the symbolic link"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.ENROLMENT_INSECURE.resolveIdentifierString()),
                    () -> assertEquals(otherProject, other.projectId(), "the other enrolled project still verifies"));
        }

        @Test
        @DisplayName("the same file and sidecar, unmoved, verify")
        void unmovedPairVerifies() {
            var enrolment = verify(PROJECT);

            assertAll(
                    () -> assertEquals(PROJECT, enrolment.projectId(), "the enrolment names the requested project"),
                    () -> assertTrue(events.isEmpty(), "a passing check records no audit event"));
        }
    }

    /**
     * A refused strict read of the sidecar or the file (PM-EXEC-1): refused as before, with its own outcome and
     * message, and also logged and audited as {@code enrolment_invalid}.
     */
    @Nested
    @DisplayName("a refused strict read is recorded as enrolment_invalid")
    class Invalid {

        private static final String NEWER_VERSION = "\"format_version\":2";
        private static final String CURRENT_VERSION = "\"format_version\":1";

        /** Writes the file and a sidecar that matches it and carries a valid HMAC, so only the strict read refuses. */
        private void writeSignedFile(byte[] content) throws IOException {
            Files.write(store.file(PROJECT), content);
            Files.write(store.sidecar(PROJECT), EnrolmentHmac.sidecar(key.read().orElseThrow(), content).write());
        }

        private byte[] otherVersionOfSidecar() throws IOException {
            var current = Files.readString(store.sidecar(PROJECT));
            return current.replace(CURRENT_VERSION, NEWER_VERSION).getBytes(StandardCharsets.UTF_8);
        }

        private void assertInvalid(StoreRefusal refusal, Outcome outcome, Path file, String expectedMessage) {
            var reason = outcome.code() + " in " + file.getFileName();
            assertAll(
                    () -> assertEquals(outcome, refusal.outcome(), "the refusal outcome matches the strict read"),
                    () -> assertEquals(expectedMessage, refusal.getMessage(), "the refusal message matches the read"),
                    () -> assertEquals(List.of(AuditEvent.enrolmentInvalid(PROJECT, reason)), events,
                            "one invalid event is recorded per refusal"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.ENROLMENT_INVALID.resolveIdentifierString()),
                    () -> assertEquals(otherProject, verify(otherProject).projectId(),
                            "the other project is still verified"));
        }

        @Test
        @DisplayName("an unreadable sidecar")
        void unreadableSidecar() throws Exception {
            var content = "not a record".getBytes(StandardCharsets.UTF_8);
            Files.write(store.sidecar(PROJECT), content);
            var expected = assertThrows(StoreRefusal.class, () -> store.decodeSidecar(PROJECT, content),
                    "an unreadable sidecar refuses the strict read");

            assertInvalid(refused(PROJECT), Outcome.UNREADABLE, store.sidecar(PROJECT), expected.getMessage());
        }

        @Test
        @DisplayName("a sidecar of another format version")
        void sidecarOfOtherVersion() throws Exception {
            var content = otherVersionOfSidecar();
            Files.write(store.sidecar(PROJECT), content);
            var expected = assertThrows(StoreRefusal.class, () -> store.decodeSidecar(PROJECT, content),
                    "an unreadable sidecar refuses the strict read");

            assertInvalid(refused(PROJECT), Outcome.FORMAT_NEWER, store.sidecar(PROJECT), expected.getMessage());
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', value = {"{\"format_version\":1}|UNREADABLE",
                "{\"format_version\":0}|FORMAT_OUTDATED", "{\"format_version\":2}|FORMAT_NEWER"})
        @DisplayName("a signed file that does not read strictly")
        void unreadableFile(String document, Outcome outcome) throws Exception {
            var content = document.getBytes(StandardCharsets.UTF_8);
            writeSignedFile(content);
            var expected = assertThrows(StoreRefusal.class, () -> store.decode(PROJECT, content),
                    "an unreadable file refuses the strict read");

            assertInvalid(refused(PROJECT), outcome, store.file(PROJECT), expected.getMessage());
        }

        @Test
        @DisplayName("a failing audit sink is suppressed on the refusal and does not change its outcome")
        void failingSink() throws Exception {
            Files.writeString(store.sidecar(PROJECT), "not a record");
            var failure = new UncheckedIOException(new IOException("audit log full"));
            var failingCheck = new EnrolmentCheck(store, key, new EnrolmentFileCheck(USER), event -> {
                throw failure;
            });

            StoreRefusal refusal;
            try (var transaction = locks.openTransaction()) {
                refusal = assertThrows(StoreRefusal.class, () -> failingCheck.verify(transaction, PROJECT),
                        "the refusal stands when the audit sink fails");
            }

            assertAll(
                    () -> assertEquals(Outcome.UNREADABLE, refusal.outcome(), "the refusal outcome is unreadable"),
                    () -> assertEquals(List.of(failure), List.of(refusal.getSuppressed()),
                            "the sink failure is attached to the refusal"));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"absent", "tampered", "insecure"})
        @DisplayName("an absent, tampered or insecure enrolment is not recorded as enrolment_invalid")
        void otherRefusals(String kind) throws Exception {
            var projectId = "absent".equals(kind) ? "unknown-project" : PROJECT;
            if ("tampered".equals(kind)) {
                Files.delete(store.sidecar(PROJECT));
            } else if ("insecure".equals(kind)) {
                Files.setPosixFilePermissions(store.file(PROJECT), PosixFilePermissions.fromString("rw-r--r--"));
            }

            refused(projectId);

            assertAll(
                    () -> assertTrue(events.stream()
                            .noneMatch(event -> AuditEvent.ENROLMENT_INVALID.equals(event.event())), events::toString),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN,
                            WARN.ENROLMENT_INVALID.resolveIdentifierString()));
        }
    }

    @Test
    @DisplayName("a refused project does not block another enrolled project")
    void isolation() throws Exception {
        Files.delete(store.sidecar(PROJECT));

        var refusal = refused(PROJECT);
        var other = verify(otherProject);

        assertAll(
                () -> assertEquals(Outcome.ENROLMENT_TAMPERED, refusal.outcome(),
                        "the refusal outcome is enrolment tampered"),
                () -> assertEquals(otherProject, other.projectId(), "the other enrolled project is unaffected"),
                () -> assertFalse(other.pilot(), "the other project is not a pilot"));
    }

    @Test
    @DisplayName("a project without file and sidecar is absent, not tampered, and records nothing")
    void notEnrolled() {
        var refusal = refused("unknown-project");

        assertAll(
                () -> assertEquals(Outcome.ABSENT, refusal.outcome(), "an unknown project is absent, not tampered"),
                () -> assertTrue(events.isEmpty(), "a passing check records no audit event"));
    }

    @Test
    @DisplayName("an unreadable sidecar record is refused as unreadable, naming the sidecar")
    void unreadableSidecar() throws Exception {
        Files.writeString(store.sidecar(PROJECT), "not a record");

        var refusal = refused(PROJECT);

        assertAll(
                () -> assertInstanceOf(StoreRefusal.class, refusal, "the refusal is a store refusal instance"),
                () -> assertEquals(Outcome.UNREADABLE, refusal.outcome(), "the refusal outcome is unreadable"),
                () -> assertEquals(store.sidecar(PROJECT), refusal.file(), "the refusal names the sidecar"));
    }
}
