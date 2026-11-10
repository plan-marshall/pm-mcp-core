/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves that the builder of {@link JobConfinementComposer} builds the same value as the canonical constructor,
 * including its null refusal for the runtime base directory. Every path is generated as an absolute path; the session
 * bus socket names the contract location of a per-user bus.
 */
@EnableGeneratorController
@DisplayName("JobConfinementComposer builder")
class JobConfinementComposerRecordsTest {

    @Test
    @DisplayName("the builder builds the composer the constructor builds")
    void composerBuilderEqualsConstructor() {
        var base = Path.of("/tmp", Generators.letterStrings(1, 20).next());
        var systemTemp = Path.of("/tmp", Generators.letterStrings(1, 20).next());
        var deviceRoot = Path.of("/dev", Generators.letterStrings(1, 20).next());
        var sessionBusSocket = Path.of("/run/user", Generators.letterStrings(1, 20).next(), "bus");

        var constructed = new JobConfinementComposer(base, systemTemp, deviceRoot, sessionBusSocket);
        var built = JobConfinementComposer.builder()
                .base(base)
                .systemTemp(systemTemp)
                .deviceRoot(deviceRoot)
                .sessionBusSocket(sessionBusSocket)
                .build();

        assertEquals(constructed, built, "the builder builds the same confinement composer");
    }

    @Test
    @DisplayName("the builder refuses a missing runtime base like the constructor")
    void composerBuilderRefusesMissingBase() {
        var builder = JobConfinementComposer.builder()
                .systemTemp(Path.of("/tmp", Generators.letterStrings(1, 20).next()))
                .deviceRoot(Path.of("/dev", Generators.letterStrings(1, 20).next()));
        var refusal = assertThrows(NullPointerException.class, builder::build);

        assertEquals("base", refusal.getMessage(), "the refusal names the runtime base");
    }
}
