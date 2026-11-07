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

import static de.planmarshall.runtime.testing.generators.RuntimeGenerators.words;
import static org.junit.jupiter.api.Assertions.assertEquals;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The confinement of a job as {@code pm-exec} takes it (PM-SEC-5): every option is followed by its absolute, normalized
 * path, the order is stable and the empty confinement passes nothing.
 * <p>
 * Contract literals that stay: the launcher options {@code --write}, {@code --read} and {@code --deny-read}, and the
 * {@code ..} segment the normalization removes. The paths are generated; their prefixes {@code a-} and {@code b-} fix
 * the sorted order the test expects, and the input lists them unsorted on purpose.
 */
@EnableGeneratorController
@DisplayName("Job confinement")
class JobConfinementTest {

    @Test
    @DisplayName("the launcher arguments are the options with their sorted, normalized paths")
    void launcherArguments() {
        var first = "/a-" + words().next();
        var second = "/b-" + words().next() + "/" + words().next();
        var read = "/" + words().next() + "/" + words().next();
        var denied = "/" + words().next();
        var confinement = new JobConfinement(
                List.of(Path.of(second + "/../" + Path.of(second).getFileName()), Path.of(first), Path.of(first)),
                List.of(Path.of(read)),
                List.of(Path.of(denied)));

        assertEquals(List.of(
                "--write", first,
                "--write", second,
                "--read", read,
                "--deny-read", denied), confinement.launcherArguments(),
                "the options carry sorted, normalized, distinct paths");
    }

    @Test
    @DisplayName("the empty confinement passes no option")
    void none() {
        assertEquals(List.of(), JobConfinement.none().launcherArguments(), "the empty confinement passes nothing");
    }
}
