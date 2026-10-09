/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.lock;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.planmarshall.core.log.PmMcpLogMessages.INFO;
import de.planmarshall.core.log.PmMcpLogMessages.WARN;
import de.planmarshall.core.store.AtomicStoreFile;
import de.planmarshall.core.store.HolderInstance;
import de.planmarshall.core.store.LeaseCodec;
import de.planmarshall.core.store.LeaseOwner;
import de.planmarshall.core.store.LeaseRecord;
import de.planmarshall.core.store.LeaseStore;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.ScopeType;
import de.planmarshall.runtime.lock.StaleLeaseSweep.Action;
import de.planmarshall.runtime.lock.StaleLeaseSweep.Decision;
import de.planmarshall.runtime.lock.StaleLeaseSweep.EmptyPopulation;
import de.planmarshall.runtime.lock.StaleLeaseSweep.Rule;

/**
 * The sweep of a lease store at a runtime start (PM-IMPL-7), over a real lease store and the lock manager of the
 * runtime. The holder that is alive is the process of this test; the holder that is not alive has the process id of
 * this test and another start instant, as the lease of an ended runtime whose process id was reused.
 */
@EnableTestLogger
@DisplayName("Stale lease sweep")
class StaleLeaseSweepTest {

    private static final Instant ACQUIRED_AT = Instant.parse("2026-10-09T08:16:00Z");
    private static final Instant NOW = Instant.parse("2026-10-09T10:00:00Z");
    private static final Predicate<LeaseRecord> NO_EVIDENCE = lease -> false;
    private static final Predicate<LeaseRecord> ABANDONED = lease -> true;

    @TempDir
    Path base;

    private Path store;
    private LockKey storeKey;
    private LeaseStore leaseStore;
    private HolderInstance alive;
    private HolderInstance dead;

    @BeforeEach
    void createStore() throws IOException {
        store = Files.createDirectories(base.resolve("state")).resolve("build-slots.json");
        storeKey = LockKey.buildSlots(base);
        leaseStore = new LeaseStore(new AtomicStoreFile(store, storeKey), new FileLockManager(new RecordingAuditSink()));
        var self = ProcessHandle.current();
        var startedAt = self.info().startInstant().orElseThrow();
        alive = new HolderInstance(self.pid(), startedAt);
        dead = new HolderInstance(self.pid(), startedAt.minusSeconds(86_400));
    }

    private LeaseRecord claim(String key, HolderInstance holder) {
        var owner = new LeaseOwner("pm-mcp-core", ScopeType.PLAN, "a-plan", holder, null);
        var lease = new LeaseRecord(key, owner, ACQUIRED_AT, null);
        assertTrue(leaseStore.claim(lease).granted());
        return lease;
    }

    /** A write replaces the store by a rename, so the file of an unwritten store is the same file as before. */
    private Object fileIdentity() throws IOException {
        return Files.readAttributes(store, BasicFileAttributes.class).fileKey();
    }

    /** @return whether this process holds a lock of the operating system on the file */
    private static boolean isLocked(Path file) {
        try (var channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            var lock = channel.tryLock();
            if (lock != null) {
                lock.release();
            }
            return false;
        } catch (OverlappingFileLockException _) {
            return true;
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static String sweepFinished(Path store, int kept, int orphaned, int removed) {
        return "Stale lease sweep of '%s' finished: %s kept, %s orphaned, %s removed"
                .formatted(store, kept, orphaned, removed);
    }

    @Nested
    @DisplayName("a holder that is alive")
    class HolderAlive {

        @Test
        @DisplayName("its lease is kept, and the store is left byte-identical and unwritten")
        void kept() throws IOException {
            var lease = claim("slot-1", alive);
            var bytesBefore = Files.readAllBytes(store);
            var fileBefore = fileIdentity();

            var result = StaleLeaseSweep.sweep(leaseStore, NO_EVIDENCE, NOW);

            assertAll(
                    () -> assertEquals(List.of(new Decision(lease, Action.KEPT, Rule.HOLDER_ALIVE)),
                            result.decisions()),
                    () -> assertEquals(Optional.of(EmptyPopulation.ALL_KEPT), result.emptyPopulation()),
                    () -> assertArrayEquals(bytesBefore, Files.readAllBytes(store)),
                    () -> assertEquals(fileBefore, fileIdentity(), "the store file was not replaced"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                            INFO.STALE_LEASE_SWEEP_FINISHED.resolveIdentifierString()),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                            sweepFinished(store, 1, 0, 0)),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, StaleLeaseSweep.class));
        }

        /**
         * A lease is owned by its scope, not by the runtime. While its runtime lives the sweep has nothing to
         * decide, whatever the caller knows about the scope: the evidence is not even asked for.
         */
        @Test
        @DisplayName("its lease is kept even when the caller's evidence would call it abandoned")
        void keptAgainstEvidence() {
            var lease = claim("slot-1", alive);
            var asked = new ArrayList<LeaseRecord>();

            var result = StaleLeaseSweep.sweep(leaseStore, candidate -> {
                asked.add(candidate);
                return true;
            }, NOW);

            assertAll(
                    () -> assertEquals(Rule.HOLDER_ALIVE, result.decisions().getFirst().rule()),
                    () -> assertEquals(List.of(), asked),
                    () -> assertEquals(List.of(lease), leaseStore.snapshot()));
        }
    }

