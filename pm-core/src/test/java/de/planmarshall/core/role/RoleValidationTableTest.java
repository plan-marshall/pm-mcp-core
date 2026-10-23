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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.profile.EnrolledHarness;

/**
 * The validation table of {@code model-work/01-roles-and-configuration.adoc#role-validation}, row by row: every reason of
 * the table has a refusing fixture, and a valid artifact passes. The reason {@code session_first_not_permitted} is not
 * part of the table and is not reported.
 */
@DisplayName("Validation against the client profile")
class RoleValidationTableTest {

    @Test
    @DisplayName("the valid artifact passes every row")
    void validArtifactPasses() {
        var skills = check(RoleFixtures.valid().artifact(), RoleFixtures.harness(), RoleFixtures.catalogue(),
                new EffortValue(null), RoleFixtures.environment(), false);

        assertEquals(List.of(new RoleSkill("skill://pm/role/implement/SKILL.md", RoleFixtures.SKILL_DIGEST)), skills);
    }

    @Test
    @DisplayName("a zero_tool role passes on a harness that runs zero_tool")
    void zeroToolRolePasses() {
        check(zeroTool(List.of()).artifact(), RoleFixtures.harness(), RoleFixtures.catalogue(), new EffortValue(null),
                RoleFixtures.environment(), false);
    }

    @Test
    @DisplayName("the steward passes although the catalogue does not allow its model")
    void stewardSkipsTheModelCatalogue() {
        check(RoleFixtures.valid().artifact(), RoleFixtures.harness(),
                RoleFixtures.catalogue(RoleFixtures.HARNESS, "another-model"), new EffortValue(null),
                RoleFixtures.environment(), true);
    }

