/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.verify;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import de.planmarshall.core.spi.AwaitedEvent;
import de.planmarshall.core.spi.FreeParam;
import de.planmarshall.core.spi.OutcomeClass;
import de.planmarshall.core.spi.Primitive;
import de.planmarshall.core.spi.PrimitiveOutcome;
import de.planmarshall.core.spi.PrimitiveRegistry;
import de.planmarshall.core.spi.PrimitiveResult;
import de.planmarshall.core.spi.WorkflowExecutionContext;

/**
 * Primitives written for the verifier tests and the registry that holds them. The fixtures under {@code verify/} name
 * these primitives and no others; {@code test.missing} is the one id they name that is never registered.
 */
final class TestPrimitives {

    /** An {@code auto} primitive without free parameters: {@code done}, {@code skipped}. */
    static final String PREPARE = "test.prepare";

    /** A {@code job} and {@code composite_job} primitive: {@code succeeded}, {@code failed}, {@code cancelled}. */
    static final String BUILD = "test.build";

    /** A link primitive without free parameters: {@code done}. */
    static final String PROCEED = "test.proceed";

    /**
     * A link primitive with the bounded free text {@code note} ({@code 1..80}), the unbounded free text
     * {@code comment} and an engine-resolved {@code attempt}: {@code chosen}, {@code declined}.
     */
    static final String CHOOSE = "test.choose";

    /** A {@code facts} primitive: {@code done}. */
    static final String READ_FACTS = "test.read-facts";

    /** A {@code predicate:} primitive: {@code held}, {@code not_held}. */
    static final String READY = "test.ready";

    /** A return-evaluation primitive for entity kind change: {@code approved}. */
    static final String EVALUATE_CHANGE = "test.evaluate-change";

    /** A list primitive for each clause: {@code done}. */
    static final String LIST_CHANGES = "test.list-changes";

    /** A primitive that enqueues a job: {@code enqueued}, {@code skipped}. */
    static final String ENQUEUE = "test.enqueue";

    /** A primitive requiring artifact {@code test.artifact}: {@code done}. */
    static final String CONSUME = "test.consume";

    /** A primitive producing artifact {@code test.artifact}: {@code done}. */
    static final String PRODUCE = "test.produce";

    /** The id the fixtures name for a primitive that is not registered. */
    static final String MISSING = "test.missing";

    private TestPrimitives() {
    }

    /**
     * @return a registry of every primitive of this class
     */
    static PrimitiveRegistry registry() {
        return new PrimitiveRegistry(List.of(
                new Fixed<>(PREPARE, NoParams.class, PrepareOutcome.class),
                new Fixed<>(BUILD, NoParams.class, JobOutcome.class),
                new Fixed<>(PROCEED, NoParams.class, DoneOutcome.class),
                new Fixed<>(CHOOSE, ChooseParams.class, ChooseOutcome.class),
                new Fixed<>(READ_FACTS, NoParams.class, DoneOutcome.class),
                new Fixed<>(READY, NoParams.class, PredicateOutcome.class),
                new Fixed<>(EVALUATE_CHANGE, NoParams.class, EvaluateChangeOutcome.class),
                new Fixed<>(LIST_CHANGES, NoParams.class, DoneOutcome.class),
                new EnqueuePrimitive(),
                new ArtifactConsumer(),
                new ArtifactProducer()));
    }

    /**
     * @return a catalog providing sub-workflows for test call verification
     */
    static WorkflowCatalog catalog() {
        var commitAndPush = VerifyFixtures.parseSource("git.commit_and_push.pmwf", """
                workflow git.commit_and_push version 1 format 1
                scope subworkflow
                param push : enum[enabled|disabled]
                param attempts : int[1..5]
                param targets : list<enum[origin|mirror]>
                start git.done

                state git.done terminal done success
                    phase inherit
                """);
        var depth5 = VerifyFixtures.parseSource("test.depth5.pmwf", """
                workflow test.depth5 version 1 format 1
                scope subworkflow
                start depth5.done

                state depth5.done terminal done success
                    phase inherit
                """);
        var depth4 = VerifyFixtures.parseSource("test.depth4.pmwf", """
                workflow test.depth4 version 1 format 1
                scope subworkflow
                start depth4.step

                state depth4.step
                    phase inherit
                    call test.depth5()
                        done -> depth4.done
                        stepped_out -> depth4.done

                state depth4.done terminal done success
                    phase inherit
                """);
        var depth3 = VerifyFixtures.parseSource("test.depth3.pmwf", """
                workflow test.depth3 version 1 format 1
                scope subworkflow
                start depth3.step

                state depth3.step
                    phase inherit
                    call test.depth4()
                        done -> depth3.done
                        stepped_out -> depth3.done

                state depth3.done terminal done success
                    phase inherit
                """);
        var depth2 = VerifyFixtures.parseSource("test.depth2.pmwf", """
                workflow test.depth2 version 1 format 1
                scope subworkflow
                start depth2.step

                state depth2.step
                    phase inherit
                    call test.depth3()
                        done -> depth2.done
                        stepped_out -> depth2.done

                state depth2.done terminal done success
                    phase inherit
                """);
        var depth1 = VerifyFixtures.parseSource("test.depth1.pmwf", """
                workflow test.depth1 version 1 format 1
                scope subworkflow
                start depth1.step

                state depth1.step
                    phase inherit
                    call test.depth2()
                        done -> depth1.done
                        stepped_out -> depth1.done

                state depth1.done terminal done success
                    phase inherit
                """);
        var callCycle = VerifyFixtures.parseSource("verify.call-cycle.pmwf", """
                workflow verify.call-cycle version 1 format 1
                scope subworkflow
                start verify.entry

                state verify.entry
                    phase inherit
                    call verify.call-cycle()
                        done -> verify.done
                        stepped_out -> verify.done

                state verify.done terminal done success
                    phase inherit
                """);
        return new WorkflowCatalog(List.of(commitAndPush, depth1, depth2, depth3, depth4, depth5, callCycle));
    }

