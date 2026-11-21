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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.spi.Command;

@DisplayName("Command generator")
@EnableGeneratorController
class CommandGeneratorTest {

    private final CommandGenerator generator = new CommandGenerator();

    @Test
    @DisplayName("Should yield valid and distinct commands")
    void shouldYieldValidAndDistinctCommands() {
        var first = generator.next();
        var second = generator.next();

        assertAll("the generated commands",
                () -> assertNotNull(first, "The generator yields a value"),
                () -> assertNotEquals(first.commandId(), second.commandId(), "Two calls yield distinct command ids"),
                () -> assertInstanceOf(CommandGenerator.Input.class, first.input(),
                        "The command carries the generated input"),
                () -> assertNotNull(first.provenance().triggerRef(), "The provenance carries a reference"));
    }

    @Test
    @DisplayName("Should be registered by the factory of the artifact")
    void shouldBeRegisteredByTheFactory() {
        assertInstanceOf(CommandGenerator.class, CoreGenerators.commands(), "The factory yields the generator");
    }

    @Test
    @DisplayName("Should report the generated type")
    void shouldReportTheGeneratedType() {
        assertEquals(Command.class, generator.getType(), "The generator names the command type");
    }
}
