/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.credentials;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import javax.crypto.SecretKey;

import de.planmarshall.core.service.LockManager;
import de.planmarshall.runtime.enrolment.EnrolmentKey;
import de.planmarshall.runtime.enrolment.EnrolmentSidecars;

/**
 * Carries the enrolment integrity key from one credential store into another, the part the store move and the first
 * recording of a store share (PM-CRED-2, PM-SEC-5): the key is copied, read back from the target and compared, and
 * every HMAC sidecar of the machine is verified under the key read back ({@link EnrolmentSidecars}). Nothing is
 * deleted here and no store is recorded: the caller copies the key last, verifies, switches {@code credentials.store}
 * once, and only then deletes the key from the store it came from.
 * <p>
 * A key a store holds already is never overwritten by {@link #copy(SecretStore, SecretStore)}: it writes nothing where
 * the target holds the same key and refuses a target that holds another one. {@link #targetKey(SecretStore,
 * SecretStore)} tells the move what the target holds before it starts, so its clean-up deletes from the target only a
 * key it copied. The first recording reconciles two differing keys instead, by the sidecars
 * ({@link #failureUnder(SecretKey)}, ADR runtime-credentials/007): the store being recorded is given the key the
 * sidecars verify under and the other store's key is deleted after the switch.
 * <p>
 * The caller holds {@code locks/credentials.lock}. The sidecars are verified in a transaction of their own, because
 * the verification takes the audit leaf lock last, after which a transaction may acquire nothing, and the caller takes
 * {@code locks/machine-config.lock} after it for the switch. No key reaches a log record or an exception message.
 *
 * @since 0.1
 */
final class IntegrityKeyCarry {

    /** What the target store holds of the integrity key, compared with the store the key is carried from. */
    enum TargetKey {
        /** The target holds no integrity key. */
        NONE,
        /** The target holds an integrity key, and the source holds the same key or none. */
        HELD,
        /** The target holds another integrity key than the source; carrying the key would lose the target's key. */
        OTHER
    }

    private final Path stateDirectory;
    private final LockManager locks;
    private final String user;
    private final EnrolmentSidecars sidecars;
    private final SecureRandom random = new SecureRandom();

    /**
     * @param base  the base directory {@code <PM_MCP_BASE>} of the runtime
     * @param locks the lock manager, which opens the transaction of the sidecar verification
     * @param user  the name of the running user, the owner of the key file and of every file the verification reads
     */
    IntegrityKeyCarry(Path base, LockManager locks, String user) {
        var root = Objects.requireNonNull(base, "base").toAbsolutePath().normalize();
        this.stateDirectory = root.resolve("state");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.user = Objects.requireNonNull(user, "user");
        this.sidecars = new EnrolmentSidecars(root, user);
    }

    /**
     * @param store a credential store
     * @return the integrity key as it lives in the store: its keyring entry, or the key file below {@code state/}
     */
    EnrolmentKey keyIn(SecretStore store) {
        return new EnrolmentKey(store, stateDirectory, user, random);
    }

    /**
     * Compares the integrity key the target holds with the key of the source, before anything is carried.
     *
     * @param from   the store the key lives in
     * @param target the store the key is to be carried into
     * @return {@link TargetKey#NONE} if the target holds no key, {@link TargetKey#OTHER} if both hold a key and the keys
     *         differ, else {@link TargetKey#HELD}
     * @throws SecretStoreException with the reason of the store if a key cannot be read
     */
    TargetKey targetKey(SecretStore from, SecretStore target) {
        var held = keyIn(target).read();
        if (held.isEmpty()) {
            return TargetKey.NONE;
        }
        var key = keyIn(from).read();
        return key.isPresent() && !sameKey(key.get(), held.get()) ? TargetKey.OTHER : TargetKey.HELD;
    }

    /**
     * @param from   the store the key lives in
     * @param target the store the key is to be carried into
     * @return the refusal of a target that holds another integrity key than the source, naming both stores
     */
    static SecretStoreException otherKey(SecretStore from, SecretStore target) {
        return new SecretStoreException(SecretStoreException.Reason.FAILED,
                "'%s' holds another integrity key than '%s'".formatted(target.name(), from.name()));
    }

    /**
     * Copies the integrity key into the target and reads it back. A target that holds the same key already is not
     * written; a target that holds another key is refused and keeps it.
     *
     * @param from   the store the key lives in
     * @param target the store the key is copied into
     * @return the key read back from the target; empty if {@code from} holds no key, so there is nothing signed
     * @throws SecretStoreException with {@code credential_store_failed} if the target holds another key, or the key
     *                              does not read back as the key copied, or with the reason of the store if it cannot
     *                              be read or written
     */
    Optional<SecretKey> copy(SecretStore from, SecretStore target) {
        var key = keyIn(from).read();
        if (key.isEmpty()) {
            return key;
        }
        var targetKey = keyIn(target);
        var held = targetKey.read();
        if (held.isPresent()) {
            if (!sameKey(key.get(), held.get())) {
                throw otherKey(from, target);
            }
            return held;
        }
        targetKey.write(key.get());
        var back = targetKey.read();
        if (back.isEmpty() || !sameKey(key.get(), back.get())) {
            throw new SecretStoreException(SecretStoreException.Reason.FAILED,
                    "the integrity key does not read back from '%s'".formatted(target.name()));
        }
        return back;
    }

    /**
     * Verifies every sidecar of the machine under the key, in a transaction of its own.
     *
     * @param key the key read back from the target
     * @throws SecretStoreException with {@code credential_store_failed} if a sidecar does not verify, naming the file
     *                              and the reason
     * @throws UncheckedIOException if the enrolment directory cannot be listed or a file cannot be read
     */
    void verifySidecars(SecretKey key) {
        var failure = failureUnder(key);
        if (failure.isPresent()) {
            throw new SecretStoreException(SecretStoreException.Reason.FAILED,
                    "%s does not verify under the moved integrity key: %s".formatted(failure.get().file(),
                            failure.get().reason()));
        }
    }

    /**
     * The sidecars of the machine under one key, in a transaction of its own, so a caller that weighs two candidate
     * keys can tell under which of them every sidecar verifies.
     *
     * @param key the key the sidecars are verified under
     * @return the first sidecar that does not verify under the key; empty if every sidecar verifies
     * @throws UncheckedIOException if the enrolment directory cannot be listed or a file cannot be read
     */
    Optional<EnrolmentSidecars.Failure> failureUnder(SecretKey key) {
        Objects.requireNonNull(key, "key");
        try (var verification = locks.openTransaction()) {
            return sidecars.verify(verification, key);
        }
    }

    /**
     * Compares two keys in constant time and clears the bytes compared.
     *
     * @return whether both keys are the same
     */
    static boolean sameKey(SecretKey expected, SecretKey actual) {
        var expectedBytes = expected.getEncoded();
        var actualBytes = actual.getEncoded();
        try {
            return MessageDigest.isEqual(expectedBytes, actualBytes);
        } finally {
            Arrays.fill(expectedBytes, (byte) 0);
            Arrays.fill(actualBytes, (byte) 0);
        }
    }
}
