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
import java.util.regex.Pattern;

import de.planmarshall.core.role.EffortValue;
import de.planmarshall.core.role.RoleAssignment;

/**
 * What one level names of a role in a role assignment store (Store Schemas § Role Assignments, Model Work § Role
 * Assignment): its harness, its model, the effort and the optional escalation. The harness and the model are required;
 * the effort is {@link EffortSetting#INHERIT absent}, {@link EffortSetting#DEFAULT named as null}, or a
 * {@link EffortSetting#VALUE value} the harness accepts for the model.
 *
 * @param harness    the client id of an enrolled, worker-eligible harness
 * @param model      the model, allowed on that harness by the effective catalogue
 * @param effort     what the level names of the effort
 * @param effortValue the effort value, present exactly for {@link EffortSetting#VALUE}
 * @param escalation  the optional stronger configuration of the role
 * @since 0.1
 */
public record RoleAssignmentValue(String harness, String model, EffortSetting effort,
Optional<EffortValue> effortValue, Optional<Escalation> escalation) {

    private static final Pattern HARNESS = Pattern.compile("[a-z][a-z0-9-]{1,31}");

    /**
     * @throws IllegalArgumentException if the harness or the model is empty, the harness is not a client id, or the
     *                                  effort setting and its value do not belong together
     */
    public RoleAssignmentValue {
        Objects.requireNonNull(harness, "harness");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(effort, "effort");
        Objects.requireNonNull(effortValue, "effortValue");
        Objects.requireNonNull(escalation, "escalation");
        if (!HARNESS.matcher(harness).matches()) {
            throw new IllegalArgumentException("Not a client id of a role assignment: " + harness);
        }
        if (model.isEmpty() || model.length() > 128) {
            throw new IllegalArgumentException("A role assignment names a model of 1 to 128 characters: " + model);
        }
        if ((effort == EffortSetting.VALUE) != effortValue.isPresent()) {
            throw new IllegalArgumentException(
                    "The effort setting '%s' and its value do not belong together: %s".formatted(effort, effortValue));
        }
    }

    /**
     * @param harness the harness
     * @param model   the model
     * @param effort  the effort value; {@code null} for the profile's default effort
     * @return the assignment the onboarding writes
     */
    public static RoleAssignmentValue of(String harness, String model, String effort) {
        var value = effort == null ? Optional.<EffortValue>empty() : Optional.of(new EffortValue(effort));
        return new RoleAssignmentValue(harness, model, effort == null ? EffortSetting.DEFAULT : EffortSetting.VALUE,
                value, Optional.empty());
    }

    /**
     * @param harness the harness
     * @param model   the model
     * @return the assignment that keeps the effort of the lower level
     */
    public static RoleAssignmentValue inheritEffort(String harness, String model) {
        return new RoleAssignmentValue(harness, model, EffortSetting.INHERIT, Optional.empty(), Optional.empty());
    }

    /**
     * @return the value as the role package reads an assignment
     */
    public RoleAssignment assignment() {
        return new RoleAssignment(Optional.of(harness), Optional.of(model), effortValue);
    }

    /**
     * @param escalation the stronger configuration of the role
     * @return this assignment with the escalation
     */
    public RoleAssignmentValue withEscalation(Escalation escalation) {
        return new RoleAssignmentValue(harness, model, effort, effortValue, Optional.of(escalation));
    }
}
