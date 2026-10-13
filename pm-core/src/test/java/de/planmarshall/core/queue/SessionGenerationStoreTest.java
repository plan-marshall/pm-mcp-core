/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.queue;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.queue.SessionGeneration.Scope;
import de.planmarshall.core.queue.SessionGeneration.Workdir;
import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.ScopeSessionRow.Client;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.StoreRefusal.Outcome;

/**
 * The session generation record of a project (PM-WORK-5): a new generation fences every earlier generation of its
 * session key in the same write under the queue lock, a fenced generation is refused, one fenced by an enter is told
 * apart as superseded, and the record is read without a lock.
 */
@DisplayName("Session generation store")
class SessionGenerationStoreTest {

    private static final String PROJECT_ID = "pm-mcp-core";
    private static final String KEY = "a".repeat(64);
    private static final String OTHER_KEY = "b".repeat(64);
    private static final Instant START = Instant.parse("2026-10-13T08:00:00Z");
    private static final SessionGenerationStore.Request SESSION = new SessionGenerationStore.Request(KEY,
            Client.CLAUDE, "session-1", true);
    private static final SessionGenerationStore.Request OTHER_SESSION = new SessionGenerationStore.Request(OTHER_KEY,
            Client.OPENCODE, null, false);
    private static final Scope PLAN = new Scope(Scope.Type.PLAN, "lock-manager");
    private static final Workdir PLAN_WORKTREE = new Workdir(Workdir.Kind.PLAN_WORKTREE, "lock-manager");

    @TempDir
    Path base;

    private RecordingLocks locks;
    private SteppingClock clock;
    private SessionGenerationStore store;
    private LockKey queueKey;

    @BeforeEach
    void createStore() {
        locks = new RecordingLocks();
        clock = new SteppingClock(START);
        store = new SessionGenerationStore(base, PROJECT_ID, locks, clock);
        queueKey = LockKey.queue(base, PROJECT_ID);
    }

    /** A write replaces the record by a rename, so the file of an unwritten record is the same file as before. */
    private Object fileIdentity() throws IOException {
        return Files.readAttributes(store.store(), BasicFileAttributes.class).fileKey();
    }

    private void assertTransactionsOnTheQueueKeyOnly() {
        assertAll(
                () -> assertTrue(locks.transactions > 0, "a transaction was opened"),
                () -> assertEquals(Set.of(queueKey), new HashSet<>(locks.acquisitions), "keys acquired"),
                () -> assertEquals(0, locks.open, "transactions open afterwards"));
    }

    @Nested
    @DisplayName("issue")
    class Issue {

        @Test
        @DisplayName("the first generation of a session is current, written to queue/ under the queue lock")
        void first() {
            var issued = store.relayStart(SESSION);

            assertAll(
                    () -> assertEquals(base.toAbsolutePath().normalize().resolve("projects").resolve(PROJECT_ID)
                            .resolve("queue").resolve("session-generations.json"), store.store()),
                    () -> assertTrue(Files.isRegularFile(store.store())),
                    () -> assertEquals(SessionGeneration.issued(issued.id(), KEY, Client.CLAUDE, "session-1",
                            GenerationCause.RELAY_START, START, true), issued),
                    () -> assertEquals(List.of(issued), store.snapshot()),
                    () -> assertInstanceOf(GenerationCheck.Current.class, store.check(issued.id())),
                    SessionGenerationStoreTest.this::assertTransactionsOnTheQueueKeyOnly);
        }

        @Test
        @DisplayName("a second entry fences the first generation in the same write, and the first is refused")
        void secondEntryFencesTheFirst() {
            var first = store.relayStart(SESSION);
            clock.advance(Duration.ofMinutes(5));

            var second = store.newSession(SESSION);

            var check = store.check(first.id());
            assertAll(
                    () -> assertEquals(2, store.snapshot().size()),
                    () -> assertInstanceOf(GenerationCheck.Current.class, store.check(second.id())),
                    () -> assertEquals("generation_fenced", GenerationCheck.Fenced.CODE),
                    () -> assertEquals(new SessionGeneration.Fence(second.issuedAt(), false),
                            assertInstanceOf(GenerationCheck.Fenced.class, check).generation().fence()));
        }

