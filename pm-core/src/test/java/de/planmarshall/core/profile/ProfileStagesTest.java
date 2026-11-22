/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.profile;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.ProfileStages.StartContext;
import de.planmarshall.core.profile.ProfileStages.StartContextInput;
import de.planmarshall.core.profile.ProfileStages.UsageCoverage;

/**
 * The stages {@code isolation}, {@code egress} and {@code usage} record the profile values they settle, and the usage
 * stage compares the parsed figures with the harness's own record field by field (PM-CLIENT-5, Profile Verification).
 * The stages of an endpoint are held here too: every endpoint runs all four, {@code classes} included.
 */
@EnableGeneratorController
@DisplayName("Profile verification stages")
class ProfileStagesTest {

    private static final String ENDPOINT_MODEL = Generators.letterStrings(5, 20).next();
    private static final String WRITER_MODEL = Generators.letterStrings(3, 10).next();
    private static final String READ_ONLY_MODEL = WRITER_MODEL + "-b";

    @Test
    @DisplayName("an endpoint verification runs all four stages and refuses a null classes stage")
    void endpointVerificationRefusesNullClasses() {
        var identity = new ProfileStages.EndpointIdentity(true, null, null, List.of(ENDPOINT_MODEL), Map.of());
        var launch = new ProfileStages.EndpointLaunch(true, true, "enforced", true,
                Map.of("two_options_ascending", "valid", "one_option", "valid"));
        var limits = new ProfileStages.EndpointLimits(false, 0);

        assertAll(
                // The stage names are the wire names of the verification record.
                () -> assertEquals(ProfileStages.ENDPOINT_STAGES, List.of("identity", "launch", "classes", "limits"),
                        "An endpoint runs all four stages"),
                () -> {
                    var refusal = assertThrows(NullPointerException.class,
                            () -> new ProfileStages.EndpointVerification(identity, launch, null, limits),
                            "A null classes stage is refused");
                    assertEquals("classes", refusal.getMessage(), "the refusal names the classes stage");
                });
    }

    private static LoadedProfile profile() {
        var bytes = ProfileFixtures.validBytes();
        var tree = ProfileValidator.validate(bytes, "profiles/acme-cli.json", ProfileRules.none());
        return new LoadedProfile("acme-cli", ProfileSetKind.SHIPPED, tree, bytes, LoadedProfile.State.VERIFIED, null);
    }

    private static JsonNode turn(String json) {
        return JsonDocument.parse(json.getBytes(UTF_8), "turn");
    }

    @Test
    @DisplayName("the isolation stage records exclusivity and the two capabilities it measured")
    void isolationRecorded() {
        var result = ProfileStages.isolation(profile(), true, true);

        assertAll(
                () -> assertTrue(result.exclusive(), "isolation records exclusive access"),
                () -> assertTrue(result.projectSettingsControl(), "isolation records settings control"),
                () -> assertTrue(result.extensionIsolation(), "isolation records extension boundary"));
    }

    @Test
    @DisplayName("the egress stage records the endpoints a worker's proxy admits")
    void egressRecorded() {
        var result = ProfileStages.egress(profile());

        assertEquals(List.of("api.acme.test:443"), result.endpoints(), "egress admits the proxy endpoint");
    }

    @Test
    @DisplayName("the resumability stage records a measured hold and leaves the unmeasured resume false")
    void resumabilityRecorded() {
        var result = new ProfileStages.ResumabilityResult(new ProfileStages.FormMeasurement(true, 3600),
                ProfileStages.FormMeasurement.notMeasured());

        assertAll(
                () -> assertTrue(result.holdVerified(), "measured hold is verified"),
                () -> assertEquals(3600, result.maxHoldSeconds(), "hold limit is 3600 seconds"),
                () -> assertFalse(result.resumeVerified(), "unmeasured resume is not verified"),
                () -> assertNull(result.maxResumeAgeSeconds(), "no resume age without a measurement"),
                () -> assertEquals(List.of(
                                new ProfileStages.ValueEntry("/headless/resumability/hold", "measured"),
                                new ProfileStages.ValueEntry("/headless/resumability/resume", "not_measured")),
                        result.values(), "both resumability values are recorded"));
    }

