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
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.role.RoleForm;

/**
 * The measurements of one grading of a role's configuration (Store Schemas § Role Qualification Record; Model Work
 * § Qualification Records): what the grading measured, per form and per topic the agreement and the item count, per
 * form the tokens used and the time, the warm-fresh gap, and every item's answer, score, tokens and time.
 * <p>
 * A record holds no pass flag. Whether it qualifies a form is evaluated at every check against the project's current
 * agreement level and allowed gap, so a changed level applies to every existing record at once; a record with an
 * ungraded item qualifies no form. It is bound to the role's qualification digest and to the corpus revision: a change
 * of either makes it stale.
 *
 * @param role                the graded role
 * @param qualificationDigest the digest of the graded members the record is bound to
 * @param configuration       the graded members, recorded beside the digest
 * @param corpusRevision      the revision of the corpus the grading ran on
 * @param missedSources       the corpus sources the import missed; empty for a complete corpus
 * @param forms               the measurements per graded form; always holds {@code fresh}
 * @param warmFreshGap        the largest fresh-minus-warm agreement over the topics; {@code null} when only one form
 *                            was graded
 * @param ungraded            the number of items whose judge task closed {@code lost}
 * @param gradedAt            when the grading finished
 * @param items               every offered item and run
 * @since 0.1
 */
