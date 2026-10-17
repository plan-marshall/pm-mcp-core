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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.workflow.plan.HeaderJson.ArrayNode;
import de.planmarshall.workflow.plan.HeaderJson.Node;
import de.planmarshall.workflow.plan.HeaderJson.ObjectNode;

/**
 * The check of a plan instance header against every constraint of the {@code PlanInstanceHeader} schema (PM-IMPL-5,
 * PM-EXT-7). A valid header with one element in every list yields no violation; each single mutation of it, a missing
 * required member, a member the schema does not declare at any level, a wrong constant, a value below its minimum, an
 * unknown value of a closed enumeration, a value against its pattern or of the wrong type, yields exactly its one
 * violation, named by the JSON pointer of the member. A text that is no single JSON value is one violation, never an
 * exception.
 */
@DisplayName("HeaderSchemaCheck")
class HeaderSchemaCheckTest {

    private static final String DIGEST_A = "a".repeat(64);
    private static final String DIGEST_B = "b".repeat(64);
    private static final String DIGEST_C = "c".repeat(64);
    private static final String DIGEST_D = "d".repeat(64);

    /** The reason a policy call naming a unit outside the six remediation policy units is refused with. */
    private static final String POLICY_UNITS_REASON = "must be one of [remediation.ci-failure,"
            + " remediation.review-findings, remediation.build-failure, remediation.merge-conflict,"
            + " remediation.qgate-findings, remediation.analysis]";

    /** A header that satisfies the schema, with one element in every list and both optional argument members. */
    private static final String VALID = """
            {
              "format_version": 1,
              "plan_id": "plan-24",
              "version": 3,
              "event": "waiver",
              "composed_at": "2026-10-14T08:05:00Z",
              "inputs": {
                "config_hash": "%1$s",
                "templates": [{"unit": "plan", "file": ".marshall/config/plan.pmwf", "digest": "%2$s",
                               "origin": "project"}],
                "sub_units": [{"unit": "remediation.ci-failure", "digest": "%3$s", "origin": "default"}],
                "composition_rules": {"revision": "r7", "digest": "%4$s"},
                "plan_facts_digest": "%1$s",
                "realized_footprint_digest": null,
                "change_type": "feature"
              },
              "lane": "deep",
              "posture": "standard",
              "steps": {"phase_5_verification": ["verify:quality-gate"],
                        "phase_6": ["default:push", "project:lint-docs"]},
              "pruned": [{"element": "step", "id": "default:create-pr", "reason": "adopt", "source_fact": "lane"}],
              "policy_calls": [{"call_site": "finalize.remediate-ci", "unit": "remediation.ci-failure",
                                "args": {"fix_budget": {"value": 4, "source": "waiver", "waiver_id": "W-1"},
                                         "actions": {"value": ["retry"], "source": "composition_rule",
                                                     "file": null}}}],
              "waived_gates": [{"gate": "ci_check:build", "waiver_id": "W-2"}]
            }
            """.formatted(DIGEST_A, DIGEST_B, DIGEST_C, DIGEST_D);

    private static Node json(String text) {
        try {
            return HeaderJson.parse(text);
        } catch (HeaderJson.Malformed e) {
            throw new AssertionError("fixture JSON is malformed: " + e.getMessage(), e);
        }
    }

    private static Node valid() {
        return json(VALID);
    }

    /** The valid header with the member or element at {@code pointer} replaced, added, or (when empty) removed. */
    private static Node edited(String pointer, Optional<Node> replacement) {
        var path = Arrays.asList(pointer.substring(1).split("/"));
        return edit(valid(), path, replacement);
    }

    private static Node edit(Node node, List<String> path, Optional<Node> replacement) {
        var token = path.getFirst();
        var rest = path.subList(1, path.size());
        return switch (node) {
            case ObjectNode object -> {
                var members = new LinkedHashMap<>(object.members());
                if (rest.isEmpty()) {
                    replacement.ifPresentOrElse(value -> members.put(token, value), () -> members.remove(token));
                } else {
                    members.put(token, edit(members.get(token), rest, replacement));
                }
                yield new ObjectNode(members);
            }
            case ArrayNode array -> {
                var elements = new ArrayList<>(array.elements());
                var index = Integer.parseInt(token);
                if (rest.isEmpty()) {
                    replacement.ifPresentOrElse(value -> elements.set(index, value), () -> elements.remove(index));
                } else {
                    elements.set(index, edit(elements.get(index), rest, replacement));
                }
                yield new ArrayNode(elements);
            }
            default -> throw new AssertionError("no object or array at " + token);
        };
    }

