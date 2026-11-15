/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.spi.validation;

import static de.planmarshall.core.spi.validation.ValidationFixtures.criterion;
import static de.planmarshall.core.spi.validation.ValidationFixtures.intent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.spi.validation.SelfReviewSubmission.Accepted;
import de.planmarshall.core.spi.validation.SelfReviewSubmission.ItemResult;
import de.planmarshall.core.spi.validation.SelfReviewSubmission.Refused;
import de.planmarshall.core.spi.validation.SelfReviewSubmission.Status;
import de.planmarshall.core.spi.validation.SelfReviewSubmission.Verdict;
import de.planmarshall.core.store.IntentRecord;
import de.planmarshall.core.store.IntentRecord.CriterionKind;
import de.planmarshall.core.store.IntentRecord.VerifiedBy;

@EnableGeneratorController
@DisplayName("The self-review over the intent's constraints and non-goals")
class SelfReviewSubmissionTest {

    private static final String EVIDENCE = Generators.letterStrings(5, 30).next();

    @DisplayName("A clean round judges every criterion, constraint, non-goal and decision")
    @Test
    void cleanRound() {
        var accepted = assertInstanceOf(Accepted.class, SelfReviewSubmission.validate(clean(), intentRecord()),
                "the clean round over all five items is accepted");
        assertEquals(Status.CLEAN, accepted.round().status(), "the round's status is reported as clean");
        assertTrue(accepted.round().clean(), "the round itself counts as clean");
        assertTrue(accepted.round().findings().isEmpty(), "a clean round raises no finding");
        assertEquals(5, accepted.round().results().size(), "all five items of the record are reviewed");
    }

    @DisplayName("A round that omits an item is refused, naming the item")
    @ParameterizedTest
    @ValueSource(strings = {"CN-1", "NG-1", "DC-1"})
    void refusesOmittedItem(String omitted) {
        var results = new ArrayList<>(clean().results());
        results.removeIf(result -> omitted.equals(result.itemId()));

        var refused = assertInstanceOf(Refused.class, SelfReviewSubmission.validate(
                new SelfReviewSubmission(results), intentRecord()),
                "a round omitting a held item is refused");
        assertTrue(refused.detail().contains(omitted), "the refusal names the omitted item");
    }

    @DisplayName("A respected constraint without evidence is refused")
    @Test
    void refusesRespectedConstraintWithoutEvidence() {
        var results = replace(clean(), new ItemResult("CN-1", Verdict.RESPECTED, null));

        var refused = assertInstanceOf(Refused.class, SelfReviewSubmission.validate(
                new SelfReviewSubmission(results), intentRecord()),
                "a respected constraint without evidence is refused");
        assertTrue(refused.detail().contains("CN-1"), "the refusal names the respected constraint CN-1");
        assertTrue(refused.detail().contains("evidence"), "and says the evidence is missing");
    }

    @DisplayName("A followed decision without evidence is refused")
    @Test
    void refusesFollowedDecisionWithoutEvidence() {
        var results = replace(clean(), new ItemResult("DC-1", Verdict.FOLLOWED, "  "));
        assertInstanceOf(Refused.class, SelfReviewSubmission.validate(new SelfReviewSubmission(results),
                intentRecord()), "a followed decision without evidence is refused");
    }

    @DisplayName("A violated constraint is a finding and the round is not clean")
    @Test
    void violatedConstraintIsAFinding() {
        var results = replace(clean(), new ItemResult("CN-1", Verdict.VIOLATED, null));
        var accepted = assertInstanceOf(Accepted.class,
                SelfReviewSubmission.validate(new SelfReviewSubmission(results), intentRecord()),
                "a violated constraint still yields an accepted round");

        assertEquals(Status.FINDINGS, accepted.round().status(), "the round's status is findings, not clean");
        assertFalse(accepted.round().clean(), "a violated constraint leaves the round unclean");
        assertEquals(List.of("CN-1"), accepted.round().findings().stream().map(ItemResult::itemId).toList(),
                "the finding names exactly the violated CN-1");
    }

    @DisplayName("A crossed non-goal and a departed decision are findings")
    @Test
    void crossedNonGoalAndDepartedDecisionAreFindings() {
        var crossed = replace(clean(), new ItemResult("NG-1", Verdict.CROSSED, null));
        var departed = replace(clean(), new ItemResult("DC-1", Verdict.DEPARTED, null));

        assertEquals(Status.FINDINGS, assertInstanceOf(Accepted.class,
                SelfReviewSubmission.validate(new SelfReviewSubmission(crossed), intentRecord()),
                "a crossed non-goal still yields an accepted round").round().status(),
                "the crossed non-goal makes the round findings");
        assertEquals(Status.FINDINGS, assertInstanceOf(Accepted.class,
                SelfReviewSubmission.validate(new SelfReviewSubmission(departed), intentRecord()),
                "a departed decision still yields an accepted round").round().status(),
                "the departed decision makes the round findings");
    }

