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
import java.util.LinkedHashMap;
import java.util.Optional;

import de.planmarshall.workflow.plan.HeaderJson.Node;
import de.planmarshall.workflow.plan.HeaderJson.NullNode;
import de.planmarshall.workflow.plan.HeaderJson.ObjectNode;
import de.planmarshall.workflow.plan.HeaderJson.StringNode;
import org.jspecify.annotations.Nullable;

/**
 * Why a composition was attempted, or why it was refused (PM-IMPL-5): the {@code cause} object of a line of the
 * composition log. Every member may be absent ({@code null}); a composition names the members its event has.
 *
 * @param gate           for {@code fallback}: the gate or condition whose exhaustion took the fallback
 * @param fallbackCause  for {@code fallback}: the gap cause of the fallback
 * @param waiverId       for {@code waiver}: the waiver that composed the version
 * @param deliverableId  the deliverable the composition is about, {@code D<n>}
 * @param replanId       for {@code replan}: the re-planning whose revision composed the version, {@code RP-<n>}
 * @param fromConfigHash for {@code config_adopted}: the configuration hash of the version in force
 * @param toConfigHash   for {@code config_adopted}: the configuration hash of the candidate
 * @param refusal        for a refused composition: why it was refused
 * @since 0.1
 */
public record CompositionCause(@Nullable String gate, @Nullable FallbackCause fallbackCause,
        @Nullable String waiverId, @Nullable String deliverableId, @Nullable String replanId,
        @Nullable String fromConfigHash, @Nullable String toConfigHash,
        CompositionResult.@Nullable Refusal refusal) {

    /** A cause without any member. */
    public static final CompositionCause NONE = new CompositionCause(null, null, null, null, null, null, null, null);

    /**
     * @param refusal why a composition was refused
     * @return a cause naming only the refusal
     */
    public static CompositionCause refused(CompositionResult.Refusal refusal) {
        return new CompositionCause(null, null, null, null, null, null, null, refusal);
    }

    /**
     * @return the cause as a JSON object, every member present, an absent one {@code null}
     */
    Node toNode() {
        var members = new LinkedHashMap<String, Node>();
        members.put("gate", text(gate));
        members.put("fallback_cause", text(fallbackCause == null ? null : fallbackCause.wireName()));
        members.put("waiver_id", text(waiverId));
        members.put("deliverable_id", text(deliverableId));
        members.put("replan_id", text(replanId));
        members.put("from_config_hash", text(fromConfigHash));
        members.put("to_config_hash", text(toConfigHash));
        members.put("refusal", text(refusal == null ? null : refusal.wireName()));
        return new ObjectNode(members);
    }

    private static Node text(@Nullable String value) {
        return value == null ? NullNode.INSTANCE : new StringNode(value);
    }

    /**
     * The gap cause of a fallback, the value of {@code cause.fallback_cause} in the composition log. The set equals the
     * {@code fallback_cause} enumeration of the composition record schema, in its order.
     *
     * @since 0.1
     */
    public enum FallbackCause {
        /** A budget was spent. */
        EXHAUSTED("exhausted"),
        /** A caller routed a step-out. */
        STEPPED_OUT("stepped_out"),
        /** A closed decision selected the fallback. */
        DECIDED("decided"),
        /** A task closed infeasible. */
        INFEASIBLE("infeasible"),
        /** A condition's retry budget was spent. */
        CONDITION("condition"),
        /** The provider refused the merge action. */
        PROVIDER_REFUSED("provider_refused"),
        /**
         * The pre-merge barrier passed while the plan's asking variant leaves the merge to the operator; not a failure.
         */
        MERGE_BY_OPERATOR("merge_by_operator"),
        /** No push remote exists; not a failure. */
        LOCAL_ONLY("local_only"),
        /** The changes of {@code landing.files} could not be written into the main checkout. */
        FILES_NOT_APPLICABLE("files_not_applicable");

        private final String wireName;

        FallbackCause(String wireName) {
            this.wireName = wireName;
        }

        /**
         * @return the value of {@code fallback_cause} in the composition log
         */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the value as written in the composition log
         * @return the cause of that wire name, or empty if no cause has it
         */
        public static Optional<FallbackCause> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(cause -> cause.wireName.equals(wireName)).findFirst();
        }
    }
}
