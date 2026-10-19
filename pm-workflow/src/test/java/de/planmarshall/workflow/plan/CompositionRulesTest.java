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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.workflow.ast.CallValue;
import de.planmarshall.workflow.plan.CompositionRules.Refusal;
import de.planmarshall.workflow.verify.CompiledInvariants;

/**
 * The composition rule file (PM-IMPL-5, PM-EXT-7). The shipped file is a classpath resource of the module, outside
 * every location of model-facing content; it loads, and its revision and SHA-256 digest are stable. A file written for
 * the test that is malformed, or holds a rule that adds an element, binds a budget or names a gate, is refused as a
 * value naming that refusal; a rule never extends the template, never lifts a budget and never touches a gate.
 */
@DisplayName("CompositionRules")
class CompositionRulesTest {

    private static final List<String> SHIPPED_RULE_IDS = List.of("land-lane", "adopt-lane", "local-only",
            "posture-minimal", "posture-standard", "posture-default-off", "analysis-provider-unconfigured",
            "no-code-delta", "report-change-type", "post-run-returned", "post-run-handed-back",
            "post-run-empty-harvest");

    /**
     * The floor-immune steps (lane class {@code core} or {@code derived-state}) of the step catalogue: only the rules
     * of {@code local_only}, {@code adopt} and the post-run-only forms may prune one of them.
     */
    private static final Set<String> FLOOR_IMMUNE_STEPS = Set.of("default:finalize-step-sync-baseline",
            "default:pre-push-quality-gate", "default:push", "default:create-pr", "default:ci-verify",
            "default:branch-cleanup", "default:record-metrics", "default:finalize-step-print-phase-breakdown",
            "default:emit-landing", "default:archive-plan", "default:architecture-refresh");

    private static final Set<PruneReason> FLOOR_OVERRIDING_REASONS = Set.of(PruneReason.LOCAL_ONLY,
            PruneReason.ADOPT, PruneReason.RETURNED, PruneReason.HANDED_BACK, PruneReason.EMPTY_HARVEST);

    /** A rule file around the given rules, written for the test. */
    private static CompositionRules.Loading file(String rules) {
        return CompositionRules.parse(("{\"format_version\": 1, \"revision\": \"t1\", \"rules\": [" + rules + "]}")
                .getBytes(StandardCharsets.UTF_8));
    }

    private static CompositionRules loaded(CompositionRules.Loading loading) {
        return assertInstanceOf(CompositionRules.Loaded.class, loading, loading::toString).rules();
    }

    @Nested
    @DisplayName("Shipped file")
    class ShippedFile {

        @Test
        @DisplayName("loads with its revision, its rules in file order and the digest of its bytes")
        void loads() throws Exception {
            var rules = loaded(CompositionRules.shipped());
            byte[] bytes;
            try (var in = CompositionRules.class.getResourceAsStream(CompositionRules.RESOURCE)) {
                assertNotNull(in, CompositionRules.RESOURCE);
                bytes = in.readAllBytes();
            }

            assertAll(
                    () -> assertEquals("1", rules.revision()),
                    () -> assertTrue(rules.digest().matches("[0-9a-f]{64}"), rules.digest()),
                    () -> assertEquals(PlanFacts.sha256(bytes), rules.digest()),
                    () -> assertEquals(SHIPPED_RULE_IDS, rules.rules().stream().map(CompositionRule::id).toList()));
        }

        @Test
        @DisplayName("loading it again yields the same revision and digest")
        void stable() {
            var first = loaded(CompositionRules.shipped());
            var second = loaded(CompositionRules.shipped());

            assertEquals(first, second);
        }

        @Test
        @DisplayName("lives outside every location of model-facing content")
        void notModelFacing() {
            assertAll(
                    () -> assertTrue(CompositionRules.RESOURCE.startsWith("/de/planmarshall/workflow/plan/")),
                    () -> List.of("/workflows/", "/roles/", "/bundles/", "/skills/").forEach(location ->
                            assertFalse(CompositionRules.RESOURCE.contains(location), location)));
        }

