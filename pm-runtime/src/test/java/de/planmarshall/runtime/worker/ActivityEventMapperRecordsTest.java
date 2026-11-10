/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.queue.ActivityEntry;

/**
 * Proves that the builder of {@link ActivityEventMapper.Mapped} builds the value its canonical constructor builds. The
 * mapped entry checks no component for null, so it has no refusal to mirror. Every value is generated; the tool, the
 * result and the stop reason are values of their closed vocabularies.
 */
@EnableGeneratorController
@DisplayName("ActivityEventMapper.Mapped builder")
class ActivityEventMapperRecordsTest {

    @Test
    @DisplayName("the mapped builder builds the mapped entry the constructor builds")
    void mappedBuilderEqualsConstructor() {
        var kind = Generators.enumValues(ActivityEntry.Kind.class).next();
        var text = Generators.letterStrings(1, 40).next();
        var truncated = Generators.booleans().next();
        var tool = new ActivityEntry.Tool(Generators.letterStrings(1, 20).next(), null);
        var result = new ActivityEntry.Result(Generators.integers(0, 10_000).next(), Generators.booleans().next());
        var path = Generators.letterStrings(1, 40).next();
        var stopReason = Generators.enumValues(ActivityEntry.StopReason.class).next();
        var usage = "{}";

        var constructed = new ActivityEventMapper.Mapped(kind, text, truncated, tool, result, path, stopReason,
                usage);
        var built = ActivityEventMapper.Mapped.builder()
                .kind(kind)
                .text(text)
                .truncated(truncated)
                .tool(tool)
                .result(result)
                .path(path)
                .stopReason(stopReason)
                .usage(usage)
                .build();

        assertEquals(constructed, built, "the builder builds the same mapped entry");
    }
}
