/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role;

import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One validated role artifact (PM-WORK-1, Role Artifact Schema): the JSON document {@code <name>.json} of a role set.
 * <p>
 * The harness, model and effort held here are the artifact's own values, the lowest of the levels of
 * {@link EffectiveRole}. Every other member is the role's effective value: an artifact replaces the artifact of the same
 * name in a lower set as a whole, members are never merged ({@link RoleSetLoader}).
 * <p>
 * An artifact is data. The members the specification lets a role lower are checked here, against the compiled values of
 * the harness and of the off-track signals; everything that depends on the client profile of the harness, the
 * enrolment, the model catalogue or the project's skills is checked by {@link RoleValidation}.
 *
 * @param formatVersion       the format version, {@value RoleArtifactValidator#FORMAT_VERSION}
 * @param name                the role name, equal to the file name without {@code .json}
 * @param description         what the role works on
 * @param roleClass           what the role may do
 * @param tools               the exact tool set of the role
 * @param harness             the client id of the role's own harness, before the assignment
 * @param model               the role's own model, before the assignment
 * @param effort              the role's own effort, before the assignment; its value is {@code null} for no effort flag
 * @param form                the worker form of the role
 * @param count               the workers of the role that run at once per project
 * @param skills              the role's skills in delivery order; the first is the role skill
 * @param recycling           the recycling bounds of a warm role; {@code null} for a fresh role
 * @param budgetFloorSeconds  the floor of the job budget learned for the role
 * @param contextBudgetTokens the context budget of a worker, or {@code null} for the harness fallback
 * @param questions           the continuation bounds after a question, or {@code null}
 * @param offTrack            per off-track signal the threshold below the compiled one and the action
 * @since 0.1
 */
public record RoleArtifact(int formatVersion, String name, String description, RoleClass roleClass, List<String> tools,
String harness, String model, EffortValue effort, RoleForm form, int count, List<String> skills,
@Nullable Recycling recycling, int budgetFloorSeconds, @Nullable Integer contextBudgetTokens,
@Nullable Questions questions, Map<OffTrackSignal, OffTrackSetting> offTrack) {

    /** Copies the lists and the map. */
    public RoleArtifact {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(roleClass, "roleClass");
        Objects.requireNonNull(tools, "tools");
        Objects.requireNonNull(harness, "harness");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(effort, "effort");
        Objects.requireNonNull(form, "form");
        Objects.requireNonNull(skills, "skills");
        if (skills.isEmpty()) {
            throw new IllegalArgumentException("a role names at least one skill, its role skill");
        }
        tools = List.copyOf(tools);
        skills = List.copyOf(skills);
        offTrack = Collections.unmodifiableMap(new LinkedHashMap<>(offTrack));
    }

    /** @return whether the role has no tool and no PM-MCP connection */
    public boolean zeroTool() {
        return roleClass == RoleClass.ZERO_TOOL;
    }

    /** @return the first skill, the role's contract */
    public String roleSkill() {
        return skills.getFirst();
    }

    /** @return the recycling bounds of a warm role, or empty for a fresh role */
    public Optional<Recycling> recyclingBounds() {
        return Optional.ofNullable(recycling);
    }

    /** @return the continuation bounds after a question, or empty */
    public Optional<Questions> questionBounds() {
        return Optional.ofNullable(questions);
    }

    /** @return the context budget of a worker of the role, or empty for the harness fallback */
    public Optional<Integer> declaredContextBudget() {
        return Optional.ofNullable(contextBudgetTokens);
    }

    /** @return one off-track setting by signal, or empty when the artifact does not name the signal */
    public Optional<OffTrackSetting> offTrack(OffTrackSignal signal) {
        return Optional.ofNullable(offTrack.get(signal));
    }

    /**
     * The recycling bounds of a warm role (PM-WORK-1).
     *
     * @param idleWakeups         consecutive wait answers without a task after which the worker is recycled; {@code null}
     *                            takes the host's value
     * @param tokenBudget         the token budget of one generation
     * @param tasksPerGeneration  the tasks a generation takes before it is recycled; {@code null} unbounded
     * @since 0.1
     */
    public record Recycling(Integer idleWakeups, int tokenBudget, Integer tasksPerGeneration) {

        /**
         * @throws IllegalArgumentException if the token budget is below the schema's minimum
         */
        public Recycling {
            if (tokenBudget < 10_000) {
                throw new IllegalArgumentException("the token budget is at least 10000, not " + tokenBudget);
            }
        }
    }

    /**
     * How long a task of a role continues after it asked (PM-WORK-1, and
     * {@code model-work/06-envelopes-questions-deadlines.adoc#continuation}).
     *
     * @param holdSeconds         how long a worker holds its task for the answer; {@code 0} no hold
     * @param resumeWithinSeconds how long a task stays suspended for a resume after the hold; {@code 0} no resume
     * @since 0.1
     */
    public record Questions(int holdSeconds, int resumeWithinSeconds) {

        /**
         * @throws IllegalArgumentException if a value is outside the schema's bounds
         */
        public Questions {
            if (holdSeconds < 0 || holdSeconds > 3_600) {
                throw new IllegalArgumentException("hold_seconds is between 0 and 3600, not " + holdSeconds);
            }
            if (resumeWithinSeconds < 0 || resumeWithinSeconds > 604_800) {
                throw new IllegalArgumentException("resume_within_seconds is between 0 and 604800, not "
                        + resumeWithinSeconds);
            }
        }
    }

    /**
     * The action the server takes when an off-track signal holds (PM-WORK-7, and
     * {@code model-work/03-supervision-and-recycling.adoc#worker-off-track}).
     *
     * @since 0.1
     */
    public enum OffTrackAction {

        /** The plan's process and the task's activity carry the signal; nothing else changes. */
        SHOW("show"),
        /** The worker's next tool result carries a server note naming the signal. */
        NUDGE("nudge"),
        /** The attempt ends with the envelope's yield, or as {@link #RESTART} outside an envelope. */
        YIELD("yield"),
        /** The worker is ended and its task offered again. */
        RESTART("restart");

        private final String wireName;

        OffTrackAction(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the action in a representation */
        public String wireName() {
            return wireName;
        }
    }

    /**
     * One off-track signal of a role's workers (PM-WORK-7): the compiled threshold, the floor below which the compiled
     * value is the cap of the signal, and the wire name.
     *
     * @since 0.1
     */
    public enum OffTrackSignal {

        /** The same tool call with the same target in a row. */
        REPEATING("repeating", 5.0, 1.0),
        /** Completed turns without following a link of the task. */
        NO_PROGRESS("no_progress", 40.0, 5.0),
        /** Tokens spent on the current plan task above a multiple of its predicted cost. */
        OVER_SIZE("over_size", 3.0, 1.5),
        /** Unplanned changed files recorded for the current plan task. */
        OUTSIDE_FOOTPRINT("outside_footprint", 3.0, 1.0);

        private final String wireName;
        private final double compiledThreshold;
        private final double floor;

        OffTrackSignal(String wireName, double compiledThreshold, double floor) {
            this.wireName = wireName;
            this.compiledThreshold = compiledThreshold;
            this.floor = floor;
        }

        /** @return the name of the signal in a representation */
        public String wireName() {
            return wireName;
        }

        /** @return the compiled threshold, which a role artifact may lower and never raise */
        public double compiledThreshold() {
            return compiledThreshold;
        }

        /** @return the lowest threshold the signal admits */
        public double floor() {
            return floor;
        }
    }

    /**
     * The threshold and action of one off-track signal (PM-WORK-7).
     *
     * @param threshold the threshold, which lowers the compiled one and never raises it; {@code null} takes the compiled
     *                  value
     * @param action    the action the server takes when the signal holds
     * @since 0.1
     */
    public record OffTrackSetting(@Nullable Double threshold, OffTrackAction action) {

        /** @throws NullPointerException if the action is {@code null} */
        public OffTrackSetting {
            Objects.requireNonNull(action, "action");
            if (threshold != null && !Double.isFinite(threshold)) {
                throw new IllegalArgumentException("a threshold is finite, not " + threshold);
            }
        }

        /**
         * @param signal the signal
         * @return the effective threshold of the signal
         * @throws IllegalArgumentException if the threshold is outside the signal's floor and compiled value
         */
        public double effectiveThreshold(OffTrackSignal signal) {
            double effective = threshold == null ? signal.compiledThreshold() : threshold;
            if (effective < signal.floor() || effective > signal.compiledThreshold()) {
                throw new IllegalArgumentException("the threshold %s of '%s' is outside [%s, %s]".formatted(effective,
                        signal.wireName(), signal.floor(), signal.compiledThreshold()));
            }
            return effective;
        }
    }
}
