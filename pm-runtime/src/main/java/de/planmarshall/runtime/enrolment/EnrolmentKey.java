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
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;


import de.cuioss.tools.logging.CuiLogger;
import de.planmarshall.core.log.PmMcpLogMessages.ERROR;
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
 * A read has three outcomes: the key is present, it is absent, or it cannot be read (an insecure or malformed key
 * file, a failing keyring), which is a {@link SecretStoreException}; an absent key is never confused with an
 * unreadable one. The key never appears in a log record or an exception message. One instance serves the runtime, so
 * two first enrolment writes never create two keys.
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
     * Reads the key, creating it if it does not exist yet: the path of an enrolment write.
     *
     * @return the key
     * @throws SecretStoreException if the key exists and cannot be read, or cannot be created
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
        return read().orElseThrow(() -> unavailable(new SecretStoreException(SecretStoreException.Reason.FAILED,
                "key file %s vanished after another writer created it".formatted(keyFile))));
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

    /** @return whether this call created the key; {@code false} if another writer created the key file first */
    private boolean create(String encoded) {
        try {
            if (inKeyFile()) {
                return writeKeyFile(encoded);
            }
            store.put(CredentialAccount.global(ACCOUNT), encoded);
            return true;
        } catch (SecretStoreException e) {
            throw unavailable(e);
        }
    }

    /** @return whether this call created the file; {@code false} if it existed already */
    private boolean writeKeyFile(String encoded) {
        try (var channel = FileChannel.open(keyFile, Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                     PosixModes.fileAttribute())) {
            var buffer = ByteBuffer.wrap(encoded.getBytes(StandardCharsets.US_ASCII));
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
            return true;
        } catch (FileAlreadyExistsException _) {
            return false;
        } catch (IOException e) {
            throw new SecretStoreException(SecretStoreException.Reason.FAILED, "key file %s cannot be created"
                    .formatted(keyFile), e);
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
