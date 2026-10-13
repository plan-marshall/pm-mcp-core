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
 * {@link de.planmarshall.core.store.RetryCounters} and never reset by a restart (PM-WF-7).
 * {@link de.planmarshall.core.store.WorkflowStatusStore} reads the store without a lock and writes it only as a
 * target of the caller's {@link de.planmarshall.core.store.ScopeTransaction}.</li>
 * <li><em>The scope session log</em> (PM-ARCH-4, PM-CLIENT-3): {@code sessions.jsonl} of a plan, epic or workspace
 * scope ({@link de.planmarshall.core.store.ScopeSessionLog}), one entry row per {@code pm-mcp enter}
 * ({@link de.planmarshall.core.store.ScopeSessionRow}, read and written by
 * {@link de.planmarshall.core.store.ScopeSessionRowCodec}), appended through the caller's
 * {@link de.planmarshall.core.store.ScopeTransaction}, which owes the {@code entry_client_changed} record when the
 * declared client changes. A row holds no path of a checkout.</li>
 * </ul>
 * <p>
 * The package also holds the path confinement to the workspace root and its worktrees (PM-SEC-2): a project path is
 * accepted only when its canonical form lies inside the enrolled repository root or inside a live linked git worktree
 * of it ({@link de.planmarshall.core.store.PathConfinement}). The machine files below the instance directory are
 * outside this rule and have their own.
 * <p>
 * Specification:
 * <ul>
 * <li>{@code doc/specification/store-schemas/01-format-and-session-log.adoc}</li>
 * <li>{@code doc/specification/store-schemas/02-workflow-state-store.adoc}</li>
 * <li>{@code doc/specification/store-schemas/13-leases-integrity-testing.adoc}</li>
 * <li>{@code doc/specification/filesystem-layout.adoc}</li>
 * </ul>
 */
package de.planmarshall.core.store;
