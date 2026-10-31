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
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * The six phases of a plan, each carried by the phase sub-workflow the plan unit calls (PM-WF-16, PM-IMPL-5).
 * <p>
 * A phase is never declared by a state and never by a sub-workflow's own frontmatter: it is the fact that the phase
 * sub-workflow is on the call stack. The plan unit calls each phase through the call state {@code phases.<phase>}, and
 * that call enters the phase unit named here. Every sub-workflow called within a phase — a remediation unit, the land
 * sub-workflow, a chat — carries the phase of the enclosing frame, and so does every entity unit.
 * <p>
 * The wire identifier is the identifier of the phase's unit, which is also the value the status store records for the
 * phase of a scope ({@code phase-1-init} … {@code phase-6-finalize}).
 *
 * @since 0.1
 */
public enum Phase {

    /** The init phase, unit {@code phase-1-init}. */
    INIT("phase-1-init"),

    /** The refine phase, unit {@code phase-2-refine}. */
    REFINE("phase-2-refine"),

    /** The outline phase, unit {@code phase-3-outline}. */
    OUTLINE("phase-3-outline"),

    /** The plan phase, unit {@code phase-4-plan}. */
    PLAN("phase-4-plan"),

    /** The execute phase, unit {@code phase-5-execute}. */
    EXECUTE("phase-5-execute"),

    /** The finalize phase, unit {@code phase-6-finalize}. */
    FINALIZE("phase-6-finalize");

    /** The identifier of the unit that composes the phases: the plan unit. */
    public static final String PLAN_UNIT = "plan";

    /** The prefix of the plan unit's call state of a phase: {@code phases.<phase>}. */
    public static final String CALL_STATE_PREFIX = "phases.";

    private final String unitId;

    Phase(String unitId) {
        this.unitId = unitId;
    }

    /**
     * @return the identifier of the unit that carries the phase
     */
    public String unitId() {
        return unitId;
    }

    /**
     * @return the value the status store records for the phase of a scope
     */
    public String wireName() {
        return unitId;
    }

    /**
     * @return the identifier of the plan unit's call state that enters the phase, {@code phases.<name>}
     */
    public String callStateId() {
        return CALL_STATE_PREFIX + name().toLowerCase(Locale.ROOT);
    }

    /**
     * @param unitId the identifier of a unit
     * @return the phase the unit carries; empty for a unit that is not a phase sub-workflow
     */
    public static Optional<Phase> ofUnit(String unitId) {
        return Arrays.stream(values()).filter(phase -> phase.unitId.equals(unitId)).findFirst();
    }

    /**
     * @param stateId the identifier of a state
     * @return the phase the plan unit's call state {@code phases.<name>} enters; empty for another state
     */
    public static Optional<Phase> ofCallState(@Nullable String stateId) {
        if (stateId == null || !stateId.startsWith(CALL_STATE_PREFIX)) {
            return Optional.empty();
        }
        var name = stateId.substring(CALL_STATE_PREFIX.length());
        return Arrays.stream(values()).filter(phase -> phase.name().toLowerCase(Locale.ROOT).equals(name))
                .findFirst();
    }

    /**
     * @return the identifiers of the six phase units in phase order
     */
    public static List<String> unitIds() {
        return Arrays.stream(values()).map(Phase::unitId).toList();
    }
}
