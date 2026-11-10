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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

/**
 * The native parse of a pyproject (Python) build log, ported from {@code build-pyproject/_pyproject_cmd_parse.py}.
 * <p>
 * A pyproject log commonly carries the output of several tools of one verify run, so the parser detects each tool by its
 * own content marker and combines the results: mypy ({@code path:line: error: message}), whose category is the first match
 * of the original's Python pattern table (a type error by default, an import failure for an import message, and a lint or
 * test-failure category for a message that table places so), ruff ({@code path:line:column: CODE message}) as
 * {@link Category#LINT_ERROR}, and pytest's {@code FAILED} and {@code ERROR} short-summary lines as
 * {@link Category#TEST_FAILURE} and {@link Category#TEST_COLLECTION_ERROR}.
 * <p>
 * The pytest parse follows the original: a block under the {@code FAILURES} or {@code ERRORS} banner supplies the location
 * (the deepest {@code path.py:NN} frame of its traceback, ahead of any captured-output section) and, for a collection error
 * whose summary line carries no message tail, the message from the block's {@code E} gutter. The summary line supplies the
 * counts.
 * <p>
 * Three behaviours are deliberate changes against the original, all recorded in ADR {@code core-build/003}: the location is
 * the deepest traceback frame rather than the test file's frame; a pytest-xdist {@code @<group>} suffix is stripped before
 * test identity matching; and every pytest summary block of a multi-run log is summed rather than the last one read. A
 * filter that matched nothing is held as in the original: pytest's {@code no tests ran} summary carries no count, so the
 * port reports no count at all rather than a zero.
 *
 * @since 0.1
 */
@UtilityClass
public class PyprojectOutputParser {

    private static final String ERROR_LINE_PREFIX = "ERROR ";

    private static final String NODE_SEPARATOR = "::";

    private static final String MESSAGE_DELIMITER = " - ";

    private static final String CAPTURED_SECTION = "\n---";

    private static final String XDIST_GROUP = "@[A-Za-z0-9_.-]+$";

    private static final String NO_TESTS_MARKER = "no tests ran";

    private static final String PY_FILE = ".py";

    private static final Pattern MYPY_DETECT = Pattern.compile("\\.pyi?:\\d+(?::\\d+)?: error:");

    private static final Pattern RUFF_DETECT = Pattern.compile("\\.py:\\d+:\\d+: [A-Z]+\\d+");

    @SuppressWarnings("java:S8786") // ported pattern; the fixtures pin its behaviour, a rewrite risks a silent change
    private static final Pattern MYPY = Pattern.compile("^(.+\\.pyi?):(\\d+)(?::\\d+)?: error: (.+)$");

    @SuppressWarnings("java:S8786") // ported pattern; the fixtures pin its behaviour, a rewrite risks a silent change
    private static final Pattern RUFF = Pattern.compile("^(.+\\.py):(\\d+):(\\d+): ([A-Z]+\\d+) (.+)$");

    private static final String FAILED_PREFIX = "FAILED ";

    private static final Pattern FAILURES_BANNER_LINE = Pattern.compile("^=+ FAILURES =+\\s*$");

    private static final Pattern ERRORS_BANNER_LINE = Pattern.compile("^=+ ERRORS =+\\s*$");

    @SuppressWarnings("java:S8786") // ported pattern; the fixtures pin its behaviour, a rewrite risks a silent change
    private static final Pattern SECTION_LINE = Pattern.compile("^=+\\s+\\S.*\\s+=+\\s*$");

    private static final Pattern ERROR_HEADER_COLLECTING = Pattern.compile("^ERROR collecting (.+)$");

    private static final Pattern ERROR_HEADER_PHASE = Pattern.compile("^ERROR at (?:setup|teardown) of (.+)$");

    @SuppressWarnings("java:S8786") // ported pattern; the fixtures pin its behaviour, a rewrite risks a silent change
    private static final Pattern EXCEPTION_LINE = Pattern.compile("^E([ \\t]+)(\\S.*?)[ \\t]*$", Pattern.MULTILINE);

    private static final Pattern FRAME = Pattern.compile("^(\\S.*\\.py):(\\d+):");

