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

/**
 * The native Cobertura coverage report parser ({@code coverage.xml}, as written by {@code coverage.py} for pytest).
 * <p>
 * The reader walks the report once with the hardened StAX reader of {@link CoverageXmlReader} and takes the root
 * {@code line-rate} and {@code branch-rate} as the overall percentages. Per class it takes the class {@code line-rate} (and
 * the {@code branch-rate} where present) and the methods whose {@code line-rate} is zero as the {@code missed_methods}
 * detail.
 * <p>
 * The XML hardening is the same as JaCoCo's: a DTD declaration is tolerated without resolution, and an external entity is
 * refused, never expanded.
 *
 * @since 0.1
 */
@UtilityClass
public class CoberturaReportParser {

    private static final CoverageFormat FORMAT = CoverageFormat.COBERTURA;

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
        Double lineRate = null;
        Double branchRate = null;
        List<CoverageItem> items = new ArrayList<>();
        Deque<String> path = new ArrayDeque<>();
        ClassState current = null;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.ENTITY_REFERENCE) {
                return new CoverageParseResult.Refused(FORMAT, FORMAT.id() + ": external entity reference refused");
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                String local = reader.getLocalName();
                if ("coverage".equals(local) && path.isEmpty()) {
                    lineRate = rate(reader, "line-rate");
                    branchRate = rate(reader, "branch-rate");
                } else if ("class".equals(local)) {
                    current = new ClassState(requiredName(reader), file(reader), rate(reader, "line-rate"),
                            rate(reader, "branch-rate"));
                } else if ("method".equals(local)) {
                    if (current != null) {
                        current.readMethod(attribute(reader, "name"), rate(reader, "line-rate"));
                    }
                }
                path.push(local);
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                String local = reader.getLocalName();
                path.pop();
                if ("class".equals(local) && current != null) {
                    items.add(current.item());
                    current = null;
                }
            }
        }
        CoverageMetrics overall = CoverageMetrics.builder()
                .line(CoveragePercentages.percentageOfRate(lineRate == null ? 0.0 : lineRate))
                .branch(CoveragePercentages.percentageOfRate(branchRate == null ? 0.0 : branchRate))
                .build();
        return new CoverageParseResult.Parsed(new CoverageReport(FORMAT, overall, items));
    }

    private static String requiredName(XMLStreamReader reader) {
        String name = attribute(reader, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("a class carries no name");
        }
        return name;
    }

    private static String file(XMLStreamReader reader) {
        String file = attribute(reader, "filename");
        return file.isBlank() ? null : file;
    }

    private static Double rate(XMLStreamReader reader, String name) {
        String value = attribute(reader, name);
        return value.isBlank() ? null : Double.valueOf(value);
    }

    private static String attribute(XMLStreamReader reader, String name) {
        String value = reader.getAttributeValue(null, name);
        return value == null ? "" : value;
    }

    private static CoverageParseResult refusal(Exception cause) {
        String message = cause.getMessage();
        String detail = message == null ? cause.getClass().getSimpleName() : message;
        return new CoverageParseResult.Refused(FORMAT, FORMAT.id() + ": " + detail);
    }

    /**
     * The methods of the class being walked.
     */
    private static final class ClassState {

        private final String name;
        private final String file;
        private final Double lineRate;
        private final Double branchRate;
        private final List<String> missedMethods = new ArrayList<>();

        private ClassState(String name, String file, Double lineRate, Double branchRate) {
            this.name = name;
            this.file = file;
            this.lineRate = lineRate;
            this.branchRate = branchRate;
        }

        private void readMethod(String name, Double lineRate) {
            if (lineRate != null && lineRate == 0.0 && !name.isBlank()) {
                missedMethods.add(name);
            }
        }

        private CoverageItem item() {
            return CoverageItem.builder()
                    .name(name)
                    .file(file)
                    .line(CoveragePercentages.percentageOfRate(lineRate == null ? 0.0 : lineRate))
                    .branch(branchRate == null ? null : CoveragePercentages.percentageOfRate(branchRate))
                    .missedMethods(missedMethods)
                    .build();
        }
    }
}
