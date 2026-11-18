/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.config;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The transcription methods a project allows (Store Schemas § Project Values over Machine Values): the project narrows
 * the machine's list and never widens it, and a method the machine does not allow is refused {@code invalid_arguments}.
 */
@DisplayName("Project transcription methods")
class TranscribeMethodsTest {

    @Test
    @DisplayName("Should accept a project's subset of the machine's methods")
    void acceptsASubset() {
        var chosen = TranscribeMethods.chosen(List.of("local"), List.of("local", "provider"));

        assertEquals(List.of("local"), chosen.methods(), "The project keeps its narrowed subset");
    }

    @Test
    @DisplayName("Should refuse a method the machine does not allow")
    void refusesAWidenedMethod() {
        var chosenMethods = List.of("provider");
        var machineMethods = List.of("local");

        var refusal = assertThrows(IllegalArgumentException.class,
                () -> TranscribeMethods.chosen(chosenMethods, machineMethods),
                "A method the machine does not allow is refused");

        assertTrue(refusal.getMessage().contains(TranscribeMethods.INVALID_ARGUMENTS),
                "The refusal carries the invalid_arguments reason");
    }

    @Test
    @DisplayName("Should refuse a project that allows no method")
    void refusesNoMethod() {
        List<String> none = List.of();
        var machineMethods = List.of("local");

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new TranscribeMethods(none),
                        "A project allows at least one method"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> TranscribeMethods.chosen(none, machineMethods),
                        "The chosen subset is not empty"));
    }
}
