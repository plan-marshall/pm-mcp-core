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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
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
 * a bounded buffer, has the digest of the whole content in memory. The content bytes are generated; the lengths at and
 * around the buffer size are the boundaries of the streaming, and the store file name is a fixture path.
 */
@EnableGeneratorController
@DisplayName("Store digest")
class StoreDigestTest {

    private static final int BUFFER = StoreDigest.Builder.BUFFER_SIZE;

    // Fixture path of the streamed store.
    private static final String STORE = "store.jsonl";

    @TempDir
    Path base;

    /**
     * @return generated content whose bytes differ along its length, so a misplaced chunk changes the digest: an odd
     *         step walks every byte value before it repeats
     */
    private static byte[] content(int length) {
        var step = 2 * Generators.integers(0, 127).next() + 1;
        var offset = Generators.integers(0, 255).next();
        var bytes = new byte[length];
        for (var i = 0; i < length; i++) {
            bytes[i] = (byte) (i * step + offset);
        }
        return bytes;
    }

    @ParameterizedTest(name = "{0} bytes")
    @ValueSource(ints = {0, 1, BUFFER - 1, BUFFER, BUFFER + 1, 3 * BUFFER + 17})
    @DisplayName("a file streamed in chunks has the digest of its content in memory (buffer boundaries)")
    void streamedFile(int length) throws Exception {
        var bytes = content(length);
        var file = Files.write(base.resolve(STORE), bytes);

        var streamed = StoreDigest.builder().update(file).build();

        assertAll(
                () -> assertEquals(StoreDigest.of(bytes), streamed, "the streamed digest should match memory"),
                () -> assertEquals(Optional.of(StoreDigest.of(bytes)), StoreDigest.ofFile(file),
                        "the file digest should match memory"));
    }

    @ParameterizedTest(name = "split at {0}")
    @ValueSource(ints = {0, 1, BUFFER, 2 * BUFFER + 5})
    @DisplayName("a prefix of a file followed by a block in memory has the digest of the concatenation (boundaries)")
    void prefixAndBlock(int split) throws Exception {
        var whole = content(2 * BUFFER + 5);
        var file = Files.write(base.resolve(STORE), whole);
        var block = Arrays.copyOfRange(whole, split, whole.length);

        var fromPrefix = StoreDigest.builder().update(file, split).update(block).build();

        assertEquals(StoreDigest.of(whole), fromPrefix, "prefix and block should digest as the whole");
    }

    @Test
    @DisplayName("a limit beyond the end of a file adds the whole file")
    void limitBeyondEnd() throws Exception {
        var bytes = content(BUFFER + Generators.integers(1, BUFFER).next());
        var file = Files.write(base.resolve(STORE), bytes);
        var limit = bytes.length + Generators.longs(1L, 10L * BUFFER).next();

        assertEquals(StoreDigest.of(bytes), StoreDigest.builder().update(file, limit).build(),
                "a limit past the end should add the whole file");
    }

    @Test
    @DisplayName("a file that does not exist has no digest")
    void missingFile() throws Exception {
        assertEquals(Optional.empty(), StoreDigest.ofFile(base.resolve(STORE)),
                "a missing file should have no digest");
    }

    @Test
    @DisplayName("chunks in memory have the digest of their concatenation, an empty chunk changing nothing")
    void chunksInMemory() {
        var length = Generators.integers(2, 1000).next();
        var split = Generators.integers(1, length - 1).next();
        var whole = content(length);

        var chunked = StoreDigest.builder().update(Arrays.copyOf(whole, split)).update(new byte[0])
                .update(Arrays.copyOfRange(whole, split, length)).build();

        assertEquals(StoreDigest.of(whole), chunked, "the chunks should digest as the whole content");
    }
}
