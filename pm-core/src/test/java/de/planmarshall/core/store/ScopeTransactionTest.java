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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.easymock.EasyMock.newCapture;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serial;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Stream;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.InternalFaultException.Reason;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.ScopeTransaction.Step;

/**
 * The journaled write of the stores of a scope (PM-IMPL-7): a commit writes every target, the digests of the sidecar
 * and leaves neither the journal nor a staged file; it runs only under the lock of the scope, takes each store once,
 * and repairs a torn JSONL tail before it appends.
 * <p>
 * What a commit stopped between its steps leaves, and how the recovery completes it, is the subject of
 * {@link JournalRecoveryTest}. The store file names of the plan scope, the layout below the workspace and the fixture
 * paths outside the scope are contracts; the plan ids, the store contents, the rule name and the instant of the commits
 * are generated. Targets added to one transaction stay in a deliberate order.
 */
@EnableGeneratorController
@DisplayName("Scope transaction")
class ScopeTransactionTest {

    private static final String PROJECT = "pm-mcp-core";

    // Store file names of the plan scope (Store Schemas).
    private static final String STATUS = "status.json";
    private static final String REFERENCES = "references.json";
    private static final String WORK_LOG = "logs/work.jsonl";

    // Fixture path of a file outside the scope.
    private static final String OUTSIDE = "outside.json";

    @TempDir
    Path base;

    private String plan;
    private String anotherPlan;
    private String oldStatus;
    private String newStatus;
    private String newReferences;
    private String firstLine;
    private String secondLine;
    private String tornTail;
    private Clock clock;

    private JournalScope scope;
    private RecordingLockManager locks;
    private Capture<AuditEvent> auditEvents;
    private AuditSink audit;

    @BeforeEach
    void createScope() throws IOException {
        plan = "plan-" + lowerName();
        anotherPlan = plan + "x";
        var phase = lowerName();
        oldStatus = "{\"format_version\":1,\"phase\":\"" + phase + "\"}";
        newStatus = "{\"format_version\":1,\"phase\":\"" + phase + "-" + lowerName() + "\"}";
        newReferences = "{\"format_version\":1,\"files\":[\"" + lowerName() + ".java\"]}";
        var n = Generators.integers(1, 1000).next();
        firstLine = "{\"format_version\":1,\"n\":" + n + "}\n";
        secondLine = "{\"format_version\":1,\"n\":" + (n + 1) + "}\n";
        tornTail = "{\"format_version\":1,\"text\":\"" + lowerName();
        clock = Clock.fixed(Instant.ofEpochSecond(Generators.longs(1_700_000_000L, 1_800_000_000L).next()),
                ZoneOffset.UTC);
        scope = JournalScope.plan(base, PROJECT, plan);
        Files.createDirectories(scope.journalFile().getParent());
        Files.createDirectories(scope.resolve(WORK_LOG).getParent());
        locks = new RecordingLockManager();
        auditEvents = newCapture(CaptureType.ALL);
        audit = AuditSinkDoubles.capturing(auditEvents);
    }

    private static String lowerName() {
        return Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }

    private static byte[] bytes(String... parts) {
        return String.join("", parts).getBytes(UTF_8);
    }

    private Path store(String relativePath) {
        return scope.resolve(relativePath);
    }

    private ScopeTransaction openUnder(LockTransaction transaction) {
        return ScopeTransaction.open(transaction, scope, audit, clock);
    }

    private DigestSidecar sidecar() {
        return DigestSidecar.load(scope.sidecarFile());
    }

    /** @return the files below the path base whose name marks them as staged by a transaction */
    private List<Path> stagedFiles() throws IOException {
        try (Stream<Path> files = Files.walk(scope.pathBase())) {
            return files.filter(Files::isRegularFile).filter(file -> {
                var name = file.getFileName().toString();
                return name.contains(".tmp.") || name.contains(".append.");
            }).toList();
        }
    }

    /** One {@code transaction_id} per owed record, the deduplication key of the audit log reader. */
    @Nested
    @DisplayName("owed records")
    class OwedRecords {

        private static Object idOf(AuditEvent event) {
            return event.details().get(AuditEvent.DETAIL_TRANSACTION_ID);
        }

        @Test
        @DisplayName("two records owed by one transaction carry two ids, both distinct from the transaction's own")
        void distinctIds() {
            var rule = lowerName();
            String transactionId;
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);
                write.replace(STATUS, bytes(newStatus));
                write.owe(AuditEvent.crossScopeReconciled(rule, STATUS, write.recordId().value()));
                write.owe(AuditEvent.crossScopeReconciled(rule, REFERENCES, write.recordId().value()));
                transactionId = write.transactionId().value();
                write.commit();
            }

