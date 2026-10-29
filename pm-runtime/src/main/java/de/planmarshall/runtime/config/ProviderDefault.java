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

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * One provider of the machine's defaults (Store Schemas § Machine Provider Defaults): whether it is available, the
 * integration variant a project that sets none takes, and the credential key it uses where a project names none.
 * <p>
 * A provider is recorded available only after its verification passed; one whose checks failed is recorded only on the
 * operator's word, and then as not available, and never serves a project in that state. A {@code cli} variant exists
 * only for the providers that have one.
 *
 * @param available     whether the provider's verification passed
 * @param variant       the default integration variant, or empty when the provider's only variant applies
 * @param credentialKey the credential entry the provider uses where a project names none
 * @since 0.1
 */
public record ProviderDefault(boolean available, Optional<ProviderVariant> variant,
Optional<String> credentialKey) {

    private static final Pattern CREDENTIAL_KEY = Pattern.compile("[a-z0-9][a-z0-9-]{1,62}[a-z0-9]");

    /** Rejects {@code null} components and a credential key outside its grammar. */
    public ProviderDefault {
        Objects.requireNonNull(variant, "variant");
        Objects.requireNonNull(credentialKey, "credentialKey");
        credentialKey.ifPresent(key -> {
            if (!CREDENTIAL_KEY.matcher(key).matches()) {
                throw new IllegalArgumentException("Not a valid name of a credential key: " + key);
            }
        });
    }

    /**
     * @param available whether the provider's verification passed
     * @param variant   the default integration variant
     * @return the default without a credential key
     */
    public static ProviderDefault of(boolean available, ProviderVariant variant) {
        return new ProviderDefault(available, Optional.of(variant), Optional.empty());
    }

    /**
     * @param provider the provider the default belongs to
     * @throws IllegalArgumentException if a {@code cli} variant is named for a provider without one
     */
    public void requireVariantOf(Provider provider) {
        if (variant.isPresent() && variant.get() == ProviderVariant.CLI && !provider.hasCliVariant()) {
            throw new IllegalArgumentException(
                    "The provider '%s' has no cli variant".formatted(provider.wireName()));
        }
    }
}
