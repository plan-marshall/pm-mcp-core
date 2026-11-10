/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves that the builder of {@link WebState} builds the value its canonical constructor builds. The flags and the port
 * are generated; the record refuses no component.
 */
@EnableGeneratorController
@DisplayName("WebState builder")
class WebStateRecordsTest {

    @Test
    @DisplayName("the builder builds the web state the constructor builds")
    void webStateBuilderEqualsConstructor() {
        var enabled = Generators.booleans().next();
        var lan = Generators.booleans().next();
        var port = Generators.integers(1, 65_535).next();
        var open = Generators.booleans().next();

        var constructed = new WebState(enabled, lan, port, open);
        var built = WebState.builder()
                .enabled(enabled)
                .lan(lan)
                .port(port)
                .open(open)
                .build();

        assertEquals(constructed, built, "the builder builds the same web state");
    }
}
