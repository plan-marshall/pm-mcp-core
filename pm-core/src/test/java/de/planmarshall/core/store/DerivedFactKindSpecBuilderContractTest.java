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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.role.corpus.JsonValue;

/**
 * The builder of {@link DerivedFactKindSpec} builds the value the canonical constructor builds, and an absent schema is
 * refused with the same refusal.
 */
@DisplayName("Derived fact kind spec builder")
class DerivedFactKindSpecBuilderContractTest {

    private static final String INPUTS = "the descriptor tree hash of the worktree";

    @Test
    @DisplayName("the builder builds the constructor's value and refuses an absent schema")
    void shouldBuildLikeTheConstructorAndRefuseAnAbsentSchema() {
        var schema = JsonValue.parse("{\"type\":\"object\"}".getBytes(UTF_8));
        var classes = Set.of(DerivedFact.DerivationClass.LOCAL, DerivedFact.DerivationClass.NETWORK);
        var canonical = new DerivedFactKindSpec(schema, classes, false, INPUTS);
        var built = DerivedFactKindSpec.builder().schema(canonical.schema()).classes(canonical.classes())
                .qualifierAllowed(canonical.qualifierAllowed()).inputs(canonical.inputs()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new DerivedFactKindSpec(null, classes, false, INPUTS),
                            "the constructor refuses a null schema");
                    var incomplete = DerivedFactKindSpec.builder().classes(classes).qualifierAllowed(false)
                            .inputs(INPUTS);
                    var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                            "the builder refuses a missing schema");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent schema with the same message");
                });
    }
}
