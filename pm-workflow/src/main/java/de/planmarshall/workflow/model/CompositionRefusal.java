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

import java.io.Serial;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleValidationReason;

/**
 * A composition refused because a model state or a consultation clause names a role it cannot use (PM-WORK-1, Composition
 * Check): {@code role_undefined} when the role is absent or invalid — naming the role, the state and the validation reason
 * where there is one — and {@code class_mismatch} when the role's class does not fit its use, naming the role, its class,
 * the state and the required class.
 *
 * @since 0.1
 */
public final class CompositionRefusal extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Why the composition was refused. The set is closed. */
    public enum Reason {
        /** The role is absent from the effective role set, or its artifact is invalid. */
        ROLE_UNDEFINED("role_undefined"),
        /** The role's class does not fit the use. */
        CLASS_MISMATCH("class_mismatch");

        private final String wireName;

        Reason(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the reason in a representation */
        public String wireName() {
            return wireName;
        }
    }

    private final Reason reason;
    private final String role;
    private final String stateId;
    private final RoleClass roleClass;
    private final Set<RoleClass> requiredClasses;
    private final RoleValidationReason validationReason;
    private final String detail;

    private CompositionRefusal(Reason reason, String role, String stateId, RoleClass roleClass,
            Set<RoleClass> requiredClasses, Optional<RoleValidationReason> validationReason, String detail) {
        super(message(reason, role, stateId, roleClass, validationReason, detail));
        this.reason = Objects.requireNonNull(reason, "reason");
        this.role = Objects.requireNonNull(role, "role");
        this.stateId = Objects.requireNonNull(stateId, "stateId");
        this.roleClass = roleClass;
        this.requiredClasses = requiredClasses;
        this.validationReason = validationReason.orElse(null);
        this.detail = detail == null ? "" : detail;
    }

    /**
     * @param role             the role name
     * @param stateId          the state that names it
     * @param validationReason the reason the role is undefined, empty when it is absent
     * @param detail           what is wrong
     * @return the refusal
     */
    public static CompositionRefusal roleUndefined(String role, String stateId,
            Optional<RoleValidationReason> validationReason, String detail) {
        return new CompositionRefusal(Reason.ROLE_UNDEFINED, role, stateId, null, Set.of(), validationReason, detail);
    }

    /**
     * @param role            the role name
     * @param stateId         the state that names it
     * @param roleClass       the class of the role
     * @param requiredClasses the classes the use admits
     * @return the refusal
     */
    public static CompositionRefusal classMismatch(String role, String stateId, RoleClass roleClass,
            Set<RoleClass> requiredClasses) {
        var required = requiredClasses.stream().map(RoleClass::wireName).sorted().toList();
        return new CompositionRefusal(Reason.CLASS_MISMATCH, role, stateId, roleClass, Set.copyOf(requiredClasses),
                Optional.empty(), "the role is %s, the use requires %s"
                        .formatted(roleClass == null ? "of no class" : roleClass.wireName(), required));
    }

    /** @return why the composition was refused */
    public Reason reason() {
        return reason;
    }

    /** @return the refused role */
    public String role() {
        return role;
    }

    /** @return the state that names the role */
    public String stateId() {
        return stateId;
    }

    /** @return the class of the role; {@code null} when the role is undefined */
    public RoleClass roleClass() {
        return roleClass;
    }

    /** @return the classes the use admits; empty when the role is undefined */
    public Set<RoleClass> requiredClasses() {
        return requiredClasses;
    }

    /** @return the validation reason of an invalid role; empty when the role is absent or mismatched */
    public Optional<RoleValidationReason> validationReason() {
        return Optional.ofNullable(validationReason);
    }

    /** @return what is wrong, naming the value where one exists */
    public String detail() {
        return detail;
    }

    private static String message(Reason reason, String role, String stateId, RoleClass roleClass,
            Optional<RoleValidationReason> validationReason, String detail) {
        var validation = validationReason.isEmpty()
                ? ""
                : " (%s)".formatted(validationReason.get().wireName());
        var classes = roleClass == null ? "" : " [%s]".formatted(roleClass.wireName());
        return "%s: %s names the role '%s'%s%s: %s".formatted(stateId, reason.wireName(), role, validation, classes,
                detail);
    }
}
