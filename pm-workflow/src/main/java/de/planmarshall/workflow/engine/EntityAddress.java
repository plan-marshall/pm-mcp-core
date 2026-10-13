/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.engine;

import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.store.JournalScopeType;

/**
 * The address of an entity within the scope a call addresses (PM-ARCH-4: every stateful call carries its scope and, for
 * an entity, the entity identifier). The scope is a plan or an epic, named by its id, or the project's workspace scope,
 * which a tool call names with the reserved {@code plan_id} value {@value #NO_PLAN} and which carries no scope id.
 * <p>
 * The address holds the entity identifier as it was supplied: its grammar and whether it names anything in the scope
 * are decided by {@link EntityAddressResolver}, which answers every such refusal as a value. Only a malformed address
 * (a {@code null} component, a scope type that cannot be addressed, a scope id where none belongs or none where one
 * does) is a programming error of the caller and refused with an exception.
 *
 * @param scopeType the type of the addressed scope: {@link JournalScopeType#PLAN}, {@link JournalScopeType#EPIC} or
 *                  {@link JournalScopeType#WORKSPACE}
 * @param scopeId   the plan or epic id; empty for the workspace scope
 * @param entity    the entity identifier as supplied
 * @since 0.1
 */
public record EntityAddress(JournalScopeType scopeType, Optional<String> scopeId, String entity) {

    /** The reserved value of a tool's {@code plan_id} that addresses the project's workspace scope. */
    public static final String NO_PLAN = "NO_PLAN";

    /**
     * @throws NullPointerException     if a component is {@code null}
     * @throws IllegalArgumentException if the scope type is the lesson store, a plan or epic has no scope id, the
     *                                  workspace scope has one, or the scope id is {@value #NO_PLAN}
     */
    public EntityAddress {
        Objects.requireNonNull(scopeType, "scopeType");
        Objects.requireNonNull(scopeId, "scopeId");
        Objects.requireNonNull(entity, "entity");
        if (scopeType == JournalScopeType.LESSONS) {
            throw new IllegalArgumentException("the lesson store is no addressable scope");
        }
        if (scopeType.scopeIdRequired() != scopeId.isPresent()) {
            throw new IllegalArgumentException("a %s scope %s a scope id".formatted(scopeType.wireName(),
                    scopeType.scopeIdRequired() ? "requires" : "carries no"));
        }
        if (scopeId.filter(NO_PLAN::equals).isPresent()) {
            throw new IllegalArgumentException(NO_PLAN + " addresses the workspace scope and is no scope id");
        }
    }

    /**
     * @param entity the entity identifier as supplied
     * @return the address of an entity of the project's workspace scope
     */
    public static EntityAddress workspace(String entity) {
        return new EntityAddress(JournalScopeType.WORKSPACE, Optional.empty(), entity);
    }

    /**
     * @param planId the plan id
     * @param entity the entity identifier as supplied
     * @return the address of an entity of a plan scope
     */
    public static EntityAddress plan(String planId, String entity) {
        return new EntityAddress(JournalScopeType.PLAN, Optional.of(Objects.requireNonNull(planId, "planId")), entity);
    }

    /**
     * @param epicId the epic id
     * @param entity the entity identifier as supplied
     * @return the address of an entity of an epic scope
     */
    public static EntityAddress epic(String epicId, String entity) {
        return new EntityAddress(JournalScopeType.EPIC, Optional.of(Objects.requireNonNull(epicId, "epicId")), entity);
    }

    /**
     * Builds the address from the arguments of a scoped tool call: exactly one of {@code plan_id} and {@code epic_id},
     * where {@code plan_id} {@value #NO_PLAN} names the workspace scope.
     *
     * @param planId the {@code plan_id} argument, a plan id or {@value #NO_PLAN}; empty when not supplied
     * @param epicId the {@code epic_id} argument; empty when not supplied
     * @param entity the {@code entity} argument
     * @return the address
     * @throws NullPointerException     if an argument is {@code null}
     * @throws IllegalArgumentException if neither or both of {@code plan_id} and {@code epic_id} are supplied; the tool
     *                                  binding refuses such a call before an address is built
     */
    public static EntityAddress fromArguments(Optional<String> planId, Optional<String> epicId, String entity) {
        Objects.requireNonNull(planId, "planId");
        Objects.requireNonNull(epicId, "epicId");
        if (planId.isPresent() == epicId.isPresent()) {
            throw new IllegalArgumentException("exactly one of plan_id and epic_id must be supplied");
        }
        if (epicId.isPresent()) {
            return epic(epicId.get(), entity);
        }
        var plan = planId.get();
        return NO_PLAN.equals(plan) ? workspace(entity) : plan(plan, entity);
    }

    /** @return whether the address names the project's workspace scope */
    public boolean isWorkspace() {
        return scopeType == JournalScopeType.WORKSPACE;
    }
}
