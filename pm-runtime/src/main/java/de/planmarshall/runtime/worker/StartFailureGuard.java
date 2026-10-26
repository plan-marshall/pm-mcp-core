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

import java.time.Duration;
import java.time.InstantSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import de.cuioss.tools.logging.CuiLogger;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;

/**
 * The start-failure guard of one supervisor (PM-WORK-3, Start-Failure Guard): it counts the consecutive starts of one
 * role on one harness that failed before the worker's first call, holds the role's starts there once the count reaches
 * {@value #HOLD_THRESHOLD}, and admits one probe after {@link #PROBE_BACKOFF} to find out whether the harness recovered.
 * <p>
 * A start fails when the worker exits before its first call — the launch's job record carries no {@code connected_at} —
 * and when the runtime refuses the launch before its spawn. A worker of a {@code zero_tool} role makes no call at all,
 * so it is never counted here: the supervisor consults the guard for a role that has a connection only.
 * <p>
 * The guard owns four transitions of the specification:
 * <ul>
 *   <li><em>Count</em>: {@link #startFailed} counts one failure per role and harness and reports it, bounded and
 *       redacted, through the audit log and as its returned value;</li>
 *   <li><em>Hold</em>: at {@value #HOLD_THRESHOLD} consecutive failures the guard holds, and
 *       {@link WorkerProtocol#closeUnavailable} closes the role's queued and new tasks {@code lost} with the reason
 *       {@code role_unavailable};</li>
 *   <li><em>Probe</em>: after the backoff the next start of the role on that harness is one probe instead of a hold,
 *       and a probe that fails restarts the backoff;</li>
 *   <li><em>Clear</em>: {@link #firstCall} resets the count on the first call of any worker of the role on the
 *       harness — a probe's first call clears the guard — and {@link #clear} does the same when the role's
 *       configuration changes or the harness is enrolled again.</li>
 * </ul>
 * The count is kept per supervisor in a {@link StartFailureState} per role and harness, rebuilt from the supervisor's
 * generation registry when the guard is constructed, so a guard built beside a supervisor that already started workers
 * resumes the count of the generations that exited before their first call instead of counting from zero. The guard
 * reads the instant of the probe backoff from the injected clock and logs its counted failures and its holds through
 * {@code WORKER_START_FAILED} and {@code WORKER_START_GUARD_HELD}.
 *
 * @since 0.1
 */
public final class StartFailureGuard implements SupervisorEnvironment.StartGuard {

    /** The number of consecutive start failures after which the guard holds. */
    public static final int HOLD_THRESHOLD = 3;

    /** The backoff after which the next start of the role on the harness is one probe. */
    public static final Duration PROBE_BACKOFF = Duration.ofSeconds(1800);

    private static final CuiLogger LOGGER = new CuiLogger(StartFailureGuard.class);

    private final String projectId;

    private final Supplier<List<WorkerGeneration>> generations;

    private final WorkerProtocol protocol;

    private final AuditSink audit;

    private final InstantSource clock;

    private final Map<StartFailureState.Key, StartFailureState> states = new HashMap<>();

