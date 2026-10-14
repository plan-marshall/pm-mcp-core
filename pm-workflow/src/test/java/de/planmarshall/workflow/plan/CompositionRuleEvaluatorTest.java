/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.plan;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import de.planmarshall.workflow.ast.CallValue;
import de.planmarshall.workflow.plan.PlanInstanceHeader.Lane;
import de.planmarshall.workflow.plan.PlanInstanceHeader.Posture;
import de.planmarshall.workflow.plan.PrunedElement.Kind;

/**
 * The evaluation of the shipped composition rules against the facts of a plan (PM-IMPL-5, PM-EXT-7): each rule that
 * holds contributes exactly its prunes, with its reason and the fact it read as the source fact, and its bindings, in
 * rule order; with the fact not holding, the same rule contributes nothing. An unknown fact never makes a rule hold
 * except one asking whether it is unset, so an unreadable push remote or footprint is never read as "none".
 */
@DisplayName("CompositionRuleEvaluator")
class CompositionRuleEvaluatorTest {

    private static final CompositionRules SHIPPED = ((CompositionRules.Loaded) CompositionRules.shipped()).rules();

    private static RuleEffects evaluate(PlanFacts facts) {
        return CompositionRuleEvaluator.evaluate(SHIPPED, facts);
    }

    private static PrunedElement step(String id, PruneReason reason, String fact) {
        return new PrunedElement(Kind.STEP, id, reason, fact);
    }

    private static PrunedElement state(String id, PruneReason reason, String fact) {
        return new PrunedElement(Kind.STATE, id, reason, fact);
    }

    private static final RuleEffects NOTHING = new RuleEffects(List.of(), List.of());

    @Nested
    @DisplayName("Lanes")
    class Lanes {

        /**
         * A land plan never authors a fix (PM-IMPL-5): its instance binds every remediation call with
         * {@code actions=[retry]} and prunes task allocation, {@code loop_back} and the in-branch fix with reason
         * {@code land_lane}, so no remediation offers a fix or a loop-back on the user's landed work.
         */
        @Test
        @DisplayName("land binds every policy call to retry and prunes task allocation, fix and loop_back")
        void land() {
            var effects = evaluate(PlanFacts.builder().lane(Lane.LAND).build());

            assertEquals(new RuleEffects(
                    List.of(state("finalize.allocate-remediation", PruneReason.LAND_LANE, "lane"),
                            new PrunedElement(Kind.POLICY_ACTION, "*/fix", PruneReason.LAND_LANE, "lane"),
                            new PrunedElement(Kind.POLICY_ACTION, "*/loop_back", PruneReason.LAND_LANE, "lane"),
                            step("default:finalize-step-simplify", PruneReason.LAND_LANE, "lane")),
                    List.of(new CompositionRule.Binding("*", "actions",
                            new CallValue.EnumList(List.of("retry"))))), effects);
        }

        @Test
        @DisplayName("adopt prunes create-pr, since the adopted pull request exists")
        void adopt() {
            assertEquals(new RuleEffects(List.of(step("default:create-pr", PruneReason.ADOPT, "lane")), List.of()),
                    evaluate(PlanFacts.builder().lane(Lane.ADOPT).build()));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = Lane.class, names = {"LIGHT", "DEEP"})
        @DisplayName("a request plan binds nothing and prunes nothing for its lane, so fix stays among its actions")
        void requestPlanControl(Lane lane) {
            assertEquals(NOTHING, evaluate(PlanFacts.builder().lane(lane).build()));
        }
    }

    @Nested
    @DisplayName("Configuration")
    class Configuration {

