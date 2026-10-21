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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * The build test of the shipped profile set (PM-CLIENT-5, Profile Sets): every profile the release ships loads and
 * validates, its {@code client_id} is the name of its resource, and the detection precedences are pairwise different.
 */
@DisplayName("Shipped client profiles")
class ShippedProfilesTest {

    @Test
    @DisplayName("every shipped profile loads and validates")
    void everyShippedProfileValidates() {
        var shipped = ShippedProfiles.read().shipped();

        assertFalse(shipped.isEmpty(),
                "The resources of pm-api must hold at least one profile below classpath:/profiles/");
        assertAll(shipped.keySet().stream().map(clientId -> (Executable) () -> {
            var document = shipped.get(clientId).document();
            var profile = ProfileValidator.validate(document, "profiles/" + clientId + ".json", ProfileRules.none());
            assertEquals(clientId, ProfileValidator.clientId(profile),
                    "The client id of the profile must equal the name of its resource");
        }).toList());
    }

    @Test
    @DisplayName("the detection precedences of the shipped profiles are pairwise different")
    void precedencesArePairwiseDifferent() {
        var shipped = ShippedProfiles.read().shipped();
        var seen = new HashSet<Long>();
        for (var entry : shipped.entrySet()) {
            var profile = ProfileValidator.validate(entry.getValue().document(),
                    "profiles/" + entry.getKey() + ".json", ProfileRules.none());
            var detection = profile.member("detection").filter(value -> !value.isNull());
            if (detection.isEmpty()) {
                continue;
            }
            var precedence = detection.get().member("precedence");
            if (precedence.isEmpty() || precedence.get().integerValue().isEmpty()) {
                continue;
            }
            var value = precedence.get().integerValue().getAsLong();
            assertTrue(seen.add(value), () -> "The detection precedence %d occurs twice".formatted(value));
        }
    }
}
