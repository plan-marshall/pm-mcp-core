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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.workflow.ast.CallValue;
import de.planmarshall.workflow.ast.UnitOrigin;

/**
 * The plan instance header model and its canonical JSON (PM-IMPL-5, PM-EXT-7). The {@code instance} line is one line of
 * canonical JSON, members sorted by name, no whitespace, UTF-8: writing an unchanged header again yields the same
 * bytes and so the same digest, and a header read back from its line equals the header written. A header serialized
 * non-canonically would change the digest of an unchanged instance.
 */
@EnableGeneratorController
@DisplayName("PlanInstanceHeader")
class PlanInstanceHeaderTest {

    private static final String DIGEST_A = "a".repeat(64);
    private static final String DIGEST_B = "b".repeat(64);
    private static final String DIGEST_C = "c".repeat(64);
    private static final String DIGEST_D = "d".repeat(64);
    private static final Instant COMPOSED_AT = Instant.parse("2026-10-14T08:05:00Z");

    /** A header with every list empty and every optional member absent. */
    static PlanInstanceHeader minimalHeader() {
        return new PlanInstanceHeader("plan-24", 1, CompositionEvent.START, COMPOSED_AT,
                new CompositionInputs("c", List.of(), List.of(), "r1", DIGEST_A, DIGEST_B, Optional.empty(),
                        Optional.empty(), Optional.empty()),
                Optional.empty(), Optional.empty(), new PlanInstanceHeader.Steps(List.of(), List.of()), List.of(),
                List.of(), List.of());
    }

