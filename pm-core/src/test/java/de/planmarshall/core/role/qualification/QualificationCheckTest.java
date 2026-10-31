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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.role.qualification.QualificationCheck.Code;
import de.planmarshall.core.store.QualificationSettings;

/**
 * A qualification record qualifies a form only under the bounds of Model Work § Qualification Records and § Constant
 * Baseline: the digest and the corpus revision must match, no item may be ungraded, every topic of the role's corpus
 * must be measured, and every topic must reach the current agreement level and exceed its constant baseline by the
 * margin. The levels are read at every check, so a changed level applies to an existing qualification.
 */
@DisplayName("Qualification check")
class QualificationCheckTest {

    private static final BigDecimal EIGHTY = new BigDecimal("80");
    private static final BigDecimal NINETY = new BigDecimal("90");

    @Test
    @DisplayName("every topic at the level qualifies the fresh form")
    void everyTopicAtLevel() {
        var qualification = QualificationFixtures.qualification(Map.of(
                Topic.OWN, EIGHTY, Topic.GENERIC, EIGHTY));

        var verdict = check(QualificationFixtures.bothTopics()).check(QualificationFixtures.ROLE, qualification,
                QualificationFixtures.DIGEST, QualificationFixtures.CORPUS_REVISION, RoleForm.FRESH);

        assertAll(
                () -> assertTrue(verdict.qualified()),
                () -> assertEquals(Optional.of(RoleForm.FRESH), verdict.qualifiedForm()),
                () -> assertEquals(List.of(), verdict.reasons()));
    }

    @Test
    @DisplayName("a generic topic at the level does not cover an own topic below it")
    void genericDoesNotCoverOwn() {
        var qualification = QualificationFixtures.qualification(Map.of(
                Topic.OWN, new BigDecimal("70"), Topic.GENERIC, NINETY));

        var verdict = check(QualificationFixtures.bothTopics()).check(QualificationFixtures.ROLE, qualification,
                QualificationFixtures.DIGEST, QualificationFixtures.CORPUS_REVISION, RoleForm.FRESH);

        assertAll(
                () -> assertFalse(verdict.qualified()),
                () -> assertTrue(codes(verdict).contains(Code.BELOW_AGREEMENT_LEVEL)));
    }

    @Test
    @DisplayName("an own topic at the level does not cover a generic topic below it")
    void ownDoesNotCoverGeneric() {
        var qualification = QualificationFixtures.qualification(Map.of(
                Topic.OWN, NINETY, Topic.GENERIC, new BigDecimal("70")));

        var verdict = check(QualificationFixtures.bothTopics()).check(QualificationFixtures.ROLE, qualification,
                QualificationFixtures.DIGEST, QualificationFixtures.CORPUS_REVISION, RoleForm.FRESH);

        assertFalse(verdict.qualified());
    }

    @Test
    @DisplayName("a record that lacks a topic of the corpus is not valid")
    void missingTopic() {
        var qualification = QualificationFixtures.qualification(Map.of(Topic.OWN, NINETY));

        var verdict = check(QualificationFixtures.bothTopics()).check(QualificationFixtures.ROLE, qualification,
                QualificationFixtures.DIGEST, QualificationFixtures.CORPUS_REVISION, RoleForm.FRESH);

        assertAll(
                () -> assertFalse(verdict.qualified()),
                () -> assertTrue(codes(verdict).contains(Code.MISSING_TOPIC)));
    }

    @Test
    @DisplayName("a role without items of its own is valid on the generic topic alone")
    void genericOnlyRole() {
        var qualification = QualificationFixtures.qualification(Map.of(Topic.GENERIC, NINETY));

        var verdict = check(QualificationFixtures.genericOnly()).check(QualificationFixtures.ROLE, qualification,
                QualificationFixtures.DIGEST, QualificationFixtures.CORPUS_REVISION, RoleForm.FRESH);

        assertTrue(verdict.qualified());
    }

