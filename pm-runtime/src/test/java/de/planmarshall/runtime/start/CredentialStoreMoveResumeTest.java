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
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.TransactionId;
import de.planmarshall.runtime.audit.AuditHead;
import de.planmarshall.runtime.credentials.CredentialAccount;
import de.planmarshall.runtime.credentials.CredentialStoreChoice;
import de.planmarshall.runtime.credentials.CredentialStoreChoice.Store;
import de.planmarshall.runtime.credentials.CredentialStoreChoice.Writer;
import de.planmarshall.runtime.credentials.CredentialStoreChoiceFixture;
import de.planmarshall.runtime.credentials.CredentialStoreMove;
import de.planmarshall.runtime.credentials.CredentialStoreMoveRecord;
import de.planmarshall.runtime.credentials.CredentialStoreMoveRecord.Operation;
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
    private static final TransactionId TRANSACTION = new TransactionId("018f0000-0000-7000-8000-000000000001");
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
        recordStore(Store.OS, Writer.CREDENTIAL_STORE_ASPECT);
        osStore.put(GITHUB, GITHUB_SECRET);
        osStore.put(SONAR, SONAR_SECRET);
        var key = keyIn(osStore).readOrCreate();
        write(base.resolve(HarnessesSidecarCheck.FILE_NAME), HARNESSES);
        write(base.resolve(HarnessesSidecarCheck.FILE_NAME + EnrolmentSidecar.SUFFIX),
                EnrolmentHmac.sidecar(key, HARNESSES).write());
        return key;
    }

    /** Records the store as the writer records it: the aspect in its own transaction, the move in one of its own. */
    private void recordStore(Store store, Writer writer) {
        if (writer == Writer.CREDENTIAL_STORE_ASPECT) {
            choice.record(locks, USER, store);
            return;
        }
        try (var transaction = locks.openTransaction()) {
            choice.record(transaction, store, writer);
        }
    }

    /** Switches {@code credentials.store} as the first recording does, in a transaction that holds the credentials lock. */
    private void switchStore(Store store) {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(LockKey.credentials(base));
            choice.record(transaction, store, Writer.CREDENTIAL_STORE_ASPECT);
        }
    }

    private EnrolmentKey keyIn(SecretStore store) {
        return new EnrolmentKey(store, stateDirectory, USER, random);
    }

    private void openMove(Phase phase) throws IOException {
        write(recordFile(), new CredentialStoreMoveRecord(Store.OS, Store.FILE, STARTED, phase, false, Operation.MOVE,
                TRANSACTION).write());
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
                () -> assertEquals(Optional.of(new CredentialStoreMoveRecord(Store.OS, Store.FILE, STARTED, phase,
                        false, Operation.MOVE, TRANSACTION)), resumed),
                () -> assertEquals(Optional.of(Store.FILE), choice.recorded()),
                () -> assertEquals(Optional.of(GITHUB_SECRET), fileStore.get(GITHUB)),
                () -> assertEquals(Optional.of(SONAR_SECRET), fileStore.get(SONAR)),
                () -> assertArrayEquals(key.getEncoded(), keyIn(fileStore).read().orElseThrow().getEncoded()),
                () -> assertTrue(osStore.secrets.isEmpty(), osStore.secrets.keySet().toString()),
                () -> assertFalse(Files.exists(recordFile())),
                () -> assertEquals(List.of(new AuditEvent(CredentialStoreMove.EVENT, AuditEvent.CATEGORY_OPERATOR,
                                AuditEvent.OUTCOME_SUCCEEDED, Map.of(CredentialStoreMove.DETAIL_FROM, Store.OS.value(),
                                        CredentialStoreMove.DETAIL_TO, Store.FILE.value(), CredentialStoreMove.DETAIL_ENTRIES,
                                        2L, AuditEvent.DETAIL_TRANSACTION_ID, TRANSACTION.value()))),
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
                () -> recordStore(Store.FILE, Writer.CREDENTIAL_STORE_ASPECT));
        var recordedWhileOpen = choice.recorded();
        resume.run();
        recordStore(Store.FILE, Writer.CREDENTIAL_STORE_ASPECT);

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
            recordStore(Store.FILE, Writer.STORE_MOVE);
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
            recordStore(Store.FILE, Writer.STORE_MOVE);
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
            recordStore(Store.FILE, Writer.STORE_MOVE);
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

    @Nested
    @DisplayName("a first recording cut by a stop")
    class FirstRecording {

        /** The machine before the choice: the key and the sidecars in the OS store, no store recorded. */
        private SecretKey machineBeforeTheChoice() throws IOException {
            var key = keyIn(osStore).readOrCreate();
            write(base.resolve(HarnessesSidecarCheck.FILE_NAME), HARNESSES);
            write(base.resolve(HarnessesSidecarCheck.FILE_NAME + EnrolmentSidecar.SUFFIX),
                    EnrolmentHmac.sidecar(key, HARNESSES).write());
            return key;
        }

        /** Writes the record of a first recording of the file store, as the stopped recording left it. */
        private void openFirstRecording(Phase phase, boolean targetHeldKey) throws IOException {
            write(recordFile(), new CredentialStoreMoveRecord(Store.OS, Store.FILE, STARTED, phase, targetHeldKey,
                    Operation.FIRST_RECORDING, TRANSACTION).write());
        }

        private void assertRecorded(Optional<CredentialStoreMoveRecord> resumed, Phase phase, SecretKey key) {
            assertAll(
                    () -> assertEquals(Optional.of(new CredentialStoreMoveRecord(Store.OS, Store.FILE, STARTED, phase,
                            false, Operation.FIRST_RECORDING, TRANSACTION)), resumed),
                    () -> assertEquals(Optional.of(Store.FILE), choice.recorded()),
                    () -> assertArrayEquals(key.getEncoded(), keyIn(fileStore).read().orElseThrow().getEncoded()),
                    () -> assertTrue(keyIn(osStore).read().isEmpty(), "the key of the OS store is deleted"),
                    () -> assertFalse(Files.exists(recordFile())),
                    () -> assertTrue(movedEvents().isEmpty(), "a first recording appends no move audit record"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                            INFO.CREDENTIAL_STORE_FIRST_RECORDING_RESUMED.resolveIdentifierString()),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO,
                            INFO.CREDENTIAL_STORE_MOVE_RESUMED.resolveIdentifierString()));
        }

        @Test
        @DisplayName("cut before the switch: the copy is removed from the file store, the recording starts again and "
                + "records the store")
        void cutBeforeTheSwitch() throws Exception {
            var key = machineBeforeTheChoice();
            keyIn(fileStore).write(key);
            openFirstRecording(Phase.COPYING, false);

            var resumed = resume.run();

            assertRecorded(resumed, Phase.COPYING, key);
        }

        @Test
        @DisplayName("cut between the switch and the rewrite of the record: the switch is read as done and the key is "
                + "deleted from the OS store")
        void cutBetweenTheSwitchAndTheRecord() throws Exception {
            var key = machineBeforeTheChoice();
            keyIn(fileStore).write(key);
            openFirstRecording(Phase.COPYING, false);
            switchStore(Store.FILE);

            var resumed = resume.run();

            assertRecorded(resumed, Phase.COPYING, key);
        }

        @Test
        @DisplayName("cut after the rewrite of the record: the key is deleted from the OS store and the record ends")
        void cutAfterTheSwitch() throws Exception {
            var key = machineBeforeTheChoice();
            keyIn(fileStore).write(key);
            openFirstRecording(Phase.SWITCHED, false);
            switchStore(Store.FILE);

            var resumed = resume.run();

            assertRecorded(resumed, Phase.SWITCHED, key);
        }

        @Test
        @DisplayName("a second resume on the finished state does nothing")
        void idempotent() throws Exception {
            var key = machineBeforeTheChoice();
            keyIn(fileStore).write(key);
            openFirstRecording(Phase.SWITCHED, false);
            switchStore(Store.FILE);
            resume.run();

            var second = resume.run();

            assertAll(
                    () -> assertTrue(second.isEmpty()),
                    () -> assertEquals(Optional.of(Store.FILE), choice.recorded()),
                    () -> assertArrayEquals(key.getEncoded(), keyIn(fileStore).read().orElseThrow().getEncoded()),
                    () -> assertTrue(keyIn(osStore).read().isEmpty()));
        }

        @Test
        @DisplayName("a first recording in phase switched without the store it records is refused and deletes nothing")
        void switchedWithoutTheRecordedStore() throws Exception {
            var key = machineBeforeTheChoice();
            keyIn(fileStore).write(key);
            openFirstRecording(Phase.SWITCHED, false);

            var failure = assertThrows(IllegalStateException.class, resume::run);

            assertAll(
                    () -> assertTrue(failure.getMessage().contains("nothing was deleted"), failure.getMessage()),
                    () -> assertTrue(choice.recorded().isEmpty()),
                    () -> assertArrayEquals(key.getEncoded(), keyIn(osStore).read().orElseThrow().getEncoded()),
                    () -> assertArrayEquals(key.getEncoded(), keyIn(fileStore).read().orElseThrow().getEncoded()),
                    () -> assertTrue(Files.exists(recordFile())));
        }

        @Test
        @DisplayName("while the record of a first recording exists the aspect records no store and no move starts")
        void aspectRefusedUntilTheResume() throws Exception {
            machineBeforeTheChoice();
            openFirstRecording(Phase.COPYING, false);

            var refusal = assertThrows(CredentialStoreChoice.Refusal.class,
                    () -> choice.record(locks, USER, Store.FILE));
            var resume = resumeMove();
            var move = assertThrows(IllegalStateException.class, () -> resume.move(Store.OS));

            assertAll(
                    () -> assertEquals(CredentialStoreChoice.Refusal.Reason.ASPECT_OWNED, refusal.reason()),
                    () -> assertTrue(move.getMessage().contains("the next start of the runtime resumes it"),
                            move.getMessage()),
                    () -> assertTrue(choice.recorded().isEmpty()),
                    () -> assertTrue(Files.exists(recordFile())));
        }

        @Test
        @DisplayName("a first recording cut before the switch while another store is recorded is refused and deletes "
                + "nothing")
        void anotherStoreRecorded() throws Exception {
            var key = machineBeforeTheChoice();
            recordStore(Store.OS, Writer.CREDENTIAL_STORE_ASPECT);
            keyIn(fileStore).write(key);
            openFirstRecording(Phase.COPYING, false);

            var failure = assertThrows(IllegalStateException.class, resume::run);

            assertAll(
                    () -> assertTrue(failure.getMessage().contains("a first recording switches only where no store is "
                            + "recorded"), failure.getMessage()),
                    () -> assertEquals(Optional.of(Store.OS), choice.recorded()),
                    () -> assertArrayEquals(key.getEncoded(), keyIn(osStore).read().orElseThrow().getEncoded()),
                    () -> assertTrue(Files.exists(recordFile())));
        }

        @Test
        @DisplayName("a first recording cut before the switch whose other store holds no key while the store being "
                + "recorded does is refused, records nothing and ends its record")
        void sourceWithoutTheKeptKey() throws Exception {
            keyIn(fileStore).readOrCreate();
            openFirstRecording(Phase.COPYING, false);

            var failure = assertThrows(SecretStoreException.class, resume::run);

            assertAll(
                    () -> assertTrue(failure.getMessage().contains("cannot be completed"), failure.getMessage()),
                    () -> assertTrue(choice.recorded().isEmpty()),
                    () -> assertFalse(Files.exists(recordFile()), "nothing was copied, so there is nothing to resume"),
                    () -> assertTrue(keyIn(fileStore).read().isPresent()),
                    () -> assertTrue(keyIn(osStore).read().isEmpty()));
        }

        @Test
        @DisplayName("a first recording whose recorded store holds a key the sidecars do not verify under beside another "
                + "key in the other store is refused and deletes nothing")
        void anotherKeyBesideOneTheSidecarsDoNotVerifyUnder() throws Exception {
            var kept = machineBeforeTheChoice();
            var other = keyIn(fileStore).readOrCreate();
            openFirstRecording(Phase.SWITCHED, true);
            switchStore(Store.FILE);

            var failure = assertThrows(IllegalStateException.class, resume::run);

            assertAll(
                    () -> assertTrue(failure.getMessage().contains("the sidecars do not verify under"),
                            failure.getMessage()),
                    () -> assertEquals(Optional.of(Store.FILE), choice.recorded()),
                    () -> assertArrayEquals(kept.getEncoded(), keyIn(osStore).read().orElseThrow().getEncoded()),
                    () -> assertArrayEquals(other.getEncoded(), keyIn(fileStore).read().orElseThrow().getEncoded()),
                    () -> assertTrue(Files.exists(recordFile())));
        }

        private CredentialStoreMove resumeMove() {
            return new CredentialStoreMove(base, choice, locks, events::add, USER,
                    Clock.fixed(STARTED, ZoneOffset.UTC));
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
