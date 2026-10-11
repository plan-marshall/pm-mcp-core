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
import static de.planmarshall.runtime.start.RecoveryStepFixtures.STORE;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;


import de.planmarshall.core.store.JournalScope;
import de.planmarshall.runtime.lock.FileLockManager;
import de.planmarshall.runtime.start.RecoveryStep.Outcome;
import de.planmarshall.runtime.start.RecoveryStepFixtures.RecordingAuditSink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The journal roll-forward step of the per-project recovery over plan scopes in a test base, on the lock manager of
 * the runtime.
 */
@DisplayName("Journal roll-forward step")
class JournalRollForwardStepTest {

    private static final byte[] CONTENT = "{\"state\":\"done\"}".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path base;

    private final RecordingAuditSink auditSink = new RecordingAuditSink();

    private FileLockManager lockManager;
    private JournalScope planA;
    private JournalScope planB;

    @BeforeEach
    void createScopes() {
        lockManager = new FileLockManager(auditSink);
        planA = JournalScope.plan(base, PROJECT_A, "plan-a");
        planB = JournalScope.plan(base, PROJECT_A, "plan-b");
    }

    private Outcome run(JournalScope... scopes) {
        return new JournalRollForwardStep(_ -> List.of(scopes), lockManager, auditSink).run(PROJECT_A);
    }

    @Test
    @DisplayName("is the journal roll-forward step")
    void shouldBeJournalRollForwardStep() {
        assertEquals(RecoveryStep.Name.JOURNAL_ROLL_FORWARD,
                new JournalRollForwardStep(JournalRollForwardStep.ProjectScopes.NONE, lockManager, auditSink).name());
    }

    @Test
    @DisplayName("an interrupted journal is rolled forward and its record reaches the audit sink")
    void shouldRollForwardInterruptedJournal() throws IOException {
        var journal = RecoveryStepFixtures.interruptedReplace(planA, CONTENT);

        var outcome = run(planA);

        var report = assertInstanceOf(Outcome.Done.class, outcome).report();
        var transactionId = journal.transactionId().value();
        assertAll(
                () -> assertArrayEquals(CONTENT, Files.readAllBytes(planA.resolve(STORE))),
                () -> assertFalse(Files.exists(planA.journalFile()), "journal deleted"),
                () -> assertEquals("plan plan-a: rolled forward " + transactionId + ", applied [" + STORE + "]",
                        report),
                () -> assertEquals(List.of("transaction_rolled_forward"), auditSink.eventNames()),
                () -> assertEquals(transactionId, auditSink.events().getFirst().details().get("transaction_id")));
    }

    @Test
    @DisplayName("a scope without a journal has nothing to recover")
    void shouldReportNothingToRecover() throws IOException {
        Files.createDirectories(planA.journalFile().getParent());

        var outcome = run(planA);

        assertAll(
                () -> assertEquals(new Outcome.Done("plan plan-a: nothing to recover"), outcome),
                () -> assertEquals(List.of(), auditSink.eventNames()));
    }

    @Test
    @DisplayName("a changed staged file is an invariant violation that leaves journal and store; the next scope is"
            + " still recovered")
    void shouldReportInvariantViolationAndGoOn() throws IOException {
        var broken = RecoveryStepFixtures.interruptedReplace(planA, CONTENT);
        var staged = planA.resolve(broken.targets().getFirst().stagedPath());
        Files.writeString(staged, "changed", StandardCharsets.UTF_8);
        var journalBefore = Files.readAllBytes(planA.journalFile());
        RecoveryStepFixtures.interruptedReplace(planB, CONTENT);

        var outcome = run(planA, planB);

        var reason = assertInstanceOf(Outcome.Failed.class, outcome).reason();
        assertAll(
                () -> assertTrue(reason.startsWith("plan plan-a: invariant violation in [" + STORE + "]"), reason),
                () -> assertTrue(reason.contains("plan plan-b: rolled forward"), reason),
                () -> assertArrayEquals(journalBefore, Files.readAllBytes(planA.journalFile()), "journal kept"),
                () -> assertFalse(Files.exists(planA.resolve(STORE)), "store of the violating scope untouched"),
                () -> assertEquals("changed", Files.readString(staged)),
                () -> assertArrayEquals(CONTENT, Files.readAllBytes(planB.resolve(STORE))));
    }

    @Test
    @DisplayName("a refused journal is a failure of its scope and is left in place")
    void shouldReportRefusedJournal() throws IOException {
        Files.createDirectories(planA.journalFile().getParent());
        Files.writeString(planA.journalFile(), "{\"format_version\":", StandardCharsets.UTF_8);

        var outcome = run(planA);

        var reason = assertInstanceOf(Outcome.Failed.class, outcome).reason();
        assertAll(
                () -> assertEquals("plan plan-a: store refused (UNREADABLE)", reason),
                () -> assertTrue(Files.exists(planA.journalFile())));
    }

    @Test
    @DisplayName("a project without a journal scope has nothing to roll forward")
    void shouldReportNoScope() {
        assertEquals(new Outcome.Done("no journal scope"), run());
    }
}
