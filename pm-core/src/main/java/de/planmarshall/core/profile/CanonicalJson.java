/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.profile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * The canonical JSON of a client profile or verification record (RFC 8785, JCS), which its {@code profile_digest} is
 * computed over (PM-CLIENT-5).
 * <p>
 * The writer has one output for one value: an object's members sorted by their names in UTF-16 code-unit order, no
 * whitespace, strings escaped with the two mandatory escapes and the short forms of the control characters (the other
 * control characters as {@code \\u00xx}, lower case, never the escaped solidus), integers as their decimal spelling
 * and a decimal in the shortest plain spelling that round-trips. A profile declares only integers, so the digest of a
 * valid profile never depends on the decimal rule.
 *
 * @since 0.1
 */
public final class CanonicalJson {

    private static final HexFormat HEX = HexFormat.of();

    private CanonicalJson() {
    }

    /**
     * @param root the document
     * @return its canonical JSON, one UTF-8 string without a trailing line break
     */
    public static String write(JsonNode root) {
        var out = new StringBuilder();
        write(root, out);
        return out.toString();
    }

    /**
     * @param root the document
     * @return the {@code sha256:} digest of its canonical JSON, as a profile record records it
     */
    public static String digest(JsonNode root) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + HEX.formatHex(digest.digest(write(root)
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("The JDK provides no SHA-256", e);
        }
    }

    private static void write(JsonNode value, StringBuilder out) {
        switch (value.kind()) {
            case OBJECT -> writeObject(value, out);
            case ARRAY -> writeArray(value, out);
            case STRING -> writeString(value.text().orElseThrow(), out);
            case INTEGER -> out.append(value.integerValue().orElseThrow());
            case DECIMAL -> out.append(decimal(value.decimalValue().orElseThrow()));
            case BOOLEAN -> out.append(value.booleanValue().orElseThrow());
            case NULL -> out.append("null");
        }
    }

    private static void writeObject(JsonNode value, StringBuilder out) {
        var sorted = new TreeMap<String, JsonNode>(value.members());
        out.append('{');
        var first = true;
        for (Map.Entry<String, JsonNode> member : sorted.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            writeString(member.getKey(), out);
            out.append(':');
            write(member.getValue(), out);
        }
        out.append('}');
    }

    private static void writeArray(JsonNode value, StringBuilder out) {
        out.append('[');
        var first = true;
        for (var element : value.elements()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            write(element, out);
        }
        out.append(']');
    }

    private static void writeString(String text, StringBuilder out) {
        out.append('"');
        for (var index = 0; index < text.length(); index++) {
            var character = text.charAt(index);
            switch (character) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\t' -> out.append("\\t");
                case '\n' -> out.append("\\n");
                case '\f' -> out.append("\\f");
                case '\r' -> out.append("\\r");
                default -> {
                    if (character < 0x20) {
                        out.append("\\u00").append(HEX.toHexDigits((byte) character).toLowerCase());
                    } else {
                        out.append(character);
                    }
                }
            }
        }
        out.append('"');
    }

    /**
     * Writes a number as ECMAScript writes it where a plain spelling exists: no trailing zeros, no exponent, and
     * {@code 0} for a negative zero. The profile schema admits integers only; a decimal is written for the digest of
     * an untrusted document, so a decimal a valid profile never holds only has to be written deterministically.
     */
    private static String decimal(BigDecimal value) {
        if (value.signum() == 0) {
            return "0";
        }
        var stripped = value.stripTrailingZeros();
        if (stripped.scale() <= 0) {
            return stripped.toBigInteger().toString();
        }
        return stripped.toPlainString();
    }
}
