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

import java.io.ByteArrayInputStream;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.zip.CRC32;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import lombok.experimental.UtilityClass;

/**
 * The accept step of the harness icon store (Store Schemas § Harness Icon Store; Harness Profiles § Harness Icons): it
 * takes a candidate's bytes and returns what may be stored, or refuses with a reason.
 * <p>
 * The format is decided by the bytes and never by a name: the PNG signature selects the PNG path, a document that parses
 * as XML with an {@code svg} root in the SVG namespace selects the SVG path, and everything else is refused. The bound of
 * {@value #MAX_BYTES} bytes is applied before any parsing.
 * <p>
 * The sanitiser is an allow-list, not a deny-list. An SVG is re-written from its parsed tree: only the elements and
 * attributes named here are kept, the rest of the tree is dropped, and a construct that cannot be made safe is refused —
 * a DOCTYPE (and with it every entity declaration), an element outside the list, an {@code on*} event attribute, a
 * {@code href} that is not a fragment of the same document, and a {@code style} that could fetch or import anything. A PNG
 * is kept byte for byte once its chunk structure is closed by its CRCs and its chunk types are all in the allow-list; a
 * text chunk, which can carry arbitrary text, is refused.
 * <p>
 * Nothing of the candidate is logged or reported beyond its length and the refusal's reason.
 *
 * @since 0.1
 */
@UtilityClass
public class HarnessIconSanitiser {

    /** The largest candidate the store accepts: {@code 256} KiB. */
    public static final int MAX_BYTES = 256 * 1024;

    private static final String SVG_NAMESPACE = "http://www.w3.org/2000/svg";

    private static final String XML_NAMESPACE = "http://www.w3.org/XML/1998/namespace";

    private static final String XML_DECLARATION = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";

    /** The elements an icon may keep; everything else is refused. */
    private static final Set<String> ELEMENTS = Set.of("svg", "g", "defs", "title", "desc", "path", "rect", "circle",
            "ellipse", "line", "polyline", "polygon", "text", "tspan");

    /** The elements whose character content is kept. */
    private static final Set<String> TEXT_ELEMENTS = Set.of("title", "desc", "text", "tspan");

    /** The attributes an icon may keep, beyond the root's namespace declaration; everything else is dropped. */
    private static final Set<String> ATTRIBUTES = Set.of("id", "d", "x", "y", "x1", "y1", "x2", "y2", "cx", "cy", "r",
            "rx", "ry", "width", "height", "points", "viewBox", "preserveAspectRatio", "transform", "fill",
            "fill-opacity", "fill-rule", "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin", "stroke-opacity",
            "stroke-dasharray", "stroke-dashoffset", "stroke-miterlimit", "opacity", "font-family", "font-size",
            "font-weight", "font-style", "text-anchor", "dominant-baseline", "letter-spacing", "space");

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    /** The chunks a PNG icon may carry; everything else is refused. */
    private static final Set<String> PNG_CHUNKS = Set.of("IHDR", "PLTE", "IDAT", "IEND", "tRNS", "gAMA", "cHRM", "sRGB",
            "pHYs", "bKGD", "sBIT");

    /** The chunks that carry text, which can name anything. */
    private static final Set<String> PNG_TEXT_CHUNKS = Set.of("tEXt", "zTXt", "iTXt");

    private static final int PNG_HEADER = 8;

    private static final int CHUNK_OVERHEAD = 12;

    /**
     * @param candidate the candidate icon's bytes
     * @return the icon as it may be stored
     * @throws IconRefusal          if the candidate is too large, is neither an SVG nor a PNG, or carries a construct the
     *                              allow-list refuses
     * @throws NullPointerException if the candidate is {@code null}
     */
    public static SanitisedIcon sanitise(byte[] candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (candidate.length > MAX_BYTES) {
            throw new IconRefusal(IconRefusal.TOO_LARGE, candidate.length + " bytes, the bound is " + MAX_BYTES);
        }
        if (candidate.length == 0) {
            throw new IconRefusal(IconRefusal.FORMAT_UNSUPPORTED, "the candidate is empty");
        }
        if (startsWithPngSignature(candidate)) {
            return SanitisedIcon.of(IconFormat.PNG, sanitisePng(candidate));
        }
        if (!startsWithMarkup(candidate)) {
            throw new IconRefusal(IconRefusal.FORMAT_UNSUPPORTED, "neither the PNG signature nor an XML document");
        }
        return SanitisedIcon.of(IconFormat.SVG, sanitiseSvg(candidate).getBytes(UTF_8));
    }

