/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.job;

import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.build.BuildLogText;
import de.planmarshall.core.build.EslintOutputParser;
import de.planmarshall.core.build.EslintResult;
import de.planmarshall.core.build.GradleOutputParser;
import de.planmarshall.core.build.JestOutputParser;
import de.planmarshall.core.build.MavenOutputParser;
import de.planmarshall.core.build.NpmErrorParser;
import de.planmarshall.core.build.ParsedBuild;
import de.planmarshall.core.build.PyprojectOutputParser;
import de.planmarshall.core.build.Severity;
import de.planmarshall.core.build.TapOutputParser;
import de.planmarshall.core.build.TestCounts;
import de.planmarshall.core.build.TypeScriptOutputParser;
import de.planmarshall.core.store.JobId;
import de.planmarshall.core.store.JobRecord;
import de.planmarshall.core.store.ProjectEnrolment.BuildSystem;

/**
 * The adapter from the native build log parsers of {@code core.build} to the runner's
 * {@link SubprocessJobRunner.OutputParser} (Job Runtime § Native Build Output Parsers and § Unparseable Output
 * Fallback).
 * <p>
 * The build system of the job decides the parser. It is the first component of the job's budget key, which the
 * Timeouts &amp; Budgets specification fixes as {@code <build_system>:<verb>[@<module_id>]}: {@code maven},
 * {@code gradle}, {@code npm} and {@code pyproject}. An {@code npm} log can be written by several tools, so the log's
 * own markers select among the npm error parser and the TypeScript, ESLint, Jest and TAP scanners, the first match
 * winning; a log that carries none of their markers, a key of an unknown build system and a job of a kind other than
 * {@code build} all yield {@link JobEvidence.ParseResult#NOTHING}.
 * <p>
 * The parsed build becomes the {@link JobEvidence.ParseResult}: the positive success marker the tool printed, the
 * number of diagnostics the parser located, and the contradicting evidence — an error-severity diagnostic or a
 * measured failing test count. A warning beside a green build contradicts nothing. A green {@code status} alone is not
 * a success marker: a tool that prints no positive marker keeps a zero exit {@code indeterminate} by row 8 of the
 * outcome derivation rather than guessing a success from the absence of diagnostics. The npm family prints three kinds
 * of marker and each is dispatched: Jest's and TAP's passing summary, and the install family's completion line
 * ({@code added|removed|changed ... packages}, {@code up to date, audited N packages}). A clean {@code tsc} or ESLint
 * run prints nothing at all, so it stays {@code indeterminate}: the job's verb does not stand in for a printed
 * summary.
 * <p>
 * The install family's completion line is a success marker only for a job whose verb — the second component of the
 * budget key — is {@code install} or {@code clean-install}. A job of any other verb that runs {@code npm ci} before
 * its own tool prints that line too, and it says nothing about the tool the verb names; when the npm branch reaches
 * the npm error parser under such a verb, the result carries no success marker. Its diagnostics, its contradicting
 * evidence and the dispatch order stay as they are. A test runner no scanner here reads (mocha, vitest) after
 * {@code npm ci} under a non-install verb therefore stays {@code indeterminate}.
 * <p>
 * The stripped text is cut to {@link BuildLogText#MAX_LINE_LENGTH} characters a line by
 * {@link BuildLogText#boundLines(String)} before any dispatch pattern sees it. Every whitespace repetition of the
 * ESLint and Jest markers — the leading run ({@code ^[ \t]*} rather than {@code ^\s*}) and the runs between their
 * parts — is horizontal space only, so none of them crosses a line end and a marker never takes the next line's first
 * word. The TypeScript, TAP and npm markers carry no whitespace repetition; the TAP and npm markers anchor with
 * {@code ^}.
 *
 * @since 0.1
 */
public final class BuildOutputParser implements SubprocessJobRunner.OutputParser {

    /** A tsc diagnostic code; it carries no whitespace repetition. */
    private static final Pattern TYPESCRIPT_MARKER = Pattern.compile("\\berror TS\\d+\\b");

    /**
     * An ESLint row: the position, then the severity. Its leading run and the run between the position and the severity
     * are horizontal space only, so a position that ends its line is no row.
     */
    private static final Pattern ESLINT_ROW = Pattern.compile("(?m)^[ \\t]*\\d+:\\d+[ \\t]+(?:error|warning)\\b");

    /**
     * The ESLint problem summary: the runs around its count are horizontal space only, so a mark that ends its line is
     * no summary.
     */
    private static final Pattern ESLINT_SUMMARY = Pattern.compile("[✖✗][ \\t]*\\d+[ \\t]+problems?");

