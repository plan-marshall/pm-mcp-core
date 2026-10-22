/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.model;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.role.EffectiveRole;
import de.planmarshall.core.role.EffortValue;
import de.planmarshall.core.role.HarnessProfile;
import de.planmarshall.core.role.RoleArtifact;
import de.planmarshall.core.role.RoleArtifactValidator;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.role.RoleResolution;
import de.planmarshall.core.role.RoleResolver;
import de.planmarshall.core.role.RoleSkill;
import de.planmarshall.core.role.RoleValidationReason;

/**
 * The composition check of {@code model-work/01-roles-and-configuration.adoc#role-composition}: a named role that is
 * absent or invalid refuses with {@code role_undefined} naming the role, the state and the validation reason; a role
 * whose class does not fit its use, and a consulted {@code zero_tool} role, refuse with {@code class_mismatch} naming the
 * role, its class, the state and the required class. The control composes every kind of the shipped units with its role.
 */
@DisplayName("Role composition")
class RoleCompositionTest {

    private static final String STATE = "implement.envelope-3";

    @Test
    @DisplayName("a writer role composes on the task kind")
    void writerOnTaskComposes() {
        var composition = composition(Map.of("implement", role(RoleClass.WRITER, RoleForm.FRESH)));

        assertDoesNotThrow(() -> composition.check(List.of(RoleUse.forModelJob(STATE, "task", "implement"))));
    }

    @Test
    @DisplayName("a zero_tool role composes on the screen kind")
    void zeroToolOnScreenComposes() {
        var composition = composition(Map.of("screener", role(RoleClass.ZERO_TOOL, RoleForm.FRESH)));

        assertDoesNotThrow(() -> composition.check(List.of(RoleUse.forModelJob(STATE, "screen", "screener"))));
    }

    @Test
    @DisplayName("a read_only role composes on the decision kind")
    void readOnlyOnDecisionComposes() {
        var composition = composition(Map.of("triage-review", role(RoleClass.READ_ONLY, RoleForm.FRESH)));

        assertDoesNotThrow(() -> composition.check(List.of(RoleUse.forModelJob(STATE, "decision", "triage-review"))));
    }

    @Test
    @DisplayName("a read_only role on the task kind is class_mismatch")
    void readOnlyOnTaskMismatches() {
        var composition = composition(Map.of("review-code", role(RoleClass.READ_ONLY, RoleForm.FRESH)));

        var refusal = assertThrows(CompositionRefusal.class,
                () -> composition.check(List.of(RoleUse.forModelJob(STATE, "task", "review-code"))));

        assertAll(
                () -> assertEquals(CompositionRefusal.Reason.CLASS_MISMATCH, refusal.reason()),
                () -> assertEquals("review-code", refusal.role()),
                () -> assertEquals(STATE, refusal.stateId()),
                () -> assertEquals(RoleClass.READ_ONLY, refusal.roleClass()),
                () -> assertEquals(Set.of(RoleClass.WRITER), refusal.requiredClasses()));
    }

    @Test
    @DisplayName("an absent role is role_undefined")
    void absentRoleIsUndefined() {
        var composition = composition(Map.of());

        var refusal = assertThrows(CompositionRefusal.class,
                () -> composition.check(List.of(RoleUse.forModelJob(STATE, "task", "implement"))));

        assertAll(
                () -> assertEquals(CompositionRefusal.Reason.ROLE_UNDEFINED, refusal.reason()),
                () -> assertEquals("implement", refusal.role()),
                () -> assertEquals(STATE, refusal.stateId()),
                () -> assertEquals(Optional.empty(), refusal.validationReason()));
    }

