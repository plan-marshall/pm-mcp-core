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

import java.io.Serial;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import de.planmarshall.core.store.LockKey;

/**
 * The failure class {@code internal_fault} of the foundation (PM-IMPL-7): the engine itself broke one of its own
 * rules, and the call that hit it fails without having written anything. The reasons are closed. The assembly maps
 * this exception to the error code of the client contract; the foundation names no type of that contract.
 *
 * @since 0.1
 */
public class InternalFaultException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Why the engine failed.
     *
     * @since 0.1
     */
    public enum Reason {
        /** A transaction asked for a lock that must not be acquired after the locks it holds. */
        LOCK_ORDER_VIOLATION("lock_order_violation");

        private final String code;

        Reason(String code) {
            this.code = code;
        }

        /** @return the name of the reason in an audit record */
        public String code() {
            return code;
        }
    }

    /** The reason. */
    private final Reason reason;

    /** The details of the fault; not part of the serialized form. */
    private final transient Map<String, Object> details;

    /**
     * @param reason  the reason
     * @param message the description
     * @param details the details of the fault, as its audit record carries them
     * @throws NullPointerException if an argument, a detail key or a detail value is {@code null}
     */
    public InternalFaultException(Reason reason, String message, Map<String, Object> details) {
        super(Objects.requireNonNull(message, "message"));
        this.reason = Objects.requireNonNull(reason, "reason");
        this.details = Map.copyOf(details);
    }

    /**
     * @param held      the keys the transaction held, in the lock order
     * @param requested the key that was refused
     * @return the fault of an out-of-order lock acquisition; its message and its details name lock paths only
     */
    public static InternalFaultException lockOrderViolation(List<LockKey> held, LockKey requested) {
        var details = AuditEvent.lockOrderViolation(held, requested).details();
        var message = "Lock order violation: '%s' requested while holding %s"
                .formatted(details.get(AuditEvent.DETAIL_REQUESTED), details.get(AuditEvent.DETAIL_HELD));
        return new InternalFaultException(Reason.LOCK_ORDER_VIOLATION, message, details);
    }

    /** @return the reason */
    public Reason reason() {
        return reason;
    }

    /** @return the details of the fault, unmodifiable; empty after deserialization */
    public Map<String, Object> details() {
        return details == null ? Map.of() : details;
    }
}
