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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * An immutable registry of named parameter schemas (PM-IMPL-5, PM-HYP-10).
 *
 * @since 0.1
 */
public final class SchemaRegistry {

    private final Set<String> schemas;

    /**
     * @param schemas the schema names to register
     * @throws NullPointerException if the collection or any element is {@code null}
     */
    public SchemaRegistry(Collection<String> schemas) {
        Objects.requireNonNull(schemas, "schemas");
        this.schemas = Collections.unmodifiableSet(new LinkedHashSet<>(schemas));
    }

    /**
     * @param schemaName the schema name to check
     * @return {@code true} if the named schema is registered
     */
    public boolean contains(String schemaName) {
        return schemas.contains(schemaName);
    }

    /**
     * Returns a new registry with the additional schema name.
     *
     * @param schemaName the schema name to add
     * @return a new registry containing the schema
     * @throws NullPointerException if {@code schemaName} is {@code null}
     */
    public SchemaRegistry with(String schemaName) {
        Objects.requireNonNull(schemaName, "schemaName");
        var updated = new LinkedHashSet<>(schemas);
        updated.add(schemaName);
        return new SchemaRegistry(updated);
    }

    /**
     * @return the default registry containing the standard specification parameter schemas
     */
    public static SchemaRegistry defaults() {
        return new SchemaRegistry(List.of(
                "question-envelope",
                "question-answer",
                "vote-answer",
                "triage-groups",
                "simplify-proposal",
                "commit-result"));
    }
}
