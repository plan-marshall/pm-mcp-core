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

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * The closed set of the six unit types (PM-IMPL-5): what a unit is to the system and to the operator. Together with
 * the unit's {@link WorkflowScope scope} the type fixes the shape of its interface, the entries and exits its
 * frontmatter declares. The {@code type} line of a frontmatter writes the {@link #wireName() wire name}, the lower-case
 * name of the constant.
 *
 * @since 0.1
 */
public enum UnitType {
    /**
     * The process of a scope that the server starts and that is never called: the plan unit, a workflow of the six
     * phase sub-workflows, and the epic unit. Scope {@code plan} or {@code epic}; no entries, no exits.
     */
    WORKFLOW,
    /**
     * The workflow of a recipe, kept beside the recipe's descriptor: the process of a recipe's plan, started when the
     * operator starts the recipe. Scope {@code plan}; no entries, no exits.
     */
    RECIPE,
    /**
     * A unit another unit calls with {@code call}: the phase sub-workflows, the sub-workflow of the {@code land} lane,
     * the variants of the plan behaviour axes, {@code git.commit_and_push}, the remediation policy units and
     * {@code replan}; and the adjudication unit, which the engine runs in its own frame. Scope {@code subworkflow}; one
     * or more entries (none for the adjudication unit) and one or more exits (none for a landing variant).
     */
    SUBWORKFLOW,
    /**
     * The runtime's own flows, compiled only: the machine unit, the workspace unit, the project onboarding unit and the
     * bootstrap units (started), and the sub-workflows of these flows (called). Scope {@code machine},
     * {@code workspace} or {@code entity} with no entries and no exits when started; scope {@code subworkflow} with one
     * or more entries and exits when called.
     */
    SYSTEM,
    /**
     * A unit a task runs in, compiled only: the task protocol unit {@code worker-task} and the entity units. The
     * engine starts it for a task or an entity; its exits are the outcomes the task's state reads. Scope
     * {@code subworkflow} ({@code worker-task}) or {@code entity}; no entries, one or more exits.
     */
    TASK,
    /**
     * A version of a plan instance, which only the server composes and writes. Scope {@code plan}; no entries, no
     * exits.
     */
    INSTANCE;

    /**
     * The name of the type as the {@code type} line of a frontmatter writes it.
     *
     * @return the lower-case name of the constant
     */
    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Looks a type up by its wire name.
     *
     * @param wireName the value of a {@code type} line
     * @return the type whose {@link #wireName()} equals {@code wireName}, or empty when none does
     */
    public static Optional<UnitType> fromWireName(String wireName) {
        return Arrays.stream(values()).filter(type -> type.wireName().equals(wireName)).findFirst();
    }
}
