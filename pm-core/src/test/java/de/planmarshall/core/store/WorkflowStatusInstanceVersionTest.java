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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The instance version in force of the workflow state (PM-IMPL-5, Store Schemas § Workflow State Store): an integer from
 * {@code 1} on the plan scope, absent while no version is in force, and refused off the plan scope and below {@code 1}.
 */
@DisplayName("The instance version of the workflow state")
class WorkflowStatusInstanceVersionTest {

    private static final Instant AT = Instant.parse("2026-11-18T10:00:00Z");

    private static WorkflowStatus status(JournalScopeType scopeType, Integer instanceVersion) {
        return new WorkflowStatus(scopeType, scopeType == JournalScopeType.PLAN ? "plan-core" : null,
                "phase-1-init", "init.begin", List.of(), instanceVersion, 0, 0, Map.of(), List.of(),
                RetryCounters.empty(), null, Map.of(), Map.of(), null, AT, Map.of());
    }

    @Test
    @DisplayName("a plan's version is held and answers as the version in force")
    void planVersionIsInForce() {
        var inForce = status(JournalScopeType.PLAN, 3);

        assertAll(
                () -> assertEquals(3, inForce.instanceVersion(), "the status holds the version"),
                () -> assertEquals(Optional.of(3), inForce.instanceVersionInForce(),
                        "the version in force answers the held version"),
                () -> assertEquals(Optional.empty(), status(JournalScopeType.PLAN, null).instanceVersionInForce(),
                        "a plan without a version answers none"));
    }

    @Test
    @DisplayName("a version below 1 and a version off the plan scope are refused")
    void invalidVersionIsRefused() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> status(JournalScopeType.PLAN, 0),
                        "a version below one is refused"),
                () -> assertThrows(IllegalArgumentException.class, () -> status(JournalScopeType.WORKSPACE, 1),
                        "a version off the plan scope is refused"));
    }
}
