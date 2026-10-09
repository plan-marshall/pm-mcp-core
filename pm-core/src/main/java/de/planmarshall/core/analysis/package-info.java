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
 * The provider-neutral code-analysis contract: the analysis status of a commit or pull request ({@link
 * de.planmarshall.core.analysis.AnalysisStatus}), the quality gate result and the new issues of a code analysis
 * provider. A provider adapter maps its results to the closed status values.
 * <p>
 * Specification:
 * <ul>
 * <li>{@code doc/specification/module-structure.adoc} (Findings &amp; Code-Analysis Contracts)</li>
 * <li>{@code doc/specification/phase-workflows/04-finalize-step-bands.adoc}</li>
 * <li>{@code doc/specification/hypermedia-format/03-composite-wait-and-encoder.adoc} ({@code analysis_status})</li>
 * </ul>
 */
package de.planmarshall.core.analysis;
