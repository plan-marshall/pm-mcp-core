/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.service;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import de.planmarshall.core.store.LockKey;

/**
 * One audit record as its producer hands it to the {@link AuditSink} (PM-IMPL-7). It carries what the producer
 * knows; what makes a record of the audit log out of it (its identifier, its instant, its place in the chain) is
 * added by the audit store.
 * <p>
 * Every record is built by the factory of its event, which fixes its name, category and outcome and exactly the
 * details the specification names; the enrolment records (PM-EXEC-2, PM-SEC-4, PM-SEC-5) carry the project and, for
 * a refusal, its reason. The three records of a succeeded operator write ({@code enrolment_written},
 * {@code enrolment_removed}, {@code enrolment_rebaselined}) also carry the transaction of the write: they are owed in
 * a durable intent before the write and handed over at least once, and a record handed over again carries the same
 * transaction id. A refusal changed nothing and is not owed; its record carries no transaction. The operator command
 * that resolves a refused enrolment is named by the refusal, not by its record.
 *
 * @param event   the name of the event
 * @param category the category of the event
 * @param outcome  the outcome the event records
 * @param details  the details of the event, unmodifiable
 * @since 0.1
 */
public record AuditEvent(String event, String category, String outcome, Map<String, Object> details) {

    /** The name of the event that records a refused out-of-order lock acquisition. */
    public static final String LOCK_ORDER_VIOLATION = "lock_order_violation";

    /** The category of the events of the stores and their locks. */
    public static final String CATEGORY_STORE = "store";

    /** The outcome of an action that was refused. */
    public static final String OUTCOME_REFUSED = "refused";

    /** The detail of a lock order violation that lists the lock paths the transaction held, in the lock order. */
    public static final String DETAIL_HELD = "held";

    /** The detail of a lock order violation that names the lock path the transaction asked for. */
    public static final String DETAIL_REQUESTED = "requested";

    /** The name of the event that records the repair of a torn last line of a JSONL store. */
    public static final String JSONL_TAIL_REPAIRED = "jsonl_tail_repaired";

    /** The name of the event that records the roll-forward of an interrupted transaction journal. */
    public static final String TRANSACTION_ROLLED_FORWARD = "transaction_rolled_forward";

    /** The name of the event that records one reconciliation of a write outside a scope at recovery. */
    public static final String CROSS_SCOPE_RECONCILED = "cross_scope_reconciled";

    /** The outcome of an action that was carried out. */
    public static final String OUTCOME_SUCCEEDED = "succeeded";

    /** The detail that names a store by its path. */
    public static final String DETAIL_STORE = "store";

    /** The detail of a tail repair that counts the bytes cut off the store. */
    public static final String DETAIL_TRUNCATED_BYTES = "truncated_bytes";

    /** The detail of a tail repair that holds the SHA-256 of the bytes cut off the store. */
    public static final String DETAIL_TRUNCATED_SHA256 = "truncated_sha256";

    /** The detail of a roll-forward that names the type of the scope, by its wire name. */
    public static final String DETAIL_SCOPE_TYPE = "scope_type";

    /** The detail of a roll-forward that names the scope; left out for a scope without an id. */
    public static final String DETAIL_SCOPE_ID = "scope_id";

    /**
     * The detail that names the transaction of a store mutation: of a roll-forward the transaction of the journal, of
     * a tail repair the id of the repair, of a reconciliation the transaction of the derived write, of a succeeded
     * enrolment write the transaction of the write. A record handed
     * over again after a failed or interrupted hand-over carries the same id, so a reader drops the duplicate.
     */
    public static final String DETAIL_TRANSACTION_ID = "transaction_id";

    /** The detail of a roll-forward that lists the store paths of the journal's targets, in its order. */
    public static final String DETAIL_TARGETS = "targets";

    /** The detail of a cross-scope reconciliation that names the reconciliation rule that applied. */
    public static final String DETAIL_RECONCILIATION = "reconciliation";

    /** The name of the event that records an enrolment refused because it does not match its sidecar. */
    public static final String ENROLMENT_TAMPERED = "enrolment_tampered";

