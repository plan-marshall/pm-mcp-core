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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

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

    private static final Pattern FINDING = Pattern.compile(
            "CUI_REWRITE-(10[01]): Finding (?:detected|pre-existing) at (.+?):(\\d+):(\\d+) by (.+?): (.*)$");

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
        if (!REWRITE_RUN.matcher(logText).find()) {
            return new RewriteLogResult(RewriteVerdict.NOT_OBSERVED, List.of(), NOT_OBSERVED_REASON);
        }
        if (!domainActive) {
            return new RewriteLogResult(RewriteVerdict.DOMAIN_INACTIVE, List.of(), DOMAIN_INACTIVE_REASON);
        }
        var findings = new ArrayList<RewriteFinding>();
        for (String rawLine : logText.split("\n", -1)) {
            String line = stripCarriageReturn(rawLine);
            Matcher match = FINDING.matcher(line);
            if (match.find()) {
                findings.add(new RewriteFinding(match.group(2), Integer.parseInt(match.group(3)),
                        Integer.parseInt(match.group(4)), match.group(5), match.group(6),
                        "100".equals(match.group(1))
                                ? RewriteClassification.NEWLY_DETECTED
                                : RewriteClassification.PRE_EXISTING));
                continue;
            }
            if (line.contains(RECIPE_LOG_PREFIX + "-100:") || line.contains(RECIPE_LOG_PREFIX + "-101:")) {
                return new RewriteLogResult(RewriteVerdict.PARSE_ERROR, List.of(),
                        "finding line does not match the pinned template: " + line.strip());
            }
        }
        return new RewriteLogResult(RewriteVerdict.OBSERVED, findings, null);
    }

    private static String stripCarriageReturn(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }
}
