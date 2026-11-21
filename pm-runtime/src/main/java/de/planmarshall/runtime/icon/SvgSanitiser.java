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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import lombok.experimental.UtilityClass;

/**
 * The SVG half of the icon accept step (Harness Profiles § Harness Icons; R09): an allow-list filter, not a deny-list.
 * <p>
 * The document is parsed with the JDK's StAX reader configured without a DTD and without external entities, and a
 * document that declares a DOCTYPE is refused before the reader sees it, so no entity expansion and no external DTD is
 * ever reached. The rendered result keeps only the elements and attributes of the allow-list: an element outside it is
 * refused, an attribute outside it is dropped, an {@code on*} attribute and a reference outside the document are
 * refused, and a {@code style} that reaches a URL or imports a sheet is refused. The rendered result is therefore the
 * allow-list of the input and nothing else, which is what the store writes and a client is served.
 * <p>
 * The closed lists grow only with a decision recorded in the package's ADR; a caller never adds to them.
 *
 * @since 0.1
 */
@UtilityClass
public class SvgSanitiser {

    /** The depth bound of a document the filter keeps. */
    static final int MAX_DEPTH = 64;

    /** The element bound of a document the filter keeps. */
    static final int MAX_ELEMENTS = 5000;

    private static final String SVG_NAMESPACE = "http://www.w3.org/2000/svg";
    private static final String XLINK_NAMESPACE = "http://www.w3.org/1999/xlink";
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private static final Set<String> REFUSED_ELEMENTS = Set.of("script", "foreignObject", "use");

    private static final Set<String> ALLOWED_ELEMENTS = Set.of("svg", "g", "title", "desc", "defs", "clipPath",
            "mask", "path", "rect", "circle", "ellipse", "line", "polyline", "polygon", "linearGradient",
            "radialGradient", "stop", "pattern", "image", "text", "tspan");

    private static final Set<String> ALLOWED_ATTRIBUTES = Set.of("id", "class", "style", "fill", "fill-opacity",
            "fill-rule", "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin", "stroke-dasharray",
            "stroke-dashoffset", "stroke-opacity", "stroke-miterlimit", "opacity", "color", "display", "visibility",
            "d", "points", "x", "y", "x1", "y1", "x2", "y2", "cx", "cy", "r", "rx", "ry", "width", "height", "viewBox",
            "preserveAspectRatio", "transform", "offset", "stop-color", "stop-opacity", "gradientUnits",
            "gradientTransform", "spreadMethod", "clipPathUnits", "maskUnits", "maskContentUnits", "patternUnits",
            "patternContentUnits", "patternTransform", "href", "role", "aria-label", "aria-hidden", "focusable",
            "pathLength", "font-family", "font-size", "font-weight", "text-anchor", "dominant-baseline",
            "letter-spacing", "word-spacing");

    /**
     * @param document an SVG document
     * @return the allow-listed rendering of the document, in the SVG namespace
     * @throws IconRefusal if the document declares a DOCTYPE, does not parse, carries a refused element or attribute,
     *                     or is deeper or larger than the bound
     */
    static byte[] sanitise(byte[] document) {
        if (declaresDoctype(document)) {
            throw new IconRefusal(IconRefusal.SVG_DOCTYPE,
                    "An SVG that declares a DOCTYPE is refused, so no entity is expanded");
        }
        try {
            var reader = factory().createXMLEventReader(new ByteArrayInputStream(document));
            try {
                return render(reader).getBytes(StandardCharsets.UTF_8);
            } finally {
                close(reader);
            }
        } catch (XMLStreamException malformed) {
            throw new IconRefusal(IconRefusal.SVG_MALFORMED, "The SVG does not parse as an XML document");
        }
    }

    /**
     * @param reader the reader to close; a reader whose open failed is not passed here
     */
    private static void close(XMLEventReader reader) {
        try {
            reader.close();
        } catch (XMLStreamException alreadyRefused) {
            // the render's own refusal names the fault; a failing close never replaces it
            return;
        }
    }

    /**
     * @param candidate arbitrary bytes
     * @return whether the bytes start as an XML document, ignoring a byte order mark and leading whitespace
     */
    static boolean looksLikeXml(byte[] candidate) {
        var position = 0;
        if (candidate.length >= BOM.length && candidate[0] == BOM[0] && candidate[1] == BOM[1]
                && candidate[2] == BOM[2]) {
            position = BOM.length;
        }
        while (position < candidate.length && candidate[position] <= ' ') {
            position++;
        }
        return position < candidate.length && candidate[position] == '<';
    }

    private static XMLInputFactory factory() {
        var factory = XMLInputFactory.newDefaultFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        return factory;
    }

    private static String render(XMLEventReader reader) throws XMLStreamException {
        var out = new StringBuilder();
        var depth = 0;
        var elements = 0;
        var rootSeen = false;
        while (reader.hasNext()) {
            var event = reader.nextEvent();
            if (event.getEventType() == XMLEvent.DTD) {
                throw new IconRefusal(IconRefusal.SVG_DOCTYPE, "An SVG that declares a DOCTYPE is refused");
            }
            if (event.isStartElement()) {
                var element = event.asStartElement();
                var local = element.getName().getLocalPart();
                if (!rootSeen) {
                    requireSvgRoot(local);
                    rootSeen = true;
                }
                requireSvgNamespace(element);
                requireAllowedElement(local);
                depth++;
                elements++;
                if (depth > MAX_DEPTH) {
                    throw new IconRefusal(IconRefusal.SVG_TOO_DEEP, "The SVG nests deeper than the bound");
                }
                if (elements > MAX_ELEMENTS) {
                    throw new IconRefusal(IconRefusal.SVG_TOO_COMPLEX, "The SVG carries more elements than the bound");
                }
                out.append('<').append(local);
                if (depth == 1) {
                    out.append(" xmlns=\"").append(SVG_NAMESPACE).append('"');
                }
                appendAttributes(out, element);
                out.append('>');
            } else if (event.isEndElement()) {
                depth--;
                out.append("</").append(event.asEndElement().getName().getLocalPart()).append('>');
            } else if (event.isCharacters()) {
                escapeText(out, event.asCharacters().getData());
            }
        }
        if (!rootSeen) {
            throw new IconRefusal(IconRefusal.NOT_SVG_OR_PNG, "The document carries no SVG root element");
        }
        return out.toString();
    }

