/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import java.util.Optional;

import de.planmarshall.core.service.LockTransaction;

/**
 * What anchors the audit log beyond its own chain (PM-SEC-6): the keyed head, the rotation and the audit index. The
 * writer of the log ({@link AuditLog}) calls it under the audit lock, in every append; the runtime implements it,
 * because the head is keyed by the enrolment integrity key, which this module does not hold.
 * <p>
 * An implementation takes no {@link LockKey} of its own: the audit lock is the leaf of the total lock order, so no
 * lock may be acquired after it. And it never throws for a head or an index it cannot write: the record stays
 * appended, and the next verification reports the gap.
 *
 * @since 0.1
 */
public interface AuditAnchor {

    /** The anchor that anchors nothing: no rotation, no head, and no predecessor beyond the log itself. */
    AuditAnchor NONE = new AuditAnchor() {

        @Override
        public void beforeAppend(LockTransaction transaction, AuditLog log) {
            // nothing to anchor
        }

        @Override
        public Optional<String> emptyLogPredecessor() {
            return Optional.empty();
        }

        @Override
        public void afterAppend(LockTransaction transaction, AuditLog log, AuditRecord appended, String lineHash) {
            // nothing to anchor
        }
    };

    /**
     * Called before a record is appended: rotates the log when a rotation is due. A rotation writes the checkpoint
     * record through {@link AuditLog#appendCheckpoint(LockTransaction, AuditRecord)} on the handed log and renames
     * {@link AuditLog#logFile()}; the log is empty afterwards.
     *
     * @param transaction the writer's transaction, which holds the audit lock
     * @param log         the log the record is appended to
     */
    void beforeAppend(LockTransaction transaction, AuditLog log);

    /**
     * The hash a line chains to when the log holds no complete line: the {@code checkpoint_hash} of the last entry
     * of the audit index, the checkpoint line that closed the rotated log. Called under the audit lock.
     *
     * @return the hash; empty when no rotation ever happened, so the line chains to {@link AuditCodec#GENESIS_HASH}
     */
    Optional<String> emptyLogPredecessor();

    /**
     * Called after every append: writes the head from the new last line, the record count and the last event id.
     *
     * @param transaction the writer's transaction, which holds the audit lock
     * @param log         the log the record was appended to
     * @param appended    the record appended last
     * @param lineHash    the SHA-256 of its canonical line, in lower-case hex
     */
    void afterAppend(LockTransaction transaction, AuditLog log, AuditRecord appended, String lineHash);
}
