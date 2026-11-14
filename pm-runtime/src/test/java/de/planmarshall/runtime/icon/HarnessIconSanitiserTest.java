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

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The accept step of the harness icon store (Store Schemas § Harness Icon Store; Harness Profiles § Harness Icons), with
 * the negative controls first: every construct the allow-list refuses is planted in a candidate that would otherwise be a
 * valid icon, and the test fails on the unsanitised input. Then the sanitiser itself, and a generated-markup property that
 * the output holds nothing outside the allow-list.
 * <p>
 * The SVG and PNG byte shapes are the wire formats and stay literal; the generated part is the text of the markup and the
 * attribute values.
 */
@EnableGeneratorController
@DisplayName("Harness icon sanitiser")
class HarnessIconSanitiserTest {

    private static final String SVG_NAMESPACE = "http://www.w3.org/2000/svg";

    private static final Pattern ELEMENT = Pattern.compile("<([A-Za-z][A-Za-z0-9]*)");

    private static final Pattern ATTRIBUTE = Pattern.compile("\\s([A-Za-z][A-Za-z0-9:-]*)=");

    private static final List<String> ALLOWED_ELEMENTS = List.of("svg", "g", "defs", "title", "desc", "path", "rect",
            "circle", "ellipse", "line", "polyline", "polygon", "text", "tspan");

    private static final List<String> ALLOWED_ATTRIBUTES = List.of("xmlns", "id", "d", "x", "y", "x1", "y1", "x2",
            "y2", "cx", "cy", "r", "rx", "ry", "width", "height", "points", "viewBox", "preserveAspectRatio",
            "transform", "fill", "fill-opacity", "fill-rule", "stroke", "stroke-width", "stroke-linecap",
            "stroke-linejoin", "stroke-opacity", "stroke-dasharray", "stroke-dashoffset", "stroke-miterlimit", "opacity",
            "font-family", "font-size", "font-weight", "font-style", "text-anchor", "dominant-baseline", "letter-spacing",
            "space");

    private static final String TITLE = Generators.letterStrings(4, 12).next();

    private static final String COLOUR = "#" + Generators.letterStrings(6, 6).next();

    @Test
    @DisplayName("a planted script element is refused")
    void scriptElementRefused() {
        var refusal = refusalOf(svg("<script>alert(1)</script>"));

        assertEquals(IconRefusal.SVG_ELEMENT_REFUSED, refusal.code(), "A script element is refused by the allow-list");
    }

    @Test
    @DisplayName("a planted foreignObject element is refused")
    void foreignObjectRefused() {
        var refusal = refusalOf(svg("<foreignObject><body xmlns=\"http://www.w3.org/1999/xhtml\"/></foreignObject>"));

        assertEquals(IconRefusal.SVG_ELEMENT_REFUSED, refusal.code(), "A foreign object is refused by the allow-list");
    }

    @Test
    @DisplayName("a planted use element is refused")
    void useElementRefused() {
        var refusal = refusalOf(svg("<use href=\"#icon\"/>"));

        assertEquals(IconRefusal.SVG_ELEMENT_REFUSED, refusal.code(), "A use element is refused by the allow-list");
    }

    @Test
    @DisplayName("a planted data: image is refused")
    void dataImageRefused() {
        var refusal = refusalOf(svg("<image href=\"data:image/png;base64,AAAA\"/>"));

        assertEquals(IconRefusal.SVG_ELEMENT_REFUSED, refusal.code(), "An embedded image is refused as an element");
    }

    @Test
    @DisplayName("a planted onload attribute is refused")
    void eventAttributeRefused() {
        var refusal = refusalOf("<svg xmlns=\"" + SVG_NAMESPACE + "\" onload=\"alert(1)\"><path d=\"M0 0\"/></svg>");

        assertEquals(IconRefusal.SVG_ATTRIBUTE_REFUSED, refusal.code(), "An event attribute is refused as such");
    }

    @Test
    @DisplayName("a planted external href is refused")
    void externalHrefRefused() {
        var refusal = refusalOf(svg("<g href=\"https://example.test/icon.svg\"><path d=\"M0 0\"/></g>"));

        assertEquals(IconRefusal.SVG_ATTRIBUTE_REFUSED, refusal.code(), "A reference that leaves the document");
    }

    @Test
    @DisplayName("a planted url() style is refused")
    void urlStyleRefused() {
        var refusal = refusalOf(svg("<g style=\"fill:url(#paint)\"><path d=\"M0 0\"/></g>"));

        assertEquals(IconRefusal.SVG_ATTRIBUTE_REFUSED, refusal.code(), "A style that could fetch or import");
    }

