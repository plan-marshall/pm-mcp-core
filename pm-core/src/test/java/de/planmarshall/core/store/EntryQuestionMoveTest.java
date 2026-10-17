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

import static de.planmarshall.core.store.QuestionRecordCodecTest.answered;
import static de.planmarshall.core.store.QuestionRecordCodecTest.asked;
import static de.planmarshall.core.store.QuestionRecordCodecTest.delivered;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.service.LockTransaction;

/**
 * The question log of a pending entry (PM-WF-12, PM-IMPL-7): {@code pending/<entry_id>/questions.jsonl} belongs to the
 * workspace scope until the entry creates its plan or epic, and is then moved into the new scope, where it is read and
 * appended to, and no file of it stays below {@code pending/<entry_id>/}. The move is two targets of two transactions,
 * the new scope committed first, so that a stop between the commits leaves the entry's log in place and a repeated
 * move is refused instead of writing the lines twice.
 */
@DisplayName("Entry question move")
class EntryQuestionMoveTest {

    private static final String PROJECT = "pm-mcp-core";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-17T09:00:00Z"), ZoneOffset.UTC);
    private static final EntryId ENTRY = new EntryId("ent-0123456789abcdef");

    @TempDir
    Path base;

    private JournalScope workspace;
    private JournalScope plan;
    private QuestionLog entryLog;
    private Path entryDirectory;
    private RecordingLockManager locks;
    private RecordingAuditSink audit;

    @BeforeEach
    void createScopes() throws IOException {
        workspace = JournalScope.workspace(base, PROJECT);
        plan = JournalScope.plan(base, PROJECT, "a-plan");
        Files.createDirectories(workspace.journalFile().getParent());
        Files.createDirectories(plan.journalFile().getParent());
        entryLog = QuestionLog.ofEntry(workspace, ENTRY);
        entryDirectory = entryLog.store().getParent();
        Files.createDirectories(entryDirectory);
        locks = new RecordingLockManager();
        audit = new RecordingAuditSink();
    }

