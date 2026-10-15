/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The SHA-256 of a source file or LFS object (PM-WORK-6): the known test vectors of FIPS 180-2, from bytes and from a
 * stream, and the comparison with an expected hash.
 */
@DisplayName("SHA-256")
class Sha256Test {

    private static final String EMPTY = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final String ABC = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    static Stream<Arguments> vectors() {
        return Stream.of(Arguments.of("", EMPTY), Arguments.of("abc", ABC));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @MethodSource("vectors")
    @DisplayName("the hash of bytes is the known vector")
    void bytes(String input, String expected) {
        assertEquals(expected, Sha256.hex(input.getBytes(StandardCharsets.US_ASCII)));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @MethodSource("vectors")
    @DisplayName("the hash of a stream is the known vector")
    void stream(String input, String expected) throws Exception {
        try (var in = new ByteArrayInputStream(input.getBytes(StandardCharsets.US_ASCII))) {
            assertEquals(expected, Sha256.hex(in));
        }
    }

    @Test
    @DisplayName("bytes match their own hash")
    void matches() {
        assertTrue(Sha256.matches("abc".getBytes(StandardCharsets.US_ASCII), ABC));
    }

    @Test
    @DisplayName("an upper-case spelling of the hash is not the lower-case format and does not match")
    void upperCase() {
        assertFalse(Sha256.matches("abc".getBytes(StandardCharsets.US_ASCII), ABC.toUpperCase()));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {EMPTY, "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ae", "abc", ""})
    @DisplayName("bytes do not match another hash or a value that is no hash")
    void mismatch(String expected) {
        assertFalse(Sha256.matches("abc".getBytes(StandardCharsets.US_ASCII), expected));
    }
}
