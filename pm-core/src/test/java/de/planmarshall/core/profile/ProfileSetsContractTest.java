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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The canonical object methods of a shipped profile (PM-CLIENT-5): the document bytes identify the value, and the
 * mutable array is compared by content rather than by identity. The record around it has compiler-generated equality;
 * this class is the one that carries the hand-written contract.
 */
@EnableGeneratorController
@DisplayName("Contract of a shipped profile")
class ProfileSetsContractTest implements ShouldHandleObjectContracts<ProfileSets.ShippedProfile> {

    @Override
    public ProfileSets.ShippedProfile getUnderTest() {
        return new ProfileSets.ShippedProfile(Generators.letterStrings(1, 40).next().getBytes(UTF_8));
    }

    /** The document is handed on as bytes; Java serialization is not part of its contract. */
    @Override
    @Test
    @DisplayName("a shipped profile is deliberately not Serializable")
    public void shouldImplementSerializable() {
        assertFalse(Serializable.class.isAssignableFrom(ProfileSets.ShippedProfile.class),
                "The shipped profile is not Java-serialized; its document bytes are the wire form");
    }
}
