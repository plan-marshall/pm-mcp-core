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
import java.util.Set;

import de.planmarshall.core.role.RoleClass;

/**
 * The task a {@code job llm} state creates (PM-WF-5, PM-WORK-1): the state, the entity it works on, the role name, and the
 * classes its use admits.
 * <p>
 * The accepted classes travel with the job, set by its factory from the entity kind's table ({@link RoleUse#classesOf}) or,
 * for a kind a unit registers with a class set of its own, passed by the caller. Composition and task creation therefore
 * apply one set, never two rules.
 *
 * @param scopeId         the plan or epic the state belongs to; empty for the workspace scope
 * @param stateId         the state the task is created for
 * @param entityKey       the entity the task works on, the key of the task in {@code active_tasks}
 * @param role            the role the state names
 * @param consultation    whether the task serves a {@code consult role} clause
 * @param acceptedClasses the classes the use admits
 * @since 0.1
 */
public record ModelJob(Optional<String> scopeId, String stateId, String entityKey, String role, boolean consultation,
        Set<RoleClass> acceptedClasses) {

    /**
     * @throws NullPointerException     if a component is {@code null}
     * @throws IllegalArgumentException if a name is blank
     */
    public ModelJob {
        Objects.requireNonNull(scopeId, "scopeId");
        requireNonBlank(stateId, "stateId");
        requireNonBlank(entityKey, "entityKey");
        requireNonBlank(role, "role");
        acceptedClasses = Set.copyOf(Objects.requireNonNull(acceptedClasses, "acceptedClasses"));
    }

    /**
     * @param scopeId    the plan or epic; empty for the workspace scope
     * @param stateId    the model state
     * @param entityKey  the entity the task works on
     * @param entityKind the entity kind of the {@code job llm} clause
     * @param role       the role the state names
     * @return the job of a model state
     */
    public static ModelJob forModelJob(Optional<String> scopeId, String stateId, String entityKey, String entityKind,
            String role) {
        return new ModelJob(scopeId, stateId, entityKey, role, false, RoleUse.classesOf(entityKind));
    }

    /**
     * @param scopeId   the plan or epic; empty for the workspace scope
     * @param stateId   the state that consults
     * @param entityKey the entity the task works on
     * @param role      the consulted role
     * @return the job of a consultation
     */
    public static ModelJob consultation(Optional<String> scopeId, String stateId, String entityKey, String role) {
        return new ModelJob(scopeId, stateId, entityKey, role, true, RoleUse.CONSULTATION_CLASSES);
    }

    /**
     * @param roleClass the class of the role the state names
     * @return whether the use admits the class
     */
    public boolean accepts(RoleClass roleClass) {
        return acceptedClasses.contains(roleClass);
    }

    /**
     * @param roleClass the class of the role the state names
     * @return what the use requires, for a refusal
     */
    public String requirement(RoleClass roleClass) {
        var required = acceptedClasses.stream().map(RoleClass::wireName).sorted().toList();
        return "the role is %s, the use requires %s".formatted(roleClass.wireName(), required);
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
