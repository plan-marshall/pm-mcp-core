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

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * The native parse of a {@code tsc} build log, ported from {@code build-npm/_npm_parse_typescript.py}.
 * <p>
 * The scanner reads the two diagnostic forms the compiler prints — {@code path(line,col): error TSnnnn: message} and
 * {@code path:line:col - error TSnnnn: message} — into one {@link Category#COMPILATION_ERROR} diagnostic each, carrying the
 * file, line, column and the {@code TS} code prefixed to the message.
 * <p>
 * One behaviour is a deliberate change against the original (ADR {@code core-build/003}): the original scans the second
 * form only when the first yielded nothing, so a log that mixes the two forms silently loses every {@code path:line:col -
 * error ...} diagnostic. This port scans both forms over every line and deduplicates across them.
 *
 * @since 0.1
 */
@UtilityClass
public class TypeScriptOutputParser {

    @SuppressWarnings("java:S8786") // ported pattern; the fixtures pin its behaviour, a rewrite risks a silent change
    private static final Pattern PARENTHESIZED =
            Pattern.compile("^(.+?)\\((\\d+),(\\d+)\\):\\s*(error)\\s+(TS\\d+):\\s*(.+)$");

    @SuppressWarnings("java:S8786") // ported pattern; the fixtures pin its behaviour, a rewrite risks a silent change
    private static final Pattern COLON_SEPARATED =
            Pattern.compile("^(.+?):(\\d+):(\\d+)\\s*-\\s*(error)\\s+(TS\\d+):\\s*(.+)$");

    /**
     * Parses a TypeScript build log.
     *
     * @param logText the log text as the caller holds it; ANSI escape sequences are stripped here
     * @return the parsed build
     * @throws NullPointerException if the log text is null
     */
    public static ParsedBuild parse(String logText) {
        Objects.requireNonNull(logText, "logText");
        return parseStripped(BuildLogText.stripAnsi(logText));
    }

    /**
     * Reads and parses a TypeScript build log.
     *
     * @param logFile the file to read
     * @return the parsed build
     */
    public static ParsedBuild parse(Path logFile) {
        return parseStripped(BuildLogText.read(logFile));
    }

    private static ParsedBuild parseStripped(String logText) {
        List<String> lines = List.of(logText.split("\n", -1));
        List<Diagnostic> diagnostics = DiagnosticCollector.collect(lines, TypeScriptOutputParser::filedDiagnostic);
        BuildStatus status = diagnostics.isEmpty() ? BuildStatus.SUCCESS : BuildStatus.FAILURE;
        return new ParsedBuild(diagnostics, status, false, null,
                ParsedBuild.noTestsRanFrom(status, false, null, false));
    }

    private static Optional<Diagnostic> filedDiagnostic(String rawLine) {
        String line = withoutCarriageReturn(rawLine);
        return diagnostic(PARENTHESIZED.matcher(line)).or(() -> diagnostic(COLON_SEPARATED.matcher(line)));
    }

    private static Optional<Diagnostic> diagnostic(Matcher matcher) {
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String message = matcher.group(5) + ": " + matcher.group(6);
        Integer line = position(matcher.group(2));
        Integer column = line == null ? null : position(matcher.group(3));
        return Optional.of(new Diagnostic(matcher.group(1), line, column, DiagnosticCollector.stored(message),
                Category.COMPILATION_ERROR, Severity.ERROR, null));
    }

    /**
     * Reads a line or column number, or reports it absent when the digits do not fit an {@code int}: a hostile or
     * truncated log must not make the parse throw.
     *
     * @param digits the digit run of the line or column
     * @return the number, or {@code null} when it is not representable
     */
    private static @Nullable Integer position(String digits) {
        try {
            return Integer.valueOf(digits);
        } catch (NumberFormatException _) {
            return null;
        }
    }

    private static String withoutCarriageReturn(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }
}
