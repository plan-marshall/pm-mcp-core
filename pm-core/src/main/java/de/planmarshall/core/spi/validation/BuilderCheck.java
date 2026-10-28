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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.store.CapabilityIndex;
import de.planmarshall.core.store.IntentRecord;
import de.planmarshall.core.store.IntentRecord.Decider;
import de.planmarshall.core.store.IntentRecord.Decision;

/**
 * The submission of the builder check, {@code submit:builder-check} (Phase Workflows § Builder Check;
 * PM-WATCH-PHASE-109, and the former `PM-WATCH-PHASE-111`, retired by this change): every question a builder of the
 * plan would still need answered, each sorted.
 * <p>
 * The server checks what it can decide: a question that names a constraint {@code CN-<n>} or two criteria is always the
 * operator's, because whether the two items can both hold and whether a default the operator accepted permits what the
 * constraint rules out are the operator's judgment, never the builder's. A {@code minor} answer is the builder's own
 * call and becomes a decision of decider {@code model}, marked as the builder check's, in a new revision.
 * <p>
 * The facts of the check carry the intent record and the capability index only: no member of {@code request.md}'s
 * original input or clarifications and no earlier submission reaches the task.
 *
 * @since 0.1
 */
public final class BuilderCheck {

    /** How the builder sorted one question. */
    public enum Sort {
        /** The record answers it, and the entry names the item. */
        ANSWERED("answered"),
        /** The builder's own call, with the decision it would take. */
        MINOR("minor"),
        /** Only the operator can answer it. */
        OPERATOR("operator");

        private final String wireName;

        Sort(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the sort in the submission */
        public String wireName() {
            return wireName;
        }
    }

    /**
     * One question of the check.
     *
     * @param sort     how the builder sorted it
     * @param text     the question, 5 to 2000 characters
     * @param items    the identifiers of the record the question names
     * @param decision for {@code minor}, the decision the builder would take; {@code null} otherwise
     * @since 0.1
     */
    public record Question(Sort sort, String text, List<String> items, String decision) {

        /**
         * @throws IllegalArgumentException if a member is out of its bounds
         * @throws NullPointerException     if a value is {@code null}
         */
        public Question {
            Objects.requireNonNull(sort, "sort");
            Objects.requireNonNull(text, "text");
            if (text.length() < 5 || text.length() > 2000) {
                throw new IllegalArgumentException("a question is 5 to 2000 characters: " + text.length());
            }
            Objects.requireNonNull(items, "items");
            items = List.copyOf(items);
            if (items.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("a question holds a null item");
            }
            if (sort == Sort.MINOR && (decision == null || decision.length() < 5 || decision.length() > 1000)) {
                throw new IllegalArgumentException("a minor question carries its decision, 5 to 1000 characters");
            }
            if (sort != Sort.MINOR && decision != null) {
                throw new IllegalArgumentException("only a minor question carries a decision");
            }
        }
    }

    /**
     * The check's one submission.
     *
     * @param questions the questions, in the order the builder sorts them
     * @since 0.1
     */
    public record Submission(List<Question> questions) {

        /**
         * @throws NullPointerException if a value is {@code null}
         */
        public Submission {
            Objects.requireNonNull(questions, "questions");
            questions = List.copyOf(questions);
        }
    }

    /** The outcome of a check. */
    public sealed interface Outcome permits Accepted, Refused {
    }

    /**
     * The submission is consistent with the record.
     *
     * @param intentRecord      the record with the builder's own calls added; the same instance when it made none
     * @param operatorQuestions the questions only the operator can answer, in order
     * @since 0.1
     */
    public record Accepted(IntentRecord intentRecord, List<Question> operatorQuestions) implements Outcome {

