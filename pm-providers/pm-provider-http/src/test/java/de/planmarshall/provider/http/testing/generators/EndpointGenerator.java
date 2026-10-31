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
import java.net.URI;
import java.util.Set;

import de.planmarshall.provider.http.Endpoint;

/**
 * Generates a valid {@link Endpoint}: an {@code https} API base ending with {@code /} and one generated redirect host.
 *
 * @since 0.1
 */
public final class EndpointGenerator implements TypedGenerator<Endpoint> {

    @Override
    public Endpoint next() {
        var suffix = Generators.integers(1, 999).next();
        return new Endpoint(URI.create("https://api-" + suffix + ".example.test/"),
                Set.of("cdn-" + suffix + ".example.test"));
    }

    @Override
    public Class<Endpoint> getType() {
        return Endpoint.class;
    }
}
