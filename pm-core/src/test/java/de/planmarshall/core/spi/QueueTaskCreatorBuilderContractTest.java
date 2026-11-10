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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builder of {@link QueueTaskCreator.CreateRequest} builds the value the canonical constructor builds, and a missing
 * state id is refused with the same refusal.
 */
@DisplayName("Queue task creator builder")
class QueueTaskCreatorBuilderContractTest {

    @Test
    @DisplayName("the create request builder builds the constructor's value and refuses an absent state id alike")
    void shouldBuildCreateRequestLikeTheConstructorAndRefuseAnAbsentStateIdAlike() {
        var canonical = new QueueTaskCreator.CreateRequest("plan-1", "state-1", "entity-1", "review-code");
        var built = QueueTaskCreator.CreateRequest.builder().scopeId(canonical.scopeId())
                .stateId(canonical.stateId()).entityKey(canonical.entityKey()).role(canonical.role()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new QueueTaskCreator.CreateRequest("plan-1", null, "entity-1", "review-code"),
                            "the constructor refuses a null state id");
                    var incomplete = QueueTaskCreator.CreateRequest.builder().scopeId("plan-1")
                            .entityKey("entity-1").role("review-code");
                    var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                            "the builder refuses a missing state id");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent state id with the same message");
                });
    }
}