    static Stream<Arguments> refusals() {
        var environment = RoleFixtures.environment();
        environment.unresolvedSkill("skill://pm/role/missing/SKILL.md");
        return Stream.of(
                Arguments.of(RoleFixtures.valid().artifact(), Optional.empty(), Optional.of(RoleFixtures.enrolled()),
                        Optional.of(RoleFixtures.catalogue()), environment, false,
                        RoleValidationReason.HARNESS_UNKNOWN, "no profile has the client id"),
                Arguments.of(RoleFixtures.valid().artifact(), Optional.of(ineligible()),
                        Optional.of(RoleFixtures.enrolled()), Optional.of(RoleFixtures.catalogue()), environment, false,
                        RoleValidationReason.HARNESS_NOT_ELIGIBLE, "the profile is not worker-eligible"),
                Arguments.of(RoleFixtures.valid().artifact(), Optional.of(withoutReadOnly()),
                        Optional.of(RoleFixtures.enrolled()), Optional.of(RoleFixtures.catalogue()), environment, false,
                        RoleValidationReason.HARNESS_CLASS_NOT_RUNNABLE, "the profile cannot run read_only"),
                Arguments.of(zeroTool(List.of()).artifact(), Optional.of(withoutZeroTool()),
                        Optional.of(RoleFixtures.enrolled()), Optional.of(RoleFixtures.catalogue()), environment, false,
                        RoleValidationReason.HARNESS_CLASS_NOT_RUNNABLE, "the profile cannot run zero_tool"),
                Arguments.of(RoleFixtures.valid().set("/class", "writer").artifact(),
                        Optional.of(withoutClass(RoleClass.WRITER)), Optional.of(RoleFixtures.enrolled()),
                        Optional.of(RoleFixtures.catalogue()), environment, false,
                        RoleValidationReason.HARNESS_CLASS_NOT_RUNNABLE, "the profile cannot run writer"),
                Arguments.of(RoleFixtures.valid().artifact(), Optional.of(RoleFixtures.harness()), Optional.empty(),
                        Optional.of(RoleFixtures.catalogue()), environment, false,
                        RoleValidationReason.HARNESS_UNKNOWN, "the harness is not enrolled"),
                Arguments.of(RoleFixtures.valid().set("/class", "writer").artifact(),
                        Optional.of(restricted("other-model")), Optional.of(RoleFixtures.enrolled()),
                        Optional.of(RoleFixtures.catalogue()), environment, false,
                        RoleValidationReason.MODEL_RESTRICTED, "the model refuses a restricted tool set"),
                Arguments.of(RoleFixtures.valid().set("/effort", "low").artifact(),
                        Optional.of(RoleFixtures.harness()), Optional.of(RoleFixtures.enrolled()),
                        Optional.of(RoleFixtures.catalogue()), environment, false,
                        RoleValidationReason.EFFORT_UNSUPPORTED, "the effort is below every accepted value"),
                Arguments.of(RoleFixtures.valid().set("/effort", "medium").artifact(),
                        Optional.of(noEfforts()), Optional.of(RoleFixtures.enrolled()),
                        Optional.of(RoleFixtures.catalogue()), environment, false,
                        RoleValidationReason.EFFORT_UNSUPPORTED, "the model takes no effort flag"),
                Arguments.of(RoleFixtures.valid().artifact(), Optional.of(RoleFixtures.harness()),
                        Optional.of(RoleFixtures.enrolled()),
                        Optional.of(RoleFixtures.catalogue(RoleFixtures.HARNESS, "another-model")), environment, false,
                        RoleValidationReason.MODEL_NOT_ALLOWED, "the effective catalogue does not allow the model"),
                Arguments.of(tools(List.of("pm_state", "pm_do", "pm_skill", "pm_skill_file")).artifact(),
                        Optional.of(RoleFixtures.harness()), Optional.of(RoleFixtures.enrolled()),
                        Optional.of(RoleFixtures.catalogue()), environment, false,
                        RoleValidationReason.TOOL_NOT_PERMITTED, "a PM-MCP tool every role names is missing"),
                Arguments.of(tools(List.of("pm_wait", "pm_state", "pm_do", "pm_skill", "pm_skill_file", "Agent"))
                                .artifact(),
                        Optional.of(RoleFixtures.harness()), Optional.of(RoleFixtures.enrolled()),
                        Optional.of(RoleFixtures.catalogue()), environment, false,
                        RoleValidationReason.TOOL_NOT_PERMITTED, "the harness tool that starts sub-agents"),
                Arguments.of(tools(List.of("pm_wait", "pm_state", "pm_do", "pm_skill", "pm_skill_file", "ask_user"))
                                .artifact(),
                        Optional.of(RoleFixtures.harness()), Optional.of(RoleFixtures.enrolled()),
                        Optional.of(RoleFixtures.catalogue()), environment, false,
                        RoleValidationReason.TOOL_NOT_PERMITTED, "the harness tool that asks the user"),
                Arguments.of(tools(List.of("pm_wait", "pm_state", "pm_do", "pm_skill", "pm_skill_file", "pm_build"))
                                .artifact(),
                        Optional.of(RoleFixtures.harness()), Optional.of(RoleFixtures.enrolled()),
                        Optional.of(RoleFixtures.catalogue()), environment, false,
                        RoleValidationReason.TOOL_NOT_PERMITTED, "pm_build is outside the read_only tool set"),
                Arguments.of(zeroTool(List.of("pm_state")).artifact(),
                        Optional.of(RoleFixtures.harness()), Optional.of(RoleFixtures.enrolled()),
                        Optional.of(RoleFixtures.catalogue()), environment, false,
                        RoleValidationReason.TOOL_NOT_PERMITTED, "a zero_tool role names a tool"),
                Arguments.of(warmZeroTool(), Optional.of(RoleFixtures.harness()),
                        Optional.of(RoleFixtures.enrolled()), Optional.of(RoleFixtures.catalogue()), environment, false,
                        RoleValidationReason.FORM_NOT_PERMITTED, "a zero_tool role is warm"),
                Arguments.of(RoleFixtures.valid().set("/skills", List.of("skill://pm/compiled/x/SKILL.md")).artifact(),
                        Optional.of(RoleFixtures.harness()), Optional.of(RoleFixtures.enrolled()),
                        Optional.of(RoleFixtures.catalogue()), environment, false,
                        RoleValidationReason.SKILL_UNRESOLVED, "the skill does not resolve in the project"),
                Arguments.of(RoleFixtures.valid()
                                .set("/skills", List.of("skill://pm/role/missing/SKILL.md")).artifact(),
                        Optional.of(RoleFixtures.harness()), Optional.of(RoleFixtures.enrolled()),
                        Optional.of(RoleFixtures.catalogue()), environment, false,
                        RoleValidationReason.SKILL_UNRESOLVED, "the role skill is refused by the project"));
    }

