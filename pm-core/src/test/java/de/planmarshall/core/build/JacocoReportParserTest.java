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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The ported tests of {@link JacocoReportParser}: each case reads one plain-file fixture below {@code ported/coverage},
 * whose expected values are read by hand from the original and its pytest case ({@code PROVENANCE.adoc}).
 *
 * @since 0.1
 */
@EnableGeneratorController
@DisplayName("JacocoReportParser")
class JacocoReportParserTest {

    /** The refusal reason of the entity-reference backstop; the reason text is the contract. */
    private static final String ENTITY_REFUSAL = "jacoco: entity reference refused";

    @Test
    @DisplayName("reads the overall counters of a fully covered report")
    void readsTheOverallCounters() {
        CoverageReport report = CoverageReportFixtures.reportOf(
                JacocoReportParser.parse(CoverageReportFixtures.path("jacoco-high.xml")));

        assertAll(
                () -> assertEquals(CoverageFormat.JACOCO, report.format(), "The report names its format"),
                () -> assertEquals(100.0, report.overall().line(), "The line counter is fully covered"),
                () -> assertEquals(100.0, report.overall().branch(), "The branch counter is fully covered"),
                () -> assertEquals(90.0, report.overall().instruction(), "The instruction counter is 18 of 20"),
                () -> assertEquals(100.0, report.overall().function(), "The method counter is fully covered"),
                () -> assertNull(report.overall().statement(), "JaCoCo carries no statement counter"));
    }

    @Test
    @DisplayName("reads the per-class counter and the missed methods")
    void readsTheClassCounterAndMissedMethods() {
        CoverageReport report = CoverageReportFixtures.reportOf(
                JacocoReportParser.parse(CoverageReportFixtures.path("jacoco-low.xml")));

        CoverageItem item = report.items().getFirst();

        assertAll(
                () -> assertEquals(22.22, report.overall().line(), "The overall line percentage is 2 of 9"),
                () -> assertEquals(25.0, report.overall().branch(), "The overall branch percentage is 1 of 4"),
                () -> assertEquals(21.74, report.overall().instruction(), "The instruction percentage is 5 of 23"),
                () -> assertEquals(50.0, report.overall().function(), "The method percentage is 1 of 2"),
                () -> assertEquals(1, report.items().size(), "The report carries one class"),
                () -> assertEquals("com.example.LegacyService", item.name(), "The class name is dotted"),
                () -> assertEquals(22.22, item.line(), "The class line percentage is 2 of 9"),
                () -> assertNull(item.file(), "A JaCoCo class names no separate file"),
                () -> assertEquals(List.of("cleanup"), item.missedMethods(), "The missed method is cleanup"));
    }

    @Test
    @DisplayName("leaves the constructors out of the missed methods")
    void leavesTheConstructorsOut() {
        CoverageReport report = CoverageReportFixtures.reportOf(JacocoReportParser
                .parse(CoverageReportFixtures.path("deliberate-jacoco-init-excluded.xml")));

        assertAll(
                () -> assertEquals(1, report.items().size(), "The report carries one class"),
                () -> assertEquals(List.of("run"), report.items().getFirst().missedMethods(),
                        "Only the real missed method is named"),
                () -> assertEquals(50.0, report.items().getFirst().line(), "The class line percentage is 1 of 2"));
    }

    @Test
    @DisplayName("passes the parsed fixture whose branch coverage is below")
    void passesTheParsedFixtureOnItsLine() {
        CoverageReport report = CoverageReportFixtures.reportOf(
                JacocoReportParser.parse(CoverageReportFixtures.path("deliberate-branch-below-line-above.xml")));

        CoverageVerdict verdict = report.evaluate(80);

        assertAll(
                () -> assertEquals(33.33, report.overall().branch(), "The fixture branch is below 80"),
                () -> assertEquals(90.0, report.overall().line(), "The fixture line is above 80"),
                () -> assertTrue(verdict.passed(), "The parsed fixture passes on its line"),
                () -> assertTrue(verdict.lowCoverage().isEmpty(), "The parsed fixture lists no low item"));
    }

