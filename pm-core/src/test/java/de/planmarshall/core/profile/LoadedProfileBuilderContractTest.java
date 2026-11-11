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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builder of {@link LoadedProfile} builds the value the canonical constructor builds, including its copied document
 * bytes.
 */
@EnableGeneratorController
@DisplayName("Loaded profile builder")
class LoadedProfileBuilderContractTest {

    @Test
    @DisplayName("the builder builds the constructor's value")
    void shouldBuildLikeTheConstructor() {
        var document = JsonNode.object(Map.of("client_id", JsonNode.string(Generators.letterStrings(3, 12).next())));

        var canonical = new LoadedProfile(Generators.letterStrings(3, 12).next(), ProfileSetKind.MACHINE, document,
                new byte[]{1, 2, 3}, LoadedProfile.State.VERIFIED, null);
        var built = LoadedProfile.builder().clientId(canonical.clientId()).kind(canonical.kind())
                .document(canonical.document()).bytes(canonical.bytes()).state(canonical.state())
                .verificationRecord(canonical.verificationRecord()).build();

        assertEquals(canonical, built, "the builder builds the constructor's value");
    }
}
