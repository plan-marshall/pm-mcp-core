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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The members of the effective role that are not the three values (PM-WORK-1, Role Artifact Schema): the continuation bounds
 * cut to the harness's {@code headless.resumability}, the context budget taken from the artifact, then the harness, then
 * the compiled fallback, and the idle wakeups of a warm role.
 */
@EnableGeneratorController
@DisplayName("Effective role facts")
class EffectiveRoleFactsTest {

    @Test
    @DisplayName("the continuation bounds are cut to the harness's resumability")
    void questionsAreCut() {
        var hold = Generators.integers(301, 3_600).next();
        var resume = Generators.integers(1, 604_800).next();
        var artifact = RoleFixtures.valid()
                .set("/questions", map("hold_seconds", hold, "resume_within_seconds", resume)).artifact();
        var profile = profile(new HarnessProfile.Resumability(true, 300, false, null),
                Generators.integers(200_000, 800_000).next(), Generators.integers(1, 10).next());

        var role = role(artifact, profile);

        assertAll(
                () -> assertEquals(300, role.questions().holdSeconds(), "cut to max_hold_seconds"),
                () -> assertEquals(0, role.questions().resumeWithinSeconds(), "the harness declares no resume"));
    }

    @Test
    @DisplayName("a harness that declares neither form makes the member ineffective")
    void questionsAreIneffective() {
        var hold = Generators.integers(1, 3_600).next();
        var resume = Generators.integers(1, 604_800).next();
        var artifact = RoleFixtures.valid()
                .set("/questions", map("hold_seconds", hold, "resume_within_seconds", resume)).artifact();
        var profile = profile(new HarnessProfile.Resumability(false, null, false, null),
                Generators.integers(200_000, 800_000).next(), Generators.integers(1, 10).next());

        var role = role(artifact, profile);

        assertAll(
                () -> assertEquals(0, role.questions().holdSeconds()),
                () -> assertEquals(0, role.questions().resumeWithinSeconds()));
    }

    @Test
    @DisplayName("an artifact bound below the harness maximum keeps its own value")
    void questionsBelowHarnessMax() {
        var hold = Generators.integers(1, 300).next();
        var resume = Generators.integers(1, 600).next();
        var artifact = RoleFixtures.valid()
                .set("/questions", map("hold_seconds", hold, "resume_within_seconds", resume)).artifact();
        var profile = profile(new HarnessProfile.Resumability(true, 300, true, 600),
                Generators.integers(200_000, 800_000).next(), Generators.integers(1, 10).next());

        var role = role(artifact, profile);

        assertAll(
                () -> assertEquals(hold, role.questions().holdSeconds(), "the artifact's hold wins"),
                () -> assertEquals(resume, role.questions().resumeWithinSeconds(), "the artifact's resume wins"));
    }

    @Test
    @DisplayName("the context budget falls back to the harness and then to the compiled value")
    void contextBudgetFallsBack() {
        var artifactBudget = Generators.integers(10_000, 500_000).next();
        var harnessBudget = Generators.integers(500_001, 1_000_000).next();
        var artifact = RoleFixtures.valid().artifact();

        assertAll(
                () -> assertEquals(artifactBudget, role(RoleFixtures.valid()
                        .set("/context_budget_tokens", artifactBudget).artifact(), profile(
                        new HarnessProfile.Resumability(false, null, false, null), harnessBudget,
                        Generators.integers(1, 10).next()))
                        .contextBudgetTokens(), "the artifact wins"),
                () -> assertEquals(harnessBudget, role(artifact, profile(
                        new HarnessProfile.Resumability(false, null, false, null), harnessBudget,
                        Generators.integers(1, 10).next()))
                        .contextBudgetTokens(), "the harness fallback applies"),
                () -> assertEquals(EffectiveRole.COMPILED_CONTEXT_BUDGET, role(artifact, profile(
                        new HarnessProfile.Resumability(false, null, false, null), null, null))
                        .contextBudgetTokens(), "the compiled fallback applies"));
    }

