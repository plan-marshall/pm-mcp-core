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

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * One aspect of the machine's onboarding, in the fixed order of the guided and the automatic run (Workflow DSL § Machine
 * Scope, item 3; State Catalogue § Machine Onboarding): the value is the aspect unit's own prefix, {@code language},
 * {@code harness}, {@code catalogue}, {@code roles}, {@code store}, {@code providers} and {@code defaults}, which is also
 * what a {@code reconfigure:<aspect>} link of the machine's settings names.
 * <p>
 * The list lives in this module because two modules need it and neither may depend on the other: `pm-workflow` names an
 * aspect in its automatic run's result and its routing, and `pm-runtime` names it in the settings' links. Both read this
 * enum, so the closed list and its order exist once.
 *
 * @since 0.1
 */
public enum OnboardingAspect {

    /** The interface and model languages, {@code onboard.language}. */
    LANGUAGE("language"),
    /** The harness scan, the login probes and the enrolments, {@code onboard.harnesses}. */
    HARNESSES("harness"),
    /** The model catalogue, {@code onboard.models}. */
    MODELS("catalogue"),
    /** The default role assignment and the steward role, {@code onboard.assign-roles}. */
    ROLE_ASSIGNMENT("roles"),
    /** The credential store as the operator's choice, {@code onboard.credential-store}. */
    CREDENTIAL_STORE("store"),
    /** The machine's provider defaults and their verification, {@code onboard.providers}. */
    PROVIDERS("providers"),
    /** The machine's plan behaviour defaults, {@code onboard.plan-defaults}. */
    PLAN_DEFAULTS("defaults");

    private final String wireName;

    OnboardingAspect(String wireName) {
        this.wireName = wireName;
    }

    /**
     * @return the name of the aspect, the key of its links and of the result's items
     */
    public String wireName() {
        return wireName;
    }

    /**
     * @return the aspect the run takes after this one; empty for the last one
     */
    public Optional<OnboardingAspect> next() {
        var following = ordinal() + 1;
        return following < values().length ? Optional.of(values()[following]) : Optional.empty();
    }

    /**
     * @param wireName the name of an aspect, as a {@code reconfigure:<aspect>} link carries it
     * @return the aspect of that name; empty for a name no aspect has
     */
    public static Optional<OnboardingAspect> of(String wireName) {
        Objects.requireNonNull(wireName, "wireName");
        return Arrays.stream(values()).filter(aspect -> aspect.wireName.equals(wireName)).findFirst();
    }
}