    @SuppressWarnings("java:S8786") // ported pattern; the fixtures pin its behaviour, a rewrite risks a silent change
    private static final Pattern SUMMARY_LINE =
            Pattern.compile("^.*\\b(?:passed|failed|skipped)\\b.*\\bin\\s+[\\d.]+s.*$");

    private static final Pattern PASSED_COUNT = Pattern.compile("\\b(\\d+) passed\\b");

    private static final Pattern FAILED_COUNT = Pattern.compile("\\b(\\d+) failed\\b");

    private static final Pattern SKIPPED_COUNT = Pattern.compile("\\b(\\d+) skipped\\b");

    /** The metacharacters that make a pattern of the original's Python table a regular expression rather than a literal. */
    private static final Pattern PATTERN_METACHARACTERS = Pattern.compile("[\\\\^$.*+?{}\\[\\]|()]");

    /**
     * The original's {@code PYTHON_PATTERNS} table, in its order: the first category whose pattern matches a mypy message
     * wins, and a message that matches none stays a {@link Category#TYPE_ERROR}.
     */
    private static final List<PythonCategory> PYTHON_CATEGORIES = List.of(
            new PythonCategory(Category.TYPE_ERROR,
                    matchers("\\.py:\\d+: error:", "incompatible type", "incompatible return value",
                            "has no attribute", "missing positional argument")),
            new PythonCategory(Category.LINT_ERROR, matchers("\\.py:\\d+:\\d+: [A-Z]+\\d+", "ruff")),
            new PythonCategory(Category.TEST_FAILURE, matchers("^FAILED ", "AssertionError", "assert ")),
            new PythonCategory(Category.IMPORT_ERROR,
                    matchers("ModuleNotFoundError", "ImportError", "No module named")));

    /**
     * Parses a pyproject build log.
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
     * Reads and parses a pyproject build log.
     *
     * @param logFile the file to read
     * @return the parsed build
     */
    public static ParsedBuild parse(Path logFile) {
        return parseStripped(BuildLogText.read(logFile));
    }

    private static ParsedBuild parseStripped(String logText) {
        List<String> lines = withoutCarriageReturns(List.of(logText.split("\n", -1)));
        var diagnostics = new ArrayList<Diagnostic>();
        @Nullable TestCounts testCounts = null;
        boolean successMarker = false;
        if (MYPY_DETECT.matcher(logText).find()) {
            diagnostics.addAll(mypyDiagnostics(lines));
        }
        if (RUFF_DETECT.matcher(logText).find()) {
            diagnostics.addAll(ruffDiagnostics(lines));
        }
        if (hasPytestOutput(lines)) {
            diagnostics.addAll(pytestDiagnostics(lines));
            testCounts = pytestTestCounts(lines).orElse(null);
            successMarker = hasPassedSummary(lines);
        }
        List<Diagnostic> result = List.copyOf(diagnostics);
        boolean failed = result.stream().anyMatch(diagnostic -> diagnostic.severity() == Severity.ERROR);
        BuildStatus status = failed ? BuildStatus.FAILURE : BuildStatus.SUCCESS;
        boolean noTestsRan = ParsedBuild.noTestsRanFrom(status, successMarker, testCounts,
                logText.contains(NO_TESTS_MARKER));
        return ParsedBuild.builder().diagnostics(result).status(status).successMarker(successMarker)
                .testCounts(testCounts).noTestsRan(noTestsRan).build();
    }

    private static boolean hasPassedSummary(List<String> lines) {
        return lines.stream()
                .anyMatch(line -> SUMMARY_LINE.matcher(line).matches() && PASSED_COUNT.matcher(line).find());
    }

    private static List<Diagnostic> mypyDiagnostics(List<String> lines) {
        var diagnostics = new ArrayList<Diagnostic>();
        var seen = new HashSet<String>();
        for (String line : lines) {
            Matcher matcher = MYPY.matcher(line);
            Integer number = matcher.matches() ? integer(matcher.group(2)) : null;
            if (number != null) {
                add(diagnostics, seen, new Diagnostic(matcher.group(1), number, null,
                        DiagnosticCollector.stored(matcher.group(3)), categoryOf(matcher.group(3)), Severity.ERROR,
                        null));
            }
        }
        return diagnostics;
    }

