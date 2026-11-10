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
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.role.EffectiveRole;
import de.planmarshall.core.role.EffortValue;
import de.planmarshall.core.role.RoleArtifact;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.runtime.launch.LaunchRequest;

/**
 * Proves that the builder of {@link WorkerStartRequest} builds the value its canonical constructor builds, including
 * its null refusal for the role. Every value is generated; the job-private start names no plan, no working directory
 * and no envelope, and the warm role admits a resumed session.
 */
@EnableGeneratorController
@DisplayName("WorkerStartRequest builder")
class WorkerStartRequestRecordsTest {

    @Test
    @DisplayName("the builder builds the worker start request the constructor builds")
    void workerStartRequestBuilderEqualsConstructor() {
        var role = effectiveRole(RoleForm.WARM);
        var workdirKind = LaunchRequest.WorkdirKind.JOB_PRIVATE;
        var writesFreeText = Generators.booleans().next();
        var stateSkills = List.of(Generators.letterStrings(1, 40).next());
        var domainSkills = List.of(Generators.letterStrings(1, 40).next());
        var zeroToolPrompt = "";
        var resumeSessionId = Generators.letterStrings(1, 40).next();

        var constructed = new WorkerStartRequest(role, workdirKind, null, null, null, writesFreeText, stateSkills,
                domainSkills, zeroToolPrompt, resumeSessionId);
        var built = WorkerStartRequest.builder()
                .role(role)
                .workdirKind(workdirKind)
                .planId(null)
                .workdir(null)
                .envelope(null)
                .writesFreeText(writesFreeText)
                .stateSkills(stateSkills)
                .domainSkills(domainSkills)
                .zeroToolPrompt(zeroToolPrompt)
                .resumeSessionId(resumeSessionId)
                .build();

        assertEquals(constructed, built, "the builder builds the same worker start request");
    }

    @Test
    @DisplayName("the builder refuses a missing role like the constructor")
    void workerStartRequestBuilderRefusesMissingRole() {
        var refusal = assertThrows(NullPointerException.class, () -> WorkerStartRequest.builder().build());

        assertEquals("role", refusal.getMessage(), "the refusal names the effective role");
    }

    private static EffectiveRole effectiveRole(RoleForm form) {
        var name = Generators.letterStrings(4, 16).next();
        var artifact = new RoleArtifact(1, name, Generators.letterStrings(4, 32).next(), RoleClass.WRITER,
                List.of("read_file"), "claude", "frontier", new EffortValue("high"), form, 1,
                List.of("skill://pm/role/" + name + "/SKILL.md"), null, 60, null, new RoleArtifact.Questions(0, 0),
                Map.of());
        return new EffectiveRole(artifact, "claude", "frontier", new EffortValue("high"), List.of(), "1.0", null,
                new RoleArtifact.Questions(0, 0), Map.of(), 400_000, OptionalInt.of(3));
    }
}
