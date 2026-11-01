/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
/**
 * The build output parsers, the evaluation of a gate, and the compiled artifact pattern table.
 * <p>
 * A parser is a pure function of the log text: it reads the text of one build tool and returns a {@link
 * de.planmarshall.core.build.ParsedBuild} — the diagnostics the tool reported, the status it printed, the test counts it
 * aggregated and the verdict on whether it ran any test at all. The counts and the verdict may not be guessed: an
 * aggregation the parser cannot make is {@link de.planmarshall.core.build.TestCounts.Unmeasured} and carries no number,
 * and a {@code no_tests_ran} verdict needs the tool's own positive marker.
 * <p>
 * The parsers delivered so far are {@link de.planmarshall.core.build.MavenOutputParser}, {@link
 * de.planmarshall.core.build.GradleOutputParser} and {@link de.planmarshall.core.build.OpenRewriteLogParser}; each is
 * ported from the {@code plan-marshall} parser module of the same tool, and its tests are ported from that module's
 * pytest suite. The npm, TypeScript, ESLint, Jest, TAP and Python parsers are not implemented yet.
 * <p>
 * The category table of the JVM parsers is compiled once, in {@link de.planmarshall.core.build.JvmCategoryPatterns}, and
 * shared by Maven and Gradle. The port reproduces the originals' parsing, and every behaviour it changes on purpose — an
 * {@code [ERROR]} line no pattern recognises is {@link de.planmarshall.core.build.Category#UNRECOGNIZED}, an unmeasured
 * population carries no number, a {@code no_tests_ran} verdict needs the tool's own marker, and a format drift is never
 * read as zero findings — has a fixture of its own and is listed in {@code pm-core/doc/adr/core-build/002}.
 * <p>
 * Specification: {@code doc/specification/job-runtime/03-parsers-and-gates.adoc}. Developer guide:
 * {@code pm-core/doc/developer/core-build.adoc}. The decisions of the package are recorded in
 * {@code pm-core/doc/adr/core-build/}.
 */
@NullMarked
package de.planmarshall.core.build;

import org.jspecify.annotations.NullMarked;
