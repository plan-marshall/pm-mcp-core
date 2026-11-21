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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The sanitising accept step of a candidate harness icon: every refusal the specification names, and the allow-list the
 * sanitised SVG keeps.
 *
 * <p>
 * The candidates are built byte by byte, because each is the wire format the sanitiser must judge: a minimal SVG
 * document, an attack string with the element or attribute it carries, and a minimal PNG with its chunks. The harness
 * id of the store test and the media types are generated or taken from the contract.
 */
@EnableGeneratorController
@DisplayName("Icon sanitiser")
class IconSanitiserTest {

    private static final String SVG_OPEN = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 16 16\">";
    private static final String SVG_CLOSE = "</svg>";
    private static final String SAFE_PATH = "<path d=\"M0 0h16v16H0z\" fill=\"#336699\"/>";

    @Test
    @DisplayName("keeps an allow-listed SVG and its allow-listed attributes")
    void keepsAllowListedSvg() {
        var sanitised = IconSanitiser.accept(svg(SAFE_PATH));

        assertEquals(IconMediaType.SVG, sanitised.mediaType(), "the bytes are read as an SVG");
        var text = new String(sanitised.content(), StandardCharsets.UTF_8);
        assertTrue(text.contains("<path"), "the allow-listed path element is kept");
        assertTrue(text.contains("fill=\"#336699\""), "the allow-listed fill attribute is kept");
        assertFalse(text.contains("script"), "no script element survives the filter");
    }

    @Test
    @DisplayName("drops an attribute outside the allow-list")
    void dropsUnknownAttribute() {
        var sanitised = IconSanitiser.accept(svg("<rect width=\"4\" height=\"4\" made-up=\"x\"/>"));

        var text = new String(sanitised.content(), StandardCharsets.UTF_8);
        assertTrue(text.contains("width=\"4\""), "the allow-listed width is kept");
        assertFalse(text.contains("made-up"), "the undeclared attribute is dropped");
    }

    @Test
    @DisplayName("refuses a candidate larger than the compiled bound")
    void refusesTooLarge() {
        var candidate = new byte[IconSanitiser.MAX_BYTES + 1];
        Arrays.fill(candidate, (byte) ' ');

        assertRefused(IconRefusal.TOO_LARGE, candidate, "a candidate above 256 KiB is refused");
    }

    @Test
    @DisplayName("refuses bytes that are neither an SVG nor a PNG")
    void refusesForeignBytes() {
        var candidate = Generators.letterStrings(64, 64).next().getBytes(StandardCharsets.UTF_8);

        assertRefused(IconRefusal.NOT_SVG_OR_PNG, candidate, "arbitrary text is refused as an icon");
    }

    @Nested
    @DisplayName("SVG attacks")
    class SvgAttacks {

