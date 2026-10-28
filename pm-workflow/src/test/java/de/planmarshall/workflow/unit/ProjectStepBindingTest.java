/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.unit;

import static de.planmarshall.workflow.unit.UnitFixtures.COMPLETES_PROJECT_STEP;
import static de.planmarshall.workflow.unit.UnitFixtures.PROJECT_STEP;
import static de.planmarshall.workflow.unit.UnitFixtures.configPath;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.planmarshall.workflow.finalize.FinalizeStepDescriptor;

/**
 * The binding of the project steps (PM-IMPL-5): the project steps the plan unit completes, the declared project step
 * descriptors and the ids of the steps bound to an enrolled project script must agree. Each disagreement is the
 * finding {@code project_step_unbound} with the file and the line of the node that disagrees; the agreeing triple
 * passes. Reading the declarations and the bindings is left to later packages, so the test passes both in.
 */
@EnableTestLogger
@DisplayName("Project step binding")
class ProjectStepBindingTest {

    private static final String FINALIZE_FILE = UnitFixtures.FINALIZE_FILE;

    /** The order of the project step: a free order before the merge gate. */
    private static final int ORDER = 30;

    @TempDir
    Path temp;

    private Path project;

    @BeforeEach
    void project() throws IOException {
        project = Files.createDirectories(temp.toRealPath().resolve("project"));
    }

    private static List<FinalizeStepDescriptor> declared() {
        return List.of(UnitFixtures.projectStep(PROJECT_STEP, ORDER));
    }

    static Stream<Arguments> disagreements() {
        return Stream.of(
                Arguments.of("in the unit but undeclared", Optional.of(COMPLETES_PROJECT_STEP), List.of(), Set.of(),
                        configPath(FINALIZE_FILE),
                        COMPLETES_PROJECT_STEP.lineOf("completes " + PROJECT_STEP)),
                Arguments.of("declared but unbound", Optional.of(COMPLETES_PROJECT_STEP), declared(), Set.of(),
                        configPath(FINALIZE_FILE), COMPLETES_PROJECT_STEP.lineOf("state finalize.create-pr")),
                Arguments.of("declared but absent from the unit", Optional.empty(), declared(), Set.of(PROJECT_STEP),
                        configPath("init.pmwf"), UnitFixtures.lineOf("init.pmwf", UnitFixtures.PLAN_HEADER)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("disagreements")
    @DisplayName("reports project_step_unbound with the file and the line of the disagreeing node")
    void disagreement(String name, Optional<UnitFixtures.Mutation> mutation, List<FinalizeStepDescriptor> steps,
            Set<String> bound, String file, int line) {
        mutation.ifPresentOrElse(change -> UnitFixtures.writeProject(project, change),
                () -> UnitFixtures.writeProject(project));

        var result = UnitFixtures.load("binding", project, steps, bound);

        var unbound = result.findings().stream()
                .filter(finding -> finding.kind() == UnitFinding.Kind.PROJECT_STEP_UNBOUND).toList();
        assertAll(name,
                () -> assertEquals(1, unbound.size(), result.findings()::toString),
                () -> assertEquals(UnitFinding.PROJECT_STEP_UNBOUND, unbound.getFirst().code()),
                () -> assertEquals(file, unbound.getFirst().file()),
                () -> assertEquals(line, unbound.getFirst().line()),
                () -> assertTrue(unbound.getFirst().message().contains(PROJECT_STEP), unbound.getFirst()::message),
                () -> assertEquals(UnitLoadResult.CONFIG_INVALID, result.refusal().orElseThrow().code()));
    }

    @Test
    @DisplayName("passes the unit, the declaration and the binding that agree")
    void agreeingTriple() {
        UnitFixtures.writeProject(project, COMPLETES_PROJECT_STEP);

        var result = UnitFixtures.load("binding", project, declared(), Set.of(PROJECT_STEP));

        assertAll(
                () -> assertTrue(result.isVerified(), result.findings()::toString),
                () -> assertTrue(result.units().containsKey(PlanUnitMerger.PLAN_UNIT)));
    }

    @Test
    @DisplayName("a step the unit completes twice is reported once")
    void reportedOnce() {
        var twice = new UnitFixtures.Mutation("the archive state completes the project step as well", FINALIZE_FILE,
                "        completed -> completed\n            completes default:archive-plan as done",
                "        completed -> completed\n            completes default:archive-plan as done\n"
                        + "            completes " + PROJECT_STEP + " as done");
        UnitFixtures.writeProject(project, COMPLETES_PROJECT_STEP, twice);

        var result = UnitFixtures.load("binding", project, List.of(), Set.of());

        assertEquals(1, result.findings().stream()
                        .filter(finding -> finding.kind() == UnitFinding.Kind.PROJECT_STEP_UNBOUND).count(),
                result.findings()::toString);
    }
}
