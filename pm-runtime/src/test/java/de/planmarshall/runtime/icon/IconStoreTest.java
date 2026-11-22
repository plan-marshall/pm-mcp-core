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

import static de.planmarshall.runtime.testing.generators.RuntimeGenerators.shortWords;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.runtime.lock.FileLockManager;

/**
 * The harness icon store: the file and index one accept step writes, the mode of the file, the atomic replacement and
 * the refusals of a harness id that cannot name a file and a target that is a symbolic link.
 *
 * <p>
 * The harness ids are generated words, the instants and the source URLs are generated, and the icon bytes are the
 * minimal SVG document and PNG image the wire format names. The literals that stay are the contract: the file names
 * {@code icons/index.json}, {@code icons/<harness_id>.svg} and {@code icons/<harness_id>.png}, the media types
 * {@code image/svg+xml} and {@code image/png}, and the permissions {@code rw-------} the file must have.
 */
@EnableGeneratorController
@DisplayName("Icon store")
class IconStoreTest {

    private static final byte[] SVG = ("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 16 16\">"
            + "<path d=\"M0 0h16v16H0z\" fill=\"#336699\"/></svg>").getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path base;

    private IconStore store;
    private Instant fetchedAt;

    @BeforeEach
    void setUp() {
        fetchedAt = Instant.ofEpochSecond(Generators.integers(0, Integer.MAX_VALUE).next());
        store = new IconStore(base, new FileLockManager(event -> {
        }));
    }

    @Test
    @DisplayName("writes an SVG icon and its index entry under the icons directory")
    void writesSvg() throws Exception {
        var harnessId = harnessId();
        var sourceUrl = "https://example.invalid/" + Generators.letterStrings(8, 8).next() + ".svg";

        var stored = store.accept(harnessId, sourceUrl, fetchedAt, SVG);

        assertEquals(base.resolve("icons").resolve(harnessId + ".svg"), stored.file(), "the file is icons/<id>.svg");
        assertTrue(Files.exists(stored.file()), "the icon file is written");
        var index = store.index();
        var entry = index.find(harnessId).orElseThrow();
        assertEquals(sourceUrl, entry.sourceUrl(), "the index records the source URL");
        assertEquals("image/svg+xml", entry.mediaType(), "the index records the media type");
        assertEquals(Files.size(stored.file()), entry.size(), "the index records the stored file size");
        assertEquals(fetchedAt, entry.fetchedAt(), "the index records the fetch time");
    }

