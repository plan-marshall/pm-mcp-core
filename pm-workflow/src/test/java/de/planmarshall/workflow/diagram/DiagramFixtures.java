/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.diagram;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.spi.AwaitedEvent;
import de.planmarshall.core.spi.FreeParam;
import de.planmarshall.core.spi.OutcomeClass;
import de.planmarshall.core.spi.Primitive;
import de.planmarshall.core.spi.PrimitiveOutcome;
import de.planmarshall.core.spi.PrimitiveRegistry;
import de.planmarshall.core.spi.PrimitiveResult;
import de.planmarshall.core.spi.WorkflowExecutionContext;
import de.planmarshall.workflow.ast.UnitOrigin;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.dsl.ParseResult;
import de.planmarshall.workflow.dsl.WorkflowParser;
import de.planmarshall.workflow.verify.EntityKind;
import de.planmarshall.workflow.verify.EntityKindRegistry;
import de.planmarshall.workflow.verify.QuestionClassRegistry;
import de.planmarshall.workflow.verify.SchemaRegistry;
import de.planmarshall.workflow.verify.WorkflowCatalog;
import de.planmarshall.workflow.verify.WorkflowValidator;

/**
 * Reads the fixture of the diagram tests from the test classpath: the unit {@code diagram/diagram-fixture.pmwf},
 * written for these tests and never copied from a unit of the product, and its golden files
 * {@code diagram/diagram-fixture.mmd}, {@code .adoc} and {@code .txt}, read as UTF-8.
 * <p>
 * The fixture is parsed and checked by the static verifier before a test renders it, so the golden files show a unit
 * the generator is meant for. The primitives, the entity kind {@code change} and the called sub-workflow
 * {@code diagram.callee} it names are registered here, because the helpers of the verifier tests are package-private
 * to their package.
 */
final class DiagramFixtures {

    /** The classpath resource of the fixture unit. */
    static final String FIXTURE = "diagram/diagram-fixture.pmwf";

    /** The classpath resource of the Mermaid golden file, rendered without an active state. */
    static final String MERMAID_GOLDEN = "diagram/diagram-fixture.mmd";

    /** The classpath resource of the AsciiDoc golden file. */
    static final String ASCIIDOC_GOLDEN = "diagram/diagram-fixture.adoc";

    /** The classpath resource of the terminal golden file, rendered without an active state. */
    static final String ASCII_GOLDEN = "diagram/diagram-fixture.txt";

    private DiagramFixtures() {
    }

    /**
     * Reads, parses and verifies the fixture unit.
     *
     * @return the tree of the fixture, which passed every check of the static verifier
     */
    static WorkflowDefinition fixture() {
        var definition = parse(FIXTURE, read(FIXTURE));
        var result = validator().verify(definition);
        assertTrue(result.passed(), () -> FIXTURE + " was refused by the verifier: " + result);
        return definition;
    }

    /**
     * Parses a unit that must follow the grammar.
     *
     * @param resourceName the name its source locations carry
     * @param source       the text of the unit
     * @return the tree of the unit
     */
    static WorkflowDefinition parse(String resourceName, String source) {
        var result = WorkflowParser.parse(resourceName, source, UnitOrigin.DEFAULT);
        return assertInstanceOf(ParseResult.Parsed.class, result, () -> resourceName + " was refused: " + result)
                .definition();
    }