    @Test
    @DisplayName("the idle wakeups fall back to the host and stay null when neither sets them")
    void idleWakeupsFallBack() {
        var idleWakeups = Generators.integers(1, 10).next();
        var warm = RoleFixtures.valid().set("/form", "warm")
                .set("/recycling", map("idle_wakeups", null, "token_budget",
                        Generators.integers(10_000, 100_000).next(), "tasks_per_generation", null))
                .artifact();

        assertAll(
                () -> assertEquals(idleWakeups, role(warm, profile(
                                new HarnessProfile.Resumability(false, null, false, null),
                                Generators.integers(200_000, 800_000).next(), idleWakeups)).idleWakeups(),
                        "the host's value applies"),
                () -> assertNull(role(warm, profile(new HarnessProfile.Resumability(false, null, false, null),
                        Generators.integers(200_000, 800_000).next(), null)).idleWakeups(), "neither sets it"));
    }

    @Test
    @DisplayName("an off-track signal the artifact omits takes the compiled threshold and the action show")
    void offTrackDefaults() {
        var role = role(RoleFixtures.valid().artifact(), profile(
                new HarnessProfile.Resumability(false, null, false, null),
                Generators.integers(200_000, 800_000).next(), Generators.integers(1, 10).next()));

        assertAll(
                () -> assertEquals(RoleArtifact.OffTrackSignal.values().length, role.offTrack().size()),
                () -> assertEquals(RoleArtifact.OffTrackAction.SHOW,
                        role.offTrack().get(RoleArtifact.OffTrackSignal.NO_PROGRESS).action()),
                () -> assertEquals(RoleArtifact.OffTrackSignal.NO_PROGRESS.compiledThreshold(),
                        role.offTrack().get(RoleArtifact.OffTrackSignal.NO_PROGRESS)
                                .effectiveThreshold(RoleArtifact.OffTrackSignal.NO_PROGRESS)));
    }

    @Test
    @DisplayName("a non-finite threshold is refused")
    void nonFiniteThresholdIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new RoleArtifact.OffTrackSetting(Double.NaN, RoleArtifact.OffTrackAction.SHOW));
    }

    @Test
    @DisplayName("an off-track setting outside the signal's bounds is refused")
    void offTrackSettingIsGuarded() {
        var setting = new RoleArtifact.OffTrackSetting(9.0, RoleArtifact.OffTrackAction.SHOW);

        assertThrows(IllegalArgumentException.class,
                () -> setting.effectiveThreshold(RoleArtifact.OffTrackSignal.REPEATING));
    }

    @Test
    @DisplayName("the weights identity travels beside the digest inputs")
    void weightsIdentity() {
        var identity = Generators.letterStrings(5, 20).next();
        var role = EffectiveRole.of(RoleFixtures.valid().artifact(), RoleFixtures.HARNESS, RoleFixtures.MODEL,
                new EffortValue(null), List.of(new RoleSkill("skill://pm/role/implement/SKILL.md",
                        RoleFixtures.SKILL_DIGEST)), "1.4", Optional.of(identity), RoleFixtures.harness());

        assertEquals(Optional.of(identity), role.weightsIdentity());
    }

    private static EffectiveRole role(RoleArtifact artifact, HarnessProfile profile) {
        return EffectiveRole.of(artifact, RoleFixtures.HARNESS, RoleFixtures.MODEL, new EffortValue(null),
                List.of(new RoleSkill("skill://pm/role/implement/SKILL.md", RoleFixtures.SKILL_DIGEST)), "1.4",
                Optional.empty(), profile);
    }

    private static HarnessProfile profile(HarnessProfile.Resumability resumability, Integer contextBudget,
            Integer idleWakeups) {
        var classes = new LinkedHashMap<RoleClass, HarnessProfile.ToolSet>();
        classes.put(RoleClass.WRITER, HarnessProfile.ToolSet.ENFORCED);
        classes.put(RoleClass.READ_ONLY, HarnessProfile.ToolSet.ADVISORY);
        classes.put(RoleClass.ZERO_TOOL, HarnessProfile.ToolSet.ENFORCED);
        return new HarnessProfile(RoleFixtures.HARNESS, true, true, Set.of("*"), classes,
                Map.of(RoleFixtures.MODEL, List.of("medium", "high")),
                Map.of("frontier", "x" + Generators.letterStrings(3, 10).next(), "standard", RoleFixtures.MODEL,
                        "fast", "y" + Generators.letterStrings(3, 10).next()),
                contextBudget, idleWakeups, resumability, "ask_user");
    }

    private static Map<String, Object> map(Object... entries) {
        var map = new LinkedHashMap<String, Object>();
        for (var index = 0; index < entries.length; index += 2) {
            map.put((String) entries[index], entries[index + 1]);
        }
        return map;
    }
}
