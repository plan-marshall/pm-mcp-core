/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.guard.fixtures;

import java.net.http.HttpClient;

/**
 * Control of the analysis boundary guard: a class calling {@link HttpClient}, on which the rule must fail.
 */
public final class HttpClientFixture {

    private HttpClientFixture() {
    }

    /** @return a new HTTP client */
    public static HttpClient client() {
        return HttpClient.newHttpClient();
    }
}
