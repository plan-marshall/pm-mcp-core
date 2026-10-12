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
import java.util.Set;

/**
 * A registered question class with its closed set of resolution outcomes (PM-IMPL-5, PM-WF-12).
 *
 * @param name the question class name
 * @param outcomes the closed set of resolution outcomes for this question class
 * @since 0.1
 */
public record QuestionClass(String name, Set<String> outcomes) {

    /**
     * Refuses {@code null} components and copies outcomes into an immutable set.
     *
     * @param name the question class name
     * @param outcomes the resolution outcomes
     * @throws NullPointerException if any component is {@code null}
     */
    public QuestionClass {
        Objects.requireNonNull(name, "name");
        outcomes = Set.copyOf(outcomes);
    }
}
