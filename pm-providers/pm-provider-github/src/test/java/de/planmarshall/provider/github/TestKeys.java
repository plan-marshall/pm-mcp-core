/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.github;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Arrays;
import java.util.Base64;

/**
 * A generated RSA key pair with its PKCS#8 and PKCS#1 PEM, and JWT verification (test helper).
 * <p>
 * Every literal here is a contract: the PEM armour lines of RFC 7468, the algorithm names of the JDK, the 2048-bit key
 * size GitHub issues, and the DER layout of the PKCS#8 prefix. The key material itself comes from the
 * {@link KeyPairGenerator}, not from the test-data generators, so the fields holding it stay static and final.
 */
final class TestKeys {

    /** The key algorithm of a GitHub App private key (contract: JDK algorithm name). */
    private static final String RSA = "RSA";
    /** The key size of a GitHub App private key (contract: GitHub issues 2048-bit keys). */
    private static final int KEY_SIZE = 2048;
    /** The signature algorithm of RS256 (contract: JDK algorithm name of RFC 7518 RS256). */
    private static final String SHA256_WITH_RSA = "SHA256withRSA";
    /** The line length of PEM base64 (contract: RFC 7468). */
    private static final int PEM_LINE = 64;
    /** The line separator of PEM (contract: RFC 7468). */
    private static final byte[] PEM_SEPARATOR = "\n".getBytes(StandardCharsets.US_ASCII);

    static final KeyPair PAIR = generate();
    /** The PKCS#8 form; its armour lines are the contract of RFC 7468. */
    static final String PKCS8_PEM = "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder(PEM_LINE, PEM_SEPARATOR).encodeToString(PAIR.getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----\n";

    /**
     * The same key as GitHub delivers it: the {@code RSAPrivateKey} structure the JDK's PKCS#8 encoding of a
     * 2048-bit key carries as the content of its OCTET STRING, after a fixed prefix of {@value #PKCS8_PREFIX} bytes.
     * Its armour lines are the contract of the PKCS#1 PEM form.
     */
    static final String PKCS1_PEM = "-----BEGIN RSA PRIVATE KEY-----\n"
            + Base64.getMimeEncoder(PEM_LINE, PEM_SEPARATOR).encodeToString(pkcs1())
            + "\n-----END RSA PRIVATE KEY-----\n";

    /** SEQUENCE header (4), version (3), AlgorithmIdentifier (15), OCTET STRING header (4): the DER contract. */
    private static final int PKCS8_PREFIX = 26;
    /** The DER tag of an OCTET STRING (contract: X.690). */
    private static final int OCTET_STRING = 0x04;
    /** The DER long-form length marker of two length bytes (contract: X.690). */
    private static final int TWO_LENGTH_BYTES = 0x82;

    private TestKeys() {
    }

    private static KeyPair generate() {
        try {
            var generator = KeyPairGenerator.getInstance(RSA);
            generator.initialize(KEY_SIZE);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static byte[] pkcs1() {
        byte[] pkcs8 = PAIR.getPrivate().getEncoded();
        if (pkcs8[PKCS8_PREFIX - 4] != OCTET_STRING || (pkcs8[PKCS8_PREFIX - 3] & 0xff) != TWO_LENGTH_BYTES) {
            throw new IllegalStateException("unexpected PKCS#8 layout");
        }
        return Arrays.copyOfRange(pkcs8, PKCS8_PREFIX, pkcs8.length);
    }

    /** Verifies the RS256 signature and returns the decoded claims JSON. */
    static String verifiedClaims(String jwt, PublicKey key) throws GeneralSecurityException {
        // the compact serialization of a JWT: three base64url parts separated by dots (contract: RFC 7515)
        String[] parts = jwt.split("\\.");
        if (parts.length != 3) {
            throw new GeneralSecurityException("not a compact JWT");
        }
        var verifier = Signature.getInstance(SHA256_WITH_RSA);
        verifier.initVerify(key);
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        if (!verifier.verify(Base64.getUrlDecoder().decode(parts[2]))) {
            throw new GeneralSecurityException("bad signature");
        }
        return new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
    }

    static String header(String jwt) {
        return new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[0]), StandardCharsets.UTF_8);
    }
}
