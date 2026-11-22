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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.queue.SecretRedactor;

/**
 * The native parse of the OpenRewrite finding lines of a Maven build log, ported from
 * {@code pm-dev-java-cui:parse-rewrite-log} and its call site {@code build-maven/_maven_cmd_rewrite_log.py}.
 * <p>
 * The parser reads the structured {@code WARN} lines {@code cui-open-rewrite} {@code #118} emits: one finding per line,
 * classified as newly detected or pre-existing by its identifier, never by the human verb.
 * <p>
 * It runs only when the build reached the {@code rewrite:run} goal and the java-cui domain is active; the domain state is
 * an input of the parse, never a lookup, so the parse stays a pure function of its arguments. The four verdicts are
 * fail-closed: only {@link RewriteVerdict#OBSERVED} carries findings, and a build that never reached the goal is
 * {@link RewriteVerdict#NOT_OBSERVED} rather than a vacuous clean.
 * <p>
 * One deliberate change against the original: a line that carries a finding identifier but no longer matches the finding
 * template is {@link RewriteVerdict#PARSE_ERROR}. The original silently ignores such a line, so a format drift would read
 * as zero findings; this port refuses to report an observation it could not make (ADR {@code core-build/002}).
 * <p>
 * The line format is pinned by the provenance corpus of the ported parser, which the fixtures carry verbatim. The
 * identifier prefix is matched as a substring, because the surrounding log layout prepends a timestamp, a level tag or a
 * logger name that the recipe itself never emits.
 *
 * @since 0.1
 */
@UtilityClass
public class OpenRewriteLogParser {

    /** The log-record prefix of the recipe's finding templates. */
    public static final String RECIPE_LOG_PREFIX = "CUI_REWRITE";

    private static final Pattern REWRITE_RUN = Pattern.compile("---\\s+rewrite(?:-maven-plugin:[\\w.\\-]+)?:run\\s+\\(");

    /** The literal head of a finding line; the ported template's text before the path. */
    private static final String FINDING_HEAD = "CUI_REWRITE-(10[01]): Finding (?:detected|pre-existing) at ";

    /** The head alone, which {@link #findingMatch(String)} finds before it runs the whole template once. */
    private static final Pattern FINDING_HEAD_PATTERN = Pattern.compile(FINDING_HEAD);

    /**
     * The finding template, the ported {@code (.+?):(\d+):(\d+) by (.+?): (.*)$} after the head. The path is the
     * shortest text of at least one character that the location {@code :<line>:<column> by } follows, written as a
     * tempered token that stops where the location begins, so no path length is tried twice; the location's digits are
     * possessive because a colon or a blank, never a digit, follows them; the recipe name is written the same way as a
     * token that stops at the first {@code : }. None changes a match of the ported form.
     */
    private static final Pattern FINDING = Pattern.compile(
            FINDING_HEAD + "(.(?:(?!:\\d++:\\d++ by ).)*+):(\\d++):(\\d++) by (.(?:(?!: ).)*+): (.*)$");

    /** The identifier of a finding line, matched with a word boundary so a colon-less one is still recognised. */
    private static final Pattern FINDING_IDENTIFIER = Pattern.compile(RECIPE_LOG_PREFIX + "-10[01]\\b");

    private static final String NOT_OBSERVED_REASON =
            "build did not reach rewrite:run — OpenRewrite findings were not produced this run";

    private static final String DOMAIN_INACTIVE_REASON =
            "no active domain provides the rewrite-log-parse verb — log-parse signal skipped";

    /**
     * Parses the OpenRewrite findings of a build log.
     *
     * @param logText      the build-log text as the caller holds it; ANSI escape sequences are stripped here
     * @param domainActive whether the java-cui domain is active for this build
     * @return the fail-closed result
     * @throws NullPointerException if the log text is null
     */
    public static RewriteLogResult parse(String logText, boolean domainActive) {
        Objects.requireNonNull(logText, "logText");
        return parseStripped(BuildLogText.stripAnsi(logText), domainActive);
    }

    /**
     * Reads and parses the OpenRewrite findings of a build-log file.
     *
     * @param logFile      the file to read
     * @param domainActive whether the java-cui domain is active for this build
     * @return the fail-closed result
     */
    public static RewriteLogResult parse(Path logFile, boolean domainActive) {
        return parseStripped(BuildLogText.read(logFile), domainActive);
    }

