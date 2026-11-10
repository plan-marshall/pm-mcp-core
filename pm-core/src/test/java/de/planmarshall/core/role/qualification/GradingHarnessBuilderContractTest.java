/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.qualification;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.role.RoleForm;

/**
 * The builder of {@link GradingHarness.Offered} builds the value the canonical constructor builds, and a missing item is
 * refused with the same refusal.
 */
@DisplayName("Grading harness builder")
class GradingHarnessBuilderContractTest {

    @Test
    @DisplayName("the offered builder builds the constructor's value and refuses an absent item alike")
    void shouldBuildOfferedLikeTheConstructorAndRefuseAnAbsentItemAlike() {
        var canonical = new GradingHarness.Offered(
                QualificationFixtures.item("review/sample/q-1", "review-code", "first").build(),
                RoleForm.FRESH, Checkout.CURRENT, 1);
        var built = GradingHarness.Offered.builder().item(canonical.item()).form(canonical.form())
                .checkout(canonical.checkout()).run(canonical.run()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new GradingHarness.Offered(null, RoleForm.FRESH, Checkout.CURRENT, 1),
                            "the constructor refuses a null item");
                    var incomplete = GradingHarness.Offered.builder().form(RoleForm.FRESH)
                            .checkout(Checkout.CURRENT).run(1);
                    var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                            "the builder refuses a missing item");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent item with the same message");
                });
    }
}
