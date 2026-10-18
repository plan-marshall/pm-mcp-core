/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.enrolment;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import javax.crypto.SecretKey;

import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.EnrolmentSidecar;
import de.planmarshall.core.store.EnrolmentStore;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.runtime.audit.AuditHead;

/**
 * Every HMAC sidecar of the machine under one integrity key (PM-SEC-5, PM-CRED-2): what the credential-store move
 * verifies under the key it read back from the target store before it switches the store. All three kinds are verified
 * here, in the runtime:
 * <ol>
 * <li>every enrolment file {@code enrolments/<project_id>.json} against its sidecar, each under its enrolment lock, in
 * the lexicographic order of the project ids: the per-use checks bound to one no-follow snapshot of the file and of
 * the sidecar, the sidecar present, its strict read, the size and SHA-256, and the HMAC;</li>
 * <li>{@code harnesses.json} against {@code harnesses.json.hmac} through {@link HarnessesSidecarCheck}, under
 * {@code locks/harnesses.lock}; neither file existing passes;</li>
 * <li>the audit head {@code logs/audit.head} ({@code AuditHead.verifies}), under the audit leaf lock; no head file
 * passes.</li>
 * </ol>
 * The locks are taken in the total lock order: the enrolment locks, then {@code harnesses}, the audit leaf last, so a
 * caller that holds {@code locks/credentials.lock} keeps the order, and the transaction can take no lock after this
 * verification. The first failure ends it and names the file and the reason. Nothing is written, logged or audited:
 * the caller decides what a failure means, and the refusals of the enrolment check stay with the enrolment check.
 *
 * @since 0.1
 */
public final class EnrolmentSidecars {

    /** The reason of an audit head whose HMAC is not the HMAC of its other fields under the key. */
    static final String HEAD_HMAC_MISMATCH = "the audit head does not verify under the key";

    private final Path base;
    private final EnrolmentStore store;
    private final EnrolmentFileCheck fileCheck;
    private final HarnessesSidecarCheck harnesses;

    /**
     * @param base the base directory {@code <PM_MCP_BASE>} of the runtime
     * @param user the name of the running user, the owner every enrolment file and sidecar must have
     */
    public EnrolmentSidecars(Path base, String user) {
        this.base = Objects.requireNonNull(base, "base").toAbsolutePath().normalize();
        this.store = new EnrolmentStore(this.base);
        this.fileCheck = new EnrolmentFileCheck(user);
        this.harnesses = new HarnessesSidecarCheck(this.base, user);
    }

    /**
     * Verifies every sidecar under the key, acquiring the locks in the transaction in the total lock order.
     *
     * @param transaction the transaction of the caller; it holds no lock that sorts after an enrolment lock
     * @param key         the integrity key the sidecars are verified under
     * @return the first failure; empty if every sidecar verifies
     * @throws InternalFaultException if a lock must not be acquired after the locks the transaction holds
     * @throws UncheckedIOException   if the enrolment directory cannot be listed or a file cannot be read
     */
    public Optional<Failure> verify(LockTransaction transaction, SecretKey key) {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(key, "key");
        for (var projectId : store.projectIds()) {
            var failure = verifyEnrolment(transaction, projectId, key);
            if (failure.isPresent()) {
                return failure;
            }
        }
        var harnessesResult = harnesses.verify(transaction, key);
        if (!harnessesResult.passed()) {
            return failure(harnessesResult.file().orElseThrow(), harnessesResult.reason().orElseThrow());
        }
        return verifyAuditHead(transaction, key);
    }

    private Optional<Failure> verifyEnrolment(LockTransaction transaction, String projectId, SecretKey key) {
        transaction.acquire(store.lockKey(projectId));
        var file = store.file(projectId);
        var sidecarFile = store.sidecar(projectId);
        var content = fileCheck.snapshot(file);
        if (content.violation().isPresent()) {
            return failure(file, content.violation().get());
        }
        var sidecarContent = fileCheck.snapshot(sidecarFile);
        if (sidecarContent.violation().isPresent()) {
            return failure(sidecarFile, sidecarContent.violation().get());
        }
        if (content.isAbsent()) {
            return sidecarContent.isAbsent() ? Optional.empty()
                    : failure(sidecarFile, EnrolmentCheck.SIDECAR_WITHOUT_FILE);
        }
        if (sidecarContent.isAbsent()) {
            return failure(file, EnrolmentCheck.FILE_WITHOUT_SIDECAR);
        }
        EnrolmentSidecar sidecar;
        try {
            sidecar = store.decodeSidecar(projectId, sidecarContent.content().orElseThrow());
        } catch (StoreRefusal e) {
            return failure(sidecarFile, e.outcome().code() + " in " + sidecarFile.getFileName());
        }
        var bytes = content.content().orElseThrow();
        if (!sidecar.matches(bytes)) {
            return failure(file, EnrolmentCheck.DIGEST_MISMATCH);
        }
        if (!EnrolmentHmac.verify(key, bytes, sidecar.hmac())) {
            return failure(file, EnrolmentCheck.HMAC_MISMATCH);
        }
        return Optional.empty();
    }

    private Optional<Failure> verifyAuditHead(LockTransaction transaction, SecretKey key) {
        transaction.acquire(LockKey.auditLog(base));
        var headFile = AuditHead.headFile(base);
        try {
            var head = AuditHead.read(headFile);
            if (head.isEmpty() || AuditHead.verifies(key, head.get())) {
                return Optional.empty();
            }
            return failure(headFile, HEAD_HMAC_MISMATCH);
        } catch (StoreRefusal e) {
            return failure(headFile, e.outcome().code() + " in " + headFile.getFileName());
        }
    }

    private static Optional<Failure> failure(Path file, String reason) {
        return Optional.of(new Failure(file, reason));
    }

    /**
     * A sidecar that does not verify.
     *
     * @param file   the file the failure concerns: the enrolment file, its sidecar, {@code harnesses.json} or its
     *               sidecar, or the audit head
     * @param reason why it does not verify
     * @since 0.1
     */
    public record Failure(Path file, String reason) {

        /**
         * @throws NullPointerException if a component is {@code null}
         */
        public Failure {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(reason, "reason");
        }
    }
}
