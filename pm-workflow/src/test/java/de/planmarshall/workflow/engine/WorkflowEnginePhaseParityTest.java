/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.HashSet;
import java.util.Set;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.WorkflowStatus;

/**
 * The phase map of the engine keeps parity with the phases of the status store: every phase of the DSL the
 * engine maps resolves to a phase {@link WorkflowStatus#PHASES} admits, and every phase of the store, the terminal
 * ones included, is reached by exactly one phase of the DSL. A phase added to the store without the engine, or to the
 * engine without the store, fails here instead of failing a running unit in {@code phaseOf} with an unresolved name.
 * {@code WorkflowStatus.PHASES} itself is held to the schema of the workflow state store by the schema parity test of
 * {@code pm-core}.
 */
@DisplayName("WorkflowEngine phase parity")
class WorkflowEnginePhaseParityTest {

    @Test
    @DisplayName("every phase the engine maps is a phase of the status store")
    void mappedPhasesAreStorePhases() {
        var mapped = Set.copyOf(WorkflowEngine.PHASES.values());

        assertFalse(mapped.isEmpty(), "the engine maps phases");
        assertEquals(Set.of(), difference(mapped, WorkflowStatus.PHASES),
                "the engine maps to a phase the status store does not admit");
    }

    @Test
    @DisplayName("every phase of the status store is mapped by the engine")
    void storePhasesAreMapped() {
        var mapped = Set.copyOf(WorkflowEngine.PHASES.values());

        assertEquals(Set.of(), difference(WorkflowStatus.PHASES, mapped),
                "the status store admits a phase the engine does not map");
    }

    @Test
    @DisplayName("no two phases of the DSL map to the same phase of the status store")
    void mappingIsInjective() {
        assertEquals(WorkflowEngine.PHASES.size(), Set.copyOf(WorkflowEngine.PHASES.values()).size());
    }

    private static Set<String> difference(Set<String> from, Set<String> minus) {
        var remaining = new HashSet<>(from);
        remaining.removeAll(minus);
        return remaining;
    }
}
