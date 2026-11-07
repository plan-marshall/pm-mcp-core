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
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import de.planmarshall.runtime.enrolment.EnrolmentKey;

/**
 * Generates a 32-byte key of the enrolment integrity algorithm ({@link EnrolmentKey#ALGORITHM}) from drawn letters.
 * The key is never the integrity key of a test's enrolment, so a test uses it as the key anyone without the integrity
 * key could sign with.
 *
 * @since 0.1
 */
public final class HmacKeyGenerator implements TypedGenerator<SecretKey> {

    /** The number of bytes of the key, the length of the integrity key. */
    private static final int KEY_BYTES = 32;

    @Override
    public SecretKey next() {
        return new SecretKeySpec(Generators.letterStrings(KEY_BYTES, KEY_BYTES).next()
                .getBytes(StandardCharsets.UTF_8), EnrolmentKey.ALGORITHM);
    }

    @Override
    public Class<SecretKey> getType() {
        return SecretKey.class;
    }
}
