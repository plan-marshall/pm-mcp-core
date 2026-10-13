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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Serial;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.store.PendingEntryRecord.Bootstrap;
import de.planmarshall.core.store.PendingEntryRecord.Kind;
import de.planmarshall.core.store.ScopeTransaction.Step;
import de.planmarshall.core.store.TransactionJournal.Mode;

/**
 * The pending entry records of the workspace scope (PM-CLIENT-1, PM-IMPL-7): created, replaced and removed only as
 * targets of a workspace transaction, a removal a delete target, a fresh id never shared by two entries
 * (PM-WATCH-STORE-42), and a listing that names only what parses as an entry id.
 */
@DisplayName("Pending entry store")
class PendingEntryStoreTest {

    private static final String PROJECT = "pm-mcp-core";
    private static final Instant CREATED_AT = Instant.parse("2026-10-13T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-13T09:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path base;

    private JournalScope workspace;
    private PendingEntryStore store;
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

    /** A source of digits that answers the given values in turn, as a collision of draws needs it. */
    private static final class ScriptedRandom implements RandomGenerator {

        private final Deque<Long> values;

        ScriptedRandom(Long... values) {
            this.values = new ArrayDeque<>(List.of(values));
        }

        @Override
        public long nextLong() {
            return values.removeFirst();
        }
    }

    @BeforeEach
    void createWorkspace() throws IOException {
        workspace = JournalScope.workspace(base, PROJECT);
        Files.createDirectories(workspace.journalFile().getParent());
        Files.createDirectories(workspace.resolve(PendingEntryStore.DIRECTORY));
        store = new PendingEntryStore(workspace);
        locks = new RecordingLockManager();
        audit = new RecordingAuditSink();
    }

    private static PendingEntryRecord entry(EntryId entryId, String state) {
        return PendingEntryRecord.builder().entryId(entryId).kind(Kind.PLAN).createdAt(CREATED_AT)
                .updatedAt(CREATED_AT).session(ScopeSessionRowCodecTest.row())
                .bootstrap(Bootstrap.builder().state(state).callStack(List.of()).facts("{}").options("{}").build())
                .build();
    }

    /** Runs the work in a workspace transaction of its own and commits it. */
    private <T> T inTransaction(Function<ScopeTransaction, T> work) {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(workspace.lockKey());
            var write = ScopeTransaction.open(transaction, workspace, audit, CLOCK);
            var result = work.apply(write);
            write.commit();
            return result;
        }
    }

    private void run(Consumer<ScopeTransaction> work) {
        inTransaction(write -> {
            work.accept(write);
            return null;
        });
    }

    /** Allocates an id and creates its record in one transaction, as an entry does. */
    private EntryId enter(RandomGenerator random) {
        return inTransaction(write -> {
            var entryId = store.allocate(write, random);
            store.create(write, entry(entryId, "start.choose"));
            return entryId;
        });
    }

    @Nested
    @DisplayName("a record")
    class Records {

        @Test
        @DisplayName("is created as a target of the workspace transaction, read back equal and covered by the sidecar")
        void created() {
            var entryId = new EntryId("ent-0123456789abcdef");
            var entry = entry(entryId, "start.choose");

            run(write -> store.create(write, entry));

            assertAll(
                    () -> assertEquals(Optional.of(entry), store.read(entryId)),
                    () -> assertEquals(workspace.pathBase().resolve("pending/ent-0123456789abcdef.json"),
                            store.store(entryId)),
                    () -> assertEquals(Optional.of(StoreDigest.of(PendingEntryCodec.write(entry))),
                            DigestSidecar.load(workspace.sidecarFile()).digest(PendingEntryStore.storePath(entryId))),
                    () -> assertFalse(Files.exists(workspace.journalFile())));
        }

