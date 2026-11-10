/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builder of the validator's endpoint block builds the value the canonical constructor builds.
 */
@DisplayName("Profile validator endpoint block builder")
class ProfileValidatorBuilderContractTest {

    @Test
    @DisplayName("the endpoint block builder builds the constructor's value")
    void shouldBuildEndpointBlockLikeTheConstructor() {
        var canonical = new ProfileValidator.Checker.EndpointBlock("openai_chat", "loopback",
                "http://127.0.0.1:11434", null);
        var built = ProfileValidator.Checker.EndpointBlock.builder().protocol(canonical.protocol())
                .locality(canonical.locality()).baseUrl(canonical.baseUrl())
                .credentialKey(canonical.credentialKey()).build();

        assertEquals(canonical, built, "the builder builds the constructor's value");
    }
}
