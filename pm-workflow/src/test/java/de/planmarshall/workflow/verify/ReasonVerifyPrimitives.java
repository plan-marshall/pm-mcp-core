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

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import de.planmarshall.core.spi.AwaitedEvent;
import de.planmarshall.core.spi.CycleMeasure;
import de.planmarshall.core.spi.OutcomeClass;
import de.planmarshall.core.spi.OutcomeReason;
import de.planmarshall.core.spi.Primitive;
import de.planmarshall.core.spi.PrimitiveOutcome;
import de.planmarshall.core.spi.PrimitiveRegistry;
import org.jspecify.annotations.Nullable;

/**
 * The primitives of the reason fixtures under {@code verify/reasons/} and the registry that holds them:
 * {@link #CHECK}, whose {@code failed} carries three reasons and whose {@code skipped} carries one, and its reasonless
 * twin {@link #PLAIN}, the matched control of the Rule 9 cases. Both are EasyMock primitives; the enums are their
 * closed outcome and reason sets.
 */
// Every datum in this class is a contract: primitive ids, outcome and reason wire names and the condition kind.
final class ReasonVerifyPrimitives {

    /**
     * A primitive with the outcomes {@code passed} ({@code SUCCESS}), {@code failed} ({@code FAILURE}, the reasons of
     * {@link FailedReason}) and {@code skipped} ({@code SKIP}, the reason of {@link SkippedReason}).
     */
    static final String CHECK = "reason.check";

    /** The reasonless twin of {@link #CHECK}: the same outcomes and classes, no reason. */
    static final String PLAIN = "reason.plain";

    /** The condition kind {@link FailedReason#OUTSIDE_FOOTPRINT} maps to. */
    static final String FOOTPRINT_VIOLATION = "footprint_violation";

    private ReasonVerifyPrimitives() {
    }

    /** @return a registry of {@link #CHECK} and {@link #PLAIN} */
    static PrimitiveRegistry registry() {
        return new PrimitiveRegistry(List.of(primitive(CHECK, CheckOutcome.class),
                primitive(PLAIN, PlainOutcome.class)));
    }

    @SuppressWarnings("unchecked")
    private static <R extends Enum<R> & PrimitiveOutcome> Primitive<TestPrimitives.NoParams, R> primitive(String id,
            Class<R> outcomeType) {
        Primitive<TestPrimitives.NoParams, R> primitive = createMock(Primitive.class);
        expect(primitive.id()).andReturn(id).anyTimes();
        expect(primitive.parameterType()).andReturn(TestPrimitives.NoParams.class).anyTimes();
        expect(primitive.outcomeType()).andReturn(outcomeType).anyTimes();
        expect(primitive.awaitedEvent()).andReturn(AwaitedEvent.NONE).anyTimes();
        expect(primitive.producedArtifacts()).andReturn(Set.of()).anyTimes();
        expect(primitive.requiredArtifacts()).andReturn(Set.of()).anyTimes();
        expect(primitive.waivableGates()).andReturn(Set.of()).anyTimes();
        expect(primitive.enqueuesJob()).andReturn(false).anyTimes();
        expect(primitive.cycleMeasure()).andReturn(CycleMeasure.NONE).anyTimes();
        expect(primitive.exhaustionOutcome()).andReturn(Optional.empty()).anyTimes();
        replay(primitive);
        return primitive;
    }

    /** The reasons of {@link CheckOutcome#FAILED}: one maps to {@code footprint_violation}, two to none. */
    enum FailedReason implements OutcomeReason {
        OUTSIDE_FOOTPRINT("outside_footprint", FOOTPRINT_VIOLATION), TESTS_RED("tests_red", null),
        LINT_RED("lint_red", null);

        private final String wireName;
        private final @Nullable String conditionKind;

        FailedReason(String wireName, @Nullable String conditionKind) {
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

    /** The reason of {@link CheckOutcome#SKIPPED}, mapped to no condition. */
    enum SkippedReason implements OutcomeReason {
        NOTHING_TO_CHECK;

        @Override
        public String wireName() {
            return "nothing_to_check";
        }
    }

    /** The outcomes of {@link #CHECK}. */
    enum CheckOutcome implements PrimitiveOutcome {
        PASSED("passed", OutcomeClass.SUCCESS), FAILED("failed", OutcomeClass.FAILURE),
        SKIPPED("skipped", OutcomeClass.SKIP);

        private final String wireName;
        private final OutcomeClass outcomeClass;

        CheckOutcome(String wireName, OutcomeClass outcomeClass) {
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

        @Override
        public Optional<Class<? extends OutcomeReason>> reasonType() {
            return switch (this) {
                case FAILED -> Optional.of(FailedReason.class);
                case SKIPPED -> Optional.of(SkippedReason.class);
                case PASSED -> Optional.empty();
            };
        }
    }

    /** The outcomes of {@link #PLAIN}: those of {@link CheckOutcome} without a reason. */
    enum PlainOutcome implements PrimitiveOutcome {
        PASSED("passed", OutcomeClass.SUCCESS), FAILED("failed", OutcomeClass.FAILURE),
        SKIPPED("skipped", OutcomeClass.SKIP);

        private final String wireName;
        private final OutcomeClass outcomeClass;

        PlainOutcome(String wireName, OutcomeClass outcomeClass) {
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