    @Test
    @DisplayName("writes the icon file with mode 0600")
    void writesOwnerOnly() throws Exception {
        var stored = store.accept(harnessId(), "https://example.invalid/a.svg", fetchedAt, SVG);

        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(stored.file())),
                "the icon file is readable by its owner alone");
    }

    @Test
    @DisplayName("writes a PNG icon with its extension and media type")
    void writesPng() {
        var harnessId = harnessId();

        var stored = store.accept(harnessId, "https://example.invalid/a.png", fetchedAt, PngFixtures.png());

        assertEquals(base.resolve("icons").resolve(harnessId + ".png"), stored.file(), "the file is icons/<id>.png");
        assertEquals("image/png", store.index().find(harnessId).orElseThrow().mediaType(),
                "the index records the PNG media type");
    }

    @Test
    @DisplayName("replaces an existing icon of the same harness")
    void replacesExisting() throws Exception {
        var harnessId = harnessId();
        store.accept(harnessId, "https://example.invalid/a.svg", fetchedAt, SVG);
        var other = SVG.clone();
        var second = store.accept(harnessId, "https://example.invalid/b.svg", fetchedAt.plusSeconds(1), other);

        assertEquals("https://example.invalid/b.svg", second.entry().sourceUrl(),
                "the index entry is replaced with the new source");
        assertEquals(List.of(harnessId), store.index().entries().keySet().stream().toList(),
                "the index still holds exactly one entry");
        try (var files = Files.list(base.resolve("icons"))) {
            assertEquals(0, files.filter(path -> path.getFileName().toString().endsWith(".staged")).count(),
                    "no staged file is left behind");
        }
    }

    @Test
    @DisplayName("refuses a harness id that could leave the icons directory")
    void refusesTravellingHarnessId() {
        var candidate = "../outside";

        var refusal = assertThrows(IconRefusal.class,
                () -> store.accept(candidate, "https://example.invalid/a.svg", fetchedAt, SVG),
                "a harness id outside the grammar is refused");

        assertEquals(IconRefusal.HARNESS_ID_INVALID, refusal.code(), "the refusal names the harness id");
        assertFalse(Files.exists(base.resolve("outside.svg")), "nothing is written outside the directory");
    }

    @Test
    @DisplayName("refuses a target that is a symbolic link")
    void refusesSymlinkTarget() throws Exception {
        var harnessId = harnessId();
        var outside = base.resolve("outside.svg");
        Files.writeString(outside, "untouched", StandardCharsets.UTF_8);
        Files.createDirectories(base.resolve("icons"));
        Files.createSymbolicLink(base.resolve("icons").resolve(harnessId + ".svg"), outside);

        var refusal = assertThrows(IconRefusal.class,
                () -> store.accept(harnessId, "https://example.invalid/a.svg", fetchedAt, SVG),
                "a symbolic link as the target is refused");

        assertEquals(IconRefusal.TARGET_IS_LINK, refusal.code(), "the refusal names the link");
        assertEquals("untouched", Files.readString(outside), "the link target is never written through");
    }

    @Test
    @DisplayName("reads back the index it wrote")
    void readsBackIndex() {
        var harnessId = harnessId();
        var stored = store.accept(harnessId, "https://example.invalid/a.svg", fetchedAt, SVG);

        var fresh = new IconStore(base, new FileLockManager(event -> {
        }));

        assertEquals(stored.entry(), fresh.find(harnessId).orElseThrow().entry(),
                "a fresh store reads the entry back unchanged");
    }

    @Test
    @DisplayName("does not serve a file that differs from its index entry")
    void doesNotServeAChangedFile() throws Exception {
        var harnessId = harnessId();
        var stored = store.accept(harnessId, "https://example.invalid/a.svg", fetchedAt, SVG);
        var storedBytes = Files.readAllBytes(stored.file());
        storedBytes[storedBytes.length - 5] = (byte) (storedBytes[storedBytes.length - 5] ^ 0x01);
        Files.write(stored.file(), storedBytes);

        assertTrue(store.find(harnessId).isEmpty(), "a file whose digest differs is not served");
    }

    @Test
    @DisplayName("replaces an icon of the other media type without leaving the old file")
    void replacesAcrossMediaTypes() {
        var harnessId = harnessId();
        var svg = store.accept(harnessId, "https://example.invalid/a.svg", fetchedAt, SVG);

        var png = store.accept(harnessId, "https://example.invalid/a.png", fetchedAt, PngFixtures.png());

        assertTrue(Files.notExists(svg.file()), "the replaced SVG file is removed");
        assertFalse(Files.notExists(png.file()), "the PNG file is written");
    }

    @Test
    @DisplayName("does not serve a file larger than the bound or of another size")
    void doesNotServeAFileOutsideTheBound() throws Exception {
        var harnessId = harnessId();
        var stored = store.accept(harnessId, "https://example.invalid/a.svg", fetchedAt, SVG);
        Files.write(stored.file(), new byte[IconSanitiser.MAX_BYTES + 1]);
        var tooLarge = store.find(harnessId).isEmpty();
        Files.write(stored.file(), new byte[3]);
        var tooShort = store.find(harnessId).isEmpty();

        assertAll(
                () -> assertTrue(tooLarge, "a file above the bound is not served"),
                () -> assertTrue(tooShort, "a file of another size is not served"));
    }

    @Test
    @DisplayName("refuses a source URL without a host or with whitespace")
    void refusesAMalformedSource() {
        var harnessId = harnessId();
        var withoutHost = assertThrows(IconRefusal.class,
                () -> store.accept(harnessId, "https:///a.svg", fetchedAt, SVG),
                "a source URL without a host is refused");
        var withWhitespace = assertThrows(IconRefusal.class,
                () -> store.accept(harnessId, "https://example.invalid/a b.svg", fetchedAt, SVG),
                "a source URL with whitespace is refused");

        assertAll(
                () -> assertEquals(IconRefusal.SOURCE_URL_INVALID, withoutHost.code(), "the host is required"),
                () -> assertEquals(IconRefusal.SOURCE_URL_INVALID, withWhitespace.code(),
                        "whitespace is refused"));
    }

    @Test
    @DisplayName("refuses a source that is not an https URL")
    void refusesNonHttpsSource() {
        var harnessId = harnessId();
        var refusal = assertThrows(IconRefusal.class,
                () -> store.accept(harnessId, "http://example.invalid/a.svg", fetchedAt, SVG),
                "a source that is not https is refused");

        assertEquals(IconRefusal.SOURCE_URL_INVALID, refusal.code(), "the refusal names the source URL");
    }

    @Test
    @DisplayName("refuses a source URL above the bound")
    void refusesASourceUrlAboveTheBound() {
        var harnessId = harnessId();
        var sourceUrl = "https://example.invalid/" + "a".repeat(2048) + ".svg";

        var refusal = assertThrows(IconRefusal.class,
                () -> store.accept(harnessId, sourceUrl, fetchedAt, SVG),
                "a source URL above 2048 characters is refused");

        assertEquals(IconRefusal.SOURCE_URL_INVALID, refusal.code(), "the refusal names the source URL");
    }

    @Test
    @DisplayName("refuses an index with a format version the store does not read")
    void refusesAForeignIndexVersion() {
        assertThrows(IllegalArgumentException.class, () -> new IconIndex(2, Map.of()),
                "an index of another format version is refused");
    }

    @Test
    @DisplayName("refuses an index that is a symbolic link")
    void refusesASymlinkedIndex() throws Exception {
        var outside = base.resolve("outside-index.json");
        Files.writeString(outside, "{\"format_version\":1,\"entries\":{}}", StandardCharsets.UTF_8);
        Files.createDirectories(base.resolve("icons"));
        Files.createSymbolicLink(base.resolve("icons").resolve("index.json"), outside);

        var refusal = assertThrows(IconRefusal.class, () -> store.index(), "a linked index is refused");

        assertEquals(IconRefusal.TARGET_IS_LINK, refusal.code(), "the refusal names the link");
    }

    @Test
    @DisplayName("refuses an index that does not read as the store's index")
    void refusesUnreadableIndex() throws Exception {
        Files.createDirectories(base.resolve("icons"));
        Files.writeString(base.resolve("icons").resolve("index.json"), "{\"format_version\":1}",
                StandardCharsets.UTF_8);

        var refusal = assertThrows(IconRefusal.class, () -> store.index(), "an unreadable index is refused");

        assertEquals(IconRefusal.INDEX_UNREADABLE, refusal.code(), "the refusal names the index");
    }

    @Test
    @DisplayName("reports its directory and its index file")
    void reportsItsPaths() {
        assertAll(
                () -> assertEquals(base.resolve("icons"), store.directory(), "the directory is icons/"),
                () -> assertEquals(base.resolve("icons").resolve("index.json"), store.indexFile(),
                        "the index is icons/index.json"));
    }

    @Test
    @DisplayName("refuses a store directory that is a symbolic link")
    void refusesASymlinkedDirectory() throws Exception {
        Files.createDirectories(base.resolve("elsewhere"));
        Files.createSymbolicLink(base.resolve("icons"), base.resolve("elsewhere"));

        var refusal = assertThrows(IconRefusal.class,
                () -> store.accept(harnessId(), "https://example.invalid/a.svg", fetchedAt, SVG),
                "a store directory that is a link is refused");

        assertEquals(IconRefusal.TARGET_IS_LINK, refusal.code(), "the refusal names the link");
    }

    @Test
    @DisplayName("refuses an index of a format version the store does not read")
    void refusesAnIndexOfAnotherVersion() throws Exception {
        Files.createDirectories(base.resolve("icons"));
        Files.writeString(base.resolve("icons").resolve("index.json"), "{\"format_version\":2,\"entries\":{}}",
                StandardCharsets.UTF_8);

        var refusal = assertThrows(IconRefusal.class, () -> store.index(), "an index of version 2 is refused");

        assertEquals(IconRefusal.INDEX_UNREADABLE, refusal.code(), "the refusal names the index");
    }

    @Test
    @DisplayName("refuses an index entry of another media type or size")
    void refusesABadEntry() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new IconIndexEntry("https://example.invalid/a", "0".repeat(64), 1, "image/gif",
                                Instant.EPOCH), "a media type the store does not keep is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new IconIndexEntry("https://example.invalid/a", "0".repeat(64), 0, "image/png",
                                Instant.EPOCH), "an empty icon is refused"));
    }

    private static String harnessId() {
        return shortWords().next();
    }
}
