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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The learned budgets of one project, {@code build-timings.json} (Store Schemas § Build Timings Store; Job Runtime §
 * Adaptive Timeout Learning): one entry per budget key, and for each the three baselines, the one-shot escalation and
 * the instant of the last observation.
 * <p>
 * The store is written only by the runtime at a job's fate, under the build-timings lock of the project, and it lives
 * in the per-project machine store, so a moved checkout keeps its budgets. A worker key is {@code llm:<role>}: one key
 * per role whatever the harness, and its floor is the role artifact's {@code budget_floor_seconds}.
 * <p>
 * The three baselines are the same blend over the finished runs of the key, kept apart by the run's kind: a clean run
 * is the baseline allocation uses, a residue-poisoned or a cold run is used only while the clean baseline has no
 * observation. A timed-out run never feeds a baseline, and a finished run clears the escalation, which is one-shot.
 *
 * @param keys the entries by their budget key, in the order of the document
 * @since 0.1
 */
public record BuildTimings(Map<String, KeyTimings> keys) {

    /** The pattern of a budget key (Timeouts &amp; Budgets Specification, Job Budgets). */
    public static final Pattern BUDGET_KEY =
            Pattern.compile("[a-z][a-z0-9_]{0,31}:[a-z][a-z0-9.-]{0,63}(@[A-Za-z0-9][A-Za-z0-9_.-]{0,56})?");

    /** The format version of a build-timings store document that is read and written. */
    public static final int FORMAT_VERSION = 1;

    /**
     * @throws NullPointerException if the map or a key or value is {@code null}
     */
    public BuildTimings {
        Objects.requireNonNull(keys, "keys");
        keys = Collections.unmodifiableMap(new LinkedHashMap<>(keys));
    }

    /** @return the empty store */
    public static BuildTimings empty() {
        return new BuildTimings(Map.of());
    }

    /**
     * @param key the budget key
     * @return the timings of the key; empty if the store has no observation for it
     */
    public Optional<KeyTimings> timings(String key) {
        return Optional.ofNullable(keys.get(key));
    }

    /**
     * One baseline: the blended duration {@code L} of the finished runs of one kind of a key, and the number of runs it
     * was blended from.
     *
     * @param learnedSeconds the blended baseline; {@code null} before the first observation
     * @param observations   the number of finished runs the baseline rests on
     * @since 0.1
     */
    public record Baseline(Long learnedSeconds, int observations) {

        /**
         * @throws IllegalArgumentException if the observations are negative, a baseline has a value but no observation,
         *                                  or observations but no value
         */
        public Baseline {
            if (observations < 0 || (learnedSeconds == null) != (observations == 0)
                    || learnedSeconds != null && learnedSeconds < 1) {
                throw new IllegalArgumentException(
                        "A baseline carries a value of at least 1 exactly when it rests on an observation");
            }
        }

        /** @return the baseline before the first observation */
        public static Baseline empty() {
            return new Baseline(null, 0);
        }

        /**
         * @param previous the baseline before the run
         * @param seconds  the duration of the finished run, at least 1
         * @return the baseline after the run: the run itself when it is the first, else the max-biased blend
         *         {@code floor(0.8 * max(L, d) + 0.2 * min(L, d))}
         */
        public static Baseline observed(Baseline previous, long seconds) {
            Objects.requireNonNull(previous, "previous");
            if (seconds < 1) {
                throw new IllegalArgumentException("An observation is at least one second: " + seconds);
            }
            if (previous.learnedSeconds() == null) {
                return new Baseline(seconds, 1);
            }
            var old = previous.learnedSeconds();
            var high = Math.max(old, seconds);
            var low = Math.min(old, seconds);
            // (4 * high + low) / 5, split so neither product overflows for the accepted long values
            var blended = 4 * (high / 5) + low / 5 + (4 * (high % 5) + low % 5) / 5;
            return new Baseline(Math.max(1, blended), previous.observations() + 1);
        }
    }

    /**
     * The timings of one budget key.
     *
     * @param clean     the finished runs without residue or cold start
     * @param poisoned  the residue-poisoned runs
     * @param cold      the cold rebuilds
     * @param escalationSeconds the one-shot escalation budget a timed-out run set; {@code null} when none is owed
     * @param lastObservedAt    the instant of the last finished run; {@code null} before the first
     * @since 0.1
     */
    public record KeyTimings(Baseline clean, Baseline poisoned, Baseline cold, Long escalationSeconds,
    Instant lastObservedAt) {

        /**
         * @throws NullPointerException     if a baseline is {@code null}
         * @throws IllegalArgumentException if the escalation is below {@code 1}
         */
        public KeyTimings {
            Objects.requireNonNull(clean, "clean");
            Objects.requireNonNull(poisoned, "poisoned");
            Objects.requireNonNull(cold, "cold");
            if (escalationSeconds != null && escalationSeconds < 1) {
                throw new IllegalArgumentException(
                        "'escalation_seconds' must be at least 1: " + escalationSeconds);
            }
        }

        /** @return the timings of a key no run of which has finished */
        public static KeyTimings empty() {
            return new KeyTimings(Baseline.empty(), Baseline.empty(), Baseline.empty(), null, null);
        }

        /**
         * @return the baseline allocation uses: the clean one, else the poisoned one while the clean one has no
         *         observation, else the cold one
         */
        public Learned learned() {
            if (clean.observations() > 0) {
                return new Learned(clean.learnedSeconds(), clean.observations());
            }
            if (poisoned.observations() > 0) {
                return new Learned(poisoned.learnedSeconds(), poisoned.observations());
            }
            if (cold.observations() > 0) {
                return new Learned(cold.learnedSeconds(), cold.observations());
            }
            return new Learned(null, 0);
        }
    }

    /**
     * A learned baseline and the observations it rests on.
     *
     * @param seconds      the learned baseline; {@code null} before the first observation
     * @param observations the number of observations
     * @since 0.1
     */
    public record Learned(Long seconds, int observations) {
    }

    /**
     * The kind of a finished run, which decides the baseline it feeds.
     *
     * @since 0.1
     */
    public enum Residue {
        /** A run without residue or cold start. */
        CLEAN("clean"),
        /** A run poisoned by residue. */
        POISONED("poisoned"),
        /** A cold rebuild. */
        COLD("cold");

        private final String wireName;

        Residue(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a store */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a store
         * @return the value of that name; empty if no value has it
         */
        public static Optional<Residue> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }
}