    @Test
    @DisplayName("fails the parsed fixture whose line coverage is below")
    void failsTheParsedFixtureOnItsLine() {
        CoverageReport report = CoverageReportFixtures.reportOf(
                JacocoReportParser.parse(CoverageReportFixtures.path("deliberate-branch-above-line-below.xml")));

        CoverageVerdict verdict = report.evaluate(80);

        assertAll(
                () -> assertEquals(90.0, report.overall().branch(), "The fixture branch is above 80"),
                () -> assertEquals(40.0, report.overall().line(), "The fixture line is below 80"),
                () -> assertFalse(verdict.passed(), "The parsed fixture fails on its line"),
                () -> assertEquals(1, verdict.lowCoverage().size(), "The parsed fixture lists its low class"));
    }

    @Test
    @DisplayName("refuses a report that uses an external entity")
    void refusesAnExternalEntity() {
        CoverageParseResult result =
                JacocoReportParser.parse(CoverageReportFixtures.path("deliberate-jacoco-external-entity.xml"));

        CoverageParseResult.Refused refused =
                assertRefused(result, "An external entity is refused, not expanded");

        assertAll(
                () -> assertEquals(CoverageFormat.JACOCO, refused.format(), "The refusal names JaCoCo"),
                () -> assertTrue(refused.reason().startsWith("jacoco: "), "The reason carries the format id"));
    }

    @Test
    @DisplayName("refuses a truncated report")
    void refusesATruncatedReport() {
        CoverageParseResult result =
                JacocoReportParser.parse(CoverageReportFixtures.path("deliberate-jacoco-truncated.xml"));

        CoverageParseResult.Refused refused = assertRefused(result, "A truncated report is refused, not zero");

        assertEquals(CoverageFormat.JACOCO, refused.format(), "The refusal names JaCoCo");
    }

    // The reason text "absent.xml: no such file" is the contract: the file name, never the directory.
    @Test
    @DisplayName("refuses an unreadable report file, naming its file name only")
    void refusesAnUnreadableFile() {
        Path absent = CoverageReportFixtures.path("jacoco-high.xml").resolveSibling("absent.xml");

        CoverageParseResult result = JacocoReportParser.parse(absent);

        CoverageParseResult.Refused refused = assertRefused(result, "A missing report file is refused");
        assertAll(
                () -> assertEquals(CoverageFormat.JACOCO, refused.format(), "The refusal names JaCoCo"),
                () -> assertEquals("jacoco: absent.xml: no such file", refused.reason(),
                        "The reason names the file name only"),
                () -> assertFalse(refused.reason().contains(absent.getParent().toString()),
                        "The reason never names the directory"));
    }

    @Test
    @DisplayName("refuses an entity that is referenced in the element content")
    void refusesAContentEntity() {
        CoverageParseResult result = JacocoReportParser
                .parse(CoverageReportFixtures.path("deliberate-jacoco-external-entity-content.xml"));

        CoverageParseResult.Refused refused =
                assertInstanceOf(CoverageParseResult.Refused.class, result, "A content entity is refused");

        assertAll(
                () -> assertEquals(CoverageFormat.JACOCO, refused.format(), "The refusal names JaCoCo"),
                () -> assertEquals(ENTITY_REFUSAL, refused.reason(), "The parser refuses the entity reference"));
    }

    @Test
    @DisplayName("keeps the metrics the report does not carry absent")
    void keepsTheAbsentMetricsNull() {
        CoverageReport report = CoverageReportFixtures.reportOf(JacocoReportParser.parse("""
                <report name="line-only">
                  <counter type="LINE" missed="1" covered="1"/>
                </report>
                """));

        assertAll(
                () -> assertEquals(50.0, report.overall().line(), "The line counter is 1 of 2"),
                () -> assertNull(report.overall().instruction(), "An absent instruction counter is null"),
                () -> assertNull(report.overall().branch(), "An absent branch counter is null"),
                () -> assertNull(report.overall().function(), "An absent method counter is null"));
    }

