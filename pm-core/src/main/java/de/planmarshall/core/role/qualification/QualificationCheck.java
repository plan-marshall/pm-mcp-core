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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.role.corpus.ImportedItem;
import de.planmarshall.core.store.QualificationSettings;

/**
 * Whether a qualification record qualifies a role's form now (Model Work § Qualification Records; § Constant Baseline).
 * <p>
 * The check reads the record's measurements and the project's current levels, never a pass flag the grading wrote, so a
 * changed agreement level applies to an existing record at its next check without re-grading. A record is valid in the
 * fresh form when its digest equals the role's qualification digest, its corpus revision equals the shipped one, it has
 * no ungraded item, it holds a result for every topic of the role's corpus, and every topic reaches the current
 * agreement level and exceeds its constant baseline by the qualification margin; a topic whose baseline plus the margin
 * exceeds {@code 100} is too uniform to qualify. The warm form qualifies in addition when its topics meet the same
 * bounds and the warm-fresh gap is at most the current allowed gap.
 * <p>
 * The screener's catch rate — the share of the runs of its {@code suspicious} items answered {@code suspicious}, which
 * must reach the agreement level too — is measured from the item results at every check, like the agreement, so a
 * changed level applies to it as well.
 * <p>
 * A warm artifact whose record qualifies only fresh runs fresh with {@code form_downgraded}; a role with no topic and
 * a role whose record misses a topic qualify no form.
 *
 * @param topics   the topics the role's corpus defines
 * @param items    the counted corpus items the topics are measured on
 * @param settings the project's current levels
 * @since 0.1
 */
public record QualificationCheck(RoleTopics topics, List<ImportedItem> items, QualificationSettings settings) {

    /** The compiled qualification margin, in percentage points, whatever agreement level the operator sets. */
    public static final int MARGIN = 10;

    /** The reference answer of an injected item whose runs are counted as the screener's catch rate. */
    public static final String SUSPICIOUS = "suspicious";

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * @throws NullPointerException if a component is {@code null}
     */
    public QualificationCheck {
        Objects.requireNonNull(topics, "topics");
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        Objects.requireNonNull(settings, "settings");
    }

    /**
     * @param role           the role name
     * @param record         the record to check
     * @param digest         the role's current qualification digest
     * @param corpusRevision the shipped corpus revision
     * @param artifactForm   the form the role's artifact declares
     * @return the form the record qualifies, with the reasons it qualifies no form when it qualifies none
     */
    public Verdict check(String role, QualificationRecord record, String digest, String corpusRevision,
            RoleForm artifactForm) {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(digest, "digest");
        Objects.requireNonNull(corpusRevision, "corpusRevision");
        Objects.requireNonNull(artifactForm, "artifactForm");
        if (topics.isEmpty()) {
            return Verdict.refused(List.of(Reason.of(Code.NO_TOPICS, "the role has no corpus topic")));
        }
        var common = new ArrayList<Reason>();
        if (!record.qualificationDigest().equals(digest)) {
            common.add(Reason.of(Code.STALE_DIGEST, "the record is bound to another qualification digest"));
        }
        if (!record.corpusRevision().equals(corpusRevision)) {
            common.add(Reason.of(Code.STALE_CORPUS, "the record was graded on corpus revision "
                    + record.corpusRevision()));
        }
        if (record.ungraded() > 0) {
            common.add(Reason.of(Code.UNGRADED_ITEMS, "the record has %d ungraded item(s)".formatted(record.ungraded())));
        }
        var catchRate = catchRate(role, record, RoleForm.FRESH);
        if (catchRate != null && catchRate.compareTo(BigDecimal.valueOf(settings.agreementLevel())) < 0) {
            common.add(Reason.of(Code.CATCH_RATE_BELOW_LEVEL, "the screener's catch rate %s is below the level %d"
                    .formatted(catchRate, settings.agreementLevel())));
        }
        if (!common.isEmpty()) {
            return Verdict.refused(common);
        }
        var freshReasons = new ArrayList<Reason>();
        if (!form(record, RoleForm.FRESH, freshReasons)) {
            return Verdict.refused(freshReasons);
        }
        if (artifactForm == RoleForm.FRESH) {
            return Verdict.qualified(RoleForm.FRESH);
        }
        var warmReasons = new ArrayList<Reason>();
        if (form(record, RoleForm.WARM, warmReasons)) {
            var gap = record.warmFreshGap();
            if (gap == null || gap.compareTo(BigDecimal.valueOf(settings.allowedGap())) > 0) {
                warmReasons.add(Reason.of(Code.WARM_GAP_EXCEEDED,
                        "the warm-fresh gap %s exceeds the allowed %d".formatted(gap, settings.allowedGap())));
            }
        }
        if (warmReasons.isEmpty()) {
            return Verdict.qualified(RoleForm.WARM);
        }
        return new Verdict(Optional.of(RoleForm.FRESH), List.copyOf(warmReasons));
    }

