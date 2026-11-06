/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.config;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The machine's own check of its plan defaults (State Catalogue § Plan Defaults): a pull request needs a provider the
 * machine serves, the other landing variants need none, and no repository is consulted.
 * <p>
 * The landing variants and the message keys are the specified vocabulary; the credential key and the unknown variant
 * are generated.
 */
@EnableGeneratorController
@DisplayName("Machine defaults validation")
class MachineDefaultsValidationTest {

    /** Contract literal: the message key of a pull request without a serving provider. */
    private static final String NO_PROVIDER_KEY = "defaults.validation.landing.pull-request.no-provider";

    /** Contract literal: the message key of an unknown landing variant. */
    private static final String UNKNOWN_KEY = "defaults.validation.unknown";

    private static String lower() {
        return Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }

    private static ProviderDefaults providers(boolean hosting) {
        return new ProviderDefaults(Map.of(Provider.GITHUB, new ProviderDefault(hosting,
                Optional.of(ProviderVariant.NATIVE), Optional.of(lower() + "-" + lower()))));
    }

    @Test
    @DisplayName("a pull request needs a git hosting provider the machine serves")
    void pullRequest() {
        var without = MachineDefaultsValidation.landing(MachineDefaultsValidation.LANDING_PULL_REQUEST,
                ProviderDefaults.none());
        var with = MachineDefaultsValidation.landing(MachineDefaultsValidation.LANDING_PULL_REQUEST,
                providers(true));

        assertAll(
                () -> assertFalse(without.valid(), "a pull request without a provider is invalid"),
                () -> assertTrue(without.reason().orElseThrow().contains("no provider"),
                        "the reason must name the missing provider"),
                () -> assertEquals(NO_PROVIDER_KEY, without.messageKey(),
                        "the message key must name the missing provider"),
                () -> assertTrue(with.valid(), "a pull request with a provider is valid"),
                () -> assertTrue(with.reason().isEmpty(), "a valid landing carries no reason"));
    }

    @Test
    @DisplayName("a provider the machine records as not available does not serve the pull request")
    void unavailableProvider() {
        assertFalse(MachineDefaultsValidation.landing(MachineDefaultsValidation.LANDING_PULL_REQUEST,
                providers(false)).valid(), "an unavailable provider serves no pull request");
    }

    @Test
    @DisplayName("the git transport alone does not open a pull request")
    void gitTransportIsNoForge() {
        var gitOnly = new ProviderDefaults(Map.of(Provider.GIT, new ProviderDefault(true,
                Optional.of(ProviderVariant.NATIVE), Optional.empty())));

        assertFalse(MachineDefaultsValidation.landing(MachineDefaultsValidation.LANDING_PULL_REQUEST,
                gitOnly).valid(), "the git transport alone serves no pull request");
    }

    @Test
    @DisplayName("landing.main and landing.files need no provider, and an unknown variant is refused")
    void otherVariants() {
        // Longer than every specified variant name, so it names none of them
        var unknown = "landing." + Generators.letterStrings(13, 20).next().toLowerCase(Locale.ROOT);

        assertAll(
                () -> assertTrue(MachineDefaultsValidation.landing(MachineDefaultsValidation.LANDING_MAIN,
                        ProviderDefaults.none()).valid(), "landing.main must need no provider"),
                () -> assertTrue(MachineDefaultsValidation.landing(MachineDefaultsValidation.LANDING_FILES,
                        ProviderDefaults.none()).valid(), "landing.files must need no provider"),
                () -> assertFalse(MachineDefaultsValidation.landing(unknown, ProviderDefaults.none()).valid(),
                        "an unknown landing variant must be refused"),
                () -> assertEquals(UNKNOWN_KEY,
                        MachineDefaultsValidation.landing(unknown, ProviderDefaults.none()).messageKey(),
                        "the message key must name the unknown variant"));
    }
}