    private static RewriteLogResult parseStripped(String logText, boolean domainActive) {
        String bounded = BuildLogText.boundLines(logText);
        if (!REWRITE_RUN.matcher(bounded).find()) {
            return new RewriteLogResult(RewriteVerdict.NOT_OBSERVED, List.of(), NOT_OBSERVED_REASON);
        }
        if (!domainActive) {
            return new RewriteLogResult(RewriteVerdict.DOMAIN_INACTIVE, List.of(), DOMAIN_INACTIVE_REASON);
        }
        var findings = new ArrayList<RewriteFinding>();
        for (String rawLine : bounded.split("\n", -1)) {
            var error = accept(stripCarriageReturn(rawLine), findings);
            if (error.isPresent()) {
                return error.get();
            }
        }
        return new RewriteLogResult(RewriteVerdict.OBSERVED, findings, null);
    }

    /**
     * Reads one line for a finding, or refuses it: a finding line adds its finding, and a line that carries the finding
     * identifier without matching the pinned template is a {@link RewriteVerdict#PARSE_ERROR}.
     *
     * @param line     the line to read
     * @param findings the findings the parse has collected, appended to
     * @return the failure to answer with, or empty when the line carried nothing to answer
     */
    private static Optional<RewriteLogResult> accept(String line, List<RewriteFinding> findings) {
        if (mayBeFinding(line)) {
            var found = findingMatch(line);
            if (found.isPresent()) {
                Matcher match = found.get();
                if (!match.group(2).isBlank() && !match.group(5).isBlank()
                        && !SecretRedactor.redactText(match.group(2)).orElseThrow().isBlank()) {
                    return addFinding(line, match, findings);
                }
            }
        }
        return FINDING_IDENTIFIER.matcher(line).find()
                ? Optional.of(parseError(line, "the finding line does not match the pinned template"))
                : Optional.empty();
    }

    private static Optional<RewriteLogResult> addFinding(String line, Matcher match, List<RewriteFinding> findings) {
        Optional<Integer> lineNumber = digits(match.group(3));
        Optional<Integer> column = digits(match.group(4));
        if (lineNumber.isEmpty() || column.isEmpty()) {
            return Optional.of(parseError(line, "the finding location is outside the int range"));
        }
        findings.add(RewriteFinding.builder().path(match.group(2)).line(lineNumber.get()).column(column.get())
                .recipe(match.group(5)).message(match.group(6))
                .classification("100".equals(match.group(1))
                        ? RewriteClassification.NEWLY_DETECTED
                        : RewriteClassification.PRE_EXISTING)
                .build());
        return Optional.empty();
    }

    /**
     * Whether a line can carry a finding, asked before the pinned expression runs. Its template needs both the
     * identifier prefix and the {@code by} that separates the recipe from the message, so a line with neither cannot
     * match: every other line of a build log now costs two substring scans instead of a scan of the expression.
     *
     * @param line the line to inspect
     * @return whether the line can carry a finding
     */
    static boolean mayBeFinding(String line) {
        return line.indexOf("CUI_REWRITE-10") >= 0 && line.indexOf(" by ") >= 0;
    }

    /**
     * The match of the finding template in a line, the one {@code FINDING.matcher(line).find()} gives, in one pass.
     * <p>
     * No part of the template matches a line terminator except the final one {@code $} admits, so a match begins after
     * the last terminator inside the line; and when the template fails at the first head after it, it fails at every
     * later head too, whose path, location and message are each a suffix of the first one's. So the template runs once,
     * at that first head, and a line carrying many heads costs one scan instead of one per head.
     *
     * @param line the line to inspect
     * @return the match, its groups readable, or empty when the line holds no finding
     */
    static Optional<Matcher> findingMatch(String line) {
        var from = 0;
        for (var index = 0; index < line.length() - 1; index++) {
            if (BuildLogText.isLineTerminator(line.charAt(index))) {
                from = index + 1;
            }
        }
        Matcher head = FINDING_HEAD_PATTERN.matcher(line).region(from, line.length());
        if (!head.find()) {
            return Optional.empty();
        }
        Matcher match = FINDING.matcher(line).region(head.start(), line.length());
        return match.lookingAt() ? Optional.of(match) : Optional.empty();
    }

    private static RewriteLogResult parseError(String line, String reason) {
        return new RewriteLogResult(RewriteVerdict.PARSE_ERROR, List.of(), reason + ": " + line.strip());
    }

    private static Optional<Integer> digits(String text) {
        try {
            return Optional.of(Integer.valueOf(text));
        } catch (NumberFormatException _) {
            return Optional.empty();
        }
    }

    private static String stripCarriageReturn(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }
}
