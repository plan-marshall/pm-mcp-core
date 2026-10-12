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

import java.util.Objects;
import java.util.Optional;

/**
 * A registered entity kind (PM-IMPL-5, PM-WF-5). Holds the kind's name, its optional entity unit, and the
 * return-evaluation primitive that runs upon task completion.
 *
 * @param name the entity kind name
 * @param entityUnit the optional entity unit; empty for kinds with no entity unit (e.g., {@code screen})
 * @param returnEvaluationPrimitiveId the identifier of the return-evaluation primitive
 * @since 0.1
 */
public record EntityKind(
        String name,
        Optional<String> entityUnit,
        String returnEvaluationPrimitiveId) {

    /**
     * Refuses {@code null} components.
     *
     * @param name the entity kind name
     * @param entityUnit the optional entity unit
     * @param returnEvaluationPrimitiveId the return-evaluation primitive identifier
     * @throws NullPointerException if any component is {@code null}
     */
    public EntityKind {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(entityUnit, "entityUnit");
        Objects.requireNonNull(returnEvaluationPrimitiveId, "returnEvaluationPrimitiveId");
    }

    /**
     * Convenience constructor wrapping a nullable entity unit string in an {@link Optional}.
     *
     * @param name the entity kind name
     * @param entityUnit the entity unit string, or {@code null} if none
     * @param returnEvaluationPrimitiveId the return-evaluation primitive identifier
     */
    public EntityKind(String name, String entityUnit, String returnEvaluationPrimitiveId) {
        this(name, Optional.ofNullable(entityUnit), returnEvaluationPrimitiveId);
    }

    /**
     * @return {@code true} if this entity kind has an entity unit
     */
    public boolean hasEntityUnit() {
        return entityUnit.isPresent();
    }
}
