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
 * The JSON and JSONL state stores (PM-IMPL-2, PM-IMPL-7): how a store is read, written, recovered and verified, the
 * store layout of workspace, project and machine, and the global lock order.
 * <ul>
 * <li><em>Format versions and refusals</em>: every document and record carries a {@code format_version}, and a reader
 * accepts exactly the current one. What it refuses is a {@link de.planmarshall.core.store.StoreRefusal} naming the
 * file and both versions, and it is never rewritten; the codecs read through the one strict reading of
 * {@code StrictJson}.</li>
 * <li><em>Writes and lock-free reads</em>: a store is replaced by atomic rename
 * ({@link de.planmarshall.core.store.AtomicStoreFile}) and read without a lock
 * ({@link de.planmarshall.core.store.StoreSnapshot}); a JSONL store grows only by appends under its lock, and a torn
 * last line is repaired at the tail with an audit record ({@link de.planmarshall.core.store.JsonlStoreFile}).</li>
 * <li><em>The transaction journal</em>: the stores of a scope ({@link de.planmarshall.core.store.JournalScope}) are
 * written by a {@link de.planmarshall.core.store.ScopeTransaction} that stages every target, writes its
 * {@link de.planmarshall.core.store.TransactionJournal}, applies the targets, records their digests and closes the
 * journal; {@link de.planmarshall.core.store.JournalRecovery} rolls a stopped transaction forward. A target replaces,
 * appends to or deletes a store.</li>
 * <li><em>Digests</em>: the {@link de.planmarshall.core.store.DigestSidecar} of a scope records the size and SHA-256
 * of its stores, only through the journal. {@link de.planmarshall.core.store.ScopeIntegrity} decides an edit outside
 * a transaction only under the lock of the scope, reports it as unverified without the lock, and re-records a digest
 * when an edit is accepted.</li>
 * <li><em>Writes across scopes</em>: an {@link de.planmarshall.core.store.OrderedWrite} writes the authoritative store
 * first and every derived store after it, one lock at a time, and a
 * {@link de.planmarshall.core.store.CrossScopeReconciler} completes the derived writes a crash left undone.</li>
 * <li><em>Locks and leases</em>: the key of every lock in the total lock order
 * ({@link de.planmarshall.core.store.LockKey}) and the lease stores of the machine.</li>
 * <li><em>Project enrolments</em> (PM-EXEC-2, PM-MIG-1): the typed, closed enrolment of a project
 * ({@link de.planmarshall.core.store.ProjectEnrolment}, read and written by
 * {@link de.planmarshall.core.store.EnrolmentCodec}), its integrity sidecar record
 * ({@link de.planmarshall.core.store.EnrolmentSidecar}), the store of one file per project under its own lock
 * ({@link de.planmarshall.core.store.EnrolmentStore}), which stages the file and its sidecar completely before it
 * renames them into place and settles a stopped write of the pair
 * ({@link de.planmarshall.core.store.PairRecovery}), and the lifecycle gate compiled into the release
 * ({@link de.planmarshall.core.store.LifecycleGate}). The key and the HMAC belong to the runtime, not to this
 * package.</li>
 * <li><em>The workflow state store</em> (PM-ARCH-6, PM-WF-16): the status document {@code mcp/status.json} of a
 * plan, epic or workspace scope ({@link de.planmarshall.core.store.WorkflowStatus}), read and written by
 * {@link de.planmarshall.core.store.WorkflowStatusCodec}: the members the engine owns are typed (position, call
 * stack, loop-back accounting, bindings, condition overlay), every other member of the closed schema is kept as raw
 * JSON and written back unchanged. The retry counts of the server-rule retries are typed as
 * {@link de.planmarshall.core.store.RetryCounters} and never reset by a restart (PM-WF-7). The waiver requests of a
 * plan are typed as its member {@code waiver_requests[]} ({@link de.planmarshall.core.store.WaiverRequest} with the
 * closed {@link de.planmarshall.core.store.WaiverRequestOutcome}), without a time limit (PM-SEC-3).
 * {@link de.planmarshall.core.store.WorkflowStatusStore} reads the store without a lock and writes it only as a
 * target of the caller's {@link de.planmarshall.core.store.ScopeTransaction}.</li>
 * <li><em>The pending entry record</em> (PM-CLIENT-1): {@code pending/<entry_id>.json} of the workspace scope
 * ({@link de.planmarshall.core.store.PendingEntryRecord}, read and written by
 * {@link de.planmarshall.core.store.PendingEntryCodec}), the state store of the bootstrap of an entry a client opens
 * for a scope that does not exist yet, named by its {@link de.planmarshall.core.store.EntryId}: the envelope and the
 * members the engine owns are typed, {@code facts}, {@code source} and {@code options} kept as raw JSON.
 * {@link de.planmarshall.core.store.PendingEntryStore} allocates a fresh id and creates, replaces and removes a record
 * as a target of the caller's workspace {@link de.planmarshall.core.store.ScopeTransaction}, and lists only the names
 * that parse as entry ids.</li>
 * <li><em>The job records</em> (PM-EXEC-4, PM-ARCH-7): {@code jobs/<job_id>.json} of a project, of the machine scope
 * and of an onboarding entry ({@link de.planmarshall.core.store.JobRecord}, read and written by
 * {@link de.planmarshall.core.store.JobRecordCodec}), the write-ahead journal of one job named by its
 * {@link de.planmarshall.core.store.JobId}, which owns the job kinds: every member this package writes is typed,
 * {@code worker} and {@code gate_facts} kept as raw JSON. {@link de.planmarshall.core.store.JobRecordStore} allocates a
 * fresh id and writes the record at its fixed points (queued, write-ahead, post-spawn, fate), each an atomic replace
 * under the lock of the scope that owns the job: the plan, epic or workspace lock, the project's queue lock for a
 * worker, the machine queue lock {@code machine/locks/queue.lock} for the machine scope and the entry lock
 * {@code onboardings/locks/<entry_id>.lock} for a probe of an onboarding entry. The raw log {@code <job_id>.log} lies
 * beside the record.</li>
 * <li><em>The audit log</em> (PM-SEC-6): {@code logs/audit.jsonl} as a hash chain
 * ({@link de.planmarshall.core.store.AuditLog}) of records ({@link de.planmarshall.core.store.AuditRecord}), each
 * naming the SHA-256 of the line before it, appended through the JSONL append path under the audit lock, the leaf of
 * the lock order. The formats of the head {@code logs/audit.head} ({@link de.planmarshall.core.store.AuditHeadRecord})
 * and of the audit index {@code logs/audit-index.jsonl} ({@link de.planmarshall.core.store.AuditIndexEntry}) are read
 * and written, with the records, by {@link de.planmarshall.core.store.AuditCodec}; the keyed head, the rotation and the
 * index are the runtime's, behind the seam {@link de.planmarshall.core.store.AuditAnchor} the log calls under the
 * audit lock. {@link de.planmarshall.core.store.AuditLogSink} is the production audit sink: every record a component
 * hands over becomes one record of the chain.</li>
 * <li><em>The operator's waivers</em> (PM-SEC-3): the append-only waiver log {@code waivers.jsonl} of a plan
 * ({@link de.planmarshall.core.store.WaiverLog} of {@link de.planmarshall.core.store.WaiverRecord}, read and written
 * by {@link de.planmarshall.core.store.WaiverRecordCodec}) and its consumption log {@code waiver-consumptions.jsonl}
 * ({@link de.planmarshall.core.store.WaiverConsumptionLog} of
 * {@link de.planmarshall.core.store.WaiverConsumptionRecord}, read and written by
 * {@link de.planmarshall.core.store.WaiverConsumptionCodec}), each appended only as a target of the plan's
 * {@link de.planmarshall.core.store.ScopeTransaction}; a revocation is a new line, and the last readable line of a
 * waiver is its current state.</li>
 * <li><em>The question log</em> (PM-WF-12): the append-only {@code questions.jsonl} of a plan, an epic, the workspace
 * and a pending entry ({@link de.planmarshall.core.store.QuestionLog} of
 * {@link de.planmarshall.core.store.QuestionRecord}, read and written by
 * {@link de.planmarshall.core.store.QuestionRecordCodec}), which holds under the scope lock that a question is closed
 * by exactly one line and that an answer names the current revision, returning a broken invariant as a
 * {@link de.planmarshall.core.store.QuestionRefusal}; an entry's log moves into the scope the entry creates.</li>
 * <li><em>The plan's history</em> (PM-WF-16): the append-only {@code mcp/history.jsonl} of a plan
 * ({@link de.planmarshall.core.store.PlanHistory} of {@link de.planmarshall.core.store.PlanHistoryLine}, read and
 * written by {@link de.planmarshall.core.store.PlanHistoryCodec}), one line per state entered and left, per
 * sub-workflow called and returned and per instance version taking force, appended as a target of the plan's
 * {@link de.planmarshall.core.store.ScopeTransaction}; the runtime derives every node's state from it.</li>
 * <li><em>The per-project machine store</em> (PM-ARCH-1): {@code projects/<project_id>/} with its directories and its
 * identity record {@code store.json} ({@link de.planmarshall.core.store.ProjectStore},
 * {@link de.planmarshall.core.store.ProjectStoreIdentity}, read and written by
 * {@link de.planmarshall.core.store.ProjectStoreIdentityCodec}), created under the workspace lock and read as present,
 * absent or unreadable; nothing with state lies below the repository.</li>
 * </ul>
 * <p>
 * The package also holds the path confinement to the workspace root and its worktrees (PM-SEC-2): a project path is
 * accepted only when its canonical form lies inside the enrolled repository root or inside a live linked git worktree
 * of it ({@link de.planmarshall.core.store.PathConfinement}). The machine files below the instance directory are
 * outside this rule and have their own. The Project Resolution Rule maps a checkout path to its enrolled project and
 * to the main checkout or the worktree it lies in ({@link de.planmarshall.core.store.ProjectResolver},
 * {@link de.planmarshall.core.store.ProjectResolution}), reading the pointer files git writes with the recognition of a
 * linked worktree the confinement uses, and running no git process.
 * <p>
 * Specification:
 * <ul>
 * <li>{@code doc/specification/store-schemas/01-format-and-session-log.adoc}</li>
 * <li>{@code doc/specification/store-schemas/02-workflow-state-store.adoc}</li>
 * <li>{@code doc/specification/store-schemas/10-job-and-lesson-records.adoc}</li>
 * <li>{@code doc/specification/store-schemas/11-enrolment-store.adoc}</li>
 * <li>{@code doc/specification/store-schemas/12-machine-stores.adoc}</li>
 * <li>{@code doc/specification/store-schemas/13-leases-integrity-testing.adoc}</li>
 * <li>{@code doc/specification/store-schemas/14-plan-instance-waivers-questions.adoc}</li>
 * <li>{@code doc/specification/filesystem-layout.adoc}</li>
 * </ul>
 */
@NullMarked
package de.planmarshall.core.store;

import org.jspecify.annotations.NullMarked;
