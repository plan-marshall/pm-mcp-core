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

import java.util.Objects;
import java.util.OptionalLong;

/**
 * One figure of a metrics row (PM-WF-14, Store Schemas § Metrics &amp; Quality Report): a measured value, {@code
 * unmeasured}, or {@code not_applicable}.
 * <p>
 * The three values are distinct and none of them is a default. A figure the harness does not report is
 * {@code unmeasured} and never {@code 0}; a figure that does not exist for the harness by design is
 * {@code not_applicable} and counts {@code 0}, so the harness is still measured; a measured {@code 0} is a third value
 * of its own. The distinction is what keeps a token total from silently reading a missing telemetry figure as a real
 * zero.
 *
 * @param state the state of the figure
 * @param value the measured value; {@code 0} for the two unmeasured states, which carry no value
 * @since 0.1
 */
public record Measurement(State state, long value) {

    /** The state of a measurement. The set is closed. */
    public enum State {
        /** The harness reported the figure. */
        MEASURED,
        /** The harness did not report the figure, or its output could not be parsed. */
        UNMEASURED,
        /** The figure does not exist for the harness by design; it counts {@code 0}. */
        NOT_APPLICABLE
    }

    /**
     * @throws NullPointerException     if the state is {@code null}
     * @throws IllegalArgumentException if the value is negative or is set for a state that carries none
     */
    public Measurement {
        Objects.requireNonNull(state, "state");
        if (value < 0) {
            throw new IllegalArgumentException("A measurement is not negative: " + value);
        }
        if (state != State.MEASURED && value != 0) {
            throw new IllegalArgumentException("Only a measured figure carries a value: " + state);
        }
    }

    /**
     * @param value the measured value, not negative
     * @return the measured figure
     */
    public static Measurement measured(long value) {
        return new Measurement(State.MEASURED, value);
    }

    /** @return the figure the harness did not report */
    public static Measurement unmeasured() {
        return new Measurement(State.UNMEASURED, 0);
    }

    /** @return the figure that does not exist for the harness by design, counted {@code 0} */
    public static Measurement notApplicable() {
        return new Measurement(State.NOT_APPLICABLE, 0);
    }

    /** @return whether the harness reported the figure */
    public boolean isMeasured() {
        return state == State.MEASURED;
    }

    /** @return whether the figure exists for the harness by design; it counts {@code 0} then */
    public boolean isNotApplicable() {
        return state == State.NOT_APPLICABLE;
    }

    /**
     * @return the measured value; {@code 0} for {@code not_applicable}, and nothing for {@code unmeasured}
     */
    public OptionalLong measuredValue() {
        return state == State.MEASURED ? OptionalLong.of(value) : OptionalLong.empty();
    }
}
