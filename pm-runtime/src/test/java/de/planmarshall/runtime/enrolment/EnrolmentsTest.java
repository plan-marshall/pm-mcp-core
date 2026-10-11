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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;


import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.planmarshall.core.log.PmMcpLogMessages.INFO;
import de.planmarshall.core.log.PmMcpLogMessages.WARN;
import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.service.LiveWork.Item;
import de.planmarshall.core.service.LiveWork.Kind;
import de.planmarshall.core.store.EnrolmentCodec;
import de.planmarshall.core.store.EnrolmentStore;
import de.planmarshall.core.store.ProjectEnrolment;
import de.planmarshall.core.store.ProjectEnrolment.Build;
import de.planmarshall.core.store.StoreDigest;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.StoreRefusal.Outcome;
import de.planmarshall.runtime.credentials.FileSecretStore;
import de.planmarshall.runtime.credentials.SecretStoreException;
import de.planmarshall.runtime.credentials.SecretStoreException.Reason;
import de.planmarshall.runtime.enrolment.Enrolments.ScopeActiveException;
import de.planmarshall.runtime.lock.FileLockManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The operator writes of the enrolment store (PM-EXEC-2, PM-MIG-1, PM-IMPL-7): {@code enrol} writes a file and a
 * sidecar record the check accepts, {@code unenrol} is refused while the project has live work and otherwise removes
 * only the file and its sidecar, and a re-baseline makes a reviewed file pass again.
 */
@EnableTestLogger
@DisplayName("Enrolments")
class EnrolmentsTest {

    private static final String PROJECT = "pm-mcp-core";
    private static final String USER = System.getProperty("user.name");

    @TempDir
    Path base;

    private final List<AuditEvent> events = new ArrayList<>();
    private final Map<String, List<Item>> liveWork = new HashMap<>();
    private Path state;
    private AuditSink sink;
    private EnrolmentStore store;
    private EnrolmentKey key;
    private FileLockManager locks;
    private EnrolmentCheck check;
    private Enrolments enrolments;

    @BeforeEach
    void create() throws IOException {
        Files.createDirectories(base.resolve(EnrolmentStore.DIRECTORY_NAME));
        state = Files.createDirectories(base.resolve("state"));
        store = new EnrolmentStore(base);
        key = new EnrolmentKey(new FileSecretStore(base.resolve("credentials")), state, USER, new SecureRandom());
        sink = events::add;
        locks = new FileLockManager(sink);
        var fileCheck = new EnrolmentFileCheck(USER);
        check = new EnrolmentCheck(store, key, fileCheck, sink);
        enrolments = enrolmentsWith(key, sink);
    }

    private Enrolments enrolmentsWith(EnrolmentKey integrityKey, AuditSink auditSink) {
        return new Enrolments(store, integrityKey, new EnrolmentFileCheck(USER), locks,
                projectId -> liveWork.getOrDefault(projectId, List.of()), auditSink);
    }

    private static ProjectEnrolment enrolment(boolean pilot, Instant releasedAt) {
        return enrolment(PROJECT, pilot, releasedAt);
    }

    private static ProjectEnrolment enrolment(String projectId, boolean pilot, Instant releasedAt) {
        return new ProjectEnrolment(projectId, "/home/dev/git/" + projectId, Instant.parse("2026-10-11T08:00:00Z"),
                pilot, null, releasedAt, List.of(), List.of(), Build.defaults(), Map.of(), new TreeMap<>(),
                new TreeMap<>(), new TreeMap<>(), List.of(), new TreeMap<>());
    }

    private ProjectEnrolment verify() {
        try (var transaction = locks.openTransaction()) {
            return check.verify(transaction, PROJECT);
        }
    }

    @Nested
    @DisplayName("enrol")
    class Enrol {