    @Nested
    @DisplayName("Valid header")
    class ValidHeader {

        @Test
        @DisplayName("a header that satisfies every constraint yields no violation")
        void noViolation() {
            assertAll(
                    () -> assertEquals(List.of(), HeaderSchemaCheck.check(VALID)),
                    () -> assertEquals(List.of(), HeaderSchemaCheck.check(valid())));
        }

        @ParameterizedTest(name = "{0} absent")
        @ValueSource(strings = {"/posture", "/inputs/realized_footprint_digest", "/inputs/change_type",
                "/inputs/providers_digest", "/inputs/templates/0/file", "/policy_calls/0/args/fix_budget/waiver_id",
                "/policy_calls/0/args/actions/file"})
        @DisplayName("an optional member may be absent")
        void optionalAbsent(String pointer) {
            assertEquals(List.of(), HeaderSchemaCheck.check(edited(pointer, Optional.empty())));
        }

        @ParameterizedTest(name = "{0} null")
        @ValueSource(strings = {"/lane", "/posture", "/inputs/change_type", "/inputs/providers_digest",
                "/inputs/templates/0/file", "/policy_calls/0/args/fix_budget/waiver_id"})
        @DisplayName("a member whose type admits null may be null")
        void nullable(String pointer) {
            assertEquals(List.of(), HeaderSchemaCheck.check(edited(pointer, Optional.of(json("null")))));
        }

