/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.runtime.test.TestBases;

/**
 * The self-signed certificate of the web listener. The algorithms, object identifiers, subject, permissions, DER
 * encodings and the loopback names are the specified TLS parameters; the host name and the interface address are
 * generated. The {@link SecureRandom} is the entropy source of the key generation, not test data.
 */
@EnableGeneratorController
@DisplayName("Self-signed certificate of the web listener")
class WebTlsTest {

    /** How many key pairs the strict-DER test generates: enough to meet every leading-byte case, not test data. */
    private static final int GENERATED_PAIRS = 2000;

    /** Contract literal: the signature algorithm of the certificate. */
    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";

    /** Contract literal: the loopback name every certificate carries. */
    private static final String LOCALHOST = "localhost";

    /** @return a generated host name, a single lower-case DNS label */
    private static String hostName() {
        return Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }

    /** @return a generated address of the private range 10.0.0.0/8, as an interface of the host carries it */
    private static String interfaceAddress() {
        return "10.%d.%d.%d".formatted(Generators.integers(0, 255).next(), Generators.integers(0, 255).next(),
                Generators.integers(1, 254).next());
    }

    @Test
    @DisplayName("generates a verifiable ECDSA P-256 certificate with host, interface and loopback names")
    void shouldGenerateCertificate() throws Exception {
        var host = hostName();
        var address = interfaceAddress();
        var tls = WebTls.generate(host, List.of(InetAddress.getByName(address)), new SecureRandom(),
                Instant.now());

        var certificate = tls.certificate();
        certificate.verify(certificate.getPublicKey());
        certificate.checkValidity();
        assertEquals(SIGNATURE_ALGORITHM, certificate.getSigAlgName(), "the certificate is signed with ECDSA");
        assertEquals("CN=pm-mcpd", certificate.getSubjectX500Principal().getName(),
                "the subject must name the daemon");
        assertEquals(-1, certificate.getBasicConstraints(), "the certificate must be no CA certificate");
        // Contract literal: the object identifier of TLS server authentication
        assertTrue(certificate.getExtendedKeyUsage().contains("1.3.6.1.5.5.7.3.1"),
                "the certificate must serve TLS server authentication");
        var names = tls.names();
        // Contract literals: the mDNS suffix and the IPv4 loopback address every certificate names
        for (var expected : List.of(host, host + ".local", LOCALHOST, address, "127.0.0.1")) {
            assertTrue(names.contains(expected), "the names of the certificate must hold " + expected);
        }
        // Contract literal: a SHA-256 fingerprint of 32 bytes, two hex digits each and a colon between them
        assertEquals(32 * 3 - 1, tls.fingerprint().length(), "the fingerprint must be a colon-separated SHA-256");
        var der = Base64.getMimeDecoder().decode(tls.privateKeyPem().replaceAll("-----[A-Z ]+-----", ""));
        assertEquals("EC", KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der))
                .getAlgorithm(), "the private key must be an EC key");
    }

    @Test
    @DisplayName("writes key and certificate privately once and reuses them")
    void shouldPersistAndReuse() throws Exception {
        // A fixture argument, the prefix of the base directory, not test data
        var base = TestBases.create("pmt");
        try {
            // Contract literals: the directory of the TLS material below the base and its POSIX modes
            var dir = base.resolve("web").resolve("tls");
            var first = WebTls.loadOrCreate(dir);
            var second = WebTls.loadOrCreate(dir);

            assertEquals(first.fingerprint(), second.fingerprint(), "the second load must reuse the material");
            assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(dir)),
                    "the directory must be private to the owner");
            assertEquals("rw-------",
                    PosixFilePermissions.toString(Files.getPosixFilePermissions(dir.resolve(WebTls.KEY_FILE))),
                    "the key file must be private to the owner");
            assertTrue(first.names().contains(LOCALHOST), "the certificate must name localhost");
        } finally {
            TestBases.delete(base);
        }
    }

    @Test
    @DisplayName("encodes DER lengths and object identifiers (the specified X.690 encodings)")
    void shouldEncodeDer() {
        var length = Generators.integers(256, 65_535).next();

        var longValue = Der.tlv(Der.TAG_OCTET_STRING, new byte[length]);

        assertEquals("0603551d11", HexFormat.of().formatHex(Der.oid("2.5.29.17")),
                "the OID of subjectAltName must be encoded");
        assertEquals("06072a8648ce3d0201", HexFormat.of().formatHex(Der.oid("1.2.840.10045.2.1")),
                "the OID of an EC public key must be encoded");
        assertEquals("0482%04x".formatted(length), HexFormat.of().formatHex(longValue, 0, 4),
                "a two-byte length must take the long form");
        assertEquals("020100", HexFormat.of().formatHex(Der.integer(BigInteger.ZERO)),
                "zero must be encoded as a one-byte integer");
    }

    @Test
    @DisplayName("finds a host name and interface addresses")
    void shouldInspectHost() {
        assertFalse(WebTls.hostName().isBlank(), "the host name of the machine must be found");
        assertTrue(WebTls.interfaceAddresses().stream().noneMatch(InetAddress::isLinkLocalAddress),
                "no link-local address may be named");
    }

    @Test
    @DisplayName("every generated key pair yields strict DER, a matching key and a certificate that verifies")
    void shouldGenerateValidMaterialForManyKeyPairs() throws Exception {
        var random = new SecureRandom();
        var host = hostName();
        var addresses = List.of(InetAddress.getByName(interfaceAddress()));
        for (int i = 0; i < GENERATED_PAIRS; i++) {
            var tls = WebTls.generate(host, addresses, random, Instant.now());

            var certificateDer = der(tls.certificatePem());
            assertEquals(certificateDer.length, strictLength(certificateDer, 0),
                    "the certificate DER must be strict, pair " + i);
            var keyDer = der(tls.privateKeyPem());
            assertEquals(keyDer.length, strictLength(keyDer, 0), "the key DER must be strict, pair " + i);
            var certificate = tls.certificate();
            certificate.verify(certificate.getPublicKey());
            certificate.checkValidity();
            assertTrue(certificate.getSerialNumber().signum() > 0, "the serial must be positive, pair " + i);
            var key = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(keyDer));
            var signer = Signature.getInstance(SIGNATURE_ALGORITHM);
            signer.initSign(key, random);
            signer.update(certificateDer);
            var verifier = Signature.getInstance(SIGNATURE_ALGORITHM);
            verifier.initVerify(certificate.getPublicKey());
            verifier.update(certificateDer);
            assertTrue(verifier.verify(signer.sign()), "key and certificate must match, pair " + i);
        }
    }

    private static byte[] der(String pem) {
        return Base64.getMimeDecoder().decode(pem.replaceAll("-----[A-Z ]+-----", ""));
    }

    /**
     * Walks one DER element: definite lengths in their shortest form, and the children of a constructed element
     * filling it exactly.
     *
     * @return the offset behind the element
     */
    private static int strictLength(byte[] der, int offset) throws GeneralSecurityException {
        var tag = der[offset] & 0xFF;
        var first = der[offset + 1] & 0xFF;
        var position = offset + 2;
        int length;
        if (first < 0x80) {
            length = first;
        } else {
            var count = first & 0x7F;
            if (count == 0 || count > 3 || (der[position] & 0xFF) == 0) {
                throw new GeneralSecurityException("length form at " + offset);
            }
            length = 0;
            for (int i = 0; i < count; i++) {
                length = length << 8 | der[position++] & 0xFF;
            }
            if (length < 0x80) {
                throw new GeneralSecurityException("long form for a short length at " + offset);
            }
        }
        var end = position + length;
        if (end > der.length) {
            throw new GeneralSecurityException("element at " + offset + " exceeds the encoding");
        }
        if (tag == Der.TAG_INTEGER && length > 1 && (der[position] == 0 && (der[position + 1] & 0x80) == 0
                || der[position] == (byte) 0xFF && (der[position + 1] & 0x80) != 0)) {
            throw new GeneralSecurityException("integer with a redundant leading byte at " + offset);
        }
        if ((tag & 0x20) != 0) {
            var child = position;
            while (child < end) {
                child = strictLength(der, child);
            }
            if (child != end) {
                throw new GeneralSecurityException("children of the element at " + offset + " overrun it");
            }
        }
        return end;
    }
}