    private static boolean startsWithPngSignature(byte[] candidate) {
        if (candidate.length < PNG_SIGNATURE.length) {
            return false;
        }
        for (var index = 0; index < PNG_SIGNATURE.length; index++) {
            if (candidate[index] != PNG_SIGNATURE[index]) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWithMarkup(byte[] candidate) {
        var index = 0;
        if (candidate.length >= 3 && candidate[0] == (byte) 0xEF && candidate[1] == (byte) 0xBB
                && candidate[2] == (byte) 0xBF) {
            index = 3;
        }
        while (index < candidate.length && Character.isWhitespace(candidate[index])) {
            index++;
        }
        return index < candidate.length && candidate[index] == '<';
    }

    /**
     * Re-writes an SVG candidate against the allow-list.
     *
     * @param candidate the candidate's bytes
     * @return the sanitised document, as text
     * @throws IconRefusal if the candidate declares a DOCTYPE, does not parse, or carries a refused construct
     */
    private static String sanitiseSvg(byte[] candidate) {
        requireNoDoctype(candidate);
        var factory = xmlInputFactory();
        var out = new StringBuilder(candidate.length);
        out.append(XML_DECLARATION);
        var open = new ArrayDeque<String>();
        var rootSeen = false;
        try {
            var reader = factory.createXMLStreamReader(new ByteArrayInputStream(candidate));
            while (reader.hasNext()) {
                switch (reader.next()) {
                    case XMLStreamConstants.START_ELEMENT -> {
                        rootSeen |= open.isEmpty();
                        requireElement(reader, open.isEmpty());
                        writeStartElement(reader, out, open.isEmpty());
                        open.push(reader.getLocalName());
                    }
                    case XMLStreamConstants.END_ELEMENT -> {
                        out.append("</").append(reader.getLocalName()).append('>');
                        open.pop();
                    }
                    case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> {
                        if (!open.isEmpty() && TEXT_ELEMENTS.contains(open.peek())) {
                            writeEscaped(reader.getText(), out, false);
                        }
                    }
                    default -> {
                        // comments, processing instructions, the document's start and end and ignorable space carry nothing
                    }
                }
            }
        } catch (XMLStreamException e) {
            throw new IconRefusal(IconRefusal.SVG_MARKUP_INVALID, e.getMessage());
        }
        if (!rootSeen) {
            throw new IconRefusal(IconRefusal.FORMAT_UNSUPPORTED, "the document holds no element");
        }
        return out.toString();
    }

    private static XMLInputFactory xmlInputFactory() {
        var factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
        factory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
        return factory;
    }

    /**
     * Refuses a DOCTYPE by its bytes, before the parser sees the document: a DOCTYPE declares entities, and no icon needs
     * one. The check is on the ASCII spelling, which an XML document must use for a markup declaration.
     */
    private static void requireNoDoctype(byte[] candidate) {
        var text = new String(candidate, US_ASCII).toLowerCase(Locale.ROOT);
        if (text.contains("<!doctype")) {
            throw new IconRefusal(IconRefusal.SVG_DOCTYPE_REFUSED, "a DOCTYPE declaration");
        }
    }

    private static void requireElement(XMLStreamReader reader, boolean root) {
        var name = reader.getLocalName();
        var namespace = reader.getNamespaceURI();
        if (root) {
            if (!"svg".equals(name) || !SVG_NAMESPACE.equals(namespace)) {
                throw new IconRefusal(IconRefusal.FORMAT_UNSUPPORTED, "the root element is not an SVG element");
            }
            return;
        }
        if (!ELEMENTS.contains(name)) {
            throw new IconRefusal(IconRefusal.SVG_ELEMENT_REFUSED, "<" + name + ">");
        }
        if (namespace != null && !SVG_NAMESPACE.equals(namespace)) {
            throw new IconRefusal(IconRefusal.SVG_ELEMENT_REFUSED, "<" + name + "> in namespace " + namespace);
        }
    }

    private static void writeStartElement(XMLStreamReader reader, StringBuilder out, boolean root) {
        var name = reader.getLocalName();
        out.append('<').append(name);
        if (root) {
            out.append(" xmlns=\"").append(SVG_NAMESPACE).append('"');
        }
        for (var index = 0; index < reader.getAttributeCount(); index++) {
            var attribute = reader.getAttributeLocalName(index);
            var value = reader.getAttributeValue(index);
            if (keepAttribute(attribute, reader.getAttributeNamespace(index), value)) {
                out.append(' ').append(attribute).append("=\"");
                writeEscaped(value, out, true);
                out.append('"');
            }
        }
        out.append('>');
    }

    /**
     * Decides one attribute. The dangerous ones are refused — an event handler, a reference that leaves the document, a
     * style that could fetch or import — and an attribute outside the allow-list is dropped, because the output is
     * re-serialised from the allow-list and never copies the candidate's markup through.
     */
    private static boolean keepAttribute(String attribute, String namespace, String value) {
        if (attribute.toLowerCase(Locale.ROOT).startsWith("on")) {
            throw new IconRefusal(IconRefusal.SVG_ATTRIBUTE_REFUSED, "event attribute '" + attribute + "'");
        }
        if ("href".equals(attribute)) {
            if (!value.startsWith("#")) {
                throw new IconRefusal(IconRefusal.SVG_ATTRIBUTE_REFUSED, "'" + attribute + "' leaves the document");
            }
            return false;
        }
        if ("style".equals(attribute)) {
            if (value.contains("(") || value.contains("@") || value.contains("\\")) {
                throw new IconRefusal(IconRefusal.SVG_ATTRIBUTE_REFUSED, "'style' could fetch or import");
            }
            return true;
        }
        if ("xmlns".equals(attribute) || "xmlns".equals(namespace)) {
            return false;
        }
        if (XML_NAMESPACE.equals(namespace)) {
            return "space".equals(attribute) && ATTRIBUTES.contains(attribute);
        }
        return (namespace == null || namespace.isEmpty()) && ATTRIBUTES.contains(attribute);
    }

    private static void writeEscaped(String text, StringBuilder out, boolean attribute) {
        for (var index = 0; index < text.length(); index++) {
            var character = text.charAt(index);
            switch (character) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append(attribute ? "&quot;" : "\"");
                default -> out.append(character);
            }
        }
    }

    /**
     * Checks a PNG candidate's chunk structure and returns it unchanged.
     *
     * @param candidate the candidate's bytes, whose signature is already known
     * @return the same bytes
     * @throws IconRefusal if the structure is broken or a chunk is not in the allow-list
     */
    private static byte[] sanitisePng(byte[] candidate) {
        if (candidate.length < PNG_HEADER + 2 * CHUNK_OVERHEAD) {
            throw new IconRefusal(IconRefusal.PNG_INVALID, "shorter than its header and its end");
        }
        var position = PNG_HEADER;
        var first = true;
        var dataSeen = false;
        while (position < candidate.length) {
            if (position + CHUNK_OVERHEAD > candidate.length) {
                throw new IconRefusal(IconRefusal.PNG_INVALID, "a chunk header runs past the end");
            }
            var length = readInt(candidate, position);
            if (length < 0 || length > candidate.length - position - CHUNK_OVERHEAD) {
                throw new IconRefusal(IconRefusal.PNG_INVALID, "chunk length " + length);
            }
            var type = new String(candidate, position + 4, 4, US_ASCII);
            var dataStart = position + 8;
            var crcStart = dataStart + length;
            var crc = new CRC32();
            crc.update(candidate, position + 4, 4 + length);
            if ((int) crc.getValue() != readInt(candidate, crcStart)) {
                throw new IconRefusal(IconRefusal.PNG_INVALID, "the CRC of chunk '" + type + "'");
            }
            if (first && (!"IHDR".equals(type) || length != 13)) {
                throw new IconRefusal(IconRefusal.PNG_INVALID, "the first chunk is not IHDR");
            }
            first = false;
            if (PNG_TEXT_CHUNKS.contains(type)) {
                throw new IconRefusal(IconRefusal.PNG_TEXT_CHUNK_REFUSED, "chunk '" + type + "'");
            }
            if (!PNG_CHUNKS.contains(type)) {
                throw new IconRefusal(IconRefusal.PNG_CHUNK_REFUSED, "chunk '" + type + "'");
            }
            dataSeen |= "IDAT".equals(type);
            position = crcStart + 4;
            if ("IEND".equals(type)) {
                return endOfPng(candidate, position, length, dataSeen);
            }
        }
        throw new IconRefusal(IconRefusal.PNG_INVALID, "no IEND chunk");
    }

    private static byte[] endOfPng(byte[] candidate, int position, int length, boolean dataSeen) {
        if (length != 0) {
            throw new IconRefusal(IconRefusal.PNG_INVALID, "IEND carries data");
        }
        if (position != candidate.length) {
            throw new IconRefusal(IconRefusal.PNG_INVALID, "content after IEND");
        }
        if (!dataSeen) {
            throw new IconRefusal(IconRefusal.PNG_INVALID, "no IDAT chunk");
        }
        return candidate.clone();
    }

    private static int readInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) << 24 | (bytes[offset + 1] & 0xFF) << 16 | (bytes[offset + 2] & 0xFF) << 8
                | bytes[offset + 3] & 0xFF;
    }
}
