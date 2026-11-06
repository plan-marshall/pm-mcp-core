/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.testing.generators;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;
import java.util.HexFormat;

/**
 * Generates a SHA-256 digest in the form the enrolments of {@code pm-runtime} record it: 64 lower-case hexadecimal
 * characters. The bytes are drawn from the generator, so the digest belongs to no real content; a test uses it where
 * the code under test records or compares a digest but never computes one.
 *
 * @since 0.1
 */
public final class Sha256DigestGenerator implements TypedGenerator<String> {

    /** The number of bytes of a SHA-256 digest. */
    private static final int DIGEST_BYTES = 32;

    @Override
    public String next() {
        var bytes = new byte[DIGEST_BYTES];
        var generator = Generators.bytes();
        for (var index = 0; index < bytes.length; index++) {
            bytes[index] = generator.next();
        }
        return HexFormat.of().formatHex(bytes);
    }

    @Override
    public Class<String> getType() {
        return String.class;
    }
}