        @Test
        @DisplayName("an empty push remote prunes every remote-bound step and keeps the two local gates")
        void localOnly() {
            var effects = evaluate(PlanFacts.builder().configuration("derived.push_remote", "").build());

            assertAll(
                    () -> assertEquals(List.of(
                                    step("default:push", PruneReason.LOCAL_ONLY, "derived.push_remote"),
                                    step("default:create-pr", PruneReason.LOCAL_ONLY, "derived.push_remote"),
                                    step("default:ci-verify", PruneReason.LOCAL_ONLY, "derived.push_remote"),
                                    step("default:automatic-review", PruneReason.LOCAL_ONLY, "derived.push_remote"),
                                    step("default:analysis-roundtrip", PruneReason.LOCAL_ONLY, "derived.push_remote")),
                            effects.prunes()),
                    () -> assertTrue(effects.prunes().stream().map(PrunedElement::id)
                            .noneMatch(id -> "default:pre-push-quality-gate".equals(id)
                                    || "default:pre-submission-self-review".equals(id))),
                    () -> assertEquals(List.of(), effects.bindings()));
        }

        @Test
        @DisplayName("a push remote, or one that could not be derived, prunes nothing")
        void localOnlyControl() {
            assertAll(
                    () -> assertEquals(NOTHING, evaluate(PlanFacts.builder()
                            .configuration("derived.push_remote", "origin").build())),
                    () -> assertEquals(NOTHING, evaluate(PlanFacts.builder().build())));
        }

        @Test
        @DisplayName("merge.auto_merge false prunes the merge execution and no gate")
        void autoMergeOff() {
            assertEquals(new RuleEffects(List.of(
                                    state("finalize.merge-dispatch", PruneReason.AUTO_MERGE_OFF, "merge.auto_merge"),
                                    state("finalize.merge-queue-wait", PruneReason.AUTO_MERGE_OFF, "merge.auto_merge"),
                                    state("finalize.merge-queue-exit", PruneReason.AUTO_MERGE_OFF, "merge.auto_merge")),
                            List.of()),
                    evaluate(PlanFacts.builder().configuration("merge.auto_merge", "false").build()));
        }

        @Test
        @DisplayName("merge.auto_merge true prunes nothing")
        void autoMergeOn() {
            assertEquals(NOTHING, evaluate(PlanFacts.builder().configuration("merge.auto_merge", "true").build()));
        }
    }

    @Nested
    @DisplayName("Posture")
    class PostureRules {

        @Test
        @DisplayName("minimal prunes the standard and full steps, then the steps that are off by default")
        void minimal() {
            assertEquals(List.of(
                            step("default:finalize-step-simplify", PruneReason.POSTURE, "posture"),
                            step("default:finalize-step-security-audit", PruneReason.POSTURE, "posture"),
                            step("default:pre-submission-self-review", PruneReason.POSTURE, "posture"),
                            step("default:automatic-review", PruneReason.POSTURE, "posture"),
                            step("default:analysis-roundtrip", PruneReason.POSTURE, "posture"),
                            step("default:lessons-capture", PruneReason.POSTURE, "posture"),
                            step("default:adr-propose", PruneReason.POSTURE, "posture"),
                            step("default:plan-retrospective", PruneReason.POSTURE, "posture")),
                    evaluate(PlanFacts.builder().posture(Posture.MINIMAL).build()).prunes());
        }

        @Test
        @DisplayName("standard prunes the full-tier security audit and the steps that are off by default")
        void standard() {
            assertEquals(List.of(
                            step("default:finalize-step-security-audit", PruneReason.POSTURE, "posture"),
                            step("default:adr-propose", PruneReason.POSTURE, "posture"),
                            step("default:plan-retrospective", PruneReason.POSTURE, "posture")),
                    evaluate(PlanFacts.builder().posture(Posture.STANDARD).build()).prunes());
        }

        @Test
        @DisplayName("full prunes only the steps that are off by default")
        void full() {
            assertEquals(List.of(
                            step("default:adr-propose", PruneReason.POSTURE, "posture"),
                            step("default:plan-retrospective", PruneReason.POSTURE, "posture")),
                    evaluate(PlanFacts.builder().posture(Posture.FULL).build()).prunes());
        }

