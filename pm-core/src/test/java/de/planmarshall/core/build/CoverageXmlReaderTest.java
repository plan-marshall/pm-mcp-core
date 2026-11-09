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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The hardened reader the two XML coverage parsers share: a {@code DOCTYPE} declaration is tolerated without resolution,
 * and an external entity is refused, never expanded.
 *
 * @since 0.1
 */
@DisplayName("CoverageXmlReader")
class CoverageXmlReaderTest {

    @Test
    @DisplayName("tolerates a DOCTYPE declaration without resolving it")
    void toleratesADoctypeDeclaration() throws Exception {
        String xml = Files.readString(CoverageReportFixtures.path("jacoco-high.xml"));

        try (CoverageXmlReader.Reader source = CoverageXmlReader.open(xml)) {
            XMLStreamReader reader = source.stream();
            while (reader.next() != XMLStreamConstants.START_ELEMENT) {
                assertEquals(XMLStreamConstants.DTD, reader.getEventType(),
                        "The DOCTYPE declaration precedes the root element");
            }
            assertEquals("report", reader.getLocalName(), "The reader reaches the report element");
        }
    }

    @Test
    @DisplayName("raises an error for a document that is not well-formed")
    void raisesAnErrorForMalformedXml() {
        assertThrows(XMLStreamException.class, this::readUnclosedDocument,
                "An unclosed document raises an XML error");
    }

    private void readUnclosedDocument() throws XMLStreamException {
        try (CoverageXmlReader.Reader source = CoverageXmlReader.open("<report>")) {
            XMLStreamReader reader = source.stream();
            while (reader.hasNext()) {
                reader.next();
            }
        }
    }

    @Test
    @DisplayName("refuses a document that uses an external entity")
    void refusesAnExternalEntity() throws IOException {
        String xml = Files.readString(CoverageReportFixtures.path("deliberate-jacoco-external-entity.xml"));

        assertTrue(isRefused(xml), "The external entity is refused, not resolved");
    }

    @Test
    @DisplayName("reads a plain document without an entity")
    void readsAPlainDocument() throws Exception {
        try (CoverageXmlReader.Reader source =
                     CoverageXmlReader.open("<report name=\"a\"><counter type=\"LINE\"/></report>")) {
            assertEquals(XMLStreamConstants.START_ELEMENT, source.stream().next(),
                    "The root element starts the document");
        }
    }

    private static boolean isRefused(String xml) {
        try (CoverageXmlReader.Reader source = CoverageXmlReader.open(xml)) {
            XMLStreamReader reader = source.stream();
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamConstants.ENTITY_REFERENCE) {
                    return true;
                }
            }
            return false;
        } catch (XMLStreamException _) {
            return true;
        }
    }
}
