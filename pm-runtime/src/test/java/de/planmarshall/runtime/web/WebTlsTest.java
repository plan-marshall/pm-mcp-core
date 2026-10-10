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


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.runtime.test.TestBases;

@DisplayName("Self-signed certificate of the web listener")
class WebTlsTest {

    private static final int GENERATED_PAIRS = 2000;

    @Test
    @DisplayName("generates a verifiable ECDSA P-256 certificate with host, interface and loopback names")
    void shouldGenerateCertificate() throws Exception {
        var tls = WebTls.generate("studio", List.of(InetAddress.getByName("192.168.1.20")), new SecureRandom(),
                Instant.now());

        var certificate = tls.certificate();
        certificate.verify(certificate.getPublicKey());
        certificate.checkValidity();
        assertEquals("SHA256withECDSA", certificate.getSigAlgName());
        assertEquals("CN=pm-mcpd", certificate.getSubjectX500Principal().getName());
        assertEquals(-1, certificate.getBasicConstraints());
        assertTrue(certificate.getExtendedKeyUsage().contains("1.3.6.1.5.5.7.3.1"));
        var names = tls.names();
        for (var expected : List.of("studio", "studio.local", "localhost", "192.168.1.20", "127.0.0.1")) {
            assertTrue(names.contains(expected), names.toString());
        }
        assertEquals(32 * 3 - 1, tls.fingerprint().length());
        var der = Base64.getMimeDecoder().decode(tls.privateKeyPem().replaceAll("-----[A-Z ]+-----", ""));
        assertEquals("EC", KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der))
                .getAlgorithm());
    }

    @Test
    @DisplayName("writes key and certificate privately once and reuses them")
    void shouldPersistAndReuse() throws Exception {
        var base = TestBases.create("pmt");
        try {
            var dir = base.resolve("web").resolve("tls");
            var first = WebTls.loadOrCreate(dir);
            var second = WebTls.loadOrCreate(dir);

            assertEquals(first.fingerprint(), second.fingerprint());
            assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(dir)));
            assertEquals("rw-------",
                    PosixFilePermissions.toString(Files.getPosixFilePermissions(dir.resolve(WebTls.KEY_FILE))));
            assertTrue(first.names().contains("localhost"));
        } finally {
            TestBases.delete(base);
        }
    }

    @Test
    @DisplayName("encodes DER lengths and object identifiers")
    void shouldEncodeDer() {
        assertEquals("0603551d11", HexFormat.of().formatHex(Der.oid("2.5.29.17")));
        assertEquals("06072a8648ce3d0201", HexFormat.of().formatHex(Der.oid("1.2.840.10045.2.1")));
        var longValue = Der.tlv(Der.TAG_OCTET_STRING, new byte[300]);
        assertEquals("0482012c", HexFormat.of().formatHex(longValue, 0, 4));
        assertEquals("020100", HexFormat.of().formatHex(Der.integer(BigInteger.ZERO)));
    }

    @Test
    @DisplayName("finds a host name and interface addresses")
    void shouldInspectHost() {
        assertFalse(WebTls.hostName().isBlank());
        assertTrue(WebTls.interfaceAddresses().stream().noneMatch(InetAddress::isLinkLocalAddress));
    }

    @Test
    @DisplayName("every generated key pair yields strict DER, a matching key and a certificate that verifies")
    void shouldGenerateValidMaterialForManyKeyPairs() throws Exception {
        var random = new SecureRandom();
        var addresses = List.of(InetAddress.getByName("192.168.1.20"));
        for (int i = 0; i < GENERATED_PAIRS; i++) {
            var tls = WebTls.generate("studio", addresses, random, Instant.now());

            var certificateDer = der(tls.certificatePem());
            assertEquals(certificateDer.length, strictLength(certificateDer, 0), "certificate " + i);
            var keyDer = der(tls.privateKeyPem());
            assertEquals(keyDer.length, strictLength(keyDer, 0), "key " + i);
            var certificate = tls.certificate();
            certificate.verify(certificate.getPublicKey());
            certificate.checkValidity();
            assertTrue(certificate.getSerialNumber().signum() > 0, "serial " + i);
            var key = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(keyDer));
            var signer = Signature.getInstance("SHA256withECDSA");
            signer.initSign(key, random);
            signer.update(certificateDer);
            var verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(certificate.getPublicKey());
            verifier.update(certificateDer);
            assertTrue(verifier.verify(signer.sign()), "key and certificate " + i + " do not match");
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
