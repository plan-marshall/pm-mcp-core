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

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The key a finding source and a finding responder are registered under (PM-IMPL-1): the kind of producer of the
 * findings store's {@code source} vocabulary, and for {@link FindingSourceKind#ANALYSIS} the id of the code analysis
 * provider. It mirrors the invariant of the findings store, so a finding is fetched and answered under the key it is
 * stored under.
 *
 * @param kind     the kind of producer
 * @param provider the id of the code analysis provider if {@code kind} is {@link FindingSourceKind#ANALYSIS};
 *                 {@code null} for every other kind
 * @since 0.1
 */
public record FindingSourceKey(FindingSourceKind kind, @Nullable String provider) {

    private static final Pattern PROVIDER_ID = Pattern.compile("^[a-z0-9][a-z0-9-]{1,62}[a-z0-9]$");

    /**
     * @throws NullPointerException     if the kind is {@code null}
     * @throws IllegalArgumentException if the provider is missing for {@code analysis}, present for another kind,
     *                                  or does not match the provider id pattern
     */
    public FindingSourceKey {
        Objects.requireNonNull(kind, "kind");
        if (kind == FindingSourceKind.ANALYSIS) {
            if (provider == null) {
                throw new IllegalArgumentException("provider is required iff kind is analysis");
            }
            if (!isProviderId(provider)) {
                throw new IllegalArgumentException("provider must match " + PROVIDER_ID.pattern());
            }
        } else if (provider != null) {
            throw new IllegalArgumentException("provider is set only for kind analysis, not for " + kind.wireName());
        }
    }

    /**
     * @param kind a kind of producer other than {@link FindingSourceKind#ANALYSIS}
     * @return the key of that kind
     * @throws IllegalArgumentException if the kind is {@code analysis}, which needs a provider id
     */
    public static FindingSourceKey of(FindingSourceKind kind) {
        return new FindingSourceKey(kind, null);
    }

    /**
     * @param provider the id of a code analysis provider
     * @return the key of that provider's analysis findings
     * @throws IllegalArgumentException if the id does not match the provider id pattern
     */
    public static FindingSourceKey analysis(String provider) {
        return new FindingSourceKey(FindingSourceKind.ANALYSIS, provider);
    }

    /**
     * Tells whether a value is a valid provider id: lower-case letters, digits and hyphens, 3 to 64 characters,
     * neither starting nor ending with a hyphen. The same id names a code analysis provider in a finding, in its
     * analysis result record and in its registry.
     *
     * @param value the value to check; may be {@code null}
     * @return whether the value is a valid provider id
     */
    public static boolean isProviderId(@Nullable String value) {
        return value != null && PROVIDER_ID.matcher(value).matches();
    }
}
