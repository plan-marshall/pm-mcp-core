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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.role.EffectiveRole;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.store.StoreRefusal;

/**
 * The judge is checked as an instrument before it grades a rubric item (Model Work § Judge Instrument Check): a judge
 * that scores every answer right sits at the constant baseline and fails, a judge whose blind run agrees with its oracle
 * follows its own opinion and fails, and a judge that follows the reference passes. A lost judge task leaves the answer
 * unscored and the check without a verdict; a changed judge configuration or corpus revision leaves no valid checkRecord.
 */
@DisplayName("Judge instrument check")
class JudgeCheckTest {

    private static final Instant NOW = Instant.parse("2026-10-26T10:00:00Z");
    private static final String CORPUS = "corpus-7";
    private static final String RIGHT_ANSWER = "a right answer";
    private static final String WRONG_ANSWER = "another wrong answer";

    @TempDir
    Path directory;

    @Test
    @DisplayName("a balanced topic has a constant baseline of 50")
    void baseline() {
        assertEquals(new BigDecimal("50"), JudgeCheck.baseline(items()));
    }

    @Test
    @DisplayName("a judge that follows the reference passes")
    void followingJudgePasses() {
        var runner = new Runner(JudgeCheckTest::followsReference);

        var checkRecord = runner.run();

        assertEquals(new BigDecimal("100"), checkRecord.oracleAccuracy());
        assertEquals(new BigDecimal("50"), checkRecord.blindAccuracy());
        assertEquals(new BigDecimal("50"), checkRecord.constantBaseline());
        assertEquals(0, checkRecord.unscored());
        assertTrue(JudgeCheck.Verdict.of(checkRecord, digestOf(JudgeCheckTest::judgeRole), CORPUS).passes());
    }

    @Test
    @DisplayName("a judge that scores every answer right sits at the baseline and fails")
    void constantJudgeFails() {
        var runner = new Runner(prompt -> Optional.of(score(BigDecimal.ONE)));

        var checkRecord = runner.run();
        var verdict = JudgeCheck.Verdict.of(checkRecord, digestOf(JudgeCheckTest::judgeRole), CORPUS);

        assertEquals(new BigDecimal("50"), checkRecord.oracleAccuracy());
        assertEquals(new BigDecimal("50"), checkRecord.blindAccuracy());
        assertFalse(verdict.passes());
        assertTrue(verdict.reasons().contains("oracle_not_above_baseline"));
    }

    @Test
    @DisplayName("a judge whose blind run agrees with its oracle fails")
    void blindAgreementFails() {
        var runner = new Runner(prompt -> Optional.of(score(RIGHT_ANSWER.equals(prompt.answer())
                ? BigDecimal.ONE
                : BigDecimal.ZERO)));

        var checkRecord = runner.run();
        var verdict = JudgeCheck.Verdict.of(checkRecord, digestOf(JudgeCheckTest::judgeRole), CORPUS);

        assertEquals(checkRecord.oracleAccuracy(), checkRecord.blindAccuracy());
        assertFalse(verdict.passes());
        assertTrue(verdict.reasons().contains("oracle_not_above_blind"));
    }

    @Test
    @DisplayName("a lost judge task leaves the answer unscored and the check without a verdict")
    void lostJudge() {
        var runner = new Runner(prompt -> "judge/right-1".equals(prompt.itemId())
                ? Optional.empty()
                : Optional.of(score(BigDecimal.ONE)));

        var checkRecord = runner.run();
        var verdict = JudgeCheck.Verdict.of(checkRecord, digestOf(JudgeCheckTest::judgeRole), CORPUS);

        assertEquals(1, checkRecord.unscored());
        assertFalse(verdict.passes());
        assertTrue(verdict.reasons().contains("unscored"));
    }

    @Test
    @DisplayName("the blind scoring withholds the reference and the oracle scoring carries it")
    void blindWithholdsReference() {
        var runner = new Runner(JudgeCheckTest::followsReference);

        runner.run();

        var oracle = runner.prompts.stream().filter(prompt -> prompt.reference() != null).toList();
        var blind = runner.prompts.stream().filter(prompt -> prompt.reference() == null).toList();
        assertEquals(4, oracle.size());
        assertEquals(4, blind.size());
        assertEquals(RIGHT_ANSWER, oracle.getFirst().reference());
        assertEquals("another right answer", oracle.get(1).reference());
        assertTrue(blind.stream().allMatch(prompt -> prompt.reference() == null));
    }

    @Test
    @DisplayName("the prompt of the oracle carries the item's reference")
    void oraclePrompt() {
        var runner = new Runner(JudgeCheckTest::followsReference);

        runner.run();

        assertEquals(RIGHT_ANSWER, runner.prompts.getFirst().reference());
        assertNull(runner.prompts.get(1).reference());
    }

    @Test
    @DisplayName("an oracle exactly at the baseline plus the margin passes, like the topic bound")
    void equalityPasses() {
        var scored = new JudgeCheck.ItemScore("judge/right", JudgeCheck.Label.RIGHT, Grade.of(true),
                Grade.of(false), TokenTotal.measured(100), 10L);
        var checkRecord = new JudgeCheck.Record("a".repeat(64), QualificationFixtures.HARNESS,
                QualificationFixtures.JUDGE_MODEL, CORPUS, new BigDecimal("60"), BigDecimal.ZERO,
                new BigDecimal("50"), 0, NOW, List.of(scored));

        assertTrue(JudgeCheck.Verdict.of(checkRecord, "a".repeat(64), CORPUS).passes());
    }

