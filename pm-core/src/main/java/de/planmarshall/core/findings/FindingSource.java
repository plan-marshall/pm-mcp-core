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
 * A source of findings: a provider adapter that fetches what its provider reports for a scope and returns it
 * normalized (PM-IMPL-1). Implemented by the provider modules.
 *
 * @param <S> the scope a source is asked for
 * @param <F> the normalized finding
 * @since 0.1
 */
@FunctionalInterface
public interface FindingSource<S, F> {

    /**
     * @param scope what the findings are asked for
     * @return the normalized findings of the scope, in the order of the provider
     */
    List<F> fetch(S scope);
}
