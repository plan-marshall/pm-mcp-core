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

/**
 * A literal {@code call_value}: bound to a sub-workflow parameter at a {@code call} site or declared by a
 * {@code declare} line (PM-IMPL-5, PM-WF-16).
 *
 * @since 0.1
 */
public sealed interface CallValue permits CallValue.EnumValue, CallValue.IntValue, CallValue.EnumList {

    /**
     * One value of an enumeration.
     *
     * @param value the value
     */
    record EnumValue(String value) implements CallValue {
    }

    /**
     * An integer.
     *
     * @param value the value
     */
    record IntValue(int value) implements CallValue {
    }

    /**
     * A list of distinct values of an enumeration; it may be empty, written {@code []}.
     *
     * @param values the values in source order, none of them twice; empty for the list without an element
     */
    record EnumList(List<String> values) implements CallValue {

        /**
         * Copies the values into an immutable list and refuses a value that occurs twice.
         *
         * @param values the values in source order, none of them twice
         * @throws IllegalArgumentException if a value occurs more than once
         */
        public EnumList {
            values = List.copyOf(values);
            var seen = new HashSet<String>();
            for (var value : values) {
                if (!seen.add(value)) {
                    throw new IllegalArgumentException("the value '%s' occurs twice in the list".formatted(value));
                }
            }
        }
    }
}
