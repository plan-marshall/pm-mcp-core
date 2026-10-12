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

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * What the recovery of the enrolment pair ({@link EnrolmentStore#recoverPair}) did with the staged files of one
 * stopped write of a project: it completed the write, or it discarded what the write had staged.
 *
 * @since 0.1
 */
public sealed interface PairRecovery permits PairRecovery.Completed, PairRecovery.Discarded {

    /** @return the transaction of the stopped write */
    TransactionId transactionId();

    /**
     * The stopped write was completed: the file and its sidecar it had staged completely are in place.
     *
     * @param transactionId the transaction of the stopped write
     * @param replaced      the enrolment file and sidecar that were replaced, in the order they were replaced
     * @since 0.1
     */
    record Completed(TransactionId transactionId, List<Path> replaced) implements PairRecovery {

        /** @throws NullPointerException if a component or a path is {@code null} */
        public Completed {
            Objects.requireNonNull(transactionId, "transactionId");
            replaced = List.copyOf(replaced);
        }
    }

    /**
     * The stopped write was discarded: its staging was incomplete, or its staged sidecar does not match the file it
     * stands for. The enrolment is as it was before the write, except a file that was already replaced, which the
     * integrity check then refuses.
     *
     * @param transactionId the transaction of the stopped write
     * @param deleted       the staged files that were deleted
     * @param reason        why the staging was discarded
     * @since 0.1
     */
    record Discarded(TransactionId transactionId, List<Path> deleted, String reason) implements PairRecovery {

        /** @throws NullPointerException if a component or a path is {@code null} */
        public Discarded {
            Objects.requireNonNull(transactionId, "transactionId");
            deleted = List.copyOf(deleted);
            Objects.requireNonNull(reason, "reason");
        }
    }
}
