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
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.queue.SecretRedactor;

/**
 * The native parse of an npm error log, ported from {@code build-npm/_npm_parse_errors.py}.
 * <p>
 * The scanner reads the {@code code <CODE>} blocks of the log — the {@code npm ERR! } prefix of npm up to 11 and the
 * {@code npm error } prefix of npm 12 on — and classifies each block by its code. An
 * {@code ERESOLVE} block is {@link Category#NPM_DEPENDENCY} and names the conflicting peer dependency when the
 * {@code Could not resolve dependency:} trailer names one; an {@code E404} block is {@link Category#NPM_ERROR} and names
 * the quoted package when the block carries the {@code '<package>' is not in this registry} form of npm up to 11,
 * otherwise it reports the package as not found (npm 12 prints the "requested resource" wording). Every other code is
 * {@link Category#NPM_ERROR} whose message is the code and the first line of the block, redacted through
 * {@link SecretRedactor} before the stored-length cut so a truncated message cannot keep half a secret. Both named forms
 * point into {@code package.json} without a line, as in the original.
 * <p>
 * The install family prints no error block; its completion line ({@code added 150 packages in 10s},
 * {@code up to date, audited 5 packages}) is the tool's positive success marker ({@link #printsInstallSummary}), so a
 * zero-exit install ends {@code succeeded} instead of {@code indeterminate}. A log with an error block, or one whose
 * line the pattern cannot read, keeps the marker {@code false}. {@code tsc} and ESLint print no such line: their clean
 * run stays {@code indeterminate}, and the job's verb does not stand in for a printed summary.
 * <p>
 * One behaviour is a deliberate change against the original (ADR {@code core-build/003}): the original reads only the
 * first {@code npm ERR! code} line and files at most one diagnostic, so a log that carries several error blocks loses all
 * but the first. This port files one diagnostic per block, in log order.
 * <p>
 * The peer-conflict trailer is scanned line by line rather than with the original's {@code DOTALL} expression, so a
 * hostile log line cannot make the match polynomial (the pattern-shape hazard the plan's standing constraints name).
 *
 * @since 0.1
 */
@UtilityClass
public class NpmErrorParser {

    /**
     * The two error prefixes npm prints: `npm ERR! ` up to npm 11 and `npm error ` from npm 12 on. The same failure
     * carries the same block under either prefix.
     */
    private static final Pattern ERROR_PREFIX = Pattern.compile("^(?:npm ERR!|npm error) ");

    private static final String ERESOLVE = "ERESOLVE";

    private static final String E404 = "E404";

    private static final String PACKAGE_JSON = "package.json";

    private static final Pattern CONFLICT_MARKER =
            Pattern.compile("^(?:npm ERR!|npm error) Could not resolve dependency:$");

    private static final String BOILERPLATE_PREFIX = "A complete log";

    private static final Pattern ERROR_CODE = Pattern.compile("^(?:npm ERR!|npm error) code (\\S+)");

    private static final Pattern E404_PACKAGE =
            Pattern.compile("^(?:npm ERR!|npm error) 404\\s+'([^']+)'\\s+is not in this registry");

    private static final Pattern PEER_CONFLICT = Pattern.compile("peer\\s+(\\S+)\\s+from\\s+(\\S+)");

    /**
     * The install family's completion line: {@code added 150 packages in 10s}, {@code removed 3 packages in 1s},
     * {@code changed 2 packages} or {@code up to date, audited 5 packages}. The line is anchored to its own shape, so a
     * prose sentence carrying the words {@code added} and {@code packages} does not match. The adapter
     * {@code BuildOutputParser.npm} reads the same pattern to dispatch an install log here, so the marker and the
     * dispatch cannot drift apart.
     */
    public static final Pattern INSTALL_SUMMARY =
            Pattern.compile("(?m)^\\s*(?:added|removed|changed|up to date)\\b.*\\bpackages?\\b");

    /**
     * Parses an npm error log.
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
     * Reads and parses an npm error log.
     *
     * @param logFile the file to read
     * @return the parsed build
     */
    public static ParsedBuild parse(Path logFile) {
        return parseStripped(BuildLogText.read(logFile));
    }

    private static ParsedBuild parseStripped(String logText) {
        List<String> lines = List.of(logText.split("\n", -1));
        List<Diagnostic> diagnostics = extractDiagnostics(lines);
        BuildStatus status = diagnostics.isEmpty() ? BuildStatus.SUCCESS : BuildStatus.FAILURE;
        boolean successMarker = status == BuildStatus.SUCCESS && printsInstallSummary(lines);
        return new ParsedBuild(diagnostics, status, successMarker, null,
                ParsedBuild.noTestsRanFrom(status, successMarker, null, false));
    }