    @Test
    @DisplayName("an invalid role is role_undefined with its validation reason")
    void invalidRoleCarriesItsReason() {
        RoleResolver resolver = name -> new RoleResolution.Undefined(name,
                Optional.of(RoleValidationReason.MODEL_NOT_ALLOWED), "the catalogue does not allow the model");
        var composition = new RoleComposition(resolver);

        var refusal = assertThrows(CompositionRefusal.class,
                () -> composition.check(List.of(RoleUse.forModelJob(STATE, "task", "implement"))));

        assertAll(
                () -> assertEquals(CompositionRefusal.Reason.ROLE_UNDEFINED, refusal.reason()),
                () -> assertEquals(Optional.of(RoleValidationReason.MODEL_NOT_ALLOWED), refusal.validationReason()),
                () -> assertEquals("the catalogue does not allow the model", refusal.detail()));
    }

    @Test
    @DisplayName("a consulted zero_tool role is class_mismatch")
    void consultedZeroToolMismatches() {
        var composition = composition(Map.of("screener", role(RoleClass.ZERO_TOOL, RoleForm.FRESH)));

        var refusal = assertThrows(CompositionRefusal.class,
                () -> composition.check(List.of(RoleUse.forConsultation(STATE, "screener"))));

        assertAll(
                () -> assertEquals(CompositionRefusal.Reason.CLASS_MISMATCH, refusal.reason()),
                () -> assertEquals(Set.of(RoleClass.WRITER, RoleClass.READ_ONLY), refusal.requiredClasses()));
    }

    @Test
    @DisplayName("a consulted read_only role composes")
    void consultedReadOnlyComposes() {
        var composition = composition(Map.of("architecture", role(RoleClass.READ_ONLY, RoleForm.FRESH)));

        assertDoesNotThrow(() -> composition.check(List.of(RoleUse.forConsultation(STATE, "architecture"))));
    }

    private static RoleComposition composition(Map<String, EffectiveRole> roles) {
        RoleResolver resolver = name -> roles.containsKey(name)
                ? new RoleResolution.Defined(roles.get(name))
                : new RoleResolution.Undefined(name, Optional.empty(), "the role is absent");
        return new RoleComposition(resolver);
    }

    private static EffectiveRole role(RoleClass roleClass, RoleForm form) {
        var artifact = artifact(roleClass, form);
        return EffectiveRole.of(artifact, "acme-cli", "acme-standard", new EffortValue(null),
                List.of(new RoleSkill("skill://pm/role/x/SKILL.md", "a".repeat(64))), "1.4",
                Optional.empty(), profile());
    }

    private static RoleArtifact artifact(RoleClass roleClass, RoleForm form) {
        var tools = roleClass == RoleClass.ZERO_TOOL
                ? "[]"
                : "[\"pm_wait\",\"pm_state\",\"pm_do\",\"pm_skill\",\"pm_skill_file\"]";
        var recycling = form == RoleForm.WARM
                ? ",\"recycling\":{\"idle_wakeups\":null,\"token_budget\":10000,\"tasks_per_generation\":null}"
                : "";
        var json = """
                {"format_version":1,"name":"role-x","description":"A role.","class":"%s","tools":%s,
                 "harness":"acme-cli","model":"acme-standard","effort":null,"form":"%s","count":2,
                 "skills":["skill://pm/role/x/SKILL.md"],"budget_floor_seconds":300%s}
                """.formatted(roleClass.wireName(), tools, form.wireName(), recycling);
        return RoleArtifactValidator.validate(json.getBytes(StandardCharsets.UTF_8), "role-x");
    }

    private static HarnessProfile profile() {
        var classes = new LinkedHashMap<RoleClass, HarnessProfile.ToolSet>();
        classes.put(RoleClass.WRITER, HarnessProfile.ToolSet.ENFORCED);
        classes.put(RoleClass.READ_ONLY, HarnessProfile.ToolSet.ADVISORY);
        classes.put(RoleClass.ZERO_TOOL, HarnessProfile.ToolSet.ENFORCED);
        return new HarnessProfile("acme-cli", true, true, Set.of("*"), classes,
                Map.of("acme-standard", List.of("medium", "high")),
                Map.of("frontier", "acme-frontier", "standard", "acme-standard", "fast", "acme-fast"),
                400_000, 3, new HarnessProfile.Resumability(true, 600, true, 604_800), "ask_user");
    }
}