    @Nested
    @DisplayName("a holder that is not alive")
    class HolderDead {

        /**
         * The missing evidence is the case that must keep: a sweep that removed a lease because nothing spoke for
         * it would release the claim of a scope that merely waits for its next call.
         */
        @Test
        @DisplayName("without evidence its lease is kept and marked as orphaned at the instant of the sweep")
        void orphaned() {
            var lease = claim("slot-1", dead);

            var result = StaleLeaseSweep.sweep(leaseStore, NO_EVIDENCE, NOW);

            var orphaned = lease.withOwner(lease.owner().orphaned(NOW));
            assertAll(
                    () -> assertEquals(List.of(new Decision(orphaned, Action.ORPHANED, Rule.SKIPPED_NO_EVIDENCE)),
                            result.decisions()),
                    () -> assertEquals("skipped_no_evidence", result.decisions().getFirst().rule().code()),
                    () -> assertEquals(Optional.of(EmptyPopulation.ALL_KEPT), result.emptyPopulation()),
                    () -> assertEquals(List.of(orphaned), leaseStore.snapshot()),
                    () -> assertEquals(dead, leaseStore.snapshot().getFirst().owner().holderInstance()),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.LEASE_ORPHANED.resolveIdentifierString()),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            "Lease 'slot-1' in '%s' orphaned: its holder (pid %s)".formatted(store, dead.runtimePid())),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                            sweepFinished(store, 0, 1, 0)));
        }

        /** The matched case to the one above: the same lease, and the evidence present. */
        @Test
        @DisplayName("with evidence of abandonment its lease is removed")
        void removed() {
            var lease = claim("slot-1", dead);

            var result = StaleLeaseSweep.sweep(leaseStore, ABANDONED, NOW);

            assertAll(
                    () -> assertEquals(List.of(new Decision(lease, Action.REMOVED, Rule.ABANDONED)),
                            result.decisions()),
                    () -> assertEquals("abandoned", result.decisions().getFirst().rule().code()),
                    () -> assertEquals(Optional.empty(), result.emptyPopulation()),
                    () -> assertEquals(List.of(), leaseStore.snapshot()),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, StaleLeaseSweep.class),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                            sweepFinished(store, 0, 0, 1)));
        }

        @Test
        @DisplayName("a lease that is orphaned already keeps its first orphaned_at, and the store is not written")
        void orphanedOnce() throws IOException {
            claim("slot-1", dead);
            StaleLeaseSweep.sweep(leaseStore, NO_EVIDENCE, NOW);
            var fileBefore = fileIdentity();

            var result = StaleLeaseSweep.sweep(leaseStore, NO_EVIDENCE, NOW.plusSeconds(3600));

            var decision = result.decisions().getFirst();
            assertAll(
                    () -> assertEquals(Action.KEPT, decision.action()),
                    () -> assertEquals(Rule.SKIPPED_NO_EVIDENCE, decision.rule()),
                    () -> assertEquals(NOW, decision.lease().owner().orphanedAt()),
                    () -> assertEquals(NOW, leaseStore.snapshot().getFirst().owner().orphanedAt()),
                    () -> assertEquals(fileBefore, fileIdentity(), "the store file was not replaced"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.LEASE_ORPHANED.resolveIdentifierString()));
        }

        @Test
        @DisplayName("an orphaned lease is removed by a later sweep that has the evidence")
        void orphanedThenRemoved() {
            claim("slot-1", dead);
            StaleLeaseSweep.sweep(leaseStore, NO_EVIDENCE, NOW);

            var result = StaleLeaseSweep.sweep(leaseStore, ABANDONED, NOW.plusSeconds(3600));

            assertAll(
                    () -> assertEquals(Action.REMOVED, result.decisions().getFirst().action()),
                    () -> assertEquals(List.of(), leaseStore.snapshot()));
        }

        @Test
        @DisplayName("a reclaimed key is free: after the removal another scope claims it")
        void reclaimable() {
            claim("slot-1", dead);
            StaleLeaseSweep.sweep(leaseStore, ABANDONED, NOW);

            var next = claim("slot-1", alive);

            assertEquals(List.of(next), leaseStore.snapshot());
        }
    }

    @Nested
    @DisplayName("a store")
    class Store {

        @Test
        @DisplayName("each lease gets its own decision, in the order of the store, and the counts are logged")
        void mixed() {
            var live = claim("slot-live", alive);
            var withEvidence = claim("slot-abandoned", dead);
            var withoutEvidence = claim("slot-waiting", dead);

            var result = StaleLeaseSweep.sweep(leaseStore, lease -> lease.key().equals("slot-abandoned"), NOW);

            var orphaned = withoutEvidence.withOwner(withoutEvidence.owner().orphaned(NOW));
            assertAll(
                    () -> assertEquals(List.of(
                            new Decision(live, Action.KEPT, Rule.HOLDER_ALIVE),
                            new Decision(withEvidence, Action.REMOVED, Rule.ABANDONED),
                            new Decision(orphaned, Action.ORPHANED, Rule.SKIPPED_NO_EVIDENCE)), result.decisions()),
                    () -> assertEquals(1, result.count(Action.KEPT)),
                    () -> assertEquals(1, result.count(Action.ORPHANED)),
                    () -> assertEquals(1, result.count(Action.REMOVED)),
                    () -> assertEquals(Optional.empty(), result.emptyPopulation()),
                    () -> assertEquals(List.of(live, orphaned), leaseStore.snapshot()),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                            sweepFinished(store, 1, 1, 1)));
        }

        /**
         * The lease is read, judged and written under the one lock of the store. Would the sweep judge on a
         * lock-free read and write later, an adoption between the two would be overwritten.
         */
        @Test
        @DisplayName("the evidence is asked for while the lock of the store is held, and the lock is released after")
        void underOneLock() {
            claim("slot-1", dead);
            var lockedWhileJudging = new ArrayList<Boolean>();

            StaleLeaseSweep.sweep(leaseStore, lease -> {
                lockedWhileJudging.add(isLocked(storeKey.lockFile()));
                return false;
            }, NOW);

            assertAll(
                    () -> assertEquals(List.of(true), lockedWhileJudging),
                    () -> assertFalse(isLocked(storeKey.lockFile())));
        }

        @Test
        @DisplayName("a store without leases is reported as nothing_listed and is not written")
        void nothingListed() throws IOException {
            claim("slot-1", alive);
            leaseStore.release("slot-1");
            var fileBefore = fileIdentity();

            var result = StaleLeaseSweep.sweep(leaseStore, ABANDONED, NOW);

            assertAll(
                    () -> assertEquals(List.of(), result.decisions()),
                    () -> assertEquals(Optional.of(EmptyPopulation.NOTHING_LISTED), result.emptyPopulation()),
                    () -> assertEquals("nothing_listed", result.emptyPopulation().orElseThrow().code()),
                    () -> assertEquals(Optional.empty(), result.refusal()),
                    () -> assertEquals(fileBefore, fileIdentity(), "the store file was not replaced"));
        }

        @Test
        @DisplayName("a store that does not exist is reported as root_absent, is not created and is not logged as swept")
        void rootAbsent() {
            var result = StaleLeaseSweep.sweep(leaseStore, ABANDONED, NOW);

            assertAll(
                    () -> assertEquals(List.of(), result.decisions()),
                    () -> assertEquals(Optional.of(EmptyPopulation.ROOT_ABSENT), result.emptyPopulation()),
                    () -> assertEquals("root_absent", result.emptyPopulation().orElseThrow().code()),
                    () -> assertFalse(Files.exists(store)),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO, StaleLeaseSweep.class));
        }

        @Test
        @DisplayName("a store that is refused is reported as root_unreadable with the refusal and left byte-identical")
        void rootUnreadable() throws IOException {
            var newer = "{\"format_version\":2,\"slots\":[]}".getBytes(StandardCharsets.UTF_8);
            Files.write(store, newer);

            var result = StaleLeaseSweep.sweep(leaseStore, ABANDONED, NOW);

            assertAll(
                    () -> assertEquals(List.of(), result.decisions()),
                    () -> assertEquals(Optional.of(EmptyPopulation.ROOT_UNREADABLE), result.emptyPopulation()),
                    () -> assertEquals("root_unreadable", result.emptyPopulation().orElseThrow().code()),
                    () -> assertEquals(Optional.of(LeaseCodec.Refusal.FORMAT_NEWER), result.refusal()),
                    () -> assertArrayEquals(newer, Files.readAllBytes(store)),
                    () -> assertFalse(isLocked(storeKey.lockFile())),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO, StaleLeaseSweep.class));
        }

        @Test
        @DisplayName("a missing store, evidence or instant is refused before anything is read")
        void missingArguments() {
            assertAll(
                    () -> assertThrows(NullPointerException.class,
                            () -> StaleLeaseSweep.sweep(null, NO_EVIDENCE, NOW)),
                    () -> assertThrows(NullPointerException.class, () -> StaleLeaseSweep.sweep(leaseStore, null, NOW)),
                    () -> assertThrows(NullPointerException.class,
                            () -> StaleLeaseSweep.sweep(leaseStore, NO_EVIDENCE, null)),
                    () -> assertFalse(Files.exists(storeKey.lockFile())));
        }
    }
}
