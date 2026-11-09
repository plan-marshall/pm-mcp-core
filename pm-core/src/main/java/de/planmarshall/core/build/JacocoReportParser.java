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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import lombok.experimental.UtilityClass;

/**
 * The native JaCoCo coverage report parser ({@code jacoco.xml}).
 * <p>
 * The reader walks the report once with the hardened StAX reader of {@link CoverageXmlReader} and takes the
 * {@code INSTRUCTION}, {@code BRANCH}, {@code LINE} and {@code METHOD} counters of the report element as the overall
 * percentages, each {@code covered / (missed + covered)}. Per class it takes the {@code LINE} counter (and the
 * {@code BRANCH} counter where the class carries one) and the methods whose {@code METHOD} counter has a missed part as
 * the {@code missed_methods} detail. The constructors {@code <init>} and {@code <clinit>} are never part of that detail,
 * because they are not methods a test is expected to call.
 * <p>
 * JaCoCo writes a {@code DOCTYPE} with a system identifier, so the reader tolerates the declaration without resolving it;
 * a document with an external entity is refused, never expanded.
 *
 * @since 0.1
 */
@UtilityClass
public class JacocoReportParser {

    private static final CoverageFormat FORMAT = CoverageFormat.JACOCO;

    /**
     * Parses a JaCoCo XML report from its text.
     *
     * @param xml the report text
     * @return the parsed report, or a refusal naming the format and the reason
     */
    public static CoverageParseResult parse(String xml) {
        Objects.requireNonNull(xml, "xml");
        try (CoverageXmlReader.Reader source = CoverageXmlReader.open(xml)) {
            return parse(source.stream());
        } catch (XMLStreamException | IllegalArgumentException e) {
            return refusal(e);
        }
    }

    /**
     * Parses a JaCoCo XML report from a file.
     *
     * @param reportFile the report file
     * @return the parsed report, or a refusal naming the format and the reason
     */
    public static CoverageParseResult parse(Path reportFile) {
        Objects.requireNonNull(reportFile, "reportFile");
        try (CoverageXmlReader.Reader source = CoverageXmlReader.open(Files.readAllBytes(reportFile))) {
            return parse(source.stream());
        } catch (IOException | XMLStreamException | IllegalArgumentException e) {
            return refusal(e);
        }
    }

    private static CoverageParseResult parse(XMLStreamReader reader) throws XMLStreamException {
        Map<String, int[]> overall = new HashMap<>();
        List<CoverageItem> items = new ArrayList<>();
        Deque<String> path = new ArrayDeque<>();
        ClassState current = null;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.ENTITY_REFERENCE) {
                return new CoverageParseResult.Refused(FORMAT, FORMAT.id() + ": external entity reference refused");
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                String parent = path.peek();
                String local = reader.getLocalName();
                if ("class".equals(local)) {
                    current = new ClassState(requiredName(reader, "class"));
                } else if ("method".equals(local)) {
                    if (current != null) {
                        current.startMethod(attribute(reader, "name"));
                    }
                } else if ("counter".equals(local)) {
                    readCounter(reader, parent, overall, current);
                }
                path.push(local);
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                String local = reader.getLocalName();
                path.pop();
                if ("method".equals(local)) {
                    if (current != null) {
                        current.endMethod();
                    }
                } else if ("class".equals(local)) {
                    if (current != null) {
                        items.add(current.item());
                        current = null;
                    }
                }
            }
        }
        return new CoverageParseResult.Parsed(new CoverageReport(FORMAT, metrics(overall), items));
    }

    private static CoverageMetrics metrics(Map<String, int[]> overall) {
        return CoverageMetrics.builder()
                .instruction(percentage(overall, "INSTRUCTION"))
                .branch(percentage(overall, "BRANCH"))
                .line(percentage(overall, "LINE"))
                .function(percentage(overall, "METHOD"))
                .build();
    }

    private static double percentage(Map<String, int[]> counters, String type) {
        int[] counter = counters.get(type);
        return counter == null ? 0.0 : CoveragePercentages.percentage(counter[1], counter[0] + (long) counter[1]);
    }

    private static void readCounter(XMLStreamReader reader, String parent, Map<String, int[]> overall,
            ClassState current) {
        String type = attribute(reader, "type");
        int missed = count(reader, "missed");
        int covered = count(reader, "covered");
        if ("report".equals(parent)) {
            overall.put(type, new int[]{missed, covered});
        } else if (current != null) {
            current.readCounter(parent, type, missed, covered);
        }
    }

    private static String requiredName(XMLStreamReader reader, String element) {
        String name = attribute(reader, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("a " + element + " carries no name");
        }
        return name;
    }

    private static String attribute(XMLStreamReader reader, String name) {
        String value = reader.getAttributeValue(null, name);
        return value == null ? "" : value;
    }

    private static int count(XMLStreamReader reader, String name) {
        String value = attribute(reader, name);
        return value.isBlank() ? 0 : Integer.parseInt(value);
    }

    private static CoverageParseResult refusal(Exception cause) {
        String message = cause.getMessage();
        String detail = message == null ? cause.getClass().getSimpleName() : message;
        return new CoverageParseResult.Refused(FORMAT, FORMAT.id() + ": " + detail);
    }

    /**
     * The counters and methods of the class being walked.
     */
    private static final class ClassState {

        private final String name;
        private final List<String> missedMethods = new ArrayList<>();
        private int lineMissed;
        private int lineCovered;
        private int branchMissed = -1;
        private int branchCovered;
        private String methodName = "";
        private int methodMissed;

        private ClassState(String name) {
            this.name = name;
        }

        private void startMethod(String name) {
            this.methodName = name;
            this.methodMissed = 0;
        }

        private void endMethod() {
            if (methodMissed > 0 && !"<init>".equals(methodName) && !"<clinit>".equals(methodName)) {
                missedMethods.add(methodName);
            }
            methodName = "";
            methodMissed = 0;
        }

        private void readCounter(String parent, String type, int missed, int covered) {
            if ("class".equals(parent) && "LINE".equals(type)) {
                lineMissed = missed;
                lineCovered = covered;
            } else if ("class".equals(parent) && "BRANCH".equals(type)) {
                branchMissed = missed;
                branchCovered = covered;
            } else if ("method".equals(parent) && "METHOD".equals(type)) {
                methodMissed = missed;
            }
        }

        private CoverageItem item() {
            return CoverageItem.builder()
                    .name(name.replace('/', '.'))
                    .line(CoveragePercentages.percentage(lineCovered, lineMissed + (long) lineCovered))
                    .branch(branchMissed < 0
                            ? null
                            : CoveragePercentages.percentage(branchCovered, branchMissed + (long) branchCovered))
                    .missedMethods(missedMethods)
                    .build();
        }
    }
}
