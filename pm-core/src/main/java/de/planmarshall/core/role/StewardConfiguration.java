/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role;

import java.util.Optional;

/**
 * The configuration of the machine's advisor (PM-WORK-1, Steward Role), read from the machine instead of its artifact.
 * <p>
 * Its harness is {@code harnesses.default} of {@code machine-config.json}; its model is the model that harness's profile
 * maps the tier {@code standard} to ({@code headless.model_tiers}); its effort is the effort of the steward's default
 * assignment. The artifact's {@code harness} and {@code model} are not read, and no assignment or override names the
 * steward.
 *
 * @param harness the machine's default harness
 * @param model   the model of the tier {@code standard} on that harness
 * @param effort  the effort of the steward's default assignment
 * @since 0.1
 */
public record StewardConfiguration(String harness, String model, EffortValue effort) {

    /** The tier of the closed list the steward runs on. */
    public static final String TIER = "standard";

    /**
     * @param environment the port through which the machine configuration, the profile and the default assignment are read
     * @return the steward's configuration, or empty when the machine has no default harness, no profile of it, or no
     *         model for the tier
     */
    public static Optional<StewardConfiguration> resolve(RoleEnvironment environment) {
        var harness = environment.machineConfiguration().flatMap(MachineConfiguration::defaultHarness);
        if (harness.isEmpty()) {
            return Optional.empty();
        }
        var profile = environment.profile(harness.get());
        if (profile.isEmpty()) {
            return Optional.empty();
        }
        var model = profile.get().tierModel(TIER);
        if (model.isEmpty()) {
            return Optional.empty();
        }
        var effort = environment.defaultAssignment()
                .flatMap(assignment -> assignment.forRole(RoleCatalog.STEWARD))
                .map(DefaultAssignment.DefaultRole::effort)
                .orElse(null);
        return Optional.of(new StewardConfiguration(harness.get(), model.get(), new EffortValue(effort)));
    }
}
