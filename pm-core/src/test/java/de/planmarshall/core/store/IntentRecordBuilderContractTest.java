/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.IntentRecord.AssumptionStatus;
import de.planmarshall.core.store.IntentRecord.ConstraintSource;
import de.planmarshall.core.store.IntentRecord.CriterionKind;
import de.planmarshall.core.store.IntentRecord.Decider;
import de.planmarshall.core.store.IntentRecord.VerifiedBy;

/**
 * The builders of {@link IntentRecord} and of its nested records build the values the canonical constructors build, and
 * an absent required component is refused with the same refusal.
 */
@EnableGeneratorController
@DisplayName("Intent record builders")
class IntentRecordBuilderContractTest {

    @Test
    @DisplayName("the record builder builds the constructor's value and refuses an absent why")
    void shouldBuildRecordLikeTheConstructorAndRefuseAnAbsentWhy() {
        var canonical = IntentFixtures.valid();
        var built = IntentRecord.builder().why(canonical.why()).goal(canonical.goal())
                .criteria(canonical.criteria()).currentBehaviour(canonical.currentBehaviour())
                .nonGoals(canonical.nonGoals()).constraints(canonical.constraints())
                .decisions(canonical.decisions()).assumptions(canonical.assumptions())
                .openQuestions(canonical.openQuestions()).deferred(canonical.deferred())
                .sourceClaims(canonical.sourceClaims()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new IntentRecord(null, canonical.goal(), canonical.criteria(),
                                    canonical.currentBehaviour(), canonical.nonGoals(), canonical.constraints(),
                                    canonical.decisions(), canonical.assumptions(), canonical.openQuestions(),
                                    canonical.deferred(), canonical.sourceClaims()),
                            "the constructor refuses a null why");
                    var incomplete = IntentRecord.builder().goal(canonical.goal())
                            .criteria(canonical.criteria())
                            .nonGoals(canonical.nonGoals()).constraints(canonical.constraints())
                            .decisions(canonical.decisions()).assumptions(canonical.assumptions())
                            .openQuestions(canonical.openQuestions()).deferred(canonical.deferred())
                            .sourceClaims(canonical.sourceClaims());
                    var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                            "the builder refuses a missing why");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent why with the same message");
                });
    }

    @Test
    @DisplayName("the criterion builder builds the constructor's value and refuses an absent id")
    void shouldBuildCriterionLikeTheConstructorAndRefuseAnAbsentId() {
        var canonical = IntentFixtures.criterion("AC-1", CriterionKind.NEW, VerifiedBy.TEST);
        var built = IntentRecord.Criterion.builder().id(canonical.id()).text(canonical.text())
                .kind(canonical.kind()).verifiedBy(canonical.verifiedBy()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new IntentRecord.Criterion(null, canonical.text(), CriterionKind.NEW,
                                    VerifiedBy.TEST),
                            "the constructor refuses a null criterion id");
                    var incomplete = IntentRecord.Criterion.builder().text(canonical.text())
                            .kind(CriterionKind.NEW).verifiedBy(VerifiedBy.TEST);
                    var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                            "the builder refuses a missing criterion id");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent criterion id with the same message");
                });
    }

    @Test
    @DisplayName("the constraint builder builds the constructor's value and refuses an absent id")
    void shouldBuildConstraintLikeTheConstructorAndRefuseAnAbsentId() {
        var text = Generators.letterStrings(10, 40).next();
        var canonical = new IntentRecord.Constraint("CN-1", text, ConstraintSource.OPERATOR, null);
        var built = IntentRecord.Constraint.builder().id(canonical.id()).text(canonical.text())
                .source(canonical.source()).evidence(canonical.evidence()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new IntentRecord.Constraint(null, text, ConstraintSource.OPERATOR, null),
                            "the constructor refuses a null constraint id");
                    var incomplete = IntentRecord.Constraint.builder().text(text)
                            .source(ConstraintSource.OPERATOR);
                    var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                            "the builder refuses a missing constraint id");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent constraint id with the same message");
                });
    }

    @Test
    @DisplayName("the decision builder builds the constructor's value and refuses an absent id")
    void shouldBuildDecisionLikeTheConstructorAndRefuseAnAbsentId() {
        var text = Generators.letterStrings(10, 40).next();
        var canonical = new IntentRecord.Decision("DC-1", text, List.of("first option", "second option"),
                Decider.MODEL, null, null, null);
        var built = IntentRecord.Decision.builder().id(canonical.id()).text(canonical.text())
                .optionsWeighed(canonical.optionsWeighed()).decider(canonical.decider())
                .questionId(canonical.questionId()).evidence(canonical.evidence()).adr(canonical.adr()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var optionsWeighed = List.of("first option");
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new IntentRecord.Decision(null, text, optionsWeighed, Decider.MODEL, null,
                                    null, null),
                            "the constructor refuses a null decision id");
                    var incomplete = IntentRecord.Decision.builder().text(text)
                            .optionsWeighed(List.of("first option")).decider(Decider.MODEL);
                    var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                            "the builder refuses a missing decision id");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent decision id with the same message");
                });
    }

    @Test
    @DisplayName("the assumption builder builds the constructor's value and refuses an absent id")
    void shouldBuildAssumptionLikeTheConstructorAndRefuseAnAbsentId() {
        var text = Generators.letterStrings(10, 40).next();
        var canonical = new IntentRecord.Assumption("AN-1", text, AssumptionStatus.VERIFIED, false, null);
        var built = IntentRecord.Assumption.builder().id(canonical.id()).text(canonical.text())
                .status(canonical.status()).riskiest(canonical.riskiest()).test(canonical.test()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new IntentRecord.Assumption(null, text, AssumptionStatus.VERIFIED, false, null),
                            "the constructor refuses a null assumption id");
                    var incomplete = IntentRecord.Assumption.builder().text(text)
                            .status(AssumptionStatus.VERIFIED).riskiest(false);
                    var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                            "the builder refuses a missing assumption id");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent assumption id with the same message");
                });
    }
}
