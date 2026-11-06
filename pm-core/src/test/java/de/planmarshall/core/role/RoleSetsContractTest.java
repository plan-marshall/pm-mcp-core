/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.valueobjects.junit5.contracts.ShouldHandleObjectContracts;
import java.io.Serializable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The canonical object methods of a shipped role (PM-WORK-1): the document bytes identify the value, and the mutable
 * array is compared by content rather than by identity. The record around it has compiler-generated equality; this
 * class is the one that carries the hand-written contract.
 */
@EnableGeneratorController
@DisplayName("Contract of a shipped role")
class RoleSetsContractTest implements ShouldHandleObjectContracts<RoleSets.ShippedRole> {

    @Override
    public RoleSets.ShippedRole getUnderTest() {
        return new RoleSets.ShippedRole(Generators.letterStrings(1, 40).next().getBytes(UTF_8));
    }

    @Test
    @DisplayName("the constructor and the accessor copy the document")
    void defensiveCopies() {
        var bytes = Generators.letterStrings(2, 40).next().getBytes(UTF_8);
        var original = bytes.clone();
        var role = new RoleSets.ShippedRole(bytes);
        bytes[0] = (byte) (bytes[0] + 1);
        var returned = role.document();
        returned[1] = (byte) (returned[1] + 1);

        assertAll(
                () -> assertArrayEquals(original, role.document(), "The document survives a mutated input"),
                () -> assertNotSame(returned, role.document(), "Each accessor call returns its own copy"));
    }

    /** The document is handed on as bytes; Java serialization is not part of its contract. */
    @Override
    @Test
    @DisplayName("a shipped role is deliberately not Serializable")
    public void shouldImplementSerializable() {
        assertFalse(Serializable.class.isAssignableFrom(RoleSets.ShippedRole.class),
                "The shipped role is not Java-serialized; its document bytes are the wire form");
    }
}
