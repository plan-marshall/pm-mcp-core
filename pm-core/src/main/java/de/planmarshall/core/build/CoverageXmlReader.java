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
import java.io.InputStream;
import java.io.StringReader;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import lombok.experimental.UtilityClass;

/**
 * Opens the hardened StAX reader the JaCoCo and Cobertura parsers share, and walks it for them.
 * <p>
 * The XML formats are read as untrusted input, and the parser itself is the defence: there is no text scan before it. A
 * document may declare a DTD (JaCoCo writes a {@code DOCTYPE} with a system identifier); that declaration is tolerated
 * and never resolved or fetched. The factory turns DTD support off, turns external entities off, keeps entity references
 * from being replaced, and resolves every external reference to an empty stream, so a DTD or entity that tries to reach a
 * file or a URL reads nothing. The walk refuses an {@code ENTITY_REFERENCE} event, so a document that references an
 * entity in element content is refused, never expanded. An undeclared entity reference in an attribute value raises no
 * such event: the reader drops it from the value, so it is never expanded and never resolved either. As defence in
 * depth, the factory also allows no protocol for an external DTD or schema ({@link XMLConstants#ACCESS_EXTERNAL_DTD},
 * {@link XMLConstants#ACCESS_EXTERNAL_SCHEMA}) where the implementation accepts the two properties; the JDK's built-in
 * StAX implementation is the supported one and accepts both. The decisions are recorded in {@code core-build/008}, which
 * supersedes the hardening part of {@code core-build/005}, and in {@code core-build/009}.
 *
 * @since 0.1
 */
@UtilityClass
class CoverageXmlReader {

    /** The production resolver: every external reference reads an empty stream, so nothing is fetched. */
    private static final XMLResolver EMPTY_RESOLVER =
            (publicId, systemId, baseUri, namespace) -> new ByteArrayInputStream(new byte[0]);

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
        return parse(xml, format, handler, EMPTY_RESOLVER);
    }

    /**
     * Walks the given XML text with the given handler, handing every external reference to the given resolver.
     * <p>
     * The parsers call the overload without a resolver, which passes the empty-stream resolver; a test passes one that
     * counts its calls, to show that the reader asks for nothing.
     *
     * @param xml      the XML text
     * @param format   the format whose refusal names the parser
     * @param handler  the format-specific walk
     * @param resolver the resolver every external reference is handed to
     * @return the handler's result, or a refusal naming the format
     */
    static CoverageParseResult parse(String xml, CoverageFormat format, Handler handler, XMLResolver resolver) {
        try (Reader source = new Reader(factory(resolver).createXMLStreamReader(new StringReader(xml)))) {
            return walk(source.stream(), format, handler);
        } catch (XMLStreamException | IllegalArgumentException e) {
            return CoverageRefusals.of(format, e);
        }
    }

    /**
     * Walks the given XML stream with the given handler, honouring the document's own encoding declaration and
     * byte-order mark.
     * <p>
     * The stream is read as it is walked, never whole; the caller owns it and closes it. A read failure of the stream —
     * the bound of {@link CoverageReportStream} among them — ends the walk with a refusal naming the format.
     *
     * @param xml     the XML stream
     * @param format  the format whose refusal names the parser
     * @param handler the format-specific walk
     * @return the handler's result, or a refusal naming the format
     */
    static CoverageParseResult parse(InputStream xml, CoverageFormat format, Handler handler) {
        return parse(xml, format, handler, EMPTY_RESOLVER);
    }

    /**
     * Walks the given XML stream with the given handler, handing every external reference to the given resolver.
     *
     * @param xml      the XML stream
     * @param format   the format whose refusal names the parser
     * @param handler  the format-specific walk
     * @param resolver the resolver every external reference is handed to
     * @return the handler's result, or a refusal naming the format
     */
    static CoverageParseResult parse(InputStream xml, CoverageFormat format, Handler handler, XMLResolver resolver) {
        try (Reader source = new Reader(factory(resolver).createXMLStreamReader(xml))) {
            return walk(source.stream(), format, handler);
        } catch (XMLStreamException | IllegalArgumentException e) {
            return CoverageRefusals.of(format, e);
        }
    }

    private static CoverageParseResult walk(XMLStreamReader reader, CoverageFormat format, Handler handler)
            throws XMLStreamException {
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.ENTITY_REFERENCE) {
                return new CoverageParseResult.Refused(format, format.id() + ": entity reference refused");
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
        return new Reader(factory(EMPTY_RESOLVER).createXMLStreamReader(new StringReader(xml)));
    }

    /**
     * Builds the hardened factory: DTD support, external entities and entity replacement off, every external reference
     * handed to the given resolver, and no protocol allowed for an external DTD or schema where the implementation
     * accepts the two access properties.
     *
     * @param resolver the resolver every external reference is handed to
     * @return the hardened factory
     */
    static XMLInputFactory factory(XMLResolver resolver) {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, Boolean.FALSE);
        setIfAccepted(factory, XMLConstants.ACCESS_EXTERNAL_DTD);
        setIfAccepted(factory, XMLConstants.ACCESS_EXTERNAL_SCHEMA);
        factory.setXMLResolver(resolver);
        return factory;
    }

    /**
     * Allows no protocol for the given access property, where the implementation accepts it. The JDK's built-in StAX
     * implementation accepts both access properties; an implementation that does not rejects the property with an
     * {@link IllegalArgumentException}, and the properties already set stay in force as the defence.
     *
     * @param factory  the factory being hardened
     * @param property the access property, {@link XMLConstants#ACCESS_EXTERNAL_DTD} or
     *                 {@link XMLConstants#ACCESS_EXTERNAL_SCHEMA}
     * @return whether the implementation accepted the property
     */
    static boolean setIfAccepted(XMLInputFactory factory, String property) {
        try {
            factory.setProperty(property, "");
            return true;
        } catch (IllegalArgumentException _) {
            return false;
        }
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
