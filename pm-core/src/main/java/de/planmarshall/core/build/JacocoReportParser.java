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
import java.util.Optional;
import javax.xml.stream.XMLStreamReader;
import lombok.experimental.UtilityClass;

/**
 * The native JaCoCo coverage report parser ({@code jacoco.xml}).
 * <p>
 * The reader walks the report once through {@link CoverageXmlReader} and takes the {@code INSTRUCTION}, {@code BRANCH},
 * {@code LINE} and {@code METHOD} counters of the report element as the overall percentages, each
 * {@code covered / (missed + covered)}. A counter the report does not carry leaves its metric absent, and a report
 * without a {@code LINE} counter is refused. Per class it takes the {@code LINE} counter (and the {@code BRANCH} counter
 * where the class carries one) and the methods whose {@code METHOD} counter has a missed part as the {@code missed_methods}
 * detail. The constructors {@code <init>} and {@code <clinit>} are never part of that detail, because they are not
 * methods a test is expected to call.
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
        return CoverageXmlReader.parse(xml, FORMAT, new Walk());
    }

    /**
     * Parses a JaCoCo XML report from a file.
     *
     * @param reportFile the report file
     * @return the parsed report, or a refusal naming the format and the reason
     */
    public static CoverageParseResult parse(Path reportFile) {
        Objects.requireNonNull(reportFile, "reportFile");
        try {
            return CoverageXmlReader.parse(Files.readAllBytes(reportFile), FORMAT, new Walk());
        } catch (IOException e) {
            return CoverageRefusals.of(FORMAT, e);
        }
    }

    /**
     * The state of one walk over a report.
     */
    private static final class Walk implements CoverageXmlReader.Handler {

        private final Map<String, int[]> overall = new HashMap<>();
        private final List<CoverageItem> items = new ArrayList<>();
        private final Deque<String> path = new ArrayDeque<>();
        private ClassState current = ClassState.NONE;

        @Override
        public void start(XMLStreamReader reader) {
            String parent = path.peek();
            String local = reader.getLocalName();
            if (path.isEmpty() && !REPORT.equals(local)) {
                throw new IllegalArgumentException("the root element is " + local + ", not a report");
            }
            if (CLASS.equals(local)) {
                current = new ClassState(requiredName(reader));
            } else if (METHOD.equals(local) && current != ClassState.NONE) {
                current.startMethod(CoverageXmlReader.attribute(reader, NAME));
            } else if (COUNTER.equals(local)) {
                counter(reader, parent);
            }
            path.push(local);
        }

        @Override
        public void end(XMLStreamReader reader) {
            String local = reader.getLocalName();
            path.pop();
            if (METHOD.equals(local) && current != ClassState.NONE) {
                current.endMethod();
            } else if (CLASS.equals(local) && current != ClassState.NONE) {
                items.add(current.item());
                current = ClassState.NONE;
            }
        }

        @Override
        public CoverageParseResult result() {
            if (!overall.containsKey(LINE)) {
                throw new IllegalArgumentException("the report carries no LINE counter");
            }
            CoverageMetrics metrics = CoverageMetrics.builder()
                    .line(percentage(LINE))
                    .instruction(optionalPercentage(INSTRUCTION).orElse(null))
                    .branch(optionalPercentage(BRANCH).orElse(null))
                    .function(optionalPercentage(METHOD_COUNTER).orElse(null))
                    .build();
            return new CoverageParseResult.Parsed(new CoverageReport(FORMAT, metrics, items));
        }

        private void counter(XMLStreamReader reader, String parent) {
            String type = CoverageXmlReader.attribute(reader, "type");
            int missed = count(reader, "missed");
            int covered = count(reader, "covered");
            if (REPORT.equals(parent)) {
                overall.put(type, new int[]{missed, covered});
            } else if (current != ClassState.NONE) {
                current.readCounter(parent, type, missed, covered);
            }
        }

        private double percentage(String type) {
            int[] counter = overall.get(type);
            return CoveragePercentages.percentage(counter[1], counter[0] + (long) counter[1]);
        }

        private Optional<Double> optionalPercentage(String type) {
            int[] counter = overall.get(type);
            return counter == null
                    ? Optional.empty()
                    : Optional.of(CoveragePercentages.percentage(counter[1], counter[0] + (long) counter[1]));
        }

        private String requiredName(XMLStreamReader reader) {
            String name = CoverageXmlReader.attribute(reader, NAME);
            if (name.isBlank()) {
                throw new IllegalArgumentException("a class carries no name");
            }
            return name;
        }

        private int count(XMLStreamReader reader, String name) {
            String value = CoverageXmlReader.attribute(reader, name);
            return value.isBlank() ? 0 : Integer.parseInt(value);
        }
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
            if (CLASS.equals(parent) && LINE.equals(type)) {
                lineMissed = missed;
                lineCovered = covered;
            } else if (CLASS.equals(parent) && BRANCH.equals(type)) {
                branchMissed = missed;
                branchCovered = covered;
            } else if (METHOD.equals(parent) && METHOD_COUNTER.equals(type)) {
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
