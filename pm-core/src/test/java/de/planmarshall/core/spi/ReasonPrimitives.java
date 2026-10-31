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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.spi.PrimitiveRegistrationException.Reason;

/**
 * The primitives that pin the registry's reason checks: one malformed declaration per refusal, a primitive whose
 * outcome declares reasons for each admitting class, and one whose old and new defects compete for the refusal.
 */
@UtilityClass
class ReasonPrimitives {

    /**
     * @return a well-formed primitive whose {@code FAILURE} outcome declares a closed reason enum
     */
    static Primitive<?, ?> wellFormed() {
        return new TestPrimitives.Fixed<ReasonParams, ReasonedOutcome>("test.reasoned", ReasonParams.class,
                ReasonedOutcome.class, ReasonedOutcome.FAILED) {

            @Override
            public PrimitiveResult<ReasonedOutcome> execute(WorkflowExecutionContext context, ReasonParams parameters) {
                return reasoned(ReasonedOutcome.FAILED);
            }
        };
    }

    /**
     * @return a well-formed primitive whose {@code SKIP} outcome declares a closed reason enum
     */
    static Primitive<?, ?> wellFormedSkip() {
        return new TestPrimitives.Fixed<ReasonParams, SkippingReasonedOutcome>("test.reasoned-skip",
                ReasonParams.class, SkippingReasonedOutcome.class, SkippingReasonedOutcome.SKIPPED) {

            @Override
            public PrimitiveResult<SkippingReasonedOutcome> execute(WorkflowExecutionContext context,
                    ReasonParams parameters) {
                return reasoned(SkippingReasonedOutcome.SKIPPED);
            }
        };
    }

    /**
     * @return a well-formed primitive whose {@code INDETERMINATE} outcome declares a closed reason enum
     */
    static Primitive<?, ?> wellFormedIndeterminate() {
        return new TestPrimitives.Fixed<ReasonParams, IndeterminateReasonedOutcome>("test.reasoned-indeterminate",
                ReasonParams.class, IndeterminateReasonedOutcome.class, IndeterminateReasonedOutcome.UNOBSERVABLE) {

            @Override
            public PrimitiveResult<IndeterminateReasonedOutcome> execute(WorkflowExecutionContext context,
                    ReasonParams parameters) {
                return reasoned(IndeterminateReasonedOutcome.UNOBSERVABLE);
            }
        };
    }

    private static <R extends Enum<R> & PrimitiveOutcome> PrimitiveResult<R> reasoned(R outcome) {
        return new PrimitiveResult<>(outcome, Map.of(), Optional.empty(),
                Optional.of(WellFormedReason.MESSAGE_REFUSED));
    }

    /**
     * @return malformed primitives, one case for every reason the registry refuses a reason declaration for
     */
    static List<TestPrimitives.Refusal> refused() {
        return List.of(
                new TestPrimitives.Refusal(Reason.MALFORMED_REASON_TYPE,
                        "a reason type that is no enum",
                        fixed("test.reason-not-enum", NonEnumReasonOutcome.class, NonEnumReasonOutcome.FAILED)),
                new TestPrimitives.Refusal(Reason.MALFORMED_REASON_TYPE,
                        "a reason type that is a null Optional",
                        fixed("test.reason-null", NullReasonOutcome.class, NullReasonOutcome.FAILED)),
                new TestPrimitives.Refusal(Reason.MALFORMED_REASON_TYPE,
                        "an enum that does not implement OutcomeReason",
                        fixed("test.reason-foreign-enum", ForeignEnumReasonOutcome.class,
                                ForeignEnumReasonOutcome.FAILED)),
                new TestPrimitives.Refusal(Reason.MALFORMED_REASON_TYPE,
                        "a reason enum without a constant",
                        fixed("test.reason-empty", EmptyReasonOutcome.class, EmptyReasonOutcome.FAILED)),
                new TestPrimitives.Refusal(Reason.REASON_ON_UNADMITTING_CLASS,
                        "a reason on a SUCCESS outcome",
                        fixed("test.reason-on-success", SucceedingReasonedOutcome.class,
                                SucceedingReasonedOutcome.DONE)),
                new TestPrimitives.Refusal(Reason.REASON_ON_UNADMITTING_CLASS,
                        "a reason on an UNSETTLED outcome",
                        fixed("test.reason-on-unsettled", UnsettledReasonedOutcome.class,
                                UnsettledReasonedOutcome.FIX_REQUESTED)),
                new TestPrimitives.Refusal(Reason.REASON_ON_UNADMITTING_CLASS,
                        "a reason on a STEPPED_OUT outcome",
                        fixed("test.reason-on-stepped-out", SteppedOutReasonedOutcome.class,
                                SteppedOutReasonedOutcome.LEFT)),
                new TestPrimitives.Refusal(Reason.MALFORMED_REASON_WIRE_NAME,
                        "a reason wire name in upper case",
                        fixed("test.reason-upper-case", UpperCaseWireReasonOutcome.class,
                                UpperCaseWireReasonOutcome.FAILED)),
                new TestPrimitives.Refusal(Reason.MALFORMED_REASON_WIRE_NAME,
                        "a reason without a wire name",
                        fixed("test.reason-unnamed", UnnamedWireReasonOutcome.class,
                                UnnamedWireReasonOutcome.FAILED)),
                new TestPrimitives.Refusal(Reason.DUPLICATE_REASON_WIRE_NAME,
                        "two reasons with one wire name",
                        fixed("test.reason-twin", TwinWireReasonOutcome.class, TwinWireReasonOutcome.FAILED)),
                new TestPrimitives.Refusal(Reason.UNMAPPABLE_REASON_CONDITION,
                        "a condition kind the primitive cannot originate",
                        fixed("test.reason-unmappable", UnmappableConditionReasonOutcome.class,
                                UnmappableConditionReasonOutcome.FAILED)),
                new TestPrimitives.Refusal(Reason.UNMAPPABLE_REASON_CONDITION,
                        "a null condition kind",
                        fixed("test.reason-null-condition", NullConditionReasonOutcome.class,
                                NullConditionReasonOutcome.FAILED)));
    }

