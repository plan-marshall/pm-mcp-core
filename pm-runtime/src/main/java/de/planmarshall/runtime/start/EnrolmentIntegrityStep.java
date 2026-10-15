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

import java.io.UncheckedIOException;
import java.util.Objects;

import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.store.EnrolmentStore;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.runtime.credentials.SecretStoreException;
import de.planmarshall.runtime.enrolment.EnrolmentCheck;

/**
 * Step 1 of the per-project recovery, {@link RecoveryStep.Name#ENROLMENT_INTEGRITY}
 * (doc/specification/runtime-lifecycle/01-startup-recovery-drain.adoc, Per-Project Recovery; PM-SEC-5): the enrolment
 * integrity verification before the project is first served.
 * <p>
 * In one transaction under the project's enrolment lock it first settles a stopped write of the enrolment pair
 * ({@link EnrolmentStore#recoverPair}: completed or discarded, never signed) and then runs the enrolment check
 * ({@link EnrolmentCheck#verify}). Every refusal is a {@link RecoveryStep.Outcome.Failed} with the message of the
 * refusal, which names the file and the code, and stops the recovery of that project only; the check already logged
 * and audited a tampered, insecure or invalid enrolment. A project that is no longer in the store ({@code absent})
 * fails as well, because only enrolled projects are recovered. A key that cannot be read fails with its reason code,
 * never with the key. The step never signs anything and never creates a key.
 *
 * @since 0.1
 */
public final class EnrolmentIntegrityStep implements RecoveryStep {

    /** The report of a verified enrolment. */
    static final String VERIFIED = "enrolment verified";

    private final EnrolmentStore store;
    private final EnrolmentCheck check;
    private final LockManager lockManager;

    /**
     * @param store       the enrolment store
     * @param check       the enrolment check
     * @param lockManager the lock manager of the runtime
     */
    public EnrolmentIntegrityStep(EnrolmentStore store, EnrolmentCheck check, LockManager lockManager) {
        this.store = Objects.requireNonNull(store, "store");
        this.check = Objects.requireNonNull(check, "check");
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager");
    }

    @Override
    public Name name() {
        return Name.ENROLMENT_INTEGRITY;
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException if the project id is not a valid identifier; the recovery records it as a
     *                                  failed step
     */
    @Override
    public Outcome run(String projectId) {
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(store.lockKey(projectId));
            var settled = store.recoverPair(transaction, projectId);
            check.verify(transaction, projectId);
            return new Outcome.Done(settled.isEmpty() ? VERIFIED
                    : VERIFIED + ", a stopped write was settled (" + settled.size() + ")");
        } catch (StoreRefusal e) {
            return new Outcome.Failed(e.getMessage());
        } catch (SecretStoreException e) {
            return new Outcome.Failed("integrity key unavailable: " + e.reason().code());
        } catch (UncheckedIOException e) {
            return new Outcome.Failed("enrolment cannot be read: " + e.getMessage());
        }
    }
}