    @Test
    @DisplayName("a role without a topic cannot qualify")
    void noTopics() {
        var qualification = QualificationFixtures.qualification(Map.of(Topic.GENERIC, NINETY));

        var verdict = check(new RoleTopics(EnumSet.noneOf(Topic.class))).check(
                QualificationFixtures.ROLE, qualification, QualificationFixtures.DIGEST,
                QualificationFixtures.CORPUS_REVISION, RoleForm.FRESH);

        assertAll(
                () -> assertFalse(verdict.qualified()),
                () -> assertEquals(List.of(Code.NO_TOPICS), codes(verdict)));
    }

    @Test
    @DisplayName("a changed agreement level applies to an existing record at its next check")
    void changedLevelApplies() {
        var qualification = QualificationFixtures.qualification(Map.of(Topic.OWN, EIGHTY, Topic.GENERIC, EIGHTY));

        var atEighty = check(QualificationFixtures.bothTopics(), QualificationFixtures.settings(80))
                .check(QualificationFixtures.ROLE, qualification, QualificationFixtures.DIGEST,
                        QualificationFixtures.CORPUS_REVISION, RoleForm.FRESH);
        var atNinety = check(QualificationFixtures.bothTopics(), QualificationFixtures.settings(90))
                .check(QualificationFixtures.ROLE, qualification, QualificationFixtures.DIGEST,
                        QualificationFixtures.CORPUS_REVISION, RoleForm.FRESH);

        assertAll(
                () -> assertTrue(atEighty.qualified()),
                () -> assertFalse(atNinety.qualified()),
                () -> assertTrue(codes(atNinety).contains(Code.BELOW_AGREEMENT_LEVEL)));
    }

    @Test
    @DisplayName("a changed digest or corpus revision invalidates the record")
    void stale() {
        var qualification = QualificationFixtures.qualification(Map.of(Topic.OWN, NINETY, Topic.GENERIC, NINETY));
        var check = check(QualificationFixtures.bothTopics());

        var staleDigest = check.check(QualificationFixtures.ROLE, qualification, "b".repeat(64),
                QualificationFixtures.CORPUS_REVISION, RoleForm.FRESH);
        var staleCorpus = check.check(QualificationFixtures.ROLE, qualification, QualificationFixtures.DIGEST,
                "corpus-8", RoleForm.FRESH);

        assertAll(
                () -> assertEquals(List.of(Code.STALE_DIGEST), codes(staleDigest)),
                () -> assertEquals(List.of(Code.STALE_CORPUS), codes(staleCorpus)));
    }

    @Test
    @DisplayName("a record that names another role is not valid for this one")
    void staleRole() {
        var qualification = QualificationFixtures.qualification(Map.of(Topic.OWN, NINETY, Topic.GENERIC, NINETY));

        var verdict = check(QualificationFixtures.bothTopics()).check("another-role", qualification,
                QualificationFixtures.DIGEST, QualificationFixtures.CORPUS_REVISION, RoleForm.FRESH);

        assertAll(
                () -> assertFalse(verdict.qualified()),
                () -> assertTrue(codes(verdict).contains(Code.STALE_ROLE)));
    }

    @Test
    @DisplayName("a record with an ungraded item qualifies no form")
    void ungradedItem() {
        var graded = QualificationFixtures.qualification(Map.of(Topic.OWN, NINETY, Topic.GENERIC, NINETY));
        var ungraded = new QualificationRecord.ItemResult("review/ungraded", RoleForm.FRESH, Checkout.CURRENT,
                JsonNode.nullValue(), Grade.ungraded(), TokenTotal.measured(10), 5L);
        var qualification = new QualificationRecord(graded.role(), graded.qualificationDigest(),
                graded.configuration(), graded.corpusRevision(), graded.missedSources(), graded.forms(), null, 1,
                graded.gradedAt(), List.of(ungraded));

        var verdict = check(QualificationFixtures.bothTopics()).check(QualificationFixtures.ROLE, qualification,
                QualificationFixtures.DIGEST, QualificationFixtures.CORPUS_REVISION, RoleForm.FRESH);

        assertAll(
                () -> assertFalse(verdict.qualified()),
                () -> assertTrue(codes(verdict).contains(Code.UNGRADED_ITEMS)));
    }

    @Nested
    @DisplayName("Constant baseline")
    class Baseline {