        /**
         * @throws NullPointerException if a value is {@code null}
         */
        public Accepted {
            Objects.requireNonNull(intentRecord, INTENT_RECORD);
            operatorQuestions = List.copyOf(operatorQuestions);
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

    private BuilderCheck() {
    }

    /**
     * The longest minor decision the record's decision text holds beside the marker: the record's 500-character text
     * bound less the 24 characters of "from the builder check: " and a margin.
     */
    private static final int MAX_MINOR_DECISION = 400;

    /** The name of the record parameter, as a null refusal names it. */
    private static final String INTENT_RECORD = "intentRecord";

    /**
     * Checks a submission against the record, adding the builder's own calls as model decisions.
     *
     * @param submission   the check's submission
     * @param intentRecord the record the plan will carry
     * @return the accepted record with the builder's own calls, or the refusal naming the item
     */
    public static Outcome validate(Submission submission, IntentRecord intentRecord) {
        Objects.requireNonNull(submission, "submission");
        Objects.requireNonNull(intentRecord, INTENT_RECORD);
        for (var question : submission.questions()) {
            var refusal = refusal(question, intentRecord);
            if (refusal.isPresent()) {
                return new Refused(refusal.get());
            }
        }
        var operatorQuestions = submission.questions().stream().filter(question -> question.sort() == Sort.OPERATOR)
                .toList();
        return new Accepted(withMinorCalls(intentRecord, submission), operatorQuestions);
    }

    /**
     * The facts the check's task receives: the intent record and the capability index, and nothing of the conversation
     * that produced the record (PM-WATCH-PHASE-109).
     *
     * @param intentRecord the record
     * @param capabilities the project's capability index
     * @return the facts, by their names
     */
    public static Map<String, Object> facts(IntentRecord intentRecord, CapabilityIndex capabilities) {
        Objects.requireNonNull(intentRecord, INTENT_RECORD);
        Objects.requireNonNull(capabilities, "capabilities");
        var facts = new LinkedHashMap<String, Object>();
        facts.put("intent_record", intentRecord);
        facts.put("capability_index", capabilities.facts());
        return Map.copyOf(facts);
    }

    private static Optional<String> refusal(Question question, IntentRecord intentRecord) {
        for (var item : question.items()) {
            if (!intentRecord.holds(item)) {
                return Optional.of("the question names '%s', which the record does not hold"
                        .formatted(item));
            }
        }
        if (question.sort() == Sort.ANSWERED && question.items().size() != 1) {
            return Optional.of("an answered question names exactly one item of the record");
        }
        if (question.sort() == Sort.MINOR && question.decision().length() > MAX_MINOR_DECISION) {
            return Optional.of(("the minor decision is longer than %d characters, so the decision marked as the "
                    + "builder check's would exceed the record's decision text").formatted(MAX_MINOR_DECISION));
        }
        var constraints = question.items().stream().filter(item -> item.startsWith("CN-")).count();
        var criteria = question.items().stream().filter(item -> item.startsWith("AC-")).count();
        if (question.sort() == Sort.MINOR && (constraints > 0 || criteria > 1)) {
            return Optional.of(
                    "the question names a constraint or more than one criterion and is the operator's, never a minor "
                            + "call: %s".formatted(question.items()));
        }
        return Optional.empty();
    }

    private static IntentRecord withMinorCalls(IntentRecord intentRecord, Submission submission) {
        var minor = submission.questions().stream().filter(question -> question.sort() == Sort.MINOR).toList();
        if (minor.isEmpty()) {
            return intentRecord;
        }
        var next = intentRecord.decisions().stream()
                .filter(decision -> decision.id().startsWith("DC-"))
                .mapToInt(decision -> Integer.parseInt(decision.id().substring(3)))
                .max()
                .orElse(0) + 1;
        var decisions = new ArrayList<>(intentRecord.decisions());
        for (var question : minor) {
            decisions.add(new Decision("DC-" + next++, "from the builder check: " + question.decision(),
                    List.of(), Decider.MODEL, null, null, null));
        }
        return new IntentRecord(intentRecord.why(), intentRecord.goal(), intentRecord.criteria(),
                intentRecord.currentBehaviour(), intentRecord.nonGoals(), intentRecord.constraints(), decisions,
                intentRecord.assumptions(), intentRecord.openQuestions(), intentRecord.deferred(),
                intentRecord.sourceClaims());
    }
}
