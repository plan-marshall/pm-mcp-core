/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.enrolment;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import de.planmarshall.core.store.StoreDigest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The HMAC of an enrolment file (PM-EXEC-2, PM-SEC-5): HMAC-SHA-256 in lowercase hex over the exact bytes, compared in
 * constant time, carried by a sidecar record with the size and SHA-256 of the same bytes.
 */
@DisplayName("Enrolment HMAC")
class EnrolmentHmacTest {

    private static final SecretKey KEY = new SecretKeySpec("Jefe".getBytes(StandardCharsets.US_ASCII),
            EnrolmentKey.ALGORITHM);
    private static final byte[] CONTENT = "what do ya want for nothing?".getBytes(StandardCharsets.US_ASCII);

    /** RFC 4231, test case 2. */
    private static final String EXPECTED = "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843";

    @ParameterizedTest(name = "RFC 4231 vector with data ''{1}''")
    @CsvSource({
            "0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b, Hi There, "
                    + "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7",
            "4a656665, what do ya want for nothing?, "
                    + "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843"})
    @DisplayName("matches the known-answer vectors in lowercase hex")
    void knownAnswers(String keyHex, String data, String expected) {
        var key = new SecretKeySpec(HexFormat.of().parseHex(keyHex), EnrolmentKey.ALGORITHM);

        assertEquals(expected, EnrolmentHmac.hmac(key, data.getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    @DisplayName("verifies the recorded HMAC of the exact bytes and refuses a one-byte change")
    void verify() {
        var changed = Arrays.copyOf(CONTENT, CONTENT.length);
        changed[0] ^= 1;

        assertAll(
                () -> assertTrue(EnrolmentHmac.verify(KEY, CONTENT, EXPECTED)),
                () -> assertFalse(EnrolmentHmac.verify(KEY, changed, EXPECTED)),
                () -> assertFalse(EnrolmentHmac.verify(KEY, CONTENT, EXPECTED.toUpperCase())),
                () -> assertFalse(EnrolmentHmac.verify(KEY, CONTENT, EXPECTED.substring(1))));
    }

    @Test
    @DisplayName("the sidecar record carries the size and SHA-256 of the bytes and their HMAC")
    void sidecar() {
        var sidecar = EnrolmentHmac.sidecar(KEY, CONTENT);

        assertAll(
                () -> assertEquals(StoreDigest.of(CONTENT), sidecar.digest()),
                () -> assertEquals(CONTENT.length, sidecar.digest().size()),
                () -> assertEquals(EXPECTED, sidecar.hmac()),
                () -> assertTrue(sidecar.matches(CONTENT)));
    }

    @Test
    @DisplayName("refuses missing arguments and a key without key material")
    void refused() {
        var withoutMaterial = new WithoutMaterial();

        assertAll(
                () -> assertThrows(NullPointerException.class, () -> EnrolmentHmac.hmac(null, CONTENT)),
                () -> assertThrows(NullPointerException.class, () -> EnrolmentHmac.hmac(KEY, null)),
                () -> assertThrows(NullPointerException.class, () -> EnrolmentHmac.verify(KEY, CONTENT, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> EnrolmentHmac.hmac(withoutMaterial, CONTENT)));
    }

    /** A key that carries no key material, which a MAC refuses to be initialised with. */
    private static final class WithoutMaterial implements SecretKey {

        @Serial
        private static final long serialVersionUID = 1L;

        @Override
        public String getAlgorithm() {
            return EnrolmentKey.ALGORITHM;
        }

        @Override
        public String getFormat() {
            return "RAW";
        }

        @Override
        public byte[] getEncoded() {
            return null;
        }
    }
}
