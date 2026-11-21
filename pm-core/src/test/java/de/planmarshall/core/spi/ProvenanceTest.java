/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.spi;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.spi.Provenance.Trigger;

/**
 * The provenance names the cause of an advance and, where the cause has one, its reference.
 */
@EnableGeneratorController
@DisplayName("Provenance of a command")
class ProvenanceTest {

    @Test
    @DisplayName("the nine trigger wire names of the contract, in its order (contract literal)")
    void triggerWireNames() {
        var wireNames = Arrays.stream(Trigger.values()).map(Trigger::wireName).toList();

        assertEquals(List.of("start", "resume", "job_settled", "tasks_settled", "question_resolved", "operator_act",
                "time_elapsed", "step_out", "fallback"), wireNames, "the nine trigger wire names in contract order");
    }

    @Test
    @DisplayName("a provenance carries its trigger and reference")
    void carriesTriggerAndReference() {
        var trigger = Generators.enumValues(Trigger.class).next();
        var reference = Generators.nonBlankStrings().next();

        var provenance = new Provenance(trigger, reference);

        assertAll(
                () -> assertEquals(trigger, provenance.trigger(), "the provenance keeps its trigger"),
                () -> assertEquals(reference, provenance.triggerRef(), "the provenance keeps its reference"));
    }

    @Test
    @DisplayName("of names a trigger without a reference")
    void ofHasNoReference() {
        var trigger = Generators.enumValues(Trigger.class).next();

        var provenance = Provenance.of(trigger);

        assertAll(
                () -> assertEquals(trigger, provenance.trigger(), "the provenance keeps its trigger"),
                () -> assertNull(provenance.triggerRef(), "the provenance carries no reference"));
    }

    @Test
    @DisplayName("a null trigger is refused")
    void refusesNullTrigger() {
        var reference = Generators.nonBlankStrings().next();

        assertThrows(NullPointerException.class, () -> new Provenance(null, reference),
                "a provenance without a trigger is refused");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    @DisplayName("a blank reference is refused")
    void refusesBlankReference(String blank) {
        var trigger = Generators.enumValues(Trigger.class).next();

        assertThrows(IllegalArgumentException.class, () -> new Provenance(trigger, blank),
                "a blank trigger reference is refused");
    }
}
