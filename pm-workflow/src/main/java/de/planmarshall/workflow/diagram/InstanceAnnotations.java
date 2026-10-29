/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.diagram;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import de.planmarshall.workflow.plan.PlanInstance;
import de.planmarshall.workflow.plan.PlanInstanceHeader;
import de.planmarshall.workflow.plan.PrunedElement;
import de.planmarshall.workflow.plan.WaivedGate;

/**
 * What the three instance forms show beside the composed unit (PM-IMPL-5, Workflow DSL Specification, Plan Instance,
 * Display), derived once from the header of a plan instance version so that every form names the same values in the
 * same words: the title of the instance (the plan, the version and its composition event), every pruned element with
 * its reason and source fact, and every waived gate with its waiver id. The two table forms also name the lane, the
 * posture and the active state, which the Mermaid form shows by its style alone.
 * <p>
 * The values are the wire names of the header, in the order the header lists them; nothing is sorted. A value the
 * header leaves empty, the lane of a version 1 or an unset posture, is {@value #NONE}.
 *
 * @param planId      the id of the plan
 * @param version     the instance version
 * @param event       the wire name of the composition event of the version
 * @param lane        the wire name of the lane, or {@value #NONE}
 * @param posture     the wire name of the posture, or {@value #NONE}
 * @param activeState the state to highlight or mark; empty for none
 * @param pruned      every pruned element in header order
 * @param waivedGates every waived gate in header order
 * @param composed    every composed file of the version, the plan unit's included, in header order
 * @since 0.1
 */
record InstanceAnnotations(String planId, int version, String event, String lane, String posture,
Optional<String> activeState, List<Pruned> pruned, List<Waived> waivedGates, List<Composed> composed) {

    /** The value shown for a header value that is not set. */
    static final String NONE = "-";

    // \R matches a CRLF pair as one unit, so the pair collapses to a single space; no character class can express it
    @SuppressWarnings("java:S6035")
    private static final Pattern LINE_BREAKS_AND_CONTROLS = Pattern.compile("\\R|\\p{Cc}");

    /**
     * Copies the lists into unmodifiable lists that keep their order.
     *
     * @param planId      the id of the plan
     * @param version     the instance version
     * @param event       the wire name of the composition event
     * @param lane        the wire name of the lane, or {@value #NONE}
     * @param posture     the wire name of the posture, or {@value #NONE}
     * @param activeState the state to highlight or mark
     * @param pruned      every pruned element
     * @param waivedGates every waived gate
     * @param composed    every composed file of the version
     */
    InstanceAnnotations {
        Objects.requireNonNull(planId, "planId");
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(lane, "lane");
        Objects.requireNonNull(posture, "posture");
        Objects.requireNonNull(activeState, "activeState");
        pruned = List.copyOf(pruned);
        waivedGates = List.copyOf(waivedGates);
        composed = List.copyOf(composed);
    }

    /**
     * Derives the annotations of an instance version.
     *
     * @param instance    the instance version
     * @param activeState the state to highlight or mark; empty for none
     * @return its annotations
     */
    static InstanceAnnotations of(PlanInstance instance, Optional<String> activeState) {
        var header = instance.header();
        var composed = new ArrayList<Composed>();
        header.inputs().templates().stream()
                .filter(reference -> reference.file().filter(file -> file.startsWith("instance/")).isPresent())
                .forEach(reference -> composed.add(new Composed(reference.unit(), reference.file().orElseThrow(),
                        reference.digest())));
        instance.units().forEach(unit -> composed.removeIf(existing -> existing.unit().equals(unit.unit())));
        instance.units().forEach(unit -> composed.add(new Composed(unit.unit(), unit.file(), unit.digest())));
        return new InstanceAnnotations(header.planId(), header.version(), header.event().wireName(),
                header.lane().map(PlanInstanceHeader.Lane::wireName).orElse(NONE),
                header.posture().map(PlanInstanceHeader.Posture::wireName).orElse(NONE), activeState,
                header.pruned().stream().map(Pruned::of).toList(),
                header.waivedGates().stream().map(Waived::of).toList(), composed);
    }

    /**
     * @return the title every form gives the instance: {@code plan <plan_id> v<N> (<event>)}
     */
    String title() {
        return "plan %s v%d (%s)".formatted(singleLine(planId), version, event);
    }

    /**
     * Puts free text of the header on one line, as every form needs it: each line break (a line feed, a carriage
     * return, both together, or any other Unicode line terminator) and every other Unicode control character (general
     * category Cc: the C0 controls, DEL and the C1 controls U+0080 to U+009F) becomes one space. A line break would
     * end a Mermaid declaration, end an AsciiDoc table or break the box of a terminal table, and a C1 control such as
     * U+009B (CSI) would be interpreted by a terminal that prints the table.
     *
     * @param text a text of the header
     * @return the text without a line break or Unicode control character
     */
    static String singleLine(String text) {
        return LINE_BREAKS_AND_CONTROLS.matcher(text).replaceAll(" ");
    }

    /**
     * Tells whether a state of the unit is a waived gate.
     *
     * @param stateId a state of the unit
     * @return the waiver id of the first waived gate with that id; empty when the state is no waived gate
     */
    Optional<String> waiverOf(String stateId) {
        return waivedGates.stream().filter(gate -> gate.gate().equals(stateId)).map(Waived::waiverId).findFirst();
    }

    /**
     * One pruned element as every form shows it.
     *
     * @param element    the wire name of the element kind
     * @param id         the element
     * @param reason     the wire name of the prune reason
     * @param sourceFact the fact that caused the prune
     */
    record Pruned(String element, String id, String reason, String sourceFact) {

        static Pruned of(PrunedElement element) {
            return new Pruned(element.element().wireName(), element.id(), element.reason().wireName(),
                    element.sourceFact());
        }

        /**
         * @return the one-line description of the prune: {@code <element> <id>: <reason> (<source_fact>)}
         */
        String describe() {
            return "%s %s: %s (%s)".formatted(element, id, reason, sourceFact);
        }
    }

    /**
     * One composed file of the version, as every form shows it.
     *
     * @param unit   the id of the composed unit
     * @param file   the file of the unit, relative to the directory of the plan
     * @param digest the SHA-256 of the file's text
     */
    record Composed(String unit, String file, String digest) {

        /**
         * @return the one-line description of the composed file: {@code <unit> -> <file> (<digest>)}
         */
        String describe() {
            return "%s -> %s (%s)".formatted(unit, file, digest);
        }
    }

    /**
     * One waived gate as every form shows it.
     *
     * @param gate     the waivable gate identifier
     * @param waiverId the id of the waiver
     */
    record Waived(String gate, String waiverId) {

        static Waived of(WaivedGate gate) {
            return new Waived(gate.gate(), gate.waiverId());
        }

        /**
         * @return the one-line description of the waived gate: {@code <gate>: waived <waiver_id>}
         */
        String describe() {
            return "%s: waived %s".formatted(gate, waiverId);
        }
    }
}
