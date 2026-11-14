/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.icon;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * One entry of {@code icons/index.json} (Store Schemas § Harness Icon Store): the closed set of media types, the
 * {@code sha256:} digest, a positive size and an {@code https} source, each refused where it does not hold.
 * <p>
 * The media types and the {@code sha256:} form are the specified document format and stay literal; the digest, the size,
 * the instant and the source's host are generated.
 */
@EnableGeneratorController
@DisplayName("Harness icon index entry")
class IconIndexEntryTest {

    private static final String SHA256 = "sha256:" + "fedcba9876543210".repeat(4);

    private static final long SIZE = Generators.longs(1, 200_000).next();

    private static final Instant FETCHED_AT = Instant.ofEpochSecond(
            Generators.longs(1_600_000_000L, 1_900_000_000L).next());

    private static final String SOURCE =
            "https://" + Generators.letterStrings(3, 8).next().toLowerCase(Locale.ROOT) + ".example/icon.svg";

    @Test
    @DisplayName("an entry without a source is a caller's candidate")
    void entryWithoutSourceIsValid() {
        var entry = IconIndexEntry.builder()
                .sha256(SHA256)
                .size(SIZE)
                .mediaType("image/svg+xml")
                .fetchedAt(FETCHED_AT)
                .build();

        assertAll(
                () -> assertNull(entry.source(), "A caller's candidate names no source"),
                () -> assertEquals(SIZE, entry.size(), "The entry keeps its size"),
                () -> assertEquals(FETCHED_AT, entry.fetchedAt(), "The entry keeps its instant"));
    }

    @Test
    @DisplayName("an entry with an https source is a fetched icon")
    void entryWithHttpsSourceIsValid() {
        var entry = IconIndexEntry.builder()
                .source(SOURCE)
                .sha256(SHA256)
                .size(SIZE)
                .mediaType("image/png")
                .fetchedAt(FETCHED_AT)
                .build();

        assertEquals(SOURCE, entry.source(), "A fetched icon keeps its source");
    }

    @Test
    @DisplayName("a digest that is not a sha256: digest is refused")
    void badDigestRefused() {
        var digest = Generators.letterStrings(10, 20).next();

        assertThrows(IllegalArgumentException.class, () -> entry(digest, SIZE, "image/png", null),
                "A digest outside the sha256: form is refused");
    }

    @Test
    @DisplayName("a size that is not positive is refused")
    void nonPositiveSizeRefused() {
        assertThrows(IllegalArgumentException.class, () -> entry(SHA256, 0, "image/png", null),
                "An entry records a positive size");
    }

    @Test
    @DisplayName("a media type outside the closed set is refused")
    void unknownMediaTypeRefused() {
        assertThrows(IllegalArgumentException.class, () -> entry(SHA256, SIZE, "image/gif", null),
                "A media type outside the closed set is refused");
    }

    @Test
    @DisplayName("a source that is not https is refused")
    void nonHttpsSourceRefused() {
        var insecure = "http://example.test/icon.svg";

        assertThrows(IllegalArgumentException.class, () -> entry(SHA256, SIZE, "image/png", insecure),
                "A source that is not https is refused");
    }

    @Test
    @DisplayName("a source that is not a URL is refused")
    void malformedSourceRefused() {
        var malformed = "https://exa mple.test/icon.svg";

        assertThrows(IllegalArgumentException.class, () -> entry(SHA256, SIZE, "image/png", malformed),
                "A source that is not a URL is refused");
    }

    private static IconIndexEntry entry(String sha256, long size, String mediaType, String source) {
        return IconIndexEntry.builder()
                .source(source)
                .sha256(sha256)
                .size(size)
                .mediaType(mediaType)
                .fetchedAt(FETCHED_AT)
                .build();
    }
}