    private static void requireSvgRoot(String local) {
        if (!"svg".equals(local)) {
            throw new IconRefusal(IconRefusal.NOT_SVG_OR_PNG, "The document's root element is not 'svg'");
        }
    }

    /**
     * @param element a start element
     * @throws IconRefusal if the element is in a namespace other than the SVG one, so it is not emitted as an SVG
     *                     element of a meaning it never had
     */
    private static void requireSvgNamespace(StartElement element) {
        var namespace = element.getName().getNamespaceURI();
        if (namespace != null && !namespace.isEmpty() && !SVG_NAMESPACE.equals(namespace)) {
            throw new IconRefusal(IconRefusal.SVG_ELEMENT_NOT_ALLOWED,
                    "An SVG element in a namespace other than the SVG one is refused");
        }
    }

    private static void requireAllowedElement(String local) {
        if (REFUSED_ELEMENTS.contains(local)) {
            throw new IconRefusal(refusalOf(local), "The SVG carries the refused element '%s'".formatted(local));
        }
        if (!ALLOWED_ELEMENTS.contains(local)) {
            throw new IconRefusal(IconRefusal.SVG_ELEMENT_NOT_ALLOWED,
                    "The SVG carries an element outside the allow-list");
        }
    }

    private static String refusalOf(String element) {
        return switch (element) {
            case "script" -> IconRefusal.SVG_SCRIPT;
            case "foreignObject" -> IconRefusal.SVG_FOREIGN_OBJECT;
            default -> IconRefusal.SVG_USE;
        };
    }

    private static void appendAttributes(StringBuilder out, StartElement element) {
        var emitted = new HashSet<String>();
        var attributes = element.getAttributes();
        while (attributes.hasNext()) {
            appendAttribute(out, attributes.next(), emitted);
        }
    }

    private static void appendAttribute(StringBuilder out, Attribute attribute, Set<String> emitted) {
        var name = attribute.getName();
        var local = name.getLocalPart();
        if (local.toLowerCase(Locale.ROOT).startsWith("on")) {
            throw new IconRefusal(IconRefusal.SVG_EVENT_ATTRIBUTE,
                    "An SVG attribute whose name begins with 'on' is refused");
        }
        var namespace = name.getNamespaceURI();
        if (namespace != null && !namespace.isEmpty() && !SVG_NAMESPACE.equals(namespace)) {
            if (!XLINK_NAMESPACE.equals(namespace) || !"href".equals(local)) {
                return;
            }
            local = "href";
        }
        var value = attribute.getValue();
        if ("href".equals(local)) {
            requireFragment(value);
        }
        if ("style".equals(local)) {
            requireSafeStyle(value);
        }
        requireLocalUrls(value);
        if (!ALLOWED_ATTRIBUTES.contains(local) || !emitted.add(local)) {
            return;
        }
        out.append(' ').append(local).append("=\"").append(escapeAttribute(value)).append('"');
    }

    private static void requireFragment(String value) {
        if (!value.startsWith("#")) {
            throw new IconRefusal(IconRefusal.SVG_EXTERNAL_REFERENCE,
                    "An SVG reference that is not a fragment of the same document is refused");
        }
    }

    private static void requireSafeStyle(String value) {
        var lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("url(") || lower.contains("@import") || lower.contains("image-set(")
                || lower.contains("image(") || value.indexOf('\\') >= 0) {
            throw new IconRefusal(IconRefusal.SVG_EXTERNAL_REFERENCE,
                    "An SVG style that reaches a URL, imports a sheet or escapes a name is refused");
        }
    }

    private static void requireLocalUrls(String value) {
        var lower = value.toLowerCase(Locale.ROOT);
        var index = lower.indexOf("url(");
        while (index >= 0) {
            var end = value.indexOf(')', index);
            if (end < 0) {
                throw new IconRefusal(IconRefusal.SVG_EXTERNAL_REFERENCE,
                        "An SVG reference that is not closed cannot be placed");
            }
            requireFragment(trimQuotes(value.substring(index + 4, end)));
            index = lower.indexOf("url(", end);
        }
    }

    private static String trimQuotes(String target) {
        var trimmed = target.trim();
        if (trimmed.length() >= 2 && (trimmed.charAt(0) == '\'' || trimmed.charAt(0) == '"')) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private static void escapeText(StringBuilder out, String text) {
        for (var index = 0; index < text.length(); index++) {
            var character = text.charAt(index);
            switch (character) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                default -> {
                    if (character >= 0x20 || character == '\n' || character == '\t') {
                        out.append(character);
                    }
                }
            }
        }
    }

    private static String escapeAttribute(String value) {
        var escaped = new StringBuilder(value.length());
        for (var index = 0; index < value.length(); index++) {
            var character = value.charAt(index);
            switch (character) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&apos;");
                default -> escaped.append(character);
            }
        }
        return escaped.toString();
    }

    private static boolean declaresDoctype(byte[] document) {
        var text = new String(document, StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
        return text.contains("<!doctype");
    }
}
