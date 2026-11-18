/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.worker;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.getCurrentArguments;
import static org.easymock.EasyMock.replay;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.role.qualification.JudgeScorer;
import de.planmarshall.core.role.qualification.TokenTotal;

/**
 * The judge side of the grading adapter (Model Work § Grading): the judge is a zero-tool worker whose task answers the
 * structured judge-score object, and an answer that is not that object leaves the item unscored instead of aborting the
 * run.
 */
@DisplayName("Worker judge scorer")
class WorkerJudgeScorerTest {

    @TempDir
    Path base;

    private final List<ItemTaskOffers.Task> tasks = new ArrayList<>();
    private SupervisorTestFixture fixture;
    private ItemTaskOffers offers;
    private Optional<ItemTaskOffers.Submitted> submission;

    @BeforeEach
    void setUp() throws IOException {
        fixture = new SupervisorTestFixture(base);
        submission = Optional.of(new ItemTaskOffers.Submitted("{\"score\":0.75}", TokenTotal.measured(90), 30));
        offers = createMock(ItemTaskOffers.class);
        expect(offers.offer(anyObject(ItemTaskOffers.Task.class))).andAnswer(() -> {
            tasks.add((ItemTaskOffers.Task) getCurrentArguments()[0]);
            return submission;
        }).anyTimes();
        replay(offers);
    }

    @Test
    @DisplayName("the structured score of the judge task is answered with its tokens and time")
    void answersTheStructuredScore() {
        var scorer = scorer();

        var score = scorer.score(prompt("right"));

        assertAll(
                () -> assertTrue(score.isPresent(), "the judge answered a score"),
                () -> assertEquals(new BigDecimal("0.75"), score.orElseThrow().score(),
                        "the score is the one the answer states"),
                () -> assertEquals(TokenTotal.measured(90), score.orElseThrow().totalTokens(),
                        "the tokens are the task's"),
                () -> assertEquals(30, score.orElseThrow().durationMs(), "the time is the task's"));
    }

    @Test
    @DisplayName("an answer that is not the judge-score object leaves the item unscored")
    void proseLeavesTheAnswerUnscored() {
        submission = Optional.of(new ItemTaskOffers.Submitted("the answer is right, I think",
                TokenTotal.measured(90), 30));

        var score = scorer().score(prompt("right"));

        assertTrue(score.isEmpty(), "a model's prose is no score, never a failed run");
    }

    @Test
    @DisplayName("a score outside zero to one leaves the item unscored")
    void outOfRangeScoreLeavesTheAnswerUnscored() {
        submission = Optional.of(new ItemTaskOffers.Submitted("{\"score\":2}", TokenTotal.measured(90), 30));

        var score = scorer().score(prompt("right"));

        assertTrue(score.isEmpty(), "a score outside the bounds is no score");
    }

    @Test
    @DisplayName("a declined judge start answers no score and offers no task")
    void declinedStartAnswersNothing() {
        fixture.grant = false;

        var score = scorer().score(prompt("right"));

        assertAll(
                () -> assertTrue(score.isEmpty(), "a declined worker scores nothing"),
                () -> assertTrue(tasks.isEmpty(), "no task is offered without a worker"));
    }

    @Test
    @DisplayName("the fixed prompt withholds the reference of a blind judge task")
    void theBlindPromptWithholdsTheReference() {
        var withReference = WorkerJudgeScorer.prompt(prompt("right"));
        var blind = WorkerJudgeScorer.prompt(new JudgeScorer.JudgePrompt("judge/right-1", "Is it right?",
                "the facts", null, "the answer"));

        assertAll(
                () -> assertTrue(withReference.contains("Reference: right"), "the oracle task carries the reference"),
                () -> assertFalse(blind.contains("Reference:"), "the blind task carries none"),
                () -> assertTrue(blind.contains("Is it right?"), "the blind task keeps the question"),
                () -> assertTrue(blind.contains("the answer"), "the blind task keeps the answer"));
    }

    @Test
    @DisplayName("a judge role that is not zero-tool is refused before any worker starts")
    void aToolUsingJudgeRoleIsRefused() {
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 1);
        var supervisor = fixture.supervisor();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new WorkerJudgeScorer(role, supervisor, offers),
                        "a tool-using role cannot be the judge"),
                () -> assertTrue(fixture.starts.isEmpty(), "no worker starts for it"));
    }

    /**
     * @return the scorer over the fixture's zero-tool judge role
     */
    private WorkerJudgeScorer scorer() {
        return new WorkerJudgeScorer(fixture.role(RoleClass.ZERO_TOOL, RoleForm.FRESH, 2), fixture.supervisor(),
                offers);
    }

    private static JudgeScorer.JudgePrompt prompt(String reference) {
        return new JudgeScorer.JudgePrompt("judge/right-1", "Is it right?", "the facts", reference, "the answer");
    }
}
