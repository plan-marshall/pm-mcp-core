/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.start;

import de.cuioss.tools.logging.CuiLogger;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.runtime.credentials.CredentialStoreMove;
import de.planmarshall.runtime.credentials.CredentialStoreMoveRecord;
import de.planmarshall.runtime.credentials.SecretStoreException;
import de.planmarshall.runtime.log.RuntimeLogMessages.INFO;

/**
 * The resume of a move between the credential stores that a stop cut (PM-CRED-2). It is machine-wide, not a step of
 * the per-project recovery: the assembly runs it once after the socket bind. It reads the record
 * {@code state/credential-store-move.json} through {@link CredentialStoreMove#resume()}:
 * <ul>
 * <li>in phase {@code copying} with the old store recorded, the copies are deleted from the target and the move starts
 * again from the first entry, the old store holding every entry;</li>
 * <li>in phase {@code switched}, or in phase {@code copying} while the target is recorded already (a stop between the
 * switch and the rewrite of the record), the record is marked {@code switched}, the deletion from the old store is
 * finished and the record ended; nothing is deleted from the recorded store;</li>
 * <li>without a record no move is open and nothing happens.</li>
 * </ul>
 * A resumed move is logged with the record it was resumed from; the move itself logs and audits its end.
 *
 * @since 0.1
 */
public final class CredentialStoreMoveResume {

    private static final CuiLogger LOGGER = new CuiLogger(CredentialStoreMoveResume.class);

    private final CredentialStoreMove move;

    /**
     * @param move the move between the credential stores, over the base directory of the runtime
     */
    public CredentialStoreMoveResume(CredentialStoreMove move) {
        this.move = Objects.requireNonNull(move, "move");
    }

    /**
     * Resumes the open move, if there is one, and completes it.
     *
     * @return the record of the move that was resumed and completed, as it was found; empty if no move was open
     * @throws SecretStoreException with {@code credential_store_failed} if the move fails again before the switch, the
     *                              old store staying recorded, or with the reason of the store if an entry cannot be
     *                              deleted from the old store, which the next start completes
     * @throws StoreRefusal         if the record or the machine configuration cannot be read as its format
     * @throws UncheckedIOException if the record cannot be read or written, or the audit sink refuses the record of
     *                              the completed move, which stands then
     */
    public Optional<CredentialStoreMoveRecord> run() {
        var resumed = move.resume();
        resumed.ifPresent(record -> LOGGER.info(INFO.CREDENTIAL_STORE_MOVE_RESUMED, record.from().value(),
                record.to().value(), record.phase().value()));
        return resumed;
    }
}
