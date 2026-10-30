/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * One line of a plan's history {@code mcp/history.jsonl} (Store Schemas § The Plan's History): a state entered or left,
 * a sub-workflow called or returned, or an instance version taking force, with the plan's phase, the call stack and the
 * instance version in force at the line.
 * <p>
 * The history is append-only and never rewritten, and it is the record from which the runtime derives the state of
 * every node of the plan's process: {@code mcp/status.json} records where the plan is, the history records where it
 * has been, which branch it took and with which outcome.
 *
 * @param formatVersion   the format version, always {@value #FORMAT_VERSION}
 * @param seq             the position of the line in the plan's history, one above the last
 * @param event           what the line records
 * @param node            the fully qualified state id, the sub-workflow id, or {@code plan} for an instance line
 * @param callPath        the sub-workflow id of every frame of the call stack, bottom to top
 * @param phase           the plan's phase value at the line; {@code null} before the first phase is called
 * @param outcome         the outcome the state settled or the sub-workflow returned; {@code null} otherwise
 * @param instanceVersion the instance version in force at the line
 * @param at              when the line was written
 * @since 0.1
 */
public record PlanHistoryLine(int formatVersion, long seq, Event event, String node, List<String> callPath,
@Nullable String phase, @Nullable String outcome, int instanceVersion, Instant at) {

    /** The format version of the history line. */
    public static final int FORMAT_VERSION = 1;

    /** The most frames a plan's call stack can have, so the most entries a line's call path can carry. */
    public static final int MAX_CALL_PATH = 5;

    private static final Pattern OUTCOME_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*$");

    /**
     * Refuses a line that does not fit the schema. A {@code null} phase or outcome is a value, not a missing component.
     *
     * @param formatVersion   the format version
     * @param seq             the position of the line, from {@code 1}
     * @param event           what the line records
     * @param node            the node of the line
     * @param callPath        the call stack of the line
     * @param phase           the phase at the line, or {@code null}
     * @param outcome         the outcome of the line, or {@code null}
     * @param instanceVersion the instance version in force
     * @param at              when the line was written
     * @throws IllegalArgumentException if the format version, the sequence, the node, the call path, the outcome or the
     *                                  instance version does not fit
     * @throws NullPointerException     if a required component is {@code null}
     */
    public PlanHistoryLine {
        if (formatVersion != FORMAT_VERSION) {
            throw new IllegalArgumentException("the history line format version is %d, not %d"
                    .formatted(formatVersion, FORMAT_VERSION));
        }
        if (seq < 1) {
            throw new IllegalArgumentException("the history line sequence is %d, not from 1".formatted(seq));
        }
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(callPath, "callPath");
        Objects.requireNonNull(at, "at");
        callPath = List.copyOf(callPath);
        if (node.isEmpty()) {
            throw new IllegalArgumentException("the history line names no node");
        }
        if (callPath.size() > MAX_CALL_PATH) {
            throw new IllegalArgumentException("the history line carries %d call frames, not at most %d"
                    .formatted(callPath.size(), MAX_CALL_PATH));
        }
        if (outcome != null && !OUTCOME_PATTERN.matcher(outcome).matches()) {
            throw new IllegalArgumentException("the history line outcome '%s' is not a lowercase identifier"
                    .formatted(outcome));
        }
        if (instanceVersion < 1) {
            throw new IllegalArgumentException("the history line names the instance version %d, not from 1"
                    .formatted(instanceVersion));
        }
    }

    /**
     * @param seq             the position of the line
     * @param event           what the line records
     * @param node            the node of the line
     * @param callPath        the call stack at the line
     * @param phase           the phase at the line, or {@code null}
     * @param outcome         the outcome of the line, or {@code null}
     * @param instanceVersion the instance version in force
     * @param at              when the line was written
     * @return the line
     */
    public static PlanHistoryLine of(long seq, Event event, String node, List<String> callPath, String phase,
            String outcome, int instanceVersion, Instant at) {
        return new PlanHistoryLine(FORMAT_VERSION, seq, event, node, callPath, phase, outcome, instanceVersion, at);
    }

    /**
     * @return the outcome as an optional value
     */
    public Optional<String> outcomeValue() {
        return Optional.ofNullable(outcome);
    }

    /**
     * @return the phase as an optional value
     */
    public Optional<String> phaseValue() {
        return Optional.ofNullable(phase);
    }

    /**
     * What a history line records (Store Schemas § The Plan's History).
     *
     * @since 0.1
     */
    public enum Event {

        /** A state was entered. */
        ENTERED("entered"),

        /** A state was left, its outcome settled. */
        LEFT("left"),

        /** A sub-workflow was called. */
        CALLED("called"),

        /** A sub-workflow returned. */
        RETURNED("returned"),

        /** A new instance version took force. */
        INSTANCE("instance");

        private final String wireName;

        Event(String wireName) {
            this.wireName = wireName;
        }

        /**
         * @return the value of the {@code event} member of a history line
         */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the value of the {@code event} member
         * @return the event
         * @throws IllegalArgumentException if no event carries the name
         */
        public static Event ofWireName(String wireName) {
            for (var event : values()) {
                if (event.wireName.equals(wireName)) {
                    return event;
                }
            }
            throw new IllegalArgumentException("no history event is named '%s'".formatted(wireName));
        }
    }
}