    @Test
    @DisplayName("a planted DOCTYPE with an entity is refused")
    void doctypeRefused() {
        var candidate = "<?xml version=\"1.0\"?><!DOCTYPE svg [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + "<svg xmlns=\"" + SVG_NAMESPACE + "\"><text>&xxe;</text></svg>";

        var refusal = refusalOf(candidate);

        assertEquals(IconRefusal.SVG_DOCTYPE_REFUSED, refusal.code(), "A DOCTYPE is refused before parsing");
    }

    @Test
    @DisplayName("a candidate one byte above the bound is refused")
    void candidateAboveTheBoundRefused() {
        var oversized = new byte[HarnessIconSanitiser.MAX_BYTES + 1];
        oversized[0] = '<';
        oversized[1] = 's';

        var refusal = refusalOf(oversized);

        assertEquals(IconRefusal.TOO_LARGE, refusal.code(), "A candidate above 256 KiB is refused");
    }

    @Test
    @DisplayName("a PNG with a text chunk is refused")
    void pngTextChunkRefused() {
        var candidate = png(chunk("tEXt", "Comment\0planted".getBytes(US_ASCII)));

        var refusal = refusalOf(candidate);

        assertEquals(IconRefusal.PNG_TEXT_CHUNK_REFUSED, refusal.code(), "A text chunk is refused as such");
    }

    @Test
    @DisplayName("a PNG with an unknown chunk is refused")
    void pngUnknownChunkRefused() {
        var candidate = png(chunk("eXIf", "planted".getBytes(US_ASCII)));

        var refusal = refusalOf(candidate);

        assertEquals(IconRefusal.PNG_CHUNK_REFUSED, refusal.code(), "A chunk outside the allow-list is refused");
    }

    @Test
    @DisplayName("a PNG whose CRC does not match is refused")
    void pngBrokenCrcRefused() {
        var candidate = png(chunk("tRNS", new byte[]{0}));
        candidate[candidate.length - 1] = (byte) (candidate[candidate.length - 1] + 1);

        var refusal = refusalOf(candidate);

        assertEquals(IconRefusal.PNG_INVALID, refusal.code(), "A chunk whose CRC does not match is refused");
    }

    @Test
    @DisplayName("a candidate that is neither an SVG nor a PNG is refused")
    void unknownFormatRefused() {
        var refusal = refusalOf(Generators.letterStrings(20, 40).next().getBytes(UTF_8));

        assertEquals(IconRefusal.FORMAT_UNSUPPORTED, refusal.code(), "Bytes of no known format are refused");
    }

    @Test
    @DisplayName("an XML document whose root is not an SVG is refused")
    void nonSvgRootRefused() {
        var refusal = refusalOf("<html><body/></html>");

        assertEquals(IconRefusal.FORMAT_UNSUPPORTED, refusal.code(), "A document that is not an SVG is refused");
    }

    @Test
    @DisplayName("an SVG keeps its allowed elements and drops the rest of the markup")
    void svgKeepsTheAllowList() {
        var candidate = svg("<!-- a comment --><?processing instruction?>"
                + "<g data-planted=\"x\"><path d=\"M0 0\" fill=\"" + COLOUR + "\"/></g>"
                + "<text font-size=\"12\">" + TITLE + "</text>");

        var sanitised = HarnessIconSanitiser.sanitise(candidate.getBytes(UTF_8));
        var document = new String(sanitised.bytes(), UTF_8);

        assertAll(
                () -> assertEquals(IconFormat.SVG, sanitised.format(), "The candidate is an SVG"),
                () -> assertFalse(document.contains("data-planted"), "An unknown attribute is dropped"),
                () -> assertFalse(document.contains("<!--"), "A comment is dropped"),
                () -> assertFalse(document.contains("<?processing"), "A processing instruction is dropped"),
                () -> assertTrue(document.contains(TITLE), "The text content of a text element is kept"),
                () -> assertTrue(document.contains("xmlns=\"" + SVG_NAMESPACE + "\""),
                        "The root carries the SVG namespace"),
                () -> assertTrue(document.startsWith("<?xml version=\"1.0\""), "The output is a document"));
    }

