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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

import de.planmarshall.workflow.plan.HeaderJson.Node;
import de.planmarshall.workflow.plan.HeaderJson.NullNode;
import de.planmarshall.workflow.plan.HeaderJson.NumberNode;
import de.planmarshall.workflow.plan.HeaderJson.ObjectNode;
import de.planmarshall.workflow.plan.HeaderJson.StringNode;

/**
 * One line of the composition log {@code instance/composition.jsonl} of a plan (PM-IMPL-5, PM-EXT-7): one composition
 * attempt, whatever its result. The line is canonical JSON (keys sorted, no insignificant whitespace, UTF-8); an
 * absent version is written as {@code null}, an absent cause is not written.
 *
 * @param event        the composition event
 * @param result       what became of the attempt
 * @param version      the composed version; present exactly when the result is {@link Result#COMPOSED}
 * @param trigger      who triggered the event
 * @param cause        why the composition was attempted or refused, if the event names a cause
 * @param inputDigests the digests of the inputs the attempt read, by name
 * @param recordedAt   when the attempt was recorded
 * @since 0.1
 */
public record CompositionRecord(CompositionEvent event, Result result, Optional<Integer> version,
CompositionEvent.Trigger trigger, Optional<CompositionCause> cause, SortedMap<String, String> inputDigests,
Instant recordedAt) {

    /** The only format version of a line of the composition log. */
    public static final int FORMAT_VERSION = 1;

    /**
     * Refuses a missing component and a version that does not fit the result, and copies the digests into an
     * unmodifiable map ordered by name.
     *
     * @param event        the composition event
     * @param result       what became of the attempt
     * @param version      the composed version
     * @param trigger      who triggered the event
     * @param cause        why the composition was attempted or refused
     * @param inputDigests the digests of the inputs
     * @param recordedAt   when the attempt was recorded
     * @throws IllegalArgumentException if a composed attempt has no version, or another attempt has one
     */
    public CompositionRecord {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(cause, "cause");
        inputDigests = Collections.unmodifiableSortedMap(new TreeMap<>(Objects.requireNonNull(inputDigests,
                "inputDigests")));
        Objects.requireNonNull(recordedAt, "recordedAt");
        if ((result == Result.COMPOSED) != version.isPresent()) {
            throw new IllegalArgumentException("a %s attempt %s a version".formatted(result.wireName(),
                    result == Result.COMPOSED ? "needs" : "has no"));
        }
    }

    /**
     * @param instance   the composed version
     * @param recordedAt when the attempt was recorded
     * @return the record of the composed version, its trigger the event's and its digests the header's inputs
     */
    public static CompositionRecord composed(PlanInstance instance, Instant recordedAt) {
        var header = instance.header();
        return new CompositionRecord(header.event(), Result.COMPOSED, Optional.of(instance.version()),
                header.event().trigger(), Optional.empty(), inputDigests(header.inputs()), recordedAt);
    }

    /**
     * @param inputs the inputs of a version
     * @return their digests by name: {@code config_hash}, {@code composition_rules}, {@code plan_facts}, the optional
     *         digests that are present, and {@code template:<unit>} and {@code sub_unit:<unit>} per unit
     */
    public static SortedMap<String, String> inputDigests(CompositionInputs inputs) {
        var digests = new TreeMap<String, String>();
        digests.put("config_hash", inputs.configHash());
        digests.put("composition_rules", inputs.compositionRulesDigest());
        digests.put("plan_facts", inputs.planFactsDigest());
        inputs.realizedFootprintDigest().ifPresent(digest -> digests.put("realized_footprint", digest));
        inputs.providersDigest().ifPresent(digest -> digests.put("providers", digest));
        inputs.templates().forEach(unit -> digests.put("template:" + unit.unit(), unit.digest()));
        inputs.subUnits().forEach(unit -> digests.put("sub_unit:" + unit.unit(), unit.digest()));
        return digests;
    }

    /**
     * @return the canonical JSON line, without a line break
     */
    String toJson() {
        var members = new LinkedHashMap<String, Node>();
        members.put("format_version", new NumberNode(BigDecimal.valueOf(FORMAT_VERSION)));
        members.put("event", new StringNode(event.wireName()));
        members.put("result", new StringNode(result.wireName()));
        members.put("version", version.<Node>map(value -> new NumberNode(BigDecimal.valueOf(value)))
                .orElse(NullNode.INSTANCE));
        members.put("trigger", new StringNode(trigger.wireName()));
        cause.ifPresent(value -> members.put("cause", value.toNode()));
        var digests = new LinkedHashMap<String, Node>();
        inputDigests.forEach((name, digest) -> digests.put(name, new StringNode(digest)));
        members.put("input_digests", new ObjectNode(digests));
        members.put("recorded_at", new StringNode(recordedAt.toString()));
        return HeaderJson.canonical(new ObjectNode(members));
    }

    /**
     * What became of a composition attempt.
     *
     * @since 0.1
     */
    public enum Result {
        /** A new version was composed and written. */
        COMPOSED("composed"),
        /** A configuration candidate equal in effect to the version in force; no new version. */
        IDENTICAL_ADOPTED("identical_adopted"),
        /** The configuration drift was resolved keep; no new version. */
        KEPT("kept"),
        /** The position rule or a check refused the version; no new version. */
        REFUSED("refused");

        private final String wireName;

        Result(String wireName) {
            this.wireName = wireName;
        }

        /**
         * @return the value of {@code result} in the composition log
         */
        public String wireName() {
            return wireName;
        }
    }
}
