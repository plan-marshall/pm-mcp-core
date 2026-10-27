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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.ProfileStages.StartContext;
import de.planmarshall.core.profile.ProfileStages.StartContextInput;
import de.planmarshall.core.profile.ProfileStages.UsageCoverage;

/**
 * The stages {@code isolation}, {@code egress} and {@code usage} record the profile values they settle, and the usage
 * stage compares the parsed figures with the harness's own record field by field (PM-CLIENT-5, Profile Verification).
 */
@DisplayName("Profile verification stages")
class ProfileStagesTest {

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
                () -> assertTrue(result.exclusive()),
                () -> assertTrue(result.projectSettingsControl()),
                () -> assertTrue(result.extensionIsolation()));
    }

    @Test
    @DisplayName("the egress stage records the endpoints a worker's proxy admits")
    void egressRecorded() {
        var result = ProfileStages.egress(profile());

        assertEquals(List.of("api.acme.test:443"), result.endpoints());
    }

    @Test
    @DisplayName("the usage stage records every figure reported, not_applicable or missing")
    void usageRecorded() {
        var result = ProfileStages.usage(profile(), turn("""
                {"usage":{"input_tokens":10,"output_tokens":5}}
                """), Map.of());

        assertAll(
                () -> assertEquals(ProfileStages.UsageStatus.REPORTED, result.figures().get("input_tokens")),
                () -> assertEquals(ProfileStages.UsageStatus.REPORTED, result.figures().get("output_tokens")),
                () -> assertEquals(ProfileStages.UsageStatus.MISSING, result.figures().get("cache_read")),
                () -> assertEquals(ProfileStages.UsageStatus.NOT_APPLICABLE,
                        result.figures().get("cache_creation")),
                () -> assertEquals(ProfileStages.UsageStatus.MISSING, result.figures().get("reasoning")),
                () -> assertEquals(Map.of("input_tokens", new BigDecimal("10"), "output_tokens",
                        new BigDecimal("5")), result.parsed()));
    }

    @Test
    @DisplayName("the usage stage compares the parsed figures with the harness's own record")
    void usageComparedFieldByField() {
        var result = ProfileStages.usage(profile(), turn("""
                {"usage":{"input_tokens":10,"output_tokens":5}}
                """), Map.of("input_tokens", new BigDecimal("10"), "output_tokens",
                new BigDecimal("5")));

        assertEquals(Map.of("input_tokens", new BigDecimal("10"), "output_tokens",
                new BigDecimal("5")), result.parsed());
    }

    @Test
    @DisplayName("a figure that differs from the harness's own record fails the stage")
    void differingFigureFails() {
        var loaded = profile();
        var output = turn("""
                {"usage":{"input_tokens":10,"output_tokens":5}}
                """);
        var harnessRecord = Map.of("input_tokens", new BigDecimal("11"));

        var refusal = assertThrows(ProfileRefusal.class, () -> ProfileStages.usage(loaded, output, harnessRecord));

        assertAll(
                () -> assertEquals("/usage/input_tokens", refusal.pointer()),
                () -> assertEquals(ProfileRefusal.Outcome.PROFILE_INVALID, refusal.outcome()));
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
                () -> assertEquals(ProfileStages.UsageStatus.REPORTED, result.figures().get("cost_usd")),
                () -> assertEquals(new BigDecimal("0.25"), result.parsed().get("cost_usd")));
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
        var refusal = assertThrows(ProfileRefusal.class, () -> ProfileStages.usage(loaded, output, Map.of()));

        assertEquals("/usage/absent", refusal.pointer());
    }

    @Test
    @DisplayName("the usage result is immutable")
    void usageResultIsImmutable() {
        var result = ProfileStages.usage(profile(), turn("""
                {"usage":{"input_tokens":10,"output_tokens":5}}
                """), Map.of());
        var figures = new LinkedHashMap<>(result.figures());

        var view = result.figures();
        assertThrows(UnsupportedOperationException.class, view::clear);
        assertEquals(figures, result.figures());
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

        assertAll(() -> assertEquals(UsageCoverage.PARTIAL, result.coverage()),
                () -> assertEquals(List.of("cache_read", "reasoning", "tool_uses"), result.missingFigures()));
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

        assertAll(() -> assertEquals(UsageCoverage.COMPLETE, result.coverage()),
                () -> assertTrue(result.missingFigures().isEmpty()));
    }

    @Test
    @DisplayName("the usage coverage is not_reported when no report figure is reported")
    void coverageNotReported() {
        var loaded = profile(ProfileFixtures.valid().set("/headless/usage/input_tokens", null)
                .set("/headless/usage/output_tokens", null)
                .set("/headless/usage/cache_creation", null));
        var result = ProfileStages.usage(loaded, turn("{\"usage\":{}}"), Map.of());

        assertEquals(UsageCoverage.NOT_REPORTED, result.coverage());
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
                List.of(new StartContextInput("writer", "model-a", firstTurn),
                        new StartContextInput("read_only", "model-b", withoutCacheRead)));

        assertAll(() -> assertEquals(List.of(new StartContext("writer", "model-a", OptionalLong.of(115)),
                new StartContext("read_only", "model-b", OptionalLong.empty())), result.startContext()));
    }

    @Test
    @DisplayName("a profile that declares input, output or tool uses not applicable is refused by the usage validation")
    void notApplicableRefusedForInputOutputAndToolUses() {
        var loaded = profile(ProfileFixtures.valid().set("/headless/usage/input_tokens", "not_applicable"));
        var output = turn("{\"usage\":{}}");

        var refusal = assertThrows(ProfileRefusal.class, () -> ProfileStages.usage(loaded, output, Map.of()));

        assertAll(() -> assertEquals("/headless/usage/input_tokens", refusal.pointer()),
                () -> assertEquals(ProfileRefusal.Outcome.PROFILE_INVALID, refusal.outcome()));
    }
}
