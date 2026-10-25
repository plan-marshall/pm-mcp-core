/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.worker;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import de.planmarshall.core.role.RoleForm;
import de.planmarshall.runtime.start.DrainParticipant;

/**
 * The supervisor as a drain participant (PM-WORK-3, Ending a Worker; Runtime &amp; Interaction Model, Drain): the
 * runtime offers no new task, answers the waits of idle warm workers with {@code end}, lets running tasks continue up
 * to the drain timeout, then ends the remaining workers by force. Their tasks stay queued and are offered again after
 * the next start, without counting a re-offer.
 *
 * @since 0.1
 */
public final class WorkerDrainParticipant implements DrainParticipant {

    /** The name of the participant in the log and in the drain outcome. */
    public static final String NAME = "workers";

    private final WorkerSupervisor supervisor;
    private final WorkerEnding ending;
    private final List<String> terminated = new ArrayList<>();

    /**
     * @param supervisor the supervisor of the role
     * @param ending     the one end path of its generations
     * @throws NullPointerException if a collaborator is {@code null}
     */
    public WorkerDrainParticipant(WorkerSupervisor supervisor, WorkerEnding ending) {
        this.supervisor = Objects.requireNonNull(supervisor, "supervisor");
        this.ending = Objects.requireNonNull(ending, "ending");
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void stopAdmitting() {
        supervisor.stopAdmitting();
    }

    @Override
    public void endIdle() {
        for (var generation : supervisor.generations()) {
            if (generation.form() == RoleForm.WARM && generation.lifecycle() == WorkerLifecycle.WAITING) {
                ending.end(generation, EndCause.DRAIN);
            }
        }
    }

    @Override
    public boolean awaitQuiet(Instant deadline) throws InterruptedException, IOException {
        return supervisor.awaitQuiet(deadline);
    }

    @Override
    public List<String> terminateRemaining() {
        for (var generation : supervisor.generations()) {
            if (!generation.terminal()) {
                ending.forceEnd(generation, EndCause.DRAIN);
                terminated.add(generation.generationId());
            }
        }
        return List.copyOf(terminated);
    }

    @Override
    public void close() {
        supervisor.environment().timers().close();
    }
}