    private static List<Diagnostic> ruffDiagnostics(List<String> lines) {
        var diagnostics = new ArrayList<Diagnostic>();
        var seen = new HashSet<String>();
        for (String line : lines) {
            Matcher matcher = RUFF.matcher(line);
            Integer number = matcher.matches() ? integer(matcher.group(2)) : null;
            if (number != null) {
                add(diagnostics, seen, new Diagnostic(matcher.group(1), number, integer(matcher.group(3)),
                        DiagnosticCollector.stored(matcher.group(4) + " " + matcher.group(5)), Category.LINT_ERROR,
                        Severity.ERROR, null));
            }
        }
        return diagnostics;
    }

    /**
     * Categorises a mypy message through the original's Python pattern table: the first category whose pattern matches
     * wins, a pattern carrying a regular-expression metacharacter is a case-insensitive expression matched anywhere, and
     * every other pattern is a case-insensitive literal substring. A message that matches none stays a type error.
     *
     * @param message the message mypy printed
     * @return the category of the first matching pattern, or {@link Category#TYPE_ERROR}
     */
    private static Category categoryOf(String message) {
        for (PythonCategory category : PYTHON_CATEGORIES) {
            for (PythonPattern pattern : category.patterns()) {
                if (pattern.matches(message)) {
                    return category.category();
                }
            }
        }
        return Category.TYPE_ERROR;
    }

    private static List<PythonPattern> matchers(String... patterns) {
        var matchers = new ArrayList<PythonPattern>();
        for (String pattern : patterns) {
            matchers.add(PATTERN_METACHARACTERS.matcher(pattern).find()
                    ? new PythonPattern.Regex(Pattern.compile(pattern, Pattern.CASE_INSENSITIVE))
                    : new PythonPattern.Literal(pattern.toLowerCase(Locale.ROOT)));
        }
        return List.copyOf(matchers);
    }

    private static List<Diagnostic> pytestDiagnostics(List<String> lines) {
        var diagnostics = new ArrayList<Diagnostic>();
        var seen = new HashSet<String>();
        addFailures(lines, diagnostics, seen);
        addErrors(lines, diagnostics, seen);
        return diagnostics;
    }

    private static void addFailures(List<String> lines, List<Diagnostic> diagnostics, Set<String> seen) {
        Map<String, List<String>> blocks = blocks(lines, FAILURES_BANNER_LINE, PyprojectOutputParser::pytestBlockKey);
        var cursors = new HashMap<String, Integer>();
        for (String line : lines) {
            ParsedError failed = parseFailedLine(line);
            if (failed != null) {
                String testName = failed.testName() == null ? failed.file() : withoutXdistGroup(failed.testName());
                String message = failed.message() == null ? "Test " + testName + " failed" : failed.message();
                String block = consume(blocks, cursors, pytestBlockKey(testName));
                add(diagnostics, seen, pytestDiagnostic(failed.file(), block, message, Category.TEST_FAILURE));
            }
        }
    }

    /**
     * Parses a pytest {@code FAILED <path>::<node>[ - <message>]} short-summary line.
     * <p>
     * The path is the first {@code .py} whose remainder starts a node id, and the node id is split from its message
     * outside parameter brackets, so a parametrized id that carries a space (or the delimiter itself) is still a
     * failure the parser reports.
     *
     * @param line the line to inspect
     * @return the parsed line, or {@code null} when it is no failure short-summary line
     */
    private static @Nullable ParsedError parseFailedLine(String line) {
        if (!line.startsWith(FAILED_PREFIX)) {
            return null;
        }
        String rest = line.substring(FAILED_PREFIX.length());
        int searchFrom = 0;
        int start = rest.indexOf(PY_FILE, searchFrom);
        while (start >= 0) {
            ParsedError parsed = failedAt(rest, start);
            if (parsed != null) {
                return parsed;
            }
            searchFrom = start + PY_FILE.length();
            start = rest.indexOf(PY_FILE, searchFrom);
        }
        return null;
    }

    private static @Nullable ParsedError failedAt(String rest, int start) {
        if (start < 1) {
            return null;
        }
        int end = start + PY_FILE.length();
        String tail = rest.substring(end);
        if (!tail.startsWith(NODE_SEPARATOR)) {
            return null;
        }
        String nodeTail = tail.substring(NODE_SEPARATOR.length());
        if (nodeTail.isEmpty()) {
            return null;
        }
        String[] parts = splitErrorTail(nodeTail);
        return new ParsedError(rest.substring(0, end), parts[0], parts[1]);
    }

