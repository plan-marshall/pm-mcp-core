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

@DisplayName("The self-review over the intent's constraints and non-goals")
class SelfReviewSubmissionTest {

    @DisplayName("A clean round judges every criterion, constraint, non-goal and decision")
    @Test
    void cleanRound() {
        var accepted = assertInstanceOf(Accepted.class, SelfReviewSubmission.validate(clean(), intentRecord()));
        assertEquals(Status.CLEAN, accepted.round().status());
        assertTrue(accepted.round().clean());
        assertTrue(accepted.round().findings().isEmpty());
        assertEquals(5, accepted.round().results().size());
    }

    @DisplayName("A round that omits an item is refused, naming the item")
    @ParameterizedTest
    @ValueSource(strings = {"CN-1", "NG-1", "DC-1"})
    void refusesOmittedItem(String omitted) {
        var results = new ArrayList<>(clean().results());
        results.removeIf(result -> omitted.equals(result.itemId()));

        var refused = assertInstanceOf(Refused.class, SelfReviewSubmission.validate(
                new SelfReviewSubmission(results), intentRecord()));
        assertTrue(refused.detail().contains(omitted));
    }

    @DisplayName("A respected constraint without evidence is refused")
    @Test
    void refusesRespectedConstraintWithoutEvidence() {
        var results = replace(clean(), new ItemResult("CN-1", Verdict.RESPECTED, null));

        var refused = assertInstanceOf(Refused.class, SelfReviewSubmission.validate(
                new SelfReviewSubmission(results), intentRecord()));
        assertTrue(refused.detail().contains("CN-1"));
        assertTrue(refused.detail().contains("evidence"));
    }

    @DisplayName("A followed decision without evidence is refused")
    @Test
    void refusesFollowedDecisionWithoutEvidence() {
        var results = replace(clean(), new ItemResult("DC-1", Verdict.FOLLOWED, "  "));
        assertInstanceOf(Refused.class, SelfReviewSubmission.validate(new SelfReviewSubmission(results), intentRecord()));
    }

    @DisplayName("A violated constraint is a finding and the round is not clean")
    @Test
    void violatedConstraintIsAFinding() {
        var results = replace(clean(), new ItemResult("CN-1", Verdict.VIOLATED, null));
        var accepted = assertInstanceOf(Accepted.class,
                SelfReviewSubmission.validate(new SelfReviewSubmission(results), intentRecord()));

        assertEquals(Status.FINDINGS, accepted.round().status());
        assertFalse(accepted.round().clean());
        assertEquals(List.of("CN-1"), accepted.round().findings().stream().map(ItemResult::itemId).toList());
    }

    @DisplayName("A crossed non-goal and a departed decision are findings")
    @Test
    void crossedNonGoalAndDepartedDecisionAreFindings() {
        var crossed = replace(clean(), new ItemResult("NG-1", Verdict.CROSSED, null));
        var departed = replace(clean(), new ItemResult("DC-1", Verdict.DEPARTED, null));

        assertEquals(Status.FINDINGS, assertInstanceOf(Accepted.class,
                SelfReviewSubmission.validate(new SelfReviewSubmission(crossed), intentRecord())).round().status());
        assertEquals(Status.FINDINGS, assertInstanceOf(Accepted.class,
                SelfReviewSubmission.validate(new SelfReviewSubmission(departed), intentRecord())).round().status());
    }

    @DisplayName("A not_verified item is a non-blocking finding: the round is clean_unverified")
    @Test
    void notVerifiedIsCleanUnverified() {
        var results = replace(clean(), new ItemResult("CN-1", Verdict.NOT_VERIFIED, null));
        var accepted = assertInstanceOf(Accepted.class,
                SelfReviewSubmission.validate(new SelfReviewSubmission(results), intentRecord()));

        assertEquals(Status.CLEAN_UNVERIFIED, accepted.round().status());
        assertTrue(accepted.round().clean());
        assertEquals(1, accepted.round().unverified().size());
        assertTrue(accepted.round().findings().isEmpty());
    }

    @DisplayName("A record without constraints is unaffected, and a verdict of another kind is refused")
    @Test
    void recordWithoutConstraintsIsUnaffected() {
        var withoutConstraints = ValidationFixtures.intentWithoutConstraints(
                criterion("AC-1", CriterionKind.NEW, VerifiedBy.TEST),
                criterion("AC-2", CriterionKind.UNCHANGED, VerifiedBy.TEST));
        var results = List.of(
                new ItemResult("AC-1", Verdict.MET, null),
                new ItemResult("AC-2", Verdict.MET, null),
                new ItemResult("NG-1", Verdict.RESPECTED, null),
                new ItemResult("DC-1", Verdict.FOLLOWED, "The record is one file beside request.md"));
        assertInstanceOf(Accepted.class,
                SelfReviewSubmission.validate(new SelfReviewSubmission(results), withoutConstraints));

        var wrongVerdict = replace(clean(), new ItemResult("AC-1", Verdict.RESPECTED, "a review"));
        assertInstanceOf(Refused.class, SelfReviewSubmission.validate(new SelfReviewSubmission(wrongVerdict), intentRecord()));
    }

    @DisplayName("A result the record does not hold, or a duplicate result, is refused")
    @Test
    void refusesUnknownAndDuplicateResults() {
        var unknown = new ArrayList<>(clean().results());
        unknown.add(new ItemResult("AC-9", Verdict.MET, null));
        assertTrue(assertInstanceOf(Refused.class, SelfReviewSubmission.validate(
                new SelfReviewSubmission(unknown), intentRecord())).detail().contains("AC-9"));

        var duplicate = new ArrayList<>(clean().results());
        duplicate.add(new ItemResult("AC-1", Verdict.MET, null));
        assertInstanceOf(Refused.class, SelfReviewSubmission.validate(new SelfReviewSubmission(duplicate), intentRecord()));
    }

    @DisplayName("A not-met criterion is a finding")
    @Test
    void notMetCriterionIsAFinding() {
        var results = replace(clean(), new ItemResult("AC-1", Verdict.NOT_MET, null));
        assertEquals(Status.FINDINGS, assertInstanceOf(Accepted.class,
                SelfReviewSubmission.validate(new SelfReviewSubmission(results), intentRecord())).round().status());
    }

    private static SelfReviewSubmission clean() {
        return new SelfReviewSubmission(List.of(
                new ItemResult("AC-1", Verdict.MET, null),
                new ItemResult("AC-2", Verdict.MET, null),
                new ItemResult("CN-1", Verdict.RESPECTED, "The store writes once, under the plan lock"),
                new ItemResult("NG-1", Verdict.RESPECTED, null),
                new ItemResult("DC-1", Verdict.FOLLOWED, "The record is one file beside request.md")));
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
