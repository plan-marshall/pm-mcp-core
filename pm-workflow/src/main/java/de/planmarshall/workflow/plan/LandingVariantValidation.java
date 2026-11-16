/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.plan;

import java.util.Objects;

import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

/**
 * The check of a landing variant where a project is known (Workflow DSL § Plan Behaviour Axes, item 3): its setup, a
 * plan's start, a plan's settings. It refuses {@code landing.pull-request} where the project has no provider that opens
 * a pull request, and {@code landing.main} where the provider reports the base branch as protected, each with the reason
 * the operator reads.
 * <p>
 * <b>An unknown answer is no answer.</b> A provider lookup that failed is not the same as "not protected": a component
 * of the {@link Project} that a caller could not look up is {@code null}, and a variant that depends on it is refused
 * with a reason naming the failed lookup, so a failed lookup never passes the check. The caller reports {@code null}
 * only for a lookup that failed; a lookup that answered names its answer.
 * <p>
 * The machine's default is never checked here against a repository: only what the machine itself provides is the
 * machine's own check ({@code defaults.validate}, {@code MachineDefaultsValidation} of {@code pm-runtime}). A machine
 * default that does not fit a project is refused for that project, with this check's reason, at its setup or at the
 * plan's start, where the project or the plan chooses another. {@code landing.files} fits every project here; whether a
 * plan may be started from local changes under it is decided where the plan's source is chosen.
 *
 * @since 0.1
 */
@UtilityClass
public class LandingVariantValidation {

    /**
     * What the project provides, as the project's own stores and its provider report it.
     *
     * @param pullRequestProvider whether the project has a provider that opens a pull request; {@code null} where the
     *                            lookup failed
     * @param baseBranchProtected whether the provider reports the project's base branch as protected; {@code null}
     *                            where the lookup failed
     * @since 0.1
     */
    public record Project(@Nullable Boolean pullRequestProvider, @Nullable Boolean baseBranchProtected) {

        /**
         * @param pullRequestProvider whether the project has a provider that opens a pull request
         * @param baseBranchProtected whether the provider reports the base branch as protected
         * @return what the project answered
         */
        public static Project known(boolean pullRequestProvider, boolean baseBranchProtected) {
            return new Project(pullRequestProvider, baseBranchProtected);
        }

        /**
         * @return what a lookup that answered none of the two questions reports
         */
        public static Project unknown() {
            return new Project(null, null);
        }

        /**
         * @return whether both lookups answered
         */
        public boolean answered() {
            return pullRequestProvider != null && baseBranchProtected != null;
        }
    }

    /**
     * One verdict of the check.
     *
     * @param valid  whether the variant fits the project
     * @param reason why it does not; {@code null} when it does
     * @since 0.1
     */
    public record Verdict(boolean valid, @Nullable String reason) {

        /**
         * @return a verdict that the variant fits
         */
        public static Verdict fits() {
            return new Verdict(true, null);
        }

        /**
         * @param reason why the variant does not fit
         * @return a refusing verdict
         */
        public static Verdict refused(String reason) {
            return new Verdict(false, Objects.requireNonNull(reason, "reason"));
        }
    }

    /**
     * Checks a landing variant against the project.
     *
     * @param variant the landing variant in force for the project or the plan
     * @param project what the project provides
     * @return the verdict; a pull request without a provider, the base branch under a protected branch, and a variant
     *         that needs a lookup that failed are refused
     * @throws NullPointerException if an argument is {@code null}
     */
    public static Verdict validate(String variant, Project project) {
        Objects.requireNonNull(variant, "variant");
        Objects.requireNonNull(project, "project");
        if (!PlanFacts.Axis.LANDING.variants().contains(variant)) {
            return Verdict.refused("not a landing variant: " + variant);
        }
        if ("landing.pull-request".equals(variant)) {
            if (project.pullRequestProvider() == null) {
                return Verdict.refused("landing.pull-request needs the project's provider lookup, which failed");
            }
            if (!project.pullRequestProvider()) {
                return Verdict.refused("landing.pull-request needs a provider of the project that opens a pull request");
            }
        }
        if ("landing.main".equals(variant)) {
            if (project.baseBranchProtected() == null) {
                return Verdict.refused("landing.main needs the base branch's protection, whose lookup failed");
            }
            if (project.baseBranchProtected()) {
                return Verdict.refused("landing.main cannot land on the base branch the provider reports as protected");
            }
        }
        return Verdict.fits();
    }
}
