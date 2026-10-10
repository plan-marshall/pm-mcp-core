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

    private static String lockPath(LockKey key) {
        return key.lockFile().toString();
    }
}
