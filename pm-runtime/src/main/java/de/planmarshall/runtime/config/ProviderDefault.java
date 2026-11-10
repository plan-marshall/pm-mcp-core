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
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * One provider of the machine's defaults (Store Schemas § Machine Provider Defaults): whether it is available, the
 * integration variant a project that sets none takes, the credential key it uses where a project names none, and the
 * last verification result it was recorded with.
 * <p>
 * A provider is recorded available only after its verification passed; one whose checks failed is recorded only on the
 * operator's word, and then as not available, and never serves a project in that state. A {@code cli} variant exists
 * only for the providers that have one, and a provider that has one names it.
 *
 * @param available     whether the provider's verification passed
 * @param variant       the default integration variant, or {@code null} when the provider's only variant applies
 * @param credentialKey the credential entry the provider uses where a project names none; {@code null} when none
 * @param verification  the last verification result, or {@code null} when none was recorded
 * @since 0.1
 */
public record ProviderDefault(boolean available, @Nullable ProviderVariant variant,
@Nullable String credentialKey, @Nullable VerificationResult verification) {

    private static final Pattern CREDENTIAL_KEY = Pattern.compile("[a-z0-9][a-z0-9-]{1,62}[a-z0-9]");

    /** Rejects a credential key outside its grammar and an available provider without a passed result. */
    public ProviderDefault {
        if (credentialKey != null && !CREDENTIAL_KEY.matcher(credentialKey).matches()) {
            throw new IllegalArgumentException("Not a valid name of a credential key: " + credentialKey);
        }
        if (available && (verification == null || !verification.passed())) {
            throw new IllegalArgumentException(
                    "A provider recorded available carries a passed verification result");
        }
    }

    /**
     * @param verification the passed verification result
     * @param variant      the default integration variant
     * @return the default of a provider whose verification passed
     */
    public static ProviderDefault verified(VerificationResult verification, @Nullable ProviderVariant variant) {
        Objects.requireNonNull(verification, "verification");
        return new ProviderDefault(true, variant, null, verification);
    }

    /**
     * @param variant the default integration variant
     * @return the default of a provider recorded not available on the operator's word, without a result
     */
    public static ProviderDefault unavailable(@Nullable ProviderVariant variant) {
        return new ProviderDefault(false, variant, null, null);
    }

    /**
     * @param key the credential entry the provider uses where a project names none
     * @return this default, using the given credential key
     */
    public ProviderDefault withCredentialKey(@Nullable String key) {
        return new ProviderDefault(available, variant, key, verification);
    }

    /**
     * @param provider the provider the default belongs to
     * @throws IllegalArgumentException if a {@code cli} variant is named for a provider without one, or a provider
     *                                  with a {@code cli} variant names no variant
     */
    public void requireVariantOf(Provider provider) {
        if (variant == ProviderVariant.CLI && !provider.hasCliVariant()) {
            throw new IllegalArgumentException(
                    "The provider '%s' has no cli variant".formatted(provider.wireName()));
        }
        if (variant == null && provider.hasCliVariant()) {
            throw new IllegalArgumentException(
                    "The provider '%s' records a cli variant and names none".formatted(provider.wireName()));
        }
    }
}
