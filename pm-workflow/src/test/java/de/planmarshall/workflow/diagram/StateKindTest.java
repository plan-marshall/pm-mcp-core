/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.diagram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.workflow.ast.StateBehavior;

/**
 * The kind of a state (PM-IMPL-5): every variant of the sealed {@link StateBehavior}, a plain and a composite job
 * apart, maps to the label the State Catalogue's <em>Kind</em> column uses, a task state included as {@code job},
 * and every kind is the label of some variant, so the kind vocabulary holds no label without a behaviour.
 */
@DisplayName("StateKind")
class StateKindTest {

    static Stream<Arguments> behaviours() {
        return Stream.of(
                Arguments.of("link state", new StateBehavior.InteractiveLinks(List.of()), "link"),
                Arguments.of("auto state", new StateBehavior.AutoStep("diagram.prepare", List.of()), "auto"),
                Arguments.of("job state", new StateBehavior.JobStep("diagram.build", false, List.of()), "job"),
                Arguments.of("composite job state", new StateBehavior.JobStep("diagram.build", true, List.of()),
                        "composite_job"),
                Arguments.of("task state", new StateBehavior.ModelJob("change", Optional.of("reviewer"),
                        Optional.empty(), Optional.empty(), StateBehavior.AskForm.NONE, List.of(), List.of()), "job"),
                Arguments.of("call state", new StateBehavior.SubWorkflowCall("diagram.callee", Optional.empty(),
                        Map.of(), List.of()), "call"),
                Arguments.of("call state through a named entry", new StateBehavior.SubWorkflowCall("diagram.callee",
                        Optional.of("review"), Map.of(), List.of()), "call"),
                Arguments.of("question state", new StateBehavior.QuestionWait("clarification", List.of()),
                        "awaiting_user"),
                Arguments.of("terminal state", new StateBehavior.TerminalState(), "terminal"));
    }

    @ParameterizedTest(name = "{0} -> {2}")
    @MethodSource("behaviours")
    @DisplayName("Should derive the kind label from the behaviour")
    void shouldDeriveKindFromBehaviour(String name, StateBehavior behavior, String expectedLabel) {
        var kind = StateKind.of(behavior);

        assertEquals(expectedLabel, kind.label(), name);
    }

    @Test
    @DisplayName("Should derive every kind from some behaviour")
    void shouldDeriveEveryKind() {
        var derived = behaviours().map(arguments -> StateKind.of((StateBehavior) arguments.get()[1]))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(StateKind.class)));

        assertEquals(EnumSet.allOf(StateKind.class), derived);
    }

    @Test
    @DisplayName("Should refuse a missing behaviour")
    void shouldRefuseMissingBehaviour() {
        assertThrows(NullPointerException.class, () -> StateKind.of(null));
    }
}
