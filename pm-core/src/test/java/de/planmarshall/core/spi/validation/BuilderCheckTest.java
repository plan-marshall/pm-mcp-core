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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.spi.validation.BuilderCheck.Accepted;
import de.planmarshall.core.spi.validation.BuilderCheck.Question;
import de.planmarshall.core.spi.validation.BuilderCheck.Refused;
import de.planmarshall.core.spi.validation.BuilderCheck.Sort;
import de.planmarshall.core.spi.validation.BuilderCheck.Submission;
import de.planmarshall.core.store.CapabilityIndex;
import de.planmarshall.core.store.IntentRecord;
import de.planmarshall.core.store.IntentRecord.Constraint;
import de.planmarshall.core.store.IntentRecord.ConstraintSource;
import de.planmarshall.core.store.IntentRecord.Criterion;
import de.planmarshall.core.store.IntentRecord.CriterionKind;
import de.planmarshall.core.store.IntentRecord.Decider;
import de.planmarshall.core.store.IntentRecord.Decision;
import de.planmarshall.core.store.IntentRecord.NonGoal;
import de.planmarshall.core.store.IntentRecord.SourceClaim;
import de.planmarshall.core.store.IntentRecord.VerifiedBy;

@DisplayName("The builder check's submission (PM-WATCH-PHASE-109)")
class BuilderCheckTest {

    @DisplayName("A question about a constraint is always the operator's")
    @Test
    void constraintQuestionIsTheOperators() {
        var question = new Question(Sort.OPERATOR,
                "The record's default permits what 'no item is lost' rules out: the fallback drops items and the "
                        + "criterion accepts it. Is dropping an item acceptable?",
                List.of("CN-1", "AC-1"), null);
        var accepted = assertInstanceOf(Accepted.class,
                BuilderCheck.validate(new Submission(List.of(question)), intentRecord()));
        assertEquals(List.of(question), accepted.operatorQuestions());
    }

    @DisplayName("A minor question that names a constraint is refused")
    @Test
    void minorQuestionAboutConstraintIsRefused() {
        var question = new Question(Sort.MINOR, "The constraint is fine as the default", List.of("CN-1"),
                "Keep the default");
        var refused = assertInstanceOf(Refused.class,
                BuilderCheck.validate(new Submission(List.of(question)), intentRecord()));
        assertTrue(refused.detail().contains("CN-1"));
    }

    @DisplayName("A minor question that names two criteria is refused")
    @Test
    void minorQuestionAboutTwoCriteriaIsRefused() {
        var question = new Question(Sort.MINOR, "Both criteria hold", List.of("AC-1", "AC-2"),
                "Keep both criteria");
        assertInstanceOf(Refused.class, BuilderCheck.validate(new Submission(List.of(question)), intentRecord()));
    }

    @DisplayName("The builder's own call becomes a model decision of the record")
    @Test
    void minorCallBecomesModelDecision() {
        var question = new Question(Sort.MINOR, "The record does not say where the file lives",
                List.of("AC-1"), "Place the file beside request.md");
        var accepted = assertInstanceOf(Accepted.class,
                BuilderCheck.validate(new Submission(List.of(question)), intentRecord()));
        var decision = accepted.intentRecord().decisions().getLast();
        assertEquals("DC-2", decision.id());
        assertEquals(Decider.MODEL, decision.decider());
        assertTrue(decision.text().startsWith("from the builder check:"));
        assertFalse(accepted.operatorQuestions().contains(question));
    }

    @DisplayName("An answered question names exactly one item the record holds")
    @Test
    void answeredQuestionNamesOneHeldItem() {
        var answered = new Question(Sort.ANSWERED, "The goal says it", List.of("AC-1"), null);
        assertInstanceOf(Accepted.class, BuilderCheck.validate(new Submission(List.of(answered)), intentRecord()));

        var unknown = new Question(Sort.ANSWERED, "The goal says it", List.of("AC-9"), null);
        assertTrue(assertInstanceOf(Refused.class,
                BuilderCheck.validate(new Submission(List.of(unknown)), intentRecord())).detail().contains("AC-9"));

        var two = new Question(Sort.ANSWERED, "The goal says it", List.of("AC-1", "AC-2"), null);
        assertInstanceOf(Refused.class, BuilderCheck.validate(new Submission(List.of(two)), intentRecord()));
    }

    @DisplayName("The facts carry the record and the capability index only")
    @Test
    void factsCarryTheRecordAndTheCapabilityIndex(@TempDir Path root) {
        var facts = BuilderCheck.facts(intentRecord(), CapabilityIndex.read(root));
        assertEquals(List.of("capability_index", "intent_record"), facts.keySet().stream().sorted().toList());
        assertInstanceOf(IntentRecord.class, facts.get("intent_record"));
        assertEquals(List.of(), facts.get("capability_index"));
        assertFalse(facts.containsKey("request"));
        assertFalse(facts.containsKey("original_input"));
        assertFalse(facts.containsKey("clarifications"));
    }

    @DisplayName("A minor decision too long for the record is refused, not thrown")
    @Test
    void refusesOverlongMinorDecision() {
        var question = new Question(Sort.MINOR, "Where does it live?", List.of("AC-1"), "x".repeat(401));
        var refused = assertInstanceOf(Refused.class,
                BuilderCheck.validate(new Submission(List.of(question)), intentRecord()));
        assertTrue(refused.detail().contains("longer"));
    }

    @DisplayName("A question's text is bounded")
    @Test
    void questionIsBounded() {
        assertThrows(IllegalArgumentException.class,
                () -> new Question(Sort.OPERATOR, "no", List.of(), null));
        assertThrows(IllegalArgumentException.class,
                () -> new Question(Sort.MINOR, "Where does it live?", List.of(), "no"));
    }

    @DisplayName("The facts are an immutable map")
    @Test
    void factsAreImmutable(@TempDir Path root) {
        var facts = BuilderCheck.facts(intentRecord(), CapabilityIndex.read(root));
        assertThrows(UnsupportedOperationException.class,
                () -> facts.put("request", Map.of()));
    }

    private static IntentRecord intentRecord() {
        return new IntentRecord(
                "The operator cannot see what a plan intends before it is built.",
                "A plan states its intent in one record that every later phase reads.",
                List.of(new Criterion("AC-1", "WHEN a plan is refined THE system SHALL write the intent record",
                                CriterionKind.NEW, VerifiedBy.TEST, null),
                        new Criterion("AC-2",
                                "WHEN a plan is refined THE system SHALL CONTINUE TO keep the previous record intact",
                                CriterionKind.UNCHANGED, VerifiedBy.TEST, null)),
                null,
                List.of(new NonGoal("NG-1", "The record is not edited by a later phase.")),
                List.of(new Constraint("CN-1", "A record is never accepted half-written.", ConstraintSource.OPERATOR,
                        null)),
                List.of(new Decision("DC-1", "The record is one file beside request.md", List.of("a store", "one file"),
                        Decider.MODEL, null, null, null)),
                List.of(), List.of(), List.of(),
                List.of(new SourceClaim("The operator wants the intent visible", "goal", null),
                        new SourceClaim("A half-written record would mislead the phases", "CN-1", null)));
    }
}
