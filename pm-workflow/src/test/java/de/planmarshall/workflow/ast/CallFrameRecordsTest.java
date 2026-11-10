/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves that the builder of {@link CallFrame} builds the value its canonical constructor builds. Every value is
 * generated, and the bound argument is a permitted record of the sealed {@link CallValue} interface.
 */
@EnableGeneratorController
@DisplayName("CallFrame builder")
class CallFrameRecordsTest {

    @Test
    @DisplayName("the builder builds the call frame the constructor builds")
    void callFrameBuilderEqualsConstructor() {
        var callerWorkflowId = Generators.letterStrings(1, 20).next();
        var callerStateId = Generators.letterStrings(1, 20).next();
        var subWorkflowId = Generators.letterStrings(1, 20).next();
        var entry = Generators.letterStrings(1, 20).next();
        var boundArgs = Map.<String, CallValue>of(Generators.letterStrings(1, 20).next(),
                new CallValue.EnumValue(Generators.letterStrings(1, 20).next()));
        var pushedAt = Generators.letterStrings(1, 20).next();

        var constructed = new CallFrame(callerWorkflowId, callerStateId, subWorkflowId, entry, boundArgs, pushedAt);
        var built = CallFrame.builder()
                .callerWorkflowId(callerWorkflowId)
                .callerStateId(callerStateId)
                .subWorkflowId(subWorkflowId)
                .entry(entry)
                .boundArgs(boundArgs)
                .pushedAt(pushedAt)
                .build();

        assertEquals(constructed, built, "the builder builds the same call frame");
    }
}
