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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.easymock.EasyMock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The hardened reader the two XML coverage parsers share: DTD support is off, a {@code DOCTYPE} declaration is tolerated
 * and never resolved or fetched, and an entity reference is refused, never expanded.
 *
 * @since 0.1
 */
@EnableGeneratorController
@DisplayName("CoverageXmlReader")
class CoverageXmlReaderTest {

    @TempDir
    Path directory;

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
    void refusesAnExternalEntity() throws Exception {
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

    // The encoding declarations are the contract: the reader detects the encoding from the bytes, with no BOM.
    @Test
    @DisplayName("detects a BOM-less UTF-16 report from its bytes")
    void detectsABomLessUtf16Report() throws Exception {
        String xml = report("UTF-16");

        CoverageParseResult wide = JacocoReportParser.parse(write("utf-16.xml", xml, StandardCharsets.UTF_16BE));

        assertEquals(CoverageReportFixtures.reportOf(JacocoReportParser.parse(xml)),
                CoverageReportFixtures.reportOf(wide), "UTF-16 reads like the same text");
    }

    // The encoding declaration is the contract. Measured: the JDK StAX reader reads the BOM-less UTF-32 bytes up to the
    // end of the declaration and then rejects the name "UTF-32" as unknown, so the report is refused, never misread.
    @Test
    @DisplayName("refuses a BOM-less UTF-32 report whose declared encoding StAX does not know")
    void refusesABomLessUtf32Report() throws Exception {
        String xml = report("UTF-32");

        CoverageParseResult wide = JacocoReportParser.parse(write("utf-32.xml", xml, Charset.forName("UTF-32BE")));

        CoverageParseResult.Refused refused =
                assertInstanceOf(CoverageParseResult.Refused.class, wide, "A UTF-32 report is refused, not misread");
        assertTrue(refused.reason().startsWith("jacoco: ParseError at [row,col]:[1,40]"),
                "The reader stops at the end of the declaration");
    }

    // The encoding declaration is the contract: the stream carries a byte-order mark the reader honours.
    @Test
    @DisplayName("reads a UTF-16 report with a byte-order mark through the stream")
    void readsAUtf16ReportWithAByteOrderMark() throws Exception {
        String xml = report("UTF-16");

        CoverageParseResult wide = JacocoReportParser.parse(write("utf-16-bom.xml", xml, StandardCharsets.UTF_16));

        assertEquals(CoverageReportFixtures.reportOf(JacocoReportParser.parse(xml)),
                CoverageReportFixtures.reportOf(wide), "UTF-16 with a BOM reads like the text");
    }

    @Test
    @DisplayName("refuses a stream past the byte cap, naming the limit")
    void refusesAStreamPastTheByteCap() {
        byte[] xml = report("UTF-8").getBytes(StandardCharsets.UTF_8);
        long limit = xml.length - 1L;

        CoverageParseResult result = CoverageXmlReader.parse(
                new CoverageReportStream(new ByteArrayInputStream(xml), limit), CoverageFormat.JACOCO, handler());

        CoverageParseResult.Refused refused =
                assertInstanceOf(CoverageParseResult.Refused.class, result, "A report past the cap is refused");
        assertTrue(refused.reason().contains("limit of " + limit + " bytes"), "The refusal names the byte limit");
    }

    @Test
    @DisplayName("walks a stream within the byte cap to the handler's result")
    void walksAStreamWithinTheByteCap() {
        byte[] xml = report("UTF-8").getBytes(StandardCharsets.UTF_8);

        CoverageParseResult result = CoverageXmlReader.parse(
                new CoverageReportStream(new ByteArrayInputStream(xml), xml.length), CoverageFormat.JACOCO, handler());

        assertInstanceOf(CoverageParseResult.Parsed.class, result, "A report within the cap is walked");
    }

    private static CoverageXmlReader.Handler handler() {
        CoverageXmlReader.Handler handler = EasyMock.createNiceMock(CoverageXmlReader.Handler.class);
        CoverageMetrics metrics = CoverageMetrics.builder().line(Generators.doubles(0.0, 100.0).next()).build();
        EasyMock.expect(handler.result())
                .andReturn(new CoverageParseResult.Parsed(new CoverageReport(CoverageFormat.JACOCO, metrics, List.of())))
                .anyTimes();
        EasyMock.replay(handler);
        return handler;
    }

    private static String report(String encoding) {
        int missed = Generators.integers(0, 1000).next();
        int covered = Generators.integers(1, 1000).next();
        return """
                <?xml version="1.0" encoding="%s"?>
                <report name="encoded"><counter type="LINE" missed="%d" covered="%d"/></report>
                """.formatted(encoding, missed, covered);
    }

    private Path write(String name, String xml, Charset charset) throws IOException {
        return Files.write(directory.resolve(name), xml.getBytes(charset));
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
