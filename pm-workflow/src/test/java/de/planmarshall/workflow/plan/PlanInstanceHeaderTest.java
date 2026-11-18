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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.core.SchemaResource;
import de.planmarshall.workflow.ast.CallValue;
import de.planmarshall.workflow.ast.UnitOrigin;
import org.jspecify.annotations.Nullable;

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

    private static final String HEADER_SCHEMA = "de/planmarshall/workflow/plan/plan-instance-header.schema.json";
    private static final String COMPOSITION_SCHEMA = "de/planmarshall/workflow/plan/composition-record.schema.json";

    /** The {@code composition_event} enum of the header schema file, in its order. */
    private static List<String> fileCompositionEvents() {
        var defs = SchemaResource.object(SchemaResource.load(HEADER_SCHEMA).get("$defs"));
        return strings(SchemaResource.object(defs.get("composition_event")).get("enum"));
    }

    /** The {@code prune_reason} enum of the header schema file, in its order. */
    private static List<String> filePruneReasons() {
        var defs = SchemaResource.object(SchemaResource.load(HEADER_SCHEMA).get("$defs"));
        return strings(SchemaResource.object(defs.get("prune_reason")).get("enum"));
    }

    /** The {@code fallback_cause} enum of the composition record schema file, in its order, without {@code null}. */
    private static List<String> fileFallbackCauses() {
        var cause = SchemaResource.object(SchemaResource.object(SchemaResource.object(
                SchemaResource.load(COMPOSITION_SCHEMA).get("properties")).get("cause")).get("properties"));
        return strings(SchemaResource.object(cause.get("fallback_cause")).get("enum"));
    }

    /** The {@code trigger} enum of the composition record schema file, in its order. */
    private static List<String> fileTriggers() {
        var properties = SchemaResource.object(SchemaResource.load(COMPOSITION_SCHEMA).get("properties"));
        return strings(SchemaResource.object(properties.get("trigger")).get("enum"));
    }

    private static Map<?, ?> headerProperties() {
        return SchemaResource.object(SchemaResource.load(HEADER_SCHEMA).get("properties"));
    }

    private static List<String> fileLanes() {
        return strings(SchemaResource.object(headerProperties().get("lane")).get("enum"));
    }

    private static List<String> filePostures() {
        return strings(SchemaResource.object(headerProperties().get("posture")).get("enum"));
    }

    private static List<String> fileElementKinds() {
        var pruned = SchemaResource.object(SchemaResource.object(headerProperties().get("pruned")).get("items"));
        return strings(SchemaResource.object(
                SchemaResource.object(pruned.get("properties")).get("element")).get("enum"));
    }

    private static List<String> fileArgumentSources() {
        var calls = SchemaResource.object(SchemaResource.object(headerProperties().get("policy_calls")).get("items"));
        var args = SchemaResource.object(SchemaResource.object(calls.get("properties")).get("args"));
        var argument = SchemaResource.object(args.get("additionalProperties"));
        return strings(SchemaResource.object(
                SchemaResource.object(argument.get("properties")).get("source")).get("enum"));
    }

    private static List<String> fileUnitOrigins() {
        var unitRef = SchemaResource.object(SchemaResource.object(
                SchemaResource.load(HEADER_SCHEMA).get("$defs")).get("unit_ref"));
        return strings(SchemaResource.object(
                SchemaResource.object(unitRef.get("properties")).get("origin")).get("enum"));
    }

    private static List<String> strings(Object value) {
        return SchemaResource.array(value).stream().filter(Objects::nonNull).map(String::valueOf).toList();
    }

    private static List<String> closedSetDiffs(List<String> file, List<String> recorded) {
        var diffs = new ArrayList<String>();
        file.stream().filter(value -> !recorded.contains(value)).forEach(value -> diffs.add("file-only:" + value));
        recorded.stream().filter(value -> !file.contains(value)).forEach(value -> diffs.add("record-only:" + value));
        return diffs;
    }

    /** A header with every list empty and every optional member absent. */
    static PlanInstanceHeader minimalHeader() {
        return new PlanInstanceHeader("plan-24", 1, CompositionEvent.START, COMPOSED_AT,
                new CompositionInputs("c", List.of(), List.of(), "r1", DIGEST_A, DIGEST_B, null, null, null),
                null, null, new PlanInstanceHeader.Steps(List.of(), List.of()), List.of(), List.of(), List.of());
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
                        List.of(new UnitReference("plan", ".marshall/config/plan.pmwf", DIGEST_B,
                                UnitOrigin.PROJECT)),
                        List.of(new UnitReference("remediation.ci-failure", null, DIGEST_C, UnitOrigin.DEFAULT)),
                        "r7", DIGEST_D, DIGEST_A, DIGEST_B, "feature", DIGEST_C),
                PlanInstanceHeader.Lane.DEEP, PlanInstanceHeader.Posture.STANDARD,
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

            assertEquals(expected, HeaderJson.write(minimalHeader()), "the minimal header writes the canonical line");
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
                            .contains("Größe"), "a character outside ASCII survives UTF-8"));
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
                    () -> assertEquals(first, second, "writing twice yields the same line"),
                    () -> assertEquals(first, reread, "the reread header writes the same line"),
                    () -> assertArrayEquals(sha256(first), sha256(reread),
                            "the reread header has the same digest"));
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

            assertEquals(HeaderJson.write(header), HeaderJson.write(rebuilt),
                    "argument order changes no written byte");
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

            assertEquals(header, HeaderJson.read(HeaderJson.write(header)),
                    "a full header reads back equal after writing");
        }

        @Test
        @DisplayName("a header with every list empty and every optional member absent survives the round trip")
        void minimalHeaderSurvives() {
            var header = PlanInstanceHeaderTest.minimalHeader();

            assertEquals(header, HeaderJson.read(HeaderJson.write(header)), "the minimal header reads back equal");
        }

        @Test
        @DisplayName("an absent member reads back absent and a present member reads back as its value")
        void absentAndPresentMembersSurvive() {
            var read = HeaderJson.read(HeaderJson.write(fullHeader("plan-24", 1, "lane")));
            var inputs = read.inputs();
            var args = read.policyCalls().getFirst().args();

            assertAll(
                    () -> assertEquals(PlanInstanceHeader.Lane.DEEP, read.lane(), "the lane reads back as DEEP"),
                    () -> assertEquals(PlanInstanceHeader.Posture.STANDARD, read.posture(), "the posture reads back"),
                    () -> assertEquals(".marshall/config/plan.pmwf", inputs.templates().getFirst().file(),
                            "a present unit file reads back"),
                    () -> assertNull(inputs.subUnits().getFirst().file(), "an absent unit file reads back null"),
                    () -> assertEquals("feature", inputs.changeType(), "the change type reads back"),
                    () -> assertEquals(DIGEST_B, inputs.realizedFootprintDigest(), "the footprint digest reads back"),
                    () -> assertEquals(DIGEST_C, inputs.providersDigest(), "the providers digest reads back"),
                    () -> assertNull(args.get("actions").file(), "an absent argument file reads back null"),
                    () -> assertNull(args.get("actions").waiverId(), "an absent waiver id reads back null"),
                    () -> assertEquals("W-1", args.get("fix_budget").waiverId(), "a present waiver id reads back"),
                    () -> assertEquals("finalize.pmwf", args.get("mode").file(), "a present argument file reads back"));
        }

        @Test
        @DisplayName("the written header satisfies every constraint of the schema")
        void writtenHeaderIsValid() {
            assertAll(
                    () -> assertEquals(List.of(), HeaderSchemaCheck.check(fullHeader("plan-24", 1, "lane")),
                            "the full header breaks no schema constraint"),
                    () -> assertEquals(List.of(), HeaderSchemaCheck.check(PlanInstanceHeaderTest.minimalHeader()),
                            "the minimal header breaks no constraint"));
        }

        @Test
        @DisplayName("an absent optional member reads like null, and composed_at with an offset reads as its instant")
        void lenientShapesRead() {
            var json = HeaderJson.write(PlanInstanceHeaderTest.minimalHeader())
                    .replace("\"posture\":null,", "")
                    .replace("\"change_type\":null,", "")
                    .replace("2026-10-14T08:05:00Z", "2026-10-14t10:05:00+02:00");

            assertEquals(PlanInstanceHeaderTest.minimalHeader(), HeaderJson.read(json),
                    "absent members read like null values");
        }

        @Test
        @DisplayName("a policy call's in_unit survives the round trip and is written only when present")
        void inUnitSurvives() {
            var header = nestedHeader("phase-6-finalize");
            var json = HeaderJson.write(header);
            var read = HeaderJson.read(json);

            assertAll(
                    () -> assertTrue(json.contains("\"in_unit\":\"phase-6-finalize\""),
                            "the nested call writes its in_unit"),
                    () -> assertEquals(json.indexOf("\"in_unit\""), json.lastIndexOf("\"in_unit\""),
                            "only the nested call writes an in_unit"),
                    () -> assertFalse(json.contains("\"in_unit\":null"),
                            "the plan unit's call writes no in_unit"),
                    () -> assertEquals("phase-6-finalize", read.policyCalls().getLast().inUnit(),
                            "the in_unit reads back"),
                    () -> assertEquals(header, read, "the nested call survives the round trip"),
                    () -> assertEquals(List.of(), HeaderSchemaCheck.check(header),
                            "a composed unit of the templates is accepted"));
        }

        @Test
        @DisplayName("an in_unit that names no composed unit of the header's templates is refused")
        void inUnitOfNoComposedUnitRefused() {
            assertAll(
                    () -> assertEquals(List.of(new HeaderViolation("/policy_calls/1/in_unit",
                                    HeaderSchemaCheck.NOT_COMPOSED_UNIT)),
                            HeaderSchemaCheck.check(nestedHeader("no-such-unit")),
                            "an unknown unit is refused"),
                    () -> assertEquals(List.of(new HeaderViolation("/policy_calls/1/in_unit",
                                    HeaderSchemaCheck.NOT_COMPOSED_UNIT)),
                            HeaderSchemaCheck.check(nestedHeader(PlanInstanceComposer.PLAN_UNIT)),
                            "the plan unit is no composed unit"));
        }

        @Test
        @DisplayName("a unit and call site listed twice is refused at the second entry")
        void duplicateCallSiteRefused() {
            var header = nestedHeader("phase-6-finalize");
            var nested = header.policyCalls().getLast();
            var twice = new PlanInstanceHeader(header.planId(), header.version(), header.event(),
                    header.composedAt(), header.inputs(), header.lane(), header.posture(), header.steps(),
                    header.pruned(), List.of(header.policyCalls().getFirst(), nested, nested), header.waivedGates());

            assertEquals(List.of(new HeaderViolation("/policy_calls/2",
                            HeaderSchemaCheck.DUPLICATE_CALL_SITE)), HeaderSchemaCheck.check(twice),
                    "the second entry of the same unit and call site is refused");
        }

        @Test
        @DisplayName("a template without a composed file supplies no in_unit")
        void filelessTemplateIsNoComposedUnit() {
            assertEquals(List.of(new HeaderViolation("/policy_calls/1/in_unit",
                            HeaderSchemaCheck.NOT_COMPOSED_UNIT)),
                    HeaderSchemaCheck.check(nestedHeader("phase-6-finalize", null)),
                    "a fileless template is no composed unit");
        }

        /** A header whose templates name the plan unit and {@code phase-6-finalize}, with a nested policy call. */
        private static PlanInstanceHeader nestedHeader(String inUnit) {
            return nestedHeader(inUnit, "instance/phase-6-finalize.v2.pmwf");
        }

        private static PlanInstanceHeader nestedHeader(String inUnit, @Nullable String composedFile) {
            var base = fullHeader("plan-24", 2, "lane");
            var nested = PolicyCall.of("execute.remediate", "remediation.qgate-findings",
                    Map.of("fix_budget", BoundArgument.fromTemplate(new CallValue.IntValue(3), "execute.pmwf")))
                    .withInUnit(inUnit);
            var inputs = new CompositionInputs(base.inputs().configHash(),
                    List.of(new UnitReference("plan", ".marshall/config/plan.pmwf", DIGEST_B,
                                    UnitOrigin.PROJECT),
                            new UnitReference("phase-6-finalize", composedFile, DIGEST_C, UnitOrigin.DEFAULT)),
                    base.inputs().subUnits(), base.inputs().compositionRulesRevision(),
                    base.inputs().compositionRulesDigest(), base.inputs().planFactsDigest(),
                    base.inputs().realizedFootprintDigest(), base.inputs().changeType(),
                    base.inputs().providersDigest());
            return new PlanInstanceHeader(base.planId(), base.version(), base.event(), base.composedAt(), inputs,
                    base.lane(), base.posture(), base.steps(), base.pruned(),
                    List.of(base.policyCalls().getFirst(), nested), base.waivedGates());
        }
    }

    @Nested
    @DisplayName("Reading refusals")
    class ReadingRefusals {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"not json", "{} {}", ""})
        @DisplayName("a line that is no single JSON value is refused")
        void malformed(String json) {
            assertThrows(IllegalArgumentException.class, () -> HeaderJson.read(json),
                    "a line of no single JSON value is refused");
        }

        @Test
        @DisplayName("a header that breaks the schema is refused naming the violation")
        void violation() {
            var json = HeaderJson.write(PlanInstanceHeaderTest.minimalHeader()).replace("\"plan-24\"",
                    "\"Plan_24\"");

            var refused = assertThrows(IllegalArgumentException.class, () -> HeaderJson.read(json),
                    "a header breaking the schema is refused");

            assertTrue(refused.getMessage().contains("/plan_id"), refused.getMessage());
        }

        @Test
        @DisplayName("an argument value that is no literal of the DSL is refused")
        void nonLiteralValue() {
            var json = HeaderJson.write(fullHeader("plan-24", 1, "lane")).replace("\"value\":4", "\"value\":{}");

            var refused = assertThrows(IllegalArgumentException.class, () -> HeaderJson.read(json),
                    "an argument that is no DSL literal is refused");

            assertTrue(refused.getMessage().contains("fix_budget"), refused.getMessage());
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"2026-02-30T08:05:00Z", "2026-10-14T08:05:60Z", "2026-10-14T08:05:00+23:59"})
        @DisplayName("a composed_at the reader cannot parse is refused as an instance header")
        void unparsableDateTime(String composedAt) {
            var json = HeaderJson.write(PlanInstanceHeaderTest.minimalHeader()).replace("2026-10-14T08:05:00Z",
                    composedAt);

            var refused = assertThrows(IllegalArgumentException.class, () -> HeaderJson.read(json),
                    "an unparsable composed_at is refused");

            assertTrue(refused.getMessage().contains("/composed_at"), refused.getMessage());
        }

        @Test
        @DisplayName("a version beyond the range of an int is refused")
        void versionOutOfRange() {
            var json = HeaderJson.write(PlanInstanceHeaderTest.minimalHeader()).replace("\"version\":1",
                    "\"version\":3000000000");

            assertThrows(IllegalArgumentException.class, () -> HeaderJson.read(json),
                    "a version beyond the int range is refused");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"1e999999999", "9.9E+999999999", "10000000000"})
        @DisplayName("a version whose magnitude no int reaches is refused fast, before its digits are expanded")
        void hugeVersionRefusedFast(String version) {
            var json = HeaderJson.write(PlanInstanceHeaderTest.minimalHeader()).replace("\"version\":1",
                    "\"version\":" + version);

            var refused = assertTimeoutPreemptively(Duration.ofSeconds(5),
                    () -> assertThrows(IllegalArgumentException.class, () -> HeaderJson.read(json),
                            "a huge version is refused as out of range"),
                    "a huge version is refused within five seconds");

            assertTrue(refused.getMessage().startsWith("Instance header refused: 'version' is out of range: "),
                    refused.getMessage());
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"1000e2147483647", "-1000e2147483647", "1000E+2147483647"})
        @DisplayName("a version whose exponent is too large to strip is refused as out of range, not by an overflow")
        void exponentOverflowingVersionRefused(String version) {
            var json = HeaderJson.write(PlanInstanceHeaderTest.minimalHeader()).replace("\"version\":1",
                    "\"version\":" + version);

            var refused = assertThrows(IllegalArgumentException.class, () -> HeaderJson.read(json),
                    "an exponent too large to strip is refused");

            assertTrue(refused.getMessage().startsWith("Instance header refused: "), refused.getMessage());
        }

        @Test
        @DisplayName("an integral policy argument whose exponent is too large to strip is refused as out of range")
        void exponentOverflowingArgumentRefused() {
            var json = HeaderJson.write(fullHeader("plan-24", 1, "lane")).replace("\"value\":4",
                    "\"value\":1000e2147483647");

            var refused = assertThrows(IllegalArgumentException.class, () -> HeaderJson.read(json),
                    "a policy argument out of range is refused");

            assertTrue(refused.getMessage().startsWith("Instance header refused: 'fix_budget' is out of range: "),
                    refused.getMessage());
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"100", "1.0E+2", "1.00e2", "100.000"})
        @DisplayName("an integral version and argument with trailing zeros or an exponent still read")
        void integralWithTrailingZerosReads(String number) {
            var json = HeaderJson.write(fullHeader("plan-24", 1, "lane")).replace("\"version\":1",
                    "\"version\":" + number).replace("\"value\":4", "\"value\":" + number);

            var header = HeaderJson.read(json);

            assertAll(
                    () -> assertEquals(100, header.version(), "a version with trailing zeros reads as 100"),
                    () -> assertEquals(new CallValue.IntValue(100),
                            header.policyCalls().getFirst().args().get("fix_budget").value(),
                            "an integral argument value reads as 100"));
        }

        @Test
        @DisplayName("an integral policy argument whose magnitude no int reaches is refused fast")
        void hugeArgumentRefusedFast() {
            var json = HeaderJson.write(fullHeader("plan-24", 1, "lane")).replace("\"value\":4",
                    "\"value\":1e999999999");

            var refused = assertTimeoutPreemptively(Duration.ofSeconds(5),
                    () -> assertThrows(IllegalArgumentException.class, () -> HeaderJson.read(json),
                            "a huge argument is refused as out of range"),
                    "a huge argument is refused within five seconds");

            assertEquals("Instance header refused: 'fix_budget' is out of range: 1E+999999999", refused.getMessage(),
                    "the huge argument is named as out of range");
        }

        @Test
        @DisplayName("the largest int as version and as an integral argument written with an exponent still reads")
        void largestIntReads() {
            var json = HeaderJson.write(fullHeader("plan-24", Integer.MAX_VALUE, "lane")).replace("\"value\":4",
                    "\"value\":4.0e0");

            var header = HeaderJson.read(json);

            assertAll(
                    () -> assertEquals(Integer.MAX_VALUE, header.version(), "the largest int reads back unchanged"),
                    () -> assertEquals(new CallValue.IntValue(4),
                            header.policyCalls().getFirst().args().get("fix_budget").value(),
                            "an integral argument with an exponent reads back"));
        }
    }

    @Nested
    @DisplayName("Wire names")
    class WireNames {

        static Stream<CompositionEvent> compositionEvents() {
            return Arrays.stream(CompositionEvent.values());
        }

        @ParameterizedTest(name = "{0} reads back by its own wire name")
        @MethodSource("compositionEvents")
        @DisplayName("a composition event reads back and names a trigger of the file")
        void compositionEvent(CompositionEvent event) {
            assertAll(
                    () -> assertEquals(Optional.of(event), CompositionEvent.fromWireName(event.wireName()),
                            "the event should read back by its wire name"),
                    () -> assertTrue(fileTriggers().contains(event.trigger().wireName()),
                            "the event's trigger should be the file's"));
        }

        @Test
        @DisplayName("the composition events are the composition_event enum of the file, in its order")
        void compositionEventsClosed() {
            assertAll(
                    () -> assertEquals(fileCompositionEvents(), Arrays.stream(CompositionEvent.values())
                            .map(CompositionEvent::wireName).toList(), "the events should equal the file's"),
                    () -> assertEquals(fileTriggers(), Arrays.stream(CompositionEvent.Trigger.values())
                            .map(CompositionEvent.Trigger::wireName).toList(), "the triggers should equal the file's"),
                    () -> assertEquals(Optional.empty(), CompositionEvent.fromWireName("START"),
                            "an uppercase event should be refused"));
        }

        @Test
        @DisplayName("the closed-set comparison reports a value only one side has")
        void closedSetComparisonReportsOneSidedValues() {
            var recorded = Arrays.stream(PruneReason.values()).map(PruneReason::wireName).toList();
            var file = new ArrayList<>(filePruneReasons());
            file.add("synthetic_reason");

            assertAll(
                    () -> assertEquals(List.of("file-only:synthetic_reason"), closedSetDiffs(file, recorded),
                            "a file-only value should be reported"),
                    () -> assertEquals(List.of("record-only:posture"),
                            closedSetDiffs(recorded.subList(1, recorded.size()), recorded),
                            "a record-only value should be reported"));
        }

        @Test
        @DisplayName("every prune reason reads back by its own wire name")
        void everyPruneReasonReadsBack() {
            for (var reason : PruneReason.values()) {
                assertEquals(Optional.of(reason), PruneReason.fromWireName(reason.wireName()),
                        "a prune reason should read back by its wire name");
            }
        }

        @Test
        @DisplayName("the prune reasons are the prune_reason enum of the file, in its order")
        void pruneReasonsClosed() {
            assertEquals(filePruneReasons(), Arrays.stream(PruneReason.values()).map(PruneReason::wireName).toList(),
                    "the prune reasons should equal the file's");
        }

        /**
         * The two new values are accepted by the type and listed by the file. No JSON-schema validator is on this
         * module's class path (adding a dependency is out of scope), so "validates against the file" is read as the two
         * halves this test can show: the file names the value and the type accepts it. A real schema validation of a
         * written header waits for {@code runtime-schema-validation}.
         */
        @Test
        @DisplayName("the validator accepts a header carrying the two new prune reasons")
        void newPruneReasonsValidate() {
            var base = minimalHeader();
            var pruned = List.of(
                    new PrunedElement(PrunedElement.Kind.STEP, "default:create-pr", PruneReason.NO_CODE_DELTA,
                            "change_type"),
                    new PrunedElement(PrunedElement.Kind.STEP, "default:push", PruneReason.NO_PRODUCTION_DELTA,
                            "change_type"));
            var header = new PlanInstanceHeader(base.planId(), base.version(), base.event(), base.composedAt(),
                    base.inputs(), base.lane(), base.posture(), base.steps(), pruned, List.of(), List.of());

            assertAll(
                    () -> assertEquals(List.of(), HeaderSchemaCheck.check(header),
                            "the header with the new reasons should be valid"),
                    () -> assertTrue(filePruneReasons().containsAll(List.of("no_code_delta", "no_production_delta")),
                            "the file should list the new prune reasons"));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"recipe_override", "auto_merge_off", "unknown"})
        @DisplayName("a reason the specification does not list reads as no prune reason")
        void removedPruneReasons(String wireName) {
            assertEquals(Optional.empty(), PruneReason.fromWireName(wireName),
                    "an unlisted reason should have no value");
        }

        @Test
        @DisplayName("the fallback causes are the fallback_cause enum of the file, in its order")
        void fallbackCausesClosed() {
            assertEquals(fileFallbackCauses(), Arrays.stream(CompositionCause.FallbackCause.values())
                            .map(CompositionCause.FallbackCause::wireName).toList(),
                    "the fallback causes should equal the file's");
        }

        @Test
        @DisplayName("the type and the file accept the new fallback cause")
        void newFallbackCauseValidates() {
            var cause = new CompositionCause(null, CompositionCause.FallbackCause.OPERATOR_PATH, null, null, null,
                    null, null, null);
            var line = new CompositionRecord(CompositionEvent.FALLBACK, CompositionRecord.Result.REFUSED,
                    null, CompositionEvent.Trigger.SERVER, cause, new TreeMap<>(), null, COMPOSED_AT);
            var writtenCause = SchemaResource.object(SchemaResource.object(
                    SchemaResource.parse(line.toJson())).get("cause"));

            assertAll(
                    () -> assertTrue(fileFallbackCauses().contains("operator_path"),
                            "the file should list the new fallback cause"),
                    () -> assertEquals("operator_path", writtenCause.get("fallback_cause"),
                            "the line should carry the new fallback cause"),
                    () -> assertEquals(Optional.of(CompositionCause.FallbackCause.OPERATOR_PATH),
                            CompositionCause.FallbackCause.fromWireName("operator_path"),
                            "the type should accept the new fallback cause"));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"question_expired", "auto_merge_off"})
        @DisplayName("a fallback cause the specification does not list reads as no fallback cause")
        void removedFallbackCauses(String wireName) {
            assertEquals(Optional.empty(), CompositionCause.FallbackCause.fromWireName(wireName),
                    "an unlisted cause should have no value");
        }

        /**
         * The pinned drifts: {@code BoundArgument.Source} carries no {@code override} and {@code UnitOrigin} no
         * {@code machine}; {@code machine-onboarding-unit} builds them (tasks 12 and 1).
         */
        @Test
        @DisplayName("the other wire names equal their files, with two named drifts pinned")
        void otherEnumerations() {
            var kinds = Arrays.stream(PrunedElement.Kind.values()).map(PrunedElement.Kind::wireName).toList();
            var sources = Arrays.stream(BoundArgument.Source.values()).map(BoundArgument.Source::wireName).toList();
            var lanes = Arrays.stream(PlanInstanceHeader.Lane.values()).map(PlanInstanceHeader.Lane::wireName)
                    .toList();
            var postures = Arrays.stream(PlanInstanceHeader.Posture.values())
                    .map(PlanInstanceHeader.Posture::wireName).toList();
            var origins = Arrays.stream(UnitOrigin.values()).map(UnitReference::wireName).toList();

            assertAll(
                    () -> assertEquals(fileElementKinds(), kinds, "the element kinds should equal the file's"),
                    () -> assertEquals(Optional.of(PrunedElement.Kind.CALL),
                            PrunedElement.Kind.fromWireName("call"), "a call kind should read back"),
                    () -> assertEquals(List.of("file-only:override"), closedSetDiffs(fileArgumentSources(), sources),
                            "the source drift is pinned to machine-onboarding-unit"),
                    () -> assertEquals(fileLanes(), lanes, "the lanes should equal the file's"),
                    () -> assertEquals(filePostures(), postures, "the postures should equal the file's"),
                    () -> assertEquals(List.of("file-only:machine"), closedSetDiffs(fileUnitOrigins(), origins),
                            "the origin drift is pinned to machine-onboarding-unit"),
                    () -> assertEquals(Optional.of(PrunedElement.Kind.POLICY_ACTION),
                            PrunedElement.Kind.fromWireName("policy_action"), "a policy action kind should read back"),
                    () -> assertEquals(Optional.empty(), BoundArgument.Source.fromWireName("operator"),
                            "an unlisted argument source should be refused"),
                    () -> assertEquals(Optional.empty(), PlanInstanceHeader.Lane.fromWireName("fast"),
                            "an unlisted lane should be refused"),
                    () -> assertEquals(Optional.empty(), PlanInstanceHeader.Posture.fromWireName("max"),
                            "an unlisted posture should be refused"));
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
                    () -> assertEquals(new BoundArgument(value, BoundArgument.Source.TEMPLATE, "finalize.pmwf", null),
                            BoundArgument.fromTemplate(value, "finalize.pmwf"), "a template value names its file"),
                    () -> assertEquals(new BoundArgument(value, BoundArgument.Source.COMPOSITION_RULE, null, null),
                            BoundArgument.fromCompositionRule(value), "a rule value names neither file nor waiver"),
                    () -> assertEquals(new BoundArgument(value, BoundArgument.Source.WAIVER, null, "W-9"),
                            BoundArgument.fromWaiver(value, "W-9"), "a waiver value names its waiver"));
        }

        @Test
        @DisplayName("every optional component may be absent, and the absent components are null")
        void absentComponentsAccepted() {
            var header = minimalHeader();
            var reference = new UnitReference("unit", null, DIGEST_A, UnitOrigin.DEFAULT);
            var argument = BoundArgument.fromCompositionRule(new CallValue.IntValue(1));

            assertAll(
                    () -> assertNull(header.lane(), "the lane of the minimal header is absent"),
                    () -> assertNull(header.posture(), "the posture of the minimal header is absent"),
                    () -> assertNull(header.inputs().realizedFootprintDigest(), "the footprint digest is absent"),
                    () -> assertNull(header.inputs().changeType(), "the change type is absent"),
                    () -> assertNull(header.inputs().providersDigest(), "the providers digest is absent"),
                    () -> assertNull(reference.file(), "the file of the reference is absent"),
                    () -> assertNull(argument.file(), "the file of a rule argument is absent"),
                    () -> assertNull(argument.waiverId(), "the waiver of a rule argument is absent"));
        }

        @Test
        @DisplayName("the lists and the arguments are copies the caller cannot change")
        @SuppressWarnings("java:S5778") // the getter chain is the copy under test; hoisting it would test nothing
        void unmodifiable() {
            var header = fullHeader("plan-24", 1, "lane");

            assertAll(
                    () -> assertThrows(UnsupportedOperationException.class, () -> header.pruned().clear(),
                            "the pruned list of the header is a copy"),
                    () -> assertThrows(UnsupportedOperationException.class, () -> header.inputs().templates()
                            .clear(), "the template list of the header is a copy"),
                    () -> assertThrows(UnsupportedOperationException.class, () -> header.steps().phase6()
                            .clear(), "the phase 6 list of the header is a copy"),
                    () -> assertThrows(UnsupportedOperationException.class, () -> header.policyCalls().getFirst()
                            .args().clear(), "the argument map of a call is a copy"),
                    () -> assertEquals(List.of("actions", "fix_budget", "mode", "retry_budget"),
                            List.copyOf(header.policyCalls().getFirst().args().keySet()),
                            "the argument names read back in order"));
        }

        @Test
        @DisplayName("a missing component is refused")
        @SuppressWarnings("java:S5778") // each lambda is one constructor call; its arguments are the case table
        void nullRefused() {
            assertAll(
                    () -> assertThrows(NullPointerException.class, () -> new PrunedElement(null, "id",
                            PruneReason.ADOPT, "lane"), "a pruned element without its kind is refused"),
                    () -> assertThrows(NullPointerException.class, () -> new WaivedGate("gate", null),
                            "a waived gate without its waiver id is refused"),
                    () -> assertThrows(NullPointerException.class, () -> new UnitReference("unit", null, null,
                            UnitOrigin.DEFAULT), "a unit reference without its digest is refused"),
                    () -> assertThrows(NullPointerException.class, () -> new BoundArgument(null,
                                    BoundArgument.Source.WAIVER, null, null),
                            "a bound argument without its value is refused"),
                    () -> assertThrows(NullPointerException.class, () -> new PolicyCall("site", null,
                            new TreeMap<>()), "a policy call without its unit is refused"),
                    () -> assertThrows(NullPointerException.class, () -> new HeaderViolation(null, "reason"),
                            "a header violation without its pointer is refused"),
                    () -> assertThrows(NullPointerException.class, () -> new PlanInstanceHeader("plan-24", 1,
                                    null, COMPOSED_AT, minimalHeader().inputs(), null, null,
                                    minimalHeader().steps(), List.of(), List.of(), List.of()),
                            "a header without its event is refused"),
                    () -> assertThrows(NullPointerException.class, () -> new CompositionInputs(null, List.of(),
                                    List.of(), "r", DIGEST_A, DIGEST_B, null, null, null),
                            "composition inputs without a config hash are refused"));
        }
    }
}
