/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.security;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import lombok.experimental.UtilityClass;

/**
 * Minting and digesting a worker's job token (PM-SEC-5, Job Token): at a spawn the server mints a random 256-bit token
 * and stores only its SHA-256 in the job record, so the token itself is never written to a store, a log or a message.
 * The worker's relay sends the token and the daemon resolves the job from its digest on every request, stateless.
 *
 * @since 0.1
 */
@UtilityClass
public final class JobTokens {

    private static final int TOKEN_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * @return a fresh 256-bit job token, base64url without padding
     */
    public static String mint() {
        var bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * @param token a job token
     * @return its SHA-256 over the UTF-8 bytes, lowercase hexadecimal digits
     */
    public static String sha256Hex(String token) {
        return HexFormat.of().formatHex(Secrets.sha256(token));
    }

    /**
     * @param digest a SHA-256 as raw bytes
     * @return the same digest as lowercase hexadecimal digits
     */
    public static String hex(byte[] digest) {
        return HexFormat.of().formatHex(digest);
    }
}
