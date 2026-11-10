/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.core.store.JobId;
import de.planmarshall.core.store.JobRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builder of {@link JobRunner.StartRequest} builds the value the canonical constructor builds, and a missing
 * required component is refused with the same refusal.
 */
@EnableGeneratorController
@DisplayName("Job start request builder")
class JobRunnerBuilderContractTest {

    @Test
    @DisplayName("the builder builds the constructor's value and refuses alike")
    void shouldBuildLikeTheConstructorAndRefuseAlike() {
        var budgetKey = Generators.letterStrings(1, 10).next();
        var request = Generators.letterStrings(1, 10).next();
        var scope = new JobRecord.Scope(JobRecord.Scope.Type.PLAN, "v2-100");
        var derivation = new JobRecord.BudgetDerivation(JobRecord.BudgetDerivation.Kind.FLOOR_DEFAULT, null);

        var canonical = new JobRunner.StartRequest<String>(JobId.Kind.BUILD, scope, budgetKey, 60L, derivation,
                request, null);
        var built = JobRunner.StartRequest.<String>builder().kind(canonical.kind()).scope(canonical.scope())
                .budgetKey(canonical.budgetKey()).budgetSeconds(canonical.budgetSeconds())
                .budgetDerivation(canonical.budgetDerivation()).request(canonical.request())
                .stdin(canonical.stdin()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new JobRunner.StartRequest<String>(null, scope, budgetKey, 60L, derivation, request,
                                    null),
                            "the constructor refuses a null kind");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> JobRunner.StartRequest.<String>builder().scope(scope).budgetKey(budgetKey)
                                    .budgetSeconds(60L).budgetDerivation(derivation).request(request).build(),
                            "the builder refuses a missing kind");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent kind with the same message");
                });
    }
}