    /**
     * @return a primitive whose first outcome declares a malformed reason type and whose second has a malformed wire
     *         name, so the old check must decide the refusal
     */
    static Primitive<?, ?> oldDefectBeforeReasonDefect() {
        return fixed("test.reason-mixed", MixedOutcome.class, MixedOutcome.FIRST);
    }

    private static <R extends Enum<R> & PrimitiveOutcome> Primitive<?, ?> fixed(String id, Class<R> outcomeType,
            R outcome) {
        return TestPrimitives.fixed(id, ReasonParams.class, outcomeType, outcome);
    }

    /**
     * Hands a class out as a reason type, the way code compiled against a raw {@link PrimitiveOutcome} can.
     */
    @SuppressWarnings("unchecked")
    private static Class<? extends OutcomeReason> asReasonType(Class<?> type) {
        return (Class<? extends OutcomeReason>) type;
    }

    record ReasonParams() {
    }

    /** The reason wire names and condition kinds are the contract, so this fixture states them literally. */
    enum WellFormedReason implements OutcomeReason {
        FOOTPRINT("footprint_violation", "footprint_violation"),
        MESSAGE_REFUSED("message_refused", null),
        COMMIT_FAILED("commit_failed", null);

        private final String wireName;
        private final String conditionKind;

        WellFormedReason(String wireName, String conditionKind) {
            this.wireName = wireName;
            this.conditionKind = conditionKind;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        @Override
        public Optional<String> conditionKind() {
            return Optional.ofNullable(conditionKind);
        }
    }

    enum UpperCaseWireReason implements OutcomeReason {
        UPPER("Upper");

        private final String wireName;

        UpperCaseWireReason(String wireName) {
            this.wireName = wireName;
        }

        @Override
        public String wireName() {
            return wireName;
        }
    }

    enum UnnamedWireReason implements OutcomeReason {
        UNNAMED;

        @Override
        public String wireName() {
            return null;
        }
    }

    enum TwinWireReason implements OutcomeReason {
        FIRST("twin"),
        SECOND("twin");

        private final String wireName;

        TwinWireReason(String wireName) {
            this.wireName = wireName;
        }

        @Override
        public String wireName() {
            return wireName;
        }
    }

    enum UnmappableConditionReason implements OutcomeReason {
        DRIFT("config_drift", "config_drift");

        private final String wireName;
        private final String conditionKind;

        UnmappableConditionReason(String wireName, String conditionKind) {
            this.wireName = wireName;
            this.conditionKind = conditionKind;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        @Override
        public Optional<String> conditionKind() {
            return Optional.ofNullable(conditionKind);
        }
    }

    enum NullConditionReason implements OutcomeReason {
        NULL_KIND("null_kind");

        private final String wireName;

        NullConditionReason(String wireName) {
            this.wireName = wireName;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        @Override
        public Optional<String> conditionKind() {
            return null;
        }
    }

    enum EmptyReason implements OutcomeReason {
        ;

        @Override
        public String wireName() {
            return "unused";
        }
    }

    enum ForeignEnum {
        VALUE
    }

    enum ReasonedOutcome implements PrimitiveOutcome {
        FAILED;

        @Override
        public String wireName() {
            return "failed";
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.FAILURE;
        }

        @Override
        public Optional<Class<? extends OutcomeReason>> reasonType() {
            return Optional.of(WellFormedReason.class);
        }
    }

    enum SkippingReasonedOutcome implements PrimitiveOutcome {
        SKIPPED;

        @Override
        public String wireName() {
            return "skipped";
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.SKIP;
        }

