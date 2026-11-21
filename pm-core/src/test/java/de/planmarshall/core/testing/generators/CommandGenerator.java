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
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

import de.planmarshall.core.id.CommandId;
import de.planmarshall.core.id.CorrelationId;
import de.planmarshall.core.spi.Command;
import de.planmarshall.core.spi.Provenance;
import de.planmarshall.core.spi.Provenance.Trigger;

/**
 * Generates a valid {@link Command}: generated command and correlation ids, a primitive id of two generated
 * identifiers, an {@link Input} record holding generated text, a provenance of a generated trigger with a generated
 * reference and a generated instant.
 *
 * @since 0.1
 */
public final class CommandGenerator implements TypedGenerator<Command> {

    /**
     * The parameter record a generated command carries.
     *
     * @param text generated text
     */
    public record Input(String text) {
    }

    @Override
    public Command next() {
        return Command.builder()
                .commandId(new CommandId("cmd-" + hexDigits()))
                .correlationId(new CorrelationId("cor-" + hexDigits()))
                .primitiveId(identifier() + "." + identifier())
                .input(new Input(Generators.nonBlankStrings().next()))
                .provenance(new Provenance(Generators.enumValues(Trigger.class).next(),
                        Generators.nonBlankStrings().next()))
                .issuedAt(Instant.ofEpochMilli(Generators.integers(0, Integer.MAX_VALUE).next().longValue()))
                .build();
    }

    @Override
    public Class<Command> getType() {
        return Command.class;
    }

    private static String hexDigits() {
        return HexFormat.of().toHexDigits(Generators.longs().next());
    }

    private static String identifier() {
        return Generators.letterStrings(1, 12).next().toLowerCase(Locale.ROOT);
    }
}
