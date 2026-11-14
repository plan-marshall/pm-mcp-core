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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.zip.CRC32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.store.LockKey;
import de.planmarshall.runtime.lock.FileLockManager;
import de.planmarshall.runtime.log.RuntimeLogMessages;
import de.planmarshall.runtime.start.PosixModes;

/**
 * The machine's harness icon store (Store Schemas § Harness Icon Store): an accepted candidate is written as
 * {@code icons/<harness_id>.<ext>} with mode {@code 0600} and its index entry, under the icon lock; an icon of the same
 * harness is replaced; a harness id that is not one, a symbolic link, a refused candidate and a file that no longer matches
 * its entry are all refused or not served.
 * <p>
 * The SVG and PNG byte shapes and the {@code index.json} member names are the wire formats and stay literal; the text of
 * the markup, the harness id and the candidate's bytes are generated.
 */
@EnableGeneratorController
@EnableTestLogger
@DisplayName("Harness icon store")
class HarnessIconStoreTest {

    private static final String SVG_NAMESPACE = "http://www.w3.org/2000/svg";

    private static final String HARNESS_ID = "h-" + Generators.letterStrings(3, 8).next().toLowerCase(Locale.ROOT);

    private static final String TITLE = Generators.letterStrings(4, 12).next();

    @TempDir
    Path base;

    private HarnessIconStore store;

    @BeforeEach
    void createStore() {
        store = HarnessIconStore.at(base, new FileLockManager(_ -> {
            // the test records no audit
        }));
    }

    @Test
    @DisplayName("an accepted SVG is written with mode 0600 and its index entry")
    void acceptsAnSvgAndWritesTheFileAndTheIndex() throws Exception {
        var candidate = svg().getBytes(UTF_8);

        var stored = store.accept(HARNESS_ID, candidate);
        var file = store.directory().resolve(HARNESS_ID + ".svg");
        var entry = index().get(HARNESS_ID);

        assertAll(
                () -> assertTrue(Files.isRegularFile(file), "The icon file is written"),
                () -> assertEquals(PosixModes.FILE, Files.getPosixFilePermissions(file),
                        "The icon file has mode 0600"),
                () -> assertEquals(IconFormat.SVG, stored.format(), "The stored icon is an SVG"),
                () -> assertEquals(entry.sha256(), stored.entry().sha256(), "The entry names the stored digest"),
                () -> assertEquals("image/svg+xml", entry.mediaType(), "The entry names the media type"),
                () -> assertEquals(stored.size(), entry.size(), "The entry names the stored size"),
                () -> assertNull(entry.source(), "A caller's candidate names no source"));
    }

    @Test
    @DisplayName("an accepted PNG is stored byte for byte")
    void acceptsAPng() {
        var candidate = png();

        var stored = store.accept(HARNESS_ID, candidate);

        assertAll(
                () -> assertEquals(IconFormat.PNG, stored.format(), "The stored icon is a PNG"),
                () -> assertArrayEquals(candidate, stored.bytes(), "A PNG is stored byte for byte"),
                () -> assertTrue(Files.isRegularFile(store.directory().resolve(HARNESS_ID + ".png")),
                        "The icon file is written"));
    }

    @Test
    @DisplayName("a second accepted icon replaces the icon of the same harness")
    void replacesAnIconOfTheSameHarness() throws Exception {
        var first = svg().getBytes(UTF_8);
        var second = svg("<circle cx=\"4\" cy=\"4\" r=\"2\"/>").getBytes(UTF_8);

        var firstStored = store.accept(HARNESS_ID, first);
        var secondStored = store.accept(HARNESS_ID, second);

        assertAll(
                () -> assertNotEquals(firstStored.entry().sha256(), secondStored.entry().sha256(), "The two candidates differ"),
                () -> assertEquals(secondStored.entry().sha256(), index().get(HARNESS_ID).sha256(),
                        "The index names the second candidate"),
                () -> assertArrayEquals(secondStored.bytes(), store.read(HARNESS_ID).orElseThrow().bytes(),
                        "The file holds the second candidate"));
    }

    @Test
    @DisplayName("a harness id that is not one is refused before it reaches a path")
    void refusesAHarnessIdThatIsNotOne() {
        var candidate = svg().getBytes(UTF_8);
        var separator = "a/b";
        var traversal = "../" + HARNESS_ID;

        var separatorRefusal = assertThrows(IconRefusal.class, () -> store.accept(separator, candidate),
                "A harness id with a separator is refused");
        var traversalRefusal = assertThrows(IconRefusal.class, () -> store.accept(traversal, candidate),
                "A harness id with a dot segment is refused");

        assertAll(
                () -> assertEquals(IconRefusal.HARNESS_ID_INVALID, separatorRefusal.code(),
                        "The separator refusal names the id"),
                () -> assertEquals(IconRefusal.HARNESS_ID_INVALID, traversalRefusal.code(),
                        "The traversal refusal names the id"),
                () -> assertFalse(Files.exists(base.resolve("icons").resolve("..").resolve(HARNESS_ID + ".svg")),
                        "Nothing was written outside the icon directory"));
    }

