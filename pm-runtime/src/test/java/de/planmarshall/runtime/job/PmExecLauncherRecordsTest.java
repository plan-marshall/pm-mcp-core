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
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves that the builder of the {@link PmExecLauncher.Launch} record builds the same value as the canonical
 * constructor, including its null refusal for the working directory. Every value is generated; the empty confinement
 * is the contract value of a launch that composes none.
 */
@EnableGeneratorController
@DisplayName("PmExecLauncher.Launch builder")
class PmExecLauncherRecordsTest {

    @Test
    @DisplayName("the builder builds the launch the constructor builds")
    void launchBuilderEqualsConstructor() {
        var workingDirectory = Path.of("/tmp", Generators.letterStrings(1, 20).next());
        var argv = List.of(Path.of("/usr/bin", Generators.letterStrings(1, 20).next()).toString());
        var environment = Map.of(Generators.letterStrings(1, 10).next().toUpperCase(Locale.ROOT),
                Generators.letterStrings(1, 20).next());
        var stdin = Generators.letterStrings(1, 20).next();
        var log = Path.of("/tmp", Generators.letterStrings(1, 20).next() + ".log");
        var confinement = JobConfinement.none();

        var constructed = new PmExecLauncher.Launch(workingDirectory, argv, environment, stdin, log, confinement);
        var built = PmExecLauncher.Launch.builder()
                .workingDirectory(workingDirectory)
                .argv(argv)
                .environment(environment)
                .stdin(stdin)
                .log(log)
                .confinement(confinement)
                .build();

        assertEquals(constructed, built, "the builder builds the same launch value");
    }

    @Test
    @DisplayName("the builder refuses a missing working directory like the constructor")
    void launchBuilderRefusesMissingWorkingDirectory() {
        var argv = List.of(Path.of("/usr/bin", Generators.letterStrings(1, 20).next()).toString());
        var environment = Map.of(Generators.letterStrings(1, 10).next().toUpperCase(Locale.ROOT),
                Generators.letterStrings(1, 20).next());
        var log = Path.of("/tmp", Generators.letterStrings(1, 20).next() + ".log");

        var refusal = assertThrows(NullPointerException.class, () -> PmExecLauncher.Launch.builder()
                .argv(argv).environment(environment).log(log).confinement(JobConfinement.none()).build());

        assertEquals("workingDirectory", refusal.getMessage(), "the refusal names the working directory");
    }
}
