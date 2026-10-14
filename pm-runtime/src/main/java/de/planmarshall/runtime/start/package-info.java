/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
/**
 * The start sequence of the runtime up to the socket bind: the singleton lock
 * ({@link de.planmarshall.runtime.start.RuntimeLock}), the removal of a stale socket and runtime record under
 * it, the verification of the base directory and its permissions
 * ({@link de.planmarshall.runtime.start.BaseDirectoryCheck}), the bounded verification, and a fresh runtime token
 * ({@link de.planmarshall.runtime.start.RuntimeTokenFile}), in the order of
 * {@link de.planmarshall.runtime.start.StartupSequence}.
 * <p>
 * The bounded verification (step 4) does only work whose cost does not grow with the number of projects, plans or
 * jobs, and no store or unit work. It verifies the audit chain through the seam
 * {@link de.planmarshall.runtime.start.AuditChainVerification}, which the package
 * {@code de.planmarshall.runtime.audit} implements; while the assembly wires no implementation,
 * {@link de.planmarshall.runtime.start.AuditChainVerification#NONE} answers that no chain exists. A broken chain names
 * its reason ({@code link_broken}, {@code missing}, {@code reset}, {@code truncated} or {@code head_mismatch}) and, for
 * {@code link_broken}, the first broken line; it is logged with both and handed to the audit sink as an
 * {@code audit_chain_broken} record carrying {@code details.reason}, {@code details.first_broken_line} and
 * {@code details.os_principal}. It is never repaired and never stops the start, and the outcome of the verification
 * is carried in the ready outcome for the status to show.
 * <p>
 * The drain ({@link de.planmarshall.runtime.start.RuntimeDrain}) ends the runtime on {@code SIGTERM}/{@code SIGINT}
 * and on the stop endpoint: jobs, workers, the LSP pool and the web listener join it as
 * {@link de.planmarshall.runtime.start.DrainParticipant}s, which stop admitting, end what is idle, get quiet within
 * the drain timeout or have what still runs terminated, and close. The drain then removes the runtime record, the
 * token and the socket and releases the singleton lock. The drain timeout is {@code drain_timeout_seconds} of
 * {@code machine-config.json} ({@link de.planmarshall.runtime.start.DrainTimeout}), 600 seconds by default; an
 * invalid value falls back to the default.
 * <p>
 * The per-project recovery ({@link de.planmarshall.runtime.start.ProjectRecovery}) runs after the bind, never
 * before: lazily on the first call addressing a project, which waits at most the project recovery wait of 10 seconds
 * and is then answered {@code runtime_recovering}, and for every enrolled project
 * ({@link de.planmarshall.runtime.start.EnrolledProjects}, filled by the enrolment store through
 * {@code EnrolledProjects.of}) in a background pass, which then submits its background task once, the hand-over of
 * the owed audit records of projects no longer enrolled. Each project is recovered once per runtime instance, by
 * whichever trigger comes first. Its eleven steps ({@link de.planmarshall.runtime.start.RecoveryStep}) run in the
 * order of the specification, from the enrolment integrity and the owed enrolment audit records to the baseline
 * worktree sweep; a failed step stops the recovery of its project only. A step whose store or component does not
 * exist yet is a named seam that reports that it is not implemented in this runtime.
 * {@link de.planmarshall.runtime.start.ProjectRecovery#withEnrolment} builds the two enrolment steps from the
 * enrolment store; a constructor given a step list without them uses a seam for each.
 * <p>
 * Five steps are implemented:
 * <ul>
 * <li>{@link de.planmarshall.runtime.start.EnrolmentIntegrityStep}, step 1, settles a stopped write of the enrolment
 * pair and runs the enrolment check under the project's enrolment lock; a refusal fails the step and blocks only its
 * project.</li>
 * <li>{@link de.planmarshall.runtime.start.OwedEnrolmentAuditStep}, step 2, hands over the audit records the
 * project's enrolment writes still owe under the project's lock; a record the sink refuses again stays owed and is no
 * failure. Its {@code unenrolledPass} is the background task for the projects no longer enrolled.</li>
 * <li>{@link de.planmarshall.runtime.start.LeaseRecoveryStep}, over a seam that a later store fills, marks the leases of the project whose holder is dead
 * as orphaned in the machine lease stores it is given, and releases one only on positive evidence of abandonment
 * ({@link de.planmarshall.runtime.start.LeaseRecoveryStep.AbandonmentEvidence}, which finds none by default).</li>
 * <li>{@link de.planmarshall.runtime.start.JournalRollForwardStep}, over a seam, rolls forward the interrupted journal of every
 * scope of the project ({@link de.planmarshall.runtime.start.JournalRollForwardStep.ProjectScopes}) under the key of
 * the scope; an invariant violation or a refused store is a failure of its scope and modifies nothing.</li>
 * <li>{@link de.planmarshall.runtime.start.CrossScopeReconciliationStep}, over a seam, runs, after the roll-forward, the cross-scope
 * reconcilers registered for the project
 * ({@link de.planmarshall.runtime.start.CrossScopeReconciliationStep.ProjectReconcilers}).</li>
 * </ul>
 * <p>
 * The periodic sweep ({@link de.planmarshall.runtime.start.PeriodicSweep}) runs every hour
 * ({@link de.planmarshall.runtime.start.PeriodicSweep#INTERVAL}, 3600 seconds) over the enrolled projects whose
 * recovery has finished; a project still recovering is taken up by the next run. Its two steps
 * ({@link de.planmarshall.runtime.start.SweepStep}) are
 * {@link de.planmarshall.runtime.start.PendingEntryRecordsSweep}, which removes the pending entry records older than
 * {@link de.planmarshall.runtime.start.PeriodicSweep#PENDING_ENTRY_MAX_AGE} (86400 seconds) in one transaction of the
 * workspace scope, one owed {@code pending_entry_removed} audit record per record, each under a
 * {@code transaction_id} of its own, and
 * {@link de.planmarshall.runtime.start.FencedSessionGenerationsSweep}, which removes the session generations fenced
 * longer ago under the queue lock. The assembly registers both. Both durations are compiled constants, not
 * configuration. A failing step is logged and does not stop the run; the drain stops the schedule.
 * <p>
 * The version-mismatch detection ({@link de.planmarshall.runtime.start.RelayVersionCheck}) compares the
 * {@code PM-MCP-Relay-Version} header of every relayed request with the version of the runtime; a request without
 * the header is ignored. A relay of another version forwards normally. The mismatch is operator-only
 * ({@code PM-DIST-4}): once per distinct relay version per runtime instance it is handed to the audit sink as a
 * {@code relay_version_mismatch} record with both versions and logged, and the latest mismatch is kept, with the
 * recommended {@code pm-operator drain}, for {@code pm-operator status}. No representation carries it, and the
 * detection state is not persisted.
 * <p>
 * Besides the on-demand start, the runtime can register itself as a per-user service that starts it at login and
 * restarts it on failure (the service-managed start mode): {@link de.planmarshall.runtime.start.ServiceUnitFile}
 * renders, writes and removes the {@code launchd} LaunchAgent or the {@code systemd --user} unit,
 * {@link de.planmarshall.runtime.start.ServiceManager} loads and unloads it through {@code launchctl} or
 * {@code systemctl}, and {@link de.planmarshall.runtime.start.ServiceUnitInstaller} combines both into install and
 * uninstall.
 */
package de.planmarshall.runtime.start;
