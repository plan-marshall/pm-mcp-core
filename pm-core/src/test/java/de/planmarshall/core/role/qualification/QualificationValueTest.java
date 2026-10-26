/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.qualification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.role.corpus.ImportResult;
import de.planmarshall.core.role.corpus.ImportedItem;
import de.planmarshall.core.role.qualification.QualificationRecord.ItemResult;
import de.planmarshall.core.role.qualification.QualificationRecord.TopicResult;
import de.planmarshall.core.store.QualificationSettings;

/**
 * The small closed values of the qualification package (Model Work § Qualification Records; § Constant Baseline): the
 * wire names of the enumerations, the token total, the grade, the graded configuration, the offer and the plan, and the
 * guards each value refuses.
 */
@DisplayName("Qualification values")
class QualificationValueTest {

    @Test
    @DisplayName("every wire name round-trips and an unknown one is empty")
    void wireNames() {
        for (var topic : Topic.values()) {
            assertEquals(Optional.of(topic), Topic.fromWireName(topic.wireName()));
        }
        for (var checkout : Checkout.values()) {
            assertEquals(Optional.of(checkout), Checkout.fromWireName(checkout.wireName()));
        }
        for (var consent : GradingConsent.values()) {
            assertEquals(Optional.of(consent), GradingConsent.fromWireName(consent.wireName()));
        }
        for (var label : JudgeCheck.Label.values()) {
            assertEquals(Optional.of(label), JudgeCheck.Label.fromWireName(label.wireName()));
        }
        for (var form : RoleForm.values()) {
            assertEquals(Optional.of(form), RoleForm.fromWireName(form.wireName()));
        }
        assertEquals(Optional.empty(), Topic.fromWireName("none"));
        assertEquals(Optional.empty(), Checkout.fromWireName("none"));
        assertEquals(Optional.empty(), GradingConsent.fromWireName("none"));
        assertEquals(Optional.empty(), JudgeCheck.Label.fromWireName("none"));
        assertEquals(Optional.empty(), RoleForm.fromWireName("none"));
    }

    @Test
    @DisplayName("a token total sums measured counts and answers unmeasured when either is")
    void tokenTotal() {
        assertTrue(TokenTotal.measured(5).measured());
        assertFalse(TokenTotal.unmeasured().measured());
        assertEquals(TokenTotal.measured(15),
                TokenTotal.measured(5).plus(TokenTotal.measured(10)));
        assertEquals(TokenTotal.unmeasured(), TokenTotal.measured(5).plus(TokenTotal.unmeasured()));
        assertThrows(IllegalArgumentException.class, () -> new TokenTotal(0L));
        assertThrows(IllegalArgumentException.class, () -> new TokenTotal(-1L));
    }

    @Test
    @DisplayName("a grade is scored, ungraded, and judged right from 0.5")
    void grade() {
        assertTrue(Grade.of(true).judgedRight());
        assertFalse(Grade.of(false).judgedRight());
        assertFalse(Grade.ungraded().judgedRight());
        assertTrue(Grade.ungraded().isUngraded());
        assertEquals(new BigDecimal("0.6"), Grade.scored(new BigDecimal("0.6")).score());
        assertThrows(IllegalArgumentException.class, () -> new Grade(new BigDecimal("-0.1")));
    }