    private static void addErrors(List<String> lines, List<Diagnostic> diagnostics, Set<String> seen) {
        Map<String, List<String>> blocks = blocks(lines, ERRORS_BANNER_LINE, PyprojectOutputParser::pytestErrorBlockKey);
        var cursors = new HashMap<String, Integer>();
        for (String line : lines) {
            ParsedError error = parseErrorLine(line);
            if (error != null) {
                String block = consume(blocks, cursors, errorKey(error));
                add(diagnostics, seen,
                        pytestDiagnostic(error.file(), block, errorMessage(error, block),
                                Category.TEST_COLLECTION_ERROR));
            }
        }
    }

    private static String errorKey(ParsedError error) {
        return error.testName() == null ? error.file() : pytestBlockKey(withoutXdistGroup(error.testName()));
    }

    private static String errorMessage(ParsedError error, @Nullable String block) {
        String summary = error.message();
        if (summary != null) {
            return summary;
        }
        String terse = error.testName() == null
                ? "Error collecting " + error.file()
                : "Error in " + withoutXdistGroup(error.testName());
        return block == null ? terse : exceptionMessage(block).orElse(terse);
    }

    private static Diagnostic pytestDiagnostic(String summaryFile, @Nullable String block, String message,
            Category category) {
        Frame location = block == null ? null : frame(block);
        String file = location == null ? summaryFile : location.file();
        Integer line = location == null ? null : location.line();
        return new Diagnostic(file, line, null, DiagnosticCollector.stored(message), category, Severity.ERROR, null);
    }

    private static void add(List<Diagnostic> diagnostics, Set<String> seen, Diagnostic diagnostic) {
        if (seen.add(DiagnosticCollector.key(diagnostic))) {
            diagnostics.add(diagnostic);
        }
    }

    private static boolean hasPytestOutput(List<String> lines) {
        if (lines.stream().anyMatch(line -> line.contains(FAILED_PREFIX))) {
            return true;
        }
        if (lines.stream().anyMatch(line -> parseErrorLine(line) != null)) {
            return true;
        }
        String content = String.join("\n", lines);
        return content.contains("==") && (content.contains("passed") || content.contains("failed"));
    }

    /**
     * Parses a pytest {@code ERROR <path>[::<node>][ - <message>]} short-summary line.
     * <p>
     * The path is the first {@code .py} whose remainder is empty, a node-id tail or a message tail — the split the
     * original's lazy path group makes. The node-id tail is then split on the delimiter that lies outside parameter
     * brackets, so a parametrized id such as {@code test_case[a - b]} keeps its bracket.
     *
     * @param line the line to inspect
     * @return the parsed line, or {@code null} when it is no error short-summary line
     */
    private static @Nullable ParsedError parseErrorLine(String line) {
        if (!line.startsWith(ERROR_LINE_PREFIX)) {
            return null;
        }
        String rest = line.substring(ERROR_LINE_PREFIX.length());
        int searchFrom = 0;
        int start = rest.indexOf(PY_FILE, searchFrom);
        while (start >= 0) {
            ParsedError parsed = errorAt(rest, start);
            if (parsed != null) {
                return parsed;
            }
            searchFrom = start + PY_FILE.length();
            start = rest.indexOf(PY_FILE, searchFrom);
        }
        return null;
    }

    private static @Nullable ParsedError errorAt(String rest, int start) {
        if (start < 1) {
            return null;
        }
        int end = start + PY_FILE.length();
        String tail = rest.substring(end);
        if (tail.isEmpty()) {
            return new ParsedError(rest.substring(0, end), null, null);
        }
        if (tail.startsWith(NODE_SEPARATOR)) {
            String nodeTail = tail.substring(NODE_SEPARATOR.length());
            if (nodeTail.isEmpty()) {
                return null;
            }
            String[] parts = splitErrorTail(nodeTail);
            return new ParsedError(rest.substring(0, end), parts[0], parts[1]);
        }
        if (tail.startsWith(MESSAGE_DELIMITER)) {
            String message = tail.substring(MESSAGE_DELIMITER.length());
            return message.isEmpty() ? null : new ParsedError(rest.substring(0, end), null, message);
        }
        return null;
    }

