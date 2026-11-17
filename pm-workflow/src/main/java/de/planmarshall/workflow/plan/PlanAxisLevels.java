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

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.workflow.plan.PlanInstanceHeader.Posture;
import org.jspecify.annotations.Nullable;

/**
 * The three levels of the plan behaviour axes and of the posture, read from the documents that own them (Workflow DSL §
 * Plan Behaviour Axes, item 2): the plan's own values ({@code axes} and {@code posture} of its {@code overrides.json}),
 * the project's (the landing from the team's configuration in {@code .marshall/config/}, the method and the asking from
 * the project's {@code local-config.json}), and the machine's ({@code plan_defaults} of {@code machine-config.json}).
 * <p>
 * A level is given as the raw members the document records, so the readers of the three stores owe this class nothing:
 * it maps a key onto its {@link PlanFacts.Axis} or the posture, checks the value against the axis's closed variant list,
 * and refuses anything else with the level and the key named, so a value no variant admits is never bound silently.
 * A key a level does not record stays absent and defers to the next level; a level with no values at all is legal.
 * <p>
 * {@link #axes()} is what a {@link PlanFacts.Builder} takes, and {@link #posture()} resolves the posture by the same
 * levels. This is the reader the composition needs; the composer itself still reads no store.
 *
 * @since 0.1
 */
public final class PlanAxisLevels {

    /** The raw key of the posture, which is no axis but resolves by the same levels. */
    public static final String POSTURE_KEY = "posture";

    /** The levels from the nearest, in the order the binding reads them. */
    private static final List<PlanFacts.Level> LEVELS = List.of(PlanFacts.Level.PLAN,
            PlanFacts.Level.PROJECT, PlanFacts.Level.MACHINE);

    private final Map<PlanFacts.Level, Map<String, String>> recorded;

    private PlanAxisLevels(Map<PlanFacts.Level, Map<String, String>> recorded) {
        this.recorded = recorded;
    }

    /**
     * @return a builder that starts with every level unset
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @param level  the level that records the values
     * @param values the raw members of the document of that level; {@code null} for a level that records none
     * @return the levels with this one set
     * @throws IllegalArgumentException if a key is neither an axis nor the posture, or a value is no variant of its axis
     */
    public static PlanAxisLevels of(PlanFacts.Level level, @Nullable Map<String, String> values) {
        return builder().level(level, values).build();
    }

    /**
     * @return per axis the variant each level sets, the shipped level never among them
     */
    public Map<PlanFacts.Axis, Map<PlanFacts.Level, String>> axes() {
        var axes = new EnumMap<PlanFacts.Axis, Map<PlanFacts.Level, String>>(PlanFacts.Axis.class);
        for (var level : LEVELS) {
            levelValues(level).forEach((key, value) -> PlanFacts.Axis.ofVariant(value).ifPresent(axis -> axes
                    .computeIfAbsent(axis, ignored -> new EnumMap<>(PlanFacts.Level.class))
                    .put(level, value)));
        }
        var frozen = new EnumMap<PlanFacts.Axis, Map<PlanFacts.Level, String>>(PlanFacts.Axis.class);
        axes.forEach((axis, byLevel) -> frozen.put(axis, Collections.unmodifiableMap(byLevel)));
        return Collections.unmodifiableMap(frozen);
    }

    /**
     * @return the posture the nearest level sets; empty where no level sets one, which makes a plan ask it
     */
    public Optional<Posture> posture() {
        for (var level : LEVELS) {
            var posture = levelValues(level).get(POSTURE_KEY);
            if (posture != null) {
                return Optional.of(Posture.fromWireName(posture)
                        .orElseThrow(() -> new IllegalStateException("an unvalidated posture: " + posture)));
            }
        }
        return Optional.empty();
    }

    /**
     * Adds the axes and the resolved posture to the facts of a composition.
     *
     * @param facts the builder of the facts
     * @return the builder with each axis's levels and the resolved posture set
     */
    public PlanFacts.Builder applyTo(PlanFacts.Builder facts) {
        var result = Objects.requireNonNull(facts, "facts");
        axes().forEach((axis, variants) -> variants.forEach((level, variant) -> result.axis(axis, level, variant)));
        posture().ifPresent(result::posture);
        return result;
    }

    /**
     * @param level a level
     * @return the raw values that level records, by key; never {@code null}
     */
    private Map<String, String> levelValues(PlanFacts.Level level) {
        return recorded.getOrDefault(level, Map.of());
    }

    /**
     * Builds {@link PlanAxisLevels}; a level that is not set records nothing.
     *
     * @since 0.1
     */
    public static final class Builder {

        private final Map<PlanFacts.Level, Map<String, String>> levels = new EnumMap<>(PlanFacts.Level.class);

        private Builder() {
        }

        /**
         * @param level  the level that records the values; {@link PlanFacts.Level#SHIPPED} records nothing
         * @param values the raw values of that level's document; {@code null} for none
         * @return this builder
         * @throws IllegalArgumentException if the level is the shipped one, or a value is refused
         */
        public Builder level(PlanFacts.Level level, @Nullable Map<String, String> values) {
            Objects.requireNonNull(level, "level");
            if (level == PlanFacts.Level.SHIPPED) {
                throw new IllegalArgumentException("the shipped level is the fallback, not a recorded level");
            }
            if (values != null) {
                levels.put(level, validate(level, values));
            }
            return this;
        }

        /**
         * @param level  the level, never the shipped one
         * @param values the raw values; {@code null} for a level that records none
         * @return the validated values by key, in the order of the document
         */
        private static Map<String, String> validate(PlanFacts.Level level, @Nullable Map<String, String> values) {
            if (values == null) {
                return Map.of();
            }
            var validated = new LinkedHashMap<String, String>();
            values.forEach((key, value) -> {
                Objects.requireNonNull(value, () -> "the '%s' value of '%s'".formatted(key, level.wireName()));
                if (POSTURE_KEY.equals(key)) {
                    if (Posture.fromWireName(value).isEmpty()) {
                        throw new IllegalArgumentException("'%s' is no posture, recorded at the level '%s'"
                                .formatted(value, level.wireName()));
                    }
                } else {
                    var axis = axisOf(key, level);
                    if (!axis.variants().contains(value)) {
                        throw new IllegalArgumentException(("'%s' is no value of the axis '%s', one of %s, recorded at"
                                + " the level '%s'").formatted(value, key, axis.variants(), level.wireName()));
                    }
                    if (!key.equals(axis.wireName())) {
                        throw new IllegalArgumentException("'%s' is no key of the axis '%s', recorded at the level '%s'"
                                .formatted(key, axis.wireName(), level.wireName()));
                    }
                }
                validated.put(key, value);
            });
            return Collections.unmodifiableMap(validated);
        }

        private static PlanFacts.Axis axisOf(String key, PlanFacts.Level level) {
            for (var axis : PlanFacts.Axis.values()) {
                if (axis.wireName().equals(key)) {
                    return axis;
                }
            }
            throw new IllegalArgumentException(("'%s' is neither a plan behaviour axis nor the posture, recorded at the"
                    + " level '%s'").formatted(key, level.wireName()));
        }

        /**
         * @return the levels
         */
        public PlanAxisLevels build() {
            return new PlanAxisLevels(Map.copyOf(levels));
        }
    }
}
