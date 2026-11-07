/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.analysis;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.SchemaResource;
import de.planmarshall.core.analysis.AnalysisResult.Comparator;
import de.planmarshall.core.analysis.AnalysisResult.Condition;
import de.planmarshall.core.analysis.AnalysisResult.CountStatus;
import de.planmarshall.core.analysis.AnalysisResult.Gate;
import de.planmarshall.core.analysis.AnalysisResult.GateResult;
import de.planmarshall.core.findings.FindingScope;

/**
 * Every line {@link AnalysisResultCodec} writes is valid against the schema {@code AnalysisResult} of Store Schemas
 * § Analysis Result (PM-IMPL-2). The schema is the file {@code de/planmarshall/core/analysis/analysis-result.schema.json}
 * beside the codec, read from the class path.
 */
@EnableGeneratorController
@DisplayName("Analysis result schema")
class AnalysisResultSchemaTest {

    // The class-path location of the schema file is a contract.
    private static final String SCHEMA = "de/planmarshall/core/analysis/analysis-result.schema.json";
    private static final String SHA_1 = "%040x".formatted(Generators.longs().next());
    private static final String SHA_256 = "%064x".formatted(Generators.longs().next());
    private static final Instant AT = Instant.ofEpochSecond(Generators.longs(1_600_000_000L, 1_900_000_000L).next());
    private static final String PROVIDER = "p" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
    private static final String OTHER_PROVIDER = "p" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
    private static final String ANALYSIS_REF = Generators.letterStrings(5, 20).next();
    private static final int PULL_REQUEST = Generators.integers(1, 10_000).next();

    private static JsonSchema schema;

    @BeforeAll
    static void readSchema() {
        schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(SchemaResource.text(SCHEMA));
    }

    static Stream<Arguments> fixtures() {
        var failedGate = new Gate(GateResult.FAILED, List.of(
                new Condition("new_coverage", Comparator.LT, "80", "71.4", GateResult.FAILED),
                new Condition("new_duplicated_lines", Comparator.GE, "3", null, GateResult.PASSED)));
        return Stream.of(
                Arguments.of("passed commit without gate", new AnalysisResult(1, PROVIDER, FindingScope.commit(SHA_1),
                        AnalysisStatus.PASSED, null, CountStatus.MEASURED, 0L, 0, false, ANALYSIS_REF, AT)),
                Arguments.of("passed pull request with passed gate", new AnalysisResult(1, PROVIDER,
                        FindingScope.pullRequest(SHA_256, PULL_REQUEST), AnalysisStatus.PASSED,
                        new Gate(GateResult.PASSED, List.of()), CountStatus.MEASURED, 0L, 0, false, null, AT)),
                Arguments.of("gate_failed pull request with conditions", new AnalysisResult(1, OTHER_PROVIDER,
                        FindingScope.pullRequest(SHA_1, PULL_REQUEST + 1), AnalysisStatus.GATE_FAILED, failedGate,
                        CountStatus.MEASURED, 3L, 3, true, ANALYSIS_REF + "-2", AT)),
                Arguments.of("findings commit", new AnalysisResult(1, PROVIDER, FindingScope.commit(SHA_1),
                        AnalysisStatus.FINDINGS, null, CountStatus.MEASURED, 5L, 5, true, ANALYSIS_REF + "-3", AT)),
                Arguments.of("undecidable with indeterminate count", new AnalysisResult(1, PROVIDER,
                        FindingScope.commit(SHA_1), AnalysisStatus.UNDECIDABLE, null, CountStatus.INDETERMINATE,
                        null, 0, false, null, AT)),
                Arguments.of("running", new AnalysisResult(1, PROVIDER, FindingScope.commit(SHA_1),
                        AnalysisStatus.RUNNING, null, CountStatus.INDETERMINATE, null, 0, false, null, AT)),
                Arguments.of("not_run", new AnalysisResult(1, PROVIDER, FindingScope.commit(SHA_1),
                        AnalysisStatus.NOT_RUN, null, CountStatus.INDETERMINATE, null, 0, false, null, AT)));
    }

    private static Set<?> validate(String line) {
        return schema.validate(line, InputFormat.JSON);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    @DisplayName("a written line is valid against the schema of the specification")
    void writtenLineIsValid(String name, AnalysisResult result) {
        var line = new String(AnalysisResultCodec.write(result), UTF_8);

        assertEquals(Set.of(), validate(line), () -> "violations of " + line);
    }

    @Test
    @DisplayName("the validator rejects a line with an undeclared member, so a valid line is a real verdict")
    void validatorRejectsUndeclaredMember() {
        var line = new String(AnalysisResultCodec.write(new AnalysisResult(1, PROVIDER, FindingScope.commit(SHA_1),
                AnalysisStatus.PASSED, null, CountStatus.MEASURED, 0L, 0, false, null, AT)), UTF_8);
        var extended = line.replace("\"gate\":null", "\"gate\":null,\"extra\":1");

        assertFalse(validate(extended).isEmpty(), "an undeclared member must violate the schema");
    }
}
