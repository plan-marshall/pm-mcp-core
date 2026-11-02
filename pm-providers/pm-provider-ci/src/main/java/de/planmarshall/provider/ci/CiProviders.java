/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.ci;

import de.planmarshall.core.findings.FindingSourceKey;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The CI providers of a runtime, each found by its id (PM-IMPL-1). The members are the ones passed to the
 * constructor; nothing is looked up on the classpath. The registry is immutable after construction. An id that is not
 * registered is answered as empty, which is not a {@link de.planmarshall.provider.http.Result.Outcome}.
 *
 * @since 0.1
 */
public final class CiProviders {

    private final Map<String, CiProvider> providers;

    /**
     * @param providers the CI providers, at most one per id
     * @throws NullPointerException     if the collection, a provider or a provider's id is {@code null}
     * @throws IllegalArgumentException if an id does not match the provider id pattern or two providers share an id
     */
    public CiProviders(Collection<? extends CiProvider> providers) {
        Objects.requireNonNull(providers, "providers");
        var byId = new LinkedHashMap<String, CiProvider>();
        for (CiProvider provider : providers) {
            Objects.requireNonNull(provider, "provider");
            var id = Objects.requireNonNull(provider.id(), "provider id");
            if (!FindingSourceKey.isProviderId(id)) {
                throw new IllegalArgumentException("the provider id '%s' does not match the provider id pattern"
                                        .formatted(id));
            }
            if (byId.putIfAbsent(id, provider) != null) {
                throw new IllegalArgumentException("a second provider is registered under the id '%s'".formatted(id));
            }
        }
        this.providers = Collections.unmodifiableMap(byId);
    }

    /**
     * @param id the id of a CI provider
     * @return the provider registered under the id; empty if none is
     */
    public Optional<CiProvider> find(String id) {
        return Optional.ofNullable(providers.get(id));
    }

    /**
     * Selects the provider of a resolved URL's forge.
     *
     * @param url the resolved source URL
     * @return the provider of {@link ForgeUrl#forge()}; empty if none is registered for that forge
     */
    public Optional<CiProvider> select(ForgeUrl url) {
        return find(url.forge());
    }

    /**
     * @return the registered ids, in the order the providers were passed
     */
    public Set<String> ids() {
        return providers.keySet();
    }
}
