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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.workflow.ast.CallValue;
import de.planmarshall.workflow.plan.PlanInstanceHeader.Lane;
import de.planmarshall.workflow.plan.PlanInstanceHeader.Posture;
import de.planmarshall.workflow.plan.PrunedElement.Kind;

/**
 * The evaluation of the shipped composition rules against the facts of a plan (PM-IMPL-5, PM-EXT-7): each rule that
 * holds contributes exactly its prunes, with its reason and the fact it read as the source fact, and its bindings, in
 * rule order; with the fact not holding, the same rule contributes nothing. An unknown fact never makes a rule hold
 * except one asking whether it is unset, so an unreadable push remote or footprint is never read as "none".
 * <p>
 * The facts of most tests configure the analysis provider, so the rule that prunes the analysis step of a project
 * without one stays silent and each test sees only the rule it is about.
 */
@EnableGeneratorController
@DisplayName("CompositionRuleEvaluator")
class CompositionRuleEvaluatorTest {

    private static final CompositionRules SHIPPED = ((CompositionRules.Loaded) CompositionRules.shipped()).rules();

    private static final String SONAR = "integrations.sonar";

    private static final RuleEffects NOTHING = new RuleEffects(List.of(), List.of());

    private static RuleEffects evaluate(PlanFacts facts) {
        return CompositionRuleEvaluator.evaluate(SHIPPED, facts);
    }

    /** Facts with the analysis provider configured and every other fact unknown. */
    private static PlanFacts.Builder facts() {
        return PlanFacts.builder().configuration(SONAR, Generators.letterStrings(1, 12).next());
    }

    private static PrunedElement step(String id, PruneReason reason, String fact) {
        return new PrunedElement(Kind.STEP, id, reason, fact);
    }

    private static PrunedElement state(String id, PruneReason reason, String fact) {
        return new PrunedElement(Kind.STATE, id, reason, fact);
    }

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
            var effects = evaluate(facts().lane(Lane.LAND).build());

