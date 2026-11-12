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
import java.util.List;

import de.planmarshall.core.profile.EndpointProfile;

/**
 * Generates a valid {@link EndpointProfile} of the {@code openai_chat} protocol on the loopback interface: the update
 * evidence and the weights probe are absent; the client id, the serving program, the base URL (with its generated port)
 * and the model tier carry generated values, and the identity is a fixed version probe.
 *
 * @since 0.1
 */
public final class EndpointProfileGenerator implements TypedGenerator<EndpointProfile> {

    @Override
    public EndpointProfile next() {
        var suffix = Generators.longs(1, Long.MAX_VALUE).next();
        var tiers = new LinkedHashMap<String, String>();
        tiers.put("fast", "model-fast-" + suffix);
        return new EndpointProfile("generated-client-" + suffix, "generated-server-" + suffix,
                new EndpointProfile.Identity(List.of("--version"), "version ([0-9.]+)"), null,
                new EndpointProfile.Endpoint("openai_chat", "http://127.0.0.1:" + port(), "/ready",
                        "/v1/models", "/data", "none", 8192, 1024, 30),
                tiers, null);
    }

    private static int port() {
        return Generators.integers(1, 65_535).next();
    }

    @Override
    public Class<EndpointProfile> getType() {
        return EndpointProfile.class;
    }
}