    /** Asks a question in the entry's log and delivers it, through the workspace transaction. */
    private void askInEntry() {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(workspace.lockKey());
            var write = ScopeTransaction.open(transaction, workspace, audit, CLOCK);
            assertEquals(Optional.empty(), entryLog.append(write, List.of(asked("Q-001", "Q-001"),
                    delivered("Q-001", 1))));
            write.commit();
        }
    }

    /** The two transactions of the creation of a scope, under one lock transaction holding both keys. */
    private record Creation(ScopeTransaction workspace, ScopeTransaction target) {
    }

    private Creation open(LockTransaction transaction, JournalScope target) {
        transaction.acquire(workspace.lockKey());
        transaction.acquire(target.lockKey());
        return new Creation(ScopeTransaction.open(transaction, workspace, audit, CLOCK),
                ScopeTransaction.open(transaction, target, audit, CLOCK));
    }

    @Test
    @DisplayName("moves the entry's log into the new plan, where it is read, and leaves no file below the entry")
    void movedIntoPlan() throws IOException {
        askInEntry();
        var entryBytes = Files.readAllBytes(entryLog.store());

        int moved;
        try (var transaction = locks.openTransaction()) {
            var creation = open(transaction, plan);
            moved = QuestionLog.moveEntryQuestions(creation.workspace(), ENTRY, creation.target());
            creation.target().commit();
            creation.workspace().commit();
        }

        var planLog = new QuestionLog(plan);
        try (var files = Files.list(entryDirectory)) {
            var left = files.toList();
            assertAll(
                    () -> assertEquals(2, moved),
                    () -> assertArrayEquals(entryBytes, Files.readAllBytes(planLog.store())),
                    () -> assertEquals(List.of(asked("Q-001", "Q-001"), delivered("Q-001", 1)),
                            planLog.read().records()),
                    () -> assertEquals(List.of(), left, "no file stays below pending/<entry_id>/"),
                    () -> assertEquals(Optional.of(StoreDigest.of(entryBytes)),
                            DigestSidecar.load(plan.sidecarFile()).digest(QuestionLog.FILE_NAME)),
                    () -> assertEquals(Optional.empty(),
                            DigestSidecar.load(workspace.sidecarFile()).digest(entryLog.storePath())));
        }
    }

    @Test
    @DisplayName("lets the new scope go on with the moved questions under its own invariants")
    void appendedInNewScope() {
        askInEntry();
        try (var transaction = locks.openTransaction()) {
            var creation = open(transaction, plan);
            QuestionLog.moveEntryQuestions(creation.workspace(), ENTRY, creation.target());
            creation.target().commit();
            creation.workspace().commit();
        }
        var planLog = new QuestionLog(plan);

        try (var transaction = locks.openTransaction()) {
            transaction.acquire(plan.lockKey());
            var write = ScopeTransaction.open(transaction, plan, audit, CLOCK);
            assertEquals(Optional.empty(), planLog.answer(write, List.of(answered("Q-001", 1))));
            write.commit();
        }

        assertAll(
                () -> assertEquals(Optional.of(answered("Q-001", 1)), planLog.read().current("Q-001")),
                () -> assertTrue(planLog.read().isClosed("Q-001")));
    }

    @Test
    @DisplayName("is refused at a repeated move after a stop between the two commits, and writes the lines once")
    void repeatedMoveAfterStop() throws IOException {
        askInEntry();
        try (var transaction = locks.openTransaction()) {
            var creation = open(transaction, plan);
            QuestionLog.moveEntryQuestions(creation.workspace(), ENTRY, creation.target());
            creation.target().commit();
        }
        var planLog = new QuestionLog(plan);
        var planBytes = Files.readAllBytes(planLog.store());
        assertTrue(Files.exists(entryLog.store()), "a stop before the second commit leaves the entry's log");

        try (var transaction = locks.openTransaction()) {
            var creation = open(transaction, plan);
            QuestionLog.moveEntryQuestions(creation.workspace(), ENTRY, creation.target());
            var target = creation.target();

            assertThrows(IllegalStateException.class, target::commit, "the new log exists and is not created again");

            creation.workspace().commit();
        }

        assertAll(
                () -> assertArrayEquals(planBytes, Files.readAllBytes(planLog.store())),
                () -> assertEquals(2, planLog.read().records().size()),
                () -> assertFalse(Files.exists(entryLog.store())));
    }

    @Test
    @DisplayName("moves the complete lines only, not a torn tail")
    void tornTailNotMoved() throws IOException {
        askInEntry();
        var complete = Files.readAllBytes(entryLog.store());
        Files.write(entryLog.store(), "{\"format_version\":1,".getBytes(UTF_8), StandardOpenOption.APPEND);

        int moved;
        try (var transaction = locks.openTransaction()) {
            var creation = open(transaction, plan);
            moved = QuestionLog.moveEntryQuestions(creation.workspace(), ENTRY, creation.target());
            creation.target().commit();
            creation.workspace().commit();
        }

        assertAll(
                () -> assertEquals(2, moved),
                () -> assertArrayEquals(complete, Files.readAllBytes(new QuestionLog(plan).store())),
                () -> assertFalse(Files.exists(entryLog.store())));
    }

    @Test
    @DisplayName("moves nothing for an entry without a log, and creates no log in the new scope")
    void entryWithoutLog() {
        try (var transaction = locks.openTransaction()) {
            var creation = open(transaction, plan);

            var moved = QuestionLog.moveEntryQuestions(creation.workspace(), ENTRY, creation.target());

            assertAll(
                    () -> assertEquals(0, moved),
                    () -> assertFalse(creation.target().isTarget(new QuestionLog(plan).store())),
                    () -> assertFalse(creation.workspace().isTarget(entryLog.store())));
        }
    }

    @Test
    @DisplayName("removes an entry's log of a torn tail alone without creating a log in the new scope")
    void onlyTornTail() throws IOException {
        Files.write(entryLog.store(), "{\"format_version\":1,".getBytes(UTF_8));
        try (var transaction = locks.openTransaction()) {
            var creation = open(transaction, plan);

            var moved = QuestionLog.moveEntryQuestions(creation.workspace(), ENTRY, creation.target());
            creation.workspace().commit();

            assertAll(
                    () -> assertEquals(0, moved),
                    () -> assertFalse(creation.target().isTarget(new QuestionLog(plan).store())));
        }
        assertFalse(Files.exists(entryLog.store()));
    }

    @Test
    @DisplayName("moves into an epic as into a plan")
    void movedIntoEpic() throws IOException {
        var epic = JournalScope.epic(base, PROJECT, "an-epic");
        Files.createDirectories(epic.pathBase());
        askInEntry();
        var entryBytes = Files.readAllBytes(entryLog.store());

        try (var transaction = locks.openTransaction()) {
            var creation = open(transaction, epic);
            QuestionLog.moveEntryQuestions(creation.workspace(), ENTRY, creation.target());
            creation.target().commit();
            creation.workspace().commit();
        }

        assertArrayEquals(entryBytes, Files.readAllBytes(new QuestionLog(epic).store()));
    }

    @Test
    @DisplayName("is refused for a target that is no plan or epic, and for a workspace transaction of another scope")
    void wrongScopes() {
        askInEntry();
        try (var transaction = locks.openTransaction()) {
            var creation = open(transaction, plan);
            var workspaceTransaction = creation.workspace();
            var planTransaction = creation.target();

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> QuestionLog.moveEntryQuestions(workspaceTransaction, ENTRY, workspaceTransaction)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> QuestionLog.moveEntryQuestions(planTransaction, ENTRY, planTransaction)));
        }
        assertTrue(Files.exists(entryLog.store()));
    }
}
