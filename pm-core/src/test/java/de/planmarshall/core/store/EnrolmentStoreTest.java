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

import static org.easymock.EasyMock.newCapture;
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
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.log.CoreLogMessages.INFO;
import de.planmarshall.core.log.CoreLogMessages.WARN;
import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.InternalFaultException.Reason;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.StoreRefusal.Outcome;
import de.planmarshall.core.testing.generators.CoreGenerators;

/**
 * The project enrolment store (PM-EXEC-2, PM-IMPL-7): one file and one sidecar per project, read and written only
 * under the project's own lock, the set of enrolled projects being the listing of the directory. An operator write
 * owes its audit record in the project's intent file before the mutation; the intent keeps the record through a
 * refused or stopped hand-over and is removed only after the sink accepted every record it owes.
 */
@EnableTestLogger
@EnableGeneratorController
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
        var generated = CoreGenerators.projectEnrolments().next();
        return new ProjectEnrolment(projectId, "/home/dev/git/" + projectId, generated.enrolledAt(), pilot,
                generated.containerEngine(), generated.releasedAt(), generated.wrappers(), generated.hostTools(),
                generated.build(), generated.providers(), generated.projectScripts(), generated.languageServers(),
                generated.commandBudgets(), generated.writePaths(), generated.buildEnvironment());
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
        assertAll("the store should place its three files",
                () -> assertEquals(base.toAbsolutePath().normalize().resolve("enrolments/pm-mcp-core.json"),
                        store.file(PROJECT), "The enrolment file should be <id>.json in enrolments"),
                () -> assertEquals(base.toAbsolutePath().normalize().resolve("enrolments/pm-mcp-core.json.hmac"),
                        store.sidecar(PROJECT), "The sidecar should sit beside the enrolment file"),
                () -> assertEquals(LockKey.enrolment(base, PROJECT), store.lockKey(PROJECT),
                        "The lock key should be the enrolment lock of the project"),
                () -> assertThrows(IllegalArgumentException.class, () -> store.file("../projects"),
                        "A project id outside the grammar should be refused"));
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

                assertAll("the pair should be read back as written",
                        () -> assertArrayEquals(EnrolmentCodec.write(enrolment), content,
                                "The file should hold the canonical bytes"),
                        () -> assertEquals(enrolment, store.decode(PROJECT, content),
                                "The file should decode to the same record"),
                        () -> assertEquals(sidecarOf(enrolment), sidecar,
                                "The sidecar should record the same digest and HMAC"),
                        () -> assertTrue(sidecar.matches(content),
                                "The sidecar should match the bytes it was read with"));
            }
            assertEquals(List.of(LockKey.enrolment(base, PROJECT), LockKey.enrolment(base, PROJECT)),
                    locks.acquisitions(), "Both accesses should take the project's lock");
        }

        @Test
        @DisplayName("a project without an enrolment reads as no file and no sidecar")
        void absent() {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(store.lockKey(PROJECT));

                assertAll("an absent project should read as nothing",
                        () -> assertTrue(store.readFile(transaction, PROJECT).isEmpty(),
                                "A project without a file should read no file"),
                        () -> assertTrue(store.readSidecar(transaction, PROJECT).isEmpty(),
                                "A project without a sidecar should read no sidecar"));
            }
        }

        @Test
        @DisplayName("a sidecar that does not record the canonical bytes is refused and nothing is written")
        void mismatchedSidecar() {
            var enrolment = enrolment(PROJECT, false);
            var other = sidecarOf(enrolment(PROJECT, true));

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(store.lockKey(PROJECT));

                assertThrows(IllegalArgumentException.class, () -> store.write(transaction, enrolment, other),
                        "A sidecar of other bytes should be refused");
            }
            assertAll("a refused write should leave nothing behind",
                    () -> assertFalse(Files.exists(store.file(PROJECT)),
                            "No enrolment file should have been written"),
                    () -> assertFalse(Files.exists(store.sidecar(PROJECT)),
                            "No sidecar should have been written"));
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

            assertAll("a re-baseline should replace the sidecar only",
                    () -> assertArrayEquals(file, Files.readAllBytes(store.file(PROJECT)),
                            "The enrolment file should keep its bytes"),
                    () -> assertArrayEquals(rebaselined.write(), Files.readAllBytes(store.sidecar(PROJECT)),
                            "The sidecar should hold the new record"));
        }

        @Test
        @DisplayName("a removal deletes the file and its sidecar and nothing else, and reports what it found")
        void remove() throws Exception {
            enrol(enrolment(PROJECT, false));
            var projectStore = Files.createDirectories(base.resolve("projects").resolve(PROJECT));
            Files.writeString(projectStore.resolve("store.json"), "{}");

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(store.lockKey(PROJECT));

                assertTrue(store.remove(transaction, PROJECT), "The first removal should find the pair");
                assertFalse(store.remove(transaction, PROJECT), "The second removal should find nothing");
            }
            assertAll("a removal should delete the pair and nothing else",
                    () -> assertFalse(Files.exists(store.file(PROJECT)),
                            "The enrolment file should be gone"),
                    () -> assertFalse(Files.exists(store.sidecar(PROJECT)),
                            "The sidecar should be gone"),
                    () -> assertEquals("{}", Files.readString(projectStore.resolve("store.json")),
                            "Another file of the project should be kept"));
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
            var otherProject = "project-" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(store.lockKey(otherProject));

                assertAll("every access should be refused without the project's lock",
                        () -> assertEquals(Reason.STORE_LOCK_NOT_HELD, assertThrows(InternalFaultException.class,
                                        () -> store.write(transaction, enrolment, sidecar),
                                        "A write without the lock should fail").reason(),
                                "A write should be refused as a lock fault"),
                        () -> assertThrows(InternalFaultException.class, () -> store.readFile(transaction, PROJECT),
                                "A file read should be refused as a lock fault"),
                        () -> assertThrows(InternalFaultException.class,
                                () -> store.readSidecar(transaction, PROJECT),
                                "A sidecar read should be refused as a lock fault"),
                        () -> assertThrows(InternalFaultException.class,
                                () -> store.writeSidecar(transaction, PROJECT, sidecar),
                                "A sidecar write should be refused as a lock fault"),
                        () -> assertThrows(InternalFaultException.class, () -> store.remove(transaction, PROJECT),
                                "A removal should be refused as a lock fault"));
            }
            assertFalse(Files.exists(store.file(PROJECT)), "No refusal should write an enrolment file");
        }
    }

    @Nested
    @DisplayName("listing and decoding")
    class ListingAndDecoding {

        @Test
        @DisplayName("the enrolled projects are the enrolment files of the directory, not sidecars or other entries")
        void projectIds() throws Exception {
            var zeta = "zeta-" + Generators.letterStrings(3, 8).next().toLowerCase(Locale.ROOT);
            enrol(enrolment(zeta, false));
            enrol(enrolment(PROJECT, false));
            Files.writeString(store.directory().resolve(Generators.letterStrings(1, 8).next() + ".txt"),
                    Generators.letterStrings(1, 8).next());
            Files.writeString(store.directory().resolve("Upper.json"), "{}");

            assertEquals(Set.of(PROJECT, zeta), store.projectIds(),
                    "The listing should hold exactly the enrolment files");
        }

        @Test
        @DisplayName("a store without its directory lists no project")
        void noDirectory() {
            assertTrue(new EnrolmentStore(base.resolve("elsewhere")).projectIds().isEmpty(),
                    "A store without its directory should list no project");
        }

        @Test
        @DisplayName("an enrolment whose project_id is not the name of its file is refused as unreadable")
        void foreignProjectId() {
            var otherProject = "project-" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
            var content = EnrolmentCodec.write(enrolment(otherProject, false));

            var refusal = assertThrows(StoreRefusal.class, () -> store.decode(PROJECT, content),
                    "A foreign project id should be refused");

            assertAll("a foreign project id is unreadable",
                    () -> assertEquals(Outcome.UNREADABLE, refusal.outcome(),
                            "The refusal should report an unreadable document"),
                    () -> assertEquals(store.file(PROJECT), refusal.file(),
                            "The refusal should name the enrolment file"));
        }

        @Test
        @DisplayName("an unreadable sidecar is refused naming the sidecar file")
        void unreadableSidecar() {
            var content = HMAC.getBytes(StandardCharsets.UTF_8);

            var refusal = assertThrows(StoreRefusal.class, () -> store.decodeSidecar(PROJECT, content),
                    "An unreadable sidecar should be refused");

            assertEquals(store.sidecar(PROJECT), refusal.file(), "The refusal should name the sidecar file");
        }
    }

    @Nested
    @DisplayName("the audit intent of the operator writes")
    class AuditIntent {

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
                    store.auditIntent(PROJECT), "The intent path should sit beside the enrolment file");
        }

        @Nested
        @DisplayName("owing a record")
        class Owe {

            @Test
            @DisplayName("writes the record durably to the intent file, where a later reader finds it")
            void owesDurably() {
                var written = written();

                owe(PROJECT, written);

                assertAll("the record should be owed durably",
                        () -> assertTrue(Files.exists(store.auditIntent(PROJECT)),
                                "The intent file should have been written"),
                        () -> assertEquals(List.of(written), owed(store),
                                "The same store should read the owed record"),
                        () -> assertEquals(List.of(written), owed(new EnrolmentStore(base)),
                                "A new store should read the owed record"));
            }

            @Test
            @DisplayName("keeps a second record of another transaction after the first, in order")
            void keepsOrder() {
                var first = written();
                var second = AuditEvent.enrolmentRebaselined(PROJECT, transactionId());

                owe(PROJECT, first, second);

                assertEquals(List.of(first, second), owed(store),
                        "The owed records should keep their order");
            }

            @Test
            @DisplayName("does not owe a record twice whose transaction id is already owed")
            void idempotent() throws Exception {
                var id = transactionId();
                owe(PROJECT, AuditEvent.enrolmentWritten(PROJECT, id));
                var before = Files.readAllBytes(store.auditIntent(PROJECT));

                owe(PROJECT, AuditEvent.enrolmentWritten(PROJECT, id), AuditEvent.enrolmentRemoved(PROJECT, id));

                assertArrayEquals(before, Files.readAllBytes(store.auditIntent(PROJECT)),
                        "A record already owed should not be written twice");
            }

            @Test
            @DisplayName("refuses a record with a detail an intent cannot hold, and writes nothing")
            void notWritable() {
                var event = new AuditEvent(AuditEvent.ENROLMENT_WRITTEN, AuditEvent.CATEGORY_OPERATOR,
                        AuditEvent.OUTCOME_SUCCEEDED, Map.of(AuditEvent.DETAIL_PROJECT_ID, true));

                try (var transaction = locks.openTransaction()) {
                    transaction.acquire(store.lockKey(PROJECT));

                    assertThrows(IllegalArgumentException.class, () -> store.oweAudit(transaction, PROJECT, event),
                            "A detail an intent cannot hold should be refused");
                }
                assertFalse(Files.exists(store.auditIntent(PROJECT)),
                        "A refused record should write no intent file");
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

                assertEquals(List.of(first, second), owed(store),
                        "A confirmation should replace the record in its place");
            }

            @Test
            @DisplayName("keeps the other unconfirmed record unconfirmed, which a stop before its confirmation leaves")
            void keepsOthers() {
                var first = AuditEvent.enrolmentWritten(PROJECT, transactionId());
                var second = AuditEvent.enrolmentRebaselined(PROJECT, transactionId());
                owe(PROJECT, first.notConfirmed(), second.notConfirmed());

                confirm(second);

                assertEquals(List.of(first.notConfirmed(), second), owed(store),
                        "The other unconfirmed record should stay unconfirmed");
            }

            @Test
            @DisplayName("owes the record afresh when no record of its transaction is owed")
            void owesAfresh() {
                var other = AuditEvent.enrolmentRemoved(PROJECT, transactionId());
                var confirmed = written();
                owe(PROJECT, other);

                confirm(confirmed);

                assertEquals(List.of(other, confirmed), owed(store),
                        "A record without a transaction peer should be owed afresh");
            }

            @Test
            @DisplayName("hands the confirmed record over once, with the transaction id it was owed with")
            void handedOverConfirmed() {
                var confirmed = written();
                owe(PROJECT, confirmed.notConfirmed());
                confirm(confirmed);
                var sink = new RecordingAuditSink();

                var delivered = deliver(sink);

                assertAll("the confirmed record should be handed over once",
                        () -> assertEquals(List.of(confirmed), delivered,
                                "The delivered list should hold the record"),
                        () -> assertEquals(List.of(confirmed), sink.events(),
                                "The sink should have accepted the record"),
                        () -> assertFalse(Files.exists(store.auditIntent(PROJECT)),
                                "The intent file should be removed"));
            }

            @Test
            @DisplayName("hands the unconfirmed record over as it is when it was never confirmed")
            void handedOverUnconfirmed() {
                var unconfirmed = written().notConfirmed();
                owe(PROJECT, unconfirmed);
                var sink = new RecordingAuditSink();

                var delivered = deliver(sink);

                assertAll("an unconfirmed record should be handed over as it is",
                        () -> assertEquals(List.of(unconfirmed), delivered,
                                "The delivered list should hold the record"),
                        () -> assertEquals("failed", unconfirmed.outcome(),
                                "The record should keep its failed outcome"),
                        () -> assertFalse(Files.exists(store.auditIntent(PROJECT)),
                                "The intent file should be removed"));
            }

            @Test
            @DisplayName("refuses a record without a transaction id or with a detail an intent cannot hold")
            void refusedRecords() {
                var withoutTransaction = new AuditEvent(AuditEvent.ENROLMENT_WRITTEN, AuditEvent.CATEGORY_OPERATOR,
                        AuditEvent.OUTCOME_SUCCEEDED, Map.of(AuditEvent.DETAIL_PROJECT_ID, PROJECT));
                var notWritable = new AuditEvent(AuditEvent.ENROLMENT_WRITTEN, AuditEvent.CATEGORY_OPERATOR,
                        AuditEvent.OUTCOME_SUCCEEDED, Map.of(AuditEvent.DETAIL_PROJECT_ID, true));

                assertAll("records an intent cannot hold should be refused",
                        () -> assertThrows(IllegalArgumentException.class, () -> confirm(notWritable),
                                "A detail an intent cannot hold should be refused"),
                        () -> assertThrows(NullPointerException.class, () -> confirm(withoutTransaction),
                                "A record without a transaction id should be refused"),
                        () -> assertFalse(Files.exists(store.auditIntent(PROJECT)),
                                "No refused record should write an intent file"));
            }
        }

        @Nested
        @DisplayName("listing")
        class Listing {

            @Test
            @DisplayName("the intent file is not an enrolment, and the owing projects are exactly those with an intent")
            void intentIsNotAnEnrolment() throws Exception {
                var alpha = "alpha-" + Generators.letterStrings(3, 8).next().toLowerCase(Locale.ROOT);
                var zeta = "zeta-" + Generators.letterStrings(3, 8).next().toLowerCase(Locale.ROOT);
                enrol(enrolment(PROJECT, false));
                enrol(enrolment(alpha, false));
                owe(zeta, AuditEvent.enrolmentRemoved(zeta, transactionId()));
                owe(PROJECT, written());
                Files.writeString(store.directory().resolve("Upper.json.audit-intent"), "{}");

                assertAll("an intent file should not count as an enrolment",
                        () -> assertEquals(List.of(alpha, PROJECT), List.copyOf(store.projectIds()),
                                "The listing should hold the two enrolments in order"),
                        () -> assertEquals(List.of(PROJECT, zeta), List.copyOf(store.auditOwingProjectIds()),
                                "The owing listing should hold the two intents in order"));
            }

            @Test
            @DisplayName("a store without its directory lists no owing project")
            void noDirectory() {
                assertTrue(new EnrolmentStore(base.resolve("elsewhere")).auditOwingProjectIds().isEmpty(),
                        "A store without its directory should list no owing project");
            }

            @Test
            @DisplayName("a store without an intent lists no owing project")
            void noIntent() {
                enrol(enrolment(PROJECT, false));

                assertTrue(store.auditOwingProjectIds().isEmpty(),
                        "A store without an intent should list no owing project");
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

                assertAll("an accepting sink should receive the records in order",
                        () -> assertEquals(List.of(first, second), delivered,
                                "The delivered list should hold both records in order"),
                        () -> assertEquals(List.of(first, second), sink.events(),
                                "The sink should have accepted both records in order"),
                        () -> assertFalse(Files.exists(store.auditIntent(PROJECT)),
                                "The intent file should be removed"));
            }

            @Test
            @DisplayName("with nothing owed nothing is handed over and nothing is touched")
            void nothingOwed() throws Exception {
                enrol(enrolment(PROJECT, false));
                var before = listing();
                var sink = new RecordingAuditSink();

                var delivered = deliver(sink);

                assertAll("nothing owed should hand over nothing",
                        () -> assertTrue(delivered.isEmpty(), "Nothing should be delivered"),
                        () -> assertTrue(sink.events().isEmpty(), "The sink should receive nothing"),
                        () -> assertEquals(before, listing(), "The directory should be untouched"));
            }

            @Test
            @DisplayName("a sink that refuses the first record keeps every record owed, warns once and throws nothing")
            void refused() {
                var first = written();
                var second = AuditEvent.enrolmentRebaselined(PROJECT, transactionId());
                owe(PROJECT, first, second);
                Capture<AuditEvent> refused = newCapture(CaptureType.ALL);
                var sink = AuditSinkDoubles.refusing(refused);

                var delivered = deliver(sink);

                assertAll("a refusing sink should keep every record owed",
                        () -> assertTrue(delivered.isEmpty(), "Nothing should be delivered"),
                        () -> assertEquals(List.of(first, second), owed(store),
                                "Every record should still be owed in order"),
                        () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                                WARN.AUDIT_RECORD_DEFERRED.resolveIdentifierString()),
                        () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                                "'" + AuditEvent.ENROLMENT_WRITTEN + "' with transaction id '"
                                        + first.details().get(AuditEvent.DETAIL_TRANSACTION_ID) + "'"));

                var again = deliver(sink);

                assertAll("a second hand-over should deliver what was owed",
                        () -> assertEquals(List.of(first, second), again,
                                "The second hand-over should deliver both records"),
                        () -> assertEquals(List.of(first, second), refused.getValues(),
                                "The sink should accept both records on the second try"),
                        () -> assertFalse(Files.exists(store.auditIntent(PROJECT)),
                                "The intent file should be removed after the hand-over"));
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
                        throw new UncheckedIOException(new IOException(Generators.letterStrings(10, 30).next()));
                    }
                    accepted.add(event);
                };

                var delivered = deliver(sink);

                assertAll("a sink refusing the second record should keep it owed",
                        () -> assertEquals(List.of(first), delivered,
                                "Only the first record should be delivered"),
                        () -> assertEquals(List.of(first), accepted,
                                "The sink should have accepted only the first record"),
                        () -> assertEquals(List.of(second), owed(store),
                                "Only the second record should still be owed"));

                var recording = new RecordingAuditSink();
                var rest = deliver(recording);

                assertAll("the remaining record should be handed over once",
                        () -> assertEquals(List.of(second), rest,
                                "The second hand-over should deliver the second record"),
                        () -> assertEquals(List.of(second), recording.events(),
                                "The recording sink should accept the second record"),
                        () -> assertFalse(Files.exists(store.auditIntent(PROJECT)),
                                "The intent file should be removed"));
            }

            @Test
            @DisplayName("a stop at the hand-over leaves the intent, and a new transaction hands the record over once")
            void stopped() {
                var written = written();
                owe(PROJECT, written);
                var stopping = AuditSinkDoubles.stopping();

                try (var transaction = locks.openTransaction()) {
                    transaction.acquire(store.lockKey(PROJECT));

                    assertThrows(AuditSinkDoubles.ProcessStopped.class,
                            () -> store.deliverOwedAudit(transaction, PROJECT, stopping),
                            "A stopped hand-over should propagate the stop");
                }
                assertEquals(List.of(written), owed(store),
                        "A stopped hand-over should keep the record owed");

                store = new EnrolmentStore(base);
                var sink = new RecordingAuditSink();
                var delivered = deliver(sink);

                assertAll("a new transaction should hand the record over once",
                        () -> assertEquals(List.of(written), delivered,
                                "The new transaction should deliver the record"),
                        () -> assertEquals(List.of(written), sink.events(),
                                "The sink should accept the record"),
                        () -> assertFalse(Files.exists(store.auditIntent(PROJECT)),
                                "The intent file should be removed"));
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
                var otherProject = "project-" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);

                try (var transaction = locks.openTransaction()) {
                    transaction.acquire(store.lockKey(otherProject));

                    assertAll("every audit access should need the project's lock",
                            () -> assertEquals(Reason.STORE_LOCK_NOT_HELD, assertThrows(InternalFaultException.class,
                                            () -> store.oweAudit(transaction, PROJECT, written),
                                            "Owing without the lock should fail").reason(),
                                    "Owing a record should be refused as a lock fault"),
                            () -> assertThrows(InternalFaultException.class,
                                    () -> store.confirmAudit(transaction, PROJECT, written),
                                    "Confirming a record should be refused as a lock fault"),
                            () -> assertThrows(InternalFaultException.class,
                                    () -> store.owedAudit(transaction, PROJECT),
                                    "Reading the owed records should be refused as a lock fault"),
                            () -> assertThrows(InternalFaultException.class,
                                    () -> store.deliverOwedAudit(transaction, PROJECT, sink),
                                    "Handing over should be refused as a lock fault"),
                            () -> assertThrows(InternalFaultException.class,
                                    () -> store.exists(transaction, PROJECT),
                                    "The existence query should be refused as a lock fault"));
                }
                assertAll("a lock refusal should write nothing",
                        () -> assertFalse(Files.exists(store.auditIntent(PROJECT)),
                                "No intent file should have been written"),
                        () -> assertTrue(sink.events().isEmpty(),
                                "The sink should have received nothing"));
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

                    assertAll("an unreadable intent should refuse every access",
                            () -> assertEquals(intent, assertThrows(StoreRefusal.class,
                                            () -> store.owedAudit(transaction, PROJECT),
                                            "An unreadable intent should be refused").file(),
                                    "Reading the owed records should name the intent file"),
                            () -> assertEquals(intent, assertThrows(StoreRefusal.class,
                                            () -> store.oweAudit(transaction, PROJECT, written),
                                            "An unreadable intent should be refused").file(),
                                    "Owing a record should name the intent file"),
                            () -> assertEquals(intent, assertThrows(StoreRefusal.class,
                                            () -> store.confirmAudit(transaction, PROJECT, written),
                                            "An unreadable intent should be refused").file(),
                                    "Confirming a record should name the intent file"),
                            () -> assertEquals(intent, assertThrows(StoreRefusal.class,
                                            () -> store.deliverOwedAudit(transaction, PROJECT, sink),
                                            "An unreadable intent should be refused").file(),
                                    "Handing over should name the intent file"));
                }
                assertAll("a refused intent should be kept",
                        () -> assertEquals(content, Files.readString(intent),
                                "The intent file should keep its content"),
                        () -> assertTrue(sink.events().isEmpty(),
                                "The sink should have received nothing"));
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

            assertEquals(file || sidecar, exists.get(), "The existence should follow the two files");
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
        private static final String ELSEWHERE = "elsewhere";
        private static final String NOTHING = "nothing at all";
        private static final String ENROLLED = "an enrolment written normally";
        private static final String NO_DIRECTORY = "no enrolments directory";

        /** Stands for a process killed after a step of the write: nothing is cleaned up. */
        private static final class Killed extends RuntimeException {

            private static final long serialVersionUID = 1L;
        }

        private ProjectEnrolment old;
        private ProjectEnrolment newer;

        @BeforeEach
        void createEnrolments() {
            old = enrolment(PROJECT, false);
            newer = enrolment(PROJECT, true);
        }

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

                assertThrows(Killed.class, () -> stopping.write(transaction, enrolment, sidecar),
                        "A stopped write should throw the kill sentinel");
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

        private boolean hasStagedWrite(EnrolmentStore enrolmentStore, String projectId) {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(enrolmentStore.lockKey(projectId));
                return enrolmentStore.hasStagedWrite(transaction, projectId);
            }
        }

        private boolean exists(String projectId) {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(store.lockKey(projectId));
                return store.exists(transaction, projectId);
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
        void writeLeavesTheCompletePairAndNoStagedFile() throws Exception {
            write(store, old);
            var namesAfterFirst = names();
            var firstConsistent = consistent(PROJECT);

            write(store, newer);
            var namesAfterSecond = names();
            var other = sidecarOf(old);
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(store.lockKey(PROJECT));

                assertThrows(IllegalArgumentException.class, () -> store.write(transaction, newer, other),
                        "A sidecar of other bytes should be refused");
            }

            assertAll("a write should leave exactly the complete pair",
                    () -> assertEquals(List.of(PROJECT + ".json", PROJECT + ".json.hmac"), namesAfterFirst,
                            "The first write should leave the file and its sidecar"),
                    () -> assertTrue(firstConsistent, "The first pair should be consistent"),
                    () -> assertEquals(namesAfterFirst, namesAfterSecond,
                            "The second write should leave the same names"),
                    () -> assertEquals(namesAfterFirst, names(),
                            "The refused write should leave the same names"),
                    () -> assertArrayEquals(EnrolmentCodec.write(newer), Files.readAllBytes(store.file(PROJECT)),
                            "The file should hold the newer enrolment"),
                    () -> assertArrayEquals(sidecarOf(newer).write(), Files.readAllBytes(store.sidecar(PROJECT)),
                            "The sidecar should hold the newer record"),
                    () -> assertTrue(consistent(PROJECT), "The pair should be consistent"));
        }

        @ParameterizedTest(name = "stopped after {0}")
        @CsvSource({"FILE_STAGED, false, Discarded", "SIDECAR_STAGED, true, Completed",
                "FILE_REPLACED, true, Completed", "SIDECAR_REPLACED, true, none"})
        @DisplayName("a write stopped after each step is settled to the old or the new pair, never a half-new one")
        void stoppedAfterEachStep(EnrolmentStore.PairStep step, boolean newPair, String kind) throws Exception {
            write(store, old);
            writeStopped(step, newer);
            var stopped = stagedTransactions();

            var recovered = recover(PROJECT);

            var expected = EnrolmentCodec.write(newPair ? newer : old);
            assertAll("a stopped write should settle to a whole pair",
                    () -> assertEquals(NONE.equals(kind) ? List.of() : List.of(kind), kinds(recovered),
                            "The recovery should report the expected outcome"),
                    () -> assertEquals(stopped, recovered.stream().map(PairRecovery::transactionId).toList(),
                            "The recovery should settle the staged transaction"),
                    () -> assertArrayEquals(expected, Files.readAllBytes(store.file(PROJECT)),
                            "The file should hold the expected enrolment"),
                    () -> assertTrue(consistent(PROJECT), "The pair should be consistent"),
                    () -> assertTrue(stagedNames().isEmpty(), "No staged name should remain"));
            assertPairLogs(kind);
        }

        @ParameterizedTest(name = "stopped after {0}")
        @CsvSource({"FILE_STAGED, Discarded, false", "SIDECAR_STAGED, Completed, true"})
        @DisplayName("a stopped first enrolment is no enrolment before the recovery, and one after a completion")
        void firstEnrolmentStopped(EnrolmentStore.PairStep step, String kind, boolean enrolled) {
            writeStopped(step, newer);
            var before = store.projectIds();

            var recovered = recover(PROJECT);

            assertAll("a stopped first enrolment should settle to enrolled or not",
                    () -> assertTrue(before.isEmpty(), "No project should be listed before recovery"),
                    () -> assertEquals(List.of(kind), kinds(recovered),
                            "The recovery should report the expected outcome"),
                    () -> assertEquals(enrolled, store.projectIds().contains(PROJECT),
                            "The listing should follow the recovery outcome"),
                    () -> assertEquals(enrolled, Files.exists(store.file(PROJECT)),
                            "The file should exist only for an enrolment"),
                    () -> assertTrue(stagedNames().isEmpty(), "No staged name should remain"));
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
                throws Exception {
            write(store, old);
            var oldFile = Files.readAllBytes(store.file(PROJECT));
            var oldSidecar = Files.readAllBytes(store.sidecar(PROJECT));
            var transactionId = stage(PROJECT, stagedFile, stagedSidecar, Instant.now());

            var recovered = recover(PROJECT);

            assertAll(description,
                    () -> assertEquals(List.of(DISCARDED), kinds(recovered),
                            "The incomplete staging should be discarded"),
                    () -> assertEquals(transactionId, recovered.getFirst().transactionId(),
                            "The discard should name the staged transaction"),
                    () -> assertFalse(((PairRecovery.Discarded) recovered.getFirst()).reason().isBlank(),
                            "The discard should state a reason"),
                    () -> assertArrayEquals(oldFile, Files.readAllBytes(store.file(PROJECT)),
                            "The old enrolment file should be untouched"),
                    () -> assertArrayEquals(oldSidecar, Files.readAllBytes(store.sidecar(PROJECT)),
                            "The old sidecar should be untouched"),
                    () -> assertTrue(stagedNames().isEmpty(), "No staged name should remain"));
            assertPairLogs(DISCARDED);
        }

        @Test
        @DisplayName("a replaced file with a staged sidecar that does not match it: the sidecar is discarded, the file kept")
        void replacedFileWithForeignSidecar() throws Exception {
            write(store, old);
            Files.write(store.file(PROJECT), EnrolmentCodec.write(newer));
            var oldSidecar = Files.readAllBytes(store.sidecar(PROJECT));
            var transactionId = stage(PROJECT, null, sidecarOf(enrolment(SIBLING, false)).write(), Instant.now());

            var recovered = recover(PROJECT);

            assertAll("a foreign staged sidecar should be discarded alone",
                    () -> assertEquals(List.of(new PairRecovery.Discarded(transactionId,
                                    List.of(staged(store.sidecar(PROJECT), transactionId)),
                                    ((PairRecovery.Discarded) recovered.getFirst()).reason())), recovered,
                            "The sidecar should be discarded for its transaction"),
                    () -> assertArrayEquals(EnrolmentCodec.write(newer), Files.readAllBytes(store.file(PROJECT)),
                            "The replaced enrolment file should be kept"),
                    () -> assertArrayEquals(oldSidecar, Files.readAllBytes(store.sidecar(PROJECT)),
                            "The old sidecar should be kept"),
                    () -> assertFalse(consistent(PROJECT),
                            "The kept file and sidecar should not match"),
                    () -> assertTrue(stagedNames().isEmpty(), "No staged name should remain"));
        }

        @Test
        @DisplayName("a staged sidecar whose enrolment file is missing is discarded")
        void stagedSidecarWithoutFile() throws Exception {
            stage(PROJECT, null, sidecarOf(newer).write(), Instant.now());

            var recovered = recover(PROJECT);

            assertAll("a staged sidecar without its file should be discarded",
                    () -> assertEquals(List.of(DISCARDED), kinds(recovered),
                            "The orphan staging should be discarded"),
                    () -> assertFalse(Files.exists(store.sidecar(PROJECT)),
                            "No sidecar should have been kept"),
                    () -> assertTrue(stagedNames().isEmpty(), "No staged name should remain"));
        }

        @Test
        @DisplayName("the staged names of one project never claim those of a project whose id begins with it")
        void isolation() throws Exception {
            var projectContent = EnrolmentCodec.write(newer);
            var sibling = enrolment(SIBLING, true);
            var siblingContent = EnrolmentCodec.write(sibling);
            stage(PROJECT, projectContent, sidecarOf(newer).write(), Instant.now());
            var siblingStaged = stage(SIBLING, siblingContent, sidecarOf(sibling).write(), Instant.now());
            var leftover = Files.writeString(store.directory()
                    .resolve(PROJECT + ".json.tmp." + Generators.letterStrings(8, 12).next()), "{}");
            var unrelated = Files.writeString(
                    store.directory().resolve(Generators.letterStrings(1, 8).next() + ".txt"),
                    Generators.letterStrings(1, 8).next());
            var siblingNames = List.of(staged(store.file(SIBLING), siblingStaged).getFileName().toString(),
                    staged(store.sidecar(SIBLING), siblingStaged).getFileName().toString());

            var recovered = recover(PROJECT);
            var namesAfterRecovery = names();
            var removed = remove(PROJECT);
            var namesAfterRemove = names();
            var siblingRecovered = recover(SIBLING);

            assertAll("a project's staged names should never claim its sibling's",
                    () -> assertEquals(List.of(COMPLETED), kinds(recovered),
                            "The project's staged pair should be completed"),
                    () -> assertTrue(namesAfterRecovery.containsAll(siblingNames), namesAfterRecovery::toString),
                    () -> assertTrue(removed, "The project should be removed"),
                    () -> assertTrue(namesAfterRemove.containsAll(siblingNames), namesAfterRemove::toString),
                    () -> assertEquals(List.of(COMPLETED), kinds(siblingRecovered),
                            "The sibling's staged pair should be completed"),
                    () -> assertArrayEquals(siblingContent, Files.readAllBytes(store.file(SIBLING)),
                            "The sibling file should hold its own enrolment"),
                    () -> assertTrue(Files.exists(leftover), "The leftover name should be kept"),
                    () -> assertTrue(Files.exists(unrelated), "The unrelated file should be kept"),
                    () -> assertEquals(Set.of(SIBLING), store.projectIds(),
                            "Only the sibling should remain enrolled"));
        }

        @Test
        @DisplayName("several stopped writes of one project are settled in ascending transaction order")
        void severalTransactionsInOrder() throws Exception {
            var first = enrolment(PROJECT, false);
            var second = enrolment(PROJECT, true);
            var later = Instant.parse("2026-10-12T08:00:01Z");
            var earlier = Instant.parse("2026-10-12T08:00:00Z");
            var secondId = stage(PROJECT, EnrolmentCodec.write(second), sidecarOf(second).write(), later);
            var firstId = stage(PROJECT, EnrolmentCodec.write(first), sidecarOf(first).write(), earlier);

            var recovered = recover(PROJECT);

            assertAll("several stopped writes should settle in transaction order",
                    () -> assertEquals(List.of(firstId, secondId),
                            recovered.stream().map(PairRecovery::transactionId).toList(),
                            "The recoveries should be in ascending transaction order"),
                    () -> assertEquals(List.of(COMPLETED, COMPLETED), kinds(recovered),
                            "Both stagings should be completed"),
                    () -> assertArrayEquals(EnrolmentCodec.write(second), Files.readAllBytes(store.file(PROJECT)),
                            "The later enrolment should be in place"),
                    () -> assertTrue(consistent(PROJECT), "The pair should be consistent"),
                    () -> assertTrue(stagedNames().isEmpty(), "No staged name should remain"));
        }

        @Test
        @DisplayName("a write settles an earlier stopped write first, and its own pair is the one in place")
        void writeSettlesAnEarlierStoppedWriteFirst() {
            write(store, old);
            writeStopped(EnrolmentStore.PairStep.SIDECAR_STAGED, newer);

            write(store, old);

            assertAll("a write should settle its earlier stopped write first",
                    () -> assertArrayEquals(EnrolmentCodec.write(old), Files.readAllBytes(store.file(PROJECT)),
                            "The own pair should be the one in place"),
                    () -> assertTrue(consistent(PROJECT), "The pair should be consistent"),
                    () -> assertTrue(stagedNames().isEmpty(), "No staged name should remain"));
            assertPairLogs(COMPLETED);
        }

        @Test
        @DisplayName("a removal drops staged leftovers, so the unenrolled project is never rolled forward again")
        void removeDropsStagedLeftovers() throws Exception {
            write(store, old);
            writeStopped(EnrolmentStore.PairStep.SIDECAR_STAGED, newer);

            var removed = remove(PROJECT);
            var namesAfterRemove = names();
            var recovered = recover(PROJECT);
            writeStopped(EnrolmentStore.PairStep.SIDECAR_STAGED, newer);
            var stagedOnly = stagedNames();
            var removedStagedOnly = remove(PROJECT);

            assertAll("a removal should drop the staged leftovers",
                    () -> assertTrue(removed, "The pair should be removed"),
                    () -> assertTrue(namesAfterRemove.isEmpty(), namesAfterRemove::toString),
                    () -> assertTrue(recovered.isEmpty(), "Nothing should be left to recover"),
                    () -> assertEquals(2, stagedOnly.size(), "The stopped write should leave two names"),
                    () -> assertFalse(removedStagedOnly, "A staged-only project should not be removed"),
                    () -> assertTrue(names().isEmpty(), "The directory should be empty"),
                    () -> assertTrue(store.projectIds().isEmpty(), "No project should remain enrolled"));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {NOTHING, ENROLLED, NO_DIRECTORY})
        @DisplayName("a project without a staged name has no staged write")
        void noStagedWrite(String situation) {
            var enrolmentStore = switch (situation) {
                case ENROLLED -> {
                    write(store, old);
                    yield store;
                }
                case NO_DIRECTORY -> new EnrolmentStore(base.resolve(ELSEWHERE));
                default -> store;
            };

            assertFalse(hasStagedWrite(enrolmentStore, PROJECT),
                    "A project without a staged name has none");
        }

        @ParameterizedTest(name = "stopped after {0}")
        @CsvSource({"FILE_STAGED, true", "SIDECAR_STAGED, true", "FILE_REPLACED, true", "SIDECAR_REPLACED, false"})
        @DisplayName("a write stopped while a name is staged leaves a staged write, one stopped after the last rename not")
        void stagedWriteAfterEachStep(EnrolmentStore.PairStep step, boolean staged) {
            write(store, old);
            writeStopped(step, newer);

            assertEquals(staged, hasStagedWrite(store, PROJECT),
                    "The staged-write answer should follow the step");
        }

        @ParameterizedTest(name = "stopped after {0}")
        @EnumSource(value = EnrolmentStore.PairStep.class, names = {"FILE_STAGED", "SIDECAR_STAGED"})
        @DisplayName("a stopped first enrolment is a staged write although no enrolment exists")
        void stagedFirstEnrolment(EnrolmentStore.PairStep step) {
            writeStopped(step, newer);

            assertAll("a stopped first enrolment should still be a staged write",
                    () -> assertTrue(hasStagedWrite(store, PROJECT),
                            "The staging should count as a staged write"),
                    () -> assertFalse(exists(PROJECT), "No enrolment should exist yet"),
                    () -> assertTrue(store.projectIds().isEmpty(), "No project should be listed"));
        }

        @ParameterizedTest(name = "stopped after {0}")
        @EnumSource(value = EnrolmentStore.PairStep.class, names = {"FILE_STAGED", "SIDECAR_STAGED", "FILE_REPLACED"})
        @DisplayName("a staged write is gone once the recovery settled it and once a removal dropped it")
        void noStagedWriteAfterRecoveryOrRemoval(EnrolmentStore.PairStep step) {
            write(store, old);
            writeStopped(step, newer);
            var stagedBeforeRecovery = hasStagedWrite(store, PROJECT);
            recover(PROJECT);
            var stagedAfterRecovery = hasStagedWrite(store, PROJECT);
            writeStopped(step, newer);
            var stagedBeforeRemoval = hasStagedWrite(store, PROJECT);
            remove(PROJECT);
            var stagedAfterRemoval = hasStagedWrite(store, PROJECT);

            assertAll("a settled or dropped staging should leave no staged write",
                    () -> assertTrue(stagedBeforeRecovery, "The stopped write should be staged before recovery"),
                    () -> assertFalse(stagedAfterRecovery, "The recovery should settle the staging"),
                    () -> assertTrue(stagedBeforeRemoval, "The stopped write should be staged before removal"),
                    () -> assertFalse(stagedAfterRemoval, "The removal should drop the staging"));
        }

        @Test
        @DisplayName("the staged names of a project whose id begins with another, a leftover and other files do not count")
        void stagedWriteIsolation() throws Exception {
            var sibling = enrolment(SIBLING, true);
            stage(SIBLING, EnrolmentCodec.write(sibling), sidecarOf(sibling).write(), Instant.now());
            Files.writeString(store.directory()
                    .resolve(PROJECT + ".json.tmp." + Generators.letterStrings(8, 12).next()), "{}");
            Files.writeString(
                    store.directory().resolve(Generators.letterStrings(1, 8).next() + ".txt"), PROJECT);
            var projectWithSiblingStaged = hasStagedWrite(store, PROJECT);
            var siblingWithSiblingStaged = hasStagedWrite(store, SIBLING);
            remove(SIBLING);
            stage(PROJECT, EnrolmentCodec.write(newer), sidecarOf(newer).write(), Instant.now());

            assertAll("a staged write should belong to one project alone",
                    () -> assertFalse(projectWithSiblingStaged,
                            "The sibling's staging should not count for the project"),
                    () -> assertTrue(siblingWithSiblingStaged,
                            "The sibling's staging should count for the sibling"),
                    () -> assertFalse(hasStagedWrite(store, SIBLING),
                            "The removed sibling should have no staged write"),
                    () -> assertTrue(hasStagedWrite(store, PROJECT),
                            "The project's own staging should count"));
        }

        @Test
        @DisplayName("the staged-write query is refused without the project's lock, another project's lock does not count")
        void stagedWriteWithoutLock() {
            writeStopped(EnrolmentStore.PairStep.SIDECAR_STAGED, newer);

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(store.lockKey(SIBLING));

                assertEquals(Reason.STORE_LOCK_NOT_HELD, assertThrows(InternalFaultException.class,
                                () -> store.hasStagedWrite(transaction, PROJECT),
                                "The query without the lock should fail").reason(),
                        "The staged-write query should be refused as a lock fault");
            }
            assertTrue(hasStagedWrite(store, PROJECT),
                    "The staging should survive the refused query");
        }

        @Test
        @DisplayName("a directory that cannot be listed fails the staged-write query naming the project")
        void stagedWriteUnlistable() throws Exception {
            var broken = Files.createDirectories(base.resolve(ELSEWHERE));
            Files.writeString(broken.resolve(EnrolmentStore.DIRECTORY_NAME),
                    Generators.letterStrings(5, 20).next());
            var unlistable = new EnrolmentStore(broken);

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(unlistable.lockKey(PROJECT));

                var failure = assertThrows(UncheckedIOException.class,
                        () -> unlistable.hasStagedWrite(transaction, PROJECT),
                        "A directory that cannot be listed should fail");
                assertTrue(failure.getMessage().contains("'" + PROJECT + "'"), failure::getMessage);
            }
        }

        @Test
        @DisplayName("the recovery is refused without the project's lock, another project's lock does not count")
        void recoverWithoutLock() throws Exception {
            writeStopped(EnrolmentStore.PairStep.SIDECAR_STAGED, newer);
            var before = names();

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(store.lockKey(SIBLING));

                assertEquals(Reason.STORE_LOCK_NOT_HELD, assertThrows(InternalFaultException.class,
                                () -> store.recoverPair(transaction, PROJECT),
                                "The recovery without the lock should fail").reason(),
                        "The recovery should be refused as a lock fault");
            }
            var namesAfterRefusal = names();
            var recovered = recover(PROJECT);

            assertAll("a refused recovery should leave the staging for the real one",
                    () -> assertEquals(before, namesAfterRefusal,
                            "The refused recovery should change no name"),
                    () -> assertEquals(List.of(COMPLETED), kinds(recovered),
                            "The recovery should then complete the staging"));
        }

        @Test
        @DisplayName("a store without its directory recovers nothing")
        void noDirectory() {
            var elsewhere = new EnrolmentStore(base.resolve(ELSEWHERE));
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(elsewhere.lockKey(PROJECT));

                assertTrue(elsewhere.recoverPair(transaction, PROJECT).isEmpty(),
                        "A store without its directory should recover nothing");
            }
        }

        @Test
        @DisplayName("the base is absolute and normalized, for a relative and an unnormalized argument")
        void baseIsAbsoluteAndNormalized() {
            assertAll("the base should be absolute and normalized",
                    () -> assertEquals(Path.of("x").toAbsolutePath().normalize(),
                            new EnrolmentStore(Path.of("relative/../x")).base(),
                            "A relative base should resolve against the working directory"),
                    () -> assertEquals(base.toAbsolutePath().normalize().resolve("b"),
                            new EnrolmentStore(base.resolve("a/../b")).base(),
                            "A parent segment in the base should be normalized"));
        }

        /**
         * An unenrol deletes the enrolment file before its sidecar; a stop in between leaves a sidecar without its file
         * beside the record the unenrol owes. Such a sidecar is not an enrolment: {@link EnrolmentStore#hasEnrolmentFile}
         * tells it from one, and {@link EnrolmentStore#removeOrphanSidecar} removes it and nothing else.
         */
        @Nested
        @DisplayName("the orphan sidecar of an interrupted unenrol")
        class OrphanSidecar {

            /** What the enrolments directory holds for the project before the query. */
            enum Leftover {
                PAIR(true),
                NOTHING(false),
                ORPHAN_SIDECAR(false),
                STAGED_ONLY(false),
                FILE_ONLY(true),
                NO_DIRECTORY(false);

                private final boolean enrolmentFile;

                Leftover(boolean enrolmentFile) {
                    this.enrolmentFile = enrolmentFile;
                }
            }

            /** Leaves what an unenrol stopped between its two deletions leaves: the sidecar and the owed record. */
            private void interruptRemoval(String projectId) throws IOException {
                write(store, enrolment(projectId, false));
                try (var transaction = locks.openTransaction()) {
                    transaction.acquire(store.lockKey(projectId));
                    store.oweAudit(transaction, projectId,
                            AuditEvent.enrolmentRemoved(projectId, transactionId()).notConfirmed());
                }
                Files.delete(store.file(projectId));
            }

            private EnrolmentStore arrange(Leftover leftover) throws IOException {
                if (leftover == Leftover.NO_DIRECTORY) {
                    return new EnrolmentStore(base.resolve(ELSEWHERE));
                }
                switch (leftover) {
                    case PAIR -> write(store, old);
                    case ORPHAN_SIDECAR -> interruptRemoval(PROJECT);
                    case STAGED_ONLY -> writeStopped(EnrolmentStore.PairStep.SIDECAR_STAGED, newer);
                    case FILE_ONLY -> {
                        write(store, old);
                        Files.delete(store.sidecar(PROJECT));
                    }
                    default -> {
                        // the directory holds nothing of the project
                    }
                }
                return store;
            }

            private boolean hasEnrolmentFile(EnrolmentStore enrolmentStore, String projectId) {
                try (var transaction = locks.openTransaction()) {
                    transaction.acquire(enrolmentStore.lockKey(projectId));
                    return enrolmentStore.hasEnrolmentFile(transaction, projectId);
                }
            }

            private boolean removeOrphanSidecar(String projectId) {
                try (var transaction = locks.openTransaction()) {
                    transaction.acquire(store.lockKey(projectId));
                    return store.removeOrphanSidecar(transaction, projectId);
                }
            }

            /** The bytes of every regular file of the directory, by name. */
            private Map<String, String> contents() throws IOException {
                var contents = new TreeMap<String, String>();
                for (var name : names()) {
                    var file = store.directory().resolve(name);
                    if (Files.isRegularFile(file)) {
                        contents.put(name, HexFormat.of().formatHex(Files.readAllBytes(file)));
                    }
                }
                return contents;
            }

            @ParameterizedTest(name = "{0}")
            @EnumSource(Leftover.class)
            @DisplayName("the enrolment file is there only for a written pair and a file without its sidecar")
            void enrolmentFileOnlyForAFile(Leftover leftover) throws Exception {
                var enrolmentStore = arrange(leftover);

                assertEquals(leftover.enrolmentFile, hasEnrolmentFile(enrolmentStore, PROJECT),
                        "The enrolment-file answer should follow the leftover");
            }

            @Test
            @DisplayName("an orphan sidecar is something left to remove, but no enrolment file and no enrolment")
            void orphanIsNoEnrolment() throws Exception {
                interruptRemoval(PROJECT);

                assertAll("an orphan sidecar should be no enrolment",
                        () -> assertTrue(exists(PROJECT),
                                "The sidecar alone should count as existing"),
                        () -> assertFalse(hasEnrolmentFile(store, PROJECT),
                                "The orphan should not count as an enrolment file"),
                        () -> assertTrue(store.projectIds().isEmpty(),
                                "The project should not be listed as enrolled"),
                        () -> assertEquals(Set.of(PROJECT), store.auditOwingProjectIds(),
                                "The project should still owe its removal record"));
            }

            @Test
            @DisplayName("the orphan sidecar is removed once, and the intent, staged names and other projects stay")
            void removesTheOrphanSidecarOnly() throws Exception {
                interruptRemoval(PROJECT);
                stage(PROJECT, EnrolmentCodec.write(newer), sidecarOf(newer).write(), Instant.now());
                write(store, enrolment(SIBLING, false));
                var before = contents();
                before.remove(store.sidecar(PROJECT).getFileName().toString());

                var removed = removeOrphanSidecar(PROJECT);
                var removedAgain = removeOrphanSidecar(PROJECT);

                assertAll("the orphan sidecar should be removed once alone",
                        () -> assertTrue(removed, "The first removal should find the orphan"),
                        () -> assertFalse(removedAgain, "The second removal should find nothing"),
                        () -> assertFalse(Files.exists(store.sidecar(PROJECT)),
                                "The orphan sidecar should be gone"),
                        () -> assertEquals(before, contents(),
                                "Every other file should be kept byte for byte"));
            }

            @Test
            @DisplayName("a sidecar beside its enrolment file is no orphan, and the pair is left as it is")
            void keepsASidecarBesideItsFile() throws Exception {
                write(store, old);
                var before = contents();

                var removed = removeOrphanSidecar(PROJECT);

                assertAll("a sidecar beside its file should not be removed",
                        () -> assertFalse(removed, "A complete pair should not be touched"),
                        () -> assertEquals(before, contents(),
                                "The pair should be left byte for byte"));
            }

            @Test
            @DisplayName("the orphan sidecar of a project whose id begins with another is never removed for the other")
            void isolation() throws Exception {
                interruptRemoval(SIBLING);
                var removedForProject = removeOrphanSidecar(PROJECT);
                var siblingKept = Files.exists(store.sidecar(SIBLING));
                interruptRemoval(PROJECT);

                var removedForSibling = removeOrphanSidecar(SIBLING);
                var projectKept = Files.exists(store.sidecar(PROJECT));
                var removedForItself = removeOrphanSidecar(PROJECT);

                assertAll("an orphan should never be removed for another project",
                        () -> assertFalse(removedForProject,
                                "The sibling's orphan should not be removed for the project"),
                        () -> assertTrue(siblingKept, "The sibling's orphan should be kept"),
                        () -> assertTrue(removedForSibling,
                                "The sibling's orphan should be removed for the sibling"),
                        () -> assertFalse(Files.exists(store.sidecar(SIBLING)),
                                "The sibling's orphan should be gone"),
                        () -> assertTrue(projectKept, "The project's orphan should be kept"),
                        () -> assertTrue(removedForItself,
                                "The project's orphan should be removed for itself"));
            }

            @Test
            @DisplayName("both queries are refused without the project's lock, another project's lock does not count")
            void withoutLock() throws Exception {
                interruptRemoval(PROJECT);
                var before = contents();

                try (var transaction = locks.openTransaction()) {
                    transaction.acquire(store.lockKey(SIBLING));

                    assertAll("both queries should need the project's lock",
                            () -> assertEquals(Reason.STORE_LOCK_NOT_HELD, assertThrows(InternalFaultException.class,
                                            () -> store.hasEnrolmentFile(transaction, PROJECT),
                                            "The file query without the lock should fail").reason(),
                                    "The file query should be refused as a lock fault"),
                            () -> assertEquals(Reason.STORE_LOCK_NOT_HELD, assertThrows(InternalFaultException.class,
                                            () -> store.removeOrphanSidecar(transaction, PROJECT),
                                            "The removal without the lock should fail").reason(),
                                    "The removal should be refused as a lock fault"));
                }
                var unchanged = contents();
                var enrolmentFile = hasEnrolmentFile(store, PROJECT);
                var removed = removeOrphanSidecar(PROJECT);

                assertAll("a lock refusal should change nothing",
                        () -> assertEquals(before, unchanged,
                                "The directory should be unchanged"),
                        () -> assertFalse(enrolmentFile, "The orphan should be no enrolment file"),
                        () -> assertTrue(removed, "The orphan should then be removed"));
            }

            @Test
            @DisplayName("a sidecar that cannot be deleted fails naming the project, and nothing changes")
            void undeletableSidecar() throws Exception {
                interruptRemoval(PROJECT);
                Files.delete(store.sidecar(PROJECT));
                var kept = Files.writeString(Files.createDirectory(store.sidecar(PROJECT)).resolve("kept"), PROJECT);
                var before = contents();
                var namesBefore = names();

                try (var transaction = locks.openTransaction()) {
                    transaction.acquire(store.lockKey(PROJECT));

                    var failure = assertThrows(UncheckedIOException.class,
                            () -> store.removeOrphanSidecar(transaction, PROJECT),
                            "An undeletable sidecar should fail the removal");
                    assertTrue(failure.getMessage().contains("'" + PROJECT + "'"), failure::getMessage);
                }
                assertAll("a failed removal should change nothing",
                        () -> assertEquals(before, contents(), "The directory should keep its bytes"),
                        () -> assertEquals(namesBefore, names(), "The directory should keep its names"),
                        () -> assertTrue(Files.exists(kept), "The blocking entry should be kept"));
            }
        }
    }

    private static String transactionId() {
        return TransactionId.generate(Clock.systemUTC()).value();
    }
}
