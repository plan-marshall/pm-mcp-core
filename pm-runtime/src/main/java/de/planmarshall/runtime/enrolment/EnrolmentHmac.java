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

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.store.EnrolmentSidecar;
import de.planmarshall.core.store.StoreDigest;

/**
 * The HMAC of an enrolment file (PM-EXEC-2, PM-SEC-5): the lowercase-hex HMAC-SHA-256 over the exact canonical bytes
 * the runtime wrote, under the {@link EnrolmentKey enrolment integrity key}, and the sidecar record that carries it
 * with the size and the SHA-256 of those bytes.
 *
 * @since 0.1
 */
@UtilityClass
public final class EnrolmentHmac {

    /**
     * @param key     the enrolment integrity key
     * @param content the exact bytes of an enrolment file
     * @return the HMAC-SHA-256 over the bytes, 64 lowercase hexadecimal digits
     * @throws IllegalArgumentException if the key is not usable for HMAC-SHA-256
     */
    public static String hmac(SecretKey key, byte[] content) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(content, "content");
        try {
            var mac = Mac.getInstance(EnrolmentKey.ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("The JDK provides no " + EnrolmentKey.ALGORITHM, e);
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("The key is not usable for " + EnrolmentKey.ALGORITHM, e);
        }
    }

    /**
     * @param key     the enrolment integrity key
     * @param content the exact canonical bytes of an enrolment file
     * @return the sidecar record of the bytes: their size, their SHA-256 and their HMAC
     */
    public static EnrolmentSidecar sidecar(SecretKey key, byte[] content) {
        return new EnrolmentSidecar(StoreDigest.of(content), hmac(key, content));
    }

    /**
     * Compares the HMAC of the bytes with a recorded one in constant time.
     *
     * @param key      the enrolment integrity key
     * @param content  the exact bytes of an enrolment file
     * @param recorded the HMAC its sidecar records
     * @return whether the recorded HMAC is the HMAC of the bytes under the key
     */
    public static boolean verify(SecretKey key, byte[] content, String recorded) {
        Objects.requireNonNull(recorded, "recorded");
        return MessageDigest.isEqual(hmac(key, content).getBytes(StandardCharsets.US_ASCII),
                recorded.getBytes(StandardCharsets.US_ASCII));
    }
}
