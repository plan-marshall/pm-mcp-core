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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.log.PmMcpLogMessages.INFO;
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
        @DisplayName("confirming a record")
        class Confirm {

            private void confirm(AuditEvent confirmed) {
                inLock(PROJECT, transaction -> store.confirmAudit(transaction, PROJECT, confirmed));
            }

            @Test
            @DisplayName("replaces the unconfirmed record of the same transaction by the succeeded one, in its place")
            void replacesInPlace() {
                var first = AuditEvent.enrolmentWritten(PROJECT, transactionId());
                var second = AuditEvent.enrolmentRebaselined(PROJECT, transactionId());
                owe(PROJECT, first, second.notConfirmed());

                confirm(second);

                assertEquals(List.of(first, second), owed(store));
            }

            @Test
            @DisplayName("keeps the other unconfirmed record unconfirmed, which a stop before its confirmation leaves")
            void keepsOthers() {
                var first = AuditEvent.enrolmentWritten(PROJECT, transactionId());
                var second = AuditEvent.enrolmentRebaselined(PROJECT, transactionId());
                owe(PROJECT, first.notConfirmed(), second.notConfirmed());

                confirm(second);

                assertEquals(List.of(first.notConfirmed(), second), owed(store));
            }

            @Test
            @DisplayName("owes the record afresh when no record of its transaction is owed")
            void owesAfresh() {
                var other = AuditEvent.enrolmentRemoved(PROJECT, transactionId());
                var confirmed = written();
                owe(PROJECT, other);

                confirm(confirmed);

                assertEquals(List.of(other, confirmed), owed(store));
            }

            @Test
            @DisplayName("hands the confirmed record over once, with the transaction id it was owed with")
            void handedOverConfirmed() {
                var confirmed = written();
                owe(PROJECT, confirmed.notConfirmed());
                confirm(confirmed);
                var sink = new RecordingAuditSink();

                var delivered = deliver(sink);

                assertAll(
                        () -> assertEquals(List.of(confirmed), delivered),
                        () -> assertEquals(List.of(confirmed), sink.events()),
                        () -> assertFalse(Files.exists(store.auditIntent(PROJECT))));
            }

            @Test
            @DisplayName("hands the unconfirmed record over as it is when it was never confirmed")
            void handedOverUnconfirmed() {
                var unconfirmed = written().notConfirmed();
                owe(PROJECT, unconfirmed);
                var sink = new RecordingAuditSink();

                var delivered = deliver(sink);

                assertAll(
                        () -> assertEquals(List.of(unconfirmed), delivered),
                        () -> assertEquals("failed", unconfirmed.outcome()),
                        () -> assertFalse(Files.exists(store.auditIntent(PROJECT))));
            }

            @Test
            @DisplayName("refuses a record without a transaction id or with a detail an intent cannot hold")
            void refusedRecords() {
                var withoutTransaction = new AuditEvent(AuditEvent.ENROLMENT_WRITTEN, AuditEvent.CATEGORY_OPERATOR,
                        AuditEvent.OUTCOME_SUCCEEDED, Map.of(AuditEvent.DETAIL_PROJECT_ID, PROJECT));
                var notWritable = new AuditEvent(AuditEvent.ENROLMENT_WRITTEN, AuditEvent.CATEGORY_OPERATOR,
                        AuditEvent.OUTCOME_SUCCEEDED, Map.of(AuditEvent.DETAIL_PROJECT_ID, true));

                assertAll(
                        () -> assertThrows(IllegalArgumentException.class, () -> confirm(notWritable)),
                        () -> assertThrows(NullPointerException.class, () -> confirm(withoutTransaction)),
                        () -> assertFalse(Files.exists(store.auditIntent(PROJECT))));
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
                                    () -> store.confirmAudit(transaction, PROJECT, written)),
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
                                    () -> store.confirmAudit(transaction, PROJECT, written)).file()),
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

    /**
     * The enrolment file and its sidecar are one pair: a write stages both completely before the first rename, and a
     * write stopped on the way (here by a hook that throws, standing for a killed process) is settled by
     * {@link EnrolmentStore#recoverPair} to the complete new pair or the untouched old one, never to a half-new one.
     */
    @Nested
    @DisplayName("the enrolment pair")
    class Pair {

        private static final String SIBLING = PROJECT + "-c";
        private static final String STAGED_SEGMENT = ".tmp.";
        private static final String COMPLETED = "Completed";
        private static final String DISCARDED = "Discarded";
        private static final String NONE = "none";

        /** Stands for a process killed after a step of the write: nothing is cleaned up. */
        private static final class Killed extends RuntimeException {

            private static final long serialVersionUID = 1L;
        }

        private final ProjectEnrolment old = enrolment(PROJECT, false);
        private final ProjectEnrolment newer = enrolment(PROJECT, true);

        private void write(EnrolmentStore enrolmentStore, ProjectEnrolment enrolment) {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(enrolmentStore.lockKey(enrolment.projectId()));
                enrolmentStore.write(transaction, enrolment, sidecarOf(enrolment));
            }
        }

        private void writeStopped(EnrolmentStore.PairStep step, ProjectEnrolment enrolment) {
            var stopping = new EnrolmentStore(base, observed -> {
                if (observed == step) {
                    throw new Killed();
                }
            });
            var sidecar = sidecarOf(enrolment);
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(stopping.lockKey(enrolment.projectId()));

                assertThrows(Killed.class, () -> stopping.write(transaction, enrolment, sidecar));
            }
        }

        /** Recovers as after a restart: a new store, a new transaction. */
        private List<PairRecovery> recover(String projectId) {
            var restarted = new EnrolmentStore(base);
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(restarted.lockKey(projectId));
                return restarted.recoverPair(transaction, projectId);
            }
        }

        private boolean remove(String projectId) {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(store.lockKey(projectId));
                return store.remove(transaction, projectId);
            }
        }

        private List<String> names() throws IOException {
            try (var entries = Files.list(store.directory())) {
                return entries.map(entry -> entry.getFileName().toString()).sorted().toList();
            }
        }

        private List<String> stagedNames() throws IOException {
            return names().stream().filter(name -> name.contains(STAGED_SEGMENT)).toList();
        }

        private List<TransactionId> stagedTransactions() throws IOException {
            return stagedNames().stream().map(name -> TransactionId.ofSuffix(name).orElseThrow()).distinct()
                    .toList();
        }

        private boolean consistent(String projectId) throws IOException {
            var sidecar = EnrolmentSidecar.read(store.sidecar(projectId),
                    Files.readAllBytes(store.sidecar(projectId)));
            return sidecar.matches(Files.readAllBytes(store.file(projectId)));
        }

        private Path staged(Path target, TransactionId transactionId) {
            return target.resolveSibling(
                    transactionId.stagedName(target.getFileName().toString(), TransactionJournal.Mode.REPLACE));
        }

        /** Stages by hand what a stopped write leaves; a {@code null} content leaves that name out. */
        private TransactionId stage(String projectId, byte[] fileContent, byte[] sidecarContent, Instant at)
                throws IOException {
            var transactionId = TransactionId.generate(Clock.fixed(at, ZoneOffset.UTC));
            if (fileContent != null) {
                Files.write(staged(store.file(projectId), transactionId), fileContent);
            }
            if (sidecarContent != null) {
                Files.write(staged(store.sidecar(projectId), transactionId), sidecarContent);
            }
            return transactionId;
        }

        private static List<String> kinds(List<PairRecovery> recoveries) {
            return recoveries.stream().map(recovery -> recovery.getClass().getSimpleName()).toList();
        }

        private static void assertPairLogs(String kind) {
            if (COMPLETED.equals(kind)) {
                LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                        INFO.ENROLMENT_PAIR_COMPLETED.resolveIdentifierString());
            } else {
                LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO, EnrolmentStore.class);
            }
            if (DISCARDED.equals(kind)) {
                LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                        WARN.ENROLMENT_PAIR_DISCARDED.resolveIdentifierString());
            } else {
                LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, EnrolmentStore.class);
            }
        }

        @Test
        @DisplayName("a write leaves exactly the complete pair, a second write replaces both, a mismatch writes nothing")
        void writeLeavesTheCompletePairAndNoStagedFile() throws IOException {
            write(store, old);
            var namesAfterFirst = names();
            var firstConsistent = consistent(PROJECT);

            write(store, newer);
            var namesAfterSecond = names();
            var other = sidecarOf(old);
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(store.lockKey(PROJECT));

                assertThrows(IllegalArgumentException.class, () -> store.write(transaction, newer, other));
            }

            assertAll(
                    () -> assertEquals(List.of(PROJECT + ".json", PROJECT + ".json.hmac"), namesAfterFirst),
                    () -> assertTrue(firstConsistent),
                    () -> assertEquals(namesAfterFirst, namesAfterSecond),
                    () -> assertEquals(namesAfterFirst, names()),
                    () -> assertArrayEquals(EnrolmentCodec.write(newer), Files.readAllBytes(store.file(PROJECT))),
                    () -> assertArrayEquals(sidecarOf(newer).write(), Files.readAllBytes(store.sidecar(PROJECT))),
                    () -> assertTrue(consistent(PROJECT)));
        }

        @ParameterizedTest(name = "stopped after {0}")
        @CsvSource({"FILE_STAGED, false, Discarded", "SIDECAR_STAGED, true, Completed",
                "FILE_REPLACED, true, Completed", "SIDECAR_REPLACED, true, none"})
        @DisplayName("a write stopped after each step is settled to the old or the new pair, never a half-new one")
        void stoppedAfterEachStep(EnrolmentStore.PairStep step, boolean newPair, String kind) throws IOException {
            write(store, old);
            writeStopped(step, newer);
            var stopped = stagedTransactions();

            var recovered = recover(PROJECT);

            var expected = newPair ? EnrolmentCodec.write(newer) : EnrolmentCodec.write(old);
            assertAll(
                    () -> assertEquals(NONE.equals(kind) ? List.of() : List.of(kind), kinds(recovered)),
                    () -> assertEquals(stopped, recovered.stream().map(PairRecovery::transactionId).toList()),
                    () -> assertArrayEquals(expected, Files.readAllBytes(store.file(PROJECT))),
                    () -> assertTrue(consistent(PROJECT)),
                    () -> assertTrue(stagedNames().isEmpty()));
            assertPairLogs(kind);
        }

        @ParameterizedTest(name = "stopped after {0}")
        @CsvSource({"FILE_STAGED, Discarded, false", "SIDECAR_STAGED, Completed, true"})
        @DisplayName("a stopped first enrolment is no enrolment before the recovery, and one after a completion")
        void firstEnrolmentStopped(EnrolmentStore.PairStep step, String kind, boolean enrolled) throws IOException {
            writeStopped(step, newer);
            var before = store.projectIds();

            var recovered = recover(PROJECT);

            assertAll(
                    () -> assertTrue(before.isEmpty()),
                    () -> assertEquals(List.of(kind), kinds(recovered)),
                    () -> assertEquals(enrolled, store.projectIds().contains(PROJECT)),
                    () -> assertEquals(enrolled, Files.exists(store.file(PROJECT))),
                    () -> assertTrue(stagedNames().isEmpty()));
        }

        static Stream<Arguments> incompleteStagings() {
            var newerContent = EnrolmentCodec.write(enrolment(PROJECT, true));
            var document = new EnrolmentSidecar(StoreDigest.of(newerContent), HMAC).write();
            var otherVersion = new String(document, StandardCharsets.UTF_8)
                    .replace("\"format_version\":1", "\"format_version\":2").getBytes(StandardCharsets.UTF_8);
            var foreign = new EnrolmentSidecar(StoreDigest.of(EnrolmentCodec.write(enrolment(SIBLING, true))), HMAC)
                    .write();
            var changedFile = EnrolmentCodec.write(enrolment(PROJECT, false));
            return Stream.of(
                    Arguments.of("a torn staged sidecar", newerContent,
                            Arrays.copyOf(document, document.length / 2)),
                    Arguments.of("a staged sidecar of another file", newerContent, foreign),
                    Arguments.of("a staged sidecar of another format version", newerContent, otherVersion),
                    Arguments.of("a staged file changed after its sidecar was staged", changedFile, document),
                    Arguments.of("a staged file without its sidecar", newerContent, null));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("incompleteStagings")
        @DisplayName("an incomplete or mismatching staging is discarded with its file, the old pair untouched")
        void incompleteStagingDiscarded(String description, byte[] stagedFile, byte[] stagedSidecar)
                throws IOException {
            write(store, old);
            var oldFile = Files.readAllBytes(store.file(PROJECT));
            var oldSidecar = Files.readAllBytes(store.sidecar(PROJECT));
            var transactionId = stage(PROJECT, stagedFile, stagedSidecar, Instant.now());

            var recovered = recover(PROJECT);

            assertAll(description,
                    () -> assertEquals(List.of(DISCARDED), kinds(recovered)),
                    () -> assertEquals(transactionId, recovered.getFirst().transactionId()),
                    () -> assertFalse(((PairRecovery.Discarded) recovered.getFirst()).reason().isBlank()),
                    () -> assertArrayEquals(oldFile, Files.readAllBytes(store.file(PROJECT))),
                    () -> assertArrayEquals(oldSidecar, Files.readAllBytes(store.sidecar(PROJECT))),
                    () -> assertTrue(stagedNames().isEmpty()));
            assertPairLogs(DISCARDED);
        }

        @Test
        @DisplayName("a replaced file with a staged sidecar that does not match it: the sidecar is discarded, the file kept")
        void replacedFileWithForeignSidecar() throws IOException {
            write(store, old);
            Files.write(store.file(PROJECT), EnrolmentCodec.write(newer));
            var oldSidecar = Files.readAllBytes(store.sidecar(PROJECT));
            var transactionId = stage(PROJECT, null, sidecarOf(enrolment(SIBLING, false)).write(), Instant.now());

            var recovered = recover(PROJECT);

            assertAll(
                    () -> assertEquals(List.of(new PairRecovery.Discarded(transactionId,
                                    List.of(staged(store.sidecar(PROJECT), transactionId)),
                                    ((PairRecovery.Discarded) recovered.getFirst()).reason())), recovered),
                    () -> assertArrayEquals(EnrolmentCodec.write(newer), Files.readAllBytes(store.file(PROJECT))),
                    () -> assertArrayEquals(oldSidecar, Files.readAllBytes(store.sidecar(PROJECT))),
                    () -> assertFalse(consistent(PROJECT)),
                    () -> assertTrue(stagedNames().isEmpty()));
        }

        @Test
        @DisplayName("a staged sidecar whose enrolment file is missing is discarded")
        void stagedSidecarWithoutFile() throws IOException {
            stage(PROJECT, null, sidecarOf(newer).write(), Instant.now());

            var recovered = recover(PROJECT);

            assertAll(
                    () -> assertEquals(List.of(DISCARDED), kinds(recovered)),
                    () -> assertFalse(Files.exists(store.sidecar(PROJECT))),
                    () -> assertTrue(stagedNames().isEmpty()));
        }

        @Test
        @DisplayName("the staged names of one project never claim those of a project whose id begins with it")
        void isolation() throws IOException {
            var projectContent = EnrolmentCodec.write(newer);
            var sibling = enrolment(SIBLING, true);
            var siblingContent = EnrolmentCodec.write(sibling);
            stage(PROJECT, projectContent, sidecarOf(newer).write(), Instant.now());
            var siblingStaged = stage(SIBLING, siblingContent, sidecarOf(sibling).write(), Instant.now());
            var leftover = Files.writeString(store.directory().resolve(PROJECT + ".json.tmp." + UUID.randomUUID()),
                    "{}");
            var unrelated = Files.writeString(store.directory().resolve("notes.txt"), "x");
            var siblingNames = List.of(staged(store.file(SIBLING), siblingStaged).getFileName().toString(),
                    staged(store.sidecar(SIBLING), siblingStaged).getFileName().toString());

            var recovered = recover(PROJECT);
            var namesAfterRecovery = names();
            var removed = remove(PROJECT);
            var namesAfterRemove = names();
            var siblingRecovered = recover(SIBLING);

            assertAll(
                    () -> assertEquals(List.of(COMPLETED), kinds(recovered)),
                    () -> assertTrue(namesAfterRecovery.containsAll(siblingNames), namesAfterRecovery::toString),
                    () -> assertTrue(removed),
                    () -> assertTrue(namesAfterRemove.containsAll(siblingNames), namesAfterRemove::toString),
                    () -> assertEquals(List.of(COMPLETED), kinds(siblingRecovered)),
                    () -> assertArrayEquals(siblingContent, Files.readAllBytes(store.file(SIBLING))),
                    () -> assertTrue(Files.exists(leftover)),
                    () -> assertTrue(Files.exists(unrelated)),
                    () -> assertEquals(Set.of(SIBLING), store.projectIds()));
        }

        @Test
        @DisplayName("several stopped writes of one project are settled in ascending transaction order")
        void severalTransactionsInOrder() throws IOException {
            var first = enrolment(PROJECT, false);
            var second = enrolment(PROJECT, true);
            var later = Instant.parse("2026-10-12T08:00:01Z");
            var earlier = Instant.parse("2026-10-12T08:00:00Z");
            var secondId = stage(PROJECT, EnrolmentCodec.write(second), sidecarOf(second).write(), later);
            var firstId = stage(PROJECT, EnrolmentCodec.write(first), sidecarOf(first).write(), earlier);

            var recovered = recover(PROJECT);

            assertAll(
                    () -> assertEquals(List.of(firstId, secondId),
                            recovered.stream().map(PairRecovery::transactionId).toList()),
                    () -> assertEquals(List.of(COMPLETED, COMPLETED), kinds(recovered)),
                    () -> assertArrayEquals(EnrolmentCodec.write(second), Files.readAllBytes(store.file(PROJECT))),
                    () -> assertTrue(consistent(PROJECT)),
                    () -> assertTrue(stagedNames().isEmpty()));
        }

        @Test
        @DisplayName("a write settles an earlier stopped write first, and its own pair is the one in place")
        void writeSettlesAnEarlierStoppedWriteFirst() throws IOException {
            write(store, old);
            writeStopped(EnrolmentStore.PairStep.SIDECAR_STAGED, newer);

            write(store, old);

            assertAll(
                    () -> assertArrayEquals(EnrolmentCodec.write(old), Files.readAllBytes(store.file(PROJECT))),
                    () -> assertTrue(consistent(PROJECT)),
                    () -> assertTrue(stagedNames().isEmpty()));
            assertPairLogs(COMPLETED);
        }

        @Test
        @DisplayName("a removal drops staged leftovers, so the unenrolled project is never rolled forward again")
        void removeDropsStagedLeftovers() throws IOException {
            write(store, old);
            writeStopped(EnrolmentStore.PairStep.SIDECAR_STAGED, newer);

            var removed = remove(PROJECT);
            var namesAfterRemove = names();
            var recovered = recover(PROJECT);
            writeStopped(EnrolmentStore.PairStep.SIDECAR_STAGED, newer);
            var stagedOnly = stagedNames();
            var removedStagedOnly = remove(PROJECT);

            assertAll(
                    () -> assertTrue(removed),
                    () -> assertTrue(namesAfterRemove.isEmpty(), namesAfterRemove::toString),
                    () -> assertTrue(recovered.isEmpty()),
                    () -> assertEquals(2, stagedOnly.size()),
                    () -> assertFalse(removedStagedOnly),
                    () -> assertTrue(names().isEmpty()),
                    () -> assertTrue(store.projectIds().isEmpty()));
        }

        @Test
        @DisplayName("the recovery is refused without the project's lock, another project's lock does not count")
        void recoverWithoutLock() throws IOException {
            writeStopped(EnrolmentStore.PairStep.SIDECAR_STAGED, newer);
            var before = names();

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(store.lockKey(SIBLING));

                assertEquals(Reason.STORE_LOCK_NOT_HELD, assertThrows(InternalFaultException.class,
                        () -> store.recoverPair(transaction, PROJECT)).reason());
            }
            var namesAfterRefusal = names();
            var recovered = recover(PROJECT);

            assertAll(
                    () -> assertEquals(before, namesAfterRefusal),
                    () -> assertEquals(List.of(COMPLETED), kinds(recovered)));
        }

        @Test
        @DisplayName("a store without its directory recovers nothing")
        void noDirectory() {
            var elsewhere = new EnrolmentStore(base.resolve("elsewhere"));
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(elsewhere.lockKey(PROJECT));

                assertTrue(elsewhere.recoverPair(transaction, PROJECT).isEmpty());
            }
        }

        @Test
        @DisplayName("the base is absolute and normalized, for a relative and an unnormalized argument")
        void baseIsAbsoluteAndNormalized() {
            assertAll(
                    () -> assertEquals(Path.of("x").toAbsolutePath().normalize(),
                            new EnrolmentStore(Path.of("relative/../x")).base()),
                    () -> assertEquals(base.toAbsolutePath().normalize().resolve("b"),
                            new EnrolmentStore(base.resolve("a/../b")).base()));
        }
    }

    private static String transactionId() {
        return TransactionId.generate(Clock.systemUTC()).value();
    }
}
