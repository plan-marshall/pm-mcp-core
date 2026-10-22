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
import java.util.regex.Pattern;

/**
 * Resolves a named role to its effective configuration at one read (PM-WORK-1, Role Assignment and Validation). The
 * catalog holds the three role sets and the port through which everything outside an artifact is read; every call loads
 * the sets afresh and reads the assignments, the catalogues and the skills again, so an edited artifact, a changed
 * assignment or a new plan override applies to the next composition, task creation and offer.
 * <p>
 * A role whose artifact is absent or invalid, or whose effective values fail validation, resolves to
 * {@link RoleResolution.Undefined} with the reason of the table where one exists. The steward is resolved from the
 * machine configuration and the default assignment instead of its artifact ({@link #resolveSteward()}).
 *
 * @since 0.1
 */
public final class RoleCatalog {

    /** The shipped role of the machine's advisor. */
    public static final String STEWARD = "steward";

    /** The shipped role that scores a rubric item of the role grading. */
    public static final String JUDGE = "judge";

    private static final Pattern VERSION_PART = Pattern.compile("[0-9]+");

    private final RoleSets sets;
    private final RoleEnvironment environment;

    /**
     * @param sets        the three role sets
     * @param environment the port through which everything outside an artifact is read
     */
    public RoleCatalog(RoleSets sets, RoleEnvironment environment) {
        this.sets = sets;
        this.environment = environment;
    }

    /**
     * @param planOverrides the plan's own values of the roles, from its {@code overrides.json}
     * @return the resolver a composition of that plan uses
     */
    public RoleResolver forPlan(RoleAssignments planOverrides) {
        return role -> resolve(role, planOverrides.forRole(role).orElse(RoleAssignment.none()));
    }

    /**
     * @param roleName     a role name
     * @param planOverride the plan's override of the role, which wins over every store
     * @return the effective configuration, or why the role is undefined
     */
    public RoleResolution resolve(String roleName, RoleAssignment planOverride) {
        var set = RoleSetLoader.load(sets);
        var artifact = set.effective(roleName);
        if (artifact.isEmpty()) {
            return absent(roleName, set);
        }
        var effective = planOverride.over(assigned(roleName).over(base(artifact.get())));
        var harness = effective.harness().orElseThrow();
        var model = effective.model().orElseThrow();
        var effort = effective.effort().orElse(artifact.get().effort());
        return effective(artifact.get(), harness, model, effort, false);
    }

    /**
     * Resolves the machine's advisor (PM-WORK-1, Steward Role): its harness from {@code harnesses.default} of
     * {@code machine-config.json}, its model from the tier {@code standard} of that harness's profile, and the effort of
     * its default assignment; its artifact's {@code harness} and {@code model} are not read, and
     * {@code model_not_allowed} never applies.
     *
     * @return the steward's effective configuration, or why it is undefined
     */
    public RoleResolution resolveSteward() {
        var set = RoleSetLoader.load(sets);
        var artifact = set.effective(STEWARD);
        if (artifact.isEmpty()) {
            return absent(STEWARD, set);
        }
        var configuration = StewardConfiguration.resolve(environment);
        if (configuration.isEmpty()) {
            return new RoleResolution.Undefined(STEWARD, Optional.of(RoleValidationReason.HARNESS_UNKNOWN),
                    "the machine's default harness or its tier 'standard' is not configured");
        }
        var steward = configuration.get();
        return effective(artifact.get(), steward.harness(), steward.model(), steward.effort(), true);
    }

    private RoleResolution effective(RoleArtifact artifact, String harness, String model, EffortValue effort,
            boolean steward) {
        var profile = environment.profile(harness);
        var enrolled = environment.enrolledHarness(harness);
        var catalogue = environment.projectCatalogue().or(environment::machineCatalogue);
        try {
            var skills = RoleValidation.check(artifact, harness, model, effort, profile, enrolled, catalogue,
                    environment, steward);
            var facts = profile.orElseThrow();
            var version = majorMinor(enrolled.orElseThrow().version());
            var weights = environment.weightsIdentity(harness, model);
            return new RoleResolution.Defined(EffectiveRole.of(artifact, harness, model, effort, skills, version,
                    weights, facts));
        } catch (RoleRefusal refusal) {
            return new RoleResolution.Undefined(artifact.name(), Optional.of(refusal.reason()), refusal.detail());
        }
    }

    private RoleAssignment assigned(String roleName) {
        return environment.projectAssignments().flatMap(assignments -> assignments.forRole(roleName))
                .orElse(RoleAssignment.none())
                .over(environment.machineAssignments().flatMap(assignments -> assignments.forRole(roleName))
                        .orElse(RoleAssignment.none()));
    }

    private static RoleAssignment base(RoleArtifact artifact) {
        return new RoleAssignment(Optional.of(artifact.harness()), Optional.of(artifact.model()),
                Optional.of(artifact.effort()));
    }

    private static RoleResolution absent(String roleName, RoleSet set) {
        return set.refusal(roleName)
                .<RoleResolution>map(refusal -> new RoleResolution.Undefined(roleName,
                        Optional.of(refusal.refusal().reason()), refusal.refusal().detail()))
                .orElseGet(() -> new RoleResolution.Undefined(roleName, Optional.empty(),
                        "no role set defines the role"));
    }

    /**
     * @param version the exact version an enrolled harness reported
     * @return its major.minor, which binds a qualification record and both role digests
     */
    public static String majorMinor(String version) {
        var dots = version.split("\\.", -1);
        var major = dots.length > 0 && VERSION_PART.matcher(dots[0]).matches() ? dots[0] : "0";
        var minor = dots.length > 1 && VERSION_PART.matcher(dots[1]).matches() ? dots[1] : "0";
        return major + "." + minor;
    }
}