    /** A primitive that returns its first outcome and overrides no default of the SPI. */
    static final class Fixed<P extends Record, R extends Enum<R> & PrimitiveOutcome> implements Primitive<P, R> {

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

    record NoParams() {
    }

    record ChooseParams(@FreeParam(minLength = 1, maxLength = 80) String note, @FreeParam String comment,
    int attempt) {
    }

    enum PrepareOutcome implements PrimitiveOutcome {
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

    /** The outcomes of a job, {@code cancelled} included as on every job primitive. */
    enum JobOutcome implements PrimitiveOutcome {
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

    enum DoneOutcome implements PrimitiveOutcome {
        DONE;

        @Override
        public String wireName() {
            return "done";
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.SUCCESS;
        }
    }

    enum ChooseOutcome implements PrimitiveOutcome {
        CHOSEN("chosen"), DECLINED("declined");

        private final String wireName;

        ChooseOutcome(String wireName) {
            this.wireName = wireName;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.SUCCESS;
        }
    }

    enum PredicateOutcome implements PrimitiveOutcome {
        HELD("held"), NOT_HELD("not_held");

        private final String wireName;

        PredicateOutcome(String wireName) {
            this.wireName = wireName;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.SUCCESS;
        }
    }

    enum EvaluateChangeOutcome implements PrimitiveOutcome {
        APPROVED("approved");

        private final String wireName;

        EvaluateChangeOutcome(String wireName) {
            this.wireName = wireName;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.SUCCESS;
        }
    }

    enum EnqueueOutcome implements PrimitiveOutcome {
        ENQUEUED("enqueued", OutcomeClass.SUCCESS, true),
        SKIPPED("skipped", OutcomeClass.SKIP, false);

        private final String wireName;
        private final OutcomeClass outcomeClass;
        private final boolean startsJob;

        EnqueueOutcome(String wireName, OutcomeClass outcomeClass, boolean startsJob) {
            this.wireName = wireName;
            this.outcomeClass = outcomeClass;
            this.startsJob = startsJob;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        @Override
        public OutcomeClass outcomeClass() {
            return outcomeClass;
        }

        @Override
        public boolean startsJob() {
            return startsJob;
        }
    }

    static final class EnqueuePrimitive implements Primitive<NoParams, EnqueueOutcome> {
        @Override
        public String id() {
            return ENQUEUE;
        }

        @Override
        public Class<NoParams> parameterType() {
            return NoParams.class;
        }

        @Override
        public Class<EnqueueOutcome> outcomeType() {
            return EnqueueOutcome.class;
        }

        @Override
        public PrimitiveResult<EnqueueOutcome> execute(WorkflowExecutionContext context, NoParams parameters) {
            return new PrimitiveResult<>(EnqueueOutcome.ENQUEUED, Map.of(), Optional.empty());
        }

        @Override
        public AwaitedEvent awaitedEvent() {
            return AwaitedEvent.NONE;
        }

        @Override
        public boolean enqueuesJob() {
            return true;
        }
    }

    static final class ArtifactConsumer implements Primitive<NoParams, DoneOutcome> {
        @Override
        public String id() {
            return CONSUME;
        }

        @Override
        public Class<NoParams> parameterType() {
            return NoParams.class;
        }

        @Override
        public Class<DoneOutcome> outcomeType() {
            return DoneOutcome.class;
        }

        @Override
        public PrimitiveResult<DoneOutcome> execute(WorkflowExecutionContext context, NoParams parameters) {
            return new PrimitiveResult<>(DoneOutcome.DONE, Map.of(), Optional.empty());
        }

        @Override
        public AwaitedEvent awaitedEvent() {
            return AwaitedEvent.NONE;
        }

        @Override
        public Set<String> requiredArtifacts() {
            return Set.of("test.artifact");
        }
    }

    static final class ArtifactProducer implements Primitive<NoParams, DoneOutcome> {
        @Override
        public String id() {
            return PRODUCE;
        }

        @Override
        public Class<NoParams> parameterType() {
            return NoParams.class;
        }

        @Override
        public Class<DoneOutcome> outcomeType() {
            return DoneOutcome.class;
        }

        @Override
        public PrimitiveResult<DoneOutcome> execute(WorkflowExecutionContext context, NoParams parameters) {
            return new PrimitiveResult<>(DoneOutcome.DONE, Map.of(), Optional.empty());
        }

        @Override
        public AwaitedEvent awaitedEvent() {
            return AwaitedEvent.NONE;
        }

        @Override
        public Set<String> producedArtifacts() {
            return Set.of("test.artifact");
        }
    }
}
