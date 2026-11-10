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
 * The builder of {@link LeaseRecord} builds the value the canonical constructor builds, and an absent key is refused
 * with the same refusal.
 */
@DisplayName("Lease record builder")
class LeaseRecordBuilderContractTest {

    private static final String KEY = "plan-marshall/pm-mcp-core";
    private static final Instant ACQUIRED_AT = Instant.parse("2026-10-09T08:16:00Z");

    @Test
    @DisplayName("the builder builds the constructor's value and refuses an absent key")
    void shouldBuildLikeTheConstructorAndRefuseAnAbsentKey() {
        var owner = new LeaseOwner("pm-mcp-core", ScopeType.PLAN, "lock-manager",
                new HolderInstance(4711L, Instant.parse("2026-10-09T08:15:30Z")), null);
        var canonical = new LeaseRecord(KEY, owner, ACQUIRED_AT, ACQUIRED_AT.plusSeconds(3_600));
        var built = LeaseRecord.builder().key(canonical.key()).owner(canonical.owner())
                .acquiredAt(canonical.acquiredAt()).leaseExpiresAt(canonical.leaseExpiresAt()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new LeaseRecord(null, owner, ACQUIRED_AT, ACQUIRED_AT.plusSeconds(3_600)),
                            "the constructor refuses a null key");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> LeaseRecord.builder().owner(owner).acquiredAt(ACQUIRED_AT)
                                    .leaseExpiresAt(ACQUIRED_AT.plusSeconds(3_600)).build(),
                            "the builder refuses a missing key");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent key with the same message");
                });
    }
}
