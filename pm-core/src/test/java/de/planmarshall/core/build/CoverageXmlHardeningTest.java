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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamReader;
import org.easymock.EasyMock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The hostile prologs (a) to (f) of PLAN-V2-132, fed through {@link CoverageXmlReader} with a resolver that counts its
 * calls, and through the JaCoCo and Cobertura parsers. The LCOV and Istanbul parsers read no XML, so no prolog applies to
 * them.
 * <p>
 * The prolog literals (the system identifiers, entity names and declarations) are the contract of each case; every
 * display name says so. Each probe asserts on what differs between the text-prefilter reader of {@code f97bc7f2} and the
 * DTD-off reader: the resolver is never called, and the outcome is the parser's own. An entity reference in element
 * content is refused; one in an attribute value (case (e)) is dropped from the value, never expanded or resolved. Cases
 * (a) to (e) failed on {@code f97bc7f2}; case (f) is the negative control and passes on both.
 *
 * @since 0.1
 */
@EnableGeneratorController
@DisplayName("CoverageXmlReader hardening")
class CoverageXmlHardeningTest {

    /** Case (a): a quoted {@code >} in the system literal hides the internal subset from a text scan. */
    private static final String QUOTED_GREATER_THAN = "<!DOCTYPE r SYSTEM \"a>b\" [<!ENTITY x \"y\">]>";

    /** Case (d): an internal subset that declares and references a parameter entity. */
    private static final String PARAMETER_ENTITY =
            "<!DOCTYPE r [<!ENTITY % p SYSTEM \"file:///nonexistent/p.dtd\"> %p;]>";

    /** Case (e): the entity reference placed in the {@code name} attribute of the JaCoCo report element. */
    private static final String ATTRIBUTE_ENTITY = "&x;";

    /** The refusal reason of the entity-reference backstop, after the format id; the reason text is the contract. */
    private static final String ENTITY_REFUSAL = "entity reference refused";

