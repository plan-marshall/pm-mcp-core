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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;


import de.cuioss.tools.logging.CuiLogger;
import de.planmarshall.core.log.PmMcpLogMessages.ERROR;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.runtime.credentials.CredentialAccount;
import de.planmarshall.runtime.credentials.SecretStore;
import de.planmarshall.runtime.credentials.SecretStoreException;
import de.planmarshall.runtime.start.PosixModes;

/**
 * The enrolment integrity key (PM-EXEC-2, PM-SEC-5): the operator-lane secret the HMAC of every enrolment file is
 * computed with.
 * <p>
 * Where the active credential store is a keyring, the key is its entry under the global account
 * {@value #ACCOUNT}; where it is the fallback file store, the key is the file {@code <state>/}{@value #FILE_NAME},
 * owned by the running user with no permission bit outside {@code 0600}. The key is 256 random bits, kept
 * base64url-encoded without padding. It is created on the first enrolment write and never replaced; only the runtime
 * reads it.
 * <p>
 * The key file is created atomically: the key is written to a temporary file in the state directory with mode
 * {@code 0600} and synced, published under its name by a hard link that never replaces an existing file, and the
 * directory is synced. A crash therefore leaves the complete key or no key file. A temporary file a crash leaves
 * behind holds only key material at mode {@code 0600} and is read by no path.
 * <p>
 * A read has three outcomes: the key is present, it is absent, or it cannot be read (an insecure or malformed key
 * file, a failing keyring), which is a {@link SecretStoreException}; an absent key is never confused with an
 * unreadable one. The key never appears in a log record or an exception message.
 * <p>
 * Within one instance the methods are synchronized. Across runtime instances the callers that may create the key hold
 * {@link #creationLock(Path)}, the credentials lock, so two first enrolment writes never create two keys; a keyring
 * write replaces an existing secret, so the key is read back after the write and a key another writer won is
 * adopted.
 *
 * @since 0.1
 */
public final class EnrolmentKey {

    /** The global account of the key in a keyring. */
    public static final String ACCOUNT = "enrolment-integrity-key";

    /** The name of the key file below the state directory, on a host without a keyring. */
    public static final String FILE_NAME = "enrolment-hmac.key";

    /** The algorithm the key is used with. */
    public static final String ALGORITHM = "HmacSHA256";

    /** The length of the key in bytes. */
    static final int KEY_BYTES = 32;

    /** The name prefix of the temporary file a new key file is written to, followed by a random UUID. */
    static final String TEMPORARY_PREFIX = FILE_NAME + ".tmp.";

    private static final CuiLogger LOGGER = new CuiLogger(EnrolmentKey.class);

    private final SecretStore store;
    private final Path keyFile;
    private final EnrolmentFileCheck fileCheck;
    private final SecureRandom random;

    /**
     * @param store          the active credential store
     * @param stateDirectory the state directory {@code <PM_MCP_BASE>/state}, which holds the key file on a host
     *                       without a keyring
     * @param user           the name of the running user, the owner the key file must have
     * @param random         the source of a new key
     */
    public EnrolmentKey(SecretStore store, Path stateDirectory, String user, SecureRandom random) {
        this.store = Objects.requireNonNull(store, "store");
        this.keyFile = Objects.requireNonNull(stateDirectory, "stateDirectory").toAbsolutePath().normalize()
                .resolve(FILE_NAME);
        this.fileCheck = new EnrolmentFileCheck(user);
        this.random = Objects.requireNonNull(random, "random");
    }

    /**
     * @return the key; empty if it does not exist yet
     * @throws SecretStoreException if the key exists and cannot be read: an insecure or malformed key file, a failing
     *                              keyring
     */
    public synchronized Optional<SecretKey> read() {
        try {
            var encoded = inKeyFile() ? readKeyFile() : store.get(CredentialAccount.global(ACCOUNT));
            return encoded.map(this::decode);
        } catch (SecretStoreException e) {
            throw unavailable(e);
        }
    }

    /**
     * The lock a caller that may create the key holds around {@link #readOrCreate()}: the credentials lock of the
     * base, the existing machine-level lock of credential writes, because the key is a credential. Its id
     * {@code credentials} sorts before every enrolment lock id {@code enrolments/<project_id>}, so a transaction takes
     * it first and never after a project's lock; taking it while a project's lock is held would break the total lock
     * order.
     *
     * @param base the base directory of the runtime
     * @return the key of the credentials lock of the base
     */
    public static LockKey creationLock(Path base) {
        return LockKey.credentials(base);
    }

