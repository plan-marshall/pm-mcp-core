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

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The confinement of a job as {@code pm-exec} takes it (PM-SEC-5): every option is followed by its absolute, normalized
 * path, the order is stable and the empty confinement passes nothing.
 */
@DisplayName("Job confinement")
class JobConfinementTest {

    @Test
    @DisplayName("the launcher arguments are the options with their sorted, normalized paths")
    void launcherArguments() {
        var confinement = new JobConfinement(
                List.of(Path.of("/work/project/../project"), Path.of("/tmp"), Path.of("/tmp")),
                List.of(Path.of("/base/run/jobs/b-0000000000000000")),
                List.of(Path.of("/base")));

        assertEquals(List.of(
                "--write", "/tmp",
                "--write", "/work/project",
                "--read", "/base/run/jobs/b-0000000000000000",
                "--deny-read", "/base"), confinement.launcherArguments());
    }

    @Test
    @DisplayName("the empty confinement passes no option")
    void none() {
        assertEquals(List.of(), JobConfinement.none().launcherArguments());
    }
}
