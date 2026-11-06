/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.plan;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.workflow.ast.CallValue;
import org.jspecify.annotations.Nullable;

/**
 * The effective value of one argument of a policy call, with its provenance (PM-IMPL-5): the template's call argument,
 * a composition rule, or the {@code raise} waiver that set the ceiling. The value is a literal of the DSL, so an
 * integer budget, an enumeration value, or a list such as the allowed {@code actions}.
 *
 * @param value    the effective value
 * @param source   where the value comes from
 * @param file     for {@link Source#TEMPLATE}, the unit file whose call argument is the value; {@code null} if unknown
 * @param waiverId for {@link Source#WAIVER}, the id of the {@code raise} waiver that set the value; {@code null}
 *                 otherwise
 * @since 0.1
 */
public record BoundArgument(CallValue value, Source source, @Nullable String file, @Nullable String waiverId) {

    /**
     * Refuses a missing value or source; an absent file or waiver id is {@code null}.
     *
     * @param value    the effective value
     * @param source   where the value comes from
     * @param file     the unit file of a template value, {@code null} if unknown
     * @param waiverId the waiver id of a waiver value, {@code null} otherwise
     */
    public BoundArgument {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(source, "source");
    }

    /**
     * @param value the call argument of the template
     * @param file  the unit file that binds it
     * @return the argument as the template binds it
     */
    public static BoundArgument fromTemplate(CallValue value, String file) {
        return new BoundArgument(value, Source.TEMPLATE, file, null);
    }

    /**
     * @param value the value a composition rule fixes
     * @return the argument as the rule binds it
     */
    public static BoundArgument fromCompositionRule(CallValue value) {
        return new BoundArgument(value, Source.COMPOSITION_RULE, null, null);
    }

    /**
     * @param value    the ceiling a {@code raise} waiver sets
     * @param waiverId the id of the waiver
     * @return the argument as the waiver binds it
     */
    public static BoundArgument fromWaiver(CallValue value, String waiverId) {
        return new BoundArgument(value, Source.WAIVER, null, waiverId);
    }

    /**
     * Where the effective value of an argument comes from.
     *
     * @since 0.1
     */
    public enum Source {
        /** The call argument of the template unit. */
        TEMPLATE("template"),
        /** A composition rule fixed the value. */
        COMPOSITION_RULE("composition_rule"),
        /** A {@code raise} waiver set the ceiling. */
        WAIVER("waiver");

        private final String wireName;

        Source(String wireName) {
            this.wireName = wireName;
        }

        /**
         * @return the value of {@code source} of a bound argument in the instance header
         */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the value of {@code source} as written in a header
         * @return the source of that wire name, or empty if no source has it
         */
        public static Optional<Source> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(source -> source.wireName.equals(wireName)).findFirst();
        }
    }
}
