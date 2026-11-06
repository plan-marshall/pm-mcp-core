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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.TestLoggerFactory;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.log.CoreLogMessages;
import de.planmarshall.core.log.CoreLogMessages.INFO;
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
import de.planmarshall.runtime.log.RuntimeLogMessages.ERROR;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;
import de.planmarshall.runtime.start.RecoveryStep.Name;
import de.planmarshall.runtime.start.RecoveryStep.Outcome;

/**
 * Step 1 of the per-project recovery (PM-SEC-5): a stopped write of the enrolment pair is settled first, then the
 * enrolment check runs; every refusal fails the step of its project only, with the message of the refusal.
 */
@EnableGeneratorController
@EnableTestLogger
@DisplayName("Enrolment integrity step")
class EnrolmentIntegrityStepTest {

    /** Contract literal: the owner-only mode a write stages its files with. */
    private static final String OWNER_ONLY = "rw-------";

    @TempDir
    Path base;

    private final String project = "project-" + name();
    private final String other = "other-" + name();
    private EnrolmentFixture fixture;
    private EnrolmentIntegrityStep step;
    private byte[] oldFile;
    private byte[] oldSidecar;
    private byte[] newFile;
    private byte[] newSidecar;

    private static String name() {
        return Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }

    /** Enrols both projects; keeps the bytes of the old and of a new pair of the project, the old one in place. */
    @BeforeEach
    void enrol() throws IOException {
        fixture = new EnrolmentFixture(base);
        step = new EnrolmentIntegrityStep(fixture.store, fixture.check, fixture.locks);
        fixture.enrol(project, true);
        newFile = Files.readAllBytes(fixture.store.file(project));
        newSidecar = Files.readAllBytes(fixture.store.sidecar(project));
        fixture.enrol(project, false);
        oldFile = Files.readAllBytes(fixture.store.file(project));
        oldSidecar = Files.readAllBytes(fixture.store.sidecar(project));
        fixture.enrol(other, false);
        fixture.events.clear();
    }

    private Path staged(Path target, TransactionId transactionId) {
        return EnrolmentFixture.staged(target, transactionId);
    }

    /** Writes a staged file as a write stages it: readable and writable by its owner only. */
    private static Path writeStaged(Path file, byte[] content) throws IOException {
        Files.write(file, content);
        return Files.setPosixFilePermissions(file, PosixFilePermissions.fromString(OWNER_ONLY));
    }

    private String report(Outcome outcome) {
        return assertInstanceOf(Outcome.Done.class, outcome, "the step must be done").report();
    }

    private String failure(Outcome outcome) {
        return assertInstanceOf(Outcome.Failed.class, outcome, "the step must fail with a reason").reason();
    }

    private void assertOtherProjectStillDone() {
        assertTrue(report(step.run(other)).contains(EnrolmentIntegrityStep.VERIFIED),
                "the other project must still be verified");
    }

    @Test
    @DisplayName("a good enrolment is verified, and the step writes nothing and creates no key")
    void verified() throws Exception {
        var names = fixture.names();
        var keyBytes = Files.readAllBytes(fixture.key.keyFile());

        var outcome = step.run(project);

        assertAll(
                () -> assertEquals(Name.ENROLMENT_INTEGRITY, step.name(), "the step must carry its specified name"),
                () -> assertEquals(new Outcome.Done(EnrolmentIntegrityStep.VERIFIED), outcome,
                        "a good enrolment must be verified"),
                () -> assertEquals(names, fixture.names(), "the step must create no file"),
                () -> assertArrayEquals(oldFile, Files.readAllBytes(fixture.store.file(project)),
                        "the enrolment file must stay unchanged"),
                () -> assertArrayEquals(oldSidecar, Files.readAllBytes(fixture.store.sidecar(project)),
                        "the sidecar must stay unchanged"),
                () -> assertArrayEquals(keyBytes, Files.readAllBytes(fixture.key.keyFile()),
                        "the key file must stay unchanged"),
                () -> assertTrue(fixture.events.isEmpty(), "a good enrolment is not audited"),
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
            var instant = Instant.ofEpochSecond(Generators.longs(1_700_000_000L, 2_000_000_000L).next());
            transactionId = TransactionId.generate(Clock.fixed(instant, ZoneOffset.UTC));
            otherStaged = writeStaged(staged(fixture.store.file(other), transactionId), newFile);
        }

        private void assertSettled(Outcome outcome, byte[] expectedFile) {
            assertAll(
                    // Contract literal: the specified report text of a settled write
                    () -> assertTrue(report(outcome).contains("a stopped write was settled (1)"),
                            "the report must name the settled write"),
                    () -> assertArrayEquals(expectedFile, Files.readAllBytes(fixture.store.file(project)),
                            "the enrolment file must be the settled one"),
                    () -> assertArrayEquals(EnrolmentCodec.write(fixture.verify(project)), expectedFile,
                            "the settled enrolment must verify"),
                    () -> assertFalse(Files.exists(staged(fixture.store.file(project), transactionId)),
                            "the staged file must be gone"),
                    () -> assertFalse(Files.exists(staged(fixture.store.sidecar(project), transactionId)),
                            "the staged sidecar must be gone"),
                    () -> assertTrue(Files.exists(otherStaged), "a file of another project must stay"));
        }

        @Test
        @DisplayName("only the file staged: discarded, the old enrolment is verified")
        void fileStagedOnly() throws Exception {
            writeStaged(staged(fixture.store.file(project), transactionId), newFile);

            assertSettled(step.run(project), oldFile);
            LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                    CoreLogMessages.WARN.ENROLMENT_PAIR_DISCARDED.resolveIdentifierString());
        }

