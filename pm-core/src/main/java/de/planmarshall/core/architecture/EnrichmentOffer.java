/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.architecture;

import java.util.List;
import java.util.Objects;

/**
 * The offer the project setup shows before an architecture enrichment costs model tokens (Domain Extensions § Setup
 * Enrichment; Harness Profiles § Tokens, Not Money): the modules to describe and the expected tokens of their
 * enrichment, counted in tokens and never in money. The enrichment runs only behind the operator's {@code model_cost}
 * consent; a declined one is recorded with its reason and owed to a later run, and the project is ready without the
 * descriptions.
 * <p>
 * The estimate is compiled: one module's description is one task that writes the module's responsibility, its purpose
 * and its key packages, so the expected tokens are that compiled figure per offered module.
 *
 * @param modules        the modules to describe, named as the discovery named them
 * @param expectedTokens the expected tokens of the whole enrichment
 * @since 0.1
 */
public record EnrichmentOffer(List<String> modules, long expectedTokens) {

    /** The compiled expected tokens of describing one module. */
    public static final long PER_MODULE = 6_000L;

    /**
     * @throws NullPointerException     if the modules are {@code null}
     * @throws IllegalArgumentException if no module is offered, or the expected tokens are not the compiled estimate of
     *                                  the offered modules
     */
    public EnrichmentOffer {
        var snapshot = List.copyOf(Objects.requireNonNull(modules, "modules"));
        if (snapshot.isEmpty()) {
            throw new IllegalArgumentException("An enrichment offers at least one module to describe");
        }
        if (expectedTokens != expectedTokens(snapshot.size())) {
            throw new IllegalArgumentException(
                    "An enrichment of %d modules costs the compiled %d tokens: %d"
                            .formatted(snapshot.size(), expectedTokens(snapshot.size()), expectedTokens));
        }
        modules = snapshot;
    }

    /**
     * Estimates the tokens the enrichment of a number of modules spends.
     *
     * @param modules the number of modules to describe
     * @return the expected tokens
     * @throws IllegalArgumentException if the number is negative
     */
    public static long expectedTokens(int modules) {
        if (modules < 0) {
            throw new IllegalArgumentException("A module count is not negative: " + modules);
        }
        return modules * PER_MODULE;
    }

    /**
     * @param modules the modules to describe
     * @return the offer of those modules
     * @throws IllegalArgumentException if the list is empty
     */
    public static EnrichmentOffer of(List<String> modules) {
        var snapshot = List.copyOf(Objects.requireNonNull(modules, "modules"));
        return new EnrichmentOffer(snapshot, expectedTokens(snapshot.size()));
    }

    /**
     * The cost line a consent state shows: the expected tokens and nothing else, so no price of any currency is ever
     * put before the operator in place of the usage the harness reports.
     *
     * @return the cost line, for example {@code "12000 tokens"}
     */
    public String costLine() {
        return expectedTokens + " tokens";
    }
}
