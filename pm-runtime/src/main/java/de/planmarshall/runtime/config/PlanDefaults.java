/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.config;

import java.util.List;
import java.util.Objects;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * The machine's default of each plan behaviour axis, {@code plan_defaults} of {@code machine-config.json} (Workflow DSL
 * § Plan Behaviour Axes, Store Schemas § Machine Configuration Store): the machine's level of the three axes and its
 * optional posture. The machine's settings write it, and a plan's composition reads it as the machine level of each axis
 * ({@code PlanAxisLevels} of {@code pm-workflow}); the project's value and the plan's own override it.
 * <p>
 * The values are the wire names of the closed variant lists the schema declares. This record keeps the schema's
 * {@code enum} values as its own closed lists, so a value the schema admits is never refused here and a value it does not
 * is never written; {@code MachineConfigurationSchemaParityTest} reads the two lists out of the schema and fails on drift.
 *
 * @param landing the machine's landing default, {@code landing.pull-request}, {@code landing.main} or
 *                {@code landing.files}
 * @param method  the machine's method default, {@code method.test-first} or {@code method.tests-after}
 * @param asking  the machine's asking default, {@code ask.every-phase}, {@code ask.key-points} or
 *                {@code ask.questions-only}
 * @param posture the machine's posture, {@code minimal}, {@code standard} or {@code full}; {@code null} where the machine
 *                sets none, which makes a plan ask it in {@code init.options}
 * @since 0.1
 */
@Builder
public record PlanDefaults(String landing, String method, String asking, @Nullable String posture) {

    /** The schema's variants of {@code plan_defaults.landing}. */
    public static final List<String> LANDING_VARIANTS = List.of("landing.pull-request", "landing.main",
            "landing.files");

    /** The schema's variants of {@code plan_defaults.method}. */
    public static final List<String> METHOD_VARIANTS = List.of("method.test-first", "method.tests-after");

    /** The schema's variants of {@code plan_defaults.asking}. */
    public static final List<String> ASKING_VARIANTS = List.of("ask.every-phase", "ask.key-points",
            "ask.questions-only");

    /** The schema's postures of {@code plan_defaults.posture}. */
    public static final List<String> POSTURES = List.of("minimal", "standard", "full");

    /** The schema's keys of {@code plan_defaults}, the closed member list. */
    public static final List<String> KEYS = List.of("landing", "method", "asking", "posture");

    /**
     * Refuses a value outside its axis's closed list; a missing landing, method or asking is refused, and a posture may
     * be absent.
     *
     * @throws NullPointerException     if landing, method or asking is {@code null}
     * @throws IllegalArgumentException if a value is not one the schema admits
     */
    public PlanDefaults {
        Objects.requireNonNull(landing, "landing");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(asking, "asking");
        requireVariant("landing", landing, LANDING_VARIANTS);
        requireVariant("method", method, METHOD_VARIANTS);
        requireVariant("asking", asking, ASKING_VARIANTS);
        if (posture != null) {
            requireVariant("posture", posture, POSTURES);
        }
    }

    /**
     * @return the machine defaults the schema applies while the document records none: the shipped plan defaults and no
     *         posture
     */
    public static PlanDefaults shipped() {
        return new PlanDefaults("landing.pull-request", "method.tests-after", "ask.questions-only", null);
    }

    /**
     * Reads a {@code plan_defaults} member at the schema's semantics: a member the document does not set takes the
     * schema default, and a posture it does not set stays unset.
     *
     * @param landing the recorded landing default; {@code null} where the document sets none
     * @param method  the recorded method default; {@code null} where the document sets none
     * @param asking  the recorded asking default; {@code null} where the document sets none
     * @param posture the recorded posture; {@code null} where the document sets none
     * @return the defaults the level sets
     * @throws IllegalArgumentException if a recorded value is not one the schema admits
     */
    public static PlanDefaults of(@Nullable String landing, @Nullable String method, @Nullable String asking,
            @Nullable String posture) {
        var shipped = shipped();
        return new PlanDefaults(landing == null ? shipped.landing() : landing,
                method == null ? shipped.method() : method,
                asking == null ? shipped.asking() : asking, posture);
    }

    private static void requireVariant(String key, String value, List<String> admitted) {
        if (!admitted.contains(value)) {
            throw new IllegalArgumentException("'%s' is no value of '%s', one of %s".formatted(value, key, admitted));
        }
    }
}
