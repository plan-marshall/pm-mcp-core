/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builder of {@link AuditEvent} builds the value the canonical constructor builds, and a missing required
 * component is refused with the same refusal.
 */
@EnableGeneratorController
@DisplayName("Audit event builder")
class AuditEventBuilderContractTest {

    @Test
    @DisplayName("the builder builds the event the constructor builds and refuses an absent event alike")
    void shouldBuildLikeTheConstructorAndRefuseAlike() {
        var event = Generators.nonEmptyStrings().next();
        var category = Generators.nonEmptyStrings().next();
        var outcome = Generators.nonEmptyStrings().next();
        var detail = Generators.nonEmptyStrings().next();

        var canonical = new AuditEvent(event, category, outcome, Map.of(AuditEvent.DETAIL_STORE, detail));
        var built = AuditEvent.builder().event(event).category(category).outcome(outcome)
                .details(canonical.details()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new AuditEvent(null, category, outcome, Map.of()),
                            "the constructor refuses a null event");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> AuditEvent.builder().category(category).outcome(outcome).details(Map.of()).build(),
                            "the builder refuses a missing event");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent event with the same message");
                });
    }
}