        @Test
        @DisplayName("binds no budget, adds no element and names no gate")
        void shippedRulesRestricted() {
            var rules = loaded(CompositionRules.shipped()).rules();

            assertAll(
                    () -> assertTrue(rules.stream().flatMap(rule -> rule.bindings().stream())
                            .allMatch(binding -> "actions".equals(binding.argument()))),
                    () -> assertTrue(rules.stream().flatMap(rule -> rule.prunes().stream())
                            .noneMatch(prune -> prune.id().contains("gate"))));
        }

        /**
         * The three families the specification names without content each have exactly one shipped rule, over the
         * fact and with the condition and targets the specification states for it.
         */
        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.plan.CompositionRulesTest#families")
        @DisplayName("ships one rule per prune family, over its fact, with its targets")
        void family(PruneReason reason, CompositionRule.Condition when, List<String> targets) {
            var rules = loaded(CompositionRules.shipped()).rules().stream()
                    .filter(rule -> rule.prunes().stream().anyMatch(prune -> prune.reason() == reason)).toList();

            assertEquals(1, rules.size(), rules::toString);
            var rule = rules.getFirst();
            assertAll(
                    () -> assertEquals(when, rule.when()),
                    () -> assertEquals(targets, rule.prunes().stream().map(CompositionRule.Prune::id).toList()),
                    () -> assertTrue(rule.prunes().stream().allMatch(prune -> prune.reason() == reason
                            && prune.element() == PrunedElement.Kind.STEP)),
                    () -> assertEquals(List.of(), rule.bindings()));
        }

        @Test
        @DisplayName("prunes a floor-immune step only for local-only runs, adopt and the post-run-only forms")
        void floorImmunity() {
            var floorPrunes = loaded(CompositionRules.shipped()).rules().stream()
                    .flatMap(rule -> rule.prunes().stream())
                    .filter(prune -> FLOOR_IMMUNE_STEPS.contains(prune.id())).toList();

            assertAll(
                    () -> assertFalse(floorPrunes.isEmpty()),
                    () -> assertTrue(floorPrunes.stream()
                                    .allMatch(prune -> FLOOR_OVERRIDING_REASONS.contains(prune.reason())),
                            floorPrunes::toString));
        }
    }

    static Stream<Arguments> families() {
        return Stream.of(
                Arguments.of(PruneReason.PROVIDER_UNCONFIGURED, new CompositionRule.Unset("integrations.sonar"),
                        List.of("default:analysis-roundtrip")),
                Arguments.of(PruneReason.FOOTPRINT_DISJOINT, new CompositionRule.Disjoint("footprint", List.of(
                                "**.java", "**.kt", "**.kts", "**.groovy", "**.gradle", "**.scala",
                                "**.js", "**.mjs", "**.cjs", "**.jsx", "**.ts", "**.tsx", "**.py", "**.sh",
                                "pom.xml", "**/pom.xml", "package.json", "**/package.json", "pyproject.toml",
                                "**/pyproject.toml", "Dockerfile", "**/Dockerfile", "Containerfile",
                                "**/Containerfile")),
                        List.of("default:finalize-step-simplify", "default:adr-propose")),
                Arguments.of(PruneReason.CHANGE_TYPE_EXEMPT,
                        new CompositionRule.In("change_type", List.of("analysis", "verification")),
                        List.of("default:finalize-step-simplify")));
    }

    @Nested
    @DisplayName("Rule shape")
    class RuleShape {