    /**
     * Whether the log printed the install family's completion line. The marker needs a successful status, which the
     * caller supplies by requiring no error block; a completion line beside an error block is therefore no marker.
     *
     * @param lines the log lines
     * @return whether an install completion line was printed
     */
    private static boolean printsInstallSummary(List<String> lines) {
        return lines.stream().anyMatch(line -> INSTALL_SUMMARY.matcher(line).find());
    }

    private static List<Diagnostic> extractDiagnostics(List<String> lines) {
        var diagnostics = new ArrayList<Diagnostic>();
        int blockStart = -1;
        String code = null;
        for (int index = 0; index <= lines.size(); index++) {
            String next = index < lines.size() ? errorCode(lines.get(index)) : null;
            boolean last = index == lines.size();
            if (last || next != null) {
                if (code != null) {
                    diagnostics.add(diagnostic(code, lines.subList(blockStart, index)));
                }
                code = next;
                blockStart = index;
            }
        }
        return List.copyOf(diagnostics);
    }

    private static @Nullable String errorCode(String line) {
        Matcher matcher = ERROR_CODE.matcher(line);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static Diagnostic diagnostic(String code, List<String> block) {
        return switch (code) {
            case ERESOLVE -> new Diagnostic(PACKAGE_JSON, null, null, eresolveMessage(block), Category.NPM_DEPENDENCY,
                    Severity.ERROR, null);
            case E404 -> new Diagnostic(PACKAGE_JSON, null, null, e404Message(block), Category.NPM_ERROR, Severity.ERROR,
                    null);
            default -> new Diagnostic(null, null, null,
                    storedRedacted(code + ": " + firstLine(block)),
                    Category.NPM_ERROR, Severity.ERROR, null);
        };
    }

    /**
     * Redacts a generic block's composed message before the stored-length cut, so a cut value cannot keep a prefix the
     * redactor would have matched whole. The dedicated `ERESOLVE` and `E404` messages are built from dependency names and
     * need no redaction.
     *
     * @param message the composed message
     * @return the redacted message, cut to the stored length
     */
    private static String storedRedacted(String message) {
        return DiagnosticCollector.stored(SecretRedactor.redactText(message).orElseThrow());
    }

    private static String eresolveMessage(List<String> block) {
        return findPeerConflict(block)
                .map(conflict -> "ERESOLVE: Could not resolve peer dependency " + conflict[0] + " from " + conflict[1])
                .orElse("ERESOLVE: Unable to resolve dependency tree");
    }

    private static String e404Message(List<String> block) {
        for (String line : block) {
            Matcher matcher = E404_PACKAGE.matcher(line);
            if (matcher.find()) {
                return "E404: Package '" + matcher.group(1) + "' not found in registry";
            }
        }
        return "E404: Package not found";
    }

    /**
     * Returns the last {@code peer <dependency> from <package>} trailer of the block, which is the one the original's
     * greedy {@code DOTALL} expression resolves to.
     */
    private static Optional<String[]> findPeerConflict(List<String> block) {
        int marker = -1;
        for (int index = 0; index < block.size(); index++) {
            if (CONFLICT_MARKER.matcher(block.get(index).strip()).matches()) {
                marker = index;
                break;
            }
        }
        if (marker < 0) {
            return Optional.empty();
        }
        String[] conflict = null;
        for (int index = marker + 1; index < block.size(); index++) {
            Matcher matcher = PEER_CONFLICT.matcher(block.get(index));
            while (matcher.find()) {
                conflict = new String[]{matcher.group(1), matcher.group(2)};
            }
        }
        return Optional.ofNullable(conflict);
    }

    /**
     * Returns the first reportable line of a block, the original's {@code message_lines[0]}: the first line that follows
     * {@code npm ERR!}, ignoring an empty remainder and the {@code A complete log} pointer.
     */
    private static String firstLine(List<String> block) {
        for (String line : block) {
            Matcher prefix = ERROR_PREFIX.matcher(line);
            if (prefix.find()) {
                String content = line.substring(prefix.end()).strip();
                if (!content.isEmpty() && !content.startsWith(BOILERPLATE_PREFIX)) {
                    return content;
                }
            }
        }
        return "npm command failed";
    }
}
