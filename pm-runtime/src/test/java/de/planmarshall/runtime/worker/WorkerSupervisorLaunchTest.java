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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.store.JobRecord;
import de.planmarshall.runtime.job.WorkerStart;
import de.planmarshall.runtime.launch.HeadlessBlock;
import de.planmarshall.runtime.worker.WorkerSupervisor.StartOutcome;

/**
 * The composed launch of one start (PM-WORK-3, Worker Launch): the landed {@link
 * de.planmarshall.runtime.launch.LaunchComposer} composes it from the role and the profile's block, the refusal path
 * records a failed composition, and the {@link WorkerStart} carries the project, the role's floor budget and the
 * harness's writable paths.
 */
@DisplayName("Worker supervisor launch")
class WorkerSupervisorLaunchTest {

    private static final SupervisorEnvironment.StartGuard OPEN_GUARD = SupervisorEnvironment.StartGuard.open();

    private static final SupervisorEnvironment.EligibilityGate OPEN_GATE =
            SupervisorEnvironment.EligibilityGate.open();

    @TempDir
    Path base;

    private SupervisorTestFixture fixture;

    @BeforeEach
    void setUp() throws IOException {
        fixture = new SupervisorTestFixture(base);
    }

    @Test
    @DisplayName("the landed composer composes the launch from the role and the harness block")
    void composesThroughTheLandedComposer() {
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 1);

        var outcome = start(role).getFirst();
        var generation = assertInstanceOf(StartOutcome.Started.class, outcome).generation();
        var launch = fixture.starter.starts.getFirst().launch();

        assertAll(
                () -> assertEquals(generation.generationId(), launch.generationId()),
                () -> assertEquals(base.resolve("bin").resolve("claude").toString(), launch.argv().getFirst(),
                        "the enrolled executable starts the vector"),
                () -> assertEquals(fixture.workdir, launch.workingDirectory()),
                () -> assertEquals("claude-sonnet-4", launch.model()),
                () -> assertEquals("implementer", launch.role()),
                () -> assertTrue(launch.prompt().text().contains("## Contract envelope"),
                        "a fresh worker's prompt carries its one task's envelope"),
                () -> assertTrue(launch.deliveredSkills().stream()
                        .anyMatch(skill -> SupervisorTestFixture.ROLE_SKILL.equals(skill.uri())),
                        "the role skill is delivered in-band"));
    }

    @Test
    @DisplayName("a failed composition is recorded as prompt_composition_failed through the refusal path")
    void compositionFailureIsRecorded() {
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 1);
        var brokenSkills = (de.planmarshall.runtime.launch.SkillSource) uri -> Optional.empty();
        var supervisor = new WorkerSupervisor(fixture.environment(OPEN_GUARD, OPEN_GATE, brokenSkills));

        var outcomes = supervisor.start(List.of(fixture.freshRequest(role)));

        assertAll(
                () -> assertEquals(WorkerStartRefusal.LAUNCH_REFUSED,
                        assertInstanceOf(StartOutcome.Declined.class, outcomes.getFirst()).reason()),
                () -> assertEquals(1, fixture.starter.refusals.size()),
                () -> assertEquals("prompt_composition_failed", fixture.starter.refusals.getFirst().reason().code()),
                () -> assertTrue(fixture.starter.starts.isEmpty()),
                () -> assertEquals(1, fixture.slots.released.size()));
    }

    @Test
    @DisplayName("the worker start carries the project, the role floor budget and the writable paths")
    void workerStartCarriesTheContracts() {
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 1);

        start(role);
        var start = fixture.starter.starts.getFirst();
        var block = HeadlessBlock.of(fixture.profile);

        assertAll(
                () -> assertEquals(SupervisorTestFixture.PROJECT, start.enrolment().projectId()),
                () -> assertEquals(JobRecord.Scope.Type.PROJECT, start.scope().type()),
                () -> assertEquals("worker:implementer", start.budgetKey()),
                () -> assertEquals(60, start.budgetSeconds()),
                () -> assertEquals(JobRecord.BudgetDerivation.Kind.FLOOR_DEFAULT,
                        start.budgetDerivation().kind()),
                () -> assertEquals(block.writablePaths(base.resolve("home")), start.writablePaths()),
                () -> assertTrue(start.egressProxy().isEmpty()));
    }

    @Test
    @DisplayName("the generation id is the job id")
    void generationIdIsTheJobId() {
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 1);

        var generation = assertInstanceOf(StartOutcome.Started.class, start(role).getFirst()).generation();

        assertAll(
                () -> assertEquals(generation.jobId().value(), generation.generationId()),
                () -> assertEquals(generation.generationId(),
                        fixture.starter.starts.getFirst().launch().generationId()),
                () -> assertEquals(WorkerLifecycle.STARTING, generation.lifecycle()));
    }

    private List<StartOutcome> start(de.planmarshall.core.role.EffectiveRole role) {
        return new WorkerSupervisor(fixture.environment(OPEN_GUARD, OPEN_GATE))
                .start(List.of(fixture.freshRequest(role)));
    }
}
