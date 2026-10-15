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

/**
 * One waiver in force for a plan, as the composition reads it (PM-IMPL-5, PM-EXT-7): the composer reads no waiver
 * store, the caller passes the waivers in force as values.
 * <p>
 * A {@link Mode#MET met} waiver marks its gate waived in the instance header. A {@link Mode#RAISE raise} waiver names
 * the budget it raises as {@code <argument>:<policy>}, for example {@code fix_budget:ci-failure}, and binds the new
 * ceiling at every call site of the remediation unit {@code remediation.<policy>} that binds the argument; the header
 * names the waiver id as the source of that value.
 *
 * @param id    the waiver id, {@code W-<n>}
 * @param gate  the gate a met waiver declares met, or the budget {@code <argument>:<policy>} a raise sets
 * @param mode  whether the waiver declares the gate met or raises a ceiling
 * @param value the new ceiling of a raise; empty for a met waiver
 * @since 0.1
 */
public record Waiver(String id, String gate, Mode mode, Optional<Integer> value) {

    /**
     * Refuses a missing component, a raise without a ceiling and a met waiver with one.
     *
     * @param id    the waiver id
     * @param gate  the gate or budget
     * @param mode  the mode
     * @param value the ceiling of a raise
     * @throws IllegalArgumentException if a raise has no value or a met waiver has one
     */
    public Waiver {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(gate, "gate");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(value, "value");
        if (mode == Mode.RAISE && value.isEmpty()) {
            throw new IllegalArgumentException("a raise waiver needs a value");
        }
        if (mode == Mode.MET && value.isPresent()) {
            throw new IllegalArgumentException("a met waiver takes no value");
        }
    }

    /**
     * @param id   the waiver id
     * @param gate the gate the waiver declares met
     * @return the met waiver
     */
    public static Waiver met(String id, String gate) {
        return new Waiver(id, gate, Mode.MET, Optional.empty());
    }

    /**
     * @param id     the waiver id
     * @param budget the budget it raises, {@code <argument>:<policy>}
     * @param value  the new ceiling
     * @return the raise waiver
     */
    public static Waiver raise(String id, String budget, int value) {
        return new Waiver(id, budget, Mode.RAISE, Optional.of(value));
    }

    /**
     * @return the argument a raise binds, the part of the gate before the colon; empty for a met waiver or a gate
     *         without a colon
     */
    Optional<String> raisedArgument() {
        var colon = gate.indexOf(':');
        return mode == Mode.RAISE && colon > 0 ? Optional.of(gate.substring(0, colon)) : Optional.empty();
    }

    /**
     * @return the remediation unit a raise applies to, {@code remediation.<policy>}; empty for a met waiver or a gate
     *         without a colon
     */
    Optional<String> raisedUnit() {
        var colon = gate.indexOf(':');
        return mode == Mode.RAISE && colon > 0 ? Optional.of("remediation." + gate.substring(colon + 1))
                : Optional.empty();
    }

    /**
     * Whether a waiver declares a gate met or raises a budget ceiling.
     *
     * @since 0.1
     */
    public enum Mode {
        /** The gate is declared met. */
        MET("met"),
        /** A remediation budget ceiling is raised. */
        RAISE("raise");

        private final String wireName;

        Mode(String wireName) {
            this.wireName = wireName;
        }

        /**
         * @return the value of {@code mode} in the waiver record
         */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the value of {@code mode} as written in a waiver record
         * @return the mode of that wire name, or empty if no mode has it
         */
        public static Optional<Mode> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(mode -> mode.wireName.equals(wireName)).findFirst();
        }
    }
}
