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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import de.planmarshall.core.store.IntentAcceptance.AnswerChannel;
import de.planmarshall.core.store.IntentAcceptance.AnsweredPart;
import de.planmarshall.core.store.IntentAcceptance.Context;
import de.planmarshall.core.store.IntentRecord.Assumption;
import de.planmarshall.core.store.IntentRecord.AssumptionStatus;
import de.planmarshall.core.store.IntentRecord.Constraint;
import de.planmarshall.core.store.IntentRecord.ConstraintSource;
import de.planmarshall.core.store.IntentRecord.Criterion;
import de.planmarshall.core.store.IntentRecord.CriterionKind;
import de.planmarshall.core.store.IntentRecord.Decider;
import de.planmarshall.core.store.IntentRecord.Decision;
import de.planmarshall.core.store.IntentRecord.NonGoal;
import de.planmarshall.core.store.IntentRecord.SourceClaim;
import de.planmarshall.core.store.IntentRecord.VerifiedBy;
import de.planmarshall.core.store.IntentStore.Revision;

/** Records and contexts the intent-store tests share. */
@EnableGeneratorController
final class IntentFixtures {

    /** The plan id of the fixture: an identifier the record and its acceptance are addressed by. */
    static final String PLAN_ID = "106-plan-intent-and-outline-records";

    static final Instant AT = Instant.ofEpochSecond(Generators.longs(1_600_000_000L, 1_900_000_000L).next());

    private IntentFixtures() {
    }

    static IntentRecord valid() {
        return new IntentRecord(
                Generators.letterStrings(20, 60).next(),
                Generators.letterStrings(10, 40).next(),
                List.of(criterion("AC-1", CriterionKind.NEW, VerifiedBy.TEST),
                        criterion("AC-2", CriterionKind.UNCHANGED, VerifiedBy.TEST)),
                null,
                List.of(new NonGoal("NG-1", Generators.letterStrings(5, 40).next())),
                List.of(new Constraint("CN-1", Generators.letterStrings(10, 60).next(), ConstraintSource.OPERATOR,
                        null)),
                List.of(new Decision("DC-1", Generators.letterStrings(10, 60).next(), options(), Decider.MODEL, null,
                        null, null)),
                List.of(new Assumption("AN-1", Generators.letterStrings(5, 40).next(), AssumptionStatus.VERIFIED,
                        false, null)),
                List.of(),
                List.of(),
                List.of(new SourceClaim("The operator wants the intent visible", "goal", null),
                        new SourceClaim(Generators.letterStrings(10, 60).next(), "CN-1", null)));
    }

    private static List<String> options() {
        return List.of(Generators.letterStrings(2, 20).next(), Generators.letterStrings(2, 20).next());
    }

    static IntentRecord withCriteria(List<Criterion> criteria) {
        var base = valid();
        return new IntentRecord(base.why(), base.goal(), criteria, base.currentBehaviour(), base.nonGoals(),
                base.constraints(), base.decisions(), base.assumptions(), base.openQuestions(), base.deferred(),
                base.sourceClaims());
    }

    static IntentRecord withDecisions(List<Decision> decisions) {
        var base = valid();
        return new IntentRecord(base.why(), base.goal(), base.criteria(), base.currentBehaviour(), base.nonGoals(),
                base.constraints(), decisions, base.assumptions(), base.openQuestions(), base.deferred(),
                base.sourceClaims());
    }

    static IntentRecord withClaims(List<SourceClaim> claims) {
        var base = valid();
        return new IntentRecord(base.why(), base.goal(), base.criteria(), base.currentBehaviour(), base.nonGoals(),
                base.constraints(), base.decisions(), base.assumptions(), base.openQuestions(), base.deferred(),
                claims);
    }

    static Criterion criterion(String id, CriterionKind kind, VerifiedBy verifiedBy) {
        var text = kind == CriterionKind.UNCHANGED
                ? "WHEN a plan is refined THE system SHALL CONTINUE TO keep the previous record intact"
                : "WHEN a plan is refined THE system SHALL write the intent record beside request.md";
        return new Criterion(id, text, kind, verifiedBy, null);
    }

    static Decision operatorDecision(String id, String questionId) {
        return new Decision(id, Generators.letterStrings(10, 60).next(), options(), Decider.OPERATOR, questionId,
                null, null);
    }

    static Revision revision(int number, IntentRecord intentRecord) {
        return new Revision(number, AT.plusSeconds(number), "TASK-00" + number, intentRecord);
    }

    static Context context(Path treeRoot) {
        return new Context(PLAN_ID, questionId -> Optional.empty(), (capability, requirement) -> Optional.empty(),
                Optional.empty(), treeRoot);
    }

    static Context answered(Path treeRoot, String questionId, String planId, AnswerChannel channel) {
        return new Context(PLAN_ID, asked -> questionId.equals(asked)
                ? Optional.of(new AnsweredPart(planId, channel))
                : Optional.empty(), (capability, requirement) -> Optional.empty(), Optional.empty(), treeRoot);
    }
}