    @Test
    @DisplayName("a judge check record without an item is refused")
    void itemsRequired() {
        assertThrows(IllegalArgumentException.class, () -> new JudgeCheck.Record("a".repeat(64),
                QualificationFixtures.HARNESS, QualificationFixtures.JUDGE_MODEL, CORPUS, new BigDecimal("60"),
                BigDecimal.ZERO, new BigDecimal("50"), 0, NOW, List.of()));
    }

    @Test
    @DisplayName("a changed judge digest or corpus revision leaves no valid record")
    void staleRecord() {
        var checkRecord = new Runner(JudgeCheckTest::followsReference).run();

        assertFalse(JudgeCheck.Verdict.of(checkRecord, "b".repeat(64), CORPUS).passes());
        assertFalse(JudgeCheck.Verdict.of(checkRecord, digestOf(JudgeCheckTest::judgeRole), "corpus-8").passes());
    }

    @Test
    @DisplayName("the check refuses an empty judge topic")
    void emptyTopic() {
        var runner = new Runner(JudgeCheckTest::followsReference);
        var judge = judgeRole();
        var noItems = List.<JudgeCheck.Item>of();

        assertThrows(IllegalArgumentException.class, () -> runner.check.run(judge, noItems, CORPUS));
    }

    @Test
    @DisplayName("the record round-trips through its canonical text")
    void roundTrip() {
        var checkRecord = new Runner(JudgeCheckTest::followsReference).run();
        var file = directory.resolve("judge-check.json");

        var document = JudgeCheckCodec.write(checkRecord);
        var read = JudgeCheckCodec.read(file, document);

        assertEquals(checkRecord, read);
        assertEquals(new String(document, StandardCharsets.UTF_8),
                new String(JudgeCheckCodec.write(read), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("an unreadable judge check is refused with the file named")
    void unreadable() {
        var file = directory.resolve("judge-check.json");
        var text = new String(JudgeCheckCodec.write(new Runner(JudgeCheckTest::followsReference).run()),
                StandardCharsets.UTF_8);
        var document = text.replace("\"label\":\"right\"", "\"label\":\"maybe\"").getBytes(StandardCharsets.UTF_8);

        var refused = assertThrows(StoreRefusal.class, () -> JudgeCheckCodec.read(file, document));

        assertEquals(file, refused.file());
    }

    @Test
    @DisplayName("a score outside 0 to 1 is refused")
    void scoreRange() {
        var outside = new BigDecimal("1.1");

        assertThrows(IllegalArgumentException.class, () -> Grade.scored(outside));
    }

    private static Optional<JudgeScorer.JudgeScore> followsReference(JudgeScorer.JudgePrompt prompt) {
        if (prompt.reference() != null) {
            return Optional.of(score(RIGHT_ANSWER.equals(prompt.answer()) ? BigDecimal.ONE : BigDecimal.ZERO));
        }
        return Optional.of(score(BigDecimal.ZERO));
    }

    private static String digestOf(Supplier<EffectiveRole> role) {
        return role.get().qualificationDigest();
    }

    private static EffectiveRole judgeRole() {
        return QualificationFixtures.role("judge", RoleClass.ZERO_TOOL, RoleForm.FRESH);
    }

    private static List<JudgeCheck.Item> items() {
        return List.of(
                new JudgeCheck.Item("judge/right-1", "Is the answer right?", "", RIGHT_ANSWER, RIGHT_ANSWER,
                        JudgeCheck.Label.RIGHT),
                new JudgeCheck.Item("judge/right-2", "Is the answer right?", "", "another right answer", RIGHT_ANSWER,
                        JudgeCheck.Label.RIGHT),
                new JudgeCheck.Item("judge/wrong-1", "Is the answer right?", "", "a wrong answer", WRONG_ANSWER,
                        JudgeCheck.Label.WRONG),
                new JudgeCheck.Item("judge/wrong-2", "Is the answer right?", "", "a wrong answer", WRONG_ANSWER,
                        JudgeCheck.Label.WRONG));
    }

    private static JudgeScorer.JudgeScore score(BigDecimal score) {
        return new JudgeScorer.JudgeScore(score, TokenTotal.measured(100), 10);
    }

    /** The runner of one check, which records every prompt. */
    private static final class Runner {

        private final List<JudgeScorer.JudgePrompt> prompts = new ArrayList<>();
        private final JudgeInstrumentCheck check;

        private Runner(Function<JudgeScorer.JudgePrompt,
                Optional<JudgeScorer.JudgeScore>> scorer) {
            this.check = new JudgeInstrumentCheck(prompt -> {
                prompts.add(prompt);
                return scorer.apply(prompt);
            }, Clock.fixed(NOW, ZoneOffset.UTC));
        }

        private JudgeCheck.Record run() {
            return check.run(judgeRole(), items(), CORPUS);
        }
    }
}
