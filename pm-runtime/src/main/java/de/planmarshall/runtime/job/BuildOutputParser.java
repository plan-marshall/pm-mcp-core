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
 *
 * @since 0.1
 */
public final class BuildOutputParser implements SubprocessJobRunner.OutputParser {

    private static final Pattern TYPESCRIPT_MARKER = Pattern.compile("\\berror TS\\d+\\b");

    private static final Pattern ESLINT_ROW = Pattern.compile("(?m)^\\s*\\d+:\\d+\\s+(?:error|warning)\\b");

    private static final Pattern ESLINT_SUMMARY = Pattern.compile("[✖✗]\\s*\\d+\\s+problems?");

    private static final Pattern JEST_MARKER = Pattern.compile("(?m)^\\s*(?:PASS|FAIL)\\s+\\S+|^\\s*Tests:\\s+");

    private static final Pattern TAP_MARKER = Pattern.compile("(?m)^(?:TAP version \\d+|1\\.\\.\\d+|(?:not )?ok \\d+)");

    private static final Pattern NPM_MARKER = Pattern.compile("(?m)^(?:npm ERR!|npm error) ");

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
        var text = BuildLogText.stripAnsi(output);
        return system(job).flatMap(buildSystem -> parse(buildSystem, text))
                .orElse(JobEvidence.ParseResult.NOTHING);
    }

    private static Optional<BuildSystem> system(JobRecord job) {
        var key = job.budgetKey();
        var separator = key.indexOf(':');
        return separator > 0 ? BuildSystem.fromWireName(key.substring(0, separator)) : Optional.empty();
    }

    private static Optional<JobEvidence.ParseResult> parse(BuildSystem buildSystem, String output) {
        return switch (buildSystem) {
            case MAVEN -> Optional.of(result(MavenOutputParser.parse(output)));
            case GRADLE -> Optional.of(result(GradleOutputParser.parse(output)));
            case NPM -> npm(output);
            case PYPROJECT -> Optional.of(result(PyprojectOutputParser.parse(output)));
        };
    }

    private static Optional<JobEvidence.ParseResult> npm(String output) {
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
            return Optional.of(result(NpmErrorParser.parse(output)));
        }
        return Optional.empty();
    }

    private static JobEvidence.ParseResult result(ParsedBuild build) {
        return result(build, false);
    }

    private static JobEvidence.ParseResult result(ParsedBuild build, boolean unplacedErrors) {
        var contradicting = build.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.severity() == Severity.ERROR)
                || unplacedErrors || failingTests(build.testCounts());
        return new JobEvidence.ParseResult(build.successMarker(), build.diagnostics().size(), contradicting,
                Optional.empty());
    }

    private static boolean failingTests(@Nullable TestCounts counts) {
        return counts instanceof TestCounts.Measured measured && measured.failed() > 0;
    }
}
