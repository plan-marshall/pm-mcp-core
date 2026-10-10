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

import java.util.Objects;
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
}