    /** A header with one element in every list, every optional member present and every argument value kind. */
    static PlanInstanceHeader fullHeader(String planId, int version, String sourceFact) {
        var args = Map.of(
                "fix_budget", BoundArgument.fromWaiver(new CallValue.IntValue(4), "W-1"),
                "retry_budget", BoundArgument.fromTemplate(new CallValue.IntValue(2), "finalize.pmwf"),
                "actions", BoundArgument.fromCompositionRule(new CallValue.EnumList(List.of("retry"))),
                "mode", BoundArgument.fromTemplate(new CallValue.EnumValue("strict"), "finalize.pmwf"));
        return new PlanInstanceHeader(planId, version, CompositionEvent.WAIVER, COMPOSED_AT,
                new CompositionInputs(DIGEST_A,
                        List.of(new UnitReference("plan", Optional.of(".marshall/config/plan.pmwf"), DIGEST_B,
                                UnitOrigin.PROJECT)),
                        List.of(new UnitReference("remediation.ci-failure", Optional.empty(), DIGEST_C,
                                UnitOrigin.DEFAULT)),
                        "r7", DIGEST_D, DIGEST_A, Optional.of(DIGEST_B), Optional.of("feature"), Optional.of(DIGEST_C)),
                Optional.of(PlanInstanceHeader.Lane.DEEP), Optional.of(PlanInstanceHeader.Posture.STANDARD),
                new PlanInstanceHeader.Steps(List.of("verify:quality-gate"),
                        List.of("default:push", "project:lint-docs")),
                List.of(new PrunedElement(PrunedElement.Kind.STEP, "default:create-pr", PruneReason.ADOPT,
                        sourceFact)),
                List.of(PolicyCall.of("finalize.remediate-ci", "remediation.ci-failure", args)),
                List.of(new WaivedGate("ci_check:build", "W-2")));
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Nested
    @DisplayName("Canonical JSON")
    class CanonicalJson {

        @Test
        @DisplayName("is one line, members sorted by name at every level, no whitespace, absent values as null")
        void exactSpelling() {
            var expected = "{\"composed_at\":\"2026-10-14T08:05:00Z\",\"event\":\"start\",\"format_version\":1,"
                    + "\"inputs\":{\"change_type\":null,\"composition_rules\":{\"digest\":\"" + DIGEST_A
                    + "\",\"revision\":\"r1\"},\"config_hash\":\"c\",\"plan_facts_digest\":\"" + DIGEST_B
                    + "\",\"providers_digest\":null,\"realized_footprint_digest\":null,\"sub_units\":[],"
                    + "\"templates\":[]},\"lane\":null,\"plan_id\":\"plan-24\",\"policy_calls\":[],"
                    + "\"posture\":null,\"pruned\":[],\"steps\":{\"phase_5_verification\":[],\"phase_6\":[]},"
                    + "\"version\":1,\"waived_gates\":[]}";

            assertEquals(expected, HeaderJson.write(minimalHeader()));
        }

        @Test
        @DisplayName("orders the arguments of a policy call and the members of each argument by name")
        void argumentsSorted() {
            var json = HeaderJson.write(fullHeader("plan-24", 3, "lane"));

            assertTrue(json.contains("\"args\":{\"actions\":{\"file\":null,\"source\":\"composition_rule\","
                    + "\"value\":[\"retry\"],\"waiver_id\":null},\"fix_budget\":{\"file\":null,\"source\":\"waiver\","
                    + "\"value\":4,\"waiver_id\":\"W-1\"},\"mode\":{\"file\":\"finalize.pmwf\",\"source\":\"template\","
                    + "\"value\":\"strict\",\"waiver_id\":null},\"retry_budget\":{\"file\":\"finalize.pmwf\","
                    + "\"source\":\"template\",\"value\":2,\"waiver_id\":null}}"), json);
        }

        @Test
        @DisplayName("writes a character outside ASCII as itself in UTF-8, never as an escape")
        void utf8() {
            var json = HeaderJson.write(fullHeader("plan-24", 1, "Größe"));

            assertAll(
                    () -> assertTrue(json.contains("\"source_fact\":\"Größe\""), json),
                    () -> assertTrue(new String(json.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)
                            .contains("Größe")));
        }

        @Test
        @DisplayName("re-serializing an unchanged header yields a byte-identical line and digest")
        void byteIdentical() {
            var planId = Generators.letterStrings(3, 20).next().toLowerCase(Locale.ROOT);
            var header = fullHeader(planId, Generators.integers(1, 100_000).next(),
                    Generators.letterStrings(1, 30).next());

            var first = HeaderJson.write(header);
            var second = HeaderJson.write(header);
            var reread = HeaderJson.write(HeaderJson.read(first));

            assertAll(
                    () -> assertEquals(first, second),
                    () -> assertEquals(first, reread),
                    () -> assertArrayEquals(sha256(first), sha256(reread)));
        }

        @Test
        @DisplayName("an equal header built from arguments in another order writes the same line")
        void argumentOrderIrrelevant() {
            var header = fullHeader("plan-24", 2, "lane");
            var call = header.policyCalls().getFirst();
            var reversed = new TreeMap<String, BoundArgument>(Comparator.reverseOrder());
            reversed.putAll(call.args());
            var rebuilt = new PlanInstanceHeader(header.planId(), header.version(), header.event(),
                    header.composedAt(), header.inputs(), header.lane(), header.posture(), header.steps(),
                    header.pruned(), List.of(PolicyCall.of(call.callSite(), call.unit(), reversed)),
                    header.waivedGates());

            assertEquals(HeaderJson.write(header), HeaderJson.write(rebuilt));
        }
    }

    @Nested
    @DisplayName("Round trip")
    class RoundTrip {

        @Test
        @DisplayName("a header read back from its line equals the header written")
        void fullHeaderSurvives() {
            var planId = Generators.letterStrings(3, 20).next().toLowerCase(Locale.ROOT);
            var header = PlanInstanceHeaderTest.fullHeader(planId, Generators.integers(1, 100_000).next(),
                    Generators.letterStrings(1, 30).next());

            assertEquals(header, HeaderJson.read(HeaderJson.write(header)));
        }

        @Test
        @DisplayName("a header with every list empty and every optional member absent survives the round trip")
        void minimalHeaderSurvives() {
            var header = PlanInstanceHeaderTest.minimalHeader();

            assertEquals(header, HeaderJson.read(HeaderJson.write(header)));
        }

        @Test
        @DisplayName("the written header satisfies every constraint of the schema")
        void writtenHeaderIsValid() {
            assertAll(
                    () -> assertEquals(List.of(), HeaderSchemaCheck.check(fullHeader("plan-24", 1, "lane"))),
                    () -> assertEquals(List.of(), HeaderSchemaCheck.check(PlanInstanceHeaderTest.minimalHeader())));
        }

        @Test
        @DisplayName("an absent optional member reads like null, and composed_at with an offset reads as its instant")
        void lenientShapesRead() {
            var json = HeaderJson.write(PlanInstanceHeaderTest.minimalHeader())
                    .replace("\"posture\":null,", "")
                    .replace("\"change_type\":null,", "")
                    .replace("2026-10-14T08:05:00Z", "2026-10-14t10:05:00+02:00");

            assertEquals(PlanInstanceHeaderTest.minimalHeader(), HeaderJson.read(json));
        }
    }

    @Nested
    @DisplayName("Reading refusals")
    class ReadingRefusals {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"not json", "{} {}", ""})
        @DisplayName("a line that is no single JSON value is refused")
        void malformed(String json) {
            assertThrows(IllegalArgumentException.class, () -> HeaderJson.read(json));
        }