        @Test
        @DisplayName("both staged: completed, the new enrolment is in place and verified")
        void bothStaged() throws Exception {
            writeStaged(staged(fixture.store.file(project), transactionId), newFile);
            writeStaged(staged(fixture.store.sidecar(project), transactionId), newSidecar);

            assertSettled(step.run(project), newFile);
            LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                    INFO.ENROLMENT_PAIR_COMPLETED.resolveIdentifierString());
        }

        @Test
        @DisplayName("file replaced, old sidecar, staged sidecar: completed, the pair verifies")
        void fileReplaced() throws Exception {
            Files.write(fixture.store.file(project), newFile);
            writeStaged(staged(fixture.store.sidecar(project), transactionId), newSidecar);

            assertSettled(step.run(project), newFile);
            LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                    INFO.ENROLMENT_PAIR_COMPLETED.resolveIdentifierString());
        }

        @Test
        @DisplayName("a torn staged sidecar: discarded with its file, the old enrolment is verified")
        void tornSidecar() throws Exception {
            writeStaged(staged(fixture.store.file(project), transactionId), newFile);
            writeStaged(staged(fixture.store.sidecar(project), transactionId),
                    Arrays.copyOf(newSidecar, newSidecar.length / 2));

            assertSettled(step.run(project), oldFile);
            LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                    CoreLogMessages.WARN.ENROLMENT_PAIR_DISCARDED.resolveIdentifierString());
        }

        @Test
        @DisplayName("without a staged name of the project nothing is settled and nothing is logged")
        void nothingStaged() {
            var outcome = step.run(project);

            assertAll(
                    () -> assertEquals(new Outcome.Done(EnrolmentIntegrityStep.VERIFIED), outcome,
                            "the enrolment must be verified unchanged"),
                    () -> assertTrue(Files.exists(otherStaged), "a file of another project must stay"),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO, EnrolmentStore.class),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, EnrolmentStore.class));
        }
    }

    @Nested
    @DisplayName("a refusal fails the step with its message and keeps its log and audit record "
            + "(the reason codes are the specified wire names)")
    class Refusals {

        @Test
        @DisplayName("a file edited without its sidecar is tampered")
        void tampered() throws Exception {
            Files.write(fixture.store.file(project), newFile);

            var reason = failure(step.run(project));

            assertAll(
                    () -> assertTrue(reason.contains("enrolment_tampered"), "the reason must name the tampering"),
                    () -> assertTrue(reason.contains(fixture.store.file(project).toString()),
                            "the reason must name the enrolment file"),
                    () -> assertEquals(List.of(AuditEvent.ENROLMENT_TAMPERED),
                            fixture.events.stream().map(AuditEvent::event).toList(),
                            "the tampering must be audited once"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.ENROLMENT_TAMPERED.resolveIdentifierString()));
            assertOtherProjectStillDone();
        }

        @Test
        @DisplayName("a file with mode 0666 is insecure")
        void insecureMode() throws Exception {
            // Contract literal: the mode 0666 the specification names as insecure
            Files.setPosixFilePermissions(fixture.store.file(project), PosixFilePermissions.fromString("rw-rw-rw-"));

            var reason = failure(step.run(project));

            assertAll(
                    () -> assertTrue(reason.contains("enrolment_insecure"), "the reason must name the insecure mode"),
                    () -> assertEquals(List.of(AuditEvent.ENROLMENT_INSECURE),
                            fixture.events.stream().map(AuditEvent::event).toList(),
                            "the insecure file must be audited once"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.ENROLMENT_INSECURE.resolveIdentifierString()));
            assertOtherProjectStillDone();
        }

        @Test
        @DisplayName("a file that is a symbolic link is insecure")
        void symbolicLink() throws Exception {
            var target = Files.move(fixture.store.file(project), base.resolve(name() + ".json"));
            Files.createSymbolicLink(fixture.store.file(project), target);

            var reason = failure(step.run(project));

            assertTrue(reason.contains("enrolment_insecure"), "a symbolic link must be refused as insecure");
            assertOtherProjectStillDone();
        }

        @Test
        @DisplayName("an unreadable file with a matching sidecar and HMAC is unreadable, audited as enrolment_invalid")
        void unreadableFile() throws Exception {
            // Contract literal: a file of the schema that lacks every required field but the format version
            var content = "{\"format_version\":1}".getBytes(StandardCharsets.UTF_8);
            Files.write(fixture.store.file(project), content);
            Files.write(fixture.store.sidecar(project), fixture.signedSidecar(content));

            var expected = AuditEvent.enrolmentInvalid(project, "unreadable in " + project + ".json");

            var reason = failure(step.run(project));

            assertAll(
                    () -> assertTrue(reason.contains("unreadable"), "the reason must name the unreadable file"),
                    () -> assertEquals(List.of(expected), fixture.events, "the file must be audited as invalid"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.ENROLMENT_INVALID.resolveIdentifierString()));
            assertOtherProjectStillDone();
        }

        @Test
        @DisplayName("a project that vanished from the store is absent")
        void vanished() throws Exception {
            Files.delete(fixture.store.file(project));
            Files.delete(fixture.store.sidecar(project));

            var reason = failure(step.run(project));

            assertAll(
                    () -> assertTrue(reason.contains("absent"), "the reason must name the absent project"),
                    () -> assertTrue(fixture.events.isEmpty(), "an absent project is not audited"));
            assertOtherProjectStillDone();
        }

        @Test
        @DisplayName("a missing key is tampered")
        void missingKey() throws Exception {
            Files.delete(fixture.key.keyFile());

            var reason = failure(step.run(project));

            assertTrue(reason.contains("enrolment_tampered"), "a missing key must be refused as tampered");
        }

        @Test
        @DisplayName("an unreadable key fails with its reason code and never with the key")
        void unreadableKey() throws Exception {
            var keyText = Files.readString(fixture.key.keyFile()).strip();
            // The SecureRandom is the entropy source the key needs to create a key, not test data
            var failingKey = new EnrolmentKey(new LockedKeyring(), base.resolve("state"), EnrolmentFixture.USER,
                    new SecureRandom());
            var failingStep = new EnrolmentIntegrityStep(fixture.store,
                    new EnrolmentCheck(fixture.store, failingKey, new EnrolmentFileCheck(EnrolmentFixture.USER),
                            fixture.sink), fixture.locks);

            var reason = failure(failingStep.run(project));

            assertAll(
                    () -> assertEquals("integrity key unavailable: "
                            + SecretStoreException.Reason.LOCKED.code(), reason,
                            "the reason must carry the code of the store"),
                    () -> assertFalse(reason.contains(keyText), "the reason must never carry the key"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.ERROR,
                            ERROR.ENROLMENT_KEY_UNAVAILABLE.resolveIdentifierString()),
                    () -> assertTrue(TestLoggerFactory.getTestHandler()
                                    .resolveLogMessagesContaining(TestLogLevel.ERROR, keyText).isEmpty(),
                            "the log must never carry the key"));
            assertOtherProjectStillDone();
        }
    }

    @Test
    @DisplayName("an invalid project id is not caught by the step; the recovery records it as a failed step")
    void invalidProjectId() {
        var invalid = "../" + name();

        assertThrows(IllegalArgumentException.class, () -> step.run(invalid),
                "an id that leaves the store must be refused");
    }

    /** A keyring whose every read fails as locked. */
    private static final class LockedKeyring implements SecretStore {

        /** Contract literal: the error text of the simulated locked keyring. */
        private static final String LOCKED = "keyring locked";

        @Override
        public String name() {
            return SecretStore.KEYCHAIN;
        }

        @Override
        public void put(CredentialAccount account, String secret) {
            throw new SecretStoreException(SecretStoreException.Reason.LOCKED, LOCKED);
        }

        @Override
        public Optional<String> get(CredentialAccount account) {
            throw new SecretStoreException(SecretStoreException.Reason.LOCKED, LOCKED);
        }

        @Override
        public boolean delete(CredentialAccount account) {
            return false;
        }

        @Override
        public List<CredentialAccount> entries() {
            throw new SecretStoreException(SecretStoreException.Reason.LOCKED, LOCKED);
        }
    }
}
