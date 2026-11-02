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

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

/**
 * Extracts the file, line and column of a JVM build error or warning line.
 * <p>
 * The patterns are the ones the ported {@code plan-marshall} {@code _build_jvm_patterns.py} recognises, tried in this
 * order:
 * <ol>
 * <li>{@code file.java:[line,column]} — the Maven compiler output,</li>
 * <li>{@code file.(java|kt|groovy|scala):line[:column]} — the Gradle and generic compiler output,</li>
 * <li>{@code file.java:line:} — a line-only reference,</li>
 * <li>{@code TestName.methodName:line} — a test-failure reference, whose file is {@code TestName.java}.</li>
 * </ol>
 * A location is absent component-wise and never guessed: a line that matches no pattern carries no file, line or column.
 * A number too large for an {@code int} is not a location — the component is dropped, and so is every component that
 * depends on it (a column without its line is no location), so a hostile log cannot make the parse throw.
 *
 * @since 0.1
 */
@UtilityClass
public class JvmFileLocation {

    @SuppressWarnings("java:S8786") // ported pattern; the fixtures pin its behaviour, a rewrite risks a silent change
    private static final Pattern MAVEN_STYLE = Pattern.compile("([^\\s\\[\\]]+\\.java):\\[(\\d+),(\\d+)\\]");

    @SuppressWarnings("java:S8786") // ported pattern; the fixtures pin its behaviour, a rewrite risks a silent change
    private static final Pattern GRADLE_STYLE =
            Pattern.compile("([^\\s:]+\\.(java|kt|groovy|scala)):(\\d+):?(\\d+)?");

    @SuppressWarnings("java:S8786") // ported pattern; the fixtures pin its behaviour, a rewrite risks a silent change
    private static final Pattern LINE_ONLY = Pattern.compile("([^\\s\\[\\]]+\\.java):(\\d+):");

    @SuppressWarnings("java:S8786") // ported pattern; the fixtures pin its behaviour, a rewrite risks a silent change
    private static final Pattern TEST_FAILURE = Pattern.compile("(\\w+Test)\\.(\\w+):(\\d+)");

    /**
     * Extracts the location of one log line.
     *
     * @param line the log line to inspect
     * @return the location; every component is {@code null} when no pattern matched
     */
    public static Location parse(String line) {
        Matcher maven = MAVEN_STYLE.matcher(line);
        if (maven.find()) {
            return withColumn(maven.group(1), maven.group(2), maven.group(3));
        }
        Matcher gradle = GRADLE_STYLE.matcher(line);
        if (gradle.find()) {
            return withColumn(gradle.group(1), gradle.group(3), gradle.group(4));
        }
        Matcher lineOnly = LINE_ONLY.matcher(line);
        if (lineOnly.find()) {
            return new Location(lineOnly.group(1), toInt(lineOnly.group(2)), null);
        }
        Matcher testFailure = TEST_FAILURE.matcher(line);
        if (testFailure.find()) {
            return new Location(testFailure.group(1) + ".java", toInt(testFailure.group(3)), null);
        }
        return Location.NONE;
    }

    private static Location withColumn(String file, String lineDigits, @Nullable String columnDigits) {
        Integer line = toInt(lineDigits);
        if (line == null) {
            return new Location(file, null, null);
        }
        return new Location(file, line, toInt(columnDigits));
    }

    private static @Nullable Integer toInt(@Nullable String digits) {
        if (digits == null) {
            return null;
        }
        try {
            return Integer.valueOf(digits);
        } catch (NumberFormatException _) {
            return null;
        }
    }

    /**
     * A location with absent components.
     *
     * @param file   the source file, or {@code null}
     * @param line   the line in the file, or {@code null}
     * @param column the column in the line, or {@code null}
     * @since 0.1
     */
    public record Location(@Nullable String file, @Nullable Integer line, @Nullable Integer column) {

        /** The location of a line that names no file, line or column. */
        public static final Location NONE = new Location(null, null, null);
    }
}
