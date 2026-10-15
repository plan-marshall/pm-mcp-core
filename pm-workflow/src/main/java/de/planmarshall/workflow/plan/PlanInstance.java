/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.plan;

import java.util.Objects;

import de.planmarshall.workflow.ast.WorkflowDefinition;

/**
 * One version of a plan instance (PM-IMPL-5): its number, its header, its tree and its text, the content of
 * {@code instance/plan.v<N>.pmwf}. The text is the printed tree: the {@code workflow plan version <N>} header, the
 * {@code instance} line with the canonical header JSON, and the composed states.
 * <p>
 * The composer returns only verified versions. The public constructor checks that the version, the header and the unit
 * agree on the version number, and nothing more, so it can build a value that was never composed or verified; a caller
 * hands the store the results of the composer alone.
 *
 * @param version the instance version, from {@code 1}
 * @param header  the instance header
 * @param unit    the composed unit, its {@code instance} header included
 * @param text    the DSL text of the version, every line ended by a line feed
 * @since 0.1
 */
public record PlanInstance(int version, PlanInstanceHeader header, WorkflowDefinition unit, String text) {

    /**
     * Refuses a missing component and a version, header or unit that disagree on the version number.
     *
     * @param version the instance version
     * @param header  the instance header
     * @param unit    the composed unit
     * @param text    the DSL text
     * @throws IllegalArgumentException if the header or the unit names another version
     */
    public PlanInstance {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(text, "text");
        if (header.version() != version || unit.version() != version) {
            throw new IllegalArgumentException("the version %d disagrees with the header (%d) or the unit (%d)"
                    .formatted(version, header.version(), unit.version()));
        }
    }
}