        @Test
        @DisplayName("refuses a DOCTYPE, so no entity or external DTD is reached")
        void refusesDoctype() {
            var candidate = ("<?xml version=\"1.0\"?><!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" "
                    + "\"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">" + SVG_OPEN + SAFE_PATH + SVG_CLOSE)
                    .getBytes(StandardCharsets.UTF_8);

            assertRefused(IconRefusal.SVG_DOCTYPE, candidate, "a document with a DOCTYPE is refused");
        }

        @Test
        @DisplayName("refuses an undeclared entity reference without expanding it")
        void refusesEntity() {
            var candidate = ("<!DOCTYPE svg [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>" + SVG_OPEN
                    + "<desc>&xxe;</desc>" + SVG_CLOSE).getBytes(StandardCharsets.UTF_8);

            assertRefused(IconRefusal.SVG_DOCTYPE, candidate, "an entity declaration needs a DOCTYPE");
        }

        @Test
        @DisplayName("refuses a script element")
        void refusesScript() {
            assertRefused(IconRefusal.SVG_SCRIPT,
                    svg("<script>alert(1)</script>"), "an SVG script element is refused");
        }

        @Test
        @DisplayName("refuses a foreignObject element")
        void refusesForeignObject() {
            assertRefused(IconRefusal.SVG_FOREIGN_OBJECT,
                    svg("<foreignObject><body/></foreignObject>"),
                    "an SVG foreignObject element is refused");
        }

        @Test
        @DisplayName("refuses a use element")
        void refusesUse() {
            assertRefused(IconRefusal.SVG_USE,
                    svg("<use href=\"#x\"/>"), "an SVG use element is refused");
        }

        @Test
        @DisplayName("refuses an image with an external reference")
        void refusesExternalImage() {
            assertRefused(IconRefusal.SVG_EXTERNAL_REFERENCE,
                    svg("<image href=\"https://example.invalid/a.png\"/>"),
                    "an image with an external URL is refused");
        }

        @Test
        @DisplayName("refuses an image with a data reference")
        void refusesDataImage() {
            assertRefused(IconRefusal.SVG_EXTERNAL_REFERENCE,
                    svg("<image href=\"data:image/png;base64,AAAA\"/>"),
                    "an image with a data URL is refused");
        }

        @Test
        @DisplayName("refuses an event attribute")
        void refusesEventAttribute() {
            assertRefused(IconRefusal.SVG_EVENT_ATTRIBUTE,
                    svg("<rect width=\"4\" height=\"4\" onclick=\"alert(1)\"/>"),
                    "an on* attribute is refused whatever its value");
        }

        @Test
        @DisplayName("refuses an href outside the same document")
        void refusesExternalHref() {
            assertRefused(IconRefusal.SVG_EXTERNAL_REFERENCE,
                    svg("<path d=\"M0 0\" href=\"http://example.invalid/x\"/>"),
                    "an href that is not a document fragment is refused");
        }

        @Test
        @DisplayName("refuses a style that reaches a URL")
        void refusesStyleUrl() {
            assertRefused(IconRefusal.SVG_EXTERNAL_REFERENCE,
                    svg("<rect width=\"4\" height=\"4\" style=\"fill:url(https://example.invalid/x)\"/>"),
                    "a style with url( is refused");
        }

        @Test
        @DisplayName("refuses a style that imports another sheet")
        void refusesStyleImport() {
            assertRefused(IconRefusal.SVG_EXTERNAL_REFERENCE,
                    svg("<rect width=\"4\" height=\"4\" style=\"@import url(x.css)\"/>"),
                    "a style with @import is refused");
        }

        @Test
        @DisplayName("refuses an element outside the allow-list")
        void refusesUnknownElement() {
            assertRefused(IconRefusal.SVG_ELEMENT_NOT_ALLOWED,
                    svg("<animate attributeName=\"x\" to=\"9\"/>"),
                    "an element outside the allow-list is refused");
        }

        @Test
        @DisplayName("refuses a document that does not parse")
        void refusesMalformed() {
            var candidate = "<svg xmlns=\"http://www.w3.org/2000/svg\"><path".getBytes(StandardCharsets.UTF_8);

            assertRefused(IconRefusal.SVG_MALFORMED, candidate, "an unbalanced document is refused");
        }

        @Test
        @DisplayName("refuses a style that escapes its way to a URL")
        void refusesEscapedStyleUrl() {
            assertRefused(IconRefusal.SVG_EXTERNAL_REFERENCE,
                    svg("<rect width=\"4\" height=\"4\" style=\"fill:u\\72l(https://example.invalid/x)\"/>"),
                    "a CSS-escaped url( is refused");
        }

        @Test
        @DisplayName("keeps one href when both namespaces carry one")
        void keepsOneHref() {
            var sanitised = IconSanitiser.accept(svg(
                    "<path xmlns:xlink=\"http://www.w3.org/1999/xlink\" href=\"#a\" xlink:href=\"#b\"/>"));

            var text = new String(sanitised.content(), StandardCharsets.UTF_8);
            assertEquals(1, count(text, "href="), "exactly one href attribute is emitted");
        }

        @Test
        @DisplayName("refuses an element in a namespace other than the SVG one")
        void refusesForeignNamespace() {
            assertRefused(IconRefusal.SVG_ELEMENT_NOT_ALLOWED,
                    svg("<x:rect xmlns:x=\"urn:x\" width=\"1\" height=\"1\"/>"),
                    "an element in a foreign namespace is refused");
        }

        @Test
        @DisplayName("refuses a document nested deeper than the bound")
        void refusesTooDeep() {
            var nested = "<g>".repeat(SvgSanitiser.MAX_DEPTH + 1) + "</g>".repeat(SvgSanitiser.MAX_DEPTH + 1);

            assertRefused(IconRefusal.SVG_TOO_DEEP, svg(nested), "a document deeper than 64 is refused");
        }

        @Test
        @DisplayName("refuses a document with more elements than the bound")
        void refusesTooComplex() {
            var many = "<rect width=\"1\" height=\"1\"/>".repeat(SvgSanitiser.MAX_ELEMENTS);

            assertRefused(IconRefusal.SVG_TOO_COMPLEX, svg(many), "a document over 5000 elements is refused");
        }
    }

