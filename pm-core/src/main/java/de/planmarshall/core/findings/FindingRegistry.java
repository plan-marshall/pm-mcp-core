/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.findings;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * The finding sources and finding responders of a runtime, each found by its {@link FindingSourceKey}
 * (PM-IMPL-1). The members are the ones passed to the constructor; nothing is looked up on the classpath. The
 * registry is immutable after construction.
 *
 * @since 0.1
 */
public final class FindingRegistry {

    private final Map<FindingSourceKey, FindingSource> sources;
    private final Map<FindingSourceKey, FindingResponder> responders;

    /**
     * @param sources    the finding sources, at most one per key
     * @param responders the finding responders, at most one per key
     * @throws NullPointerException     if a collection, a member or a member's key is {@code null}
     * @throws IllegalArgumentException if two sources, or two responders, share a key
     */
    public FindingRegistry(Collection<? extends FindingSource> sources,
            Collection<? extends FindingResponder> responders) {
        this.sources = byKey(sources, FindingSource::key, "source");
        this.responders = byKey(responders, FindingResponder::key, "responder");
    }

    /**
     * @param key the key of a finding source
     * @return the source registered under the key; empty if none is
     */
    public Optional<FindingSource> source(FindingSourceKey key) {
        return Optional.ofNullable(sources.get(key));
    }

    /**
     * @param key the key of a finding responder
     * @return the responder registered under the key; empty if none is
     */
    public Optional<FindingResponder> responder(FindingSourceKey key) {
        return Optional.ofNullable(responders.get(key));
    }

    private static <T> Map<FindingSourceKey, T> byKey(Collection<? extends T> members,
            Function<T, FindingSourceKey> keyOf, String kind) {
        Objects.requireNonNull(members, kind + "s");
        var byKey = new LinkedHashMap<FindingSourceKey, T>();
        for (T member : members) {
            Objects.requireNonNull(member, kind);
            var key = Objects.requireNonNull(keyOf.apply(member), kind + " key");
            if (byKey.putIfAbsent(key, member) != null) {
                throw new IllegalArgumentException("a second " + kind + " is registered under the key " + key);
            }
        }
        return Map.copyOf(byKey);
    }
}
