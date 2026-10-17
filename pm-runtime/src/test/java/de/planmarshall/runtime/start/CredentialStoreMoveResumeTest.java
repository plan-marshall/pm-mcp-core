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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.store.EnrolmentSidecar;
import de.planmarshall.core.store.EnrolmentStore;
import de.planmarshall.runtime.audit.AuditHead;
import de.planmarshall.runtime.credentials.CredentialAccount;
import de.planmarshall.runtime.credentials.CredentialStoreChoice;
import de.planmarshall.runtime.credentials.CredentialStoreChoice.Store;
import de.planmarshall.runtime.credentials.CredentialStoreChoice.Writer;
import de.planmarshall.runtime.credentials.CredentialStoreChoiceFixture;
import de.planmarshall.runtime.credentials.CredentialStoreMove;
import de.planmarshall.runtime.credentials.CredentialStoreMoveRecord;
import de.planmarshall.runtime.credentials.CredentialStoreMoveRecord.Phase;
import de.planmarshall.runtime.credentials.FileSecretStore;
import de.planmarshall.runtime.credentials.SecretStore;
import de.planmarshall.runtime.credentials.SecretStoreException;
import de.planmarshall.runtime.enrolment.EnrolmentHmac;
import de.planmarshall.runtime.enrolment.EnrolmentKey;
import de.planmarshall.runtime.enrolment.HarnessesSidecarCheck;
import de.planmarshall.runtime.lock.FileLockManager;
import de.planmarshall.runtime.log.RuntimeLogMessages.INFO;

/**
 * The resume of a move between the credential stores cut by a stop (PM-CRED-2): the machine state a stop leaves
 * before and after the switch is written as the test's arrangement, against a fake OS store, so the test runs on
 * every OS. After the resume the target is recorded, holds every entry and the integrity key, and the old store and
 * the record are gone.
 */
@EnableTestLogger
@DisplayName("CredentialStoreMoveResume: a move cut by a stop is completed after the next start")
class CredentialStoreMoveResumeTest {

    private static final String SERVICE = "de.planmarshall.mcp";
    private static final String USER = System.getProperty("user.name");
    private static final String OWNER_ONLY = "rw-------";
    private static final CredentialAccount GITHUB = CredentialAccount.global("github");
    private static final CredentialAccount SONAR = CredentialAccount.project("pm-mcp-core", "sonar");
    private static final String GITHUB_SECRET = "ghp-secret-of-the-github-entry";
    private static final String SONAR_SECRET = "squ-secret-of-the-sonar-entry";
    private static final Instant STARTED = Instant.parse("2026-10-17T15:00:00Z");
    private static final byte[] HARNESSES = """
            {"format_version":1,"harnesses":{}}""".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path base;

    private final SecureRandom random = new SecureRandom();
    private final List<AuditEvent> events = new ArrayList<>();
    private final InMemoryOsStore osStore = new InMemoryOsStore();
    private FileLockManager locks;
    private CredentialStoreChoice choice;
    private FileSecretStore fileStore;
    private Path stateDirectory;
    private CredentialStoreMoveResume resume;

    @BeforeEach
    void setUp() throws IOException {
        stateDirectory = Files.createDirectories(base.resolve("state"));
        Files.createDirectories(new EnrolmentStore(base).directory());
        Files.createDirectories(AuditHead.headFile(base).getParent());
        locks = new FileLockManager(events::add);
        choice = CredentialStoreChoiceFixture.withOsStore(base, SERVICE, osStore);
        fileStore = new FileSecretStore(base.resolve("credentials"));
        resume = new CredentialStoreMoveResume(new CredentialStoreMove(base, choice, locks, events::add, USER,
                Clock.fixed(STARTED, ZoneOffset.UTC)));
    }

    /** Records the OS store, fills it with two entries and the key, and signs {@code harnesses.json} under the key. */
    private SecretKey machineOnTheOsStore() throws IOException {
        record(Store.OS, Writer.CREDENTIAL_STORE_ASPECT);
        osStore.put(GITHUB, GITHUB_SECRET);
        osStore.put(SONAR, SONAR_SECRET);
        var key = keyIn(osStore).readOrCreate();
        write(base.resolve(HarnessesSidecarCheck.FILE_NAME), HARNESSES);
        write(base.resolve(HarnessesSidecarCheck.FILE_NAME + EnrolmentSidecar.SUFFIX),
                EnrolmentHmac.sidecar(key, HARNESSES).write());
        return key;
    }

    private void record(Store store, Writer writer) {
        if (writer == Writer.CREDENTIAL_STORE_ASPECT) {
            choice.record(locks, USER, store);
            return;
        }
        try (var transaction = locks.openTransaction()) {
            choice.record(transaction, store, writer);
        }
    }

