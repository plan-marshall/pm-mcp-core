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

import static de.planmarshall.runtime.start.RecoveryStepFixtures.PROJECT_A;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;


import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.planmarshall.core.store.EntryId;
import de.planmarshall.core.store.JournalScope;
import de.planmarshall.core.store.PendingEntryCodec;
import de.planmarshall.core.store.PendingEntryRecord;
import de.planmarshall.core.store.PendingEntryStore;
import de.planmarshall.core.store.ScopeTransaction;
import de.planmarshall.runtime.lock.FileLockManager;
import de.planmarshall.runtime.start.RecoveryStep.Outcome;
import de.planmarshall.runtime.start.RecoveryStepFixtures.RecordingAuditSink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The pending-record step of the periodic sweep over the workspace scope of a project in a test base, on the lock
 * manager of the runtime: an old record is removed in a workspace transaction with its audit record, a young one is
 * kept, a name that is no entry id is never touched, and what cannot be done is reported as a failure.
 */
@EnableTestLogger
@DisplayName("Pending entry records sweep")
class PendingEntryRecordsSweepTest {

    private static final Instant NOW = Instant.parse("2026-10-13T12:00:00Z");
    private static final Instant OLDER_THAN = NOW.minus(PeriodicSweep.PENDING_ENTRY_MAX_AGE);
    private static final Instant OLD = OLDER_THAN.minusSeconds(60);
    private static final Instant YOUNG = OLDER_THAN.plusSeconds(60);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final EntryId OLD_ENTRY = new EntryId("ent-00000000000000a1");
    private static final EntryId YOUNG_ENTRY = new EntryId("ent-00000000000000b2");

    @TempDir
    Path base;

    private final RecordingAuditSink auditSink = new RecordingAuditSink();

    private FileLockManager lockManager;
    private JournalScope workspace;
    private PendingEntryStore store;
    private PendingEntryRecordsSweep sweep;

    @BeforeEach
    void createWorkspace() throws IOException {
        lockManager = new FileLockManager(auditSink);
        workspace = JournalScope.workspace(base, PROJECT_A);
        Files.createDirectories(workspace.journalFile().getParent());
        Files.createDirectories(workspace.resolve(PendingEntryStore.DIRECTORY));
        store = new PendingEntryStore(workspace);
        sweep = new PendingEntryRecordsSweep(base, lockManager, auditSink, CLOCK);
    }

    /** A record of a plan the server starts for an epic row, the one kind that carries no session row. */
    private static PendingEntryRecord entry(EntryId entryId, Instant createdAt) {
        return PendingEntryRecord.builder().entryId(entryId).kind(PendingEntryRecord.Kind.PLAN).fromEpic("big-epic")
                .createdAt(createdAt).updatedAt(createdAt)
                .bootstrap(PendingEntryRecord.Bootstrap.builder().state("start.choose").callStack(List.of())
                        .facts("{}").options("{}").build())
                .build();
    }

    private void inWorkspace(Consumer<ScopeTransaction> work) {
        try (var lock = lockManager.openTransaction()) {
            lock.acquire(workspace.lockKey());
            var transaction = ScopeTransaction.open(lock, workspace, auditSink, CLOCK);
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

    @Test
    @DisplayName("is the pending-record step of the periodic sweep")
    void name() {
        assertEquals(SweepStep.Name.PENDING_ENTRY_RECORDS, sweep.name());
    }

    @Test
    @DisplayName("an old record is removed with one pending_entry_removed record; a young one is kept")
    void removesOldKeepsYoung() {
        create(entry(OLD_ENTRY, OLD), entry(YOUNG_ENTRY, YOUNG));
        var before = auditSink.events().size();

        var outcome = sweep.run(PROJECT_A, OLDER_THAN);

        var removals = auditSink.events().subList(before, auditSink.events().size());
        assertAll(
                () -> assertEquals(new Outcome.Done("removed 1 pending entry records"), outcome),
                () -> assertEquals(Optional.empty(), store.read(OLD_ENTRY)),
                () -> assertTrue(store.read(YOUNG_ENTRY).isPresent(), "the young record is kept"),
                () -> assertEquals(1, removals.size(), removals::toString),
                () -> assertEquals("pending_entry_removed", removals.getFirst().event()),
                () -> assertEquals(Map.of("entry_id", OLD_ENTRY.value(), "store", "pending/" + OLD_ENTRY.fileName(),
                        "created_at", OLD.toString()), withoutTransaction(removals.getFirst().details())),
                () -> assertTrue(removals.getFirst().details().containsKey("transaction_id")),
                () -> assertFalse(Files.exists(workspace.journalFile()), "the workspace journal is closed"),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                        "Periodic sweep removed 1 pending entry records of project '" + PROJECT_A + "'"));
    }

    @Test
    @DisplayName("a record created exactly at the instant is kept")
    void createdAtTheInstantKept() {
        var atTheInstant = new EntryId("ent-00000000000000c3");
        create(entry(atTheInstant, OLDER_THAN));

        var outcome = sweep.run(PROJECT_A, OLDER_THAN);

        assertAll(
                () -> assertEquals(new Outcome.Done("removed 0 pending entry records"), outcome),
                () -> assertTrue(store.read(atTheInstant).isPresent()),
                () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO, "Periodic sweep removed"));
    }