    @Test
    @DisplayName("the sanitised SVG of generated markup holds nothing outside the allow-list")
    void sanitisedMarkupHoldsOnlyTheAllowList() {
        var candidate = generatedSvg();

        var document = new String(HarnessIconSanitiser.sanitise(candidate.getBytes(UTF_8)).bytes(), UTF_8)
                .replaceFirst("^<\\?xml[^>]*\\?>", "");

        assertAll(
                () -> assertTrue(ALLOWED_ELEMENTS.containsAll(names(ELEMENT, document)),
                        "Every element of the output is allowed"),
                () -> assertTrue(ALLOWED_ATTRIBUTES.containsAll(names(ATTRIBUTE, document)),
                        "Every attribute of the output is allowed"),
                () -> assertFalse(document.contains("onload"), "No event attribute survives the accept step"));
    }

    @Test
    @DisplayName("a real 1x1 PNG passes the accept step byte for byte (a wire-format literal)")
    void pngPassesTheAcceptStep() {
        var candidate = png();

        var sanitised = HarnessIconSanitiser.sanitise(candidate);

        assertAll(
                () -> assertEquals(IconFormat.PNG, sanitised.format(), "The candidate is a PNG"),
                () -> assertEquals(candidate.length, sanitised.size(), "A PNG is stored byte for byte"),
                () -> assertTrue(sanitised.sha256().startsWith("sha256:"), "The digest is a sha256: digest"));
    }

    @Test
    @DisplayName("a style that fetches nothing is kept")
    void styleWithoutAFetchIsKept() {
        var sanitised = HarnessIconSanitiser.sanitise(
                svg("<g style=\"fill:red;stroke:blue\"><path d=\"M0 0\"/></g>").getBytes(UTF_8));

        assertTrue(new String(sanitised.bytes(), UTF_8).contains("style=\"fill:red;stroke:blue\""),
                "A plain style declaration survives the accept step");
    }

    @Test
    @DisplayName("an xml:space attribute is kept")
    void xmlSpaceIsKept() {
        var sanitised = HarnessIconSanitiser.sanitise(
                svg("<text xml:space=\"preserve\">" + TITLE + "</text>").getBytes(UTF_8));

        assertTrue(new String(sanitised.bytes(), UTF_8).contains("space=\"preserve\""),
                "The xml:space attribute survives as its local name");
    }

    @Test
    @DisplayName("a fragment href is dropped rather than refused")
    void fragmentHrefIsDropped() {
        var sanitised = HarnessIconSanitiser.sanitise(
                svg("<g href=\"#icon\"><path d=\"M0 0\"/></g>").getBytes(UTF_8));

        assertFalse(new String(sanitised.bytes(), UTF_8).contains("href"),
                "A fragment reference is dropped from the output");
    }

    @Test
    @DisplayName("cdata in a text element is kept as text")
    void cdataInATextElementIsKept() {
        var sanitised = HarnessIconSanitiser.sanitise(
                svg("<text><![CDATA[" + TITLE + "\"]]></text>").getBytes(UTF_8));

        assertTrue(new String(sanitised.bytes(), UTF_8).contains(TITLE),
                "The character data of a text element survives");
    }

    @Test
    @DisplayName("an element of a foreign namespace is refused")
    void foreignNamespaceElementRefused() {
        var refusal = refusalOf(svg("<x:g xmlns:x=\"http://example.test\"><path d=\"M0 0\"/></x:g>"));

        assertEquals(IconRefusal.SVG_ELEMENT_REFUSED, refusal.code(), "A foreign namespace is refused as an element");
    }

    @Test
    @DisplayName("an SVG behind a byte-order mark is accepted")
    void bomPrefixedSvgIsAccepted() {
        var candidate = new ByteArrayOutputStream();
        candidate.writeBytes(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        candidate.writeBytes(svg("<path d=\"M0 0\"/>").getBytes(UTF_8));

        var sanitised = HarnessIconSanitiser.sanitise(candidate.toByteArray());

        assertEquals(IconFormat.SVG, sanitised.format(), "A byte-order mark does not hide the document");
    }

    @Test
    @DisplayName("a PNG shorter than its header and end is refused")
    void shortPngRefused() {
        var refusal = refusalOf(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});

        assertEquals(IconRefusal.PNG_INVALID, refusal.code(), "A PNG without chunks is refused");
    }

    @Test
    @DisplayName("a PNG whose first chunk is not the header is refused")
    void pngWithoutHeaderRefused() {
        var refusal = refusalOf(pngOf(chunk("IDAT", new byte[] {1}), chunk("IEND", new byte[0])));

        assertEquals(IconRefusal.PNG_INVALID, refusal.code(), "A PNG without IHDR first is refused");
    }

