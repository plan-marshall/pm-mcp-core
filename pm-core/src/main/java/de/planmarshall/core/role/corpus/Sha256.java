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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import lombok.experimental.UtilityClass;

/**
 * The SHA-256 of a source file or LFS object (PM-WORK-6), as lower-case hexadecimal digits, and its comparison with
 * the hash a manifest or pointer expects in constant time.
 *
 * @since 0.1
 */
@UtilityClass
public class Sha256 {

    private static final HexFormat HEX = HexFormat.of();

    /**
     * @param content the bytes
     * @return their SHA-256, 64 lower-case hexadecimal digits
     */
    public static String hex(byte[] content) {
        Objects.requireNonNull(content, "content");
        return HEX.formatHex(digest().digest(content));
    }

    /**
     * Reads the stream to its end; the caller closes it.
     *
     * @param content the bytes
     * @return their SHA-256, 64 lower-case hexadecimal digits
     * @throws IOException if the stream cannot be read
     */
    public static String hex(InputStream content) throws IOException {
        Objects.requireNonNull(content, "content");
        var digest = digest();
        try (var sink = new DigestOutputStream(OutputStream.nullOutputStream(), digest)) {
            content.transferTo(sink);
        }
        return HEX.formatHex(digest.digest());
    }

    /**
     * Compares the SHA-256 of the bytes with the expected one; the time the comparison takes does not depend on where
     * the two differ.
     *
     * @param content  the bytes
     * @param expected the expected SHA-256, 64 hexadecimal digits
     * @return {@code true} if the bytes have the expected SHA-256; {@code false} if they do not, or the expected value
     *         is not a SHA-256
     */
    public static boolean matches(byte[] content, String expected) {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(expected, "expected");
        if (!SourceManifest.SHA256.matcher(expected).matches()) {
            return false;
        }
        return MessageDigest.isEqual(digest().digest(content), HEX.parseHex(expected));
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is a required algorithm of every Java platform", e);
        }
    }
}
