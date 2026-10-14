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

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The header of one plan instance version (PM-IMPL-5, PM-EXT-7): which version it is, the event that composed it,
 * every input by digest, and the effect of the facts with provenance, the pruned elements, the policy calls with their
 * bound arguments and the waived gates. In the {@code .pmwf} file of the version the header is the single
 * {@code instance} line after the {@code workflow} line, written as one line of canonical JSON, so its digest is
 * reproducible.
 * <p>
 * The record holds the values as given; whether they satisfy every constraint of the header schema (the patterns of
 * the ids and digests, the closed unit list of the policy calls, the version from {@code 1}) is answered by
 * {@link HeaderSchemaCheck}, as violations, never by an exception. The lists are copied into unmodifiable lists that
 * keep their order.
 *
 * @param planId      the id of the plan
 * @param version     the instance version, from {@code 1}
 * @param event       the composition event that produced the version
 * @param composedAt  when the version was composed
 * @param inputs      every input the version was composed from
 * @param lane        the lane of the plan; empty only in version 1 of a plan whose lane is not yet known
 * @param posture     the posture of the plan, if set
 * @param steps       the step lists of the version
 * @param pruned      every template element the composition left out
 * @param policyCalls every policy call with its effective arguments
 * @param waivedGates every gate a waiver marks as waived
 * @since 0.1
 */
public record PlanInstanceHeader(String planId, int version, CompositionEvent event, Instant composedAt,
CompositionInputs inputs, Optional<Lane> lane, Optional<Posture> posture, Steps steps, List<PrunedElement> pruned,
List<PolicyCall> policyCalls, List<WaivedGate> waivedGates) {

    /** The only format version of the header, the value of {@code format_version}. */
    public static final int FORMAT_VERSION = 1;

    /**
     * Refuses a missing component and copies the lists into unmodifiable lists that keep their order.
     *
     * @param planId      the id of the plan
     * @param version     the instance version
     * @param event       the composition event
     * @param composedAt  when the version was composed
     * @param inputs      every input the version was composed from
     * @param lane        the lane of the plan, if known
     * @param posture     the posture of the plan, if set
     * @param steps       the step lists of the version
     * @param pruned      every template element left out
     * @param policyCalls every policy call
     * @param waivedGates every waived gate
     */
    public PlanInstanceHeader {
        Objects.requireNonNull(planId, "planId");
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(composedAt, "composedAt");
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(lane, "lane");
        Objects.requireNonNull(posture, "posture");
        Objects.requireNonNull(steps, "steps");
        pruned = List.copyOf(pruned);
        policyCalls = List.copyOf(policyCalls);
        waivedGates = List.copyOf(waivedGates);
    }

    /**
     * The step lists of a version: the Phase 5 verification steps and the Phase 6 finalize steps it keeps.
     *
     * @param phase5Verification the Phase 5 verification step ids
     * @param phase6             the Phase 6 step ids, each {@code default:<name>} or {@code project:<name>}
     * @since 0.1
     */
    public record Steps(List<String> phase5Verification, List<String> phase6) {

        /**
         * Copies both lists into unmodifiable lists that keep their order.
         *
         * @param phase5Verification the Phase 5 verification step ids
         * @param phase6             the Phase 6 step ids
         */
        public Steps {
            phase5Verification = List.copyOf(phase5Verification);
            phase6 = List.copyOf(phase6);
        }
    }

    /**
     * The lane of a plan, which decides the route the instance keeps.
     *
     * @since 0.1
     */
    public enum Lane {
        /** The light planning lane. */
        LIGHT("light"),
        /** The deep planning lane. */
        DEEP("deep"),
        /** The land lane: an existing change is landed, remediation only retries. */
        LAND("land"),
        /** The adopt lane: an existing pull request is adopted. */
        ADOPT("adopt");

        private final String wireName;

        Lane(String wireName) {
            this.wireName = wireName;
        }

        /**
         * @return the value of {@code lane} in the instance header
         */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the value of {@code lane} as written in a header
         * @return the lane of that wire name, or empty if no lane has it
         */
        public static Optional<Lane> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(lane -> lane.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * The posture of a plan, whose effect is the posture prunes from version 1 on.
     *
     * @since 0.1
     */
    public enum Posture {
        /** The minimal posture. */
        MINIMAL("minimal"),
        /** The standard posture. */
        STANDARD("standard"),
        /** The full posture. */
        FULL("full");

        private final String wireName;

        Posture(String wireName) {
            this.wireName = wireName;
        }

        /**
         * @return the value of {@code posture} in the instance header
         */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the value of {@code posture} as written in a header
         * @return the posture of that wire name, or empty if no posture has it
         */
        public static Optional<Posture> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(posture -> posture.wireName.equals(wireName)).findFirst();
        }
    }
}
