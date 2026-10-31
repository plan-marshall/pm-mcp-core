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

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.store.IntentRecord;

/**
 * The result of the pre-submission self-review over the plan's intent record (Phase Workflows § Finalize Step Ordering
 * Bands, {@code pre-submission-self-review}; the former implementation-watch item `PM-WATCH-PHASE-111`, retired by this
 * change): one result per criterion {@code AC-<n>}, constraint
 * {@code CN-<n>}, non-goal {@code NG-<n>} and decision {@code DC-<n>} of the record, beside the criteria.
 * <p>
 * A submission that omits one of them is refused, its detail naming the item: a constraint rules out what no
 * criterion states, so a review that judges the criteria alone passes a plan that meets every criterion and breaks a
 * constraint. A {@code respected} constraint and a {@code followed} decision carry their evidence; a
 * {@code not_verified} result is a non-blocking finding, so a round that holds one is {@code clean_unverified}, never
 * clean.
 *
 * @param results one result per judged item
 * @since 0.1
 */
public record SelfReviewSubmission(List<ItemResult> results) {

    /** The kinds of item a result covers. */
    public enum ItemKind {
        /** An acceptance criterion {@code AC-<n>}. */
        CRITERION,
        /** A constraint {@code CN-<n>}. */
        CONSTRAINT,
        /** A non-goal {@code NG-<n>}. */
        NON_GOAL,
        /** A decision {@code DC-<n>}. */
        DECISION
    }

    /** The verdict of one item. */
    public enum Verdict {
        /** A criterion the change holds. */
        MET("met"),
        /** A criterion the change misses. */
        NOT_MET("not_met"),
        /** A constraint the change keeps. */
        RESPECTED("respected"),
        /** A constraint the change breaks. */
        VIOLATED("violated"),
        /** A non-goal the change keeps. */
        CROSSED("crossed"),
        /** A decision the change follows. */
        FOLLOWED("followed"),
        /** A decision the change departs from. */
        DEPARTED("departed"),
        /** The review could not judge the item. */
        NOT_VERIFIED("not_verified");

        private final String wireName;

        Verdict(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the verdict in a submission */
        public String wireName() {
            return wireName;
        }

        /** @return whether the verdict is a finding */
        public boolean isFinding() {
            return this == NOT_MET || this == VIOLATED || this == CROSSED || this == DEPARTED;
        }
    }

    /** The round's outcome. */
    public enum Status {
        /** No finding and no unverified item. */
        CLEAN,
        /** No finding, at least one {@code not_verified} item: a non-blocking finding. */
        CLEAN_UNVERIFIED,
        /** At least one finding: the round is not clean and blocks. */
        FINDINGS
    }

    /**
     * One item's result.
     *
     * @param itemId  the identifier of the record's item
     * @param verdict the verdict
     * @param evidence for {@code respected} and {@code followed}, the evidence; may be {@code null} otherwise
     * @since 0.1
     */
    public record ItemResult(String itemId, Verdict verdict, @Nullable String evidence) {

        /**
         * @throws NullPointerException if a value is {@code null}
         */
        public ItemResult {
            Objects.requireNonNull(itemId, "itemId");
            Objects.requireNonNull(verdict, "verdict");
        }
    }

    /**
     * The round's results.
     *
     * @param results one result per judged item
     * @since 0.1
     */
    public record Round(List<ItemResult> results) {

        /**
         * @throws NullPointerException if a value is {@code null}
         */
        public Round {
            Objects.requireNonNull(results, "results");
            results = List.copyOf(results);
        }

        /** @return the results that are findings */
        public List<ItemResult> findings() {
            return results.stream().filter(result -> result.verdict().isFinding()).toList();
        }

        /** @return the results the review could not judge */
        public List<ItemResult> unverified() {
            return results.stream().filter(result -> result.verdict() == Verdict.NOT_VERIFIED).toList();
        }

        /** @return whether the round blocks */
        public Status status() {
            if (!findings().isEmpty()) {
                return Status.FINDINGS;
            }
            return unverified().isEmpty() ? Status.CLEAN : Status.CLEAN_UNVERIFIED;
        }

        /** @return whether the round is clean, with or without unverified items */
        public boolean clean() {
            return status() != Status.FINDINGS;
        }
    }

    /** The outcome of a submission. */
    public sealed interface Outcome permits Accepted, Refused {
    }

    /**
     * The submission covers every item of the record.
     *
     * @param round the round's results
     * @since 0.1
     */
    public record Accepted(Round round) implements Outcome {

        /**
         * @throws NullPointerException if the round is {@code null}
         */
        public Accepted {
            Objects.requireNonNull(round, "round");
        }
    }

    /**
     * The submission cannot be read against the record.
     *
     * @param detail the reason, naming the item
     * @since 0.1
     */
    public record Refused(String detail) implements Outcome {

