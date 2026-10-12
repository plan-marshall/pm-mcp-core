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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;


import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.TestLoggerFactory;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.planmarshall.core.log.PmMcpLogMessages.ERROR;
import de.planmarshall.core.log.PmMcpLogMessages.INFO;
import de.planmarshall.core.log.PmMcpLogMessages.WARN;
import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.store.EnrolmentCodec;
import de.planmarshall.core.store.EnrolmentStore;
import de.planmarshall.core.store.TransactionId;
import de.planmarshall.runtime.credentials.CredentialAccount;
import de.planmarshall.runtime.credentials.SecretStore;
import de.planmarshall.runtime.credentials.SecretStoreException;
import de.planmarshall.runtime.enrolment.EnrolmentCheck;
import de.planmarshall.runtime.enrolment.EnrolmentFileCheck;
import de.planmarshall.runtime.enrolment.EnrolmentKey;
import de.planmarshall.runtime.start.RecoveryStep.Name;
import de.planmarshall.runtime.start.RecoveryStep.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Step 1 of the per-project recovery (PM-SEC-5): a stopped write of the enrolment pair is settled first, then the
 * enrolment check runs; every refusal fails the step of its project only, with the message of the refusal.
 */
@EnableTestLogger
@DisplayName("Enrolment integrity step")
class EnrolmentIntegrityStepTest {

    private static final String PROJECT = "pm-mcp-core";
    private static final String OTHER = "other-project";

    @TempDir
    Path base;

    private EnrolmentFixture fixture;
    private EnrolmentIntegrityStep step;
    private byte[] oldFile;
    private byte[] oldSidecar;
    private byte[] newFile;
    private byte[] newSidecar;

    /** Enrols both projects; keeps the bytes of the old and of a new pair of the project, the old one in place. */
    @BeforeEach
    void enrol() throws IOException {
        fixture = new EnrolmentFixture(base);
        step = new EnrolmentIntegrityStep(fixture.store, fixture.check, fixture.locks);
        fixture.enrol(PROJECT, true);
        newFile = Files.readAllBytes(fixture.store.file(PROJECT));
        newSidecar = Files.readAllBytes(fixture.store.sidecar(PROJECT));
        fixture.enrol(PROJECT, false);
        oldFile = Files.readAllBytes(fixture.store.file(PROJECT));
        oldSidecar = Files.readAllBytes(fixture.store.sidecar(PROJECT));
        fixture.enrol(OTHER, false);
        fixture.events.clear();
    }

    private Path staged(Path target, TransactionId transactionId) {
        return EnrolmentFixture.staged(target, transactionId);
    }

    /** Writes a staged file as a write stages it: readable and writable by its owner only. */
    private static Path writeStaged(Path file, byte[] content) throws IOException {
        Files.write(file, content);
        return Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
    }

    private String report(Outcome outcome) {
        return assertInstanceOf(Outcome.Done.class, outcome).report();
    }

    private String failure(Outcome outcome) {
        return assertInstanceOf(Outcome.Failed.class, outcome).reason();
    }

    private void assertOtherProjectStillDone() {
        assertTrue(report(step.run(OTHER)).contains(EnrolmentIntegrityStep.VERIFIED));
    }