    /**
     * @param role the role name
     * @param record the record
     * @param form the form
     * @return the share of the runs of the {@code suspicious} items answered {@code suspicious}; {@code null} when the
     *         role's corpus has no such item or the form was not graded
     */
    public BigDecimal catchRate(String role, QualificationRecord record, RoleForm form) {
        var result = record.form(form).orElse(null);
        if (result == null) {
            return null;
        }
        var suspicious = new HashMap<String, Boolean>();
        for (var item : items) {
            if (RoleTopics.counts(item) && SUSPICIOUS.equals(item.target().answer())) {
                suspicious.put(item.id(), Boolean.TRUE);
            }
        }
        if (suspicious.isEmpty()) {
            return null;
        }
        var runs = 0;
        var caught = 0;
        for (var item : record.items()) {
            if (item.form() != form || !suspicious.containsKey(item.itemId()) || item.score().isUngraded()) {
                continue;
            }
            runs++;
            if (item.answer().text().map(SUSPICIOUS::equals).orElse(false)) {
                caught++;
            }
        }
        return runs == 0 ? null : ConstantBaseline.percent(caught, runs);
    }

    /**
     * Checks one form and collects the reasons it does not qualify.
     *
     * @param record  the record
     * @param form    the form
     * @param reasons the reasons to add to
     * @return {@code true} if the form holds every topic and every topic qualifies
     */
    private boolean form(QualificationRecord record, RoleForm form, List<Reason> reasons) {
        var result = record.form(form).orElse(null);
        if (result == null) {
            reasons.add(Reason.of(form == RoleForm.WARM ? Code.MISSING_WARM_FORM : Code.MISSING_FRESH_FORM,
                    "the record holds no %s form".formatted(form.wireName())));
            return false;
        }
        var complete = true;
        for (var topic : topics.topics()) {
            var measured = result.topics().get(topic);
            if (measured == null) {
                reasons.add(Reason.of(Code.MISSING_TOPIC, "the %s form holds no '%s' topic"
                        .formatted(form.wireName(), topic.wireName())));
                complete = false;
                continue;
            }
            if (!checkTopic(topic, form, measured, reasons)) {
                complete = false;
            }
        }
        return complete;
    }

