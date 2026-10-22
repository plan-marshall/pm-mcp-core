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

import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.spi.WorkflowClock;

/**
 * Selects the wait the server holds open for a model (PM-WF-8, PM-IMPL-4): with no model involvement, from the
 * calling connection's client profile and the request's progress token.
 * <p>
 * The selection is one rule in one place. A progress wait needs both the profile's
 * {@code connection.progress_resets_timeout} and a {@code _meta.progressToken} in the request; without either the
 * call is held by the bounded long-poll. For a {@code pm_build} that qualifies, the wait holds up to the progress wait
 * upper bound; a {@code pm_wait} keeps the bounded wait window in both cases, and the progress frames hold the host's
 * timer through it.
 *
 * @since 0.1
 */
public final class WaitSelection {

    private WaitSelection() {
    }

    /**
     * Selects the wait of one call and starts it at the clock's instant.
     *
     * @param connection    the effective connection block of the calling connection's client profile
     * @param progressToken the request's {@code _meta.progressToken}; empty when the request carries none, and a blank
     *                      token counts as none
     * @param call          the call held open
     * @param settings      the machine-wide progress values
     * @param clock         the clock the wait starts at
     * @return the wait
     * @throws NullPointerException if an argument is {@code null}
     */
    public static ModelWait select(ConnectionSettings connection, Optional<String> progressToken, ModelCall call,
            WaitSettings settings, WorkflowClock clock) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(progressToken, "progressToken");
        Objects.requireNonNull(call, "call");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(clock, "clock");
        var qualifies = connection.progressResetsTimeout() && progressToken.filter(token -> !token.isBlank()).isPresent();
        if (!qualifies) {
            return new ModelWait.Bounded(call, clock.now(), connection.boundedWait());
        }
        var window = call == ModelCall.PM_BUILD ? settings.progressWaitMax() : connection.boundedWait();
        return new ModelWait.Progress(call, clock.now(), window, settings.progressInterval());
    }
}
