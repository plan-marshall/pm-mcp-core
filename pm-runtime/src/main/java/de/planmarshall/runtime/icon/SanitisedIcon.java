/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.icon;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * One candidate icon after the accept step (Store Schemas § Harness Icon Store): the format its bytes turned out to have,
 * the bytes as they may be stored, and the digest the index records for them.
 * <p>
 * An SVG is re-written against the allow-list, so its bytes are not the candidate's; a PNG is kept byte for byte once its
 * chunk structure is closed. The type is a class rather than a record because it carries an array: a record's generated
 * {@code equals} and {@code hashCode} would compare the array by identity, and the store never needs to compare two icons.
 *
 * @since 0.1
 */
public final class SanitisedIcon {

    private static final HexFormat HEX = HexFormat.of();

    private final IconFormat format;
    private final byte[] bytes;
    private final String sha256;

    /**
     * @param format the format the bytes turned out to have
     * @param bytes  the bytes as they may be stored; kept as a copy
     * @param sha256 the digest of {@code bytes}, as {@code sha256:} and 64 lower-case hexadecimal digits
     * @throws NullPointerException     if a component is {@code null}
     * @throws IllegalArgumentException if the bytes are empty or the digest is not a {@code sha256:} digest
     */
    SanitisedIcon(IconFormat format, byte[] bytes, String sha256) {
        this.format = Objects.requireNonNull(format, "format");
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length == 0) {
            throw new IllegalArgumentException("A sanitised icon carries bytes");
        }
        if (sha256 == null || !sha256.matches("sha256:[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Not a sha256: digest: " + sha256);
        }
        this.bytes = bytes.clone();
        this.sha256 = sha256;
    }

    /**
     * @param format the format the bytes turned out to have
     * @param bytes  the bytes as they may be stored
     * @return the sanitised icon, with the digest of the bytes it keeps
     */
    public static SanitisedIcon of(IconFormat format, byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        return new SanitisedIcon(format, bytes, digest(bytes));
    }

    /**
     * @param bytes the bytes
     * @return the {@code sha256:} digest of the bytes
     */
    static String digest(byte[] bytes) {
        try {
            return "sha256:" + HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("The JDK provides no SHA-256", e);
        }
    }

    /** @return the format the bytes turned out to have */
    public IconFormat format() {
        return format;
    }

    /** @return the bytes as they may be stored; a copy */
    public byte[] bytes() {
        return bytes.clone();
    }

    /** @return the digest of the bytes, as {@code sha256:} and 64 lower-case hexadecimal digits */
    public String sha256() {
        return sha256;
    }

    /** @return the length of the stored bytes */
    public long size() {
        return bytes.length;
    }
}
