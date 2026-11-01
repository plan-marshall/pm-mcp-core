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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A result pairs its reason with the outcome that declares one: present exactly when the outcome declares a reason
 * type, and then an instance of it (Decision 4, Workflow DSL § Outcomes and Reasons).
 */
@EnableGeneratorController
@DisplayName("Result of an execution with a reason")
class PrimitiveResultTest {

    @Test
    @DisplayName("a result carries the reason its outcome declares")
    void acceptsDeclaredReason() {
        var result = new PrimitiveResult<>(ReasonedOutcome.FAILED, Map.of(), Optional.empty(),
                Optional.of(TestReason.FOOTPRINT));

        assertEquals(Optional.of(TestReason.FOOTPRINT), result.reason(), "the declared reason is carried");
    }

    @Test
    @DisplayName("an outcome that declares reasons refuses a result without one")
    void refusesMissingReason() {
        var thrown = assertThrows(IllegalArgumentException.class,
                () -> new PrimitiveResult<>(ReasonedOutcome.FAILED, Map.of(), Optional.empty(), Optional.empty()));

        assertTrue(thrown.getMessage().contains("reason"), "the refusal names the missing reason");
    }

    @Test
    @DisplayName("a reason of another enum is refused")
    void refusesForeignReason() {
        var foreignReason = Optional.<OutcomeReason>of(OtherReason.OTHER);
        var thrown = assertThrows(IllegalArgumentException.class,
                () -> new PrimitiveResult<>(ReasonedOutcome.FAILED, Map.of(), Optional.empty(), foreignReason));

        assertTrue(thrown.getMessage().contains("reason"), "the refusal names the foreign reason");
    }

    @Test
    @DisplayName("an outcome that declares no reason refuses one")
    void refusesReasonOnUnadmittedOutcome() {
        var unadmittedReason = Optional.<OutcomeReason>of(TestReason.FOOTPRINT);
        var thrown = assertThrows(IllegalArgumentException.class,
                () -> new PrimitiveResult<>(PlainOutcome.DONE, Map.of(), Optional.empty(), unadmittedReason));

        assertTrue(thrown.getMessage().contains("reason"), "the refusal names the unadmitted reason");
    }

    @Test
    @DisplayName("the three-component form carries no reason and keeps the facts copy")
    void threeComponentFormCarriesNoReason() {
        var factName = Generators.nonEmptyStrings().next();
        var factValue = Generators.nonEmptyStrings().next();
        var facts = new HashMap<String, Object>();
        facts.put(factName, factValue);
        var result = new PrimitiveResult<>(PlainOutcome.DONE, facts, Optional.empty());

        facts.put(factName, Generators.nonEmptyStrings().next());

        assertAll(
                () -> assertEquals(Optional.empty(), result.reason(), "the three-component form carries no reason"),
                () -> assertEquals(Map.of(factName, factValue), result.updatedFacts(), "the facts are copied"));
    }

    @Test
    @DisplayName("the three-component form refuses an outcome that declares a reason")
    void threeComponentFormRefusesMissingReason() {
        var thrown = assertThrows(IllegalArgumentException.class,
                () -> new PrimitiveResult<>(ReasonedOutcome.FAILED, Map.of(), Optional.empty()));

        assertTrue(thrown.getMessage().contains("reason"), "the refusal names the missing reason");
    }

    @Test
    @DisplayName("the builder defaults both optional components to empty")
    void builderDefaultsOptionalComponents() {
        var result = PrimitiveResult.<PlainOutcome>builder().outcome(PlainOutcome.DONE).build();

        assertAll(
                () -> assertEquals(Optional.empty(), result.reason(), "the builder defaults the reason to empty"),
                () -> assertEquals(Optional.empty(), result.spawnedJobId(), "the builder defaults the job id to empty"),
                () -> assertEquals(Map.of(), result.updatedFacts(), "the builder defaults the facts to empty"));
    }

    @Test
    @DisplayName("the builder carries a reason the outcome declares")
    void builderCarriesDeclaredReason() {
        var result = PrimitiveResult.<ReasonedOutcome>builder().outcome(ReasonedOutcome.FAILED)
                .reason(Optional.of(TestReason.FOOTPRINT)).build();

        assertEquals(Optional.of(TestReason.FOOTPRINT), result.reason(), "the builder carries the reason");
    }

    @Test
    @DisplayName("a null reason component is refused")
    void refusesNullReason() {
        var thrown = assertThrows(NullPointerException.class,
                () -> new PrimitiveResult<>(PlainOutcome.DONE, Map.of(), Optional.empty(), null));

        assertEquals("reason", thrown.getMessage(), "the refusal names the null component");
    }

    /**
     * The wire names are the contract of the SPI, so the fixture states them literally.
     */
    enum TestReason implements OutcomeReason {
        FOOTPRINT("footprint_violation"),
        COMMIT_FAILED("commit_failed");

        private final String wireName;

        TestReason(String wireName) {
            this.wireName = wireName;
        }

        @Override
        public String wireName() {
            return wireName;
        }
    }

    enum OtherReason implements OutcomeReason {
        OTHER("other");

        private final String wireName;

        OtherReason(String wireName) {
            this.wireName = wireName;
        }

        @Override
        public String wireName() {
            return wireName;
        }
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
            return Optional.of(TestReason.class);
        }
    }

    enum PlainOutcome implements PrimitiveOutcome {
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
}
