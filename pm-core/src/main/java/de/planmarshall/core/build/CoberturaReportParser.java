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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.xml.stream.XMLStreamReader;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

/**
 * The native Cobertura coverage report parser ({@code coverage.xml}, as written by {@code coverage.py} for pytest).
 * <p>
 * The reader walks the report once through {@link CoverageXmlReader} and takes the root {@code line-rate} and
 * {@code branch-rate} as the overall percentages. Per class it takes the class {@code line-rate} (and the
 * {@code branch-rate} where present) and the methods whose {@code line-rate} is zero as the {@code missed_methods}
 * detail. A missing root {@code line-rate}, a rate that is not a plain decimal number within {@code 0..1} and a document
 * that is not a {@code coverage} report are refusals; a missing {@code branch-rate} leaves the branch metric absent.
 * <p>
 * The zero rule is decided at the root. A root {@code lines-valid="0"} measured no line, so the report is refused
 * ({@code no lines measured}). A root {@code branches-valid} of {@code 0}, or none, measured no branch, so the overall
 * branch metric and the branch rate of every class are absent; under a root that measured branches, a
 * {@code branch-rate="0"} is a measured {@code 0.0}.
 * <p>
 * The XML hardening is the same as JaCoCo's: a {@code DOCTYPE} is tolerated and never resolved or fetched, and a document
 * that references an entity is refused, never expanded.
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

    private static final String LINES_VALID = "lines-valid";

    private static final String BRANCHES_VALID = "branches-valid";

    /** The refusal reason of a report whose root {@code lines-valid} is {@code 0}. */
    private static final String NO_LINES_MEASURED = "no lines measured";

    /**
     * Parses a Cobertura XML report from its text.
     *
     * @param xml the report text
     * @return the parsed report, or a refusal naming the format and the reason
     */
    public static CoverageParseResult parse(String xml) {
        Objects.requireNonNull(xml, "xml");
        return CoverageXmlReader.parse(xml, FORMAT, new Walk());
    }

    /**
     * Parses a Cobertura XML report from a file, streaming it within the bound of {@link CoverageReportStream}.
     *
     * @param reportFile the report file
     * @return the parsed report, or a refusal naming the format and the reason; a file that cannot be read is refused
     *         naming its file name only
     */
    public static CoverageParseResult parse(Path reportFile) {
        Objects.requireNonNull(reportFile, "reportFile");
        try (InputStream source = new CoverageReportStream(Files.newInputStream(reportFile))) {
            return CoverageXmlReader.parse(source, FORMAT, new Walk());
        } catch (IOException e) {
            return CoverageRefusals.of(FORMAT, reportFile, e);
        }
    }

    /**
     * The state of one walk over a report.
     */
    private static final class Walk implements CoverageXmlReader.Handler {

        private final List<CoverageItem> items = new ArrayList<>();
        private final Deque<String> path = new ArrayDeque<>();
        private ClassState current = ClassState.NONE;
        private boolean rootSeen;
        private double lineRate;
        private @Nullable Double branchRate;
        private boolean branchesMeasured;

        @Override
        public void start(XMLStreamReader reader) {
            String local = reader.getLocalName();
            if (path.isEmpty() && !COVERAGE.equals(local)) {
                throw new IllegalArgumentException("the root element is " + local + ", not a coverage");
            }
            if (COVERAGE.equals(local)) {
                readRoot(reader);
            } else if (CLASS.equals(local)) {
                if (current != ClassState.NONE) {
                    throw new IllegalArgumentException("a class is nested in another class");
                }
                current = new ClassState(requiredName(reader), file(reader).orElse(null),
                        CoveragePercentages.percentageOfRate(requireRate(reader, LINE_RATE)), branchPercentage(reader));
            } else if (METHOD.equals(local) && current != ClassState.NONE) {
                current.readMethod(CoverageXmlReader.attribute(reader, NAME), rate(reader, LINE_RATE).orElse(null));
            }
            path.push(local);
        }

        @Override
        public void end(XMLStreamReader reader) {
            String local = reader.getLocalName();
            path.pop();
            if (CLASS.equals(local) && current != ClassState.NONE) {
                items.add(current.item());
                current = ClassState.NONE;
            }
        }

        @Override
        public CoverageParseResult result() {
            if (!rootSeen) {
                throw new IllegalArgumentException("the report carries no coverage root");
            }
            CoverageMetrics metrics = CoverageMetrics.builder()
                    .line(lineRate)
                    .branch(branchRate)
                    .build();
            return new CoverageParseResult.Parsed(new CoverageReport(FORMAT, metrics, items));
        }

        /**
         * Reads the root, where the zero rule is decided: {@code lines-valid="0"} refuses the report, and a
         * {@code branches-valid} of {@code 0} or none makes every branch metric of the report absent.
         *
         * @param reader the reader positioned on the root
         */
        private void readRoot(XMLStreamReader reader) {
            if (path.isEmpty()) {
                rootSeen = true;
                if (count(reader, LINES_VALID).orElse(-1L) == 0) {
                    throw new IllegalArgumentException(NO_LINES_MEASURED);
                }
                lineRate = CoveragePercentages.percentageOfRate(requireRate(reader, LINE_RATE));
                branchesMeasured = count(reader, BRANCHES_VALID).orElse(0L) > 0;
                branchRate = branchPercentage(reader);
            }
        }

        private @Nullable Double branchPercentage(XMLStreamReader reader) {
            Optional<Double> branch = rate(reader, BRANCH_RATE).map(CoveragePercentages::percentageOfRate);
            return branchesMeasured ? branch.orElse(null) : null;
        }

        private Optional<Long> count(XMLStreamReader reader, String name) {
            String value = CoverageXmlReader.attribute(reader, name);
            if (value.isBlank()) {
                return Optional.empty();
            }
            long count = Long.parseLong(value.strip());
            if (count < 0) {
                throw new IllegalArgumentException("the " + name + " is negative: " + count);
            }
            return Optional.of(count);
        }

        private String requiredName(XMLStreamReader reader) {
            String name = CoverageXmlReader.attribute(reader, NAME);
            if (name.isBlank()) {
                throw new IllegalArgumentException("a class carries no name");
            }
            return name;
        }

        private Optional<String> file(XMLStreamReader reader) {
            String file = CoverageXmlReader.attribute(reader, FILENAME);
            return file.isBlank() ? Optional.empty() : Optional.of(file);
        }

        private Optional<String> rate(XMLStreamReader reader, String name) {
            String value = CoverageXmlReader.attribute(reader, name);
            return value.isBlank() ? Optional.empty() : Optional.of(value);
        }

        private String requireRate(XMLStreamReader reader, String name) {
            return rate(reader, name)
                    .orElseThrow(() -> new IllegalArgumentException("the " + name + " is absent"));
        }
    }

    /**
     * The methods of the class being walked.
     */
    private static final class ClassState {

        private static final ClassState NONE = new ClassState("", null, 0.0, null);

        private final String name;
        private final @Nullable String file;
        private final double line;
        private final @Nullable Double branch;
        private final List<String> missedMethods = new ArrayList<>();

        private ClassState(String name, @Nullable String file, double line, @Nullable Double branch) {
            this.name = name;
            this.file = file;
            this.line = line;
            this.branch = branch;
        }

        private void readMethod(String name, @Nullable String lineRate) {
            if (lineRate == null) {
                return;
            }
            if (CoveragePercentages.rate(lineRate).signum() == 0 && !name.isBlank()) {
                missedMethods.add(name);
            }
        }

        private CoverageItem item() {
            return CoverageItem.builder()
                    .name(name)
                    .file(file)
                    .line(line)
                    .branch(branch)
                    .missedMethods(missedMethods)
                    .build();
        }
    }
}
