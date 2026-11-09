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
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import lombok.experimental.UtilityClass;

/**
 * Opens the hardened StAX reader the JaCoCo and Cobertura parsers share.
 * <p>
 * The XML formats are read as untrusted input: a document may declare a DTD (JaCoCo writes a {@code DOCTYPE} with a
 * system identifier) and that declaration is tolerated without resolution, but an external entity is never resolved.
 * The factory keeps DTD support on so the declaration parses, turns external entities off, keeps entity references from
 * being replaced, and resolves every external reference to an empty stream, so a DTD or entity that tries to reach a file
 * or a URL reads nothing; the parsers refuse an {@code ENTITY_REFERENCE} event as well. A document that uses an entity
 * defined outside it is therefore refused, never expanded. The decisions are recorded in {@code core-build/005}.
 *
 * @since 0.1
 */
@UtilityClass
class CoverageXmlReader {

    /**
     * Opens a reader over the given XML text.
     *
     * @param xml the XML text
     * @return the configured reader, to be closed by the caller
     * @throws XMLStreamException if the text is not well-formed XML
     */
    static Reader open(String xml) throws XMLStreamException {
        return new Reader(factory().createXMLStreamReader(new StringReader(xml)));
    }

    /**
     * Opens a reader over the given XML bytes, honouring the document's own encoding declaration.
     *
     * @param xml the XML bytes
     * @return the configured reader, to be closed by the caller
     * @throws XMLStreamException if the bytes are not well-formed XML
     */
    static Reader open(byte[] xml) throws XMLStreamException {
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
