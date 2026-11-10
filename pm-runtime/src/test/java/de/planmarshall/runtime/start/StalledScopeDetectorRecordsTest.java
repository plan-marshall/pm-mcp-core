/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.start;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.AuditRecord;
import de.planmarshall.core.store.JournalScopeType;
import de.planmarshall.core.store.RetryCounters;
import de.planmarshall.core.store.WorkflowStatus;
import de.planmarshall.runtime.start.StalledScopeDetector.Cause;
import de.planmarshall.runtime.start.StalledScopeDetector.StalledHold;
import de.planmarshall.runtime.start.StalledScopeDetector.View;
import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * Proves that the builders of {@link View} and {@link StalledHold} build the values their canonical constructors build,
 * including their null refusals. The ids and the instants are generated; the status is a real plan status.
 */
@EnableGeneratorController
@DisplayName("StalledScopeDetector builders")
class StalledScopeDetectorRecordsTest {

    private static WorkflowStatus status() {
        return WorkflowStatus.builder()
                .scopeType(JournalScopeType.PLAN)
                .scopeId(RuntimeGenerators.planIds().next())
                .phase("phase-5-execute")
                .state("execute.review")
                .callStack(List.of())
                .policyCounters(Map.of())
                .waiverRequests(List.of())
                .retryCounters(RetryCounters.empty())
                .activeJobs(Map.of())
                .activeTasks(Map.of())
                .updatedAt(Instant.ofEpochSecond(Generators.longs(1_700_000_000L, 2_000_000_000L).next()))
                .retained(Map.of())
                .build();
    }

    @Test
    @DisplayName("the builder builds the view the constructor builds")
    void viewBuilderEqualsConstructor() {
        var projectId = RuntimeGenerators.projectIds().next();
        var status = status();
        var paused = Generators.booleans().next();
        var operatorWait = Generators.booleans().next();
        var otherHold = Generators.booleans().next();
        var awaitsExternalEvent = Generators.booleans().next();

        var constructed = new View(projectId, status, paused, operatorWait, otherHold, awaitsExternalEvent);
        var built = View.builder()
                .projectId(projectId)
                .status(status)
                .paused(paused)
                .operatorWait(operatorWait)
                .otherHold(otherHold)
                .awaitsExternalEvent(awaitsExternalEvent)
                .build();

        assertEquals(constructed, built, "the builder builds the same view");
    }

    @Test
    @DisplayName("the builder refuses a missing project id like the constructor")
    void viewBuilderRefusesMissingProjectId() {
        var builder = View.builder();
        var refusal = assertThrows(NullPointerException.class, builder::build);

        assertEquals("projectId", refusal.getMessage(), "the refusal names the project id");
    }

    @Test
    @DisplayName("the builder builds the stalled hold the constructor builds")
    void stalledHoldBuilderEqualsConstructor() {
        var scopeType = AuditRecord.Scope.PLAN;
        var scopeId = RuntimeGenerators.planIds().next();
        var state = "execute.review";
        var cause = Cause.NO_PROGRESS;
        var idleSince = Instant.ofEpochSecond(Generators.longs(1_700_000_000L, 2_000_000_000L).next());
        var idleSeconds = Generators.longs(0, 100_000).next();

        var constructed = new StalledHold(scopeType, scopeId, state, cause, idleSince, idleSeconds);
        var built = StalledHold.builder()
                .scopeType(scopeType)
                .scopeId(scopeId)
                .state(state)
                .cause(cause)
                .idleSince(idleSince)
                .idleSeconds(idleSeconds)
                .build();

        assertEquals(constructed, built, "the builder builds the same stalled hold");
    }

    @Test
    @DisplayName("the builder refuses a missing scope type like the constructor")
    void stalledHoldBuilderRefusesMissingScopeType() {
        var builder = StalledHold.builder();
        var refusal = assertThrows(NullPointerException.class, builder::build);

        assertEquals("scopeType", refusal.getMessage(), "the refusal names the scope type");
    }
}
