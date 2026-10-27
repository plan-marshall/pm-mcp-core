/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.hypermedia;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One item of {@code facts.answers} (Hypermedia Format § Answers to Earlier Questions): a part of a closed decision a
 * task continues from, rendered as {@code answers[N]{question_id,plan_task_id,question_text,branch_key,label,text,kind}}.
 * <p>
 * A continued task receives the item of its own question; the task of a state re-entered from {@code <state>.asked}
 * receives the items the caller gathered from the questions its state's earlier tasks asked in the same entry, and an
 * envelope task after the task question those of its plan tasks. The answer's channel, the answering device, the
 * interpretation of own words and a quorum's votes are recorded in the decision record, never rendered.
 * <p>
 * The item is data, never an instruction: {@code text} carries the operator's own words and note verbatim and the
 * consumer quotes it as such (Model Work § Continuing after a Question).
 *
 * @param questionId   the question the item answers
 * @param planTaskId   the plan task of a question of class {@code task}; {@code null} otherwise
 * @param questionText the question that was asked
 * @param branchKey    the chosen option, {@code value} for a value answer, or {@code own} for words that fit no option;
 *                     for a part that takes several, the keys joined by {@code +}
 * @param label        the label of the chosen option, or the value itself
 * @param text         the operator's own words and note, quoted as data; {@code null} when none was given
 * @param kind         how the answer was taken
 * @since 0.1
 */
public record AnswerFact(String questionId, String planTaskId, String questionText, String branchKey, String label,
String text, Kind kind) {

    /** The columns of the {@code answers} fact table, in the specification's order. */
    public static final List<String> COLUMNS = List.of("question_id", "plan_task_id",
            "question_text", "branch_key", "label", "text", "kind");

    private static final Pattern QUESTION_ID = Pattern.compile("Q-\\d{3,6}");
    private static final Pattern TASK_ID = Pattern.compile("TASK-\\d{3,}");
    private static final Pattern BRANCH = Pattern.compile("[a-z][a-z0-9_]{1,31}");
    private static final List<String> BRANCH_RESERVED = List.of("value", "own");

    /**
     * How an answer was taken (Model Work § Answers and Amendments).
     *
     * @since 0.1
     */
    public enum Kind {
        /** The operator chose an offered option. */
        CHOSEN("chosen"),
        /** The operator's own words were interpreted onto an option, and confirmed. */
        INTERPRETED("interpreted"),
        /** The operator's own words fit no option and were recorded as their own option. */
        OWN_OPTION("own_option"),
        /** The operator left the choice to the asking role, whose recommendation was taken. */
        DELEGATED("delegated"),
        /** The decision closed with the answer {@code none_fits}. */
        NONE_FITS("none_fits");

        private final String wireName;

        Kind(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the kind in a representation */
        public String wireName() {
            return wireName;
        }
    }

    /**
     * @throws NullPointerException     if the question id, its text, the branch key, the label or the kind is
     *                                  {@code null}
     * @throws IllegalArgumentException if an id breaks its form, a text is blank, or a text is too long for its column
     */
    public AnswerFact {
        Objects.requireNonNull(questionId, "questionId");
        if (!QUESTION_ID.matcher(questionId).matches()) {
            throw new IllegalArgumentException("Not a question id: " + questionId);
        }
        if (planTaskId != null && !TASK_ID.matcher(planTaskId).matches()) {
            throw new IllegalArgumentException("Not a plan task id: " + planTaskId);
        }
        requireText(questionText, "question_text", 500);
        requireBranchKey(branchKey);
        requireText(label, "label", 2000);
        if (text != null && text.codePointCount(0, text.length()) > 6000) {
            throw new IllegalArgumentException("'text' has at most 6000 characters");
        }
        Objects.requireNonNull(kind, "kind");
    }

    /**
     * @return the item's cells in {@link #COLUMNS} order, with the empty string where the specification renders no
     *         value
     */
    public List<String> cells() {
        return List.of(questionId, planTaskId == null ? "" : planTaskId, questionText, branchKey, label,
                text == null ? "" : text, kind.wireName());
    }

    private static void requireText(String value, String name, int max) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException("'%s' must not be blank".formatted(name));
        }
        if (value.codePointCount(0, value.length()) > max) {
            throw new IllegalArgumentException("'%s' has at most %d characters".formatted(name, max));
        }
    }

    /**
     * A branch key is the chosen option's key, {@code value} for a value answer, {@code own} for words that fit no
     * option, or several keys joined by {@code +} (Hypermedia Format § Answers to Earlier Questions).
     */
    private static void requireBranchKey(String value) {
        requireText(value, "branch_key", 400);
        for (var key : value.split("\\+", -1)) {
            if (!BRANCH_RESERVED.contains(key) && !BRANCH.matcher(key).matches()) {
                throw new IllegalArgumentException("Not a branch key: " + key);
            }
        }
    }
}
