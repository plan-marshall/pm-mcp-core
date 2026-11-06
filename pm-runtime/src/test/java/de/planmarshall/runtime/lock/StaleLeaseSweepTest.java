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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
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
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.store.AtomicStoreFile;
import de.planmarshall.core.store.HolderInstance;
import de.planmarshall.core.store.LeaseOwner;
import de.planmarshall.core.store.LeaseRecord;
import de.planmarshall.core.store.LeaseStore;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.ScopeType;
import de.planmarshall.core.store.StoreRefusal.Outcome;
import de.planmarshall.runtime.lock.StaleLeaseSweep.Action;
import de.planmarshall.runtime.lock.StaleLeaseSweep.Decision;
import de.planmarshall.runtime.lock.StaleLeaseSweep.EmptyPopulation;
import de.planmarshall.runtime.lock.StaleLeaseSweep.Rule;
import de.planmarshall.runtime.log.RuntimeLogMessages.INFO;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;

/**
 * The sweep of a lease store at a runtime start (PM-IMPL-7), over a real lease store and the lock manager of the
 * runtime. The holder that is alive is the process of this test; the holder that is not alive has the process id of
 * this test and another start instant, as the lease of an ended runtime whose process id was reused.
 *
 * <p>
 * The instants, the project and plan ids and the lease keys are generated. The literals that stay are the contract:
 * the holder liveness at its boundary (this process, and a start one day before its own), the store's file name
 * {@code build-slots.json} and the refused store documents in its wire format, the rule and population codes, the
 * logged message texts and the decision counts.
 */
@EnableGeneratorController
@EnableTestLogger
@DisplayName("Stale lease sweep")
class StaleLeaseSweepTest {

    private static final Predicate<LeaseRecord> NO_EVIDENCE = lease -> false;
    private static final Predicate<LeaseRecord> ABANDONED = lease -> true;

    @TempDir
    Path base;

    private Path store;
    private LockKey storeKey;
    private LeaseStore leaseStore;
    private HolderInstance alive;
    private HolderInstance dead;
    private Instant acquiredAt;
    private Instant now;
    private String project;
    private String otherProject;
    private String planId;
    private String slot;

    /** The channels the probes opened; closed after the test, when the lock of the store is released. */
    private final List<FileChannel> probes = new ArrayList<>();

    @BeforeEach
    void createStore() throws IOException {
        acquiredAt = Instant.ofEpochSecond(Generators.integers(0, Integer.MAX_VALUE / 2).next());
        now = acquiredAt.plusSeconds(Generators.integers(1, 1_000_000).next());
        project = "project-" + lowerLetters();
        otherProject = project + "-other";
        planId = "plan-" + lowerLetters();
        slot = slotKey();
        store = Files.createDirectories(base.resolve("state")).resolve("build-slots.json");
        storeKey = LockKey.buildSlots(base);
        leaseStore = new LeaseStore(new AtomicStoreFile(store, storeKey), new FileLockManager(new RecordingAuditSink()));
        var self = ProcessHandle.current();
        var startedAt = self.info().startInstant().orElseThrow();
        alive = new HolderInstance(self.pid(), startedAt);
        dead = new HolderInstance(self.pid(), startedAt.minusSeconds(86_400));
    }

    private static String lowerLetters() {
        return Generators.letterStrings(2, 10).next().toLowerCase(Locale.ROOT);
    }

    private static String slotKey() {
        return "slot-" + lowerLetters();
    }

    private LeaseRecord claim(String key, HolderInstance holder) {
        return claim(key, project, holder);
    }

    private LeaseRecord claim(String key, String projectId, HolderInstance holder) {
        var owner = new LeaseOwner(projectId, ScopeType.PLAN, planId, holder, null);
        var lease = new LeaseRecord(key, owner, acquiredAt, null);
        assertTrue(leaseStore.claim(lease).granted(), "the fixture lease is granted");
        return lease;
    }

    /** A write replaces the store by a rename, so the file of an unwritten store is the same file as before. */
    private Object fileIdentity() throws IOException {
        return Files.readAttributes(store, BasicFileAttributes.class).fileKey();
    }

    @AfterEach
    void closeProbes() throws IOException {
        for (var probe : probes) {
            probe.close();
        }
    }

