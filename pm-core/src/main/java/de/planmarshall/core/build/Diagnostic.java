/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.build;

import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;
import lombok.Builder;

/**
 * One structured diagnostic of a build log: where the tool pointed and what it said.
 * <p>
 * A diagnostic is a pure value. The location is absent component-wise: a line that carries no file has a null file, and
 * one that names a file but no line has a null line. A field, a record component and a return value follow the null
 * contract of the package: an absent location is a {@code @Nullable} component, never an {@code Optional}, and the
 * message itself is never null.
 * <p>
 * The {@code stackTrace} component carries the frames the parser collected for this diagnostic, {@code null} where the
 * log carried none. It is the target of the Maven and Gradle parsers' stack-trace collection, which the ported
 * originals attach to the last issue they filed (see {@link StackTraceCollector}).
 * <p>
 * The record has seven components, so it carries a {@code @Builder} beside its validating canonical constructor.
 *
 * @param file       the source file the diagnostic points into, or {@code null} when the tool named none
 * @param line       the line in {@code file}, or {@code null} when the tool named none
 * @param column     the column in {@code line}, or {@code null} when the tool named none
 * @param message    the diagnostic text with the tool's level prefix removed, never blank
 * @param category   the category the compiled table assigned, never {@code null}
 * @param severity   the level the tool printed the line at, never {@code null}
 * @param stackTrace the frames collected for this diagnostic joined by {@code \n}, or {@code null} when there were none
 * @since 0.1
 */
@Builder
public record Diagnostic(
@Nullable String file,
@Nullable Integer line,
@Nullable Integer column,
String message,
Category category,
Severity severity,
@Nullable String stackTrace) {

    /**
     * Validates the diagnostic.
     *
     * @throws IllegalArgumentException if the message is blank, or a location is negative or present without its
     *                                  enclosing location (a line without a file, a column without a line)
     * @throws NullPointerException     if the message, the category or the severity is null
     */
    public Diagnostic {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(severity, "severity");
        if (message.isBlank()) {
            throw new IllegalArgumentException("a diagnostic message must not be blank");
        }
        if (file != null && file.isBlank()) {
            throw new IllegalArgumentException("a diagnostic file must not be blank");
        }
        if (line != null && line < 0) {
            throw new IllegalArgumentException("a diagnostic line must not be negative: " + line);
        }
        if (column != null && column < 0) {
            throw new IllegalArgumentException("a diagnostic column must not be negative: " + column);
        }
        if (line != null && file == null) {
            throw new IllegalArgumentException("a diagnostic line needs a file");
        }
        if (column != null && line == null) {
            throw new IllegalArgumentException("a diagnostic column needs a line");
        }
    }

    /**
     * Returns a copy of this diagnostic carrying the given collected stack trace.
     *
     * @param collectedStackTrace the frames joined by {@code \n}
     * @return the copy
     */
    public Diagnostic withStackTrace(String collectedStackTrace) {
        return new Diagnostic(file, line, column, message, category, severity, collectedStackTrace);
    }

    /**
     * Whether the diagnostic points into a source file.
     *
     * @return {@code true} when the file is present
     */
    public boolean isLocated() {
        return file != null;
    }

    /**
     * Returns the category name in the spelling of the ported originals.
     *
     * @return the category name in lower case, e.g. {@code compilation_error}
     */
    public String categoryName() {
        return category.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Returns the severity name in the spelling of the ported originals.
     *
     * @return the severity name in lower case, e.g. {@code error}
     */
    public String severityName() {
        return severity.name().toLowerCase(Locale.ROOT);
    }
}
