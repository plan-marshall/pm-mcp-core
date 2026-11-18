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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.role.EffectiveRole;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleResolution;
import de.planmarshall.core.role.RoleResolver;

/**
 * The model identity of two listed roles of one quorum declaration (PM-WORK-1, Model Work § Model Identity; Workflow DSL
 * § Static AST Verification Rules, Rule 13).
 * <p>
 * One model reaches a harness under several identifiers: a router's prefixes ({@code openrouter/deepseek/…}), a route
 * variant after a colon ({@code :free}), another case. Two roles name the same model when the weights identities their
 * enrolments record are equal or, where either enrolment records none, when their identifiers are equal after
 * removing everything up to the last {@code /}, removing everything from the first {@code :} on, and lowering the case.
 * The comparison errs towards refusing: {@code qwen3:32b} and {@code qwen3:8b} count as one model, because a quorum of
 * them shares the blind spots the quorum exists to avoid.
 *
 * @since 0.1
 */
public final class ModelIdentity {

    private ModelIdentity() {
    }

    /**
     * @param first  the first listed role
     * @param second the second listed role
     * @return the identity both name, empty when they name different models
     */
    public static Optional<String> shared(EffectiveRole first, EffectiveRole second) {
        var firstWeights = first.weightsIdentity();
        var secondWeights = second.weightsIdentity();
        if (firstWeights != null && secondWeights != null) {
            return firstWeights.equals(secondWeights) ? Optional.of(firstWeights) : Optional.empty();
        }
        var firstStem = identifierStem(first.model());
        var secondStem = identifierStem(second.model());
        return firstStem.equals(secondStem) ? Optional.of(firstStem) : Optional.empty();
    }

    /**
     * @param model a model identifier
     * @return the identifier without its router prefixes and its route variant, lower-cased
     */
    public static String identifierStem(String model) {
        Objects.requireNonNull(model, "model");
        var lastSlash = model.lastIndexOf('/');
        var withoutPrefix = lastSlash < 0 ? model : model.substring(lastSlash + 1);
        var colon = withoutPrefix.indexOf(':');
        var withoutVariant = colon < 0 ? withoutPrefix : withoutPrefix.substring(0, colon);
        return withoutVariant.toLowerCase(Locale.ROOT);
    }

    /**
     * Resolves the listed roles of every quorum declaration and refuses the first pair that names one model.
     *
     * @param resolver     the resolver of the plan's effective role set
     * @param declarations the declarations by decision kind
     * @param site         the unit or state the declarations belong to, named by the refusal
     * @throws CompositionRefusal {@code quorum_invalid} for a listed role that is undefined or not {@code read_only},
     *                             {@code quorum_not_diverse} for two listed roles of one declaration that name one
     *                             model
     */
    public static void check(RoleResolver resolver, List<ListedQuorum> declarations, String site) {
        for (var declaration : declarations) {
            var roles = new ArrayList<EffectiveRole>();
            for (var roleName : declaration.roles()) {
                var resolution = resolver.resolve(roleName);
                if (resolution instanceof RoleResolution.Undefined undefined) {
                    throw CompositionRefusal.quorumInvalid(roleName, site, null,
                            "the role is not defined by the effective role set: " + undefined.detail());
                }
                var role = ((RoleResolution.Defined) resolution).role();
                if (role.roleClass() != RoleClass.READ_ONLY) {
                    throw CompositionRefusal.quorumInvalid(roleName, site, role.roleClass(),
                            "a quorum lists only read_only roles");
                }
                roles.add(role);
            }
            for (var first = 0; first < roles.size(); first++) {
                for (var second = first + 1; second < roles.size(); second++) {
                    var firstRole = roles.get(first);
                    var secondRole = roles.get(second);
                    var identity = shared(firstRole, secondRole);
                    if (identity.isPresent()) {
                        throw CompositionRefusal.quorumNotDiverse(declaration.roles().get(first), firstRole.model(),
                                declaration.roles().get(second), secondRole.model(), identity.get(), site);
                    }
                }
            }
        }
    }

    /**
     * One quorum declaration of a composed unit.
     *
     * @param decisionKind the decision kind the declaration is for
     * @param roles        the listed roles, in source order
     * @since 0.1
     */
    public record ListedQuorum(String decisionKind, List<String> roles) {

        /**
         * @throws NullPointerException if a component is {@code null}
         */
        public ListedQuorum {
            Objects.requireNonNull(decisionKind, "decisionKind");
            roles = List.copyOf(roles);
        }
    }
}