    /**
     * The channel of a probe stays open until the test has ended: closing any channel on a file releases every lock
     * the process holds on it, so a probe that closed its channel would drop the lock of the store for the rest of
     * the sweep. No test fails without this, because the loss is visible from another process only.
     *
     * @return whether this process holds a lock of the operating system on the file
     */
    private boolean isLocked(Path file) {
        try {
            var channel = FileChannel.open(file, StandardOpenOption.WRITE);
            probes.add(channel);
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

    static Stream<Arguments> refusedStores() {
        return Stream.of(
                Arguments.of("{\"format_version\":2,\"slots\":[]}", Outcome.FORMAT_NEWER),
                Arguments.of("{\"format_version\":0,\"leases\":[]}", Outcome.FORMAT_OUTDATED),
                Arguments.of("{\"format_version\":1,\"leases\":[", Outcome.UNREADABLE));
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
        void kept() throws Exception {
            var lease = claim(slot, alive);
            var bytesBefore = Files.readAllBytes(store);
            var fileBefore = fileIdentity();

            var result = StaleLeaseSweep.sweep(leaseStore, NO_EVIDENCE, now);

            assertAll(
                    () -> assertEquals(List.of(new Decision(lease, Action.KEPT, Rule.HOLDER_ALIVE)),
                            result.decisions(), "the live holder's lease is kept"),
                    () -> assertEquals(Optional.of(EmptyPopulation.ALL_KEPT), result.emptyPopulation(),
                            "the population is reported all kept"),
                    () -> assertArrayEquals(bytesBefore, Files.readAllBytes(store),
                            "the store is left byte-identical"),
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
            var lease = claim(slot, alive);
            var asked = new ArrayList<LeaseRecord>();

            var result = StaleLeaseSweep.sweep(leaseStore, candidate -> {
                asked.add(candidate);
                return true;
            }, now);

            assertAll(
                    () -> assertEquals(Rule.HOLDER_ALIVE, result.decisions().getFirst().rule(),
                            "the live holder decides the lease"),
                    () -> assertEquals(List.of(), asked, "the evidence is not asked for a live holder"),
                    () -> assertEquals(List.of(lease), leaseStore.snapshot(), "the live holder's lease stays"));
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
            var lease = claim(slot, dead);

            var result = StaleLeaseSweep.sweep(leaseStore, NO_EVIDENCE, now);

            var orphaned = lease.withOwner(lease.owner().orphaned(now));
            assertAll(
                    () -> assertEquals(List.of(new Decision(orphaned, Action.ORPHANED, Rule.SKIPPED_NO_EVIDENCE)),
                            result.decisions(), "the dead holder's lease is orphaned"),
                    () -> assertEquals("skipped_no_evidence", result.decisions().getFirst().rule().code(),
                            "the rule code names the missing evidence"),
                    () -> assertEquals(Optional.of(EmptyPopulation.ALL_KEPT), result.emptyPopulation(),
                            "the population is reported all kept"),
                    () -> assertEquals(List.of(orphaned), leaseStore.snapshot(),
                            "the store holds the orphaned lease"),
                    () -> assertEquals(dead, leaseStore.snapshot().getFirst().owner().holderInstance(),
                            "the orphaned lease keeps its holder"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.LEASE_ORPHANED.resolveIdentifierString()),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            "Lease '%s' in '%s' orphaned: its holder (pid %s)".formatted(slot, store,
                                    dead.runtimePid())),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                            sweepFinished(store, 0, 1, 0)));
        }

        /** The matched case to the one above: the same lease, and the evidence present. */
        @Test
        @DisplayName("with evidence of abandonment its lease is removed")
        void removed() {
            var lease = claim(slot, dead);

            var result = StaleLeaseSweep.sweep(leaseStore, ABANDONED, now);

            assertAll(
                    () -> assertEquals(List.of(new Decision(lease, Action.REMOVED, Rule.ABANDONED)),
                            result.decisions(), "the abandoned lease is removed"),
                    () -> assertEquals("abandoned", result.decisions().getFirst().rule().code(),
                            "the rule code names the abandonment"),
                    () -> assertEquals(Optional.empty(), result.emptyPopulation(),
                            "a removal is no empty population"),
                    () -> assertEquals(List.of(), leaseStore.snapshot(), "the store holds no lease any more"),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, StaleLeaseSweep.class),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                            sweepFinished(store, 0, 0, 1)));
        }

