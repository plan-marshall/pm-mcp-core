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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.StoreRefusal;

/**
 * The index document of the harness icon store (Store Schemas § Harness Icon Store): a round trip that keeps the entries
 * byte for byte, the source of a fetched icon, and a refusal for every shape the schema does not allow.
 * <p>
 * The member names, the media types and the {@code sha256:} form are the specified document format and stay literal; the
 * harness id, the digest, the size and the instant are generated.
 */
@EnableGeneratorController
@DisplayName("Harness icon index codec")
class IconIndexCodecTest {

    private static final Path FILE = Path.of("icons", "index.json");

    private static final String HARNESS_ID = "h-" + Generators.letterStrings(3, 8).next().toLowerCase(Locale.ROOT);

    private static final String SHA256 = "sha256:" + "0123456789abcdef".repeat(4);

    private static final long SIZE = Generators.longs(1, 200_000).next();

    private static final Instant FETCHED_AT = Instant.ofEpochSecond(Generators.longs(1_600_000_000L, 1_900_000_000L).next());

    @Test
    @DisplayName("an index with a source survives a write and a read byte for byte")
    void roundTripWithSource() {
        var source = "https://example.test/" + Generators.letterStrings(3, 8).next() + "/icon.svg";
        var icons = Map.of(HARNESS_ID, new IconIndexEntry(source, SHA256, SIZE, "image/svg+xml", FETCHED_AT));

        var document = IconIndexCodec.write(icons);
        var read = IconIndexCodec.read(FILE, document);

        assertAll(
                () -> assertEquals(icons, read, "The entries survive the round trip"),
                () -> assertTrue(new String(document, UTF_8).contains("\"format_version\":1"),
                        "The document declares its format version"),
                () -> assertEquals(document.length, IconIndexCodec.write(read).length,
                        "A read and a write of the same index agree"));
    }

    @Test
    @DisplayName("an entry without a source writes no source member")
    void entryWithoutSourceWritesNoMember() {
        var icons = Map.of(HARNESS_ID, new IconIndexEntry(null, SHA256, SIZE, "image/png", FETCHED_AT));

        var written = IconIndexCodec.write(icons);
        var document = new String(written, UTF_8);

        assertAll(
                () -> assertFalse(document.contains("\"source\""), "A caller's candidate has no source member"),
                () -> assertEquals(icons, IconIndexCodec.read(FILE, written), "The entry survives the round trip"));
    }

    @Test
    @DisplayName("the entries are written in the order of their harness ids")
    void entriesAreWrittenInIdOrder() {
        var icons = new LinkedHashMap<String, IconIndexEntry>();
        icons.put("h-zulu", new IconIndexEntry(null, SHA256, SIZE, "image/png", FETCHED_AT));
        icons.put("h-alpha", new IconIndexEntry(null, SHA256, SIZE, "image/png", FETCHED_AT));

        var document = new String(IconIndexCodec.write(icons), UTF_8);

        assertTrue(document.indexOf("h-alpha") < document.indexOf("h-zulu"), "The harness ids are sorted");
    }

    @Test
    @DisplayName("a document with an undeclared member is refused")
    void undeclaredMemberRefused() {
        var document = "{\"format_version\":1,\"icons\":{},\"planted\":true}".getBytes(UTF_8);

        assertThrows(StoreRefusal.class, () -> IconIndexCodec.read(FILE, document),
                "An undeclared member is refused as unreadable");
    }

    @Test
    @DisplayName("a document of another format version is refused")
    void otherFormatVersionRefused() {
        var document = "{\"format_version\":2,\"icons\":{}}".getBytes(UTF_8);

        assertThrows(StoreRefusal.class, () -> IconIndexCodec.read(FILE, document),
                "Another format version is refused as unreadable");
    }

    @Test
    @DisplayName("a document that is not an object is refused")
    void nonObjectDocumentRefused() {
        var document = "[]".getBytes(UTF_8);

        assertThrows(StoreRefusal.class, () -> IconIndexCodec.read(FILE, document),
                "A document that is not an object is refused");
    }

    @Test
    @DisplayName("a document with content after it is refused")
    void contentAfterDocumentRefused() {
        var document = "{\"format_version\":1,\"icons\":{}}{}".getBytes(UTF_8);

        assertThrows(StoreRefusal.class, () -> IconIndexCodec.read(FILE, document),
                "Content after the document is refused");
    }

    @Test
    @DisplayName("an entry whose key is not a harness id is refused")
    void nonHarnessIdRefused() {
        var document = ("{\"format_version\":1,\"icons\":{\"../evil\":" + entry() + "}}").getBytes(UTF_8);

        assertThrows(StoreRefusal.class, () -> IconIndexCodec.read(FILE, document),
                "A key that is not a harness id is refused");
    }

    @Test
    @DisplayName("an entry with a media type outside the closed set is refused")
    void unknownMediaTypeRefused() {
        var document = ("{\"format_version\":1,\"icons\":{\"" + HARNESS_ID + "\":{\"sha256\":\"" + SHA256
                + "\",\"size\":" + SIZE + ",\"media_type\":\"image/gif\",\"fetched_at\":\"" + FETCHED_AT
                + "\"}}}").getBytes(UTF_8);

        assertThrows(StoreRefusal.class, () -> IconIndexCodec.read(FILE, document),
                "A media type outside the closed set is refused");
    }

    @Test
    @DisplayName("an entry with an undeclared member is refused")
    void undeclaredEntryMemberRefused() {
        var document = ("{\"format_version\":1,\"icons\":{\"" + HARNESS_ID + "\":{\"sha256\":\"" + SHA256
                + "\",\"size\":" + SIZE + ",\"media_type\":\"image/png\",\"fetched_at\":\"" + FETCHED_AT
                + "\",\"planted\":1}}}").getBytes(UTF_8);

        assertThrows(StoreRefusal.class, () -> IconIndexCodec.read(FILE, document),
                "An undeclared entry member is refused");
    }

    @Test
    @DisplayName("an entry that misses a required member is refused")
    void missingMemberRefused() {
        var document = ("{\"format_version\":1,\"icons\":{\"" + HARNESS_ID + "\":{\"sha256\":\"" + SHA256
                + "\",\"media_type\":\"image/png\",\"fetched_at\":\"" + FETCHED_AT + "\"}}}").getBytes(UTF_8);

        assertThrows(StoreRefusal.class, () -> IconIndexCodec.read(FILE, document),
                "A missing required member is refused");
    }

    private static String entry() {
        return "{\"sha256\":\"" + SHA256 + "\",\"size\":" + SIZE + ",\"media_type\":\"image/png\",\"fetched_at\":\""
                + FETCHED_AT + "\"}";
    }
}
