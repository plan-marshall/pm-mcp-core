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

import de.cuioss.test.generator.Generators;
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

    private static final String WHY = Generators.letterStrings(20, 60).next();
    private static final String GOAL = Generators.letterStrings(10, 40).next();
    private static final String NON_GOAL = Generators.letterStrings(5, 40).next();
    private static final String CONSTRAINT = Generators.letterStrings(5, 40).next();
    private static final String DECISION = Generators.letterStrings(5, 40).next();
    private static final String OPTION = Generators.letterStrings(5, 20).next();
    private static final String OTHER_OPTION = Generators.letterStrings(5, 20).next();
    private static final String CLAIM = Generators.letterStrings(5, 40).next();
    private static final String OTHER_CLAIM = Generators.letterStrings(5, 40).next();
    private static final String OUTLINE_TITLE = Generators.letterStrings(20, 60).next();
    private static final String DELIVERABLE_NAME = Generators.letterStrings(5, 20).next();
    private static final String DELIVERABLE_DESCRIPTION = Generators.letterStrings(5, 20).next();
    private static final String DELIVERABLE_CHANGE = Generators.letterStrings(10, 20).next();

    private ValidationFixtures() {
    }

    static IntentRecord intent(Criterion... criteria) {
        return new IntentRecord(
                WHY,
                GOAL,
                List.of(criteria),
                null,
                List.of(new NonGoal("NG-1", NON_GOAL)),
                List.of(new Constraint("CN-1", CONSTRAINT, ConstraintSource.OPERATOR, null)),
                List.of(new Decision("DC-1", DECISION, List.of(OPTION, OTHER_OPTION),
                        Decider.MODEL, null, null, null)),
                List.of(), List.of(), List.of(),
                List.of(new SourceClaim(CLAIM, "goal", null),
                        new SourceClaim(OTHER_CLAIM, "CN-1", null)));
    }

    static IntentRecord intentWithoutConstraints(Criterion... criteria) {
        return new IntentRecord(
                WHY,
                GOAL,
                List.of(criteria),
                null,
                List.of(new NonGoal("NG-1", NON_GOAL)),
                List.of(),
                List.of(new Decision("DC-1", DECISION, List.of(OPTION, OTHER_OPTION),
                        Decider.MODEL, null, null, null)),
                List.of(), List.of(), List.of(),
                List.of(new SourceClaim(CLAIM, "goal", null)));
    }

    static Criterion criterion(String id, CriterionKind kind, VerifiedBy verifiedBy) {
        var text = kind == CriterionKind.UNCHANGED
                ? "WHEN a plan is refined THE system SHALL CONTINUE TO keep the previous record intact"
                : "WHEN a plan is refined THE system SHALL write the intent record beside request.md";
        return new Criterion(id, text, kind, verifiedBy, null);
    }

    static OutlineRecord outline(Deliverable... deliverables) {
        return new OutlineRecord(OUTLINE_TITLE, null, ScopeEstimate.SINGLE_MODULE, List.of(deliverables));
    }

    static Deliverable deliverable(int number, List<Integer> depends, String... satisfies) {
        return new Deliverable(number, DELIVERABLE_NAME + " " + number, DELIVERABLE_DESCRIPTION + " " + number,
                ChangeType.FEATURE, ExecutionMode.AUTOMATED, "core", "pm-core", depends, null,
                List.of(Profile.IMPLEMENTATION),
                List.of(new AffectedFile("pm-core/src/main/java/%d.java".formatted(number), FileIntent.WRITE_NEW,
                        null)),
                DELIVERABLE_CHANGE + " " + number,
                List.of(new Verification("mvn verify", "the build is green")), List.of(satisfies),
                List.of(), List.of(), List.of());
    }
}
