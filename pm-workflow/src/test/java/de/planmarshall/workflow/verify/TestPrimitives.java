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
                new Fixed<>(READY, NoParams.class, PredicateOutcome.class)));
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
}
