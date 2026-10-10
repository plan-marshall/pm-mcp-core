/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.ast;

import java.util.List;

/**
 * A {@code guard <expr>} clause with the enum or boolean configuration keys it reads ({@code depends_on_config})
 * (PM-IMPL-5).
 *
 * @param expr            the guard expression
 * @param dependsOnConfig the configuration keys the guard reads, in source order
 * @since 0.1
 */
public record GuardSpec(GuardExpr expr, List<String> dependsOnConfig) {

    /**
     * Copies the configuration keys into an immutable list.
     *
     * @param expr            the guard expression
     * @param dependsOnConfig the configuration keys the guard reads
     */
    public GuardSpec {
        dependsOnConfig = List.copyOf(dependsOnConfig);
    }
}
