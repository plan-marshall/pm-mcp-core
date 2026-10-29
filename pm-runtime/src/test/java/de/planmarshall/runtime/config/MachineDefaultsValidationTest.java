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

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The machine's own check of its plan defaults (State Catalogue § Plan Defaults): a pull request needs a provider the
 * machine serves, the other landing variants need none, and no repository is consulted.
 */
@DisplayName("Machine defaults validation")
class MachineDefaultsValidationTest {

    private static ProviderDefaults providers(boolean hosting) {
        return new ProviderDefaults(Map.of(Provider.GITHUB, new ProviderDefault(hosting,
                Optional.of(ProviderVariant.NATIVE), Optional.of("github"))));
    }

    @Test
    @DisplayName("a pull request needs a git hosting provider the machine serves")
    void pullRequest() {
        var without = MachineDefaultsValidation.landing(MachineDefaultsValidation.LANDING_PULL_REQUEST,
                ProviderDefaults.none());
        var with = MachineDefaultsValidation.landing(MachineDefaultsValidation.LANDING_PULL_REQUEST,
                providers(true));

        assertAll(
                () -> assertFalse(without.valid()),
                () -> assertTrue(without.reason().orElseThrow().contains("no provider")),
                () -> assertEquals("defaults.validation.landing.pull-request.no-provider",
                        without.messageKey()),
                () -> assertTrue(with.valid()),
                () -> assertTrue(with.reason().isEmpty()));
    }

    @Test
    @DisplayName("a provider the machine records as not available does not serve the pull request")
    void unavailableProvider() {
        assertFalse(MachineDefaultsValidation.landing(MachineDefaultsValidation.LANDING_PULL_REQUEST,
                providers(false)).valid());
    }

    @Test
    @DisplayName("the git transport alone does not open a pull request")
    void gitTransportIsNoForge() {
        var gitOnly = new ProviderDefaults(Map.of(Provider.GIT, new ProviderDefault(true,
                Optional.of(ProviderVariant.NATIVE), Optional.empty())));

        assertFalse(MachineDefaultsValidation.landing(MachineDefaultsValidation.LANDING_PULL_REQUEST,
                gitOnly).valid());
    }

    @Test
    @DisplayName("landing.main and landing.files need no provider, and an unknown variant is refused")
    void otherVariants() {
        assertAll(
                () -> assertTrue(MachineDefaultsValidation.landing(MachineDefaultsValidation.LANDING_MAIN,
                        ProviderDefaults.none()).valid()),
                () -> assertTrue(MachineDefaultsValidation.landing(MachineDefaultsValidation.LANDING_FILES,
                        ProviderDefaults.none()).valid()),
                () -> assertFalse(MachineDefaultsValidation.landing("landing.teleport",
                        ProviderDefaults.none()).valid()),
                () -> assertEquals("defaults.validation.unknown",
                        MachineDefaultsValidation.landing("landing.teleport", ProviderDefaults.none()).messageKey()));
    }
}