        @Test
        @DisplayName("a header that breaks the schema is refused naming the violation")
        void violation() {
            var json = HeaderJson.write(PlanInstanceHeaderTest.minimalHeader()).replace("\"plan-24\"",
                    "\"Plan_24\"");

            var refused = assertThrows(IllegalArgumentException.class, () -> HeaderJson.read(json));

            assertTrue(refused.getMessage().contains("/plan_id"), refused.getMessage());
        }

        @Test
        @DisplayName("an argument value that is no literal of the DSL is refused")
        void nonLiteralValue() {
            var json = HeaderJson.write(fullHeader("plan-24", 1, "lane")).replace("\"value\":4", "\"value\":{}");

            var refused = assertThrows(IllegalArgumentException.class, () -> HeaderJson.read(json));

            assertTrue(refused.getMessage().contains("fix_budget"), refused.getMessage());
        }

        @Test
        @DisplayName("a version beyond the range of an int is refused")
        void versionOutOfRange() {
            var json = HeaderJson.write(PlanInstanceHeaderTest.minimalHeader()).replace("\"version\":1",
                    "\"version\":3000000000");

            assertThrows(IllegalArgumentException.class, () -> HeaderJson.read(json));
        }
    }

    @Nested
    @DisplayName("Wire names")
    class WireNames {

        @ParameterizedTest(name = "{0} is {1}, triggered by {2}")
        @CsvSource({
                "START, start, SERVER",
                "OUTLINE, outline, SERVER",
                "PLAN, plan, SERVER",
                "CONFIG_ADOPTED, config_adopted, OPERATOR",
                "WAIVER, waiver, OPERATOR",
                "FALLBACK, fallback, SERVER",
                "EMPTY_HARVEST, empty_harvest, SERVER",
                "REPLAN, replan, SERVER"})
        @DisplayName("a composition event carries its wire name and its trigger")
        void compositionEvent(CompositionEvent event, String wireName, CompositionEvent.Trigger trigger) {
            assertAll(
                    () -> assertEquals(wireName, event.wireName()),
                    () -> assertEquals(trigger, event.trigger()),
                    () -> assertEquals(Optional.of(event), CompositionEvent.fromWireName(wireName)));
        }

        @Test
        @DisplayName("the composition events are closed at eight, the triggers at server and operator")
        void compositionEventsClosed() {
            assertAll(
                    () -> assertEquals(8, CompositionEvent.values().length),
                    () -> assertEquals(List.of("server", "operator"), Arrays.stream(CompositionEvent.Trigger.values())
                            .map(CompositionEvent.Trigger::wireName).toList()),
                    () -> assertEquals(Optional.empty(), CompositionEvent.fromWireName("START")));
        }

        @ParameterizedTest(name = "{0} is {1}")
        @CsvSource({
                "POSTURE, posture",
                "PROVIDER_UNCONFIGURED, provider_unconfigured",
                "FOOTPRINT_DISJOINT, footprint_disjoint",
                "CHANGE_TYPE_EXEMPT, change_type_exempt",
                "RECIPE_OVERRIDE, recipe_override",
                "LOCAL_ONLY, local_only",
                "LAND_LANE, land_lane",
                "ADOPT, adopt",
                "AUTO_MERGE_OFF, auto_merge_off",
                "EMPTY_HARVEST, empty_harvest",
                "RETURNED, returned",
                "HANDED_BACK, handed_back"})
        @DisplayName("a prune reason carries its wire name")
        void pruneReason(PruneReason reason, String wireName) {
            assertAll(
                    () -> assertEquals(wireName, reason.wireName()),
                    () -> assertEquals(Optional.of(reason), PruneReason.fromWireName(wireName)));
        }

        @Test
        @DisplayName("the prune reasons are closed at twelve")
        void pruneReasonsClosed() {
            assertAll(
                    () -> assertEquals(12, PruneReason.values().length),
                    () -> assertEquals(Optional.empty(), PruneReason.fromWireName("unknown")));
        }

        @Test
        @DisplayName("element kinds, argument sources, lanes, postures and origins carry their wire names")
        void otherEnumerations() {
            assertAll(
                    () -> assertEquals(List.of("step", "state", "link", "policy_action"),
                            Arrays.stream(PrunedElement.Kind.values()).map(PrunedElement.Kind::wireName).toList()),
                    () -> assertEquals(List.of("template", "composition_rule", "waiver"),
                            Arrays.stream(BoundArgument.Source.values()).map(BoundArgument.Source::wireName)
                                    .toList()),
                    () -> assertEquals(List.of("light", "deep", "land", "adopt"),
                            Arrays.stream(PlanInstanceHeader.Lane.values()).map(PlanInstanceHeader.Lane::wireName)
                                    .toList()),
                    () -> assertEquals(List.of("minimal", "standard", "full"),
                            Arrays.stream(PlanInstanceHeader.Posture.values())
                                    .map(PlanInstanceHeader.Posture::wireName).toList()),
                    () -> assertEquals(List.of("default", "project"),
                            Arrays.stream(UnitOrigin.values()).map(UnitReference::wireName).toList()),
                    () -> assertEquals(Optional.of(PrunedElement.Kind.POLICY_ACTION),
                            PrunedElement.Kind.fromWireName("policy_action")),
                    () -> assertEquals(Optional.empty(), BoundArgument.Source.fromWireName("operator")),
                    () -> assertEquals(Optional.empty(), PlanInstanceHeader.Lane.fromWireName("fast")),
                    () -> assertEquals(Optional.empty(), PlanInstanceHeader.Posture.fromWireName("max")));
        }
    }

    @Nested
    @DisplayName("Values")
    class Values {

        @Test
        @DisplayName("the argument factories record the provenance of their source")
        void argumentFactories() {
            var value = new CallValue.IntValue(3);

            assertAll(
                    () -> assertEquals(new BoundArgument(value, BoundArgument.Source.TEMPLATE,
                                    Optional.of("finalize.pmwf"), Optional.empty()),
                            BoundArgument.fromTemplate(value, "finalize.pmwf")),
                    () -> assertEquals(new BoundArgument(value, BoundArgument.Source.COMPOSITION_RULE,
                            Optional.empty(), Optional.empty()), BoundArgument.fromCompositionRule(value)),
                    () -> assertEquals(new BoundArgument(value, BoundArgument.Source.WAIVER, Optional.empty(),
                            Optional.of("W-9")), BoundArgument.fromWaiver(value, "W-9")));
        }

        @Test
        @DisplayName("the lists and the arguments are copies the caller cannot change")
        void unmodifiable() {
            var header = fullHeader("plan-24", 1, "lane");

            assertAll(
                    () -> assertThrows(UnsupportedOperationException.class, () -> header.pruned().clear()),
                    () -> assertThrows(UnsupportedOperationException.class, () -> header.inputs().templates()
                            .clear()),
                    () -> assertThrows(UnsupportedOperationException.class, () -> header.steps().phase6()
                            .clear()),
                    () -> assertThrows(UnsupportedOperationException.class, () -> header.policyCalls().getFirst()
                            .args().clear()),
                    () -> assertEquals(List.of("actions", "fix_budget", "mode", "retry_budget"),
                            List.copyOf(header.policyCalls().getFirst().args().keySet())));
        }

        @Test
        @DisplayName("a missing component is refused")
        void nullRefused() {
            var empty = Optional.<String>empty();
            assertAll(
                    () -> assertThrows(NullPointerException.class, () -> new PrunedElement(null, "id",
                            PruneReason.ADOPT, "lane")),
                    () -> assertThrows(NullPointerException.class, () -> new WaivedGate("gate", null)),
                    () -> assertThrows(NullPointerException.class, () -> new UnitReference("unit", empty, null,
                            UnitOrigin.DEFAULT)),
                    () -> assertThrows(NullPointerException.class, () -> new BoundArgument(null,
                            BoundArgument.Source.WAIVER, empty, empty)),
                    () -> assertThrows(NullPointerException.class, () -> new PolicyCall("site", null,
                            new TreeMap<>())),
                    () -> assertThrows(NullPointerException.class, () -> new HeaderViolation(null, "reason")),
                    () -> assertThrows(NullPointerException.class, () -> new PlanInstanceHeader("plan-24", 1,
                            null, COMPOSED_AT, minimalHeader().inputs(), Optional.empty(), Optional.empty(),
                            minimalHeader().steps(), List.of(), List.of(), List.of())),
                    () -> assertThrows(NullPointerException.class, () -> new CompositionInputs(null, List.of(),
                            List.of(), "r", DIGEST_A, DIGEST_B, empty, empty, empty)));
        }
    }
}
