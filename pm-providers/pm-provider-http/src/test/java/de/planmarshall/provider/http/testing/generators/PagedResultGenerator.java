/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.http.testing.generators;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;
import java.util.List;

import de.planmarshall.provider.http.PagedResult;

/**
 * Generates a valid, complete {@link PagedResult} of one page whose last response is a generated successful
 * {@link de.planmarshall.provider.http.Response}.
 *
 * @since 0.1
 */
public final class PagedResultGenerator implements TypedGenerator<PagedResult> {

    private final ResponseGenerator responses = new ResponseGenerator();

    @Override
    public PagedResult next() {
        var suffix = Generators.integers(1, 999).next();
        return new PagedResult(List.of("{\"page\":" + suffix + "}"), true, responses.next());
    }

    @Override
    public Class<PagedResult> getType() {
        return PagedResult.class;
    }
}