    /**
     * Splits an error line's node-id tail at the first delimiter outside parameter brackets.
     *
     * @param tail everything after {@code ::}
     * @return the node id and the message, the message being {@code null} when no delimiter separates them
     */
    private static String[] splitErrorTail(String tail) {
        int depth = 0;
        for (int index = 0; index < tail.length(); index++) {
            char character = tail.charAt(index);
            if (character == '[') {
                depth++;
            } else if (character == ']') {
                depth = Math.max(depth - 1, 0);
            } else if (depth == 0 && tail.startsWith(MESSAGE_DELIMITER, index)) {
                return new String[]{tail.substring(0, index), tail.substring(index + MESSAGE_DELIMITER.length())};
            }
        }
        return new String[]{tail, null};
    }

    private static String withoutXdistGroup(String nodeId) {
        return nodeId.replaceFirst(XDIST_GROUP, "");
    }

    private static String pytestBlockKey(String name) {
        return name.replace(NODE_SEPARATOR, ".").strip();
    }

    private static String pytestErrorBlockKey(String name) {
        String stripped = name.strip();
        Matcher collecting = ERROR_HEADER_COLLECTING.matcher(stripped);
        if (collecting.matches()) {
            return collecting.group(1);
        }
        Matcher phase = ERROR_HEADER_PHASE.matcher(stripped);
        if (phase.matches()) {
            return pytestBlockKey(phase.group(1));
        }
        return pytestBlockKey(name);
    }

    private static Map<String, List<String>> blocks(List<String> lines, Pattern banner, UnaryOperator<String> key) {
        Map<String, List<String>> blocks = new LinkedHashMap<>();
        var current = new ArrayList<String>();
        var state = new BlockState();
        for (String line : lines) {
            if (banner.matcher(line).matches()) {
                state.inSection = true;
            } else if (state.inSection) {
                acceptBlockLine(line, state, blocks, current, key);
            }
        }
        flush(blocks, state.currentKey, current);
        return blocks;
    }

    private static void acceptBlockLine(String line, BlockState state, Map<String, List<String>> blocks,
            List<String> current, UnaryOperator<String> key) {
        String header = blockHeader(line);
        if (header != null) {
            flush(blocks, state.currentKey, current);
            state.currentKey = key.apply(header);
        } else if (SECTION_LINE.matcher(line).matches()) {
            flush(blocks, state.currentKey, current);
            state.currentKey = null;
            state.inSection = false;
        } else if (state.currentKey != null) {
            current.add(line);
        }
    }

    private static void flush(Map<String, List<String>> blocks, @Nullable String key, List<String> lines) {
        if (key != null) {
            blocks.computeIfAbsent(key, unused -> new ArrayList<>()).add(String.join("\n", lines).strip());
            lines.clear();
        }
    }

    /**
     * Returns the name of a pytest block header such as {@code ____ test_alpha ____}, or {@code null} for a line that is
     * no header. The trailing underscore rule is read from the end, so a long name that collapses the padding to one
     * underscore per side is still recognised.
     *
     * @param line the line to inspect
     * @return the name inside the header, or {@code null}
     */
    private static @Nullable String blockHeader(String line) {
        int end = line.length();
        while (end > 0 && Character.isWhitespace(line.charAt(end - 1))) {
            end--;
        }
        int trailing = end;
        while (trailing > 0 && line.charAt(trailing - 1) == '_') {
            trailing--;
        }
        if (trailing == end || trailing == 0 || !Character.isWhitespace(line.charAt(trailing - 1))) {
            return null;
        }
        int nameEnd = trailing;
        while (nameEnd > 0 && Character.isWhitespace(line.charAt(nameEnd - 1))) {
            nameEnd--;
        }
        int leading = 0;
        while (leading < nameEnd && line.charAt(leading) == '_') {
            leading++;
        }
        if (leading == 0) {
            return null;
        }
        int nameStart = leading;
        while (nameStart < nameEnd && Character.isWhitespace(line.charAt(nameStart))) {
            nameStart++;
        }
        if (nameStart >= nameEnd) {
            return null;
        }
        return line.substring(nameStart, nameEnd);
    }

    private static @Nullable String consume(Map<String, List<String>> blocks, Map<String, Integer> cursors,
            String key) {
        List<String> candidates = blocks.get(key);
        int cursor = cursors.getOrDefault(key, 0);
        if (candidates == null || cursor >= candidates.size()) {
            return null;
        }
        cursors.put(key, cursor + 1);
        return candidates.get(cursor);
    }

