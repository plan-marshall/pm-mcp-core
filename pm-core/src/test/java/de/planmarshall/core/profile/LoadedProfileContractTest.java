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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.valueobjects.junit5.contracts.ShouldHandleObjectContracts;
import java.io.Serializable;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The canonical object methods of a loaded profile (PM-CLIENT-5): the client id, the set, the document and the bytes
 * identify the value, and the mutable byte array is compared by content rather than by identity.
 */
@EnableGeneratorController
@DisplayName("Contract of a loaded profile")
class LoadedProfileContractTest implements ShouldHandleObjectContracts<LoadedProfile> {

    @Override
    public LoadedProfile getUnderTest() {
        var document = JsonNode.object(Map.of(Generators.letterStrings(1, 8).next(),
                JsonNode.string(Generators.letterStrings(1, 20).next())));
        return new LoadedProfile(Generators.letterStrings(1, 20).next(), ProfileSetKind.MACHINE, document,
                Generators.letterStrings(1, 40).next().getBytes(UTF_8), LoadedProfile.State.VERIFIED, null);
    }

    /**
     * A profile is handed on as its document bytes and canonical digest; Java serialization is not part of its
     * contract.
     */
    @Override
    @Test
    @DisplayName("a loaded profile is deliberately not Serializable")
    public void shouldImplementSerializable() {
        assertFalse(Serializable.class.isAssignableFrom(LoadedProfile.class),
                "A loaded profile is not Java-serialized; its document bytes are the wire form");
    }
}
