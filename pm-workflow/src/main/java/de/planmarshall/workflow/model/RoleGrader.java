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

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.role.corpus.ImportResult;
import de.planmarshall.core.role.corpus.ImportedItem;
import de.planmarshall.core.role.qualification.Checkout;
import de.planmarshall.core.role.qualification.ConstantBaseline;
import de.planmarshall.core.role.qualification.Grade;
import de.planmarshall.core.role.qualification.GradedConfiguration;
import de.planmarshall.core.role.qualification.GradingConsent;
import de.planmarshall.core.role.qualification.GradingHarness;
import de.planmarshall.core.role.qualification.GradingOffer;
import de.planmarshall.core.role.qualification.GradingOutcome;
import de.planmarshall.core.role.qualification.GradingPlan;
import de.planmarshall.core.role.qualification.JudgeCheck;
import de.planmarshall.core.role.qualification.JudgeChecks;
import de.planmarshall.core.role.qualification.JudgeInstrumentCheck;
import de.planmarshall.core.role.qualification.JudgeScorer;
import de.planmarshall.core.role.qualification.QualificationRecord;
import de.planmarshall.core.role.qualification.QualificationRecordStore;
import de.planmarshall.core.role.qualification.RoleTopics;
import de.planmarshall.core.role.qualification.Scoring;
import de.planmarshall.core.role.qualification.TokenTotal;
import de.planmarshall.core.role.qualification.Topic;

/**
 * The grading run of one role's configuration (Model Work § Grading; § Judge Instrument Check; § Corpus Sources).
 * <p>
 * A run starts only on the operator's consent, and never on a failed corpus import. It validates the judge as an
 * instrument first — running the check when the project holds no valid record for the judge's qualification digest and
 * the corpus revision, and refusing {@code judge_unchecked} when the check has no verdict or does not pass — and
 * refuses {@code scorer_not_independent} when the judge runs the graded model or a declared sibling. Every item of the
 * role's topics is then offered to the graded configuration in the fresh form and, unless the role is a
 * {@code zero_tool} role, in the warm form; the screener's items are offered as often as its profile says, each in its
 * own worker.
 * <p>
 * A closed answer is scored deterministically; a rubric answer goes to a task of the judge role. A lost answer or judge
 * task leaves the item ungraded. The next run reuses every graded item of the same digest and corpus revision and
 * re-grades only the ungraded ones — a recorded answer by a new judge task, an item without an answer by a new offer.
 * <p>
 * The record is written to the role set the store names; it holds measurements, never a pass flag.
 *
 * @since 0.1
 */
public final class RoleGrader {

    private final GradingHarness harness;
    private final JudgeScorer scorer;
    private final QualificationRecordStore records;
    private final JudgeChecks judgeChecks;
    private final JudgeInstrumentCheck instrumentCheck;
    private final Clock clock;

    /**
     * @param harness     the task surface of the graded configuration
     * @param scorer      the task surface of the judge
     * @param records     the role set's qualification records; a run writes the project set's record through it
     * @param judgeChecks the project set's judge check record
     * @param clock       the clock the record's timestamps are taken from
     */
    public RoleGrader(GradingHarness harness, JudgeScorer scorer, QualificationRecordStore records,
            JudgeChecks judgeChecks, Clock clock) {
        this.harness = Objects.requireNonNull(harness, "harness");
        this.scorer = Objects.requireNonNull(scorer, "scorer");
        this.records = Objects.requireNonNull(records, "records");
        this.judgeChecks = Objects.requireNonNull(judgeChecks, "judgeChecks");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.instrumentCheck = new JudgeInstrumentCheck(scorer, clock);
    }

    /**
     * @param plan a grading plan
     * @return the offer the operator consents to, with the expected tokens
     */
    public GradingOffer offer(GradingPlan plan) {
        return GradingOffer.of(plan);
    }

