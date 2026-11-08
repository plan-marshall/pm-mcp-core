/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.testing.generators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@EnableGeneratorController
@DisplayName("Queue task id generator")
class QueueTaskIdGeneratorTest {

    @Test
    @DisplayName("Should yield the written queue task id")
    void shouldYieldTheWrittenId() {
        var taskId = RuntimeGenerators.queueTaskIds().next();

        assertTrue(taskId.matches("qt-[0-9a-f]{16}"), "A queue task id has the qt- prefix and 16 hex digits");
        assertEquals(String.class, RuntimeGenerators.queueTaskIds().getType(), "The generator names the string type");
    }
}