    @Nested
    @DisplayName("PNG")
    class Png {

        @Test
        @DisplayName("keeps a PNG and strips its text chunk")
        void stripsTextChunk() {
            var candidate = PngFixtures.png(PngFixtures.chunk("tEXt", "Comment\u0000hello".getBytes(
                    StandardCharsets.UTF_8)));

            var sanitised = IconSanitiser.accept(candidate);

            assertEquals(IconMediaType.PNG, sanitised.mediaType(), "the signature decides the PNG");
            assertFalse(contains(sanitised.content(), "tEXt".getBytes(StandardCharsets.US_ASCII)),
                    "the text chunk is stripped from the stored image");
            assertTrue(contains(sanitised.content(), "IHDR".getBytes(StandardCharsets.US_ASCII)),
                    "the header chunk stays in the stored image");
        }

        @Test
        @DisplayName("refuses a PNG with a broken chunk checksum")
        void refusesBrokenCrc() {
            var candidate = PngFixtures.png();
            candidate[candidate.length - 5] = (byte) (candidate[candidate.length - 5] ^ 0xFF);

            assertRefused(IconRefusal.PNG_MALFORMED, candidate, "a chunk whose CRC differs is refused");
        }

        @Test
        @DisplayName("refuses a PNG without its end chunk")
        void refusesMissingIend() {
            var candidate = PngFixtures.pngWithoutEnd();

            assertRefused(IconRefusal.PNG_MALFORMED, candidate, "a PNG without IEND is truncated");
        }

        @Test
        @DisplayName("refuses a PNG whose first chunk is not the header")
        void refusesFirstChunk() {
            var candidate = PngFixtures.signed(PngFixtures.chunk("IDAT", new byte[4]));

            assertRefused(IconRefusal.PNG_MALFORMED, candidate, "a PNG not led by IHDR is refused");
        }

        @Test
        @DisplayName("refuses a PNG that carries no image data")
        void refusesPngWithoutImageData() {
            assertRefused(IconRefusal.PNG_MALFORMED, PngFixtures.pngWithoutImageData(),
                    "a PNG without an IDAT chunk is refused");
        }

        @Test
        @DisplayName("strips the exchangeable image chunk as well as the text chunks")
        void stripsExifChunk() {
            var candidate = PngFixtures.png(PngFixtures.chunk("eXIf", "note".getBytes(StandardCharsets.UTF_8)));

            var sanitised = IconSanitiser.accept(candidate);

            assertFalse(contains(sanitised.content(), "eXIf".getBytes(StandardCharsets.US_ASCII)),
                    "the eXIf chunk is stripped from the stored image");
        }
    }

    private static void assertRefused(String code, byte[] candidate, String message) {
        var refusal = assertThrows(IconRefusal.class, () -> IconSanitiser.accept(candidate), message);
        assertEquals(code, refusal.code(), "the refusal names " + code);
    }

    private static byte[] svg(String body) {
        return (SVG_OPEN + body + SVG_CLOSE).getBytes(StandardCharsets.UTF_8);
    }

    private static int count(String haystack, String needle) {
        var count = 0;
        var index = haystack.indexOf(needle);
        while (index >= 0) {
            count++;
            index = haystack.indexOf(needle, index + needle.length());
        }
        return count;
    }

    private static boolean contains(byte[] haystack, byte[] needle) {        outer:
        for (var start = 0; start <= haystack.length - needle.length; start++) {
            for (var offset = 0; offset < needle.length; offset++) {
                if (haystack[start + offset] != needle[offset]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