    private EnrolmentKey keyIn(SecretStore store) {
        return new EnrolmentKey(store, stateDirectory, USER, random);
    }

    private void openMove(Phase phase) throws IOException {
        write(recordFile(), new CredentialStoreMoveRecord(Store.OS, Store.FILE, STARTED, phase).write());
    }

    private Path recordFile() {
        return CredentialStoreMoveRecord.file(base);
    }

    private static void write(Path path, byte[] bytes) throws IOException {
        Files.write(path, bytes);
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(OWNER_ONLY));
    }

    private List<AuditEvent> movedEvents() {
        return events.stream().filter(event -> CredentialStoreMove.EVENT.equals(event.event())).toList();
    }

    /** Asserts the end state of a move from the OS store into the file store that the resume completed. */
    private void assertCompleted(Optional<CredentialStoreMoveRecord> resumed, Phase phase, SecretKey key) {
        assertAll(
                () -> assertEquals(Optional.of(new CredentialStoreMoveRecord(Store.OS, Store.FILE, STARTED, phase)),
                        resumed),
                () -> assertEquals(Optional.of(Store.FILE), choice.recorded()),
                () -> assertEquals(Optional.of(GITHUB_SECRET), fileStore.get(GITHUB)),
                () -> assertEquals(Optional.of(SONAR_SECRET), fileStore.get(SONAR)),
                () -> assertArrayEquals(key.getEncoded(), keyIn(fileStore).read().orElseThrow().getEncoded()),
                () -> assertTrue(osStore.secrets.isEmpty(), osStore.secrets.keySet().toString()),
                () -> assertFalse(Files.exists(recordFile())),
                () -> assertEquals(List.of(new AuditEvent(CredentialStoreMove.EVENT, AuditEvent.CATEGORY_OPERATOR,
                                AuditEvent.OUTCOME_SUCCEEDED, Map.of(CredentialStoreMove.DETAIL_FROM, Store.OS.value(),
                                        CredentialStoreMove.DETAIL_TO, Store.FILE.value(), CredentialStoreMove.DETAIL_ENTRIES,
                                        2L))),
                        movedEvents()),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                        INFO.CREDENTIAL_STORE_MOVE_RESUMED.resolveIdentifierString()),
                () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                        "resumed from phase '%s'".formatted(phase.value())));
    }

    @Test
    @DisplayName("without a record no move is open and nothing happens")
    void noRecord() throws Exception {
        machineOnTheOsStore();

        var resumed = resume.run();

        assertAll(
                () -> assertTrue(resumed.isEmpty()),
                () -> assertEquals(Optional.of(Store.OS), choice.recorded()),
                () -> assertEquals(List.of(GITHUB, SONAR), osStore.entries().stream()
                        .filter(account -> !EnrolmentKey.ACCOUNT.equals(account.account())).toList()),
                () -> assertTrue(fileStore.entries().isEmpty(), fileStore.entries().toString()),
                () -> assertTrue(movedEvents().isEmpty()),
                () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO, CredentialStoreMoveResume.class));
    }

    @Test
    @DisplayName("while the record of a cut move exists the aspect records no store; once the resume ended the move "
            + "it records again")
    void aspectRefusedUntilTheResume() throws Exception {
        machineOnTheOsStore();
        openMove(Phase.COPYING);

        var refusal = assertThrows(CredentialStoreChoice.Refusal.class,
                () -> record(Store.FILE, Writer.CREDENTIAL_STORE_ASPECT));
        var recordedWhileOpen = choice.recorded();
        resume.run();
        record(Store.FILE, Writer.CREDENTIAL_STORE_ASPECT);

        assertAll(
                () -> assertEquals(CredentialStoreChoice.Refusal.Reason.ASPECT_OWNED, refusal.reason()),
                () -> assertEquals(Optional.of(Store.OS), recordedWhileOpen),
                () -> assertEquals(Optional.of(Store.FILE), choice.recorded()),
                () -> assertFalse(Files.exists(recordFile())));
    }

    @Nested
    @DisplayName("a move cut before the switch")
    class CutBeforeTheSwitch {

        @Test
        @DisplayName("is started again from the first entry and completed, the old store emptied")
        void startedAgain() throws Exception {
            var key = machineOnTheOsStore();
            fileStore.put(GITHUB, GITHUB_SECRET);
            openMove(Phase.COPYING);

            var resumed = resume.run();

            assertCompleted(resumed, Phase.COPYING, key);
        }

        @Test
        @DisplayName("a copy left in the target with a stale value is replaced by the entry of the old store")
        void staleCopyReplaced() throws Exception {
            var key = machineOnTheOsStore();
            fileStore.put(SONAR, "a value the cut copy left behind");
            openMove(Phase.COPYING);

            var resumed = resume.run();

            assertCompleted(resumed, Phase.COPYING, key);
        }
    }

    @Nested
    @DisplayName("a move cut between the switch and the rewrite of the record")
    class CutBetweenTheSwitchAndTheRecord {

        /** Arranges what the stop leaves: the target recorded and complete, the record still in phase copying. */
        private SecretKey switchedWithACopyingRecord() throws IOException {
            var key = machineOnTheOsStore();
            fileStore.put(GITHUB, GITHUB_SECRET);
            fileStore.put(SONAR, SONAR_SECRET);
            keyIn(fileStore).write(key);
            openMove(Phase.COPYING);
            record(Store.FILE, Writer.STORE_MOVE);
            return key;
        }

        @Test
        @DisplayName("is treated as switched: the deletion from the old store is finished and the record ended")
        void treatedAsSwitched() throws Exception {
            var key = switchedWithACopyingRecord();

            var resumed = resume.run();

            assertCompleted(resumed, Phase.COPYING, key);
        }

        @Test
        @DisplayName("deletes nothing from the recorded target and copies nothing over it")
        void recordedTargetKept() throws Exception {
            var key = switchedWithACopyingRecord();
            fileStore.put(SONAR, "a value written into the recorded store after the switch");

            resume.run();

            assertAll(
                    () -> assertEquals(Optional.of(Store.FILE), choice.recorded()),
                    () -> assertEquals(Optional.of(GITHUB_SECRET), fileStore.get(GITHUB)),
                    () -> assertEquals(Optional.of("a value written into the recorded store after the switch"),
                            fileStore.get(SONAR)),
                    () -> assertArrayEquals(key.getEncoded(), keyIn(fileStore).read().orElseThrow().getEncoded()),
                    () -> assertTrue(osStore.secrets.isEmpty(), osStore.secrets.keySet().toString()),
                    () -> assertFalse(Files.exists(recordFile())));
        }
    }

    @Nested
    @DisplayName("a move cut after the switch")
    class CutAfterTheSwitch {

        @Test
        @DisplayName("finishes the deletion from the old store and ends the record")
        void deletionFinished() throws Exception {
            var key = machineOnTheOsStore();
            fileStore.put(GITHUB, GITHUB_SECRET);
            fileStore.put(SONAR, SONAR_SECRET);
            keyIn(fileStore).write(key);
            openMove(Phase.SWITCHED);
            record(Store.FILE, Writer.STORE_MOVE);
            osStore.delete(GITHUB);

            var resumed = resume.run();

            assertCompleted(resumed, Phase.SWITCHED, key);
        }

        @Test
        @DisplayName("a deletion the old store refuses fails the resume and leaves the record for the next start")
        void deletionRefused() throws Exception {
            var key = machineOnTheOsStore();
            fileStore.put(GITHUB, GITHUB_SECRET);
            fileStore.put(SONAR, SONAR_SECRET);
            keyIn(fileStore).write(key);
            openMove(Phase.SWITCHED);
            record(Store.FILE, Writer.STORE_MOVE);
            osStore.refuseDeletes = true;

            var failure = assertThrows(SecretStoreException.class, resume::run);

            assertAll(
                    () -> assertEquals(SecretStoreException.Reason.LOCKED, failure.reason()),
                    () -> assertEquals(Optional.of(Store.FILE), choice.recorded()),
                    () -> assertTrue(Files.exists(recordFile())),
                    () -> assertTrue(movedEvents().isEmpty()),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO, CredentialStoreMoveResume.class));
        }
    }

    /** An OS store in memory, which can refuse every deletion. */
    private static final class InMemoryOsStore implements SecretStore {

        private final Map<CredentialAccount, String> secrets = new LinkedHashMap<>();
        private boolean refuseDeletes;

        @Override
        public String name() {
            return SecretStore.KEYCHAIN;
        }

        @Override
        public void put(CredentialAccount account, String secret) {
            secrets.put(account, secret);
        }

        @Override
        public Optional<String> get(CredentialAccount account) {
            return Optional.ofNullable(secrets.get(account));
        }

        @Override
        public boolean delete(CredentialAccount account) {
            if (refuseDeletes) {
                throw new SecretStoreException(SecretStoreException.Reason.LOCKED,
                        "the fake store refuses to delete " + account.account());
            }
            return secrets.remove(account) != null;
        }

        @Override
        public List<CredentialAccount> entries() {
            return SecretStore.ordered(secrets.keySet());
        }
    }
}
