/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.ast;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/**
 * The type of a parameter: of a free parameter of a link, of a sub-workflow parameter, or of the result of a
 * sub-workflow (PM-IMPL-5).
 *
 * @since 0.1
 */
public sealed interface ParamType
        permits ParamType.Text, ParamType.Int, ParamType.EnumOf, ParamType.SchemaRef, ParamType.ListOf {

    /**
     * {@code text}: a string with optional length bounds. The source form of the bounds is the range
     * {@code [<low>..<high>]}, so the two bounds are both present or both absent.
     *
     * @param minLength the least length, present exactly when {@code maxLength} is
     * @param maxLength the greatest length, present exactly when {@code minLength} is
     */
    record Text(Optional<Integer> minLength, Optional<Integer> maxLength) implements ParamType {

        /**
         * Refuses a single bound.
         *
         * @param minLength the least length, present exactly when {@code maxLength} is
         * @param maxLength the greatest length, present exactly when {@code minLength} is
         * @throws IllegalArgumentException if exactly one of the two bounds is present
         */
        public Text {
            requirePairedBounds(minLength.isPresent(), maxLength.isPresent());
        }
    }

    /**
     * {@code int}: an integer with optional bounds. The source form of the bounds is the range
     * {@code [<low>..<high>]}, so the two bounds are both present or both absent.
     *
     * @param min the least value, present exactly when {@code max} is
     * @param max the greatest value, present exactly when {@code min} is
     */
    record Int(Optional<Integer> min, Optional<Integer> max) implements ParamType {

        /**
         * Refuses a single bound.
         *
         * @param min the least value, present exactly when {@code max} is
         * @param max the greatest value, present exactly when {@code min} is
         * @throws IllegalArgumentException if exactly one of the two bounds is present
         */
        public Int {
            requirePairedBounds(min.isPresent(), max.isPresent());
        }
    }

    /**
     * {@code enum[...]}: one of a closed set of values.
     *
     * @param values the admitted values in declaration order, none of them twice
     */
    record EnumOf(List<String> values) implements ParamType {

        /**
         * Copies the values into an immutable list and refuses a value that occurs twice.
         *
         * @param values the admitted values in declaration order, none of them twice
         * @throws IllegalArgumentException if a value occurs more than once
         */
        public EnumOf {
            values = List.copyOf(values);
            var seen = new HashSet<String>();
            for (var value : values) {
                if (!seen.add(value)) {
                    throw new IllegalArgumentException("the value '%s' occurs twice in the set".formatted(value));
                }
            }
        }
    }

    /**
     * {@code schema[<schemaName>]}: a JSON object validated against the named closed schema.
     *
     * @param schemaName the name of the schema
     */
    record SchemaRef(String schemaName) implements ParamType {
    }

    /**
     * {@code list<...>}: a list of elements of one type; the element type is never a list itself.
     *
     * @param elementType the type of the elements
     */
    record ListOf(ParamType elementType) implements ParamType {
    }

    private static void requirePairedBounds(boolean lowPresent, boolean highPresent) {
        if (lowPresent != highPresent) {
            throw new IllegalArgumentException("a range needs both bounds or none, but only the %s bound is present"
                    .formatted(lowPresent ? "low" : "high"));
        }
    }
}