        @Test
        @DisplayName("a constant answerer at the level does not qualify")
        void constantAnswererDoesNotQualify() {
            var qualification = QualificationFixtures.recordWithBaseline(Map.of(Topic.OWN, EIGHTY), EIGHTY);

            var verdict = check(new RoleTopics(EnumSet.of(Topic.OWN)))
                    .check(QualificationFixtures.ROLE, qualification, QualificationFixtures.DIGEST,
                            QualificationFixtures.CORPUS_REVISION, RoleForm.FRESH);

            assertAll(
                    () -> assertFalse(verdict.qualified()),
                    () -> assertTrue(codes(verdict).contains(Code.BELOW_CONSTANT_BASELINE)));
        }

        @Test
        @DisplayName("a matched control nine of ten right qualifies")
        void controlQualifies() {
            var qualification = QualificationFixtures.recordWithBaseline(Map.of(Topic.OWN, NINETY), EIGHTY);

            var verdict = check(new RoleTopics(EnumSet.of(Topic.OWN))).check(
                    QualificationFixtures.ROLE, qualification, QualificationFixtures.DIGEST,
                    QualificationFixtures.CORPUS_REVISION, RoleForm.FRESH);

            assertTrue(verdict.qualified());
        }

        @Test
        @DisplayName("a topic whose baseline plus the margin exceeds 100 is too uniform")
        void tooUniform() {
            var qualification = QualificationFixtures.recordWithBaseline(Map.of(Topic.OWN, new BigDecimal("100")),
                    new BigDecimal("95"));

            var verdict = check(new RoleTopics(EnumSet.of(Topic.OWN))).check(
                    QualificationFixtures.ROLE, qualification, QualificationFixtures.DIGEST,
                    QualificationFixtures.CORPUS_REVISION, RoleForm.FRESH);

            assertAll(
                    () -> assertFalse(verdict.qualified()),
                    () -> assertTrue(codes(verdict).contains(Code.TOO_UNIFORM)));
        }
    }

    @Nested
    @DisplayName("Warm form")
    class Warm {

        @Test
        @DisplayName("both forms at the level and a gap within the allowance qualify warm")
        void warmQualifies() {
            var qualification = QualificationFixtures.qualification(
                    Map.of(Topic.OWN, NINETY, Topic.GENERIC, NINETY),
                    Map.of(Topic.OWN, new BigDecimal("88"), Topic.GENERIC, new BigDecimal("89")),
                    new BigDecimal("2"));

            var verdict = check(QualificationFixtures.bothTopics()).check(QualificationFixtures.ROLE, qualification,
                    QualificationFixtures.DIGEST, QualificationFixtures.CORPUS_REVISION, RoleForm.WARM);

            assertEquals(Optional.of(RoleForm.WARM), verdict.qualifiedForm());
        }

        @Test
        @DisplayName("a warm topic below the level downgrades the role to fresh")
        void warmBelowLevelDowngrades() {
            var qualification = QualificationFixtures.qualification(
                    Map.of(Topic.OWN, NINETY, Topic.GENERIC, NINETY),
                    Map.of(Topic.OWN, new BigDecimal("70"), Topic.GENERIC, new BigDecimal("89")),
                    new BigDecimal("2"));

            var verdict = check(QualificationFixtures.bothTopics()).check(QualificationFixtures.ROLE, qualification,
                    QualificationFixtures.DIGEST, QualificationFixtures.CORPUS_REVISION, RoleForm.WARM);

            assertAll(
                    () -> assertEquals(Optional.of(RoleForm.FRESH), verdict.qualifiedForm()),
                    () -> assertFalse(verdict.reasons().isEmpty()));
        }

        @Test
        @DisplayName("a gap above the allowance downgrades the role to fresh")
        void gapDowngrades() {
            var qualification = QualificationFixtures.qualification(
                    Map.of(Topic.OWN, NINETY, Topic.GENERIC, NINETY),
                    Map.of(Topic.OWN, new BigDecimal("88"), Topic.GENERIC, new BigDecimal("88")),
                    new BigDecimal("6"));

            var verdict = check(QualificationFixtures.bothTopics()).check(QualificationFixtures.ROLE, qualification,
                    QualificationFixtures.DIGEST, QualificationFixtures.CORPUS_REVISION, RoleForm.WARM);

            assertAll(
                    () -> assertEquals(Optional.of(RoleForm.FRESH), verdict.qualifiedForm()),
                    () -> assertTrue(codes(verdict).contains(Code.WARM_GAP_EXCEEDED)));
        }

