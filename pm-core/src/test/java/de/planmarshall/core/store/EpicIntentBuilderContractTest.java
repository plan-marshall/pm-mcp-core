/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builder of {@link EpicIntent} builds the value the canonical constructor builds, and an absent destination is
 * refused with the same refusal.
 */
@EnableGeneratorController
@DisplayName("Epic intent builder")
class EpicIntentBuilderContractTest {

    @Test
    @DisplayName("the builder builds the constructor's value and refuses an absent destination")
    void shouldBuildLikeTheConstructorAndRefuseAnAbsentDestination() {
        var doneWhen = List.of(Generators.letterStrings(10, 30).next(), Generators.letterStrings(10, 30).next(),
                Generators.letterStrings(10, 30).next());
        var nonGoals = List.of(Generators.letterStrings(10, 30).next());
        var canonical = new EpicIntent(Generators.letterStrings(20, 40).next(), doneWhen, nonGoals, List.of());
        var built = EpicIntent.builder().destination(canonical.destination()).doneWhen(canonical.doneWhen())
                .nonGoals(canonical.nonGoals()).notYetSpecified(canonical.notYetSpecified()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    List<String> notYetSpecified = List.of();
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new EpicIntent(null, doneWhen, nonGoals, notYetSpecified),
                            "the constructor refuses a null destination");
                    var incomplete = EpicIntent.builder().doneWhen(doneWhen).nonGoals(nonGoals)
                            .notYetSpecified(List.of());
                    var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                            "the builder refuses a missing destination");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent destination with the same message");
                });
    }
}
