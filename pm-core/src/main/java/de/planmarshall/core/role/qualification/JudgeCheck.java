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

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The measurements of one instrument check of a judge configuration (Store Schemas § Judge Check Record; Model Work
 * § Judge Instrument Check), and whether they pass the check now.
 * <p>
 * The check is never a role's topic and grades no role. Every item of the corpus topic {@code judge/} carries a known
 * answer and its label; the judge scores it twice, once with the reference (the oracle) and once with the reference
 * withheld (blind). A score of at least {@code 0.5} counts as judging the answer right, and the accuracy of a run is the
 * percent of the answers judged as their label says. The record stores measurements, never a pass flag; the grading
 * compares them with the qualification margin at every check.
 *
 * @since 0.1
 */
public final class JudgeCheck {

    /** The format version of a judge check record. */
    public static final int FORMAT_VERSION = 1;

    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9-]{1,31}");

    private JudgeCheck() {
    }

    /**
     * The measurements of one check.
     *
     * @param qualificationDigest the digest of the checked judge configuration
     * @param harness             the judge's harness
     * @param model               the judge's model
     * @param corpusRevision      the revision of the corpus the check ran on
     * @param oracleAccuracy      the percent of the answers judged as their label says, with the reference given
     * @param blindAccuracy       the same with the reference withheld
     * @param constantBaseline    the larger share of right and wrong answers in the topic
     * @param unscored            the answers a lost judge task left unscored
     * @param checkedAt           when the check finished
     * @param items               every item's two scores
     * @since 0.1
     */
    public record Record(String qualificationDigest, String harness, String model, String corpusRevision,
    BigDecimal oracleAccuracy, BigDecimal blindAccuracy, BigDecimal constantBaseline, int unscored,
    Instant checkedAt, List<ItemScore> items) {

        /**
         * @throws IllegalArgumentException if a value is outside the record's format
         */
        public Record {
            require(qualificationDigest, DIGEST, "qualification_digest");
            require(harness, NAME, "harness");
            Objects.requireNonNull(model, "model");
            if (model.isEmpty()) {
                throw new IllegalArgumentException("'model' must not be empty");
            }
            Objects.requireNonNull(corpusRevision, "corpusRevision");
            if (corpusRevision.isEmpty()) {
                throw new IllegalArgumentException("'corpus_revision' must not be empty");
            }
            require(oracleAccuracy, "oracle_accuracy");
            require(blindAccuracy, "blind_accuracy");
            require(constantBaseline, "constant_baseline");
            if (unscored < 0) {
                throw new IllegalArgumentException("'unscored' must not be negative: " + unscored);
            }
            Objects.requireNonNull(checkedAt, "checkedAt");
            items = List.copyOf(Objects.requireNonNull(items, "items"));
            if (items.isEmpty()) {
                throw new IllegalArgumentException("A judge check record scores at least one item");
            }
        }

        private static void require(BigDecimal value, String field) {
            Objects.requireNonNull(value, field);
            if (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IllegalArgumentException("'%s' lies outside 0 to 100: %s".formatted(field, value));
            }
        }

        private static void require(String value, Pattern pattern, String field) {
            Objects.requireNonNull(value, field);
            if (!pattern.matcher(value).matches()) {
                throw new IllegalArgumentException("'%s' is outside its format: %s".formatted(field, value));
            }
        }
    }

    /**
     * One answer of the judge topic, with what the configuration scored it.
     *
     * @param itemId      the item
     * @param label       whether the answer is right or wrong
     * @param oracleScore the score with the reference given
     * @param blindScore  the score with the reference withheld
     * @param totalTokens the tokens both scorings used
     * @param durationMs  how long the two scorings took; {@code null} when unknown
     * @since 0.1
     */
    public record ItemScore(String itemId, Label label, Grade oracleScore, Grade blindScore, TokenTotal totalTokens,
    @Nullable Long durationMs) {

        /**
         * @throws IllegalArgumentException if the item carries no id or a negative duration
         */
        public ItemScore {
            Objects.requireNonNull(itemId, "itemId");
            if (itemId.isEmpty()) {
                throw new IllegalArgumentException("'item_id' must not be empty");
            }
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(oracleScore, "oracleScore");
            Objects.requireNonNull(blindScore, "blindScore");
            Objects.requireNonNull(totalTokens, "totalTokens");
            if (durationMs != null && durationMs < 0) {
                throw new IllegalArgumentException("'duration_ms' must not be negative: " + durationMs);
            }
        }
    }

    /**
     * Whether an answer is right or wrong in the judge topic. The set is closed.
     *
     * @since 0.1
     */
    public enum Label {
        /** The known answer is right, so the judge must score it from {@code 0.5}. */
        RIGHT("right"),
        /** The known answer is wrong, so the judge must score it below {@code 0.5}. */
        WRONG("wrong");

        private final String wireName;

        Label(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the label in a record */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a label in a document
         * @return the label; empty if the name is not one
         */
        public static Optional<Label> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(label -> label.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * One item of the judge instrument check, as the corpus offers it.
     *
     * @param itemId    the item
     * @param question  the question
     * @param facts     the facts the judge sees with the question
     * @param reference the reference answer
     * @param answer    the known answer to score
     * @param label     whether the known answer is right or wrong
     * @since 0.1
     */
    public record Item(String itemId, String question, String facts, String reference, String answer, Label label) {

        /** @throws NullPointerException if a component is {@code null} */
        public Item {
            Objects.requireNonNull(itemId, "itemId");
            Objects.requireNonNull(question, "question");
            Objects.requireNonNull(facts, "facts");
            Objects.requireNonNull(reference, "reference");
            Objects.requireNonNull(answer, "answer");
            Objects.requireNonNull(label, "label");
        }
    }

    /**
     * Whether a record passes the check now, against the current judge digest and corpus revision.
     *
     * @param passes  whether the check passes
     * @param reasons why it does not pass; empty when it passes
     * @since 0.1
     */
    public record Verdict(boolean passes, List<String> reasons) {

        /** @throws NullPointerException if the reasons are {@code null} */
        public Verdict {
            reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
        }

        /**
         * @param record          the record
         * @param digest          the judge's current qualification digest
         * @param corpusRevision  the shipped corpus revision
         * @return the verdict
         */
        public static Verdict of(Record entry, String digest, String corpusRevision) {
            Objects.requireNonNull(entry, "entry");
            var reasons = new ArrayList<String>();
            if (!entry.qualificationDigest().equals(digest)) {
                reasons.add("stale_digest");
            }
            if (!entry.corpusRevision().equals(corpusRevision)) {
                reasons.add("stale_corpus");
            }
            if (entry.unscored() > 0) {
                reasons.add("unscored");
            }
            var margin = BigDecimal.valueOf(QualificationCheck.MARGIN);
            if (entry.oracleAccuracy().compareTo(entry.constantBaseline().add(margin)) < 0) {
                reasons.add("oracle_not_above_baseline");
            }
            if (entry.oracleAccuracy().compareTo(entry.blindAccuracy().add(margin)) < 0) {
                reasons.add("oracle_not_above_blind");
            }
            return new Verdict(reasons.isEmpty(), reasons);
        }
    }

    /**
     * @param items the corpus items of the judge topic
     * @return the constant baseline of the check: the larger share of right and wrong answers
     */
    public static BigDecimal baseline(List<Item> items) {
        Objects.requireNonNull(items, "items");
        if (items.isEmpty()) {
            return BigDecimal.ZERO;
        }
        var right = items.stream().filter(item -> item.label() == Label.RIGHT).count();
        var wrong = items.size() - right;
        return ConstantBaseline.percent((int) Math.max(right, wrong), items.size());
    }
}