    @Test
    @DisplayName("refuses a document that is not a JaCoCo report")
    void refusesANonReportDocument() {
        CoverageParseResult result =
                JacocoReportParser.parse("<coverage line-rate=\"0.5\" branch-rate=\"0.5\"/>");

        CoverageParseResult.Refused refused =
                assertInstanceOf(CoverageParseResult.Refused.class, result, "Another root element is refused");

        assertEquals(CoverageFormat.JACOCO, refused.format(), "The refusal names JaCoCo");
    }

    @Test
    @DisplayName("refuses a report without a line counter")
    void refusesAReportWithoutLineCounter() {
        CoverageParseResult result = JacocoReportParser.parse("<report name=\"empty\"/>");

        CoverageParseResult.Refused refused = assertInstanceOf(CoverageParseResult.Refused.class, result,
                "A report without a LINE counter is refused");

        assertEquals(CoverageFormat.JACOCO, refused.format(), "The refusal names JaCoCo");
    }

    @Test
    @DisplayName("refuses a negative counter")
    void refusesANegativeCounter() {
        CoverageParseResult result = JacocoReportParser.parse("""
                <report name="negative">
                  <counter type="LINE" missed="-1" covered="1"/>
                </report>
                """);

        CoverageParseResult.Refused refused = assertInstanceOf(CoverageParseResult.Refused.class, result,
                "A negative counter is refused");

        assertEquals(CoverageFormat.JACOCO, refused.format(), "The refusal names JaCoCo");
    }

    @Test
    @DisplayName("leaves a class without a line counter out of the measured items")
    void skipsAClassWithoutLineCounter() {
        CoverageReport report = CoverageReportFixtures.reportOf(JacocoReportParser.parse("""
                <report name="class-lines">
                  <counter type="LINE" missed="0" covered="1"/>
                  <package name="p">
                    <class name="p/A"/>
                  </package>
                </report>
                """));

        assertAll(
                () -> assertEquals(100.0, report.overall().line(), "The overall line stays measured"),
                () -> assertTrue(report.items().isEmpty(), "The unmeasured class is not listed as an item"));
    }

    // The counter values are the contract: an overall LINE counter of 0/0 measured no line.
    @Test
    @DisplayName("refuses a report whose overall line counter is 0/0")
    void refusesAReportWithoutMeasuredLines() {
        CoverageParseResult result = JacocoReportParser.parse("""
                <report name="no-lines">
                  <counter type="LINE" missed="0" covered="0"/>
                </report>
                """);

        CoverageParseResult.Refused refused = assertRefused(result, "A report with no measured line is refused");

        assertAll(
                () -> assertEquals(CoverageFormat.JACOCO, refused.format(), "The refusal names JaCoCo"),
                () -> assertEquals("jacoco: no lines measured", refused.reason(), "The reason says no line measured"));
    }

    // The counter values are the contract: a 0/0 counter is absent, never a measured 0.0.
    @Test
    @DisplayName("reads an overall instruction, branch and method counter of 0/0 as absent")
    void readsTheEmptyOverallCountersAsAbsent() {
        CoverageReport report = CoverageReportFixtures.reportOf(JacocoReportParser.parse("""
                <report name="empty-counters">
                  <counter type="INSTRUCTION" missed="0" covered="0"/>
                  <counter type="BRANCH" missed="0" covered="0"/>
                  <counter type="LINE" missed="1" covered="1"/>
                  <counter type="METHOD" missed="0" covered="0"/>
                </report>
                """));

        assertAll(
                () -> assertEquals(50.0, report.overall().line(), "The line counter is 1 of 2"),
                () -> assertNull(report.overall().instruction(), "An instruction counter of 0/0 is null"),
                () -> assertNull(report.overall().branch(), "A branch counter of 0/0 is null"),
                () -> assertNull(report.overall().function(), "A method counter of 0/0 is null"));
    }