        @Test
        @DisplayName("reads every condition kind and every literal kind of a binding")
        void everyKind() {
            var rules = loaded(file("""
                    {"id": "a", "when": {"fact": "lane", "equals": "land"}, "then": {
                       "prune": [{"element": "link", "id": "s/next", "reason": "land_lane"}],
                       "bind": [{"call_site": "c", "argument": "actions", "value": ["retry", "fix"]},
                                {"call_site": "c", "argument": "mode", "value": "strict"},
                                {"call_site": "c", "argument": "level", "value": 2}]}},
                    {"id": "b", "when": {"fact": "posture", "in": ["minimal", "full"]}, "then": {}},
                    {"id": "c", "when": {"fact": "derived.push_remote", "empty": true}, "then": {"prune": []}},
                    {"id": "d", "when": {"fact": "integrations.sonar", "unset": true}, "then": {"bind": []}},
                    {"id": "e", "when": {"fact": "footprint", "disjoint": ["**/*.java"]}, "then": {}}
                    """)).rules();

            assertEquals(List.of(
                    new CompositionRule("a", new CompositionRule.Equals("lane", "land"),
                            List.of(new CompositionRule.Prune(PrunedElement.Kind.LINK, "s/next",
                                    PruneReason.LAND_LANE)),
                            List.of(new CompositionRule.Binding("c", "actions",
                                            new CallValue.EnumList(List.of("retry", "fix"))),
                                    new CompositionRule.Binding("c", "mode", new CallValue.EnumValue("strict")),
                                    new CompositionRule.Binding("c", "level", new CallValue.IntValue(2)))),
                    new CompositionRule("b", new CompositionRule.In("posture", List.of("minimal", "full")),
                            List.of(), List.of()),
                    new CompositionRule("c", new CompositionRule.Empty("derived.push_remote"), List.of(), List.of()),
                    new CompositionRule("d", new CompositionRule.Unset("integrations.sonar"), List.of(), List.of()),
                    new CompositionRule("e", new CompositionRule.Disjoint("footprint", List.of("**/*.java")),
                            List.of(), List.of())), rules);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"100", "1.0E+2", "1.00e2"})
        @DisplayName("an integral binding value with trailing zeros or an exponent still loads")
        void integralValueWithTrailingZerosLoads(String value) {
            var rules = loaded(file("""
                    {"id": "r", "when": {"fact": "lane", "equals": "land"}, "then": {
                       "bind": [{"call_site": "*", "argument": "level", "value": %s}]}}""".formatted(value))).rules();

            assertEquals(new CallValue.IntValue(100), rules.getFirst().bindings().getFirst().value());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.plan.CompositionRulesTest#axisFacts")
        @DisplayName("a rule over the bound variant of an axis or a declaration of it loads")
        void axisFactLoads(String fact) {
            var rules = loaded(file("""
                    {"id": "r", "when": {"fact": "%s", "equals": "false"}, "then": {}}""".formatted(fact))).rules();

            assertEquals(fact, rules.getFirst().when().fact());
        }

        @Test
        @DisplayName("the registered axis facts are the bound variant of each axis and the declarations of its "
                + "variants")
        void registeredAxisFacts() {
            assertEquals(PlanFacts.AXIS_FACT_NAMES, Set.copyOf(axisFacts()));
        }

        @Test
        @DisplayName("the digest is the SHA-256 of the exact bytes, so a changed byte changes it")
        void digestOfBytes() {
            var first = loaded(file("{\"id\": \"a\", \"when\": {\"fact\": \"lane\", \"equals\": \"land\"}, "
                    + "\"then\": {}}"));
            var second = loaded(file("{\"id\": \"a\",  \"when\": {\"fact\": \"lane\", \"equals\": \"land\"}, "
                    + "\"then\": {}}"));

            assertAll(
                    () -> assertEquals(first.rules(), second.rules()),
                    () -> assertNotEquals(first.digest(), second.digest()));
        }
    }

    @Nested
    @DisplayName("Refusals")
    class Refusals {

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.plan.CompositionRulesTest#refusedFiles")
        @DisplayName("a file that breaks the rule shape or a rule restriction is refused by name, as a value")
        void refused(String name, String content, Refusal refusal, String detailPart) {
            var loading = CompositionRules.parse(content.getBytes(StandardCharsets.UTF_8));

            var refused = assertInstanceOf(CompositionRules.Refused.class, loading, name);
            assertAll(
                    () -> assertEquals(refusal, refused.refusal(), refused::detail),
                    () -> assertTrue(refused.detail().contains(detailPart), refused::detail));
        }