    @Test
    @DisplayName("a PNG without image data is refused")
    void pngWithoutDataRefused() {
        var refusal = refusalOf(pngOf(chunk("IHDR", header()), chunk("IEND", new byte[0])));

        assertEquals(IconRefusal.PNG_INVALID, refusal.code(), "A PNG without IDAT is refused");
    }

    @Test
    @DisplayName("a PNG without its end chunk is refused")
    void pngWithoutEndRefused() {
        var refusal = refusalOf(pngOf(chunk("IHDR", header()), chunk("IDAT", new byte[] {1})));

        assertEquals(IconRefusal.PNG_INVALID, refusal.code(), "A PNG without IEND is refused");
    }

    @Test
    @DisplayName("a PNG whose end chunk carries data is refused")
    void pngEndWithDataRefused() {
        var refusal = refusalOf(
                pngOf(chunk("IHDR", header()), chunk("IDAT", new byte[] {1}), chunk("IEND", new byte[] {1})));

        assertEquals(IconRefusal.PNG_INVALID, refusal.code(), "An IEND with data is refused");
    }

    @Test
    @DisplayName("a PNG with content after its end is refused")
    void pngWithTrailingContentRefused() {
        var candidate = new ByteArrayOutputStream();
        candidate.writeBytes(png());
        candidate.writeBytes(new byte[] {1});

        var refusal = refusalOf(candidate.toByteArray());

        assertEquals(IconRefusal.PNG_INVALID, refusal.code(), "Content after IEND is refused");
    }

    @Test
    @DisplayName("a PNG whose chunk runs past the end is refused")
    void pngWithATruncatedChunkRefused() {
        var complete = png();
        var truncated = new byte[complete.length - 4];
        System.arraycopy(complete, 0, truncated, 0, truncated.length);

        var refusal = refusalOf(truncated);

        assertEquals(IconRefusal.PNG_INVALID, refusal.code(), "A truncated chunk is refused");
    }

    private static IconRefusal refusalOf(String candidate) {
        return refusalOf(candidate.getBytes(UTF_8));
    }

    private static IconRefusal refusalOf(byte[] candidate) {
        return assertThrows(IconRefusal.class, () -> HarnessIconSanitiser.sanitise(candidate),
                "The candidate is refused by the accept step");
    }

    private static String svg(String content) {
        return "<svg xmlns=\"" + SVG_NAMESPACE + "\" viewBox=\"0 0 16 16\">" + content + "</svg>";
    }

    private static String generatedSvg() {
        var content = new StringBuilder();
        content.append("<title>").append(TITLE).append("</title>");
        content.append("<rect x=\"0\" y=\"0\" width=\"16\" height=\"16\" fill=\"").append(COLOUR).append("\"/>");
        content.append("<circle cx=\"8\" cy=\"8\" r=\"4\" stroke=\"").append(COLOUR).append("\"/>");
        content.append("<text x=\"1\" y=\"12\" font-size=\"10\">").append(TITLE).append("</text>");
        return svg(content.toString());
    }

    private static List<String> names(Pattern pattern, String document) {
        var names = new ArrayList<String>();
        var matcher = pattern.matcher(document);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    /**
     * @return a structurally valid PNG: its signature, an {@code IHDR}, one {@code IDAT}, and an {@code IEND}, each with
     *         its CRC. The accept step checks the structure, never the pixel data.
     */
    private static byte[] png(byte[]... extraChunks) {
        var out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        out.writeBytes(chunk("IHDR", header()));
        out.writeBytes(chunk("IDAT", Generators.strings(4, 12).next().getBytes(US_ASCII)));
        for (var chunk : extraChunks) {
            out.writeBytes(chunk);
        }
        out.writeBytes(chunk("IEND", new byte[0]));
        return out.toByteArray();
    }

    /** @return the PNG signature followed by the chunks exactly as given */
    private static byte[] pngOf(byte[]... chunks) {
        var out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        for (var chunk : chunks) {
            out.writeBytes(chunk);
        }
        return out.toByteArray();
    }

    private static byte[] header() {
        var header = new ByteArrayOutputStream();
        header.writeBytes(new byte[]{0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0});
        return header.toByteArray();
    }

    private static byte[] chunk(String type, byte[] data) {
        var chunk = new ByteArrayOutputStream();
        var length = data.length;
        chunk.writeBytes(new byte[]{(byte) (length >>> 24), (byte) (length >>> 16), (byte) (length >>> 8),
                (byte) length});
        var typeBytes = type.getBytes(US_ASCII);
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
