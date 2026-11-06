/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.start;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.store.EntryId;
import de.planmarshall.core.store.JournalScope;
import de.planmarshall.core.store.PendingEntryCodec;
import de.planmarshall.core.store.PendingEntryRecord;
import de.planmarshall.core.store.PendingEntryStore;
import de.planmarshall.core.store.ScopeTransaction;
import de.planmarshall.runtime.lock.FileLockManager;
import de.planmarshall.runtime.log.RuntimeLogMessages.INFO;
import de.planmarshall.runtime.start.RecoveryStep.Outcome;
import de.planmarshall.runtime.start.RecoveryStepFixtures.RecordingAuditSink;

/**
 * The pending-record step of the periodic sweep over the workspace scope of a project in a test base, on the lock
 * manager of the runtime: an old record is removed in a workspace transaction with its audit record, a young one is
 * kept, a name that is no entry id is never touched, and what cannot be done is reported as a failure. The reports,
 * the audit record name and its detail keys are the specified contract; the project, the instants and the entry ids
 * are generated.
 */
@EnableGeneratorController
@EnableTestLogger
@DisplayName("Pending entry records sweep")
class PendingEntryRecordsSweepTest {

    /** Contract literal: the name of the audit record of a removal. */
    private static final String PENDING_ENTRY_REMOVED = "pending_entry_removed";

    @TempDir
    Path base;

    private final Instant now = Instant.ofEpochSecond(Generators.longs(1_700_000_000L, 2_000_000_000L).next());
    private final Instant olderThan = now.minus(PeriodicSweep.PENDING_ENTRY_MAX_AGE);
    private final Instant old = olderThan.minusSeconds(Generators.longs(1, 86_400).next());
    private final Instant young = olderThan.plusSeconds(Generators.longs(1, 86_400).next());
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final String projectId = "project-" + Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    private final Set<EntryId> drawn = new HashSet<>();
    private final EntryId oldEntry = entryId();
    private final EntryId youngEntry = entryId();
    private final RecordingAuditSink auditSink = new RecordingAuditSink();

    private FileLockManager lockManager;
    private JournalScope workspace;
    private PendingEntryStore store;
    private PendingEntryRecordsSweep sweep;

    @BeforeEach
    void createWorkspace() throws IOException {
        lockManager = new FileLockManager(auditSink);
        workspace = JournalScope.workspace(base, projectId);
        Files.createDirectories(workspace.journalFile().getParent());
        Files.createDirectories(workspace.resolve(PendingEntryStore.DIRECTORY));
        store = new PendingEntryStore(workspace);
        sweep = new PendingEntryRecordsSweep(base, lockManager, auditSink, clock);
    }

    /** @return a generated entry id of 16 lower-case hexadecimal digits, distinct from the others of the test */
    private EntryId entryId() {
        EntryId entryId;
        do {
            entryId = new EntryId("ent-" + Generators.strings("0123456789abcdef", 16, 16).next());
        } while (!drawn.add(entryId));
        return entryId;
    }

