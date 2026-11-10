/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.guard.fixtures;

import de.cuioss.http.client.handler.HttpHandler;
import java.net.URI;

/**
 * Control of the outbound-calls guard: a class that builds an {@link HttpHandler} and is on no allow-list, on which
 * rule B must fail.
 */
public final class CuiHttpFixture {

    private CuiHttpFixture() {
    }

    /** @return a handler for a destination no list names */
    public static HttpHandler handler() {
        return HttpHandler.builder()
                .uri(URI.create("https://example.invalid/resource"))
                .connectionTimeoutSeconds(1)
                .readTimeoutSeconds(1)
                .build();
    }
}
