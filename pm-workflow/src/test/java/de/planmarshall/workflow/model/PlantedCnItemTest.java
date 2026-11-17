/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.model;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.getCurrentArguments;
import static org.easymock.EasyMock.replay;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.role.corpus.ImportedItem;
import de.planmarshall.core.role.corpus.JsonValue;
import de.planmarshall.core.role.qualification.GradingConsent;
import de.planmarshall.core.role.qualification.GradingOutcome;
import de.planmarshall.core.role.qualification.JudgeScorer;
import de.planmarshall.core.role.qualification.QualificationCheck;
import de.planmarshall.core.role.qualification.RoleTopics;
import de.planmarshall.core.role.qualification.TokenTotal;
import de.planmarshall.core.role.qualification.Topic;
import de.planmarshall.core.store.QualificationSettings;

/**
 * The planted constraint item of the review roles (Model Work § Corpus Topics of a Role): an item whose reference rules out
 * a defect that every criterion and every test accepts, because a constraint {@code CN-<n>} of the intent record forbids
 * it. An answer that grades such a plan clean disagrees with the reference, so the role fails the item and the record
 * qualifies no form.
 */
@DisplayName("Planted constraint item")
class PlantedCnItemTest {

    private static final String CONSTRAINT = "CN-4";

    @TempDir
    Path base;

    @Test
    @DisplayName("a review that grades the plan clean fails the planted item and the role")
    void aCleanAnswerFailsTheRole() {
        var harness = new GraderFixtures.Harness().answers("review/planted-cn", "The plan is clean: no findings.");
        var grader = grader(harness);
        var item = planted();
        var plan = GraderFixtures.plan(GraderFixtures.role(), List.of(item),
                GraderFixtures.judgeConfiguration(GraderFixtures.judge()), GraderFixtures.complete());

        var graded = assertInstanceOf(GradingOutcome.Graded.class,
                grader.grade(plan, GradingConsent.GRANTED), "the grading runs and writes its record");
        var verdict = new QualificationCheck(new RoleTopics(EnumSet.of(Topic.OWN)), List.of(item),
                QualificationSettings.defaults())
                .check(GraderFixtures.ROLE, graded.entry(), GraderFixtures.role().qualificationDigest(),
                        GraderFixtures.CORPUS_REVISION, RoleForm.FRESH);

        assertAll(
                () -> assertEquals(BigDecimal.ZERO,
                        graded.entry().topic(Topic.OWN).orElseThrow().agreement(),
                        "a clean answer agrees with no topic item"),
                () -> assertEquals(BigDecimal.ZERO, graded.entry().items().getFirst().score().score(),
                        "the judge scored the clean answer zero"),
                () -> assertFalse(verdict.qualified(), "the role qualifies no form on a missed constraint"),
                () -> assertEquals(QualificationCheck.Code.BELOW_AGREEMENT_LEVEL,
                        verdict.reasons().getFirst().code(),
                        "the agreement below the level names the failure"));
    }

    @Test
    @DisplayName("a review that names the constraint meets the item")
    void aNamingAnswerMeetsTheItem() {
        var harness = new GraderFixtures.Harness().answers("review/planted-cn",
                "The plan violates " + CONSTRAINT + ": it keeps the default the specification chose.");
        var grader = grader(harness);
        var plan = GraderFixtures.plan(GraderFixtures.role(), List.of(planted()),
                GraderFixtures.judgeConfiguration(GraderFixtures.judge()), GraderFixtures.complete());

        var graded = assertInstanceOf(GradingOutcome.Graded.class,
                grader.grade(plan, GradingConsent.GRANTED), "the grading writes its record");

        assertEquals(BigDecimal.ONE, graded.entry().items().getFirst().score().score(),
                "the named constraint meets the rubric");
    }

    /**
     * @param harness the scripted harness of the graded role
     * @return a grader whose judge scores the answer by whether it names the constraint
     */
    private RoleGrader grader(GraderFixtures.Harness harness) {
        JudgeScorer judge = createMock(JudgeScorer.class);
        expect(judge.score(anyObject(JudgeScorer.JudgePrompt.class))).andAnswer(() -> {
            var prompt = (JudgeScorer.JudgePrompt) getCurrentArguments()[0];
            var score = prompt.answer().contains(CONSTRAINT) ? BigDecimal.ONE : BigDecimal.ZERO;
            return Optional.of(new JudgeScorer.JudgeScore(score, TokenTotal.measured(50), 10));
        }).anyTimes();
        replay(judge);
        return new RoleGrader(harness, judge,
                GraderFixtures.store(base.resolve("projects/pm-mcp-core/roles/qualification")),
                new GraderFixtures.JudgeCheckMemory(), GraderFixtures.CLOCK);
    }

    private static ImportedItem planted() {
        var input = new ImportedItem.Input("Review the plan against its intent record.", "the plan text", null,
                JsonValue.parse("{\"type\":\"string\"}".getBytes(StandardCharsets.UTF_8)));
        var rubric = List.of(new ImportedItem.RubricItem("R1",
                "the answer names the constraint " + CONSTRAINT + " that the intent record rules out"));
        var target = new ImportedItem.Target(null, "the intent record rules the default out", List.of(), rubric,
                false);
        var grading = new ImportedItem.Grading(ImportedItem.GradingMethod.RUBRIC_ITEMS, null, null);
        var provenance = new ImportedItem.Provenance(LocalDate.of(2026, 11, 17),
                new ImportedItem.Origin(URI.create("https://example.org/plan-marshall/pm-mcp-core"),
                        "c".repeat(40), "review/planted-cn.json", 1),
                List.of("planted at the operator's decision of 2026-11-17"));
        var review = new ImportedItem.Review(ImportedItem.ReviewStatus.CONFIRMED, "operator",
                LocalDate.of(2026, 11, 17), "the reference is the intent record's constraint");
        return new ImportedItem("review/planted-cn", "review", GraderFixtures.ROLE,
                "A plan that keeps a default the intent record rules out", List.of(CONSTRAINT), null, input, target,
                grading, provenance, review, null, "canary");
    }
}