    @Test
    @DisplayName("a symbolic link at the target is refused")
    void refusesASymbolicLinkAtTheTarget() throws Exception {
        Files.createDirectories(store.directory());
        var outside = base.resolve("outside.svg");
        Files.writeString(outside, svg());
        Files.createSymbolicLink(store.directory().resolve(HARNESS_ID + ".svg"), outside);

        var refusal = assertThrows(IconRefusal.class, () -> store.accept(HARNESS_ID, svg().getBytes(UTF_8)),
                "A symbolic link at the target is refused");

        assertAll(
                () -> assertEquals(IconRefusal.PATH_REFUSED, refusal.code(), "The refusal names the path"),
                () -> assertEquals(svg(), Files.readString(outside), "The link's target is untouched"));
    }

    @Test
    @DisplayName("a file that differs from its index entry is not served")
    void doesNotServeAFileThatDiffersFromItsEntry() throws Exception {
        store.accept(HARNESS_ID, svg().getBytes(UTF_8));
        Files.writeString(store.directory().resolve(HARNESS_ID + ".svg"), svg("<rect width=\"1\" height=\"1\"/>"));

        var served = store.read(HARNESS_ID);

        assertTrue(served.isEmpty(), "A file the index does not stand for is not served");
    }

    @Test
    @DisplayName("an icon of an unknown harness is not served")
    void unknownHarnessIsNotServed() {
        var served = store.read(HARNESS_ID);

        assertTrue(served.isEmpty(), "A harness without an icon is not served");
    }

    @Test
    @DisplayName("an index entry whose file is gone is not served")
    void entryWithoutItsFileIsNotServed() throws Exception {
        store.accept(HARNESS_ID, svg().getBytes(UTF_8));
        Files.delete(store.directory().resolve(HARNESS_ID + ".svg"));

        var served = store.read(HARNESS_ID);

        assertTrue(served.isEmpty(), "An entry whose file is gone is not served");
    }

    @Test
    @DisplayName("a refused candidate is logged with its code and length")
    void refusedCandidateIsLogged() {
        var candidate = svg("<script>alert(1)</script>").getBytes(UTF_8);

        assertThrows(IconRefusal.class, () -> store.accept(HARNESS_ID, candidate),
                "A script element is refused by the store");

        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, HARNESS_ID);
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, IconRefusal.SVG_ELEMENT_REFUSED);
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                RuntimeLogMessages.PREFIX + "-147");
    }

    @Test
    @DisplayName("the lock of the icons is the icons lock")
    void lockIsTheIconsLock() {
        assertEquals(LockKey.icons(store.base()), store.lockKey(), "The store is guarded by locks/icons.lock");
    }

    @Test
    @DisplayName("the index is read back as the entries it was written with")
    void indexRoundTrips() {
        var stored = store.accept(HARNESS_ID, svg().getBytes(UTF_8));

        Optional<IconIndexEntry> entry = index().entrySet().stream().filter(e -> HARNESS_ID.equals(e.getKey()))
                .map(Map.Entry::getValue).findFirst();

        assertAll(
                () -> assertTrue(entry.isPresent(), "The index holds the harness"),
                () -> assertEquals(stored.entry(), entry.orElseThrow(), "The entry survives the round trip"));
    }

    private Map<String, IconIndexEntry> index() {
        try {
            return IconIndexCodec.read(store.indexFile(), Files.readAllBytes(store.indexFile()));
        } catch (IOException e) {
            throw new UncheckedIOException("The index cannot be read", e);
        }
    }

    private static String svg() {
        return svg("<path d=\"M0 0h16v16H0z\" fill=\"#123456\"/><text x=\"1\" y=\"12\">" + TITLE + "</text>");
    }

    private static String svg(String content) {
        return "<svg xmlns=\"" + SVG_NAMESPACE + "\" viewBox=\"0 0 16 16\">" + content + "</svg>";
    }

    /** @return a structurally valid PNG: its signature, an IHDR, one IDAT and an IEND, each with its CRC */
    private static byte[] png() {
        var out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        out.writeBytes(chunk("IHDR", new byte[]{0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0}));
        out.writeBytes(chunk("IDAT", Generators.strings(4, 12).next().getBytes(UTF_8)));
        out.writeBytes(chunk("IEND", new byte[0]));
        return out.toByteArray();
    }

    private static byte[] chunk(String type, byte[] data) {
        var chunk = new ByteArrayOutputStream();
        var length = data.length;
        chunk.writeBytes(new byte[]{(byte) (length >>> 24), (byte) (length >>> 16), (byte) (length >>> 8),
                (byte) length});
        var typeBytes = type.getBytes(UTF_8);
        chunk.writeBytes(typeBytes);
        chunk.writeBytes(data);
        var crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        var value = (int) crc.getValue();
        chunk.writeBytes(new byte[]{(byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value});
        return chunk.toByteArray();
    }
}
