/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.corpus;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A Git LFS pointer file (PM-WORK-6): what a repository stores in place of a file kept in LFS, naming the object by
 * its SHA-256 and size.
 * <p>
 * {@link #parse(byte[])} accepts exactly the pointer of the LFS specification v1 that git-lfs writes: the line
 * {@code version https://git-lfs.github.com/spec/v1} first, then {@code oid sha256:<64 hexadecimal digits>} and
 * {@code size <bytes>}, keys sorted, each line ended by a line feed, nothing else. Anything else is not a pointer.
 *
 * @param oid  the SHA-256 of the object, 64 lower-case hexadecimal digits
 * @param size the size of the object in bytes
 * @since 0.1
 */
public record LfsPointer(String oid, long size) {

    /** The version line every pointer starts with. */
    public static final String VERSION = "version https://git-lfs.github.com/spec/v1";

    /** The longest pointer file git-lfs reads as a pointer. */
    public static final int MAX_POINTER_SIZE = 1024;

    private static final Pattern POINTER = Pattern.compile(
            "\\A" + Pattern.quote(VERSION) + "\noid sha256:([0-9a-f]{64})\nsize (0|[1-9][0-9]{0,18})\n\\z");

    /**
     * @throws IllegalArgumentException if the oid is not a SHA-256 or the size is negative
     */
    public LfsPointer {
        SourceManifest.require(oid, SourceManifest.SHA256, "oid");
        if (size < 0) {
            throw new IllegalArgumentException("'size' must not be negative: " + size);
        }
    }

    /**
     * @param content the content of a file at a commit
     * @return the pointer; empty if the content is not exactly an LFS pointer of the specification v1
     */
    public static Optional<LfsPointer> parse(byte[] content) {
        Objects.requireNonNull(content, "content");
        if (content.length > MAX_POINTER_SIZE) {
            return Optional.empty();
        }
        var matcher = POINTER.matcher(new String(content, StandardCharsets.US_ASCII));
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new LfsPointer(matcher.group(1), Long.parseLong(matcher.group(2))));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** @return the pointer file as git-lfs writes it, the size in ASCII digits whatever the default locale */
    public byte[] toBytes() {
        return (VERSION + "\noid sha256:" + oid + "\nsize " + size + "\n")
                .getBytes(StandardCharsets.US_ASCII);
    }
}
