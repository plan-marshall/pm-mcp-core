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

import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One row of a source file (PM-WORK-6): the line it starts on and its named fields in the order of the source.
 * <p>
 * {@link JsonlRowReader} and {@link CsvRowReader} read the rows of a file; a file they cannot read is refused with a
 * {@link MalformedSource} naming the line.
 *
 * @param line   the line of the file the row starts on, from 1
 * @param fields the named fields of the row in the order of the source; unmodifiable
 * @since 0.1
 */
public record SourceRow(int line, Map<String, String> fields) {

    /**
     * A source file that is not in the format its reader reads.
     *
     * @since 0.1
     */
    public static final class MalformedSource extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        /** The line of the file the refusal concerns. */
        private final int line;

        /**
         * @param line   the line of the file the refusal concerns, from 1
         * @param detail what is malformed
         * @param cause  the failure that found it; {@code null} if there is none
         */
        public MalformedSource(int line, String detail, @Nullable Throwable cause) {
            super("line " + line + ": " + Objects.requireNonNull(detail, "detail"), cause);
            this.line = line;
        }

        /** @return the line of the file the refusal concerns, from 1 */
        public int line() {
            return line;
        }
    }

    /**
     * @throws IllegalArgumentException if the line is below 1
     */
    public SourceRow {
        if (line < 1) {
            throw new IllegalArgumentException("'line' must be at least 1: " + line);
        }
        fields = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(fields, "fields")));
    }
}
