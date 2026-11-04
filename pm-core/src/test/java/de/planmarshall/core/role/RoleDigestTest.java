/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The two digests of a role follow the graded members only (PM-WORK-1; the hazard is that a record bound to too many or too
 * few members either falls to the conservative fallback for an edit the grading never measured, or stays valid after the
 * model or a skill changed). The table below edits one member of the artifact: a change of {@code count},
 * {@code recycling}, {@code budget_floor_seconds}, {@code context_budget_tokens}, {@code questions}, {@code off_track} or
 * {@code form} changes the configuration digest and keeps the qualification digest; a change of {@code description}
 * changes neither; a change of {@code harness}, {@code model}, {@code effort}, {@code tools}, {@code class}, a skill
 * digest or the harness major.minor changes both; the weights identity changes only the qualification digest.
 */
@EnableGeneratorController
@DisplayName("Role digests")
class RoleDigestTest {

    @Test
    @DisplayName("the valid role has two hex digests")
    void digestsAreHex() {
        var role = base();

        assertEquals(64, role.configurationDigest().length());
        assertEquals(64, role.qualificationDigest().length());
    }

    static Stream<Arguments> edits() {
        var count = Generators.integers(3, 16).next();
        var budgetFloor = Generators.integers(301, 86_400).next();
        var contextBudget = Generators.integers(10_000, 500_000).next();
        var idleWakeups = Generators.integers(1, 100).next();
        var tokenBudget = Generators.integers(10_000, 100_000).next();
        var tasksPerGeneration = Generators.integers(1, 20).next();
        var hold = Generators.integers(0, 3_600).next();
        var resume = Generators.integers(0, 604_800).next();
        var threshold = Generators.integers(1, 5).next();
        var description = Generators.letterStrings(10, 60).next();
        return Stream.of(
                Arguments.of("count", RoleFixtures.valid().set("/count", count), true, false),
                Arguments.of("budget_floor_seconds", RoleFixtures.valid().set("/budget_floor_seconds", budgetFloor),
                        true, false),
                Arguments.of("context_budget_tokens", RoleFixtures.valid().set("/context_budget_tokens", contextBudget),
                        true, false),
                Arguments.of("recycling and form", RoleFixtures.valid().set("/form", "warm").set("/recycling",
                        map("idle_wakeups", idleWakeups, "token_budget", tokenBudget,
                                "tasks_per_generation", tasksPerGeneration)), true, false),
                Arguments.of("questions", RoleFixtures.valid().set("/questions",
                        map("hold_seconds", hold, "resume_within_seconds", resume)), true, false),
                Arguments.of("off_track", RoleFixtures.valid().set("/off_track", map("repeating",
                        map("threshold", threshold, "action", "nudge"))), true, false),
                Arguments.of("description", RoleFixtures.valid().set("/description", description), false, false),
                Arguments.of("tools", RoleFixtures.valid().set("/tools",
                        List.of("pm_wait", "pm_state", "pm_do", "pm_skill", "pm_skill_file", "pm_lsp")), true, true),
                Arguments.of("class", RoleFixtures.valid().set("/class", "writer"), true, true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("edits")
    @DisplayName("an edit changes exactly the digests of its members")
    void editChanges(String member, RoleFixtures.RoleFixture fixture, boolean configuration, boolean qualification) {
        var base = base();
        var edited = role(fixture.artifact(), RoleFixtures.HARNESS, RoleFixtures.MODEL, new EffortValue(null),
                skills(), "1.4", Optional.empty());

        assertAll(
                () -> assertDigest("configuration", base.configurationDigest(), edited.configurationDigest(),
                        configuration),
                () -> assertDigest("qualification", base.qualificationDigest(), edited.qualificationDigest(),
                        qualification));
    }

    static Stream<Arguments> effectiveEdits() {
        var harness = "h" + Generators.letterStrings(3, 12).next();
        var model = "n" + Generators.letterStrings(3, 12).next();
        var weightsIdentity = Generators.letterStrings(5, 20).next();
        return Stream.of(
                Arguments.of("harness", harness, RoleFixtures.MODEL, new EffortValue(null), "1.4", Optional.empty(),
                        true, true),
                Arguments.of("model", RoleFixtures.HARNESS, model, new EffortValue(null), "1.4", Optional.empty(),
                        true, true),
                Arguments.of("effort", RoleFixtures.HARNESS, RoleFixtures.MODEL, new EffortValue("medium"), "1.4",
                        Optional.empty(), true, true),
                Arguments.of("harness major.minor", RoleFixtures.HARNESS, RoleFixtures.MODEL, new EffortValue(null),
                        "2.0", Optional.empty(), true, true),
                Arguments.of("weights identity", RoleFixtures.HARNESS, RoleFixtures.MODEL, new EffortValue(null), "1.4",
                        Optional.of(weightsIdentity), false, true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("effectiveEdits")
    @DisplayName("an edit of the effective values or the weights identity changes its digests")
    void effectiveEditChanges(String member, String harness, String model, EffortValue effort, String version,
            Optional<String> weights, boolean configuration, boolean qualification) {
        var base = base();
        var edited = role(RoleFixtures.valid().artifact(), harness, model, effort, skills(), version, weights);

        assertAll(
                () -> assertDigest("configuration", base.configurationDigest(), edited.configurationDigest(),
                        configuration),
                () -> assertDigest("qualification", base.qualificationDigest(), edited.qualificationDigest(),
                        qualification));
    }

    @Test
    @DisplayName("a changed skill digest changes both digests")
    void skillDigestChangesBoth() {
        var base = base();
        var edited = role(RoleFixtures.valid().artifact(), RoleFixtures.HARNESS, RoleFixtures.MODEL,
                new EffortValue(null), List.of(new RoleSkill("skill://pm/role/implement/SKILL.md",
                        "%064x".formatted(Generators.longs().next()))),
                "1.4", Optional.empty());

        assertAll(
                () -> assertNotEquals(base.configurationDigest(), edited.configurationDigest()),
                () -> assertNotEquals(base.qualificationDigest(), edited.qualificationDigest()));
    }

    @Test
    @DisplayName("a recycling edit alone changes only the configuration digest")
    void recyclingChangesConfigurationOnly() {
        var idleWakeups = Generators.integers(1, 99).next();
        var tokenBudget = Generators.integers(10_000, 50_000).next();
        var tasksPerGeneration = Generators.integers(1, 20).next();
        var before = role(RoleFixtures.valid().set("/form", "warm").set("/recycling",
                        map("idle_wakeups", idleWakeups, "token_budget", tokenBudget,
                                "tasks_per_generation", tasksPerGeneration)).artifact(),
                RoleFixtures.HARNESS, RoleFixtures.MODEL, new EffortValue(null), skills(), "1.4", Optional.empty());
        var after = role(RoleFixtures.valid().set("/form", "warm").set("/recycling",
                        map("idle_wakeups", idleWakeups + 1, "token_budget", tokenBudget + 1_000,
                                "tasks_per_generation", tasksPerGeneration + 1)).artifact(),
                RoleFixtures.HARNESS, RoleFixtures.MODEL, new EffortValue(null), skills(), "1.4", Optional.empty());

        assertAll(
                () -> assertNotEquals(before.configurationDigest(), after.configurationDigest()),
                () -> assertEquals(before.qualificationDigest(), after.qualificationDigest()));
    }

    private static void assertDigest(String name, String before, String after, boolean changed) {
        if (changed) {
            assertNotEquals(before, after, name);
        } else {
            assertEquals(before, after, name);
        }
    }

    private static EffectiveRole base() {
        return role(RoleFixtures.valid().artifact(), RoleFixtures.HARNESS, RoleFixtures.MODEL, new EffortValue(null),
                skills(), "1.4", Optional.empty());
    }

    private static EffectiveRole role(RoleArtifact artifact, String harness, String model, EffortValue effort,
            List<RoleSkill> skills, String version, Optional<String> weights) {
        return EffectiveRole.of(artifact, harness, model, effort, skills, version, weights, RoleFixtures.harness());
    }

    private static List<RoleSkill> skills() {
        return List.of(new RoleSkill("skill://pm/role/implement/SKILL.md", RoleFixtures.SKILL_DIGEST));
    }

    private static Map<String, Object> map(Object... entries) {
        var map = new LinkedHashMap<String, Object>();
        for (var index = 0; index < entries.length; index += 2) {
            map.put((String) entries[index], entries[index + 1]);
        }
        return map;
    }
}
