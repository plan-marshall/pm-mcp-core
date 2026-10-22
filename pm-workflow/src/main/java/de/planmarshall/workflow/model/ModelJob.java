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
import java.util.Optional;

import de.planmarshall.core.role.RoleClass;

/**
 * The task a {@code job llm} state creates (PM-WF-5, PM-WORK-1): the state, the entity it works on, the entity kind that
 * decides the class its role must be, and the role name.
 *
 * @param scopeId      the plan or epic the state belongs to; empty for the workspace scope
 * @param stateId      the state the task is created for
 * @param entityKey    the entity the task works on, the key of the task in {@code active_tasks}
 * @param entityKind   the entity kind of the {@code job llm} clause
 * @param role         the role the state names
 * @param consultation whether the task serves a {@code consult role} clause
 * @since 0.1
 */
public record ModelJob(Optional<String> scopeId, String stateId, String entityKey, String entityKind, String role,
        boolean consultation) {

    /**
     * @throws NullPointerException     if a component is {@code null}
     * @throws IllegalArgumentException if a name is blank
     */
    public ModelJob {
        Objects.requireNonNull(scopeId, "scopeId");
        requireNonBlank(stateId, "stateId");
        requireNonBlank(entityKey, "entityKey");
        requireNonBlank(entityKind, "entityKind");
        requireNonBlank(role, "role");
    }

    /**
     * @param roleClass the class of the role the state names
     * @return whether the use admits the class: a consultation admits every class but {@code zero_tool}, a model state the
     *         classes its entity kind requires
     */
    public boolean accepts(RoleClass roleClass) {
        if (consultation) {
            return roleClass != RoleClass.ZERO_TOOL;
        }
        return RoleUse.classesOf(entityKind).contains(roleClass);
    }

    /**
     * @param roleClass the class of the role the state names
     * @return what the use requires, for a refusal
     */
    public String requirement(RoleClass roleClass) {
        return consultation
                ? "a consulted role is writer or read_only, not %s".formatted(roleClass.wireName())
                : "the kind '%s' requires %s, not %s".formatted(entityKind,
                        RoleUse.classesOf(entityKind).stream().map(RoleClass::wireName).sorted().toList(),
                        roleClass.wireName());
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
