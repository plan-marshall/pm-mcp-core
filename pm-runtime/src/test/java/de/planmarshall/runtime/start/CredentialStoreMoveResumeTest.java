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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
@EnableGeneratorController
@EnableTestLogger
@DisplayName("CredentialStoreMoveResume: a move cut by a stop is completed after the next start")
class CredentialStoreMoveResumeTest {

    /** Contract literal: the service name of the product under which the OS store keeps its entries. */
    private static final String SERVICE = "de.planmarshall.mcp";
    private static final String USER = System.getProperty("user.name");
    /** Contract literal: the owner-only mode the runtime prescribes for its state files. */
    private static final String OWNER_ONLY = "rw-------";
    /** Contract literal: the smallest {@code harnesses.json} of its schema. */
    private static final byte[] HARNESSES = """
            {"format_version":1,"harnesses":{}}""".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path base;

    /** The SecureRandom is the entropy source the integrity key needs, not test data. */
    private final SecureRandom random = new SecureRandom();
    private final List<AuditEvent> events = new ArrayList<>();
    private final InMemoryOsStore osStore = new InMemoryOsStore();
    private final CredentialAccount github = CredentialAccount.global("token-" + name());
    private final CredentialAccount sonar = CredentialAccount.project("project-" + name(), "token-" + name());
    private final String githubSecret = secret();
    private final String sonarSecret = secret();
    private final Instant started = Instant.ofEpochSecond(Generators.longs(1_700_000_000L, 2_000_000_000L).next());
    private final TransactionId transaction = transactionId();
    private FileLockManager locks;
    private CredentialStoreChoice choice;
    private FileSecretStore fileStore;
    private Path stateDirectory;
    private CredentialStoreMoveResume resume;

    private static String name() {
        return Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }

    private static String secret() {
        return Generators.letterStrings(16, 40).next();
    }

    private static String hex(int length) {
        return Generators.strings("0123456789abcdef", length, length).next();
    }

    /** @return a generated transaction id of the form of a lower-case UUID of version 7 */
    private static TransactionId transactionId() {
        return new TransactionId("%s-%s-7%s-8%s-%s".formatted(hex(8), hex(4), hex(3), hex(3), hex(12)));
    }

    @BeforeEach
    void setUp() throws IOException {
        // Contract literals: the directory names the runtime prescribes below the base
        stateDirectory = Files.createDirectories(base.resolve("state"));
        Files.createDirectories(new EnrolmentStore(base).directory());
        Files.createDirectories(AuditHead.headFile(base).getParent());
        locks = new FileLockManager(events::add);
        choice = CredentialStoreChoiceFixture.withOsStore(base, SERVICE, osStore);
        fileStore = new FileSecretStore(base.resolve("credentials"));
        resume = new CredentialStoreMoveResume(new CredentialStoreMove(base, choice, locks, events::add, USER,
                Clock.fixed(started, ZoneOffset.UTC)));
    }

    /** Records the OS store, fills it with two entries and the key, and signs {@code harnesses.json} under the key. */
    private SecretKey machineOnTheOsStore() throws IOException {
        recordStore(Store.OS, Writer.CREDENTIAL_STORE_ASPECT);
        osStore.put(github, githubSecret);
        osStore.put(sonar, sonarSecret);
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
        try (var open = locks.openTransaction()) {
            choice.record(open, store, writer);
        }
    }

    /** Switches {@code credentials.store} as the first recording does, in a transaction that holds the credentials lock. */
    private void switchStore(Store store) {
        try (var open = locks.openTransaction()) {
            open.acquire(LockKey.credentials(base));
            choice.record(open, store, Writer.CREDENTIAL_STORE_ASPECT);
        }
    }

    private EnrolmentKey keyIn(SecretStore store) {
        return new EnrolmentKey(store, stateDirectory, USER, random);
    }