        @Override
        public Optional<Class<? extends OutcomeReason>> reasonType() {
            return Optional.of(WellFormedReason.class);
        }
    }

    enum IndeterminateReasonedOutcome implements PrimitiveOutcome {
        UNOBSERVABLE;

        @Override
        public String wireName() {
            return "unobservable";
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.INDETERMINATE;
        }

        @Override
        public Optional<Class<? extends OutcomeReason>> reasonType() {
            return Optional.of(WellFormedReason.class);
        }
    }

    enum NonEnumReasonOutcome implements PrimitiveOutcome {
        FAILED;

        @Override
        public String wireName() {
            return "failed";
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.FAILURE;
        }

        @Override
        public Optional<Class<? extends OutcomeReason>> reasonType() {
            return Optional.of(asReasonType(String.class));
        }
    }

    enum NullReasonOutcome implements PrimitiveOutcome {
        FAILED;

        @Override
        public String wireName() {
            return "failed";
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.FAILURE;
        }

        @Override
        public Optional<Class<? extends OutcomeReason>> reasonType() {
            return null;
        }
    }

    enum ForeignEnumReasonOutcome implements PrimitiveOutcome {
        FAILED;

        @Override
        public String wireName() {
            return "failed";
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.FAILURE;
        }

        @Override
        public Optional<Class<? extends OutcomeReason>> reasonType() {
            return Optional.of(asReasonType(ForeignEnum.class));
        }
    }

    enum EmptyReasonOutcome implements PrimitiveOutcome {
        FAILED;

        @Override
        public String wireName() {
            return "failed";
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.FAILURE;
        }

        @Override
        public Optional<Class<? extends OutcomeReason>> reasonType() {
            return Optional.of(EmptyReason.class);
        }
    }

    enum SucceedingReasonedOutcome implements PrimitiveOutcome {
        DONE;

        @Override
        public String wireName() {
            return "done";
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.SUCCESS;
        }

        @Override
        public Optional<Class<? extends OutcomeReason>> reasonType() {
            return Optional.of(WellFormedReason.class);
        }
    }

    enum UnsettledReasonedOutcome implements PrimitiveOutcome {
        FIX_REQUESTED;

        @Override
        public String wireName() {
            return "fix_requested";
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.UNSETTLED;
        }

        @Override
        public Optional<Class<? extends OutcomeReason>> reasonType() {
            return Optional.of(WellFormedReason.class);
        }
    }

    enum SteppedOutReasonedOutcome implements PrimitiveOutcome {
        LEFT;

        @Override
        public String wireName() {
            return "left";
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.STEPPED_OUT;
        }

        @Override
        public Optional<Class<? extends OutcomeReason>> reasonType() {
            return Optional.of(WellFormedReason.class);
        }
    }

    enum UpperCaseWireReasonOutcome implements PrimitiveOutcome {
        FAILED;

        @Override
        public String wireName() {
            return "failed";
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.FAILURE;
        }

        @Override
        public Optional<Class<? extends OutcomeReason>> reasonType() {
            return Optional.of(UpperCaseWireReason.class);
        }
    }

    enum UnnamedWireReasonOutcome implements PrimitiveOutcome {
        FAILED;

        @Override
        public String wireName() {
            return "failed";
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.FAILURE;
        }

        @Override
        public Optional<Class<? extends OutcomeReason>> reasonType() {
            return Optional.of(UnnamedWireReason.class);
        }
    }

    enum TwinWireReasonOutcome implements PrimitiveOutcome {
        FAILED;

        @Override
        public String wireName() {
            return "failed";
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.FAILURE;
        }

        @Override
        public Optional<Class<? extends OutcomeReason>> reasonType() {
            return Optional.of(TwinWireReason.class);
        }
    }

    enum UnmappableConditionReasonOutcome implements PrimitiveOutcome {
        FAILED;

        @Override
        public String wireName() {
            return "failed";
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.FAILURE;
        }

        @Override
        public Optional<Class<? extends OutcomeReason>> reasonType() {
            return Optional.of(UnmappableConditionReason.class);
        }
    }

    enum NullConditionReasonOutcome implements PrimitiveOutcome {
        FAILED;

        @Override
        public String wireName() {
            return "failed";
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.FAILURE;
        }

        @Override
        public Optional<Class<? extends OutcomeReason>> reasonType() {
            return Optional.of(NullConditionReason.class);
        }
    }

    enum MixedOutcome implements PrimitiveOutcome {
        FIRST,
        SECOND;

        @Override
        public String wireName() {
            return this == FIRST ? "first" : "Upper-Outcome";
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.FAILURE;
        }

        @Override
        public Optional<Class<? extends OutcomeReason>> reasonType() {
            return this == FIRST ? Optional.of(UpperCaseWireReason.class) : Optional.empty();
        }
    }
}
