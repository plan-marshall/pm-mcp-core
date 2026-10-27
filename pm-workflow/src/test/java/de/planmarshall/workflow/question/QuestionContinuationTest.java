/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.question;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.queue.QueueTaskRecord;
import de.planmarshall.core.queue.QueueTaskRecord.FallbackReason;
import de.planmarshall.core.role.RoleArtifact;
import de.planmarshall.workflow.ast.StateBehavior;

/**
 * The server's choice of the form of a continuation (Model Work § Continuing after a Question): the state, the role
 * artifact and the harness decide it, and the model never does.
 */
@DisplayName("Question continuation forms")
class QuestionContinuationTest {

    private static final Instant AT = Instant.parse("2026-10-27T10:00:00Z");

    private static RoleArtifact.Questions bounds(int hold, int resume) {
        return new RoleArtifact.Questions(hold, resume);
    }

    @Test
    @DisplayName("an ask state of a role that may hold holds, with the hold limit of the role")
    void holds() {
        var decision = QuestionContinuation.decide(StateBehavior.AskForm.ASK, false, bounds(600, 3600), AT);

        assertAll(
                () -> assertEquals(QuestionContinuation.Form.HOLD, decision.form()),
                () -> assertTrue(decision.holds()),
                () -> assertEquals(AT.plusSeconds(600), decision.holdUntil()),
                () -> assertNull(decision.resumeUntil()),
                () -> assertNull(decision.fallbackReason()));
    }

    @Test
    @DisplayName("the hold precedes the resume where the role allows both")
    void holdPrecedesResume() {
        var decision = QuestionContinuation.decide(StateBehavior.AskForm.ASK, false, bounds(600, 3600), AT);

        assertEquals(QuestionContinuation.Form.HOLD, decision.form());
    }

    @Test
    @DisplayName("a role that may only resume suspends the task, with the resume limit of the role")
    void resumes() {
        var decision = QuestionContinuation.decide(StateBehavior.AskForm.ASK, false, bounds(0, 3600), AT);

        assertAll(
                () -> assertEquals(QuestionContinuation.Form.RESUME, decision.form()),
                () -> assertTrue(decision.resumes()),
                () -> assertEquals(AT.plusSeconds(3600), decision.resumeUntil()),
                () -> assertNull(decision.holdUntil()));
    }

    @Test
    @DisplayName("an ask fresh state never continues")
    void askFreshFallsBack() {
        var decision = QuestionContinuation.decide(StateBehavior.AskForm.ASK_FRESH, false, bounds(600, 3600), AT);

        assertAll(
                () -> assertEquals(QuestionContinuation.Form.FALLBACK, decision.form()),
                () -> assertEquals(FallbackReason.NOT_ALLOWED, decision.fallbackReason()));
    }

    @Test
    @DisplayName("an envelope task continues whichever form its state declares")
    void envelopeTaskAlwaysContinues() {
        var decision = QuestionContinuation.decide(StateBehavior.AskForm.ASK_FRESH, true, bounds(600, 3600), AT);

        assertEquals(QuestionContinuation.Form.HOLD, decision.form());
    }

    @Test
    @DisplayName("a role without questions never continues, and so does one whose harness verified no form")
    void noRoleBoundsFallsBack() {
        var withoutQuestions = QuestionContinuation.decide(StateBehavior.AskForm.ASK, true, bounds(0, 0), AT);
        var unverifiedHarness = QuestionContinuation.decide(StateBehavior.AskForm.ASK, false, bounds(0, 0), AT);

        assertAll(
                () -> assertEquals(FallbackReason.NOT_ALLOWED, withoutQuestions.fallbackReason()),
                () -> assertEquals(FallbackReason.NOT_ALLOWED, unverifiedHarness.fallbackReason()));
    }

    @Test
    @DisplayName("after a hold the task suspends where it may resume and falls back otherwise")
    void afterHold() {
        var resumed = QuestionContinuation.afterHold(StateBehavior.AskForm.ASK, false, bounds(600, 3600),
                AT.plusSeconds(600));
        var expired = QuestionContinuation.afterHold(StateBehavior.AskForm.ASK, false, bounds(600, 0),
                AT.plusSeconds(600));

        assertAll(
                () -> assertEquals(QuestionContinuation.Form.RESUME, resumed.form()),
                () -> assertEquals(FallbackReason.HOLD_EXPIRED, expired.fallbackReason()));
    }

    @Test
    @DisplayName("a state without an ask link files no question to continue")
    void withoutAsk() {
        assertThrows(IllegalArgumentException.class,
                () -> QuestionContinuation.decide(StateBehavior.AskForm.NONE, false, bounds(600, 0), AT));
    }

    @Test
    @DisplayName("a fallback records no continuation form")
    void fallbackHasNoRecordForm() {
        var decision = QuestionContinuation.fallback(FallbackReason.RESUME_EXPIRED);

        assertAll(
                () -> assertTrue(decision.recordForm().isEmpty()),
                () -> assertEquals(FallbackReason.RESUME_EXPIRED, decision.fallbackReason()));
    }

    @Test
    @DisplayName("a hold and a resume name their queue-record form, a fallback names none")
    void recordForms() {
        var hold = QuestionContinuation.decide(StateBehavior.AskForm.ASK, false, bounds(600, 0), AT);
        var resume = QuestionContinuation.decide(StateBehavior.AskForm.ASK, false, bounds(0, 3600), AT);

        assertAll(
                () -> assertEquals(QueueTaskRecord.ContinuationForm.HOLD, hold.recordForm().orElseThrow()),
                () -> assertEquals(QueueTaskRecord.ContinuationForm.RESUME, resume.recordForm().orElseThrow()));
    }
}
