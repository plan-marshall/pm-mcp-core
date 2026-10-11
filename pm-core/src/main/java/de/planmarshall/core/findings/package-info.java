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
 * The provider-neutral findings contract (PM-IMPL-1), implemented by the provider modules:
 * <ul>
 * <li>the findings ledger record {@link de.planmarshall.core.findings.Finding}, with every member of its schema
 * and the closed store enumerations ({@link de.planmarshall.core.findings.FindingSourceKind},
 * {@link de.planmarshall.core.findings.Severity}, {@link de.planmarshall.core.findings.FindingStatus},
 * {@link de.planmarshall.core.findings.Resolution}, {@link de.planmarshall.core.findings.ScopeClass},
 * {@link de.planmarshall.core.findings.TransmitOutcome}, {@link de.planmarshall.core.findings.TransmitReason},
 * {@link de.planmarshall.core.findings.ResolveState}, {@link de.planmarshall.core.findings.FindingPhase}) and the
 * closed dispositions of triage ({@link de.planmarshall.core.findings.Disposition});</li>
 * <li>the scope {@link de.planmarshall.core.findings.FindingScope}, a commit or a pull request, which is also the
 * target of code analysis, so {@code core.analysis} depends on this package and never the reverse;</li>
 * <li>the registry key {@link de.planmarshall.core.findings.FindingSourceKey}: the kind of producer of the store's
 * {@code source} vocabulary, with the provider id for {@code analysis};</li>
 * <li>a source of normalized findings ({@link de.planmarshall.core.findings.FindingSource}) and the answer to a
 * finding on its provider ({@link de.planmarshall.core.findings.FindingResponder}), each registered under its key in
 * the {@link de.planmarshall.core.findings.FindingRegistry}, whose members are passed to its constructor.</li>
 * </ul>
 * The package holds declarations, validation and lookup only: it makes no HTTP call and writes no file or store.
 * <p>
 * Specification:
 * <ul>
 * <li>{@code doc/specification/module-structure.adoc} (Findings &amp; Code-Analysis Contracts, Packages, Dependency
 * Rules)</li>
 * <li>{@code doc/specification/phase-workflows/05-finalize-review-metrics-descriptor.adoc}</li>
 * <li>{@code doc/specification/store-schemas/03-findings-and-review-records.adoc} (Findings Ledger Record)</li>
 * </ul>
 */
package de.planmarshall.core.findings;
