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
import static org.junit.jupiter.api.Assertions.assertFalse;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.valueobjects.junit5.contracts.ShouldHandleObjectContracts;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The canonical object methods of the role sets (PM-WORK-1): the shipped roles and the two directories are the value,
 * and {@link RoleSets.ShippedRole} compares the document bytes by content.
 */
@EnableGeneratorController
@DisplayName("Contract of the role sets")
class RoleSetsContractTest implements ShouldHandleObjectContracts<RoleSets> {

    @Override
    public RoleSets getUnderTest() {
        var shipped = Map.of(Generators.letterStrings(1, 8).next(),
                new RoleSets.ShippedRole(Generators.letterStrings(1, 40).next().getBytes(UTF_8)));
        return new RoleSets(shipped, Path.of(Generators.letterStrings(1, 8).next()),
                Path.of(Generators.letterStrings(1, 8).next()));
    }

    /** The sets name directories of the machine; they are resolved in place, never serialized. */
    @Override
    @Test
    @DisplayName("the role sets are deliberately not Serializable")
    public void shouldImplementSerializable() {
        assertFalse(Serializable.class.isAssignableFrom(RoleSets.class),
                "The role sets are not Java-serialized; their directories are resolved in place");
    }
}
