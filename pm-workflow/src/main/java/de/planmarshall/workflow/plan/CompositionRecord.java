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
 * absent version is written as {@code null}, an absent cause is not written. The {@code diff_summary} of a composed
 * version ({@link DiffSummary}) is the version relative to the version before it, written as {@code null} for version
 * 1; an attempt that composed no version writes no {@code diff_summary}.
 *
 * @param event        the composition event
 * @param result       what became of the attempt
 * @param version      the composed version; present exactly when the result is {@link Result#COMPOSED}
 * @param trigger      who triggered the event
 * @param cause        why the composition was attempted or refused, if the event names a cause
 * @param inputDigests the digests of the inputs the attempt read, by name
 * @param diffSummary  the composed version relative to the version before it; present exactly when a version after
 *                     the first was composed
 * @param recordedAt   when the attempt was recorded
 * @since 0.1
 */
public record CompositionRecord(CompositionEvent event, Result result, Optional<Integer> version,
CompositionEvent.Trigger trigger, Optional<CompositionCause> cause, SortedMap<String, String> inputDigests,
Optional<DiffSummary> diffSummary, Instant recordedAt) {

    /** The only format version of a line of the composition log. */
    public static final int FORMAT_VERSION = 1;

    /**
     * Refuses a missing component, a version that does not fit the result and a diff summary that does not fit the
     * version, and copies the digests into an unmodifiable map ordered by name.
     *
     * @param event        the composition event
     * @param result       what became of the attempt
     * @param version      the composed version
     * @param trigger      who triggered the event
     * @param cause        why the composition was attempted or refused
     * @param inputDigests the digests of the inputs
     * @param diffSummary  the composed version relative to the version before it
     * @param recordedAt   when the attempt was recorded
     * @throws IllegalArgumentException if a composed attempt has no version, or another attempt has one; if a
     *                                  composed version after the first has no diff summary, or version 1 or an
     *                                  attempt that composed no version has one
     */
    public CompositionRecord {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(cause, "cause");
        inputDigests = Collections.unmodifiableSortedMap(new TreeMap<>(Objects.requireNonNull(inputDigests,
                "inputDigests")));
        Objects.requireNonNull(diffSummary, "diffSummary");
        Objects.requireNonNull(recordedAt, "recordedAt");
        if ((result == Result.COMPOSED) != version.isPresent()) {
            throw new IllegalArgumentException("a %s attempt %s a version".formatted(result.wireName(),
                    result == Result.COMPOSED ? "needs" : "has no"));
        }
        if (version.filter(composed -> composed > 1).isPresent() != diffSummary.isPresent()) {
            throw new IllegalArgumentException("a %s attempt%s %s".formatted(result.wireName(),
                    version.map(composed -> " of version " + composed).orElse(""),
                    diffSummary.isPresent() ? "takes no diff summary" : "needs a diff summary"));
        }
    }

    /**
     * @param instance   the composed version
     * @param previous   the version in force before it; empty exactly for version 1
     * @param recordedAt when the attempt was recorded
     * @return the record of the composed version, its trigger the event's, its digests the header's inputs and its
     *         diff summary the version relative to the previous one
     * @throws IllegalArgumentException if the previous version is not the version before the composed one
     */
    public static CompositionRecord composed(PlanInstance instance, Optional<PlanInstance> previous,
            Instant recordedAt) {
        var header = instance.header();
        var before = instance.version() - 1;
        if (previous.map(PlanInstance::version).orElse(0) != before) {
            throw new IllegalArgumentException("version %d is composed after version %d, not after %s".formatted(
                    instance.version(), before, previous.map(value -> "version " + value.version())
                            .orElse("no version")));
        }
        return new CompositionRecord(header.event(), Result.COMPOSED, Optional.of(instance.version()),
                header.event().trigger(), Optional.empty(), inputDigests(header.inputs()),
                previous.map(value -> DiffSummary.between(value.header(), header)), recordedAt);
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
        if (result == Result.COMPOSED) {
            members.put("diff_summary", diffSummary.map(DiffSummary::toNode).orElse(NullNode.INSTANCE));
        }
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
