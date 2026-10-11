/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.findings;

import java.util.List;

/**
 * A source of findings: a provider adapter that fetches what its provider reports for a scope and returns it as
 * normalized {@link Finding}s (PM-IMPL-1). Implemented by the provider modules and registered in a
 * {@link FindingRegistry} under its {@link #key()}, the kind of producer of the findings store's {@code source}
 * vocabulary with the provider id for {@code analysis}, so a finding is fetched under the key it is stored under.
 *
 * @since 0.1
 */
public interface FindingSource {

    /**
     * @return the key this source is registered under; its findings carry the same {@code source} and
     *         {@code provider}
     */
    FindingSourceKey key();

    /**
     * @param scope the commit or pull request the findings are asked for
     * @return the normalized findings of the scope, in the order of the provider
     */
    List<Finding> fetch(FindingScope scope);
}
