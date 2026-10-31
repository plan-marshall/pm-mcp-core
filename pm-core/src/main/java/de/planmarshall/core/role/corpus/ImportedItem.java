/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.corpus;

import static de.planmarshall.core.role.corpus.SourceManifest.COMMIT_PATTERN;
import static de.planmarshall.core.role.corpus.SourceManifest.RELATIVE_PATH;
import static de.planmarshall.core.role.corpus.SourceManifest.require;
import static de.planmarshall.core.role.corpus.SourceManifest.requireRepository;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A corpus item generated from one row of a fetched source (PM-WORK-6): the members of the corpus item format, with
 * the provenance of the row it was generated from.
 * <p>
 * The imported item has the format of a corpus item, tightened: {@code provenance.kind} is {@code fetched}, and
 * {@code provenance.origin} names the repository, the pinned commit, the path and the line of the row. The origin
 * repository follows the rule of the manifest's repository (an absolute {@code https} URI without user information,
 * query or fragment), so every source a manifest names produces items this format accepts. Every optional
 * member of the corpus item format has one shape only: an absent list is empty and a present one is not,
 * {@code expected_empty} is present only when {@code true}, an absent group or contamination note is {@code null} and a
 * present one holds a character other than a line feed. The group and the contamination note are literal text copied
 * from the overlay row, never templates. Members the generation never writes (context files, the location of a rubric
 * item) are not part of it. So an imported item is accepted by the item schema of the corpus and by
 * {@code imported-item.schema.json} of this package; {@link ImportedItemCodec} reads and writes it. Each pattern of the
 * record is the pattern of the schema, compiled with {@link PythonRegex} and matched against the whole value, as
 * {@link SourceManifest} does; only {@link #TEXT_PATTERN} is found anywhere in the value, as the schema's {@code .} is.
 *
 * @param id            {@code <topic>/<slug>}, the path of the item below the corpus without {@code .json}
 * @param topic         the directory of the item
 * @param role          the configured role that answers the item
 * @param title         the title, at most 120 characters
 * @param tags          the tags; unmodifiable, empty if there are none
 * @param group         the group the item belongs to, literal text; {@code null} if it belongs to none
 * @param input         what the model sees
 * @param target        the reference answer
 * @param grading       how an answer is graded
 * @param provenance    where the item comes from
 * @param review        who reviewed the reference
 * @param contamination the contamination note, literal text; {@code null} if there is none
 * @param canary        the corpus canary
 * @since 0.1
 */
public record ImportedItem(String id, String topic, String role, String title, List<String> tags, String group,
Input input, Target target, Grading grading, Provenance provenance, Review review, String contamination,
String canary) {

    /** An item id: a topic path and a slug. */
    public static final Pattern ID_PATTERN = PythonRegex.compile("^[a-z0-9][a-z0-9-]*(/[a-z0-9][a-z0-9-]*)+$");

    /** A topic: the directory of an item below the corpus root. */
    public static final Pattern TOPIC_PATTERN = PythonRegex.compile("^[a-z0-9-]+(/[a-z0-9-]+)*$");

    /** The slug of an item: the file name below its topic, without {@code .json}. */
    public static final Pattern SLUG_PATTERN = PythonRegex.compile("^[a-z0-9][a-z0-9-]*$");

    /** The longest title, in characters. */
    public static final int TITLE_MAX_LENGTH = 120;

    /**
     * A group and a contamination note, of an item and of an overlay row: the schema pattern {@code .} found anywhere
     * in the value with Python's meaning, so a present value holds at least one character other than {@code \n}.
     */
    public static final Pattern TEXT_PATTERN = PythonRegex.compile(".");

    /**
     * What the model sees.
     *
     * @param question     the question
     * @param facts        exactly the text the model sees besides the question
     * @param choices      the choices of a closed answer, at least two; {@code null} for an open answer
     * @param answerSchema the JSON Schema the answer of the item is checked against, a JSON object; {@code null} if
     *                     there is none
     * @since 0.1
     */
    public record Input(String question, String facts, @Nullable List<String> choices,
    @Nullable JsonValue answerSchema) {

        /**
         * @throws IllegalArgumentException if fewer than two choices are given
         */
        public Input {
            Objects.requireNonNull(question, "question");
            Objects.requireNonNull(facts, "facts");
            if (choices != null) {
                choices = List.copyOf(choices);
                if (choices.size() < 2) {
                    throw new IllegalArgumentException("'choices' must name at least two choices");
                }
            }
            if (answerSchema != null && !(answerSchema instanceof JsonValue.JsonObject)) {
                throw new IllegalArgumentException(
                        "'answer_schema' must be an object or null: " + answerSchema.toJson());
            }
        }
    }

    /**
     * The reference answer.
     *
     * @param answer        a choice, a label, or the expected action of an open answer; {@code null} if there is none
     * @param rationale     why the answer is the reference
     * @param alternatives  other answers the reviewer accepts; unmodifiable, empty if there are none
     * @param items         the rubric items an open answer must meet; unmodifiable, empty if there are none
     * @param expectedEmpty {@code true} for a control: the correct answer reports nothing
     * @since 0.1
     */
    public record Target(@Nullable String answer, String rationale, List<String> alternatives,
    List<RubricItem> items, boolean expectedEmpty) {

        /**
         * Copies the lists.
         */
        public Target {
            Objects.requireNonNull(rationale, "rationale");
            alternatives = List.copyOf(Objects.requireNonNull(alternatives, "alternatives"));
            items = List.copyOf(Objects.requireNonNull(items, "items"));
        }
    }

    /**
     * One rubric item an open answer must meet: a seeded defect or an essential property.
     *
     * @param id          the id of the item, unique in its item
     * @param description what the answer must find or meet
     * @since 0.1
     */
    public record RubricItem(String id, String description) {

        /**
         * Requires both values.
         */
        public RubricItem {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(description, "description");
        }
    }

    /**
     * How an answer is graded.
     *
     * @param method        the grading method
     * @param judge         the judge instruction or model of a rubric method; {@code null} if there is none
     * @param passThreshold the share of the score that passes, between 0 and 1; {@code null} if there is none
     * @since 0.1
     */
    public record Grading(GradingMethod method, String judge, @Nullable BigDecimal passThreshold) {

        /**
         * @throws IllegalArgumentException if the threshold is outside 0 to 1
         */
        public Grading {
            Objects.requireNonNull(method, "method");
            if (passThreshold != null
                    && (passThreshold.signum() < 0 || passThreshold.compareTo(BigDecimal.ONE) > 0)) {
                throw new IllegalArgumentException("'pass_threshold' must be between 0 and 1: " + passThreshold);
            }
        }
    }

    /**
     * The grading method of an item. The set is closed.
     *
     * @since 0.1
     */
    public enum GradingMethod {
        /** The answer is compared with the reference choice. */
        EXACT_CHOICE("exact_choice"),
        /** The answer is compared with the reference label. */
        LABEL("label"),
        /** The answer scores the share of the rubric items it finds; a control scores 1 only when empty. */
        RUBRIC_ITEMS("rubric_items"),
        /** The answer scores the share of the rubric items it meets. */
        RUBRIC_PROPERTIES("rubric_properties");

        private final String wireName;

        GradingMethod(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the method in a document */
        public String wireName() {
            return wireName;
        }

        /** @return {@code true} for a method that compares the answer with one of the choices */
        public boolean isClosed() {
            return this == EXACT_CHOICE || this == LABEL;
        }

        /**
         * @param wireName the name of a method in a document
         * @return the method; empty if the name is not one
         */
        public static Optional<GradingMethod> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(method -> method.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * Where an imported item comes from. Its kind is always {@code fetched}.
     *
     * @param created the date the item was generated from its row
     * @param origin  the row the item was generated from
     * @param history how the item came about, at least one dated entry; unmodifiable
     * @since 0.1
     */
    public record Provenance(LocalDate created, Origin origin, List<String> history) {

        /** The provenance kind of every imported item. */
        public static final String KIND = "fetched";

        /**
         * @throws IllegalArgumentException if the history is empty
         */
        public Provenance {
            Objects.requireNonNull(created, "created");
            Objects.requireNonNull(origin, "origin");
            history = List.copyOf(Objects.requireNonNull(history, "history"));
            if (history.isEmpty()) {
                throw new IllegalArgumentException("'history' must have at least one entry");
            }
        }
    }

    /**
     * The row of a fetched source an item was generated from.
     *
     * @param repository the repository of the source
     * @param commit     the pinned commit of the source
     * @param path       the repository-relative path of the file
     * @param line       the line of the row in the file, from 1
     * @since 0.1
     */
    public record Origin(URI repository, String commit, String path, int line) {

        /**
         * @throws IllegalArgumentException if a value is outside the format
         */
        public Origin {
            requireRepository(repository);
            require(commit, COMMIT_PATTERN, "commit");
            require(path, RELATIVE_PATH, "path");
            if (line < 1) {
                throw new IllegalArgumentException("'line' must be at least 1: " + line);
            }
        }
    }

    /**
     * Who reviewed the reference.
     *
     * @param status the review status
     * @param by     who reviewed it; {@code null} if not named
     * @param date   when it was reviewed; {@code null} if not named
     * @param notes  notes of the review; {@code null} if there are none
     * @since 0.1
     */
    public record Review(ReviewStatus status, String by, @Nullable LocalDate date, String notes) {

        /**
         * Requires the status.
         */
        public Review {
            Objects.requireNonNull(status, "status");
        }
    }

    /**
     * The review status of an item. The set is closed.
     *
     * @since 0.1
     */
    public enum ReviewStatus {
        /** The reference is not confirmed yet. */
        DRAFT("draft"),
        /** The reference is confirmed. */
        CONFIRMED("confirmed"),
        /** The reference is disputed. */
        CONTESTED("contested"),
        /** The item is no longer used. */
        RETIRED("retired");

        private final String wireName;

        ReviewStatus(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the status in a document */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a status in a document
         * @return the status; empty if the name is not one
         */
        public static Optional<ReviewStatus> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(status -> status.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * @throws IllegalArgumentException if a value is outside the format, the id is not below the topic, or two rubric
     *                                  items share an id
     */
    public ImportedItem {
        require(id, ID_PATTERN, "id");
        require(topic, TOPIC_PATTERN, "topic");
        if (!id.substring(0, id.lastIndexOf('/')).equals(topic)) {
            throw new IllegalArgumentException("'id' %s is not an item of the topic %s".formatted(id, topic));
        }
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(title, "title");
        if (title.codePointCount(0, title.length()) > TITLE_MAX_LENGTH) {
            throw new IllegalArgumentException("'title' is longer than " + TITLE_MAX_LENGTH + " characters");
        }
        tags = List.copyOf(Objects.requireNonNull(tags, "tags"));
        requireText(group, "group");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(grading, "grading");
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(review, "review");
        requireText(contamination, "contamination");
        Objects.requireNonNull(canary, "canary");
        var rubricIds = new HashSet<String>();
        for (var item : target.items()) {
            if (!rubricIds.add(item.id())) {
                throw new IllegalArgumentException("rubric item '%s' twice".formatted(item.id()));
            }
        }
    }

    /** @return the slug of the item: its id below the topic */
    public String slug() {
        return id.substring(id.lastIndexOf('/') + 1);
    }

    /**
     * Checks a group or a contamination note, of an item or of an overlay row.
     *
     * @param value the value; {@code null} if not given
     * @param field the name of the member, named by a refusal
     * @throws IllegalArgumentException if a present value holds no character other than a line feed
     */
    static void requireText(@Nullable String value, String field) {
        if (value != null && !TEXT_PATTERN.matcher(value).find()) {
            throw new IllegalArgumentException(
                    "'%s' must hold a character other than a line feed: %s".formatted(field, value));
        }
    }
}