        /**
         * The refused budgets are the budget parameters of the remediation schema, read from its one definition: a
         * budget parameter added to the schema is refused here without a second edit.
         */
        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.plan.CompositionRulesTest#budgetParameters")
        @DisplayName("a binding of every budget parameter of the remediation schema is refused raises_budget")
        void everyBudgetParameterRefused(String argument) {
            var loading = file(bindingOf(argument));

            var refused = assertInstanceOf(CompositionRules.Refused.class, loading, loading::toString);
            assertAll(
                    () -> assertEquals(Refusal.RAISES_BUDGET, refused.refusal(), refused::detail),
                    () -> assertTrue(refused.detail().contains(argument), refused::detail));
        }

        @Test
        @DisplayName("a binding of an argument outside the budget parameters loads")
        void otherArgumentLoads() {
            var rules = loaded(file(bindingOf("mode"))).rules();

            assertEquals("mode", rules.getFirst().bindings().getFirst().argument());
        }

        private static String bindingOf(String argument) {
            return """
                    {"id": "r", "when": {"fact": "lane", "equals": "land"}, "then": {
                       "bind": [{"call_site": "*", "argument": "%s", "value": 1}]}}""".formatted(argument);
        }
    }

    static List<String> axisFacts() {
        return List.of("axes.landing", "axes.method", "axes.asking", "axes.method.order", "axes.asking.wait_after",
                "axes.asking.outline_review", "axes.asking.self_merge");
    }

    static Stream<String> budgetParameters() {
        var parameters = CompiledInvariants.remediationBudgetParameters();
        assertFalse(parameters.isEmpty(), "the remediation schema declares no budget parameter");
        return parameters.stream().sorted();
    }

