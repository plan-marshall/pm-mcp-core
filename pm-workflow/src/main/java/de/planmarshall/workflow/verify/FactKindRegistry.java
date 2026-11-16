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
import java.util.Objects;
import java.util.Set;

/**
 * The closed fact kinds a unit may read in its frontmatter (PM-WF-16, Rule 1).
 * <p>
 * The registry is an argument of the verifier, never code of this module: the fact-kind enumeration lives outside
 * {@code pm-workflow} (ADR {@code workflow-verify/015}), so the production wiring supplies the registry and the default
 * is the empty one, which refuses every {@code reads_fact} a unit declares (fail closed).
 *
 * @param kinds the known fact kinds, in declaration order
 * @since 0.1
 */
public record FactKindRegistry(Set<String> kinds) {

    /**
     * Refuses a missing set and copies it into an unmodifiable set that keeps its order.
     *
     * @param kinds the known fact kinds
     * @throws NullPointerException if the set or one of its elements is {@code null}
     */
    public FactKindRegistry {
        Objects.requireNonNull(kinds, "kinds");
        kinds = Collections.unmodifiableSet(new LinkedHashSet<>(kinds));
    }

    /**
     * @return the registry of no kind, the default of a verifier whose caller supplies none
     */
    public static FactKindRegistry empty() {
        return new FactKindRegistry(Set.of());
    }

    /**
     * @param kinds the known fact kinds
     * @return a registry of the kinds
     * @throws IllegalArgumentException if a kind occurs twice
     */
    public static FactKindRegistry of(Collection<String> kinds) {
        var copied = new LinkedHashSet<String>();
        for (var kind : kinds) {
            if (!copied.add(Objects.requireNonNull(kind, "kind"))) {
                throw new IllegalArgumentException("the fact kind '%s' is registered twice".formatted(kind));
            }
        }
        return new FactKindRegistry(copied);
    }

    /**
     * @param kind a fact kind
     * @return whether the closed set of fact kinds holds it
     */
    public boolean contains(String kind) {
        return kinds.contains(kind);
    }
}
