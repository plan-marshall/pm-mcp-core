/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.engine;

import java.time.Duration;
import java.util.Objects;

/**
 * The {@code connection} block of a client profile, as far as a wait needs it (PM-WF-8; Client Profiles § Worker
 * Block): the bounded wait window, the harness's idle timeout, and whether a progress frame resets the harness's
 * tool-call timer.
 * <p>
 * The values are the effective ones of the profile set that counts for the calling connection, an operator override in
 * {@code machine-config.json} included. Profile validation enforces the bounds and the relation between the bounded
 * wait and the idle timeout; this record repeats them so that a wait is never built from values the profile validation
 * would refuse.
 *
 * @param boundedWait           the bounded wait window, {@code connection.bounded_wait_seconds}, 15 to 3600 seconds
 * @param idleTimeout           the harness's idle timeout, {@code connection.idle_timeout_seconds}
 * @param progressResetsTimeout whether a {@code notifications/progress} frame resets the harness's tool-call timer
 * @since 0.1
 */
public record ConnectionSettings(Duration boundedWait, Duration idleTimeout, boolean progressResetsTimeout) {

    /** The shortest bounded wait window a profile admits. */
    public static final Duration MIN_BOUNDED_WAIT = Duration.ofSeconds(15);

    /** The longest bounded wait window a profile admits. */
    public static final Duration MAX_BOUNDED_WAIT = Duration.ofSeconds(3600);

    /** The margin the idle timeout must leave above a bounded wait when progress resets no timer. */
    public static final Duration IDLE_MARGIN = Duration.ofSeconds(30);

    /**
     * @throws NullPointerException     if a component is {@code null}
     * @throws IllegalArgumentException if the bounded wait is outside 15 to 3600 seconds, or, for a harness whose
     *                                  progress resets no timer, the bounded wait is not at least the 30-second
     *                                  margin below the idle timeout
     */
    public ConnectionSettings {
        Objects.requireNonNull(boundedWait, "boundedWait");
        Objects.requireNonNull(idleTimeout, "idleTimeout");
        if (boundedWait.compareTo(MIN_BOUNDED_WAIT) < 0 || boundedWait.compareTo(MAX_BOUNDED_WAIT) > 0) {
            throw new IllegalArgumentException("the bounded wait %s is outside %s to %s"
                    .formatted(boundedWait, MIN_BOUNDED_WAIT, MAX_BOUNDED_WAIT));
        }
        if (idleTimeout.isNegative() || idleTimeout.isZero()) {
            throw new IllegalArgumentException("the idle timeout %s is not positive".formatted(idleTimeout));
        }
        if (!progressResetsTimeout && boundedWait.compareTo(idleTimeout.minus(IDLE_MARGIN)) > 0) {
            var message = "the bounded wait %s is not at least %s below the idle timeout %s of a harness whose"
                    + " progress resets no timer";
            throw new IllegalArgumentException(message.formatted(boundedWait, IDLE_MARGIN, idleTimeout));
        }
    }
}
