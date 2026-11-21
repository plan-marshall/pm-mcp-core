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

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * One line of a plan's history {@code mcp/history.jsonl} (Store Schemas § The Plan's History): a state entered or left,
 * a sub-workflow called or returned, or an instance version taking force, with the plan's phase, the call stack and the
 * instance version in force at the line.
 * <p>
 * The history is append-only and never rewritten, and it is the record from which the runtime derives the state of
 * every node of the plan's process: {@code mcp/status.json} records where the plan is, the history records where it
 * has been, which branch it took and with which outcome.
 * <p>
 * A {@code left} line names the reason of its outcome when the primitive's result carried one: the reason is data
 * that explains the outcome and never chose the transition. It is an optional member, written only when present, so a
 * line without a reason is the line of format version {@value #FORMAT_VERSION} it always was.
 *
 * @param formatVersion   the format version, always {@value #FORMAT_VERSION}
 * @param seq             the position of the line in the plan's history, one above the last
 * @param event           what the line records
 * @param node            the fully qualified state id, the sub-workflow id, or {@code plan} for an instance line
 * @param callPath        the sub-workflow id of every frame of the call stack, bottom to top
 * @param phase           the plan's phase value at the line; {@code null} before the first phase is called
 * @param outcome         the outcome the state settled or the sub-workflow returned; {@code null} otherwise
 * @param reason          the wire name of the reason of the outcome of a {@code left} line whose result carried one;
 *                        {@code null} otherwise
 * @param instanceVersion the instance version in force at the line
 * @param at              when the line was written
 * @since 0.1
 */
@Builder
public record PlanHistoryLine(int formatVersion, long seq, Event event, String node, List<String> callPath,
@Nullable String phase, @Nullable String outcome, @Nullable String reason, int instanceVersion, Instant at) {

    /** The format version of the history line. */
    public static final int FORMAT_VERSION = 1;

    /** The most frames a plan's call stack can have, so the most entries a line's call path can carry. */
    public static final int MAX_CALL_PATH = 5;

    static final Pattern OUTCOME_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*$");

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
     * @param reason          the reason of the outcome of a {@code left} line, or {@code null}
     * @param instanceVersion the instance version in force
     * @param at              when the line was written
     * @throws IllegalArgumentException if the format version, the sequence, the node, the call path, the outcome, the
     *                                  reason or the instance version does not fit, or a reason is on a line that is no
     *                                  {@code left} line or carries no outcome
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
        if (reason != null) {
            requireReason(event, outcome, reason);
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
        return new PlanHistoryLine(FORMAT_VERSION, seq, event, node, callPath, phase, outcome, null,
                instanceVersion, at);
    }

    private static void requireReason(Event event, @Nullable String outcome, String reason) {
        if (!OUTCOME_PATTERN.matcher(reason).matches()) {
            throw new IllegalArgumentException("the history line reason '%s' is not a lowercase identifier"
                    .formatted(reason));
        }
        if (event != Event.LEFT) {
            throw new IllegalArgumentException("the history line reason '%s' is on a '%s' line, not a 'left' line"
                    .formatted(reason, event.wireName()));
        }
        if (outcome == null) {
            throw new IllegalArgumentException("the history line reason '%s' explains no outcome".formatted(reason));
        }
    }

    /**
     * @return the outcome as an optional value
     */
    public Optional<String> outcomeValue() {
        return Optional.ofNullable(outcome);
    }

    /**
     * @return the reason of the outcome as an optional value; empty for a line without one
     */
    public Optional<String> reasonValue() {
        return Optional.ofNullable(reason);
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
