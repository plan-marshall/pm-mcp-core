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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import java.io.IOException;
import java.io.Serial;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.log.CoreLogMessages;
import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.service.LiveWork.Item;
import de.planmarshall.core.service.LiveWork.Kind;
import de.planmarshall.core.store.EnrolmentCodec;
import de.planmarshall.core.store.EnrolmentStore;
import de.planmarshall.core.store.PairRecovery;
import de.planmarshall.core.store.ProjectEnrolment;
import de.planmarshall.core.store.ProjectEnrolment.Build;
import de.planmarshall.core.store.StoreDigest;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.StoreRefusal.Outcome;
import de.planmarshall.core.store.TransactionId;
import de.planmarshall.runtime.credentials.FileSecretStore;
import de.planmarshall.runtime.credentials.SecretStoreException;
import de.planmarshall.runtime.credentials.SecretStoreException.Reason;
import de.planmarshall.runtime.enrolment.Enrolments.ScopeActiveException;
import de.planmarshall.runtime.lock.FileLockManager;
import de.planmarshall.runtime.log.RuntimeLogMessages.INFO;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;

/**
 * The operator writes of the enrolment store (PM-EXEC-2, PM-MIG-1, PM-IMPL-7): {@code enrol} writes a file and a
 * sidecar record the check accepts, {@code unenrol} is refused while the project has live work and otherwise removes
 * only the file and its sidecar, and a re-baseline makes a reviewed file pass again. Each write owes its audit record
 * in the project's intent file before the mutation and hands it over after it; a refused or stopped hand-over leaves
 * the record owed, and the next write or the replay hands it over with the same transaction id.
 */
@EnableTestLogger
@DisplayName("Enrolments")
class EnrolmentsTest {

    private static final String PROJECT = "pm-mcp-core";
    private static final String USER = System.getProperty("user.name");
    private static final String LIVE_PLAN = "add-login";
    private static final String WORLD_WRITABLE = "rw-rw-rw-";
    private static final String AUDIT_LOG_FULL = "audit log full";
    private static final String OWNER_ONLY = "rw-------";
    private static final String STAGED_SEGMENT = ".tmp.";

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

    /** @return a failure of the audit sink, prepared before the call that meets it */
    private static UncheckedIOException auditFailure() {
        return new UncheckedIOException(new IOException(AUDIT_LOG_FULL));
    }

    /** @return an audit sink that fails every append with the given failure */
    private static AuditSink failingWith(UncheckedIOException failure) {
        return event -> {
            throw failure;
        };
    }

    private ProjectEnrolment verify() {
        try (var transaction = locks.openTransaction()) {
            return check.verify(transaction, PROJECT);
        }
    }

    /**
     * @param event     the name of a succeeded operator event
     * @param projectId the project
     * @return the record of the event as it is without its transaction id, which a test cannot know in advance
     */
    private static AuditEvent succeeded(String event, String projectId) {
        return new AuditEvent(event, AuditEvent.CATEGORY_OPERATOR, AuditEvent.OUTCOME_SUCCEEDED,
                Map.of(AuditEvent.DETAIL_PROJECT_ID, projectId));
    }

    private static List<AuditEvent> withoutTransactionIds(List<AuditEvent> records) {
        return records.stream().map(event -> {
            var details = new HashMap<>(event.details());
            details.remove(AuditEvent.DETAIL_TRANSACTION_ID);
            return new AuditEvent(event.event(), event.category(), event.outcome(), details);
        }).toList();
    }

    private static String transactionIdOf(AuditEvent event) {
        return (String) event.details().get(AuditEvent.DETAIL_TRANSACTION_ID);
    }

    /** @return the staged name a stopped write of the transaction leaves for the target */
    private static Path staged(Path target, TransactionId transactionId) {
        return target.resolveSibling(target.getFileName() + STAGED_SEGMENT + transactionId.value());
    }

    /**
     * Stages by hand what a stopped write of the project's pair leaves, as a write stages it (readable and writable by
     * its owner only); a {@code null} content leaves that name out.
     */
    private void stageByHand(byte[] fileContent, byte[] sidecarContent) throws IOException {
        var transactionId = TransactionId.generate(Clock.systemUTC());
        if (fileContent != null) {
            writeOwnerOnly(staged(store.file(PROJECT), transactionId), fileContent);
        }
        if (sidecarContent != null) {
            writeOwnerOnly(staged(store.sidecar(PROJECT), transactionId), sidecarContent);
        }
    }

