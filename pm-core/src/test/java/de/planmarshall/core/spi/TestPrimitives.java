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

import de.planmarshall.core.spi.PrimitiveRegistrationException.Reason;

/**
 * Primitives written for the tests of the SPI: one well-formed primitive for every {@link OutcomeClass}, each with
 * its own parameter record and its own closed outcome enum, one primitive that starts a job, one with an attempt
 * cap, and the malformed primitives a registry refuses.
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

    /**
     * @return a well-formed primitive with an attempt cap: once the cap is spent it returns an outcome of class
     *         {@link OutcomeClass#FAILURE}
     */
    static Primitive<?, ?> capped() {
        return new Capped("test.retry-capped", Optional.of("attempts_exhausted"));
    }

    /**
     * @return malformed primitives, at least one case for every reason a registry refuses a primitive for
     */
    static List<Refusal> refused() {
        return List.of(
                new Refusal(Reason.DUPLICATE_ID, "two primitives with one id", "test.fail",
                        List.of(failing("test.fail"), failing("test.fail"))),
                new Refusal(Reason.MALFORMED_ID, "an id without a dot", failing("commit")),
                new Refusal(Reason.MALFORMED_ID, "an id in upper case", failing("Git.Commit")),
                new Refusal(Reason.MALFORMED_ID, "an id of three segments", failing("git.commit.now")),
                new Refusal(Reason.MALFORMED_ID, "no id", failing(null)),
                new Refusal(Reason.PARAMETER_TYPE_NOT_A_RECORD, "no parameter type",
                        new Fixed<FailParams, FailOutcome>("test.no-parameters", null, FailOutcome.class,
                                FailOutcome.FAILED)),
                new Refusal(Reason.PARAMETER_TYPE_NOT_A_RECORD, "a parameter type that is no record",
                        new Fixed<FailParams, FailOutcome>("test.class-parameters", outsideItsBound(String.class),
                                FailOutcome.class, FailOutcome.FAILED)),
                new Refusal(Reason.OPEN_OUTCOME_SET, "no outcome type",
                        new Fixed<FailParams, FailOutcome>("test.no-outcomes", FailParams.class, null,
                                FailOutcome.FAILED)),
                new Refusal(Reason.OPEN_OUTCOME_SET, "an outcome type that is no enum",
                        new Fixed<FailParams, FailOutcome>("test.class-outcomes", FailParams.class,
                                outsideItsBound(String.class), FailOutcome.FAILED)),
                new Refusal(Reason.OPEN_OUTCOME_SET, "an outcome enum without a constant",
                        new Fixed<FailParams, NoOutcome>("test.empty-outcomes", FailParams.class, NoOutcome.class,
                                null)),
                new Refusal(Reason.DUPLICATE_WIRE_NAME, "two outcomes with one wire name",
                        fixed("test.twin-outcomes", FailParams.class, TwinOutcome.class, TwinOutcome.FIRST)),
                new Refusal(Reason.MALFORMED_WIRE_NAME, "a wire name in upper case",
                        fixed("test.upper-case-outcome", FailParams.class, UpperCaseOutcome.class,
                                UpperCaseOutcome.HOOK_FAILED)),
                new Refusal(Reason.MALFORMED_WIRE_NAME, "an outcome without a wire name",
                        fixed("test.unnamed-outcome", FailParams.class, UnnamedOutcome.class,
                                UnnamedOutcome.UNNAMED)),
                new Refusal(Reason.RESERVED_WIRE_NAME, "the wire name of the engine's step-out",
                        fixed("test.reserved-outcome", FailParams.class, ReservedOutcome.class,
                                ReservedOutcome.STEPPED_OUT)),
                new Refusal(Reason.UNDECLARED_JOB_SIDE_EFFECT, "a job enqueued without a job-starting outcome",
                        enqueuingWithoutJobStartingOutcome()),
                new Refusal(Reason.UNDECLARED_JOB_SIDE_EFFECT, "a job-starting outcome without a declared job",
                        fixed("test.start-silently", StartJobParams.class, StartJobOutcome.class,
                                StartJobOutcome.STARTED)),
                new Refusal(Reason.ATTEMPT_CAP_WITHOUT_FAILURE_EXHAUSTION, "an attempt cap without an exhaustion",
                        new Capped("test.cap-open", Optional.empty())),
                new Refusal(Reason.ATTEMPT_CAP_WITHOUT_FAILURE_EXHAUSTION, "an exhaustion that is null",
                        new Capped("test.cap-null", null)),
                new Refusal(Reason.ATTEMPT_CAP_WITHOUT_FAILURE_EXHAUSTION, "an exhaustion that is no outcome",
                        new Capped("test.cap-unknown", Optional.of("unknown"))),
                new Refusal(Reason.ATTEMPT_CAP_WITHOUT_FAILURE_EXHAUSTION, "an exhaustion that is no failure",
                        cappedWithSuccessAsExhaustion()),
                new Refusal(Reason.MISSING_AWAITED_EVENT, "no awaited event", awaitingNothingDeclared()));
    }

    /**
     * @return a primitive that enqueues a job although none of its outcomes starts one
     */
    static Primitive<?, ?> enqueuingWithoutJobStartingOutcome() {
        return new Fixed<>("test.enqueue-silently", FailParams.class, FailOutcome.class, FailOutcome.FAILED) {

            @Override
            public boolean enqueuesJob() {
                return true;
            }
        };
    }

    /**
     * @return a primitive with an attempt cap whose exhaustion outcome is of class {@link OutcomeClass#SUCCESS}
     */
    static Primitive<?, ?> cappedWithSuccessAsExhaustion() {
        return new Capped("test.cap-succeeds", Optional.of("retried"));
    }

    /**
     * @return a primitive whose awaited event is {@code null}
     */
    static Primitive<?, ?> awaitingNothingDeclared() {
        return new Fixed<>("test.await-undeclared", FailParams.class, FailOutcome.class, FailOutcome.FAILED) {

            @Override
            public AwaitedEvent awaitedEvent() {
                return null;
            }
        };
    }

    private static Primitive<?, ?> failing(String id) {
        return fixed(id, FailParams.class, FailOutcome.class, FailOutcome.FAILED);
    }

    /**
     * Hands a class out as if it satisfied the bound of a type parameter. Code compiled against a raw
     * {@link Primitive} can do the same, which is why a registry checks the classes it is given.
     */
    @SuppressWarnings("unchecked")
    private static <T> Class<T> outsideItsBound(Class<?> type) {
        return (Class<T>) type;
    }

    /** Primitives a registry refuses, with the reason of the refusal and the id it names. */
    record Refusal(Reason reason, String description, String refusedId, List<Primitive<?, ?>> primitives) {

        Refusal(Reason reason, String description, Primitive<?, ?> primitive) {
            this(reason, description, String.valueOf(primitive.id()), List.of(primitive));
        }

        @Override
        public String toString() {
            return reason + ": " + description;
        }
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

    /** Retries under an attempt cap and names the outcome it returns once the cap is spent. */
    static final class Capped extends Fixed<CapParams, CapOutcome> {

        private final Optional<String> exhaustion;

        Capped(String id, Optional<String> exhaustion) {
            super(id, CapParams.class, CapOutcome.class, CapOutcome.RETRIED);
            this.exhaustion = exhaustion;
        }

        @Override
        public CycleMeasure cycleMeasure() {
            return CycleMeasure.ATTEMPT_CAP;
        }

        @Override
        public Optional<String> exhaustionOutcome() {
            return exhaustion;
        }
    }

    /** An outcome of class {@link OutcomeClass#FAILURE}; the malformed outcome enums differ in their wire names. */
    interface FailureOutcome extends PrimitiveOutcome {

        @Override
        default OutcomeClass outcomeClass() {
            return OutcomeClass.FAILURE;
        }
    }

    enum NoOutcome implements FailureOutcome {
        ;

        @Override
        public String wireName() {
            return "none";
        }
    }

    enum TwinOutcome implements FailureOutcome {
        FIRST, SECOND;

        @Override
        public String wireName() {
            return "twin";
        }
    }

    enum UpperCaseOutcome implements FailureOutcome {
        HOOK_FAILED;

        @Override
        public String wireName() {
            return "Hook-Failed";
        }
    }

    enum UnnamedOutcome implements FailureOutcome {
        UNNAMED;

        @Override
        public String wireName() {
            return null;
        }
    }

    enum ReservedOutcome implements FailureOutcome {
        STEPPED_OUT;

        @Override
        public String wireName() {
            return "stepped_out";
        }
    }

    enum CapOutcome implements PrimitiveOutcome {
        RETRIED("retried", OutcomeClass.SUCCESS), ATTEMPTS_EXHAUSTED("attempts_exhausted", OutcomeClass.FAILURE);

        private final String wireName;
        private final OutcomeClass outcomeClass;

        CapOutcome(String wireName, OutcomeClass outcomeClass) {
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

    record CapParams() {
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
