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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.WaiverRequestOutcome;
import de.planmarshall.workflow.ast.StateBehavior;
import de.planmarshall.workflow.ast.UnitOrigin;
import de.planmarshall.workflow.dsl.ParseResult;
import de.planmarshall.workflow.dsl.WorkflowParser;

/**
 * A waiver request ends by the operator's act, and one of those acts is the decline (Workflow DSL, Waivers, Waiver
 * Requests): the waiver wait has no expiry by time, and its {@code declined} outcome leaves it on the unit's exhausted
 * transition.
 */
@DisplayName("Waiver request decline")
class WaiverDeclineTest {

    @Test
    @DisplayName("Should route the declined waiver request out of the waiver wait")
    void declinedLeavesTheWaiverWait() {
        var parsed = WorkflowParser.parse("remediation-qgate-findings.pmwf",
                VerifyFixtures.read("unit-fixtures/defaults/remediation-qgate-findings.pmwf"), UnitOrigin.DEFAULT);
        var unit = assertInstanceOf(ParseResult.Parsed.class, parsed, parsed::toString).definition();
        var wait = unit.states().get("remediation-qgate.waiver-wait");

        var job = assertInstanceOf(StateBehavior.JobStep.class, wait.behavior());
        var declined = job.transitions().stream().filter(transition -> "declined".equals(transition.outcomeName()))
                .toList();
        assertAll(
                () -> assertTrue(job.composite(), "the waiver wait is a composite job"),
                () -> assertEquals(1, declined.size()),
                () -> assertEquals("remediation-qgate.exhausted", declined.getFirst().targetStateId()),
                () -> assertTrue(job.transitions().stream()
                                .noneMatch(transition -> "expired".equals(transition.outcomeName())),
                        "no waiver request ends by time"),
                () -> assertEquals("declined", WaiverRequestOutcome.DECLINED.wireName()),
                () -> assertEquals(List.of(WaiverRequestOutcome.WAIVED, WaiverRequestOutcome.RAISED,
                                WaiverRequestOutcome.DECLINED, WaiverRequestOutcome.CANCELLED),
                        List.of(WaiverRequestOutcome.values())));
    }
}
