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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link LandingVariantValidation} checks a landing variant where a project is known (Workflow DSL § Plan Behaviour
 * Axes, item 3): a pull request needs a provider of the project, the base branch path is refused on a protected branch,
 * and the files path fits every project. The variant ids are the specified closed list of the landing axis.
 */
@DisplayName("Landing variant validation")
class LandingVariantValidationTest {

    private static LandingVariantValidation.Project project(boolean provider, boolean protectedBranch) {
        return new LandingVariantValidation.Project(provider, protectedBranch);
    }

    @Test
    @DisplayName("a pull request without a provider is refused with its reason")
    void pullRequestWithoutProvider() {
        var verdict = LandingVariantValidation.validate("landing.pull-request", project(false, false));

        assertAll(
                () -> assertFalse(verdict.valid(), "A pull request without a provider must be refused"),
                () -> assertTrue(verdict.reason().contains("needs a provider"),
                        "The reason names the missing provider"),
                () -> assertTrue(LandingVariantValidation.validate("landing.pull-request",
                        project(true, true)).valid(), "A pull request with a provider fits"));
    }

    @Test
    @DisplayName("the base branch path is refused on a protected branch")
    void mainOnProtectedBranch() {
        var verdict = LandingVariantValidation.validate("landing.main", project(false, true));

        assertAll(
                () -> assertFalse(verdict.valid(), "A protected base branch must refuse landing.main"),
                () -> assertTrue(verdict.reason().contains("protected"), "The reason names the protection"),
                () -> assertTrue(LandingVariantValidation.validate("landing.main", project(false, false)).valid(),
                        "landing.main fits an unprotected base branch"));
    }

    @Test
    @DisplayName("the files path fits every project, the provider irrelevant")
    void filesAlwaysFits() {
        assertAll(
                () -> assertTrue(LandingVariantValidation.validate("landing.files", project(false, true)).valid(),
                        "landing.files fits without a provider"),
                () -> assertTrue(LandingVariantValidation.validate("landing.files", project(true, false)).valid(),
                        "landing.files fits with a provider"));
    }

    @Test
    @DisplayName("a variant not of the landing axis is refused, and a fitting one has no reason")
    void inventory() {
        var unknown = LandingVariantValidation.validate("method.test-first", project(true, false));
        var fits = LandingVariantValidation.validate("landing.pull-request", project(true, false));

        assertAll(
                () -> assertFalse(unknown.valid(), "A variant off the landing axis is refused"),
                () -> assertTrue(unknown.reason().contains("not a landing variant"), "The reason names the defect"),
                () -> assertTrue(fits.valid(), "A fitting variant is valid"),
                () -> assertNull(fits.reason(), "A fitting variant carries no reason"));
    }
}
