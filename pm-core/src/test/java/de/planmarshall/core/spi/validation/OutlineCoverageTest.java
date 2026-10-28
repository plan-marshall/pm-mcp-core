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

import static de.planmarshall.core.spi.validation.ValidationFixtures.criterion;
import static de.planmarshall.core.spi.validation.ValidationFixtures.deliverable;
import static de.planmarshall.core.spi.validation.ValidationFixtures.intent;
import static de.planmarshall.core.spi.validation.ValidationFixtures.outline;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.IntentRecord;
import de.planmarshall.core.store.IntentRecord.CriterionKind;
import de.planmarshall.core.store.IntentRecord.VerifiedBy;
import de.planmarshall.core.store.OutlineRecord;

@DisplayName("The outline's satisfies coverage")
class OutlineCoverageTest {

    private final OutlineValidator validator = new OutlineValidator();

    @DisplayName("The guard of an unchanged criterion may not run after the change it guards")
    @Test
    void refusesGuardDownstreamDirectly() {
        var intent = intent(criterion("AC-1", CriterionKind.NEW, VerifiedBy.TEST),
                criterion("AC-2", CriterionKind.UNCHANGED, VerifiedBy.TEST));
        var outline = outline(deliverable(1, List.of(), "AC-1"), deliverable(2, List.of(1), "AC-2"));

        var violations = validator.validate(outline, intent);

        assertEquals(1, violations.size());
        assertEquals(OutlineValidator.RULE_UNCHANGED_GUARD_DOWNSTREAM, violations.getFirst().ruleKey());
        assertTrue(violations.getFirst().message().contains("AC-2"));
        assertTrue(violations.getFirst().message().contains("deliverable 2"));
        assertTrue(violations.getFirst().message().contains("deliverable 1"));
    }

    @DisplayName("The guard is refused through two other deliverables")
    @Test
    void refusesGuardThroughTwoOthers() {
        var intent = intent(criterion("AC-1", CriterionKind.NEW, VerifiedBy.TEST),
                criterion("AC-2", CriterionKind.UNCHANGED, VerifiedBy.TEST));
        var outline = outline(deliverable(1, List.of(), "AC-1"), deliverable(2, List.of(1), "DC-1"),
                deliverable(3, List.of(2), "AC-2"));

        var violations = validator.validate(outline, intent);

        assertEquals(1, violations.size());
        assertTrue(violations.getFirst().message().contains("deliverable 3"));
        assertTrue(violations.getFirst().message().contains("deliverable 1"));
    }

    @DisplayName("A deliverable that satisfies both kinds is not refused")
    @Test
    void acceptsDeliverableSatisfyingBothKinds() {
        var intent = intent(criterion("AC-1", CriterionKind.NEW, VerifiedBy.TEST),
                criterion("AC-2", CriterionKind.UNCHANGED, VerifiedBy.TEST));
        var outline = outline(deliverable(1, List.of(), "AC-1", "AC-2"));

        assertTrue(validator.validate(outline, intent).isEmpty());
    }

    @DisplayName("An unchanged criterion verified by inspection or demonstration is not checked")
    @Test
    void doesNotCheckNonTestGuards() {
        var intent = intent(criterion("AC-1", CriterionKind.NEW, VerifiedBy.TEST),
                criterion("AC-2", CriterionKind.UNCHANGED, VerifiedBy.INSPECTION));
        var outline = outline(deliverable(1, List.of(), "AC-1"), deliverable(2, List.of(1), "AC-2"));

        assertTrue(validator.validate(outline, intent).isEmpty());
    }

    @DisplayName("A plan without an unchanged criterion is unaffected")
    @Test
    void planWithoutUnchangedCriterionIsUnaffected() {
        var intent = intent(criterion("AC-1", CriterionKind.NEW, VerifiedBy.TEST));
        var outline = outline(deliverable(1, List.of(), "AC-1"));
        assertTrue(validator.validate(outline, intent).isEmpty());
    }

    @DisplayName("Matched control: the guard first, the change after it, is accepted")
    @Test
    void acceptsGuardFirst() {
        var intent = intent(criterion("AC-1", CriterionKind.NEW, VerifiedBy.TEST),
                criterion("AC-2", CriterionKind.UNCHANGED, VerifiedBy.TEST));
        var outline = outline(deliverable(1, List.of(), "AC-2"), deliverable(2, List.of(1), "AC-1"));

        assertTrue(validator.validate(outline, intent).isEmpty());
    }

    @DisplayName("A satisfies entry the record does not hold is refused")
    @Test
    void refusesUnknownSatisfiesEntry() {
        var intent = intent(criterion("AC-1", CriterionKind.NEW, VerifiedBy.TEST));
        var outline = outline(deliverable(1, List.of(), "AC-9"));

        var violations = validator.validate(outline, intent);

        assertTrue(violations.stream().anyMatch(violation ->
                OutlineValidator.RULE_SATISFIES_UNKNOWN.equals(violation.ruleKey())));
        assertTrue(violations.stream().anyMatch(violation ->
                OutlineValidator.RULE_SATISFIES_UNCOVERED.equals(violation.ruleKey())));
    }

    @DisplayName("A criterion no deliverable names is refused, and an empty satisfies too")
    @Test
    void refusesUncoveredCriterionAndEmptySatisfies() {
        var intent = intent(criterion("AC-1", CriterionKind.NEW, VerifiedBy.TEST),
                criterion("AC-2", CriterionKind.NEW, VerifiedBy.TEST));
        var outline = outline(deliverable(1, List.of(), "AC-1"), deliverable(2, List.of()));

        var violations = validator.validate(outline, intent);

        assertTrue(violations.stream().anyMatch(violation ->
                OutlineValidator.RULE_SATISFIES_UNCOVERED.equals(violation.ruleKey())
                        && violation.message().contains("AC-2")));
        assertTrue(violations.stream().anyMatch(violation ->
                OutlineValidator.RULE_SATISFIES_EMPTY.equals(violation.ruleKey())
                        && violation.message().contains("deliverable 2")));
    }

    @DisplayName("The record-based check does not weaken the text check")
    @Test
    void textCheckStaysIndependent() {
        assertTrue(validator.validate("## 1. Deliverable\n\n**Affected files:**\n- `A.java` (write-new)\n").isEmpty());
    }

    @DisplayName("The intent record is required")
    @Test
    void refusesNullArguments() {
        var validIntent = intent(criterion("AC-1", CriterionKind.NEW, VerifiedBy.TEST));
        assertThrows(NullPointerException.class, () -> validator.validate((OutlineRecord) null, validIntent));
        var validOutline = outline(deliverable(1, List.of(), "AC-1"));
        assertThrows(NullPointerException.class, () -> validator.validate(validOutline, (IntentRecord) null));
    }
}