public record QualificationRecord(String role, String qualificationDigest, GradedConfiguration configuration,
String corpusRevision, List<String> missedSources, Map<RoleForm, FormResult> forms, BigDecimal warmFreshGap,
int ungraded, Instant gradedAt, List<ItemResult> items) {

    /** The format version of a qualification record. */
    public static final int FORMAT_VERSION = 1;

    private static final Pattern ROLE = Pattern.compile("[a-z][a-z0-9-]{1,31}");
    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern SOURCE = Pattern.compile("[a-z][a-z0-9-]{1,63}");

    /**
     * @throws IllegalArgumentException if the record does not have the one shape its schema gives it
     */
    public QualificationRecord {
        require(role, ROLE, "role");
        require(qualificationDigest, DIGEST, "qualification_digest");
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(corpusRevision, "corpusRevision");
        if (corpusRevision.isEmpty()) {
            throw new IllegalArgumentException("'corpus_revision' must not be empty");
        }
        missedSources = List.copyOf(Objects.requireNonNull(missedSources, "missedSources"));
        var sources = new HashSet<String>();
        for (var source : missedSources) {
            require(source, SOURCE, "missed source");
            if (!sources.add(source)) {
                throw new IllegalArgumentException("The source '%s' is missed twice".formatted(source));
            }
        }
        var graded = new EnumMap<RoleForm, FormResult>(RoleForm.class);
        graded.putAll(Objects.requireNonNull(forms, "forms"));
        if (!graded.containsKey(RoleForm.FRESH)) {
            throw new IllegalArgumentException("A record holds the fresh form");
        }
        forms = Collections.unmodifiableMap(graded);
        if (ungraded < 0) {
            throw new IllegalArgumentException("'ungraded' must not be negative: " + ungraded);
        }
        Objects.requireNonNull(gradedAt, "gradedAt");
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        if (graded.size() == 1 && warmFreshGap != null) {
            throw new IllegalArgumentException("'warm_fresh_gap' is null when only one form was graded");
        }
        if (graded.size() > 1 && warmFreshGap == null) {
            throw new IllegalArgumentException("'warm_fresh_gap' is measured when both forms were graded");
        }
    }

    /**
     * @param form a form
     * @return the form's measurements; empty if the form was not graded
     */
    public Optional<FormResult> form(RoleForm form) {
        return Optional.ofNullable(forms.get(form));
    }

    /**
     * @param topic a topic
     * @return the fresh agreement of the topic; empty if the fresh form does not hold it
     */
    public Optional<TopicResult> topic(Topic topic) {
        return forms.get(RoleForm.FRESH).topics().entrySet().stream()
                .filter(entry -> entry.getKey() == topic)
                .map(Map.Entry::getValue)
                .findFirst();
    }

    /** @return the number of items the grading offered, over every form and run */
    public int offered() {
        return items.size();
    }

    private static void require(String value, Pattern pattern, String field) {
        Objects.requireNonNull(value, field);
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException("'%s' is outside its format: %s".formatted(field, value));
        }
    }

    /**
     * The measurements of one form (Store Schemas § Role Qualification Record, {@code formResult}).
     *
     * @param topics      the agreement per topic; the topics the role's corpus defines
     * @param items       the graded items of all topics of this form, ungraded ones excluded
     * @param totalTokens the tokens the form's items used
     * @param durationMs  how long the form's grading took
     * @since 0.1
     */
    public record FormResult(Map<Topic, TopicResult> topics, int items, TokenTotal totalTokens, long durationMs) {

        /**
         * @throws IllegalArgumentException if no topic is measured or a count is negative
         */
        public FormResult {
            var measured = new EnumMap<Topic, TopicResult>(Topic.class);
            measured.putAll(Objects.requireNonNull(topics, "topics"));
            if (measured.isEmpty()) {
                throw new IllegalArgumentException("A form measures at least one topic");
            }
            topics = Collections.unmodifiableMap(measured);
            if (items < 0) {
                throw new IllegalArgumentException("'items' must not be negative: " + items);
            }
            Objects.requireNonNull(totalTokens, "totalTokens");
            if (durationMs < 0) {
                throw new IllegalArgumentException("'duration_ms' must not be negative: " + durationMs);
            }
        }
    }

    /**
     * The agreement of one topic (Store Schemas § Role Qualification Record, {@code topicResult}).
     *
     * @param agreement        the percent of the topic's graded items answered right
     * @param constantBaseline the agreement of the best constant answerer over the same items
     * @param items            the graded items of the topic, ungraded ones excluded
     * @since 0.1
     */
    public record TopicResult(BigDecimal agreement, BigDecimal constantBaseline, int items) {

        /**
         * @throws IllegalArgumentException if a value lies outside {@code 0} to {@code 100}, or no item is measured
         */
        public TopicResult {
            require(agreement, "agreement");
            require(constantBaseline, "constant_baseline");
            if (items < 1) {
                throw new IllegalArgumentException("A topic measures at least one item: " + items);
            }
        }

        private static void require(BigDecimal value, String field) {
            Objects.requireNonNull(value, field);
            if (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IllegalArgumentException("'%s' lies outside 0 to 100: %s".formatted(field, value));
            }
        }
    }

    /**
     * One offered item and run of a grading (Store Schemas § Role Qualification Record, {@code items}).
     *
     * @param itemId     the corpus item
     * @param form       the form it was offered in
     * @param checkout   where it ran
     * @param answer     the submitted answer; the JSON {@code null} when the task closed without one
     * @param score      the grade
     * @param totalTokens the tokens the answer used
     * @param durationMs how long the answer took; {@code null} when unknown
     * @since 0.1
     */
    public record ItemResult(String itemId, RoleForm form, Checkout checkout, JsonNode answer, Grade score,
    TokenTotal totalTokens, Long durationMs) {

        /**
         * @throws IllegalArgumentException if the item carries no id or a negative duration
         */
        public ItemResult {
            Objects.requireNonNull(itemId, "itemId");
            if (itemId.isEmpty()) {
                throw new IllegalArgumentException("'item_id' must not be empty");
            }
            Objects.requireNonNull(form, "form");
            Objects.requireNonNull(checkout, "checkout");
            Objects.requireNonNull(answer, "answer");
            Objects.requireNonNull(score, "score");
            Objects.requireNonNull(totalTokens, "totalTokens");
            if (durationMs != null && durationMs < 0) {
                throw new IllegalArgumentException("'duration_ms' must not be negative: " + durationMs);
            }
        }
    }
}
