/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.onboarding;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * One aspect of the machine's onboarding, in the fixed order of the guided and the automatic run (State Catalogue §
 * Machine Onboarding; {@code language}, {@code harness}, {@code catalogue}, {@code roles}, {@code store}, {@code
 * providers}, {@code defaults}): the first run and the automatic run take them in this order, and a later run calls
 * exactly one of them with {@code onboard_run: reconfigure}. The value is the aspect unit's own prefix (Workflow DSL §
 * Machine Scope, item 3), which is also what a {@code reconfigure:<aspect>} link of the settings names.
 *
 * @since 0.1
 */
public enum OnboardingAspect {

    /** The interface and model languages, {@code onboard.language}, whose states carry the prefix {@code language.*}. */
    LANGUAGE("language"),
    /** The harness scan, the login probes and the enrolments, {@code onboard.harnesses}, prefix {@code harness.*}. */
    HARNESSES("harness"),
    /** The model catalogue, {@code onboard.models}, prefix {@code catalogue.*}. */
    MODELS("catalogue"),
    /** The default role assignment and the steward role, {@code onboard.assign-roles}, prefix {@code roles.*}. */
    ROLE_ASSIGNMENT("roles"),
    /** The credential store as the operator's choice, {@code onboard.credential-store}, prefix {@code store.*}. */
    CREDENTIAL_STORE("store"),
    /** The machine's provider defaults and their verification, {@code onboard.providers}, prefix {@code providers.*}. */
    PROVIDERS("providers"),
    /** The machine's plan behaviour defaults, {@code onboard.plan-defaults}, prefix {@code defaults.*}. */
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
     * @param wireName the name of an aspect, as a link's {@code reconfigure:<aspect>} carries it
     * @return the aspect of that name; empty for a name no aspect has
     */
    public static Optional<OnboardingAspect> of(String wireName) {
        Objects.requireNonNull(wireName, "wireName");
        return Arrays.stream(values()).filter(aspect -> aspect.wireName.equals(wireName)).findFirst();
    }
}
