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

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import de.planmarshall.core.profile.EnrolledHarness;
import de.planmarshall.core.role.EffortValue;
import de.planmarshall.core.role.HarnessProfile;
import de.planmarshall.core.role.ModelCatalogue;
import de.planmarshall.core.role.RoleArtifact;
import de.planmarshall.core.role.RoleRefusal;
import de.planmarshall.core.role.RoleValidationReason;

/**
 * Validates the values {@code roles.write} writes as the role's effective configuration (Model Work § Role Assignment,
 * State Catalogue § Role Assignment, {@code roles.validate}): the harness is enrolled and worker-eligible, the profile
 * runs the role's class, the model is allowed by the effective catalogue ({@code model_not_allowed} otherwise) and the
 * effort is one the harness accepts for the model. A value that fails is refused with the reason of the validation
 * table, and nothing is written.
 * <p>
 * The checks are the ones the state names; the artifact's own rules (tools, form, skills) belong to the artifact and are
 * validated when it is read, not when an assignment is written. The role's own values and its escalation are each
 * validated against <em>their own</em> harness: the escalation may name another harness than the role, and its profile
 * and enrolment are looked up by its own client id.
 *
 * @since 0.1
 */
public final class RoleAssignmentValidation {

    private RoleAssignmentValidation() {
    }

    /**
     * The lookups every value is validated against.
     *
     * @param profiles   the facts of a harness's profile by client id
     * @param enrolments the enrolled build of a harness by client id
     * @param catalogue  the effective model catalogue
     * @since 0.1
     */
    public record Facts(Function<String, Optional<HarnessProfile>> profiles,
            Function<String, Optional<EnrolledHarness>> enrolments, Optional<ModelCatalogue> catalogue) {

        /** Rejects {@code null} components. */
        public Facts {
            Objects.requireNonNull(profiles, "profiles");
            Objects.requireNonNull(enrolments, "enrolments");
            Objects.requireNonNull(catalogue, "catalogue");
        }
    }

    /**
     * @param role     the role the assignment is written for
     * @param value    the assignment
     * @param artifact the role's effective artifact, which names the class and the model's requirements
     * @param facts    the profile, enrolment and catalogue lookups
     * @throws RoleRefusal with the reason of the failing row of the validation table
     */
    public static void require(String role, RoleAssignmentValue value, RoleArtifact artifact, Facts facts) {
        Objects.requireNonNull(facts, "facts");
        check(role, value.harness(), value.model(), value.effortValue(), artifact, facts);
        value.escalation().ifPresent(escalation -> check(role, escalation.harness(), escalation.model(),
                escalation.effort(), artifact, facts));
    }

    private static void check(String role, String harness, String model, Optional<EffortValue> effort,
            RoleArtifact artifact, Facts facts) {
        var profile = facts.profiles();
        var enrolments = facts.enrolments();
        var catalogue = facts.catalogue();
        var harnessFacts = profile.apply(harness).orElseThrow(() -> invalid(RoleValidationReason.HARNESS_UNKNOWN,
                role, artifact, "no client profile has the client id '%s'".formatted(harness)));
        if (enrolments.apply(harness).isEmpty()) {
            throw invalid(RoleValidationReason.HARNESS_UNKNOWN, role, artifact,
                    "the harness '%s' is not enrolled".formatted(harness));
        }
        if (!harnessFacts.eligible()) {
            throw invalid(RoleValidationReason.HARNESS_NOT_ELIGIBLE, role, artifact,
                    "the profile of '%s' is not worker-eligible".formatted(harness));
        }
        if (!harnessFacts.runs(artifact.roleClass())) {
            throw invalid(RoleValidationReason.HARNESS_CLASS_NOT_RUNNABLE, role, artifact,
                    "the profile of '%s' runs no %s role".formatted(harness, artifact.roleClass().wireName()));
        }
        if (!effortSupported(harnessFacts, model, effort)) {
            throw invalid(RoleValidationReason.EFFORT_UNSUPPORTED, role, artifact,
                    "the harness '%s' does not accept the effort %s for '%s'".formatted(harness,
                            effort.map(EffortValue::value).orElse("null"), model));
        }
        var allowed = catalogue.orElseThrow(() -> invalid(RoleValidationReason.MODEL_NOT_ALLOWED, role, artifact,
                "no effective model catalogue exists"));
        if (!allowed.allows(harness, model)) {
            throw invalid(RoleValidationReason.MODEL_NOT_ALLOWED, role, artifact,
                    "the effective model catalogue does not allow '%s' on '%s'".formatted(model, harness));
        }
    }

    /**
     * The effort is supported when the harness lists it, when it is the absence of an effort flag, or when it is above
     * the lowest value the harness lists — the same rule the role validation applies to an artifact's own effort.
     */
    private static boolean effortSupported(HarnessProfile profile, String model, Optional<EffortValue> effort) {
        if (effort.isEmpty() || effort.get().value() == null) {
            return true;
        }
        var values = profile.efforts(model);
        var value = effort.get().value();
        if (values.isEmpty()) {
            return false;
        }
        return values.contains(value) || value.compareTo(values.getFirst()) > 0;
    }

    private static RoleRefusal invalid(RoleValidationReason reason, String role, RoleArtifact artifact, String detail) {
        var pointer = artifact.name().equals(role) ? "" : "/" + role;
        return RoleRefusal.invalid(reason, role, pointer, detail);
    }

    /**
     * @param catalogue the effective catalogue of a project: its own when it has one, else the machine's
     * @param project   the project's catalogue
     * @param machine   the machine's catalogue
     * @return the effective catalogue
     */
    public static Optional<ModelCatalogue> effective(Optional<ModelCatalogue> project,
            Optional<ModelCatalogue> machine) {
        return project.or(() -> machine);
    }
}
