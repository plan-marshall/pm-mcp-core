/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.finalize;

import java.util.Objects;

/**
 * One violation of a {@link PipelineRule} by a step of a roster (PM-IMPL-5). A violation is a value, never an
 * exception.
 *
 * @param rule     the rule the step violates
 * @param stepName the name of the step the violation belongs to
 * @param message  what is wrong, naming the orders, artifacts or fields concerned
 * @since 0.1
 */
public record PipelineViolation(PipelineRule rule, String stepName, String message) {

    /**
     * Refuses a missing component.
     *
     * @param rule     the rule the step violates
     * @param stepName the name of the step
     * @param message  what is wrong
     * @throws NullPointerException if a component is {@code null}
     */
    public PipelineViolation {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(stepName, "stepName");
        Objects.requireNonNull(message, "message");
    }
}
