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
 * A template element a composition left out of a plan instance, with the reason and the fact that pruned it
 * (PM-IMPL-5): one entry of {@code pruned[]} in the instance header. Pruned elements are absent from the instance body
 * and named only here.
 *
 * @param element    the kind of the element
 * @param id         the element: a step id, a state id, {@code <state_id>/<action>} for a link, or
 *                   {@code <call_site>/<action>} for a policy action
 * @param reason     why the element was left out
 * @param sourceFact the effective configuration key, derived fact kind, plan fact or composition event that caused
 *                   the prune
 * @since 0.1
 */
public record PrunedElement(Kind element, String id, PruneReason reason, String sourceFact) {

    /**
     * Refuses a missing component.
     *
     * @param element    the kind of the element
     * @param id         the element
     * @param reason     why the element was left out
     * @param sourceFact the fact that caused the prune
     */
    public PrunedElement {
        Objects.requireNonNull(element, "element");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(sourceFact, "sourceFact");
    }

    /**
     * The kind of a pruned template element.
     *
     * @since 0.1
     */
    public enum Kind {
        /** A finalize or verification step. */
        STEP("step"),
        /** A state of the unit. */
        STATE("state"),
        /** A link a state offers. */
        LINK("link"),
        /** An action a policy call allows. */
        POLICY_ACTION("policy_action");

        private final String wireName;

        Kind(String wireName) {
            this.wireName = wireName;
        }

        /**
         * @return the value of {@code element} in a pruned element of the instance header
         */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the value of {@code element} as written in a header
         * @return the kind of that wire name, or empty if no kind has it
         */
        public static Optional<Kind> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(kind -> kind.wireName.equals(wireName)).findFirst();
        }
    }
}
