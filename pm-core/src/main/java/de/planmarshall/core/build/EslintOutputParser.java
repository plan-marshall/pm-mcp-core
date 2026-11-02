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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * The native parse of an ESLint stylish-formatter log, ported from {@code build-npm/_npm_parse_eslint.py}.
 * <p>
 * The scanner keeps the last file-header line it saw and files every {@code line:column severity message rule} row
 * below it as a {@link Category#LINT_ERROR} diagnostic, with the rule prefixed to the message, as in the original. The
 * problem summary ({@code ✖ N problems (E errors, W warnings)}) is parsed beside the rows and returned as
 * {@link EslintResult#problems()}.
 * <p>
 * Two behaviours differ from the original, both recorded in ADR {@code core-build/003}. The original defines the summary
 * pattern and never reads it, so the counts it carries are lost; this port reports them. And a row whose message and rule
 * are separated by a single space does not match the original's pattern either, but this port reaches the same verdict
 * without the pattern's backtracking, so a hostile line cannot make the parse polynomial.
 *
 * @since 0.1
 */
@UtilityClass
public class EslintOutputParser {

    private static final String ERROR = "error";

    @SuppressWarnings("java:S8786") // ported pattern; the fixtures pin its behaviour, a rewrite risks a silent change
    private static final Pattern FILE_PATH =
            Pattern.compile("^(/[^\\s:]+|[A-Z]:\\\\[^\\s:]+|\\.{0,2}/[^\\s:]+\\.\\w+)$");

    @SuppressWarnings("java:S8786") // ported pattern; the fixtures pin its behaviour, a rewrite risks a silent change
    private static final Pattern ISSUE_HEAD = Pattern.compile("^\\s+(\\d+):(\\d+)\\s+(error|warning)\\s+(.+)$");

    private static final Pattern PROBLEM_SUMMARY =
            Pattern.compile("[✖✗]\\s*(\\d+)\\s+problems?\\s*\\((\\d+)\\s+errors?,\\s*(\\d+)\\s+warnings?\\)");

    /**
     * Parses an ESLint stylish-formatter log.
     *
     * @param logText the log text as the caller holds it; ANSI escape sequences are stripped here
     * @return the parsed result
     * @throws NullPointerException if the log text is null
     */
    public static EslintResult parse(String logText) {
        Objects.requireNonNull(logText, "logText");
        return parseStripped(BuildLogText.stripAnsi(logText));
    }

    /**
     * Reads and parses an ESLint stylish-formatter log.
     *
     * @param logFile the file to read
     * @return the parsed result
     */
    public static EslintResult parse(Path logFile) {
        return parseStripped(BuildLogText.read(logFile));
    }

    private static EslintResult parseStripped(String logText) {
        List<String> lines = List.of(logText.split("\n", -1));
        List<Diagnostic> diagnostics = extractDiagnostics(lines);
        boolean failed = diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == Severity.ERROR);
        BuildStatus status = failed ? BuildStatus.FAILURE : BuildStatus.SUCCESS;
        ParsedBuild build = new ParsedBuild(diagnostics, status, false, null,
                ParsedBuild.noTestsRanFrom(status, false, null, false));
        return new EslintResult(build, problemCounts(lines).orElse(null));
    }

    private static List<Diagnostic> extractDiagnostics(List<String> lines) {
        var diagnostics = new ArrayList<Diagnostic>();
        var seen = new HashSet<String>();
        String currentFile = null;
        for (String rawLine : lines) {
            String line = withoutCarriageReturn(rawLine);
            Optional<String> file = filePath(line);
            if (file.isPresent()) {
                currentFile = file.get();
            } else if (currentFile != null) {
                Optional<Diagnostic> diagnostic = issue(line, currentFile);
                if (diagnostic.isPresent() && seen.add(DiagnosticCollector.key(diagnostic.get()))) {
                    diagnostics.add(diagnostic.get());
                }
            }
        }
        return List.copyOf(diagnostics);
    }

    private static Optional<String> filePath(String line) {
        Matcher matcher = FILE_PATH.matcher(line);
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static Optional<Diagnostic> issue(String line, String file) {
        Matcher head = ISSUE_HEAD.matcher(line);
        if (!head.matches()) {
            return Optional.empty();
        }
        String remainder = head.group(4);
        int end = endWithoutTrailingWhitespace(remainder);
        int tokenStart = startOfLastToken(remainder, end);
        if (tokenStart < 0) {
            return Optional.empty();
        }
        int whitespaceStart = startOfWhitespaceRun(remainder, tokenStart);
        if (whitespaceStart <= 0 || tokenStart - whitespaceStart < 2) {
            return Optional.empty();
        }
        String message = remainder.substring(0, whitespaceStart).strip();
        if (message.isEmpty()) {
            return Optional.empty();
        }
        String rule = remainder.substring(tokenStart, end);
        Severity severity = ERROR.equals(head.group(3)) ? Severity.ERROR : Severity.WARNING;
        return Optional.of(new Diagnostic(file, Integer.valueOf(head.group(1)), Integer.valueOf(head.group(2)),
                DiagnosticCollector.stored(rule + ": " + message), Category.LINT_ERROR, severity, null));
    }

    private static int endWithoutTrailingWhitespace(String text) {
        int end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return end;
    }

    private static int startOfLastToken(String text, int end) {
        int start = end;
        while (start > 0 && !Character.isWhitespace(text.charAt(start - 1))) {
            start--;
        }
        return start == 0 || start == end ? -1 : start;
    }

    private static int startOfWhitespaceRun(String text, int tokenStart) {
        int start = tokenStart;
        while (start > 0 && Character.isWhitespace(text.charAt(start - 1))) {
            start--;
        }
        return start;
    }

    private static Optional<EslintResult.ProblemCounts> problemCounts(List<String> lines) {
        long errors = 0;
        long warnings = 0;
        boolean found = false;
        for (String rawLine : lines) {
            Matcher matcher = PROBLEM_SUMMARY.matcher(withoutCarriageReturn(rawLine));
            while (matcher.find()) {
                found = true;
                errors += toLong(matcher.group(2));
                warnings += toLong(matcher.group(3));
            }
        }
        if (!found || errors > Integer.MAX_VALUE || warnings > Integer.MAX_VALUE) {
            return Optional.empty();
        }
        return Optional.of(new EslintResult.ProblemCounts((int) errors, (int) warnings));
    }

    private static long toLong(String digits) {
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException _) {
            return Long.MAX_VALUE;
        }
    }

    private static String withoutCarriageReturn(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }
}
