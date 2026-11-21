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

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
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
import de.planmarshall.core.store.TransactionId;
import de.planmarshall.runtime.audit.AuditHead;
import de.planmarshall.runtime.credentials.CredentialStoreChoice.Store;
import de.planmarshall.runtime.credentials.CredentialStoreMove.Step;
import de.planmarshall.runtime.credentials.CredentialStoreMoveRecord.Operation;
import de.planmarshall.runtime.credentials.CredentialStoreMoveRecord.Phase;
import de.planmarshall.runtime.enrolment.EnrolmentHmac;
import de.planmarshall.runtime.enrolment.EnrolmentKey;
import de.planmarshall.runtime.enrolment.HarnessesSidecarCheck;
import de.planmarshall.runtime.lock.FileLockManager;
import de.planmarshall.runtime.log.RuntimeLogMessages.INFO;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;
import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * The move between the credential stores (PM-CRED-2, PM-CRED-3, PM-SEC-5), against a fake OS store, so it runs on
 * every OS: every entry is copied, read back and deleted from the old store, the integrity key last after the
 * sidecars verified under it, the store is switched once and the move is audited; a failure before the switch leaves
 * the old store recorded and the target without copies; a move stopped after any step is completed by
 * {@link CredentialStoreMove#resume()}.
 * <p>
 * The project id and the two entry secrets are generated (the {@code ghp-} and {@code squ-} prefixes are the wire shapes
 * of the providers); the store names, the record phases, the audit fields and the fixed instant are the contracts.
 */
@EnableGeneratorController
@EnableTestLogger
@DisplayName("CredentialStoreMove: every entry between the OS store and the file store")
class CredentialStoreMoveTest {

    private static final String SERVICE = "de.planmarshall.mcp";
    private static final String USER = System.getProperty("user.name");
    private static final String OWNER_ONLY = "rw-------";
    private static final CredentialAccount GITHUB = CredentialAccount.global("github");
    private static final Instant STARTED = Instant.parse("2026-10-17T15:00:00Z");
    private static final TransactionId TRANSACTION = new TransactionId("018f0000-0000-7000-8000-000000000001");
    private static final byte[] HARNESSES = """
            {"format_version":1,"harnesses":{}}""".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path base;

    private final SecureRandom random = new SecureRandom();
    private final List<AuditEvent> events = new ArrayList<>();
    private final FakeOsStore osStore = new FakeOsStore();

    /** The keyrings of a macOS machine whose Keychain is the fake OS store. */
    private final CredentialStoreChoice.Keyrings keyrings = keyrings();

    private CredentialStoreChoice.Keyrings keyrings() {
        CredentialStoreChoice.Keyrings mock = createMock(CredentialStoreChoice.Keyrings.class);
        expect(mock.keychain(anyObject(String.class))).andReturn(osStore).anyTimes();
        expect(mock.secretService(anyObject(Path.class), anyObject(String.class)))
                .andReturn(Optional.empty()).anyTimes();
        replay(mock);
        return mock;
    }
    private FileLockManager locks;
    private CredentialStoreChoice choice;
    private FileSecretStore fileStore;
    private Path stateDirectory;
    private EnrolmentStore enrolments;
    private String project;
    private CredentialAccount sonar;
    private String githubSecret;
    private String sonarSecret;
    private String heldSecret;

    @BeforeEach
    void setUp() throws IOException {
        project = RuntimeGenerators.projectIds().next();
        sonar = CredentialAccount.project(project, "sonar");
        githubSecret = "ghp-" + Generators.letterStrings(8, 24).next();
        sonarSecret = "squ-" + Generators.letterStrings(8, 24).next();
        heldSecret = Generators.letterStrings(8, 30).next();
        stateDirectory = Files.createDirectories(base.resolve("state"));
        enrolments = new EnrolmentStore(base);
        Files.createDirectories(enrolments.directory());
        Files.createDirectories(AuditHead.headFile(base).getParent());
        AuditSink sink = events::add;
        locks = new FileLockManager(sink);
        choice = new CredentialStoreChoice(base, SERVICE, MachinePaths.Os.MACOS, Map.of(), keyrings);
        fileStore = new FileSecretStore(base.resolve("credentials"));
    }

    /** Records the store, fills it with two entries and the key, and signs every sidecar under that key. */
    private SecretKey machineOn(Store store) throws IOException {
        choice.record(locks, USER, store);
        var secrets = store == Store.OS ? osStore : fileStore;
        secrets.put(GITHUB, githubSecret);
        secrets.put(sonar, sonarSecret);
        var key = keyIn(secrets).readOrCreate();
        signAll(key);
        return key;
    }

    private EnrolmentKey keyIn(SecretStore store) {
        return new EnrolmentKey(store, stateDirectory, USER, random);
    }

    private void signAll(SecretKey key) throws IOException {
        signed(enrolments.file(project), enrolments.sidecar(project),
                ("{\"project_id\":\"" + project + "\"}").getBytes(StandardCharsets.UTF_8), key);
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
        var unsigned = new AuditHeadRecord(AuditHeadRecord.CURRENT_LOG_FILE, 1,
                TransactionId.generate(Clock.fixed(STARTED, ZoneOffset.UTC)).value(),
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
                () -> assertMovedState(to, target, old, key),
                () -> assertEquals(1, movedEvents().size(), "the move is audited exactly once"),
                () -> assertEquals(Map.of(CredentialStoreMove.DETAIL_FROM, from.value(),
                                CredentialStoreMove.DETAIL_TO, to.value(), CredentialStoreMove.DETAIL_ENTRIES, 2L),
                        withoutTransactionId(movedEvents().getFirst()),
                        "the audit record names both stores and two entries"),
                () -> assertTransactionIdAt(STARTED, movedEvents().getFirst().details()
                        .get(AuditEvent.DETAIL_TRANSACTION_ID)),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                        INFO.CREDENTIAL_STORE_MOVED.resolveIdentifierString()));
    }

    /** Asserts the stores and the record of a completed move, without its audit record. */
    private void assertMovedState(Store to, SecretStore target, SecretStore old, SecretKey key) {
        assertAll(
                () -> assertEquals(Optional.of(to), choice.recorded(), "the moved store is the one recorded"),
                () -> assertEquals(Optional.of(githubSecret), target.get(GITHUB),
                        "the github entry moved into the target store"),
                () -> assertEquals(Optional.of(sonarSecret), target.get(sonar),
                        "the sonar entry moved into the target store"),
                () -> assertArrayEquals(key.getEncoded(), keyIn(target).read().orElseThrow().getEncoded(),
                        "the integrity key moved with its bytes unchanged"),
                () -> assertEquals(List.of(), old.entries(), "the old store holds no entry after the move"),
                () -> assertTrue(keyIn(old).read().isEmpty(), "the old store no longer holds the key"),
                () -> assertFalse(Files.exists(recordFile()), "the record is gone once the move ends"),
                () -> assertTrue(movedEvents().stream().allMatch(event -> CredentialStoreMove.EVENT.equals(
                        event.event())), "every audited record is a store-move event"));
    }

    /** @return the details of the record without its transaction id */
    private static Map<String, Object> withoutTransactionId(AuditEvent event) {
        var details = new HashMap<>(event.details());
        details.remove(AuditEvent.DETAIL_TRANSACTION_ID);
        return details;
    }

    /** Asserts that a transaction id is the one written form and carries the instant of the given clock. */
    static void assertTransactionIdAt(Instant instant, Object value) {
        var id = new TransactionId(String.valueOf(value));
        assertEquals(instant.toEpochMilli(),
                Long.parseLong(id.value().substring(0, 8) + id.value().substring(9, 13), 16),
                "the transaction id carries the clock's instant");
    }

    /** A reader of the audit log: it drops a record whose transaction id it has seen, as the specification states. */
    private static List<AuditEvent> distinctEvents(List<AuditEvent> events) {
        var seen = new HashSet<Object>();
        return events.stream().filter(event -> seen.add(event.details().get(AuditEvent.DETAIL_TRANSACTION_ID)))
                .toList();
    }

    /** Asserts the end state of a move from the OS store into the file store that failed before the switch. */
    private void assertNothingSwitched(SecretStoreException failure) {
        assertAll(
                () -> assertEquals(SecretStoreException.Reason.FAILED, failure.reason(),
                        "the failure reason is FAILED"),
                () -> assertTrue(failure.getMessage().contains(
                                "failed before the switch, the copies were removed from 'file' and 'os' stays recorded"),
                        "the failure says the copies were removed"),
                () -> assertNoSecretIn(failure),
                () -> assertEquals(Optional.of(Store.OS), choice.recorded(),
                        "the old store stays the recorded one"),
                () -> assertEquals(Optional.of(githubSecret), osStore.get(GITHUB),
                        "the github entry stays in the old store"),
                () -> assertEquals(Optional.of(sonarSecret), osStore.get(sonar),
                        "the sonar entry stays in the old store"),
                () -> assertTrue(keyIn(osStore).read().isPresent(), "the old store keeps the integrity key"),
                () -> assertEquals(List.of(), fileStore.entries(), "no entry is left in the target store"),
                () -> assertFalse(Files.exists(keyIn(fileStore).keyFile()),
                        "no key file is left in the target store"),
                () -> assertFalse(Files.exists(recordFile()), "the failed move leaves no record behind"),
                () -> assertTrue(movedEvents().isEmpty(), "no move is audited when the switch fails"),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                        WARN.CREDENTIAL_STORE_MOVE_FAILED.resolveIdentifierString()));
    }

    private void assertNoSecretIn(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            var message = String.valueOf(cause.getMessage());
            assertFalse(message.contains(githubSecret) || message.contains(sonarSecret),
                    "no message of the failure holds a secret");
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
                    () -> assertFalse(Files.exists(keyIn(fileStore).keyFile()),
                            "the key file is gone from the old store"));
        }

        @Test
        @DisplayName("the integrity key is copied last, after every Token Lane entry")
        void keyLast() throws Exception {
            machineOn(Store.FILE);

            move().move(Store.OS);

            assertEquals(List.of(GITHUB.account(), sonar.account(), EnrolmentKey.ACCOUNT), osStore.puts,
                    "the key is copied after both entries");
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
                case ENROLMENT -> signed(enrolments.file(project), enrolments.sidecar(project),
                        ("{\"project_id\":\"" + project + "\",\"pilot\":true}")
                                .getBytes(StandardCharsets.UTF_8), other);
                case HARNESSES -> signed(harnesses(), harnessesSidecar(), HARNESSES, other);
                case AUDIT_HEAD -> writeHead(other);
            }
            var mover = move();

            var failure = assertThrows(SecretStoreException.class, () -> mover.move(Store.FILE),
                    "a sidecar that fails under the moved key is refused");

            assertNothingSwitched(failure);
        }

        @Test
        @DisplayName("a copy the target refuses")
        void failingCopy() throws Exception {
            machineOn(Store.FILE);
            osStore.failPutOf = sonar.account();
            var mover = move();

            var failure = assertThrows(SecretStoreException.class, () -> mover.move(Store.OS),
                    "a copy the target refuses fails the move");

            assertAll(
                    () -> assertEquals(SecretStoreException.Reason.FAILED, failure.reason(),
                            "the refused copy fails with reason FAILED"),
                    () -> assertNoSecretIn(failure),
                    () -> assertEquals(Optional.of(Store.FILE), choice.recorded(),
                            "the old store stays the recorded one"),
                    () -> assertEquals(Set.of(), osStore.secrets.keySet(), "the target keeps no copy of an entry"),
                    () -> assertEquals(List.of(GITHUB, sonar), fileStore.entries(),
                            "the old store keeps both of its entries"),
                    () -> assertTrue(keyIn(fileStore).read().isPresent(),
                            "the old store keeps the integrity key"),
                    () -> assertFalse(Files.exists(recordFile()), "the failed move leaves no record behind"));
        }

        @Test
        @DisplayName("an entry that reads back with another value")
        void failingReadBack() throws Exception {
            machineOn(Store.FILE);
            osStore.changedReadOf = GITHUB.account();
            var mover = move();

            var failure = assertThrows(SecretStoreException.class, () -> mover.move(Store.OS),
                    "an entry that reads back changed fails the move");

            assertAll(
                    () -> assertEquals(SecretStoreException.Reason.FAILED, failure.reason(),
                            "the changed read back fails with reason FAILED"),
                    () -> assertTrue(failure.getMessage().contains("does not read back"),
                            "the failure says the entry does not read back"),
                    () -> assertNoSecretIn(failure),
                    () -> assertEquals(Optional.of(Store.FILE), choice.recorded(),
                            "the old store stays the recorded one"),
                    () -> assertEquals(Set.of(), osStore.secrets.keySet(), "the target keeps no copy of an entry"),
                    () -> assertEquals(List.of(GITHUB, sonar), fileStore.entries(),
                            "the old store keeps both of its entries"),
                    () -> assertFalse(Files.exists(recordFile()), "the failed move leaves no record behind"));
        }

        @Test
        @DisplayName("an integrity key that reads back as another key")
        void failingKeyReadBack() throws Exception {
            machineOn(Store.FILE);
            osStore.readAs.put(EnrolmentKey.ACCOUNT,
                    Base64.getUrlEncoder().withoutPadding().encodeToString(otherKey().getEncoded()));
            var mover = move();

            var failure = assertThrows(SecretStoreException.class, () -> mover.move(Store.OS),
                    "a key that reads back as another fails the move");

            assertAll(
                    () -> assertEquals(SecretStoreException.Reason.FAILED, failure.reason(),
                            "the other key read back fails with FAILED"),
                    () -> assertTrue(failure.getMessage().contains("integrity key does not read back"),
                            "the failure says the key does not read back"),
                    () -> assertEquals(Optional.of(Store.FILE), choice.recorded(),
                            "the old store stays the recorded one"),
                    () -> assertEquals(Set.of(), osStore.secrets.keySet(), "the target keeps no copy of an entry"),
                    () -> assertEquals(List.of(GITHUB, sonar), fileStore.entries(),
                            "the old store keeps both of its entries"),
                    () -> assertTrue(keyIn(fileStore).read().isPresent(),
                            "the old store still holds the integrity key"),
                    () -> assertFalse(Files.exists(recordFile()), "no record is left after the failed switch"));
        }

        @Test
        @DisplayName("an entry the old store lists and no longer holds")
        void vanishedEntry() throws Exception {
            machineOn(Store.OS);
            osStore.vanishedOf = sonar.account();
            var mover = move();

            var failure = assertThrows(SecretStoreException.class, () -> mover.move(Store.FILE),
                    "an entry the old store no longer holds fails");

            assertAll(
                    () -> assertEquals(SecretStoreException.Reason.FAILED, failure.reason(),
                            "the vanished entry fails with reason FAILED"),
                    () -> assertTrue(failure.getMessage().contains("vanished"),
                            "the failure says the entry vanished"),
                    () -> assertNoSecretIn(failure),
                    () -> assertEquals(Optional.of(Store.OS), choice.recorded(),
                            "the old store stays the recorded one"),
                    () -> assertEquals(Optional.of(githubSecret), osStore.get(GITHUB),
                            "the github entry stays in the old store"),
                    () -> assertEquals(List.of(), fileStore.entries(), "no entry is left in the target store"),
                    () -> assertFalse(Files.exists(recordFile()), "the failed move leaves no record behind"));
        }

        @Test
        @DisplayName("a clean-up the target refuses is added to the cause, and the record is kept for the next start")
        void failingCleanUp() throws Exception {
            machineOn(Store.FILE);
            osStore.failPutOf = sonar.account();
            osStore.failDeleteOf = GITHUB.account();
            var mover = move();

            var failure = assertThrows(SecretStoreException.class, () -> mover.move(Store.OS),
                    "a clean-up the target refuses keeps the record");

            assertAll(
                    () -> assertEquals(SecretStoreException.Reason.FAILED, failure.reason(),
                            "the failed clean-up fails with reason FAILED"),
                    () -> assertNoSecretIn(failure),
                    () -> assertTrue(failure.getMessage().contains(
                                    "the copies could not all be removed from 'os' and 'file' stays recorded; the "
                                            + "record is kept"),
                            "the failure says the record is kept"),
                    () -> assertEquals(1, failure.getCause().getSuppressed().length,
                            "the clean-up failure carries one suppressed error"),
                    () -> assertTrue(failure.getCause().getSuppressed()[0].getMessage().contains("refuses to delete"),
                            "the suppressed error is the refused deletion"),
                    () -> assertEquals(Optional.of(Store.FILE), choice.recorded(),
                            "the old store stays the recorded one"),
                    () -> assertEquals(List.of(GITHUB, sonar), fileStore.entries(),
                            "the old store keeps both of its entries"),
                    () -> assertTrue(keyIn(fileStore).read().isPresent(),
                            "the old store keeps the integrity key"),
                    () -> assertEquals(Optional.of(githubSecret), osStore.get(GITHUB),
                            "the target keeps the github entry it copied"),
                    () -> assertTrue(Files.exists(recordFile()), "the record is kept for the next start"));
        }

        @Test
        @DisplayName("after a clean-up the target refused, the resume removes the copies and completes the move")
        void resumeAfterAFailedCleanUpCompletes() throws Exception {
            var key = machineOn(Store.FILE);
            osStore.failPutOf = sonar.account();
            osStore.failDeleteOf = GITHUB.account();
            var failing = move();
            assertThrows(SecretStoreException.class, () -> failing.move(Store.OS),
                    "the first move fails in the clean-up");
            osStore.failPutOf = null;
            osStore.failDeleteOf = null;

            var resumed = move().resume();

            assertAll(
                    () -> assertTrue(resumed.isPresent(), "the resume reports the completed move"),
                    () -> assertMoved(Store.FILE, Store.OS, osStore, fileStore, key));
        }

        @Test
        @DisplayName("after a clean-up the target refused, a resume whose copy fails again removes the copies and "
                + "deletes the record")
        void resumeAfterAFailedCleanUpFailsCleanly() throws Exception {
            machineOn(Store.FILE);
            osStore.failPutOf = sonar.account();
            osStore.failDeleteOf = GITHUB.account();
            var failing = move();
            assertThrows(SecretStoreException.class, () -> failing.move(Store.OS),
                    "the first move fails in the clean-up");
            osStore.failDeleteOf = null;
            var mover = move();

            var failure = assertThrows(SecretStoreException.class, mover::resume,
                    "a copy that fails again fails the resume");

            assertAll(
                    () -> assertTrue(failure.getMessage().contains("the copies were removed from 'os'"),
                            "the failure says the copies were removed"),
                    () -> assertEquals(Optional.of(Store.FILE), choice.recorded(),
                            "the old store stays the recorded one"),
                    () -> assertEquals(Set.of(), osStore.secrets.keySet(), "the target keeps no copy of an entry"),
                    () -> assertEquals(List.of(GITHUB, sonar), fileStore.entries(),
                            "the old store keeps both of its entries"),
                    () -> assertTrue(keyIn(fileStore).read().isPresent(),
                            "the old store keeps the integrity key"),
                    () -> assertFalse(Files.exists(recordFile()), "the failed resume leaves no record behind"),
                    () -> assertTrue(movedEvents().isEmpty(), "no move is audited by the failed resume"));
        }

        @Test
        @DisplayName("a target recorded when the copy fails keeps the copies and the old store, and the record is "
                + "deleted")
        void targetRecordedWhenTheCopyFails() throws Exception {
            machineOn(Store.FILE);
            osStore.failPutOf = sonar.account();
            var mover = move(step -> {
                if (step == Step.RECORDED) {
                    rewriteMachineConfiguration("{\"format_version\":1,\"revision\":9,\"max_build_slots\":2,"
                            + "\"lsp_idle_timeout_seconds\":900,\"credentials\":{\"store\":\"os\"}}");
                }
            });

            var failure = assertThrows(SecretStoreException.class, () -> mover.move(Store.OS),
                    "a target recorded during the copy ends the move");

            assertAll(
                    () -> assertEquals(SecretStoreException.Reason.FAILED, failure.reason(),
                            "the stopped copy fails with reason FAILED"),
                    () -> assertTrue(failure.getMessage().contains(
                                    "'os' is recorded, so the copies stay in 'os', 'file' keeps every entry and the "
                                            + "record is deleted"),
                            "the failure says the copies stay in the target"),
                    () -> assertNoSecretIn(failure),
                    () -> assertEquals(Optional.of(githubSecret), osStore.get(GITHUB),
                            "the target keeps the github entry it copied"),
                    () -> assertEquals(List.of(GITHUB, sonar), fileStore.entries(),
                            "the old store keeps both of its entries"),
                    () -> assertTrue(keyIn(fileStore).read().isPresent(),
                            "the old store keeps the integrity key"),
                    () -> assertFalse(Files.exists(recordFile()), "the record is deleted once the move ends"),
                    () -> assertTrue(movedEvents().isEmpty(), "no move is audited for the refused copy"));
        }

        @Test
        @DisplayName("a machine configuration that cannot be read keeps the copies in the target and the record, and "
                + "says so")
        void unreadableMachineConfiguration() throws Exception {
            machineOn(Store.FILE);
            osStore.failPutOf = sonar.account();
            var mover = move(step -> {
                if (step == Step.RECORDED) {
                    rewriteMachineConfiguration("{\"format_version\":1,\"revision\":\"one\"}");
                }
            });

            var failure = assertThrows(SecretStoreException.class, () -> mover.move(Store.OS),
                    "a configuration that cannot be read keeps the record");

            assertAll(
                    () -> assertEquals(SecretStoreException.Reason.FAILED, failure.reason(),
                            "the unreadable store fails with reason FAILED"),
                    () -> assertTrue(failure.getMessage().contains(
                                    "the recorded store cannot be read, so the copies stay in 'os'; the record is kept"),
                            "the failure says the recorded store is unreadable"),
                    () -> assertFalse(failure.getMessage().contains("unknown"),
                            "the failure names no unknown store"),
                    () -> assertNoSecretIn(failure),
                    () -> assertEquals(1, failure.getCause().getSuppressed().length,
                            "the failure carries one suppressed error"),
                    () -> assertEquals(Optional.of(githubSecret), osStore.get(GITHUB),
                            "the target keeps the github entry it copied"),
                    () -> assertEquals(List.of(GITHUB, sonar), fileStore.entries(),
                            "the old store keeps both of its entries"),
                    () -> assertTrue(keyIn(fileStore).read().isPresent(),
                            "the old store keeps the integrity key"),
                    () -> assertTrue(Files.exists(recordFile()), "the record is kept for the next start"),
                    () -> assertTrue(movedEvents().isEmpty(),
                            "no move is audited when the store cannot be read"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.CREDENTIAL_STORE_MOVE_FAILED.resolveIdentifierString()));
        }

        @Test
        @DisplayName("a record that leaves phase copying under the move fails it before the switch")
        void wrongPhase() throws Exception {
            machineOn(Store.OS);
            var mover = move(step -> {
                if (step == Step.ENTRIES_COPIED) {
                    rewriteRecord(new CredentialStoreMoveRecord(Store.OS, Store.FILE, STARTED, Phase.SWITCHED, false,
                            Operation.MOVE, TRANSACTION));
                }
            });

            var failure = assertThrows(IllegalStateException.class, () -> mover.move(Store.FILE),
                    "a record out of phase stops the move before the switch");

            assertAll(
                    () -> assertTrue(failure.getMessage().contains("not in phase 'copying'"),
                            "the failure names the phase the record left"),
                    () -> assertEquals(Optional.of(Store.OS), choice.recorded(),
                            "the old store stays the recorded one"),
                    () -> assertEquals(Optional.of(githubSecret), osStore.get(GITHUB),
                            "the github entry stays in the old store"),
                    () -> assertTrue(movedEvents().isEmpty(), "no move is audited for the stopped switch"));
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
                    () -> assertEquals(1, refusals.size(), "the aspect is refused exactly once"),
                    () -> assertEquals(CredentialStoreChoice.Refusal.Reason.ASPECT_OWNED, refusals.getFirst().reason(),
                            "the refusal reports the aspect as owned"),
                    () -> assertTrue(refusals.getFirst().getMessage().contains("only that operation records"),
                            "the refusal names the move as the only recorder"),
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
                    () -> assertTrue(waitedDuringTheMove.get(), "the aspect waits while the move holds the lock"),
                    () -> assertFalse(aspect.isAlive(), "the aspect thread has finished after the move"),
                    () -> assertEquals(CredentialStoreChoice.Refusal.Reason.ASPECT_OWNED, refusal.get().reason(),
                            "the refusal reports the aspect as owned"),
                    () -> assertTrue(refusal.get().getMessage().contains("a change to 'os' is a move"),
                            "the refusal says the change is a move"),
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
            assertThrows(Stop.class, () -> stopped.move(Store.FILE),
                    "the step hook stops the move after the copies");

            var refusal = assertThrows(CredentialStoreChoice.Refusal.class,
                    () -> choice.record(locks, USER, Store.FILE),
                    "a record of the cut move refuses the aspect");
            var recordedWhileOpen = choice.recorded();
            move().resume();
            choice.record(locks, USER, Store.FILE);

            assertAll(
                    () -> assertEquals(CredentialStoreChoice.Refusal.Reason.ASPECT_OWNED, refusal.reason(),
                            "the refusal reports the aspect as owned"),
                    () -> assertEquals(Optional.of(Store.OS), recordedWhileOpen,
                            "the store stays the old one while the record exists"),
                    () -> assertEquals(Optional.of(Store.FILE), choice.recorded(),
                            "the resume leaves the new store recorded"),
                    () -> assertFalse(Files.exists(recordFile()), "the resume deletes the record of the move"),
                    () -> assertEquals(Optional.of(githubSecret), fileStore.get(GITHUB),
                            "the new store holds the github entry"));
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

            assertThrows(Stop.class, () -> stopped.move(Store.FILE),
                    "the step hook stops the move after the step");
            var moveRecord = CredentialStoreMoveRecord.read(recordFile(), Files.readAllBytes(recordFile()));
            var resumed = move().resume();

            assertAll(
                    () -> assertEquals(stop.compareTo(Step.SWITCHED) < 0 ? Phase.COPYING : Phase.SWITCHED,
                            moveRecord.phase(), "the record keeps its phase at the stop"),
                    () -> assertEquals(Optional.of(moveRecord), resumed, "the resume reports the record it completed"),
                    () -> assertMovedState(Store.FILE, fileStore, osStore, key),
                    () -> assertEquals(1, distinctEvents(movedEvents()).size(),
                            "the reader counts the completed move once"));
        }

        @Test
        @DisplayName("a stop between the switch and the rewrite of the record: the resume copies nothing and finishes the "
                + "deletion from the old store")
        void stoppedBetweenSwitchAndRecord() throws Exception {
            var key = machineOn(Store.FILE);
            var stopped = move(stopAfter(Step.STORE_RECORDED));
            assertThrows(Stop.class, () -> stopped.move(Store.OS),
                    "the step hook stops the move after the switch");
            var moveRecord = CredentialStoreMoveRecord.read(recordFile(), Files.readAllBytes(recordFile()));
            osStore.puts.clear();

            var resumed = move().resume();

            assertAll(
                    () -> assertEquals(Phase.COPYING, moveRecord.phase(), "the record still names phase copying"),
                    () -> assertEquals(Optional.of(moveRecord), resumed, "the resume reports the record it completed"),
                    () -> assertEquals(List.of(), osStore.puts, "the resume copies no entry again"),
                    () -> assertMoved(Store.FILE, Store.OS, osStore, fileStore, key));
        }

        @Test
        @DisplayName("a target that refuses a copy after a stop between the switch and the rewrite of the record keeps "
                + "every entry, since the recorded store is never emptied")
        void recordedTargetNeverEmptied() throws Exception {
            var key = machineOn(Store.FILE);
            var stopped = move(stopAfter(Step.STORE_RECORDED));
            assertThrows(Stop.class, () -> stopped.move(Store.OS),
                    "the step hook stops the move after the switch");
            osStore.failPutOf = sonar.account();

            move().resume();

            assertAll(
                    () -> assertEquals(Optional.of(Store.OS), choice.recorded(),
                            "the switched store stays the recorded one"),
                    () -> assertEquals(Optional.of(githubSecret), osStore.get(GITHUB),
                            "the target keeps the github entry it copied"),
                    () -> assertEquals(Optional.of(sonarSecret), osStore.get(sonar),
                            "the target keeps the sonar entry it copied"),
                    () -> assertArrayEquals(key.getEncoded(), keyIn(osStore).read().orElseThrow().getEncoded(),
                            "the integrity key stays in the target store"),
                    () -> assertEquals(List.of(), fileStore.entries(), "no entry is left in the target store"),
                    () -> assertFalse(Files.exists(recordFile()), "the failed resume leaves no record behind"),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN,
                            WARN.CREDENTIAL_STORE_MOVE_FAILED.resolveIdentifierString()));
        }

        @Test
        @DisplayName("a resume before the switch whose copy fails again leaves the old store recorded and holding "
                + "every entry")
        void failingResume() throws Exception {
            machineOn(Store.FILE);
            var stopped = move(stopAfter(Step.ENTRIES_COPIED));
            assertThrows(Stop.class, () -> stopped.move(Store.OS),
                    "the step hook stops the move after the copies");
            osStore.failPutOf = sonar.account();
            var mover = move();

            var failure = assertThrows(SecretStoreException.class, mover::resume,
                    "a copy that fails again fails the resume");

            assertAll(
                    () -> assertEquals(SecretStoreException.Reason.FAILED, failure.reason(),
                            "the failed resume fails with reason FAILED"),
                    () -> assertNoSecretIn(failure),
                    () -> assertEquals(Optional.of(Store.FILE), choice.recorded(),
                            "the old store stays the recorded one"),
                    () -> assertEquals(Set.of(), osStore.secrets.keySet(), "the target keeps no copy of an entry"),
                    () -> assertEquals(List.of(GITHUB, sonar), fileStore.entries(),
                            "the old store keeps both of its entries"),
                    () -> assertTrue(keyIn(fileStore).read().isPresent(),
                            "the old store keeps the integrity key"),
                    () -> assertFalse(Files.exists(recordFile()), "the failed resume leaves no record behind"),
                    () -> assertTrue(movedEvents().isEmpty(), "no move is audited by the failed resume"));
        }

        @Test
        @DisplayName("a record in phase switched while the old store is recorded is refused, deletes nothing and is "
                + "kept")
        void switchedRecordWithTheOldStoreRecorded() throws Exception {
            var key = machineOn(Store.OS);
            write(recordFile(),
                    new CredentialStoreMoveRecord(Store.OS, Store.FILE, STARTED, Phase.SWITCHED, false, Operation.MOVE,
                            TRANSACTION).write());
            var mover = move();

            var failure = assertThrows(IllegalStateException.class, mover::resume,
                    "a switched record over the old store stops the resume");

            assertAll(
                    () -> assertTrue(failure.getMessage().contains("credentials.store names 'os', not 'file'"),
                            "the failure names the store that is recorded"),
                    () -> assertEquals(Optional.of(Store.OS), choice.recorded(),
                            "the old store stays the recorded one"),
                    () -> assertEquals(Optional.of(githubSecret), osStore.get(GITHUB),
                            "the old store keeps the github entry"),
                    () -> assertEquals(Optional.of(sonarSecret), osStore.get(sonar),
                            "the old store keeps the sonar entry"),
                    () -> assertArrayEquals(key.getEncoded(), keyIn(osStore).read().orElseThrow().getEncoded(),
                            "the integrity key stays in the old store"),
                    () -> assertTrue(Files.exists(recordFile()), "the record is kept after the refused resume"),
                    () -> assertTrue(movedEvents().isEmpty(), "no move is audited by the refused resume"));
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
            assertThrows(UncheckedIOException.class, () -> failing.move(Store.FILE),
                    "an audit sink that fails once fails the move");
            var moveRecord = CredentialStoreMoveRecord.read(recordFile(), Files.readAllBytes(recordFile()));
            var eventsBeforeTheResume = movedEvents().size();

            new CredentialStoreMove(base, choice, locks, sink, USER, Clock.fixed(STARTED, ZoneOffset.UTC),
                    CredentialStoreMove.StepHook.NONE).resume();

            assertAll(
                    () -> assertEquals(Phase.SWITCHED, moveRecord.phase(), "the record stays in phase switched"),
                    () -> assertEquals(0, eventsBeforeTheResume, "the failing sink audited no move yet"),
                    () -> assertMoved(Store.OS, Store.FILE, fileStore, osStore, key));
        }

        @Test
        @DisplayName("without a record the resume does nothing")
        void noRecord() throws Exception {
            machineOn(Store.OS);

            assertAll(
                    () -> assertTrue(move().resume().isEmpty(), "a resume without a record does nothing"),
                    () -> assertEquals(Optional.of(Store.OS), choice.recorded(),
                            "the old store stays the recorded one"),
                    () -> assertTrue(fileStore.entries().isEmpty(), "the file store stays without entries"),
                    () -> assertTrue(movedEvents().isEmpty(), "no move is audited without a record"));
        }
    }

    @Nested
    @DisplayName("a move that cannot start writes nothing")
    class Refused {

        @Test
        @DisplayName("before a store is recorded it is refused with onboarding_incomplete")
        void notRecorded() {
            var mover = move();

            var refusal = assertThrows(CredentialStoreChoice.Refusal.class, () -> mover.move(Store.FILE),
                    "a move without a recorded store is refused");

            assertAll(
                    () -> assertEquals(CredentialStoreChoice.Refusal.Reason.ONBOARDING_INCOMPLETE, refusal.reason(),
                            "the refusal reports onboarding incomplete"),
                    () -> assertFalse(Files.exists(recordFile()), "nothing is written before the refusal"));
        }

        @Test
        @DisplayName("a move into the recorded store is refused")
        void intoTheRecordedStore() throws Exception {
            machineOn(Store.OS);
            var mover = move();

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> mover.move(Store.OS),
                            "a move into the recorded store is refused"),
                    () -> assertFalse(Files.exists(recordFile()), "nothing is written before the refusal"),
                    () -> assertTrue(fileStore.entries().isEmpty(), "the file store stays without entries"));
        }

        @Test
        @DisplayName("a target that holds a Token Lane entry already is refused before the record is written, and "
                + "nothing is written or deleted in either store")
        void targetNotEmpty() throws Exception {
            var key = machineOn(Store.OS);
            fileStore.put(GITHUB, heldSecret);
            var mover = move();

            var failure = assertThrows(SecretStoreException.class, () -> mover.move(Store.FILE),
                    "a target that holds an entry is refused");

            assertAll(
                    () -> assertEquals(SecretStoreException.Reason.FAILED, failure.reason(),
                            "the refused target fails with reason FAILED"),
                    () -> assertTrue(failure.getMessage().contains("'file' holds Token Lane entries already (1)"),
                            "the failure counts the entries the target holds"),
                    () -> assertNoSecretIn(failure),
                    () -> assertFalse(failure.getMessage().contains(heldSecret),
                            "the failure never holds the held secret"),
                    () -> assertFalse(Files.exists(recordFile()), "nothing is written before the refusal"),
                    () -> assertEquals(Optional.of(Store.OS), choice.recorded(),
                            "the old store stays the recorded one"),
                    () -> assertEquals(Optional.of(heldSecret), fileStore.get(GITHUB),
                            "the held entry stays in the target store"),
                    () -> assertEquals(List.of(GITHUB), fileStore.entries(),
                            "the target lists only the entry it held"),
                    () -> assertEquals(Optional.of(githubSecret), osStore.get(GITHUB),
                            "the old store keeps the github entry"),
                    () -> assertEquals(Optional.of(sonarSecret), osStore.get(sonar),
                            "the old store keeps the sonar entry"),
                    () -> assertArrayEquals(key.getEncoded(), keyIn(osStore).read().orElseThrow().getEncoded(),
                            "the integrity key stays in the old store"),
                    () -> assertTrue(movedEvents().isEmpty(), "no move is audited for the refused target"));
        }

        @Test
        @DisplayName("while a move is open another one is refused")
        void moveOpen() throws Exception {
            machineOn(Store.OS);
            var open = new CredentialStoreMoveRecord(Store.OS, Store.FILE, STARTED, Phase.COPYING, false,
                    Operation.MOVE, TRANSACTION);
            write(recordFile(), open.write());
            var mover = move();

            assertAll(
                    () -> assertThrows(IllegalStateException.class, () -> mover.move(Store.FILE),
                            "a second move while one is open is refused"),
                    () -> assertTrue(fileStore.entries().isEmpty(), "the file store stays without entries"),
                    () -> assertEquals(Optional.of(Store.OS), choice.recorded(),
                            "the old store stays the recorded one"));
        }
    }

    @Nested
    @DisplayName("the audit record of a completed move")
    class Audited {

        @Test
        @DisplayName("carries the transaction id of its record, and a record the next start hands over again carries the "
                + "same id, which the reader drops")
        void reDeliveredKeepsItsId() throws Exception {
            machineOn(Store.OS);
            var stopped = move(step -> {
                if (step == Step.AUDITED) {
                    throw new Stop();
                }
            });

            assertThrows(Stop.class, () -> stopped.move(Store.FILE),
                    "the step hook stops the move after its audit");
            var written = CredentialStoreMoveRecord.read(recordFile(), Files.readAllBytes(recordFile()));
            var first = movedEvents().getFirst();

            move().resume();

            assertAll(
                    () -> assertEquals(written.transactionId().value(),
                            first.details().get(AuditEvent.DETAIL_TRANSACTION_ID),
                            "the audit carries the id of its record"),
                    () -> assertTransactionIdAt(STARTED, written.transactionId().value()),
                    () -> assertEquals(2, movedEvents().size(), "the audit record is delivered at least once"),
                    () -> assertEquals(written.transactionId().value(),
                            movedEvents().get(1).details().get(AuditEvent.DETAIL_TRANSACTION_ID),
                            "the handed-over record carries the same id"),
                    () -> assertEquals(List.of(first), distinctEvents(movedEvents()),
                            "the reader counts the handed-over record once"),
                    () -> assertFalse(Files.exists(recordFile()), "the completed resume deletes the record"));
        }

        @Test
        @DisplayName("a record of another move carries another id and is a new event")
        void anotherMoveIsANewEvent() throws Exception {
            machineOn(Store.OS);

            move().move(Store.FILE);
            move().move(Store.OS);

            var ids = movedEvents().stream().map(event -> event.details().get(AuditEvent.DETAIL_TRANSACTION_ID))
                    .toList();

            assertAll(
                    () -> assertEquals(2, movedEvents().size(), "both moves are audited as their own event"),
                    () -> assertNotEquals(ids.get(0), ids.get(1), "the two moves carry different record ids"),
                    () -> assertEquals(2, distinctEvents(movedEvents()).size(),
                            "two distinct ids, so two distinct events"));
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
        return List.of(Files.readAllBytes(enrolments.sidecar(project)), Files.readAllBytes(harnessesSidecar()),
                Files.readAllBytes(AuditHead.headFile(base)));
    }

    private static void assertSameSidecars(List<byte[]> expected, List<byte[]> actual) {
        assertEquals(expected.size(), actual.size(), "the same number of sidecars survived");
        for (var i = 0; i < expected.size(); i++) {
            assertArrayEquals(expected.get(i), actual.get(i), "each sidecar keeps the bytes it was signed with");
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

            var failure = assertThrows(SecretStoreException.class, () -> mover.move(to),
                    "a target with its own key is refused");

            assertAll(
                    () -> assertEquals(SecretStoreException.Reason.FAILED, failure.reason(),
                            "the key conflict fails with reason FAILED"),
                    () -> assertTrue(failure.getMessage().contains("holds another integrity key than"),
                            "the failure says the target holds another key"),
                    () -> assertTrue(failure.getMessage().contains("nothing was written or deleted"),
                            "the failure says nothing was written"),
                    () -> assertNoSecretIn(failure),
                    () -> assertFalse(Files.exists(recordFile()), "nothing is written before the refusal"),
                    () -> assertEquals(Optional.of(from), choice.recorded(),
                            "the old store stays the recorded one"),
                    () -> assertArrayEquals(targetKey.getEncoded(), keyIn(target).read().orElseThrow().getEncoded(),
                            "the target key is untouched by the move"),
                    () -> assertArrayEquals(oldKey.getEncoded(),
                            keyIn(storeOf(from)).read().orElseThrow().getEncoded(),
                            "the old key is untouched by the move"),
                    () -> assertSameSidecars(signed, sidecars()),
                    () -> assertTrue(target.entries().stream().allMatch(
                                    account -> EnrolmentKey.ACCOUNT.equals(account.account())),
                            "the target holds no entry but its own key"),
                    () -> assertEquals(List.of(GITHUB, sonar), storeOf(from).entries().stream()
                                    .filter(account -> !EnrolmentKey.ACCOUNT.equals(account.account())).toList(),
                            "the old store keeps both of its entries"),
                    () -> assertEquals(List.of(), osStore.puts, "the refused move copies no entry"),
                    () -> assertTrue(movedEvents().isEmpty(), "no move is audited for the key conflict"));
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
                    () -> assertEquals(List.of(GITHUB.account(), sonar.account()), osStore.puts,
                            "the same key is not written again"));
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

            var failure = assertThrows(SecretStoreException.class, () -> mover.move(to),
                    "a move that fails after the key copy is refused");

            assertAll(
                    () -> assertTrue(failure.getMessage().contains("the copies were removed from '%s'".formatted(
                            to.value())), "the failure says the copies were removed"),
                    () -> assertEquals(Optional.of(from), choice.recorded(),
                            "the old store stays the recorded one"),
                    () -> assertArrayEquals(key.getEncoded(), keyIn(target).read().orElseThrow().getEncoded(),
                            "the same key stays in the target store"),
                    () -> assertArrayEquals(key.getEncoded(), keyIn(storeOf(from)).read().orElseThrow().getEncoded(),
                            "the same key stays in the old store"),
                    () -> assertTrue(target.get(GITHUB).isEmpty(), "the target keeps no github entry"),
                    () -> assertTrue(target.get(sonar).isEmpty(), "the target keeps no sonar entry"),
                    () -> assertEquals(Optional.of(githubSecret), storeOf(from).get(GITHUB),
                            "the old store keeps the github entry"),
                    () -> assertFalse(Files.exists(recordFile()), "the failed move leaves no record behind"),
                    () -> assertTrue(movedEvents().isEmpty(), "no move is audited for the failed key copy"));
        }

        @Test
        @DisplayName("the same key as the old store's stays in the target when a resume removes the copies and fails "
                + "again")
        void sameKeyKeptByTheResume() throws Exception {
            var key = machineOn(Store.FILE);
            keyIn(osStore).write(key);
            var stopped = move(stopAfter(Step.KEY_COPIED));
            assertThrows(Stop.class, () -> stopped.move(Store.OS),
                    "the step hook stops the move after the key copy");
            var moveRecord = CredentialStoreMoveRecord.read(recordFile(), Files.readAllBytes(recordFile()));
            osStore.failPutOf = sonar.account();
            var mover = move();

            var failure = assertThrows(SecretStoreException.class, mover::resume,
                    "a copy that fails again fails the resume");

            assertAll(
                    () -> assertTrue(moveRecord.targetHeldKey(), "the record names that the target held the key"),
                    () -> assertTrue(failure.getMessage().contains("the copies were removed from 'os'"),
                            "the failure says the copies were removed"),
                    () -> assertEquals(Optional.of(Store.FILE), choice.recorded(),
                            "the old store stays the recorded one"),
                    () -> assertArrayEquals(key.getEncoded(), keyIn(osStore).read().orElseThrow().getEncoded(),
                            "the same key stays in the target store"),
                    () -> assertTrue(osStore.get(GITHUB).isEmpty(), "the target keeps no github entry"),
                    () -> assertEquals(List.of(GITHUB, sonar), fileStore.entries(),
                            "the old store keeps both of its entries"),
                    () -> assertArrayEquals(key.getEncoded(), keyIn(fileStore).read().orElseThrow().getEncoded(),
                            "the same key stays in the old store"),
                    () -> assertFalse(Files.exists(recordFile()), "the failed resume leaves no record behind"));
        }

        @Test
        @DisplayName("a key the move copied into the target is removed when a resume removes the copies and fails again")
        void copiedKeyRemovedByTheResume() throws Exception {
            machineOn(Store.FILE);
            var stopped = move(stopAfter(Step.KEY_COPIED));
            assertThrows(Stop.class, () -> stopped.move(Store.OS),
                    "the step hook stops the move after the key copy");
            var moveRecord = CredentialStoreMoveRecord.read(recordFile(), Files.readAllBytes(recordFile()));
            osStore.failPutOf = sonar.account();
            var mover = move();

            assertThrows(SecretStoreException.class, mover::resume, "a copy that fails again fails the resume");

            assertAll(
                    () -> assertFalse(moveRecord.targetHeldKey(), "the record says the target held no key"),
                    () -> assertEquals(Set.of(), osStore.secrets.keySet(), "the target keeps no copy of an entry"),
                    () -> assertTrue(keyIn(fileStore).read().isPresent(),
                            "the old store keeps the integrity key"),
                    () -> assertFalse(Files.exists(recordFile()), "the failed resume leaves no record behind"));
        }

        @Test
        @DisplayName("a key in the target while the old store holds none is no conflict and stays")
        void keyWhileTheOldStoreHoldsNone() {
            choice.record(locks, USER, Store.OS);
            osStore.put(GITHUB, githubSecret);
            var targetKey = otherKey();
            keyIn(fileStore).write(targetKey);

            move().move(Store.FILE);

            assertAll(
                    () -> assertEquals(Optional.of(Store.FILE), choice.recorded(),
                            "the moved store is the one recorded"),
                    () -> assertEquals(Optional.of(githubSecret), fileStore.get(GITHUB),
                            "the new store holds the github entry"),
                    () -> assertArrayEquals(targetKey.getEncoded(),
                            keyIn(fileStore).read().orElseThrow().getEncoded(),
                            "the target key stays where it was written"),
                    () -> assertEquals(List.of(), osStore.entries(), "the old store holds no entry after the move"),
                    () -> assertFalse(Files.exists(recordFile()), "the record is gone once the move ends"));
        }
    }

    /** The stop of a process, simulated by the step hook. */
    private static final class Stop extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;
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
