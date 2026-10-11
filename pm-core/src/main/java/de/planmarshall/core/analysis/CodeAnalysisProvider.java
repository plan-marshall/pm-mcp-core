/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.analysis;

import de.planmarshall.core.findings.Finding;
import de.planmarshall.core.findings.FindingScope;
import java.util.List;

/**
 * A code analysis provider (PM-IMPL-1), implemented by a provider module and registered under its {@link #id()} in
 * the {@link CodeAnalysisProviderRegistry}. It answers the two operations of code analysis for a commit or a pull
 * request, each with a closed {@link AnalysisOutcome}; a provider maps its own results to the closed values and
 * never adds one.
 * <p>
 * The issues it returns are findings of the source {@code analysis}, carrying the provider's {@link #id()} as their
 * {@code provider} and the provider's own key of each issue as {@code provider_ref.issue_key}.
 *
 * @since 0.1
 */
public interface CodeAnalysisProvider {

    /**
     * @return the id of the provider, matching the provider id pattern; the same id names it in its findings and in
     *         its analysis result records
     */
    String id();

    /**
     * The operation {@code analysis.status}: what the provider reports about the target.
     *
     * @param target the commit or pull request asked about
     * @return the analysis result for {@link AnalysisOutcome#OK}, without a completeness; no value for a transport
     *         outcome
     */
    AnalysisCallResult<AnalysisResult> status(FindingScope target);

    /**
     * The operation {@code analysis.issues}: the new issues the provider reports for the target.
     *
     * @param target the commit or pull request asked about
     * @return the issues as findings of the source {@code analysis} with the completeness of the read for
     *         {@link AnalysisOutcome#OK}; neither for a transport outcome
     */
    AnalysisCallResult<List<Finding>> issues(FindingScope target);
}