    /** The name of the event that records an enrolment file or sidecar refused by a per-use check. */
    public static final String ENROLMENT_INSECURE = "enrolment_insecure";

    /** The name of the event that records an enrolment file and its sidecar written by {@code enrol}. */
    public static final String ENROLMENT_WRITTEN = "enrolment_written";

    /** The name of the event that records an enrolment file and its sidecar removed by {@code unenrol}. */
    public static final String ENROLMENT_REMOVED = "enrolment_removed";

    /** The name of the event that records the sidecar of an enrolment rewritten by {@code accept-edit enrolment}. */
    public static final String ENROLMENT_REBASELINED = "enrolment_rebaselined";

    /** The name of the event that records an {@code unenrol} refused while the project has live work. */
    public static final String UNENROL_REFUSED = "unenrol_refused";

    /** The category of the events of the integrity and per-use checks. */
    public static final String CATEGORY_SECURITY = "security";

    /** The category of the events of the operator verbs. */
    public static final String CATEGORY_OPERATOR = "operator";

    /** The detail that names the project by its id. */
    public static final String DETAIL_PROJECT_ID = "project_id";

    /** The detail of a refusal that names why it was refused. */
    public static final String DETAIL_REASON = "reason";

    /** The reason of an {@code unenrol} refused while the project has live work. */
    public static final String REASON_SCOPE_ACTIVE = "scope_active";

    /**
     * @throws NullPointerException if a component, a detail key or a detail value is {@code null}
     */
    public AuditEvent {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(outcome, "outcome");
        details = Map.copyOf(details);
    }

    /**
     * @param held      the keys the transaction held, in the lock order
     * @param requested the key that was refused
     * @return the record of the refused acquisition, with the lock paths of the held and of the requested key
     */
    public static AuditEvent lockOrderViolation(List<LockKey> held, LockKey requested) {
        var heldPaths = held.stream().map(AuditEvent::lockPath).toList();
        return new AuditEvent(LOCK_ORDER_VIOLATION, CATEGORY_STORE, OUTCOME_REFUSED,
                Map.of(DETAIL_HELD, heldPaths, DETAIL_REQUESTED, lockPath(requested)));
    }

    /**
     * @param store           the JSONL store whose torn last line was cut off
     * @param truncatedBytes  the number of bytes cut off
     * @param truncatedSha256 the SHA-256 of the bytes cut off, in lower-case hex
     * @param transactionId   the id of the repair, by which a record handed over again is recognised as the same
     * @return the record of the repair
     */
    public static AuditEvent jsonlTailRepaired(Path store, long truncatedBytes, String truncatedSha256,
            String transactionId) {
        Objects.requireNonNull(store, DETAIL_STORE);
        return new AuditEvent(JSONL_TAIL_REPAIRED, CATEGORY_STORE, OUTCOME_SUCCEEDED,
                Map.of(DETAIL_STORE, store.toString(), DETAIL_TRUNCATED_BYTES, truncatedBytes,
                        DETAIL_TRUNCATED_SHA256, truncatedSha256, DETAIL_TRANSACTION_ID, transactionId));
    }

    /**
     * @param scopeType     the wire name of the type of the scope ({@code plan}, {@code epic}, {@code workspace},
     *                      {@code lessons})
     * @param scopeId       the id of the scope; {@code null} for a scope without an id, which leaves the detail out
     * @param transactionId the transaction of the journal
     * @param targets       the store paths of the journal's targets, in its order
     * @return the record of the roll-forward
     */
    public static AuditEvent transactionRolledForward(String scopeType, String scopeId, String transactionId,
            List<String> targets) {
        var details = new HashMap<String, Object>();
        details.put(DETAIL_SCOPE_TYPE, scopeType);
        if (scopeId != null) {
            details.put(DETAIL_SCOPE_ID, scopeId);
        }
        details.put(DETAIL_TRANSACTION_ID, transactionId);
        details.put(DETAIL_TARGETS, List.copyOf(targets));
        return new AuditEvent(TRANSACTION_ROLLED_FORWARD, CATEGORY_STORE, OUTCOME_SUCCEEDED, details);
    }

