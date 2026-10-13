/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.finalize;

import static de.planmarshall.workflow.finalize.FinalizeFixtures.passingRoster;
import static de.planmarshall.workflow.finalize.FinalizeFixtures.step;
import static de.planmarshall.workflow.finalize.FinalizeFixtures.withAdded;
import static de.planmarshall.workflow.finalize.FinalizeFixtures.withStep;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.workflow.finalize.FinalizeStepDescriptor.InputScope;

/**
 * The checks over a roster of finalize steps (PM-IMPL-5). The passing roster, written for the test, passes; each
 * checkable rule is refused by a roster that breaks it in one place, with the rule and the step named; every rule has
 * a refusing roster or a named hook, so a rule cannot be added unchecked.
 */
@DisplayName("FinalizePipelineValidator")
class FinalizePipelineValidatorTest {

    private static final String PRE_PUSH = "default:pre-push-quality-gate";
    private static final String PUSH = "default:push";
    private static final String RETROSPECTIVE = "default:plan-retrospective";
    private static final String BREAKDOWN = "default:print-phase-breakdown";
    private static final String SONAR = "default:sonar-roundtrip";

    static Stream<Arguments> refusals() {
        return Stream.of(
                refusal("an order collision", PipelineRule.ORDER_UNIQUE, "default:create-pr",
                        withStep("default:create-pr", step -> step.order(30))),
                refusal("the push not at 11", PipelineRule.PRE_PUSH_PLACEMENT, PUSH,
                        withStep(PUSH, step -> step.order(12))),
                refusal("the pre-push quality gate not at 10", PipelineRule.PRE_PUSH_PLACEMENT, PRE_PUSH,
                        withStep(PRE_PUSH, step -> step.order(9))),
                refusal("another step at the order of the push", PipelineRule.PRE_PUSH_PLACEMENT, "default:extra",
                        withAdded(step("default:extra", 11).build())),
                refusal("a mutating step at the merge gate", PipelineRule.SOURCE_MUTATION_BEFORE_MERGE_GATE,
                        "default:branch-cleanup",
                        withStep("default:branch-cleanup", FinalizeFixtures.Step::mutatesSource)),
                refusal("a mutating step after the merge gate", PipelineRule.SOURCE_MUTATION_BEFORE_MERGE_GATE,
                        "default:late", withAdded(step("default:late", 80).mutatesSource().build())),
                refusal("a post-run review step below 900", PipelineRule.POST_RUN_REVIEW_BAND, RETROSPECTIVE,
                        withStep(RETROSPECTIVE, step -> step.order(890))),
                refusal("a post-run review step above 1000", PipelineRule.POST_RUN_REVIEW_BAND, "default:late-review",
                        withAdded(step("default:late-review", 1001).postRunReview().build())),
                refusal("a post-run review step that mutates source", PipelineRule.POST_RUN_REVIEW_BAND, BREAKDOWN,
                        withStep(BREAKDOWN, FinalizeFixtures.Step::mutatesSource)),
                refusal("a read after the destroy", PipelineRule.READS_BEFORE_DESTROYS, "default:late-reader",
                        withAdded(step("default:late-reader", 80).reads("worktree").build())),
                refusal("a metrics consumer before 998 without the snapshot", PipelineRule.READS_BEFORE_DESTROYS,
                        RETROSPECTIVE, withStep(RETROSPECTIVE, FinalizeFixtures.Step::recordsFacts)),
                refusal("a head-dependent step without an input scope", PipelineRule.ANCHOR_AND_FACT_CONTRACT,
                        PRE_PUSH, withStep(PRE_PUSH, FinalizeFixtures.Step::headDependentWithoutScope)),
                refusal("the scope declared with empty verdict inputs", PipelineRule.ANCHOR_AND_FACT_CONTRACT, SONAR,
                        withStep(SONAR, step -> step.scope(InputScope.DECLARED))),
                refusal("another scope with verdict inputs", PipelineRule.ANCHOR_AND_FACT_CONTRACT, PRE_PUSH,
                        withStep(PRE_PUSH, step -> step.scope(InputScope.PLAN_FOOTPRINT, "coverage_report"))),
                refusal("an input scope on a step that is not head-dependent", PipelineRule.ANCHOR_AND_FACT_CONTRACT,
                        PUSH, withStep(PUSH, step -> step.scope(InputScope.REMOTE_STATE))));
    }

    private static Arguments refusal(String name, PipelineRule rule, String stepName,
            List<FinalizeStepDescriptor> roster) {
        return Arguments.of(name, rule, stepName, roster);
    }

    private static boolean names(List<PipelineViolation> violations, PipelineRule rule, String stepName) {
        return violations.stream().anyMatch(violation -> violation.rule() == rule
                && violation.stepName().equals(stepName) && !violation.message().isBlank());
    }

    @Nested
    @DisplayName("Passing roster")
    class Passing {

        @Test
        @DisplayName("passes every rule")
        void passes() {
            assertEquals(List.of(), FinalizePipelineValidator.validate(passingRoster()));
        }