        @Test
        @DisplayName("a generation an enter of its session replaced checks as superseded, not as fenced")
        void enterSupersedes() {
            var first = store.relayStart(SESSION);
            clock.advance(Duration.ofMinutes(1));

            var entered = store.enter(SESSION);

            var check = store.check(first.id());
            assertAll(
                    () -> assertEquals(GenerationCause.ENTER, entered.cause()),
                    () -> assertEquals("generation_superseded", GenerationCheck.Superseded.CODE),
                    () -> assertTrue(assertInstanceOf(GenerationCheck.Superseded.class, check).generation().fence()
                            .byEnter()),
                    () -> assertInstanceOf(GenerationCheck.Current.class, store.check(entered.id())));
        }

        @Test
        @DisplayName("a generation fenced once keeps its first fence when its session issues again")
        void firstFenceKept() {
            var first = store.relayStart(SESSION);
            clock.advance(Duration.ofMinutes(1));
            var second = store.enter(SESSION);
            clock.advance(Duration.ofMinutes(1));

            var third = store.newSession(SESSION);

            assertAll(
                    () -> assertInstanceOf(GenerationCheck.Superseded.class, store.check(first.id())),
                    () -> assertEquals(second.issuedAt(), ((GenerationCheck.Superseded) store.check(first.id()))
                            .generation().fence().at()),
                    () -> assertInstanceOf(GenerationCheck.Fenced.class, store.check(second.id())),
                    () -> assertInstanceOf(GenerationCheck.Current.class, store.check(third.id())));
        }

        @Test
        @DisplayName("an issue fences only the generations of its own session key")
        void otherSessionUntouched() {
            var other = store.relayStart(OTHER_SESSION);

            store.enter(SESSION);

            assertEquals(new GenerationCheck.Current(other), store.check(other.id()));
        }

        @Test
        @DisplayName("issues of one session in the same clock instant get strictly increasing instants")
        void strictlyIncreasingInstants() {
            var first = store.relayStart(SESSION);

            var second = store.enter(SESSION);

            var fenced = (GenerationCheck.Superseded) store.check(first.id());
            assertAll(
                    () -> assertEquals(START, first.issuedAt()),
                    () -> assertEquals(START.plusNanos(1), second.issuedAt()),
                    () -> assertEquals(second.issuedAt(), fenced.generation().fence().at()));
        }

        @Test
        @DisplayName("a drawn id that names a generation of the record is drawn again")
        void collisionRedrawn() {
            var drawn = new SequenceRandom(7L, 7L, 9L);
            var redrawing = new SessionGenerationStore(base, PROJECT_ID, locks, clock, drawn);

            var first = redrawing.relayStart(SESSION);
            var second = redrawing.relayStart(OTHER_SESSION);

            assertAll(
                    () -> assertEquals(GenerationId.random(new SequenceRandom(7L)), first.id()),
                    () -> assertEquals(GenerationId.random(new SequenceRandom(9L)), second.id()),
                    () -> assertEquals(2, redrawing.snapshot().size()),
                    () -> assertTrue(drawn.values.isEmpty(), "every drawn value was used"));
        }

