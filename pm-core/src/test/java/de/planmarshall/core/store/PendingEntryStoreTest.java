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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
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
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.random.RandomGenerator;
import org.easymock.CaptureType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.store.PendingEntryRecord.Bootstrap;
import de.planmarshall.core.store.PendingEntryRecord.Kind;
import de.planmarshall.core.store.ScopeTransaction.Step;
import de.planmarshall.core.store.TransactionJournal.Mode;

/**
 * The pending entry records of the workspace scope (PM-CLIENT-1, PM-IMPL-7): created, replaced and removed only as
 * targets of a workspace transaction, a removal a delete target, a fresh id never shared by two entries
 * (PM-WATCH-STORE-42), and a listing that names only what parses as an entry id. The {@code pending/} layout, the
 * {@code .json} file names and the entry-id grammar table are contracts; ids, states and instants are generated.
 */
@EnableGeneratorController
@DisplayName("Pending entry store")
class PendingEntryStoreTest {

    private static final String PROJECT = "pm-mcp-core";

    @TempDir
    Path base;

    private Instant createdAt;
    private Clock clock;
    private EntryId entryId;
    private EntryId otherId;
    private String state;
    private String nextState;
    private JournalScope workspace;
    private PendingEntryStore store;
    private RecordingLockManager locks;
    private AuditSink audit;

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
        createdAt = Instant.ofEpochSecond(Generators.longs(1_700_000_000L, 1_800_000_000L).next());
        clock = Clock.fixed(createdAt.plusSeconds(Generators.longs(0, 100_000L).next()), ZoneOffset.UTC);
        var first = Generators.longs(0, Long.MAX_VALUE - 1).next();
        entryId = new EntryId("ent-%016x".formatted(first));
        otherId = new EntryId("ent-%016x".formatted(first + 1));
        state = lowerName() + "." + lowerName();
        nextState = state + "-" + lowerName();
        workspace = JournalScope.workspace(base, PROJECT);
        Files.createDirectories(workspace.journalFile().getParent());
        Files.createDirectories(workspace.resolve(PendingEntryStore.DIRECTORY));
        store = new PendingEntryStore(workspace);
        locks = new RecordingLockManager();
        audit = AuditSinkDoubles.capturing(newCapture(CaptureType.ALL));
    }

    private static String lowerName() {
        return Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }

    private static String shortId() {
        return "ent-%08x".formatted(Generators.integers(0, Integer.MAX_VALUE).next());
    }

    private PendingEntryRecord entry(EntryId id, String bootstrapState) {
        return PendingEntryRecord.builder().entryId(id).kind(Kind.PLAN).createdAt(createdAt)
                .updatedAt(createdAt)
                .bootstrap(Bootstrap.builder().state(bootstrapState).callStack(List.of()).facts("{}").options("{}")
                        .build())
                .build();
    }

    /** Runs the work in a workspace transaction of its own and commits it. */
    private <T> T inTransaction(Function<ScopeTransaction, T> work) {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(workspace.lockKey());
            var write = ScopeTransaction.open(transaction, workspace, audit, clock);
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
            var allocated = store.allocate(write, random);
            store.create(write, entry(allocated, state));
            return allocated;
        });
    }

    @Nested
    @DisplayName("a record")
    class Records {

        @Test
        @DisplayName("is created as a target of the workspace transaction, read back equal and covered by the sidecar")
        void created() {
            var entry = entry(entryId, state);

            run(write -> store.create(write, entry));

            assertAll(
                    () -> assertEquals(Optional.of(entry), store.read(entryId), "the record should be read back"),
                    () -> assertEquals(workspace.pathBase().resolve("pending/" + entryId.value() + ".json"),
                            store.store(entryId), "the record should lie in pending/"),
                    () -> assertEquals(Optional.of(StoreDigest.of(PendingEntryCodec.write(entry))),
                            DigestSidecar.load(workspace.sidecarFile()).digest(PendingEntryStore.storePath(entryId)),
                            "the sidecar should hold the record digest"),
                    () -> assertFalse(Files.exists(workspace.journalFile()), "the journal should be closed after it"));
        }

        @Test
        @DisplayName("is replaced by its next bootstrap state, and only once it exists")
        void replaced() {
            var next = entry(entryId, nextState);
            var absent = entry(otherId, nextState);

            run(write -> store.create(write, entry(entryId, state)));
            run(write -> store.replace(write, next));

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(workspace.lockKey());
                var write = ScopeTransaction.open(transaction, workspace, audit, clock);

                assertAll(
                        () -> assertEquals(Optional.of(next), store.read(entryId), "the next state should be read"),
                        () -> assertThrows(IllegalArgumentException.class, () -> store.replace(write, absent),
                                "an absent record should not be replaced"),
                        () -> assertThrows(IllegalArgumentException.class, () -> store.create(write, next),
                                "an existing record should not be created"));
            }
        }

        @Test
        @DisplayName("that states another entry_id than its file name is refused as unreadable and left as it is")
        void entryIdDiffersFromFileName() throws Exception {
            var document = PendingEntryCodec.write(entry(otherId, state));
            Files.write(store.store(entryId), document);

            var refusal = assertThrows(StoreRefusal.class, () -> store.read(entryId),
                    "a record of another id should be refused");

            assertAll(
                    () -> assertEquals(StoreRefusal.Outcome.UNREADABLE, refusal.outcome(),
                            "the refusal should be unreadable"),
                    () -> assertEquals(store.store(entryId), refusal.file(), "the refusal should name the file"),
                    () -> assertTrue(refusal.getMessage().contains(otherId.value()),
                            "the refusal should name the stated id"),
                    () -> assertTrue(refusal.getMessage().contains(entryId.value()),
                            "the refusal should name the file's id"),
                    () -> assertEquals(Optional.empty(), store.read(otherId), "the stated id should have no record"),
                    () -> assertArrayEquals(document, Files.readAllBytes(store.store(entryId)),
                            "the refused file should stay unchanged"));
        }

        @Test
        @DisplayName("is removed as a delete target of the workspace journal, with its sidecar entry")
        void removed() throws Exception {
            run(write -> store.create(write, entry(entryId, state)));
            ScopeTransaction.StepHook hook = (step, target) -> {
                if (step == Step.JOURNAL) {
                    throw new StopWrite();
                }
            };
            boolean existed;
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(workspace.lockKey());
                var write = ScopeTransaction.open(transaction, workspace, audit, clock, hook);
                existed = store.remove(write, entryId);
                assertThrows(StopWrite.class, write::commit, "the commit should stop after the journal");
            }
            var journal = TransactionJournal.read(workspace.journalFile(),
                    Files.readAllBytes(workspace.journalFile()));

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(workspace.lockKey());
                JournalRecovery.recover(transaction, workspace, audit);
            }

            assertAll(
                    () -> assertTrue(existed, "the removal should report the record"),
                    () -> assertEquals(List.of(TransactionJournal.Target.delete("pending/" + entryId.fileName())),
                            journal.targets(), "the journal should hold one delete target"),
                    () -> assertEquals(JournalScopeType.WORKSPACE, journal.scopeType(),
                            "the journal should be the workspace's"),
                    () -> assertEquals(Mode.DELETE, journal.targets().getFirst().mode(),
                            "the target should be a delete"),
                    () -> assertEquals(Optional.empty(), store.read(entryId), "the record should be removed"),
                    () -> assertEquals(Optional.empty(), DigestSidecar.load(workspace.sidecarFile())
                            .digest(PendingEntryStore.storePath(entryId)), "the sidecar entry should be removed"),
                    () -> assertEquals(Set.of(), store.entryIds(), "no entry should be listed"));
        }

        @Test
        @DisplayName("that does not exist is removed without error, and the removal says so")
        void removeAbsent() {
            var removed = inTransaction(write -> store.remove(write, entryId));

            assertAll(
                    () -> assertFalse(removed, "the removal should report no record"),
                    () -> assertFalse(Files.exists(workspace.journalFile()), "no journal should remain"),
                    () -> assertEquals(Set.of(), store.entryIds(), "no entry should be listed"));
        }

        @Test
        @DisplayName("of another scope's transaction is refused")
        void otherScope() throws Exception {
            var plan = JournalScope.plan(base, PROJECT, "plan-" + lowerName());
            Files.createDirectories(plan.journalFile().getParent());
            var entry = entry(entryId, state);
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(plan.lockKey());
                var write = ScopeTransaction.open(transaction, plan, audit, clock);

                assertAll(
                        () -> assertThrows(IllegalArgumentException.class, () -> store.create(write, entry),
                                "a plan transaction should not create"),
                        () -> assertThrows(IllegalArgumentException.class, () -> store.allocate(write),
                                "a plan transaction should not allocate"),
                        () -> assertThrows(IllegalArgumentException.class, () -> store.remove(write, entryId),
                                "a plan transaction should not remove"));
            }
        }

        @Test
        @DisplayName("belongs to the workspace scope only")
        void workspaceOnly() {
            var plan = JournalScope.plan(base, PROJECT, "plan-" + lowerName());

            assertThrows(IllegalArgumentException.class, () -> new PendingEntryStore(plan),
                    "a plan scope should have no pending store");
        }
    }

    /** PM-WATCH-STORE-42: an id is checked against the records present under the lock that creates its record. */
    @Nested
    @DisplayName("allocation")
    class Allocation {

        @Test
        @DisplayName("of two entries gives two distinct records, a colliding draw redrawn")
        void twoEntriesTwoRecords() {
            var draw = Generators.longs(0, Long.MAX_VALUE - 1).next();
            var first = enter(new ScriptedRandom(draw));

            var second = enter(new ScriptedRandom(draw, draw + 1));

            assertAll(
                    () -> assertNotEquals(first, second, "the colliding draw should be redrawn"),
                    () -> assertEquals(Set.of(first, second), store.entryIds(), "both entries should be listed"),
                    () -> assertEquals(state, store.read(first).orElseThrow().bootstrap().state(),
                            "the first record should keep its state"),
                    () -> assertEquals(state, store.read(second).orElseThrow().bootstrap().state(),
                            "the second record should keep its state"));
        }

        @Test
        @DisplayName("within one transaction never hands out an id the transaction already writes")
        void sameTransaction() {
            var draw = Generators.longs(0, Long.MAX_VALUE - 2).next();
            var ids = inTransaction(write -> {
                var first = store.allocate(write, new ScriptedRandom(draw));
                store.create(write, entry(first, state));
                var second = store.allocate(write, new ScriptedRandom(draw, draw + 2));
                store.create(write, entry(second, state));
                return List.of(first, second);
            });

            assertAll(
                    () -> assertNotEquals(ids.getFirst(), ids.getLast(), "the two ids should be distinct"),
                    () -> assertEquals(Set.copyOf(ids), store.entryIds(), "both ids should be listed"));
        }

        @Test
        @DisplayName("draws an id of ent- and 16 hex digits from the secure source (id grammar)")
        void secureDraw() {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(workspace.lockKey());
                var drawn = store.allocate(ScopeTransaction.open(transaction, workspace, audit, clock));

                assertTrue(drawn.value().matches("ent-[0-9a-f]{16}"), "the drawn id should have 16 hex digits");
            }
        }
    }

    @Nested
    @DisplayName("listing")
    class Listing {

        @Test
        @DisplayName("names only the files whose name parses as an entry id (the file names are the grammar table)")
        void onlyEntryIds() throws Exception {
            var pending = workspace.resolve(PendingEntryStore.DIRECTORY);
            Files.writeString(pending.resolve("ent-0123abcd.json"), "{}");
            Files.writeString(pending.resolve("notes.json"), "{}");
            Files.writeString(pending.resolve("ent-XYZ.json"), "{}");
            Files.writeString(pending.resolve("ent-0123.json"), "{}");
            Files.writeString(pending.resolve("ent-0123abcd.json.tmp.0192a3b4-c5d6-7e8f-9a0b-1c2d3e4f5a6b"), "{}");
            Files.writeString(pending.resolve("ent-89abcdef"), "{}");
            Files.createDirectories(pending.resolve("ent-11111111.json"));

            assertEquals(Set.of(new EntryId("ent-0123abcd")), store.entryIds(),
                    "only the entry file should be listed");
        }

        @Test
        @DisplayName("of a workspace without pending/ is empty")
        void absentDirectory() throws Exception {
            Files.delete(workspace.resolve(PendingEntryStore.DIRECTORY));

            assertEquals(Set.of(), store.entryIds(), "a missing directory should list nothing");
        }
    }

    @Nested
    @DisplayName("entry id")
    class EntryIds {

        @Test
        @DisplayName("parses ent- and 8 to 32 hex digits and nothing else (grammar boundaries)")
        void parse() {
            var generated = shortId();
            assertAll(
                    () -> assertEquals(Optional.of(new EntryId(generated)), EntryId.parse(generated),
                            "a generated id should parse"),
                    () -> assertEquals(Optional.of(new EntryId("ent-01234567")), EntryId.parse("ent-01234567"),
                            "eight hex digits should parse"),
                    () -> assertTrue(EntryId.parse("ent-" + "a".repeat(32)).isPresent(),
                            "thirty-two hex digits should parse"),
                    () -> assertEquals(Optional.empty(), EntryId.parse("ent-0123456"),
                            "seven hex digits should not parse"),
                    () -> assertEquals(Optional.empty(), EntryId.parse("ent-" + "a".repeat(33)),
                            "thirty-three hex digits should not parse"),
                    () -> assertEquals(Optional.empty(), EntryId.parse("ent-0123456G"),
                            "a non-hex digit should not parse"),
                    () -> assertEquals(Optional.empty(), EntryId.parse("ENT-01234567"),
                            "an uppercase prefix should not parse"),
                    () -> assertEquals(Optional.empty(), EntryId.parse(null), "a null text should not parse"),
                    () -> assertThrows(IllegalArgumentException.class, () -> new EntryId("x"),
                            "a malformed id should be refused"));
        }

        @Test
        @DisplayName("names its record <entry_id>.json and is found again from that name")
        void fileName() {
            var value = shortId();
            var id = new EntryId(value);

            assertAll(
                    () -> assertEquals(value + ".json", id.fileName(), "the file name should add .json"),
                    () -> assertEquals(Optional.of(id), EntryId.ofFileName(id.fileName()),
                            "the file name should parse back"),
                    () -> assertEquals(Optional.empty(), EntryId.ofFileName(value),
                            "a name without .json should not parse"),
                    () -> assertEquals(value, id.toString(), "the id should print as its value"));
        }

        @Test
        @DisplayName("drawn twice from the same digits is the same id")
        void deterministicDraw() {
            var draw = Generators.longs(0, Long.MAX_VALUE).next();
            assertEquals(EntryId.random(new ScriptedRandom(draw)), EntryId.random(new ScriptedRandom(draw)),
                    "the same digits should give the same id");
        }
    }
}
