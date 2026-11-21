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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Each of the four coverage parsers reads a report file through {@link CoverageReportStream}: its package-private
 * {@code parse(Path, long limit)} overload is handed a limit one byte below the report's size and must refuse the read,
 * naming the limit, and the same report under a limit of its own size must parse. A parser that opened the file without
 * the stream would read the report whole and parse it under the small limit too, so removing the stream from any of
 * the four turns its case red.
 * <p>
 * Contract literals that stay: the report bodies, which are the file formats of JaCoCo, Cobertura, LCOV and Istanbul,
 * and the refusal's {@code limit of <n> bytes}, which is the reason text of the stream.
 */
@EnableGeneratorController
@DisplayName("Coverage parser stream bound")
class CoverageParserStreamBoundTest {

    @TempDir
    Path directory;

    static Stream<Arguments> parsers() {
        BiFunction<Path, Long, CoverageParseResult> jacoco = JacocoReportParser::parse;
        BiFunction<Path, Long, CoverageParseResult> cobertura = CoberturaReportParser::parse;
        BiFunction<Path, Long, CoverageParseResult> lcov = LcovReportParser::parse;
        BiFunction<Path, Long, CoverageParseResult> istanbul = IstanbulSummaryParser::parse;
        return Stream.of(
                Arguments.of("jacoco.xml", CoverageFormat.JACOCO,
                        (Supplier<String>) CoverageParserStreamBoundTest::jacoco, jacoco),
                Arguments.of("coverage.xml", CoverageFormat.COBERTURA,
                        (Supplier<String>) CoverageParserStreamBoundTest::cobertura, cobertura),
                Arguments.of("lcov.info", CoverageFormat.LCOV, (Supplier<String>) CoverageParserStreamBoundTest::lcov,
                        lcov),
                Arguments.of("coverage-summary.json", CoverageFormat.ISTANBUL,
                        (Supplier<String>) CoverageParserStreamBoundTest::istanbul, istanbul));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("parsers")
    @DisplayName("refuses a report one byte past the limit it is handed")
    void refusesAReportPastTheLimit(String fileName, CoverageFormat format, Supplier<String> report,
            BiFunction<Path, Long, CoverageParseResult> parse) throws IOException {
        Path file = write(fileName, report.get());
        long limit = Files.size(file) - 1;

        CoverageParseResult result = parse.apply(file, limit);

        var refused = assertInstanceOf(CoverageParseResult.Refused.class, result, "a report past the limit is refused");
        assertAll(
                () -> assertEquals(format, refused.format(), "the refusal names the parser's format"),
                () -> assertTrue(refused.reason().contains("limit of " + limit + " bytes"),
                        "the refusal names the stream's byte limit"));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("parsers")
    @DisplayName("parses the same report under a limit of its own size")
    void parsesAReportAtTheLimit(String fileName, CoverageFormat format, Supplier<String> report,
            BiFunction<Path, Long, CoverageParseResult> parse) throws IOException {
        Path file = write(fileName, report.get());

        CoverageParseResult result = parse.apply(file, Files.size(file));

        var parsed = assertInstanceOf(CoverageParseResult.Parsed.class, result, "a report at the limit is parsed");
        assertEquals(format, parsed.report().format(), "the parsed report names the parser's format");
    }

    private Path write(String name, String content) throws IOException {
        return Files.writeString(directory.resolve(name), content, StandardCharsets.UTF_8);
    }

    private static int covered() {
        return Generators.integers(1, 1000).next();
    }

    private static String jacoco() {
        return """
                <report name="bound"><counter type="LINE" missed="%d" covered="%d"/></report>
                """.formatted(Generators.integers(0, 1000).next(), covered());
    }

    private static String cobertura() {
        return """
                <coverage lines-valid="%d" line-rate="0.5"/>
                """.formatted(covered());
    }

    private static String lcov() {
        var found = covered();
        return """
                SF:src/a.js
                LF:%d
                LH:%d
                end_of_record
                """.formatted(found, Generators.integers(0, found).next());
    }

    private static String istanbul() {
        var total = covered();
        var metric = "{\"total\": %d, \"covered\": %d, \"pct\": 50}".formatted(total,
                Generators.integers(0, total).next());
        return """
                {"total": {"lines": %1$s, "statements": %1$s, "functions": %1$s, "branches": %1$s}}
                """.formatted(metric);
    }
}
