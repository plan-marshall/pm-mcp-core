/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The identifier of a lesson of the project lesson store (Store Schemas § Lesson Record, Identifiers §
 * {@code lesson_id}; PM-IMPL-2).
 * <p>
 * The identifier is the UTC date and hour of creation plus a per-hour sequence, {@code yyyy-MM-dd-HH-nnn}: the
 * sequence has at least three digits and grows, so an hour never runs out of identifiers. The store holds one file
 * {@code <lesson_id>.md} per lesson, and a file name that does not parse as an identifier is no lesson.
 * <p>
 * The hour prefix is the key the store allocates a new identifier against: it reads the sequence of the highest
 * identifier of the current hour and continues from it.
 *
 * @param value the identifier, matching {@value #PATTERN}
 * @since 0.1
 */
public record LessonId(String value) {

    /** The longest identifier the schema admits. */
    public static final int MAX_LENGTH = 64;

    /** The grammar of the identifier, as the schema states it. */
    public static final String PATTERN = "^[0-9]{4}-[0-9]{2}-[0-9]{2}-[0-9]{2}-[0-9]{3,}$";

    /** The suffix of the file a lesson is stored in. */
    public static final String FILE_SUFFIX = ".md";

    /** The length of the UTC hour prefix {@code yyyy-MM-dd-HH}. */
    public static final int HOUR_PREFIX_LENGTH = 13;

    private static final Pattern GRAMMAR = Pattern.compile(PATTERN);

    /**
     * @param value the identifier
     * @throws IllegalArgumentException if the value does not match the grammar of the schema or exceeds its length
     */
    public LessonId {
        Objects.requireNonNull(value, "value");
        if (value.length() > MAX_LENGTH || !GRAMMAR.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "A lesson id is a UTC date and hour followed by at least three digits, at most %d characters: '%s'"
                            .formatted(MAX_LENGTH, value));
        }
    }

    /**
     * @param value a candidate identifier
     * @return the identifier; empty when the value does not parse
     */
    public static Optional<LessonId> parse(String value) {
        Objects.requireNonNull(value, "value");
        if (value.length() > MAX_LENGTH || !GRAMMAR.matcher(value).matches()) {
            return Optional.empty();
        }
        return Optional.of(new LessonId(value));
    }

    /**
     * Reads the identifier a lesson file is named by.
     *
     * @param fileName the name of a file of the lesson store
     * @return the identifier; empty when the name is not {@code <lesson_id>.md} with a parsable identifier
     */
    public static Optional<LessonId> fromFileName(String fileName) {
        Objects.requireNonNull(fileName, "fileName");
        if (!fileName.endsWith(FILE_SUFFIX)) {
            return Optional.empty();
        }
        return parse(fileName.substring(0, fileName.length() - FILE_SUFFIX.length()));
    }

    /** @return the name of the file the lesson is stored in */
    public String fileName() {
        return value + FILE_SUFFIX;
    }

    /** @return the UTC hour prefix {@code yyyy-MM-dd-HH} a new identifier is allocated against */
    public String hourPrefix() {
        return value.substring(0, HOUR_PREFIX_LENGTH);
    }

    /** @return the identifier itself, the form a record and a message use */
    @Override
    public String toString() {
        return value;
    }
}
