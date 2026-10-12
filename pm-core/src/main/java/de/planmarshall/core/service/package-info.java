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
 * The service interfaces the foundation declares and {@code pm-runtime} implements: the job runner, the secret
 * source and plan locking. The engine reaches the runtime through them alone.
 * <p>
 * Locking is the {@link de.planmarshall.core.service.LockManager lock manager}, through which every lock acquisition
 * of the runtime goes, with the {@link de.planmarshall.core.service.LockTransaction transaction} that holds the
 * locks. An acquisition against the total lock order is refused with the failure class
 * {@link de.planmarshall.core.service.InternalFaultException internal_fault} and recorded through the audit seam: an
 * {@link de.planmarshall.core.service.AuditEvent audit event} handed to the
 * {@link de.planmarshall.core.service.AuditSink audit sink}, which the audit store implements.
 * <p>
 * The {@link de.planmarshall.core.service.LiveWork live work} of a project, its non-terminal plans and epics and its
 * running jobs, is what refuses its {@code unenrol} with {@code scope_active} (PM-EXEC-2); the refusal names every
 * item. The enrolment writes and refusals are recorded through the audit seam as well, each by the factory of its
 * event.
 * <p>
 * Specification:
 * <ul>
 * <li>{@code doc/specification/module-structure.adoc} (Modules, {@code pm-core})</li>
 * <li>{@code doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc}</li>
 * <li>{@code doc/specification/store-schemas/13-leases-integrity-testing.adoc} (Leases, Integrity Invariants)</li>
 * <li>{@code doc/specification/cli-and-security/02-credentials-and-operator.adoc} (Enrolment Integrity; Enrolment,
 * Lifecycle Gate &amp; Release)</li>
 * </ul>
 */
package de.planmarshall.core.service;