    private static @Nullable Frame frame(String block) {
        int captured = block.indexOf(CAPTURED_SECTION);
        String traceback = captured < 0 ? block : block.substring(0, captured);
        @Nullable Frame frame = null;
        for (String line : traceback.split("\n", -1)) {
            Matcher matcher = FRAME.matcher(line);
            if (matcher.find()) {
                frame = new Frame(matcher.group(1), integer(matcher.group(2)));
            }
        }
        return frame;
    }

    private static Optional<String> exceptionMessage(String block) {
        Matcher matcher = EXCEPTION_LINE.matcher(block);
        int minimum = Integer.MAX_VALUE;
        String message = null;
        while (matcher.find()) {
            int indent = matcher.group(1).length();
            if (indent < minimum) {
                minimum = indent;
                message = matcher.group(2);
            }
        }
        return Optional.ofNullable(message);
    }

    private static Optional<TestCounts> pytestTestCounts(List<String> lines) {
        long passed = 0;
        long failed = 0;
        long skipped = 0;
        boolean found = false;
        boolean unrepresentable = false;
        for (String line : lines) {
            if (SUMMARY_LINE.matcher(line).matches()) {
                found = true;
                long blockPassed = count(PASSED_COUNT, line);
                long blockFailed = count(FAILED_COUNT, line);
                long blockSkipped = count(SKIPPED_COUNT, line);
                boolean representable = blockPassed >= 0 && blockFailed >= 0 && blockSkipped >= 0
                        && blockPassed <= Integer.MAX_VALUE - passed && blockFailed <= Integer.MAX_VALUE - failed
                        && blockSkipped <= Integer.MAX_VALUE - skipped;
                if (representable) {
                    passed += blockPassed;
                    failed += blockFailed;
                    skipped += blockSkipped;
                } else {
                    unrepresentable = true;
                }
            }
        }
        if (!found) {
            return Optional.empty();
        }
        return Optional.of(unrepresentable
                ? TestCounts.unmeasured()
                : TestCounts.from(passed, failed, skipped, null));
    }

    private static long count(Pattern pattern, String line) {
        Matcher matcher = pattern.matcher(line);
        if (!matcher.find()) {
            return 0L;
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException _) {
            return -1L;
        }
    }

    private static @Nullable Integer integer(String digits) {
        try {
            return Integer.valueOf(digits);
        } catch (NumberFormatException _) {
            return null;
        }
    }

    private static List<String> withoutCarriageReturns(List<String> lines) {
        var result = new ArrayList<String>(lines.size());
        for (String line : lines) {
            result.add(line.endsWith("\r") ? line.substring(0, line.length() - 1) : line);
        }
        return result;
    }

    /** The running state of one pass over a block section of the log. */
    private static final class BlockState {

        private @Nullable String currentKey;

        private boolean inSection;
    }

    private record ParsedError(String file, @Nullable String testName, @Nullable String message) {
    }

    private record Frame(String file, @Nullable Integer line) {
    }

    /**
     * One category of the original's Python pattern table.
     *
     * @param category the category the patterns assign
     * @param patterns the compiled patterns, in the original's order
     */
    private record PythonCategory(Category category, List<PythonPattern> patterns) {
    }

    /**
     * One pattern of the original's Python table: a case-insensitive regular expression matched anywhere, or a
     * case-insensitive literal substring.
     */
    private sealed interface PythonPattern {

        /**
         * Whether the message matches this pattern.
         *
         * @param message the message to inspect
         * @return {@code true} when the pattern matches
         */
        boolean matches(String message);

        /**
         * A literal pattern, matched as a case-insensitive substring.
         *
         * @param lower the pattern in lower case
         */
        record Literal(String lower) implements PythonPattern {

            @Override
            public boolean matches(String message) {
                return message.toLowerCase(Locale.ROOT).contains(lower);
            }
        }

        /**
         * A regular-expression pattern, matched case-insensitively anywhere in the message.
         *
         * @param pattern the compiled expression
         */
        record Regex(Pattern pattern) implements PythonPattern {

            @Override
            public boolean matches(String message) {
                return pattern.matcher(message).find();
            }
        }
    }
}
