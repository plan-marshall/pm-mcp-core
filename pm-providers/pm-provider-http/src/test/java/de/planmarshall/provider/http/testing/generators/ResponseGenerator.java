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
import java.util.Optional;

import de.planmarshall.provider.http.Response;

/**
 * Generates a valid successful {@link Response} with a generated body and {@code ETag}; the next target and the rate
 * limit reset instant are absent.
 *
 * @since 0.1
 */
public final class ResponseGenerator implements TypedGenerator<Response> {

    @Override
    public Response next() {
        var suffix = Generators.longs(1, Long.MAX_VALUE).next();
        return new Response(Response.Outcome.OK, 200, "{\"value\":\"" + suffix + "\"}",
                Optional.of("etag-" + suffix), Optional.empty(), Optional.empty(), "");
    }

    @Override
    public Class<Response> getType() {
        return Response.class;
    }
}
