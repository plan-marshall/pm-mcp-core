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

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The machine's provider defaults, {@code providers} of {@code machine-config.json} (Store Schemas § Machine Provider
 * Defaults): per provider whether it is available, the integration variant a project that sets none takes, and the
 * credential key it uses where a project names none. A provider the machine does not serve is absent, not recorded as
 * unavailable.
 *
 * @param providers the defaults per provider
 * @since 0.1
 */
public record ProviderDefaults(Map<Provider, ProviderDefault> providers) {

    /** Copies the defaults and checks each variant against its provider. */
    public ProviderDefaults {
        var copy = new EnumMap<Provider, ProviderDefault>(Provider.class);
        Objects.requireNonNull(providers, "providers").forEach((provider, value) -> {
            Objects.requireNonNull(provider, "provider");
            Objects.requireNonNull(value, "default");
            value.requireVariantOf(provider);
            copy.put(provider, value);
        });
        providers = Collections.unmodifiableMap(copy);
    }

    /** @return the defaults of a machine that serves no provider */
    public static ProviderDefaults none() {
        return new ProviderDefaults(Map.of());
    }

    /**
     * @param provider a provider
     * @return the default of the provider, or empty when the machine does not serve it
     */
    public Optional<ProviderDefault> of(Provider provider) {
        return Optional.ofNullable(providers.get(provider));
    }

    /**
     * @param provider a provider
     * @return whether the provider is recorded available, so it may serve a project
     */
    public boolean available(Provider provider) {
        return of(provider).filter(ProviderDefault::available).isPresent();
    }
}