    @Test
    @DisplayName("a good enrolment is verified, and the step writes nothing and creates no key")
    void verified() throws IOException {
        var names = fixture.names();
        var keyBytes = Files.readAllBytes(fixture.key.keyFile());

        var outcome = step.run(PROJECT);

        assertAll(
                () -> assertEquals(Name.ENROLMENT_INTEGRITY, step.name()),
                () -> assertEquals(new Outcome.Done(EnrolmentIntegrityStep.VERIFIED), outcome),
                () -> assertEquals(names, fixture.names()),
                () -> assertArrayEquals(oldFile, Files.readAllBytes(fixture.store.file(PROJECT))),
                () -> assertArrayEquals(oldSidecar, Files.readAllBytes(fixture.store.sidecar(PROJECT))),
                () -> assertArrayEquals(keyBytes, Files.readAllBytes(fixture.key.keyFile())),
                () -> assertTrue(fixture.events.isEmpty()),
                () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO, EnrolmentStore.class),
                () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, EnrolmentStore.class));
    }

    /** The stopped writes of the pair, built by hand exactly as a stop leaves them. */
    @Nested
    @DisplayName("a stopped write of the pair is settled before the check")
    class StoppedWrite {

        private TransactionId transactionId;
        private Path otherStaged;

        @BeforeEach
        void stageForOther() throws IOException {
            transactionId = TransactionId.generate(Clock.systemUTC());
            otherStaged = writeStaged(staged(fixture.store.file(OTHER), transactionId), newFile);
        }

        private void assertSettled(Outcome outcome, byte[] expectedFile) throws IOException {
            assertAll(
                    () -> assertTrue(report(outcome).contains("a stopped write was settled (1)"), outcome::toString),
                    () -> assertArrayEquals(expectedFile, Files.readAllBytes(fixture.store.file(PROJECT))),
                    () -> assertArrayEquals(EnrolmentCodec.write(fixture.verify(PROJECT)), expectedFile),
                    () -> assertFalse(Files.exists(staged(fixture.store.file(PROJECT), transactionId))),
                    () -> assertFalse(Files.exists(staged(fixture.store.sidecar(PROJECT), transactionId))),
                    () -> assertTrue(Files.exists(otherStaged)));
        }

        @Test
        @DisplayName("only the file staged: discarded, the old enrolment is verified")
        void fileStagedOnly() throws IOException {
            writeStaged(staged(fixture.store.file(PROJECT), transactionId), newFile);

            assertSettled(step.run(PROJECT), oldFile);
            LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                    WARN.ENROLMENT_PAIR_DISCARDED.resolveIdentifierString());
        }

        @Test
        @DisplayName("both staged: completed, the new enrolment is in place and verified")
        void bothStaged() throws IOException {
            writeStaged(staged(fixture.store.file(PROJECT), transactionId), newFile);
            writeStaged(staged(fixture.store.sidecar(PROJECT), transactionId), newSidecar);

            assertSettled(step.run(PROJECT), newFile);
            LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                    INFO.ENROLMENT_PAIR_COMPLETED.resolveIdentifierString());
        }

        @Test
        @DisplayName("file replaced, old sidecar, staged sidecar: completed, the pair verifies")
        void fileReplaced() throws IOException {
            Files.write(fixture.store.file(PROJECT), newFile);
            writeStaged(staged(fixture.store.sidecar(PROJECT), transactionId), newSidecar);

            assertSettled(step.run(PROJECT), newFile);
            LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                    INFO.ENROLMENT_PAIR_COMPLETED.resolveIdentifierString());
        }

        @Test
        @DisplayName("a torn staged sidecar: discarded with its file, the old enrolment is verified")
        void tornSidecar() throws IOException {
            writeStaged(staged(fixture.store.file(PROJECT), transactionId), newFile);
            writeStaged(staged(fixture.store.sidecar(PROJECT), transactionId),
                    Arrays.copyOf(newSidecar, newSidecar.length / 2));

            assertSettled(step.run(PROJECT), oldFile);
            LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                    WARN.ENROLMENT_PAIR_DISCARDED.resolveIdentifierString());
        }

        @Test
        @DisplayName("without a staged name of the project nothing is settled and nothing is logged")
        void nothingStaged() {
            var outcome = step.run(PROJECT);

            assertAll(
                    () -> assertEquals(new Outcome.Done(EnrolmentIntegrityStep.VERIFIED), outcome),
                    () -> assertTrue(Files.exists(otherStaged)),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO, EnrolmentStore.class),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, EnrolmentStore.class));
        }
    }

    @Nested
    @DisplayName("a refusal fails the step with its message and keeps its log and audit record")
    class Refusals {

        @Test
        @DisplayName("a file edited without its sidecar is tampered")
        void tampered() throws IOException {
            Files.write(fixture.store.file(PROJECT), newFile);

            var reason = failure(step.run(PROJECT));

            assertAll(
                    () -> assertTrue(reason.contains("enrolment_tampered"), reason),
                    () -> assertTrue(reason.contains(fixture.store.file(PROJECT).toString()), reason),
                    () -> assertEquals(List.of(AuditEvent.ENROLMENT_TAMPERED),
                            fixture.events.stream().map(AuditEvent::event).toList()),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.ENROLMENT_TAMPERED.resolveIdentifierString()));
            assertOtherProjectStillDone();
        }

        @Test
        @DisplayName("a file with mode 0666 is insecure")
        void insecureMode() throws IOException {
            Files.setPosixFilePermissions(fixture.store.file(PROJECT), PosixFilePermissions.fromString("rw-rw-rw-"));

            var reason = failure(step.run(PROJECT));

            assertAll(
                    () -> assertTrue(reason.contains("enrolment_insecure"), reason),
                    () -> assertEquals(List.of(AuditEvent.ENROLMENT_INSECURE),
                            fixture.events.stream().map(AuditEvent::event).toList()),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.ENROLMENT_INSECURE.resolveIdentifierString()));
            assertOtherProjectStillDone();
        }

        @Test
        @DisplayName("a file that is a symbolic link is insecure")
        void symbolicLink() throws IOException {
            var target = Files.move(fixture.store.file(PROJECT), base.resolve("elsewhere.json"));
            Files.createSymbolicLink(fixture.store.file(PROJECT), target);

            var reason = failure(step.run(PROJECT));

            assertTrue(reason.contains("enrolment_insecure"), reason);
            assertOtherProjectStillDone();
        }

        @Test
        @DisplayName("an unreadable file with a matching sidecar and HMAC is unreadable, audited as enrolment_invalid")
        void unreadableFile() throws IOException {
            var content = "{\"format_version\":1}".getBytes(StandardCharsets.UTF_8);
            Files.write(fixture.store.file(PROJECT), content);
            Files.write(fixture.store.sidecar(PROJECT), fixture.signedSidecar(content));

            var expected = AuditEvent.enrolmentInvalid(PROJECT, "unreadable in " + PROJECT + ".json");

            var reason = failure(step.run(PROJECT));

            assertAll(
                    () -> assertTrue(reason.contains("unreadable"), reason),
                    () -> assertEquals(List.of(expected), fixture.events),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.ENROLMENT_INVALID.resolveIdentifierString()));
            assertOtherProjectStillDone();
        }

        @Test
        @DisplayName("a project that vanished from the store is absent")
        void vanished() throws IOException {
            Files.delete(fixture.store.file(PROJECT));
            Files.delete(fixture.store.sidecar(PROJECT));

            var reason = failure(step.run(PROJECT));

            assertAll(
                    () -> assertTrue(reason.contains("absent"), reason),
                    () -> assertTrue(fixture.events.isEmpty()));
            assertOtherProjectStillDone();
        }

        @Test
        @DisplayName("a missing key is tampered")
        void missingKey() throws IOException {
            Files.delete(fixture.key.keyFile());

            var reason = failure(step.run(PROJECT));

            assertTrue(reason.contains("enrolment_tampered"), reason);
        }

        @Test
        @DisplayName("an unreadable key fails with its reason code and never with the key")
        void unreadableKey() throws IOException {
            var keyText = Files.readString(fixture.key.keyFile()).strip();
            var failingKey = new EnrolmentKey(new LockedKeyring(), base.resolve("state"), EnrolmentFixture.USER,
                    new SecureRandom());
            var failingStep = new EnrolmentIntegrityStep(fixture.store,
                    new EnrolmentCheck(fixture.store, failingKey, new EnrolmentFileCheck(EnrolmentFixture.USER),
                            fixture.sink), fixture.locks);

            var reason = failure(failingStep.run(PROJECT));

            assertAll(
                    () -> assertEquals("integrity key unavailable: "
                            + SecretStoreException.Reason.LOCKED.code(), reason),
                    () -> assertFalse(reason.contains(keyText)),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.ERROR,
                            ERROR.ENROLMENT_KEY_UNAVAILABLE.resolveIdentifierString()),
                    () -> assertTrue(TestLoggerFactory.getTestHandler()
                            .resolveLogMessagesContaining(TestLogLevel.ERROR, keyText).isEmpty()));
            assertOtherProjectStillDone();
        }
    }

    @Test
    @DisplayName("an invalid project id is not caught by the step; the recovery records it as a failed step")
    void invalidProjectId() {
        var invalid = "../projects";

        assertThrows(IllegalArgumentException.class, () -> step.run(invalid));
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
