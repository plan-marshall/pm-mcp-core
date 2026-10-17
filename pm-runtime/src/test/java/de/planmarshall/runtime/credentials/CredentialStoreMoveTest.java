/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.credentials;

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
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import de.planmarshall.api.MachinePaths;
import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.store.AuditCodec;
import de.planmarshall.core.store.AuditHeadRecord;
import de.planmarshall.core.store.EnrolmentSidecar;
import de.planmarshall.core.store.EnrolmentStore;
import de.planmarshall.runtime.audit.AuditHead;
import de.planmarshall.runtime.credentials.CredentialStoreChoice.Store;
import de.planmarshall.runtime.credentials.CredentialStoreChoice.Writer;
import de.planmarshall.runtime.credentials.CredentialStoreMove.Step;
import de.planmarshall.runtime.credentials.CredentialStoreMoveRecord.Phase;
import de.planmarshall.runtime.enrolment.EnrolmentHmac;
import de.planmarshall.runtime.enrolment.EnrolmentKey;
import de.planmarshall.runtime.enrolment.HarnessesSidecarCheck;
import de.planmarshall.runtime.lock.FileLockManager;
import de.planmarshall.runtime.log.RuntimeLogMessages.INFO;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;

/**
 * The move between the credential stores (PM-CRED-2, PM-CRED-3, PM-SEC-5), against a fake OS store, so it runs on
 * every OS: every entry is copied, read back and deleted from the old store, the integrity key last after the
 * sidecars verified under it, the store is switched once and the move is audited; a failure before the switch leaves
 * the old store recorded and the target without copies; a move stopped after any step is completed by
 * {@link CredentialStoreMove#resume()}.
 */
@EnableTestLogger
@DisplayName("CredentialStoreMove: every entry between the OS store and the file store")
class CredentialStoreMoveTest {

    private static final String SERVICE = "de.planmarshall.mcp";
    private static final String USER = System.getProperty("user.name");
    private static final String OWNER_ONLY = "rw-------";
    private static final String PROJECT = "pm-mcp-core";
    private static final CredentialAccount GITHUB = CredentialAccount.global("github");
    private static final CredentialAccount SONAR = CredentialAccount.project(PROJECT, "sonar");
    private static final String GITHUB_SECRET = "ghp-secret-of-the-github-entry";
    private static final String SONAR_SECRET = "squ-secret-of-the-sonar-entry";
    private static final Instant STARTED = Instant.parse("2026-10-17T15:00:00Z");
    private static final byte[] HARNESSES = """
            {"format_version":1,"harnesses":{}}""".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path base;

    private final SecureRandom random = new SecureRandom();
    private final List<AuditEvent> events = new ArrayList<>();
    private final FakeOsStore osStore = new FakeOsStore();
    private FileLockManager locks;
    private CredentialStoreChoice choice;
    private FileSecretStore fileStore;
    private Path stateDirectory;
    private EnrolmentStore enrolments;

    @BeforeEach
    void setUp() throws IOException {
        stateDirectory = Files.createDirectories(base.resolve("state"));
        enrolments = new EnrolmentStore(base);
        Files.createDirectories(enrolments.directory());
        Files.createDirectories(AuditHead.headFile(base).getParent());
        AuditSink sink = events::add;
        locks = new FileLockManager(sink);
        choice = new CredentialStoreChoice(base, SERVICE, MachinePaths.Os.MACOS, Map.of(), new FakeKeyrings());
        fileStore = new FileSecretStore(base.resolve("credentials"));
    }

    /** Records the store, fills it with two entries and the key, and signs every sidecar under that key. */
    private SecretKey machineOn(Store store) throws IOException {
        try (var transaction = locks.openTransaction()) {
            choice.record(transaction, store, Writer.CREDENTIAL_STORE_ASPECT);
        }
        var secrets = store == Store.OS ? osStore : fileStore;
        secrets.put(GITHUB, GITHUB_SECRET);
        secrets.put(SONAR, SONAR_SECRET);
        var key = keyIn(secrets).readOrCreate();
        signAll(key);
        return key;
    }

    private EnrolmentKey keyIn(SecretStore store) {
        return new EnrolmentKey(store, stateDirectory, USER, random);
    }

    private void signAll(SecretKey key) throws IOException {
        signed(enrolments.file(PROJECT), enrolments.sidecar(PROJECT),
                "{\"project_id\":\"pm-mcp-core\"}".getBytes(StandardCharsets.UTF_8), key);
        signed(harnesses(), harnessesSidecar(), HARNESSES, key);
        writeHead(key);
    }

