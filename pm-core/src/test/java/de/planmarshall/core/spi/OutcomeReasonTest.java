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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import de.planmarshall.core.store.WorkflowStatus;

/**
 * The reason of an outcome maps to a closed set of conditions, and that set must not drift from the kinds the status
 * store persists (PM-IMPL-5, Workflow DSL § Outcomes and Reasons).
 */
@EnableGeneratorController
@DisplayName("Contract of an outcome reason")
class OutcomeReasonTest {

    /**
     * The condition kinds are the contract of the persisted schema, so the test states them literally.
     */
    @Test
    @DisplayName("the mappable condition kinds are exactly the two a primitive can originate")
    void mappableConditionKindsAreTheTwoPrimitiveConditions() {
        assertEquals(
                OutcomeReason.MAPPABLE_CONDITION_KINDS, Set.of("footprint_violation", "precondition_unmet"), "only the two primitive conditions are mappable");
    }

    /**
     * A kind the store refuses would be a mapping the engine could never persist, so the set is checked against the
     * constructor that validates a pending condition.
     */
    @Test
    @DisplayName("every mappable condition kind is a kind the status store accepts")
    void everyMappableKindIsAPersistedConditionKind() {
        assertAll(OutcomeReason.MAPPABLE_CONDITION_KINDS.stream()
                .<Executable>map(kind -> () -> assertDoesNotThrow(
                        () -> new WorkflowStatus.PendingCondition(kind, 0, null, "{}", Instant.now()),
                        "the persisted kind accepts " + kind)));
    }

    /**
     * The engine owns these conditions; a primitive that mapped a reason to one of them would claim a cause it does
     * not originate (Decision 6).
     */
    @Test
    @DisplayName("the engine's own conditions are not mappable, and neither is an integrity breach")
    void engineOwnedConditionsAreNotMappable() {
        assertAll(
                () -> assertFalse(OutcomeReason.MAPPABLE_CONDITION_KINDS.contains("integrity_breach"),
                        "an integrity breach names its store, so it is not mappable"),
                () -> assertFalse(OutcomeReason.MAPPABLE_CONDITION_KINDS.contains("guard_indeterminate"),
                        "a guard indeterminacy belongs to the engine"),
                () -> assertFalse(OutcomeReason.MAPPABLE_CONDITION_KINDS.contains("job_incomplete"),
                        "a job incompleteness belongs to the engine"),
                () -> assertFalse(OutcomeReason.MAPPABLE_CONDITION_KINDS.contains("config_drift"),
                        "a configuration drift belongs to the engine"));
    }

    @Test
    @DisplayName("a reason maps to no condition unless it says otherwise")
    void conditionKindIsEmptyByDefault() {
        assertEquals(Optional.empty(), TestReason.COMMIT_FAILED.conditionKind(), "no condition by default");
    }

    /**
     * The wire names and condition kinds are the contract, so the fixture states them literally.
     */
    enum TestReason implements OutcomeReason {
        FOOTPRINT("footprint_violation", "footprint_violation"),
        COMMIT_FAILED("commit_failed", null);

        private final String wireName;
        private final String conditionKind;

        TestReason(String wireName, String conditionKind) {
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
}
