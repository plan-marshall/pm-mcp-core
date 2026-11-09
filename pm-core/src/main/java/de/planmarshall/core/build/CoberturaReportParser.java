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
import java.util.List;
import java.util.Objects;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

/**
 * The native Cobertura coverage report parser ({@code coverage.xml}, as written by {@code coverage.py} for pytest).
 * <p>
 * The reader walks the report once with the hardened StAX reader of {@link CoverageXmlReader} and takes the root
 * {@code line-rate} and {@code branch-rate} as the overall percentages. Per class it takes the class {@code line-rate} (and
 * the {@code branch-rate} where present) and the methods whose {@code line-rate} is zero as the {@code missed_methods}
 * detail. A missing root {@code line-rate}, a rate that is not a finite number and a document that is not a
 * {@code coverage} report are refusals; a missing {@code branch-rate} leaves the branch metric absent.
 * <p>
 * The XML hardening is the same as JaCoCo's: a DTD declaration is tolerated without resolution, and an external entity is
 * refused, never expanded.
 *
 * @since 0.1
 */
@UtilityClass
public class CoberturaReportParser {

    private static final CoverageFormat FORMAT = CoverageFormat.COBERTURA;

    private static final String COVERAGE = "coverage";

    private static final String CLASS = "class";

    private static final String METHOD = "method";

    private static final String LINE_RATE = "line-rate";

    private static final String BRANCH_RATE = "branch-rate";

    private static final String NAME = "name";

    private static final String FILENAME = "filename";

    /**
     * Parses a Cobertura XML report from its text.
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
     * Parses a Cobertura XML report from a file.
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

        private final List<CoverageItem> items = new ArrayList<>();
        private final Deque<String> path = new ArrayDeque<>();
        private ClassState current = ClassState.NONE;
        private boolean rootSeen;
        private double lineRate;
        private @Nullable Double branchRate;

        private void start(XMLStreamReader reader) {
            String local = reader.getLocalName();
            if (path.isEmpty() && !COVERAGE.equals(local)) {
                throw new IllegalArgumentException("the root element is " + local + ", not a coverage");
            }
            switch (local) {
                case COVERAGE -> readRoot(reader);
                case CLASS -> current = new ClassState(requiredName(reader), file(reader),
                        requireRate(reader, LINE_RATE), rate(reader, BRANCH_RATE));
                case METHOD -> current.readMethod(CoverageXmlReader.attribute(reader, NAME),
                        rate(reader, LINE_RATE));
                default -> {
                }
            }
            path.push(local);
        }

        private void readRoot(XMLStreamReader reader) {
            if (path.isEmpty()) {
                rootSeen = true;
                lineRate = requireRate(reader, LINE_RATE);
                branchRate = rate(reader, BRANCH_RATE);
            }
        }

        private void end(XMLStreamReader reader) {
            String local = reader.getLocalName();
            path.pop();
            if (CLASS.equals(local) && current != ClassState.NONE) {
                items.add(current.item());
                current = ClassState.NONE;
            }
        }

        private CoverageParseResult entityRefused() {
            return new CoverageParseResult.Refused(FORMAT, FORMAT.id() + ": external entity reference refused");
        }

        private CoverageParseResult result() {
            if (!rootSeen) {
                throw new IllegalArgumentException("the report carries no coverage root");
            }
            CoverageMetrics metrics = CoverageMetrics.builder()
                    .line(CoveragePercentages.percentageOfRate(lineRate))
                    .branch(branchRate == null ? null : CoveragePercentages.percentageOfRate(branchRate))
                    .build();
            return new CoverageParseResult.Parsed(new CoverageReport(FORMAT, metrics, items));
        }
    }

    private static String requiredName(XMLStreamReader reader) {
        String name = CoverageXmlReader.attribute(reader, NAME);
        if (name.isBlank()) {
            throw new IllegalArgumentException("a class carries no name");
        }
        return name;
    }

    private static @Nullable String file(XMLStreamReader reader) {
        String file = CoverageXmlReader.attribute(reader, FILENAME);
        return file.isBlank() ? null : file;
    }

    private static @Nullable Double rate(XMLStreamReader reader, String name) {
        String value = CoverageXmlReader.attribute(reader, name);
        return value.isBlank() ? null : Double.valueOf(value);
    }

    private static double requireRate(XMLStreamReader reader, String name) {
        Double value = rate(reader, name);
        if (value == null) {
            throw new IllegalArgumentException("the " + name + " is absent");
        }
        return value;
    }

    /**
     * The methods of the class being walked.
     */
    private static final class ClassState {

        private static final ClassState NONE = new ClassState("", null, 0.0, null);

        private final String name;
        private final @Nullable String file;
        private final double lineRate;
        private final @Nullable Double branchRate;
        private final List<String> missedMethods = new ArrayList<>();

        private ClassState(String name, @Nullable String file, double lineRate, @Nullable Double branchRate) {
            this.name = name;
            this.file = file;
            this.lineRate = lineRate;
            this.branchRate = branchRate;
        }

        private void readMethod(String name, @Nullable Double lineRate) {
            if (lineRate != null && lineRate == 0.0 && !name.isBlank()) {
                missedMethods.add(name);
            }
        }

        private CoverageItem item() {
            return CoverageItem.builder()
                    .name(name)
                    .file(file)
                    .line(CoveragePercentages.percentageOfRate(lineRate))
                    .branch(branchRate == null ? null : CoveragePercentages.percentageOfRate(branchRate))
                    .missedMethods(missedMethods)
                    .build();
        }
    }
}