    /**
     * Reads the key, creating it if it does not exist yet: the path of an enrolment write. The caller holds
     * {@link #creationLock(Path)}, so two runtime instances never create two keys.
     * <p>
     * The key is read first, and only a key that does not exist is created: a key that exists but cannot be read (an
     * insecure, partial or malformed key file, a failing keyring) is a {@link SecretStoreException} of the read and is
     * never replaced. If another writer creates the key file between the read and the create, its key is kept and
     * returned. A keyring write replaces an existing secret, so after writing the keyring entry the key is read back
     * and the key the entry then holds is returned: a writer that bypassed the lock and wrote between the read and the
     * write wins, and its key is adopted.
     *
     * @return the key
     * @throws SecretStoreException if the key exists and cannot be read, cannot be created, or vanished after it was
     *                              written
     */
    public synchronized SecretKey readOrCreate() {
        var existing = read();
        if (existing.isPresent()) {
            return existing.get();
        }
        var bytes = new byte[KEY_BYTES];
        random.nextBytes(bytes);
        try {
            if (create(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes))) {
                return new SecretKeySpec(bytes, ALGORITHM);
            }
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
        var vanished = inKeyFile() ? "key file %s vanished after another writer created it".formatted(keyFile)
                : "%s vanished after it was written".formatted(source());
        return read().orElseThrow(
                () -> unavailable(new SecretStoreException(SecretStoreException.Reason.FAILED, vanished)));
    }

    /** @return the key file below the state directory, used on a host without a keyring */
    public Path keyFile() {
        return keyFile;
    }

    private boolean inKeyFile() {
        return SecretStore.FILE.equals(store.name());
    }

    private Optional<String> readKeyFile() {
        if (!Files.exists(keyFile, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        try {
            var violation = fileCheck.violation(keyFile);
            if (violation.isPresent()) {
                throw new SecretStoreException(SecretStoreException.Reason.INSECURE,
                        "key file %s refused: %s".formatted(keyFile, violation.get()));
            }
            return Optional.of(Files.readString(keyFile, StandardCharsets.US_ASCII).strip());
        } catch (IOException | UncheckedIOException e) {
            throw new SecretStoreException(SecretStoreException.Reason.FAILED, "key file %s cannot be read"
                    .formatted(keyFile), e);
        }
    }

    /**
     * @return whether the key this call generated is the key; {@code false} if another writer created the key file
     *         first, and always for a keyring, whose entry is read back because a write replaces what it holds
     */
    private boolean create(String encoded) {
        try {
            if (inKeyFile()) {
                return writeKeyFile(encoded);
            }
            store.put(CredentialAccount.global(ACCOUNT), encoded);
            return false;
        } catch (SecretStoreException e) {
            throw unavailable(e);
        }
    }

    /**
     * Creates the key file atomically: the key is written to a temporary file {@value #TEMPORARY_PREFIX}{@code <uuid>}
     * beside it with mode {@code 0600} and synced, the temporary file is published by a hard link, and the state
     * directory is forced to disk. A hard link never replaces an existing file, so a key another writer created first
     * is kept, and the name it publishes holds the complete key or does not exist. A rename is not used: on POSIX it
     * silently replaces an existing key file, and every HMAC written under the replaced key would be stranded. The
     * temporary file is removed on success and on failure. This path never deletes or overwrites an existing key file:
     * {@link #readOrCreate()} reads first, and a partial or unreadable key file is refused there, never replaced.
     *
     * @return whether this call created the file; {@code false} if it existed already
     * @throws SecretStoreException with {@link SecretStoreException.Reason#FAILED} if the key file cannot be created
     *                              (also on a file system without hard links; there is no non-atomic fallback), or if
     *                              it was created and the state directory cannot be forced to disk
     */
    private boolean writeKeyFile(String encoded) {
        var temporary = keyFile.resolveSibling(TEMPORARY_PREFIX + UUID.randomUUID());
        try {
            try {
                writeSynced(temporary, encoded);
                Files.createLink(keyFile, temporary);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (FileAlreadyExistsException _) {
            return false;
        } catch (IOException | UnsupportedOperationException e) {
            throw new SecretStoreException(SecretStoreException.Reason.FAILED, "key file %s cannot be created"
                    .formatted(keyFile), e);
        }
        forceStateDirectory();
        return true;
    }

    private static void writeSynced(Path file, String encoded) throws IOException {
        try (var channel = FileChannel.open(file, Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                     PosixModes.fileAttribute())) {
            var buffer = ByteBuffer.wrap(encoded.getBytes(StandardCharsets.US_ASCII));
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    private void forceStateDirectory() {
        try (var directory = FileChannel.open(keyFile.getParent(), StandardOpenOption.READ)) {
            directory.force(true);
        } catch (IOException e) {
            throw new SecretStoreException(SecretStoreException.Reason.FAILED,
                    "key file %s exists, but its directory cannot be forced to disk".formatted(keyFile), e);
        }
    }

    private SecretKey decode(String encoded) {
        byte[] bytes;
        try {
            bytes = Base64.getUrlDecoder().decode(encoded);
        } catch (IllegalArgumentException _) {
            throw new SecretStoreException(SecretStoreException.Reason.INVALID,
                    "%s does not hold an integrity key in base64url".formatted(source()));
        }
        try {
            if (bytes.length != KEY_BYTES) {
                throw new SecretStoreException(SecretStoreException.Reason.INVALID,
                        "%s does not hold an integrity key of %d bytes".formatted(source(), KEY_BYTES));
            }
            return new SecretKeySpec(bytes, ALGORITHM);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    private String source() {
        return inKeyFile() ? keyFile.toString() : "credential store '%s' account '%s'".formatted(store.name(), ACCOUNT);
    }

    private SecretStoreException unavailable(SecretStoreException e) {
        LOGGER.error(e, ERROR.ENROLMENT_KEY_UNAVAILABLE, source(), e.reason().code());
        return e;
    }
}
