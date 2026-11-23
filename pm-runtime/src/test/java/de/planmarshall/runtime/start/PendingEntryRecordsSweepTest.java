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

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.getCurrentArguments;
import static org.easymock.EasyMock.newCapture;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.store.EntryId;
import de.planmarshall.core.store.JournalScope;
import de.planmarshall.core.store.PendingEntryCodec;
import de.planmarshall.core.store.PendingEntryCreation;
import de.planmarshall.core.store.PendingEntryRecord;
import de.planmarshall.core.store.PendingEntryStore;
import de.planmarshall.core.store.ScopeTransaction;
import de.planmarshall.core.store.SourceManifest;
import de.planmarshall.core.store.StoreDigest;
import de.planmarshall.runtime.lock.FileLockManager;
import de.planmarshall.runtime.start.RecoveryStep.Outcome;
import de.planmarshall.runtime.test.AuditSinkDoubles;

/**
 * The pending-entry step of the periodic sweep over the workspace scope of a project in a test base, on the lock
 * manager of the runtime: an entry whose record exists is kept however old it is, the directory of an entry whose
 * removal a stop cut off — or of a creation that never committed — is removed in a workspace transaction with its audit
 * record, a name that is no entry id is never touched, and what cannot be done is reported as a failure. The reports,
 * the audit record name and its detail keys are the specified contract; the project, the instants and the entry ids are
 * generated.
 */
@EnableGeneratorController
@DisplayName("Pending entry records sweep")
class PendingEntryRecordsSweepTest {

    /** Contract literal: the name of the audit record of a removal. */
    private static final String PENDING_ENTRY_REMOVED = "pending_entry_removed";

    @TempDir
    Path base;

    private final Instant now = Instant.ofEpochSecond(Generators.longs(1_700_000_000L, 2_000_000_000L).next());
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final String projectId = "project-" + Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    private final Set<EntryId> drawn = new HashSet<>();
    private final EntryId openEntry = entryId();
    private final EntryId residue = entryId();
    private final Capture<AuditEvent> auditEvents = newCapture(CaptureType.ALL);
    private final AuditSink auditSink = AuditSinkDoubles.capturing(auditEvents);

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

    /** A record of a plan the server starts for an epic row. */
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

    /** Removes the stores of an entry in a transaction of its own and leaves its directory: a cut-off removal. */
    private void cutOffRemoval(EntryId entryId) {
        inWorkspace(transaction -> store.remove(transaction, entryId));
    }

