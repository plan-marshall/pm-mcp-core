/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.spi;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Primitives written for the tests of the SPI: one well-formed primitive for every {@link OutcomeClass}, each with
 * its own parameter record and its own closed outcome enum, and one primitive that starts a job.
 */
final class TestPrimitives {

    /** The job identifier the job-starting primitive reports. */
    static final String SPAWNED_JOB_ID = "b-0123456789abcdef";

    private TestPrimitives() {
    }

    /**
     * @return the six well-formed primitives, one for every outcome class, each with the parameters to run it with
     */
    static List<Case> oneForEveryOutcomeClass() {
        return List.of(
                new Case(new Complete(), new CompleteParams("request"), OutcomeClass.SUCCESS, "done"),
                new Case(fixed("test.skip-absent", SkipParams.class, SkipOutcome.class, SkipOutcome.SKIPPED_NO_INPUT),
                        new SkipParams(), OutcomeClass.SKIP, "skipped_no_input"),
                new Case(fixed("test.fail", FailParams.class, FailOutcome.class, FailOutcome.FAILED),
                        new FailParams(), OutcomeClass.FAILURE, "failed"),
                new Case(fixed("test.observe", ObserveParams.class, ObserveOutcome.class, ObserveOutcome.UNOBSERVABLE),
                        new ObserveParams(), OutcomeClass.INDETERMINATE, "unobservable"),
                new Case(fixed("test.hand-back", HandBackParams.class, HandBackOutcome.class,
                        HandBackOutcome.FIX_REQUESTED), new HandBackParams(), OutcomeClass.UNSETTLED, "fix_requested"),
                new Case(fixed("test.leave", LeaveParams.class, LeaveOutcome.class, LeaveOutcome.LEFT),
                        new LeaveParams(), OutcomeClass.STEPPED_OUT, "left"));
    }

    /**
     * @return the primitives of {@link #oneForEveryOutcomeClass()} without their parameters
     */
    static List<Primitive<?, ?>> wellFormed() {
        return oneForEveryOutcomeClass().stream().<Primitive<?, ?>>map(Case::primitive).toList();
    }

    /**
     * @return a context of a call that addresses the plan or epic with the identifier
     */
    static WorkflowExecutionContext scope(String scopeId) {
        return () -> Optional.of(scopeId);
    }

    /**
     * @return a context of a call that addresses the workspace scope
     */
    static WorkflowExecutionContext workspace() {
        return Optional::empty;
    }

    /**
     * Runs a primitive whose parameter type is not known statically.
     *
     * @return what the primitive returned for the parameters
     */
    static <P extends Record, R extends Enum<R> & PrimitiveOutcome> PrimitiveResult<R> run(Primitive<P, R> primitive,
            WorkflowExecutionContext context, Record parameters) {
        return primitive.execute(context, primitive.parameterType().cast(parameters));
    }

    static <P extends Record, R extends Enum<R> & PrimitiveOutcome> Fixed<P, R> fixed(String id,
            Class<P> parameterType, Class<R> outcomeType, R outcome) {
        return new Fixed<>(id, parameterType, outcomeType, outcome);
    }

    /** A primitive with the parameters to run it with and what its outcome is expected to be. */
    record Case(Primitive<?, ?> primitive, Record parameters, OutcomeClass outcomeClass, String wireName) {

        @Override
        public String toString() {
            return outcomeClass + " by " + primitive.id();
        }
    }

    /** A primitive that returns one fixed outcome and overrides no default of the SPI. */
    static class Fixed<P extends Record, R extends Enum<R> & PrimitiveOutcome> implements Primitive<P, R> {

        private final String id;
        private final Class<P> parameterType;
        private final Class<R> outcomeType;
        private final R outcome;

        Fixed(String id, Class<P> parameterType, Class<R> outcomeType, R outcome) {
            this.id = id;
            this.parameterType = parameterType;
            this.outcomeType = outcomeType;
            this.outcome = outcome;
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
            return new PrimitiveResult<>(outcome, Map.of(), Optional.empty());
        }

        @Override
        public AwaitedEvent awaitedEvent() {
            return AwaitedEvent.NONE;
        }
    }

    /** Completes, and records the subject it was given and the scope it ran for. */
    static final class Complete extends Fixed<CompleteParams, CompleteOutcome> {

        Complete() {
            super("test.complete", CompleteParams.class, CompleteOutcome.class, CompleteOutcome.DONE);
        }

        @Override
        public PrimitiveResult<CompleteOutcome> execute(WorkflowExecutionContext context, CompleteParams parameters) {
            var facts = new LinkedHashMap<String, Object>();
            facts.put("subject", parameters.subject());
            context.scopeId().ifPresent(scopeId -> facts.put("scope", scopeId));
            return new PrimitiveResult<>(CompleteOutcome.DONE, facts, Optional.empty());
        }
    }

    /** Enqueues a job and reports it with its job-starting outcome. */
    static final class StartJob extends Fixed<StartJobParams, StartJobOutcome> {

        StartJob() {
            super("test.start-job", StartJobParams.class, StartJobOutcome.class, StartJobOutcome.STARTED);
        }

        @Override
        public PrimitiveResult<StartJobOutcome> execute(WorkflowExecutionContext context, StartJobParams parameters) {
            return new PrimitiveResult<>(StartJobOutcome.STARTED, Map.of(), Optional.of(SPAWNED_JOB_ID));
        }

        @Override
        public AwaitedEvent awaitedEvent() {
            return AwaitedEvent.JOB_SETTLED;
        }

        @Override
        public boolean enqueuesJob() {
            return true;
        }
    }

    record CompleteParams(@FreeParam(minLength = 1, maxLength = 80) String subject) {
    }

    record SkipParams() {
    }

    record FailParams() {
    }

    record ObserveParams() {
    }

    record HandBackParams() {
    }

    record LeaveParams() {
    }

    record StartJobParams() {
    }

    enum CompleteOutcome implements PrimitiveOutcome {
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

    enum SkipOutcome implements PrimitiveOutcome {
        SKIPPED_NO_INPUT;

        @Override
        public String wireName() {
            return "skipped_no_input";
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.SKIP;
        }
    }

    enum FailOutcome implements PrimitiveOutcome {
        FAILED;

        @Override
        public String wireName() {
            return "failed";
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.FAILURE;
        }
    }

    enum ObserveOutcome implements PrimitiveOutcome {
        UNOBSERVABLE;

        @Override
        public String wireName() {
            return "unobservable";
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.INDETERMINATE;
        }
    }

    enum HandBackOutcome implements PrimitiveOutcome {
        FIX_REQUESTED;

        @Override
        public String wireName() {
            return "fix_requested";
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.UNSETTLED;
        }
    }

    enum LeaveOutcome implements PrimitiveOutcome {
        LEFT;

        @Override
        public String wireName() {
            return "left";
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.STEPPED_OUT;
        }
    }

    enum StartJobOutcome implements PrimitiveOutcome {
        STARTED;

        @Override
        public String wireName() {
            return "started";
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.SUCCESS;
        }

        @Override
        public boolean startsJob() {
            return true;
        }
    }
}
