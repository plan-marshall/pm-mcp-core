/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.start;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves that the builder of {@link RuntimeDrain.Outcome} builds the value its canonical constructor builds. The exit
 * code and the three lists are generated; each list names one participant or failure.
 */
@EnableGeneratorController
@DisplayName("RuntimeDrain.Outcome builder")
class RuntimeDrainRecordsTest {

    @Test
    @DisplayName("the builder builds the drain outcome the constructor builds")
    void outcomeBuilderEqualsConstructor() {
        var exitCode = Generators.integers(0, 255).next();
        var ended = List.of(Generators.letterStrings(3, 12).next());
        var terminated = List.of(Generators.letterStrings(3, 12).next());
        var failures = List.of(Generators.letterStrings(3, 12).next());

        var constructed = new RuntimeDrain.Outcome(exitCode, ended, terminated, failures);
        var built = RuntimeDrain.Outcome.builder()
                .exitCode(exitCode)
                .ended(ended)
                .terminated(terminated)
                .failures(failures)
                .build();

        assertEquals(constructed, built, "the builder builds the same drain outcome");
    }
}
