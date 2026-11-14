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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The value types of the harness icon store: the closed set of formats in both directions, the sanitised icon's digest and
 * its defensive copy, and the stored icon's format read from its entry.
 * <p>
 * The extensions, the media types and the {@code sha256:} form are the specified document format and stay literal; the
 * payload, the digest and the harness id are generated.
 */
@EnableGeneratorController
@DisplayName("Harness icon value types")
class IconValueTypesTest {

    private static final String HARNESS_ID = "h-" + Generators.letterStrings(3, 8).next().toLowerCase(Locale.ROOT);

    private static final String SHA256 = "sha256:" + "0011223344556677".repeat(4);

    private static final Instant FETCHED_AT = Instant.ofEpochSecond(
            Generators.longs(1_600_000_000L, 1_900_000_000L).next());

    @Test
    @DisplayName("the closed set of formats resolves in both directions")
    void formatsResolveInBothDirections() {
        var svgByExtension = IconFormat.ofExtension("svg");
        var pngByMediaType = IconFormat.ofMediaType("image/png");
        var unknownExtension = IconFormat.ofExtension("gif");
        var unknownMediaType = IconFormat.ofMediaType("image/gif");

        assertAll(
                () -> assertEquals(Optional.of(IconFormat.SVG), svgByExtension, "The extension svg is an SVG"),
                () -> assertEquals(Optional.of(IconFormat.PNG), pngByMediaType, "The media type png is a PNG"),
                () -> assertTrue(unknownExtension.isEmpty(), "An unknown extension resolves to nothing"),
                () -> assertTrue(unknownMediaType.isEmpty(), "An unknown media type resolves to nothing"),
                () -> assertEquals("image/svg+xml", IconFormat.SVG.mediaType(), "The SVG media type"),
                () -> assertEquals("png", IconFormat.PNG.extension(), "The PNG extension"));
    }

    @Test
    @DisplayName("a sanitised icon keeps a copy of its bytes and their digest")
    void sanitisedIconKeepsACopy() {
        var payload = Generators.letterStrings(8, 16).next().getBytes(UTF_8);
        var icon = SanitisedIcon.of(IconFormat.SVG, payload);
        var returned = icon.bytes();
        returned[0] = (byte) 'x';

        assertAll(
                () -> assertEquals(IconFormat.SVG, icon.format(), "The icon keeps its format"),
                () -> assertEquals(payload.length, icon.size(), "The icon keeps its length"),
                () -> assertTrue(icon.sha256().matches("sha256:[0-9a-f]{64}"), "The digest has the sha256: form"),
                () -> assertArrayEquals(payload, icon.bytes(), "A change of the returned bytes is not the icon"));
    }

    @Test
    @DisplayName("a sanitised icon without bytes or with a bad digest is refused")
    void sanitisedIconRefusals() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SanitisedIcon(IconFormat.PNG, new byte[0], SHA256),
                        "An icon without bytes is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SanitisedIcon(IconFormat.PNG, new byte[] {1}, "not-a-digest"),
                        "An icon with a bad digest is refused"));
    }

    @Test
    @DisplayName("a stored icon reads its format from its entry")
    void storedIconReadsItsFormat() {
        var payload = Generators.letterStrings(8, 16).next().getBytes(UTF_8);
        var entry = IconIndexEntry.builder()
                .sha256(SHA256)
                .size(payload.length)
                .mediaType("image/png")
                .fetchedAt(FETCHED_AT)
                .build();
        var icon = new HarnessIcon(HARNESS_ID, entry, payload);

        assertAll(
                () -> assertEquals(IconFormat.PNG, icon.format(), "The format is the entry's media type"),
                () -> assertEquals(HARNESS_ID, icon.harnessId(), "The icon names its harness"),
                () -> assertEquals(payload.length, icon.size(), "The icon keeps its length"),
                () -> assertArrayEquals(payload, icon.bytes(), "The icon hands out its bytes"));
    }
}
