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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.workflow.ast.SourceLocation;

/**
 * The values of the static verifier (PM-IMPL-5): the rule vocabulary with the specification rule of every check, a
 * violation that refuses a missing component, and a result that keeps an immutable copy of its violations.
 */
@DisplayName("VerificationResult, Violation and VerificationRule")
class VerificationResultTest {

    private static final SourceLocation LOCATION = new SourceLocation("inline.pmwf", 1, 1);

    @Test
    @DisplayName("Should name the specification rule of every check")
    void shouldNameSpecificationRules() {
        var byRule = EnumSet.allOf(VerificationRule.class).stream()
                .collect(Collectors.toMap(rule -> rule, VerificationRule::specificationRule));

        assertEquals(Map.ofEntries(
                Map.entry(VerificationRule.PRIMITIVE_BINDING, 1),
                Map.entry(VerificationRule.PARAMETER_EQUIVALENCE, 1),
                Map.entry(VerificationRule.ENTITY_KIND_BINDING, 1),
                Map.entry(VerificationRule.ROLE_NAME_GRAMMAR, 1),
                Map.entry(VerificationRule.SCHEMA_BINDING, 1),
                Map.entry(VerificationRule.QUESTION_CLASS_BINDING, 1),
                Map.entry(VerificationRule.JOB_STARTING_OUTCOME, 1),
                Map.entry(VerificationRule.OUTCOME_COVERAGE, 2),
                Map.entry(VerificationRule.MODEL_JOB_OUTCOMES, 2),
                Map.entry(VerificationRule.QUESTION_OUTCOMES, 2),
                Map.entry(VerificationRule.STATE_ID_UNIQUE, 3),
                Map.entry(VerificationRule.STATE_EXISTS, 3),
                Map.entry(VerificationRule.REACHABILITY, 3),
                Map.entry(VerificationRule.CONFIG_REACHABILITY, 3),
                Map.entry(VerificationRule.ABORT_TERMINAL_TARGETING, 3),
                Map.entry(VerificationRule.TARGET_NOT_RESERVED, 3),
                Map.entry(VerificationRule.EXIT_PATH, 3),
                Map.entry(VerificationRule.LINK_ID_UNIQUE, 3),
                Map.entry(VerificationRule.LEAF_TASK_CONSTRAINT, 4),
                Map.entry(VerificationRule.RELEASED_STATE, 5),
                Map.entry(VerificationRule.SUBWORKFLOW_CALL_CONTRACT, 6),
                Map.entry(VerificationRule.CALL_GRAPH_DAG, 7),
                Map.entry(VerificationRule.GATE_PRODUCER_COVERAGE, 8),
                Map.entry(VerificationRule.STEP_COMPLETION, 9),
                Map.entry(VerificationRule.SESSION_DRIVEN_LINKS, 10),
                Map.entry(VerificationRule.CYCLE_AWAITED_EVENT, 11),
                Map.entry(VerificationRule.LINK_COMPATIBILITY, 12)), byRule);
    }

    @Test
    @DisplayName("Should keep an immutable copy of the violations and pass only without one")
    void shouldCopyViolations() {
        var violation = new Violation(VerificationRule.REACHABILITY, "verify.entry", LOCATION, "unreachable");
        var source = new ArrayList<>(List.of(violation));

        var result = new VerificationResult(source);
        source.clear();
        var violations = result.violations();

        assertAll(() -> assertEquals(List.of(violation), violations),
                () -> assertFalse(result.passed()),
                () -> assertTrue(new VerificationResult(List.of()).passed()),
                () -> assertThrows(UnsupportedOperationException.class, violations::clear));
    }

    @Test
    @DisplayName("Should refuse a violation with a missing component")
    void shouldRefuseIncompleteViolation() {
        var rule = VerificationRule.EXIT_PATH;
        assertAll(
                () -> assertThrows(NullPointerException.class, () -> new Violation(null, "s", LOCATION, "m")),
                () -> assertThrows(NullPointerException.class, () -> new Violation(rule, null, LOCATION, "m")),
                () -> assertThrows(NullPointerException.class, () -> new Violation(rule, "s", null, "m")),
                () -> assertThrows(NullPointerException.class, () -> new Violation(rule, "s", LOCATION, null)));
    }
}