    @Test
    @DisplayName("the graded configuration lists the members the digest covers")
    void gradedConfiguration() {
        var configuration = GradedConfiguration.of(QualificationFixtures.role("implement", RoleClass.WRITER,
                RoleForm.FRESH));

        assertEquals(QualificationFixtures.HARNESS, configuration.harness());
        assertEquals(QualificationFixtures.VERSION, configuration.harnessVersion());
        assertEquals(RoleClass.WRITER, configuration.roleClass());
        assertEquals(1, configuration.skillDigests().size());
        assertThrows(IllegalArgumentException.class, () -> new GradedConfiguration("Bad Harness", "1.4", "m", null,
                List.of(), RoleClass.WRITER, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new GradedConfiguration("acme", "1.4.2", "m", null,
                List.of(), RoleClass.WRITER, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new GradedConfiguration("acme", "1.4", "", null,
                List.of(), RoleClass.WRITER, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new GradedConfiguration("acme", "1.4", "m", null,
                List.of(), RoleClass.WRITER, Map.of("skill://pm/role/x/SKILL.md", "not-a-digest")));
        assertThrows(IllegalArgumentException.class, () -> new GradedConfiguration("acme", "1.4", "m", null,
                List.of(), RoleClass.WRITER, Map.of("other/x", "a".repeat(64))));
    }

    private static JudgeConfiguration judge() {
        return new JudgeConfiguration(QualificationFixtures.role("judge", RoleClass.ZERO_TOOL, RoleForm.FRESH),
                Set.of("acme-sibling"), List.of(
                        new JudgeCheck.Item("judge/right", "Is it right?", "", "a right answer", "a right answer",
                                JudgeCheck.Label.RIGHT),
                        new JudgeCheck.Item("judge/wrong", "Is it right?", "", "a wrong answer", "a wrong answer",
                                JudgeCheck.Label.WRONG)));
    }

    @Test
    @DisplayName("the plan refuses an empty corpus revision and the offer refuses an empty form list")
    void planAndOffer() {
        var judge = judge();
        assertThrows(IllegalArgumentException.class, () -> new GradingPlan(QualificationFixtures.role("implement",
                        RoleClass.WRITER, RoleForm.FRESH), List.of(), new ImportResult(List.of(), List.of()), "",
                URI.create("https://example.org/x"), GradingProfile.single(), judge, ItemCheckout.none(),
                Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new GradingOffer("implement", List.of(), 0, List.of(),
                1, 0, 0));
        assertEquals(5, GradingProfile.screener().runsPerItem());
        assertTrue(GradingProfile.screener().repeated());
        assertFalse(GradingProfile.single().repeated());
        assertThrows(IllegalArgumentException.class, () -> new GradingProfile(0));
        assertThrows(IllegalArgumentException.class, () -> new GradingOutcome.Declined(false));
        assertThrows(NullPointerException.class, () -> new GradingOutcome.Refused(null, "x"));
    }

    @Test
    @DisplayName("the offer counts the items, the forms and the expected tokens")
    void offer() {
        var judge = judge();
        var plan = new GradingPlan(QualificationFixtures.role("review-code", RoleClass.READ_ONLY, RoleForm.FRESH),
                QualificationFixtures.corpus(), new ImportResult(List.of(), List.of()),
                QualificationFixtures.CORPUS_REVISION, QualificationFixtures.REPOSITORY, GradingProfile.single(),
                judge, ItemCheckout.none(), Optional.empty());

        var offer = GradingOffer.of(plan);

        assertEquals(2, offer.items());
        assertEquals(1, offer.runsPerItem());
        assertEquals(2, offer.judgeItems());
        assertEquals(2 * 4_000L * 2 + 2 * 2L * 2_000L, offer.expectedTokens());
        assertEquals(1, GradingOffer.runs(plan));
    }

    @Test
    @DisplayName("the constant baseline refuses a non-positive denominator and the judge baseline an empty topic")
    void baselineGuards() {
        assertThrows(IllegalArgumentException.class, () -> ConstantBaseline.percent(0, 0));
        assertEquals(BigDecimal.ZERO, JudgeCheck.baseline(List.of()));
    }

    @Test
    @DisplayName("the record values refuse a form without a topic, a topic without items and an item without an id")
    void recordGuards() {
        assertThrows(IllegalArgumentException.class, () -> new QualificationRecord.FormResult(Map.of(), 0,
                TokenTotal.measured(1), 0));
        assertThrows(IllegalArgumentException.class, () -> new TopicResult(new BigDecimal("90"), BigDecimal.ZERO, 0));
        assertThrows(IllegalArgumentException.class, () -> new TopicResult(new BigDecimal("101"), BigDecimal.ZERO, 1));
        assertThrows(IllegalArgumentException.class, () -> new ItemResult("", RoleForm.FRESH, Checkout.CURRENT,
                JsonNode.nullValue(), Grade.of(true), TokenTotal.measured(1), 0L));
    }

    @Test
    @DisplayName("the role topics refuse null and answer whether a topic is required")
    void topicSet() {
        var topics = QualificationFixtures.bothTopics();

        assertTrue(topics.contains(Topic.OWN));
        assertTrue(topics.contains(Topic.GENERIC));
        assertThrows(NullPointerException.class, () -> new RoleTopics(null));
        assertTrue(RoleTopics.counts(QualificationFixtures.item("review/x", "review-code", "clean").build()));
        assertFalse(RoleTopics.counts(QualificationFixtures.item("review/x", "review-code", "clean")
                .status(ImportedItem.ReviewStatus.RETIRED).build()));
    }

    @Test
    @DisplayName("the qualification settings default to 80 and 5")
    void settings() {
        assertEquals(80, QualificationSettings.DEFAULT_AGREEMENT_LEVEL);
        assertEquals(5, QualificationSettings.DEFAULT_ALLOWED_GAP);
        assertEquals(QualificationSettings.defaults(), QualificationFixtures.settings());
    }
}
