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

import org.jspecify.annotations.Nullable;

/**
 * A registered entity kind (PM-IMPL-5, PM-WF-5). Holds the kind's name, its optional entity unit, and the
 * return-evaluation primitive that runs upon task completion.
 *
 * @param name the entity kind name
 * @param entityUnit the entity unit; {@code null} for kinds with no entity unit (e.g., {@code screen})
 * @param returnEvaluationPrimitiveId the identifier of the return-evaluation primitive
 * @since 0.1
 */
public record EntityKind(
String name,
@Nullable String entityUnit,
String returnEvaluationPrimitiveId) {

    /**
     * Refuses a missing name or return-evaluation primitive; a kind without an entity unit carries {@code null}.
     *
     * @param name the entity kind name
     * @param entityUnit the entity unit, {@code null} if none
     * @param returnEvaluationPrimitiveId the return-evaluation primitive identifier
     * @throws NullPointerException if the name or the return-evaluation primitive is {@code null}
     */
    public EntityKind {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(returnEvaluationPrimitiveId, "returnEvaluationPrimitiveId");
    }

    /**
     * @return {@code true} if this entity kind has an entity unit
     */
    public boolean hasEntityUnit() {
        return entityUnit != null;
    }
}