        @Test
        @DisplayName("a session key out of its form is refused before anything is written")
        void invalidKeyRefused() {
            var invalid = new SessionGenerationStore.Request("not-a-key", Client.CLAUDE, null, true);

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> store.enter(invalid)),
                    () -> assertFalse(Files.exists(store.store())));
        }
    }

    @Nested
    @DisplayName("relay start")
    class RelayStart {

        @Test
        @DisplayName("a relay start returns the current generation of the session without writing")
        void returnsCurrent() throws Exception {
            var entered = store.enter(SESSION);
            var before = fileIdentity();
            clock.advance(Duration.ofMinutes(3));

            var bound = store.relayStart(SESSION);

            assertAll(
                    () -> assertEquals(entered, bound),
                    () -> assertEquals(before, fileIdentity()),
                    () -> assertEquals(1, store.snapshot().size()));
        }

        @Test
        @DisplayName("a relay start of another session issues a new generation by relay_start")
        void issuesForAnotherSession() {
            store.enter(SESSION);

            var bound = store.relayStart(OTHER_SESSION);

            assertAll(
                    () -> assertEquals(GenerationCause.RELAY_START, bound.cause()),
                    () -> assertEquals(2, store.snapshot().size()));
        }

        @Test
        @DisplayName("new session and enter always issue, even while a current generation exists")
        void newSessionAndEnterAlwaysIssue() {
            var first = store.relayStart(SESSION);

            var second = store.newSession(SESSION);
            var third = store.enter(SESSION);

            assertAll(
                    () -> assertNotEquals(first.id(), second.id()),
                    () -> assertNotEquals(second.id(), third.id()),
                    () -> assertEquals(3, store.snapshot().size()));
        }
    }

    @Nested
    @DisplayName("assign scope")
    class AssignScope {

        @Test
        @DisplayName("a current generation gets its scope and working directory, written to the record")
        void current() {
            var issued = store.relayStart(SESSION);

            var check = store.assignScope(issued.id(), PLAN, PLAN_WORKTREE);

            var assigned = issued.withScope(PLAN, PLAN_WORKTREE);
            assertAll(
                    () -> assertEquals(new GenerationCheck.Current(assigned), check),
                    () -> assertEquals(List.of(assigned), store.snapshot()),
                    SessionGenerationStoreTest.this::assertTransactionsOnTheQueueKeyOnly);
        }

        @Test
        @DisplayName("a fenced generation gets no scope, and nothing is written")
        void fenced() throws Exception {
            var first = store.relayStart(SESSION);
            store.newSession(SESSION);
            var before = fileIdentity();

            var check = store.assignScope(first.id(), PLAN, PLAN_WORKTREE);

            assertAll(
                    () -> assertInstanceOf(GenerationCheck.Fenced.class, check),
                    () -> assertEquals(before, fileIdentity()));
        }

        @Test
        @DisplayName("an unknown generation is reported as unknown")
        void unknown() {
            store.relayStart(SESSION);
            var unknown = new GenerationId("sg-00000000000000ff");

            assertEquals(new GenerationCheck.Unknown(unknown), store.assignScope(unknown, PLAN, PLAN_WORKTREE));
        }

        @Test
        @DisplayName("assigned in the caller's transaction, the queue lock is taken in it and held until it closes")
        void callersTransaction() {
            var issued = store.relayStart(SESSION);
            var scopeKey = LockKey.plan(base, PROJECT_ID, "lock-manager");

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scopeKey);

                store.assignScope(transaction, issued.id(), PLAN, PLAN_WORKTREE);

                assertTrue(transaction.holds(queueKey), "the queue lock is held by the caller's transaction");
            }
            assertEquals(PLAN, store.snapshot().getFirst().scope());
        }
    }

    @Nested
    @DisplayName("removal of fenced generations")
    class Removal {

        @Test
        @DisplayName("fenced generations fenced before the instant are removed; younger fenced and current ones kept")
        void removesOldFenced() {
            var oldest = store.relayStart(SESSION);
            clock.advance(Duration.ofHours(1));
            var younger = store.newSession(SESSION);
            clock.advance(Duration.ofHours(1));
            var current = store.enter(SESSION);
            var other = store.relayStart(OTHER_SESSION);

            var removed = store.removeFencedOlderThan(younger.issuedAt().plusSeconds(1));

            assertAll(
                    () -> assertEquals(List.of(oldest.id()), removed.stream().map(SessionGeneration::id).toList()),
                    () -> assertEquals(List.of(younger.id(), current.id(), other.id()),
                            store.snapshot().stream().map(SessionGeneration::id).toList()),
                    () -> assertInstanceOf(GenerationCheck.Superseded.class, store.check(younger.id())),
                    () -> assertEquals(new GenerationCheck.Unknown(oldest.id()), store.check(oldest.id())));
        }

        @Test
        @DisplayName("a generation fenced exactly at the instant is kept")
        void fencedAtTheInstantKept() {
            store.relayStart(SESSION);
            clock.advance(Duration.ofHours(1));
            var second = store.newSession(SESSION);

            assertEquals(List.of(), store.removeFencedOlderThan(second.issuedAt()));
        }

        @Test
        @DisplayName("nothing to remove writes nothing")
        void nothingToRemove() throws Exception {
            store.relayStart(SESSION);
            var before = fileIdentity();

            var removed = store.removeFencedOlderThan(START.plus(Duration.ofDays(30)));

            assertAll(
                    () -> assertEquals(List.of(), removed),
                    () -> assertEquals(before, fileIdentity()));
        }

        @Test
        @DisplayName("a removal on a project without a record writes no record")
        void noRecord() {
            assertAll(
                    () -> assertEquals(List.of(), store.removeFencedOlderThan(START)),
                    () -> assertFalse(Files.exists(store.store())));
        }
    }

    @Nested
    @DisplayName("lock-free read")
    class LockFreeRead {

        @Test
        @DisplayName("a project without a record reads no generation, and every check is unknown")
        void absentRecord() {
            var id = new GenerationId("sg-0000000000000001");

            assertAll(
                    () -> assertEquals(List.of(), store.snapshot()),
                    () -> assertEquals(new GenerationCheck.Unknown(id), store.check(id)),
                    () -> assertEquals(0, locks.transactions, "no transaction was opened"));
        }

        @Test
        @DisplayName("a read and a check take no lock")
        void noLock() {
            var issued = store.relayStart(SESSION);
            locks.reset();

            store.snapshot();
            store.check(issued.id());

            assertAll(
                    () -> assertEquals(0, locks.transactions),
                    () -> assertEquals(List.of(), locks.acquisitions));
        }

        @Test
        @DisplayName("a second store over the same project reads the generations the first wrote")
        void secondInstance() {
            var issued = store.enter(SESSION);

            var restarted = new SessionGenerationStore(base, PROJECT_ID, new RecordingLocks(), clock);

            assertEquals(List.of(issued), restarted.snapshot());
        }
    }

    @Nested
    @DisplayName("refused record")
    class RefusedRecord {

        @Test
        @DisplayName("a record in another format version is refused by a write and by a read, and left as it is")
        void otherVersion() throws Exception {
            Files.createDirectories(store.store().getParent());
            var document = "{\"format_version\":2,\"generations\":{}}".getBytes(StandardCharsets.UTF_8);
            Files.write(store.store(), document);

            var onWrite = assertThrows(StoreRefusal.class, () -> store.enter(SESSION));
            var onRead = assertThrows(StoreRefusal.class, () -> store.snapshot());

            assertAll(
                    () -> assertEquals(Outcome.FORMAT_NEWER, onWrite.outcome()),
                    () -> assertEquals(Outcome.FORMAT_NEWER, onRead.outcome()),
                    () -> assertArrayEquals(document, Files.readAllBytes(store.store())),
                    () -> assertEquals(0, locks.open, "no transaction is left open"));
        }
    }

    /** A clock that stands still until the test moves it. */
    private static final class SteppingClock extends Clock {

        private Instant now;

        SteppingClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    /** A source of drawn ids that returns the given values in order. */
    private static final class SequenceRandom implements RandomGenerator {

        private final Deque<Long> values = new ArrayDeque<>();

        SequenceRandom(long... values) {
            for (var value : values) {
                this.values.add(value);
            }
        }

        @Override
        public long nextLong() {
            return values.removeFirst();
        }
    }

    /** A lock manager that takes no lock of the operating system and records what it is asked for. */
    private static final class RecordingLocks implements LockManager {

        private final List<LockKey> acquisitions = new ArrayList<>();
        private int transactions;
        private int open;

        void reset() {
            acquisitions.clear();
            transactions = 0;
        }

        @Override
        public LockTransaction openTransaction() {
            transactions++;
            open++;
            return new LockTransaction() {

                private final Set<LockKey> own = new HashSet<>();
                private boolean closed;

                @Override
                public void acquire(LockKey key) {
                    acquisitions.add(key);
                    own.add(key);
                }

                @Override
                public boolean holds(LockKey key) {
                    return own.contains(key);
                }

                @Override
                public void close() {
                    if (!closed) {
                        closed = true;
                        open--;
                        own.clear();
                    }
                }
            };
        }
    }
}
