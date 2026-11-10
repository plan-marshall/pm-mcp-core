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

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builders of {@link CapabilitySpec.Requirement} and of {@link CapabilitySpec.Scenario} build the values the
 * canonical constructors build, and an absent name is refused with the same refusal.
 */
@DisplayName("Capability spec builders")
class CapabilitySpecBuilderContractTest {

    private static final String EARS_TEXT =
            "WHEN a plan is refined THE system SHALL write the intent record beside request.md";

    @Test
    @DisplayName("the scenario builder builds the constructor's value and refuses an absent name")
    void shouldBuildScenarioLikeTheConstructorAndRefuseAnAbsentName() {
        var canonical = new CapabilitySpec.Scenario("a-spec-writes", "the store is clean", "a spec is written",
                "the file is replaced");
        var built = CapabilitySpec.Scenario.builder().name(canonical.name()).given(canonical.given())
                .when(canonical.when()).then(canonical.then()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new CapabilitySpec.Scenario(null, "the store is clean", "a spec is written",
                                    "the file is replaced"),
                            "the constructor refuses a null name");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> CapabilitySpec.Scenario.builder().given("the store is clean")
                                    .when("a spec is written").then("the file is replaced").build(),
                            "the builder refuses a missing name");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent name with the same message");
                });
    }

    @Test
    @DisplayName("the requirement builder builds the constructor's value and refuses an absent name")
    void shouldBuildRequirementLikeTheConstructorAndRefuseAnAbsentName() {
        var scenario = new CapabilitySpec.Scenario("a-spec-writes", null, "a spec is written",
                "the file is replaced");
        var canonical = new CapabilitySpec.Requirement("records-are-valid", EARS_TEXT, List.of(scenario), "plan-one",
                List.of("CapabilitySpecTest.java"));
        var built = CapabilitySpec.Requirement.builder().name(canonical.name()).text(canonical.text())
                .scenarios(canonical.scenarios()).planId(canonical.planId()).verifiedBy(canonical.verifiedBy())
                .build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new CapabilitySpec.Requirement(null, EARS_TEXT, List.of(scenario), "plan-one",
                                    List.of()),
                            "the constructor refuses a null name");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> CapabilitySpec.Requirement.builder().text(EARS_TEXT).scenarios(List.of(scenario))
                                    .planId("plan-one").verifiedBy(List.of()).build(),
                            "the builder refuses a missing name");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent name with the same message");
                });
    }
}
