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
final class IntentFixtures {

    static final String PLAN_ID = "106-plan-intent-and-outline-records";

    static final Instant AT = Instant.parse("2026-10-27T10:00:00Z");

    private IntentFixtures() {
    }

    static IntentRecord valid() {
        return new IntentRecord(
                "The operator cannot see what a plan intends before it is built.",
                "A plan states its intent in one record that every later phase reads.",
                List.of(criterion("AC-1", CriterionKind.NEW, VerifiedBy.TEST),
                        criterion("AC-2", CriterionKind.UNCHANGED, VerifiedBy.TEST)),
                null,
                List.of(new NonGoal("NG-1", "The record is not edited by a later phase.")),
                List.of(new Constraint("CN-1", "A record is never accepted half-written.", ConstraintSource.OPERATOR,
                        null)),
                List.of(new Decision("DC-1", "The record is one file beside request.md", List.of("a store", "one file"),
                        Decider.MODEL, null, null, null)),
                List.of(new Assumption("AN-1", "The plan store is writable", AssumptionStatus.VERIFIED, false, null)),
                List.of(),
                List.of(),
                List.of(new SourceClaim("The operator wants the intent visible", "goal", null),
                        new SourceClaim("A half-written record would mislead the phases", "CN-1", null)));
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
        return new Decision(id, "The operator chose the one-file record", List.of("a store", "one file"),
                Decider.OPERATOR, questionId, null, null);
    }

    static Revision revision(int number, IntentRecord record) {
        return new Revision(number, AT.plusSeconds(number), "TASK-00" + number, record);
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
