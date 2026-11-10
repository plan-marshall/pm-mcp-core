/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.queue;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builder of {@link ActivityEventMapping.KindMapping} builds the value the canonical constructor builds, and a
 * missing match is refused with the same refusal.
 */
@DisplayName("Activity event mapping kind builder")
class ActivityEventMappingBuilderContractTest {

    @Test
    @DisplayName("the kind mapping builder builds the constructor's value and refuses alike")
    void shouldBuildKindMappingLikeTheConstructorAndRefuseAlike() {
        var canonical = new ActivityEventMapping.KindMapping(List.of("text"), "/text", null, List.of(), null, null,
                null, null);
        var built = ActivityEventMapping.KindMapping.builder().match(canonical.match())
                .textPointer(canonical.textPointer()).toolPointer(canonical.toolPointer())
                .targetPointers(canonical.targetPointers()).sizePointer(canonical.sizePointer())
                .isErrorPointer(canonical.isErrorPointer()).pathPointer(canonical.pathPointer())
                .stopReasonPointer(canonical.stopReasonPointer()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new ActivityEventMapping.KindMapping(null, "/text", null, List.of(), null, null,
                                    null, null),
                            "the constructor refuses a null match");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> ActivityEventMapping.KindMapping.builder().textPointer("/text")
                                    .targetPointers(List.of()).build(),
                            "the builder refuses a missing match");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent match with the same message");
                });
    }
}