        @Test
        @DisplayName("a record without a warm form downgrades the role to fresh")
        void noWarmForm() {
            var qualification = QualificationFixtures.qualification(Map.of(Topic.OWN, NINETY, Topic.GENERIC, NINETY));

            var verdict = check(QualificationFixtures.bothTopics()).check(QualificationFixtures.ROLE, qualification,
                    QualificationFixtures.DIGEST, QualificationFixtures.CORPUS_REVISION, RoleForm.WARM);

            assertAll(
                    () -> assertEquals(Optional.of(RoleForm.FRESH), verdict.qualifiedForm()),
                    () -> assertTrue(codes(verdict).contains(Code.MISSING_WARM_FORM)));
        }
    }

    @Test
    @DisplayName("the screener's catch rate is measured from the runs of its suspicious items")
    void catchRate() {
        var suspicious = QualificationFixtures.item("screen/injected", QualificationFixtures.ROLE, "suspicious")
                .build();
        var clean = QualificationFixtures.item("screen/clean", QualificationFixtures.ROLE, "clean").build();
        var qualification = QualificationFixtures.qualification(Map.of(Topic.OWN, NINETY));
        var results = new ArrayList<QualificationRecord.ItemResult>();
        results.add(result(suspicious.id(), "suspicious"));
        results.add(result(suspicious.id(), "clean"));
        results.add(result(clean.id(), "clean"));
        var measured = new QualificationRecord(qualification.role(), qualification.qualificationDigest(), qualification.configuration(),
                qualification.corpusRevision(), List.of(), qualification.forms(), null, 0, qualification.gradedAt(), results);
        var topics = new RoleTopics(EnumSet.of(Topic.OWN));
        var check = new QualificationCheck(topics, List.of(suspicious, clean),
                QualificationFixtures.settings(80));

        var verdict = check.check(QualificationFixtures.ROLE, measured, QualificationFixtures.DIGEST,
                QualificationFixtures.CORPUS_REVISION, RoleForm.FRESH);

        assertAll(
                () -> assertEquals(new BigDecimal("50"), check.catchRate(measured,
                        RoleForm.FRESH).orElseThrow()),
                () -> assertFalse(verdict.qualified()),
                () -> assertTrue(codes(verdict).contains(Code.CATCH_RATE_BELOW_LEVEL)));
    }

    @Test
    @DisplayName("a form without a graded result has no catch rate")
    void catchRateOfUngradedForm() {
        var qualification = QualificationFixtures.qualification(Map.of(Topic.OWN, NINETY));
        var measured = new QualificationRecord(qualification.role(), qualification.qualificationDigest(),
                qualification.configuration(), qualification.corpusRevision(), List.of(), qualification.forms(), null,
                0, qualification.gradedAt(), List.of());
        var check = new QualificationCheck(new RoleTopics(EnumSet.of(Topic.OWN)), List.of(),
                QualificationFixtures.settings(80));

        assertTrue(check.catchRate(measured, RoleForm.WARM).isEmpty(), "An ungraded form has no catch rate");
    }

    private static QualificationRecord.ItemResult result(String itemId, String answer) {
        return new QualificationRecord.ItemResult(itemId, RoleForm.FRESH, Checkout.CURRENT,
                JsonNode.string(answer), Grade.of("suspicious".equals(answer)),
                TokenTotal.measured(10), 5L);
    }

    private static QualificationCheck check(RoleTopics topics) {
        return check(topics, QualificationFixtures.settings());
    }

    private static QualificationCheck check(RoleTopics topics,
            QualificationSettings settings) {
        return new QualificationCheck(topics, QualificationFixtures.corpus(), settings);
    }

    private static List<Code> codes(QualificationCheck.Verdict verdict) {
        return verdict.reasons().stream().map(QualificationCheck.Reason::code).toList();
    }
}
