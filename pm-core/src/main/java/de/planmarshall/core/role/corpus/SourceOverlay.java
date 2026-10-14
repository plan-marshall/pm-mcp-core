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

import static de.planmarshall.core.role.corpus.SourceManifest.NAME;
import static de.planmarshall.core.role.corpus.SourceManifest.RELATIVE_PATH;
import static de.planmarshall.core.role.corpus.SourceManifest.require;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import de.planmarshall.core.role.corpus.ImportedItem.Grading;
import de.planmarshall.core.role.corpus.ImportedItem.Review;
import de.planmarshall.core.role.corpus.ImportedItem.Target;

/**
 * The overlay of one corpus source, {@code sources/<name>/overlay.json} (PM-WORK-6): the rows of one file of the
 * source that become items, and per row what the item adds to it.
 * <p>
 * A row is selected by the value of its key field, or by its position among the rows of the file when the overlay
 * names no key field. Per row the overlay writes the framing of the item (slug, topic, role, title, tags), the
 * question, the facts and the choices as templates whose {@code {field}} placeholders name fields of the row, the
 * written reference answer with its rubric items, the grading and the review. The record refuses every value outside
 * its format with an {@link IllegalArgumentException}; {@link SourceOverlayCodec} reads and writes the document.
 *
 * @param source   the name of the source, the name of its manifest
 * @param file     the repository-relative path of the file the rows are read from, a file of the manifest
 * @param keyField the field whose value selects a row; {@code null} selects rows by their position, from 1
 * @param created  the date the overlay was written, the creation date of its items
 * @param rows     the selected rows, at least one; unmodifiable
 * @since 0.1
 */
public record SourceOverlay(String source, String file, String keyField, LocalDate created, List<Row> rows) {

    /** The key of a row selected by its position among the rows of the file, from 1. */
    public static final Pattern POSITION = Pattern.compile("^[1-9][0-9]*$");

    /**
     * One selected row and the item generated from it.
     *
     * @param key      the value of the key field of the row, or its position when the overlay names no key field
     * @param slug     the slug of the item: its file name below the topic without {@code .json}
     * @param topic    the topic of the item, the directory it belongs to
     * @param role     the configured role that answers the item
     * @param title    the title of the item, at most 120 characters
     * @param tags     the tags of the item; unmodifiable, empty if there are none
     * @param question the template of the question
     * @param facts    the template of the facts
     * @param choices  the templates of the choices of a closed answer; {@code null} for an open answer
     * @param target   the written reference answer
     * @param grading  how an answer is graded
     * @param review   who reviewed the reference
     * @since 0.1
     */
    public record Row(String key, String slug, String topic, String role, String title, List<String> tags,
    String question, String facts, List<String> choices, Target target, Grading grading, Review review) {

        /**
         * @throws IllegalArgumentException if a value is outside the format
         */
        public Row {
            Objects.requireNonNull(key, "key");
            if (key.isEmpty()) {
                throw new IllegalArgumentException("'key' must not be empty");
            }
            require(slug, ImportedItem.SLUG, "id");
            require(topic, ImportedItem.TOPIC, "topic");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(title, "title");
            if (title.codePointCount(0, title.length()) > ImportedItem.TITLE_MAX_LENGTH) {
                throw new IllegalArgumentException(
                        "'title' is longer than %d characters".formatted(ImportedItem.TITLE_MAX_LENGTH));
            }
            tags = List.copyOf(Objects.requireNonNull(tags, "tags"));
            Objects.requireNonNull(question, "question");
            Objects.requireNonNull(facts, "facts");
            if (choices != null) {
                choices = List.copyOf(choices);
                if (choices.size() < 2) {
                    throw new IllegalArgumentException("'choices' must name at least two choices");
                }
            }
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(grading, "grading");
            Objects.requireNonNull(review, "review");
        }

        /** @return the id of the item generated from the row: {@code <topic>/<slug>} */
        public String itemId() {
            return topic + "/" + slug;
        }
    }

    /**
     * @throws IllegalArgumentException if a value is outside the format, or a key or an item id repeats
     */
    public SourceOverlay {
        require(source, NAME, "source");
        require(file, RELATIVE_PATH, "file");
        if (keyField != null && keyField.isEmpty()) {
            throw new IllegalArgumentException("'key_field' must not be empty");
        }
        Objects.requireNonNull(created, "created");
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("'rows' must select at least one row");
        }
        var keys = new HashSet<String>();
        var ids = new HashSet<String>();
        for (var row : rows) {
            if (keyField == null && !POSITION.matcher(row.key()).matches()) {
                throw new IllegalArgumentException(
                        "'key' must be a position from 1 without a key field: " + row.key());
            }
            if (!keys.add(row.key())) {
                throw new IllegalArgumentException("'rows' selects the row '%s' twice".formatted(row.key()));
            }
            if (!ids.add(row.itemId())) {
                throw new IllegalArgumentException("'rows' generates the item '%s' twice".formatted(row.itemId()));
            }
        }
    }
}
