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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The canonical JSON of a profile (RFC 8785): sorted members, one string spelling, one number spelling, and a digest
 * over the canonical bytes (PM-CLIENT-5).
 */
// Every datum in this class is a contract: the canonical JSON spellings and the digest format it asserts.
@DisplayName("Canonical JSON")
class CanonicalJsonTest {

    private static String canonical(String json) {
        return CanonicalJson.write(JsonDocument.parse(json.getBytes(UTF_8), "document"));
    }

    @Test
    @DisplayName("sorts the members of every object")
    void sortsMembers() {
        assertEquals("{\"a\":2,\"b\":1}", canonical("{\"b\":1,\"a\":2}"), "object members are sorted");
        assertEquals("{\"a\":{\"c\":3,\"d\":4},\"b\":1}", canonical("{\"b\":1,\"a\":{\"d\":4,\"c\":3}}"),
                "nested object members are sorted");
    }

    @Test
    @DisplayName("sorts by UTF-16 code units, as RFC 8785 requires")
    void sortsByUtf16() {
        var json = "{\"\\u20ac\":1,\"\\r\":2,\"1\":3,\"\\u0080\":4,\"\\ud83d\\ude00\":5}";

        assertEquals("{\"\\r\":2,\"1\":3,\"\u0080\":4,\"\u20ac\":1,\"\ud83d\ude00\":5}", canonical(json),
                "sorted by UTF-16 code units");
    }

    @Test
    @DisplayName("escapes the two mandatory characters and the control characters only")
    void escapesStrings() {
        assertAll(
                () -> assertEquals("\"a\\\"b\\\\c\"", canonical("\"a\\\"b\\\\c\""), "quote and backslash escaped"),
                () -> assertEquals("\"\\b\\t\\n\\f\\r\"", canonical("\"\\b\\t\\n\\f\\r\""), "short control escapes"),
                () -> assertEquals("\"\\u000b\"", canonical("\"\\u000b\""), "vertical tab uses an escape"),
                () -> assertEquals("\"\u20ac\u00e4\"", canonical("\"\u20ac\u00e4\""), "non-ASCII stays unescaped"),
                () -> assertEquals("\"a/b\"", canonical("\"a\\/b\""), "a forward slash is not escaped"));
    }

    @Test
    @DisplayName("writes an integer in its plain decimal spelling")
    void writesIntegers() {
        assertAll(
                () -> assertEquals("0", canonical("0"), "zero is written as plain zero"),
                () -> assertEquals("0", canonical("-0"), "negative zero collapses to zero"),
                () -> assertEquals("-12", canonical("-12"), "-12 keeps a plain decimal spelling"),
                () -> assertEquals("9007199254740991", canonical("9007199254740991"), "large integer stays exact"));
    }

    @Test
    @DisplayName("writes arrays, booleans and null unchanged")
    void writesLiterals() {
        assertEquals("[true,false,null,[1,2]]", canonical("[true,false,null,[1,2]]"),
                "arrays and literals unchanged");
    }

    @Test
    @DisplayName("the digest is sha256 over the canonical UTF-8 bytes")
    void digestIsOverCanonicalBytes() throws Exception {
        var document = new LinkedHashMap<String, JsonNode>();
        document.put("a", JsonNode.integer(1));
        var digest = CanonicalJson.digest(JsonNode.object(document));
        var expected = "sha256:" + HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest("{\"a\":1}".getBytes(UTF_8)));

        assertAll(
                () -> assertTrue(digest.matches("sha256:[0-9a-f]{64}"), "the digest carries the sha256 prefix"),
                () -> assertEquals(expected, digest, "digest is sha256 of the bytes"),
                () -> assertEquals("[1,\"x\"]", CanonicalJson.write(JsonNode.array(List.of(JsonNode.integer(1),
                        JsonNode.string("x")))), "array of number and string"));
    }
}
