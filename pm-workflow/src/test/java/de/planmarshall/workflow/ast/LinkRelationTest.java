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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("LinkRelation")
class LinkRelationTest {

    @ParameterizedTest(name = "{0} defaults to {1}, takes no parameters: {2}")
    @CsvSource({
            "NEXT, NOOP, true",
            "ALT, NOOP, true",
            "RETRY, NOOP, true",
            "FIX, NOOP, true",
            "LOOP_BACK, SUBMIT, true",
            "SUBMIT, SUBMIT, false",
            "ESCALATE, SUBMIT, false",
            "CONSULT, SUBMIT, false",
            "ABORT, SUBMIT, false"})
    @DisplayName("carries its default form and its allowed forms")
    void forms(LinkRelation relation, LinkForm defaultForm, boolean noopAllowed) {
        assertEquals(defaultForm, relation.defaultForm());
        assertEquals(noopAllowed ? Set.of(LinkForm.NOOP, LinkForm.SUBMIT) : Set.of(LinkForm.SUBMIT),
                relation.allowedForms());
    }

    @ParameterizedTest
    @EnumSource(LinkRelation.class)
    @DisplayName("allows its own default form")
    void defaultIsAllowed(LinkRelation relation) {
        assertTrue(relation.allowedForms().contains(relation.defaultForm()));
    }

    @Test
    @DisplayName("is closed at nine relations and two forms")
    void closed() {
        assertEquals(9, LinkRelation.values().length);
        assertEquals(List.of("SUBMIT", "NOOP"), Arrays.stream(LinkForm.values()).map(Enum::name).toList());
    }
}
