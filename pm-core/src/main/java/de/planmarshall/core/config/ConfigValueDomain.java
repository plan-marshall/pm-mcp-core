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

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The value domain of an effective configuration key (Store Schemas 07, Effective Configuration and Sources): the closed
 * set of shapes the schema subset of the configuration files can express.
 * <p>
 * The set is total over the schemas of {@code config-project}, {@code config-workflow} and {@code local-config}: every
 * node a walk of those schemas reaches has exactly one member here, and {@link Union} carries the one shape the
 * single-type members cannot, the schema keyword {@code oneOf}. A domain is a value and is compared structurally: an
 * enumeration's values and a union's alternatives are compared in declaration order.
 *
 * @since 0.1
 */
public sealed interface ConfigValueDomain
        permits ConfigValueDomain.Enumeration, ConfigValueDomain.IntegerRange, ConfigValueDomain.Bool,
        ConfigValueDomain.Text, ConfigValueDomain.ListOf, ConfigValueDomain.Object, ConfigValueDomain.Union {

    /**
     * {@code enum}: one of a closed set of values, from the schema keywords {@code enum} and {@code const}.
     *
     * @param values the admitted values in declaration order, none of them twice
     */
    record Enumeration(List<String> values) implements ConfigValueDomain {

        /**
         * Copies the values into an immutable list and refuses a value that occurs twice or none at all.
         *
         * @param values the admitted values in declaration order
         * @throws IllegalArgumentException if no value is present or a value occurs more than once
         */
        public Enumeration {
            var copied = List.copyOf(values);
            if (copied.isEmpty()) {
                throw new IllegalArgumentException("a closed value set needs at least one value");
            }
            var seen = new HashSet<String>();
            for (var value : copied) {
                if (!seen.add(value)) {
                    throw new IllegalArgumentException("the value '%s' occurs twice in the set".formatted(value));
                }
            }
            values = copied;
        }
    }

    /**
     * {@code integer} with or without bounds, from the schema keywords {@code minimum} and {@code maximum}. A bound that
     * is absent is {@code null}: the schema states that bound for no value of the key.
     *
     * @param min the least admitted value, {@code null} when the schema states no lower bound
     * @param max the greatest admitted value, {@code null} when the schema states no upper bound
     */
    record IntegerRange(@Nullable Integer min, @Nullable Integer max) implements ConfigValueDomain {

        /**
         * Refuses a range whose bounds are the wrong way round.
         *
         * @param min the least admitted value
         * @param max the greatest admitted value
         * @throws IllegalArgumentException if both bounds are present and the least is above the greatest
         */
        public IntegerRange {
            if (min != null && max != null && min > max) {
                throw new IllegalArgumentException("the range %d..%d has its bounds the wrong way round"
                        .formatted(min, max));
            }
        }
    }

    /**
     * {@code boolean}: the two values of a flag.
     */
    record Bool() implements ConfigValueDomain {
    }

    /**
     * {@code string}: any text, including the strings a {@code pattern}, a {@code format} or a length bound narrows.
     */
    record Text() implements ConfigValueDomain {
    }

    /**
     * {@code array}: a list whose members all have one domain.
     *
     * @param element the domain of every member
     */
    record ListOf(ConfigValueDomain element) implements ConfigValueDomain {

        /**
         * Refuses a missing element domain.
         *
         * @param element the domain of every member
         * @throws NullPointerException if the domain is {@code null}
         */
        public ListOf {
            Objects.requireNonNull(element, "element");
        }
    }

    /**
     * {@code object}: a node whose own members are keys of their own.
     */
    record Object() implements ConfigValueDomain {
    }

    /**
     * {@code oneOf}: a value of one of at least two alternatives, none of which is another.
     *
     * @param alternatives the admitted alternatives in declaration order
     */
    record Union(List<ConfigValueDomain> alternatives) implements ConfigValueDomain {

        /**
         * Copies the alternatives into an immutable list and refuses fewer than two or a repeated one.
         *
         * @param alternatives the admitted alternatives in declaration order
         * @throws IllegalArgumentException if fewer than two alternatives are present or one occurs twice
         */
        public Union {
            var copied = List.copyOf(alternatives);
            if (copied.size() < 2) {
                throw new IllegalArgumentException("a union needs at least two alternatives");
            }
            if (new HashSet<>(copied).size() != copied.size()) {
                throw new IllegalArgumentException("a union alternative occurs twice");
            }
            alternatives = copied;
        }
    }
}
