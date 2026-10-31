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
import org.jspecify.annotations.Nullable;

import de.planmarshall.provider.http.Endpoint;

/**
 * Generates a valid {@link Endpoint}. With no argument it yields a generated {@code https} API base ending with
 * {@code /} and one generated redirect host; with a base URI it yields that base without redirect hosts, which is what
 * a test needs for a mock server it has just started.
 *
 * @since 0.1
 */
public final class EndpointGenerator implements TypedGenerator<Endpoint> {

    private final @Nullable URI fixedBaseUri;

    /** Creates the generator of a generated base URI. */
    public EndpointGenerator() {
        this.fixedBaseUri = null;
    }

    /**
     * @param baseUri the base URI of the endpoint, for example the address of a mock server
     */
    public EndpointGenerator(URI baseUri) {
        this.fixedBaseUri = baseUri;
    }

    @Override
    public Endpoint next() {
        if (fixedBaseUri != null) {
            return Endpoint.of(fixedBaseUri);
        }
        var suffix = Generators.longs(1, Long.MAX_VALUE).next();
        return new Endpoint(URI.create("https://api-" + suffix + ".example.test/"),
                Set.of("cdn-" + suffix + ".example.test"));
    }

    @Override
    public Class<Endpoint> getType() {
        return Endpoint.class;
    }
}