    private Path writeFile(String name, String content) throws IOException {
        return Files.writeString(store.directoryPath().resolve(name), content, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("is the pending-entry step of the periodic sweep")
    void name() {
        assertEquals(SweepStep.Name.PENDING_ENTRY_RECORDS, sweep.name(),
                "the step must be the pending-entry step");
    }

    @Test
    @DisplayName("an entry whose record exists is kept, however old it is: nothing expires")
    void openEntryKept() {
        var veryOld = now.minusSeconds(Generators.longs(86_400, 10_000_000L).next());
        create(entry(openEntry, veryOld));
        var before = auditEvents.getValues().size();

        var outcome = sweep.run(projectId, now);

        assertAll(
                () -> assertEquals(new Outcome.Done("removed 0 pending entry directories"), outcome,
                        "the step must report nothing removed"),
                () -> assertTrue(store.read(openEntry).isPresent(), "the open entry must be kept"),
                () -> assertTrue(Files.isDirectory(store.entryDirectory(openEntry)),
                        "the open entry's directory must be kept"),
                () -> assertEquals(before, auditEvents.getValues().size(), "no audit record must be owed"));
    }

    @Test
    @DisplayName("the directory of an entry whose removal a stop cut off is removed with one audit record")
    void cutOffRemovalSwept() {
        create(entry(residue, now));
        cutOffRemoval(residue);
        var before = auditEvents.getValues().size();

        var outcome = sweep.run(projectId, now);

        var removals = auditEvents.getValues().subList(before, auditEvents.getValues().size());
        assertAll(
                () -> assertEquals(new Outcome.Done("removed 1 pending entry directories"), outcome,
                        "the step must report one removed directory"),
                () -> assertFalse(Files.exists(store.entryDirectory(residue)), "the residue directory must be gone"),
                () -> assertEquals(1, removals.size(), "one audit record must be owed by the removal"),
                () -> assertEquals(PENDING_ENTRY_REMOVED, removals.getFirst().event(),
                        "the audit record must be pending_entry_removed"),
                // Contract literals: the detail keys of pending_entry_removed and the directory prefix
                () -> assertEquals(Map.of("entry_id", residue.value(), "store", "pending/" + residue.value(),
                                "removed_at", now.toString()), withoutTransaction(removals.getFirst().details()),
                        "the audit record must name the directory and the run instant"),
                () -> assertTrue(removals.getFirst().details().containsKey("transaction_id"),
                        "the audit record must carry a transaction_id"),
                () -> assertFalse(Files.exists(workspace.journalFile()), "the workspace journal is closed"));
    }

    @Test
    @DisplayName("two residue directories removed in one run owe two records with two distinct transaction ids")
    void removalsCarryDistinctTransactionIds() {
        var second = entryId();
        create(entry(residue, now), entry(second, now));
        cutOffRemoval(residue);
        cutOffRemoval(second);
        var before = auditEvents.getValues().size();

        var outcome = sweep.run(projectId, now);

        var removals = auditEvents.getValues().subList(before, auditEvents.getValues().size());
        var transactionIds = removals.stream().map(removal -> removal.details().get("transaction_id")).toList();
        assertAll(
                () -> assertEquals(new Outcome.Done("removed 2 pending entry directories"), outcome,
                        "the step must report two removed directories"),
                () -> assertEquals(List.of(PENDING_ENTRY_REMOVED, PENDING_ENTRY_REMOVED),
                        removals.stream().map(AuditEvent::event).toList(), "two removal records must be owed"),
                () -> assertEquals(2, transactionIds.size(), "each removal record carries a transaction id"),
                () -> assertTrue(transactionIds.stream().allMatch(String.class::isInstance),
                        "every transaction id must be a string"),
                () -> assertEquals(2, Set.copyOf(transactionIds).size(),
                        "the two transaction ids must be distinct"),
                () -> assertFalse(Files.exists(store.entryDirectory(residue)), "the first directory must be gone"),
                () -> assertFalse(Files.exists(store.entryDirectory(second)), "the second directory must be gone"));
    }

    @Test
    @DisplayName("the directory of a creation cut after both its commits is removed by the evidence of its absent record")
    void creationCutAfterBothCommits() throws Exception {
        create(entry(residue, now));
        var plan = JournalScope.plan(base, projectId, "plan-" + Generators.letterStrings(3, 12).next()
                .toLowerCase(Locale.ROOT));
        Files.createDirectories(plan.journalFile().getParent());
        var creation = new PendingEntryCreation(workspace);
        try (var lock = lockManager.openTransaction()) {
            lock.acquire(workspace.lockKey());
            lock.acquire(plan.lockKey());
            var workspaceWrite = ScopeTransaction.open(lock, workspace, auditSink, clock);
            var target = ScopeTransaction.open(lock, plan, auditSink, clock);
            creation.move(workspaceWrite, target, residue);
            target.commit();
            workspaceWrite.commit();
        }
        var before = auditEvents.getValues().size();

        var outcome = sweep.run(projectId, now);

        var removals = auditEvents.getValues().subList(before, auditEvents.getValues().size());
        assertAll(
                () -> assertEquals(Optional.empty(), store.read(residue), "the move must delete the record"),
                () -> assertEquals(new Outcome.Done("removed 1 pending entry directories"), outcome,
                        "the sweep must remove the cut creation's directory"),
                () -> assertFalse(Files.exists(store.entryDirectory(residue)),
                        "the directory of the cut creation must be gone"),
                () -> assertTrue(Files.exists(plan.pathBase().resolve("request.md")),
                        "the created scope must stay"),
                () -> assertEquals(1, removals.size(), "one audit record must be owed for the removal"));
    }

    @Test
    @DisplayName("a populated creation cut after both commits keeps its sources and transcript, and is swept")
    void populatedCreationCutAfterBothCommits() throws Exception {
        var plan = JournalScope.plan(base, projectId, "plan-" + Generators.letterStrings(3, 12).next()
                .toLowerCase(Locale.ROOT));
        Files.createDirectories(plan.journalFile().getParent());
        var description = Generators.letterStrings(10, 40).next();
        var content = description.getBytes(StandardCharsets.UTF_8);
        var sourceId = "source_" + Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
        var manifest = new SourceManifest(1, List.of(new SourceManifest.Source(sourceId,
                SourceManifest.Kind.DESCRIPTION, "the operator", "operator", SourceManifest.Trust.TRUSTED,
                List.of(new SourceManifest.SourceFile("description.md", content.length, StoreDigest.of(content).sha256())),
                now, null, null)));
        create(entry(residue, now));
        inWorkspace(transaction -> store.replaceSources(transaction, residue, manifest));
        Files.write(store.sourcesDirectory(residue).resolve("description.md"), content);
        var transcript = Generators.letterStrings(10, 40).next();
        Files.writeString(store.chatDirectory(residue).resolve("session.md"), transcript, StandardCharsets.UTF_8);
        var creation = new PendingEntryCreation(workspace);
        try (var lock = lockManager.openTransaction()) {
            lock.acquire(workspace.lockKey());
            lock.acquire(plan.lockKey());
            var workspaceWrite = ScopeTransaction.open(lock, workspace, auditSink, clock);
            var target = ScopeTransaction.open(lock, plan, auditSink, clock);
            creation.move(workspaceWrite, target, residue);
            target.commit();
            workspaceWrite.commit();
        }
        var before = auditEvents.getValues().size();

        var outcome = sweep.run(projectId, now);

        var removals = auditEvents.getValues().subList(before, auditEvents.getValues().size());
        assertAll(
                () -> assertEquals(description,
                        Files.readString(plan.pathBase().resolve("sources/description.md")),
                        "the created scope must keep the carried source"),
                () -> assertEquals(transcript, Files.readString(plan.pathBase().resolve("chat/session.md")),
                        "the created scope must keep the carried transcript"),
                () -> assertEquals(new Outcome.Done("removed 1 pending entry directories"), outcome,
                        "the sweep must remove the populated cut creation's directory"),
                () -> assertFalse(Files.exists(store.entryDirectory(residue)),
                        "the populated directory of the cut creation must be gone"),
                () -> assertEquals(1, removals.size(),
                        "one audit record must be owed for the populated removal"));
    }

    @Test
    @DisplayName("a name whose directory is no entry id is never touched, however old")
    void unparsableNamesUntouched() throws Exception {
        // Fixture paths: a name without the prefix, one with upper-case digits and one with too few digits, each a
        // file and a directory, so the filter is proved before a recursive delete
        var notes = writeFile("notes.json", "{}");
        var upper = writeFile("ent-00000000000000AB.json", "{}");
        var tooShort = writeFile("ent-1.json", "{}");
        var notesDirectory = Files.createDirectories(store.directoryPath().resolve("notes"));
        var upperDirectory = Files.createDirectories(store.directoryPath().resolve("ent-00000000000000AC"));
        var tooShortDirectory = Files.createDirectories(store.directoryPath().resolve("ent-2"));
        Files.writeString(notesDirectory.resolve("child.md"), "{}");
        Files.writeString(upperDirectory.resolve("child.md"), "{}");
        Files.writeString(tooShortDirectory.resolve("child.md"), "{}");

        var outcome = sweep.run(projectId, now);

        assertAll(
                () -> assertEquals(new Outcome.Done("removed 0 pending entry directories"), outcome,
                        "the step must report nothing removed"),
                () -> assertTrue(Files.exists(notes), "a name without the prefix is never touched"),
                () -> assertTrue(Files.exists(upper), "a name with upper-case digits is never touched"),
                () -> assertTrue(Files.exists(tooShort), "a name with too few digits is never touched"),
                () -> assertTrue(Files.exists(notesDirectory.resolve("child.md")),
                        "a foreign directory keeps its child"),
                () -> assertTrue(Files.exists(upperDirectory.resolve("child.md")),
                        "an upper-case directory keeps its child"),
                () -> assertTrue(Files.exists(tooShortDirectory.resolve("child.md")),
                        "a short-named directory keeps its child"),
                () -> assertEquals(List.of(), AuditSinkDoubles.eventNames(auditEvents),
                        "no audit record must be handed over"));
    }

    @Test
    @DisplayName("a project without pending entries has nothing to remove")
    void noEntries() {
        var otherProject = "other-" + Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);

        var outcome = new PendingEntryRecordsSweep(base, lockManager, auditSink, clock).run(otherProject, now);

        assertEquals(new Outcome.Done("removed 0 pending entry directories"), outcome,
                "a project without entries has nothing to remove");
    }

    @Test
    @DisplayName("an unreadable record is kept and reported as a failure, after the readable residue was removed")
    void unreadableKeptAsFailure() throws Exception {
        create(entry(residue, now), entry(openEntry, now));
        cutOffRemoval(residue);
        var broken = entryId();
        Files.createDirectories(store.entryDirectory(broken));
        // Fixture: a truncated record that does not parse
        Files.writeString(store.entryDirectory(broken).resolve(PendingEntryStore.RECORD), "{\"format_version\":1,");

        var outcome = sweep.run(projectId, now);

        var reason = assertInstanceOf(Outcome.Failed.class, outcome, "an unreadable record fails the step").reason();
        assertAll(
                () -> assertTrue(reason.startsWith("removed 1 pending entry directories; kept unreadable entries"),
                        "the report must count the removed directory"),
                () -> assertTrue(reason.contains(broken.value() + " (unreadable)"),
                        "the report must name the unreadable record"),
                () -> assertFalse(Files.exists(store.entryDirectory(residue)), "the readable residue is removed"),
                () -> assertTrue(Files.exists(store.store(broken)), "the unreadable record is kept"));
    }

    @Test
    @DisplayName("an unreadable entry and a held entry are both reported, and the readable residue is removed")
    void unreadableAndHeldBothReported() throws Exception {
        create(entry(residue, now));
        cutOffRemoval(residue);
        var broken = entryId();
        Files.createDirectories(store.entryDirectory(broken));
        // Fixture: a truncated record that does not parse
        Files.writeString(store.entryDirectory(broken).resolve(PendingEntryStore.RECORD), "{\"format_version\":1,");
        var linked = entryId();
        create(entry(linked, now));
        cutOffRemoval(linked);
        var sources = store.sourcesDirectory(linked);
        var elsewhere = Files.createDirectory(base.resolve("elsewhere"));
        Files.delete(sources);
        Files.createSymbolicLink(sources, elsewhere);

        var outcome = sweep.run(projectId, now);

        var reason = assertInstanceOf(Outcome.Failed.class, outcome, "two failed entries fail the step").reason();
        assertAll(
                () -> assertTrue(reason.contains("kept unreadable entries"),
                        "the report must name the unreadable entries"),
                () -> assertTrue(reason.contains(broken.value()), "the report must name the unreadable record"),
                () -> assertTrue(reason.contains("kept or failed entries"),
                        "the report must name the held entry too"),
                () -> assertTrue(reason.contains(linked.value() + " (held: "),
                        "the report must name the held entry"),
                () -> assertFalse(Files.exists(store.entryDirectory(residue)), "the readable residue is removed"));
    }

    @Test
    @DisplayName("a directory the discard cannot remove is reported, and the removed count is kept in the report")
    void discardRefusedAfterCommitKeepsTheCount() {
        create(entry(residue, now));
        cutOffRemoval(residue);
        var recreated = new AtomicBoolean();
        AuditSink sink = createMock(AuditSink.class);
        sink.append(anyObject(AuditEvent.class));
        expectLastCall().andAnswer(() -> {
            var event = (AuditEvent) getCurrentArguments()[0];
            if (PENDING_ENTRY_REMOVED.equals(event.event()) && recreated.compareAndSet(false, true)) {
                Files.write(store.store(residue), PendingEntryCodec.write(entry(residue, now)));
            }
            return null;
        }).anyTimes();
        replay(sink);
        var own = new PendingEntryRecordsSweep(base, new FileLockManager(sink), sink, clock);

        var outcome = own.run(projectId, now);

        var reason = assertInstanceOf(Outcome.Failed.class, outcome, "a refused discard fails the step").reason();
        assertAll(
                () -> assertTrue(reason.startsWith("removed 1 pending entry directories; kept or failed entries:"),
                        "the report must keep the removed count"),
                () -> assertTrue(reason.contains(residue.value()), "the report must name the kept directory"),
                () -> assertTrue(Files.exists(store.entryDirectory(residue)), "the directory must still be there"));
        verify(sink);
    }

    @Test
    @DisplayName("a refusal that is no symbolic-link refusal holds no entry: one link holds one entry alone")
    void secondCauseIsNoHeldEntry() {
        var link = new JournalScope.SymbolicLinkRefusal("Store path 'x' passes through a symbolic link below base");
        var other = new IllegalArgumentException("the recovery of the workspace broke");

        assertAll(
                () -> assertTrue(PendingEntryRecordsSweep.holdsEntry(link),
                        "the link refusal should hold its entry"),
                () -> assertFalse(PendingEntryRecordsSweep.holdsEntry(other),
                        "another refusal should be no held entry"));
    }

    @Test
    @DisplayName("a record deleted outside a transaction is tampered: the sweep keeps the entry and reports it")
    void tamperedRecordKept() throws Exception {
        create(entry(openEntry, now));
        Files.delete(store.store(openEntry));
        var before = auditEvents.getValues().size();

        var outcome = sweep.run(projectId, now);

        var reason = assertInstanceOf(Outcome.Failed.class, outcome, "a tampered entry fails the step").reason();
        assertAll(
                () -> assertTrue(reason.contains(openEntry.value() + " (tampered)"),
                        "the report must name the kept entry"),
                () -> assertTrue(Files.exists(store.entryDirectory(openEntry)),
                        "the tampered entry's directory is kept"),
                () -> assertEquals(before, auditEvents.getValues().size(), "no removal record is owed"));
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    @DisplayName("a store path through a symbolic link holds that entry, and the readable residue is still removed")
    void symlinkedStoreHeld() throws Exception {
        create(entry(residue, now));
        cutOffRemoval(residue);
        var linked = entryId();
        create(entry(linked, now));
        cutOffRemoval(linked);
        var sources = store.sourcesDirectory(linked);
        var elsewhere = Files.createDirectory(base.resolve("elsewhere"));
        Files.delete(sources);
        Files.createSymbolicLink(sources, elsewhere);

        var outcome = sweep.run(projectId, now);

        var reason = assertInstanceOf(Outcome.Failed.class, outcome, "a linked store path fails the step").reason();
        assertAll(
                () -> assertTrue(reason.startsWith("removed 1 pending entry directories; kept or failed entries:"),
                        "the report must count the removed directory"),
                () -> assertTrue(reason.contains(linked.value() + " (held: "),
                        "the report must name the held entry"),
                () -> assertFalse(Files.exists(store.entryDirectory(residue)), "the readable residue is removed"),
                () -> assertTrue(Files.exists(store.entryDirectory(linked)), "the linked entry is kept"),
                () -> assertTrue(Files.isSymbolicLink(sources), "the link is left as it is"));
    }

    @Test
    @DisplayName("a record that states another entry_id than its directory is kept and reported, and the residue "
            + "is still removed")
    void mismatchingEntryIdKeptAsFailure() throws Exception {
        create(entry(residue, now));
        cutOffRemoval(residue);
        var named = entryId();
        var stated = entryId();
        Files.createDirectories(store.entryDirectory(named));
        var mismatching = Files.write(store.store(named), PendingEntryCodec.write(entry(stated, now)));

        var outcome = sweep.run(projectId, now);

        var reason = assertInstanceOf(Outcome.Failed.class, outcome, "a mismatching record fails the step").reason();
        assertAll(
                () -> assertTrue(reason.startsWith("removed 1 pending entry directories; kept unreadable entries"),
                        "the report must count the removed directory"),
                () -> assertTrue(reason.contains(named.value() + " (unreadable)"),
                        "the report must name the directory of the record"),
                () -> assertFalse(reason.contains(stated.value()), "the report must not name the stated id"),
                () -> assertFalse(Files.exists(store.entryDirectory(residue)), "the residue is still removed"),
                () -> assertTrue(Files.exists(mismatching), "the mismatching record is kept"));
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    @DisplayName("a record that cannot be read at all is kept and reported, and the residue is still removed")
    void ioFailureKeptAsFailure() throws Exception {
        create(entry(residue, now), entry(openEntry, now));
        cutOffRemoval(residue);
        var locked = entryId();
        Files.createDirectories(store.entryDirectory(locked));
        var file = Files.writeString(store.store(locked), "{}");
        Files.setPosixFilePermissions(file, Set.of());
        assumeFalse(Files.isReadable(file), "a file without permissions is unreadable for this user");

        var outcome = sweep.run(projectId, now);

        var reason = assertInstanceOf(Outcome.Failed.class, outcome, "a record not readable fails the step").reason();
        assertAll(
                () -> assertTrue(reason.startsWith("removed 1 pending entry directories; kept unreadable entries"),
                        "the report must count the removed directory"),
                () -> assertTrue(reason.contains(locked.value() + " (io: "),
                        "the report must name the I/O failure of the record"),
                () -> assertFalse(Files.exists(store.entryDirectory(residue)), "the readable residue is removed"),
                () -> assertTrue(Files.exists(file), "the record that cannot be read is kept"));
    }

    @Test
    @DisplayName("a workspace journal still to recover is reported as a failure, and nothing is removed")
    void journalToRecoverFails() throws Exception {
        create(entry(residue, now));
        cutOffRemoval(residue);
        Files.writeString(workspace.journalFile(), "{}", StandardCharsets.UTF_8);

        var outcome = sweep.run(projectId, now);

        assertAll(
                () -> assertTrue(assertInstanceOf(Outcome.Failed.class, outcome,
                                "a journal to recover fails the step").reason()
                                .startsWith("pending entry directories not swept: "),
                        "the report must say that nothing was swept"),
                () -> assertTrue(Files.exists(store.entryDirectory(residue)),
                        "the residue directory must stay in the store"));
    }

    private static Map<String, Object> withoutTransaction(Map<String, ?> details) {
        var copy = new HashMap<String, Object>(details);
        copy.remove("transaction_id");
        return copy;
    }
}
