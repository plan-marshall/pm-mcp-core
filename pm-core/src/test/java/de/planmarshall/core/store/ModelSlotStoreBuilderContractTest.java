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

import de.planmarshall.core.store.ModelSlotStore.Form;
import de.planmarshall.core.store.ModelSlotStore.Lease;
import de.planmarshall.core.store.ModelSlotStore.Waiting;
import de.planmarshall.core.store.ModelSlotStore.WorkerClass;

@DisplayName("Model-slot store builder contract")
class ModelSlotStoreBuilderContractTest {

    private static final Instant AT = Instant.parse("2026-10-22T10:00:00Z");

    private static LeaseOwner owner() {
        return new LeaseOwner("pm-mcp-core", ScopeType.PLAN, "plan-core", new HolderInstance(1, AT), null);
    }

    @Test
    @DisplayName("the builder builds the constructor's lease")
    void builderBuildsLease() {
        var canonical = new Lease(owner(), "l-0000000000000001", "writer", WorkerClass.WRITER, Form.WARM,
                "plan-core", AT, null);

        var built = Lease.builder().owner(canonical.owner()).jobId(canonical.jobId()).role(canonical.role())
                .workerClass(canonical.workerClass()).form(canonical.form()).planId(canonical.planId())
                .acquiredAt(canonical.acquiredAt()).leaseExpiresAt(canonical.leaseExpiresAt()).build();

        var fromConstructor = assertThrows(NullPointerException.class,
                () -> new Lease(null, canonical.jobId(), canonical.role(), canonical.workerClass(),
                        canonical.form(), canonical.planId(), canonical.acquiredAt(),
                        canonical.leaseExpiresAt()),
                "the constructor refuses an absent owner");
        var incomplete = Lease.builder().jobId(canonical.jobId()).role(canonical.role())
                .workerClass(canonical.workerClass()).form(canonical.form()).planId(canonical.planId())
                .acquiredAt(canonical.acquiredAt()).leaseExpiresAt(canonical.leaseExpiresAt());
        var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                "the builder refuses an absent owner");

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                        "both refuse an absent owner with the same message"));
    }

    @Test
    @DisplayName("the builder builds the constructor's waiting worker")
    void builderBuildsWaiting() {
        var canonical = new Waiting(owner(), "l-0000000000000002", "reader", WorkerClass.READ_ONLY,
                Form.FRESH, null, AT);

        var built = Waiting.builder().owner(canonical.owner()).jobId(canonical.jobId()).role(canonical.role())
                .workerClass(canonical.workerClass()).form(canonical.form()).planId(canonical.planId())
                .enqueuedAt(canonical.enqueuedAt()).build();

        var fromConstructor = assertThrows(NullPointerException.class,
                () -> new Waiting(null, canonical.jobId(), canonical.role(), canonical.workerClass(),
                        canonical.form(), canonical.planId(), canonical.enqueuedAt()),
                "the constructor refuses an absent owner");
        var incomplete = Waiting.builder().jobId(canonical.jobId()).role(canonical.role())
                .workerClass(canonical.workerClass()).form(canonical.form()).planId(canonical.planId())
                .enqueuedAt(canonical.enqueuedAt());
        var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                "the builder refuses an absent owner");

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                        "both refuse an absent owner with the same message"));
    }
}
