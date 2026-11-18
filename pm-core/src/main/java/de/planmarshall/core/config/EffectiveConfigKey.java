/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.config;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * One effective configuration key of Store Schemas 07 and 08 (PM-EXT-6): its effective-key form, its value domain and
 * whether the configuration compiles a default for it.
 * <p>
 * The effective key is {@code <file_stem>.<dotted path>} (Store Schemas 07, Effective Configuration and Sources), for
 * example {@code project.review.currency} or {@code local-config.commit_trailer.email}. Its grammar is
 * {@code stem "." segment {"." segment}}, where a segment is an identifier ({@code [a-z][a-z0-9_-]*}) or the single
 * wildcard {@code *} that stands for exactly one segment of a map node. A stem is one of {@link #STEMS}; the stem
 * {@code derived} names a fact, not a configuration value, and is refused with a message that says so.
 *
 * @param key                the effective-key form
 * @param domain             the value domain of the key
 * @param hasCompiledDefault whether the configuration compiles a default, so the key is never absent
 * @since 0.1
 */
public record EffectiveConfigKey(String key, ConfigValueDomain domain, boolean hasCompiledDefault) {

    /**
     * The file stems a key may name: the three configuration files whose nodes are configuration values.
     */
    static final Set<String> STEMS = Set.of("project", "workflow", "local-config");

    private static final String DERIVED_STEM = "derived";
    private static final String WILDCARD = "*";
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z][a-z0-9_-]*");

    /**
     * Refuses a key whose effective-key form breaks the grammar.
     *
     * @param key                the effective-key form
     * @param domain             the value domain of the key
     * @param hasCompiledDefault whether the configuration compiles a default
     * @throws NullPointerException     if a component is {@code null}
     * @throws IllegalArgumentException if the key names a {@code derived.} fact, a stem other than the three, no dotted
     *                                  path, an empty segment, or a segment that is neither an identifier nor {@code *}
     */
    public EffectiveConfigKey {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(domain, "domain");
        var segments = key.split("\\.", -1);
        if (segments.length < 2) {
            throw new IllegalArgumentException("the key '%s' has no dotted path after its stem".formatted(key));
        }
        var stem = segments[0];
        if (DERIVED_STEM.equals(stem)) {
            throw new IllegalArgumentException(
                    "the key '%s' names a derived fact; facts are read by kind, not by configuration key".formatted(key));
        }
        if (!STEMS.contains(stem)) {
            throw new IllegalArgumentException(
                    "the key '%s' names the stem '%s'; a configuration key names one of %s".formatted(key, stem, STEMS));
        }
        for (var index = 1; index < segments.length; index++) {
            var segment = segments[index];
            if (segment.isEmpty()) {
                throw new IllegalArgumentException("the key '%s' has an empty segment at position %d"
                        .formatted(key, index));
            }
            if (!WILDCARD.equals(segment) && !IDENTIFIER.matcher(segment).matches()) {
                throw new IllegalArgumentException(
                        "the key '%s' has the segment '%s', which is neither an identifier nor the wildcard"
                                .formatted(key, segment));
            }
        }
    }
}
