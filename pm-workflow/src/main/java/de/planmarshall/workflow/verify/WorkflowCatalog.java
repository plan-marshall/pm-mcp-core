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

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.workflow.ast.WorkflowDefinition;

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
}
