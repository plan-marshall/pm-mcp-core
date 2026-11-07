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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import de.planmarshall.provider.http.Json;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The GitHub App JWT. The claim names, the header values {@code RS256} and {@code JWT}, the issued-at back-dating of
 * 60 seconds and the lifetime of 600 seconds are the contract of GitHub's App authentication; the PEM armour lines and
 * the DER length encodings are the contract of RFC 7468 and X.690. The client id and the signing instant are
 * generated.
 */
@EnableGeneratorController
@DisplayName("GitHubAppJwt")
class GitHubAppJwtTest {

    /** The back-dating of {@code iat} against clock drift (contract: GitHub's App authentication). */
    private static final long BACKDATE_SECONDS = 60;
    /** The lifetime of the JWT (contract: GitHub's App authentication, at most ten minutes). */
    private static final long LIFETIME_SECONDS = 600;

    private Instant now;
    private String clientId;

    @BeforeEach
    void generate() {
        now = Instant.ofEpochSecond(Generators.longs(1_700_000_000L, 2_000_000_000L).next());
        clientId = Generators.letterStrings(8, 24).next();
    }

    @Test
    @DisplayName("signs RS256 verifiable with the public key, with the specified window and issuer")
    void signsAndVerifies() throws Exception {
        String jwt = GitHubAppJwt.sign(clientId, TestKeys.PKCS8_PEM, now);

        Object claims = Json.parse(TestKeys.verifiedClaims(jwt, TestKeys.PAIR.getPublic())).orElseThrow();
        Object header = Json.parse(TestKeys.header(jwt)).orElseThrow();

        assertAll(
                () -> assertEquals(Optional.of("RS256"), Json.string(header, "alg"), "the header names RS256"),
                () -> assertEquals(Optional.of("JWT"), Json.string(header, "typ"), "the header names the JWT type"),
                () -> assertEquals(Optional.of(String.valueOf(now.getEpochSecond() - BACKDATE_SECONDS)),
                        Json.string(claims, "iat"), "the issued-at claim is back-dated"),
                () -> assertEquals(Optional.of(String.valueOf(now.getEpochSecond() + LIFETIME_SECONDS)),
                        Json.string(claims, "exp"), "the expiry claim ends the lifetime"),
                () -> assertEquals(Optional.of(clientId), Json.string(claims, "iss"),
                        "the issuer claim is the client id"),
                () -> assertFalse(jwt.contains("="), "the JWT is base64url without padding"));
    }

    @Test
    @DisplayName("is rejected by another key and differs per signing instant")
    void otherKeyRejects() throws Exception {
        String jwt = GitHubAppJwt.sign(clientId, TestKeys.PKCS8_PEM, now);
        var other = KeyPairGenerator.getInstance("RSA");
        other.initialize(2048);
        var otherKey = other.generateKeyPair().getPublic();

        assertAll(
                () -> assertThrows(GeneralSecurityException.class, () -> TestKeys.verifiedClaims(jwt, otherKey),
                        "another key does not verify the JWT"),
                () -> assertNotEquals(jwt, GitHubAppJwt.sign(clientId, TestKeys.PKCS8_PEM, now.plusSeconds(1)),
                        "another signing instant yields another JWT"));
    }

    @Test
    @DisplayName("signs with a PKCS#1 key (BEGIN RSA PRIVATE KEY), the form GitHub delivers")
    void signsWithPkcs1() throws Exception {
        String jwt = GitHubAppJwt.sign(clientId, TestKeys.PKCS1_PEM, now);

        Object claims = Json.parse(TestKeys.verifiedClaims(jwt, TestKeys.PAIR.getPublic())).orElseThrow();

        assertAll(
                () -> assertEquals(Optional.of(clientId), Json.string(claims, "iss"),
                        "the issuer claim is the client id"),
                () -> assertEquals(GitHubAppJwt.sign(clientId, TestKeys.PKCS8_PEM, now), jwt,
                        "the same key yields the same signature"));
    }

    @Test
    @DisplayName("wraps a PKCS#1 key in exactly the PKCS#8 structure of the JDK")
    void wrapsPkcs1() throws Exception {
        assertAll(
                () -> assertArrayEquals(TestKeys.PAIR.getPrivate().getEncoded(),
                        GitHubAppJwt.wrapPkcs1(TestKeys.pkcs1()), "the wrapped key is the JDK's PKCS#8 form"),
                // CRLF line ends are a PEM variant the parser must accept
                () -> assertEquals(TestKeys.PAIR.getPrivate(),
                        GitHubAppJwt.privateKey(TestKeys.PKCS1_PEM.replace("\n", "\r\n")),
                        "a PKCS#1 PEM with CRLF reads as the same key"));
    }

    @ParameterizedTest(name = "length {0}")
    @CsvSource({"0,0400", "1,0401", "127,047f", "128,048180", "255,0481ff", "256,04820100", "65535,0482ffff",
            "65536,0483010000"})
    @DisplayName("writes DER lengths in the short and the minimal long form (X.690 boundaries)")
    void writesDerLengths(int length, String header) {
        byte[] tlv = GitHubAppJwt.tlv(0x04, new byte[length]);

        assertAll(
                () -> assertEquals(header, HexFormat.of().formatHex(tlv, 0, tlv.length - length),
                        "the tag and length bytes are minimal DER"),
                () -> assertEquals(header.length() / 2 + length, tlv.length,
                        "the TLV holds header and content alone"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "-----BEGIN RSA PRIVATE KEY-----\nMIIB\n-----END RSA PRIVATE KEY-----",
            "-----BEGIN RSA PRIVATE KEY-----\n!!not base64!!\n-----END RSA PRIVATE KEY-----",
            "-----BEGIN RSA PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----",
            "-----BEGIN PRIVATE KEY-----\nAAAA\n-----END RSA PRIVATE KEY-----",
            "-----BEGIN EC PRIVATE KEY-----\nAAAA\n-----END EC PRIVATE KEY-----",
            "-----BEGIN PRIVATE KEY-----\n!!not base64!!\n-----END PRIVATE KEY-----",
            "-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----",
            "not a pem"
    })
    @DisplayName("refuses keys that are not PEM-encoded PKCS#8 or PKCS#1 RSA, without echoing them (PEM fixtures)")
    void refusesKeys(String pem) {
        var refusal = assertThrows(GitHubAppJwt.JwtSigningException.class, () -> GitHubAppJwt.sign(clientId, pem, now),
                "a key that is no RSA PEM is refused");

        assertAll(
                () -> assertFalse(refusal.getMessage().contains("MIIB"), "the refusal does not echo the key"),
                // the bound of a refusal message that carries no key material
                () -> assertTrue(refusal.getMessage().length() < 120, "the refusal message stays short"));
    }
}