    /**
     * @param projectId   the project whose starts the report names
     * @param generations the supervisor's registered generations, in start order, which the count is rebuilt from
     * @param protocol    the module-boundary contract that closes the role's tasks when the guard holds
     * @param audit       where the report of a counted failure is handed over
     * @param clock       the clock of the probe backoff
     * @throws NullPointerException if a collaborator is {@code null}
     */
    public StartFailureGuard(String projectId, Supplier<List<WorkerGeneration>> generations, WorkerProtocol protocol,
            AuditSink audit, InstantSource clock) {
        this.projectId = Objects.requireNonNull(projectId, "projectId");
        this.generations = Objects.requireNonNull(generations, "generations");
        this.protocol = Objects.requireNonNull(protocol, "protocol");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.clock = Objects.requireNonNull(clock, "clock");
        rebuild();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Once the probe backoff has passed, this answer admits exactly one start as the probe: the caller that receives
     * {@code false} for a holding guard is that probe, and every later start is held again until the probe's first call
     * or its failure settles it.
     */
    @Override
    public synchronized boolean holds(String role, String harness) {
        var key = new StartFailureState.Key(role, harness);
        var state = states.get(key);
        if (state == null || !state.held()) {
            return false;
        }
        if (state.probeInFlight() || clock.instant().isBefore(state.probeDue())) {
            return true;
        }
        states.put(key, state.withProbeInFlight(true));
        return false;
    }

    /**
     * {@inheritDoc}
     * <p>
     * The specification requires the clear when the role's configuration changes or the harness is enrolled again; the
     * next start of the role on that harness is then admitted as if no start had failed.
     */
    @Override
    public synchronized void clear(String role, String harness) {
        states.remove(new StartFailureState.Key(role, harness));
    }

    /**
     * Records the first call of a worker of the role on the harness: the count resets, so the guard is open again. A
     * probe worker's first call clears the hold the same way, and a worker that connects after fewer failures resets a
     * count that had not reached the hold.
     *
     * @param role    the role
     * @param harness the client id of the harness
     * @throws NullPointerException if a value is {@code null}
     */
    public synchronized void firstCall(String role, String harness) {
        states.remove(new StartFailureState.Key(role, harness));
    }

    /**
     * Counts one start failure of the role on the harness and reports it: the {@code WORKER_START_FAILED} record carries
     * the consecutive number and the bounded, redacted detail, and the guard hands one
     * {@link AuditEvent#workerStartFailed} record to the audit log per counted failure. Reaching the hold closes the
     * role's queued and new tasks through {@link WorkerProtocol#closeUnavailable} and logs
     * {@code WORKER_START_GUARD_HELD}; a failure of a holding guard is a failed probe and restarts the backoff the same
     * way.
     *
     * @param role    the role whose start failed
     * @param harness the client id of the harness the start failed on
     * @param logTail the failed worker's own error text, which the report bounds and redacts
     * @return the bounded, redacted report of this failure, which the assembly hands to its clients
     * @throws NullPointerException if a value is {@code null}
     */
    public synchronized StartFailureReport startFailed(String role, String harness, String logTail) {
        var key = new StartFailureState.Key(role, harness);
        var previous = states.getOrDefault(key, StartFailureState.OPEN);
        var counted = previous.consecutiveFailures() + 1;
        var report = StartFailureReport.of(role, harness, logTail);
        LOGGER.warn(WARN.WORKER_START_FAILED, role, harness, counted, report.detail());
        audit.append(AuditEvent.workerStartFailed(projectId, role, harness, report.detail()));
        if (previous.held() || counted >= HOLD_THRESHOLD) {
            states.put(key, new StartFailureState(counted, true, clock.instant().plus(PROBE_BACKOFF), false,
                    report.detail()));
            LOGGER.warn(WARN.WORKER_START_GUARD_HELD, role, harness, counted);
            protocol.closeUnavailable(role, harness);
        } else {
            states.put(key, new StartFailureState(counted, false, null, false, report.detail()));
        }
        return report;
    }

    /**
     * Rebuilds the count from the supervisor's generation registry: the generations of one role and harness are read in
     * start order, every generation that exited before its first call counts one failure, any generation that reached
     * another lifecycle state resets the count, and one that is still starting neither counts nor resets. A rebuilt
     * count at or past the hold holds, with its probe due one backoff from now and no failure text, because the
     * registry keeps none.
     */
    private void rebuild() {
        var now = clock.instant();
        for (var generation : generations.get()) {
            var key = new StartFailureState.Key(generation.role(), generation.harness());
            var counted = states.getOrDefault(key, StartFailureState.OPEN).consecutiveFailures();
            if (generation.lifecycle() == WorkerLifecycle.START_FAILED) {
                counted++;
            } else if (generation.lifecycle() != WorkerLifecycle.STARTING) {
                counted = 0;
            } else {
                continue;
            }
            if (counted == 0) {
                states.remove(key);
            } else if (counted >= HOLD_THRESHOLD) {
                states.put(key, new StartFailureState(counted, true, now.plus(PROBE_BACKOFF), false, null));
            } else {
                states.put(key, new StartFailureState(counted, false, null, false, null));
            }
        }
    }
}
