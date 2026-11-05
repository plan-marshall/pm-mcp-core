/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The incremental digest of a store (PM-IMPL-7): content added in chunks, from memory or streamed from a file through
 * a bounded buffer, has the digest of the whole content in memory.
 */
@DisplayName("Store digest")
class StoreDigestTest {

    private static final int BUFFER = StoreDigest.Builder.BUFFER_SIZE;

    @TempDir
    Path base;

    /** @return content whose bytes differ along its length, so a misplaced chunk changes the digest */
    private static byte[] content(int length) {
        var bytes = new byte[length];
        for (var i = 0; i < length; i++) {
            bytes[i] = (byte) (i * 31 + 7);
        }
        return bytes;
    }

    @ParameterizedTest(name = "{0} bytes")
    @ValueSource(ints = {0, 1, BUFFER - 1, BUFFER, BUFFER + 1, 3 * BUFFER + 17})
    @DisplayName("a file streamed in chunks has the digest of its content in memory")
    void streamedFile(int length) throws Exception {
        var bytes = content(length);
        var file = Files.write(base.resolve("store.jsonl"), bytes);

        var streamed = StoreDigest.builder().update(file).build();

        assertAll(
                () -> assertEquals(StoreDigest.of(bytes), streamed),
                () -> assertEquals(Optional.of(StoreDigest.of(bytes)), StoreDigest.ofFile(file)));
    }

    @ParameterizedTest(name = "split at {0}")
    @ValueSource(ints = {0, 1, BUFFER, 2 * BUFFER + 5})
    @DisplayName("a prefix of a file followed by a block in memory has the digest of the concatenation")
    void prefixAndBlock(int split) throws Exception {
        var whole = content(2 * BUFFER + 5);
        var file = Files.write(base.resolve("store.jsonl"), whole);
        var block = Arrays.copyOfRange(whole, split, whole.length);

        var fromPrefix = StoreDigest.builder().update(file, split).update(block).build();

        assertEquals(StoreDigest.of(whole), fromPrefix);
    }

    @Test
    @DisplayName("a limit beyond the end of a file adds the whole file")
    void limitBeyondEnd() throws Exception {
        var bytes = content(BUFFER + 3);
        var file = Files.write(base.resolve("store.jsonl"), bytes);

        assertEquals(StoreDigest.of(bytes), StoreDigest.builder().update(file, 10L * BUFFER).build());
    }

    @Test
    @DisplayName("a file that does not exist has no digest")
    void missingFile() throws Exception {
        assertEquals(Optional.empty(), StoreDigest.ofFile(base.resolve("missing.jsonl")));
    }

    @Test
    @DisplayName("chunks in memory have the digest of their concatenation, an empty chunk changing nothing")
    void chunksInMemory() {
        var whole = content(100);

        var chunked = StoreDigest.builder().update(Arrays.copyOf(whole, 40)).update(new byte[0])
                .update(Arrays.copyOfRange(whole, 40, 100)).build();

        assertEquals(StoreDigest.of(whole), chunked);
    }
}