    @Test
    @DisplayName("a file whose name is no entry id is never touched, however old")
    void unparsableNamesUntouched() throws Exception {
        var notes = writeFile("notes.json", "{}");
        var upper = writeFile("ent-00000000000000AB.json", "{}");
        var tooShort = writeFile("ent-1.json", "{}");
        var oldTime = FileTime.from(OLD.minusSeconds(86_400));
        for (var file : List.of(notes, upper, tooShort)) {
            Files.setLastModifiedTime(file, oldTime);
        }

        var outcome = sweep.run(PROJECT_A, OLDER_THAN);

        assertAll(
                () -> assertEquals(new Outcome.Done("no pending entry record"), outcome),
                () -> assertTrue(Files.exists(notes)),
                () -> assertTrue(Files.exists(upper)),
                () -> assertTrue(Files.exists(tooShort)),
                () -> assertEquals(List.of(), auditSink.eventNames()));
    }

    @Test
    @DisplayName("a project without pending records has nothing to remove")
    void noRecords() {
        assertEquals(new Outcome.Done("no pending entry record"), new PendingEntryRecordsSweep(base, lockManager,
                auditSink, CLOCK).run("project-b", OLDER_THAN));
    }

    @Test
    @DisplayName("an unreadable record is kept and reported as a failure, after the readable old ones were removed")
    void unreadableKeptAsFailure() throws Exception {
        create(entry(OLD_ENTRY, OLD));
        var broken = new EntryId("ent-00000000000000d4");
        writeFile(broken.fileName(), "{\"format_version\":1,");

        var outcome = sweep.run(PROJECT_A, OLDER_THAN);

        var reason = assertInstanceOf(Outcome.Failed.class, outcome).reason();
        assertAll(
                () -> assertTrue(reason.startsWith("removed 1 pending entry records; kept unreadable records"),
                        reason),
                () -> assertTrue(reason.contains(broken.value() + " (unreadable)"), reason),
                () -> assertEquals(Optional.empty(), store.read(OLD_ENTRY)),
                () -> assertTrue(Files.exists(store.store(broken)), "the unreadable record is kept"));
    }

    @Test
    @DisplayName("an old record that states another entry_id than its file name is kept and reported as unreadable, "
            + "and the other old records are still removed")
    void mismatchingEntryIdKeptAsFailure() throws Exception {
        var secondOld = new EntryId("ent-00000000000000f6");
        create(entry(OLD_ENTRY, OLD), entry(secondOld, OLD));
        var named = new EntryId("ent-00000000000000f7");
        var stated = new EntryId("ent-00000000000000f8");
        var mismatching = Files.write(store.store(named), PendingEntryCodec.write(entry(stated, OLD)));
        var before = auditSink.events().size();

        var outcome = sweep.run(PROJECT_A, OLDER_THAN);

        var reason = assertInstanceOf(Outcome.Failed.class, outcome).reason();
        var removals = auditSink.events().subList(before, auditSink.events().size());
        assertAll(
                () -> assertTrue(reason.startsWith("removed 2 pending entry records; kept unreadable records"),
                        reason),
                () -> assertTrue(reason.contains(named.value() + " (unreadable)"), reason),
                () -> assertFalse(reason.contains(stated.value()), reason),
                () -> assertEquals(Optional.empty(), store.read(OLD_ENTRY)),
                () -> assertEquals(Optional.empty(), store.read(secondOld)),
                () -> assertTrue(Files.exists(mismatching), "the mismatching record is kept"),
                () -> assertEquals(List.of(OLD_ENTRY.value(), secondOld.value()),
                        removals.stream().map(removal -> removal.details().get("entry_id")).toList()));
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    @DisplayName("a record that cannot be read at all is kept and reported, and the readable old ones are still removed")
    void ioFailureKeptAsFailure() throws Exception {
        create(entry(OLD_ENTRY, OLD));
        var locked = new EntryId("ent-00000000000000e5");
        var file = writeFile(locked.fileName(), "{}");
        Files.setPosixFilePermissions(file, Set.of());
        assumeFalse(Files.isReadable(file), "a file without permissions is readable for this user");

        var outcome = sweep.run(PROJECT_A, OLDER_THAN);

        var reason = assertInstanceOf(Outcome.Failed.class, outcome).reason();
        assertAll(
                () -> assertTrue(reason.startsWith("removed 1 pending entry records; kept unreadable records"),
                        reason),
                () -> assertTrue(reason.contains(locked.value() + " (io: "), reason),
                () -> assertEquals(Optional.empty(), store.read(OLD_ENTRY)),
                () -> assertTrue(Files.exists(file), "the record that cannot be read is kept"));
    }

    @Test
    @DisplayName("a workspace journal still to recover is reported as a failure, and nothing is removed")
    void journalToRecoverFails() throws Exception {
        create(entry(OLD_ENTRY, OLD));
        Files.writeString(workspace.journalFile(), "{}", StandardCharsets.UTF_8);

        var outcome = sweep.run(PROJECT_A, OLDER_THAN);

        assertAll(
                () -> assertTrue(assertInstanceOf(Outcome.Failed.class, outcome).reason()
                        .startsWith("pending entry records not swept: "), outcome::toString),
                () -> assertTrue(store.read(OLD_ENTRY).isPresent(), "the record is kept"));
    }

    private static Map<String, Object> withoutTransaction(Map<String, ?> details) {
        var copy = new HashMap<String, Object>(details);
        copy.remove("transaction_id");
        return copy;
    }
}