    @ParameterizedTest(name = "{6}: {7}")
    @MethodSource("refusals")
    @DisplayName("the reason of the row is reported")
    void refuses(RoleArtifact artifact, Optional<HarnessProfile> profile, Optional<EnrolledHarness> enrolled,
            Optional<ModelCatalogue> catalogue, RoleEnvironment environment, boolean steward,
            RoleValidationReason expected, String why) {
        var effort = artifact.effort();
        var values = new RoleValidation.Values(RoleFixtures.HARNESS, RoleFixtures.MODEL, effort);
        var refusal = assertThrows(RoleRefusal.class,
                () -> RoleValidation.check(artifact, values, profile, enrolled, catalogue, environment, steward), why);

        assertEquals(expected, refusal.reason(), why);
    }

    @Test
    @DisplayName("the table has exactly its ten reasons and no session_first_not_permitted")
    void reasonSetIsClosed() {
        var names = Stream.of(RoleValidationReason.values()).map(RoleValidationReason::wireName).toList();

        assertAll(
                () -> assertTrue(names.containsAll(List.of("schema_invalid", "harness_unknown",
                        "harness_not_eligible", "harness_class_not_runnable", "model_restricted",
                        "effort_unsupported", "model_not_allowed", "tool_not_permitted", "form_not_permitted",
                        "skill_unresolved"))),
                () -> assertEquals(10, names.size()),
                () -> assertTrue(names.stream().noneMatch("session_first_not_permitted"::equals)));
    }

    private static List<RoleSkill> check(RoleArtifact artifact, HarnessProfile profile, ModelCatalogue catalogue,
            EffortValue effort, RoleEnvironment environment, boolean steward) {
        return RoleValidation.check(artifact, new RoleValidation.Values(RoleFixtures.HARNESS, RoleFixtures.MODEL, effort),
                Optional.of(profile), Optional.of(RoleFixtures.enrolled()), Optional.of(catalogue), environment,
                steward);
    }

    private static HarnessProfile ineligible() {
        return profile(false, RoleFixtures.harness().classes(), Set.of("*"), RoleFixtures.harness().modelEfforts());
    }

    private static HarnessProfile withoutReadOnly() {
        return withoutClass(RoleClass.READ_ONLY);
    }

    private static HarnessProfile withoutZeroTool() {
        return withoutClass(RoleClass.ZERO_TOOL);
    }

    private static HarnessProfile withoutClass(RoleClass roleClass) {
        var classes = new LinkedHashMap<>(RoleFixtures.harness().classes());
        classes.remove(roleClass);
        return profile(true, classes, Set.of("*"), RoleFixtures.harness().modelEfforts());
    }

    private static HarnessProfile restricted(String... models) {
        return profile(true, RoleFixtures.harness().classes(), Set.of(models),
                RoleFixtures.harness().modelEfforts());
    }

    private static HarnessProfile noEfforts() {
        return profile(true, RoleFixtures.harness().classes(), Set.of("*"), Map.of(RoleFixtures.MODEL, List.of()));
    }

    private static HarnessProfile profile(boolean eligible, Map<RoleClass, HarnessProfile.ToolSet> classes,
            Set<String> restricted, Map<String, List<String>> efforts) {
        return new HarnessProfile(RoleFixtures.HARNESS, eligible, true, restricted, classes, efforts,
                RoleFixtures.harness().modelTiers(), 400_000, 3, RoleFixtures.harness().resumability(), "ask_user");
    }

    private static RoleFixtures.RoleFixture tools(List<String> tools) {
        return RoleFixtures.valid().set("/tools", new ArrayList<>(tools));
    }

    private static RoleFixtures.RoleFixture zeroTool(List<String> tools) {
        return RoleFixtures.valid()
                .set("/class", "zero_tool")
                .set("/tools", new ArrayList<>(tools))
                .set("/skills", List.of("skill://pm/role/judge/SKILL.md"));
    }

    private static RoleArtifact warmZeroTool() {
        return zeroTool(List.of())
                .set("/form", "warm")
                .set("/recycling", map("idle_wakeups", null, "token_budget", 10_000, "tasks_per_generation", null))
                .artifact();
    }

    private static Map<String, Object> map(Object... entries) {
        var map = new LinkedHashMap<String, Object>();
        for (var index = 0; index < entries.length; index += 2) {
            map.put((String) entries[index], entries[index + 1]);
        }
        return map;
    }
}
