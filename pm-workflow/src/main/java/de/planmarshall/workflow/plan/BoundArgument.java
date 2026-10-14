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

/**
 * The effective value of one argument of a policy call, with its provenance (PM-IMPL-5): the template's call argument,
 * a composition rule, or the {@code raise} waiver that set the ceiling. The value is a literal of the DSL, so an
 * integer budget, an enumeration value, or a list such as the allowed {@code actions}.
 *
 * @param value    the effective value
 * @param source   where the value comes from
 * @param file     for {@link Source#TEMPLATE}, the unit file whose call argument is the value, if known
 * @param waiverId for {@link Source#WAIVER}, the id of the {@code raise} waiver that set the value
 * @since 0.1
 */
public record BoundArgument(CallValue value, Source source, Optional<String> file, Optional<String> waiverId) {

    /**
     * Refuses a missing component; an absent file or waiver id is an empty {@link Optional}.
     *
     * @param value    the effective value
     * @param source   where the value comes from
     * @param file     the unit file of a template value, if known
     * @param waiverId the waiver id of a waiver value
     */
    public BoundArgument {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(waiverId, "waiverId");
    }

    /**
     * @param value the call argument of the template
     * @param file  the unit file that binds it
     * @return the argument as the template binds it
     */
    public static BoundArgument fromTemplate(CallValue value, String file) {
        return new BoundArgument(value, Source.TEMPLATE, Optional.of(file), Optional.empty());
    }

    /**
     * @param value the value a composition rule fixes
     * @return the argument as the rule binds it
     */
    public static BoundArgument fromCompositionRule(CallValue value) {
        return new BoundArgument(value, Source.COMPOSITION_RULE, Optional.empty(), Optional.empty());
    }

    /**
     * @param value    the ceiling a {@code raise} waiver sets
     * @param waiverId the id of the waiver
     * @return the argument as the waiver binds it
     */
    public static BoundArgument fromWaiver(CallValue value, String waiverId) {
        return new BoundArgument(value, Source.WAIVER, Optional.empty(), Optional.of(waiverId));
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
