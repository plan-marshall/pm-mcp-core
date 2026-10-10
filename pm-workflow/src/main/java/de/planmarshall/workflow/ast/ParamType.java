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
     * {@code text}: a string with optional length bounds.
     *
     * @param minLength the least length, if bounded
     * @param maxLength the greatest length, if bounded
     */
    record Text(Optional<Integer> minLength, Optional<Integer> maxLength) implements ParamType {
    }

    /**
     * {@code int}: an integer with optional bounds.
     *
     * @param min the least value, if bounded
     * @param max the greatest value, if bounded
     */
    record Int(Optional<Integer> min, Optional<Integer> max) implements ParamType {
    }

    /**
     * {@code enum[...]}: one of a closed set of values.
     *
     * @param values the admitted values in declaration order
     */
    record EnumOf(List<String> values) implements ParamType {

        /**
         * Copies the values into an immutable list.
         *
         * @param values the admitted values in declaration order
         */
        public EnumOf {
            values = List.copyOf(values);
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
}
