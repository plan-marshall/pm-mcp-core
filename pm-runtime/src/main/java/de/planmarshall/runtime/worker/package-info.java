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
 * The worker supervisor, one per project and role: starting, acknowledgement, silence and exit detection,
 * recycling, ending and fencing of workers (PM-WORK-3, PM-WF-14).
 * <p>
 * The {@link de.planmarshall.runtime.worker.WorkerSupervisor} starts a role's workers within its {@code count} and
 * keeps their {@link de.planmarshall.runtime.worker.WorkerGeneration} registry, bounded by the start-failure guard, the
 * verification-record gate and the machine model slots. Every generation's state is written into its job record's
 * {@code worker} block through the landed {@code pm-core} record, and no value of a host or a role is compiled into
 * the package: the timing comes from the harness's client profile and the recycling rules from the role artifact (see
 * {@link de.planmarshall.runtime.worker.SupervisorEnvironment}).
 * <p>
 * The package composes and starts through the landed contracts of {@code runtime.launch} and {@code runtime.job} — it
 * keeps no launch composition and no process of its own — and it reaches the task protocol, which {@code pm-runtime}
 * may not reference, through {@link de.planmarshall.runtime.worker.WorkerProtocol}, adapted by the assembly
 * {@code pm-mcp-server}. The machine model slots are reached through
 * {@link de.planmarshall.runtime.worker.ModelSlots}, whose production adapter delegates to the landed
 * {@link de.planmarshall.runtime.launch.ModelSlotScheduler}.
 * <p>
 * Specification:
 * <ul>
 * <li>{@code doc/specification/model-work/03-supervision-and-recycling.adoc}</li>
 * <li>{@code doc/specification/model-work/04-worker-launch.adoc}</li>
 * <li>{@code doc/specification/timeouts/01-job-budgets-and-waits.adoc} (Worker Supervision &amp; Recycling)</li>
 * </ul>
 * Developer guide: {@code pm-runtime/doc/developer/runtime-worker.adoc}.
 */
@NullMarked
package de.planmarshall.runtime.worker;

import org.jspecify.annotations.NullMarked;