    // The counter values are the contract: a class LINE 0/0 is no item, a class BRANCH 0/0 is no branch.
    @Test
    @DisplayName("omits a class whose line counter is 0/0 and keeps a class branch of 0/0 absent")
    void omitsAClassWithoutMeasuredLines() {
        CoverageReport report = CoverageReportFixtures.reportOf(JacocoReportParser.parse("""
                <report name="class-zero">
                  <counter type="LINE" missed="1" covered="1"/>
                  <package name="p">
                    <class name="p/Empty">
                      <counter type="LINE" missed="0" covered="0"/>
                    </class>
                    <class name="p/Measured">
                      <counter type="BRANCH" missed="0" covered="0"/>
                      <counter type="LINE" missed="1" covered="1"/>
                    </class>
                  </package>
                </report>
                """));

        assertAll(
                () -> assertEquals(1, report.items().size(), "Only the measured class is an item"),
                () -> assertEquals("p.Measured", report.items().getFirst().name(), "The measured class is kept"),
                () -> assertEquals(50.0, report.items().getFirst().line(), "The class line is 1 of 2"),
                () -> assertNull(report.items().getFirst().branch(), "A class branch counter of 0/0 is null"));
    }

    // The prolog is the contract: a comment that mentions a DOCTYPE precedes the real one, which declares the entity.
    @Test
    @DisplayName("refuses an entity declared behind a comment that mentions a DOCTYPE")
    void refusesAnEntityDeclaredBehindAComment() {
        CoverageParseResult result = JacocoReportParser.parse("""
                <?xml version="1.0"?>
                <!-- <!DOCTYPE fake > -->
                <!DOCTYPE report [<!ENTITY probe SYSTEM "file:///nonexistent">]>
                <report name="a"><counter type="LINE" missed="0" covered="1"/>&probe;</report>
                """);

        CoverageParseResult.Refused refused = assertInstanceOf(CoverageParseResult.Refused.class, result,
                "The parser refuses the declared entity");

        assertAll(
                () -> assertEquals(CoverageFormat.JACOCO, refused.format(), "The refusal names JaCoCo"),
                () -> assertEquals(ENTITY_REFUSAL, refused.reason(), "The parser refuses the entity reference"));
    }

    // The counters are the contract: a LINE counter written twice at the report root.
    @Test
    @DisplayName("refuses a report that carries a root counter type twice")
    void refusesARepeatedRootCounter() {
        CoverageParseResult result = JacocoReportParser.parse("""
                <report name="repeated-root">
                  <counter type="LINE" missed="9" covered="0"/>
                  <counter type="LINE" missed="0" covered="9"/>
                </report>
                """);

        CoverageParseResult.Refused refused = assertRefused(result, "A repeated root counter is refused");

        assertAll(
                () -> assertEquals(CoverageFormat.JACOCO, refused.format(), "The refusal names JaCoCo"),
                () -> assertEquals("jacoco: the report carries the LINE counter twice", refused.reason(),
                        "The reason names the repeated counter type"));
    }

    // The counters are the contract: a LINE counter written twice directly within one class.
    @Test
    @DisplayName("refuses a class that carries a counter type twice")
    void refusesARepeatedClassCounter() {
        CoverageParseResult result = JacocoReportParser.parse("""
                <report name="repeated-class">
                  <counter type="LINE" missed="1" covered="1"/>
                  <package name="p">
                    <class name="p/A">
                      <counter type="LINE" missed="2" covered="0"/>
                      <counter type="LINE" missed="0" covered="2"/>
                    </class>
                  </package>
                </report>
                """);

        CoverageParseResult.Refused refused = assertRefused(result, "A repeated class counter is refused");

        assertAll(
                () -> assertEquals(CoverageFormat.JACOCO, refused.format(), "The refusal names JaCoCo"),
                () -> assertEquals("jacoco: the class p.A carries the LINE counter twice", refused.reason(),
                        "The reason names the class and counter type"));
    }

