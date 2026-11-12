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

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.core.role.EffectiveRole;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.role.RoleResolution;
import de.planmarshall.core.role.RoleResolver;
import de.planmarshall.core.role.RoleValidationReason;

/**
 * The composition check of {@code model-work/01-roles-and-configuration.adoc#role-composition}: a named role that is
 * absent or invalid refuses with {@code role_undefined} naming the role, the state and the validation reason; a role
 * whose class does not fit its use, and a consulted {@code zero_tool} role, refuse with {@code class_mismatch} naming the
 * role, its class, the state and the required class. The controls compose the writer's envelope kind, the screener's
 * screen kind and the judge's {@code zero_tool} kind with their roles; a {@code zero_tool} role on the vote kind is
 * refused, because the vote kind requires {@code read_only}.
 */
@EnableGeneratorController
@DisplayName("Role composition")
class RoleCompositionTest {

    // The fixture state id is the key the composition is exercised with.
    private static final String STATE = "implement.envelope-3";

    @Test
    @DisplayName("a writer role composes on the task kind")
    void writerOnTaskComposes() {
        var composition = composition(Map.of("implement", role(RoleClass.WRITER, RoleForm.FRESH)));

        composes(composition, RoleUse.forModelJob(STATE, "task", "implement"));
    }

    @Test
    @DisplayName("a writer role composes on the envelope kind")
    void writerOnEnvelopeComposes() {
        var composition = composition(Map.of("implement", role(RoleClass.WRITER, RoleForm.FRESH)));

        composes(composition, RoleUse.forModelJob(STATE, "envelope", "implement"));
    }

    @Test
    @DisplayName("a zero_tool role composes on the judge's zero_tool kind")
    void zeroToolOnJudgeKindComposes() {
        var composition = composition(Map.of("judge", role(RoleClass.ZERO_TOOL, RoleForm.FRESH)));

        composes(composition, RoleUse.forModelJob(STATE, "zero_tool", "judge"));
    }

    @Test
    @DisplayName("a zero_tool role on the decision or vote kind is class_mismatch: no judgment role votes")
    void zeroToolOnDecisionMismatches() {
        var composition = composition(Map.of("judge", role(RoleClass.ZERO_TOOL, RoleForm.FRESH)));

        var decision = refused(composition, RoleUse.forModelJob(STATE, "decision", "judge"));
        var vote = refused(composition, RoleUse.forModelJob(STATE, "vote", "judge"));

        assertAll(
                () -> assertEquals(CompositionRefusal.Reason.CLASS_MISMATCH, decision.reason(),
                        "a zero_tool decision role is a mismatch"),
                () -> assertEquals(RoleClass.ZERO_TOOL, decision.roleClass().orElseThrow(),
                        "the refusal names the zero_tool class"),
                () -> assertEquals(Set.of(RoleClass.READ_ONLY), decision.requiredClasses(),
                        "the decision kind requires read_only only"),
                () -> assertEquals(CompositionRefusal.Reason.CLASS_MISMATCH, vote.reason(),
                        "a zero_tool vote role is a mismatch"),
                () -> assertEquals(Set.of(RoleClass.READ_ONLY), vote.requiredClasses(),
                        "the vote kind requires read_only only"));
    }

    @Test
    @DisplayName("a writer role on the decision kind is class_mismatch")
    void writerOnDecisionMismatches() {
        var composition = composition(Map.of("implement", role(RoleClass.WRITER, RoleForm.FRESH)));

        var refusal = refused(composition, RoleUse.forModelJob(STATE, "decision", "implement"));

        assertAll(
                () -> assertEquals(CompositionRefusal.Reason.CLASS_MISMATCH, refusal.reason(),
                        "a writer decision role is a mismatch"),
                () -> assertEquals(Set.of(RoleClass.READ_ONLY), refusal.requiredClasses(),
                        "the decision kind requires read_only only"));
    }

    @Test
    @DisplayName("a read_only role on the envelope kind is class_mismatch")
    void readOnlyOnEnvelopeMismatches() {
        var composition = composition(Map.of("review-code", role(RoleClass.READ_ONLY, RoleForm.FRESH)));

        var refusal = refused(composition, RoleUse.forModelJob(STATE, "envelope", "review-code"));

        assertAll(
                () -> assertEquals(CompositionRefusal.Reason.CLASS_MISMATCH, refusal.reason()),
                () -> assertEquals(Set.of(RoleClass.WRITER), refusal.requiredClasses()));
    }

    @Test
    @DisplayName("a zero_tool role composes on the screen kind")
    void zeroToolOnScreenComposes() {
        var composition = composition(Map.of("screener", role(RoleClass.ZERO_TOOL, RoleForm.FRESH)));

        composes(composition, RoleUse.forModelJob(STATE, "screen", "screener"));
    }

    @Test
    @DisplayName("a read_only role composes on the decision kind")
    void readOnlyOnDecisionComposes() {
        var composition = composition(Map.of("triage-review", role(RoleClass.READ_ONLY, RoleForm.FRESH)));

        composes(composition, RoleUse.forModelJob(STATE, "decision", "triage-review"));
    }

