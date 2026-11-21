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
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The role artifact validator refuses every document that breaks the closed schema of
 * {@code model-work/01-roles-and-configuration.adoc#role-artifact} as a whole, with {@code schema_invalid}: an unknown
 * member, a missing member, {@code recycling} missing for {@code warm} or present for {@code fresh}, an {@code off_track}
 * threshold above the compiled one, {@code questions} or {@code off_track} on a {@code zero_tool} role, and a name that
 * is a reserved keyword of the workflow language.
 */
@EnableGeneratorController
@DisplayName("Role artifact validation")
class RoleArtifactValidatorTest {

    @Test
    @DisplayName("the valid fixture loads with its members")
    void validArtifactLoads() {
        var artifact = RoleFixtures.valid().artifact();

        assertAll(
                () -> assertEquals(1, artifact.formatVersion(), "fixture declares format version one"),
                () -> assertEquals(RoleFixtures.ROLE, artifact.name(), "artifact name is the fixture role"),
                () -> assertEquals(RoleClass.READ_ONLY, artifact.roleClass(), "fixture role is read_only"),
                () -> assertEquals(RoleForm.FRESH, artifact.form(), "fixture form is fresh"),
                () -> assertEquals(2, artifact.count(), "fixture count is two"),
                () -> assertEquals(List.of("pm_wait", "pm_state", "pm_do", "pm_skill", "pm_skill_file"),
                        artifact.tools(), "tool set is the documented five"),
                () -> assertEquals(List.of("skill://pm/role/implement/SKILL.md"), artifact.skills(),
                        "single skill URI is declared"),
                () -> assertEquals(300, artifact.budgetFloorSeconds(), "budget floor is five minutes"),
                () -> assertNull(artifact.contextBudgetTokens(), "no context budget is declared"),
                () -> assertNull(artifact.questions(), "no questions member is declared"),
                () -> assertTrue(artifact.offTrack().isEmpty(), "off_track map is empty"),
                () -> assertNull(artifact.recycling(), "fresh artifact has no recycling"));
    }

    @Test
    @DisplayName("a warm artifact declares its recycling")
    void warmRecyclingLoads() {
        var idleWakeups = Generators.integers(1, 100).next();
        var tokenBudget = Generators.integers(10_000, 100_000).next();
        var tasksPerGeneration = Generators.integers(1, 20).next();
        var artifact = RoleFixtures.valid()
                .set("/form", "warm")
                .set("/recycling", recycling(idleWakeups, tokenBudget, tasksPerGeneration))
                .artifact();

        assertAll(
                () -> assertEquals(RoleForm.WARM, artifact.form(), "warm form is projected"),
                () -> assertEquals(idleWakeups, artifact.recycling().idleWakeups(),
                        "recycling idle wakeups round-trip"),
                () -> assertEquals(tokenBudget, artifact.recycling().tokenBudget(),
                        "recycling token budget round-trips"),
                () -> assertEquals(tasksPerGeneration, artifact.recycling().tasksPerGeneration(),
                        "tasks per generation round-trips"));
    }

    @Test
    @DisplayName("questions, context budget and off_track load where the schema allows them")
    void optionalMembersLoad() {
        var contextBudget = Generators.integers(10_000, 500_000).next();
        var hold = Generators.integers(0, 3_600).next();
        var resume = Generators.integers(0, 604_800).next();
        var threshold = Generators.integers(1, 5).next();
        var artifact = RoleFixtures.valid()
                .set("/context_budget_tokens", contextBudget)
                .set("/questions", map("hold_seconds", hold, "resume_within_seconds", resume))
                .set("/off_track", map("repeating", map("threshold", threshold, "action", "nudge"),
                        "no_progress", map("action", "yield")))
                .artifact();

        assertAll(
                () -> assertEquals(contextBudget, artifact.contextBudgetTokens(), "artifact context budget is kept"),
                () -> assertEquals(hold, artifact.questions().holdSeconds(), "questions hold seconds round-trip"),
                () -> assertEquals(resume, artifact.questions().resumeWithinSeconds(), "resume bound round-trips"),
                () -> assertEquals((double) threshold,
                        artifact.offTrack().get(RoleArtifact.OffTrackSignal.REPEATING).threshold(),
                        "repeating threshold is read back"),
                () -> assertEquals(RoleArtifact.OffTrackAction.NUDGE,
                        artifact.offTrack().get(RoleArtifact.OffTrackSignal.REPEATING).action(),
                        "repeating action is nudge"),
                () -> assertNull(artifact.offTrack().get(RoleArtifact.OffTrackSignal.NO_PROGRESS).threshold(),
                        "no_progress has no threshold"),
                () -> assertEquals(RoleArtifact.OffTrackAction.YIELD,
                        artifact.offTrack().get(RoleArtifact.OffTrackSignal.NO_PROGRESS).action(),
                        "no_progress action is yield"));
    }

    @Nested
    @DisplayName("refuses as a whole")
    class Refusals {

        static Stream<Arguments> refusals() {
            return Stream.of(
                    Arguments.of(RoleFixtures.valid().set("/bogus", 1), "an undeclared member"),
                    Arguments.of(RoleFixtures.valid().remove("/model"), "a missing member"),
                    Arguments.of(RoleFixtures.valid().set("/format_version", 2), "another format version"),
                    Arguments.of(RoleFixtures.valid().set("/name", "Implement"), "a name outside the grammar"),
                    Arguments.of(RoleFixtures.valid().set("/description", ""), "an empty description"),
                    Arguments.of(RoleFixtures.valid().set("/description", "x".repeat(241)), "an over-long description"),
                    Arguments.of(RoleFixtures.valid().set("/class", "worker"), "an undeclared class"),
                    Arguments.of(RoleFixtures.valid().set("/tools/0", "pm state"), "a tool name outside the grammar"),
                    Arguments.of(RoleFixtures.valid().set("/tools", List.of("pm_state", "pm_state")),
                            "a tool named twice"),
                    Arguments.of(RoleFixtures.valid().set("/harness", "Acme-CLI"), "a harness outside the grammar"),
                    Arguments.of(RoleFixtures.valid().set("/model", ""), "an empty model"),
                    Arguments.of(RoleFixtures.valid().set("/effort", 3), "an effort that is neither string nor null"),
                    Arguments.of(RoleFixtures.valid().set("/form", "cold"), "an undeclared form"),
                    Arguments.of(RoleFixtures.valid().set("/count", 0), "a count below one"),
                    Arguments.of(RoleFixtures.valid().set("/count", 17), "a count above sixteen"),
                    Arguments.of(RoleFixtures.valid().set("/skills", new ArrayList<>()), "no skills"),
                    Arguments.of(RoleFixtures.valid().set("/skills/0", "role/implement"), "a skill URI outside the grammar"),
                    Arguments.of(RoleFixtures.valid().set("/skills", List.of("skill://pm/role/implement/SKILL.md",
                            "skill://pm/role/implement/SKILL.md")), "a skill named twice"),
                    Arguments.of(RoleFixtures.valid().set("/budget_floor_seconds", 59), "a budget floor below a minute"),
                    Arguments.of(RoleFixtures.valid().set("/budget_floor_seconds", 86_401),
                            "a budget floor above a day"),
                    Arguments.of(RoleFixtures.valid().set("/context_budget_tokens", 9_999),
                            "a context budget below the minimum"),
                    Arguments.of(RoleFixtures.valid().set("/recycling", recycling(null, 10_000, null)),
                            "recycling on a fresh artifact"),
                    Arguments.of(RoleFixtures.valid().set("/form", "warm"), "a warm artifact without recycling"),
                    Arguments.of(RoleFixtures.valid().set("/form", "warm")
                                    .set("/recycling", recycling(0, 10_000, null)),
                            "an idle wakeup count below one"),
                    Arguments.of(RoleFixtures.valid().set("/form", "warm")
                                    .set("/recycling", recycling(1, 9_999, null)),
                            "a token budget below the minimum"),
                    Arguments.of(RoleFixtures.valid().set("/questions", map("hold_seconds", 3_601,
                            "resume_within_seconds", 0)), "a hold above an hour"),
                    Arguments.of(RoleFixtures.valid().set("/questions", map("hold_seconds", 0,
                            "resume_within_seconds", 604_801)), "a resume above a week"),
                    Arguments.of(RoleFixtures.valid().set("/questions", map("hold_seconds", 0)),
                            "questions without a resume bound"),
                    Arguments.of(RoleFixtures.valid().set("/off_track", map("repeating", map("action", "ignore"))),
                            "an undeclared off-track action"),
                    Arguments.of(RoleFixtures.valid().set("/off_track", map("bogus", map("action", "show"))),
                            "an undeclared off-track signal"),
                    Arguments.of(RoleFixtures.valid().set("/off_track", map("repeating", map("threshold", 6,
                            "action", "show"))), "a threshold above the compiled repeating threshold"),
                    Arguments.of(RoleFixtures.valid().set("/off_track", map("no_progress", map("threshold", 41,
                            "action", "show"))), "a threshold above the compiled no-progress threshold"),
                    Arguments.of(RoleFixtures.valid().set("/off_track", map("over_size", map("threshold", 3.5,
                            "action", "show"))), "a threshold above the compiled over-size threshold"),
                    Arguments.of(RoleFixtures.valid().set("/off_track", map("outside_footprint", map("threshold", 4,
                            "action", "show"))), "a threshold above the compiled footprint threshold"),
                    Arguments.of(RoleFixtures.valid().set("/off_track", map("repeating", map("threshold", 0.5,
                            "action", "show"))), "a threshold below the floor of the signal"),
                    Arguments.of(RoleFixtures.valid().set("/off_track", map("no_progress", map("threshold", 4,
                            "action", "show"))), "a threshold below the no-progress floor"),
                    Arguments.of(RoleFixtures.valid().set("/off_track", map("over_size", map("threshold", 1.4,
                            "action", "show"))), "a threshold below the over-size floor"),
                    Arguments.of(zeroTool().set("/questions", map("hold_seconds", 0,
                            "resume_within_seconds", 0)), "questions on a zero_tool role"),
                    Arguments.of(zeroTool().set("/off_track", map("repeating", map("action", "show"))),
                            "off_track on a zero_tool role"));
        }

        @ParameterizedTest(name = "{1}")
        @MethodSource("refusals")
        @DisplayName("a document that breaks the schema is schema_invalid")
        void refuses(RoleFixtures.RoleFixture fixture, String why) {
            var refusal = assertThrows(RoleRefusal.class, fixture::artifact, "the schema-breaking document is refused");

            assertEquals(RoleValidationReason.SCHEMA_INVALID, refusal.reason(), "the refusal's reason is schema_invalid");
        }
    }

    @Test
    @DisplayName("the name must equal the file name")
    void nameMustEqualFileName() {
        var document = RoleFixtures.validBytes();
        var refusal = assertThrows(RoleRefusal.class, () -> RoleArtifactValidator.validate(document, "review-code"),
                "a name mismatch is refused");

        assertAll(
                () -> assertEquals(RoleValidationReason.SCHEMA_INVALID, refusal.reason(),
                        "name mismatch refuses as schema_invalid"),
                () -> assertEquals("review-code", refusal.role(), "refusal names the file's role"),
                () -> assertTrue(refusal.detail().contains("implement"), "the detail names the document's own name"));
    }

    // the keywords are the contract: reserved words of the workflow language that match the role-name grammar
    @ParameterizedTest(name = "the reserved keyword ''{0}''")
    @ValueSource(strings = {"role", "state", "consult", "workflow", "from"})
    @DisplayName("a name that is a reserved keyword of the workflow language is schema_invalid")
    void refusesAReservedKeyword(String keyword) {
        var document = RoleFixtures.valid().set("/name", keyword).bytes();

        var refusal = assertThrows(RoleRefusal.class, () -> RoleArtifactValidator.validate(document, keyword),
                "an artifact named by a keyword is refused");

        assertAll(
                () -> assertEquals(RoleValidationReason.SCHEMA_INVALID, refusal.reason(),
                        "a keyword name refuses as schema_invalid"),
                () -> assertEquals(keyword, refusal.role(), "the refusal names the keyword role"),
                () -> assertTrue(refusal.detail().contains("reserved keyword"),
                        "the detail says the name is a keyword"));
    }

    private static RoleFixtures.RoleFixture zeroTool() {
        return RoleFixtures.valid()
                .set("/class", "zero_tool")
                .set("/tools", new ArrayList<>())
                .set("/skills", List.of("skill://pm/role/judge/SKILL.md"));
    }

    private static Map<String, Object> recycling(Integer idleWakeups, int tokenBudget, Integer tasksPerGeneration) {
        var recycling = new LinkedHashMap<String, Object>();
        recycling.put("idle_wakeups", idleWakeups);
        recycling.put("token_budget", tokenBudget);
        recycling.put("tasks_per_generation", tasksPerGeneration);
        return recycling;
    }

    private static Map<String, Object> map(Object... entries) {
        var map = new LinkedHashMap<String, Object>();
        for (var index = 0; index < entries.length; index += 2) {
            map.put((String) entries[index], entries[index + 1]);
        }
        return map;
    }
}
