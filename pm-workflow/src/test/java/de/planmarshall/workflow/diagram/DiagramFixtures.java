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
import java.time.Instant;
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
import de.planmarshall.workflow.plan.ComposedUnit;
import de.planmarshall.workflow.plan.CompositionEvent;
import de.planmarshall.workflow.plan.CompositionInputs;
import de.planmarshall.workflow.plan.PlanInstance;
import de.planmarshall.workflow.plan.PlanInstanceHeader;
import de.planmarshall.workflow.plan.PruneReason;
import de.planmarshall.workflow.plan.PrunedElement;
import de.planmarshall.workflow.plan.UnitReference;
import de.planmarshall.workflow.plan.WaivedGate;
import de.planmarshall.workflow.verify.EntityKind;
import de.planmarshall.workflow.verify.EntityKindRegistry;
import de.planmarshall.workflow.verify.QuestionClassRegistry;
import de.planmarshall.workflow.verify.SchemaRegistry;
import de.planmarshall.workflow.verify.WorkflowCatalog;
import de.planmarshall.workflow.verify.WorkflowValidator;

/**
 * Reads the fixture of the diagram tests from the test classpath: the unit {@code diagram/diagram-fixture.pmwf},
 * written for these tests and never copied from a unit of the product, and its golden files
 * {@code diagram/diagram-fixture.mmd}, {@code .adoc} and {@code .txt}, read as UTF-8; and the fixture of the instance
 * forms, a plan instance version whose composed unit is {@code diagram/instance-fixture.pmwf}, also written for these
 * tests, with its golden files {@code diagram/instance-fixture.mmd}, {@code .adoc} and {@code .txt}.
 * <p>
 * The fixture is parsed and checked by the static verifier before a test renders it, so the golden files show a unit
 * the generator is meant for. The primitives, the entity kind {@code change} and the called sub-workflow
 * {@code diagram.callee} it names are registered here, because the helpers of the verifier tests are package-private
 * to their package. The callee declares the result schema {@code commit-result}, because the fixture guards the
 * {@code done} of its call on the result. The kind {@code change} names no entity unit: no state of the fixture asks, and the verifier
 * refuses a named entity unit the catalog does not hold.
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

    /** The classpath resource of the composed unit of the fixture instance. */
    static final String INSTANCE_FIXTURE = "diagram/instance-fixture.pmwf";

    /** The classpath resource of the instance Mermaid golden file, rendered without an active state. */
    static final String INSTANCE_MERMAID_GOLDEN = "diagram/instance-fixture.mmd";

    /** The classpath resource of the instance AsciiDoc golden file, rendered without an active state. */
    static final String INSTANCE_ASCIIDOC_GOLDEN = "diagram/instance-fixture.adoc";

    /** The classpath resource of the instance terminal golden file, rendered without an active state. */
    static final String INSTANCE_ASCII_GOLDEN = "diagram/instance-fixture.txt";

    /** The plan of the fixture instance. */
    static final String INSTANCE_PLAN_ID = "diagram-fixture";

    /** The version of the fixture instance, which its unit's header repeats. */
    static final int INSTANCE_VERSION = 3;

    /** The gate the fixture instance waives, a state of its unit. */
    static final String WAIVED_GATE = "finalize.merge-gate";

    /** The waiver that waives {@link #WAIVED_GATE}. */
    static final String WAIVER_ID = "W-7";

    /** The elements the fixture instance prunes, in header order: a step, a state and a policy action. */
    static final List<PrunedElement> PRUNED = List.of(
            new PrunedElement(PrunedElement.Kind.STEP, "default:analysis-roundtrip", PruneReason.PROVIDER_UNCONFIGURED,
                    "integrations.sonar"),
            new PrunedElement(PrunedElement.Kind.STATE, "finalize.merge-dispatch", PruneReason.MERGE_BY_OPERATOR,
                    "axes.asking"),
            new PrunedElement(PrunedElement.Kind.POLICY_ACTION, "finalize.remediate-ci/loop_back",
                    PruneReason.LOCAL_ONLY, "derived.push_remote"));

    private static final String DIGEST = "0123456789abcdef".repeat(4);

    private DiagramFixtures() {
    }

    /**
     * Reads and parses the composed unit of the fixture instance and gives it the fixture header: event {@code plan},
     * lane {@code deep}, posture {@code standard}, the elements of {@link #PRUNED}, and {@link #WAIVED_GATE} waived by
     * {@link #WAIVER_ID}.
     *
     * The unit passes the static verifier, so the golden files show an instance the generator is meant for.
     *
     * @return the fixture instance version
     */
    static PlanInstance instance() {
        var text = read(INSTANCE_FIXTURE);
        var unit = parse(INSTANCE_FIXTURE, text);
        var result = validator().verify(unit);
        assertTrue(result.passed(), () -> INSTANCE_FIXTURE + " was refused by the verifier: " + result);
        return instance(unit, text);
    }

    /**
     * Gives a composed unit the fixture header; the unit must name the version {@link #INSTANCE_VERSION}.
     *
     * @param unit the composed unit
     * @param text the DSL text of the unit
     * @return the instance version of that unit
     */
    static PlanInstance instance(WorkflowDefinition unit, String text) {
        var inputs = new CompositionInputs(DIGEST, List.of(new UnitReference("plan", Optional.of("plan.pmwf"), DIGEST,
                        UnitOrigin.DEFAULT)), List.of(), "1", DIGEST, DIGEST, Optional.empty(), Optional.of("feature"),
                Optional.empty());
        var header = new PlanInstanceHeader(INSTANCE_PLAN_ID, INSTANCE_VERSION, CompositionEvent.PLAN,
                Instant.parse("2026-10-14T09:00:00Z"), inputs, Optional.of(PlanInstanceHeader.Lane.DEEP),
                Optional.of(PlanInstanceHeader.Posture.STANDARD),
                new PlanInstanceHeader.Steps(List.of(), List.of("default:archive-plan")), PRUNED, List.of(),
                List.of(new WaivedGate(WAIVED_GATE, WAIVER_ID)));
        return new PlanInstance(INSTANCE_VERSION, header, unit, text);
    }

    /**
     * Gives the fixture instance one composed unit of its version, so the three forms render the composed set.
     *
     * @return the fixture instance with the composed file {@code instance/phase-x.v4.pmwf}
     */
    static PlanInstance instanceWithComposedFile() {
        var base = instance();
        var composed = ComposedUnit.of("phase-x", parse("phase-x.pmwf", """
                ---
                name: phase-x
                description: A composed phase written for the diagram tests.
                type: subworkflow
                icon: workflow.default
                entry: main
                exit: done
                modification_date: 2026-10-29
                version: 0.1.0
                author: plan-marshall
                ---
                workflow phase-x version 3 format 1
                scope subworkflow
                entry main -> phase-x.done

                state phase-x.done terminal done success
                """));
        return new PlanInstance(base.version(), base.header(), base.unit(), base.text(), List.of(composed));
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
                new EntityKind("change", Optional.empty(), "diagram.evaluate-change")));
        var callee = parse("diagram.callee.pmwf", """
                ---
                name: diagram.callee
                description: A unit written for the tests of pm-workflow.
                type: subworkflow
                icon: workflow.default
                entry: main
                exit: done
                modification_date: 2026-01-15
                version: 0.1.0
                author: plan-marshall
                ---
                workflow diagram.callee version 1 format 1
                scope subworkflow
                result schema[commit-result]
                entry main -> callee.done

                state callee.done terminal done success
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