    /**
     * @param reconciliation the reconciliation rule that applied
     * @param store          the store it wrote
     * @param transactionId  the transaction that wrote the store, by which a record handed over again is recognised
     *                       as the same
     * @return the record of the reconciliation
     */
    public static AuditEvent crossScopeReconciled(String reconciliation, String store, String transactionId) {
        return new AuditEvent(CROSS_SCOPE_RECONCILED, CATEGORY_STORE, OUTCOME_SUCCEEDED,
                Map.of(DETAIL_RECONCILIATION, reconciliation, DETAIL_STORE, store, DETAIL_TRANSACTION_ID,
                        transactionId));
    }

    /**
     * @param projectId the enrolled project
     * @param reason    what failed: the HMAC, the recorded size or SHA-256, a sidecar without its file, a file without
     *                  its sidecar
     * @return the record of the enrolment refused as tampered
     */
    public static AuditEvent enrolmentTampered(String projectId, String reason) {
        return refusal(ENROLMENT_TAMPERED, CATEGORY_SECURITY, projectId, reason);
    }

    /**
     * @param projectId the enrolled project
     * @param reason    what failed: a foreign owner, a mode broader than {@code 0600}, a symbolic link
     * @return the record of the enrolment refused by a per-use check
     */
    public static AuditEvent enrolmentInsecure(String projectId, String reason) {
        return refusal(ENROLMENT_INSECURE, CATEGORY_SECURITY, projectId, reason);
    }

    /**
     * @param projectId     the enrolled project
     * @param transactionId the transaction of the write, by which a record handed over again is recognised as the
     *                      same
     * @return the record of the enrolment file and its sidecar written
     * @throws NullPointerException if the project id or the transaction id is {@code null}
     */
    public static AuditEvent enrolmentWritten(String projectId, String transactionId) {
        return operatorSucceeded(ENROLMENT_WRITTEN, projectId, transactionId);
    }

    /**
     * @param projectId     the unenrolled project
     * @param transactionId the transaction of the removal, by which a record handed over again is recognised as the
     *                      same
     * @return the record of the enrolment file and its sidecar removed
     * @throws NullPointerException if the project id or the transaction id is {@code null}
     */
    public static AuditEvent enrolmentRemoved(String projectId, String transactionId) {
        return operatorSucceeded(ENROLMENT_REMOVED, projectId, transactionId);
    }

    /**
     * @param projectId     the enrolled project
     * @param transactionId the transaction of the re-baseline, by which a record handed over again is recognised as
     *                      the same
     * @return the record of the sidecar of the enrolment rewritten over the current file
     * @throws NullPointerException if the project id or the transaction id is {@code null}
     */
    public static AuditEvent enrolmentRebaselined(String projectId, String transactionId) {
        return operatorSucceeded(ENROLMENT_REBASELINED, projectId, transactionId);
    }

    /**
     * @param projectId the project whose {@code unenrol} was refused
     * @return the record of the {@code unenrol} refused with {@link #REASON_SCOPE_ACTIVE}
     */
    public static AuditEvent unenrolRefused(String projectId) {
        return refusal(UNENROL_REFUSED, CATEGORY_OPERATOR, projectId, REASON_SCOPE_ACTIVE);
    }

    private static AuditEvent refusal(String event, String category, String projectId, String reason) {
        return new AuditEvent(event, category, OUTCOME_REFUSED,
                Map.of(DETAIL_PROJECT_ID, Objects.requireNonNull(projectId, "projectId"), DETAIL_REASON,
                        Objects.requireNonNull(reason, DETAIL_REASON)));
    }

    private static AuditEvent operatorSucceeded(String event, String projectId, String transactionId) {
        return new AuditEvent(event, CATEGORY_OPERATOR, OUTCOME_SUCCEEDED,
                Map.of(DETAIL_PROJECT_ID, Objects.requireNonNull(projectId, "projectId"), DETAIL_TRANSACTION_ID,
                        Objects.requireNonNull(transactionId, "transactionId")));
    }

    private static String lockPath(LockKey key) {
        return key.lockFile().toString();
    }
}
