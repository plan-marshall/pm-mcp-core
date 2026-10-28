/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.spi.validation;

import java.util.List;

import de.planmarshall.core.store.IntentRecord;
import de.planmarshall.core.store.IntentRecord.Constraint;
import de.planmarshall.core.store.IntentRecord.ConstraintSource;
import de.planmarshall.core.store.IntentRecord.Criterion;
import de.planmarshall.core.store.IntentRecord.CriterionKind;
import de.planmarshall.core.store.IntentRecord.Decider;
import de.planmarshall.core.store.IntentRecord.Decision;
import de.planmarshall.core.store.IntentRecord.NonGoal;
import de.planmarshall.core.store.IntentRecord.SourceClaim;
import de.planmarshall.core.store.IntentRecord.VerifiedBy;
import de.planmarshall.core.store.OutlineRecord;
import de.planmarshall.core.store.OutlineRecord.AffectedFile;
import de.planmarshall.core.store.OutlineRecord.ChangeType;
import de.planmarshall.core.store.OutlineRecord.Deliverable;
import de.planmarshall.core.store.OutlineRecord.ExecutionMode;
import de.planmarshall.core.store.OutlineRecord.FileIntent;
import de.planmarshall.core.store.OutlineRecord.Profile;
import de.planmarshall.core.store.OutlineRecord.ScopeEstimate;
import de.planmarshall.core.store.OutlineRecord.Verification;

/** Records and outlines the validation tests share. */
final class ValidationFixtures {

    private ValidationFixtures() {
    }

    static IntentRecord intent(Criterion... criteria) {
        return new IntentRecord(
                "The operator cannot see what a plan intends before it is built.",
                "A plan states its intent in one record that every later phase reads.",
                List.of(criteria),
                null,
                List.of(new NonGoal("NG-1", "The record is not edited by a later phase.")),
                List.of(new Constraint("CN-1", "A record is never accepted half-written.", ConstraintSource.OPERATOR,
                        null)),
                List.of(new Decision("DC-1", "The record is one file beside request.md", List.of("a store", "one file"),
                        Decider.MODEL, null, null, null)),
                List.of(), List.of(), List.of(),
                List.of(new SourceClaim("The operator wants the intent visible", "goal", null),
                        new SourceClaim("A half-written record would mislead the phases", "CN-1", null)));
    }

    static IntentRecord intentWithoutConstraints(Criterion... criteria) {
        return new IntentRecord(
                "The operator cannot see what a plan intends before it is built.",
                "A plan states its intent in one record that every later phase reads.",
                List.of(criteria),
                null,
                List.of(new NonGoal("NG-1", "The record is not edited by a later phase.")),
                List.of(),
                List.of(new Decision("DC-1", "The record is one file beside request.md", List.of("a store", "one file"),
                        Decider.MODEL, null, null, null)),
                List.of(), List.of(), List.of(),
                List.of(new SourceClaim("The operator wants the intent visible", "goal", null)));
    }

    static Criterion criterion(String id, CriterionKind kind, VerifiedBy verifiedBy) {
        var text = kind == CriterionKind.UNCHANGED
                ? "WHEN a plan is refined THE system SHALL CONTINUE TO keep the previous record intact"
                : "WHEN a plan is refined THE system SHALL write the intent record beside request.md";
        return new Criterion(id, text, kind, verifiedBy, null);
    }

    static OutlineRecord outline(Deliverable... deliverables) {
        return new OutlineRecord("The deliverables that implement the intent record", null,
                ScopeEstimate.SINGLE_MODULE, List.of(deliverables));
    }

    static Deliverable deliverable(int number, List<Integer> depends, String... satisfies) {
        return new Deliverable(number, "Deliverable " + number, "The deliverable " + number,
                ChangeType.FEATURE, ExecutionMode.AUTOMATED, "core", "pm-core", depends, null,
                List.of(Profile.IMPLEMENTATION),
                List.of(new AffectedFile("pm-core/src/main/java/%d.java".formatted(number), FileIntent.WRITE_NEW,
                        null)),
                "Change the file of the deliverable " + number,
                List.of(new Verification("mvn verify", "the build is green")), List.of(satisfies),
                List.of(), List.of(), List.of());
    }
}