        @Test
        @DisplayName("passes an empty roster")
        void emptyRoster() {
            assertEquals(List.of(), FinalizePipelineValidator.validate(List.of()));
        }

        @Test
        @DisplayName("passes a metrics consumer ordered after record-metrics without the snapshot label")
        void consumerAfterRecordMetrics() {
            var roster = withStep(BREAKDOWN, FinalizeFixtures.Step::recordsFacts);

            assertEquals(List.of(), FinalizePipelineValidator.validate(roster));
        }

        @Test
        @DisplayName("passes a step that reads and destroys the same artifact")
        void readsAndDestroysItself() {
            var roster = withAdded(step("default:cleanup-scratch", 50).reads("scratch").destroys("scratch").build());

            assertEquals(List.of(), FinalizePipelineValidator.validate(roster));
        }

        @Test
        @DisplayName("refuses a missing roster")
        void nullRoster() {
            assertAll(
                    () -> assertThrows(NullPointerException.class, () -> FinalizePipelineValidator.validate(null)),
                    () -> assertThrows(NullPointerException.class,
                            () -> FinalizePipelineValidator.findingSources(null)));
        }
    }

    @Nested
    @DisplayName("Refusing rosters")
    class Refusing {

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.finalize.FinalizePipelineValidatorTest#refusals")
        @DisplayName("refuses a roster that breaks a rule, naming the rule and the step")
        void refuses(String name, PipelineRule rule, String stepName, List<FinalizeStepDescriptor> roster) {
            var violations = FinalizePipelineValidator.validate(roster);

            assertTrue(names(violations, rule, stepName), () -> name + ": " + violations);
        }

        @Test
        @DisplayName("reports a collision on both steps")
        void collisionOnBothSteps() {
            var violations = FinalizePipelineValidator.validate(withStep("default:create-pr", step -> step.order(30)));

            assertAll(
                    () -> assertTrue(names(violations, PipelineRule.ORDER_UNIQUE, "default:create-pr"),
                            violations::toString),
                    () -> assertTrue(names(violations, PipelineRule.ORDER_UNIQUE, "default:automatic-review"),
                            violations::toString));
        }

        @Test
        @DisplayName("reports every violation, ordered by the order of the step and then by rule")
        void everyViolationInOrder() {
            var roster = withStep(BREAKDOWN, FinalizeFixtures.Step::mutatesSource).stream()
                    .map(descriptor -> "default:simplify".equals(descriptor.name())
                            ? FinalizeFixtures.Step.of(descriptor).headDependentWithoutScope().build()
                            : descriptor)
                    .toList();

            var violations = FinalizePipelineValidator.validate(roster);

            assertEquals(List.of("default:simplify/ANCHOR_AND_FACT_CONTRACT",
                            BREAKDOWN + "/SOURCE_MUTATION_BEFORE_MERGE_GATE", BREAKDOWN + "/POST_RUN_REVIEW_BAND"),
                    violations.stream().map(violation -> violation.stepName() + "/" + violation.rule()).toList());
        }
    }

    @Nested
    @DisplayName("Rule vocabulary")
    class RuleVocabulary {

        @ParameterizedTest(name = "{0}")
        @EnumSource(PipelineRule.class)
        @DisplayName("every rule has a refusing roster or a named hook")
        void checkedOrHooked(PipelineRule rule) {
            var refused = refusals().anyMatch(arguments -> arguments.get()[1] == rule);

            assertTrue(refused || rule.namedHook().isPresent(),
                    () -> rule + " has neither a refusing roster nor a named hook");
        }

        @Test
        @DisplayName("names the hooks of rule 6, rule 7 and rule 8, and only those")
        void namedHooks() {
            var hooked = Arrays.stream(PipelineRule.values())
                    .filter(rule -> rule.namedHook().filter(reason -> !reason.isBlank()).isPresent())
                    .map(PipelineRule::specificationRule)
                    .collect(Collectors.toSet());

            assertEquals(Set.of(6, 7, 8), hooked);
        }

        @Test
        @DisplayName("numbers the rules 1 to 8, once each")
        void numbering() {
            assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8),
                    Arrays.stream(PipelineRule.values()).map(PipelineRule::specificationRule).toList());
        }
    }

    @Nested
    @DisplayName("Finding sources")
    class FindingSources {

        @Test
        @DisplayName("derives the finding-filing steps as the steps with non-empty filesFindings")
        void derived() {
            var roster = passingRoster();

            var sources = FinalizePipelineValidator.findingSources(roster);

            assertAll(
                    () -> assertEquals(Set.of("default:automatic-review", SONAR), sources),
                    () -> assertEquals(roster.stream().filter(step -> !step.filesFindings().isEmpty())
                            .map(FinalizeStepDescriptor::name).collect(Collectors.toSet()), sources));
        }

        @Test
        @DisplayName("derives no source from a roster in which no step files findings")
        void none() {
            var roster = List.of(step("default:push", 11).build());

            assertTrue(FinalizePipelineValidator.findingSources(roster).isEmpty());
        }
    }
}
