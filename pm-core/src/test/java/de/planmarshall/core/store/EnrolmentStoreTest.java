/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

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
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.log.PmMcpLogMessages.WARN;
import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.InternalFaultException.Reason;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.ProjectEnrolment.Build;
import de.planmarshall.core.store.StoreRefusal.Outcome;

/**
 * The project enrolment store (PM-EXEC-2, PM-IMPL-7): one file and one sidecar per project, read and written only
 * under the project's own lock, the set of enrolled projects being the listing of the directory. An operator write
 * owes its audit record in the project's intent file before the mutation; the intent keeps the record through a
 * refused or stopped hand-over and is removed only after the sink accepted every record it owes.
 */
@EnableTestLogger
@DisplayName("Enrolment store")
class EnrolmentStoreTest {

    private static final String PROJECT = "pm-mcp-core";
    private static final String HMAC = "b".repeat(64);

    @TempDir
    Path base;

    private EnrolmentStore store;
    private RecordingLockManager locks;

    @BeforeEach
    void createDirectory() throws IOException {
        Files.createDirectories(base.resolve(EnrolmentStore.DIRECTORY_NAME));
        store = new EnrolmentStore(base);
        locks = new RecordingLockManager();
    }

    private static ProjectEnrolment enrolment(String projectId, boolean pilot) {
        return new ProjectEnrolment(projectId, "/home/dev/git/" + projectId, Instant.parse("2026-10-11T08:00:00Z"),
                pilot, null, null, List.of(), List.of(), Build.defaults(), Map.of(), new TreeMap<>(),
                new TreeMap<>(), new TreeMap<>(), List.of(), new TreeMap<>());
    }

    private static EnrolmentSidecar sidecarOf(ProjectEnrolment enrolment) {
        return new EnrolmentSidecar(StoreDigest.of(EnrolmentCodec.write(enrolment)), HMAC);
    }

