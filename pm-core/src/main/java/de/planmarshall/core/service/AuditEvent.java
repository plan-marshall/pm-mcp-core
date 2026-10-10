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
 *
 * @param event    the name of the event
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

    /** The detail of a roll-forward that names the transaction of the journal. */
    public static final String DETAIL_TRANSACTION_ID = "transaction_id";

    /** The detail of a roll-forward that lists the store paths of the journal's targets, in its order. */
    public static final String DETAIL_TARGETS = "targets";

    /** The detail of a cross-scope reconciliation that names the reconciliation rule that applied. */
    public static final String DETAIL_RECONCILIATION = "reconciliation";

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
     * @return the record of the repair
     */
    public static AuditEvent jsonlTailRepaired(Path store, long truncatedBytes, String truncatedSha256) {
        Objects.requireNonNull(store, DETAIL_STORE);
        return new AuditEvent(JSONL_TAIL_REPAIRED, CATEGORY_STORE, OUTCOME_SUCCEEDED,
                Map.of(DETAIL_STORE, store.toString(), DETAIL_TRUNCATED_BYTES, truncatedBytes,
                        DETAIL_TRUNCATED_SHA256, truncatedSha256));
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
     * @return the record of the reconciliation
     */
    public static AuditEvent crossScopeReconciled(String reconciliation, String store) {
        return new AuditEvent(CROSS_SCOPE_RECONCILED, CATEGORY_STORE, OUTCOME_SUCCEEDED,
                Map.of(DETAIL_RECONCILIATION, reconciliation, DETAIL_STORE, store));
    }

    private static String lockPath(LockKey key) {
        return key.lockFile().toString();
    }
}