        @Test
        @DisplayName("is replaced by its next bootstrap state, and only once it exists")
        void replaced() {
            var entryId = new EntryId("ent-0123456789abcdef");
            var next = entry(entryId, "start.ready");
            var absent = entry(new EntryId("ent-ffffffff"), "start.ready");

            run(write -> store.create(write, entry(entryId, "start.choose")));
            run(write -> store.replace(write, next));

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(workspace.lockKey());
                var write = ScopeTransaction.open(transaction, workspace, audit, CLOCK);

                assertAll(
                        () -> assertEquals(Optional.of(next), store.read(entryId)),
                        () -> assertThrows(IllegalArgumentException.class, () -> store.replace(write, absent)),
                        () -> assertThrows(IllegalArgumentException.class, () -> store.create(write, next)));
            }
        }

        @Test
        @DisplayName("is removed as a delete target of the workspace journal, with its sidecar entry")
        void removed() throws Exception {
            var entryId = new EntryId("ent-0123456789abcdef");
            run(write -> store.create(write, entry(entryId, "start.choose")));
            ScopeTransaction.StepHook hook = (step, target) -> {
                if (step == Step.JOURNAL) {
                    throw new StopWrite();
                }
            };
            boolean existed;
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(workspace.lockKey());
                var write = ScopeTransaction.open(transaction, workspace, audit, CLOCK, hook);
                existed = store.remove(write, entryId);
                assertThrows(StopWrite.class, write::commit);
            }
            var journal = TransactionJournal.read(workspace.journalFile(),
                    Files.readAllBytes(workspace.journalFile()));

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(workspace.lockKey());
                JournalRecovery.recover(transaction, workspace, audit);
            }

            assertAll(
                    () -> assertTrue(existed),
                    () -> assertEquals(List.of(TransactionJournal.Target.delete("pending/ent-0123456789abcdef.json")),
                            journal.targets()),
                    () -> assertEquals(JournalScopeType.WORKSPACE, journal.scopeType()),
                    () -> assertEquals(Mode.DELETE, journal.targets().getFirst().mode()),
                    () -> assertEquals(Optional.empty(), store.read(entryId)),
                    () -> assertEquals(Optional.empty(), DigestSidecar.load(workspace.sidecarFile())
                            .digest(PendingEntryStore.storePath(entryId))),
                    () -> assertEquals(Set.of(), store.entryIds()));
        }

        @Test
        @DisplayName("that does not exist is removed without error, and the removal says so")
        void removeAbsent() {
            var removed = inTransaction(write -> store.remove(write, new EntryId("ent-0123456789abcdef")));

            assertAll(
                    () -> assertFalse(removed),
                    () -> assertFalse(Files.exists(workspace.journalFile())),
                    () -> assertEquals(Set.of(), store.entryIds()));
        }

        @Test
        @DisplayName("of another scope's transaction is refused")
        void otherScope() throws Exception {
            var plan = JournalScope.plan(base, PROJECT, "a-plan");
            Files.createDirectories(plan.journalFile().getParent());
            var entry = entry(new EntryId("ent-0123456789abcdef"), "start.choose");
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(plan.lockKey());
                var write = ScopeTransaction.open(transaction, plan, audit, CLOCK);

                assertAll(
                        () -> assertThrows(IllegalArgumentException.class, () -> store.create(write, entry)),
                        () -> assertThrows(IllegalArgumentException.class, () -> store.allocate(write)),
                        () -> assertThrows(IllegalArgumentException.class,
                                () -> store.remove(write, entry.entryId())));
            }
        }

        @Test
        @DisplayName("belongs to the workspace scope only")
        void workspaceOnly() {
            var plan = JournalScope.plan(base, PROJECT, "a-plan");

            assertThrows(IllegalArgumentException.class, () -> new PendingEntryStore(plan));
        }
    }

    /** PM-WATCH-STORE-42: an id is checked against the records present under the lock that creates its record. */
    @Nested
    @DisplayName("allocation")
    class Allocation {

        @Test
        @DisplayName("of two entries gives two distinct records, a colliding draw redrawn")
        void twoEntriesTwoRecords() {
            var first = enter(new ScriptedRandom(7L));

            var second = enter(new ScriptedRandom(7L, 8L));

            assertAll(
                    () -> assertNotEquals(first, second),
                    () -> assertEquals(Set.of(first, second), store.entryIds()),
                    () -> assertEquals("start.choose", store.read(first).orElseThrow().bootstrap().state()),
                    () -> assertEquals("start.choose", store.read(second).orElseThrow().bootstrap().state()));
        }

        @Test
        @DisplayName("within one transaction never hands out an id the transaction already writes")
        void sameTransaction() {
            var ids = inTransaction(write -> {
                var first = store.allocate(write, new ScriptedRandom(7L));
                store.create(write, entry(first, "start.choose"));
                var second = store.allocate(write, new ScriptedRandom(7L, 9L));
                store.create(write, entry(second, "start.choose"));
                return List.of(first, second);
            });

            assertAll(
                    () -> assertNotEquals(ids.getFirst(), ids.getLast()),
                    () -> assertEquals(Set.copyOf(ids), store.entryIds()));
        }

        @Test
        @DisplayName("draws an id of ent- and 16 hex digits from the secure source")
        void secureDraw() {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(workspace.lockKey());
                var entryId = store.allocate(ScopeTransaction.open(transaction, workspace, audit, CLOCK));

                assertTrue(entryId.value().matches("ent-[0-9a-f]{16}"), entryId.value());
            }
        }
    }

    @Nested
    @DisplayName("listing")
    class Listing {

        @Test
        @DisplayName("names only the files whose name parses as an entry id")
        void onlyEntryIds() throws Exception {
            var pending = workspace.resolve(PendingEntryStore.DIRECTORY);
            Files.writeString(pending.resolve("ent-0123abcd.json"), "{}");
            Files.writeString(pending.resolve("notes.json"), "{}");
            Files.writeString(pending.resolve("ent-XYZ.json"), "{}");
            Files.writeString(pending.resolve("ent-0123.json"), "{}");
            Files.writeString(pending.resolve("ent-0123abcd.json.tmp.0192a3b4-c5d6-7e8f-9a0b-1c2d3e4f5a6b"), "{}");
            Files.writeString(pending.resolve("ent-89abcdef"), "{}");
            Files.createDirectories(pending.resolve("ent-11111111.json"));

            assertEquals(Set.of(new EntryId("ent-0123abcd")), store.entryIds());
        }

        @Test
        @DisplayName("of a workspace without pending/ is empty")
        void absentDirectory() throws Exception {
            Files.delete(workspace.resolve(PendingEntryStore.DIRECTORY));

            assertEquals(Set.of(), store.entryIds());
        }
    }

    @Nested
    @DisplayName("entry id")
    class EntryIds {

        @Test
        @DisplayName("parses ent- and 8 to 32 hex digits and nothing else")
        void parse() {
            assertAll(
                    () -> assertEquals(Optional.of(new EntryId("ent-01234567")), EntryId.parse("ent-01234567")),
                    () -> assertTrue(EntryId.parse("ent-" + "a".repeat(32)).isPresent()),
                    () -> assertEquals(Optional.empty(), EntryId.parse("ent-0123456")),
                    () -> assertEquals(Optional.empty(), EntryId.parse("ent-" + "a".repeat(33))),
                    () -> assertEquals(Optional.empty(), EntryId.parse("ent-0123456G")),
                    () -> assertEquals(Optional.empty(), EntryId.parse("ENT-01234567")),
                    () -> assertEquals(Optional.empty(), EntryId.parse(null)),
                    () -> assertThrows(IllegalArgumentException.class, () -> new EntryId("x")));
        }

        @Test
        @DisplayName("names its record <entry_id>.json and is found again from that name")
        void fileName() {
            var entryId = new EntryId("ent-01234567");

            assertAll(
                    () -> assertEquals("ent-01234567.json", entryId.fileName()),
                    () -> assertEquals(Optional.of(entryId), EntryId.ofFileName(entryId.fileName())),
                    () -> assertEquals(Optional.empty(), EntryId.ofFileName("ent-01234567")),
                    () -> assertEquals("ent-01234567", entryId.toString()));
        }

        @Test
        @DisplayName("drawn twice from the same digits is the same id")
        void deterministicDraw() {
            assertEquals(EntryId.random(new ScriptedRandom(1L)), EntryId.random(new ScriptedRandom(1L)));
        }
    }
}
