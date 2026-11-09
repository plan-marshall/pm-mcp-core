/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.build;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import lombok.experimental.UtilityClass;

/**
 * Opens the hardened StAX reader the JaCoCo and Cobertura parsers share, and walks it for them.
 * <p>
 * The XML formats are read as untrusted input: a document may declare a DTD (JaCoCo writes a {@code DOCTYPE} with a
 * system identifier) and that declaration is tolerated without resolution, but an external entity is never resolved.
 * The factory keeps DTD support on so the declaration parses, turns external entities off, keeps entity references from
 * being replaced, and resolves every external reference to an empty stream, so a DTD or entity that tries to reach a file
 * or a URL reads nothing. The walk refuses an {@code ENTITY_REFERENCE} event, so a document that uses an entity defined
 * outside it is refused, never expanded. The decisions are recorded in {@code core-build/005}.
 *
 * @since 0.1
 */
@UtilityClass
class CoverageXmlReader {

    /**
     * The format-specific part of a walk: what to do at each element and what the walk produced.
     *
     * @since 0.1
     */
    interface Handler {

        /**
         * Handles a start element.
         *
         * @param reader the reader positioned on the element
         */
        void start(XMLStreamReader reader);

        /**
         * Handles an end element.
         *
         * @param reader the reader positioned on the element
         */
        void end(XMLStreamReader reader);

        /**
         * Returns the result of the completed walk.
         *
         * @return the parsed report, or a refusal
         */
        CoverageParseResult result();
    }

    /**
     * Walks the given XML text with the given handler.
     *
     * @param xml     the XML text
     * @param format  the format whose refusal names the parser
     * @param handler the format-specific walk
     * @return the handler's result, or a refusal naming the format
     */
    static CoverageParseResult parse(String xml, CoverageFormat format, Handler handler) {
        try {
            refuseInternalSubset(xml);
            try (Reader source = open(xml)) {
                return walk(source.stream(), format, handler);
            }
        } catch (XMLStreamException | IllegalArgumentException e) {
            return CoverageRefusals.of(format, e);
        }
    }

    /**
     * Walks the given XML bytes with the given handler, honouring the document's own encoding declaration.
     *
     * @param xml     the XML bytes
     * @param format  the format whose refusal names the parser
     * @param handler the format-specific walk
     * @return the handler's result, or a refusal naming the format
     */
    static CoverageParseResult parse(byte[] xml, CoverageFormat format, Handler handler) {
        try {
            refuseInternalSubset(new String(xml, StandardCharsets.UTF_8));
            try (Reader source = open(xml)) {
                return walk(source.stream(), format, handler);
            }
        } catch (XMLStreamException | IllegalArgumentException e) {
            return CoverageRefusals.of(format, e);
        }
    }

    /**
     * Refuses a document whose DTD carries an internal subset.
     * <p>
     * The declaration a real JaCoCo report writes is external (a system identifier, no subset), and that is tolerated.
     * An internal subset is where an entity is declared, general or parameter, so a document that carries one is refused
     * before the parser reads it — an external parameter entity can otherwise be referenced without ever being fetched
     * and without a refusal.
     *
     * @param xml the document text
     * @throws IllegalArgumentException if the document declares a DTD with an internal subset
     */
    private static void refuseInternalSubset(String xml) {
        int doctype = xml.indexOf("<!DOCTYPE");
        if (doctype < 0) {
            return;
        }
        int subset = xml.indexOf('[', doctype);
        int end = xml.indexOf('>', doctype);
        if (subset >= 0 && (end < 0 || subset < end)) {
            throw new IllegalArgumentException("a DTD with an internal subset is refused");
        }
    }

    private static CoverageParseResult walk(XMLStreamReader reader, CoverageFormat format, Handler handler)
            throws XMLStreamException {
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.ENTITY_REFERENCE) {
                return new CoverageParseResult.Refused(format, format.id() + ": external entity reference refused");
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                handler.start(reader);
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                handler.end(reader);
            }
        }
        return handler.result();
    }

    /**
     * Returns the value of an attribute, or an empty string when the element does not carry it.
     *
     * @param reader the reader positioned on a start element
     * @param name   the attribute name
     * @return the attribute value, never null
     */
    static String attribute(XMLStreamReader reader, String name) {
        String value = reader.getAttributeValue(null, name);
        return value == null ? "" : value;
    }

    static Reader open(String xml) throws XMLStreamException {
        return new Reader(factory().createXMLStreamReader(new StringReader(xml)));
    }

    private static Reader open(byte[] xml) throws XMLStreamException {
        return new Reader(factory().createXMLStreamReader(new ByteArrayInputStream(xml)));
    }

    private static XMLInputFactory factory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.TRUE);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, Boolean.FALSE);
        factory.setXMLResolver((publicId, systemId, baseUri, namespace) -> new ByteArrayInputStream(new byte[0]));
        return factory;
    }

    /**
     * A StAX reader that a caller closes with a try-with-resources statement.
     *
     * @param stream the underlying reader, never null
     * @since 0.1
     */
    record Reader(XMLStreamReader stream) implements AutoCloseable {

        @Override
        public void close() throws XMLStreamException {
            stream.close();
        }
    }
}