    /**
     * A Jest suite header or summary: its leading runs and the run after {@code PASS}, {@code FAIL} or {@code Tests:}
     * are horizontal space only, so a header or a label that ends its line is no marker.
     */
    private static final Pattern JEST_MARKER =
            Pattern.compile("(?m)^[ \\t]*(?:PASS|FAIL)[ \\t]+\\S+|^[ \\t]*Tests:[ \\t]+");

    /** A TAP line; it anchors with {@code ^} and carries no whitespace repetition. */
    private static final Pattern TAP_MARKER = Pattern.compile("(?m)^(?:TAP version \\d+|1\\.\\.\\d+|(?:not )?ok \\d+)");

    /** An npm error line; it anchors with {@code ^} and carries no repetition. */
    private static final Pattern NPM_MARKER = Pattern.compile("(?m)^(?:npm ERR!|npm error) ");

    private static final String INSTALL_VERB = "install";

    private static final String CLEAN_INSTALL_VERB = "clean-install";

    /**
     * Parses the log of an ended job.
     *
     * @param job    the job record, whose kind and budget key select the parser
     * @param output the job's output after its launch report
     * @return what the selected parser found, or {@link JobEvidence.ParseResult#NOTHING}
     */
    @Override
    public JobEvidence.ParseResult parse(JobRecord job, String output) {
        if (job.kind() != JobId.Kind.BUILD) {
            return JobEvidence.ParseResult.NOTHING;
        }
        var text = BuildLogText.boundLines(BuildLogText.stripAnsi(output));
        var verb = verb(job.budgetKey());
        return system(job).flatMap(buildSystem -> parse(buildSystem, verb, text))
                .orElse(JobEvidence.ParseResult.NOTHING);
    }

    private static Optional<BuildSystem> system(JobRecord job) {
        var key = job.budgetKey();
        var separator = key.indexOf(':');
        return separator > 0 ? BuildSystem.fromWireName(key.substring(0, separator)) : Optional.empty();
    }

    // The verb of a budget key <build_system>:<verb>[@<module_id>]; the empty string for a key without a separator.
    private static String verb(String key) {
        var separator = key.indexOf(':');
        if (separator < 0) {
            return "";
        }
        var module = key.indexOf('@', separator + 1);
        return module < 0 ? key.substring(separator + 1) : key.substring(separator + 1, module);
    }

    private static Optional<JobEvidence.ParseResult> parse(BuildSystem buildSystem, String verb, String output) {
        return switch (buildSystem) {
            case MAVEN -> Optional.of(result(MavenOutputParser.parse(output)));
            case GRADLE -> Optional.of(result(GradleOutputParser.parse(output)));
            case NPM -> npm(verb, output);
            case PYPROJECT -> Optional.of(result(PyprojectOutputParser.parse(output)));
        };
    }

    private static boolean installVerb(String verb) {
        return INSTALL_VERB.equals(verb) || CLEAN_INSTALL_VERB.equals(verb);
    }

    private static Optional<JobEvidence.ParseResult> npm(String verb, String output) {
        if (TYPESCRIPT_MARKER.matcher(output).find()) {
            return Optional.of(result(TypeScriptOutputParser.parse(output)));
        }
        if (ESLINT_ROW.matcher(output).find() || ESLINT_SUMMARY.matcher(output).find()) {
            EslintResult eslint = EslintOutputParser.parse(output);
            return Optional.of(result(eslint.build(), eslint.problems() != null && eslint.problems().errors() > 0));
        }
        if (JEST_MARKER.matcher(output).find()) {
            return Optional.of(result(JestOutputParser.parse(output)));
        }
        if (TAP_MARKER.matcher(output).find()) {
            return Optional.of(result(TapOutputParser.parse(output)));
        }
        if (NPM_MARKER.matcher(output).find() || NpmErrorParser.INSTALL_SUMMARY.matcher(output).find()) {
            return Optional.of(result(NpmErrorParser.parse(output), false, installVerb(verb)));
        }
        return Optional.empty();
    }

    private static JobEvidence.ParseResult result(ParsedBuild build) {
        return result(build, false);
    }

    private static JobEvidence.ParseResult result(ParsedBuild build, boolean unplacedErrors) {
        return result(build, unplacedErrors, true);
    }

    private static JobEvidence.ParseResult result(ParsedBuild build, boolean unplacedErrors,
            boolean markerAdmitted) {
        var contradicting = build.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.severity() == Severity.ERROR)
                || unplacedErrors || failingTests(build.testCounts());
        return new JobEvidence.ParseResult(markerAdmitted && build.successMarker(), build.diagnostics().size(),
                contradicting, null);
    }

    private static boolean failingTests(@Nullable TestCounts counts) {
        return counts instanceof TestCounts.Measured measured && measured.failed() > 0;
    }
}
