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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.InternalFaultException.Reason;
import de.planmarshall.core.store.JournalRecovery.InvariantViolation;
import de.planmarshall.core.store.ScopeIntegrity.Absent;
import de.planmarshall.core.store.ScopeIntegrity.Mismatch;
import de.planmarshall.core.store.ScopeIntegrity.Untracked;
import de.planmarshall.core.store.ScopeIntegrity.Verification;
import de.planmarshall.core.store.ScopeIntegrity.Verified;
import de.planmarshall.core.store.ScopeTransaction.Step;

/**
 * The digest verification of a scope (PM-IMPL-7): an edit outside a transaction is decided only under the lock of the
 * scope, after its recovery, reported as unverified by a lock-free read and never as a breach, and accepted by a
 * re-baseline through the journal.
 * <p>
 * An out-of-band edit is a write of a store file that bypasses every transaction, as an editor or a script makes it.
 * The store file names of the plan scope, the sidecar format and the shape of a digest are contracts; the plan id, the
 * store contents and the instant of the commits are generated.
 */
@EnableGeneratorController
@DisplayName("Scope integrity")
class ScopeIntegrityTest {

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
    private String oldStatus;
    private String newStatus;
    private String first;
    private Clock clock;

    private JournalScope scope;
    private RecordingLockManager locks;
    private Capture<AuditEvent> auditEvents;
    private AuditSink audit;

