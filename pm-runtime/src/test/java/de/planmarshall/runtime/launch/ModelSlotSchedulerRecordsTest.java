/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.launch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.HolderInstance;
import de.planmarshall.core.store.LeaseOwner;
import de.planmarshall.core.store.ModelSlotStore.Form;
import de.planmarshall.core.store.ModelSlotStore.WorkerClass;
import de.planmarshall.core.store.ScopeType;
import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * Proves that the builder of {@link ModelSlotScheduler.Request} builds the value its canonical constructor builds,
 * including its null refusal for the lease owner. Every value is generated; the read-only worker serves the workspace
 * scope and names no plan.
 */
@EnableGeneratorController
@DisplayName("ModelSlotScheduler.Request builder")
class ModelSlotSchedulerRecordsTest {

    @Test
    @DisplayName("the builder builds the slot request the constructor builds")
    void requestBuilderEqualsConstructor() {
        var startedAt = Instant.ofEpochMilli(Generators.longs(0, 1_900_000_000_000L).next());
        var owner = new LeaseOwner(RuntimeGenerators.projectIds().next(), ScopeType.WORKSPACE, null,
                new HolderInstance(Generators.longs(1, 100_000).next(), startedAt), null);
        var jobId = Generators.letterStrings(4, 20).next();
        var role = Generators.letterStrings(1, 20).next();
        var workerClass = WorkerClass.READ_ONLY;
        var form = Form.FRESH;

        var constructed = new ModelSlotScheduler.Request(owner, jobId, role, workerClass, form, null);
        var built = ModelSlotScheduler.Request.builder()
                .owner(owner)
                .jobId(jobId)
                .role(role)
                .workerClass(workerClass)
                .form(form)
                .planId(null)
                .build();

        assertEquals(constructed, built, "the builder builds the same slot request");
    }

    @Test
    @DisplayName("the builder refuses a missing owner like the constructor")
    void requestBuilderRefusesMissingOwner() {
        var refusal = assertThrows(NullPointerException.class, () -> ModelSlotScheduler.Request.builder().build());

        assertEquals("owner", refusal.getMessage(), "the refusal names the lease owner");
    }
}