    /**
     * @param plan    a grading plan
     * @param consent the operator's answer to the offer
     * @return the record the grading wrote, the decline, or the reason it was refused
     */
    public GradingOutcome grade(GradingPlan plan, GradingConsent consent) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(consent, "consent");
        if (consent == GradingConsent.DECLINED) {
            return new GradingOutcome.Declined(true);
        }
        var role = plan.role();
        var name = role.artifact().name();
        if (plan.importResult().verdict() == ImportResult.Verdict.FAILED) {
            return refused(GradingOutcome.Refused.CORPUS_IMPORT_FAILED,
                    "the import failed" + missed(plan));
        }
        if (!plan.judge().independentOf(role)) {
            return refused(GradingOutcome.Refused.SCORER_NOT_INDEPENDENT,
                    "the judge runs the graded model or its sibling");
        }
        var topics = RoleTopics.of(name, role.roleClass(), plan.items());
        if (topics.isEmpty()) {
            return refused(GradingOutcome.Refused.NO_TOPICS, "the corpus holds no item for the role");
        }
        var judgeRefusal = ensureJudgeCheck(plan);
        if (judgeRefusal.isPresent()) {
            return judgeRefusal.get();
        }
        var runs = GradingOffer.runs(plan);
        var previous = plan.previous().filter(entry -> entry.qualificationDigest().equals(role.qualificationDigest())
                && entry.corpusRevision().equals(plan.corpusRevision()));
        var results = new ArrayList<QualificationRecord.ItemResult>();
        var forms = new EnumMap<RoleForm, QualificationRecord.FormResult>(RoleForm.class);
        for (var form : forms(plan)) {
            var formResults = gradeForm(plan, topics, form, previous, runs);
            var formResult = formResult(plan, topics, formResults);
            if (formResult.isEmpty()) {
                return refused(GradingOutcome.Refused.NO_GRADED_ITEMS,
                        "no counted item of the role's topics could be graded in the %s form"
                                .formatted(form.wireName()));
            }
            forms.put(form, formResult.get());
            results.addAll(formResults);
        }
        var entry = new QualificationRecord(name, role.qualificationDigest(), GradedConfiguration.of(role),
                plan.corpusRevision(), missedSources(plan), forms, warmFreshGap(forms), ungraded(results),
                clock.instant(), results);
        records.write(entry);
        return new GradingOutcome.Graded(entry);
    }

    private List<RoleForm> forms(GradingPlan plan) {
        return plan.role().zeroTool() ? List.of(RoleForm.FRESH) : List.of(RoleForm.FRESH, RoleForm.WARM);
    }

    private Optional<GradingOutcome.Refused> ensureJudgeCheck(GradingPlan plan) {
        var judge = plan.judge().role();
        var digest = judge.qualificationDigest();
        var stored = judgeChecks.lookup().orElse(null);
        var verdict = stored == null ? null : JudgeCheck.Verdict.of(stored, digest, plan.corpusRevision());
        if (verdict != null && verdict.passes()) {
            return Optional.empty();
        }
        if (plan.judge().items().isEmpty()) {
            return Optional.of(refused(GradingOutcome.Refused.JUDGE_UNCHECKED,
                    "the corpus holds no judge item to check the judge with"));
        }
        var checkRecord = instrumentCheck.run(judge, plan.judge().items(), plan.corpusRevision());
        judgeChecks.write(checkRecord);
        var checked = JudgeCheck.Verdict.of(checkRecord, digest, plan.corpusRevision());
        if (checked.passes()) {
            return Optional.empty();
        }
        return Optional.of(refused(GradingOutcome.Refused.JUDGE_UNCHECKED, String.join(", ", checked.reasons())));
    }

    private List<QualificationRecord.ItemResult> gradeForm(GradingPlan plan, RoleTopics topics, RoleForm form,
            Optional<QualificationRecord> previous, int runs) {
        var results = new ArrayList<QualificationRecord.ItemResult>();
        for (var topic : topics.topics()) {
            for (var item : RoleTopics.items(plan.items(), topic, plan.role().artifact().name())) {
                var checkout = checkout(plan, item);
                var reused = previous.map(entry -> reuse(entry, item, form, checkout)).orElse(List.of());
                if (!reused.isEmpty()) {
                    results.addAll(reused);
                    for (var run = reused.size() + 1; run <= runs; run++) {
                        results.add(offer(item, form, checkout, run));
                    }
                    continue;
                }
                for (var run = 1; run <= runs; run++) {
                    results.add(offer(item, form, checkout, run));
                }
            }
        }
        return results;
    }

    private QualificationRecord.ItemResult offer(ImportedItem item, RoleForm form, Checkout checkout, int run) {
        var answered = harness.answer(new GradingHarness.Offered(item, form, checkout, run));
        if (answered.isEmpty()) {
            return new QualificationRecord.ItemResult(item.id(), form, checkout, JsonNode.nullValue(),
                    Grade.ungraded(), TokenTotal.unmeasured(), null);
        }
        var answer = answered.get();
        if (!Scoring.judged(item)) {
            return new QualificationRecord.ItemResult(item.id(), form, checkout, JsonNode.string(answer.answer()),
                    Scoring.closed(item, answer.answer()), answer.totalTokens(), answer.durationMs());
        }
        var judged = scorer.score(prompt(item, answer.answer()));
        var grade = judged.map(score -> Grade.scored(score.score())).orElseGet(Grade::ungraded);
        var tokens = judged.map(score -> answer.totalTokens().plus(score.totalTokens()))
                .orElse(answer.totalTokens());
        var duration = judged.map(score -> answer.durationMs() + score.durationMs())
                .orElse(answer.durationMs());
        return new QualificationRecord.ItemResult(item.id(), form, checkout, JsonNode.string(answer.answer()), grade,
                tokens, duration);
    }

    /**
     * Reuses the graded runs of an item and re-grades only an ungraded one: an answer that was recorded goes back to the
     * judge, and an item that has no answer is offered again.
     */
    private List<QualificationRecord.ItemResult> reuse(QualificationRecord previous, ImportedItem item, RoleForm form,
            Checkout checkout) {
        var prior = previous.items().stream()
                .filter(result -> result.itemId().equals(item.id()) && result.form() == form)
                .toList();
        if (prior.isEmpty()) {
            return List.of();
        }
        var reused = new ArrayList<QualificationRecord.ItemResult>();
        for (var result : prior) {
            var reGraded = reGrade(item, form, checkout, result);
            if (reGraded.isEmpty()) {
                return List.of();
            }
            reused.add(reGraded.get());
        }
        return reused;
    }

    /**
     * @return the reused or re-graded run; empty when the run has no answer and must be offered again
     */
    private Optional<QualificationRecord.ItemResult> reGrade(ImportedItem item, RoleForm form, Checkout checkout,
            QualificationRecord.ItemResult result) {
        if (!result.score().isUngraded()) {
            return Optional.of(result);
        }
        var text = result.answer().text().orElse(null);
        if (text == null) {
            return Optional.empty();
        }
        var judged = Scoring.judged(item) ? scorer.score(prompt(item, text)) : Optional.<JudgeScorer.JudgeScore>empty();
        if (judged.isEmpty()) {
            return Optional.of(Scoring.judged(item)
                    ? result
                    : new QualificationRecord.ItemResult(item.id(), form, checkout, result.answer(),
                    Scoring.closed(item, text), result.totalTokens(), result.durationMs()));
        }
        return Optional.of(new QualificationRecord.ItemResult(item.id(), form, checkout, result.answer(),
                Grade.scored(judged.get().score()), result.totalTokens().plus(judged.get().totalTokens()),
                result.durationMs()));
    }

    private Optional<QualificationRecord.FormResult> formResult(GradingPlan plan, RoleTopics topics,
            List<QualificationRecord.ItemResult> results) {
        var byId = new HashMap<String, ImportedItem>();
        for (var item : plan.items()) {
            byId.put(item.id(), item);
        }
        var measured = new EnumMap<Topic, QualificationRecord.TopicResult>(Topic.class);
        var graded = 0;
        var tokens = 0L;
        var unmeasured = false;
        long duration = 0;
        for (var topic : topics.topics()) {
            var topicItems = RoleTopics.items(plan.items(), topic, plan.role().artifact().name());
            var ids = new HashSet<String>();
            topicItems.forEach(item -> ids.add(item.id()));
            var topicResults = results.stream().filter(result -> ids.contains(result.itemId())).toList();
            var gradedResults = topicResults.stream().filter(result -> !result.score().isUngraded()).toList();
            if (gradedResults.isEmpty()) {
                continue;
            }
            var right = gradedResults.stream().filter(result -> right(byId.get(result.itemId()), result)).count();
            measured.put(topic, new QualificationRecord.TopicResult(
                    ConstantBaseline.percent((int) right, gradedResults.size()),
                    ConstantBaseline.of(topicItems, gradedResults), gradedResults.size()));
            graded += gradedResults.size();
        }
        if (measured.isEmpty()) {
            return Optional.empty();
        }
        for (var result : results) {
            if (result.totalTokens().measured()) {
                tokens += result.totalTokens().tokens();
            } else {
                unmeasured = true;
            }
            if (result.durationMs() != null) {
                duration += result.durationMs();
            }
        }
        var totalTokens = unmeasured || tokens == 0 ? TokenTotal.unmeasured() : TokenTotal.measured(tokens);
        return Optional.of(new QualificationRecord.FormResult(measured, graded, totalTokens, duration));
    }

    private static boolean right(ImportedItem item, QualificationRecord.ItemResult result) {
        if (item == null || result.score().isUngraded()) {
            return false;
        }
        return Scoring.judged(item) ? Scoring.right(item, result.score().score()) : result.score().judgedRight();
    }

    private static BigDecimal warmFreshGap(Map<RoleForm, QualificationRecord.FormResult> forms) {
        var fresh = forms.get(RoleForm.FRESH);
        var warm = forms.get(RoleForm.WARM);
        if (fresh == null || warm == null) {
            return null;
        }
        BigDecimal gap = null;
        for (var entry : fresh.topics().entrySet()) {
            var warmTopic = warm.topics().get(entry.getKey());
            var warmAgreement = warmTopic == null ? BigDecimal.ZERO : warmTopic.agreement();
            var measured = entry.getValue().agreement().subtract(warmAgreement);
            gap = gap == null || measured.compareTo(gap) > 0 ? measured : gap;
        }
        if (gap == null) {
            return BigDecimal.ZERO;
        }
        return gap.stripTrailingZeros().scale() < 0 ? gap.setScale(0) : gap.stripTrailingZeros();
    }

    private static int ungraded(List<QualificationRecord.ItemResult> results) {
        return (int) results.stream().filter(result -> result.score().isUngraded()).count();
    }

    private static Checkout checkout(GradingPlan plan, ImportedItem item) {
        var origin = item.provenance().origin();
        if (plan.projectRepository().equals(origin.repository())
                && plan.checkouts().present(origin.commit())) {
            return Checkout.ITEM_COMMIT;
        }
        return Checkout.CURRENT;
    }

    private static JudgeScorer.JudgePrompt prompt(ImportedItem item, String answer) {
        var reference = item.target().answer() != null
                ? item.target().answer()
                : String.join("\n", item.target().items().stream().map(ImportedItem.RubricItem::description).toList());
        return new JudgeScorer.JudgePrompt(item.id(), item.input().question(), item.input().facts(), reference,
                answer);
    }

    private static List<String> missedSources(GradingPlan plan) {
        return plan.importResult().missed().stream().map(ImportResult.MissedSource::name).toList();
    }

    private static String missed(GradingPlan plan) {
        var missed = missedSources(plan);
        return missed.isEmpty() ? "" : ": " + String.join(", ", missed);
    }

    private static GradingOutcome.Refused refused(String reason, String detail) {
        return new GradingOutcome.Refused(reason, detail);
    }
}