        @Test
        @DisplayName("a lease that is orphaned already keeps its first orphaned_at, and the store is not written")
        void orphanedOnce() throws Exception {
            claim(slot, dead);
            StaleLeaseSweep.sweep(leaseStore, NO_EVIDENCE, now);
            var fileBefore = fileIdentity();

            var result = StaleLeaseSweep.sweep(leaseStore, NO_EVIDENCE, now.plusSeconds(3600));

            var decision = result.decisions().getFirst();
            assertAll(
                    () -> assertEquals(Action.KEPT, decision.action(), "an orphaned lease is kept again"),
                    () -> assertEquals(Rule.SKIPPED_NO_EVIDENCE, decision.rule(),
                            "the missing evidence decides again"),
                    () -> assertEquals(now, decision.lease().owner().orphanedAt(),
                            "the decision keeps the first orphaned_at"),
                    () -> assertEquals(now, leaseStore.snapshot().getFirst().owner().orphanedAt(),
                            "the store keeps the first orphaned_at"),
                    () -> assertEquals(fileBefore, fileIdentity(), "the store file was not replaced"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.LEASE_ORPHANED.resolveIdentifierString()));
        }

        @Test
        @DisplayName("an orphaned lease is removed by a later sweep that has the evidence")
        void orphanedThenRemoved() {
            claim(slot, dead);
            StaleLeaseSweep.sweep(leaseStore, NO_EVIDENCE, now);

            var result = StaleLeaseSweep.sweep(leaseStore, ABANDONED, now.plusSeconds(3600));

            assertAll(
                    () -> assertEquals(Action.REMOVED, result.decisions().getFirst().action(),
                            "the later sweep removes the orphaned lease"),
                    () -> assertEquals(List.of(), leaseStore.snapshot(), "the store holds no lease any more"));
        }

