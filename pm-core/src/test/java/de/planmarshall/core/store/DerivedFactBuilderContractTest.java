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

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.role.corpus.JsonValue;

/**
 * The builder of {@link DerivedFact} builds the value the canonical constructor builds, and an absent kind is refused
 * with the same refusal.
 */
@DisplayName("Derived fact builder")
class DerivedFactBuilderContractTest {

    private static final Instant DERIVED_AT = Instant.parse("2026-10-17T10:00:00Z");

    @Test
    @DisplayName("the builder builds the constructor's value and refuses an absent kind")
    void shouldBuildLikeTheConstructorAndRefuseAnAbsentKind() {
        var value = JsonValue.parse("{\"modules\":[\"pm-core\"]}".getBytes(UTF_8));
        var source = new DerivedFact.Source("fixture", DerivedFact.DerivationClass.LOCAL);
        var canonical = new DerivedFact(DerivedFactKind.MODULE_DISCOVERY, value, source, "a".repeat(64), DERIVED_AT,
                DerivedFact.State.CURRENT, null);
        var built = DerivedFact.builder().kind(canonical.kind()).value(canonical.value()).source(canonical.source())
                .inputDigest(canonical.inputDigest()).derivedAt(canonical.derivedAt()).state(canonical.state())
                .failure(canonical.failure()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new DerivedFact(null, value, source, "a".repeat(64), DERIVED_AT,
                                    DerivedFact.State.CURRENT, null),
                            "the constructor refuses a null kind");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> DerivedFact.builder().value(value).source(source).inputDigest("a".repeat(64))
                                    .derivedAt(DERIVED_AT).state(DerivedFact.State.CURRENT).build(),
                            "the builder refuses a missing kind");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent kind with the same message");
                });
    }
}
