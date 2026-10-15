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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A Git LFS pointer file (PM-WORK-6): exactly the pointer of the specification v1 is read with its oid and size;
 * anything else is not a pointer.
 */
@DisplayName("LFS pointer")
class LfsPointerTest {

    private static final String OID = "4d7a214614ab2935c943f9e0ff69d22eadbb8f32b1258daaa5e2ca24d17e2393";
    private static final String VALID = LfsPointer.VERSION + "\noid sha256:" + OID + "\nsize 12345\n";

    private static Optional<LfsPointer> parse(String text) {
        return LfsPointer.parse(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("a valid pointer is read with its oid and size")
    void valid() {
        assertEquals(Optional.of(new LfsPointer(OID, 12345)), parse(VALID));
    }

    @Test
    @DisplayName("a pointer of an empty object is read")
    void emptyObject() {
        assertEquals(Optional.of(new LfsPointer(OID, 0)), parse(VALID.replace("size 12345", "size 0")));
    }

    @Test
    @DisplayName("a pointer is written as git-lfs writes it and read back equal")
    void roundTrip() {
        var pointer = new LfsPointer(OID, 12345);

        assertArrayEquals(VALID.getBytes(StandardCharsets.US_ASCII), pointer.toBytes());
        assertEquals(Optional.of(pointer), LfsPointer.parse(pointer.toBytes()));
    }

    @Test
    @ExtendWith(NonLatinDigits.class)
    @DisplayName("a pointer is written with its size in ASCII digits under a locale with non-Latin digits")
    void asciiSize() {
        var pointer = new LfsPointer(OID, 12345);

        assertArrayEquals(VALID.getBytes(StandardCharsets.US_ASCII), pointer.toBytes());
    }

    static Stream<Arguments> notPointers() {
        return Stream.of(
                Arguments.of("a missing version line", "oid sha256:" + OID + "\nsize 12345\n"),
                Arguments.of("another version", VALID.replace("spec/v1", "spec/v2")),
                Arguments.of("an oid that is too short", VALID.replace(OID, OID.substring(1))),
                Arguments.of("an oid in upper case", VALID.replace(OID, OID.toUpperCase())),
                Arguments.of("another hash algorithm", VALID.replace("oid sha256:", "oid sha512:")),
                Arguments.of("a negative size", VALID.replace("size 12345", "size -1")),
                Arguments.of("a size with a leading zero", VALID.replace("size 12345", "size 012")),
                Arguments.of("a size beyond a long", VALID.replace("size 12345", "size 9999999999999999999")),
                Arguments.of("an extra key", VALID + "x-extra value\n"),
                Arguments.of("keys out of order", LfsPointer.VERSION + "\nsize 12345\noid sha256:" + OID + "\n"),
                Arguments.of("a missing last line feed", VALID.substring(0, VALID.length() - 1)),
                Arguments.of("CRLF line ends", VALID.replace("\n", "\r\n")),
                Arguments.of("an ordinary file", "id,question\nq-1,Which?\n"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("notPointers")
    @DisplayName("anything else is not a pointer")
    void notPointer(String name, String text) {
        assertTrue(parse(text).isEmpty());
    }

    @Test
    @DisplayName("a file larger than a pointer is not a pointer")
    void tooLarge() {
        assertTrue(LfsPointer.parse(new byte[LfsPointer.MAX_POINTER_SIZE + 1]).isEmpty());
    }

    @Test
    @DisplayName("a pointer with a negative size cannot be made")
    void negativeSize() {
        assertThrows(IllegalArgumentException.class, () -> new LfsPointer(OID, -1));
    }
}
