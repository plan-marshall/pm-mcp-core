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
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;


import de.cuioss.tools.logging.CuiLogger;
import de.planmarshall.core.log.PmMcpLogMessages.WARN;
import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.EnrolmentStore;
import de.planmarshall.core.store.ProjectEnrolment;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.runtime.credentials.SecretStoreException;

/**
 * The enrolment check (PM-EXEC-2, PM-SEC-4, PM-SEC-5): what the runtime runs before it trusts anything in a project's
 * enrolment file, in the project's recovery before the project is first served and before every verify-not-resolve
 * check of its jobs.
 * <p>
 * The check runs under the project's enrolment lock, in this order, and the first failure refuses:
 * <ol>
 * <li>the per-use checks of the file and its sidecar ({@code enrolment_insecure});</li>
 * <li>a sidecar without its file, a file without its sidecar ({@code enrolment_tampered});</li>
 * <li>the strict read of the sidecar record;</li>
 * <li>the size and SHA-256 of the file against the record ({@code enrolment_tampered});</li>
 * <li>the HMAC of the file under the integrity key against the record ({@code enrolment_tampered}; also when the key
 * does not exist in the active key source, with a reason that names a switch of the key source between the keyring
 * and the key file as the usual cause);</li>
 * <li>the strict read of the file.</li>
 * </ol>
 * <p>
 * A refusal as {@code enrolment_tampered} names the operator command that re-baselines the file. A refusal as
 * {@code enrolment_tampered} or {@code enrolment_insecure} is logged and handed to the audit sink; a sink that fails
 * does not lift the refusal. Every refusal blocks only the refused project.
 *
 * @since 0.1
 */
public final class EnrolmentCheck {

    /** The reason of a sidecar without its enrolment file. */
    static final String SIDECAR_WITHOUT_FILE = "sidecar without its enrolment file";

    /** The reason of an enrolment file without its sidecar. */
    static final String FILE_WITHOUT_SIDECAR = "enrolment file without its sidecar";

    /** The reason of a file whose size or SHA-256 is not the recorded one. */
    static final String DIGEST_MISMATCH = "size or SHA-256 differs from the sidecar record";

    /** The reason of a file whose HMAC is not the recorded one. */
    static final String HMAC_MISMATCH = "HMAC differs from the sidecar record";

    /**
     * The reason of a file that cannot be verified, and of an enrolment write that is refused, because the integrity
     * key does not exist in the active key source while enrolment files exist.
     */
    static final String KEY_ABSENT = "the integrity key does not exist in the active key source (keyring or key file);"
            + " a switch of the key source strands the enrolments written under the other one";

    private static final CuiLogger LOGGER = new CuiLogger(EnrolmentCheck.class);

    private final EnrolmentStore store;
    private final EnrolmentKey key;
    private final EnrolmentFileCheck fileCheck;
    private final AuditSink auditSink;

    /**
     * @param store     the enrolment store
     * @param key       the enrolment integrity key
     * @param fileCheck the per-use checks
     * @param auditSink where a refusal is recorded
     */
    public EnrolmentCheck(EnrolmentStore store, EnrolmentKey key, EnrolmentFileCheck fileCheck, AuditSink auditSink) {
        this.store = Objects.requireNonNull(store, "store");
        this.key = Objects.requireNonNull(key, "key");
        this.fileCheck = Objects.requireNonNull(fileCheck, "fileCheck");
        this.auditSink = Objects.requireNonNull(auditSink, "auditSink");
    }

    /**
     * Checks the project's enrolment and reads it. The project's enrolment lock is acquired in the transaction if it
     * does not hold it yet.
     *
     * @param transaction the transaction of the caller
     * @param projectId   the project
     * @return the verified enrolment
     * @throws StoreRefusal           as {@code enrolment_insecure} or {@code enrolment_tampered}; as {@code absent} if
     *                                neither the file nor its sidecar exists; as {@code unreadable},
     *                                {@code format_outdated} or {@code format_newer} for the sidecar or the file
     * @throws SecretStoreException   if the integrity key exists and cannot be read
     * @throws InternalFaultException if the lock must not be acquired after the locks the transaction holds
     * @throws UncheckedIOException   if a file cannot be read
     */
    public ProjectEnrolment verify(LockTransaction transaction, String projectId) {
        Objects.requireNonNull(transaction, "transaction");
        transaction.acquire(store.lockKey(projectId));
        var file = store.file(projectId);
        var sidecarFile = store.sidecar(projectId);
        checkInsecure(projectId, file);
        checkInsecure(projectId, sidecarFile);
        var content = store.readFile(transaction, projectId);
        var sidecarContent = store.readSidecar(transaction, projectId);
        if (content.isEmpty() && sidecarContent.isEmpty()) {
            throw StoreRefusal.absent(file, ProjectEnrolment.FORMAT_VERSION);
        }
        if (content.isEmpty()) {
            throw tampered(sidecarFile, projectId, SIDECAR_WITHOUT_FILE);
        }
        if (sidecarContent.isEmpty()) {
            throw tampered(file, projectId, FILE_WITHOUT_SIDECAR);
        }
        var bytes = content.get();
        var sidecar = store.decodeSidecar(projectId, sidecarContent.get());
        if (!sidecar.matches(bytes)) {
            throw tampered(file, projectId, DIGEST_MISMATCH);
        }
        var integrityKey = key.read();
        if (integrityKey.isEmpty()) {
            throw tampered(file, projectId, KEY_ABSENT);
        }
        if (!EnrolmentHmac.verify(integrityKey.get(), bytes, sidecar.hmac())) {
            throw tampered(file, projectId, HMAC_MISMATCH);
        }
        return store.decode(projectId, bytes);
    }

    private void checkInsecure(String projectId, Path file) {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        var violation = fileCheck.violation(file);
        if (violation.isPresent()) {
            var reason = violation.get();
            LOGGER.warn(WARN.ENROLMENT_INSECURE, projectId, reason);
            throw recorded(auditSink, StoreRefusal.enrolmentInsecure(file, reason),
                    AuditEvent.enrolmentInsecure(projectId, reason));
        }
    }

    private StoreRefusal tampered(Path file, String projectId, String reason) {
        return tampered(auditSink, file, projectId, reason);
    }

    /**
     * Builds the refusal as {@code enrolment_tampered}, which names the operator command that re-baselines the file,
     * logs it and hands it to the audit sink; a sink that fails does not lift the refusal. The enrolment write refuses
     * through this path too, so a refusal reads and is recorded the same wherever it arises.
     *
     * @param auditSink where the refusal is recorded
     * @param file      the refused enrolment file or sidecar
     * @param projectId the project
     * @param reason    why the file is refused
     * @return the refusal, for the caller to throw
     */
    static StoreRefusal tampered(AuditSink auditSink, Path file, String projectId, String reason) {
        LOGGER.warn(WARN.ENROLMENT_TAMPERED, projectId, reason);
        return recorded(auditSink, StoreRefusal.enrolmentTampered(file, projectId, reason),
                AuditEvent.enrolmentTampered(projectId, reason));
    }

    private static StoreRefusal recorded(AuditSink auditSink, StoreRefusal refusal, AuditEvent event) {
        try {
            auditSink.append(event);
        } catch (UncheckedIOException e) {
            refusal.addSuppressed(e);
        }
        return refusal;
    }
}