    /**
     * Reads a classpath resource as UTF-8.
     *
     * @param resource the classpath resource, for example {@link #MERMAID_GOLDEN}
     * @return the text of the resource
     */
    static String read(String resource) {
        try (var in = DiagramFixtures.class.getClassLoader().getResourceAsStream(resource)) {
            Objects.requireNonNull(in, () -> "missing fixture " + resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static WorkflowValidator validator() {
        var registry = new PrimitiveRegistry(List.of(
                new Fixed<>("diagram.proceed", NoParams.class, DoneOutcome.class),
                new Fixed<>("diagram.choose", ChooseParams.class, ChooseOutcome.class),
                new Fixed<>("diagram.ready", NoParams.class, PredicateOutcome.class),
                new Fixed<>("diagram.prepare", NoParams.class, PrepareOutcome.class),
                new Fixed<>("diagram.build", NoParams.class, JobOutcome.class),
                new Fixed<>("diagram.evaluate-change", NoParams.class, EvaluationOutcome.class),
                new Fixed<>("diagram.list-changes", NoParams.class, DoneOutcome.class)));
        var entityKinds = new EntityKindRegistry(List.of(
                new EntityKind("change", Optional.of("entity"), "diagram.evaluate-change")));
        var callee = parse("diagram.callee.pmwf", """
                workflow diagram.callee version 1 format 1
                scope subworkflow
                start callee.done

                state callee.done terminal done success
                    phase inherit
                """);
        return new WorkflowValidator(registry, List.of(), entityKinds, QuestionClassRegistry.defaults(),
                SchemaRegistry.defaults(), WorkflowCatalog.of(callee));
    }

    /** A primitive that returns its first outcome and overrides no default of the SPI. */
    private static final class Fixed<P extends Record, R extends Enum<R> & PrimitiveOutcome>
            implements Primitive<P, R> {

        private final String id;
        private final Class<P> parameterType;
        private final Class<R> outcomeType;

        Fixed(String id, Class<P> parameterType, Class<R> outcomeType) {
            this.id = id;
            this.parameterType = parameterType;
            this.outcomeType = outcomeType;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Class<P> parameterType() {
            return parameterType;
        }

        @Override
        public Class<R> outcomeType() {
            return outcomeType;
        }

        @Override
        public PrimitiveResult<R> execute(WorkflowExecutionContext context, P parameters) {
            return new PrimitiveResult<>(outcomeType.getEnumConstants()[0], Map.of(), Optional.empty());
        }

        @Override
        public AwaitedEvent awaitedEvent() {
            return AwaitedEvent.NONE;
        }
    }

    private record NoParams() {
    }

    private record ChooseParams(@FreeParam(minLength = 1, maxLength = 80) String note, @FreeParam String comment) {
    }

    private enum DoneOutcome implements PrimitiveOutcome {
        DONE("done", OutcomeClass.SUCCESS);

        private final String wireName;
        private final OutcomeClass outcomeClass;

        DoneOutcome(String wireName, OutcomeClass outcomeClass) {
            this.wireName = wireName;
            this.outcomeClass = outcomeClass;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        @Override
        public OutcomeClass outcomeClass() {
            return outcomeClass;
        }
    }

    private enum ChooseOutcome implements PrimitiveOutcome {
        CHOSEN("chosen", OutcomeClass.SUCCESS), DECLINED("declined", OutcomeClass.SKIP);

        private final String wireName;
        private final OutcomeClass outcomeClass;

        ChooseOutcome(String wireName, OutcomeClass outcomeClass) {
            this.wireName = wireName;
            this.outcomeClass = outcomeClass;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        @Override
        public OutcomeClass outcomeClass() {
            return outcomeClass;
        }
    }

    private enum PredicateOutcome implements PrimitiveOutcome {
        HELD("held", OutcomeClass.SUCCESS), NOT_HELD("not_held", OutcomeClass.SUCCESS);

        private final String wireName;
        private final OutcomeClass outcomeClass;

        PredicateOutcome(String wireName, OutcomeClass outcomeClass) {
            this.wireName = wireName;
            this.outcomeClass = outcomeClass;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        @Override
        public OutcomeClass outcomeClass() {
            return outcomeClass;
        }
    }

    private enum PrepareOutcome implements PrimitiveOutcome {
        DONE("done", OutcomeClass.SUCCESS), SKIPPED("skipped", OutcomeClass.SKIP);

        private final String wireName;
        private final OutcomeClass outcomeClass;

        PrepareOutcome(String wireName, OutcomeClass outcomeClass) {
            this.wireName = wireName;
            this.outcomeClass = outcomeClass;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        @Override
        public OutcomeClass outcomeClass() {
            return outcomeClass;
        }
    }

    private enum JobOutcome implements PrimitiveOutcome {
        SUCCEEDED("succeeded", OutcomeClass.SUCCESS), FAILED("failed", OutcomeClass.FAILURE),
        CANCELLED("cancelled", OutcomeClass.INDETERMINATE);

        private final String wireName;
        private final OutcomeClass outcomeClass;

        JobOutcome(String wireName, OutcomeClass outcomeClass) {
            this.wireName = wireName;
            this.outcomeClass = outcomeClass;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        @Override
        public OutcomeClass outcomeClass() {
            return outcomeClass;
        }
    }

    private enum EvaluationOutcome implements PrimitiveOutcome {
        APPROVED("approved", OutcomeClass.SUCCESS);

        private final String wireName;
        private final OutcomeClass outcomeClass;

        EvaluationOutcome(String wireName, OutcomeClass outcomeClass) {
            this.wireName = wireName;
            this.outcomeClass = outcomeClass;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        @Override
        public OutcomeClass outcomeClass() {
            return outcomeClass;
        }
    }
}
