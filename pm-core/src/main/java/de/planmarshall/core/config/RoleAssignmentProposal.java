/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.role.EffortValue;
import de.planmarshall.core.role.HarnessProfile;
import de.planmarshall.core.role.ModelCatalogue;
import de.planmarshall.core.role.RoleArtifact;
import de.planmarshall.core.role.RoleSet;

/**
 * The proposal {@code roles.default} shows (State Catalogue § Role Assignment, Model Work § Default Role Assignment): the
 * release's default assignment applied to the enrolled harnesses and the effective catalogue.
 * <p>
 * Per assignable role — every role of the effective set except the steward — the harness is the role's preferred harness
 * where it is enrolled, else the first enrolled harness, in the order of the client id, whose profile runs the role's
 * class; the model is the one that harness maps the role's tier to, where the catalogue allows it; and the effort is the
 * default's, narrowed to what the harness accepts for the model. A role whose default cannot be met is reported without a
 * value, with the reason and the advisory beside it, and the operator chooses its model in {@code roles.write}.
 * <p>
 * The proposal writes nothing: {@code roles.write} records what the operator confirmed, and {@link RoleAssignmentValidation}
 * refuses a value that is not the role's effective configuration.
 *
 * @since 0.1
 */
public final class RoleAssignmentProposal {

    private RoleAssignmentProposal() {
    }

    /**
     * One enrolled harness with the facts of its profile.
     *
     * @param clientId the client id of the harness
     * @param profile  the facts of its client profile
     * @since 0.1
     */
    public record Harness(String clientId, HarnessProfile profile) {

        /** Rejects {@code null} components. */
        public Harness {
            Objects.requireNonNull(clientId, "clientId");
            Objects.requireNonNull(profile, "profile");
        }
    }

    /**
     * What the proposal holds for one role.
     *
     * @param role        the role name
     * @param tier        the tier of the role's default
     * @param advisory    the advisory of the role's default
     * @param assignment  the proposed value; empty when the default cannot be met
     * @param unresolved  why the default cannot be met; empty when the value is proposed
     * @since 0.1
     */
    public record Proposal(String role, String tier, String advisory, Optional<RoleAssignmentValue> assignment,
    Optional<String> unresolved) {

        /** Rejects a value and a reason at once, and neither. */
        public Proposal {
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(tier, "tier");
            Objects.requireNonNull(advisory, "advisory");
            Objects.requireNonNull(assignment, "assignment");
            Objects.requireNonNull(unresolved, "unresolved");
            if (assignment.isPresent() == unresolved.isPresent()) {
                throw new IllegalArgumentException("A proposal holds a value or a reason for its absence: " + role);
            }
        }
    }

    /**
     * @param roles      the effective role set of the project or the machine
     * @param defaults   the release's default assignment
     * @param harnesses  the enrolled harnesses, in the order of the client id
     * @param catalogue  the effective model catalogue; empty when the machine has none yet
     * @return one proposal per assignable role, in role-name order
     */
    public static Map<String, Proposal> propose(RoleSet roles, DefaultAssignment defaults, List<Harness> harnesses,
            Optional<ModelCatalogue> catalogue) {
        Objects.requireNonNull(roles, "roles");
        Objects.requireNonNull(defaults, "defaults");
        Objects.requireNonNull(harnesses, "harnesses");
        Objects.requireNonNull(catalogue, "catalogue");
        var proposals = new LinkedHashMap<String, Proposal>();
        roles.assignable().forEach((roleName, artifact) -> proposals.put(roleName,
                propose(roleName, artifact, defaults, harnesses, catalogue)));
        return Collections.unmodifiableMap(proposals);
    }

    private static Proposal propose(String role, RoleArtifact artifact, DefaultAssignment defaults,
            List<Harness> harnesses, Optional<ModelCatalogue> catalogue) {
        var entry = defaults.of(role);
        if (entry.isEmpty()) {
            return unresolved(role, "", "", "the release's default assignment names no such role");
        }
        var defaultRole = entry.get();
        var harness = resolveHarness(artifact, defaultRole, harnesses);
        if (harness.isEmpty()) {
            return unresolved(role, defaultRole, "no enrolled harness runs the role's class");
        }
        var model = harness.get().profile().tierModel(defaultRole.tier());
        if (model.isEmpty()) {
            return unresolved(role, defaultRole, "the harness '%s' maps no model to the tier '%s'"
                    .formatted(harness.get().clientId(), defaultRole.tier()));
        }
        if (catalogue.filter(allowed -> allowed.allows(harness.get().clientId(), model.get())).isEmpty()) {
            return unresolved(role, defaultRole, "the effective model catalogue does not allow '%s' on '%s'"
                    .formatted(model.get(), harness.get().clientId()));
        }
        var effort = narrow(harness.get().profile(), model.get(), defaultRole.effort());
        return new Proposal(role, defaultRole.tier(), defaultRole.advisory(),
                Optional.of(new RoleAssignmentValue(harness.get().clientId(), model.get(),
                        defaultRole.effort().isPresent() && effort.isPresent() ? EffortSetting.VALUE
                                : EffortSetting.DEFAULT,
                        effort, Optional.empty())),
                Optional.empty());
    }

    /**
     * The preferred harness where it is enrolled, else the first enrolled harness whose profile runs the role's class.
     */
    private static Optional<Harness> resolveHarness(RoleArtifact artifact, DefaultAssignment.DefaultRole defaultRole,
            List<Harness> harnesses) {
        var preferred = harnesses.stream()
                .filter(harness -> harness.clientId().equals(defaultRole.preferredHarness()))
                .filter(harness -> harness.profile().runs(artifact.roleClass()))
                .findFirst();
        return preferred.or(() -> harnesses.stream()
                .filter(harness -> harness.profile().runs(artifact.roleClass()))
                .findFirst());
    }

    /**
     * Narrows the default's effort to what the harness accepts for the model: the value itself where the harness lists
     * it, else the greatest listed value below it, else the lowest listed one. A model without effort values runs with no
     * effort flag, which the store writes as {@code null}.
     */
    private static Optional<EffortValue> narrow(HarnessProfile profile, String model, Optional<EffortValue> effort) {
        var accepted = new ArrayList<>(profile.efforts(model));
        if (effort.isEmpty() || effort.get().value() == null || accepted.isEmpty()) {
            return Optional.empty();
        }
        var value = effort.get().value();
        if (accepted.contains(value)) {
            return effort;
        }
        var below = accepted.stream().filter(candidate -> candidate.compareTo(value) < 0)
                .reduce((first, second) -> second);
        return Optional.of(new EffortValue(below.orElse(accepted.getFirst())));
    }

    private static Proposal unresolved(String role, DefaultAssignment.DefaultRole defaultRole, String reason) {
        return unresolved(role, defaultRole.tier(), defaultRole.advisory(), reason);
    }

    private static Proposal unresolved(String role, String tier, String advisory, String reason) {
        return new Proposal(role, tier, advisory, Optional.empty(), Optional.of(reason));
    }
}
