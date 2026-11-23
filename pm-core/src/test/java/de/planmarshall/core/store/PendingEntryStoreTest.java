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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.store.PendingEntryRecord.Bootstrap;
import de.planmarshall.core.store.PendingEntryRecord.Kind;
import de.planmarshall.core.store.ScopeTransaction.Step;
import de.planmarshall.core.store.SourceManifest.Source;
import de.planmarshall.core.store.SourceManifest.SourceFile;
import de.planmarshall.core.store.SourceManifest.Trust;
import de.planmarshall.core.store.TransactionJournal.Mode;

/**
 * The pending entries {@code pending/<entry_id>/} of the workspace scope (PM-CLIENT-1, PM-IMPL-7): created with their
 * record, manifest, question store, decision store and transcript as targets of a workspace transaction, replaced,
 * removed, a fresh id never shared by two entries (PM-WATCH-STORE-42), a listing that names only the directories whose
 * name parses as an entry id, and an entry whose store differs from its digest held alone (Store Schemas § Pending
 * Entry). The {@code pending/} layout, the member names and the entry-id grammar table are contracts; ids, states and
 * instants are generated.
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
    private int sourceFiles;

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

    /** @return a source of the manifest whose file name, size and digest are generated */
    private Source source() {
        var content = Generators.letterStrings(1, 20).next();
        var digest = Generators.strings("0123456789abcdef", 64, 64).next();
        // The counter keeps two sources of one manifest off one file path, as the manifest requires
        return new Source("source_" + lowerName(), SourceManifest.Kind.DESCRIPTION, lowerName(),
                "operator", Trust.TRUSTED,
                List.of(new SourceFile(lowerName() + "-" + sourceFiles++ + ".md", content.length(), digest)),
                createdAt, null, null);
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

    /** Allocates an id and creates its entry in one transaction, as an entry does. */
    private EntryId enter(RandomGenerator random) {
        return inTransaction(write -> {
            var allocated = store.allocate(write, random);
            store.create(write, entry(allocated, state));
            return allocated;
        });
    }

    @Nested
    @DisplayName("an entry")
    class Entries {

        @Test
        @DisplayName("is created as one transaction's targets: record, manifest, two stores and a transcript")
        void created() {
            var entry = entry(entryId, state);
            var sources = new SourceManifest(1, List.of(source()));

            run(write -> store.create(write, entry, sources));

            assertAll(
                    () -> assertEquals(Optional.of(entry), store.read(entryId), "the record should be read back"),
                    () -> assertEquals(workspace.pathBase().resolve("pending/" + entryId.value() + "/entry.json"),
                            store.store(entryId), "the record should lie in the entry's directory"),
                    () -> assertEquals(Optional.of(sources), store.readSources(entryId),
                            "the manifest should be read back"),
                    () -> assertEquals(workspace.pathBase().resolve(
                                    "pending/" + entryId.value() + "/sources/sources.json"),
                            store.sourcesStore(entryId), "the manifest should lie in sources/"),
                    () -> assertEquals(Optional.of(StoreDigest.of(PendingEntryCodec.write(entry))),
                            DigestSidecar.load(workspace.sidecarFile()).digest(PendingEntryStore.storePath(entryId)),
                            "the sidecar should hold the record digest"),
                    () -> assertTrue(Files.isDirectory(store.chatDirectory(entryId)),
                            "the transcript directory should exist"),
                    () -> assertEquals(0L, Files.size(workspace.pathBase().resolve(
                                    "pending/" + entryId.value() + "/questions.jsonl")),
                            "the question store should be created empty"),
                    () -> assertEquals(0L, Files.size(workspace.pathBase().resolve(
                                    "pending/" + entryId.value() + "/decision-log.jsonl")),
                            "the decision store should be created empty"),
                    () -> assertFalse(Files.exists(workspace.journalFile()), "the journal should be closed after it"));
        }

        @Test
        @DisplayName("is created with an empty manifest and its stores when no source is given")
        void createdEmpty() {
            run(write -> store.create(write, entry(entryId, state)));

            assertAll(
                    () -> assertEquals(Optional.of(SourceManifest.empty()), store.readSources(entryId),
                            "the manifest should start empty"),
                    () -> assertEquals(Set.of(entryId), store.entryIds(), "the entry should be listed"));
        }

        @Test
        @DisplayName("is created in one transaction: a stop after its journal rolls all four stores forward")
        void createdInOneTransaction() throws Exception {
            var sources = new SourceManifest(1, List.of(source()));
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(workspace.lockKey());
                var write = ScopeTransaction.open(transaction, workspace, audit, clock, (step, _) -> {
                    if (step == Step.JOURNAL) {
                        throw new StopWrite();
                    }
                });
                store.create(write, entry(entryId, state), sources);
                assertThrows(StopWrite.class, write::commit, "the commit should stop after the journal");
            }
            var journal = TransactionJournal.read(workspace.journalFile(),
                    Files.readAllBytes(workspace.journalFile()));

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(workspace.lockKey());
                JournalRecovery.recover(transaction, workspace, audit);
            }

            assertAll(
                    () -> assertEquals(4, journal.targets().size(),
                            "one journal should carry the entry's four stores"),
                    () -> assertTrue(journal.targets().stream().allMatch(target -> target.mode() == Mode.REPLACE),
                            "every one of the four should replace its store"),
                    () -> assertEquals(Optional.of(entry(entryId, state)), store.read(entryId),
                            "the record should roll forward"),
                    () -> assertEquals(Optional.of(sources), store.readSources(entryId),
                            "the manifest should roll forward"),
                    () -> assertEquals(new PendingEntryStore.Integrity.Verified(entryId), verify(entryId),
                            "all four stores should roll forward together"),
                    () -> assertFalse(Files.exists(workspace.journalFile()),
                            "the recovery should close the journal"));
        }

        @Test
        @DisplayName("is created in one transaction: a stop in the apply step rolls the other stores forward")
        void createdAfterAPartialApply() {
            var sources = new SourceManifest(1, List.of(source()));
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(workspace.lockKey());
                var write = ScopeTransaction.open(transaction, workspace, audit, clock, (step, index) -> {
                    if (step == Step.APPLY && index == 1) {
                        throw new StopWrite();
                    }
                });
                store.create(write, entry(entryId, state), sources);
                assertThrows(StopWrite.class, write::commit, "the commit should stop inside the apply step");
            }

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(workspace.lockKey());
                JournalRecovery.recover(transaction, workspace, audit);
            }

            assertAll(
                    () -> assertEquals(Optional.of(entry(entryId, state)), store.read(entryId),
                            "the record should be applied by the recovery"),
                    () -> assertEquals(Optional.of(sources), store.readSources(entryId),
                            "the manifest should be applied by the recovery"),
                    () -> assertEquals(new PendingEntryStore.Integrity.Verified(entryId), verify(entryId),
                            "the four stores should be complete after the recovery"));
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
                                "an absent entry should not be replaced"),
                        () -> assertThrows(IllegalArgumentException.class, () -> store.create(write, next),
                                "an existing entry should not be created"));
            }
        }

        @Test
        @DisplayName("keeps each added source in its manifest and advances its revision")
        void sourcesAdded() {
            var first = source();
            var second = source();

            run(write -> store.create(write, entry(entryId, state), new SourceManifest(1, List.of(first))));
            run(write -> store.replaceSources(write, entryId, store.readSources(entryId).orElseThrow()
                    .withSource(second)));

            var sources = store.readSources(entryId).orElseThrow();
            assertAll(
                    () -> assertEquals(2, sources.sources().size(), "both sources should be in the manifest"),
                    () -> assertEquals(2, sources.revision(), "adding a source should advance the revision"),
                    () -> assertEquals(Optional.of(first), sources.source(first.sourceId()),
                            "the first source should be found by its id"),
                    () -> assertEquals(Optional.of(second), sources.source(second.sourceId()),
                            "the second source should be found by its id"));
        }

        @Test
        @DisplayName("that states another entry_id than its directory is refused as unreadable and left as it is")
        void entryIdDiffersFromDirectory() throws Exception {
            run(write -> store.create(write, entry(entryId, state)));
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
                            "the refusal should name the directory's id"),
                    () -> assertEquals(Optional.empty(), store.read(otherId), "the stated id should have no entry"),
                    () -> assertArrayEquals(document, Files.readAllBytes(store.store(entryId)),
                            "the refused file should stay unchanged"));
        }

        @Test
        @DisplayName("is removed as four delete targets of the workspace journal, the directory discarded last")
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
            var discarded = store.discard(entryId);

            assertAll(
                    () -> assertTrue(existed, "the removal should report the entry"),
                    () -> assertEquals(List.of(
                                    TransactionJournal.Target.delete("pending/" + entryId.value() + "/entry.json"),
                                    TransactionJournal.Target.delete(
                                            "pending/" + entryId.value() + "/sources/sources.json"),
                                    TransactionJournal.Target.delete(
                                            "pending/" + entryId.value() + "/questions.jsonl"),
                                    TransactionJournal.Target.delete(
                                            "pending/" + entryId.value() + "/decision-log.jsonl")),
                            journal.targets(), "the journal should hold the entry's four delete targets"),
                    () -> assertEquals(JournalScopeType.WORKSPACE, journal.scopeType(),
                            "the journal should be the workspace's"),
                    () -> assertEquals(Mode.DELETE, journal.targets().getFirst().mode(),
                            "the targets should be deletes"),
                    () -> assertEquals(Optional.empty(), store.read(entryId), "the record should be removed"),
                    () -> assertEquals(Optional.empty(), DigestSidecar.load(workspace.sidecarFile())
                            .digest(PendingEntryStore.storePath(entryId)), "the sidecar entry should be removed"),
                    () -> assertTrue(discarded, "the directory should be discarded"),
                    () -> assertFalse(Files.exists(store.entryDirectory(entryId)), "the directory should be gone"),
                    () -> assertEquals(Set.of(), store.entryIds(), "no entry should be listed"));
        }

        @Test
        @DisplayName("abandoned: the stores go in one transaction, the directory with sources and chat last")
        void abandoned() throws Exception {
            run(write -> store.create(write, entry(entryId, state)));
            var description = Files.writeString(store.sourcesDirectory(entryId).resolve("description.md"),
                    Generators.letterStrings(20, 60).next());
            var chat = Files.writeString(store.chatDirectory(entryId).resolve("session.md"),
                    Generators.letterStrings(20, 60).next());

            run(write -> store.remove(write, entryId));
            var discarded = store.discard(entryId);

            assertAll(
                    () -> assertTrue(discarded, "the abandon should find the directory"),
                    () -> assertFalse(Files.exists(store.entryDirectory(entryId)),
                            "the directory should be gone with its sources"),
                    () -> assertFalse(Files.exists(description), "the stored source should be gone"),
                    () -> assertFalse(Files.exists(chat), "the transcript should be gone"),
                    () -> assertEquals(Set.of(), store.entryIds(), "no entry should be listed"),
                    () -> assertEquals(Optional.empty(), DigestSidecar.load(workspace.sidecarFile())
                                    .digest(PendingEntryStore.storePath(entryId)),
                            "the sidecar should keep no store of the entry"));
        }

        @Test
        @DisplayName("abandoned twice: the second discard reports that nothing was there, never an error")
        void discardedTwice() {
            run(write -> store.create(write, entry(entryId, state)));
            run(write -> store.remove(write, entryId));
            store.discard(entryId);

            assertFalse(store.discard(entryId), "a directory already removed should be reported as absent");
        }

        @Test
        @DisplayName("that does not exist is removed without error, and the removal says so")
        void removeAbsent() {
            var removed = inTransaction(write -> store.remove(write, entryId));

            assertAll(
                    () -> assertFalse(removed, "the removal should report no entry"),
                    () -> assertFalse(Files.exists(workspace.journalFile()), "no journal should remain"),
                    () -> assertEquals(Set.of(), store.entryIds(), "no entry should be listed"));
        }

        @Test
        @DisplayName("whose record still exists is never discarded: the stores must be deleted by a transaction")
        void discardRefusesALiveEntry() {
            run(write -> store.create(write, entry(entryId, state)));

            assertThrows(IllegalStateException.class, () -> store.discard(entryId),
                    "a live record blocks the discard");
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

    /** Store Schemas § Pending Entry: a digest mismatch holds only that entry, until its edit is accepted. */
    @Nested
    @DisplayName("integrity")
    class Integrity {

        @Test
        @DisplayName("a verified entry is verified, an absent one is absent")
        void verifiedAndAbsent() {
            var id = enter(new ScriptedRandom(Generators.longs(0, Long.MAX_VALUE - 1).next()));

            var verified = verify(id);
            var absent = verify(otherId);

            assertAll(
                    () -> assertEquals(new PendingEntryStore.Integrity.Verified(id), verified,
                            "the untouched entry should be verified"),
                    () -> assertEquals(new PendingEntryStore.Integrity.Absent(otherId), absent,
                            "a missing entry should be absent"));
        }

        @Test
        @DisplayName("a tampered record holds that entry alone, and its edit is accepted by a re-baseline")
        void tamperedEntryAlone() throws Exception {
            var first = enter(new ScriptedRandom(Generators.longs(0, Long.MAX_VALUE - 2).next()));
            var second = enter(new ScriptedRandom(Generators.longs(0, Long.MAX_VALUE - 2).next(),
                    Generators.longs(0, Long.MAX_VALUE - 2).next()));
            Files.write(store.store(first), PendingEntryCodec.write(entry(first, nextState)));

            var tampered = assertInstanceOf(PendingEntryStore.Integrity.Tampered.class, verify(first),
                    "the edited entry should be tampered");
            var other = verify(second);

            acceptEdit(first);

            assertAll(
                    () -> assertInstanceOf(ScopeIntegrity.Mismatch.class, tampered.cause(),
                            "the cause should be the record's mismatch"),
                    () -> assertEquals(first, tampered.entryId(), "the cause should name the entry"),
                    () -> assertEquals(new PendingEntryStore.Integrity.Verified(second), other,
                            "the other entry should stay verified"),
                    () -> assertEquals(new PendingEntryStore.Integrity.Verified(first), verify(first),
                            "the re-baseline should accept the edit"));
        }

        @Test
        @DisplayName("a store that no transaction wrote is tampered, never verified")
        void untrackedIsTampered() throws Exception {
            Files.createDirectories(store.sourcesDirectory(entryId));
            Files.write(store.store(entryId), PendingEntryCodec.write(entry(entryId, state)));
            Files.write(store.sourcesStore(entryId), SourceManifestCodec.write(SourceManifest.empty()));

            var tampered = assertInstanceOf(PendingEntryStore.Integrity.Tampered.class, verify(entryId),
                    "a hand-written store should be tampered");
            assertInstanceOf(ScopeIntegrity.Untracked.class, tampered.cause(),
                    "the cause should be the untracked store");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"questions.jsonl", "decision-log.jsonl", "sources/sources.json"})
        @DisplayName("a tampered store holds the entry alone, and the cause names that store")
        void tamperedStoreAlone(String member) throws Exception {
            var id = enter(new ScriptedRandom(Generators.longs(0, Long.MAX_VALUE - 1).next()));
            Files.writeString(workspace.resolve(PendingEntryStore.directoryPath(id) + "/" + member), "{}");

            var tampered = assertInstanceOf(PendingEntryStore.Integrity.Tampered.class, verify(id),
                    "the edited store should hold its entry");

            var mismatch = assertInstanceOf(ScopeIntegrity.Mismatch.class, tampered.cause(),
                    "the cause should be the edited store's mismatch");
            assertAll(
                    () -> assertEquals(PendingEntryStore.directoryPath(id) + "/" + member, mismatch.store(),
                            "the cause should name the edited store"),
                    () -> assertNotEquals(mismatch.recorded(), mismatch.current(),
                            "the edited store should differ from its recorded digest"));
        }

        @Test
        @EnabledOnOs({OS.LINUX, OS.MAC})
        @DisplayName("a store path through a symbolic link is refused, so the caller's verify guards every later read")
        void linkedStoreRefused() throws Exception {
            var id = enter(new ScriptedRandom(Generators.longs(0, Long.MAX_VALUE - 1).next()));
            var manifest = store.sourcesStore(id);
            var outside = Files.write(base.resolve("outside-sources.json"),
                    SourceManifestCodec.write(SourceManifest.empty()));
            Files.delete(manifest);
            Files.createSymbolicLink(manifest, outside);

            var refusal = assertThrows(IllegalArgumentException.class, () -> verify(id),
                    "a linked store path should be refused by the verification");

            assertTrue(refusal.getMessage().contains("symbolic link"),
                    "the refusal should name the link it found");
        }

        @Test
        @DisplayName("a record deleted outside a transaction is a mismatch naming it, never verified")
        void deletedRecord() throws Exception {
            var id = enter(new ScriptedRandom(Generators.longs(0, Long.MAX_VALUE - 1).next()));
            Files.delete(store.store(id));

            var tampered = assertInstanceOf(PendingEntryStore.Integrity.Tampered.class, verify(id),
                    "the deleted record should hold its entry");

            var mismatch = assertInstanceOf(ScopeIntegrity.Mismatch.class, tampered.cause(),
                    "the cause should be the deleted record's mismatch");
            assertAll(
                    () -> assertEquals(PendingEntryStore.storePath(id), mismatch.store(),
                            "the cause should name the deleted record"),
                    () -> assertNull(mismatch.current(), "the deleted record should have no current digest"));
        }
    }

    private PendingEntryStore.Integrity verify(EntryId id) {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(workspace.lockKey());
            return store.verify(transaction, id, audit);
        }
    }

    private void acceptEdit(EntryId id) {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(workspace.lockKey());
            ScopeIntegrity.rebaseline(transaction, workspace, PendingEntryStore.storePath(id), audit);
        }
    }

    /** PM-WATCH-STORE-42: an id is checked against the entries present under the lock that creates its directory. */
    @Nested
    @DisplayName("allocation")
    class Allocation {

        @Test
        @DisplayName("of two entries gives two distinct entries, a colliding draw redrawn")
        void twoEntriesTwoRecords() {
            var draw = Generators.longs(0, Long.MAX_VALUE - 1).next();
            var first = enter(new ScriptedRandom(draw));

            var second = enter(new ScriptedRandom(draw, draw + 1));

            assertAll(
                    () -> assertNotEquals(first, second, "the colliding draw should be redrawn"),
                    () -> assertEquals(Set.of(first, second), store.entryIds(), "both entries should be listed"),
                    () -> assertEquals(state, store.read(first).orElseThrow().bootstrap().state(),
                            "the first entry should keep its state"),
                    () -> assertEquals(state, store.read(second).orElseThrow().bootstrap().state(),
                            "the second entry should keep its state"));
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
        @DisplayName("names only the directories whose name parses as an entry id (the names are the grammar table)")
        void onlyEntryIds() throws Exception {
            var pending = workspace.resolve(PendingEntryStore.DIRECTORY);
            Files.createDirectories(pending.resolve("ent-0123abcd"));
            Files.writeString(pending.resolve("notes.json"), "{}");
            Files.writeString(pending.resolve("ent-XYZ.json"), "{}");
            Files.writeString(pending.resolve("ent-0123.json"), "{}");
            Files.writeString(pending.resolve("ent-0123abcd.json.tmp.0192a3b4-c5d6-7e8f-9a0b-1c2d3e4f5a6b"), "{}");
            Files.writeString(pending.resolve("ent-89abcdef"), "{}");
            Files.createDirectories(pending.resolve("ent-11111111.json"));

            assertEquals(Set.of(new EntryId("ent-0123abcd")), store.entryIds(),
                    "only the entry directory should be listed");
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
        @DisplayName("names its directory by its id and is found again from that name")
        void directoryName() {
            var value = shortId();
            var id = new EntryId(value);

            assertAll(
                    () -> assertEquals(value, id.directoryName(), "the directory name should be the id"),
                    () -> assertEquals(Optional.of(id), EntryId.ofDirectoryName(id.directoryName()),
                            "the directory name should parse back"),
                    () -> assertEquals(Optional.empty(), EntryId.ofDirectoryName(value + ".json"),
                            "a name with a suffix should not parse"),
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
