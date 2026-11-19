/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.verify;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.workflow.ast.InputType;
import org.jspecify.annotations.Nullable;

/**
 * The keys of the closed configuration a unit may read in its frontmatter (PM-WF-16, Rule 1), each with its value domain
 * and whether the configuration compiles a default for it.
 * <p>
 * The registry is an argument of the verifier, never code of this module: the closed configuration schema lives outside
 * {@code pm-workflow} (ADR {@code workflow-verify/015}), so the production wiring supplies the registry and the default
 * is the empty one, which refuses every {@code reads_config} key a unit declares (fail closed).
 *
 * @param keys the keys by their effective-key form, in declaration order
 * @since 0.1
 */
public record ConfigKeyRegistry(Map<String, ConfigKey> keys) {

    /**
     * Refuses a missing map and copies it into an unmodifiable map that keeps its order.
     *
     * @param keys the keys by their effective-key form
     * @throws NullPointerException if the map or one of its values is {@code null}
     */
    public ConfigKeyRegistry {
        Objects.requireNonNull(keys, "keys");
        var copy = new LinkedHashMap<String, ConfigKey>();
        keys.forEach((key, value) -> copy.put(Objects.requireNonNull(key, "key"),
                Objects.requireNonNull(value, "value")));
        keys = Collections.unmodifiableMap(copy);
    }

    /**
     * One key of the closed configuration.
     *
     * @param key             the effective-key form, {@code <file_stem>.<dotted path>}
     * @param domain          the value domain of the key, {@code null} when the schema's domain is none of the three
     *                        closed input types, so the key can never be the source of an input fallback
     * @param compiledDefault whether the configuration compiles a default for the key, so it is never absent
     */
    public record ConfigKey(String key, @Nullable InputType domain, boolean compiledDefault) {

        /**
         * Refuses a missing key.
         *
         * @param key             the effective-key form
         * @param domain          the value domain, or {@code null} when no input type expresses it
         * @param compiledDefault whether the key has a compiled default
         * @throws NullPointerException if the key is {@code null}
         */
        public ConfigKey {
            Objects.requireNonNull(key, "key");
        }
    }

    /**
     * @return the registry of no key, the default of a verifier whose caller supplies none
     */
    public static ConfigKeyRegistry empty() {
        return new ConfigKeyRegistry(Map.of());
    }

    /**
     * @param entries the keys of the registry
     * @return a registry of the entries, by their effective-key form
     * @throws IllegalArgumentException if two entries name the same key
     */
    public static ConfigKeyRegistry of(Collection<ConfigKey> entries) {
        var byKey = new LinkedHashMap<String, ConfigKey>();
        for (var entry : entries) {
            if (byKey.put(entry.key(), entry) != null) {
                throw new IllegalArgumentException("the configuration key '%s' is registered twice"
                        .formatted(entry.key()));
            }
        }
        return new ConfigKeyRegistry(byKey);
    }

    /**
     * @param key an effective key
     * @return the registered key, or empty when the closed configuration holds none
     */
    public Optional<ConfigKey> find(String key) {
        return Optional.ofNullable(keys.get(key));
    }

    /**
     * @param key an effective key
     * @return whether the closed configuration holds the key
     */
    public boolean contains(String key) {
        return keys.containsKey(key);
    }

    /**
     * Tells whether a configuration domain is assignable to an input type: every value the domain admits is a value the
     * input type admits, so a fallback never supplies a value the input refuses. A key whose domain no input type
     * expresses is assignable to nothing.
     *
     * @param type   the type of the input
     * @param domain the value domain of the configuration key, {@code null} when no input type expresses it
     * @return whether the domain is assignable to the type
     */
    public static boolean assignable(InputType type, @Nullable InputType domain) {
        return switch (type) {
            case InputType.Enumeration(var values) -> domain instanceof InputType.Enumeration(var admitted)
                    && values.containsAll(admitted);
            case InputType.IntRange(var min, var max) -> domain instanceof InputType.IntRange(var low, var high)
                    && (min == null || low != null && low >= min)
                    && (max == null || high != null && high <= max);
            case InputType.EnumList(var values) -> domain instanceof InputType.EnumList(var admitted)
                    && values.containsAll(admitted);
            case InputType.Text _, InputType.CommitMessage _, InputType.Path _, InputType.PathList _ -> false;
        };
    }
}
