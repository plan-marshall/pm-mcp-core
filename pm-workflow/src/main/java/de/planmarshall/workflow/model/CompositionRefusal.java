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
import org.jspecify.annotations.Nullable;

/**
 * A composition refused because a model state, a consultation clause, a quorum declaration or a {@code skill} clause
 * names something it cannot use (PM-WORK-1, Composition Check; Workflow DSL § Static AST Verification Rules, Rules 13
 * and 14): {@code role_undefined} when the role is absent or invalid — naming the role, the state and the validation
 * reason where there is one — {@code class_mismatch} when the role's class does not fit its use, naming the role, its
 * class, the state and the required class, {@code quorum_invalid} when a quorum lists a role that is undefined or not
 * {@code read_only}, {@code quorum_not_diverse} when two listed roles of one declaration name the same model by their
 * model identity, naming both roles, both identifiers and the shared identity, and {@code skill_unresolved} when a
 * skill a {@code skill} clause names does not resolve in the project's skill catalogue.
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
        CLASS_MISMATCH("class_mismatch"),
        /** A quorum lists a role that is undefined or not {@code read_only}. */
        QUORUM_INVALID("quorum_invalid"),
        /** Two listed roles of one quorum declaration name the same model by their model identity. */
        QUORUM_NOT_DIVERSE("quorum_not_diverse"),
        /** A skill a {@code skill} clause names does not resolve in the project's skill catalogue. */
        SKILL_UNRESOLVED("skill_unresolved");

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
    private final @Nullable RoleClass roleClass;
    private final Set<RoleClass> requiredClasses;
    private final @Nullable RoleValidationReason validationReason;
    private final String detail;

    private CompositionRefusal(Reason reason, String role, String stateId, @Nullable RoleClass roleClass,
            Set<RoleClass> requiredClasses, @Nullable RoleValidationReason validationReason, String detail) {
        super(message(reason, role, stateId, roleClass, validationReason, detail));
        this.reason = Objects.requireNonNull(reason, "reason");
        this.role = Objects.requireNonNull(role, "role");
        this.stateId = Objects.requireNonNull(stateId, "stateId");
        this.roleClass = roleClass;
        this.requiredClasses = requiredClasses;
        this.validationReason = validationReason;
        this.detail = detail;
    }

    /**
     * @param role             the role name
     * @param stateId          the state that names it
     * @param validationReason the reason the role is undefined, {@code null} when it is absent
     * @param detail           what is wrong
     * @return the refusal
     */
    public static CompositionRefusal roleUndefined(String role, String stateId,
            @Nullable RoleValidationReason validationReason, String detail) {
        return new CompositionRefusal(Reason.ROLE_UNDEFINED, role, stateId, null, Set.of(), validationReason, detail);
    }

    /**
     * @param role            the role name
     * @param stateId         the state that names it
     * @param roleClass       the class of the role
     * @param requiredClasses the classes the use admits
     * @return the refusal
     */
    public static CompositionRefusal classMismatch(String role, String stateId, @Nullable RoleClass roleClass,
            Set<RoleClass> requiredClasses) {
        var required = requiredClasses.stream().map(RoleClass::wireName).sorted().toList();
        return new CompositionRefusal(Reason.CLASS_MISMATCH, role, stateId, roleClass, Set.copyOf(requiredClasses),
                null, "the role is %s, the use requires %s"
                .formatted(roleClass == null ? "of no class" : roleClass.wireName(), required));
    }

    /**
     * @param role      the role the quorum lists
     * @param site      the unit that declares the quorum
     * @param roleClass the class of the role; {@code null} when the role is undefined
     * @param detail    what is wrong
     * @return the refusal
     */
    public static CompositionRefusal quorumInvalid(String role, String site, @Nullable RoleClass roleClass,
            String detail) {
        return new CompositionRefusal(Reason.QUORUM_INVALID, role, site, roleClass, Set.of(), null, detail);
    }

    /**
     * @param firstRole      the first listed role
     * @param firstModel     the identifier of the first role's model
     * @param secondRole     the second listed role
     * @param secondModel    the identifier of the second role's model
     * @param identity       the identity both roles share
     * @param site           the unit that declares the quorum
     * @return the refusal
     */
    public static CompositionRefusal quorumNotDiverse(String firstRole, String firstModel, String secondRole,
            String secondModel, String identity, String site) {
        return new CompositionRefusal(Reason.QUORUM_NOT_DIVERSE, firstRole, site, null, Set.of(), null,
                "the roles '%s' (%s) and '%s' (%s) name the same model by the identity '%s'"
                        .formatted(firstRole, firstModel, secondRole, secondModel, identity));
    }

    /**
     * @param skill   the skill URI the {@code skill} clause names
     * @param stateId the state that names it
     * @return the refusal
     */
    public static CompositionRefusal skillUnresolved(String skill, String stateId) {
        return new CompositionRefusal(Reason.SKILL_UNRESOLVED, skill, stateId, null, Set.of(), null,
                "the project's skill catalogue does not resolve the skill");
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

    /** @return the class of the role; empty when the role is undefined */
    public Optional<RoleClass> roleClass() {
        return Optional.ofNullable(roleClass);
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

    private static String message(Reason reason, String role, String stateId, @Nullable RoleClass roleClass,
            @Nullable RoleValidationReason validationReason, String detail) {
        if (reason == Reason.SKILL_UNRESOLVED) {
            return "%s: skill_unresolved: the state names the skill '%s': %s".formatted(stateId, role, detail);
        }
        if (reason == Reason.QUORUM_INVALID) {
            var classes = roleClass == null ? "" : " [%s]".formatted(roleClass.wireName());
            return "%s: quorum_invalid: the quorum lists the role '%s'%s: %s"
                    .formatted(stateId, role, classes, detail);
        }
        if (reason == Reason.QUORUM_NOT_DIVERSE) {
            return "%s: quorum_not_diverse: %s".formatted(stateId, detail);
        }
        var validation = validationReason == null ? "" : " (%s)".formatted(validationReason.wireName());
        var classes = roleClass == null ? "" : " [%s]".formatted(roleClass.wireName());
        return "%s: %s names the role '%s'%s%s: %s".formatted(stateId, reason.wireName(), role, validation, classes,
                detail);
    }
}