    @Test
    @DisplayName("a measured limit outside the profile schema's range is refused")
    void resumabilityRanges() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> resultWithHold(3_601),
                        "hold above 3600 seconds is refused"),
                () -> assertThrows(IllegalArgumentException.class, () -> resultWithResume(604_801),
                        "resume above 604800 seconds refused"));
    }

    private static void resultWithHold(int seconds) {
        new ProfileStages.ResumabilityResult(new ProfileStages.FormMeasurement(true, seconds),
                ProfileStages.FormMeasurement.notMeasured());
    }

    private static void resultWithResume(int seconds) {
        new ProfileStages.ResumabilityResult(ProfileStages.FormMeasurement.notMeasured(),
                new ProfileStages.FormMeasurement(true, seconds));
    }

    @Test
    @DisplayName("a measured form without the limit it proved, and an unmeasured one with a limit, are refused")
    void resumabilityRefusals() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProfileStages.FormMeasurement(true, null),
                        "measured form without a limit refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProfileStages.FormMeasurement(false, 3600),
                        "unmeasured form with a limit refused"));
    }

    @Test
    @DisplayName("the usage stage records every figure reported, not_applicable or missing")
    void usageRecorded() {
        var result = ProfileStages.usage(profile(), turn("""
                {"usage":{"input_tokens":10,"output_tokens":5}}
                """), Map.of());

        assertAll(
                () -> assertEquals(ProfileStages.UsageStatus.REPORTED, result.figures().get("input_tokens"),
                        "input tokens are reported"),
                () -> assertEquals(ProfileStages.UsageStatus.REPORTED, result.figures().get("output_tokens"),
                        "output tokens are reported"),
                () -> assertEquals(ProfileStages.UsageStatus.MISSING, result.figures().get("cache_read"),
                        "cache read is missing"),
                () -> assertEquals(ProfileStages.UsageStatus.NOT_APPLICABLE,
                        result.figures().get("cache_creation"), "cache creation is not applicable"),
                () -> assertEquals(ProfileStages.UsageStatus.MISSING, result.figures().get("reasoning"),
                        "reasoning is missing"),
                () -> assertEquals(Map.of("input_tokens", new BigDecimal("10"), "output_tokens",
                        new BigDecimal("5")), result.parsed(), "parsed input and output figures"));
    }

    @Test
    @DisplayName("the usage stage compares the parsed figures with the harness's own record")
    void usageComparedFieldByField() {
        var result = ProfileStages.usage(profile(), turn("""
                {"usage":{"input_tokens":10,"output_tokens":5}}
                """), Map.of("input_tokens", new BigDecimal("10"), "output_tokens",
                new BigDecimal("5")));

        assertEquals(Map.of("input_tokens", new BigDecimal("10"), "output_tokens",
                new BigDecimal("5")), result.parsed(), "parsed figures echo the report");
    }

    @Test
    @DisplayName("a figure that differs from the harness's own record fails the stage")
    void differingFigureFails() {
        var loaded = profile();
        var output = turn("""
                {"usage":{"input_tokens":10,"output_tokens":5}}
                """);
        var harnessRecord = Map.of("input_tokens", new BigDecimal("11"));

        var refusal = assertThrows(ProfileRefusal.class, () -> ProfileStages.usage(loaded, output, harnessRecord),
                "differing figure is refused");

        assertAll(
                () -> assertEquals("/usage/input_tokens", refusal.pointer(), "refusal points at input_tokens"),
                () -> assertEquals(ProfileRefusal.Outcome.PROFILE_INVALID, refusal.outcome(),
                        "differing figure is profile invalid"));
    }

    @Test
    @DisplayName("a reported cost is recorded as the decimal the harness reports")
    void costRecordedAsDecimal() {
        var bytes = ProfileFixtures.valid().set("/headless/usage/cost_usd", "/cost_usd").bytes();
        var tree = ProfileValidator.validate(bytes, "profiles/acme-cli.json", ProfileRules.none());
        var loaded = new LoadedProfile("acme-cli", ProfileSetKind.SHIPPED, tree, bytes,
                LoadedProfile.State.VERIFIED, null);

        var result = ProfileStages.usage(loaded, turn("""
                {"usage":{"input_tokens":10,"output_tokens":5},"cost_usd":0.25}
                """), Map.of("cost_usd", new BigDecimal("0.25")));

        assertAll(
                () -> assertEquals(ProfileStages.UsageStatus.REPORTED, result.figures().get("cost_usd"),
                        "cost figure is reported"),
                () -> assertEquals(new BigDecimal("0.25"), result.parsed().get("cost_usd"),
                        "cost is parsed as a decimal"));
    }

    @Test
    @DisplayName("a declared pointer that does not resolve fails the stage")
    void unresolvedPointerFails() {
        var bytes = ProfileFixtures.valid().set("/headless/usage/input_tokens", "/usage/absent").bytes();
        var tree = ProfileValidator.validate(bytes, "profiles/acme-cli.json", ProfileRules.none());
        var loaded = new LoadedProfile("acme-cli", ProfileSetKind.SHIPPED, tree, bytes,
                LoadedProfile.State.VERIFIED, null);

        var output = turn("""
                {"usage":{"input_tokens":10,"output_tokens":5}}
                """);
        var refusal = assertThrows(ProfileRefusal.class, () -> ProfileStages.usage(loaded, output, Map.of()),
                "unresolved pointer is refused");

        assertEquals("/usage/absent", refusal.pointer(), "refusal points at absent path");
    }

    @Test
    @DisplayName("the usage result is immutable")
    void usageResultIsImmutable() {
        var result = ProfileStages.usage(profile(), turn("""
                {"usage":{"input_tokens":10,"output_tokens":5}}
                """), Map.of());
        var figures = new LinkedHashMap<>(result.figures());

        var view = result.figures();
        assertThrows(UnsupportedOperationException.class, view::clear, "figures view cannot be cleared");
        assertEquals(figures, result.figures(), "figures map is unchanged");
    }

    private static LoadedProfile profile(ProfileFixtures.ProfileFixture fixture) {
        var bytes = fixture.bytes();
        var tree = ProfileValidator.validate(bytes, "profiles/acme-cli.json", ProfileRules.none());
        return new LoadedProfile("acme-cli", ProfileSetKind.SHIPPED, tree, bytes, LoadedProfile.State.VERIFIED,
                null);
    }

    @Test
    @DisplayName("the usage coverage is partial with the missing figures when a report figure is undeclared")
    void coveragePartialNamesMissingFigures() {
        var result = ProfileStages.usage(profile(), turn("""
                {"usage":{"input_tokens":10,"output_tokens":5}}
                """), Map.of());

        assertAll(() -> assertEquals(UsageCoverage.PARTIAL, result.coverage(), "usage coverage is partial"),
                () -> assertEquals(List.of("cache_read", "reasoning", "tool_uses"), result.missingFigures(),
                        "missing figures are named"));
    }

    @Test
    @DisplayName("the usage coverage is complete when every report figure is reported or not applicable")
    void coverageComplete() {
        var loaded = profile(ProfileFixtures.valid().set("/headless/usage/cache_read", "/usage/cache_read")
                .set("/headless/usage/reasoning", "/usage/reasoning")
                .set("/headless/usage/tool_uses", "/usage/tool_uses"));
        var result = ProfileStages.usage(loaded, turn("""
                {"usage":{"input_tokens":10,"output_tokens":5,"cache_read":3,"reasoning":1,"tool_uses":2}}
                """), Map.of());

        assertAll(() -> assertEquals(UsageCoverage.COMPLETE, result.coverage(), "usage coverage is complete"),
                () -> assertTrue(result.missingFigures().isEmpty(), "no figures are missing"));
    }

    @Test
    @DisplayName("the usage coverage is not_reported when no report figure is reported")
    void coverageNotReported() {
        var loaded = profile(ProfileFixtures.valid().set("/headless/usage/input_tokens", null)
                .set("/headless/usage/output_tokens", null)
                .set("/headless/usage/cache_creation", null));
        var result = ProfileStages.usage(loaded, turn("{\"usage\":{}}"), Map.of());

        assertEquals(UsageCoverage.NOT_REPORTED, result.coverage(), "usage coverage is not reported");
    }

    @Test
    @DisplayName("the start context is the first turn's input plus cache read plus cache creation")
    void startContextSumsTheFirstTurnInput() {
        var loaded = profile(ProfileFixtures.valid().set("/headless/usage/cache_read", "/usage/cache_read")
                .set("/headless/usage/cache_creation", "/usage/cache_creation"));
        var firstTurn = turn("""
                {"usage":{"input_tokens":10,"output_tokens":5,"cache_read":100,"cache_creation":5}}
                """);
        var withoutCacheRead = turn("""
                {"usage":{"input_tokens":10,"output_tokens":5}}
                """);

        var result = ProfileStages.usage(loaded, firstTurn, Map.of(),
                List.of(new StartContextInput("writer", WRITER_MODEL, firstTurn),
                        new StartContextInput("read_only", READ_ONLY_MODEL, withoutCacheRead)));

        assertAll(() -> assertEquals(List.of(new StartContext("writer", WRITER_MODEL, 115L),
                        new StartContext("read_only", READ_ONLY_MODEL, null)), result.startContext(),
                "start context sums the first turn"));
    }

    @Test
    @DisplayName("a not-applicable cache figure counts 0 and a negative figure leaves the start context unmeasured")
    void startContextEdgeFigures() {
        var loaded = profile(ProfileFixtures.valid().set("/headless/usage/cache_read", "not_applicable"));
        var firstTurn = turn("""
                {"usage":{"input_tokens":10,"output_tokens":5}}
                """);
        var negative = turn("""
                {"usage":{"input_tokens":-1,"output_tokens":5}}
                """);

        var result = ProfileStages.usage(loaded, firstTurn, Map.of(),
                List.of(new StartContextInput("writer", WRITER_MODEL, firstTurn),
                        new StartContextInput("read_only", READ_ONLY_MODEL, negative)));

        assertEquals(List.of(new StartContext("writer", WRITER_MODEL, 10L),
                        new StartContext("read_only", READ_ONLY_MODEL, null)), result.startContext(),
                "negative figure leaves context unmeasured");
    }

    @Test
    @DisplayName("a profile that declares input, output or tool uses not applicable is refused by the usage validation")
    void notApplicableRefusedForInputOutputAndToolUses() {
        var loaded = profile(ProfileFixtures.valid().set("/headless/usage/input_tokens", "not_applicable"));
        var output = turn("{\"usage\":{}}");

        var refusal = assertThrows(ProfileRefusal.class, () -> ProfileStages.usage(loaded, output, Map.of()),
                "not applicable input is refused");

        assertAll(
                () -> assertEquals("/headless/usage/input_tokens", refusal.pointer(),
                        "refusal points at input_tokens"),
                () -> assertEquals(ProfileRefusal.Outcome.PROFILE_INVALID, refusal.outcome(),
                        "invalid profile outcome is reported"));
    }
}
