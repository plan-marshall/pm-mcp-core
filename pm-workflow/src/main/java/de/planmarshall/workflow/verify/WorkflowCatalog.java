/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.verify;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.workflow.ast.StateBehavior;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.ast.WorkflowScope;

/**
 * An immutable registry of registered {@link WorkflowDefinition} units (PM-IMPL-5, PM-WF-16).
 *
 * @since 0.1
 */
public final class WorkflowCatalog {

    private static final WorkflowCatalog EMPTY = new WorkflowCatalog(Map.of());

    private final Map<String, WorkflowDefinition> definitions;

    /**
     * @param definitions the workflow definitions to register
     * @throws NullPointerException if definitions or any element is null
     */
    public WorkflowCatalog(Collection<WorkflowDefinition> definitions) {
        Objects.requireNonNull(definitions, "definitions");
        var map = new LinkedHashMap<String, WorkflowDefinition>();
        for (var def : definitions) {
            Objects.requireNonNull(def, "definition");
            map.put(def.id(), def);
        }
        this.definitions = Collections.unmodifiableMap(map);
    }

    private WorkflowCatalog(Map<String, WorkflowDefinition> definitions) {
        this.definitions = Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
    }

    /**
     * @return an empty catalog
     */
    public static WorkflowCatalog empty() {
        return EMPTY;
    }

    /**
     * @param definitions the definitions to include
     * @return a catalog with the given definitions
     */
    public static WorkflowCatalog of(WorkflowDefinition... definitions) {
        return new WorkflowCatalog(List.of(definitions));
    }

    /**
     * @param id the unit identifier
     * @return the definition if registered
     */
    public Optional<WorkflowDefinition> find(String id) {
        return Optional.ofNullable(definitions.get(id));
    }

    /**
     * @param id the unit identifier
     * @return {@code true} when registered
     */
    public boolean contains(String id) {
        return definitions.containsKey(id);
    }

    /**
     * @return the registered definitions
     */
    public Collection<WorkflowDefinition> definitions() {
        return definitions.values();
    }

    /**
     * @param id the unit identifier
     * @return the registered units with a {@code call} of the unit, in registration order
     */
    List<WorkflowDefinition> callersOf(String id) {
        return definitions.values().stream().filter(definition -> calls(definition, id)).toList();
    }

    /**
     * Tells whether every caller chain of a unit through the catalog ends in plan units (Rule 2, ADR 025): the unit
     * has a caller, every caller is a {@link WorkflowScope#PLAN} unit or a {@link WorkflowScope#SUBWORKFLOW} unit
     * whose callers satisfy the same, walked transitively, and the walk reaches a plan unit. A caller of any other
     * scope, or a unit on the walk without a caller, ends a chain outside the plan units. Each unit is walked once, so
     * a call cycle, which Rule 7 refuses, does not loop.
     *
     * @param id the unit identifier, registered or not
     * @return {@code true} when only plan units reach the unit, directly or through sub-workflows
     */
    boolean calledOnlyFromPlanUnits(String id) {
        var visited = new HashSet<String>();
        var queue = new ArrayDeque<>(List.of(id));
        var reachesPlanUnit = false;
        while (!queue.isEmpty()) {
            var current = queue.poll();
            if (!visited.add(current)) {
                continue;
            }
            var callers = callersOf(current);
            if (callers.isEmpty()) {
                return false;
            }
            for (var caller : callers) {
                switch (caller.scope()) {
                    case PLAN -> reachesPlanUnit = true;
                    case SUBWORKFLOW -> queue.add(caller.id());
                    case EPIC, WORKSPACE, MACHINE, ENTITY -> {
                        return false;
                    }
                }
            }
        }
        return reachesPlanUnit;
    }

    private static boolean calls(WorkflowDefinition definition, String id) {
        return definition.states().values().stream()
                .anyMatch(state -> state.behavior() instanceof StateBehavior.SubWorkflowCall call
                        && id.equals(call.subWorkflowId()));
    }
}
