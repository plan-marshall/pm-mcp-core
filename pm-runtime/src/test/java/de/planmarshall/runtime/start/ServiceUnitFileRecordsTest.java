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
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves that the builder of {@link ServiceUnitFile} builds the value its canonical constructor builds, including its
 * null refusal for the platform. The home, the configured directory and the daemon path are generated absolute paths.
 */
@EnableGeneratorController
@DisplayName("ServiceUnitFile builder")
class ServiceUnitFileRecordsTest {

    @Test
    @DisplayName("the builder builds the service unit the constructor builds")
    void serviceUnitFileBuilderEqualsConstructor() {
        var platform = ServiceUnitFile.Platform.SYSTEMD;
        var home = Path.of("/home/" + Generators.letterStrings(3, 12).next());
        var xdgConfigHome = "/home/" + Generators.letterStrings(3, 12).next() + "/.config";
        var daemon = Path.of("/opt/" + Generators.letterStrings(3, 12).next() + "/bin/pm-mcpd");

        var constructed = new ServiceUnitFile(platform, home, xdgConfigHome, daemon);
        var built = ServiceUnitFile.builder()
                .platform(platform)
                .home(home)
                .xdgConfigHome(xdgConfigHome)
                .daemon(daemon)
                .build();

        assertEquals(constructed, built, "the builder builds the same service unit");
    }

    @Test
    @DisplayName("the builder refuses a missing platform like the constructor")
    void serviceUnitFileBuilderRefusesMissingPlatform() {
        var builder = ServiceUnitFile.builder()
                .home(Path.of("/home/builder"))
                .daemon(Path.of("/opt/pm/bin/pm-mcpd"));
        var refusal = assertThrows(NullPointerException.class, builder::build);

        assertEquals("platform", refusal.getMessage(), "the refusal names the platform");
    }
}
