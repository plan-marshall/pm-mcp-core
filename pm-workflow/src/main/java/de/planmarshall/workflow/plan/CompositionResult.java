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

import java.util.List;
import java.util.Objects;

/**
 * The outcome of one composition (PM-IMPL-5): a new verified version, or the refusal of the version as a value. A
 * refused composition writes nothing, and the version in force stays.
 *
 * @since 0.1
 */
public sealed interface CompositionResult permits CompositionResult.Composed, CompositionResult.Refused {

    /**
     * A version that passed every check and may be written.
     *
     * @param instance the composed version
     * @since 0.1
     */
    record Composed(PlanInstance instance) implements CompositionResult {

        /**
         * Refuses a missing instance.
         *
         * @param instance the composed version
         */
        public Composed {
            Objects.requireNonNull(instance, "instance");
        }
    }

    /**
     * A version that was refused; the version in force is kept.
     *
     * @param refusal    why the version was refused
     * @param violations what is wrong, one line each, naming the check, the state or member and the defect
     * @since 0.1
     */
    record Refused(Refusal refusal, List<String> violations) implements CompositionResult {

        /**
         * Refuses a missing component and copies the violations into an unmodifiable list.
         *
         * @param refusal    why the version was refused
         * @param violations what is wrong
         */
        public Refused {
            Objects.requireNonNull(refusal, "refusal");
            violations = List.copyOf(violations);
        }
    }

    /**
     * Why a composed version was refused; the value of {@code cause.refusal} in the composition log.
     *
     * @since 0.1
     */
    enum Refusal {
        /** The version lacks the current state or a state of the call stack. */
        POSITION_NOT_CONTAINED("position_not_contained"),
        /** The version breaks a verification rule, a compiled invariant or the header schema. */
        INVARIANT_FAILED("invariant_failed");

        private final String wireName;

        Refusal(String wireName) {
            this.wireName = wireName;
        }

        /**
         * @return the value of {@code refusal} in the composition log
         */
        public String wireName() {
            return wireName;
        }
    }
}
