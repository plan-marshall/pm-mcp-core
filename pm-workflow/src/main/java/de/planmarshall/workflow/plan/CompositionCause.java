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
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.workflow.plan.HeaderJson.Node;
import de.planmarshall.workflow.plan.HeaderJson.NullNode;
import de.planmarshall.workflow.plan.HeaderJson.ObjectNode;
import de.planmarshall.workflow.plan.HeaderJson.StringNode;

/**
 * Why a composition was attempted, or why it was refused (PM-IMPL-5): the {@code cause} object of a line of the
 * composition log. Every member is optional; a composition names the members its event has.
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
public record CompositionCause(Optional<String> gate, Optional<FallbackCause> fallbackCause, Optional<String> waiverId,
Optional<String> deliverableId, Optional<String> replanId, Optional<String> fromConfigHash,
Optional<String> toConfigHash, Optional<CompositionResult.Refusal> refusal) {

    /** A cause without any member. */
    public static final CompositionCause NONE = new CompositionCause(Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty());

    /**
     * Refuses a missing component; an absent member is an empty {@link Optional}.
     *
     * @param gate           the gate of a fallback
     * @param fallbackCause  the gap cause of a fallback
     * @param waiverId       the waiver of a waiver event
     * @param deliverableId  the deliverable
     * @param replanId       the re-planning of a replan event
     * @param fromConfigHash the configuration hash in force
     * @param toConfigHash   the configuration hash of the candidate
     * @param refusal        the refusal of a refused composition
     */
    public CompositionCause {
        Objects.requireNonNull(gate, "gate");
        Objects.requireNonNull(fallbackCause, "fallbackCause");
        Objects.requireNonNull(waiverId, "waiverId");
        Objects.requireNonNull(deliverableId, "deliverableId");
        Objects.requireNonNull(replanId, "replanId");
        Objects.requireNonNull(fromConfigHash, "fromConfigHash");
        Objects.requireNonNull(toConfigHash, "toConfigHash");
        Objects.requireNonNull(refusal, "refusal");
    }

    /**
     * @param refusal why a composition was refused
     * @return a cause naming only the refusal
     */
    public static CompositionCause refused(CompositionResult.Refusal refusal) {
        return new CompositionCause(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(refusal));
    }

    /**
     * @return the cause as a JSON object, every member present, an absent one {@code null}
     */
    Node toNode() {
        var members = new LinkedHashMap<String, Node>();
        members.put("gate", text(gate));
        members.put("fallback_cause", text(fallbackCause.map(FallbackCause::wireName)));
        members.put("waiver_id", text(waiverId));
        members.put("deliverable_id", text(deliverableId));
        members.put("replan_id", text(replanId));
        members.put("from_config_hash", text(fromConfigHash));
        members.put("to_config_hash", text(toConfigHash));
        members.put("refusal", text(refusal.map(CompositionResult.Refusal::wireName)));
        return new ObjectNode(members);
    }

    private static Node text(Optional<String> value) {
        return value.<Node>map(StringNode::new).orElse(NullNode.INSTANCE);
    }

    /**
     * The gap cause of a fallback, the value of {@code cause.fallback_cause} in the composition log.
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
        /** An unanswered question whose class fallback is returned. */
        QUESTION_EXPIRED("question_expired"),
        /** The provider refused the merge action. */
        PROVIDER_REFUSED("provider_refused"),
        /** The pre-merge barrier passed while the operator's merge policy is off. */
        AUTO_MERGE_OFF("auto_merge_off"),
        /** No push remote exists. */
        LOCAL_ONLY("local_only");

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
