/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.model;

import java.util.Objects;
import java.util.Set;

import de.planmarshall.core.role.RoleClass;

/**
 * One role a composed unit names, with the classes its use admits (PM-WORK-1, Composition Check).
 * <p>
 * A model state names a role for an entity kind; the kind decides the class the role's tasks require. The compiled table
 * of {@link #classesOf} names the shipped kinds: the screener's `screen` and the judge's grading kind `zero_tool` are
 * {@code zero_tool}; `envelope`, `remediation-fix`, `commit-fix`, `simplify-apply`, `conflict` and the pre-existing
 * `task` kind are {@code writer}; the vote kind admits {@code read_only}, or {@code zero_tool} for a judgment role on a
 * {@code system_one} endpoint of the protocol — the endpoint's protocol is checked by the judgment-vote package, which
 * owns the endpoint profiles; and every other kind admits {@code writer} or {@code read_only} as its work needs. A
 * consultation clause admits every class but {@code zero_tool}.
 * <p>
 * A unit that registers a kind with another class set passes it to {@link #RoleUse} directly; the compiled table covers
 * the shipped kinds only.
 *
 * @param stateId         the state that names the role
 * @param role            the role name
 * @param acceptedClasses the classes the use admits
 * @param consultation    whether the use is a {@code consult role} clause
 * @since 0.1
 */
public record RoleUse(String stateId, String role, Set<RoleClass> acceptedClasses, boolean consultation) {

    /** Copies the set. */
    public RoleUse {
        Objects.requireNonNull(stateId, "stateId");
        Objects.requireNonNull(role, "role");
        acceptedClasses = Set.copyOf(acceptedClasses);
    }

    /**
     * @param stateId    the model state
     * @param entityKind the entity kind of the {@code job llm} clause
     * @param role       the role name
     * @return the use of a model state
     */
    public static RoleUse forModelJob(String stateId, String entityKind, String role) {
        return new RoleUse(stateId, role, classesOf(entityKind), false);
    }

    /**
     * @param stateId the state that consults
     * @param role    the consulted role
     * @return the use of a {@code consult role} clause
     */
    public static RoleUse forConsultation(String stateId, String role) {
        return new RoleUse(stateId, role, Set.of(RoleClass.WRITER, RoleClass.READ_ONLY), true);
    }

    /**
     * @param entityKind a registered entity kind
     * @return the classes the kind's tasks require
     */
    public static Set<RoleClass> classesOf(String entityKind) {
        return switch (entityKind) {
            case "screen", "zero_tool" -> Set.of(RoleClass.ZERO_TOOL);
            case "envelope", "task", "remediation-fix", "commit-fix", "simplify-apply", "conflict" ->
                    Set.of(RoleClass.WRITER);
            case "vote" -> Set.of(RoleClass.READ_ONLY, RoleClass.ZERO_TOOL);
            default -> Set.of(RoleClass.WRITER, RoleClass.READ_ONLY);
        };
    }
}
