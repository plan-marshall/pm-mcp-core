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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Source location, scopes and unit types of the tree")
class AstVocabularyTest {

    @Test
    @DisplayName("a source location names resource, line and column")
    void sourceLocation() {
        var location = new SourceLocation("fixture.unit", 12, 3);

        assertEquals("fixture.unit", location.resourceName());
        assertEquals(12, location.line());
        assertEquals(3, location.column());
        assertEquals(new SourceLocation("fixture.unit", 12, 3), location);
        assertNotEquals(new SourceLocation("fixture.unit", 12, 4), location);
    }

    @Test
    @DisplayName("the scopes are the six of the language")
    void scopes() {
        var names = Arrays.stream(WorkflowScope.values()).map(Enum::name).toList();

        assertEquals(List.of("PLAN", "EPIC", "WORKSPACE", "MACHINE", "ENTITY", "SUBWORKFLOW"), names);
    }

    @Test
    @DisplayName("the unit types are the six of the language, each with its lower-case wire name")
    void unitTypes() {
        var wireNames = Arrays.stream(UnitType.values()).map(UnitType::wireName).toList();

        assertEquals(List.of("workflow", "recipe", "subworkflow", "system", "task", "instance"), wireNames);
        assertEquals(Optional.of(UnitType.TASK), UnitType.fromWireName("task"));
        assertEquals(Optional.empty(), UnitType.fromWireName("TASK"));
    }
}