    @DisplayName("A not_verified item is a non-blocking finding: the round is clean_unverified")
    @Test
    void notVerifiedIsCleanUnverified() {
        var results = replace(clean(), new ItemResult("CN-1", Verdict.NOT_VERIFIED, null));
        var accepted = assertInstanceOf(Accepted.class,
                SelfReviewSubmission.validate(new SelfReviewSubmission(results), intentRecord()),
                "a not_verified item keeps the round accepted");

        assertEquals(Status.CLEAN_UNVERIFIED, accepted.round().status(),
                "the status is clean_unverified, not findings");
        assertTrue(accepted.round().clean(), "a non-blocking finding leaves the round clean");
        assertEquals(1, accepted.round().unverified().size(), "exactly one item is counted as unverified");
        assertTrue(accepted.round().findings().isEmpty(), "an unverified item is not a finding");
    }

    @DisplayName("A record without constraints is unaffected, and a verdict of another kind is refused")
    @Test
    void recordWithoutConstraintsIsUnaffected() {
        var withoutConstraints = ValidationFixtures.intentWithoutConstraints(
                criterion("AC-1", CriterionKind.NEW, VerifiedBy.TEST),
                criterion("AC-2", CriterionKind.UNCHANGED, VerifiedBy.TEST));
        var results = List.of(
                new ItemResult("AC-1", Verdict.MET, EVIDENCE),
                new ItemResult("AC-2", Verdict.MET, EVIDENCE),
                new ItemResult("NG-1", Verdict.RESPECTED, null),
                new ItemResult("DC-1", Verdict.FOLLOWED, EVIDENCE));
        assertInstanceOf(Accepted.class,
                SelfReviewSubmission.validate(new SelfReviewSubmission(results), withoutConstraints),
                "a record without constraints accepts the review");

        var wrongVerdict = replace(clean(), new ItemResult("AC-1", Verdict.RESPECTED, EVIDENCE));
        assertInstanceOf(Refused.class, SelfReviewSubmission.validate(new SelfReviewSubmission(wrongVerdict),
                intentRecord()), "a verdict of another kind is refused");
    }

    @DisplayName("A result the record does not hold, or a duplicate result, is refused")
    @Test
    void refusesUnknownAndDuplicateResults() {
        var unknown = new ArrayList<>(clean().results());
        unknown.add(new ItemResult("AC-9", Verdict.MET, null));
        assertTrue(assertInstanceOf(Refused.class, SelfReviewSubmission.validate(
                new SelfReviewSubmission(unknown), intentRecord()),
                "a round naming an unknown item is refused").detail().contains("AC-9"),
                "the refusal names the unknown criterion AC-9");

        var duplicate = new ArrayList<>(clean().results());
        duplicate.add(new ItemResult("AC-1", Verdict.MET, null));
        assertInstanceOf(Refused.class, SelfReviewSubmission.validate(new SelfReviewSubmission(duplicate),
                intentRecord()), "a round listing the same item twice is refused");
    }

    @DisplayName("A met criterion without evidence is refused")
    @Test
    void refusesMetCriterionWithoutEvidence() {
        var results = replace(clean(), new ItemResult("AC-1", Verdict.MET, " "));
        assertInstanceOf(Refused.class, SelfReviewSubmission.validate(new SelfReviewSubmission(results),
                intentRecord()), "a met criterion without evidence is refused");
    }

    @DisplayName("A not-met criterion is a finding")
    @Test
    void notMetCriterionIsAFinding() {
        var results = replace(clean(), new ItemResult("AC-1", Verdict.NOT_MET, null));
        assertEquals(Status.FINDINGS, assertInstanceOf(Accepted.class,
                SelfReviewSubmission.validate(new SelfReviewSubmission(results), intentRecord()),
                "a not-met criterion still yields an accepted round").round().status(),
                "the not-met criterion makes the round findings");
    }

    private static SelfReviewSubmission clean() {
        return new SelfReviewSubmission(List.of(
                new ItemResult("AC-1", Verdict.MET, EVIDENCE),
                new ItemResult("AC-2", Verdict.MET, EVIDENCE),
                new ItemResult("CN-1", Verdict.RESPECTED, EVIDENCE),
                new ItemResult("NG-1", Verdict.RESPECTED, null),
                new ItemResult("DC-1", Verdict.FOLLOWED, EVIDENCE)));
    }

    private static List<ItemResult> replace(SelfReviewSubmission submission, ItemResult replacement) {
        return submission.results().stream()
                .map(result -> result.itemId().equals(replacement.itemId()) ? replacement : result)
                .toList();
    }

    private static IntentRecord intentRecord() {
        return intent(criterion("AC-1", CriterionKind.NEW, VerifiedBy.TEST),
                criterion("AC-2", CriterionKind.UNCHANGED, VerifiedBy.TEST));
    }
}