        @Test
        @DisplayName("a reclaimed key is free: after the removal another scope claims it")
        void reclaimable() {
            claim(slot, dead);
            StaleLeaseSweep.sweep(leaseStore, ABANDONED, now);

            var next = claim(slot, alive);

            assertEquals(List.of(next), leaseStore.snapshot(), "another scope holds the reclaimed key");
        }
    }

    @Nested
    @DisplayName("a store")
    class Store {

        @Test
        @DisplayName("each lease gets its own decision, in the order of the store, and the counts are logged")
        void mixed() {
            var liveKey = slot + "-live";
            var abandonedKey = slot + "-abandoned";
            var waitingKey = slot + "-waiting";
            var live = claim(liveKey, alive);
            var withEvidence = claim(abandonedKey, dead);
            var withoutEvidence = claim(waitingKey, dead);

            var result = StaleLeaseSweep.sweep(leaseStore, lease -> abandonedKey.equals(lease.key()), now);

            var orphaned = withoutEvidence.withOwner(withoutEvidence.owner().orphaned(now));
            assertAll(
                    () -> assertEquals(List.of(
                            new Decision(live, Action.KEPT, Rule.HOLDER_ALIVE),
                            new Decision(withEvidence, Action.REMOVED, Rule.ABANDONED),
                            new Decision(orphaned, Action.ORPHANED, Rule.SKIPPED_NO_EVIDENCE)), result.decisions(),
                            "each lease is decided in the store's order"),
                    () -> assertEquals(1, result.count(Action.KEPT), "one lease is kept"),
                    () -> assertEquals(1, result.count(Action.ORPHANED), "one lease is orphaned"),
                    () -> assertEquals(1, result.count(Action.REMOVED), "one lease is removed"),
                    () -> assertEquals(Optional.empty(), result.emptyPopulation(),
                            "a mixed store is no empty population"),
                    () -> assertEquals(List.of(live, orphaned), leaseStore.snapshot(),
                            "the kept and the orphaned lease remain"),
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
            claim(slot, dead);
            var lockedWhileJudging = new ArrayList<Boolean>();

            StaleLeaseSweep.sweep(leaseStore, lease -> {
                lockedWhileJudging.add(isLocked(storeKey.lockFile()));
                return false;
            }, now);

            assertAll(
                    () -> assertEquals(List.of(true), lockedWhileJudging,
                            "the store is locked while the evidence is asked"),
                    () -> assertFalse(isLocked(storeKey.lockFile()), "the store lock is released after"));
        }

        @Test
        @DisplayName("a store without leases is reported as nothing_listed and is not written")
        void nothingListed() throws Exception {
            claim(slot, alive);
            leaseStore.release(slot);
            var fileBefore = fileIdentity();

            var result = StaleLeaseSweep.sweep(leaseStore, ABANDONED, now);

            assertAll(
                    () -> assertEquals(List.of(), result.decisions(), "an empty store yields no decision"),
                    () -> assertEquals(Optional.of(EmptyPopulation.NOTHING_LISTED), result.emptyPopulation(),
                            "an empty store is reported nothing listed"),
                    () -> assertEquals("nothing_listed", result.emptyPopulation().orElseThrow().code(),
                            "the population code is nothing_listed"),
                    () -> assertEquals(Optional.empty(), result.refusal(), "an empty store is not refused"),
                    () -> assertEquals(fileBefore, fileIdentity(), "the store file was not replaced"));
        }

        @Test
        @DisplayName("a store that does not exist is reported as root_absent, is not created and is not logged as swept")
        void rootAbsent() {
            var result = StaleLeaseSweep.sweep(leaseStore, ABANDONED, now);

            assertAll(
                    () -> assertEquals(List.of(), result.decisions(), "an absent store yields no decision"),
                    () -> assertEquals(Optional.of(EmptyPopulation.ROOT_ABSENT), result.emptyPopulation(),
                            "an absent store is reported root absent"),
                    () -> assertEquals("root_absent", result.emptyPopulation().orElseThrow().code(),
                            "the population code is root_absent"),
                    () -> assertFalse(Files.exists(store), "the absent store is not created"),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO, StaleLeaseSweep.class));
        }

        @ParameterizedTest(name = "{1}")
        @MethodSource("de.planmarshall.runtime.lock.StaleLeaseSweepTest#refusedStores")
        @DisplayName("a store that is refused is reported as root_unreadable with the refusal and left byte-identical")
        void rootUnreadable(String document, Outcome outcome) throws Exception {
            var refused = document.getBytes(StandardCharsets.UTF_8);
            Files.write(store, refused);

            var result = StaleLeaseSweep.sweep(leaseStore, ABANDONED, now);

            assertAll(
                    () -> assertEquals(List.of(), result.decisions(), "a refused store yields no decision"),
                    () -> assertEquals(Optional.of(EmptyPopulation.ROOT_UNREADABLE), result.emptyPopulation(),
                            "a refused store is reported root unreadable"),
                    () -> assertEquals("root_unreadable", result.emptyPopulation().orElseThrow().code(),
                            "the population code is root_unreadable"),
                    () -> assertEquals(Optional.of(outcome), result.refusal(), "the result names the refusal"),
                    () -> assertArrayEquals(refused, Files.readAllBytes(store),
                            "the refused store is left byte-identical"),
                    () -> assertFalse(isLocked(storeKey.lockFile()), "the store lock is released after"),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO, StaleLeaseSweep.class));
        }

        @Test
        @DisplayName("a missing store, evidence or instant is refused before anything is read")
        void missingArguments() {
            assertAll(
                    () -> assertThrows(NullPointerException.class,
                            () -> StaleLeaseSweep.sweep(null, NO_EVIDENCE, now), "a missing store is refused"),
                    () -> assertThrows(NullPointerException.class, () -> StaleLeaseSweep.sweep(leaseStore, null, now),
                            "a missing evidence is refused"),
                    () -> assertThrows(NullPointerException.class,
                            () -> StaleLeaseSweep.sweep(leaseStore, NO_EVIDENCE, null), "a missing instant is refused"),
                    () -> assertFalse(Files.exists(storeKey.lockFile()), "nothing is read before the refusal"));
        }
    }

    @Nested
    @DisplayName("the leases of one project")
    class OneProject {

        @Test
        @DisplayName("only the leases of the selected project are orphaned; those of another project stay as they were")
        void orphanedOnlyForProject() {
            var own = claim(slot + "-own", project, dead);
            var foreign = claim(slot + "-foreign", otherProject, dead);

            var result = StaleLeaseSweep.sweep(leaseStore, project, NO_EVIDENCE, now);

            var orphaned = own.withOwner(own.owner().orphaned(now));
            assertAll(
                    () -> assertEquals(List.of(new Decision(orphaned, Action.ORPHANED, Rule.SKIPPED_NO_EVIDENCE)),
                            result.decisions(), "only the project's lease is decided"),
                    () -> assertEquals(1, result.count(Action.ORPHANED), "the project's lease is orphaned"),
                    () -> assertEquals(0, result.count(Action.KEPT), "a lease of another project is not counted"),
                    () -> assertEquals(Optional.of(EmptyPopulation.ALL_KEPT), result.emptyPopulation(),
                            "the population is reported all kept"),
                    () -> assertEquals(List.of(orphaned, foreign), leaseStore.snapshot(),
                            "the other project's lease is unchanged"),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                            sweepFinished(store, 0, 1, 0)));
        }

        @Test
        @DisplayName("with evidence of abandonment only the lease of the selected project is removed")
        void removedOnlyForProject() {
            var ownKey = slot + "-own";
            claim(ownKey, project, dead);
            var foreign = claim(slot + "-foreign", otherProject, dead);
            var asked = new ArrayList<LeaseRecord>();

            var result = StaleLeaseSweep.sweep(leaseStore, project, lease -> {
                asked.add(lease);
                return true;
            }, now);

            assertAll(
                    () -> assertEquals(List.of(Action.REMOVED),
                            result.decisions().stream().map(Decision::action).toList(),
                            "only the project's lease is removed"),
                    () -> assertEquals(Optional.empty(), result.emptyPopulation(),
                            "a removal is no empty population"),
                    () -> assertEquals(List.of(ownKey), asked.stream().map(LeaseRecord::key).toList(),
                            "the evidence is asked for the selected project only"),
                    () -> assertEquals(List.of(foreign), leaseStore.snapshot(),
                            "the other project's lease remains"));
        }

        @Test
        @DisplayName("a store without a lease of the project is reported as nothing_listed and is not written")
        void nothingListedForProject() throws Exception {
            var foreign = claim(slot + "-foreign", otherProject, dead);
            var bytesBefore = Files.readAllBytes(store);
            var fileBefore = fileIdentity();

            var result = StaleLeaseSweep.sweep(leaseStore, project, ABANDONED, now);

            assertAll(
                    () -> assertEquals(List.of(), result.decisions(), "no lease of the project is decided"),
                    () -> assertEquals(Optional.of(EmptyPopulation.NOTHING_LISTED), result.emptyPopulation(),
                            "the project's population is nothing listed"),
                    () -> assertEquals(List.of(foreign), leaseStore.snapshot(),
                            "the other project's lease remains"),
                    () -> assertArrayEquals(bytesBefore, Files.readAllBytes(store),
                            "the store is left byte-identical"),
                    () -> assertEquals(fileBefore, fileIdentity(), "the store file was not replaced"));
        }

        @Test
        @DisplayName("a store that is refused is reported as root_unreadable and left byte-identical")
        void rootUnreadableForProject() throws Exception {
            var refused = "{\"format_version\":1,\"leases\":[".getBytes(StandardCharsets.UTF_8);
            Files.write(store, refused);

            var result = StaleLeaseSweep.sweep(leaseStore, project, ABANDONED, now);

            assertAll(
                    () -> assertEquals(Optional.of(EmptyPopulation.ROOT_UNREADABLE), result.emptyPopulation(),
                            "a refused store is reported root unreadable"),
                    () -> assertEquals(Optional.of(Outcome.UNREADABLE), result.refusal(),
                            "the refusal names the unreadable store"),
                    () -> assertArrayEquals(refused, Files.readAllBytes(store),
                            "the refused store is left byte-identical"));
        }

        @Test
        @DisplayName("a missing project is refused before anything is read")
        void missingProject() {
            assertAll(
                    () -> assertThrows(NullPointerException.class,
                            () -> StaleLeaseSweep.sweep(leaseStore, null, NO_EVIDENCE, now),
                            "a missing project is refused"),
                    () -> assertFalse(Files.exists(storeKey.lockFile()), "nothing is read before the refusal"));
        }
    }
}
