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

/**
 * An immutable registry of known {@link EntityKind} instances (PM-IMPL-5, PM-WF-5).
 *
 * @since 0.1
 */
public final class EntityKindRegistry {

    private static final String ENTITY_UNIT = "entity";

    private final Map<String, EntityKind> kinds;

    /**
     * @param kinds the entity kinds to register
     * @throws NullPointerException if the collection or any element is {@code null}
     */
    public EntityKindRegistry(Collection<EntityKind> kinds) {
        Objects.requireNonNull(kinds, "kinds");
        var map = new LinkedHashMap<String, EntityKind>();
        for (var kind : kinds) {
            Objects.requireNonNull(kind, "kind");
            map.put(kind.name(), kind);
        }
        this.kinds = Collections.unmodifiableMap(map);
    }

    /**
     * Finds an entity kind by name.
     *
     * @param name the entity kind name
     * @return the entity kind, or empty if not found
     */
    public Optional<EntityKind> find(String name) {
        return Optional.ofNullable(kinds.get(name));
    }

    /**
     * Returns a new registry with the additional or replaced entity kind.
     *
     * @param kind the entity kind to add or replace
     * @return a new registry containing the kind
     * @throws NullPointerException if {@code kind} is {@code null}
     */
    public EntityKindRegistry with(EntityKind kind) {
        Objects.requireNonNull(kind, "kind");
        var updated = new LinkedHashMap<>(kinds);
        updated.put(kind.name(), kind);
        return new EntityKindRegistry(updated.values());
    }

    /**
     * Returns the default entity kinds. Every kind names the generic entity unit of its submission shape:
     * {@code screen} has no entity unit, {@code task} runs the task protocol unit {@code worker-task}, the single-submit
     * kinds run {@code entity}, and the multi-submit lesson review kind {@code review} runs {@code entity-multi}. The
     * registry only names the units; {@link WorkflowValidator} checks that a kind's entity unit is registered in its
     * {@link WorkflowCatalog}. The defaults carry no test identifier: tests register their own kinds.
     *
     * @return the default registry
     */
    public static EntityKindRegistry defaults() {
        return new EntityKindRegistry(List.of(
                new EntityKind("screen", Optional.empty(), "screen.evaluate"),
                new EntityKind("task", Optional.of("worker-task"), "task.evaluate"),
                new EntityKind("decision", Optional.of(ENTITY_UNIT), "adjudicate.evaluate"),
                new EntityKind("triage", Optional.of(ENTITY_UNIT), "triage.evaluate"),
                new EntityKind("simplify", Optional.of(ENTITY_UNIT), "simplify.evaluate"),
                new EntityKind("review", Optional.of("entity-multi"), "review.evaluate")));
    }
}