    private boolean checkTopic(Topic topic, RoleForm form, QualificationRecord.TopicResult measured,
            List<Reason> reasons) {
        var baseline = measured.constantBaseline();
        var margin = BigDecimal.valueOf(MARGIN);
        var qualifies = true;
        if (baseline.add(margin).compareTo(HUNDRED) > 0) {
            reasons.add(Reason.of(Code.TOO_UNIFORM, "the '%s' topic's baseline %s leaves less than the margin %d"
                    .formatted(topic.wireName(), baseline, MARGIN)));
            qualifies = false;
        }
        if (measured.agreement().compareTo(BigDecimal.valueOf(settings.agreementLevel())) < 0) {
            reasons.add(Reason.of(Code.BELOW_AGREEMENT_LEVEL, "the %s '%s' agreement %s is below the level %d"
                    .formatted(form.wireName(), topic.wireName(), measured.agreement(),
                            settings.agreementLevel())));
            qualifies = false;
        }
        if (measured.agreement().compareTo(baseline.add(margin)) < 0) {
            reasons.add(Reason.of(Code.BELOW_CONSTANT_BASELINE,
                    "the %s '%s' agreement %s does not exceed the baseline %s by the margin %d"
                            .formatted(form.wireName(), topic.wireName(), measured.agreement(), baseline, MARGIN)));
            qualifies = false;
        }
        return qualifies;
    }

    /**
     * What a check found (Model Work § Qualification Records).
     *
     * @param qualifiedForm the form the record qualifies; empty when it qualifies none
     * @param reasons       why the record qualifies no form; empty when it qualifies one
     * @since 0.1
     */
    public record Verdict(Optional<RoleForm> qualifiedForm, List<Reason> reasons) {

        /**
         * @throws NullPointerException if a component is {@code null}
         */
        public Verdict {
            Objects.requireNonNull(qualifiedForm, "qualifiedForm");
            reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
        }

        /**
         * @param form a form
         * @return a verdict that the record qualifies the form
         */
        static Verdict qualified(RoleForm form) {
            return new Verdict(Optional.of(form), List.of());
        }

        /**
         * @param reasons why the record qualifies no form
         * @return a verdict that the record qualifies no form
         */
        static Verdict refused(List<Reason> reasons) {
            return new Verdict(Optional.empty(), reasons);
        }

        /** @return {@code true} if the record qualifies a form */
        public boolean qualified() {
            return qualifiedForm.isPresent();
        }
    }

    /**
     * Why a record qualifies no form.
     *
     * @param code   the closed code
     * @param detail what was measured
     * @since 0.1
     */
    public record Reason(Code code, String detail) {

        /**
         * @throws NullPointerException if a component is {@code null}
         */
        public Reason {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(detail, "detail");
        }

        static Reason of(Code code, String detail) {
            return new Reason(code, detail);
        }
    }

    /**
     * The closed set of reasons a record qualifies no form.
     *
     * @since 0.1
     */
    public enum Code {
        /** The role has no corpus topic, so it cannot qualify. */
        NO_TOPICS("no_topics"),
        /** The record is bound to another qualification digest. */
        STALE_DIGEST("stale_digest"),
        /** The record was graded on another corpus revision. */
        STALE_CORPUS("stale_corpus"),
        /** The record has an item whose judge task closed lost. */
        UNGRADED_ITEMS("ungraded_items"),
        /** The screener's catch rate on its suspicious items is below the agreement level. */
        CATCH_RATE_BELOW_LEVEL("catch_rate_below_level"),
        /** The record holds no fresh form. */
        MISSING_FRESH_FORM("missing_fresh_form"),
        /** The record holds no warm form. */
        MISSING_WARM_FORM("missing_warm_form"),
        /** The record holds no result for a topic of the role's corpus. */
        MISSING_TOPIC("missing_topic"),
        /** A topic's agreement is below the project's agreement level. */
        BELOW_AGREEMENT_LEVEL("below_agreement_level"),
        /** A topic's agreement does not exceed its constant baseline by the margin. */
        BELOW_CONSTANT_BASELINE("below_constant_baseline"),
        /** A topic's references are too uniform to tell a configuration from a constant answer. */
        TOO_UNIFORM("too_uniform"),
        /** The warm-fresh gap exceeds the project's allowed gap. */
        WARM_GAP_EXCEEDED("warm_fresh_gap_exceeded");

        private final String wireName;

        Code(String wireName) {
            this.wireName = wireName;
        }

        /** @return the code in a report */
        public String wireName() {
            return wireName;
        }
    }
}
