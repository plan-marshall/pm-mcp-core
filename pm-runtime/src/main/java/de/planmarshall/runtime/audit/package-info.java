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
 * The anchoring and the verification of the audit trail (PM-SEC-6; Store Schemas, Security &amp; Transition Audit Log,
 * Audit Head and Audit Index; CLI, Coordination &amp; Security, Security Audit Trail): what makes the hash chain of
 * {@code logs/audit.jsonl}, which {@link de.planmarshall.core.store.AuditLog} writes, checkable for tampering.
 * <p>
 * {@link de.planmarshall.runtime.audit.AuditHead} fills the anchor seam
 * {@link de.planmarshall.core.store.AuditAnchor} of the writer: after every append it writes the head
 * {@code logs/audit.head}, the record count, the last record, the hash of the last line and of the last index line,
 * under an HMAC-SHA-256 keyed by the enrolment integrity key. It only reads that key and never creates it; while no
 * key exists no head is written and no rotation happens. Before an append it lets
 * {@link de.planmarshall.runtime.audit.AuditRotation} rotate the log when its size or the age of its first record
 * reaches the limits the caller supplies (64 MiB and 30 days by default): a {@code checkpoint} record closes the log,
 * an entry of the hash-chained index {@code logs/audit-index.jsonl} ({@link de.planmarshall.runtime.audit.AuditIndex})
 * names it, and the log is renamed to {@code audit-<first_event_id>.jsonl}; the first record of the next log chains
 * to the checkpoint line. All of it runs under the audit lock inside the writer's append, so no record slips between
 * the checkpoint and the rename.
 * <p>
 * {@link de.planmarshall.runtime.audit.AuditVerification} fills the seam
 * {@link de.planmarshall.runtime.start.AuditChainVerification} of the start: it verifies, without the lock, the head's
 * HMAC, the index chain and the records of the current log from the last checkpoint, and reports a break by its reason
 * ({@code link_broken} with the first broken line, {@code missing}, {@code reset}, {@code truncated},
 * {@code head_mismatch}). A rotation between its reads is detected by reading head and index again and is never
 * reported as a break. Its full verification, which {@code pm-operator audit verify} requests, covers every rotated
 * file the index lists as well. Nothing in this package ever repairs the chain.
 */
@NullMarked
package de.planmarshall.runtime.audit;

import org.jspecify.annotations.NullMarked;