    // The counters are the contract: each type once at the root and once per class, as JaCoCo writes them.
    @Test
    @DisplayName("reads a report that carries each counter type once at the root and per class")
    void readsEachCounterTypeOnce() {
        CoverageReport report = CoverageReportFixtures.reportOf(JacocoReportParser.parse("""
                <report name="once">
                  <package name="p">
                    <class name="p/A">
                      <method name="run">
                        <counter type="LINE" missed="1" covered="0"/>
                        <counter type="METHOD" missed="1" covered="0"/>
                      </method>
                      <counter type="BRANCH" missed="1" covered="1"/>
                      <counter type="LINE" missed="1" covered="3"/>
                      <counter type="METHOD" missed="1" covered="0"/>
                    </class>
                  </package>
                  <counter type="BRANCH" missed="1" covered="1"/>
                  <counter type="LINE" missed="1" covered="3"/>
                  <counter type="METHOD" missed="1" covered="0"/>
                </report>
                """));

        assertAll(
                () -> assertEquals(75.0, report.overall().line(), "The overall line counter is 3 of 4"),
                () -> assertEquals(50.0, report.overall().branch(), "The overall branch counter is 1 of 2"),
                () -> assertEquals(1, report.items().size(), "The report carries one class"),
                () -> assertEquals(75.0, report.items().getFirst().line(), "The class line counter is 3 of 4"),
                () -> assertEquals(List.of("run"), report.items().getFirst().missedMethods(),
                        "The missed method is run"));
    }

    @Test
    @DisplayName("cuts a hostile long counter type to the echo bound in the reason")
    void cutsAHostileCounterType() {
        String type = Generators.letterStrings(1_000, 3_000).next();

        CoverageParseResult result = JacocoReportParser.parse("""
                <report name="hostile-type">
                  <counter type="%1$s" missed="1" covered="1"/>
                  <counter type="%1$s" missed="1" covered="1"/>
                </report>
                """.formatted(type));

        CoverageParseResult.Refused refused = assertRefused(result, "A repeated hostile counter type is refused");
        assertAll(
                () -> assertEquals("jacoco: ".length() + CoverageRefusals.MAX_DETAIL_LENGTH, refused.reason().length(),
                        "The echoed type is cut to the echo bound"),
                () -> assertTrue(refused.reason().startsWith("jacoco: the report carries the "),
                        "The reason keeps its prefix and its sentence"),
                () -> assertFalse(refused.reason().contains(type), "The whole hostile type is never echoed"));
    }

    @Test
    @DisplayName("cuts a hostile long counter value to the echo bound in the reason")
    void cutsAHostileCounterValue() {
        String value = "1".repeat(Generators.integers(1_000, 3_000).next());

        CoverageParseResult result = JacocoReportParser.parse("""
                <report name="hostile-value">
                  <counter type="LINE" missed="%s" covered="1"/>
                </report>
                """.formatted(value));

        CoverageParseResult.Refused refused = assertRefused(result, "A counter value out of range is refused");
        assertAll(
                () -> assertEquals("jacoco: ".length() + CoverageRefusals.MAX_DETAIL_LENGTH, refused.reason().length(),
                        "The echoed value is cut to the echo bound"),
                () -> assertFalse(refused.reason().contains(value), "The whole hostile value is never echoed"));
    }

    private static CoverageParseResult.Refused assertRefused(CoverageParseResult result, String message) {
        return assertInstanceOf(CoverageParseResult.Refused.class, result, message);
    }
}
