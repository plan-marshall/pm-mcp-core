/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.launch;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * The text of one skill the server delivers to a worker, with the digest of exactly that text (PM-SKILL-4): the
 * {@code skill://} URI the worker sees, the in-band text, and the SHA-256 the delivery record keeps. An extension skill
 * carries the digest of the read, so a changed skill changes its digest and is delivered again to the next generation.
 *
 * @param uri    the skill URI
 * @param text   the text rendered in-band or served by {@code pm_skill}
 * @param digest the SHA-256 of the UTF-8 text, 64 lower-case hexadecimal digits
 * @since 0.1
 */
public record ResolvedSkill(String uri, String text, String digest) {

    /** @throws IllegalArgumentException if the URI is not a skill URI, the text is empty or the digest is not a SHA-256 */
    public ResolvedSkill {
        Objects.requireNonNull(uri, "uri");
        if (!uri.startsWith("skill://pm/") || !uri.endsWith("/SKILL.md")) {
            throw new IllegalArgumentException("Not a skill URI: " + uri);
        }
        Objects.requireNonNull(text, "text");
        if (text.isEmpty()) {
            throw new IllegalArgumentException("The skill '%s' renders empty".formatted(uri));
        }
        Objects.requireNonNull(digest, "digest");
        if (!digest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Not a SHA-256: " + digest);
        }
    }

    /**
     * @param uri  the skill URI
     * @param text the text of the skill
     * @return the skill with the digest of its UTF-8 text
     */
    public static ResolvedSkill of(String uri, String text) {
        return new ResolvedSkill(uri, text, sha256(text));
    }

    /**
     * @param text a text
     * @return its SHA-256 as lowercase hexadecimal digits
     */
    public static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is available on every Java platform", e);
        }
    }
}