            var ids = auditEvents.getValues().stream().map(OwedRecords::idOf).toList();
            assertAll(
                    () -> assertEquals(2, ids.size(), "both owed records should be delivered"),
                    () -> assertEquals(2, ids.stream().distinct().count(), "each record should have its own id"),
                    () -> assertFalse(ids.contains(transactionId), "no record should carry the transaction id"));
        }

        @Test
        @DisplayName("a crash after the journal re-delivers each owed record with its own id")
        void crashAfterJournal() {
            var rule = lowerName();
            List<String> owedIds;
            String transactionId;
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                ScopeTransaction.StepHook stop = (step, target) -> {
                    if (step == Step.JOURNAL) {
                        throw new AuditSinkDoubles.ProcessStopped();
                    }
                };
                var write = ScopeTransaction.open(transaction, scope, audit, clock, stop);
                write.replace(STATUS, bytes(newStatus));
                owedIds = List.of(write.recordId().value(), write.recordId().value());
                write.owe(AuditEvent.crossScopeReconciled(rule, STATUS, owedIds.get(0)));
                write.owe(AuditEvent.crossScopeReconciled(rule, REFERENCES, owedIds.get(1)));
                transactionId = write.transactionId().value();
                assertThrows(AuditSinkDoubles.ProcessStopped.class, write::commit,
                        "the hook should stop the commit");
            }

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                JournalRecovery.recover(transaction, scope, audit);
            }

            var reconciled = auditEvents.getValues().stream()
                    .filter(event -> AuditEvent.CROSS_SCOPE_RECONCILED.equals(event.event())).map(OwedRecords::idOf)
                    .toList();
            var rolledForward = auditEvents.getValues().stream()
                    .filter(event -> AuditEvent.TRANSACTION_ROLLED_FORWARD.equals(event.event()))
                    .map(OwedRecords::idOf).toList();
            assertAll(
                    () -> assertEquals(owedIds, reconciled, "each record should keep the id it was owed"),
                    () -> assertEquals(List.of(transactionId), rolledForward,
                            "the roll-forward should carry the transaction id"));
        }
    }

    @Nested
    @DisplayName("commit")
    class Committing {

        @Test
        @DisplayName("two replaces and one append leave the stores, their digests, no journal and no staged file")
        void replacesAndAppend() throws Exception {
            Files.write(store(STATUS), bytes(oldStatus));
            Files.write(store(WORK_LOG), bytes(firstLine));

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);
                write.replace(STATUS, bytes(newStatus));
                write.replace(REFERENCES, bytes(newReferences));
                write.append(WORK_LOG, bytes(secondLine));
                write.commit();
            }

            var expectedSidecar = DigestSidecar.of(Map.of(
                    STATUS, StoreDigest.of(bytes(newStatus)),
                    REFERENCES, StoreDigest.of(bytes(newReferences)),
                    WORK_LOG, StoreDigest.of(bytes(firstLine, secondLine))));
            assertAll(
                    () -> assertArrayEquals(bytes(newStatus), Files.readAllBytes(store(STATUS)),
                            "the status should hold the new content"),
                    () -> assertArrayEquals(bytes(newReferences), Files.readAllBytes(store(REFERENCES)),
                            "the references should hold the new content"),
                    () -> assertArrayEquals(bytes(firstLine, secondLine), Files.readAllBytes(store(WORK_LOG)),
                            "the block should be appended once after the old"),
                    () -> assertEquals(expectedSidecar, sidecar(), "the sidecar should hold every digest"),
                    () -> assertArrayEquals(expectedSidecar.write(), Files.readAllBytes(scope.sidecarFile()),
                            "the sidecar file should hold its wire form"),
                    () -> assertFalse(Files.exists(scope.journalFile()), "the journal should be closed"),
                    () -> assertEquals(List.of(), stagedFiles(), "no staged file should remain"));
        }

        /** Matched control of the roll-forward: a commit that is not stopped has nothing rolled forward. */
        @Test
        @DisplayName("a commit that is not stopped records no transaction_rolled_forward and leaves nothing to recover")
        void noRollForward() {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);
                write.replace(STATUS, bytes(newStatus));
                write.append(WORK_LOG, bytes(firstLine));
                write.commit();

                var outcome = JournalRecovery.recover(transaction, scope, audit);

                assertAll(
                        () -> assertEquals(new JournalRecovery.NothingToRecover(List.of()), outcome,
                                "there should be nothing to recover"),
                        () -> assertEquals(List.of(), auditEvents.getValues().stream().map(AuditEvent::event)
                                        .filter(AuditEvent.TRANSACTION_ROLLED_FORWARD::equals).toList(),
                                "no roll-forward should be recorded"),
                        () -> assertEquals(List.of(), auditEvents.getValues(), "no audit event should be recorded"));
            }
        }

        @Test
        @DisplayName("the digests of the stores the transaction does not write stay in the sidecar")
        void otherDigestsKept() {
            var otherStore = lowerName() + ".json";
            var other = StoreDigest.of(bytes(oldStatus));
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                new AtomicStoreFile(scope.sidecarFile(), scope.lockKey()).write(transaction,
                        DigestSidecar.of(Map.of(otherStore, other)).write());
                var write = openUnder(transaction);
                write.replace(STATUS, bytes(newStatus));
                write.commit();
            }

            assertEquals(DigestSidecar.of(Map.of(otherStore, other, STATUS, StoreDigest.of(bytes(newStatus)))),
                    sidecar(), "the digest of the other store should stay");
        }

        @Test
        @DisplayName("the digests of a transaction are written over a temporary sidecar its stopped write left")
        void digestsOverLeftoverTemporary() throws Exception {
            var transactionId = TransactionId.generate(clock);
            var journal = new TransactionJournal(transactionId, JournalScopeType.PLAN, plan, clock.instant(),
                    List.of(new TransactionJournal.Target(STATUS, TransactionJournal.Mode.REPLACE,
                            transactionId.stagedName(STATUS, TransactionJournal.Mode.REPLACE),
                            OptionalLong.empty(), StoreDigest.of(bytes(newStatus)))));
            var leftover = scope.sidecarFile().resolveSibling(transactionId.stagedName(
                    scope.sidecarFile().getFileName().toString(), TransactionJournal.Mode.REPLACE));
            Files.write(leftover, bytes(oldStatus));

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                ScopeTransaction.writeDigests(scope, transaction, journal);
            }

            assertAll(
                    () -> assertEquals(DigestSidecar.of(Map.of(STATUS, StoreDigest.of(bytes(newStatus)))), sidecar(),
                            "the sidecar should hold the journaled digest"),
                    () -> assertFalse(Files.exists(leftover), "the temporary sidecar should be moved away"));
        }

        @Test
        @DisplayName("an append onto a torn tail repairs it first, with one jsonl_tail_repaired record")
        void tornTailRepaired() throws Exception {
            Files.write(store(WORK_LOG), bytes(firstLine, tornTail));

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);
                write.append(WORK_LOG, bytes(secondLine));
                write.commit();
            }

            assertAll(
                    () -> assertArrayEquals(bytes(firstLine, secondLine), Files.readAllBytes(store(WORK_LOG)),
                            "the torn tail should be replaced by the block"),
                    () -> assertEquals(
                            List.of(AuditSinkDoubles.tailRepairWithoutId(store(WORK_LOG), bytes(tornTail))),
                            AuditSinkDoubles.withoutTransactionIds(auditEvents.getValues()),
                            "one tail repair should be recorded"),
                    () -> assertEquals(DigestSidecar.of(Map.of(WORK_LOG, StoreDigest.of(bytes(firstLine, secondLine)))),
                            sidecar(), "the digest should be of the repaired store"),
                    () -> assertEquals(List.of(), stagedFiles(), "no staged file should remain"));
        }

        @Test
        @DisplayName("an append to a store of several buffer sizes records the digest of the whole resulting store")
        void largeAppend() throws Exception {
            var line = bytes(firstLine);
            var large = new ByteArrayOutputStream();
            while (large.size() < 4 * StoreDigest.Builder.BUFFER_SIZE + 5) {
                large.writeBytes(line);
            }
            Files.write(store(WORK_LOG), large.toByteArray());

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);
                write.append(WORK_LOG, bytes(secondLine));
                write.commit();
            }

            large.writeBytes(bytes(secondLine));
            assertAll(
                    () -> assertArrayEquals(large.toByteArray(), Files.readAllBytes(store(WORK_LOG)),
                            "the block should follow the large store"),
                    () -> assertEquals(Optional.of(StoreDigest.of(large.toByteArray())),
                            sidecar().digest(WORK_LOG), "the digest should be of the whole store"));
        }

        @Test
        @DisplayName("an append to a store that does not exist yet records the digest of the block, size before 0")
        void appendToMissingStore() {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);
                write.append(WORK_LOG, bytes(firstLine));
                write.commit();
            }

            assertEquals(Optional.of(StoreDigest.of(bytes(firstLine))), sidecar().digest(WORK_LOG),
                    "the digest should be of the block alone");
        }

        @Test
        @DisplayName("the content handed to replace is copied, so a later change of the array is not written")
        void contentCopied() throws Exception {
            var content = bytes(newStatus);
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);
                write.replace(STATUS, content);
                content[0] = 'x';
                write.commit();
            }

            assertArrayEquals(bytes(newStatus), Files.readAllBytes(store(STATUS)),
                    "the content as handed over should be written");
        }

        @Test
        @DisplayName("a commit that cannot stage a target fails, and the transaction is not committed again")
        void unwritableTarget() {
            var missing = "missing-" + lowerName() + "/" + STATUS;
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);
                write.replace(missing, bytes(newStatus));

                assertThrows(UncheckedIOException.class, write::commit,
                        "staging into a missing directory should fail");
                assertAll(
                        () -> assertThrows(IllegalStateException.class, write::commit,
                                "a second commit should be refused"),
                        () -> assertFalse(Files.exists(scope.journalFile()), "nothing should be journaled"));
            }
        }
    }

    /** What the step hook throws to stop a commit at a boundary, as a killed process stops it. */
    private static final class StopWrite extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        StopWrite(Step step) {
            super("stopped after " + step);
        }
    }

    /**
     * A delete target removes a store and drops its sidecar entry; it stages nothing, and a commit stopped after its
     * journal is completed by the recovery to the store absent and its entry dropped.
     */
    @Nested
    @DisplayName("delete")
    class Deleting {

        /** Writes the status and the work log in a commit, so the sidecar holds both digests. */
        @BeforeEach
        void writeStores() {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);
                write.replace(STATUS, bytes(oldStatus));
                write.append(WORK_LOG, bytes(firstLine));
                write.commit();
            }
        }

        private void commitDeleting(String... relativePaths) {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);
                for (var relativePath : relativePaths) {
                    write.delete(relativePath);
                }
                write.commit();
            }
        }

        private JournalRecovery.Outcome recover() {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                return JournalRecovery.recover(transaction, scope, audit);
            }
        }

        private ScopeIntegrity.Verification verify(String relativePath) {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                return ScopeIntegrity.verify(transaction, scope, relativePath, audit);
            }
        }

        @Test
        @DisplayName("removes the store and drops its digest; the other digests, no journal and no staged file remain")
        void existingStore() {
            commitDeleting(STATUS);

            assertAll(
                    () -> assertFalse(Files.exists(store(STATUS)), "the store should be removed"),
                    () -> assertEquals(DigestSidecar.of(Map.of(WORK_LOG, StoreDigest.of(bytes(firstLine)))),
                            sidecar(), "only the work-log digest should remain"),
                    () -> assertFalse(Files.exists(scope.journalFile()), "the journal should be closed"),
                    () -> assertEquals(List.of(), stagedFiles(), "a delete should stage nothing"),
                    () -> assertEquals(new ScopeIntegrity.Absent(STATUS), verify(STATUS),
                            "the deleted store should be Absent"));
        }

        @Test
        @DisplayName("of a store that does not exist commits and leaves every other store and digest as it was")
        void absentStore() {
            var before = sidecar();

            commitDeleting(REFERENCES);

            assertAll(
                    () -> assertFalse(Files.exists(store(REFERENCES)), "the missing store should stay absent"),
                    () -> assertEquals(before, sidecar(), "the sidecar should be left as it was"),
                    () -> assertArrayEquals(bytes(oldStatus), Files.readAllBytes(store(STATUS)),
                            "the status should be left as it was"),
                    () -> assertFalse(Files.exists(scope.journalFile()), "the journal should be closed"),
                    () -> assertEquals(List.of(), stagedFiles(), "no staged file should remain"));
        }

        @Test
        @DisplayName("beside a replace and an append of other stores applies all three in one transaction")
        void besideOtherModes() {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);
                write.replace(REFERENCES, bytes(newReferences));
                write.delete(STATUS);
                write.append(WORK_LOG, bytes(secondLine));
                write.commit();
            }

            assertAll(
                    () -> assertFalse(Files.exists(store(STATUS)), "the deleted store should be removed"),
                    () -> assertArrayEquals(bytes(firstLine, secondLine), Files.readAllBytes(store(WORK_LOG)),
                            "the block should be appended to the work log"),
                    () -> assertEquals(DigestSidecar.of(Map.of(
                                    REFERENCES, StoreDigest.of(bytes(newReferences)),
                                    WORK_LOG, StoreDigest.of(bytes(firstLine, secondLine)))), sidecar(),
                            "the sidecar should hold the two written stores"),
                    () -> assertEquals(List.of(), stagedFiles(), "no staged file should remain"));
        }

        @Test
        @DisplayName("and a replace, an append or a second delete of the same store in one transaction are refused")
        void sameStoreTwice() {
            var content = bytes(newStatus);
            var block = bytes(secondLine);
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);
                write.replace(STATUS, bytes(newStatus));
                write.delete(WORK_LOG);

                assertAll(
                        () -> assertThrows(IllegalArgumentException.class, () -> write.delete(STATUS),
                                "a delete of a replaced store should fail"),
                        () -> assertThrows(IllegalArgumentException.class, () -> write.delete("./" + STATUS),
                                "a delete in another spelling should fail"),
                        () -> assertThrows(IllegalArgumentException.class, () -> write.replace(WORK_LOG, content),
                                "a replace of a deleted store should fail"),
                        () -> assertThrows(IllegalArgumentException.class, () -> write.append(WORK_LOG, block),
                                "an append to a deleted store should fail"),
                        () -> assertThrows(IllegalArgumentException.class, () -> write.delete(WORK_LOG),
                                "a second delete should be refused"));
            }
        }

        @Test
        @DisplayName("of the journal, the sidecar or a path outside the scope is refused")
        void notAStore() {
            var journal = scope.pathBase().relativize(scope.journalFile()).toString();
            var sidecar = scope.pathBase().relativize(scope.sidecarFile()).toString();
            var outside = "../" + anotherPlan + "/" + STATUS;
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);

                assertAll(
                        () -> assertThrows(IllegalArgumentException.class, () -> write.delete(journal),
                                "a delete of the journal should be refused"),
                        () -> assertThrows(IllegalArgumentException.class, () -> write.delete(sidecar),
                                "a delete of the sidecar should be refused"),
                        () -> assertThrows(IllegalArgumentException.class, () -> write.delete(outside),
                                "a delete outside the scope should be refused"));
            }
            assertTrue(Files.exists(scope.sidecarFile()), "the sidecar should be untouched");
        }

        @Test
        @DisplayName("is no further target of a committed transaction")
        void afterCommit() {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);
                write.delete(STATUS);
                write.commit();

                assertThrows(IllegalStateException.class, () -> write.delete(WORK_LOG),
                        "a committed transaction should take no target");
            }
        }

        @ParameterizedTest(name = "stopped after {0}")
        @EnumSource(Step.class)
        @DisplayName("stopped at a step boundary is left undone before its journal and completed after it")
        void stoppedAtBoundary(Step stop) throws Exception {
            var sidecarBefore = Files.readAllBytes(scope.sidecarFile());
            ScopeTransaction.StepHook hook = (step, target) -> {
                if (step == stop) {
                    throw new StopWrite(step);
                }
            };
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = ScopeTransaction.open(transaction, scope, audit, clock, hook);
                write.delete(STATUS);
                assertThrows(StopWrite.class, write::commit, "the hook should stop the commit");
            }
            var journal = StoreSnapshot.read(scope.journalFile()).content()
                    .map(document -> TransactionJournal.read(scope.journalFile(), document));

            var outcome = recover();

            if (stop == Step.STAGE) {
                assertAll(
                        () -> assertEquals(Optional.empty(), journal, "no journal should be written"),
                        () -> assertEquals(new JournalRecovery.NothingToRecover(List.of()), outcome,
                                "there should be nothing to recover"),
                        () -> assertArrayEquals(bytes(oldStatus), Files.readAllBytes(store(STATUS)),
                                "the store should be as before the commit"),
                        () -> assertArrayEquals(sidecarBefore, Files.readAllBytes(scope.sidecarFile()),
                                "the sidecar should be as before the commit"),
                        () -> assertEquals(List.of(), auditEvents.getValues(), "no audit event should be recorded"));
                return;
            }
            var applied = stop == Step.JOURNAL ? List.of(STATUS) : List.<String>of();
            assertAll(
                    () -> assertEquals(new JournalRecovery.RolledForward(journal.orElseThrow(), applied), outcome,
                            "the recovery should roll the delete forward"),
                    () -> assertFalse(Files.exists(store(STATUS)), "the store should be absent"),
                    () -> assertEquals(DigestSidecar.of(Map.of(WORK_LOG, StoreDigest.of(bytes(firstLine)))),
                            sidecar(), "the entry of the deleted store should be dropped"),
                    () -> assertFalse(Files.exists(scope.journalFile()), "the journal should be closed"),
                    () -> assertEquals(List.of(), stagedFiles(), "no staged file should remain"),
                    () -> assertEquals(List.of(AuditEvent.transactionRolledForward(JournalScopeType.PLAN.wireName(),
                                    plan, journal.orElseThrow().transactionId().value(), List.of(STATUS))), auditEvents.getValues(),
                            "the roll-forward should be audited"),
                    () -> assertEquals(new ScopeIntegrity.Absent(STATUS), verify(STATUS),
                            "the store should be consistent, no Mismatch"));
        }
    }

    /**
     * A create target writes a store that must not exist when the transaction commits; the commit checks it under the
     * scope lock, so a store another transaction of the same lock transaction wrote in between is not overwritten.
     */
    @Nested
    @DisplayName("create")
    class Creating {

        @Test
        @DisplayName("writes a store that is absent, with its digest, and leaves no journal and no staged file")
        void absentStore() {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);
                write.create(STATUS, bytes(newStatus));
                write.commit();
            }

            assertAll(
                    () -> assertArrayEquals(bytes(newStatus), Files.readAllBytes(store(STATUS)),
                            "the created store should hold its content"),
                    () -> assertEquals(DigestSidecar.of(Map.of(STATUS, StoreDigest.of(bytes(newStatus)))), sidecar(),
                            "the sidecar should hold the created digest"),
                    () -> assertFalse(Files.exists(scope.journalFile()), "the journal should be closed"),
                    () -> assertEquals(List.of(), stagedFiles(), "no staged file should remain"));
        }

        @Test
        @DisplayName("of a store that exists refuses the commit; nothing is written and the transaction stays open")
        void existingStore() throws Exception {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var first = openUnder(transaction);
                first.replace(STATUS, bytes(oldStatus));
                first.commit();
            }
            var sidecarBefore = Files.readAllBytes(scope.sidecarFile());
            var references = bytes(newReferences);

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);
                write.replace(REFERENCES, bytes(newReferences));
                write.create(STATUS, bytes(newStatus));

                var refusal = assertThrows(IllegalStateException.class, write::commit,
                        "a create of an existing store should fail");

                assertAll(
                        () -> assertTrue(refusal.getMessage().contains(STATUS), "the refusal should name the store"),
                        () -> assertArrayEquals(bytes(oldStatus), Files.readAllBytes(store(STATUS)),
                                "the store should be untouched"),
                        () -> assertFalse(Files.exists(store(REFERENCES)), "no other target should be written"),
                        () -> assertArrayEquals(sidecarBefore, Files.readAllBytes(scope.sidecarFile()),
                                "the sidecar should be untouched"),
                        () -> assertFalse(Files.exists(scope.journalFile()), "no journal should be written"),
                        () -> assertEquals(List.of(), stagedFiles(), "nothing should be staged"),
                        () -> assertThrows(IllegalArgumentException.class,
                                () -> write.replace(REFERENCES, references),
                                "the open transaction should refuse a second"));
            }
        }

        @Test
        @DisplayName("of one store by two transactions of one lock transaction commits the first and refuses the second")
        void interleavedTransactions() {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var first = openUnder(transaction);
                var second = openUnder(transaction);
                first.create(STATUS, bytes(oldStatus));
                second.create(STATUS, bytes(newStatus));

                first.commit();

                assertThrows(IllegalStateException.class, second::commit, "the second create should be refused");
            }

            assertAll(
                    () -> assertArrayEquals(bytes(oldStatus), Files.readAllBytes(store(STATUS)),
                            "the first transaction's content should stay"),
                    () -> assertEquals(DigestSidecar.of(Map.of(STATUS, StoreDigest.of(bytes(oldStatus)))), sidecar(),
                            "the sidecar should hold the first digest"),
                    () -> assertFalse(Files.exists(scope.journalFile()), "the journal should be closed"),
                    () -> assertEquals(List.of(), stagedFiles(), "no staged file should remain"));
        }

        @Test
        @DisplayName("is refused for a store that is a target of the transaction already, like a replace")
        void sameStoreTwice() {
            var content = bytes(newStatus);
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);
                write.replace(STATUS, bytes(oldStatus));
                write.create(REFERENCES, bytes(newReferences));

                assertAll(
                        () -> assertThrows(IllegalArgumentException.class, () -> write.create(STATUS, content),
                                "a create of a replaced store should fail"),
                        () -> assertThrows(IllegalArgumentException.class, () -> write.replace(REFERENCES, content),
                                "a replace of a created store should fail"));
            }
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @Test
        @DisplayName("a transaction is refused without the lock of the scope, and nothing is written")
        void openWithoutLock() {
            try (var transaction = locks.openTransaction()) {
                var fault = assertThrows(InternalFaultException.class, () -> openUnder(transaction),
                        "an open without the lock should be refused");

                assertAll(
                        () -> assertEquals(Reason.STORE_LOCK_NOT_HELD, fault.reason(),
                                "the fault should be STORE_LOCK_NOT_HELD"),
                        // detail key "store" of the fault is its wire contract
                        () -> assertEquals(scope.journalFile().toString(), fault.details().get("store"),
                                "the fault should name the journal"),
                        () -> assertEquals(List.of(), stagedFiles(), "nothing should be staged"));
            }
        }

        @Test
        @DisplayName("a transaction is refused under the key of another plan")
        void openUnderAnotherKey() {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(LockKey.plan(base, PROJECT, anotherPlan));

                var fault = assertThrows(InternalFaultException.class, () -> openUnder(transaction),
                        "an open under another key should be refused");

                assertEquals(Reason.STORE_LOCK_NOT_HELD, fault.reason(), "the fault should be STORE_LOCK_NOT_HELD");
            }
        }

        @Test
        @DisplayName("a commit after the lock of the scope was released is refused, and nothing is written")
        void commitWithoutLock() throws Exception {
            Files.write(store(STATUS), bytes(oldStatus));
            ScopeTransaction write;
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                write = openUnder(transaction);
                write.replace(STATUS, bytes(newStatus));
            }

            var fault = assertThrows(InternalFaultException.class, write::commit,
                    "a commit without the lock should be refused");

            assertAll(
                    () -> assertEquals(Reason.STORE_LOCK_NOT_HELD, fault.reason(),
                            "the fault should be STORE_LOCK_NOT_HELD"),
                    () -> assertArrayEquals(bytes(oldStatus), Files.readAllBytes(store(STATUS)),
                            "the store should be untouched"),
                    () -> assertFalse(Files.exists(scope.journalFile()), "no journal should be written"),
                    () -> assertFalse(Files.exists(scope.sidecarFile()), "no sidecar should be written"),
                    () -> assertEquals(List.of(), stagedFiles(), "nothing should be staged"));
        }

        @Test
        @DisplayName("a store is a target of a transaction once: a second replace or append of its path is refused")
        void samePathTwice() {
            var replacement = bytes(newStatus);
            var second = bytes(secondLine);
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);
                write.replace(STATUS, bytes(oldStatus));
                write.append(WORK_LOG, bytes(firstLine));

                assertAll(
                        () -> assertThrows(IllegalArgumentException.class, () -> write.replace(STATUS, replacement),
                                "a second replace should be refused"),
                        () -> assertThrows(IllegalArgumentException.class, () -> write.append(WORK_LOG, second),
                                "a second append should be refused"),
                        () -> assertThrows(IllegalArgumentException.class,
                                () -> write.replace(WORK_LOG, replacement),
                                "a replace of an appended store should fail"));
            }
        }

        @Test
        @DisplayName("a store named through another spelling of its path is the same target and is refused")
        void sameStoreOtherSpelling() {
            var content = bytes(newStatus);
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);
                write.replace(STATUS, bytes(oldStatus));

                assertAll(
                        () -> assertThrows(IllegalArgumentException.class,
                                () -> write.replace("./" + STATUS, content),
                                "the dot spelling should be the same target"),
                        () -> assertThrows(IllegalArgumentException.class,
                                () -> write.replace("logs/../" + STATUS, content),
                                "the parent spelling should be the same target"));
            }
        }

        @Test
        @DisplayName("the journal and the sidecar name a store in the normalized form of its path")
        void normalizedStorePath() {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);
                write.replace("./logs/../" + STATUS, bytes(newStatus));
                write.commit();
            }

            assertEquals(DigestSidecar.of(Map.of(STATUS, StoreDigest.of(bytes(newStatus)))), sidecar(),
                    "the sidecar should name the normalized path");
        }

        @Test
        @DisplayName("the journal, the sidecar and a path outside the scope are no targets")
        void notAStore() {
            var journal = scope.pathBase().relativize(scope.journalFile()).toString();
            var sidecar = scope.pathBase().relativize(scope.sidecarFile()).toString();
            var outside = "../" + anotherPlan + "/" + STATUS;
            var content = bytes(newStatus);
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);

                assertAll(
                        () -> assertThrows(IllegalArgumentException.class, () -> write.replace(journal, content),
                                "the journal should be no target"),
                        () -> assertThrows(IllegalArgumentException.class, () -> write.replace(sidecar, content),
                                "the sidecar should be no target"),
                        () -> assertThrows(IllegalArgumentException.class, () -> write.replace(outside, content),
                                "a path outside the scope should be no target"));
            }
        }

        @Test
        @DisplayName("a store that is a symbolic link to a file outside the scope is no target; nothing is written")
        void linkedStore() throws Exception {
            var outside = Files.writeString(base.resolve(OUTSIDE), oldStatus);
            SymbolicLinks.createOrSkip(store(STATUS), outside);
            var content = bytes(newStatus);

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);

                assertThrows(IllegalArgumentException.class, () -> write.replace(STATUS, content),
                        "a linked store should be no target");
            }
            assertAll(
                    () -> assertEquals(oldStatus, Files.readString(outside),
                            "the file outside should be left as it is"),
                    () -> assertEquals(List.of(), stagedFiles(), "nothing should be staged"));
        }

        @Test
        @DisplayName("a store below a symbolic link to a directory outside the scope is no target; nothing is written")
        void linkedParent() throws Exception {
            // fixture paths: the directory outside the scope and the link to it
            var outside = Files.createDirectories(base.resolve("outside"));
            var linked = scope.pathBase().resolve("linked");
            SymbolicLinks.createOrSkip(linked, outside);
            var block = bytes(firstLine);
            var content = bytes(newStatus);

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);

                assertAll(
                        () -> assertThrows(IllegalArgumentException.class,
                                () -> write.append("linked/work.jsonl", block),
                                "an append below the link should be refused"),
                        () -> assertThrows(IllegalArgumentException.class,
                                () -> write.replace("linked/status.json", content),
                                "a replace below the link should be refused"));
            }
            try (Stream<Path> files = Files.list(outside)) {
                assertEquals(List.of(), files.toList(), "nothing should be written outside the scope");
            }
        }

        @Test
        @DisplayName("a store below the path base of the workspace but outside its owned directories is no target")
        void outsideOwnedDirectories() {
            var workspace = JournalScope.workspace(base, PROJECT);
            // layout contract: the plans directory below the workspace path base
            var planStore = "plans/" + plan + "/" + STATUS;
            var content = bytes(newStatus);
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(workspace.lockKey());
                var write = ScopeTransaction.open(transaction, workspace, audit, clock);

                assertThrows(IllegalArgumentException.class, () -> write.replace(planStore, content),
                        "a plan store should be no workspace target");
            }
        }

        @Test
        @DisplayName("an empty block and a block without a trailing newline are no append")
        void invalidBlock() {
            var empty = new byte[0];
            var unterminated = bytes(firstLine.strip());
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);

                assertAll(
                        () -> assertThrows(IllegalArgumentException.class, () -> write.append(WORK_LOG, empty),
                                "an empty block should be no append"),
                        () -> assertThrows(IllegalArgumentException.class,
                                () -> write.append(WORK_LOG, unterminated),
                                "an unterminated block should be no append"));
            }
        }

        @Test
        @DisplayName("a transaction without a target is not committed")
        void noTarget() {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);

                assertThrows(IllegalStateException.class, write::commit,
                        "a commit without a target should be refused");
            }
        }

        @Test
        @DisplayName("a committed transaction takes no further target and is not committed again")
        void committedOnce() {
            var references = bytes(newReferences);
            var block = bytes(firstLine);
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);
                write.replace(STATUS, bytes(newStatus));
                write.commit();

                assertAll(
                        () -> assertThrows(IllegalStateException.class, write::commit,
                                "a second commit should be refused"),
                        () -> assertThrows(IllegalStateException.class, () -> write.replace(REFERENCES, references),
                                "a replace after the commit should fail"),
                        () -> assertThrows(IllegalStateException.class, () -> write.append(WORK_LOG, block),
                                "an append after the commit should fail"));
            }
        }
    }
}
