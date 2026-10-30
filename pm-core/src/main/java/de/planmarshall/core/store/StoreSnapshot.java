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

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

/**
 * The lock-free read of a store (PM-IMPL-7): the content of the file as it is at the moment of the read, taken
 * without a lock of any kind. Because every write of a store replaces the file by one atomic rename, the read sees
 * the content before or after a write and never a part of one.
 * <p>
 * A snapshot never decides that a store was edited. Given the recorded size and SHA-256 of the store, it reports a
 * difference as {@link StoreIntegrity#DIGEST_UNVERIFIED} for the store it names, and the next access under the lock
 * of the store decides. A store that does not exist is a snapshot without content, not a failure.
 *
 * @since 0.1
 */
public final class StoreSnapshot {

    private static final String SHA_256 = "SHA-256";
    private static final String STORE = "store";

    private final Path file;
    private final @Nullable byte[] content;
    private final StoreIntegrity integrity;

    private StoreSnapshot(Path file, @Nullable byte[] content, StoreIntegrity integrity) {
        this.file = file;
        this.content = content;
        this.integrity = integrity;
    }

    /**
     * Reads the store without comparing its content with a digest.
     *
     * @param store the store file
     * @return the snapshot, with {@link StoreIntegrity#UNCHECKED}
     * @throws UncheckedIOException if the store exists and cannot be read
     */
    public static StoreSnapshot read(Path store) {
        Objects.requireNonNull(store, STORE);
        return new StoreSnapshot(store, readIfPresent(store).orElse(null), StoreIntegrity.UNCHECKED);
    }

    /**
     * Reads the store and compares its content with the recorded digest.
     *
     * @param store          the store file
     * @param recordedSize   the recorded size of the store in bytes
     * @param recordedSha256 the recorded SHA-256 of the store, in hexadecimal digits
     * @return the snapshot, {@link StoreIntegrity#VERIFIED} if the content has the recorded size and SHA-256 and
     *         {@link StoreIntegrity#DIGEST_UNVERIFIED} otherwise, also for a store that does not exist
     * @throws UncheckedIOException if the store exists and cannot be read
     */
    public static StoreSnapshot read(Path store, long recordedSize, String recordedSha256) {
        Objects.requireNonNull(store, STORE);
        Objects.requireNonNull(recordedSha256, "recordedSha256");
        var bytes = readIfPresent(store);
        var matches = bytes
                .filter(read -> read.length == recordedSize && sha256(read).equalsIgnoreCase(recordedSha256))
                .isPresent();
        return new StoreSnapshot(store, bytes.orElse(null),
                matches ? StoreIntegrity.VERIFIED : StoreIntegrity.DIGEST_UNVERIFIED);
    }

    /**
     * Reads the store without comparing its content, and reports it as unverified: the caller knows that the content
     * cannot be decided now, for example because a transaction of the store is in flight.
     *
     * @param store the store file
     * @return the snapshot, with {@link StoreIntegrity#DIGEST_UNVERIFIED}
     * @throws UncheckedIOException if the store exists and cannot be read
     */
    static StoreSnapshot unverified(Path store) {
        Objects.requireNonNull(store, STORE);
        return new StoreSnapshot(store, readIfPresent(store).orElse(null), StoreIntegrity.DIGEST_UNVERIFIED);
    }

    /**
     * @return the same read, reported as {@link StoreIntegrity#DIGEST_UNVERIFIED}: the caller learned after the read
     *         that its content cannot be decided, for example because a journal of the store appeared
     */
    StoreSnapshot asUnverified() {
        return new StoreSnapshot(file, content, StoreIntegrity.DIGEST_UNVERIFIED);
    }

    /** @return the store this snapshot was read from, which a report of an unverified digest names */
    public Path store() {
        return file;
    }

    /** @return the content of the store at the moment of the read; empty if the store did not exist */
    public Optional<byte[]> content() {
        return Optional.ofNullable(content).map(byte[]::clone);
    }

    /** @return what the read can say about the content */
    public StoreIntegrity integrity() {
        return integrity;
    }

    /**
     * @param bytes the bytes to digest
     * @return the SHA-256 of the bytes, in lower-case hexadecimal digits
     */
    static String sha256(byte[] bytes) {
        return HexFormat.of().formatHex(newSha256().digest(bytes));
    }

    /** @return a new SHA-256 message digest */
    static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance(SHA_256);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java platform provides " + SHA_256, e);
        }
    }

    /** One open of the file reads one inode in full; a rename during the read does not change what is read. */
    private static Optional<byte[]> readIfPresent(Path store) {
        try {
            return Optional.of(Files.readAllBytes(store));
        } catch (NoSuchFileException _) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("Store cannot be read: " + store, e);
        }
    }
}
