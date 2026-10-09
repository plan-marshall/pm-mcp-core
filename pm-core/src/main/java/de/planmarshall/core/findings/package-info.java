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
 * The provider-neutral findings contract: a source of normalized findings ({@link
 * de.planmarshall.core.findings.FindingSource}), the answer to a finding on its provider ({@link
 * de.planmarshall.core.findings.FindingResponder}) and the closed dispositions of triage ({@link
 * de.planmarshall.core.findings.Disposition}). The provider modules implement it.
 * <p>
 * Specification:
 * <ul>
 * <li>{@code doc/specification/module-structure.adoc} (Findings &amp; Code-Analysis Contracts)</li>
 * <li>{@code doc/specification/phase-workflows/05-finalize-review-metrics-descriptor.adoc}</li>
 * <li>{@code doc/specification/store-schemas/03-findings-and-review-records.adoc}</li>
 * </ul>
 */
package de.planmarshall.core.findings;
