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

import static org.easymock.EasyMock.newCapture;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.store.EntryId;
import de.planmarshall.core.store.JournalScope;
import de.planmarshall.core.store.PendingEntryRecord;
import de.planmarshall.core.store.PendingEntryStore;
import de.planmarshall.core.store.ScopeTransaction;
import de.planmarshall.runtime.lock.FileLockManager;
import de.planmarshall.runtime.test.AuditSinkDoubles;

/**
 * The periodic sweep under a runtime killed with {@code SIGKILL} in the middle of a run (PM-ARCH-7), over the real
 * {@link PendingEntryRecordsSweep} and a real {@code entry.json}: an open entry is an entry whose record exists, not a
 * marker beside a fixture file. The killed runtime is a child JVM of {@link KilledRuntimeFixture}, blocked after the
 * removal transaction committed its stores and while its owed audit record is handed over; the next run, in a new
 * instance in this JVM, completes the workspace journal and removes the directory the killed run left.
 * <p>
 * The project, the instants and the entry ids are generated; the record's state and the audit record's name are the
 * specified contract.
 */
@EnableGeneratorController
@Timeout(value = 120, unit = TimeUnit.SECONDS)
@DisplayName("Periodic sweep with a killed runtime")
class PeriodicSweepKilledRuntimeIT {

    /** Contract literal: the name of the audit record of a removal. */
    private static final String PENDING_ENTRY_REMOVED = "pending_entry_removed";

    private final KilledRuntimeFixture fixture = new KilledRuntimeFixture();

    @TempDir
    Path base;

    private final Instant now = Instant.ofEpochSecond(Generators.longs(1_700_000_000L, 2_000_000_000L).next());
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final Set<EntryId> drawn = new HashSet<>();
    private final EntryId openEntry = entryId();
    private final EntryId residue = entryId();
    private final Capture<AuditEvent> auditEvents = newCapture(CaptureType.ALL);
    private final AuditSink auditSink = AuditSinkDoubles.capturing(auditEvents);

    private FileLockManager lockManager;
    private JournalScope workspace;
    private PendingEntryStore store;

    @BeforeEach
    void createWorkspace() throws IOException {
        lockManager = new FileLockManager(auditSink);
        workspace = JournalScope.workspace(base, RecoveryStepFixtures.PROJECT_A);
        Files.createDirectories(workspace.journalFile().getParent());
        Files.createDirectories(workspace.resolve(PendingEntryStore.DIRECTORY));
        store = new PendingEntryStore(workspace);
    }

    @AfterEach
    void killChild() throws InterruptedException {
        fixture.close();
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
    private PendingEntryRecord entry(EntryId entryId) {
        // Contract literals: the start state of a bootstrap and the empty JSON objects of its facts and options
        return PendingEntryRecord.builder().entryId(entryId).kind(PendingEntryRecord.Kind.PLAN)
                .fromEpic("epic-" + Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT))
                .createdAt(now).updatedAt(now)
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

    /** The next instance's run of the real step, in this JVM. */
    private PeriodicSweep.Summary sweep() {
        return new PeriodicSweep(() -> List.of(RecoveryStepFixtures.PROJECT_A), _ -> true,
                List.of(RuntimeChildProcess.realStep(base, auditSink, lockManager)), Clock.systemUTC())
                .runOnce(now);
    }

    @Test
    @DisplayName("a run killed after its removal transaction is completed by the next run; the open entry is kept")
    void shouldCompleteInterruptedSweep() throws Exception {
        create(entry(openEntry), entry(residue));
        cutOffRemoval(residue);

        fixture.start(RuntimeChildProcess.SWEEP, base);
        fixture.awaitMarker(RuntimeChildProcess.inside(RuntimeChildProcess.SWEEP));
        fixture.kill();

        assertAll(
                () -> assertTrue(Files.exists(store.store(openEntry)), "the open entry's record must be kept"),
                () -> assertFalse(Files.exists(store.store(residue)),
                        "the killed run must remove the residue's stores"),
                () -> assertTrue(Files.exists(store.entryDirectory(residue)),
                        "the killed run must not have removed the residue's directory"),
                () -> assertTrue(Files.exists(workspace.journalFile()),
                        "the killed run must leave its journal for the recovery"));

        var summary = sweep();

        var removals = auditEvents.getValues().stream()
                .filter(event -> PENDING_ENTRY_REMOVED.equals(event.event())).toList();
        assertAll(
                () -> assertFalse(Files.exists(store.entryDirectory(residue)),
                        "the next run must remove the residue's directory"),
                () -> assertTrue(Files.exists(store.store(openEntry)), "the next run must keep the open entry"),
                () -> assertTrue(Files.exists(store.entryDirectory(openEntry)),
                        "the next run must keep the open entry's directory"),
                () -> assertFalse(Files.exists(workspace.journalFile()),
                        "the next run must close the workspace journal"),
                () -> assertEquals(new PeriodicSweep.Summary(1, 0, 0), summary,
                        "the next run must sweep the one project"),
                () -> assertTrue(removals.stream().anyMatch(removal ->
                                residue.value().equals(removal.details().get("entry_id"))
                                        && PendingEntryStore.directoryPath(residue)
                                        .equals(removal.details().get("store"))),
                        "a removal record must name the residue's directory"),
                () -> assertTrue(removals.stream().noneMatch(removal ->
                                removal.details().containsValue(openEntry.value())),
                        "no removal record must name the open entry"));
    }
}