            assertEquals(new RuleEffects(
                            List.of(state("finalize.allocate-remediation", PruneReason.LAND_LANE, "lane"),
                                    new PrunedElement(Kind.POLICY_ACTION, "*/fix", PruneReason.LAND_LANE, "lane"),
                                    new PrunedElement(Kind.POLICY_ACTION, "*/loop_back", PruneReason.LAND_LANE, "lane"),
                                    step("default:finalize-step-simplify", PruneReason.LAND_LANE, "lane")),
                            List.of(new CompositionRule.Binding("*", "actions",
                                    new CallValue.EnumList(List.of("retry"))))), effects,
                    "the land lane binds every remediation call to retry");
        }

        @Test
        @DisplayName("adopt prunes create-pr, since the adopted pull request exists")
        void adopt() {
            assertEquals(new RuleEffects(List.of(step("default:create-pr", PruneReason.ADOPT, "lane")), List.of()),
                    evaluate(facts().lane(Lane.ADOPT).build()),
                    "adopt prunes create-pr since the pull request exists");
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = Lane.class, names = {"LIGHT", "DEEP"})
        @DisplayName("a request plan binds nothing and prunes nothing for its lane, so fix stays among its actions")
        void requestPlanControl(Lane lane) {
            assertEquals(NOTHING, evaluate(facts().lane(lane).build()),
                    "a request plan binds and prunes nothing for its lane");
        }
    }

    @Nested
    @DisplayName("Configuration")
    class Configuration {

        @Test
        @DisplayName("an empty push remote prunes every remote-bound step and keeps the two local gates")
        void localOnly() {
            var effects = evaluate(facts().configuration("derived.push_remote", "").build());

            assertAll(
                    () -> assertEquals(List.of(
                                    step("default:push", PruneReason.LOCAL_ONLY, "derived.push_remote"),
                                    step("default:create-pr", PruneReason.LOCAL_ONLY, "derived.push_remote"),
                                    step("default:ci-verify", PruneReason.LOCAL_ONLY, "derived.push_remote"),
                                    step("default:automatic-review", PruneReason.LOCAL_ONLY, "derived.push_remote"),
                                    step("default:analysis-roundtrip", PruneReason.LOCAL_ONLY, "derived.push_remote")),
                            effects.prunes(), "an empty push remote prunes every remote-bound step"),
                    () -> assertTrue(effects.prunes().stream().map(PrunedElement::id)
                                    .noneMatch(id -> "default:pre-push-quality-gate".equals(id)
                                            || "default:pre-submission-self-review".equals(id)),
                            "the two local quality gates survive an empty push remote"),
                    () -> assertEquals(List.of(), effects.bindings(),
                            "an empty push remote binds nothing at all"));
        }

        @Test
        @DisplayName("a push remote, or one that could not be derived, prunes nothing")
        void localOnlyControl() {
            assertAll(
                    () -> assertEquals(NOTHING, evaluate(facts().configuration("derived.push_remote",
                            Generators.letterStrings(1, 12).next()).build()),
                            "a known push remote prunes nothing at all"),
                    () -> assertEquals(NOTHING, evaluate(facts().build()),
                            "an underivable push remote prunes nothing either"));
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
                    evaluate(facts().posture(Posture.MINIMAL).build()).prunes(),
                    "minimal prunes the standard, full and off-default steps");
        }

        @Test
        @DisplayName("standard prunes the full-tier security audit and the steps that are off by default")
        void standard() {
            assertEquals(List.of(
                            step("default:finalize-step-security-audit", PruneReason.POSTURE, "posture"),
                            step("default:adr-propose", PruneReason.POSTURE, "posture"),
                            step("default:plan-retrospective", PruneReason.POSTURE, "posture")),
                    evaluate(facts().posture(Posture.STANDARD).build()).prunes(),
                    "standard prunes the full audit and off-default steps");
        }

        @Test
        @DisplayName("full prunes only the steps that are off by default")
        void full() {
            assertEquals(List.of(
                            step("default:adr-propose", PruneReason.POSTURE, "posture"),
                            step("default:plan-retrospective", PruneReason.POSTURE, "posture")),
                    evaluate(facts().posture(Posture.FULL).build()).prunes(),
                    "full prunes only the steps that are off by default");
        }

        @Test
        @DisplayName("an unknown posture prunes nothing")
        void unknown() {
            assertEquals(NOTHING, evaluate(facts().build()), "an unknown posture prunes nothing at all");
        }
    }

    @Nested
    @DisplayName("Provider and realized footprint")
    class Families {

        private static final String REALIZED = PlanFacts.REALIZED_FOOTPRINT_FACT;

        @Test
        @DisplayName("a project without an analysis provider prunes the analysis roundtrip")
        void providerUnconfigured() {
            assertEquals(new RuleEffects(List.of(step("default:analysis-roundtrip",
                            PruneReason.PROVIDER_UNCONFIGURED, SONAR)), List.of()),
                    evaluate(PlanFacts.builder().build()), "a missing analysis provider prunes the analysis roundtrip");
        }

        @Test
        @DisplayName("a configured analysis provider keeps the analysis roundtrip")
        void providerConfiguredControl() {
            assertEquals(NOTHING, evaluate(PlanFacts.builder().configuration(SONAR,
                    Generators.letterStrings(1, 12).next()).build()),
                    "a configured analysis provider keeps the analysis roundtrip");
        }

        @Test
        @DisplayName("a realized footprint of documentation only prunes simplify and the ADR proposal, no_code_delta")
        void realizedFootprintWithoutCode() {
            var docs = List.of(Generators.letterStrings(1, 12).next() + ".adoc",
                    "doc/" + Generators.letterStrings(1, 12).next() + ".adoc");

            assertEquals(new RuleEffects(List.of(
                            step("default:finalize-step-simplify", PruneReason.NO_CODE_DELTA, REALIZED),
                            step("default:adr-propose", PruneReason.NO_CODE_DELTA, REALIZED)), List.of()),
                    evaluate(facts().realizedFootprint(docs).build()),
                    "a documentation-only change prunes simplify and the ADR");
        }

        @Test
        @DisplayName("a realized footprint of other non-code files prunes the same two steps")
        void realizedFootprintOfOtherNonCode() {
            assertEquals(2, evaluate(facts().realizedFootprint(List.of("doc/guide.adoc", "README.md",
                            ".github/project.yml")).build()).prunes().size(),
                    "a change without code prunes the two steps");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"Main.java", "pm-workflow/src/main/java/A.java", "build.gradle.kts",
                "web/src/app.ts", "web/src/app.mjs", "tools/run.py", "scripts/build.sh", "pom.xml",
                "pm-core/pom.xml", "package.json", "web/package.json", "pyproject.toml", "Dockerfile",
                "image/Containerfile"})
        @DisplayName("a realized footprint with one code or build path keeps simplify and the ADR proposal")
        void realizedFootprintWithCodeControl(String codePath) {
            assertEquals(NOTHING, evaluate(facts().realizedFootprint(List.of("doc/guide.adoc", codePath)).build()),
                    "a change with one code path keeps simplify and ADRs");
        }

        @Test
        @DisplayName("an empty or absent (unreadable) realized footprint is indeterminate and keeps every step")
        void realizedFootprintIndeterminateControl() {
            assertAll(
                    () -> assertEquals(NOTHING, evaluate(facts().realizedFootprint(List.of()).build()),
                            "an empty footprint is indeterminate and prunes nothing"),
                    () -> assertEquals(NOTHING, evaluate(facts().build()),
                            "an absent footprint prunes nothing either"));
        }

        @Test
        @DisplayName("a configuration key named footprint that lists only documentation prunes nothing")
        void declaredFootprintIsNoFact() {
            // "footprint" and "change_type" are the names of the removed facts, the contract of this control
            var facts = facts().configuration("footprint", "doc/guide.adoc")
                    .configuration("change_type", "analysis").build();

            assertAll(
                    () -> assertEquals(NOTHING, evaluate(facts),
                            "the declared footprint and the change type prune nothing"),
                    () -> assertEquals(Optional.empty(), facts.list("footprint"),
                            "the declared footprint is no list fact"));
        }
    }

    @Nested
    @DisplayName("Merge by operator")
    class MergeByOperator {

        private static final String VARIANT = "ask.key-points";

        private PlanFacts.Builder selfMerge(String value) {
            return facts().axis(PlanFacts.Axis.ASKING, PlanFacts.Level.PROJECT, VARIANT).declarations(VARIANT,
                    Map.of("wait_after", new CallValue.EnumList(List.of()), "outline_review",
                            new CallValue.EnumValue("true"), "self_merge", new CallValue.EnumValue(value)));
        }

        @Test
        @DisplayName("an asking variant that declares self_merge false loses the merge execution, sourced to it")
        void selfMergeFalse() {
            assertEquals(new RuleEffects(List.of(
                            state("finalize.merge-dispatch", PruneReason.MERGE_BY_OPERATOR, "axes.asking"),
                            state("finalize.merge-queue-wait", PruneReason.MERGE_BY_OPERATOR, "axes.asking"),
                            state("finalize.merge-queue-exit", PruneReason.MERGE_BY_OPERATOR, "axes.asking")), List.of()),
                    evaluate(selfMerge("false").build()),
                    "self_merge false prunes the merge dispatch and queue wait");
        }

        @Test
        @DisplayName("an asking variant that merges by itself, or whose declarations are unknown, keeps it")
        void selfMergeTrueControl() {
            assertAll(
                    () -> assertEquals(NOTHING, evaluate(selfMerge("true").build()),
                            "self_merge true keeps the merge execution"),
                    () -> assertEquals(NOTHING, evaluate(facts().axis(PlanFacts.Axis.ASKING, PlanFacts.Level.PROJECT,
                            VARIANT).build()), "unknown self_merge declarations keep the merge"));
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
                    evaluate(facts().postRun(form).build()),
                    "a post-run form prunes every step below order 900");
        }
    }

    @Nested
    @DisplayName("Evaluation")
    class Evaluation {

        @Test
        @DisplayName("the effects of several rules come in rule order")
        void ruleOrder() {
            var effects = evaluate(PlanFacts.builder().lane(Lane.LAND).posture(Posture.FULL)
                    .realizedFootprint(List.of("README.md")).build());

            assertEquals(List.of("lane", "lane", "lane", "lane", "posture", "posture", SONAR,
                            PlanFacts.REALIZED_FOOTPRINT_FACT, PlanFacts.REALIZED_FOOTPRINT_FACT),
                    effects.prunes().stream().map(PrunedElement::sourceFact).toList(),
                    "the prunes of several rules come in rule order");
        }

        @Test
        @DisplayName("an unset provider key holds the unset condition, a set one does not")
        void unset() {
            var rules = rules("{\"id\": \"p\", \"when\": {\"fact\": \"integrations.sonar\", \"unset\": true}, "
                    + "\"then\": {\"prune\": [{\"element\": \"step\", \"id\": \"default:analysis-roundtrip\", "
                    + "\"reason\": \"provider_unconfigured\"}]}}");

            assertAll(
                    () -> assertEquals(List.of(step("default:analysis-roundtrip", PruneReason.PROVIDER_UNCONFIGURED,
                                    SONAR)),
                            CompositionRuleEvaluator.evaluate(rules, PlanFacts.builder().build()).prunes(),
                            "an unset provider key holds the unset condition"),
                    () -> assertEquals(NOTHING, CompositionRuleEvaluator.evaluate(rules, PlanFacts.builder()
                                    .configuration(SONAR, Generators.letterStrings(1, 12).next()).build()),
                            "a set provider key does not hold the unset condition"));
        }

        @Test
        @DisplayName("a footprint is disjoint only when it is known, non-empty and matches no glob")
        void disjoint() {
            var rules = rules("{\"id\": \"f\", \"when\": {\"fact\": \"realized_footprint\", \"disjoint\": "
                    + "[\"**/*.java\", \"pom.xml\"]}, \"then\": {\"prune\": [{\"element\": \"step\", "
                    + "\"id\": \"default:x\", \"reason\": \"no_code_delta\"}]}}");

            assertAll(
                    () -> assertEquals(1, CompositionRuleEvaluator.evaluate(rules, PlanFacts.builder()
                                    .realizedFootprint(List.of("doc/a.adoc", "README.md")).build()).prunes().size(),
                            "a disjoint footprint prunes exactly one step"),
                    () -> assertEquals(NOTHING, CompositionRuleEvaluator.evaluate(rules, PlanFacts.builder()
                                    .realizedFootprint(List.of("doc/a.adoc", "src/main/java/A.java")).build()),
                            "a footprint matching a glob is not disjoint"),
                    () -> assertEquals(NOTHING, CompositionRuleEvaluator.evaluate(rules, PlanFacts.builder()
                                    .realizedFootprint(List.of("pom.xml")).build()),
                            "a footprint matching pom.xml is not disjoint"),
                    () -> assertEquals(NOTHING, CompositionRuleEvaluator.evaluate(rules, PlanFacts.builder()
                                    .realizedFootprint(List.of()).build()),
                            "an empty footprint is never disjoint"),
                    () -> assertEquals(NOTHING, CompositionRuleEvaluator.evaluate(rules, PlanFacts.builder()
                            .build()), "an unknown footprint is never disjoint"));
        }

        @Test
        @DisplayName("a single star never crosses a directory, a question mark matches one character")
        void globSegments() {
            var pattern = CompositionRule.Disjoint.compile("doc/*.ado?");

            assertAll(
                    () -> assertTrue(pattern.matcher("doc/a.adoc").matches(),
                            "a single star matches within one directory"),
                    () -> assertFalse(pattern.matcher("doc/sub/a.adoc").matches(),
                            "a single star never crosses a directory"),
                    () -> assertFalse(pattern.matcher("doc/a.ad").matches(),
                            "a pattern needing one more character does not match"),
                    () -> assertTrue(CompositionRule.Disjoint.compile("a+b.txt").matcher("a+b.txt").matches(),
                            "a plus sign in a pattern is a literal"));
        }

        @Test
        @DisplayName("the in condition holds for a listed value only, the empty condition for an empty list")
        void inAndEmpty() {
            var facts = PlanFacts.builder().posture(Posture.STANDARD).realizedFootprint(List.of()).build();

            assertAll(
                    () -> assertTrue(new CompositionRule.In("posture", List.of("standard")).holds(facts),
                            "the in condition holds for a listed value"),
                    () -> assertFalse(new CompositionRule.In("posture", List.of("full")).holds(facts),
                            "the in condition fails for an unlisted value"),
                    () -> assertTrue(new CompositionRule.Empty("realized_footprint").holds(facts),
                            "the empty condition holds for an empty list"),
                    () -> assertFalse(new CompositionRule.Empty("lane").holds(facts),
                            "the empty condition fails for an unknown fact"),
                    () -> assertFalse(new CompositionRule.Equals("realized_footprint", "").holds(facts),
                            "equals fails for a list, even an empty one"));
        }

        private static CompositionRules rules(String rule) {
            return assertInstanceOf(CompositionRules.Loaded.class, CompositionRules.parse(
                    ("{\"format_version\": 1, \"revision\": \"t1\", \"rules\": [" + rule + "]}").getBytes(
                            StandardCharsets.UTF_8)), "the parsed shipped rules are a loaded catalogue").rules();
        }
    }

    /**
     * Rules over the plan behaviour axes (PM-WF-25): a rule reads the bound variant ({@code axes.<axis>}) or a
     * declaration of it ({@code axes.<axis>.<declaration>}), which the facts carry from the variant's tree, and names
     * the bound variant as the source fact of its prunes.
     */
    @Nested
    @DisplayName("Axis facts")
    class AxisFacts {

        private static final String EVERY_PHASE = "ask.every-phase";

        private static final String QUESTIONS_ONLY = "ask.questions-only";

        private static final CompositionRules SELF_MERGE = rules("axes.asking.self_merge", "\"equals\": \"false\"");

        private static CompositionRules rules(String fact, String condition) {
            return assertInstanceOf(CompositionRules.Loaded.class, CompositionRules.parse("""
                    {"format_version": 1, "revision": "t1", "rules": [{"id": "r", "when": {"fact": "%s", %s},
                     "then": {"prune": [{"element": "state", "id": "finalize.merge-dispatch",
                       "reason": "merge_by_operator"}]}}]}""".formatted(fact, condition)
                    .getBytes(StandardCharsets.UTF_8)), "the parsed rule set is a loaded catalogue").rules();
        }

        private static Map<String, CallValue> asking(List<String> waitAfter, String selfMerge) {
            return Map.of("wait_after", new CallValue.EnumList(waitAfter), "outline_review",
                    new CallValue.EnumValue("true"), "self_merge", new CallValue.EnumValue(selfMerge));
        }

        @Test
        @DisplayName("a rule over a declaration of the bound variant holds and names the bound variant as source")
        void declarationHolds() {
            var facts = PlanFacts.builder().axis(PlanFacts.Axis.ASKING, PlanFacts.Level.PLAN, EVERY_PHASE)
                    .declarations(EVERY_PHASE, asking(List.of("init"), "false")).build();

            assertEquals(List.of(state("finalize.merge-dispatch", PruneReason.MERGE_BY_OPERATOR, "axes.asking")),
                    CompositionRuleEvaluator.evaluate(SELF_MERGE, facts).prunes(),
                    "a declaration of the bound variant makes the rule hold");
        }

        @Test
        @DisplayName("the same rule fails for a variant that merges itself and for an unknown declaration")
        void declarationFailsControl() {
            var merging = PlanFacts.builder().axis(PlanFacts.Axis.ASKING, PlanFacts.Level.PLAN, EVERY_PHASE)
                    .declarations(EVERY_PHASE, asking(List.of("init"), "true")).build();
            var unknown = PlanFacts.builder().axis(PlanFacts.Axis.ASKING, PlanFacts.Level.PLAN, EVERY_PHASE)
                    .build();
            var otherBound = PlanFacts.builder().declarations(EVERY_PHASE, asking(List.of("init"), "false"))
                    .build();

            assertAll(
                    () -> assertEquals(NOTHING, CompositionRuleEvaluator.evaluate(SELF_MERGE, merging),
                            "a variant that merges itself does not fire the rule"),
                    () -> assertEquals(NOTHING, CompositionRuleEvaluator.evaluate(SELF_MERGE, unknown),
                            "an unknown declaration does not fire the rule"),
                    () -> assertEquals(NOTHING, CompositionRuleEvaluator.evaluate(SELF_MERGE, otherBound),
                            "an unbound declaration does not fire the rule"));
        }

        @Test
        @DisplayName("a rule over the bound variant holds for the nearest level's variant only")
        void boundVariant() {
            var rules = rules("axes.asking", "\"equals\": \"" + EVERY_PHASE + "\"");
            var project = PlanFacts.builder().axis(PlanFacts.Axis.ASKING, PlanFacts.Level.PROJECT, EVERY_PHASE)
                    .axis(PlanFacts.Axis.ASKING, PlanFacts.Level.MACHINE, QUESTIONS_ONLY).build();
            var machine = PlanFacts.builder().axis(PlanFacts.Axis.ASKING, PlanFacts.Level.MACHINE, QUESTIONS_ONLY)
                    .build();

            assertAll(
                    () -> assertEquals(List.of(state("finalize.merge-dispatch", PruneReason.MERGE_BY_OPERATOR,
                                    "axes.asking")), CompositionRuleEvaluator.evaluate(rules, project).prunes(),
                            "the nearest level's variant is the one the rule reads"),
                    () -> assertEquals(NOTHING, CompositionRuleEvaluator.evaluate(rules, machine),
                            "a farther level's variant does not fire the rule"));
        }

        @Test
        @DisplayName("an empty wait_after holds the empty condition, a non-empty one does not")
        void listDeclaration() {
            var rules = rules("axes.asking.wait_after", "\"empty\": true");
            var none = PlanFacts.builder().declarations(QUESTIONS_ONLY, asking(List.of(), "true")).build();
            var some = PlanFacts.builder().declarations(QUESTIONS_ONLY, asking(List.of("plan"), "true")).build();

            assertAll(
                    () -> assertEquals(1, CompositionRuleEvaluator.evaluate(rules, none).prunes().size(),
                            "an empty wait_after fires the empty rule once"),
                    () -> assertEquals(NOTHING, CompositionRuleEvaluator.evaluate(rules, some),
                            "a non-empty wait_after does not fire the rule"),
                    () -> assertEquals(Optional.of(List.of("plan")), some.list("axes.asking.wait_after"),
                            "a non-empty wait_after is read as a list"),
                    () -> assertEquals(Optional.empty(), some.text("axes.asking.wait_after"),
                            "a list declaration has no single text value"));
        }

        @Test
        @DisplayName("the bound variant is the nearest level's, else the shipped plan default of the axis")
        void bound() {
            var facts = PlanFacts.builder().axis(PlanFacts.Axis.ASKING, PlanFacts.Level.MACHINE, EVERY_PHASE)
                    .axis(PlanFacts.Axis.ASKING, PlanFacts.Level.PLAN, QUESTIONS_ONLY).build();

            assertAll(
                    () -> assertEquals(new PlanFacts.BoundVariant(PlanFacts.Axis.ASKING, QUESTIONS_ONLY,
                                    PlanFacts.Level.PLAN), facts.bound(PlanFacts.Axis.ASKING),
                            "the nearest level's variant is the bound one"),
                    () -> assertEquals("plan.axes.asking", facts.bound(PlanFacts.Axis.ASKING).sourceFact(),
                            "the source fact names the plan level of the axis"),
                    () -> assertEquals(new PlanFacts.BoundVariant(PlanFacts.Axis.METHOD, "method.tests-after",
                                    PlanFacts.Level.SHIPPED), facts.bound(PlanFacts.Axis.METHOD),
                            "a method axis without a binding falls back to shipped"),
                    () -> assertEquals(Optional.of("landing.pull-request"), facts.text("axes.landing"),
                            "the shipped plan default of the axis is read"),
                    () -> assertEquals(Optional.empty(), facts.text("axes.method.order"),
                            "no declaration is bound for the method order"),
                    () -> assertEquals(Optional.empty(), facts.text("axes.weather"),
                            "an axis that does not exist has no value"));
        }

        @Test
        @DisplayName("a value that is no variant of its axis, the shipped level and a unit that is no variant are "
                + "refused")
        void refusals() {
            var foreignVariant = PlanFacts.builder().axis(PlanFacts.Axis.ASKING, PlanFacts.Level.PLAN,
                    "method.test-first");
            var shippedLevel = PlanFacts.builder().axis(PlanFacts.Axis.ASKING, PlanFacts.Level.SHIPPED, EVERY_PHASE);
            var noVariant = PlanFacts.builder().declarations("replan", Map.of());

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, foreignVariant::build,
                            "a value that is no variant of its axis is refused"),
                    () -> assertThrows(IllegalArgumentException.class, shippedLevel::build,
                            "the shipped level may not bind a variant"),
                    () -> assertThrows(IllegalArgumentException.class, noVariant::build,
                            "a unit that is no variant of its axis is refused"));
        }

        @Test
        @DisplayName("the digest changes with a level of an axis and the overrides, never with the declarations")
        void digest() {
            var facts = PlanFacts.builder().axis(PlanFacts.Axis.METHOD, PlanFacts.Level.PROJECT,
                    "method.test-first").build();

            assertAll(
                    () -> assertNotEquals(facts.digest(), PlanFacts.builder().axis(PlanFacts.Axis.METHOD,
                                    PlanFacts.Level.MACHINE, "method.test-first").build().digest(),
                            "a changed level of an axis changes the digest"),
                    () -> assertNotEquals(facts.digest(), PlanFacts.builder().axis(PlanFacts.Axis.METHOD,
                                    PlanFacts.Level.PROJECT, "method.test-first").overrides("{}").build().digest(),
                            "the overrides of a fact change its digest"),
                    () -> assertEquals(facts.digest(), facts.withDeclarations("method.test-first",
                                    Map.of("order", new CallValue.EnumValue("tests_first"))).digest(),
                            "the declarations of a fact leave its digest unchanged"));
        }
    }

    @Nested
    @DisplayName("Plan facts")
    class Facts {

        @Test
        @DisplayName("name every plan fact and every configuration key, and nothing else")
        void accessors() {
            var path = Generators.letterStrings(1, 8).next();
            var remote = Generators.letterStrings(1, 12).next();
            var facts = PlanFacts.builder().lane(Lane.DEEP).posture(Posture.FULL)
                    .postRun("returned").realizedFootprint(List.of(path))
                    .configuration("derived.push_remote", remote).build();

            assertAll(
                    () -> assertEquals(Optional.of("deep"), facts.text("lane"),
                            "the lane is read back as its text value"),
                    () -> assertEquals(Optional.of("full"), facts.text("posture"),
                            "the posture is read back as its text value"),
                    () -> assertEquals(Optional.empty(), facts.text("change_type"),
                            "the change type is no fact and has no text"),
                    () -> assertEquals(Optional.empty(), facts.text("recipe_id"),
                            "an unset recipe id has no text value"),
                    () -> assertEquals(Optional.of("returned"), facts.text("post_run"),
                            "the post-run form is read back as text"),
                    () -> assertEquals(Optional.empty(), facts.text("realized_footprint"),
                            "a list fact has no single text value"),
                    () -> assertEquals(Optional.of(List.of(path)), facts.list("realized_footprint"),
                            "the realized footprint reads back as its paths"),
                    () -> assertEquals(Optional.empty(), facts.list("footprint"),
                            "the declared footprint is no list fact"),
                    () -> assertEquals(Optional.empty(), facts.list("lane"),
                            "a text fact has no list value"),
                    () -> assertEquals(Optional.of(remote), facts.text("derived.push_remote"),
                            "a configured remote is read back as its text"),
                    () -> assertEquals(Optional.empty(), facts.text(SONAR),
                            "an unconfigured provider has no text value"));
        }

        @Test
        @DisplayName("the digest is 64 lowercase hex characters, stable for equal facts and changed by any fact")
        void digest() {
            var task = "TASK-" + Generators.integers(1, 5_000).next();
            var otherTask = "TASK-" + Generators.integers(5_001, 10_000).next();
            var facts = PlanFacts.builder().lane(Lane.DEEP).steps(List.of("default:push")).tasks(List.of(task))
                    .build();
            var same = PlanFacts.builder().lane(Lane.DEEP).steps(List.of("default:push")).tasks(List.of(task))
                    .build();

            assertAll(
                    () -> assertTrue(facts.digest().matches("[0-9a-f]{64}"),
                            "the digest is 64 lower-case hex digits"),
                    () -> assertEquals(facts.digest(), same.digest(), "equal facts have the same digest"),
                    () -> assertNotEquals(facts.digest(), PlanFacts.builder().lane(Lane.LIGHT)
                                    .steps(List.of("default:push")).tasks(List.of(task)).build().digest(),
                            "a different lane changes the digest"),
                    () -> assertNotEquals(facts.digest(), PlanFacts.builder().lane(Lane.DEEP)
                                    .steps(List.of("default:push")).tasks(List.of(otherTask)).build().digest(),
                            "a different task changes the digest"),
                    () -> assertNotEquals(facts.digest(), PlanFacts.builder().lane(Lane.DEEP)
                                    .steps(List.of("default:push")).tasks(List.of(task))
                                    .configuration("derived.push_remote", "").build().digest(),
                            "a changed configuration changes the digest"));
        }

        @Test
        @DisplayName("the digest of facts with every optional member absent is taken over null members")
        void digestOfAbsentMembers() {
            var facts = PlanFacts.builder().build();

            // the expected text is the canonical JSON the digest is specified over
            assertEquals(PlanFacts.sha256("{\"axes\":{},\"configuration\":{},\"lane\":null,\"overrides\":null,"
                            + "\"post_run\":null,\"posture\":null,\"realized_footprint\":null,\"steps\":[],"
                            + "\"tasks\":[]}"), facts.digest(),
                    "absent members are digested as JSON null");
        }

        @Test
        @DisplayName("the realized footprint digest is taken over its canonical JSON list, absent when it is")
        void realizedFootprintDigest() {
            var facts = PlanFacts.builder().realizedFootprint(List.of("README.md", "doc/a.adoc")).build();

            // the expected text is the canonical JSON list the digest is specified over
            assertAll(
                    () -> assertEquals(Optional.of(PlanFacts.sha256("[\"README.md\",\"doc/a.adoc\"]")),
                            facts.realizedFootprintDigest(), "the digest is over the canonical list"),
                    () -> assertEquals(Optional.empty(), PlanFacts.builder().build().realizedFootprintDigest(),
                            "an absent footprint has no digest"),
                    () -> assertEquals(Optional.empty(), facts.withoutRealizedFootprint().realizedFootprintDigest(),
                            "the masked facts carry no footprint digest"),
                    () -> assertEquals(PlanFacts.builder().build().digest(),
                            facts.withoutRealizedFootprint().digest(),
                            "the masked facts digest like facts without one"));
        }

        @Test
        @DisplayName("the digest of facts with every optional member present is taken over their values")
        void digestOfPresentMembers() {
            var facts = PlanFacts.builder().lane(Lane.DEEP).posture(Posture.FULL)
                    .postRun("returned").realizedFootprint(List.of("README.md")).overrides("{}").build();

            // the expected text is the canonical JSON the digest is specified over
            assertEquals(PlanFacts.sha256("{\"axes\":{},\"configuration\":{},\"lane\":\"deep\",\"overrides\":\""
                            + PlanFacts.sha256("{}") + "\",\"post_run\":\"returned\",\"posture\":\"full\","
                            + "\"realized_footprint\":[\"README.md\"],\"steps\":[],\"tasks\":[]}"), facts.digest(),
                    "present members are digested by their values");
        }

        @Test
        @DisplayName("absent members are read as unknown, never as an empty value")
        void absentMembersAreUnknown() {
            var facts = PlanFacts.builder().build();

            assertAll(
                    () -> assertNull(facts.lane(), "an absent lane is null"),
                    () -> assertNull(facts.realizedFootprint(), "an absent realized footprint is null"),
                    () -> assertNull(facts.overrides(), "absent overrides are null"),
                    () -> assertEquals(Optional.empty(), facts.text("lane"), "an absent lane has no text"),
                    () -> assertEquals(Optional.empty(), facts.list("realized_footprint"),
                            "an absent realized footprint has no list"));
        }
    }
}
