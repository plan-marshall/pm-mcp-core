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
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
        choice.record(locks, USER, store);
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

    /** @return a hook that stops the move after the step, as a killed process would */
    private static CredentialStoreMove.StepHook stopAfter(Step stop) {
        return step -> {
            if (step == stop) {
                throw new Stop();
            }
        };
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
                () -> assertTrue(failure.getMessage().contains(
                                "failed before the switch, the copies were removed from 'file' and 'os' stays recorded"),
                        failure.getMessage()),
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
        void osToFile() throws Exception {
            var key = machineOn(Store.OS);

            move().move(Store.FILE);

            assertMoved(Store.OS, Store.FILE, fileStore, osStore, key);
        }

        @Test
        @DisplayName("from the file store into the OS store: every entry and the key file move, the file store is emptied")
        void fileToOs() throws Exception {
            var key = machineOn(Store.FILE);

            move().move(Store.OS);

            assertAll(
                    () -> assertMoved(Store.FILE, Store.OS, osStore, fileStore, key),
                    () -> assertFalse(Files.exists(keyIn(fileStore).keyFile())));
        }

        @Test
        @DisplayName("the integrity key is copied last, after every Token Lane entry")
        void keyLast() throws Exception {
            machineOn(Store.FILE);

            move().move(Store.OS);

            assertEquals(List.of(GITHUB.account(), SONAR.account(), EnrolmentKey.ACCOUNT), osStore.puts);
        }

        @Test
        @DisplayName("a machine without harnesses.json moves")
        void withoutHarnesses() throws Exception {
            var key = machineOn(Store.OS);
            Files.delete(harnesses());
            Files.delete(harnessesSidecar());

            move().move(Store.FILE);

            assertMoved(Store.OS, Store.FILE, fileStore, osStore, key);
        }

        @Test
        @DisplayName("the start of the move is logged with both stores")
        void startLogged() throws Exception {
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
        void tamperedSidecar(Tampered tampered) throws Exception {
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
        void failingCopy() throws Exception {
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
        void failingReadBack() throws Exception {
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

        @Test
        @DisplayName("an integrity key that reads back as another key")
        void failingKeyReadBack() throws Exception {
            machineOn(Store.FILE);
            osStore.readAs.put(EnrolmentKey.ACCOUNT,
                    Base64.getUrlEncoder().withoutPadding().encodeToString(otherKey().getEncoded()));
            var mover = move();

            var failure = assertThrows(SecretStoreException.class, () -> mover.move(Store.OS));

            assertAll(
                    () -> assertEquals(SecretStoreException.Reason.FAILED, failure.reason()),
                    () -> assertTrue(failure.getMessage().contains("integrity key does not read back"),
                            failure.getMessage()),
                    () -> assertEquals(Optional.of(Store.FILE), choice.recorded()),
                    () -> assertTrue(osStore.secrets.isEmpty(), osStore.secrets.keySet().toString()),
                    () -> assertEquals(List.of(GITHUB, SONAR), fileStore.entries()),
                    () -> assertTrue(keyIn(fileStore).read().isPresent()),
                    () -> assertFalse(Files.exists(recordFile())));
        }

        @Test
        @DisplayName("an entry the old store lists and no longer holds")
        void vanishedEntry() throws Exception {
            machineOn(Store.OS);
            osStore.vanishedOf = SONAR.account();
            var mover = move();

            var failure = assertThrows(SecretStoreException.class, () -> mover.move(Store.FILE));

            assertAll(
                    () -> assertEquals(SecretStoreException.Reason.FAILED, failure.reason()),
                    () -> assertTrue(failure.getMessage().contains("vanished"), failure.getMessage()),
                    () -> assertNoSecretIn(failure),
                    () -> assertEquals(Optional.of(Store.OS), choice.recorded()),
                    () -> assertEquals(Optional.of(GITHUB_SECRET), osStore.get(GITHUB)),
                    () -> assertTrue(fileStore.entries().isEmpty(), fileStore.entries().toString()),
                    () -> assertFalse(Files.exists(recordFile())));
        }

        @Test
        @DisplayName("a clean-up the target refuses is added to the cause, and the record is kept for the next start")
        void failingCleanUp() throws Exception {
            machineOn(Store.FILE);
            osStore.failPutOf = SONAR.account();
            osStore.failDeleteOf = GITHUB.account();
            var mover = move();

            var failure = assertThrows(SecretStoreException.class, () -> mover.move(Store.OS));

            assertAll(
                    () -> assertEquals(SecretStoreException.Reason.FAILED, failure.reason()),
                    () -> assertNoSecretIn(failure),
                    () -> assertTrue(failure.getMessage().contains(
                                    "the copies could not all be removed from 'os' and 'file' stays recorded; the "
                                            + "record is kept"),
                            failure.getMessage()),
                    () -> assertEquals(1, failure.getCause().getSuppressed().length),
                    () -> assertTrue(failure.getCause().getSuppressed()[0].getMessage().contains("refuses to delete"),
                            failure.getCause().getSuppressed()[0].getMessage()),
                    () -> assertEquals(Optional.of(Store.FILE), choice.recorded()),
                    () -> assertEquals(List.of(GITHUB, SONAR), fileStore.entries()),
                    () -> assertTrue(keyIn(fileStore).read().isPresent()),
                    () -> assertEquals(Optional.of(GITHUB_SECRET), osStore.get(GITHUB)),
                    () -> assertTrue(Files.exists(recordFile())));
        }

        @Test
        @DisplayName("after a clean-up the target refused, the resume removes the copies and completes the move")
        void resumeAfterAFailedCleanUpCompletes() throws Exception {
            var key = machineOn(Store.FILE);
            osStore.failPutOf = SONAR.account();
            osStore.failDeleteOf = GITHUB.account();
            var failing = move();
            assertThrows(SecretStoreException.class, () -> failing.move(Store.OS));
            osStore.failPutOf = null;
            osStore.failDeleteOf = null;

            var resumed = move().resume();

            assertAll(
                    () -> assertTrue(resumed.isPresent()),
                    () -> assertMoved(Store.FILE, Store.OS, osStore, fileStore, key));
        }

        @Test
        @DisplayName("after a clean-up the target refused, a resume whose copy fails again removes the copies and "
                + "deletes the record")
        void resumeAfterAFailedCleanUpFailsCleanly() throws Exception {
            machineOn(Store.FILE);
            osStore.failPutOf = SONAR.account();
            osStore.failDeleteOf = GITHUB.account();
            var failing = move();
            assertThrows(SecretStoreException.class, () -> failing.move(Store.OS));
            osStore.failDeleteOf = null;
            var mover = move();

            var failure = assertThrows(SecretStoreException.class, mover::resume);

            assertAll(
                    () -> assertTrue(failure.getMessage().contains("the copies were removed from 'os'"),
                            failure.getMessage()),
                    () -> assertEquals(Optional.of(Store.FILE), choice.recorded()),
                    () -> assertTrue(osStore.secrets.isEmpty(), osStore.secrets.keySet().toString()),
                    () -> assertEquals(List.of(GITHUB, SONAR), fileStore.entries()),
                    () -> assertTrue(keyIn(fileStore).read().isPresent()),
                    () -> assertFalse(Files.exists(recordFile())),
                    () -> assertTrue(movedEvents().isEmpty()));
        }

        @Test
        @DisplayName("a target recorded when the copy fails keeps the copies and the old store, and the record is "
                + "deleted")
        void targetRecordedWhenTheCopyFails() throws Exception {
            machineOn(Store.FILE);
            osStore.failPutOf = SONAR.account();
            var mover = move(step -> {
                if (step == Step.RECORDED) {
                    rewriteMachineConfiguration("{\"format_version\":1,\"revision\":9,\"max_build_slots\":2,"
                            + "\"lsp_idle_timeout_seconds\":900,\"credentials\":{\"store\":\"os\"}}");
                }
            });

            var failure = assertThrows(SecretStoreException.class, () -> mover.move(Store.OS));

            assertAll(
                    () -> assertEquals(SecretStoreException.Reason.FAILED, failure.reason()),
                    () -> assertTrue(failure.getMessage().contains(
                                    "'os' is recorded, so the copies stay in 'os', 'file' keeps every entry and the "
                                            + "record is deleted"),
                            failure.getMessage()),
                    () -> assertNoSecretIn(failure),
                    () -> assertEquals(Optional.of(GITHUB_SECRET), osStore.get(GITHUB)),
                    () -> assertEquals(List.of(GITHUB, SONAR), fileStore.entries()),
                    () -> assertTrue(keyIn(fileStore).read().isPresent()),
                    () -> assertFalse(Files.exists(recordFile())),
                    () -> assertTrue(movedEvents().isEmpty()));
        }

        @Test
        @DisplayName("a machine configuration that cannot be read keeps the copies in the target and the record, and "
                + "says so")
        void unreadableMachineConfiguration() throws Exception {
            machineOn(Store.FILE);
            osStore.failPutOf = SONAR.account();
            var mover = move(step -> {
                if (step == Step.RECORDED) {
                    rewriteMachineConfiguration("{\"format_version\":1,\"revision\":\"one\"}");
                }
            });

            var failure = assertThrows(SecretStoreException.class, () -> mover.move(Store.OS));

            assertAll(
                    () -> assertEquals(SecretStoreException.Reason.FAILED, failure.reason()),
                    () -> assertTrue(failure.getMessage().contains(
                                    "the recorded store cannot be read, so the copies stay in 'os'; the record is kept"),
                            failure.getMessage()),
                    () -> assertFalse(failure.getMessage().contains("unknown"), failure.getMessage()),
                    () -> assertNoSecretIn(failure),
                    () -> assertEquals(1, failure.getCause().getSuppressed().length),
                    () -> assertEquals(Optional.of(GITHUB_SECRET), osStore.get(GITHUB)),
                    () -> assertEquals(List.of(GITHUB, SONAR), fileStore.entries()),
                    () -> assertTrue(keyIn(fileStore).read().isPresent()),
                    () -> assertTrue(Files.exists(recordFile())),
                    () -> assertTrue(movedEvents().isEmpty()),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.CREDENTIAL_STORE_MOVE_FAILED.resolveIdentifierString()));
        }

        @Test
        @DisplayName("a record that leaves phase copying under the move fails it before the switch")
        void wrongPhase() throws Exception {
            machineOn(Store.OS);
            var mover = move(step -> {
                if (step == Step.ENTRIES_COPIED) {
                    rewriteRecord(new CredentialStoreMoveRecord(Store.OS, Store.FILE, STARTED, Phase.SWITCHED, false));
                }
            });

            var failure = assertThrows(IllegalStateException.class, () -> mover.move(Store.FILE));

            assertAll(
                    () -> assertTrue(failure.getMessage().contains("not in phase 'copying'"), failure.getMessage()),
                    () -> assertEquals(Optional.of(Store.OS), choice.recorded()),
                    () -> assertEquals(Optional.of(GITHUB_SECRET), osStore.get(GITHUB)),
                    () -> assertTrue(movedEvents().isEmpty()));
        }
    }

    private void rewriteRecord(CredentialStoreMoveRecord moveRecord) {
        try {
            write(recordFile(), moveRecord.write());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void rewriteMachineConfiguration(String document) {
        try {
            write(base.resolve(CredentialStoreChoice.FILE_NAME), document.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Nested
    @DisplayName("while the record of a move exists only the move records a store")
    class OnlyTheMoveRecords {

        @Test
        @DisplayName("the aspect is refused with aspect_owned while the move runs, and the move completes")
        void aspectRefusedWhileTheMoveRuns() throws Exception {
            var key = machineOn(Store.OS);
            var refusals = new ArrayList<CredentialStoreChoice.Refusal>();
            var mover = move(step -> {
                if (step == Step.KEY_COPIED) {
                    try {
                        choice.record(locks, USER, Store.FILE);
                    } catch (CredentialStoreChoice.Refusal refusal) {
                        refusals.add(refusal);
                    }
                }
            });

            mover.move(Store.FILE);

            assertAll(
                    () -> assertEquals(1, refusals.size()),
                    () -> assertEquals(CredentialStoreChoice.Refusal.Reason.ASPECT_OWNED, refusals.getFirst().reason()),
                    () -> assertTrue(refusals.getFirst().getMessage().contains("only the move records"),
                            refusals.getFirst().getMessage()),
                    () -> assertMoved(Store.OS, Store.FILE, fileStore, osStore, key));
        }

        @Test
        @DisplayName("the aspect from another thread waits for the running move and is then refused for the old store")
        void aspectFromAnotherThreadWaitsForTheMove() throws Exception {
            var key = machineOn(Store.OS);
            var refusal = new AtomicReference<CredentialStoreChoice.Refusal>();
            var aspect = new Thread(() -> {
                try {
                    choice.record(locks, USER, Store.OS);
                } catch (CredentialStoreChoice.Refusal refused) {
                    refusal.set(refused);
                }
            });
            var waitedDuringTheMove = new AtomicBoolean();
            var mover = move(step -> {
                if (step == Step.KEY_COPIED) {
                    aspect.start();
                    waitedDuringTheMove.set(awaitWaiting(aspect));
                }
            });

            mover.move(Store.FILE);
            aspect.join(TimeUnit.SECONDS.toMillis(10));

            assertAll(
                    () -> assertTrue(waitedDuringTheMove.get()),
                    () -> assertFalse(aspect.isAlive()),
                    () -> assertEquals(CredentialStoreChoice.Refusal.Reason.ASPECT_OWNED, refusal.get().reason()),
                    () -> assertTrue(refusal.get().getMessage().contains("a change to 'os' is a move"),
                            refusal.get().getMessage()),
                    () -> assertMoved(Store.OS, Store.FILE, fileStore, osStore, key));
        }

        /** @return whether the thread waits, for the credentials lock the move holds, within ten seconds */
        private static boolean awaitWaiting(Thread thread) {
            var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline) {
                if (thread.getState() == Thread.State.WAITING) {
                    return true;
                }
                Thread.onSpinWait();
            }
            return false;
        }

        @Test
        @DisplayName("a record of a cut move refuses the aspect until the resume ends the move")
        void aspectRefusedUntilTheResume() throws Exception {
            machineOn(Store.OS);
            var stopped = move(stopAfter(Step.ENTRIES_COPIED));
            assertThrows(Stop.class, () -> stopped.move(Store.FILE));

            var refusal = assertThrows(CredentialStoreChoice.Refusal.class,
                    () -> choice.record(locks, USER, Store.FILE));
            var recordedWhileOpen = choice.recorded();
            move().resume();
            choice.record(locks, USER, Store.FILE);

            assertAll(
                    () -> assertEquals(CredentialStoreChoice.Refusal.Reason.ASPECT_OWNED, refusal.reason()),
                    () -> assertEquals(Optional.of(Store.OS), recordedWhileOpen),
                    () -> assertEquals(Optional.of(Store.FILE), choice.recorded()),
                    () -> assertFalse(Files.exists(recordFile())),
                    () -> assertEquals(Optional.of(GITHUB_SECRET), fileStore.get(GITHUB)));
        }
    }

    @Nested
    @DisplayName("a move cut by a stop is completed by the resume")
    class Resumed {

        @ParameterizedTest(name = "stopped after {0}")
        @EnumSource(Step.class)
        @DisplayName("a stop after each step leaves a record from which the resume completes the move")
        void stoppedAfter(Step stop) throws Exception {
            var key = machineOn(Store.OS);
            var stopped = move(stopAfter(stop));

            assertThrows(Stop.class, () -> stopped.move(Store.FILE));
            var moveRecord = CredentialStoreMoveRecord.read(recordFile(), Files.readAllBytes(recordFile()));
            var resumed = move().resume();

            assertAll(
                    () -> assertEquals(stop.compareTo(Step.SWITCHED) < 0 ? Phase.COPYING : Phase.SWITCHED,
                            moveRecord.phase()),
                    () -> assertEquals(Optional.of(moveRecord), resumed),
                    () -> assertMoved(Store.OS, Store.FILE, fileStore, osStore, key));
        }

        @Test
        @DisplayName("a stop between the switch and the rewrite of the record: the resume copies nothing and finishes the "
                + "deletion from the old store")
        void stoppedBetweenSwitchAndRecord() throws Exception {
            var key = machineOn(Store.FILE);
            var stopped = move(stopAfter(Step.STORE_RECORDED));
            assertThrows(Stop.class, () -> stopped.move(Store.OS));
            var moveRecord = CredentialStoreMoveRecord.read(recordFile(), Files.readAllBytes(recordFile()));
            osStore.puts.clear();

            var resumed = move().resume();

            assertAll(
                    () -> assertEquals(Phase.COPYING, moveRecord.phase()),
                    () -> assertEquals(Optional.of(moveRecord), resumed),
                    () -> assertTrue(osStore.puts.isEmpty(), osStore.puts.toString()),
                    () -> assertMoved(Store.FILE, Store.OS, osStore, fileStore, key));
        }

        @Test
        @DisplayName("a target that refuses a copy after a stop between the switch and the rewrite of the record keeps "
                + "every entry, since the recorded store is never emptied")
        void recordedTargetNeverEmptied() throws Exception {
            var key = machineOn(Store.FILE);
            var stopped = move(stopAfter(Step.STORE_RECORDED));
            assertThrows(Stop.class, () -> stopped.move(Store.OS));
            osStore.failPutOf = SONAR.account();

            move().resume();

            assertAll(
                    () -> assertEquals(Optional.of(Store.OS), choice.recorded()),
                    () -> assertEquals(Optional.of(GITHUB_SECRET), osStore.get(GITHUB)),
                    () -> assertEquals(Optional.of(SONAR_SECRET), osStore.get(SONAR)),
                    () -> assertArrayEquals(key.getEncoded(), keyIn(osStore).read().orElseThrow().getEncoded()),
                    () -> assertTrue(fileStore.entries().isEmpty(), fileStore.entries().toString()),
                    () -> assertFalse(Files.exists(recordFile())),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN,
                            WARN.CREDENTIAL_STORE_MOVE_FAILED.resolveIdentifierString()));
        }

        @Test
        @DisplayName("a resume before the switch whose copy fails again leaves the old store recorded and holding "
                + "every entry")
        void failingResume() throws Exception {
            machineOn(Store.FILE);
            var stopped = move(stopAfter(Step.ENTRIES_COPIED));
            assertThrows(Stop.class, () -> stopped.move(Store.OS));
            osStore.failPutOf = SONAR.account();
            var mover = move();

            var failure = assertThrows(SecretStoreException.class, mover::resume);

            assertAll(
                    () -> assertEquals(SecretStoreException.Reason.FAILED, failure.reason()),
                    () -> assertNoSecretIn(failure),
                    () -> assertEquals(Optional.of(Store.FILE), choice.recorded()),
                    () -> assertTrue(osStore.secrets.isEmpty(), osStore.secrets.keySet().toString()),
                    () -> assertEquals(List.of(GITHUB, SONAR), fileStore.entries()),
                    () -> assertTrue(keyIn(fileStore).read().isPresent()),
                    () -> assertFalse(Files.exists(recordFile())),
                    () -> assertTrue(movedEvents().isEmpty()));
        }

        @Test
        @DisplayName("a record in phase switched while the old store is recorded is refused, deletes nothing and is "
                + "kept")
        void switchedRecordWithTheOldStoreRecorded() throws Exception {
            var key = machineOn(Store.OS);
            write(recordFile(),
                    new CredentialStoreMoveRecord(Store.OS, Store.FILE, STARTED, Phase.SWITCHED, false).write());
            var mover = move();

            var failure = assertThrows(IllegalStateException.class, mover::resume);

            assertAll(
                    () -> assertTrue(failure.getMessage().contains("credentials.store names 'os', not 'file'"),
                            failure.getMessage()),
                    () -> assertEquals(Optional.of(Store.OS), choice.recorded()),
                    () -> assertEquals(Optional.of(GITHUB_SECRET), osStore.get(GITHUB)),
                    () -> assertEquals(Optional.of(SONAR_SECRET), osStore.get(SONAR)),
                    () -> assertArrayEquals(key.getEncoded(), keyIn(osStore).read().orElseThrow().getEncoded()),
                    () -> assertTrue(Files.exists(recordFile())),
                    () -> assertTrue(movedEvents().isEmpty()));
        }

        @Test
        @DisplayName("an audit sink that fails once leaves the record; the resume finishes the move and audits it")
        void auditRetriedAfterAFailingSink() throws Exception {
            var key = machineOn(Store.OS);
            var failOnce = new AtomicBoolean(true);
            AuditSink sink = event -> {
                if (failOnce.getAndSet(false)) {
                    throw new UncheckedIOException(new IOException("the audit log refuses the record"));
                }
                events.add(event);
            };
            var failing = new CredentialStoreMove(base, choice, locks, sink, USER,
                    Clock.fixed(STARTED, ZoneOffset.UTC), CredentialStoreMove.StepHook.NONE);
            assertThrows(UncheckedIOException.class, () -> failing.move(Store.FILE));
            var moveRecord = CredentialStoreMoveRecord.read(recordFile(), Files.readAllBytes(recordFile()));
            var eventsBeforeTheResume = movedEvents().size();

            new CredentialStoreMove(base, choice, locks, sink, USER, Clock.fixed(STARTED, ZoneOffset.UTC),
                    CredentialStoreMove.StepHook.NONE).resume();

            assertAll(
                    () -> assertEquals(Phase.SWITCHED, moveRecord.phase()),
                    () -> assertEquals(0, eventsBeforeTheResume),
                    () -> assertMoved(Store.OS, Store.FILE, fileStore, osStore, key));
        }

        @Test
        @DisplayName("without a record the resume does nothing")
        void noRecord() throws Exception {
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
        void intoTheRecordedStore() throws Exception {
            machineOn(Store.OS);
            var mover = move();

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> mover.move(Store.OS)),
                    () -> assertFalse(Files.exists(recordFile())),
                    () -> assertTrue(fileStore.entries().isEmpty()));
        }

        @Test
        @DisplayName("a target that holds a Token Lane entry already is refused before the record is written, and "
                + "nothing is written or deleted in either store")
        void targetNotEmpty() throws Exception {
            var key = machineOn(Store.OS);
            fileStore.put(GITHUB, "a secret the target held before the move");
            var mover = move();

            var failure = assertThrows(SecretStoreException.class, () -> mover.move(Store.FILE));

            assertAll(
                    () -> assertEquals(SecretStoreException.Reason.FAILED, failure.reason()),
                    () -> assertTrue(failure.getMessage().contains("'file' holds Token Lane entries already (1)"),
                            failure.getMessage()),
                    () -> assertNoSecretIn(failure),
                    () -> assertFalse(failure.getMessage().contains("a secret the target held"), failure.getMessage()),
                    () -> assertFalse(Files.exists(recordFile())),
                    () -> assertEquals(Optional.of(Store.OS), choice.recorded()),
                    () -> assertEquals(Optional.of("a secret the target held before the move"), fileStore.get(GITHUB)),
                    () -> assertEquals(List.of(GITHUB), fileStore.entries()),
                    () -> assertEquals(Optional.of(GITHUB_SECRET), osStore.get(GITHUB)),
                    () -> assertEquals(Optional.of(SONAR_SECRET), osStore.get(SONAR)),
                    () -> assertArrayEquals(key.getEncoded(), keyIn(osStore).read().orElseThrow().getEncoded()),
                    () -> assertTrue(movedEvents().isEmpty()));
        }

        @Test
        @DisplayName("while a move is open another one is refused")
        void moveOpen() throws Exception {
            machineOn(Store.OS);
            var open = new CredentialStoreMoveRecord(Store.OS, Store.FILE, STARTED, Phase.COPYING, false);
            write(recordFile(), open.write());
            var mover = move();

            assertAll(
                    () -> assertThrows(IllegalStateException.class, () -> mover.move(Store.FILE)),
                    () -> assertTrue(fileStore.entries().isEmpty()),
                    () -> assertEquals(Optional.of(Store.OS), choice.recorded()));
        }
    }

    private SecretStore storeOf(Store store) {
        return store == Store.OS ? osStore : fileStore;
    }

    private static Store other(Store store) {
        return store == Store.OS ? Store.FILE : Store.OS;
    }

    /** @return the bytes of every HMAC sidecar of the machine, in a fixed order */
    private List<byte[]> sidecars() throws IOException {
        return List.of(Files.readAllBytes(enrolments.sidecar(PROJECT)), Files.readAllBytes(harnessesSidecar()),
                Files.readAllBytes(AuditHead.headFile(base)));
    }

    private static void assertSameSidecars(List<byte[]> expected, List<byte[]> actual) {
        assertEquals(expected.size(), actual.size());
        for (var i = 0; i < expected.size(); i++) {
            assertArrayEquals(expected.get(i), actual.get(i), "sidecar " + i);
        }
    }

    @Nested
    @DisplayName("a target that holds an integrity key of its own")
    class TargetHoldsAKey {

        @ParameterizedTest(name = "into the {0} store")
        @EnumSource(Store.class)
        @DisplayName("another key than the old store's is refused before the record is written, and the key and the "
                + "sidecars signed under it stay untouched")
        void anotherKeyRefused(Store to) throws Exception {
            var from = other(to);
            var oldKey = machineOn(from);
            var target = storeOf(to);
            var targetKey = otherKey();
            keyIn(target).write(targetKey);
            signAll(targetKey);
            var signed = sidecars();
            osStore.puts.clear();
            var mover = move();

            var failure = assertThrows(SecretStoreException.class, () -> mover.move(to));

            assertAll(
                    () -> assertEquals(SecretStoreException.Reason.FAILED, failure.reason()),
                    () -> assertTrue(failure.getMessage().contains("holds another integrity key than"),
                            failure.getMessage()),
                    () -> assertTrue(failure.getMessage().contains("nothing was written or deleted"),
                            failure.getMessage()),
                    () -> assertNoSecretIn(failure),
                    () -> assertFalse(Files.exists(recordFile())),
                    () -> assertEquals(Optional.of(from), choice.recorded()),
                    () -> assertArrayEquals(targetKey.getEncoded(), keyIn(target).read().orElseThrow().getEncoded()),
                    () -> assertArrayEquals(oldKey.getEncoded(),
                            keyIn(storeOf(from)).read().orElseThrow().getEncoded()),
                    () -> assertSameSidecars(signed, sidecars()),
                    () -> assertTrue(target.entries().stream().allMatch(
                            account -> EnrolmentKey.ACCOUNT.equals(account.account())), target.entries().toString()),
                    () -> assertEquals(List.of(GITHUB, SONAR), storeOf(from).entries().stream()
                            .filter(account -> !EnrolmentKey.ACCOUNT.equals(account.account())).toList()),
                    () -> assertTrue(osStore.puts.isEmpty(), osStore.puts.toString()),
                    () -> assertTrue(movedEvents().isEmpty()));
        }

        @Test
        @DisplayName("the same key as the old store's is no conflict: the move completes and does not write the key "
                + "again")
        void sameKeyMoves() throws Exception {
            var key = machineOn(Store.FILE);
            keyIn(osStore).write(key);
            osStore.puts.clear();

            move().move(Store.OS);

            assertAll(
                    () -> assertMoved(Store.FILE, Store.OS, osStore, fileStore, key),
                    () -> assertEquals(List.of(GITHUB.account(), SONAR.account()), osStore.puts));
        }

        @ParameterizedTest(name = "into the {0} store")
        @EnumSource(Store.class)
        @DisplayName("the same key as the old store's stays in the target when the move fails after the copy of the key")
        void sameKeyKeptByTheCleanUp(Store to) throws Exception {
            var from = other(to);
            var key = machineOn(from);
            var target = storeOf(to);
            keyIn(target).write(key);
            writeHead(otherKey());
            var mover = move();

            var failure = assertThrows(SecretStoreException.class, () -> mover.move(to));

            assertAll(
                    () -> assertTrue(failure.getMessage().contains("the copies were removed from '%s'".formatted(
                            to.value())), failure.getMessage()),
                    () -> assertEquals(Optional.of(from), choice.recorded()),
                    () -> assertArrayEquals(key.getEncoded(), keyIn(target).read().orElseThrow().getEncoded()),
                    () -> assertArrayEquals(key.getEncoded(), keyIn(storeOf(from)).read().orElseThrow().getEncoded()),
                    () -> assertTrue(target.get(GITHUB).isEmpty()),
                    () -> assertTrue(target.get(SONAR).isEmpty()),
                    () -> assertEquals(Optional.of(GITHUB_SECRET), storeOf(from).get(GITHUB)),
                    () -> assertFalse(Files.exists(recordFile())),
                    () -> assertTrue(movedEvents().isEmpty()));
        }

        @Test
        @DisplayName("the same key as the old store's stays in the target when a resume removes the copies and fails "
                + "again")
        void sameKeyKeptByTheResume() throws Exception {
            var key = machineOn(Store.FILE);
            keyIn(osStore).write(key);
            var stopped = move(stopAfter(Step.KEY_COPIED));
            assertThrows(Stop.class, () -> stopped.move(Store.OS));
            var moveRecord = CredentialStoreMoveRecord.read(recordFile(), Files.readAllBytes(recordFile()));
            osStore.failPutOf = SONAR.account();
            var mover = move();

            var failure = assertThrows(SecretStoreException.class, mover::resume);

            assertAll(
                    () -> assertTrue(moveRecord.targetHeldKey()),
                    () -> assertTrue(failure.getMessage().contains("the copies were removed from 'os'"),
                            failure.getMessage()),
                    () -> assertEquals(Optional.of(Store.FILE), choice.recorded()),
                    () -> assertArrayEquals(key.getEncoded(), keyIn(osStore).read().orElseThrow().getEncoded()),
                    () -> assertTrue(osStore.get(GITHUB).isEmpty()),
                    () -> assertEquals(List.of(GITHUB, SONAR), fileStore.entries()),
                    () -> assertArrayEquals(key.getEncoded(), keyIn(fileStore).read().orElseThrow().getEncoded()),
                    () -> assertFalse(Files.exists(recordFile())));
        }

        @Test
        @DisplayName("a key the move copied into the target is removed when a resume removes the copies and fails again")
        void copiedKeyRemovedByTheResume() throws Exception {
            machineOn(Store.FILE);
            var stopped = move(stopAfter(Step.KEY_COPIED));
            assertThrows(Stop.class, () -> stopped.move(Store.OS));
            var moveRecord = CredentialStoreMoveRecord.read(recordFile(), Files.readAllBytes(recordFile()));
            osStore.failPutOf = SONAR.account();
            var mover = move();

            assertThrows(SecretStoreException.class, mover::resume);

            assertAll(
                    () -> assertFalse(moveRecord.targetHeldKey()),
                    () -> assertTrue(osStore.secrets.isEmpty(), osStore.secrets.keySet().toString()),
                    () -> assertTrue(keyIn(fileStore).read().isPresent()),
                    () -> assertFalse(Files.exists(recordFile())));
        }

        @Test
        @DisplayName("a key in the target while the old store holds none is no conflict and stays")
        void keyWhileTheOldStoreHoldsNone() {
            choice.record(locks, USER, Store.OS);
            osStore.put(GITHUB, GITHUB_SECRET);
            var targetKey = otherKey();
            keyIn(fileStore).write(targetKey);

            move().move(Store.FILE);

            assertAll(
                    () -> assertEquals(Optional.of(Store.FILE), choice.recorded()),
                    () -> assertEquals(Optional.of(GITHUB_SECRET), fileStore.get(GITHUB)),
                    () -> assertArrayEquals(targetKey.getEncoded(),
                            keyIn(fileStore).read().orElseThrow().getEncoded()),
                    () -> assertTrue(osStore.entries().isEmpty(), osStore.entries().toString()),
                    () -> assertFalse(Files.exists(recordFile())));
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

    /**
     * An OS store in memory that records its writes and can fail one write or one deletion, change one read, answer
     * the read of an entry it holds with another value, or lose one entry it still lists.
     */
    private static final class FakeOsStore implements SecretStore {

        private final Map<String, String> secrets = new TreeMap<>();
        private final List<String> puts = new ArrayList<>();
        private final Map<String, String> readAs = new TreeMap<>();
        private String failPutOf;
        private String failDeleteOf;
        private String changedReadOf;
        private String vanishedOf;

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
            if (account.account().equals(vanishedOf)) {
                return Optional.empty();
            }
            if (readAs.containsKey(account.account()) && secrets.containsKey(account.account())) {
                return Optional.of(readAs.get(account.account()));
            }
            var secret = Optional.ofNullable(secrets.get(account.account()));
            return account.account().equals(changedReadOf) ? secret.map(value -> value + "-changed") : secret;
        }

        @Override
        public boolean delete(CredentialAccount account) {
            if (account.account().equals(failDeleteOf)) {
                throw new SecretStoreException(SecretStoreException.Reason.FAILED,
                        "the fake store refuses to delete " + account.account());
            }
            return secrets.remove(account.account()) != null;
        }

        @Override
        public List<CredentialAccount> entries() {
            return SecretStore.ordered(secrets.keySet().stream().map(CredentialAccount::parse).toList());
        }
    }
}
