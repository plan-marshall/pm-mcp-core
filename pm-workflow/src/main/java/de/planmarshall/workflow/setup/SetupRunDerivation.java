/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.setup;

import java.util.Objects;

import de.planmarshall.core.config.SetupRun;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

/**
 * The {@code setup_run} a project setup records at its start state (State Catalogue § Project Setup, {@code
 * setup.entry}): it is derived from server facts and the entry the caller named, never from a model, and an interrupted
 * first run keeps the run it already recorded.
 * <p>
 * The rule reads: the recorded run wins, because an interrupted first run whose {@code .marshall/config/} already exists
 * is still a first run; a project whose primary checkout carries no {@code .marshall/config/} is on its first run, an
 * entry at {@code aspect} is that one aspect, an entry at {@code integrations} continues the first run, and every other
 * call is a reconcile run.
 *
 * @since 0.1
 */
@UtilityClass
public final class SetupRunDerivation {

    /**
     * Derives the run a setup call records.
     *
     * @param entry                  the entry the caller named on {@code project.setup}
     * @param configDirectoryPresent whether the primary checkout carries {@code .marshall/config/}
     * @param recorded               the run an interrupted call already recorded, if any; may be {@code null}
     * @return the run to record
     * @throws NullPointerException     if the entry is {@code null}
     * @throws IllegalArgumentException if {@code recorded} is the {@code integrations} entry, which is never a recorded
     *                                  {@code setup_run}
     */
    public static SetupRun derive(SetupRun entry, boolean configDirectoryPresent, @Nullable SetupRun recorded) {
        Objects.requireNonNull(entry, "entry");
        if (recorded == SetupRun.INTEGRATIONS) {
            throw new IllegalArgumentException("An integrations entry is no recorded setup_run");
        }
        if (recorded != null) {
            return recorded;
        }
        if (entry == SetupRun.INTEGRATIONS) {
            return SetupRun.FIRST_RUN;
        }
        if (!configDirectoryPresent) {
            return SetupRun.FIRST_RUN;
        }
        return entry == SetupRun.ASPECT ? SetupRun.ASPECT : SetupRun.RECONCILE;
    }
}