        @Test
        @DisplayName("an integer may be written with a zero fraction, as JSON Schema counts it an integer")
        void integralFraction() {
            assertEquals(List.of(), HeaderSchemaCheck.check(edited("/format_version", Optional.of(json("1.0")))));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"2026-10-14t08:05:00z", "2026-12-31T23:59:59.123456789+18:00",
                "2026-01-01T00:00:00-18:00", "2024-02-29T08:05:00Z", "0001-01-01T00:00:00Z"})
        @DisplayName("a composed_at at the edge of the date and of the time with its offset is accepted")
        void dateTimeEdges(String composedAt) {
            assertEquals(List.of(), HeaderSchemaCheck.check(edited("/composed_at",
                    Optional.of(json("\"" + composedAt + "\"")))));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"remediation.ci-failure", "remediation.review-findings", "remediation.build-failure",
                "remediation.merge-conflict", "remediation.qgate-findings", "remediation.analysis"})
        @DisplayName("a policy call may name each of the six remediation policy units")
        void sixPolicyUnits(String unit) {
            assertEquals(List.of(), HeaderSchemaCheck.check(edited("/policy_calls/0/unit",
                    Optional.of(json("\"" + unit + "\"")))));
        }

        @Test
        @DisplayName("the policy units are exactly the six remediation policy units")
        void policyUnitsEnumerated() {
            assertEquals(List.of("remediation.ci-failure", "remediation.review-findings", "remediation.build-failure",
                    "remediation.merge-conflict", "remediation.qgate-findings", "remediation.analysis"),
                    HeaderSchemaCheck.POLICY_UNITS);
        }

        @Test
        @DisplayName("an argument value may be any JSON value")
        void anyArgumentValue() {
            assertEquals(List.of(), HeaderSchemaCheck.check(edited("/policy_calls/0/args/fix_budget/value",
                    Optional.of(json("{\"x\": [true, 1.5]}")))));
        }
    }

    @Nested
    @DisplayName("One violation per constraint")
    class OneViolation {

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.plan.HeaderSchemaCheckTest#requiredMembers")
        @DisplayName("a missing required member is reported at its pointer")
        void missing(String pointer) {
            assertEquals(List.of(new HeaderViolation(pointer, HeaderSchemaCheck.MISSING)),
                    HeaderSchemaCheck.check(edited(pointer, Optional.empty())));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.plan.HeaderSchemaCheckTest#objectsOfEveryLevel")
        @DisplayName("a member the schema does not declare is reported at every level")
        void undeclared(String objectPointer) {
            var pointer = objectPointer + "/extra";

            assertEquals(List.of(new HeaderViolation(pointer, HeaderSchemaCheck.NOT_ALLOWED)),
                    HeaderSchemaCheck.check(edited(pointer, Optional.of(json("true")))));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.plan.HeaderSchemaCheckTest#wrongValues")
        @DisplayName("a wrong constant, minimum, enumeration value, pattern, type or date-time is reported")
        void wrongValue(String name, String pointer, String value, String reason) {
            assertEquals(List.of(new HeaderViolation(pointer, reason)),
                    HeaderSchemaCheck.check(edited(pointer, Optional.of(json(value)))), name);
        }

        @Test
        @DisplayName("a member of the wrong type is reported once, and what lies inside it is not checked")
        void wrongTypeStopsDescent() {
            assertAll(
                    () -> assertEquals(List.of(new HeaderViolation("/inputs", HeaderSchemaCheck.NOT_OBJECT)),
                            HeaderSchemaCheck.check(edited("/inputs", Optional.of(json("\"x\""))))),
                    () -> assertEquals(List.of(new HeaderViolation("/inputs/templates/0",
                                    HeaderSchemaCheck.NOT_OBJECT)),
                            HeaderSchemaCheck.check(edited("/inputs/templates/0", Optional.of(json("\"x\""))))),
                    () -> assertEquals(List.of(new HeaderViolation("", HeaderSchemaCheck.NOT_OBJECT)),
                            HeaderSchemaCheck.check("[]")));
        }

        @Test
        @DisplayName("a member name is escaped in the pointer as RFC 6901 demands")
        void pointerEscaped() {
            var withMember = new LinkedHashMap<>(((ObjectNode) valid()).members());
            withMember.put("a/b~c", json("1"));

            assertEquals(List.of(new HeaderViolation("/a~1b~0c", HeaderSchemaCheck.NOT_ALLOWED)),
                    HeaderSchemaCheck.check(new ObjectNode(withMember)));
        }

        @Test
        @DisplayName("several broken constraints are all reported")
        void several() {
            var twice = edit(edited("/version", Optional.of(json("0"))), List.of("lane"), Optional.of(json("1")));

            assertEquals(List.of(new HeaderViolation("/version", HeaderSchemaCheck.BELOW_ONE),
                            new HeaderViolation("/lane", HeaderSchemaCheck.NOT_STRING_OR_NULL)),
                    HeaderSchemaCheck.check(twice));
        }

        @Test
        @DisplayName("a header record is checked as it would be written")
        void headerRecord() {
            var header = new PlanInstanceHeader("Plan_24", 0, CompositionEvent.START,
                    Instant.parse("2026-10-14T08:05:00Z"),
                    new CompositionInputs("c", List.of(), List.of(), "r1", DIGEST_A, DIGEST_B, Optional.empty(),
                            Optional.empty(), Optional.empty()),
                    Optional.empty(), Optional.empty(), new PlanInstanceHeader.Steps(List.of(), List.of()),
                    List.of(), List.of(), List.of());

            assertEquals(List.of(
                            new HeaderViolation("/plan_id", "must match ^[a-z0-9][a-z0-9-]{1,62}[a-z0-9]$"),
                            new HeaderViolation("/version", HeaderSchemaCheck.BELOW_ONE)),
                    HeaderSchemaCheck.check(header));
        }
    }

    @Nested
    @DisplayName("Malformed text")
    class MalformedText {

        @Test
        @DisplayName("a member named twice is one violation at that member, never an exception")
        void duplicateMember() {
            assertEquals(List.of(new HeaderViolation("/inputs/config_hash", "member named twice")),
                    HeaderSchemaCheck.check("{\"inputs\": {\"config_hash\": \"a\", \"config_hash\": \"b\"}}"));
        }

        @Test
        @DisplayName("an empty text, content after the value, and invalid JSON are one violation of the whole")
        void notOneValue() {
            var invalid = HeaderSchemaCheck.check("not json");

            assertAll(
                    () -> assertEquals(List.of(new HeaderViolation("", "the header is empty")),
                            HeaderSchemaCheck.check("")),
                    () -> assertEquals(List.of(new HeaderViolation("", "content after the header value")),
                            HeaderSchemaCheck.check("{} []")),
                    () -> assertEquals(1, invalid.size()),
                    () -> assertEquals("", invalid.getFirst().pointer()),
                    () -> assertTrue(invalid.getFirst().reason().startsWith("the header is not valid JSON"),
                            invalid.getFirst().reason()));
        }
    }

    @Nested
    @DisplayName("Numbers written with an exponent")
    class ExponentNumbers {

        private static final String HUGE = "1000e2147483647";

        @Test
        @DisplayName("a format_version whose exponent overflows the integral strip is one violation, never an exception")
        void hugeFormatVersionReported() {
            var json = VALID.replace("\"format_version\": 1", "\"format_version\": " + HUGE);

            var violations = assertDoesNotThrow(() -> HeaderSchemaCheck.check(json));

            assertEquals(List.of(new HeaderViolation("/format_version", HeaderSchemaCheck.NOT_FORMAT_VERSION)),
                    violations);
        }

        @Test
        @DisplayName("a version whose exponent overflows the integral strip is checked as the integer it is, never "
                + "an exception")
        void hugeVersionChecked() {
            var json = VALID.replace("\"version\": 3", "\"version\": " + HUGE);

            var violations = assertDoesNotThrow(() -> HeaderSchemaCheck.check(json));

            assertEquals(List.of(), violations);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"100", "1.0E+2", "1.00e2", "100.000", "1e2"})
        @DisplayName("an integral version with trailing zeros or an exponent still satisfies the schema")
        void integralVersionAccepted(String version) {
            var json = VALID.replace("\"version\": 3", "\"version\": " + version);

            assertEquals(List.of(), HeaderSchemaCheck.check(json));
        }
    }

    static Stream<String> requiredMembers() {
        return Stream.of("/format_version", "/plan_id", "/version", "/event", "/composed_at", "/inputs", "/lane",
                "/steps", "/pruned", "/policy_calls", "/waived_gates",
                "/inputs/config_hash", "/inputs/templates", "/inputs/sub_units", "/inputs/composition_rules",
                "/inputs/plan_facts_digest",
                "/inputs/composition_rules/revision", "/inputs/composition_rules/digest",
                "/inputs/templates/0/unit", "/inputs/templates/0/digest", "/inputs/templates/0/origin",
                "/inputs/sub_units/0/unit", "/inputs/sub_units/0/digest", "/inputs/sub_units/0/origin",
                "/steps/phase_5_verification", "/steps/phase_6",
                "/pruned/0/element", "/pruned/0/id", "/pruned/0/reason", "/pruned/0/source_fact",
                "/policy_calls/0/call_site", "/policy_calls/0/unit", "/policy_calls/0/args",
                "/policy_calls/0/args/fix_budget/value", "/policy_calls/0/args/fix_budget/source",
                "/waived_gates/0/gate", "/waived_gates/0/waiver_id");
    }

    static Stream<String> objectsOfEveryLevel() {
        return Stream.of("", "/inputs", "/inputs/composition_rules", "/inputs/templates/0", "/inputs/sub_units/0",
                "/steps", "/pruned/0", "/policy_calls/0", "/policy_calls/0/args/fix_budget", "/waived_gates/0");
    }

    static Stream<Arguments> wrongValues() {
        var digestPattern = "must match ^[0-9a-f]{64}$";
        var waiverPattern = "must match ^W-\\d+$";
        return Stream.of(
                Arguments.of("format_version constant", "/format_version", "2", HeaderSchemaCheck.NOT_FORMAT_VERSION),
                Arguments.of("format_version type", "/format_version", "\"1\"", HeaderSchemaCheck.NOT_INTEGER),
                Arguments.of("version minimum", "/version", "0", HeaderSchemaCheck.BELOW_ONE),
                Arguments.of("version fraction", "/version", "1.5", HeaderSchemaCheck.NOT_INTEGER),
                Arguments.of("plan_id pattern", "/plan_id", "\"Plan_24\"",
                        "must match ^[a-z0-9][a-z0-9-]{1,62}[a-z0-9]$"),
                Arguments.of("plan_id type", "/plan_id", "24", HeaderSchemaCheck.NOT_STRING),
                Arguments.of("event enumeration", "/event", "\"begin\"",
                        "must be one of [start, outline, plan, config_adopted, waiver, fallback, empty_harvest,"
                                + " replan]"),
                Arguments.of("composed_at date-time", "/composed_at", "\"yesterday\"",
                        HeaderSchemaCheck.NOT_DATE_TIME),
                Arguments.of("composed_at without seconds", "/composed_at", "\"2026-10-14T08:05Z\"",
                        HeaderSchemaCheck.NOT_DATE_TIME),
                Arguments.of("composed_at day the month lacks", "/composed_at", "\"2026-02-30T08:05:00Z\"",
                        HeaderSchemaCheck.NOT_DATE_TIME),
                Arguments.of("composed_at leap second", "/composed_at", "\"2026-10-14T08:05:60Z\"",
                        HeaderSchemaCheck.NOT_DATE_TIME),
                Arguments.of("composed_at offset beyond the supported range", "/composed_at",
                        "\"2026-10-14T08:05:00+23:59\"", HeaderSchemaCheck.NOT_DATE_TIME),
                Arguments.of("composed_at date only", "/composed_at", "\"2026-10-14\"",
                        HeaderSchemaCheck.NOT_DATE_TIME),
                Arguments.of("composed_at month 13", "/composed_at", "\"2026-13-01T08:05:00Z\"",
                        HeaderSchemaCheck.NOT_DATE_TIME),
                Arguments.of("composed_at day 32", "/composed_at", "\"2026-10-32T08:05:00Z\"",
                        HeaderSchemaCheck.NOT_DATE_TIME),
                Arguments.of("composed_at space as separator", "/composed_at", "\"2026-10-14 08:05:00Z\"",
                        HeaderSchemaCheck.NOT_DATE_TIME),
                Arguments.of("composed_at hour 24", "/composed_at", "\"2026-10-14T24:00:00Z\"",
                        HeaderSchemaCheck.NOT_DATE_TIME),
                Arguments.of("composed_at without offset", "/composed_at", "\"2026-10-14T08:05:00\"",
                        HeaderSchemaCheck.NOT_DATE_TIME),
                Arguments.of("composed_at offset without colon", "/composed_at", "\"2026-10-14T08:05:00+0100\"",
                        HeaderSchemaCheck.NOT_DATE_TIME),
                Arguments.of("composed_at fraction without digits", "/composed_at", "\"2026-10-14T08:05:00.Z\"",
                        HeaderSchemaCheck.NOT_DATE_TIME),
                Arguments.of("lane enumeration", "/lane", "\"fast\"",
                        "must be one of [light, deep, land, adopt] or null"),
                Arguments.of("posture enumeration", "/posture", "\"max\"",
                        "must be one of [minimal, standard, full] or null"),
                Arguments.of("inputs type", "/inputs", "[]", HeaderSchemaCheck.NOT_OBJECT),
                Arguments.of("config_hash type", "/inputs/config_hash", "1", HeaderSchemaCheck.NOT_STRING),
                Arguments.of("templates type", "/inputs/templates", "{}", HeaderSchemaCheck.NOT_ARRAY),
                Arguments.of("template digest pattern", "/inputs/templates/0/digest", "\"ABC\"", digestPattern),
                Arguments.of("template file type", "/inputs/templates/0/file", "1",
                        HeaderSchemaCheck.NOT_STRING_OR_NULL),
                Arguments.of("sub-unit origin enumeration", "/inputs/sub_units/0/origin", "\"vendor\"",
                        "must be one of [default, project]"),
                Arguments.of("rules digest pattern", "/inputs/composition_rules/digest", "\"" + "A".repeat(64) + "\"",
                        digestPattern),
                Arguments.of("plan facts digest pattern", "/inputs/plan_facts_digest", "\"abc\"", digestPattern),
                Arguments.of("change_type type", "/inputs/change_type", "false",
                        HeaderSchemaCheck.NOT_STRING_OR_NULL),
                Arguments.of("phase 5 step type", "/steps/phase_5_verification/0", "7", HeaderSchemaCheck.NOT_STRING),
                Arguments.of("phase 6 step pattern", "/steps/phase_6/0", "\"finalize:push\"",
                        "must match ^(default|project):[a-z][a-z0-9-]{0,63}$"),
                Arguments.of("pruned type", "/pruned", "{}", HeaderSchemaCheck.NOT_ARRAY),
                Arguments.of("pruned element enumeration", "/pruned/0/element", "\"node\"",
                        "must be one of [step, state, link, policy_action]"),
                Arguments.of("prune reason enumeration", "/pruned/0/reason", "\"because\"",
                        "must be one of [posture, provider_unconfigured, footprint_disjoint, change_type_exempt,"
                                + " recipe_override, local_only, land_lane, adopt, auto_merge_off, empty_harvest,"
                                + " returned, handed_back]"),
                Arguments.of("policy unit enumeration", "/policy_calls/0/unit", "\"remediation.other\"",
                        POLICY_UNITS_REASON),
                Arguments.of("policy unit remediation.spend", "/policy_calls/0/unit", "\"remediation.spend\"",
                        POLICY_UNITS_REASON),
                Arguments.of("args type", "/policy_calls/0/args", "[]", HeaderSchemaCheck.NOT_OBJECT),
                Arguments.of("argument type", "/policy_calls/0/args/fix_budget", "4", HeaderSchemaCheck.NOT_OBJECT),
                Arguments.of("argument source enumeration", "/policy_calls/0/args/fix_budget/source",
                        "\"operator\"", "must be one of [template, composition_rule, waiver]"),
                Arguments.of("argument waiver id pattern", "/policy_calls/0/args/fix_budget/waiver_id", "\"X-1\"",
                        waiverPattern),
                Arguments.of("waived gate waiver id pattern", "/waived_gates/0/waiver_id", "\"W1\"", waiverPattern),
                Arguments.of("waived gate type", "/waived_gates/0/gate", "null", HeaderSchemaCheck.NOT_STRING));
    }
}
