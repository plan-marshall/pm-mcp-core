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
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.StoreRefusal;

/**
 * The verification of the harness enrolment store's integrity sidecar (PM-SEC-5, PM-CRED-2): whether
 * {@code <PM_MCP_BASE>/harnesses.json} matches its sidecar {@code harnesses.json.hmac} under one integrity key. The
 * sidecar has the format of a project enrolment's sidecar ({@link EnrolmentSidecar}) and is computed under the same
 * enrolment integrity key, so the credential-store move verifies it under the moved key before it switches the store.
 * <p>
 * The check runs under {@code locks/harnesses.lock}, which guards the file and its sidecar, in this order, and the
 * first failure ends it:
 * <ol>
 * <li>the per-use checks of the file and its sidecar (owner, mode {@code 0600}, no symbolic link, unchanged while
 * read), each bound to the one no-follow read of the file ({@code EnrolmentFileCheck.snapshot}) whose bytes the later
 * steps use ({@link Outcome#INSECURE});</li>
 * <li>a file without its sidecar, a sidecar without its file ({@link Outcome#TAMPERED}); where neither exists, no
 * harness is enrolled yet and there is nothing to verify ({@link Outcome#NOTHING_TO_VERIFY}), which passes;</li>
 * <li>the strict read of the sidecar record ({@link Outcome#UNREADABLE});</li>
 * <li>the size and SHA-256 of the file against the record ({@link Outcome#TAMPERED});</li>
 * <li>the HMAC of the file under the given key against the record ({@link Outcome#TAMPERED}).</li>
 * </ol>
 * <p>
 * The bytes of the file are never parsed against the schema of the harness enrolment store, never written and never
 * logged. Reading and writing the store, its verification before every worker spawn and the refusals
 * {@code harness_tampered} and {@code enrolment_tampered} stay with the worker launch; this check takes no audit sink
 * and refuses nothing: it answers a {@link Result}, and its caller decides what a failure means.
 *
 * @since 0.1
 */
public final class HarnessesSidecarCheck {

    /** The name of the harness enrolment store below the base directory. */
    public static final String FILE_NAME = "harnesses.json";

    /** The reason of a sidecar without its file. */
    static final String SIDECAR_WITHOUT_FILE = "sidecar without its harness enrolment file";

    /** The reason of a file without its sidecar. */
    static final String FILE_WITHOUT_SIDECAR = "harness enrolment file without its sidecar";

    private final Path base;
    private final Path file;
    private final Path sidecarFile;
    private final EnrolmentFileCheck fileCheck;

    /**
     * @param base the base directory {@code <PM_MCP_BASE>} of the runtime, which holds the file and its sidecar
     * @param user the name of the running user, the owner the file and its sidecar must have
     */
    public HarnessesSidecarCheck(Path base, String user) {
        this.base = Objects.requireNonNull(base, "base").toAbsolutePath().normalize();
        this.file = this.base.resolve(FILE_NAME);
        this.sidecarFile = this.base.resolve(FILE_NAME + EnrolmentSidecar.SUFFIX);
        this.fileCheck = new EnrolmentFileCheck(user);
    }

    /**
     * Verifies the file against its sidecar under the key. The lock {@code locks/harnesses.lock} is acquired in the
     * transaction if it does not hold it yet.
     *
     * @param transaction the transaction of the caller
     * @param key         the integrity key the sidecar is verified under
     * @return the outcome, naming the failing file and the reason of a failure
     * @throws InternalFaultException if the lock must not be acquired after the locks the transaction holds
     * @throws UncheckedIOException   if the attributes of a file cannot be read, or a file cannot be read for another
     *                                reason than a symbolic link or its removal
     */
    public Result verify(LockTransaction transaction, SecretKey key) {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(key, "key");
        transaction.acquire(LockKey.harnesses(base));
        var content = fileCheck.snapshot(file);
        if (content.violation().isPresent()) {
            return Result.failed(Outcome.INSECURE, file, content.violation().get());
        }
        var sidecarContent = fileCheck.snapshot(sidecarFile);
        if (sidecarContent.violation().isPresent()) {
            return Result.failed(Outcome.INSECURE, sidecarFile, sidecarContent.violation().get());
        }
        if (content.isAbsent() && sidecarContent.isAbsent()) {
            return Result.NOTHING;
        }
        if (content.isAbsent()) {
            return Result.failed(Outcome.TAMPERED, sidecarFile, SIDECAR_WITHOUT_FILE);
        }
        if (sidecarContent.isAbsent()) {
            return Result.failed(Outcome.TAMPERED, file, FILE_WITHOUT_SIDECAR);
        }
        EnrolmentSidecar sidecar;
        try {
            sidecar = EnrolmentSidecar.read(sidecarFile, sidecarContent.content().orElseThrow());
        } catch (StoreRefusal e) {
            return Result.failed(Outcome.UNREADABLE, sidecarFile, e.outcome().code() + " in "
                    + sidecarFile.getFileName());
        }
        var bytes = content.content().orElseThrow();
        if (!sidecar.matches(bytes)) {
            return Result.failed(Outcome.TAMPERED, file, EnrolmentCheck.DIGEST_MISMATCH);
        }
        if (!EnrolmentHmac.verify(key, bytes, sidecar.hmac())) {
            return Result.failed(Outcome.TAMPERED, file, EnrolmentCheck.HMAC_MISMATCH);
        }
        return Result.VERIFIED;
    }

    /**
     * What a verification found.
     *
     * @since 0.1
     */
    public enum Outcome {

        /** The file matches its sidecar under the key. */
        VERIFIED,

        /** Neither the file nor its sidecar exists: no harness is enrolled yet. */
        NOTHING_TO_VERIFY,

        /**
         * One half of the pair is missing, or the size, SHA-256 or HMAC of the file differs from the sidecar record.
         */
        TAMPERED,

        /** The file or its sidecar failed a per-use check. */
        INSECURE,

        /** The sidecar failed its strict read. */
        UNREADABLE
    }

    /**
     * The outcome of a verification; a failure names the file it concerns and the reason.
     *
     * @param outcome what the verification found
     * @param file    the file a failure concerns; empty if the verification passed
     * @param reason  why the verification failed; empty if it passed
     * @since 0.1
     */
    public record Result(Outcome outcome, Optional<Path> file, Optional<String> reason) {

        private static final Result VERIFIED = new Result(Outcome.VERIFIED, Optional.empty(), Optional.empty());
        private static final Result NOTHING = new Result(Outcome.NOTHING_TO_VERIFY, Optional.empty(),
                Optional.empty());

        /**
         * @throws NullPointerException     if a component is {@code null}
         * @throws IllegalArgumentException if a failure lacks its file or reason, or a pass carries one
         */
        public Result {
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(reason, "reason");
            var failure = !passes(outcome);
            if (failure != file.isPresent() || failure != reason.isPresent()) {
                throw new IllegalArgumentException("A failure names its file and reason, a pass neither: " + outcome);
            }
        }

        /** @return whether the file matches its sidecar or neither exists */
        public boolean passed() {
            return passes(outcome);
        }

        private static boolean passes(Outcome outcome) {
            return outcome == Outcome.VERIFIED || outcome == Outcome.NOTHING_TO_VERIFY;
        }

        private static Result failed(Outcome outcome, Path file, String reason) {
            return new Result(outcome, Optional.of(file), Optional.of(reason));
        }
    }
}
