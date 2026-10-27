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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.HarnessUsage;
import de.planmarshall.core.store.UsagePointers;

/**
 * The parsing fixtures of the shipped harnesses (PM-CLIENT-2, PM-CLIENT-5, Client Profiles § Tokens, Not Money):
 *
 * <p><strong>Synthetic fixtures, not recorded runs.</strong> Each shipped profile's own {@code headless.usage}
 * declaration builds a representative turn — every declared pointer carries a distinct figure — and the parsed row is
 * checked against the expected row per harness. The fixture is derived from the profile, never from the parser, so a
 * changed pointer declaration fails the row; a changed output <em>format</em> of a real harness is only caught once the
 * recorded outputs of that harness and version replace these synthetic turns, which is still owed.
 */
@DisplayName("Shipped harness usage fixtures")
class ShippedUsageFixturesTest {

    private static final int FIRST_FIGURE = 101;

    @Test
    @DisplayName("every shipped harness parses its declared usage pointers into its expected row")
    void shippedHarnessesParseTheirUsage() {
        var shipped = ShippedProfiles.read().shipped();
        assertFalse(shipped.isEmpty(), "no shipped profile on the classpath");
        var exercised = new java.util.ArrayList<String>();

        for (var entry : shipped.entrySet()) {
            var clientId = entry.getKey();
            var bytes = entry.getValue().document();
            var tree = ProfileValidator.validate(bytes, "profiles/" + clientId + ".json", ProfileRules.none());
            var profile = new LoadedProfile(clientId, ProfileSetKind.SHIPPED, tree, bytes,
                    LoadedProfile.State.VERIFIED, null);
            var usage = profile.headless("/usage").orElse(null);
            if (usage == null || declaresNoPointer(usage)) {
                continue;
            }
            exercised.add(clientId);

            var assigned = new LinkedHashMap<String, Long>();
            var notApplicable = new java.util.HashSet<String>();
            var turn = new LinkedHashMap<String, Object>();
            long next = FIRST_FIGURE;
            for (var figure : ProfileStages.USAGE_FIGURES) {
                var declaration = usage.member(figure).orElseThrow();
                if (declaration.isNull()) {
                    continue;
                }
                var pointer = declaration.text().orElseThrow();
                if (UsagePointers.NOT_APPLICABLE.equals(pointer)) {
                    notApplicable.add(figure);
                    continue;
                }
                assigned.put(figure, next);
                put(turn, pointer, next);
                next++;
            }

            var row = UsagePointers.of(usage).read(ProfileFixtures.node(turn));
            var expectedTotal = totalOrEmpty(assigned, notApplicable);
            var expectedIn = splitOrEmpty(assigned, notApplicable, "input_tokens", "cache_read", "cache_creation");
            var expectedOut = splitOrEmpty(assigned, notApplicable, "output_tokens", "reasoning");
            assertAll(clientId,
                    () -> assertEquals(assigned.getOrDefault("input_tokens", null),
                            measured(row.inputTokens()), clientId + " input"),
                    () -> assertEquals(assigned.getOrDefault("output_tokens", null),
                            measured(row.outputTokens()), clientId + " output"),
                    () -> assertEquals(assigned.getOrDefault("cache_read", null),
                            measured(row.cacheReadInputTokens()), clientId + " cache read"),
                    () -> assertEquals(assigned.getOrDefault("cache_creation", null),
                            measured(row.cacheCreationInputTokens()), clientId + " cache creation"),
                    () -> assertEquals(assigned.getOrDefault("reasoning", null),
                            measured(row.reasoningTokens()), clientId + " reasoning"),
                    () -> assertEquals(assigned.getOrDefault("tool_uses", null), measured(row.toolUses()),
                            clientId + " tool uses"),
                    () -> assertEquals(assigned.getOrDefault("cost_usd", null), cost(row), clientId + " cost"),
                    () -> assertEquals(expectedTotal, row.totalTokens(), clientId + " total"),
                    () -> assertEquals(expectedIn, row.tokensIn(), clientId + " tokens_in"),
                    () -> assertEquals(expectedOut, row.tokensOut(), clientId + " tokens_out"));
        }
        assertTrue(exercised.size() >= 3,
                "the three measured shipped harnesses (claude, opencode, antigravity) are exercised, got " + exercised);
    }

    private static boolean declaresNoPointer(JsonNode usage) {
        return ProfileStages.USAGE_FIGURES.stream().map(figure -> usage.member(figure).orElse(null))
                .allMatch(declaration -> declaration == null || declaration.isNull()
                        || UsagePointers.NOT_APPLICABLE.equals(declaration.text().orElse(null)));
    }

    private static Long measured(de.planmarshall.core.store.Measurement measurement) {
        return measurement.isMeasured() ? measurement.value() : null;
    }

    private static Long cost(HarnessUsage row) {
        return row.costUsd().map(value -> value.longValueExact()).orElse(null);
    }

    private static OptionalLong totalOrEmpty(Map<String, Long> assigned, java.util.Set<String> notApplicable) {
        long sum = 0;
        for (var figure : ProfileStages.USAGE_FIGURES) {
            if ("cost_usd".equals(figure) || "tool_uses".equals(figure)) {
                continue;
            }
            var value = assigned.get(figure);
            if (value == null) {
                if (notApplicable.contains(figure)) {
                    continue;
                }
                return OptionalLong.empty();
            }
            sum += value;
        }
        return sum < 1 ? OptionalLong.empty() : OptionalLong.of(sum);
    }

    private static OptionalLong splitOrEmpty(Map<String, Long> assigned, java.util.Set<String> notApplicable,
            String... figures) {
        long sum = 0;
        for (var figure : figures) {
            var value = assigned.get(figure);
            if (value == null) {
                if (notApplicable.contains(figure)) {
                    continue;
                }
                return OptionalLong.empty();
            }
            sum += value;
        }
        return OptionalLong.of(sum);
    }

    private static void put(Map<String, Object> root, String pointer, long value) {
        var tokens = pointer.substring(1).split("/", -1);
        Map<String, Object> current = root;
        for (var index = 0; index < tokens.length - 1; index++) {
            var token = tokens[index].replace("~1", "/").replace("~0", "~");
            @SuppressWarnings("unchecked")
            var child = (Map<String, Object>) current.computeIfAbsent(token,
                    key -> new LinkedHashMap<String, Object>());
            current = child;
        }
        current.put(tokens[tokens.length - 1].replace("~1", "/").replace("~0", "~"), value);
    }

    @Test
    @DisplayName("anticipating a figure as absent stays not applicable while an undeclared pointer stays unmeasured")
    void absentAndNotApplicableStayDistinct() {
        var bytes = ProfileFixtures.valid().bytes();
        var tree = ProfileValidator.validate(bytes, "profiles/acme-cli.json", ProfileRules.none());
        var profile = new LoadedProfile("acme-cli", ProfileSetKind.SHIPPED, tree, bytes,
                LoadedProfile.State.VERIFIED, null);
        var usage = profile.headless("/usage").orElseThrow();

        var row = UsagePointers.of(usage).read(ProfileFixtures.node(Map.of("usage",
                Map.of("input_tokens", 120, "output_tokens", 30))));

        assertAll(() -> assertTrue(row.cacheCreationInputTokens().isNotApplicable()),
                () -> assertTrue(row.cacheReadInputTokens().state()
                        == de.planmarshall.core.store.Measurement.State.UNMEASURED));
    }
}