        @Test
        @DisplayName("an unknown posture prunes nothing")
        void unknown() {
            assertEquals(NOTHING, evaluate(PlanFacts.builder().build()));
        }
    }

    @Nested
    @DisplayName("Post-run-only forms")
    class PostRun {

        @ParameterizedTest(name = "{0}")
        @CsvSource({"returned, RETURNED", "handed_back, HANDED_BACK", "empty_harvest, EMPTY_HARVEST"})
        @DisplayName("prune every step below order 900 with the reason of the form")
        void postRun(String form, PruneReason reason) {
            assertEquals(new RuleEffects(List.of(step("order<900", reason, "post_run")), List.of()),
                    evaluate(PlanFacts.builder().postRun(form).build()));
        }
    }

    @Nested
    @DisplayName("Evaluation")
    class Evaluation {

        @Test
        @DisplayName("the effects of several rules come in rule order")
        void ruleOrder() {
            var effects = evaluate(PlanFacts.builder().lane(Lane.LAND).posture(Posture.FULL)
                    .configuration("merge.auto_merge", "false").build());

            assertEquals(List.of("lane", "lane", "lane", "lane", "merge.auto_merge", "merge.auto_merge",
                            "merge.auto_merge", "posture", "posture"),
                    effects.prunes().stream().map(PrunedElement::sourceFact).toList());
        }

        @Test
        @DisplayName("an unset provider key holds the unset condition, a set one does not")
        void unset() {
            var rules = rules("{\"id\": \"p\", \"when\": {\"fact\": \"integrations.sonar\", \"unset\": true}, "
                    + "\"then\": {\"prune\": [{\"element\": \"step\", \"id\": \"default:analysis-roundtrip\", "
                    + "\"reason\": \"provider_unconfigured\"}]}}");

            assertAll(
                    () -> assertEquals(List.of(step("default:analysis-roundtrip", PruneReason.PROVIDER_UNCONFIGURED,
                                    "integrations.sonar")),
                            CompositionRuleEvaluator.evaluate(rules, PlanFacts.builder().build()).prunes()),
                    () -> assertEquals(NOTHING, CompositionRuleEvaluator.evaluate(rules, PlanFacts.builder()
                            .configuration("integrations.sonar", "sonarcloud").build())));
        }

        @Test
        @DisplayName("a footprint is disjoint only when it is known, non-empty and matches no glob")
        void disjoint() {
            var rules = rules("{\"id\": \"f\", \"when\": {\"fact\": \"footprint\", \"disjoint\": [\"**/*.java\", "
                    + "\"pom.xml\"]}, \"then\": {\"prune\": [{\"element\": \"step\", \"id\": \"default:x\", "
                    + "\"reason\": \"footprint_disjoint\"}]}}");

            assertAll(
                    () -> assertEquals(1, CompositionRuleEvaluator.evaluate(rules, PlanFacts.builder()
                            .footprint(List.of("doc/a.adoc", "README.md")).build()).prunes().size()),
                    () -> assertEquals(NOTHING, CompositionRuleEvaluator.evaluate(rules, PlanFacts.builder()
                            .footprint(List.of("doc/a.adoc", "src/main/java/A.java")).build())),
                    () -> assertEquals(NOTHING, CompositionRuleEvaluator.evaluate(rules, PlanFacts.builder()
                            .footprint(List.of("pom.xml")).build())),
                    () -> assertEquals(NOTHING, CompositionRuleEvaluator.evaluate(rules, PlanFacts.builder()
                            .footprint(List.of()).build())),
                    () -> assertEquals(NOTHING, CompositionRuleEvaluator.evaluate(rules, PlanFacts.builder()
                            .build())));
        }

        @Test
        @DisplayName("a single star never crosses a directory, a question mark matches one character")
        void globSegments() {
            var pattern = CompositionRule.Disjoint.compile("doc/*.ado?");

            assertAll(
                    () -> assertTrue(pattern.matcher("doc/a.adoc").matches()),
                    () -> assertFalse(pattern.matcher("doc/sub/a.adoc").matches()),
                    () -> assertFalse(pattern.matcher("doc/a.ad").matches()),
                    () -> assertTrue(CompositionRule.Disjoint.compile("a+b.txt").matcher("a+b.txt").matches()));
        }

