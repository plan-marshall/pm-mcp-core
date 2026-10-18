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
 * The provider-neutral code-analysis contract (PM-IMPL-1, PM-IMPL-2), implemented by the provider modules:
 * <ul>
 * <li>the closed analysis status of a commit or pull request ({@link de.planmarshall.core.analysis.AnalysisStatus}),
 * to which every provider maps its results;</li>
 * <li>the analysis result record ({@link de.planmarshall.core.analysis.AnalysisResult}), one observation of one
 * provider for one target with its quality gate and its count of new issues, checked against the consistency rules
 * of the specification on construction, and its strict canonical JSON line
 * ({@link de.planmarshall.core.analysis.AnalysisResultCodec});</li>
 * <li>the provider contract ({@link de.planmarshall.core.analysis.CodeAnalysisProvider}) with its two operations
 * {@code analysis.status} and {@code analysis.issues}, each answering an
 * {@link de.planmarshall.core.analysis.AnalysisCallResult} with the closed
 * {@link de.planmarshall.core.analysis.AnalysisOutcome} and, for the issues, the
 * {@link de.planmarshall.core.analysis.Completeness} of the read;</li>
 * <li>the {@link de.planmarshall.core.analysis.CodeAnalysisProviderRegistry}, whose providers are passed to its
 * constructor and found by their id.</li>
 * </ul>
 * The target of both operations is the {@link de.planmarshall.core.findings.FindingScope} of {@code core.findings},
 * and the issues are its {@link de.planmarshall.core.findings.Finding}s of the source {@code analysis}: this package
 * depends on {@code core.findings}, never the reverse. It holds declarations, serialization and validation only: it
 * makes no HTTP call and writes no file or store.
 * <p>
 * Specification:
 * <ul>
 * <li>{@code plan-marshall-documentation/doc/specification/module-structure.adoc} (Findings &amp; Code-Analysis Contracts, Packages)</li>
 * <li>{@code doc/specification/store-schemas/03-findings-and-review-records.adoc} (Analysis Result Record)</li>
 * <li>{@code doc/specification/phase-workflows/04-finalize-step-bands.adoc}</li>
 * <li>{@code doc/specification/hypermedia-format/03-composite-wait-and-encoder.adoc} ({@code analysis_status})</li>
 * <li>{@code doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc} (Outcome Derivation, the domain and
 * transport outcomes of an in-process operation); the outcome table itself is carried by {@code OutcomeDerivation} of
 * {@code pm-runtime} ({@code pm-runtime/doc/developer/runtime-job.adoc}, Outcome Derivation)</li>
 * </ul>
 */
package de.planmarshall.core.analysis;