    /** A record of a plan the server starts for an epic row, the one kind that carries no session row. */
    private static PendingEntryRecord entry(EntryId entryId, Instant createdAt) {
        // Contract literals: the start state of a bootstrap and the empty JSON objects of its facts and options
        return PendingEntryRecord.builder().entryId(entryId).kind(PendingEntryRecord.Kind.PLAN)
                .fromEpic("epic-" + Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT))
                .createdAt(createdAt).updatedAt(createdAt)
                .bootstrap(PendingEntryRecord.Bootstrap.builder().state("start.choose").callStack(List.of())
                        .facts("{}").options("{}").build())
                .build();
    }

    private void inWorkspace(Consumer<ScopeTransaction> work) {
        try (var lock = lockManager.openTransaction()) {
            lock.acquire(workspace.lockKey());
            var transaction = ScopeTransaction.open(lock, workspace, auditSink, clock);
            work.accept(transaction);
            transaction.commit();
        }
    }

    private void create(PendingEntryRecord... entries) {
        inWorkspace(transaction -> List.of(entries).forEach(entry -> store.create(transaction, entry)));
    }

    private Path writeFile(String name, String content) throws IOException {
        return Files.writeString(store.directoryPath().resolve(name), content, StandardCharsets.UTF_8);
    }

    private void assertSweptLogged(int removed) {
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                INFO.PENDING_ENTRY_RECORDS_SWEPT.resolveIdentifierString());
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                "Periodic sweep removed " + removed + " pending entry records of project '" + projectId + "'");
    }

    private static void assertNothingSweptLogged() {
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO,
                INFO.PENDING_ENTRY_RECORDS_SWEPT.resolveIdentifierString());
    }

    @Test
    @DisplayName("is the pending-record step of the periodic sweep")
    void name() {
        assertEquals(SweepStep.Name.PENDING_ENTRY_RECORDS, sweep.name(), "the step must be the pending-record step");
    }

    @Test
    @DisplayName("an old record is removed with one pending_entry_removed record; a young one is kept")
    void removesOldKeepsYoung() {
        create(entry(oldEntry, old), entry(youngEntry, young));
        var before = auditSink.events().size();

        var outcome = sweep.run(projectId, olderThan);

        var removals = auditSink.events().subList(before, auditSink.events().size());
        assertAll(
                () -> assertEquals(new Outcome.Done("removed 1 pending entry records"), outcome,
                        "the step must report one removed record"),
                () -> assertEquals(Optional.empty(), store.read(oldEntry), "the old record must be removed"),
                () -> assertTrue(store.read(youngEntry).isPresent(), "the young record is kept"),
                () -> assertEquals(1, removals.size(), "one audit record must be owed by the removal"),
                () -> assertEquals(PENDING_ENTRY_REMOVED, removals.getFirst().event(),
                        "the audit record must be pending_entry_removed"),
                // Contract literals: the detail keys of pending_entry_removed and the store path prefix
                () -> assertEquals(Map.of("entry_id", oldEntry.value(), "store", "pending/" + oldEntry.fileName(),
                                "created_at", old.toString()), withoutTransaction(removals.getFirst().details()),
                        "the audit record must name the removed record"),
                () -> assertTrue(removals.getFirst().details().containsKey("transaction_id"),
                        "the audit record must carry a transaction_id"),
                () -> assertFalse(Files.exists(workspace.journalFile()), "the workspace journal is closed"),
                () -> assertSweptLogged(1));
    }

    @Test
    @DisplayName("two old records removed in one run owe two records with two distinct transaction ids")
    void removalsCarryDistinctTransactionIds() {
        var secondOld = entryId();
        create(entry(oldEntry, old), entry(secondOld, old));
        var before = auditSink.events().size();

        var outcome = sweep.run(projectId, olderThan);

        var removals = auditSink.events().subList(before, auditSink.events().size());
        var transactionIds = removals.stream().map(removal -> removal.details().get("transaction_id")).toList();
        assertAll(
                () -> assertEquals(new Outcome.Done("removed 2 pending entry records"), outcome,
                        "the step must report two removed records"),
                () -> assertEquals(List.of(PENDING_ENTRY_REMOVED, PENDING_ENTRY_REMOVED),
                        removals.stream().map(AuditEvent::event).toList(), "two removal records must be owed"),
                () -> assertEquals(2, transactionIds.size(), "each removal record carries a transaction id"),
                () -> assertTrue(transactionIds.stream().allMatch(String.class::isInstance),
                        "every transaction id must be a string"),
                () -> assertEquals(2, Set.copyOf(transactionIds).size(), "the two transaction ids must be distinct"),
                () -> assertSweptLogged(2));
    }

    @Test
    @DisplayName("a record created exactly at the instant is kept")
    void createdAtTheInstantKept() {
        var atTheInstant = entryId();
        create(entry(atTheInstant, olderThan));

        var outcome = sweep.run(projectId, olderThan);

        assertAll(
                () -> assertEquals(new Outcome.Done("removed 0 pending entry records"), outcome,
                        "the step must report no removed record"),
                () -> assertTrue(store.read(atTheInstant).isPresent(), "a record created at the instant is kept"),
                () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO, "Periodic sweep removed"),
                PendingEntryRecordsSweepTest::assertNothingSweptLogged);
    }

    @Test
    @DisplayName("a file whose name is no entry id is never touched, however old (the names are the cases)")
    void unparsableNamesUntouched() throws Exception {
        // Fixture paths: a name without the prefix, one with upper-case digits and one with too few digits
        var notes = writeFile("notes.json", "{}");
        var upper = writeFile("ent-00000000000000AB.json", "{}");
        var tooShort = writeFile("ent-1.json", "{}");
        var oldTime = FileTime.from(old.minusSeconds(86_400));
        for (var file : List.of(notes, upper, tooShort)) {
            Files.setLastModifiedTime(file, oldTime);
        }

        var outcome = sweep.run(projectId, olderThan);

        assertAll(
                () -> assertEquals(new Outcome.Done("no pending entry record"), outcome,
                        "the step must report no pending entry record"),
                () -> assertTrue(Files.exists(notes), "a name without the prefix is never touched"),
                () -> assertTrue(Files.exists(upper), "a name with upper-case digits is never touched"),
                () -> assertTrue(Files.exists(tooShort), "a name with too few digits is never touched"),
                () -> assertEquals(List.of(), auditSink.eventNames(), "no audit record must be handed over"),
                PendingEntryRecordsSweepTest::assertNothingSweptLogged);
    }

    @Test
    @DisplayName("a project without pending records has nothing to remove")
    void noRecords() {
        var otherProject = "other-" + Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);

        var outcome = new PendingEntryRecordsSweep(base, lockManager, auditSink, clock).run(otherProject, olderThan);

        assertAll(
                () -> assertEquals(new Outcome.Done("no pending entry record"), outcome,
                        "a project without records has nothing to remove"),
                PendingEntryRecordsSweepTest::assertNothingSweptLogged);
    }

    @Test
    @DisplayName("an unreadable record is kept and reported as a failure, after the readable old ones were removed")
    void unreadableKeptAsFailure() throws Exception {
        create(entry(oldEntry, old));
        var broken = entryId();
        // Fixture: a truncated record that does not parse
        writeFile(broken.fileName(), "{\"format_version\":1,");

        var outcome = sweep.run(projectId, olderThan);

        var reason = assertInstanceOf(Outcome.Failed.class, outcome, "an unreadable record fails the step").reason();
        assertAll(
                () -> assertTrue(reason.startsWith("removed 1 pending entry records; kept unreadable records"),
                        "the report must count the removed record"),
                () -> assertTrue(reason.contains(broken.value() + " (unreadable)"),
                        "the report must name the unreadable record"),
                () -> assertEquals(Optional.empty(), store.read(oldEntry), "the readable old record is removed"),
                () -> assertTrue(Files.exists(store.store(broken)), "the unreadable record is kept"),
                () -> assertSweptLogged(1));
    }

    @Test
    @DisplayName("an old record that states another entry_id than its file name is kept and reported as unreadable, "
            + "and the other old records are still removed")
    void mismatchingEntryIdKeptAsFailure() throws Exception {
        var secondOld = entryId();
        create(entry(oldEntry, old), entry(secondOld, old));
        var named = entryId();
        var stated = entryId();
        var mismatching = Files.write(store.store(named), PendingEntryCodec.write(entry(stated, old)));
        var before = auditSink.events().size();

        var outcome = sweep.run(projectId, olderThan);

        var reason = assertInstanceOf(Outcome.Failed.class, outcome, "a mismatching record fails the step").reason();
        var removals = auditSink.events().subList(before, auditSink.events().size());
        assertAll(
                () -> assertTrue(reason.startsWith("removed 2 pending entry records; kept unreadable records"),
                        "the report must count the two removed records"),
                () -> assertTrue(reason.contains(named.value() + " (unreadable)"),
                        "the report must name the file of the record"),
                () -> assertFalse(reason.contains(stated.value()), "the report must not name the stated entry id"),
                () -> assertEquals(Optional.empty(), store.read(oldEntry), "the first old record is removed"),
                () -> assertEquals(Optional.empty(), store.read(secondOld), "the second old record is removed"),
                () -> assertTrue(Files.exists(mismatching), "the mismatching record is kept"),
                () -> assertEquals(List.of(oldEntry.value(), secondOld.value()).stream().sorted().toList(),
                        removals.stream().map(removal -> removal.details().get("entry_id")).toList(),
                        "the removals name the two old records in order"),
                () -> assertSweptLogged(2));
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    @DisplayName("a record that cannot be read at all is kept and reported, and the readable old ones are still removed")
    void ioFailureKeptAsFailure() throws Exception {
        create(entry(oldEntry, old));
        var locked = entryId();
        var file = writeFile(locked.fileName(), "{}");
        Files.setPosixFilePermissions(file, Set.of());
        assumeFalse(Files.isReadable(file), "a file without permissions is readable for this user");

        var outcome = sweep.run(projectId, olderThan);

        var reason = assertInstanceOf(Outcome.Failed.class, outcome, "a record not readable fails the step").reason();
        assertAll(
                () -> assertTrue(reason.startsWith("removed 1 pending entry records; kept unreadable records"),
                        "the report must count the removed record"),
                () -> assertTrue(reason.contains(locked.value() + " (io: "),
                        "the report must name the I/O failure of the record"),
                () -> assertEquals(Optional.empty(), store.read(oldEntry), "the readable old record is removed"),
                () -> assertTrue(Files.exists(file), "the record that cannot be read is kept"),
                () -> assertSweptLogged(1));
    }

    @Test
    @DisplayName("a workspace journal still to recover is reported as a failure, and nothing is removed")
    void journalToRecoverFails() throws Exception {
        create(entry(oldEntry, old));
        Files.writeString(workspace.journalFile(), "{}", StandardCharsets.UTF_8);

        var outcome = sweep.run(projectId, olderThan);

        assertAll(
                () -> assertTrue(assertInstanceOf(Outcome.Failed.class, outcome, "a journal to recover fails the step")
                                .reason().startsWith("pending entry records not swept: "),
                        "the report must say that nothing was swept"),
                () -> assertTrue(store.read(oldEntry).isPresent(), "the record is kept"),
                PendingEntryRecordsSweepTest::assertNothingSweptLogged);
    }

    private static Map<String, Object> withoutTransaction(Map<String, ?> details) {
        var copy = new HashMap<String, Object>(details);
        copy.remove("transaction_id");
        return copy;
    }
}
