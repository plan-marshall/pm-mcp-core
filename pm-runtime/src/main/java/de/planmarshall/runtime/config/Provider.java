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

import java.util.Arrays;
import java.util.Optional;

/**
 * The providers the machine can serve (Store Schemas § Machine Provider Defaults): the closed list of
 * {@code providers} of {@code machine-config.json}.
 * <p>
 * The package lies in the runtime, so the closed list may name the providers; {@code pm-core} holds no such name, as its
 * architecture guard requires.
 *
 * @since 0.1
 */
public enum Provider {

    /** The git transport of a project. */
    GIT("git", true),
    /** GitHub as the CI provider and the host of the repository. */
    GITHUB("github", true),
    /** GitLab as the CI provider and the host of the repository. */
    GITLAB("gitlab", true),
    /** A Codeberg or other Forgejo forge. */
    FORGEJO("forgejo", false),
    /** SonarQube or SonarCloud. */
    SONAR("sonar", false);

    private final String wireName;
    private final boolean cliVariant;

    Provider(String wireName, boolean cliVariant) {
        this.wireName = wireName;
        this.cliVariant = cliVariant;
    }

    /** @return the key of the provider in {@code providers} */
    public String wireName() {
        return wireName;
    }

    /** @return whether the provider has a CLI variant, so {@code cli} is a variant it may record */
    public boolean hasCliVariant() {
        return cliVariant;
    }

    /**
     * @param wireName a key of {@code providers}
     * @return the provider, or empty for a key outside the closed list
     */
    public static Optional<Provider> of(String wireName) {
        return Arrays.stream(values()).filter(provider -> provider.wireName.equals(wireName)).findFirst();
    }
}
