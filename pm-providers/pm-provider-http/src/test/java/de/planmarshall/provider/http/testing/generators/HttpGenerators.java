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

import de.cuioss.test.generator.TypedGenerator;
import lombok.experimental.UtilityClass;

import de.planmarshall.provider.http.Endpoint;
import de.planmarshall.provider.http.PagedResult;
import de.planmarshall.provider.http.Response;

/**
 * Registers the generator artifact of {@code pm-provider-http} in one place: one factory method per
 * {@link TypedGenerator}, for {@code @TypeGeneratorFactorySource(factoryClass = HttpGenerators.class,
 * factoryMethod = "endpoints")}.
 *
 * @since 0.1
 */
@UtilityClass
public class HttpGenerators {

    /** @return a generator of valid endpoints */
    public static TypedGenerator<Endpoint> endpoints() {
        return new EndpointGenerator();
    }

    /** @return a generator of valid successful responses */
    public static TypedGenerator<Response> responses() {
        return new ResponseGenerator();
    }

    /** @return a generator of valid complete paged results */
    public static TypedGenerator<PagedResult> pagedResults() {
        return new PagedResultGenerator();
    }
}
