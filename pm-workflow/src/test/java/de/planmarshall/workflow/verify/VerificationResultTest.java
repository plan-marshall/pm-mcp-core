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
 * violation that refuses a missing component, and a result that keeps immutable copies of its violations and its
 * advisories and passes on its violations alone.
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

        var result = new VerificationResult(source, List.of());
        source.clear();
        var violations = result.violations();

        assertAll(() -> assertEquals(List.of(violation), violations),
                () -> assertFalse(result.passed()),
                () -> assertTrue(new VerificationResult(List.of(), List.of()).passed()),
                () -> assertThrows(UnsupportedOperationException.class, violations::clear));
    }

    @Test
    @DisplayName("Should keep an immutable copy of the advisories and pass whatever they hold")
    void shouldCopyAdvisoriesWithoutFailing() {
        var advisory = new Violation(VerificationRule.CONFIG_REACHABILITY, "verify.entry", LOCATION, "partial");
        var source = new ArrayList<>(List.of(advisory));

        var result = new VerificationResult(List.of(), source);
        source.clear();
        var advisories = result.advisories();

        assertAll(() -> assertEquals(List.of(advisory), advisories),
                () -> assertEquals(List.of(), result.violations()),
                () -> assertTrue(result.passed()),
                () -> assertThrows(UnsupportedOperationException.class, advisories::clear));
    }

    @Test
    @DisplayName("Should refuse a missing list or a missing element in either list")
    void shouldRefuseIncompleteResult() {
        var withNull = new ArrayList<Violation>();
        withNull.add(null);
        List<Violation> empty = List.of();
        assertAll(
                () -> assertThrows(NullPointerException.class, () -> new VerificationResult(null, empty)),
                () -> assertThrows(NullPointerException.class, () -> new VerificationResult(empty, null)),
                () -> assertThrows(NullPointerException.class, () -> new VerificationResult(withNull, empty)),
                () -> assertThrows(NullPointerException.class, () -> new VerificationResult(empty, withNull)));
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

    @Test
    @DisplayName("Should order violations by state declaration order then rule ordinal")
    void shouldOrderViolationsByStateThenRule() {
        var validator = new WorkflowValidator(TestPrimitives.registry(), List.of(),
                EntityKindRegistry.defaults(), QuestionClassRegistry.defaults(), SchemaRegistry.defaults(),
                TestPrimitives.catalog());

        var byState = VerifyFixtures.parseSource("ordering-state.pmwf", """
                workflow verify.ordering-state version 1 format 1
                scope subworkflow
                result schema[unregistered-schema]
                start verify.begin

                state verify.a
                    phase verify
                    skill pm/verify/start
                    facts test.missing
                    link next proceed ()
                        do test.proceed
                            done -> verify.b

                state verify.b
                    phase verify
                    skill pm/verify/start
                    link next proceed ()
                        do test.proceed
                            done -> verify.t

                state verify.begin
                    phase verify
                    skill pm/verify/start
                    link next proceed ()
                        do test.proceed
                            done -> verify.a

                state verify.t terminal done success
                    phase verify
                """);
        var byStateFound = validator.verify(byState).violations();
        assertEquals(
                List.of(
                        Map.entry(VerificationRule.PRIMITIVE_BINDING, "verify.a"),
                        Map.entry(VerificationRule.SCHEMA_BINDING, "verify.begin")),
                byStateFound.stream().map(v -> Map.entry(v.rule(), v.stateId())).toList(),
                () -> "expected declaration order, not discovery order: " + byStateFound);

        var byRule = VerifyFixtures.parseSource("ordering-rule.pmwf", """
                workflow verify.ordering-rule version 1 format 1
                scope subworkflow
                start verify.entry

                state verify.entry
                    phase verify
                    skill pm/verify/start
                    facts test.missing
                    link next proceed ()
                        do test.proceed
                            done -> verify.ghost
                """);
        var byRuleFound = validator.verify(byRule).violations();
        assertEquals(
                List.of(
                        Map.entry(VerificationRule.PRIMITIVE_BINDING, "verify.entry"),
                        Map.entry(VerificationRule.STATE_EXISTS, "verify.entry")),
                byRuleFound.stream().map(v -> Map.entry(v.rule(), v.stateId())).toList(),
                () -> "expected rule ordinal, not discovery order: " + byRuleFound);
    }
}
