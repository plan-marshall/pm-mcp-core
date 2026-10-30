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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The digest of a store (PM-IMPL-7): its size in bytes and its SHA-256, as a transaction journal and a digest sidecar
 * record it.
 *
 * @param size   the size of the store in bytes
 * @param sha256 the SHA-256 of the store, in lower-case hexadecimal digits
 * @since 0.1
 */
public record StoreDigest(long size, String sha256) {

    private static final Pattern SHA_256 = Pattern.compile("^[0-9a-f]{64}$");

    /**
     * @throws NullPointerException     if the SHA-256 is {@code null}
     * @throws IllegalArgumentException if the size is negative or the SHA-256 is not 64 lower-case hexadecimal
     *                                  digits
     */
    public StoreDigest {
        Objects.requireNonNull(sha256, "sha256");
        if (size < 0) {
            throw new IllegalArgumentException("A size is not negative: " + size);
        }
        if (!SHA_256.matcher(sha256).matches()) {
            throw new IllegalArgumentException("Not a SHA-256 in lower-case hexadecimal digits: " + sha256);
        }
    }

    /**
     * @param content the content of a store
     * @return the digest of the content
     */
    public static StoreDigest of(byte[] content) {
        Objects.requireNonNull(content, "content");
        return new StoreDigest(content.length, StoreSnapshot.sha256(content));
    }

    /**
     * @param content the content of a store
     * @return whether the content has this size and this SHA-256
     */
    public boolean matches(byte[] content) {
        Objects.requireNonNull(content, "content");
        return content.length == size && StoreSnapshot.sha256(content).equals(sha256);
    }

    /**
     * @param file a store file
     * @return the digest of the file, computed by streaming it; empty if the file does not exist
     * @throws IOException if the file exists and cannot be read
     */
    static Optional<StoreDigest> ofFile(Path file) throws IOException {
        try {
            return Optional.of(builder().update(file).build());
        } catch (NoSuchFileException _) {
            return Optional.empty();
        }
    }

    /** @return a digest computed incrementally from chunks, with a bounded buffer for files */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Computes the digest of content that arrives in chunks: bytes in memory and files streamed through a bounded
     * buffer, in the order they are added. No chunk is kept; the digest equals {@link #of(byte[])} of the
     * concatenation. A builder is confined to the thread that creates it and must not be shared: it exists for the
     * span of one computation and is discarded with it.
     *
     * @since 0.1
     */
    static final class Builder {

        /** The size of the buffer a file is streamed through. */
        static final int BUFFER_SIZE = 8192;

        @SuppressWarnings("java:S9428") // the builder is the incremental digest of one thread; chunks are not kept
        private final MessageDigest digest = StoreSnapshot.newSha256();
        private long size;

        private Builder() {
        }

        /**
         * @param bytes the next chunk
         * @return this builder
         */
        Builder update(byte[] bytes) {
            Objects.requireNonNull(bytes, "bytes");
            digest.update(bytes);
            size += bytes.length;
            return this;
        }

        /**
         * @param file a file whose whole content is the next chunk
         * @return this builder
         * @throws IOException if the file cannot be read, a {@link NoSuchFileException} if it does not exist
         */
        Builder update(Path file) throws IOException {
            return update(file, Long.MAX_VALUE);
        }

        /**
         * @param file  a file whose first bytes are the next chunk
         * @param limit how many bytes of the file to add; all of them if the file is shorter
         * @return this builder
         * @throws IOException if the file cannot be read, a {@link NoSuchFileException} if it does not exist
         */
        Builder update(Path file, long limit) throws IOException {
            try (var in = Files.newInputStream(file)) {
                var buffer = new byte[BUFFER_SIZE];
                var remaining = limit;
                while (remaining > 0) {
                    var read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                    if (read < 0) {
                        break;
                    }
                    digest.update(buffer, 0, read);
                    size += read;
                    remaining -= read;
                }
            }
            return this;
        }

        /** @return the digest of every chunk added, in their order */
        StoreDigest build() {
            return new StoreDigest(size, HexFormat.of().formatHex(digest.digest()));
        }
    }
}