        @Test
        @DisplayName("the in condition holds for a listed value only, the empty condition for an empty list")
        void inAndEmpty() {
            var facts = PlanFacts.builder().posture(Posture.STANDARD).footprint(List.of()).build();

            assertAll(
                    () -> assertTrue(new CompositionRule.In("posture", List.of("standard")).holds(facts)),
                    () -> assertFalse(new CompositionRule.In("posture", List.of("full")).holds(facts)),
                    () -> assertTrue(new CompositionRule.Empty("footprint").holds(facts)),
                    () -> assertFalse(new CompositionRule.Empty("lane").holds(facts)),
                    () -> assertFalse(new CompositionRule.Equals("footprint", "").holds(facts)));
        }

        private static CompositionRules rules(String rule) {
            return assertInstanceOf(CompositionRules.Loaded.class, CompositionRules.parse(
                    ("{\"format_version\": 1, \"revision\": \"t1\", \"rules\": [" + rule + "]}").getBytes(
                            StandardCharsets.UTF_8))).rules();
        }
    }

    @Nested
    @DisplayName("Plan facts")
    class Facts {

        @Test
        @DisplayName("name every plan fact and every configuration key, and nothing else")
        void accessors() {
            var facts = PlanFacts.builder().lane(Lane.DEEP).posture(Posture.FULL).changeType("feature")
                    .recipeId("lesson-cleanup").postRun("returned").footprint(List.of("a"))
                    .configuration("merge.auto_merge", "true").build();

            assertAll(
                    () -> assertEquals(Optional.of("deep"), facts.text("lane")),
                    () -> assertEquals(Optional.of("full"), facts.text("posture")),
                    () -> assertEquals(Optional.of("feature"), facts.text("change_type")),
                    () -> assertEquals(Optional.of("lesson-cleanup"), facts.text("recipe_id")),
                    () -> assertEquals(Optional.of("returned"), facts.text("post_run")),
                    () -> assertEquals(Optional.empty(), facts.text("footprint")),
                    () -> assertEquals(Optional.of(List.of("a")), facts.list("footprint")),
                    () -> assertEquals(Optional.empty(), facts.list("lane")),
                    () -> assertEquals(Optional.of("true"), facts.text("merge.auto_merge")),
                    () -> assertEquals(Optional.empty(), facts.text("integrations.sonar")));
        }

        @Test
        @DisplayName("the digest is 64 lowercase hex characters, stable for equal facts and changed by any fact")
        void digest() {
            var facts = PlanFacts.builder().lane(Lane.DEEP).steps(List.of("default:push")).tasks(List.of("TASK-1"))
                    .build();
            var same = PlanFacts.builder().lane(Lane.DEEP).steps(List.of("default:push")).tasks(List.of("TASK-1"))
                    .build();

            assertAll(
                    () -> assertTrue(facts.digest().matches("[0-9a-f]{64}"), facts.digest()),
                    () -> assertEquals(facts.digest(), same.digest()),
                    () -> assertNotEquals(facts.digest(), PlanFacts.builder().lane(Lane.LIGHT)
                            .steps(List.of("default:push")).tasks(List.of("TASK-1")).build().digest()),
                    () -> assertNotEquals(facts.digest(), PlanFacts.builder().lane(Lane.DEEP)
                            .steps(List.of("default:push")).tasks(List.of("TASK-2")).build().digest()),
                    () -> assertNotEquals(facts.digest(), PlanFacts.builder().lane(Lane.DEEP)
                            .steps(List.of("default:push")).tasks(List.of("TASK-1"))
                            .configuration("merge.auto_merge", "false").build().digest()));
        }
    }
}
