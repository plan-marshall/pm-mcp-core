/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.role.EffortValue;
import de.planmarshall.core.role.RoleArtifact;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;

/**
 * Proves that the builder of {@link WorkerTaskActivity.TaskActivity} builds the value its canonical constructor builds.
 * The task facts check no component for null, so they have no refusal to mirror. Every value is generated; the role is
 * a real artifact and the raised signals are values of their closed vocabulary.
 */
@EnableGeneratorController
@DisplayName("WorkerTaskActivity.TaskActivity builder")
class WorkerTaskActivityRecordsTest {

    @Test
    @DisplayName("the task activity builder builds the task activity the constructor builds")
    void taskActivityBuilderEqualsConstructor() {
        var attempt = Generators.integers(0, 10).next();
        var role = roleArtifact();
        var alreadyRaised = Set.of(Generators.enumValues(RoleArtifact.OffTrackSignal.class).next());
        var predictedCostTokens = (double) Generators.integers(0, 1_000_000).next();
        var unplannedChanges = Generators.integers(0, 100).next();

        var constructed = new WorkerTaskActivity.TaskActivity(attempt, role, alreadyRaised, predictedCostTokens,
                unplannedChanges);
        var built = WorkerTaskActivity.TaskActivity.builder()
                .attempt(attempt)
                .role(role)
                .alreadyRaised(alreadyRaised)
                .predictedCostTokens(predictedCostTokens)
                .unplannedChanges(unplannedChanges)
                .build();

        assertEquals(constructed, built, "the builder builds the same task activity");
    }

    private static RoleArtifact roleArtifact() {
        var name = Generators.letterStrings(4, 16).next();
        return new RoleArtifact(1, name, Generators.letterStrings(4, 32).next(), RoleClass.WRITER,
                List.of("read_file"), "claude", "frontier", new EffortValue("high"), RoleForm.FRESH, 1,
                List.of("skill://pm/role/" + name + "/SKILL.md"), null, 60, null,
                new RoleArtifact.Questions(0, 0), Map.of());
    }
}