    static Stream<Arguments> refusedFiles() {
        return Stream.of(
                refusal("a rule that adds an element", rule("""
                        "then": {"add": [{"element": "step", "id": "default:extra"}]}"""),
                        Refusal.ADDS_ELEMENT, "rule 'r'"),
                refusal("a rule that binds the fix budget", rule("""
                        "then": {"bind": [{"call_site": "*", "argument": "fix_budget", "value": 9}]}"""),
                        Refusal.RAISES_BUDGET, "fix_budget"),
                refusal("a rule that binds the retry budget", rule("""
                        "then": {"bind": [{"call_site": "*", "argument": "retry_budget", "value": 9}]}"""),
                        Refusal.RAISES_BUDGET, "retry_budget"),
                refusal("a rule that binds the waiver wait", rule("""
                        "then": {"bind": [{"call_site": "*", "argument": "waiver_wait_seconds", "value": 9}]}"""),
                        Refusal.RAISES_BUDGET, "waiver_wait_seconds"),
                refusal("a rule that prunes a gate", rule("""
                        "then": {"prune": [{"element": "gate", "id": "ci_check:build", "reason": "posture"}]}"""),
                        Refusal.NAMES_GATE, "rule 'r'"),
                refusal("a rule that waives a gate", rule("""
                        "then": {"waive": [{"gate": "ci_check:build"}]}"""), Refusal.NAMES_GATE, "rule 'r'"),
                refusal("a rule with a gate member", rule("""
                        "then": {"gate": "ci_check:build"}"""), Refusal.NAMES_GATE, "rule 'r'"),
                refusal("a rule that binds a gate", rule("""
                        "then": {"bind": [{"call_site": "*", "argument": "gate", "value": "ci_check:build"}]}"""),
                        Refusal.NAMES_GATE, "rule 'r'"),
                refusal("no JSON", "{", Refusal.MALFORMED, "not valid JSON"),
                refusal("a root that is no object", "[]", Refusal.MALFORMED, "must be an object"),
                refusal("another format version", "{\"format_version\": 2, \"revision\": \"t\", \"rules\": []}",
                        Refusal.MALFORMED, "format_version"),
                refusal("an empty revision", "{\"format_version\": 1, \"revision\": \"\", \"rules\": []}",
                        Refusal.MALFORMED, "revision"),
                refusal("rules that are no array", "{\"format_version\": 1, \"revision\": \"t\", \"rules\": {}}",
                        Refusal.MALFORMED, "'rules'"),
                refusal("an undeclared member of the file",
                        "{\"format_version\": 1, \"revision\": \"t\", \"rules\": [], \"extra\": 1}",
                        Refusal.MALFORMED, "extra"),
                refusal("an unregistered fact", file2("""
                        {"id": "r", "when": {"fact": "weather", "equals": "rain"}, "then": {}}"""),
                        Refusal.MALFORMED, "weather"),
                refusal("an unregistered declaration of an axis", file2("""
                        {"id": "r", "when": {"fact": "axes.asking.colour", "equals": "red"}, "then": {}}"""),
                        Refusal.MALFORMED, "axes.asking.colour"),
                refusal("an unregistered axis", file2("""
                        {"id": "r", "when": {"fact": "axes.weather", "equals": "rain"}, "then": {}}"""),
                        Refusal.MALFORMED, "axes.weather"),
                refusal("a declaration the variants of another axis declare", file2("""
                        {"id": "r", "when": {"fact": "axes.method.self_merge", "equals": "true"}, "then": {}}"""),
                        Refusal.MALFORMED, "axes.method.self_merge"),
                refusal("two operators in one condition", file2("""
                        {"id": "r", "when": {"fact": "lane", "equals": "land", "in": ["deep"]}, "then": {}}"""),
                        Refusal.MALFORMED, "exactly one"),
                refusal("an empty flag that is not true", file2("""
                        {"id": "r", "when": {"fact": "derived.push_remote", "empty": false}, "then": {}}"""),
                        Refusal.MALFORMED, "must be true"),
                refusal("a rule id declared twice", file2("""
                        {"id": "r", "when": {"fact": "lane", "equals": "land"}, "then": {}},
                        {"id": "r", "when": {"fact": "lane", "equals": "deep"}, "then": {}}"""),
                        Refusal.MALFORMED, "twice"),
                refusal("an invalid rule id", file2("""
                        {"id": "R 1", "when": {"fact": "lane", "equals": "land"}, "then": {}}"""),
                        Refusal.MALFORMED, "rule id"),
                refusal("an undeclared member of a rule", rule("\"then\": {}, \"extra\": true"), Refusal.MALFORMED,
                        "extra"),
                refusal("an undeclared prune reason", rule("""
                        "then": {"prune": [{"element": "step", "id": "x", "reason": "because"}]}"""),
                        Refusal.MALFORMED, "prune entry"),
                refusal("the removed prune reason recipe_override", rule("""
                        "then": {"prune": [{"element": "step", "id": "x", "reason": "recipe_override"}]}"""),
                        Refusal.MALFORMED, "prune entry"),
                refusal("the removed prune reason auto_merge_off", rule("""
                        "then": {"prune": [{"element": "state", "id": "x", "reason": "auto_merge_off"}]}"""),
                        Refusal.MALFORMED, "prune entry"),
                refusal("a binding value whose exponent is too large to strip", rule("""
                        "then": {"bind": [{"call_site": "*", "argument": "actions", "value": 1000e2147483647}]}"""),
                        Refusal.MALFORMED, "value"),
                refusal("a binding value that is no literal", rule("""
                        "then": {"bind": [{"call_site": "*", "argument": "actions", "value": {"a": 1}}]}"""),
                        Refusal.MALFORMED, "value"));
    }

    private static Arguments refusal(String name, String content, Refusal refusal, String detailPart) {
        return Arguments.of(name, content, refusal, detailPart);
    }

    /** A file with one rule {@code r} over the lane, whose body after {@code when} is given. */
    private static String rule(String afterWhen) {
        return file2("{\"id\": \"r\", \"when\": {\"fact\": \"lane\", \"equals\": \"land\"}, " + afterWhen + "}");
    }

    private static String file2(String rules) {
        return "{\"format_version\": 1, \"revision\": \"t1\", \"rules\": [" + rules + "]}";
    }
}
