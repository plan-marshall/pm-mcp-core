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
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serial;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.service.AuditEvent;
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
 */
@DisplayName("Scope integrity")
class ScopeIntegrityTest {

    private static final String PROJECT = "pm-mcp-core";
    private static final String PLAN = "store-substrate";
    private static final String STATUS = "status.json";
    private static final String REFERENCES = "references.json";
    private static final String WORK_LOG = "logs/work.jsonl";
    private static final String OLD_STATUS = "{\"format_version\":1,\"phase\":\"4-plan\"}";
    private static final String NEW_STATUS = "{\"format_version\":1,\"phase\":\"5-execute\"}";
    private static final String FIRST = "{\"format_version\":1,\"n\":1}\n";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path base;

    private JournalScope scope;
    private RecordingLockManager locks;
    private RecordingAuditSink audit;

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
        scope = JournalScope.plan(base, PROJECT, PLAN);
        Files.createDirectories(scope.journalFile().getParent());
        Files.createDirectories(scope.resolve(WORK_LOG).getParent());
        locks = new RecordingLockManager();
        audit = new RecordingAuditSink();
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(scope.lockKey());
            var write = ScopeTransaction.open(transaction, scope, audit, CLOCK);
            write.replace(STATUS, bytes(OLD_STATUS));
            write.append(WORK_LOG, bytes(FIRST));
            write.commit();
        }
    }

    private static byte[] bytes(String text) {
        return text.getBytes(UTF_8);
    }

    private Path store(String relativePath) {
        return scope.resolve(relativePath);
    }

    /** Changes one byte of the status outside every transaction. */
    private byte[] editStatus() throws IOException {
        var edited = bytes(OLD_STATUS);
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
            var write = ScopeTransaction.open(transaction, scope, audit, CLOCK, hook);
            write.replace(STATUS, bytes(NEW_STATUS));
            assertThrows(StopWrite.class, write::commit);
        }
        return TransactionJournal.read(scope.journalFile(),
                StoreSnapshot.read(scope.journalFile()).content().orElseThrow());
    }

    /** Matched control of every edit below: a store nobody touched is verified both ways. */
    @Test
    @DisplayName("an unedited store is Verified under the lock and VERIFIED lock-free")
    void unedited() {
        var digest = StoreDigest.of(bytes(OLD_STATUS));

        var locked = verify(STATUS);
        var lockFree = ScopeIntegrity.snapshot(scope, STATUS);

        assertAll(
                () -> assertEquals(new Verified(STATUS, digest), locked),
                () -> assertEquals(StoreIntegrity.VERIFIED, lockFree.integrity()),
                () -> assertEquals(store(STATUS), lockFree.store()),
                () -> assertEquals(List.of(), audit.events()));
    }

    @Nested
    @DisplayName("an out-of-band edit")
    class OutOfBandEdit {

        @Test
        @DisplayName("of one byte is a Mismatch with the recorded and the current digest under the lock")
        void changedByte() throws Exception {
            var edited = editStatus();

            var outcome = verify(STATUS);

            assertEquals(new Mismatch(STATUS, StoreDigest.of(bytes(OLD_STATUS)),
                    Optional.of(StoreDigest.of(edited))), outcome);
        }

        @Test
        @DisplayName("of one byte is DIGEST_UNVERIFIED naming the store lock-free, and decides nothing")
        void changedByteLockFree() throws Exception {
            var edited = editStatus();
            locks.reset();

            var snapshot = ScopeIntegrity.snapshot(scope, STATUS);

            assertAll(
                    () -> assertEquals(StoreIntegrity.DIGEST_UNVERIFIED, snapshot.integrity()),
                    () -> assertEquals(store(STATUS), snapshot.store()),
                    () -> assertArrayEquals(edited, snapshot.content().orElseThrow()),
                    () -> assertEquals(0, locks.transactions(), "no lock was taken"),
                    () -> assertEquals(List.of(), audit.events()));
        }

        @Test
        @DisplayName("that deletes the store is a Mismatch without a current digest")
        void deletedStore() throws Exception {
            Files.delete(store(STATUS));

            var outcome = verify(STATUS);
            var snapshot = ScopeIntegrity.snapshot(scope, STATUS);

            assertAll(
                    () -> assertEquals(new Mismatch(STATUS, StoreDigest.of(bytes(OLD_STATUS)), Optional.empty()),
                            outcome),
                    () -> assertEquals(StoreIntegrity.DIGEST_UNVERIFIED, snapshot.integrity()),
                    () -> assertTrue(snapshot.content().isEmpty(), "the store does not exist"));
        }

        @Test
        @DisplayName("is found by verifyAll among the stores of the scope, in the order of their paths")
        void verifyAllFindsEdit() throws Exception {
            var edited = editStatus();

            var answers = verifyAll();

            assertEquals(List.of(new Verified(WORK_LOG, StoreDigest.of(bytes(FIRST))),
                            new Mismatch(STATUS, StoreDigest.of(bytes(OLD_STATUS)), Optional.of(StoreDigest.of(edited)))),
                    answers);
        }

        @Test
        @DisplayName("is accepted by a re-baseline: verifyAll is all Verified and the sidecar holds the edited digest")
        void rebaselined() throws Exception {
            var edited = editStatus();

            var answers = rebaseline(STATUS);

            assertAll(
                    () -> assertEquals(List.of(new Verified(WORK_LOG, StoreDigest.of(bytes(FIRST))),
                            new Verified(STATUS, StoreDigest.of(edited))), answers),
                    () -> assertEquals(Optional.of(StoreDigest.of(edited)),
                            DigestSidecar.load(scope.sidecarFile()).digest(STATUS)),
                    () -> assertArrayEquals(edited, Files.readAllBytes(store(STATUS)), "the store is not changed"),
                    () -> assertFalse(Files.exists(scope.journalFile()), "the re-baseline transaction is closed"),
                    () -> assertEquals(StoreIntegrity.VERIFIED, ScopeIntegrity.snapshot(scope, STATUS).integrity()),
                    () -> assertEquals(List.of(), audit.events()));
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
                    () -> assertEquals(StoreIntegrity.DIGEST_UNVERIFIED, status.integrity()),
                    () -> assertEquals(store(STATUS), status.store()),
                    () -> assertArrayEquals(bytes(OLD_STATUS), status.content().orElseThrow(),
                            "the store still has the content its sidecar entry records"),
                    () -> assertEquals(StoreIntegrity.DIGEST_UNVERIFIED, workLog.integrity()),
                    () -> assertTrue(Files.exists(scope.journalFile()), "a lock-free read never recovers"),
                    () -> assertEquals(List.of(), audit.events()));
        }

        /**
         * The journal that appears between the first look and the end of the read cannot be provoked without a
         * production hook; the second look is covered by its structure: a read already taken is reported unverified
         * with the content it read, without a second read.
         */
        @Test
        @DisplayName("race guard: a journal found after the read turns a VERIFIED read into DIGEST_UNVERIFIED, same bytes")
        void journalAfterRead() {
            var digest = StoreDigest.of(bytes(OLD_STATUS));
            var read = StoreSnapshot.read(store(STATUS), digest.size(), digest.sha256());

            var unverified = read.asUnverified();

            assertAll(
                    () -> assertEquals(StoreIntegrity.VERIFIED, read.integrity()),
                    () -> assertEquals(StoreIntegrity.DIGEST_UNVERIFIED, unverified.integrity()),
                    () -> assertEquals(read.store(), unverified.store()),
                    () -> assertArrayEquals(read.content().orElseThrow(), unverified.content().orElseThrow()));
        }

        @Test
        @DisplayName("verify rolls the journal forward first and answers Verified")
        void verifyRollsForward() {
            var journal = stopAfterJournal();

            var outcome = verify(STATUS);

            assertAll(
                    () -> assertEquals(new Verified(STATUS, StoreDigest.of(bytes(NEW_STATUS))), outcome),
                    () -> assertFalse(Files.exists(scope.journalFile())),
                    () -> assertEquals(List.of(AuditEvent.transactionRolledForward(JournalScopeType.PLAN.wireName(),
                            PLAN, journal.transactionId().value(), List.of(STATUS))), audit.events()));
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
                    () -> assertEquals(violation, verified),
                    () -> assertEquals(List.of(violation), all),
                    () -> assertEquals(List.of(violation), rebaselined),
                    () -> assertArrayEquals(journalBytes, Files.readAllBytes(scope.journalFile())),
                    () -> assertArrayEquals(sidecarBytes, Files.readAllBytes(scope.sidecarFile())),
                    () -> assertArrayEquals(bytes(OLD_STATUS), Files.readAllBytes(store(STATUS))),
                    () -> assertEquals(List.of(), audit.events()));
        }
    }

    @Nested
    @DisplayName("a store without a sidecar entry")
    class WithoutEntry {

        @Test
        @DisplayName("Absent is built only with a store")
        void absentRecord() {
            assertThrows(NullPointerException.class, () -> new Absent(null));
        }

        @Test
        @DisplayName("is Untracked under the lock and UNCHECKED lock-free")
        void untracked() throws Exception {
            Files.write(store(REFERENCES), bytes(NEW_STATUS));

            var outcome = verify(REFERENCES);
            var snapshot = ScopeIntegrity.snapshot(scope, REFERENCES);

            assertAll(
                    () -> assertEquals(new Untracked(REFERENCES), outcome),
                    () -> assertEquals(StoreIntegrity.UNCHECKED, snapshot.integrity()));
        }

        @Test
        @DisplayName("that does not exist either is Absent, consistent and no Mismatch, and UNCHECKED lock-free")
        void absent() {
            var outcome = verify(REFERENCES);
            var snapshot = ScopeIntegrity.snapshot(scope, REFERENCES);

            assertAll(
                    () -> assertEquals(new Absent(REFERENCES), outcome),
                    () -> assertEquals(StoreIntegrity.UNCHECKED, snapshot.integrity()),
                    () -> assertTrue(snapshot.content().isEmpty()),
                    () -> assertEquals(List.of(), audit.events()));
        }

        @Test
        @DisplayName("because a transaction deleted it is Absent, and verifyAll no longer names it")
        void deletedByTransaction() {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = ScopeTransaction.open(transaction, scope, audit, CLOCK);
                write.delete(STATUS);
                write.commit();
            }

            var outcome = verify(STATUS);
            var all = verifyAll();

            assertAll(
                    () -> assertEquals(new Absent(STATUS), outcome),
                    () -> assertEquals(List.of(new Verified(WORK_LOG, StoreDigest.of(bytes(FIRST)))), all),
                    () -> assertTrue(all.stream().noneMatch(Mismatch.class::isInstance), "no store_tampered"));
        }

        /** Matched control of {@link #absent()}: an entry that outlives its store stays the evidence of an edit. */
        @Test
        @DisplayName("a store absent while its digest is present stays a Mismatch")
        void absentWithDigest() throws Exception {
            Files.delete(store(STATUS));

            var outcome = verify(STATUS);

            assertEquals(new Mismatch(STATUS, StoreDigest.of(bytes(OLD_STATUS)), Optional.empty()), outcome);
        }

        @Test
        @DisplayName("that does not exist is refused by a re-baseline, and nothing is journaled")
        void rebaselineAbsent() throws Exception {
            var sidecarBytes = Files.readAllBytes(scope.sidecarFile());

            assertThrows(IllegalArgumentException.class, () -> rebaseline(REFERENCES));

            assertAll(
                    () -> assertFalse(Files.exists(store(REFERENCES))),
                    () -> assertFalse(Files.exists(scope.journalFile())),
                    () -> assertArrayEquals(sidecarBytes, Files.readAllBytes(scope.sidecarFile())),
                    () -> assertEquals(List.of(), audit.events()));
        }
    }

    @Nested
    @DisplayName("digests computed by streaming")
    class Streamed {

        private byte[] large() {
            var large = new ByteArrayOutputStream();
            while (large.size() < 4 * StoreDigest.Builder.BUFFER_SIZE + 5) {
                large.writeBytes(bytes(FIRST));
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
                    () -> assertEquals(new Verified(REFERENCES, StoreDigest.of(large())), verified),
                    () -> assertEquals(new Mismatch(REFERENCES, StoreDigest.of(large()),
                            Optional.of(StoreDigest.of(content))), mismatch));
        }

        @Test
        @DisplayName("a store of zero bytes is Verified against its recorded digest, as before")
        void emptyStore() throws Exception {
            Files.write(store(REFERENCES), new byte[0]);
            rebaseline(REFERENCES);

            var outcome = verify(REFERENCES);

            assertEquals(new Verified(REFERENCES, StoreDigest.of(new byte[0])), outcome);
        }
    }

    @Nested
    @DisplayName("a store path that leaves the scope")
    class Confinement {

        private void writeSidecarWithKey(String key) throws IOException {
            Files.writeString(scope.sidecarFile(), "{\"format_version\":1,\"stores\":{\"" + key
                    + "\":{\"size\":1,\"sha256\":\"" + "a".repeat(64) + "\"}}}");
        }

        @Test
        @DisplayName("an absolute sidecar key makes verifyAll a StoreRefusal naming the sidecar")
        void absoluteKey() throws Exception {
            writeSidecarWithKey(base.resolve("outside.json").toString());

            var refusal = assertThrows(StoreRefusal.class, () -> verifyAll());

            assertAll(
                    () -> assertEquals(StoreRefusal.Outcome.UNREADABLE, refusal.outcome()),
                    () -> assertEquals(scope.sidecarFile(), refusal.file()));
        }

        @Test
        @DisplayName("a sidecar key with a parent segment makes verifyAll a StoreRefusal naming the sidecar")
        void parentKey() throws Exception {
            writeSidecarWithKey("../another-plan/status.json");

            var refusal = assertThrows(StoreRefusal.class, () -> verifyAll());

            assertAll(
                    () -> assertEquals(StoreRefusal.Outcome.UNREADABLE, refusal.outcome()),
                    () -> assertEquals(scope.sidecarFile(), refusal.file()));
        }

        @Test
        @DisplayName("a sidecar entry whose store is a symbolic link makes verifyAll a StoreRefusal naming the sidecar")
        void linkedStoreOfSidecar() throws Exception {
            var outside = Files.writeString(base.resolve("outside.json"), OLD_STATUS);
            Files.delete(store(STATUS));
            SymbolicLinks.createOrSkip(store(STATUS), outside);

            var refusal = assertThrows(StoreRefusal.class, () -> verifyAll());

            assertEquals(scope.sidecarFile(), refusal.file());
        }

        @Test
        @DisplayName("a caller path through a symbolic link is refused by verify, snapshot and rebaseline")
        void linkedCallerPath() throws Exception {
            var outside = Files.writeString(base.resolve("outside.json"), OLD_STATUS);
            Files.delete(store(STATUS));
            SymbolicLinks.createOrSkip(store(STATUS), outside);

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> verify(STATUS)),
                    () -> assertThrows(IllegalArgumentException.class, () -> ScopeIntegrity.snapshot(scope, STATUS)),
                    () -> assertThrows(IllegalArgumentException.class, () -> rebaseline(STATUS)),
                    () -> assertEquals(OLD_STATUS, Files.readString(outside)));
        }

        @Test
        @DisplayName("a journal whose store became a symbolic link is refused naming the journal, and nothing moves")
        void linkedStoreOfJournal() throws Exception {
            stopAfterJournal();
            var outside = Files.writeString(base.resolve("outside.json"), OLD_STATUS);
            Files.delete(store(STATUS));
            SymbolicLinks.createOrSkip(store(STATUS), outside);
            var journalBytes = Files.readAllBytes(scope.journalFile());

            var refusal = assertThrows(StoreRefusal.class, () -> verifyAll());

            assertAll(
                    () -> assertEquals(scope.journalFile(), refusal.file()),
                    () -> assertArrayEquals(journalBytes, Files.readAllBytes(scope.journalFile())),
                    () -> assertEquals(OLD_STATUS, Files.readString(outside), "nothing written outside the scope"),
                    () -> assertTrue(Files.isSymbolicLink(store(STATUS)), "the link was not replaced"),
                    () -> assertEquals(List.of(), audit.events()));
        }
    }

    /**
     * The sidecar records a store under its normalized path; a caller that spells the path otherwise, but below the
     * path base, names the same store and must be compared with the same entry.
     */
    @Nested
    @DisplayName("a store path spelled non-normalized")
    class NonNormalizedSpelling {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"./status.json", "logs/../status.json", "x/../status.json"})
        @DisplayName("is Verified under the lock and VERIFIED lock-free for an unedited store, not Untracked")
        void unedited(String spelling) {
            var digest = StoreDigest.of(bytes(OLD_STATUS));

            var locked = verify(spelling);
            var lockFree = ScopeIntegrity.snapshot(scope, spelling);

            assertAll(
                    () -> assertEquals(new Verified(STATUS, digest), locked),
                    () -> assertEquals(StoreIntegrity.VERIFIED, lockFree.integrity()),
                    () -> assertEquals(store(STATUS), lockFree.store()));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"./status.json", "logs/../status.json", "x/../status.json"})
        @DisplayName("is a Mismatch under the lock and DIGEST_UNVERIFIED lock-free for an edited store, not UNCHECKED")
        void edited(String spelling) throws Exception {
            var edited = editStatus();

            var locked = verify(spelling);
            var lockFree = ScopeIntegrity.snapshot(scope, spelling);

            assertAll(
                    () -> assertEquals(new Mismatch(STATUS, StoreDigest.of(bytes(OLD_STATUS)),
                            Optional.of(StoreDigest.of(edited))), locked),
                    () -> assertEquals(StoreIntegrity.DIGEST_UNVERIFIED, lockFree.integrity()),
                    () -> assertArrayEquals(edited, lockFree.content().orElseThrow()));
        }
    }

    @Test
    @DisplayName("verify without the lock of the scope is refused and touches nothing")
    void withoutLock() throws Exception {
        var edited = editStatus();

        try (var transaction = locks.openTransaction()) {
            var fault = assertThrows(InternalFaultException.class,
                    () -> ScopeIntegrity.verify(transaction, scope, STATUS, audit));

            assertAll(
                    () -> assertEquals(Reason.STORE_LOCK_NOT_HELD, fault.reason()),
                    () -> assertArrayEquals(edited, Files.readAllBytes(store(STATUS))),
                    () -> assertEquals(List.of(), audit.events()));
        }
    }

    @Test
    @DisplayName("a store path outside the scope is refused")
    void outsideScope() {
        assertThrows(IllegalArgumentException.class, () -> ScopeIntegrity.snapshot(scope, "../another-plan/x"));
    }
}
