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
import org.jspecify.annotations.Nullable;

/**
 * The native JaCoCo coverage report parser ({@code jacoco.xml}).
 * <p>
 * The reader walks the report once with the hardened StAX reader of {@link CoverageXmlReader} and takes the
 * {@code INSTRUCTION}, {@code BRANCH}, {@code LINE} and {@code METHOD} counters of the report element as the overall
 * percentages, each {@code covered / (missed + covered)}. A counter the report does not carry leaves its metric absent,
 * and a report without a {@code LINE} counter is refused. Per class it takes the {@code LINE} counter (and the
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

    private static final String REPORT = "report";

    private static final String CLASS = "class";

    private static final String METHOD = "method";

    private static final String COUNTER = "counter";

    private static final String LINE = "LINE";

    private static final String BRANCH = "BRANCH";

    private static final String INSTRUCTION = "INSTRUCTION";

    private static final String METHOD_COUNTER = "METHOD";

    private static final String NAME = "name";

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
        Walk walk = new Walk();
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.ENTITY_REFERENCE) {
                return walk.entityRefused();
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                walk.start(reader);
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                walk.end(reader);
            }
        }
        return walk.result();
    }

    private static CoverageParseResult refusal(Exception cause) {
        return CoverageRefusals.of(FORMAT, cause);
    }

    /**
     * The state of one walk over a report.
     */
    private static final class Walk {

        private final Map<String, int[]> overall = new HashMap<>();
        private final List<CoverageItem> items = new ArrayList<>();
        private final Deque<String> path = new ArrayDeque<>();
        private ClassState current = ClassState.NONE;

        private void start(XMLStreamReader reader) {
            String parent = path.peek();
            String local = reader.getLocalName();
            if (path.isEmpty() && !REPORT.equals(local)) {
                throw new IllegalArgumentException("the root element is " + local + ", not a report");
            }
            switch (local) {
                case CLASS -> current = new ClassState(requiredName(reader));
                case METHOD -> current.startMethod(CoverageXmlReader.attribute(reader, NAME));
                case COUNTER -> counter(reader, parent);
                default -> {
                }
            }
            path.push(local);
        }

        private void end(XMLStreamReader reader) {
            String local = reader.getLocalName();
            path.pop();
            if (METHOD.equals(local)) {
                current.endMethod();
            } else if (CLASS.equals(local) && current != ClassState.NONE) {
                items.add(current.item());
                current = ClassState.NONE;
            }
        }

        private void counter(XMLStreamReader reader, String parent) {
            String type = CoverageXmlReader.attribute(reader, "type");
            int missed = count(reader, "missed");
            int covered = count(reader, "covered");
            if (REPORT.equals(parent)) {
                overall.put(type, new int[]{missed, covered});
            } else {
                current.readCounter(parent, type, missed, covered);
            }
        }

        private CoverageParseResult entityRefused() {
            return new CoverageParseResult.Refused(FORMAT, FORMAT.id() + ": external entity reference refused");
        }

        private CoverageParseResult result() {
            if (!overall.containsKey(LINE)) {
                throw new IllegalArgumentException("the report carries no LINE counter");
            }
            CoverageMetrics metrics = CoverageMetrics.builder()
                    .line(percentage(LINE))
                    .instruction(optionalPercentage(INSTRUCTION))
                    .branch(optionalPercentage(BRANCH))
                    .function(optionalPercentage(METHOD_COUNTER))
                    .build();
            return new CoverageParseResult.Parsed(new CoverageReport(FORMAT, metrics, items));
        }

        private double percentage(String type) {
            int[] counter = overall.get(type);
            return CoveragePercentages.percentage(counter[1], counter[0] + (long) counter[1]);
        }

        private @Nullable Double optionalPercentage(String type) {
            int[] counter = overall.get(type);
            return counter == null ? null : CoveragePercentages.percentage(counter[1], counter[0] + (long) counter[1]);
        }
    }

    private static String requiredName(XMLStreamReader reader) {
        String name = CoverageXmlReader.attribute(reader, NAME);
        if (name.isBlank()) {
            throw new IllegalArgumentException("a class carries no name");
        }
        return name;
    }

    private static int count(XMLStreamReader reader, String name) {
        String value = CoverageXmlReader.attribute(reader, name);
        return value.isBlank() ? 0 : Integer.parseInt(value);
    }

    /**
     * The counters and methods of the class being walked.
     */
    private static final class ClassState {

        private static final ClassState NONE = new ClassState("");

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
            methodName = name;
            methodMissed = 0;
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