    private void enrol(ProjectEnrolment enrolment) {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(store.lockKey(enrolment.projectId()));
            store.write(transaction, enrolment, sidecarOf(enrolment));
        }
    }

    @Test
    @DisplayName("the file, its sidecar and its lock have the paths of the enrolment store")
    void paths() {
        assertAll(
                () -> assertEquals(base.toAbsolutePath().normalize().resolve("enrolments/pm-mcp-core.json"),
                        store.file(PROJECT)),
                () -> assertEquals(base.toAbsolutePath().normalize().resolve("enrolments/pm-mcp-core.json.hmac"),
                        store.sidecar(PROJECT)),
                () -> assertEquals(LockKey.enrolment(base, PROJECT), store.lockKey(PROJECT)),
                () -> assertThrows(IllegalArgumentException.class, () -> store.file("../projects")));
    }

    @Nested
    @DisplayName("under the project's lock")
    class UnderLock {

        @Test
        @DisplayName("a written enrolment is read back as its canonical bytes and its sidecar record")
        void writeAndRead() {
            var enrolment = enrolment(PROJECT, true);

            enrol(enrolment);

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(store.lockKey(PROJECT));
                var content = store.readFile(transaction, PROJECT).orElseThrow();
                var sidecar = store.decodeSidecar(PROJECT, store.readSidecar(transaction, PROJECT).orElseThrow());

                assertAll(
                        () -> assertArrayEquals(EnrolmentCodec.write(enrolment), content),
                        () -> assertEquals(enrolment, store.decode(PROJECT, content)),
                        () -> assertEquals(sidecarOf(enrolment), sidecar),
                        () -> assertTrue(sidecar.matches(content)));
            }
            assertEquals(List.of(LockKey.enrolment(base, PROJECT), LockKey.enrolment(base, PROJECT)),
                    locks.acquisitions());
        }

        @Test
        @DisplayName("a project without an enrolment reads as no file and no sidecar")
        void absent() {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(store.lockKey(PROJECT));

                assertAll(
                        () -> assertTrue(store.readFile(transaction, PROJECT).isEmpty()),
                        () -> assertTrue(store.readSidecar(transaction, PROJECT).isEmpty()));
            }
        }

        @Test
        @DisplayName("a sidecar that does not record the canonical bytes is refused and nothing is written")
        void mismatchedSidecar() {
            var enrolment = enrolment(PROJECT, false);
            var other = sidecarOf(enrolment(PROJECT, true));

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(store.lockKey(PROJECT));

                assertThrows(IllegalArgumentException.class, () -> store.write(transaction, enrolment, other));
            }
            assertAll(
                    () -> assertFalse(Files.exists(store.file(PROJECT))),
                    () -> assertFalse(Files.exists(store.sidecar(PROJECT))));
        }

        @Test
        @DisplayName("a re-baseline replaces the sidecar only")
        void writeSidecar() throws Exception {
            var enrolment = enrolment(PROJECT, false);
            enrol(enrolment);
            var file = Files.readAllBytes(store.file(PROJECT));
            var rebaselined = new EnrolmentSidecar(StoreDigest.of(file), "c".repeat(64));

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(store.lockKey(PROJECT));
                store.writeSidecar(transaction, PROJECT, rebaselined);
            }

            assertAll(
                    () -> assertArrayEquals(file, Files.readAllBytes(store.file(PROJECT))),
                    () -> assertArrayEquals(rebaselined.write(), Files.readAllBytes(store.sidecar(PROJECT))));
        }

        @Test
        @DisplayName("a removal deletes the file and its sidecar and nothing else, and reports what it found")
        void remove() throws Exception {
            enrol(enrolment(PROJECT, false));
            var projectStore = Files.createDirectories(base.resolve("projects").resolve(PROJECT));
            Files.writeString(projectStore.resolve("store.json"), "{}");

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(store.lockKey(PROJECT));

                assertTrue(store.remove(transaction, PROJECT));
                assertFalse(store.remove(transaction, PROJECT));
            }
            assertAll(
                    () -> assertFalse(Files.exists(store.file(PROJECT))),
                    () -> assertFalse(Files.exists(store.sidecar(PROJECT))),
                    () -> assertEquals("{}", Files.readString(projectStore.resolve("store.json"))));
        }
    }

    @Nested
    @DisplayName("without the project's lock")
    class WithoutLock {

        @Test
        @DisplayName("every read and write is refused as an internal fault, and another project's lock does not count")
        void refused() {
            var enrolment = enrolment(PROJECT, false);
            var sidecar = sidecarOf(enrolment);

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(store.lockKey("other-project"));

                assertAll(
                        () -> assertEquals(Reason.STORE_LOCK_NOT_HELD, assertThrows(InternalFaultException.class,
                                () -> store.write(transaction, enrolment, sidecar)).reason()),
                        () -> assertThrows(InternalFaultException.class, () -> store.readFile(transaction, PROJECT)),
                        () -> assertThrows(InternalFaultException.class,
                                () -> store.readSidecar(transaction, PROJECT)),
                        () -> assertThrows(InternalFaultException.class,
                                () -> store.writeSidecar(transaction, PROJECT, sidecar)),
                        () -> assertThrows(InternalFaultException.class, () -> store.remove(transaction, PROJECT)));
            }
            assertFalse(Files.exists(store.file(PROJECT)));
        }
    }

    @Nested
    @DisplayName("listing and decoding")
    class ListingAndDecoding {

        @Test
        @DisplayName("the enrolled projects are the enrolment files of the directory, not sidecars or other entries")
        void projectIds() throws Exception {
            enrol(enrolment("zeta-project", false));
            enrol(enrolment(PROJECT, false));
            Files.writeString(store.directory().resolve("notes.txt"), "x");
            Files.writeString(store.directory().resolve("Upper.json"), "{}");

            assertEquals(Set.of(PROJECT, "zeta-project"), store.projectIds());
        }

        @Test
        @DisplayName("a store without its directory lists no project")
        void noDirectory() {
            assertTrue(new EnrolmentStore(base.resolve("elsewhere")).projectIds().isEmpty());
        }

        @Test
        @DisplayName("an enrolment whose project_id is not the name of its file is refused as unreadable")
        void foreignProjectId() {
            var content = EnrolmentCodec.write(enrolment("other-project", false));

            var refusal = assertThrows(StoreRefusal.class, () -> store.decode(PROJECT, content));

            assertAll(
                    () -> assertEquals(Outcome.UNREADABLE, refusal.outcome()),
                    () -> assertEquals(store.file(PROJECT), refusal.file()));
        }

        @Test
        @DisplayName("an unreadable sidecar is refused naming the sidecar file")
        void unreadableSidecar() {
            var content = HMAC.getBytes(StandardCharsets.UTF_8);

            var refusal = assertThrows(StoreRefusal.class, () -> store.decodeSidecar(PROJECT, content));

            assertEquals(store.sidecar(PROJECT), refusal.file());
        }
    }

    @Nested
    @DisplayName("the audit intent of the operator writes")
    class AuditIntent {

        private static final String ZETA = "zeta-project";

        private void inLock(String projectId, Consumer<LockTransaction> action) {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(store.lockKey(projectId));
                action.accept(transaction);
            }
        }

        private void owe(String projectId, AuditEvent... events) {
            inLock(projectId, transaction -> {
                for (var event : events) {
                    store.oweAudit(transaction, projectId, event);
                }
            });
        }

        private List<AuditEvent> owed(EnrolmentStore enrolmentStore) {
            var owed = new ArrayList<AuditEvent>();
            inLock(PROJECT, transaction -> owed.addAll(enrolmentStore.owedAudit(transaction, PROJECT)));
            return owed;
        }

        private List<AuditEvent> deliver(AuditSink sink) {
            var delivered = new ArrayList<AuditEvent>();
            inLock(PROJECT, transaction -> delivered.addAll(store.deliverOwedAudit(transaction, PROJECT, sink)));
            return delivered;
        }

        @Test
        @DisplayName("the intent file is <project_id>.json.audit-intent beside the enrolment file")
        void intentPath() {
            assertEquals(base.toAbsolutePath().normalize().resolve("enrolments/pm-mcp-core.json.audit-intent"),
                    store.auditIntent(PROJECT));
        }

        @Nested
        @DisplayName("owing a record")
        class Owe {

            @Test
            @DisplayName("writes the record durably to the intent file, where a later reader finds it")
            void owesDurably() {
                var written = written();

                owe(PROJECT, written);

                assertAll(
                        () -> assertTrue(Files.exists(store.auditIntent(PROJECT))),
                        () -> assertEquals(List.of(written), owed(store)),
                        () -> assertEquals(List.of(written), owed(new EnrolmentStore(base))));
            }

            @Test
            @DisplayName("keeps a second record of another transaction after the first, in order")
            void keepsOrder() {
                var first = written();
                var second = AuditEvent.enrolmentRebaselined(PROJECT, transactionId());

                owe(PROJECT, first, second);

                assertEquals(List.of(first, second), owed(store));
            }

            @Test
            @DisplayName("does not owe a record twice whose transaction id is already owed")
            void idempotent() throws Exception {
                var id = transactionId();
                owe(PROJECT, AuditEvent.enrolmentWritten(PROJECT, id));
                var before = Files.readAllBytes(store.auditIntent(PROJECT));

                owe(PROJECT, AuditEvent.enrolmentWritten(PROJECT, id), AuditEvent.enrolmentRemoved(PROJECT, id));

                assertArrayEquals(before, Files.readAllBytes(store.auditIntent(PROJECT)));
            }

            @Test
            @DisplayName("refuses a record with a detail an intent cannot hold, and writes nothing")
            void notWritable() {
                var event = new AuditEvent(AuditEvent.ENROLMENT_WRITTEN, AuditEvent.CATEGORY_OPERATOR,
                        AuditEvent.OUTCOME_SUCCEEDED, Map.of(AuditEvent.DETAIL_PROJECT_ID, true));

                try (var transaction = locks.openTransaction()) {
                    transaction.acquire(store.lockKey(PROJECT));

                    assertThrows(IllegalArgumentException.class, () -> store.oweAudit(transaction, PROJECT, event));
                }
                assertFalse(Files.exists(store.auditIntent(PROJECT)));
            }
        }

        @Nested
        @DisplayName("listing")
        class Listing {

            @Test
            @DisplayName("the intent file is not an enrolment, and the owing projects are exactly those with an intent")
            void intentIsNotAnEnrolment() throws Exception {
                enrol(enrolment(PROJECT, false));
                enrol(enrolment("alpha-project", false));
                owe(ZETA, AuditEvent.enrolmentRemoved(ZETA, transactionId()));
                owe(PROJECT, written());
                Files.writeString(store.directory().resolve("Upper.json.audit-intent"), "{}");

                assertAll(
                        () -> assertEquals(List.of("alpha-project", PROJECT), List.copyOf(store.projectIds())),
                        () -> assertEquals(List.of(PROJECT, ZETA), List.copyOf(store.auditOwingProjectIds())));
            }

            @Test
            @DisplayName("a store without its directory lists no owing project")
            void noDirectory() {
                assertTrue(new EnrolmentStore(base.resolve("elsewhere")).auditOwingProjectIds().isEmpty());
            }

            @Test
            @DisplayName("a store without an intent lists no owing project")
            void noIntent() {
                enrol(enrolment(PROJECT, false));

                assertTrue(store.auditOwingProjectIds().isEmpty());
            }
        }

        @Nested
        @DisplayName("handing over")
        class HandOver {

            @Test
            @DisplayName("an accepting sink receives the owed records in order, and the intent file is removed")
            void accepted() {
                var first = written();
                var second = AuditEvent.enrolmentRebaselined(PROJECT, transactionId());
                owe(PROJECT, first, second);
                var sink = new RecordingAuditSink();

                var delivered = deliver(sink);

                assertAll(
                        () -> assertEquals(List.of(first, second), delivered),
                        () -> assertEquals(List.of(first, second), sink.events()),
                        () -> assertFalse(Files.exists(store.auditIntent(PROJECT))));
            }

            @Test
            @DisplayName("with nothing owed nothing is handed over and nothing is touched")
            void nothingOwed() throws Exception {
                enrol(enrolment(PROJECT, false));
                var before = listing();
                var sink = new RecordingAuditSink();

                var delivered = deliver(sink);

                assertAll(
                        () -> assertTrue(delivered.isEmpty()),
                        () -> assertTrue(sink.events().isEmpty()),
                        () -> assertEquals(before, listing()));
            }

            @Test
            @DisplayName("a sink that refuses the first record keeps every record owed, warns once and throws nothing")
            void refused() {
                var first = written();
                var second = AuditEvent.enrolmentRebaselined(PROJECT, transactionId());
                owe(PROJECT, first, second);
                var sink = FailingAuditSink.refusing(1);

                var delivered = deliver(sink);

                assertAll(
                        () -> assertTrue(delivered.isEmpty()),
                        () -> assertEquals(List.of(first, second), owed(store)),
                        () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                                WARN.AUDIT_RECORD_DEFERRED.resolveIdentifierString()),
                        () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                                "'" + AuditEvent.ENROLMENT_WRITTEN + "' of transaction '"
                                        + first.details().get(AuditEvent.DETAIL_TRANSACTION_ID) + "'"));

                var again = deliver(sink);

                assertAll(
                        () -> assertEquals(List.of(first, second), again),
                        () -> assertEquals(List.of(first, second), sink.events()),
                        () -> assertFalse(Files.exists(store.auditIntent(PROJECT))));
            }

            @Test
            @DisplayName("a sink that refuses the second record keeps only the second owed, and hands the first once")
            void partial() {
                var first = written();
                var second = AuditEvent.enrolmentRebaselined(PROJECT, transactionId());
                owe(PROJECT, first, second);
                var accepted = new ArrayList<AuditEvent>();
                AuditSink sink = event -> {
                    if (!accepted.isEmpty()) {
                        throw new UncheckedIOException(new IOException("the audit log is full"));
                    }
                    accepted.add(event);
                };

                var delivered = deliver(sink);

                assertAll(
                        () -> assertEquals(List.of(first), delivered),
                        () -> assertEquals(List.of(first), accepted),
                        () -> assertEquals(List.of(second), owed(store)));

                var recording = new RecordingAuditSink();
                var rest = deliver(recording);

                assertAll(
                        () -> assertEquals(List.of(second), rest),
                        () -> assertEquals(List.of(second), recording.events()),
                        () -> assertFalse(Files.exists(store.auditIntent(PROJECT))));
            }

            @Test
            @DisplayName("a stop at the hand-over leaves the intent, and a new transaction hands the record over once")
            void stopped() {
                var written = written();
                owe(PROJECT, written);
                var stopping = FailingAuditSink.stopping(1);

                try (var transaction = locks.openTransaction()) {
                    transaction.acquire(store.lockKey(PROJECT));

                    assertThrows(FailingAuditSink.ProcessStopped.class,
                            () -> store.deliverOwedAudit(transaction, PROJECT, stopping));
                }
                assertEquals(List.of(written), owed(store));

                store = new EnrolmentStore(base);
                var sink = new RecordingAuditSink();
                var delivered = deliver(sink);

                assertAll(
                        () -> assertEquals(List.of(written), delivered),
                        () -> assertEquals(List.of(written), sink.events()),
                        () -> assertFalse(Files.exists(store.auditIntent(PROJECT))));
            }
        }

        @Nested
        @DisplayName("refusals")
        class Refusals {

            @Test
            @DisplayName("every access is refused without the project's lock, and another project's lock does not count")
            void withoutLock() {
                var written = written();
                var sink = new RecordingAuditSink();

                try (var transaction = locks.openTransaction()) {
                    transaction.acquire(store.lockKey("other-project"));

                    assertAll(
                            () -> assertEquals(Reason.STORE_LOCK_NOT_HELD, assertThrows(InternalFaultException.class,
                                    () -> store.oweAudit(transaction, PROJECT, written)).reason()),
                            () -> assertThrows(InternalFaultException.class,
                                    () -> store.owedAudit(transaction, PROJECT)),
                            () -> assertThrows(InternalFaultException.class,
                                    () -> store.deliverOwedAudit(transaction, PROJECT, sink)),
                            () -> assertThrows(InternalFaultException.class,
                                    () -> store.exists(transaction, PROJECT)));
                }
                assertAll(
                        () -> assertFalse(Files.exists(store.auditIntent(PROJECT))),
                        () -> assertTrue(sink.events().isEmpty()));
            }

            @ParameterizedTest
            @ValueSource(strings = {"not json", "{\"format_version\":2,\"owed_events\":[]}",
                    "{\"format_version\":1}"})
            @DisplayName("an intent file that is unreadable or of another version is refused naming it, and kept")
            void unreadableIntent(String content) throws Exception {
                var intent = Files.writeString(store.auditIntent(PROJECT), content);
                var written = written();
                var sink = new RecordingAuditSink();

                try (var transaction = locks.openTransaction()) {
                    transaction.acquire(store.lockKey(PROJECT));

                    assertAll(
                            () -> assertEquals(intent, assertThrows(StoreRefusal.class,
                                    () -> store.owedAudit(transaction, PROJECT)).file()),
                            () -> assertEquals(intent, assertThrows(StoreRefusal.class,
                                    () -> store.oweAudit(transaction, PROJECT, written)).file()),
                            () -> assertEquals(intent, assertThrows(StoreRefusal.class,
                                    () -> store.deliverOwedAudit(transaction, PROJECT, sink)).file()));
                }
                assertAll(
                        () -> assertEquals(content, Files.readString(intent)),
                        () -> assertTrue(sink.events().isEmpty()));
            }
        }

        @ParameterizedTest
        @CsvSource({"true,false", "false,true", "true,true", "false,false"})
        @DisplayName("an enrolment exists while its file or its sidecar exists")
        void exists(boolean file, boolean sidecar) throws Exception {
            if (file) {
                Files.writeString(store.file(PROJECT), "{}");
            }
            if (sidecar) {
                Files.writeString(store.sidecar(PROJECT), HMAC);
            }
            var exists = new AtomicBoolean();

            inLock(PROJECT, transaction -> exists.set(store.exists(transaction, PROJECT)));

            assertEquals(file || sidecar, exists.get());
        }

        private AuditEvent written() {
            return AuditEvent.enrolmentWritten(PROJECT, transactionId());
        }

        private List<Path> listing() throws IOException {
            try (var entries = Files.list(store.directory())) {
                return entries.sorted().toList();
            }
        }
    }

    private static String transactionId() {
        return TransactionId.generate(Clock.systemUTC()).value();
    }
}