    private void openMove(Phase phase) throws IOException {
        write(recordFile(), new CredentialStoreMoveRecord(Store.OS, Store.FILE, started, phase, false, Operation.MOVE,
                transaction).write());
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
                () -> assertEquals(Optional.of(new CredentialStoreMoveRecord(Store.OS, Store.FILE, started, phase,
                        false, Operation.MOVE, transaction)), resumed, "the resume must return the record it ended"),
                () -> assertEquals(Optional.of(Store.FILE), choice.recorded(), "the target store must be recorded"),
                () -> assertEquals(Optional.of(githubSecret), fileStore.get(github),
                        "the target must hold the global entry"),
                () -> assertEquals(Optional.of(sonarSecret), fileStore.get(sonar),
                        "the target must hold the project entry"),
                () -> assertArrayEquals(key.getEncoded(), keyIn(fileStore).read().orElseThrow().getEncoded(),
                        "the target must hold the integrity key"),
                () -> assertTrue(osStore.secrets.isEmpty(), "the old store must be emptied by the resume"),
                () -> assertFalse(Files.exists(recordFile()), "the record of the move must be gone"),
                () -> assertEquals(List.of(new AuditEvent(CredentialStoreMove.EVENT, AuditEvent.CATEGORY_OPERATOR,
                                AuditEvent.OUTCOME_SUCCEEDED, Map.of(CredentialStoreMove.DETAIL_FROM, Store.OS.value(),
                                        CredentialStoreMove.DETAIL_TO, Store.FILE.value(), CredentialStoreMove.DETAIL_ENTRIES,
                                        2L, AuditEvent.DETAIL_TRANSACTION_ID, transaction.value()))),
                        movedEvents(), "exactly one move audit record must be appended"),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                        INFO.CREDENTIAL_STORE_MOVE_RESUMED.resolveIdentifierString()),
                // Contract literal: the phase clause of the specified message of the record
                () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                        "resumed from phase '%s'".formatted(phase.value())));
    }

    @Test
    @DisplayName("without a record no move is open and nothing happens")
    void noRecord() throws Exception {
        machineOnTheOsStore();

        var resumed = resume.run();

        assertAll(
                () -> assertTrue(resumed.isEmpty(), "without a record nothing is resumed"),
                () -> assertEquals(Optional.of(Store.OS), choice.recorded(), "the OS store must stay recorded"),
                () -> assertEquals(SecretStore.ordered(Set.of(github, sonar)), osStore.entries().stream()
                        .filter(account -> !EnrolmentKey.ACCOUNT.equals(account.account())).toList(),
                        "the OS store must keep both of its entries"),
                () -> assertTrue(fileStore.entries().isEmpty(), "the file store must stay empty"),
                () -> assertTrue(movedEvents().isEmpty(), "no move audit record is appended"),
                () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO, CredentialStoreMoveResume.class));
    }

    @Test
    @DisplayName("while the record of a cut move exists the aspect records no store; once the resume ended the move "
            + "it records again")
    void aspectRefusedUntilTheResume() throws Exception {
        machineOnTheOsStore();
        openMove(Phase.COPYING);

        var refusal = assertThrows(CredentialStoreChoice.Refusal.class,
                () -> recordStore(Store.FILE, Writer.CREDENTIAL_STORE_ASPECT),
                "the aspect must be refused while the move is open");
        var recordedWhileOpen = choice.recorded();
        resume.run();
        recordStore(Store.FILE, Writer.CREDENTIAL_STORE_ASPECT);

        assertAll(
                () -> assertEquals(CredentialStoreChoice.Refusal.Reason.ASPECT_OWNED, refusal.reason(),
                        "the refusal must name the owned aspect"),
                () -> assertEquals(Optional.of(Store.OS), recordedWhileOpen,
                        "the store must not change while the move is open"),
                () -> assertEquals(Optional.of(Store.FILE), choice.recorded(),
                        "the aspect must record again after the resume"),
                () -> assertFalse(Files.exists(recordFile()), "the record of the move must be gone"));
    }

    @Nested
    @DisplayName("a move cut before the switch")
    class CutBeforeTheSwitch {

        @Test
        @DisplayName("is started again from the first entry and completed, the old store emptied")
        void startedAgain() throws Exception {
            var key = machineOnTheOsStore();
            fileStore.put(github, githubSecret);
            openMove(Phase.COPYING);

            var resumed = resume.run();

            assertCompleted(resumed, Phase.COPYING, key);
        }

        @Test
        @DisplayName("a copy left in the target with a stale value is replaced by the entry of the old store")
        void staleCopyReplaced() throws Exception {
            var key = machineOnTheOsStore();
            fileStore.put(sonar, secret());
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
            fileStore.put(github, githubSecret);
            fileStore.put(sonar, sonarSecret);
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
            var writtenAfterTheSwitch = secret();
            fileStore.put(sonar, writtenAfterTheSwitch);

            resume.run();

            assertAll(
                    () -> assertEquals(Optional.of(Store.FILE), choice.recorded(),
                            "the target store must stay recorded"),
                    () -> assertEquals(Optional.of(githubSecret), fileStore.get(github),
                            "the target must keep the global entry"),
                    () -> assertEquals(Optional.of(writtenAfterTheSwitch), fileStore.get(sonar),
                            "a value written after the switch must stay"),
                    () -> assertArrayEquals(key.getEncoded(), keyIn(fileStore).read().orElseThrow().getEncoded(),
                            "the target must keep the integrity key"),
                    () -> assertTrue(osStore.secrets.isEmpty(), "the old store must be emptied by the resume"),
                    () -> assertFalse(Files.exists(recordFile()), "the record of the move must be gone"));
        }
    }

    @Nested
    @DisplayName("a move cut after the switch")
    class CutAfterTheSwitch {

        @Test
        @DisplayName("finishes the deletion from the old store and ends the record")
        void deletionFinished() throws Exception {
            var key = machineOnTheOsStore();
            fileStore.put(github, githubSecret);
            fileStore.put(sonar, sonarSecret);
            keyIn(fileStore).write(key);
            openMove(Phase.SWITCHED);
            recordStore(Store.FILE, Writer.STORE_MOVE);
            osStore.delete(github);

            var resumed = resume.run();

            assertCompleted(resumed, Phase.SWITCHED, key);
        }

        @Test
        @DisplayName("a deletion the old store refuses fails the resume and leaves the record for the next start")
        void deletionRefused() throws Exception {
            var key = machineOnTheOsStore();
            fileStore.put(github, githubSecret);
            fileStore.put(sonar, sonarSecret);
            keyIn(fileStore).write(key);
            openMove(Phase.SWITCHED);
            recordStore(Store.FILE, Writer.STORE_MOVE);
            osStore.refuseDeletes = true;

            var failure = assertThrows(SecretStoreException.class, resume::run,
                    "a refused deletion must fail the resume");

            assertAll(
                    () -> assertEquals(SecretStoreException.Reason.LOCKED, failure.reason(),
                            "the failure must carry the reason of the store"),
                    () -> assertEquals(Optional.of(Store.FILE), choice.recorded(),
                            "the target store must stay recorded"),
                    () -> assertTrue(Files.exists(recordFile()), "the record must stay for the next start"),
                    () -> assertTrue(movedEvents().isEmpty(), "no move audit record is appended"),
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
            write(recordFile(), new CredentialStoreMoveRecord(Store.OS, Store.FILE, started, phase, targetHeldKey,
                    Operation.FIRST_RECORDING, transaction).write());
        }

        private void assertRecorded(Optional<CredentialStoreMoveRecord> resumed, Phase phase, SecretKey key) {
            assertAll(
                    () -> assertEquals(Optional.of(new CredentialStoreMoveRecord(Store.OS, Store.FILE, started, phase,
                            false, Operation.FIRST_RECORDING, transaction)), resumed,
                            "the resume must return the record it ended"),
                    () -> assertEquals(Optional.of(Store.FILE), choice.recorded(), "the file store must be recorded"),
                    () -> assertArrayEquals(key.getEncoded(), keyIn(fileStore).read().orElseThrow().getEncoded(),
                            "the file store must hold the integrity key"),
                    () -> assertTrue(keyIn(osStore).read().isEmpty(), "the key of the OS store is deleted"),
                    () -> assertFalse(Files.exists(recordFile()), "the record of the recording must be gone"),
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
                    () -> assertTrue(second.isEmpty(), "a second resume must resume nothing"),
                    () -> assertEquals(Optional.of(Store.FILE), choice.recorded(),
                            "the file store must stay recorded"),
                    () -> assertArrayEquals(key.getEncoded(), keyIn(fileStore).read().orElseThrow().getEncoded(),
                            "the file store must keep the integrity key"),
                    () -> assertTrue(keyIn(osStore).read().isEmpty(), "the OS store must stay without the key"));
        }

        @Test
        @DisplayName("a first recording in phase switched without the store it records is refused and deletes nothing "
                + "(the message is the specified error text)")
        void switchedWithoutTheRecordedStore() throws Exception {
            var key = machineBeforeTheChoice();
            keyIn(fileStore).write(key);
            openFirstRecording(Phase.SWITCHED, false);

            var failure = assertThrows(IllegalStateException.class, resume::run,
                    "a switched recording without its store fails");

            assertAll(
                    () -> assertTrue(failure.getMessage().contains("nothing was deleted"),
                            "the failure must say that nothing was deleted"),
                    () -> assertTrue(choice.recorded().isEmpty(), "no store must be recorded"),
                    () -> assertArrayEquals(key.getEncoded(), keyIn(osStore).read().orElseThrow().getEncoded(),
                            "the OS store must keep the integrity key"),
                    () -> assertArrayEquals(key.getEncoded(), keyIn(fileStore).read().orElseThrow().getEncoded(),
                            "the file store must keep the integrity key"),
                    () -> assertTrue(Files.exists(recordFile()), "the record must stay for the next start"));
        }

        @Test
        @DisplayName("while the record of a first recording exists the aspect records no store and no move starts "
                + "(the message is the specified error text)")
        void aspectRefusedUntilTheResume() throws Exception {
            machineBeforeTheChoice();
            openFirstRecording(Phase.COPYING, false);

            var refusal = assertThrows(CredentialStoreChoice.Refusal.class,
                    () -> choice.record(locks, USER, Store.FILE), "the aspect must be refused while recording");
            var resumption = resumeMove();
            var move = assertThrows(IllegalStateException.class, () -> resumption.move(Store.OS),
                    "no move must start while recording");

            assertAll(
                    () -> assertEquals(CredentialStoreChoice.Refusal.Reason.ASPECT_OWNED, refusal.reason(),
                            "the refusal must name the owned aspect"),
                    () -> assertTrue(move.getMessage().contains("the next start of the runtime resumes it"),
                            "the failure must point to the next start"),
                    () -> assertTrue(choice.recorded().isEmpty(), "no store must be recorded"),
                    () -> assertTrue(Files.exists(recordFile()), "the record must stay for the next start"));
        }

        @Test
        @DisplayName("a first recording cut before the switch while another store is recorded is refused and deletes "
                + "nothing (the message is the specified error text)")
        void anotherStoreRecorded() throws Exception {
            var key = machineBeforeTheChoice();
            recordStore(Store.OS, Writer.CREDENTIAL_STORE_ASPECT);
            keyIn(fileStore).write(key);
            openFirstRecording(Phase.COPYING, false);

            var failure = assertThrows(IllegalStateException.class, resume::run,
                    "a recording beside another store must fail");

            assertAll(
                    () -> assertTrue(failure.getMessage().contains("a first recording switches only where no store is "
                            + "recorded"), "the failure must name the recorded store"),
                    () -> assertEquals(Optional.of(Store.OS), choice.recorded(), "the OS store must stay recorded"),
                    () -> assertArrayEquals(key.getEncoded(), keyIn(osStore).read().orElseThrow().getEncoded(),
                            "the OS store must keep the integrity key"),
                    () -> assertTrue(Files.exists(recordFile()), "the record must stay for the next start"));
        }

        @Test
        @DisplayName("a first recording cut before the switch whose other store holds no key while the store being "
                + "recorded does is refused, records nothing and ends its record (the message is the error text)")
        void sourceWithoutTheKeptKey() throws Exception {
            keyIn(fileStore).readOrCreate();
            openFirstRecording(Phase.COPYING, false);

            var failure = assertThrows(SecretStoreException.class, resume::run,
                    "a source without the kept key must fail");

            assertAll(
                    () -> assertTrue(failure.getMessage().contains("cannot be completed"),
                            "the failure must say it cannot be completed"),
                    () -> assertTrue(choice.recorded().isEmpty(), "no store must be recorded"),
                    () -> assertFalse(Files.exists(recordFile()), "nothing was copied, so there is nothing to resume"),
                    () -> assertTrue(keyIn(fileStore).read().isPresent(), "the file store must keep its key"),
                    () -> assertTrue(keyIn(osStore).read().isEmpty(), "the OS store must stay without a key"));
        }

        @Test
        @DisplayName("a first recording whose recorded store holds a key the sidecars do not verify under beside another "
                + "key in the other store is refused and deletes nothing (the message is the error text)")
        void anotherKeyBesideOneTheSidecarsDoNotVerifyUnder() throws Exception {
            var kept = machineBeforeTheChoice();
            var other = keyIn(fileStore).readOrCreate();
            openFirstRecording(Phase.SWITCHED, true);
            switchStore(Store.FILE);

            var failure = assertThrows(IllegalStateException.class, resume::run,
                    "a key the sidecars refuse must fail the resume");

            assertAll(
                    () -> assertTrue(failure.getMessage().contains("the sidecars do not verify under"),
                            "the failure must name the unverified key"),
                    () -> assertEquals(Optional.of(Store.FILE), choice.recorded(),
                            "the file store must stay recorded"),
                    () -> assertArrayEquals(kept.getEncoded(), keyIn(osStore).read().orElseThrow().getEncoded(),
                            "the OS store must keep the verified key"),
                    () -> assertArrayEquals(other.getEncoded(), keyIn(fileStore).read().orElseThrow().getEncoded(),
                            "the file store must keep its other key"),
                    () -> assertTrue(Files.exists(recordFile()), "the record must stay for the next start"));
        }

        private CredentialStoreMove resumeMove() {
            return new CredentialStoreMove(base, choice, locks, events::add, USER,
                    Clock.fixed(started, ZoneOffset.UTC));
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
                // Contract literal: the error text of the simulated refusal
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