    @Test
    @DisplayName("a read_only role on the task kind is class_mismatch")
    void readOnlyOnTaskMismatches() {
        var composition = composition(Map.of("review-code", role(RoleClass.READ_ONLY, RoleForm.FRESH)));

        var refusal = refused(composition, RoleUse.forModelJob(STATE, "task", "review-code"));

        assertAll(
                () -> assertEquals(CompositionRefusal.Reason.CLASS_MISMATCH, refusal.reason()),
                () -> assertEquals("review-code", refusal.role()),
                () -> assertEquals(STATE, refusal.stateId()),
                () -> assertEquals(RoleClass.READ_ONLY, refusal.roleClass().orElseThrow()),
                () -> assertEquals(Set.of(RoleClass.WRITER), refusal.requiredClasses()));
    }

    @Test
    @DisplayName("an absent role is role_undefined")
    void absentRoleIsUndefined() {
        var composition = composition(Map.of());

        var refusal = refused(composition, RoleUse.forModelJob(STATE, "task", "implement"));

        assertAll(
                () -> assertEquals(CompositionRefusal.Reason.ROLE_UNDEFINED, refusal.reason()),
                () -> assertEquals("implement", refusal.role()),
                () -> assertEquals(STATE, refusal.stateId()),
                () -> assertEquals(Optional.empty(), refusal.validationReason()));
    }

    @Test
    @DisplayName("an invalid role is role_undefined with its validation reason")
    void invalidRoleCarriesItsReason() {
        var detail = Generators.letterStrings(10, 40).next();
        RoleResolver resolver = name -> new RoleResolution.Undefined(name,
                RoleValidationReason.MODEL_NOT_ALLOWED, detail);
        var composition = new RoleComposition(resolver);

        var refusal = refused(composition, RoleUse.forModelJob(STATE, "task", "implement"));

        assertAll(
                () -> assertEquals(CompositionRefusal.Reason.ROLE_UNDEFINED, refusal.reason()),
                () -> assertEquals(Optional.of(RoleValidationReason.MODEL_NOT_ALLOWED), refusal.validationReason()),
                () -> assertEquals(detail, refusal.detail()));
    }

    @Test
    @DisplayName("a role_undefined refusal without a validation reason names none in its message")
    void undefinedWithoutReasonNamesNone() {
        var role = "role-" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        var detail = Generators.letterStrings(10, 40).next();

        var refusal = CompositionRefusal.roleUndefined(role, STATE, null, detail);

        // The message shape is the wire contract of the refusal.
        assertAll(
                () -> assertEquals(CompositionRefusal.Reason.ROLE_UNDEFINED, refusal.reason(),
                        "the refusal is role_undefined"),
                () -> assertEquals(Optional.empty(), refusal.validationReason(),
                        "an absent reason reads as an empty Optional"),
                () -> assertEquals("%s: role_undefined names the role '%s': %s".formatted(STATE, role, detail),
                        refusal.getMessage(), "the message names no validation reason"));
    }

    @Test
    @DisplayName("a role_undefined refusal with a validation reason names it in its message")
    void undefinedWithReasonNamesIt() {
        var role = "role-" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        var detail = Generators.letterStrings(10, 40).next();
        var reason = RoleValidationReason.MODEL_NOT_ALLOWED;

        var refusal = CompositionRefusal.roleUndefined(role, STATE, reason, detail);

        // The message shape is the wire contract of the refusal.
        assertAll(
                () -> assertEquals(Optional.of(reason), refusal.validationReason(),
                        "the refusal carries the validation reason"),
                () -> assertEquals("%s: role_undefined names the role '%s' (%s): %s".formatted(STATE, role,
                        reason.wireName(), detail), refusal.getMessage(), "the message names the validation reason"));
    }

    @Test
    @DisplayName("a consulted zero_tool role is class_mismatch")
    void consultedZeroToolMismatches() {
        var composition = composition(Map.of("screener", role(RoleClass.ZERO_TOOL, RoleForm.FRESH)));

        var refusal = refused(composition, RoleUse.forConsultation(STATE, "screener"));

        assertAll(
                () -> assertEquals(CompositionRefusal.Reason.CLASS_MISMATCH, refusal.reason()),
                () -> assertEquals(Set.of(RoleClass.WRITER, RoleClass.READ_ONLY), refusal.requiredClasses()));
    }

    @Test
    @DisplayName("a consulted read_only role composes")
    void consultedReadOnlyComposes() {
        var composition = composition(Map.of("architecture", role(RoleClass.READ_ONLY, RoleForm.FRESH)));

        composes(composition, RoleUse.forConsultation(STATE, "architecture"));
    }

    private static void composes(RoleComposition composition, RoleUse use) {
        var uses = List.of(use);
        assertDoesNotThrow(() -> composition.check(uses));
    }

    private static CompositionRefusal refused(RoleComposition composition, RoleUse use) {
        var uses = List.of(use);
        return assertThrows(CompositionRefusal.class, () -> composition.check(uses));
    }

    private static RoleComposition composition(Map<String, EffectiveRole> roles) {
        RoleResolver resolver = name -> roles.containsKey(name)
                ? new RoleResolution.Defined(roles.get(name))
                : new RoleResolution.Undefined(name, null, Generators.letterStrings(5, 30).next());
        return new RoleComposition(resolver);
    }

    private static EffectiveRole role(RoleClass roleClass, RoleForm form) {
        return ModelFixtures.role("role-" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT),
                roleClass, form);
    }
}
