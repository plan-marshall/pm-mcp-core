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

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import de.planmarshall.core.role.EffectiveRole;

/**
 * Runs the instrument check of a judge configuration (Model Work § Judge Instrument Check): every item of the corpus
 * topic {@code judge/} is scored twice, once with the reference and once blind, and the record stores the two
 * accuracies, the constant baseline and every item's scores. The blind task's prompt carries no reference.
 * <p>
 * A judge task that closes {@code lost} leaves its answer unscored; a record with an unscored answer has no verdict, and
 * a grading whose judge has no valid check record that passes is refused with {@code judge_unchecked}.
 *
 * @since 0.1
 */
public final class JudgeInstrumentCheck {

    private final JudgeScorer scorer;
    private final Clock clock;

    /**
     * @param scorer the judge's task surface
     * @param clock  the clock the record's {@code checked_at} is taken from
     */
    public JudgeInstrumentCheck(JudgeScorer scorer, Clock clock) {
        this.scorer = Objects.requireNonNull(scorer, "scorer");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * @param judge          the judge configuration
     * @param items          the corpus items of the judge topic
     * @param corpusRevision the revision of the corpus
     * @return the measurements of the check
     * @throws IllegalArgumentException if the corpus holds no judge item, because then the check has no verdict
     */
    public JudgeCheck.Record run(EffectiveRole judge, List<JudgeCheck.Item> items, String corpusRevision) {
        Objects.requireNonNull(judge, "judge");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(corpusRevision, "corpusRevision");
        if (items.isEmpty()) {
            throw new IllegalArgumentException("The judge topic holds no item, so the check has no verdict");
        }
        var scores = new ArrayList<JudgeCheck.ItemScore>();
        var oracleRight = 0;
        var blindRight = 0;
        var scored = 0;
        var unscored = 0;
        for (var item : items) {
            var oracle = scorer.score(new JudgeScorer.JudgePrompt(item.itemId(), item.question(), item.facts(),
                    item.reference(), item.answer()));
            var blind = scorer.score(new JudgeScorer.JudgePrompt(item.itemId(), item.question(), item.facts(),
                    null, item.answer()));
            if (oracle.isEmpty() || blind.isEmpty()) {
                unscored++;
                var lostTokens = TokenTotal.unmeasured();
                scores.add(new JudgeCheck.ItemScore(item.itemId(), item.label(),
                        oracle.map(JudgeScorer.JudgeScore::score).map(Grade::scored).orElseGet(Grade::ungraded),
                        blind.map(JudgeScorer.JudgeScore::score).map(Grade::scored).orElseGet(Grade::ungraded),
                        lostTokens, null));
                continue;
            }
            scored++;
            var oracleGrade = Grade.scored(oracle.get().score());
            var blindGrade = Grade.scored(blind.get().score());
            if (oracleGrade.judgedRight() == (item.label() == JudgeCheck.Label.RIGHT)) {
                oracleRight++;
            }
            if (blindGrade.judgedRight() == (item.label() == JudgeCheck.Label.RIGHT)) {
                blindRight++;
            }
            scores.add(new JudgeCheck.ItemScore(item.itemId(), item.label(), oracleGrade, blindGrade,
                    oracle.get().totalTokens().plus(blind.get().totalTokens()),
                    oracle.get().durationMs() + blind.get().durationMs()));
        }
        return new JudgeCheck.Record(judge.qualificationDigest(), judge.harness(), judge.model(), corpusRevision,
                scored == 0 ? BigDecimal.ZERO : ConstantBaseline.percent(oracleRight, scored),
                scored == 0 ? BigDecimal.ZERO : ConstantBaseline.percent(blindRight, scored),
                JudgeCheck.baseline(items), unscored, clock.instant(), scores);
    }
}
