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

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builder of {@link LeaseOwner} builds the value the canonical constructor builds, and an absent project id is
 * refused with the same refusal.
 */
@DisplayName("Lease owner builder")
class LeaseOwnerBuilderContractTest {

    private static final Instant RUNTIME_STARTED_AT = Instant.parse("2026-10-09T08:15:30Z");

    @Test
    @DisplayName("the builder builds the constructor's value and refuses an absent project id")
    void shouldBuildLikeTheConstructorAndRefuseAnAbsentProjectId() {
        var holder = new HolderInstance(4711L, RUNTIME_STARTED_AT);
        var canonical = new LeaseOwner("pm-mcp-core", ScopeType.PLAN, "lock-manager", holder, null);
        var built = LeaseOwner.builder().projectId(canonical.projectId()).scopeType(canonical.scopeType())
                .scopeId(canonical.scopeId()).holderInstance(canonical.holderInstance())
                .orphanedAt(canonical.orphanedAt()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new LeaseOwner(null, ScopeType.PLAN, "lock-manager", holder, null),
                            "the constructor refuses a null project id");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> LeaseOwner.builder().scopeType(ScopeType.PLAN).scopeId("lock-manager")
                                    .holderInstance(holder).build(),
                            "the builder refuses a missing project id");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent project id with the same message");
                });
    }
}
