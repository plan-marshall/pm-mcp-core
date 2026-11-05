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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.store.JournalScope;
import de.planmarshall.runtime.lock.FileLockManager;
import de.planmarshall.runtime.start.RecoveryStep.Outcome;
import de.planmarshall.runtime.start.RecoveryStepFixtures.RecordingAuditSink;

/**
 * The journal roll-forward step of the per-project recovery over plan scopes in a test base, on the lock manager of
 * the runtime. The reports and reasons the tests compare are the specified outcome texts of the step.
 */
@EnableGeneratorController
@DisplayName("Journal roll-forward step")
class JournalRollForwardStepTest {

    /** Contract literal: the wire name of the audit record of a rolled-forward transaction. */
    private static final String ROLLED_FORWARD = "transaction_rolled_forward";

    @TempDir
    Path base;

    private final RecordingAuditSink auditSink = new RecordingAuditSink();
    private final byte[] content = ("{\"state\":\"" + name() + "\"}").getBytes(StandardCharsets.UTF_8);
    private final String planAId = "plan-" + name();
    private final String planBId = "other-" + name();

    private FileLockManager lockManager;
    private JournalScope planA;
    private JournalScope planB;

    private static String name() {
        return Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }

    @BeforeEach
    void createScopes() {
        lockManager = new FileLockManager(auditSink);
        planA = JournalScope.plan(base, PROJECT_A, planAId);
        planB = JournalScope.plan(base, PROJECT_A, planBId);
    }

    private Outcome run(JournalScope... scopes) {
        return new JournalRollForwardStep(_ -> List.of(scopes), lockManager, auditSink).run(PROJECT_A);
    }

    @Test
    @DisplayName("is the journal roll-forward step")
    void shouldBeJournalRollForwardStep() {
        assertEquals(RecoveryStep.Name.JOURNAL_ROLL_FORWARD,
                new JournalRollForwardStep(JournalRollForwardStep.ProjectScopes.NONE, lockManager, auditSink).name(),
                "the step must carry its specified name");
    }

    @Test
    @DisplayName("an interrupted journal is rolled forward and its record reaches the audit sink")
    void shouldRollForwardInterruptedJournal() throws Exception {
        var journal = RecoveryStepFixtures.interruptedReplace(planA, content);

        var outcome = run(planA);

        var report = assertInstanceOf(Outcome.Done.class, outcome, "the step must be done").report();
        var transactionId = journal.transactionId().value();
        assertAll(
                () -> assertArrayEquals(content, Files.readAllBytes(planA.resolve(STORE)),
                        "the store must hold the rolled-forward content"),
                () -> assertFalse(Files.exists(planA.journalFile()), "journal deleted"),
                () -> assertEquals("plan " + planAId + ": rolled forward " + transactionId + ", applied [" + STORE
                        + "]", report, "the report must name the rolled transaction"),
                () -> assertEquals(List.of(ROLLED_FORWARD), auditSink.eventNames(),
                        "one rolled-forward record must reach the sink"),
                // Contract literal: the wire name of the detail of the record
                () -> assertEquals(transactionId, auditSink.events().getFirst().details().get("transaction_id"),
                        "the record must carry the transaction id"));
    }

    @Test
    @DisplayName("a scope without a journal has nothing to recover")
    void shouldReportNothingToRecover() throws Exception {
        Files.createDirectories(planA.journalFile().getParent());

        var outcome = run(planA);

        assertAll(
                () -> assertEquals(new Outcome.Done("plan " + planAId + ": nothing to recover"), outcome,
                        "the step must report nothing to recover"),
                () -> assertEquals(List.of(), auditSink.eventNames(), "no record must reach the sink"));
    }

    @Test
    @DisplayName("a changed staged file is an invariant violation that leaves journal and store; the next scope is"
            + " still recovered")
    void shouldReportInvariantViolationAndGoOn() throws Exception {
        var broken = RecoveryStepFixtures.interruptedReplace(planA, content);
        var staged = planA.resolve(broken.targets().getFirst().stagedPath());
        var changed = Generators.letterStrings(3, 30).next();
        Files.writeString(staged, changed, StandardCharsets.UTF_8);
        var journalBefore = Files.readAllBytes(planA.journalFile());
        RecoveryStepFixtures.interruptedReplace(planB, content);

        var outcome = run(planA, planB);

        var reason = assertInstanceOf(Outcome.Failed.class, outcome, "a violation must fail the step").reason();
        assertAll(
                () -> assertTrue(reason.startsWith("plan " + planAId + ": invariant violation in [" + STORE + "]"),
                        "the reason must name the violation"),
                () -> assertTrue(reason.contains("plan " + planBId + ": rolled forward"),
                        "the reason must name the recovered scope"),
                () -> assertArrayEquals(journalBefore, Files.readAllBytes(planA.journalFile()), "journal kept"),
                () -> assertFalse(Files.exists(planA.resolve(STORE)), "store of the violating scope untouched"),
                () -> assertEquals(changed, Files.readString(staged), "the changed staged file must be kept"),
                () -> assertArrayEquals(content, Files.readAllBytes(planB.resolve(STORE)),
                        "the next scope must be rolled forward"));
    }

    @Test
    @DisplayName("a refused journal is a failure of its scope and is left in place")
    void shouldReportRefusedJournal() throws Exception {
        Files.createDirectories(planA.journalFile().getParent());
        // Contract literal: a torn journal, cut inside its first field
        Files.writeString(planA.journalFile(), "{\"format_version\":", StandardCharsets.UTF_8);

        var outcome = run(planA);

        var reason = assertInstanceOf(Outcome.Failed.class, outcome, "a refused journal must fail").reason();
        assertAll(
                () -> assertEquals("plan " + planAId + ": store refused (UNREADABLE)", reason,
                        "the reason must name the refusal"),
                () -> assertTrue(Files.exists(planA.journalFile()), "the refused journal must be kept"));
    }

    @Test
    @DisplayName("a project without a journal scope has nothing to roll forward")
    void shouldReportNoScope() {
        assertEquals(new Outcome.Done("no journal scope"), run(), "the step must report no journal scope");
    }
}
