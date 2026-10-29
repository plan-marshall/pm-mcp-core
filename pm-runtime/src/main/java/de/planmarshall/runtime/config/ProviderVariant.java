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
 * The integration variant of a provider (Store Schemas § Machine Provider Defaults, Credentials &amp; Enrolment §
 * Two-Lane Provider Architecture): the native variant runs in process with a Token Lane credential, the CLI variant runs
 * the operator's enrolled tool with the operator's ambient authentication.
 *
 * @since 0.1
 */
public enum ProviderVariant {

    /** The variant in process: REST and git over HTTPS with the credential of the entry. */
    NATIVE("native"),
    /** The CLI variant: the operator's enrolled tool, used only by the providers that have one. */
    CLI("cli");

    private final String wireName;

    ProviderVariant(String wireName) {
        this.wireName = wireName;
    }

    /** @return the value of {@code variant} */
    public String wireName() {
        return wireName;
    }

    /**
     * @param wireName a value of {@code variant}
     * @return the variant, or empty for a value outside the closed list
     */
    public static Optional<ProviderVariant> of(String wireName) {
        return Arrays.stream(values()).filter(variant -> variant.wireName.equals(wireName)).findFirst();
    }
}
