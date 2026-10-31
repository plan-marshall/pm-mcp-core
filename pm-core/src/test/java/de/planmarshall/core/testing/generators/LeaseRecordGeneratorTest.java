/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.testing.generators;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.LeaseRecord;
import de.planmarshall.core.store.ScopeType;

@DisplayName("Lease record generator")
@EnableGeneratorController
class LeaseRecordGeneratorTest {

    private final LeaseRecordGenerator generator = new LeaseRecordGenerator();

    @Test
    @DisplayName("Should yield valid and distinct lease records")
    void shouldYieldValidAndDistinctLeaseRecords() {
        var first = generator.next();
        var second = generator.next();

        assertAll("generated lease records",
                () -> assertNotNull(first, "The generator yields a value"),
                () -> assertNotEquals(first, second, "Two calls yield distinct values"),
                () -> assertEquals(ScopeType.PLAN, first.owner().scopeType(), "The lease belongs to a plan scope"),
                () -> assertTrue(first.leaseExpiresAt().isAfter(first.acquiredAt()),
                        "The lease expires after its claim"));
    }

    @Test
    @DisplayName("Should report the generated type")
    void shouldReportTheGeneratedType() {
        assertEquals(LeaseRecord.class, generator.getType(), "The generator names the lease type");
    }
}