    private static void writeOwnerOnly(Path file, byte[] content) throws IOException {
        Files.write(file, content);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString(OWNER_ONLY));
    }

    /** @return the sidecar record of the content under the integrity key, created if needed */
    private byte[] signed(byte[] content) {
        return EnrolmentHmac.sidecar(key.readOrCreate(), content).write();
    }

    private List<String> stagedNames() throws IOException {
        try (var entries = Files.list(store.directory())) {
            return entries.map(entry -> entry.getFileName().toString())
                    .filter(name -> name.contains(STAGED_SEGMENT)).toList();
        }
    }

    /** Settles the project's stopped writes as a recovery would, in its own transaction. */
    private List<PairRecovery> recoverPair() {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(store.lockKey(PROJECT));
            return store.recoverPair(transaction, PROJECT);
        }
    }

    /** Every record carries a transaction id that is a valid, non-empty {@link TransactionId}. */
    private static void assertValidTransactionIds(List<AuditEvent> records) {
        assertFalse(records.isEmpty(), "no record to check");
        for (var event : records) {
            var id = transactionIdOf(event);
            assertEquals(id, new TransactionId(id).value());
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
                    () -> assertEquals(List.of(succeeded(AuditEvent.ENROLMENT_WRITTEN, PROJECT)),
                            withoutTransactionIds(events)),
                    () -> assertValidTransactionIds(events),
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
            var other = enrolment(OTHER, false, null);

            var refusal = assertThrows(StoreRefusal.class, () -> keyringEnrolments.enrol(other, false));

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
            var other = enrolment(OTHER, false, null);

            var refusal = assertThrows(StoreRefusal.class, () -> enrolments.enrol(other, false));

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
            var failure = auditFailure();
            var failing = enrolmentsWith(keyringKey, event -> {
                throw failure;
            });
            var other = enrolment(OTHER, false, null);

            var refusal = assertThrows(StoreRefusal.class, () -> failing.enrol(other, false));

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
            var again = enrolment(false, null);

            var refusal = assertThrows(StoreRefusal.class, () -> enrolments.enrol(again, true));

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
            var refused = enrolment(OTHER, false, null);
            assertThrows(StoreRefusal.class, () -> keyringEnrolments.enrol(refused, false));
            events.clear();

            keyringEnrolments.rebaseline(PROJECT);
            var other = keyringEnrolments.enrol(refused, false);
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
            var other = enrolment(OTHER, false, null);

            var thrown = assertThrows(SecretStoreException.class, () -> enrolments.enrol(other, false));

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
            var other = enrolment(OTHER, false, null);

            var thrown = assertThrows(SecretStoreException.class, () -> enrolments.enrol(other, false));

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
            var items = List.of(new Item(Kind.PLAN, LIVE_PLAN), new Item(Kind.EPIC, "auth-rework"),
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
                    () -> assertEquals(0, refusal.getSuppressed().length),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.UNENROL_REFUSED.resolveIdentifierString()));
        }

        @Test
        @DisplayName("stays refused with scope_active when the audit sink fails, and carries the failure")
        void refusalSurvivesAFailingAuditSink() {
            enrolments.enrol(enrolment(false, null), false);
            var items = List.of(new Item(Kind.PLAN, LIVE_PLAN));
            liveWork.put(PROJECT, items);
            var failure = auditFailure();
            var failing = enrolmentsWith(key, failingWith(failure));

            var refusal = assertThrows(ScopeActiveException.class, () -> failing.unenrol(PROJECT));

            assertAll(
                    () -> assertEquals(PROJECT, refusal.projectId()),
                    () -> assertEquals(items, refusal.items()),
                    () -> assertTrue(refusal.getMessage().contains(ScopeActiveException.CODE), refusal.getMessage()),
                    () -> assertEquals(List.of(failure), List.of(refusal.getSuppressed())),
                    () -> assertTrue(Files.exists(store.file(PROJECT))),
                    () -> assertTrue(Files.exists(store.sidecar(PROJECT))),
                    () -> assertTrue(store.auditOwingProjectIds().isEmpty()),
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
                    () -> assertEquals(List.of(succeeded(AuditEvent.ENROLMENT_REMOVED, PROJECT)),
                            withoutTransactionIds(events)),
                    () -> assertValidTransactionIds(events),
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

        @ParameterizedTest(name = "{0}")
        @EnumSource(StagedOnly.class)
        @DisplayName("cancels a stopped first enrolment that left only staged files: audited, and it cannot come back")
        void cancelsAStagedOnlyEnrolment(StagedOnly staging) throws Exception {
            var content = EnrolmentCodec.write(enrolment(false, null));
            stageByHand(staging.file ? content : null, staging.sidecar ? signed(content) : null);

            var removed = enrolments.unenrol(PROJECT);
            var namesAfterUnenrol = stagedNames();
            var recovered = recoverPair();

            assertAll(
                    () -> assertTrue(removed),
                    () -> assertTrue(namesAfterUnenrol.isEmpty(), namesAfterUnenrol::toString),
                    () -> assertEquals(List.of(succeeded(AuditEvent.ENROLMENT_REMOVED, PROJECT)),
                            withoutTransactionIds(events)),
                    () -> assertValidTransactionIds(events),
                    () -> assertFalse(Files.exists(store.auditIntent(PROJECT))),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                            INFO.ENROLMENT_REMOVED.resolveIdentifierString()),
                    () -> assertTrue(recovered.isEmpty()),
                    () -> assertFalse(Files.exists(store.file(PROJECT))),
                    () -> assertFalse(Files.exists(store.sidecar(PROJECT))),
                    () -> assertTrue(store.projectIds().isEmpty()));
        }

        @Test
        @DisplayName("of an enrolled project with a stopped write removes the pair and the staged files")
        void removesThePairAndItsStagedLeftovers() throws Exception {
            enrolments.enrol(enrolment(false, null), false);
            events.clear();
            var content = EnrolmentCodec.write(enrolment(true, null));
            stageByHand(content, signed(content));

            var removed = enrolments.unenrol(PROJECT);

            assertAll(
                    () -> assertTrue(removed),
                    () -> assertTrue(stagedNames().isEmpty()),
                    () -> assertFalse(Files.exists(store.file(PROJECT))),
                    () -> assertFalse(Files.exists(store.sidecar(PROJECT))),
                    () -> assertTrue(recoverPair().isEmpty()),
                    () -> assertEquals(List.of(succeeded(AuditEvent.ENROLMENT_REMOVED, PROJECT)),
                            withoutTransactionIds(events)));
        }

        private List<Path> listing(Path directory) throws IOException {
            try (var entries = Files.list(directory)) {
                return entries.toList();
            }
        }
    }

    /** The staged files a stopped first enrolment can leave, with neither a file nor a sidecar in place. */
    enum StagedOnly {
        COMPLETE_PAIR(true, true),
        FILE_ONLY(true, false),
        SIDECAR_ONLY(false, true);

        private final boolean file;
        private final boolean sidecar;

        StagedOnly(boolean file, boolean sidecar) {
            this.file = file;
            this.sidecar = sidecar;
        }
    }

    /**
     * A stopped write of the pair of an enrolled project, whether it leaves the new file in place once settled, and
     * the kinds of what the re-baseline reports it settled.
     */
    enum StoppedWrite {
        NOTHING_STAGED(false, List.of()),
        COMPLETE_PAIR(true, List.of(PairRecovery.Completed.class)),
        TORN_STAGED_SIDECAR(false, List.of(PairRecovery.Discarded.class)),
        STAGED_SIDECAR_ONLY(true, List.of(PairRecovery.Completed.class));

        private final boolean newFileInPlace;
        private final List<Class<? extends PairRecovery>> settled;

        StoppedWrite(boolean newFileInPlace, List<Class<? extends PairRecovery>> settled) {
            this.newFileInPlace = newFileInPlace;
            this.settled = settled;
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
                    () -> assertTrue(outcome.settled().isEmpty()),
                    () -> assertTrue(verify().pilot()),
                    () -> assertEquals(List.of(succeeded(AuditEvent.ENROLMENT_REBASELINED, PROJECT)),
                            withoutTransactionIds(events)),
                    () -> assertValidTransactionIds(events),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                            INFO.ENROLMENT_REBASELINED.resolveIdentifierString()));
        }

        /** Leaves, beside the enrolled pair, what the stopped write leaves; the staged pair is a newer enrolment. */
        private void stop(StoppedWrite stopped, byte[] newFile) throws IOException {
            switch (stopped) {
                case COMPLETE_PAIR -> stageByHand(newFile, signed(newFile));
                case TORN_STAGED_SIDECAR -> {
                    var sidecar = signed(newFile);
                    stageByHand(newFile, Arrays.copyOf(sidecar, sidecar.length / 2));
                }
                case STAGED_SIDECAR_ONLY -> {
                    Files.write(store.file(PROJECT), newFile);
                    stageByHand(null, signed(newFile));
                }
                case NOTHING_STAGED -> {
                    // the matched control: no staged name, the re-baseline behaves as before
                }
            }
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(StoppedWrite.class)
        @DisplayName("settles a stopped write first, so the staged pair can never replace the re-baselined pair")
        void settlesAStoppedWriteFirst(StoppedWrite stopped) throws Exception {
            enrolments.enrol(enrolment(false, null), false);
            var oldFile = Files.readAllBytes(store.file(PROJECT));
            var newFile = EnrolmentCodec.write(enrolment(true, null));
            stop(stopped, newFile);
            var expected = stopped.newFileInPlace ? newFile : oldFile;

            var outcome = enrolments.rebaseline(PROJECT);

            var file = Files.readAllBytes(store.file(PROJECT));
            var sidecar = Files.readAllBytes(store.sidecar(PROJECT));
            var namesAfterRebaseline = stagedNames();
            var recovered = recoverPair();
            assertAll(
                    () -> assertTrue(namesAfterRebaseline.isEmpty(), namesAfterRebaseline::toString),
                    () -> assertArrayEquals(expected, file),
                    () -> assertEquals(StoreDigest.of(expected), store.decodeSidecar(PROJECT, sidecar).digest()),
                    () -> assertEquals(StoreDigest.of(expected), outcome.current()),
                    () -> assertEquals(stopped.settled,
                            outcome.settled().stream().map(PairRecovery::getClass).toList()),
                    () -> assertTrue(outcome.settled().stream()
                            .filter(PairRecovery.Discarded.class::isInstance)
                            .map(PairRecovery.Discarded.class::cast)
                            .noneMatch(discarded -> discarded.reason().isBlank())),
                    () -> assertTrue(recovered.isEmpty()),
                    () -> assertArrayEquals(file, Files.readAllBytes(store.file(PROJECT))),
                    () -> assertArrayEquals(sidecar, Files.readAllBytes(store.sidecar(PROJECT))),
                    () -> assertEquals(stopped.newFileInPlace, verify().pilot()));
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
            Files.setPosixFilePermissions(store.file(PROJECT), PosixFilePermissions.fromString(WORLD_WRITABLE));
            var reason = new EnrolmentFileCheck(USER).violation(store.file(PROJECT)).orElseThrow();
            events.clear();

            var refusal = assertThrows(StoreRefusal.class, () -> enrolments.rebaseline(PROJECT));

            assertAll(
                    () -> assertEquals(Outcome.ENROLMENT_INSECURE, refusal.outcome()),
                    () -> assertArrayEquals(sidecar, Files.readAllBytes(store.sidecar(PROJECT))),
                    () -> assertTrue(refusal.getMessage().contains(reason), refusal.getMessage()),
                    () -> assertEquals(List.of(AuditEvent.enrolmentInsecure(PROJECT, reason)), events),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.ENROLMENT_INSECURE.resolveIdentifierString()),
                    () -> assertFalse(Files.exists(store.auditIntent(PROJECT))),
                    () -> assertTrue(events.stream()
                            .noneMatch(event -> AuditEvent.ENROLMENT_REBASELINED.equals(event.event()))));
        }

        @Test
        @DisplayName("of an insecure file stays refused when the audit sink fails, and carries the failure")
        void insecureRefusalSurvivesAFailingAuditSink() throws Exception {
            enrolments.enrol(enrolment(false, null), false);
            Files.setPosixFilePermissions(store.file(PROJECT), PosixFilePermissions.fromString(WORLD_WRITABLE));
            var failure = auditFailure();
            var failing = enrolmentsWith(key, failingWith(failure));

            var refusal = assertThrows(StoreRefusal.class, () -> failing.rebaseline(PROJECT));

            assertAll(
                    () -> assertEquals(Outcome.ENROLMENT_INSECURE, refusal.outcome()),
                    () -> assertEquals(List.of(failure), List.of(refusal.getSuppressed())),
                    () -> assertFalse(Files.exists(store.auditIntent(PROJECT))));
        }

        @Test
        @DisplayName("of an enrolment file that is a symbolic link is refused and never follows it")
        void symbolicLinkIsRefusedAndNeverFollowed() throws Exception {
            enrolments.enrol(enrolment(false, null), false);
            var content = Files.readAllBytes(store.file(PROJECT));
            var sidecar = Files.readAllBytes(store.sidecar(PROJECT));
            var real = Files.move(store.file(PROJECT), base.resolve("real.json"));
            Files.createSymbolicLink(store.file(PROJECT), real);
            events.clear();

            var refusal = assertThrows(StoreRefusal.class, () -> enrolments.rebaseline(PROJECT));

            assertAll(
                    () -> assertEquals(Outcome.ENROLMENT_INSECURE, refusal.outcome()),
                    () -> assertTrue(refusal.getMessage().contains(EnrolmentFileCheck.SYMBOLIC_LINK),
                            refusal.getMessage()),
                    () -> assertArrayEquals(sidecar, Files.readAllBytes(store.sidecar(PROJECT))),
                    () -> assertArrayEquals(content, Files.readAllBytes(real)),
                    () -> assertEquals(List.of(AuditEvent.enrolmentInsecure(PROJECT, EnrolmentFileCheck.SYMBOLIC_LINK)),
                            events),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.ENROLMENT_INSECURE.resolveIdentifierString()));
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

    /** What the audit log does with an append: the three ways it can fail and the working one. */
    private enum SinkMode {
        WORKING, REFUSING, STOPPING, ACCEPTING_THEN_STOPPING
    }

    /** What a killed process does to its caller: an unchecked failure that is not a refused append. */
    private static final class ProcessStopped extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        ProcessStopped() {
            super("process stopped at the audit append");
        }
    }

    /** The audit log of the intent tests: it records into {@link #events} while working. */
    private final class SwitchableSink implements AuditSink {

        private SinkMode mode = SinkMode.WORKING;

        @Override
        public void append(AuditEvent event) {
            switch (mode) {
                case WORKING -> events.add(event);
                case REFUSING -> throw new UncheckedIOException(new IOException("the audit log cannot be written"));
                case STOPPING -> throw new ProcessStopped();
                case ACCEPTING_THEN_STOPPING -> {
                    events.add(event);
                    throw new ProcessStopped();
                }
            }
        }
    }

    @Nested
    @DisplayName("owe the audit record before the mutation and hand it over after it")
    class AuditIntent {

        private static final String ALPHA = "alpha-project";
        private static final String BETA = "beta-project";
        private static final String GAMMA = "gamma-project";
        private static final String NOT_JSON = "not json";
        private static final String NEWER_FORMAT = "{\"format_version\":2,\"owed_events\":[]}";
        private static final String ZETA = "zeta-project";
        private static final String REPLAYED = "Owed audit record '%s'";

        private SwitchableSink audit;
        private Enrolments owing;

        @BeforeEach
        void owing() {
            audit = new SwitchableSink();
            owing = enrolmentsWith(key, audit);
        }

        private List<AuditEvent> owed(String projectId) {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(store.lockKey(projectId));
                return store.owedAudit(transaction, projectId);
            }
        }

        private boolean owes(String projectId) {
            return Files.exists(store.auditIntent(projectId));
        }

        /** Replays with a working sink: exactly the owed record, with its transaction id, once. */
        private void assertReplayedOnce(AuditEvent owedRecord) {
            audit.mode = SinkMode.WORKING;

            var replayed = owing.replayOwedAudit();

            assertAll(
                    () -> assertEquals(1, replayed),
                    () -> assertEquals(List.of(owedRecord), events),
                    () -> assertFalse(owes(PROJECT)),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                            INFO.ENROLMENT_AUDIT_REPLAYED.resolveIdentifierString()));

            var again = owing.replayOwedAudit();

            assertAll(
                    () -> assertEquals(0, again),
                    () -> assertEquals(List.of(owedRecord), events));
        }

        private void assertOwedOnce(String event) {
            var owed = owed(PROJECT);
            assertAll(
                    () -> assertTrue(owes(PROJECT)),
                    () -> assertEquals(List.of(succeeded(event, PROJECT)), withoutTransactionIds(owed)),
                    () -> assertValidTransactionIds(owed),
                    () -> assertTrue(events.isEmpty()),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            CoreLogMessages.WARN.AUDIT_RECORD_DEFERRED.resolveIdentifierString()),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            "'" + event + "' with transaction id '" + transactionIdOf(owed.getFirst()) + "'"));
        }

        @Nested
        @DisplayName("with a working audit log")
        class Working {

            @Test
            @DisplayName("enrol records one enrolment_written with a valid transaction id and leaves no intent")
            void enrol() {
                owing.enrol(enrolment(false, null), false);

                assertNormalPath(AuditEvent.ENROLMENT_WRITTEN);
            }

            @Test
            @DisplayName("unenrol records one enrolment_removed with a valid transaction id and leaves no intent")
            void unenrol() {
                enrolments.enrol(enrolment(false, null), false);
                events.clear();

                owing.unenrol(PROJECT);

                assertNormalPath(AuditEvent.ENROLMENT_REMOVED);
            }

            @Test
            @DisplayName("rebaseline records one enrolment_rebaselined with a valid transaction id and leaves no intent")
            void rebaseline() {
                enrolments.enrol(enrolment(false, null), false);
                events.clear();

                owing.rebaseline(PROJECT);

                assertNormalPath(AuditEvent.ENROLMENT_REBASELINED);
            }

            private void assertNormalPath(String event) {
                assertAll(
                        () -> assertEquals(List.of(succeeded(event, PROJECT)), withoutTransactionIds(events)),
                        () -> assertValidTransactionIds(events),
                        () -> assertFalse(owes(PROJECT)),
                        () -> assertEquals(0, owing.replayOwedAudit()),
                        () -> assertEquals(1, events.size()),
                        () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO,
                                INFO.ENROLMENT_AUDIT_REPLAYED.resolveIdentifierString()));
            }
        }

        @Nested
        @DisplayName("with an audit log that refuses the append after the mutation")
        class RefusedAppend {

            @Test
            @DisplayName("enrol writes without throwing, keeps enrolment_written owed and the replay hands it over once")
            void enrol() {
                audit.mode = SinkMode.REFUSING;

                var written = owing.enrol(enrolment(false, null), false);

                assertAll(
                        () -> assertEquals(written, verify()),
                        () -> assertOwedOnce(AuditEvent.ENROLMENT_WRITTEN));
                assertReplayedOnce(owed(PROJECT).getFirst());
            }

            @Test
            @DisplayName("unenrol removes the pair only, keeps enrolment_removed owed and the replay hands it over once")
            void unenrol() throws Exception {
                enrolments.enrol(enrolment(false, null), false);
                events.clear();
                var projectStore = Files.createDirectories(base.resolve("projects").resolve(PROJECT));
                var storeFile = Files.writeString(projectStore.resolve("store.json"), "{\"project_root\":\"/r\"}");
                var before = Files.readAllBytes(storeFile);
                audit.mode = SinkMode.REFUSING;

                var removed = owing.unenrol(PROJECT);

                assertAll(
                        () -> assertTrue(removed),
                        () -> assertFalse(Files.exists(store.file(PROJECT))),
                        () -> assertFalse(Files.exists(store.sidecar(PROJECT))),
                        () -> assertArrayEquals(before, Files.readAllBytes(storeFile)),
                        () -> assertOwedOnce(AuditEvent.ENROLMENT_REMOVED));
                assertReplayedOnce(owed(PROJECT).getFirst());
            }

            @Test
            @DisplayName("rebaseline rewrites the sidecar, keeps enrolment_rebaselined owed and the replay hands it over")
            void rebaseline() throws Exception {
                enrolments.enrol(enrolment(false, null), false);
                Files.write(store.file(PROJECT), EnrolmentCodec.write(enrolment(true, null)));
                events.clear();
                audit.mode = SinkMode.REFUSING;

                owing.rebaseline(PROJECT);

                assertAll(
                        () -> assertTrue(verify().pilot()),
                        () -> assertOwedOnce(AuditEvent.ENROLMENT_REBASELINED));
                assertReplayedOnce(owed(PROJECT).getFirst());
            }
        }

        @Nested
        @DisplayName("with a mutation that fails after the record was owed")
        class FailedMutation {

            /** Puts a directory with an entry where the sidecar is written or removed, so that neither can be done. */
            private void blockSidecar() throws IOException {
                Files.deleteIfExists(store.sidecar(PROJECT));
                var blocker = Files.createDirectory(store.sidecar(PROJECT));
                Files.writeString(blocker.resolve("blocker"), "x");
            }

            private void unblockSidecar() throws IOException {
                var blocker = store.sidecar(PROJECT);
                Files.delete(blocker.resolve("blocker"));
                Files.delete(blocker);
            }

            /** The intent owes exactly one record of the event, in its unconfirmed form, and nothing was handed over. */
            private AuditEvent assertOwedNotConfirmed(String event) {
                var owed = owed(PROJECT);
                assertAll(
                        () -> assertEquals(1, owed.size()),
                        () -> assertEquals(event, owed.getFirst().event()),
                        () -> assertEquals(AuditEvent.OUTCOME_FAILED, owed.getFirst().outcome()),
                        () -> assertEquals(AuditEvent.REASON_NOT_CONFIRMED,
                                owed.getFirst().details().get(AuditEvent.DETAIL_REASON)),
                        () -> assertValidTransactionIds(owed),
                        () -> assertTrue(events.isEmpty()));
                return owed.getFirst();
            }

            /** The replay hands over the unconfirmed record, with its transaction id, and never a succeeded one. */
            private void assertReplayedNotConfirmed(AuditEvent unconfirmed) {
                var replayed = owing.replayOwedAudit();

                assertAll(
                        () -> assertEquals(1, replayed),
                        () -> assertEquals(List.of(unconfirmed), events),
                        () -> assertFalse(owes(PROJECT)),
                        () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                                WARN.ENROLMENT_WRITE_NOT_CONFIRMED.resolveIdentifierString()));
            }

            @Test
            @DisplayName("enrol keeps its record owed as failed with the reason not_confirmed, and never as succeeded")
            void enrol() throws Exception {
                blockSidecar();
                var enrolment = enrolment(false, null);

                assertThrows(UncheckedIOException.class, () -> owing.enrol(enrolment, false));

                assertReplayedNotConfirmed(assertOwedNotConfirmed(AuditEvent.ENROLMENT_WRITTEN));
            }

            @Test
            @DisplayName("unenrol keeps its record owed as failed with the reason not_confirmed, and never as succeeded")
            void unenrol() throws Exception {
                enrolments.enrol(enrolment(false, null), false);
                events.clear();
                blockSidecar();

                assertThrows(UncheckedIOException.class, () -> owing.unenrol(PROJECT));

                assertReplayedNotConfirmed(assertOwedNotConfirmed(AuditEvent.ENROLMENT_REMOVED));
            }

            @Test
            @DisplayName("the next write hands the unconfirmed record over before its own succeeded record")
            void nextWriteFlushesFirst() throws Exception {
                blockSidecar();
                var enrolment = enrolment(false, null);
                assertThrows(UncheckedIOException.class, () -> owing.enrol(enrolment, false));
                var unconfirmed = assertOwedNotConfirmed(AuditEvent.ENROLMENT_WRITTEN);
                unblockSidecar();

                var written = owing.enrol(enrolment, false);

                assertAll(
                        () -> assertEquals(written, verify()),
                        () -> assertEquals(2, events.size()),
                        () -> assertEquals(unconfirmed, events.getFirst()),
                        () -> assertEquals(AuditEvent.OUTCOME_SUCCEEDED, events.getLast().outcome()),
                        () -> assertEquals(AuditEvent.ENROLMENT_WRITTEN, events.getLast().event()),
                        () -> assertEquals(2, events.stream().map(EnrolmentsTest::transactionIdOf).distinct().count()),
                        () -> assertFalse(owes(PROJECT)),
                        () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                                WARN.ENROLMENT_WRITE_NOT_CONFIRMED.resolveIdentifierString()));
            }
        }

        @Nested
        @DisplayName("with a process that stops at the append")
        class Stopped {

            @Test
            @DisplayName("a stop after the mutation keeps the record, and a restarted runtime hands it over exactly once")
            void stopBeforeTheAppend() {
                audit.mode = SinkMode.STOPPING;
                var enrolment = enrolment(false, null);

                assertThrows(ProcessStopped.class, () -> owing.enrol(enrolment, false));

                var owed = owed(PROJECT);
                assertAll(
                        () -> assertEquals(PROJECT, verify().projectId()),
                        () -> assertEquals(List.of(succeeded(AuditEvent.ENROLMENT_WRITTEN, PROJECT)),
                                withoutTransactionIds(owed)),
                        () -> assertTrue(events.isEmpty()));

                var restarted = new Enrolments(new EnrolmentStore(base), key, new EnrolmentFileCheck(USER),
                        new FileLockManager(sink), projectId -> List.of(), sink);
                var replayed = restarted.replayOwedAudit();

                assertAll(
                        () -> assertEquals(1, replayed),
                        () -> assertEquals(owed, events),
                        () -> assertFalse(owes(PROJECT)));
            }

            @Test
            @DisplayName("a record whose intent outlived an accepted append is handed over again with the same "
                    + "transaction id")
            void recordWhoseIntentOutlivedAnAcceptedAppendIsHandedOverAgainWithTheSameTransactionId() {
                audit.mode = SinkMode.ACCEPTING_THEN_STOPPING;
                var enrolment = enrolment(false, null);

                assertThrows(ProcessStopped.class, () -> owing.enrol(enrolment, false));
                assertTrue(owes(PROJECT));
                audit.mode = SinkMode.WORKING;

                var replayed = owing.replayOwedAudit();

                assertAll(
                        () -> assertEquals(1, replayed),
                        () -> assertEquals(2, events.size()),
                        () -> assertEquals(events.getFirst(), events.getLast()),
                        () -> assertEquals(1, events.stream().map(EnrolmentsTest::transactionIdOf).distinct().count()),
                        () -> assertFalse(owes(PROJECT)));
            }
        }

        @Nested
        @DisplayName("the next operation")
        class NextOperation {

            @Test
            @DisplayName("flushes what an earlier write still owes, in order, and logs only that record as replayed")
            void flushesInOrder() {
                audit.mode = SinkMode.REFUSING;
                owing.enrol(enrolment(false, null), false);
                audit.mode = SinkMode.WORKING;

                owing.rebaseline(PROJECT);

                assertAll(
                        () -> assertEquals(List.of(succeeded(AuditEvent.ENROLMENT_WRITTEN, PROJECT),
                                succeeded(AuditEvent.ENROLMENT_REBASELINED, PROJECT)), withoutTransactionIds(events)),
                        () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                                INFO.ENROLMENT_AUDIT_REPLAYED.resolveIdentifierString()),
                        () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                                REPLAYED.formatted(AuditEvent.ENROLMENT_WRITTEN)),
                        () -> assertFalse(owes(PROJECT)));
            }

            @Test
            @DisplayName("an unenrol of a project no longer enrolled hands over what it owes and still returns false")
            void unenrolOfNotEnrolled() {
                enrolments.enrol(enrolment(false, null), false);
                events.clear();
                audit.mode = SinkMode.REFUSING;
                owing.unenrol(PROJECT);
                audit.mode = SinkMode.WORKING;

                var removed = owing.unenrol(PROJECT);

                assertAll(
                        () -> assertFalse(removed),
                        () -> assertEquals(List.of(succeeded(AuditEvent.ENROLMENT_REMOVED, PROJECT)),
                                withoutTransactionIds(events)),
                        () -> assertFalse(owes(PROJECT)));
            }
        }

        @Nested
        @DisplayName("nothing is owed for what changed nothing")
        class NothingOwed {

            @Test
            @DisplayName("an enrol refused for a switched key source")
            void keySourceRefusal() {
                enrolments.enrol(enrolment(false, null), false);
                var keyring = enrolmentsWith(new EnrolmentKey(new InMemoryKeyring(), state, USER, new SecureRandom()),
                        audit);
                var other = enrolment(ALPHA, false, null);

                assertThrows(StoreRefusal.class, () -> keyring.enrol(other, false));

                assertAll(
                        () -> assertFalse(owes(ALPHA)),
                        () -> assertTrue(store.auditOwingProjectIds().isEmpty()));
            }

            @Test
            @DisplayName("an enrol whose integrity key cannot be read")
            void unreadableKey() throws Exception {
                enrolments.enrol(enrolment(false, null), false);
                Files.writeString(key.keyFile(), "not base64!");
                var other = enrolment(ALPHA, false, null);

                assertThrows(SecretStoreException.class, () -> owing.enrol(other, false));

                assertAll(
                        () -> assertFalse(owes(ALPHA)),
                        () -> assertTrue(store.auditOwingProjectIds().isEmpty()));
            }

            @Test
            @DisplayName("an unenrol refused for live work, which still audits unenrol_refused directly")
            void liveWorkRefusal() {
                enrolments.enrol(enrolment(false, null), false);
                events.clear();
                liveWork.put(PROJECT, List.of(new Item(Kind.PLAN, LIVE_PLAN)));

                assertThrows(ScopeActiveException.class, () -> owing.unenrol(PROJECT));

                assertAll(
                        () -> assertFalse(owes(PROJECT)),
                        () -> assertEquals(List.of(AuditEvent.unenrolRefused(PROJECT)), events));
            }

            @ParameterizedTest
            @ValueSource(strings = {"absent", "insecure", "unreadable"})
            @DisplayName("a refused re-baseline")
            void refusedRebaseline(String damage) throws Exception {
                if (!"absent".equals(damage)) {
                    enrolments.enrol(enrolment(false, null), false);
                }
                if ("insecure".equals(damage)) {
                    Files.setPosixFilePermissions(store.file(PROJECT), PosixFilePermissions.fromString(WORLD_WRITABLE));
                } else if ("unreadable".equals(damage)) {
                    Files.writeString(store.file(PROJECT), "{\"format_version\":1}");
                }
                events.clear();

                assertThrows(StoreRefusal.class, () -> owing.rebaseline(PROJECT));

                assertAll(
                        () -> assertFalse(owes(PROJECT)),
                        () -> assertTrue(events.stream()
                                .noneMatch(event -> AuditEvent.ENROLMENT_REBASELINED.equals(event.event()))));
            }
        }

        @Test
        @DisplayName("the replay hands over every owing project in the order of its id, each with its own transaction")
        void twoProjects() throws Exception {
            audit.mode = SinkMode.REFUSING;
            owing.enrol(enrolment(ZETA, false, null), false);
            var zetaIntent = Files.readAllBytes(store.auditIntent(ZETA));
            owing.enrol(enrolment(ALPHA, false, null), false);
            var zetaAfter = Files.readAllBytes(store.auditIntent(ZETA));
            audit.mode = SinkMode.WORKING;

            var replayed = owing.replayOwedAudit();

            assertAll(
                    () -> assertArrayEquals(zetaIntent, zetaAfter),
                    () -> assertEquals(2, replayed),
                    () -> assertEquals(List.of(succeeded(AuditEvent.ENROLMENT_WRITTEN, ALPHA),
                            succeeded(AuditEvent.ENROLMENT_WRITTEN, ZETA)), withoutTransactionIds(events)),
                    () -> assertEquals(2, events.stream().map(EnrolmentsTest::transactionIdOf).distinct().count()),
                    () -> assertTrue(store.auditOwingProjectIds().isEmpty()));
        }

        @Test
        @DisplayName("an intent file of one project that is unreadable does not stop the replay for the later projects; "
                + "the first refusal is thrown after them, the later one suppressed, every such file kept")
        void unreadableIntentDoesNotStopTheReplay() throws Exception {
            audit.mode = SinkMode.REFUSING;
            owing.enrol(enrolment(ALPHA, false, null), false);
            owing.enrol(enrolment(ZETA, false, null), false);
            var unreadable = Files.writeString(store.auditIntent(BETA), NOT_JSON);
            var newer = Files.writeString(store.auditIntent(GAMMA), NEWER_FORMAT);
            audit.mode = SinkMode.WORKING;

            var refusal = assertThrows(StoreRefusal.class, owing::replayOwedAudit);

            assertAll(
                    () -> assertEquals(unreadable, refusal.file()),
                    () -> assertEquals(1, refusal.getSuppressed().length),
                    () -> assertEquals(newer, ((StoreRefusal) refusal.getSuppressed()[0]).file()),
                    () -> assertEquals(List.of(succeeded(AuditEvent.ENROLMENT_WRITTEN, ALPHA),
                            succeeded(AuditEvent.ENROLMENT_WRITTEN, ZETA)), withoutTransactionIds(events)),
                    () -> assertEquals(NOT_JSON, Files.readString(unreadable)),
                    () -> assertEquals(NEWER_FORMAT, Files.readString(newer)),
                    () -> assertEquals(List.of(BETA, GAMMA), List.copyOf(store.auditOwingProjectIds())));
        }

        @Test
        @DisplayName("the replay of an intent file that is unreadable throws that refusal and nothing is suppressed")
        void singleUnreadableIntent() throws Exception {
            audit.mode = SinkMode.REFUSING;
            owing.enrol(enrolment(ZETA, false, null), false);
            var unreadable = Files.writeString(store.auditIntent(BETA), NOT_JSON);
            audit.mode = SinkMode.WORKING;

            var refusal = assertThrows(StoreRefusal.class, owing::replayOwedAudit);

            assertAll(
                    () -> assertEquals(unreadable, refusal.file()),
                    () -> assertEquals(0, refusal.getSuppressed().length),
                    () -> assertEquals(List.of(succeeded(AuditEvent.ENROLMENT_WRITTEN, ZETA)),
                            withoutTransactionIds(events)),
                    () -> assertEquals(NOT_JSON, Files.readString(unreadable)),
                    () -> assertEquals(List.of(BETA), List.copyOf(store.auditOwingProjectIds())));
        }

        /** Owes one record for each project: the enrol of each meets a refusing sink. */
        private void oweRecords(String... projectIds) {
            audit.mode = SinkMode.REFUSING;
            for (var projectId : projectIds) {
                owing.enrol(enrolment(projectId, false, null), false);
            }
            audit.mode = SinkMode.WORKING;
        }

        /** Puts a directory where the project's intent file is: reading it fails on I/O, not as a refusal. */
        private Path directoryIntent(String projectId) throws IOException {
            return Files.createDirectory(store.auditIntent(projectId));
        }

        @Test
        @DisplayName("an intent file that fails on I/O does not stop the replay for the later projects")
        void unreadableIntentIoFailureDoesNotStopTheReplay() throws Exception {
            oweRecords(ZETA);
            var directory = directoryIntent(ALPHA);

            var failure = assertThrows(UncheckedIOException.class, owing::replayOwedAudit);

            assertAll(
                    () -> assertEquals(0, failure.getSuppressed().length),
                    () -> assertEquals(List.of(succeeded(AuditEvent.ENROLMENT_WRITTEN, ZETA)),
                            withoutTransactionIds(events)),
                    () -> assertTrue(Files.isDirectory(directory)),
                    () -> assertEquals(List.of(ALPHA), List.copyOf(store.auditOwingProjectIds())));
        }

        @Test
        @DisplayName("a refusal before an I/O failure is thrown, the I/O failure suppressed")
        void refusalBeforeIoFailureThrowsTheRefusal() throws Exception {
            oweRecords(ZETA);
            var unreadable = Files.writeString(store.auditIntent(BETA), NOT_JSON);
            var directory = directoryIntent(GAMMA);

            var refusal = assertThrows(StoreRefusal.class, owing::replayOwedAudit);

            assertAll(
                    () -> assertEquals(unreadable, refusal.file()),
                    () -> assertEquals(1, refusal.getSuppressed().length),
                    () -> assertInstanceOf(UncheckedIOException.class, refusal.getSuppressed()[0]),
                    () -> assertEquals(List.of(succeeded(AuditEvent.ENROLMENT_WRITTEN, ZETA)),
                            withoutTransactionIds(events)),
                    () -> assertEquals(NOT_JSON, Files.readString(unreadable)),
                    () -> assertTrue(Files.isDirectory(directory)));
        }

        @Test
        @DisplayName("an I/O failure before a refusal is thrown, the refusal suppressed")
        void ioFailureBeforeRefusalThrowsTheIoFailure() throws Exception {
            oweRecords(ZETA);
            directoryIntent(ALPHA);
            var unreadable = Files.writeString(store.auditIntent(BETA), NOT_JSON);

            var failure = assertThrows(UncheckedIOException.class, owing::replayOwedAudit);

            assertAll(
                    () -> assertEquals(1, failure.getSuppressed().length),
                    () -> assertEquals(unreadable,
                            assertInstanceOf(StoreRefusal.class, failure.getSuppressed()[0]).file()),
                    () -> assertEquals(List.of(succeeded(AuditEvent.ENROLMENT_WRITTEN, ZETA)),
                            withoutTransactionIds(events)));
        }

        @Test
        @DisplayName("a stopped process still stops the replay at once")
        void aStoppedProcessStillStopsTheReplay() {
            oweRecords(ZETA, ALPHA);
            audit.mode = SinkMode.STOPPING;

            assertThrows(ProcessStopped.class, owing::replayOwedAudit);

            assertAll(
                    () -> assertEquals(List.of(ALPHA, ZETA), List.copyOf(store.auditOwingProjectIds())),
                    () -> assertTrue(events.isEmpty()));
        }

        @Test
        @DisplayName("the intent file is not an enrolment: neither listed nor counted by the guard of the key")
        void intentIsNotAnEnrolment() throws Exception {
            enrolments.enrol(enrolment(false, null), false);
            events.clear();
            audit.mode = SinkMode.REFUSING;
            owing.unenrol(PROJECT);
            Files.delete(key.keyFile());
            audit.mode = SinkMode.WORKING;

            var written = owing.enrol(enrolment(ALPHA, false, null), false);

            assertAll(
                    () -> assertEquals(ALPHA, written.projectId()),
                    () -> assertTrue(Files.exists(key.keyFile())),
                    () -> assertEquals(List.of(ALPHA), List.copyOf(store.projectIds())),
                    () -> assertEquals(List.of(PROJECT), List.copyOf(store.auditOwingProjectIds())),
                    () -> assertEquals(List.of(succeeded(AuditEvent.ENROLMENT_WRITTEN, ALPHA)),
                            withoutTransactionIds(events)));
        }

        /**
         * An unenrol stopped between the removal of the enrolment file and of its sidecar leaves an orphan sidecar and
         * the unconfirmed {@code enrolment_removed} record. The project recovery lists enrolment files only; the
         * background pass finds the project by its owed record, hands it over and removes the orphan sidecar.
         */
        @Nested
        @DisplayName("an unenrol stopped between the removal of the file and of the sidecar")
        class InterruptedUnenrol {

            /** Owes the unconfirmed record of an unenrol of the project, as the unenrol does before its mutation. */
            private AuditEvent oweRemoval(String projectId) {
                var unconfirmed = AuditEvent
                        .enrolmentRemoved(projectId, TransactionId.generate(Clock.systemUTC()).value()).notConfirmed();
                try (var transaction = locks.openTransaction()) {
                    transaction.acquire(store.lockKey(projectId));
                    store.oweAudit(transaction, projectId, unconfirmed);
                }
                return unconfirmed;
            }

            /** Leaves what the stop leaves: the sidecar without its file and the owed unconfirmed record. */
            private AuditEvent interruptUnenrol(String projectId) throws IOException {
                enrolments.enrol(enrolment(projectId, false, null), false);
                var unconfirmed = oweRemoval(projectId);
                Files.delete(store.file(projectId));
                events.clear();
                return unconfirmed;
            }

            /** Replaces the project's sidecar by a directory with an entry, so that it cannot be deleted. */
            private Path undeletableSidecar(String projectId) throws IOException {
                Files.delete(store.sidecar(projectId));
                var blocker = Files.createDirectory(store.sidecar(projectId));
                Files.writeString(blocker.resolve("entry"), projectId);
                return blocker;
            }

            private static Object projectOf(AuditEvent event) {
                return event.details().get(AuditEvent.DETAIL_PROJECT_ID);
            }

            @Test
            @DisplayName("the background pass hands the record over as not confirmed and removes the orphan sidecar")
            void settledByTheBackgroundPass() throws Exception {
                var unconfirmed = interruptUnenrol(PROJECT);

                var replayed = owing.replayOwedAuditOfUnenrolledProjects();
                var sidecarLeft = Files.exists(store.sidecar(PROJECT));
                var again = owing.replayOwedAuditOfUnenrolledProjects();

                assertAll(
                        () -> assertEquals(1, replayed),
                        () -> assertEquals(List.of(unconfirmed), events),
                        () -> assertEquals(AuditEvent.OUTCOME_FAILED, events.getFirst().outcome()),
                        () -> assertEquals(AuditEvent.REASON_NOT_CONFIRMED,
                                events.getFirst().details().get(AuditEvent.DETAIL_REASON)),
                        () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                                WARN.ENROLMENT_WRITE_NOT_CONFIRMED.resolveIdentifierString()),
                        () -> assertFalse(sidecarLeft),
                        () -> assertFalse(owes(PROJECT)),
                        () -> assertTrue(store.projectIds().isEmpty()),
                        () -> assertTrue(store.auditOwingProjectIds().isEmpty()),
                        () -> assertEquals(0, again));
            }

            @Test
            @DisplayName("the sweep of every project hands the same record over but leaves the sidecar in place")
            void sweepOfEveryProjectRemovesNothing() throws Exception {
                var unconfirmed = interruptUnenrol(PROJECT);

                var replayed = owing.replayOwedAudit();

                assertAll(
                        () -> assertEquals(1, replayed),
                        () -> assertEquals(List.of(unconfirmed), events),
                        () -> assertTrue(Files.exists(store.sidecar(PROJECT))),
                        () -> assertFalse(owes(PROJECT)));
            }

            @Test
            @DisplayName("a project whose enrolment file exists is left to its own recovery, even without a sidecar")
            void enrolmentFileLeftAlone() throws Exception {
                enrolments.enrol(enrolment(false, null), false);
                oweRemoval(PROJECT);
                Files.delete(store.sidecar(PROJECT));
                var file = Files.readAllBytes(store.file(PROJECT));
                var intent = Files.readAllBytes(store.auditIntent(PROJECT));
                events.clear();

                var replayed = owing.replayOwedAuditOfUnenrolledProjects();

                assertAll(
                        () -> assertEquals(0, replayed),
                        () -> assertTrue(events.isEmpty()),
                        () -> assertArrayEquals(file, Files.readAllBytes(store.file(PROJECT))),
                        () -> assertArrayEquals(intent, Files.readAllBytes(store.auditIntent(PROJECT))));
            }

            @Test
            @DisplayName("a sidecar that cannot be removed fails only its project; the others are settled first")
            void isolation() throws Exception {
                interruptUnenrol(ALPHA);
                var blocker = undeletableSidecar(ALPHA);
                interruptUnenrol(ZETA);
                enrolments.enrol(enrolment(BETA, false, null), false);
                audit.mode = SinkMode.REFUSING;
                owing.unenrol(BETA);
                audit.mode = SinkMode.WORKING;
                events.clear();

                var failure = assertThrows(UncheckedIOException.class,
                        owing::replayOwedAuditOfUnenrolledProjects);

                assertAll(
                        () -> assertTrue(failure.getMessage().contains("'" + ALPHA + "'"), failure::getMessage),
                        () -> assertEquals(0, failure.getSuppressed().length),
                        () -> assertEquals(List.of(ALPHA, BETA, ZETA),
                                events.stream().map(InterruptedUnenrol::projectOf).toList()),
                        () -> assertTrue(Files.isDirectory(blocker)),
                        () -> assertFalse(Files.exists(store.sidecar(ZETA))),
                        () -> assertTrue(store.auditOwingProjectIds().isEmpty()));
            }

            @Test
            @DisplayName("running unenrol again removes the sidecar, hands both records over in order, and enrol works")
            void unenrolAgain() throws Exception {
                var unconfirmed = interruptUnenrol(PROJECT);

                var removed = owing.unenrol(PROJECT);
                var sidecarLeft = Files.exists(store.sidecar(PROJECT));
                var handedOver = List.copyOf(events);
                var written = owing.enrol(enrolment(false, null), false);

                assertAll(
                        () -> assertTrue(removed),
                        () -> assertFalse(sidecarLeft),
                        () -> assertEquals(2, handedOver.size()),
                        () -> assertEquals(unconfirmed, handedOver.getFirst()),
                        () -> assertEquals(succeeded(AuditEvent.ENROLMENT_REMOVED, PROJECT),
                                withoutTransactionIds(handedOver).getLast()),
                        () -> assertNotEquals(transactionIdOf(unconfirmed), transactionIdOf(handedOver.getLast())),
                        () -> assertEquals(written, verify()),
                        () -> assertFalse(owes(PROJECT)));
            }
        }
    }

    /**
     * The enrolment policy before signing (PM-EXEC-1): {@code enrol} and the re-baseline refuse an enrolment that
     * violates it as {@code enrolment_invalid}, logged and audited, before the key is resolved or anything is owed.
     */
    @Nested
    @DisplayName("the enrolment policy")
    class Policy {

        private static final String PLACEHOLDER = "placeholder";
        private static final String WRITE_PATH = "write path";
        private static final String ENVIRONMENT = "environment";
        private static final String MISSING = "missing";

        /** A directory beside the base, outside it. */
        @TempDir
        Path outside;

        private ProjectEnrolment violating(String family) {
            var build = Build.defaults();
            var writePaths = List.<String>of();
            var environment = new TreeMap<String, String>();
            switch (family) {
                case PLACEHOLDER -> build = new Build(Map.of(ProjectEnrolment.BuildSystem.MAVEN,
                        new TreeMap<>(Map.of("verify", ProjectEnrolment.Argv.of("mvn", "{goal}")))), Map.of());
                case WRITE_PATH -> writePaths = List.of(base.resolve("cache").toString());
                default -> environment.put("GH_TOKEN", "ghp");
            }
            return enrolmentOf(build, writePaths, environment);
        }

        private static ProjectEnrolment enrolmentOf(Build build, List<String> writePaths,
                TreeMap<String, String> environment) {
            return new ProjectEnrolment(PROJECT, "/home/dev/git/" + PROJECT, Instant.parse("2026-10-11T08:00:00Z"),
                    false, null, null, List.of(), List.of(), build, Map.of(), new TreeMap<>(), new TreeMap<>(),
                    new TreeMap<>(), writePaths, environment);
        }

        private static ProjectEnrolment writingTo(Path writePath) {
            return enrolmentOf(Build.defaults(), List.of(writePath.toString()), new TreeMap<>());
        }

        @Test
        @DisplayName("enrol refuses a missing write-path leaf below a symbolic link into the base before anything exists")
        void enrolRefusedForMissingLeafBelowLinkIntoBase() throws Exception {
            var link = Files.createSymbolicLink(outside.resolve("link"), store.base());
            var enrolment = writingTo(link.resolve(MISSING));
            var reason = reasonOf(enrolment);

            var refusal = assertThrows(StoreRefusal.class, () -> enrolments.enrol(enrolment, false));

            assertRefusedInvalid(refusal, reason);
            assertAll(
                    () -> assertFalse(reason.isEmpty()),
                    () -> assertFalse(Files.exists(store.file(PROJECT))),
                    () -> assertFalse(Files.exists(store.sidecar(PROJECT))),
                    () -> assertFalse(Files.exists(key.keyFile())),
                    () -> assertTrue(store.auditOwingProjectIds().isEmpty()));
        }

        @Test
        @DisplayName("enrol writes an enrolment whose write path is an ordinary missing path outside the base")
        void enrolAdmitsMissingPathOutsideTheBase() {
            var enrolment = writingTo(outside.resolve(MISSING));

            var written = enrolments.enrol(enrolment, false);

            assertAll(
                    () -> assertEquals(written, verify()),
                    () -> assertEquals(enrolment.writePaths(), written.writePaths()),
                    () -> assertTrue(events.stream().noneMatch(
                            event -> AuditEvent.ENROLMENT_INVALID.equals(event.event()))));
        }

        private String reasonOf(ProjectEnrolment enrolment) {
            return String.join("; ", EnrolmentPolicy.violations(enrolment, store.base()));
        }

        private void assertRefusedInvalid(StoreRefusal refusal, String reason) {
            assertAll(
                    () -> assertEquals(Outcome.ENROLMENT_INVALID, refusal.outcome()),
                    () -> assertEquals(store.file(PROJECT), refusal.file()),
                    () -> assertTrue(refusal.getMessage().contains(reason), refusal.getMessage()),
                    () -> assertEquals(List.of(AuditEvent.enrolmentInvalid(PROJECT, reason)), events),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.ENROLMENT_INVALID.resolveIdentifierString()),
                    () -> assertFalse(Files.exists(store.auditIntent(PROJECT))));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {PLACEHOLDER, WRITE_PATH, ENVIRONMENT})
        @DisplayName("enrol refuses a violating enrolment before the key, the intent or a file exists")
        void enrolRefused(String family) {
            var enrolment = violating(family);
            var reason = reasonOf(enrolment);

            var refusal = assertThrows(StoreRefusal.class, () -> enrolments.enrol(enrolment, false));

            assertRefusedInvalid(refusal, reason);
            assertAll(
                    () -> assertFalse(reason.isEmpty()),
                    () -> assertFalse(Files.exists(store.file(PROJECT))),
                    () -> assertFalse(Files.exists(store.sidecar(PROJECT))),
                    () -> assertFalse(Files.exists(key.keyFile())),
                    () -> assertTrue(store.auditOwingProjectIds().isEmpty()));
        }

        @Test
        @DisplayName("a compliant enrolment is written and verifies, and nothing is refused")
        void compliantControl() {
            var written = enrolments.enrol(enrolment(false, null), false);

            assertAll(
                    () -> assertEquals(written, verify()),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN,
                            WARN.ENROLMENT_INVALID.resolveIdentifierString()),
                    () -> assertTrue(events.stream().noneMatch(
                            event -> AuditEvent.ENROLMENT_INVALID.equals(event.event()))));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {PLACEHOLDER, WRITE_PATH, ENVIRONMENT})
        @DisplayName("the re-baseline refuses an edited file that violates the policy, and owes nothing")
        void rebaselineRefused(String family) throws Exception {
            enrolments.enrol(enrolment(false, null), false);
            var sidecar = Files.readAllBytes(store.sidecar(PROJECT));
            var edited = violating(family);
            Files.write(store.file(PROJECT), EnrolmentCodec.write(edited));
            Files.setPosixFilePermissions(store.file(PROJECT), PosixFilePermissions.fromString(OWNER_ONLY));
            var reason = reasonOf(edited);
            events.clear();

            var refusal = assertThrows(StoreRefusal.class, () -> enrolments.rebaseline(PROJECT));

            assertRefusedInvalid(refusal, reason);
            assertArrayEquals(sidecar, Files.readAllBytes(store.sidecar(PROJECT)));
        }

        @Test
        @DisplayName("a failing audit sink does not lift the refusal; its failure is the only suppressed exception")
        void failingSink() {
            var failure = auditFailure();
            var failing = enrolmentsWith(key, failingWith(failure));
            var enrolment = violating(ENVIRONMENT);

            var refusal = assertThrows(StoreRefusal.class, () -> failing.enrol(enrolment, false));

            assertAll(
                    () -> assertEquals(Outcome.ENROLMENT_INVALID, refusal.outcome()),
                    () -> assertEquals(List.of(failure), List.of(refusal.getSuppressed())),
                    () -> assertFalse(Files.exists(store.file(PROJECT))));
        }
    }

    @Test
    @DisplayName("a re-baseline outcome refuses missing components and keeps its own copy of what it settled")
    void rebaselineComponents() {
        var digest = StoreDigest.of(new byte[0]);
        var file = base.resolve("f");
        var none = List.<PairRecovery>of();
        var settled = new ArrayList<PairRecovery>();
        var outcome = new Enrolments.Rebaseline(file, Optional.empty(), digest, settled);
        settled.add(new PairRecovery.Completed(TransactionId.generate(Clock.systemUTC()), List.of(file)));
        var reported = outcome.settled();

        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> new Enrolments.Rebaseline(null, Optional.empty(), digest, none)),
                () -> assertThrows(NullPointerException.class,
                        () -> new Enrolments.Rebaseline(file, null, digest, none)),
                () -> assertThrows(NullPointerException.class,
                        () -> new Enrolments.Rebaseline(file, Optional.empty(), null, none)),
                () -> assertThrows(NullPointerException.class,
                        () -> new Enrolments.Rebaseline(file, Optional.empty(), digest, null)),
                () -> assertTrue(reported.isEmpty()),
                () -> assertThrows(UnsupportedOperationException.class, reported::clear));
    }
}
