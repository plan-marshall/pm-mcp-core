/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.entry;

import static org.easymock.EasyMock.newCapture;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.spi.PendingEntryReader.EntryRead;
import de.planmarshall.core.spi.PendingEntryReader.ReadRequest;
import de.planmarshall.core.store.EntryId;
import de.planmarshall.core.store.JournalScope;
import de.planmarshall.core.store.PendingEntryCodec;
import de.planmarshall.core.store.PendingEntryRecord;
import de.planmarshall.core.store.PendingEntryRecord.Kind;
import de.planmarshall.core.store.PendingEntryStore;
import de.planmarshall.core.store.ScopeTransaction;
import de.planmarshall.runtime.lock.FileLockManager;
import de.planmarshall.runtime.test.AuditSinkDoubles;

/**
 * The reader of a pending entry in its directory form (PM-ARCH-4): an entry reads live with its kind, an absent one is
 * deleted, an unreadable or mismatching record is unreadable, and a tampered record still reads live because a
 * lock-free read never decides an edit outside a transaction (its Javadoc states the trust assumptions). The project
 * id, the states and the instants are generated; the kinds are the wire contract.
 */
@EnableGeneratorController
@DisplayName("Pending entry directory reader")
class PendingEntryDirectoryReaderTest {

    @TempDir
    Path base;

    private final Instant at = Instant.ofEpochSecond(Generators.longs(1_700_000_000L, 2_000_000_000L).next());
    private final Clock clock = Clock.fixed(at, ZoneOffset.UTC);
    private final String projectId = "project-" + Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    private final EntryId entryId = new EntryId("ent-" + Generators.strings("0123456789abcdef", 16, 16).next());
    private final Capture<AuditEvent> events = newCapture(CaptureType.ALL);
    private final AuditSink auditSink = AuditSinkDoubles.capturing(events);

    private FileLockManager lockManager;
    private JournalScope workspace;
    private PendingEntryStore store;
    private PendingEntryDirectoryReader reader;

    @BeforeEach
    void createWorkspace() throws IOException {
        lockManager = new FileLockManager(auditSink);
        workspace = JournalScope.workspace(base, projectId);
        Files.createDirectories(workspace.journalFile().getParent());
        Files.createDirectories(workspace.resolve(PendingEntryStore.DIRECTORY));
        store = new PendingEntryStore(workspace);
        reader = new PendingEntryDirectoryReader(base, projectId);
    }

    private static PendingEntryRecord record(EntryId id, Kind kind) {
        return PendingEntryRecord.builder().entryId(id).kind(kind).createdAt(Instant.EPOCH)
                .updatedAt(Instant.EPOCH)
                .bootstrap(PendingEntryRecord.Bootstrap.builder().state("start.sources").callStack(List.of())
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

    private void create(PendingEntryRecord entry) {
        inWorkspace(transaction -> store.create(transaction, entry));
    }

    private EntryRead read(EntryId id) {
        return reader.read(new ReadRequest(id));
    }

    @Test
    @DisplayName("an entry reads live with the wire name of its kind")
    void live() {
        create(record(entryId, Kind.PLAN));

        assertEquals(new EntryRead.Live("plan"), read(entryId), "a plan entry should read live with its kind");
    }

    @Test
    @DisplayName("an epic entry reads live with its own kind")
    void liveEpic() {
        create(record(entryId, Kind.EPIC));

        assertEquals(new EntryRead.Live("epic"), read(entryId), "an epic entry should read live with its kind");
    }

    @Test
    @DisplayName("an entry that does not exist is deleted")
    void deleted() {
        assertEquals(new EntryRead.Deleted(), read(entryId), "a missing entry should read deleted");
    }

    @Test
    @DisplayName("the directory a cut-off removal left is deleted, not a live entry")
    void cutOffRemovalDeleted() {
        create(record(entryId, Kind.PLAN));
        inWorkspace(transaction -> store.remove(transaction, entryId));

        assertEquals(new EntryRead.Deleted(), read(entryId),
                "an entry whose record is gone should read deleted");
    }

    @Test
    @DisplayName("a record that does not parse is unreadable, never live")
    void unreadable() throws IOException {
        create(record(entryId, Kind.PLAN));
        Files.writeString(store.store(entryId), "{\"format_version\":1,");

        var read = assertInstanceOf(EntryRead.Unreadable.class, read(entryId), "a torn record should be unreadable");
        assertTrue(read.reason().contains("entry.json"), "the reason should name the record file");
    }

    @Test
    @DisplayName("a record that states another entry_id than its directory is unreadable")
    void mismatchingEntryId() throws IOException {
        var stated = new EntryId("ent-" + Generators.strings("0123456789abcdef", 16, 16).next());
        create(record(entryId, Kind.PLAN));
        Files.write(store.store(entryId), PendingEntryCodec.write(record(stated, Kind.PLAN)));

        var read = assertInstanceOf(EntryRead.Unreadable.class, read(entryId),
                "a record of another id should be unreadable");
        assertAll(
                () -> assertTrue(read.reason().contains(stated.value()), "the reason should name the stated id"),
                () -> assertTrue(read.reason().contains(entryId.value()), "the reason should name the directory"));
    }

    @Test
    @DisplayName("a tampered record still reads live: a lock-free read never decides an edit")
    void tamperedStillLive() throws IOException {
        create(record(entryId, Kind.PLAN));
        Files.write(store.store(entryId), PendingEntryCodec.write(record(entryId, Kind.EPIC).toBuilder()
                .updatedAt(Instant.EPOCH.plusSeconds(1)).build()));

        assertEquals(new EntryRead.Live("epic"), read(entryId),
                "the reader should judge the record's shape, not its digest");
    }

    @Test
    @DisplayName("a project id that is no identifier is refused at construction")
    void projectIdRefused() {
        assertThrows(IllegalArgumentException.class, () -> new PendingEntryDirectoryReader(base, "../../escape"),
                "a project id must be an identifier");
    }
}