        @Test
        @DisplayName("writes the file and a sidecar record that pass the check, creating the key")
        void writesCheckedPair() {
            var written = enrolments.enrol(enrolment(false, null), false);

            assertAll(
                    () -> assertEquals(written, verify()),
                    () -> assertArrayEquals(EnrolmentCodec.write(written), Files.readAllBytes(store.file(PROJECT))),
                    () -> assertTrue(Files.exists(key.keyFile())),
                    () -> assertEquals(List.of(AuditEvent.enrolmentWritten(PROJECT)), events),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                            INFO.ENROLMENT_WRITTEN.resolveIdentifierString()));
        }

        @Test
        @DisplayName("sets pilot from --pilot alone and clears a release")
        void pilotAndRelease() {
            var written = enrolments.enrol(enrolment(false, Instant.parse("2026-10-11T09:00:00Z")), true);

            assertAll(
                    () -> assertTrue(written.pilot()),
                    () -> assertNull(written.releasedAt()),
                    () -> assertTrue(verify().pilot()));
        }

        @Test
        @DisplayName("enrolling again without --pilot clears pilot and keeps the pair valid")
        void reEnrol() {
            enrolments.enrol(enrolment(false, null), true);

            enrolments.enrol(enrolment(true, null), false);

            assertFalse(verify().pilot());
        }
    }

    @Nested
    @DisplayName("enrol never creates an integrity key while enrolment files exist")
    class KeySourceSwitch {

        private static final String OTHER = "other-project";
        private static final String THIRD = "third-project";

        private InMemoryKeyring keyring;
        private EnrolmentKey keyringKey;
        private Enrolments keyringEnrolments;

        @BeforeEach
        void keyring() {
            keyring = new InMemoryKeyring();
            keyringKey = new EnrolmentKey(keyring, state, USER, new SecureRandom());
            keyringEnrolments = enrolmentsWith(keyringKey, sink);
        }

        private ProjectEnrolment verifyWith(EnrolmentKey integrityKey, String projectId) {
            try (var transaction = locks.openTransaction()) {
                return new EnrolmentCheck(store, integrityKey, new EnrolmentFileCheck(USER), sink)
                        .verify(transaction, projectId);
            }
        }

        private void assertRefusedForKeySwitch(StoreRefusal refusal, String firstProject) {
            assertAll(
                    () -> assertEquals(Outcome.ENROLMENT_TAMPERED, refusal.outcome()),
                    () -> assertEquals(store.file(firstProject), refusal.file()),
                    () -> assertTrue(refusal.getMessage().contains(EnrolmentCheck.KEY_ABSENT), refusal.getMessage()),
                    () -> assertTrue(refusal.getMessage().contains("pm-operator accept-edit enrolment " + firstProject),
                            refusal.getMessage()),
                    () -> assertEquals(List.of(AuditEvent.enrolmentTampered(firstProject, EnrolmentCheck.KEY_ABSENT)),
                            events),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.ENROLMENT_TAMPERED.resolveIdentifierString()));
        }

        @Test
        @DisplayName("on a fresh base without key and enrolment file, the first enrol creates the key in the keyring")
        void freshBaseCreatesKey() {
            var written = keyringEnrolments.enrol(enrolment(false, null), false);

            assertAll(
                    () -> assertEquals(written, verifyWith(keyringKey, PROJECT)),
                    () -> assertTrue(keyring.entries.containsKey(EnrolmentKey.ACCOUNT)),
                    () -> assertFalse(Files.exists(keyringKey.keyFile())));
        }

        @Test
        @DisplayName("from the key file to the keyring: refused, no key created, nothing written")
        void fileToKeyring() throws Exception {
            enrolments.enrol(enrolment(false, null), false);
            var file = Files.readAllBytes(store.file(PROJECT));
            var sidecar = Files.readAllBytes(store.sidecar(PROJECT));
            events.clear();

            var refusal = assertThrows(StoreRefusal.class,
                    () -> keyringEnrolments.enrol(enrolment(OTHER, false, null), false));

            assertRefusedForKeySwitch(refusal, PROJECT);
            assertAll(
                    () -> assertTrue(keyring.entries.isEmpty()),
                    () -> assertFalse(Files.exists(store.file(OTHER))),
                    () -> assertFalse(Files.exists(store.sidecar(OTHER))),
                    () -> assertArrayEquals(file, Files.readAllBytes(store.file(PROJECT))),
                    () -> assertArrayEquals(sidecar, Files.readAllBytes(store.sidecar(PROJECT))));
        }

        @Test
        @DisplayName("from the keyring to the key file: refused, no key file created, nothing written")
        void keyringToFile() {
            keyringEnrolments.enrol(enrolment(false, null), false);
            events.clear();

            var refusal = assertThrows(StoreRefusal.class,
                    () -> enrolments.enrol(enrolment(OTHER, false, null), false));

            assertRefusedForKeySwitch(refusal, PROJECT);
            assertAll(
                    () -> assertFalse(Files.exists(key.keyFile())),
                    () -> assertFalse(Files.exists(store.file(OTHER))),
                    () -> assertFalse(Files.exists(store.sidecar(OTHER))));
        }

        @Test
        @DisplayName("a failing audit sink does not lift the refusal, and the refusal carries the failure")
        void failingSink() {
            enrolments.enrol(enrolment(false, null), false);
            var failure = new UncheckedIOException(new IOException("audit log full"));
            var failing = enrolmentsWith(keyringKey, event -> {
                throw failure;
            });

            var refusal = assertThrows(StoreRefusal.class, () -> failing.enrol(enrolment(OTHER, false, null), false));

            assertAll(
                    () -> assertEquals(Outcome.ENROLMENT_TAMPERED, refusal.outcome()),
                    () -> assertEquals(List.of(failure), List.of(refusal.getSuppressed())),
                    () -> assertTrue(keyring.entries.isEmpty()),
                    () -> assertFalse(Files.exists(store.file(OTHER))));
        }

        @Test
        @DisplayName("re-enrolling the only enrolled project after its key vanished is refused the same way")
        void reEnrolAfterKeyVanished() throws Exception {
            enrolments.enrol(enrolment(false, null), false);
            var file = Files.readAllBytes(store.file(PROJECT));
            Files.delete(key.keyFile());
            events.clear();

            var refusal = assertThrows(StoreRefusal.class, () -> enrolments.enrol(enrolment(false, null), true));

            assertRefusedForKeySwitch(refusal, PROJECT);
            assertAll(
                    () -> assertFalse(Files.exists(key.keyFile())),
                    () -> assertArrayEquals(file, Files.readAllBytes(store.file(PROJECT))));
        }

        @Test
        @DisplayName("the re-baseline creates the new key; the other enrolments then fail by HMAC, one by one")
        void rebaselineIsTheWayOut() {
            enrolments.enrol(enrolment(false, null), false);
            enrolments.enrol(enrolment(THIRD, false, null), false);
            assertThrows(StoreRefusal.class, () -> keyringEnrolments.enrol(enrolment(OTHER, false, null), false));
            events.clear();

            keyringEnrolments.rebaseline(PROJECT);
            var other = keyringEnrolments.enrol(enrolment(OTHER, false, null), false);
            events.clear();

            var third = assertThrows(StoreRefusal.class, () -> verifyWith(keyringKey, THIRD));
            assertAll(
                    () -> assertTrue(keyring.entries.containsKey(EnrolmentKey.ACCOUNT)),
                    () -> assertEquals(PROJECT, verifyWith(keyringKey, PROJECT).projectId()),
                    () -> assertEquals(other, verifyWith(keyringKey, OTHER)),
                    () -> assertEquals(Outcome.ENROLMENT_TAMPERED, third.outcome()),
                    () -> assertTrue(third.getMessage().contains(EnrolmentCheck.HMAC_MISMATCH), third.getMessage()),
                    () -> assertTrue(third.getMessage().contains("pm-operator accept-edit enrolment " + THIRD),
                            third.getMessage()));
        }

        @Test
        @DisplayName("a key file with a mode broader than 0600 still fails the enrol as insecure and writes nothing")
        void insecureKey() throws Exception {
            enrolments.enrol(enrolment(false, null), false);
            Files.setPosixFilePermissions(key.keyFile(), PosixFilePermissions.fromString("rw-r--r--"));

            var thrown = assertThrows(SecretStoreException.class,
                    () -> enrolments.enrol(enrolment(OTHER, false, null), false));

            assertAll(
                    () -> assertEquals(Reason.INSECURE, thrown.reason()),
                    () -> assertFalse(Files.exists(store.file(OTHER))),
                    () -> assertFalse(Files.exists(store.sidecar(OTHER))));
        }

        @Test
        @DisplayName("a malformed key file still fails the enrol as invalid and writes nothing")
        void malformedKey() throws Exception {
            enrolments.enrol(enrolment(false, null), false);
            Files.writeString(key.keyFile(), "not base64!");

            var thrown = assertThrows(SecretStoreException.class,
                    () -> enrolments.enrol(enrolment(OTHER, false, null), false));

            assertAll(
                    () -> assertEquals(Reason.INVALID, thrown.reason()),
                    () -> assertFalse(Files.exists(store.file(OTHER))),
                    () -> assertFalse(Files.exists(store.sidecar(OTHER))));
        }
    }

    @Nested
    @DisplayName("unenrol")
    class Unenrol {

        @Test
        @DisplayName("is refused with scope_active while the project has a live plan, epic and job, naming each")
        void refusedForLiveWork() {
            enrolments.enrol(enrolment(false, null), false);
            events.clear();
            var items = List.of(new Item(Kind.PLAN, "add-login"), new Item(Kind.EPIC, "auth-rework"),
                    new Item(Kind.JOB, "job-1"));
            liveWork.put(PROJECT, items);

            var refusal = assertThrows(ScopeActiveException.class, () -> enrolments.unenrol(PROJECT));

            assertAll(
                    () -> assertEquals(PROJECT, refusal.projectId()),
                    () -> assertEquals(items, refusal.items()),
                    () -> assertTrue(refusal.getMessage().contains(ScopeActiveException.CODE)),
                    () -> assertTrue(refusal.getMessage().contains("plan add-login, epic auth-rework, job job-1"),
                            refusal.getMessage()),
                    () -> assertTrue(Files.exists(store.file(PROJECT))),
                    () -> assertTrue(Files.exists(store.sidecar(PROJECT))),
                    () -> assertEquals(List.of(AuditEvent.unenrolRefused(PROJECT)), events),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.UNENROL_REFUSED.resolveIdentifierString()));
        }

        @Test
        @DisplayName("without live work removes file and sidecar and leaves the project store byte-identical")
        void removesOnlyThePair() throws Exception {
            enrolments.enrol(enrolment(false, null), false);
            events.clear();
            var projectStore = Files.createDirectories(base.resolve("projects").resolve(PROJECT));
            var storeFile = Files.writeString(projectStore.resolve("store.json"), "{\"project_root\":\"/r\"}");
            var before = Files.readAllBytes(storeFile);

            var removed = enrolments.unenrol(PROJECT);

            assertAll(
                    () -> assertTrue(removed),
                    () -> assertFalse(Files.exists(store.file(PROJECT))),
                    () -> assertFalse(Files.exists(store.sidecar(PROJECT))),
                    () -> assertArrayEquals(before, Files.readAllBytes(storeFile)),
                    () -> assertEquals(List.of(storeFile), listing(projectStore)),
                    () -> assertEquals(List.of(AuditEvent.enrolmentRemoved(PROJECT)), events),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                            INFO.ENROLMENT_REMOVED.resolveIdentifierString()));
        }

        @Test
        @DisplayName("of a project that is not enrolled removes nothing and records nothing")
        void notEnrolled() {
            assertAll(
                    () -> assertFalse(enrolments.unenrol(PROJECT)),
                    () -> assertTrue(events.isEmpty()));
        }

        private List<Path> listing(Path directory) throws IOException {
            try (var entries = Files.list(directory)) {
                return entries.toList();
            }
        }
    }

    @Nested
    @DisplayName("re-baseline")
    class Rebaseline {

        @Test
        @DisplayName("makes a reviewed edit pass and reports the recorded and current size and SHA-256")
        void reviewedEdit() throws Exception {
            var written = enrolments.enrol(enrolment(false, null), false);
            var recorded = StoreDigest.of(EnrolmentCodec.write(written));
            var edited = EnrolmentCodec.write(enrolment(true, null));
            Files.write(store.file(PROJECT), edited);
            assertThrows(StoreRefusal.class, EnrolmentsTest.this::verify);
            events.clear();

            var outcome = enrolments.rebaseline(PROJECT);

            assertAll(
                    () -> assertEquals(store.file(PROJECT), outcome.file()),
                    () -> assertEquals(Optional.of(recorded), outcome.recorded()),
                    () -> assertEquals(StoreDigest.of(edited), outcome.current()),
                    () -> assertTrue(verify().pilot()),
                    () -> assertEquals(List.of(AuditEvent.enrolmentRebaselined(PROJECT)), events),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                            INFO.ENROLMENT_REBASELINED.resolveIdentifierString()));
        }

        @Test
        @DisplayName("of a file without a readable sidecar reports no recorded values")
        void unreadableSidecar() throws Exception {
            enrolments.enrol(enrolment(false, null), false);
            Files.writeString(store.sidecar(PROJECT), "garbage");

            var outcome = enrolments.rebaseline(PROJECT);

            assertAll(
                    () -> assertTrue(outcome.recorded().isEmpty()),
                    () -> assertFalse(verify().pilot()));
        }

        @Test
        @DisplayName("of a project without an enrolment file is refused as absent and writes nothing")
        void absent() {
            var refusal = assertThrows(StoreRefusal.class, () -> enrolments.rebaseline(PROJECT));

            assertAll(
                    () -> assertEquals(Outcome.ABSENT, refusal.outcome()),
                    () -> assertFalse(Files.exists(store.sidecar(PROJECT))),
                    () -> assertTrue(events.isEmpty()));
        }

        @Test
        @DisplayName("of an insecure file is refused as enrolment_insecure and leaves the sidecar unchanged")
        void insecure() throws Exception {
            enrolments.enrol(enrolment(false, null), false);
            var sidecar = Files.readAllBytes(store.sidecar(PROJECT));
            Files.setPosixFilePermissions(store.file(PROJECT), PosixFilePermissions.fromString("rw-rw-rw-"));

            var refusal = assertThrows(StoreRefusal.class, () -> enrolments.rebaseline(PROJECT));

            assertAll(
                    () -> assertEquals(Outcome.ENROLMENT_INSECURE, refusal.outcome()),
                    () -> assertArrayEquals(sidecar, Files.readAllBytes(store.sidecar(PROJECT))));
        }

        @Test
        @DisplayName("of a file that does not read as an enrolment is refused and leaves the sidecar unchanged")
        void unreadableFile() throws Exception {
            enrolments.enrol(enrolment(false, null), false);
            var sidecar = Files.readAllBytes(store.sidecar(PROJECT));
            Files.writeString(store.file(PROJECT), "{\"format_version\":1}");

            var refusal = assertThrows(StoreRefusal.class, () -> enrolments.rebaseline(PROJECT));

            assertAll(
                    () -> assertEquals(Outcome.UNREADABLE, refusal.outcome()),
                    () -> assertArrayEquals(sidecar, Files.readAllBytes(store.sidecar(PROJECT))));
        }
    }

    @Test
    @DisplayName("a re-baseline outcome refuses missing components")
    void rebaselineComponents() {
        var digest = StoreDigest.of(new byte[0]);
        var file = base.resolve("f");

        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> new Enrolments.Rebaseline(null, Optional.empty(), digest)),
                () -> assertThrows(NullPointerException.class, () -> new Enrolments.Rebaseline(file, null, digest)),
                () -> assertThrows(NullPointerException.class,
                        () -> new Enrolments.Rebaseline(file, Optional.empty(), null)));
    }
}