    @Test
    @DisplayName("(a) contract prolog: a quoted > hides the subset; the reference is refused, nothing resolved")
    void refusesTheReferenceBehindAQuotedGreaterThan() {
        Probe probe = Probe.read(QUOTED_GREATER_THAN + jacoco("", "&x;"));

        assertAll(
                () -> assertEquals(0, probe.resolverCalls(), "The quoted system literal is never resolved"),
                () -> assertRefusedFor(CoverageFormat.JACOCO, probe.result()),
                () -> assertRefusedFor(CoverageFormat.JACOCO,
                        JacocoReportParser.parse(QUOTED_GREATER_THAN + jacoco("", "&x;"))),
                () -> assertRefusedFor(CoverageFormat.COBERTURA,
                        CoberturaReportParser.parse(QUOTED_GREATER_THAN + cobertura("&x;"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"UTF-16LE", "UTF-16BE"})
    @DisplayName("(b) contract prolog: the case (a) document without a byte-order mark is refused, nothing resolved")
    void refusesTheReferenceWithoutAByteOrderMark(String charset) {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-16\"?>" + QUOTED_GREATER_THAN + jacoco("", "&x;");

        Probe probe = Probe.read(xml.getBytes(Charset.forName(charset)));

        assertAll(
                () -> assertEquals(0, probe.resolverCalls(), "The encoded system literal is never resolved"),
                () -> assertRefusedFor(CoverageFormat.JACOCO, probe.result()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://host/x.dtd", "file:///nonexistent/x.dtd"})
    @DisplayName("(c) contract system ids: an external subset is tolerated and never resolved")
    void toleratesAnExternalSubsetWithoutResolvingIt(String systemId) {
        String prolog = "<!DOCTYPE r SYSTEM \"" + systemId + "\">";

        Probe probe = Probe.read(prolog + jacoco("", ""));

        assertAll(
                () -> assertEquals(0, probe.resolverCalls(), "The external subset is never resolved"),
                () -> assertInstanceOf(CoverageParseResult.Parsed.class, probe.result(), "The DOCTYPE is tolerated"),
                () -> assertInstanceOf(CoverageParseResult.Parsed.class,
                        JacocoReportParser.parse(prolog + jacoco("", "")), "The JaCoCo report still parses"),
                () -> assertInstanceOf(CoverageParseResult.Parsed.class,
                        CoberturaReportParser.parse(prolog + cobertura("")), "The Cobertura report still parses"));
    }

    @Test
    @DisplayName("(d) contract prolog: a parameter entity in the subset is skipped, never resolved")
    void skipsAParameterEntityWithoutResolvingIt() {
        Probe probe = Probe.read(PARAMETER_ENTITY + jacoco("", ""));

        assertAll(
                () -> assertEquals(0, probe.resolverCalls(), "The parameter entity is never resolved"),
                () -> assertInstanceOf(CoverageParseResult.Parsed.class, probe.result(), "The unused subset is skipped"),
                () -> assertInstanceOf(CoverageParseResult.Parsed.class,
                        JacocoReportParser.parse(PARAMETER_ENTITY + jacoco("", "")), "The JaCoCo report still parses"),
                () -> assertInstanceOf(CoverageParseResult.Parsed.class,
                        CoberturaReportParser.parse(PARAMETER_ENTITY + cobertura("")),
                        "The Cobertura report still parses"));
    }

    @Test
    @DisplayName("(e) contract prolog: an entity reference in an attribute value is dropped, never resolved")
    void dropsAnEntityReferenceInAnAttribute() {
        String xml = "<!DOCTYPE r SYSTEM \"x.dtd\">" + jacoco(ATTRIBUTE_ENTITY, "");
        AtomicReference<String> name = new AtomicReference<>();

        Probe probe = Probe.read(xml, recording(name));

        assertAll(
                () -> assertEquals(0, probe.resolverCalls(), "The attribute entity is never resolved"),
                () -> assertEquals("", name.get(), "The entity is dropped from the attribute value"),
                () -> assertFalse(name.get().contains(ATTRIBUTE_ENTITY), "The entity text is absent from the value"),
                () -> assertInstanceOf(CoverageParseResult.Parsed.class, JacocoReportParser.parse(xml),
                        "JaCoCo reads the report without the entity"));
    }

    @Test
    @DisplayName("(f) negative control, contract text: a comment and a CDATA that mention DOCTYPE parse")
    void parsesACommentAndACdataThatMentionADoctype() {
        String xml = "<!-- <!DOCTYPE r [<!ENTITY x \"y\">]> -->" + jacoco("", "<![CDATA[<!DOCTYPE r>]]>");

        Probe probe = Probe.read(xml);

        assertAll(
                () -> assertEquals(0, probe.resolverCalls(), "The control document resolves nothing"),
                () -> assertInstanceOf(CoverageParseResult.Parsed.class, probe.result(), "The control document parses"),
                () -> assertInstanceOf(CoverageParseResult.Parsed.class, JacocoReportParser.parse(xml),
                        "JaCoCo parses the control document"));
    }

    private static String jacoco(String name, String body) {
        return "<report name=\"" + name + "\"><counter type=\"LINE\" missed=\"0\" covered=\"1\"/>" + body + "</report>";
    }

    private static String cobertura(String body) {
        return "<coverage line-rate=\"1\" branch-rate=\"1\"><packages>" + body + "</packages></coverage>";
    }

    private static void assertRefusedFor(CoverageFormat format, CoverageParseResult result) {
        CoverageParseResult.Refused refused =
                assertInstanceOf(CoverageParseResult.Refused.class, result, "The hostile document is refused");
        assertEquals(format.id() + ": " + ENTITY_REFUSAL, refused.reason(), "The parser itself refuses the reference");
    }

    /**
     * One read through {@link CoverageXmlReader} with a resolver that counts its calls.
     *
     * @param result        the reader's result
     * @param resolverCalls how often the resolver was asked for an external resource
     */
    private record Probe(CoverageParseResult result, int resolverCalls) {

        static Probe read(String xml) {
            return read(xml, handler());
        }

        static Probe read(String xml, CoverageXmlReader.Handler handler) {
            AtomicInteger calls = new AtomicInteger();
            return new Probe(CoverageXmlReader.parse(xml, CoverageFormat.JACOCO, handler, counting(calls)), calls.get());
        }

        static Probe read(byte[] xml) {
            AtomicInteger calls = new AtomicInteger();
            return new Probe(CoverageXmlReader.parse(new ByteArrayInputStream(xml), CoverageFormat.JACOCO, handler(),
                    counting(calls)), calls.get());
        }

        private static XMLResolver counting(AtomicInteger calls) {
            return (publicId, systemId, baseUri, namespace) -> {
                calls.incrementAndGet();
                return new ByteArrayInputStream(new byte[0]);
            };
        }

        private static CoverageXmlReader.Handler handler() {
            CoverageXmlReader.Handler handler = EasyMock.createNiceMock(CoverageXmlReader.Handler.class);
            expectParsed(handler);
            EasyMock.replay(handler);
            return handler;
        }
    }

    /**
     * A handler that records the {@code name} attribute of the report element as the reader delivers it.
     *
     * @param name receives the attribute value
     * @return the replayed handler mock
     */
    private static CoverageXmlReader.Handler recording(AtomicReference<String> name) {
        CoverageXmlReader.Handler handler = EasyMock.createNiceMock(CoverageXmlReader.Handler.class);
        handler.start(EasyMock.anyObject());
        EasyMock.expectLastCall().andAnswer(() -> {
            XMLStreamReader reader = EasyMock.getCurrentArgument(0);
            if ("report".equals(reader.getLocalName())) {
                name.set(CoverageXmlReader.attribute(reader, "name"));
            }
            return null;
        }).anyTimes();
        expectParsed(handler);
        EasyMock.replay(handler);
        return handler;
    }

    private static void expectParsed(CoverageXmlReader.Handler handler) {
        CoverageMetrics metrics = CoverageMetrics.builder().line(Generators.doubles(0.0, 100.0).next()).build();
        EasyMock.expect(handler.result())
                .andReturn(new CoverageParseResult.Parsed(new CoverageReport(CoverageFormat.JACOCO, metrics, List.of())))
                .anyTimes();
    }
}