    /** What the step hook throws to stop a commit after its journal was written. */
    private static final class StopWrite extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        StopWrite() {
            super("stopped after the journal");
        }
    }

    /** Creates a scope whose status and work log were written by a commit, so the sidecar holds their digests. */
    @BeforeEach
    void createScope() throws IOException {
        plan = "plan-" + lowerName();
        var phase = lowerName();
        oldStatus = "{\"format_version\":1,\"phase\":\"" + phase + "\"}";
        newStatus = "{\"format_version\":1,\"phase\":\"" + phase + "-" + lowerName() + "\"}";
        first = "{\"format_version\":1,\"n\":" + Generators.integers(1, 1000).next() + "}\n";
        clock = Clock.fixed(Instant.ofEpochSecond(Generators.longs(1_700_000_000L, 1_800_000_000L).next()),
                ZoneOffset.UTC);
        scope = JournalScope.plan(base, PROJECT, plan);
        Files.createDirectories(scope.journalFile().getParent());
        Files.createDirectories(scope.resolve(WORK_LOG).getParent());
        locks = new RecordingLockManager();
        auditEvents = newCapture(CaptureType.ALL);
        audit = AuditSinkDoubles.capturing(auditEvents);
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(scope.lockKey());
            var write = ScopeTransaction.open(transaction, scope, audit, clock);
            write.replace(STATUS, bytes(oldStatus));
            write.append(WORK_LOG, bytes(first));
            write.commit();
        }
    }

    private static String lowerName() {
        return Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(UTF_8);
    }

    private Path store(String relativePath) {
        return scope.resolve(relativePath);
    }

    /** Changes one byte of the status outside every transaction: the last letter of the phase becomes a digit. */
    private byte[] editStatus() throws IOException {
        var edited = bytes(oldStatus);
        edited[edited.length - 3] = '5';
        Files.write(store(STATUS), edited);
        return edited;
    }

    private Verification verify(String relativePath) {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(scope.lockKey());
            return ScopeIntegrity.verify(transaction, scope, relativePath, audit);
        }
    }

    private List<Verification> verifyAll() {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(scope.lockKey());
            return ScopeIntegrity.verifyAll(transaction, scope, audit);
        }
    }

    private List<Verification> rebaseline(String relativePath) {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(scope.lockKey());
            return ScopeIntegrity.rebaseline(transaction, scope, relativePath, audit);
        }
    }

    /** Replaces the status in a commit that is stopped after its journal was written. */
    private TransactionJournal stopAfterJournal() {
        ScopeTransaction.StepHook hook = (step, target) -> {
            if (step == Step.JOURNAL) {
                throw new StopWrite();
            }
        };
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(scope.lockKey());
            var write = ScopeTransaction.open(transaction, scope, audit, clock, hook);
            write.replace(STATUS, bytes(newStatus));
            assertThrows(StopWrite.class, write::commit, "the hook should stop the commit");
        }
        return TransactionJournal.read(scope.journalFile(),
                StoreSnapshot.read(scope.journalFile()).content().orElseThrow());
    }

    /** Matched control of every edit below: a store nobody touched is verified both ways. */
    @Test
    @DisplayName("an unedited store is Verified under the lock and VERIFIED lock-free")
    void unedited() {
        var digest = StoreDigest.of(bytes(oldStatus));

        var locked = verify(STATUS);
        var lockFree = ScopeIntegrity.snapshot(scope, STATUS);

        assertAll(
                () -> assertEquals(new Verified(STATUS, digest), locked,
                        "the unedited store should be Verified"),
                () -> assertEquals(StoreIntegrity.VERIFIED, lockFree.integrity(),
                        "the lock-free read should be VERIFIED"),
                () -> assertEquals(store(STATUS), lockFree.store(), "the snapshot should name the store"),
                () -> assertEquals(List.of(), auditEvents.getValues(), "no audit event should be recorded"));
    }

    @Nested
    @DisplayName("an out-of-band edit")
    class OutOfBandEdit {

        @Test
        @DisplayName("of one byte is a Mismatch with the recorded and the current digest under the lock")
        void changedByte() throws Exception {
            var edited = editStatus();

            var outcome = verify(STATUS);

            assertEquals(new Mismatch(STATUS, StoreDigest.of(bytes(oldStatus)),
                            StoreDigest.of(edited)), outcome,
                    "the edit should be a Mismatch of both digests");
        }

        @Test
        @DisplayName("of one byte is DIGEST_UNVERIFIED naming the store lock-free, and decides nothing")
        void changedByteLockFree() throws Exception {
            var edited = editStatus();
            locks.reset();

            var snapshot = ScopeIntegrity.snapshot(scope, STATUS);

            assertAll(
                    () -> assertEquals(StoreIntegrity.DIGEST_UNVERIFIED, snapshot.integrity(),
                            "the lock-free read should be DIGEST_UNVERIFIED"),
                    () -> assertEquals(store(STATUS), snapshot.store(), "the snapshot should name the store"),
                    () -> assertArrayEquals(edited, snapshot.content().orElseThrow(),
                            "the snapshot should carry the edited bytes"),
                    () -> assertEquals(0, locks.transactions(), "no lock should be taken lock-free"),
                    () -> assertEquals(List.of(), auditEvents.getValues(), "no audit event should be recorded"));
        }

        @Test
        @DisplayName("that deletes the store is a Mismatch without a current digest")
        void deletedStore() throws Exception {
            Files.delete(store(STATUS));

            var outcome = verify(STATUS);
            var snapshot = ScopeIntegrity.snapshot(scope, STATUS);

            assertAll(
                    () -> assertEquals(new Mismatch(STATUS, StoreDigest.of(bytes(oldStatus)), null),
                            outcome, "the deletion should be a Mismatch without digest"),
                    () -> assertEquals(StoreIntegrity.DIGEST_UNVERIFIED, snapshot.integrity(),
                            "the lock-free read should be DIGEST_UNVERIFIED"),
                    () -> assertTrue(snapshot.content().isEmpty(), "the deleted store should have no content"));
        }

        @Test
        @DisplayName("is found by verifyAll among the stores of the scope, in the order of their paths")
        void verifyAllFindsEdit() throws Exception {
            var edited = editStatus();

            var answers = verifyAll();

            assertEquals(List.of(new Verified(WORK_LOG, StoreDigest.of(bytes(first))),
                            new Mismatch(STATUS, StoreDigest.of(bytes(oldStatus)), StoreDigest.of(edited))),
                    answers, "verifyAll should find the edit in path order");
        }

        @Test
        @DisplayName("is accepted by a re-baseline: verifyAll is all Verified and the sidecar holds the edited digest")
        void rebaselined() throws Exception {
            var edited = editStatus();

            var answers = rebaseline(STATUS);

            assertAll(
                    () -> assertEquals(List.of(new Verified(WORK_LOG, StoreDigest.of(bytes(first))),
                                    new Verified(STATUS, StoreDigest.of(edited))), answers,
                            "the re-baseline should verify every store"),
                    () -> assertEquals(Optional.of(StoreDigest.of(edited)),
                            DigestSidecar.load(scope.sidecarFile()).digest(STATUS),
                            "the sidecar should hold the edited digest"),
                    () -> assertArrayEquals(edited, Files.readAllBytes(store(STATUS)),
                            "the store should not be changed"),
                    () -> assertFalse(Files.exists(scope.journalFile()),
                            "the re-baseline transaction should be closed"),
                    () -> assertEquals(StoreIntegrity.VERIFIED, ScopeIntegrity.snapshot(scope, STATUS).integrity(),
                            "the lock-free read should be VERIFIED again"),
                    () -> assertEquals(List.of(), auditEvents.getValues(), "no audit event should be recorded"));
        }
    }

    @Nested
    @DisplayName("with a journal present")
    class JournalPresent {

        @Test
        @DisplayName("snapshot reports DIGEST_UNVERIFIED, even for a store that matches, and leaves the journal")
        void snapshotUnverified() {
            stopAfterJournal();

            var status = ScopeIntegrity.snapshot(scope, STATUS);
            var workLog = ScopeIntegrity.snapshot(scope, WORK_LOG);

            assertAll(
                    () -> assertEquals(StoreIntegrity.DIGEST_UNVERIFIED, status.integrity(),
                            "the status read should be DIGEST_UNVERIFIED"),
                    () -> assertEquals(store(STATUS), status.store(), "the snapshot should name the status"),
                    () -> assertArrayEquals(bytes(oldStatus), status.content().orElseThrow(),
                            "the store should keep its recorded content"),
                    () -> assertEquals(StoreIntegrity.DIGEST_UNVERIFIED, workLog.integrity(),
                            "the work-log read should be DIGEST_UNVERIFIED"),
                    () -> assertTrue(Files.exists(scope.journalFile()), "a lock-free read should never recover"),
                    () -> assertEquals(List.of(), auditEvents.getValues(), "no audit event should be recorded"));
        }

        /**
         * The journal that appears between the first look and the end of the read cannot be provoked without a
         * production hook; the second look is covered by its structure: a read already taken is reported unverified
         * with the content it read, without a second read.
         */
        @Test
        @DisplayName("race guard: a journal found after the read turns a VERIFIED read into DIGEST_UNVERIFIED, same bytes")
        void journalAfterRead() {
            var digest = StoreDigest.of(bytes(oldStatus));
            var read = StoreSnapshot.read(store(STATUS), digest.size(), digest.sha256());

            var unverified = read.asUnverified();

            assertAll(
                    () -> assertEquals(StoreIntegrity.VERIFIED, read.integrity(),
                            "the read itself should be VERIFIED"),
                    () -> assertEquals(StoreIntegrity.DIGEST_UNVERIFIED, unverified.integrity(),
                            "the turned read should be DIGEST_UNVERIFIED"),
                    () -> assertEquals(read.store(), unverified.store(), "the turned read should keep its store"),
                    () -> assertArrayEquals(read.content().orElseThrow(), unverified.content().orElseThrow(),
                            "the turned read should keep its bytes"));
        }

        @Test
        @DisplayName("verify rolls the journal forward first and answers Verified")
        void verifyRollsForward() {
            var journal = stopAfterJournal();

            var outcome = verify(STATUS);

            assertAll(
                    () -> assertEquals(new Verified(STATUS, StoreDigest.of(bytes(newStatus))), outcome,
                            "the rolled-forward store should be Verified"),
                    () -> assertFalse(Files.exists(scope.journalFile()), "the journal should be removed"),
                    () -> assertEquals(List.of(AuditEvent.transactionRolledForward(JournalScopeType.PLAN.wireName(),
                                    plan, journal.transactionId().value(), List.of(STATUS))), auditEvents.getValues(),
                            "the roll-forward should be audited"));
        }

        @Test
        @DisplayName("that cannot be rolled forward is the answer of verify, verifyAll and rebaseline")
        void invariantViolation() throws Exception {
            var journal = stopAfterJournal();
            Files.delete(scope.resolve(journal.targets().getFirst().stagedPath()));
            var journalBytes = Files.readAllBytes(scope.journalFile());
            var sidecarBytes = Files.readAllBytes(scope.sidecarFile());
            var violation = new InvariantViolation(journal, List.of(STATUS));

            var verified = verify(STATUS);
            var all = verifyAll();
            var rebaselined = rebaseline(STATUS);

            assertAll(
                    () -> assertEquals(violation, verified, "verify should answer the violation"),
                    () -> assertEquals(List.of(violation), all, "verifyAll should answer the violation"),
                    () -> assertEquals(List.of(violation), rebaselined, "rebaseline should answer the violation"),
                    () -> assertArrayEquals(journalBytes, Files.readAllBytes(scope.journalFile()),
                            "the journal should be left as it is"),
                    () -> assertArrayEquals(sidecarBytes, Files.readAllBytes(scope.sidecarFile()),
                            "the sidecar should be left as it is"),
                    () -> assertArrayEquals(bytes(oldStatus), Files.readAllBytes(store(STATUS)),
                            "the store should keep its old content"),
                    () -> assertEquals(List.of(), auditEvents.getValues(), "no audit event should be recorded"));
        }
    }

    @Nested
    @DisplayName("a store without a sidecar entry")
    class WithoutEntry {

        @Test
        @DisplayName("Absent is built only with a store")
        void absentRecord() {
            assertThrows(NullPointerException.class, () -> new Absent(null),
                    "Absent without a store should be refused");
        }

        @Test
        @DisplayName("is Untracked under the lock and UNCHECKED lock-free")
        void untracked() throws Exception {
            Files.write(store(REFERENCES), bytes(newStatus));

            var outcome = verify(REFERENCES);
            var snapshot = ScopeIntegrity.snapshot(scope, REFERENCES);

            assertAll(
                    () -> assertEquals(new Untracked(REFERENCES), outcome, "the store should be Untracked"),
                    () -> assertEquals(StoreIntegrity.UNCHECKED, snapshot.integrity(),
                            "the lock-free read should be UNCHECKED"));
        }

        @Test
        @DisplayName("that does not exist either is Absent, consistent and no Mismatch, and UNCHECKED lock-free")
        void absent() {
            var outcome = verify(REFERENCES);
            var snapshot = ScopeIntegrity.snapshot(scope, REFERENCES);

            assertAll(
                    () -> assertEquals(new Absent(REFERENCES), outcome, "the missing store should be Absent"),
                    () -> assertEquals(StoreIntegrity.UNCHECKED, snapshot.integrity(),
                            "the lock-free read should be UNCHECKED"),
                    () -> assertTrue(snapshot.content().isEmpty(), "the missing store should have no content"),
                    () -> assertEquals(List.of(), auditEvents.getValues(), "no audit event should be recorded"));
        }

        @Test
        @DisplayName("because a transaction deleted it is Absent, and verifyAll no longer names it")
        void deletedByTransaction() {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = ScopeTransaction.open(transaction, scope, audit, clock);
                write.delete(STATUS);
                write.commit();
            }

            var outcome = verify(STATUS);
            var all = verifyAll();

            assertAll(
                    () -> assertEquals(new Absent(STATUS), outcome, "the deleted store should be Absent"),
                    () -> assertEquals(List.of(new Verified(WORK_LOG, StoreDigest.of(bytes(first)))), all,
                            "verifyAll should name only the work log"),
                    () -> assertTrue(all.stream().noneMatch(Mismatch.class::isInstance),
                            "no store should be reported tampered"));
        }

        /** Matched control of {@link #absent()}: an entry that outlives its store stays the evidence of an edit. */
        @Test
        @DisplayName("a store absent while its digest is present stays a Mismatch")
        void absentWithDigest() throws Exception {
            Files.delete(store(STATUS));

            var outcome = verify(STATUS);

            assertEquals(new Mismatch(STATUS, StoreDigest.of(bytes(oldStatus)), null), outcome,
                    "a recorded digest should keep the Mismatch");
        }

        @Test
        @DisplayName("that does not exist is refused by a re-baseline, and nothing is journaled")
        void rebaselineAbsent() throws Exception {
            var sidecarBytes = Files.readAllBytes(scope.sidecarFile());

            assertThrows(IllegalArgumentException.class, () -> rebaseline(REFERENCES),
                    "a re-baseline of a missing store should fail");

            assertAll(
                    () -> assertFalse(Files.exists(store(REFERENCES)), "the store should not be created"),
                    () -> assertFalse(Files.exists(scope.journalFile()), "no journal should be written"),
                    () -> assertArrayEquals(sidecarBytes, Files.readAllBytes(scope.sidecarFile()),
                            "the sidecar should be left as it is"),
                    () -> assertEquals(List.of(), auditEvents.getValues(), "no audit event should be recorded"));
        }
    }

    @Nested
    @DisplayName("digests computed by streaming")
    class Streamed {

        private byte[] large() {
            var large = new ByteArrayOutputStream();
            while (large.size() < 4 * StoreDigest.Builder.BUFFER_SIZE + 5) {
                large.writeBytes(bytes(first));
            }
            return large.toByteArray();
        }

        @Test
        @DisplayName("a store of several buffer sizes is Verified, and a change of its last byte a Mismatch")
        void largeStore() throws Exception {
            var content = large();
            Files.write(store(REFERENCES), content);
            rebaseline(REFERENCES);
            var verified = verify(REFERENCES);
            content[content.length - 2] = '9';
            Files.write(store(REFERENCES), content);

            var mismatch = verify(REFERENCES);

            assertAll(
                    () -> assertEquals(new Verified(REFERENCES, StoreDigest.of(large())), verified,
                            "the large store should be Verified"),
                    () -> assertEquals(new Mismatch(REFERENCES, StoreDigest.of(large()),
                                    StoreDigest.of(content)), mismatch,
                            "a changed last byte should be a Mismatch"));
        }

        @Test
        @DisplayName("a store of zero bytes is Verified against its recorded digest, as before")
        void emptyStore() throws Exception {
            Files.write(store(REFERENCES), new byte[0]);
            rebaseline(REFERENCES);

            var outcome = verify(REFERENCES);

            assertEquals(new Verified(REFERENCES, StoreDigest.of(new byte[0])), outcome,
                    "an empty store should be Verified");
        }
    }

    @Nested
    @DisplayName("a store path that leaves the scope")
    class Confinement {

        /** Writes a sidecar in its wire format whose one entry carries a well-formed digest of 64 hex digits. */
        private void writeSidecarWithKey(String key) throws IOException {
            Files.writeString(scope.sidecarFile(), "{\"format_version\":1,\"stores\":{\"" + key
                    + "\":{\"size\":1,\"sha256\":\"" + "a".repeat(64) + "\"}}}");
        }

        @Test
        @DisplayName("an absolute sidecar key makes verifyAll a StoreRefusal naming the sidecar")
        void absoluteKey() throws Exception {
            writeSidecarWithKey(base.resolve(OUTSIDE).toString());

            var refusal = assertThrows(StoreRefusal.class, () -> verifyAll(),
                    "an absolute key should be refused");

            assertAll(
                    () -> assertEquals(StoreRefusal.Outcome.UNREADABLE, refusal.outcome(),
                            "the refusal should be UNREADABLE"),
                    () -> assertEquals(scope.sidecarFile(), refusal.file(), "the refusal should name the sidecar"));
        }

        @Test
        @DisplayName("a sidecar key with a parent segment makes verifyAll a StoreRefusal naming the sidecar")
        void parentKey() throws Exception {
            writeSidecarWithKey("../plan-" + lowerName() + "/" + STATUS);

            var refusal = assertThrows(StoreRefusal.class, () -> verifyAll(),
                    "a key with a parent segment should fail");

            assertAll(
                    () -> assertEquals(StoreRefusal.Outcome.UNREADABLE, refusal.outcome(),
                            "the refusal should be UNREADABLE"),
                    () -> assertEquals(scope.sidecarFile(), refusal.file(), "the refusal should name the sidecar"));
        }

        @Test
        @DisplayName("a sidecar entry whose store is a symbolic link makes verifyAll a StoreRefusal naming the sidecar")
        void linkedStoreOfSidecar() throws Exception {
            var outside = Files.writeString(base.resolve(OUTSIDE), oldStatus);
            Files.delete(store(STATUS));
            SymbolicLinks.createOrSkip(store(STATUS), outside);

            var refusal = assertThrows(StoreRefusal.class, () -> verifyAll(),
                    "a linked store should be refused");

            assertEquals(scope.sidecarFile(), refusal.file(), "the refusal should name the sidecar");
        }

        @Test
        @DisplayName("a caller path through a symbolic link is refused by verify, snapshot and rebaseline")
        void linkedCallerPath() throws Exception {
            var outside = Files.writeString(base.resolve(OUTSIDE), oldStatus);
            Files.delete(store(STATUS));
            SymbolicLinks.createOrSkip(store(STATUS), outside);

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> verify(STATUS),
                            "verify should refuse the linked path"),
                    () -> assertThrows(IllegalArgumentException.class, () -> ScopeIntegrity.snapshot(scope, STATUS),
                            "snapshot should refuse the linked path"),
                    () -> assertThrows(IllegalArgumentException.class, () -> rebaseline(STATUS),
                            "rebaseline should refuse the linked path"),
                    () -> assertEquals(oldStatus, Files.readString(outside),
                            "the file outside should be left as it is"));
        }

        @Test
        @DisplayName("a journal whose store became a symbolic link is refused naming the journal, and nothing moves")
        void linkedStoreOfJournal() throws Exception {
            stopAfterJournal();
            var outside = Files.writeString(base.resolve(OUTSIDE), oldStatus);
            Files.delete(store(STATUS));
            SymbolicLinks.createOrSkip(store(STATUS), outside);
            var journalBytes = Files.readAllBytes(scope.journalFile());

            var refusal = assertThrows(StoreRefusal.class, () -> verifyAll(),
                    "a journal with a linked store should fail");

            assertAll(
                    () -> assertEquals(scope.journalFile(), refusal.file(), "the refusal should name the journal"),
                    () -> assertArrayEquals(journalBytes, Files.readAllBytes(scope.journalFile()),
                            "the journal should be left as it is"),
                    () -> assertEquals(oldStatus, Files.readString(outside),
                            "nothing should be written outside the scope"),
                    () -> assertTrue(Files.isSymbolicLink(store(STATUS)), "the link should not be replaced"),
                    () -> assertEquals(List.of(), auditEvents.getValues(), "no audit event should be recorded"));
        }
    }

    /**
     * The sidecar records a store under its normalized path; a caller that spells the path otherwise, but below the
     * path base, names the same store and must be compared with the same entry. The spellings are fixture paths.
     */
    @Nested
    @DisplayName("a store path spelled non-normalized")
    class NonNormalizedSpelling {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"./status.json", "logs/../status.json", "x/../status.json"})
        @DisplayName("is Verified under the lock and VERIFIED lock-free for an unedited store, not Untracked")
        void unedited(String spelling) {
            var digest = StoreDigest.of(bytes(oldStatus));

            var locked = verify(spelling);
            var lockFree = ScopeIntegrity.snapshot(scope, spelling);

            assertAll(
                    () -> assertEquals(new Verified(STATUS, digest), locked,
                            "the spelling should be Verified as the store"),
                    () -> assertEquals(StoreIntegrity.VERIFIED, lockFree.integrity(),
                            "the lock-free read should be VERIFIED"),
                    () -> assertEquals(store(STATUS), lockFree.store(),
                            "the snapshot should name the normalized store"));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"./status.json", "logs/../status.json", "x/../status.json"})
        @DisplayName("is a Mismatch under the lock and DIGEST_UNVERIFIED lock-free for an edited store, not UNCHECKED")
        void edited(String spelling) throws Exception {
            var edited = editStatus();

            var locked = verify(spelling);
            var lockFree = ScopeIntegrity.snapshot(scope, spelling);

            assertAll(
                    () -> assertEquals(new Mismatch(STATUS, StoreDigest.of(bytes(oldStatus)),
                                    StoreDigest.of(edited)), locked,
                            "the spelling should be a Mismatch as the store"),
                    () -> assertEquals(StoreIntegrity.DIGEST_UNVERIFIED, lockFree.integrity(),
                            "the lock-free read should be DIGEST_UNVERIFIED"),
                    () -> assertArrayEquals(edited, lockFree.content().orElseThrow(),
                            "the snapshot should carry the edited bytes"));
        }
    }

    @Test
    @DisplayName("verify without the lock of the scope is refused and touches nothing")
    void withoutLock() throws Exception {
        var edited = editStatus();

        try (var transaction = locks.openTransaction()) {
            var fault = assertThrows(InternalFaultException.class,
                    () -> ScopeIntegrity.verify(transaction, scope, STATUS, audit),
                    "verify without the lock should be refused");

            assertAll(
                    () -> assertEquals(Reason.STORE_LOCK_NOT_HELD, fault.reason(),
                            "the fault should be STORE_LOCK_NOT_HELD"),
                    () -> assertArrayEquals(edited, Files.readAllBytes(store(STATUS)),
                            "the store should be left as it is"),
                    () -> assertEquals(List.of(), auditEvents.getValues(), "no audit event should be recorded"));
        }
    }

    @Test
    @DisplayName("a store path outside the scope is refused")
    void outsideScope() {
        var outside = "../plan-" + lowerName() + "/" + lowerName();

        assertThrows(IllegalArgumentException.class, () -> ScopeIntegrity.snapshot(scope, outside),
                "a path outside the scope should be refused");
    }
}
