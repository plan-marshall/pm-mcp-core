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
import java.util.stream.Stream;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.workflow.ast.CallValue;
import de.planmarshall.workflow.plan.CompositionRules.Refusal;

/**
 * The composition rule file (PM-IMPL-5, PM-EXT-7). The shipped file is a classpath resource of the module, outside
 * every location of model-facing content; it loads, and its revision and SHA-256 digest are stable. A file written for
 * the test that is malformed, or holds a rule that adds an element, binds a budget or names a gate, is refused as a
 * value naming that refusal; a rule never extends the template, never lifts a budget and never touches a gate.
 */
@DisplayName("CompositionRules")
class CompositionRulesTest {

    private static final List<String> SHIPPED_RULE_IDS = List.of("land-lane", "adopt-lane", "local-only",
            "auto-merge-off", "posture-minimal", "posture-standard", "posture-default-off", "post-run-returned",
            "post-run-handed-back", "post-run-empty-harvest");

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
                        "then": {"waive": [{"gate": "spend"}]}"""), Refusal.NAMES_GATE, "rule 'r'"),
                refusal("a rule with a gate member", rule("""
                        "then": {"gate": "spend"}"""), Refusal.NAMES_GATE, "rule 'r'"),
                refusal("a rule that binds a gate", rule("""
                        "then": {"bind": [{"call_site": "*", "argument": "gate", "value": "spend"}]}"""),
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
