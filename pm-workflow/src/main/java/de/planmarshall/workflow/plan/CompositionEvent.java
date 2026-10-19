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
import java.util.Optional;

/**
 * The closed set of events that compose a new plan instance version (PM-IMPL-5); every version has exactly one. The
 * set equals the {@code composition_event} enumeration of the header schema, in its order.
 * <p>
 * The trigger of an event decides when its version is in force: a version of a {@linkplain Trigger#SERVER server}
 * event is composed in the transaction that enters its target state and is in force for the rest of its composing
 * chain, a version of an {@linkplain Trigger#OPERATOR operator or configuration} event is in force from the next
 * transition, never mid-state. {@link #START}, version 1, is composed in the transaction that creates the plan and is
 * in force from the plan's first state.
 *
 * @since 0.1
 */
public enum CompositionEvent {
    /** Version 1, composed when the plan is created: both lanes open, the posture prunes applied. */
    START("start", Trigger.SERVER),
    /**
     * The lane is known and recorded in the header ({@code lane}). No lane route is pruned: lane-route pruning is
     * deferred to the work package {@code doc/plans/plan-instance-alignment.adoc} (ADR {@code workflow-plan/011}).
     */
    OUTLINE("outline", Trigger.SERVER),
    /** Steps and tasks are known: provider and footprint prunes, budgets bound. */
    PLAN("plan", Trigger.SERVER),
    /** An adopted configuration or template change. */
    CONFIG_ADOPTED("config_adopted", Trigger.OPERATOR),
    /** A {@code met} waiver, a {@code raise}, or an {@code unwaive}. */
    WAIVER("waiver", Trigger.OPERATOR),
    /** The operator set or changed one of the plan's own values in {@code overrides.json}. */
    OVERRIDE("override", Trigger.OPERATOR),
    /** The post-run-only form of the {@code returned} and {@code handed_back} routes. */
    FALLBACK("fallback", Trigger.SERVER),
    /** The post-run-only form of a harvest that yielded no targets. */
    EMPTY_HARVEST("empty_harvest", Trigger.SERVER),
    /** A re-planning revised the outline or the plan tasks of a running plan. */
    REPLAN("replan", Trigger.SERVER);

    private final String wireName;
    private final Trigger trigger;

    CompositionEvent(String wireName, Trigger trigger) {
        this.wireName = wireName;
        this.trigger = trigger;
    }

    /**
     * @return the value of {@code event} in the instance header and the composition log
     */
    public String wireName() {
        return wireName;
    }

    /**
     * @return who triggers the event, which decides from when its version is in force
     */
    public Trigger trigger() {
        return trigger;
    }

    /**
     * @param wireName the value of {@code event} as written in a header or a log line
     * @return the event of that wire name, or empty if no event has it
     */
    public static Optional<CompositionEvent> fromWireName(String wireName) {
        return Arrays.stream(values()).filter(event -> event.wireName.equals(wireName)).findFirst();
    }

    /**
     * Who triggers a composition event; the value of {@code trigger} in the composition log.
     *
     * @since 0.1
     */
    public enum Trigger {
        /** The server, in the transaction that enters the target state: in force for the rest of that chain. */
        SERVER("server"),
        /** The operator or a configuration change: in force from the next transition. */
        OPERATOR("operator");

        private final String wireName;

        Trigger(String wireName) {
            this.wireName = wireName;
        }

        /**
         * @return the value of {@code trigger} in the composition log
         */
        public String wireName() {
            return wireName;
        }
    }
}