    private Path harnesses() {
        return base.resolve(HarnessesSidecarCheck.FILE_NAME);
    }

    private Path harnessesSidecar() {
        return base.resolve(HarnessesSidecarCheck.FILE_NAME + EnrolmentSidecar.SUFFIX);
    }

    private static void write(Path path, byte[] bytes) throws IOException {
        Files.write(path, bytes);
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(OWNER_ONLY));
    }

    private static void signed(Path file, Path sidecar, byte[] content, SecretKey key) throws IOException {
        write(file, content);
        write(sidecar, EnrolmentHmac.sidecar(key, content).write());
    }

    private void writeHead(SecretKey key) throws IOException {
        var unsigned = new AuditHeadRecord(AuditHeadRecord.CURRENT_LOG_FILE, 1, UUID.randomUUID().toString(),
                AuditCodec.GENESIS_HASH, AuditCodec.GENESIS_HASH, AuditCodec.GENESIS_HASH);
        write(AuditHead.headFile(base), AuditCodec.writeHead(AuditHead.sign(key, unsigned)));
    }

    private SecretKey otherKey() {
        return keyIn(new FakeOsStore()).readOrCreate();
    }

    private CredentialStoreMove move() {
        return move(CredentialStoreMove.StepHook.NONE);
    }

    private CredentialStoreMove move(CredentialStoreMove.StepHook hook) {
        return new CredentialStoreMove(base, choice, locks, events::add, USER, Clock.fixed(STARTED, ZoneOffset.UTC),
                hook);
    }

    private Path recordFile() {
        return CredentialStoreMoveRecord.file(base);
    }

    private List<AuditEvent> movedEvents() {
        return events.stream().filter(event -> CredentialStoreMove.EVENT.equals(event.event())).toList();
    }

    /** Asserts the end state of a completed move from {@code from} into {@code to}. */
    private void assertMoved(Store from, Store to, SecretStore target, SecretStore old, SecretKey key) {
        assertAll(
                () -> assertEquals(Optional.of(to), choice.recorded()),
                () -> assertEquals(Optional.of(GITHUB_SECRET), target.get(GITHUB)),
                () -> assertEquals(Optional.of(SONAR_SECRET), target.get(SONAR)),
                () -> assertArrayEquals(key.getEncoded(), keyIn(target).read().orElseThrow().getEncoded()),
                () -> assertTrue(old.entries().isEmpty(), old.entries().toString()),
                () -> assertTrue(keyIn(old).read().isEmpty()),
                () -> assertFalse(Files.exists(recordFile())),
                () -> assertEquals(List.of(new AuditEvent(CredentialStoreMove.EVENT, AuditEvent.CATEGORY_OPERATOR,
                        AuditEvent.OUTCOME_SUCCEEDED, Map.of(CredentialStoreMove.DETAIL_FROM, from.value(),
                                CredentialStoreMove.DETAIL_TO, to.value(), CredentialStoreMove.DETAIL_ENTRIES, 2L))),
                        movedEvents()),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                        INFO.CREDENTIAL_STORE_MOVED.resolveIdentifierString()));
    }

    /** Asserts the end state of a move from the OS store into the file store that failed before the switch. */
    private void assertNothingSwitched(SecretStoreException failure) {
        assertAll(
                () -> assertEquals(SecretStoreException.Reason.FAILED, failure.reason()),
                () -> assertNoSecretIn(failure),
                () -> assertEquals(Optional.of(Store.OS), choice.recorded()),
                () -> assertEquals(Optional.of(GITHUB_SECRET), osStore.get(GITHUB)),
                () -> assertEquals(Optional.of(SONAR_SECRET), osStore.get(SONAR)),
                () -> assertTrue(keyIn(osStore).read().isPresent()),
                () -> assertTrue(fileStore.entries().isEmpty(), fileStore.entries().toString()),
                () -> assertFalse(Files.exists(keyIn(fileStore).keyFile())),
                () -> assertFalse(Files.exists(recordFile())),
                () -> assertTrue(movedEvents().isEmpty()),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                        WARN.CREDENTIAL_STORE_MOVE_FAILED.resolveIdentifierString()));
    }

    private static void assertNoSecretIn(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            var message = String.valueOf(cause.getMessage());
            assertFalse(message.contains(GITHUB_SECRET) || message.contains(SONAR_SECRET), message);
        }
    }

    @Nested
    @DisplayName("a completed move")
    class Completed {

        @Test
        @DisplayName("from the OS store into the file store: every entry and the key move, the OS store is emptied")
        void osToFile() throws IOException {
            var key = machineOn(Store.OS);

            move().move(Store.FILE);

            assertMoved(Store.OS, Store.FILE, fileStore, osStore, key);
        }

        @Test
        @DisplayName("from the file store into the OS store: every entry and the key file move, the file store is emptied")
        void fileToOs() throws IOException {
            var key = machineOn(Store.FILE);

            move().move(Store.OS);

            assertAll(
                    () -> assertMoved(Store.FILE, Store.OS, osStore, fileStore, key),
                    () -> assertFalse(Files.exists(keyIn(fileStore).keyFile())));
        }

        @Test
        @DisplayName("the integrity key is copied last, after every Token Lane entry")
        void keyLast() throws IOException {
            machineOn(Store.FILE);

            move().move(Store.OS);

            assertEquals(List.of(GITHUB.account(), SONAR.account(), EnrolmentKey.ACCOUNT), osStore.puts);
        }

        @Test
        @DisplayName("a machine without harnesses.json moves")
        void withoutHarnesses() throws IOException {
            var key = machineOn(Store.OS);
            Files.delete(harnesses());
            Files.delete(harnessesSidecar());

            move().move(Store.FILE);

            assertMoved(Store.OS, Store.FILE, fileStore, osStore, key);
        }

        @Test
        @DisplayName("the start of the move is logged with both stores")
        void startLogged() throws IOException {
            machineOn(Store.OS);

            move().move(Store.FILE);

            LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                    INFO.CREDENTIAL_STORE_MOVE_STARTED.resolveIdentifierString());
        }
    }

    @Nested
    @DisplayName("a move that fails before the switch leaves the old store recorded and the target without copies")
    class FailsBeforeTheSwitch {

        /** A sidecar of one kind that fails under the moved key. */
        enum Tampered {
            ENROLMENT,
            HARNESSES,
            AUDIT_HEAD
        }

        @ParameterizedTest(name = "a tampered {0}")
        @EnumSource(Tampered.class)
        @DisplayName("a sidecar that fails under the moved key")
        void tamperedSidecar(Tampered tampered) throws IOException {
            machineOn(Store.OS);
            var other = otherKey();
            switch (tampered) {
                case ENROLMENT -> signed(enrolments.file(PROJECT), enrolments.sidecar(PROJECT),
                        "{\"project_id\":\"pm-mcp-core\",\"pilot\":true}".getBytes(StandardCharsets.UTF_8), other);
                case HARNESSES -> signed(harnesses(), harnessesSidecar(), HARNESSES, other);
                case AUDIT_HEAD -> writeHead(other);
            }
            var mover = move();

            var failure = assertThrows(SecretStoreException.class, () -> mover.move(Store.FILE));

            assertNothingSwitched(failure);
        }

        @Test
        @DisplayName("a copy the target refuses")
        void failingCopy() throws IOException {
            machineOn(Store.FILE);
            osStore.failPutOf = SONAR.account();
            var mover = move();

            var failure = assertThrows(SecretStoreException.class, () -> mover.move(Store.OS));

            assertAll(
                    () -> assertEquals(SecretStoreException.Reason.FAILED, failure.reason()),
                    () -> assertNoSecretIn(failure),
                    () -> assertEquals(Optional.of(Store.FILE), choice.recorded()),
                    () -> assertTrue(osStore.secrets.isEmpty(), osStore.secrets.keySet().toString()),
                    () -> assertEquals(List.of(GITHUB, SONAR), fileStore.entries()),
                    () -> assertTrue(keyIn(fileStore).read().isPresent()),
                    () -> assertFalse(Files.exists(recordFile())));
        }

        @Test
        @DisplayName("an entry that reads back with another value")
        void failingReadBack() throws IOException {
            machineOn(Store.FILE);
            osStore.changedReadOf = GITHUB.account();
            var mover = move();

            var failure = assertThrows(SecretStoreException.class, () -> mover.move(Store.OS));

            assertAll(
                    () -> assertEquals(SecretStoreException.Reason.FAILED, failure.reason()),
                    () -> assertTrue(failure.getMessage().contains("does not read back"), failure.getMessage()),
                    () -> assertNoSecretIn(failure),
                    () -> assertEquals(Optional.of(Store.FILE), choice.recorded()),
                    () -> assertTrue(osStore.secrets.isEmpty(), osStore.secrets.keySet().toString()),
                    () -> assertEquals(List.of(GITHUB, SONAR), fileStore.entries()),
                    () -> assertFalse(Files.exists(recordFile())));
        }
    }

    @Nested
    @DisplayName("a move cut by a stop is completed by the resume")
    class Resumed {

        @ParameterizedTest(name = "stopped after {0}")
        @EnumSource(Step.class)
        @DisplayName("a stop after each step leaves a record from which the resume completes the move")
        void stoppedAfter(Step stop) throws IOException {
            var key = machineOn(Store.OS);
            var stopped = move(step -> {
                if (step == stop) {
                    throw new Stop();
                }
            });

            assertThrows(Stop.class, () -> stopped.move(Store.FILE));
            var record = CredentialStoreMoveRecord.read(recordFile(), Files.readAllBytes(recordFile()));
            var resumed = move().resume();

            assertAll(
                    () -> assertEquals(stop.compareTo(Step.SWITCHED) < 0 ? Phase.COPYING : Phase.SWITCHED,
                            record.phase()),
                    () -> assertEquals(Optional.of(record), resumed),
                    () -> assertMoved(Store.OS, Store.FILE, fileStore, osStore, key));
        }

        @Test
        @DisplayName("without a record the resume does nothing")
        void noRecord() throws IOException {
            machineOn(Store.OS);

            assertAll(
                    () -> assertTrue(move().resume().isEmpty()),
                    () -> assertEquals(Optional.of(Store.OS), choice.recorded()),
                    () -> assertTrue(fileStore.entries().isEmpty()),
                    () -> assertTrue(movedEvents().isEmpty()));
        }
    }

    @Nested
    @DisplayName("a move that cannot start writes nothing")
    class Refused {

        @Test
        @DisplayName("before a store is recorded it is refused with onboarding_incomplete")
        void notRecorded() {
            var mover = move();

            var refusal = assertThrows(CredentialStoreChoice.Refusal.class, () -> mover.move(Store.FILE));

            assertAll(
                    () -> assertEquals(CredentialStoreChoice.Refusal.Reason.ONBOARDING_INCOMPLETE, refusal.reason()),
                    () -> assertFalse(Files.exists(recordFile())));
        }

        @Test
        @DisplayName("a move into the recorded store is refused")
        void intoTheRecordedStore() throws IOException {
            machineOn(Store.OS);
            var mover = move();

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> mover.move(Store.OS)),
                    () -> assertFalse(Files.exists(recordFile())),
                    () -> assertTrue(fileStore.entries().isEmpty()));
        }

        @Test
        @DisplayName("while a move is open another one is refused")
        void moveOpen() throws IOException {
            machineOn(Store.OS);
            var open = new CredentialStoreMoveRecord(Store.OS, Store.FILE, STARTED, Phase.COPYING);
            write(recordFile(), open.write());
            var mover = move();

            assertAll(
                    () -> assertThrows(IllegalStateException.class, () -> mover.move(Store.FILE)),
                    () -> assertTrue(fileStore.entries().isEmpty()),
                    () -> assertEquals(Optional.of(Store.OS), choice.recorded()));
        }
    }

    /** The stop of a process, simulated by the step hook. */
    private static final class Stop extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;
    }

    /** The keyrings of a macOS machine whose Keychain is the fake OS store. */
    private final class FakeKeyrings implements CredentialStoreChoice.Keyrings {

        @Override
        public SecretStore keychain(String service) {
            return osStore;
        }

        @Override
        public Optional<SecretStore> secretService(Path socket, String service) {
            return Optional.empty();
        }
    }

    /** An OS store in memory that records its writes and can fail one write or change one read. */
    private static final class FakeOsStore implements SecretStore {

        private final Map<String, String> secrets = new TreeMap<>();
        private final List<String> puts = new ArrayList<>();
        private String failPutOf;
        private String changedReadOf;

        @Override
        public String name() {
            return SecretStore.KEYCHAIN;
        }

        @Override
        public void put(CredentialAccount account, String secret) {
            if (account.account().equals(failPutOf)) {
                throw new SecretStoreException(SecretStoreException.Reason.FAILED,
                        "the fake store refuses " + account.account());
            }
            puts.add(account.account());
            secrets.put(account.account(), secret);
        }

        @Override
        public Optional<String> get(CredentialAccount account) {
            var secret = Optional.ofNullable(secrets.get(account.account()));
            return account.account().equals(changedReadOf) ? secret.map(value -> value + "-changed") : secret;
        }

        @Override
        public boolean delete(CredentialAccount account) {
            return secrets.remove(account.account()) != null;
        }

        @Override
        public List<CredentialAccount> entries() {
            return SecretStore.ordered(secrets.keySet().stream().map(CredentialAccount::parse).toList());
        }
    }
}