        /**
         * @throws NullPointerException if the detail is {@code null}
         */
        public Refused {
            Objects.requireNonNull(detail, "detail");
        }
    }

    /**
     * @throws NullPointerException if the list is {@code null}
     */
    public SelfReviewSubmission {
        Objects.requireNonNull(results, "results");
        results = List.copyOf(results);
    }

    /**
     * Checks a submission against the record and derives the round's outcome.
     *
     * @param submission   the submitted results
     * @param intentRecord the record the plan carries
     * @return the accepted round, or the refusal naming the item
     */
    public static Outcome validate(SelfReviewSubmission submission, IntentRecord intentRecord) {
        Objects.requireNonNull(submission, "submission");
        Objects.requireNonNull(intentRecord, "intentRecord");
        var seen = new HashSet<String>();
        for (var result : submission.results()) {
            var refusal = refusal(result, intentRecord, seen);
            if (refusal.isPresent()) {
                return new Refused(refusal.get());
            }
        }
        var missing = missingItem(intentRecord, seen);
        if (missing.isPresent()) {
            return new Refused("the self-review omits the item '%s'".formatted(missing.get()));
        }
        return new Accepted(new Round(submission.results()));
    }

    /**
     * @param id an item identifier
     * @return the kind of the item; empty when the identifier is none of the record's
     */
    public static Optional<ItemKind> kindOf(@Nullable String id) {
        if (id == null) {
            return Optional.empty();
        }
        return switch (id.substring(0, Math.min(3, id.length()))) {
            case "AC-" -> Optional.of(ItemKind.CRITERION);
            case "CN-" -> Optional.of(ItemKind.CONSTRAINT);
            case "NG-" -> Optional.of(ItemKind.NON_GOAL);
            case "DC-" -> Optional.of(ItemKind.DECISION);
            default -> Optional.empty();
        };
    }

    private static Optional<String> refusal(ItemResult result, IntentRecord intentRecord, HashSet<String> seen) {
        if (!seen.add(result.itemId())) {
            return Optional.of("the item '%s' is judged more than once".formatted(result.itemId()));
        }
        if (!intentRecord.holds(result.itemId())) {
            return Optional.of("the result names '%s', which the record does not hold"
                    .formatted(result.itemId()));
        }
        var kind = kindOf(result.itemId());
        if (kind.isEmpty()) {
            return Optional.of("the result names '%s', which is no criterion, constraint, non-goal or decision"
                    .formatted(result.itemId()));
        }
        if (!admitted(kind.orElseThrow(), result.verdict())) {
            return Optional.of("the %s '%s' cannot be '%s'".formatted(kind.orElseThrow().name().toLowerCase(
                    Locale.ROOT), result.itemId(), result.verdict().wireName()));
        }
        var needsEvidence = kind.orElseThrow() == ItemKind.CRITERION && result.verdict() == Verdict.MET
                || kind.orElseThrow() == ItemKind.CONSTRAINT && result.verdict() == Verdict.RESPECTED
                || kind.orElseThrow() == ItemKind.DECISION && result.verdict() == Verdict.FOLLOWED;
        if (needsEvidence && (result.evidence() == null || result.evidence().isBlank())) {
            return Optional.of("the %s '%s' is '%s' and names its evidence".formatted(
                    kind.orElseThrow().name().toLowerCase(Locale.ROOT), result.itemId(),
                    result.verdict().wireName()));
        }
        return Optional.empty();
    }

    private static boolean admitted(ItemKind kind, Verdict verdict) {
        return switch (kind) {
            case CRITERION -> verdict == Verdict.MET || verdict == Verdict.NOT_MET
                    || verdict == Verdict.NOT_VERIFIED;
            case CONSTRAINT -> verdict == Verdict.RESPECTED || verdict == Verdict.VIOLATED
                    || verdict == Verdict.NOT_VERIFIED;
            case NON_GOAL -> verdict == Verdict.RESPECTED || verdict == Verdict.CROSSED
                    || verdict == Verdict.NOT_VERIFIED;
            case DECISION -> verdict == Verdict.FOLLOWED || verdict == Verdict.DEPARTED
                    || verdict == Verdict.NOT_VERIFIED;
        };
    }

    private static Optional<String> missingItem(IntentRecord intentRecord, HashSet<String> seen) {
        var ids = new ArrayList<String>();
        intentRecord.criteria().forEach(criterion -> ids.add(criterion.id()));
        intentRecord.constraints().forEach(constraint -> ids.add(constraint.id()));
        intentRecord.nonGoals().forEach(nonGoal -> ids.add(nonGoal.id()));
        intentRecord.decisions().forEach(decision -> ids.add(decision.id()));
        return ids.stream().filter(id -> !seen.contains(id)).findFirst();
    }
}
