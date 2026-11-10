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

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builder of {@link Consent} builds the value the canonical constructor builds, and a missing required component
 * is refused with the same refusal.
 */
@DisplayName("Consent builder")
class ConsentBuilderContractTest {

    @Test
    @DisplayName("the builder builds the constructor's value and refuses alike")
    void shouldBuildLikeTheConstructorAndRefuseAlike() {
        var canonical = new Consent(ConsentKind.SCAN_HARNESSES, null, List.of(), Instant.EPOCH,
                Consent.Channel.CLI, null);

        var built = Consent.builder().kind(canonical.kind()).projectId(canonical.projectId())
                .scripts(canonical.scripts()).grantedAt(canonical.grantedAt()).channel(canonical.channel())
                .deviceId(canonical.deviceId()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new Consent(null, null, List.of(), Instant.EPOCH, Consent.Channel.CLI, null),
                            "the constructor refuses a null kind");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> Consent.builder().scripts(List.of()).grantedAt(Instant.EPOCH)
                                    .channel(Consent.Channel.CLI).build(),
                            "the builder refuses a missing kind");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent kind with the same message");
                });
    }
}
