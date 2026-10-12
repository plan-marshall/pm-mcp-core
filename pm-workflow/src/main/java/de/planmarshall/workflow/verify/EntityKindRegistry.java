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
     * @return the default registry containing the standard specification entity kinds
     */
    public static EntityKindRegistry defaults() {
        return new EntityKindRegistry(List.of(
                new EntityKind("screen", Optional.empty(), "screen.evaluate"),
                new EntityKind("task", Optional.of("worker-task"), "task.evaluate"),
                new EntityKind("change", Optional.of("entity"), "test.evaluate-change"),
                new EntityKind("decision", Optional.of("entity"), "adjudicate.evaluate"),
                new EntityKind("triage", Optional.of("entity"), "triage.evaluate"),
                new EntityKind("simplify", Optional.of("entity"), "simplify.evaluate"),
                new EntityKind("review", Optional.of("entity"), "review.evaluate")));
    }
}
