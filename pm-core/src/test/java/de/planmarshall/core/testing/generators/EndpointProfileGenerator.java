/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.testing.generators;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;
import java.util.LinkedHashMap;

import de.planmarshall.core.profile.EndpointProfile;

/**
 * Generates a valid {@link EndpointProfile} in the loopback form of the {@code openai_chat} protocol: the serving
 * program, the identity probe, the update evidence and the weights probe are absent, and the client id, the endpoint
 * block and the model tiers carry generated values.
 *
 * @since 0.1
 */
public final class EndpointProfileGenerator implements TypedGenerator<EndpointProfile> {

    @Override
    public EndpointProfile next() {
        var suffix = Generators.integers(1, 65_535).next();
        var tiers = new LinkedHashMap<String, String>();
        tiers.put("fast", "model-fast-" + suffix);
        return new EndpointProfile("generated-client-" + suffix, null, null, null,
                new EndpointProfile.Endpoint("openai_chat", "loopback", "http://127.0.0.1:" + suffix, "/ready",
                        "/v1/models", "/data", "none", 8192, 1024, 30),
                tiers, null);
    }

    @Override
    public Class<EndpointProfile> getType() {
        return EndpointProfile.class;
    }
}
