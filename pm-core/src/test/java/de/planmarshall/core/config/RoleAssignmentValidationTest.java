/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.config;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.EnrolledHarness;
import de.planmarshall.core.role.EffortValue;
import de.planmarshall.core.role.HarnessProfile;
import de.planmarshall.core.role.ModelCatalogue;
import de.planmarshall.core.role.RoleArtifact;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.role.RoleRefusal;
import de.planmarshall.core.role.RoleValidationReason;

/**
 * The validation {@code roles.write} runs before it records a value (State Catalogue § Role Assignment,
 * {@code roles.validate}): the harness is enrolled and eligible, the profile runs the role's class, the model is allowed
 * by the effective catalogue and the effort is one the harness accepts.
 */
@DisplayName("Role assignment validation")
class RoleAssignmentValidationTest {

    private static final String HARNESS = "acme-cli";
    private static final String MODEL = "acme-standard";
    private static final String ROLE = "implement";

    private static RoleArtifact artifact(RoleClass roleClass) {
        return new RoleArtifact(1, ROLE, "Implements the tasks of a plan.", roleClass,
                List.of("pm_wait", "pm_state", "pm_do", "pm_skill", "pm_skill_file"), HARNESS, MODEL,
                new EffortValue(null), RoleForm.FRESH, 2, List.of("skill://pm/role/implement/SKILL.md"), null, 300,
                null, null, Map.of());
    }

    private static HarnessProfile profile() {
        return new HarnessProfile(HARNESS, true, false, Set.of(), Map.of(RoleClass.WRITER,
                        HarnessProfile.ToolSet.ENFORCED, RoleClass.READ_ONLY, HarnessProfile.ToolSet.ADVISORY),
                Map.of(MODEL, List.of("low", "medium", "high")), Map.of("standard", MODEL), 400_000, null,
                new HarnessProfile.Resumability(false, null, false, null), "ask_user");
    }

    private static EnrolledHarness enrolled() {
        return new EnrolledHarness("acme", "1.4.2", "sha256:" + "0".repeat(64));
    }

    private static ModelCatalogue catalogue() {
        return new ModelCatalogue(Map.of(HARNESS, Set.of(MODEL)));
    }

    private static void require(RoleAssignmentValue value, RoleArtifact artifact, HarnessProfile profile,
            Optional<EnrolledHarness> enrolled, Optional<ModelCatalogue> catalogue) {
        RoleAssignmentValidation.require(ROLE, value, artifact, Optional.of(profile), enrolled, catalogue);
    }

    @Test
    @DisplayName("a value the harness and the catalogue allow passes")
    void passes() {
        assertDoesNotThrow(() -> require(RoleAssignmentValue.of(HARNESS, MODEL, "high"), artifact(RoleClass.READ_ONLY),
                profile(), Optional.of(enrolled()), Optional.of(catalogue())));
    }

    @Test
    @DisplayName("a harness without a profile or enrolment is harness_unknown")
    void harnessUnknown() {
        var value = RoleAssignmentValue.of(HARNESS, MODEL, "high");
        assertAll(
                () -> assertEquals(RoleValidationReason.HARNESS_UNKNOWN,
                        assertThrows(RoleRefusal.class, () -> RoleAssignmentValidation.require(ROLE, value,
                                artifact(RoleClass.READ_ONLY), Optional.empty(), Optional.of(enrolled()),
                                Optional.of(catalogue()))).reason()),
                () -> assertEquals(RoleValidationReason.HARNESS_UNKNOWN,
                        assertThrows(RoleRefusal.class, () -> require(value, artifact(RoleClass.READ_ONLY), profile(),
                                Optional.empty(), Optional.of(catalogue()))).reason()));
    }

    @Test
    @DisplayName("an ineligible harness, a class it does not run, an unsupported effort and a refused model are refused")
    void refusals() {
        var ineligible = new HarnessProfile(HARNESS, false, false, Set.of(), profile().classes(), profile().modelEfforts(),
                profile().modelTiers(), 400_000, null, profile().resumability(), "ask_user");
        var noReadOnly = new HarnessProfile(HARNESS, true, false, Set.of(),
                Map.of(RoleClass.WRITER, HarnessProfile.ToolSet.ENFORCED), profile().modelEfforts(),
                profile().modelTiers(), 400_000, null, profile().resumability(), "ask_user");
        var value = RoleAssignmentValue.of(HARNESS, MODEL, "high");

        assertAll(
                () -> assertEquals(RoleValidationReason.HARNESS_NOT_ELIGIBLE,
                        assertThrows(RoleRefusal.class, () -> require(value, artifact(RoleClass.READ_ONLY), ineligible,
                                Optional.of(enrolled()), Optional.of(catalogue()))).reason()),
                () -> assertEquals(RoleValidationReason.HARNESS_CLASS_NOT_RUNNABLE,
                        assertThrows(RoleRefusal.class, () -> require(value, artifact(RoleClass.READ_ONLY), noReadOnly,
                                Optional.of(enrolled()), Optional.of(catalogue()))).reason()),
                () -> assertEquals(RoleValidationReason.EFFORT_UNSUPPORTED,
                        assertThrows(RoleRefusal.class, () -> require(
                                RoleAssignmentValue.of(HARNESS, "acme-frontier", "high"),
                                artifact(RoleClass.READ_ONLY), profile(), Optional.of(enrolled()),
                                Optional.of(catalogue()))).reason()),
                () -> assertEquals(RoleValidationReason.MODEL_NOT_ALLOWED,
                        assertThrows(RoleRefusal.class, () -> require(value, artifact(RoleClass.READ_ONLY), profile(),
                                Optional.of(enrolled()),
                                Optional.of(new ModelCatalogue(Map.of(HARNESS, Set.of("acme-fast")))))).reason()),
                () -> assertEquals(RoleValidationReason.MODEL_NOT_ALLOWED,
                        assertThrows(RoleRefusal.class, () -> require(value, artifact(RoleClass.READ_ONLY), profile(),
                                Optional.of(enrolled()), Optional.empty())).reason()));
    }

    @Test
    @DisplayName("the escalation is validated like the role's own values")
    void escalation() {
        var escalation = new Escalation(HARNESS, "acme-fast", Optional.empty());
        var value = RoleAssignmentValue.of(HARNESS, MODEL, "high").withEscalation(escalation);

        var refusal = assertThrows(RoleRefusal.class, () -> require(value, artifact(RoleClass.READ_ONLY), profile(),
                Optional.of(enrolled()), Optional.of(catalogue())));

        assertEquals(RoleValidationReason.MODEL_NOT_ALLOWED, refusal.reason());
    }

    @Test
    @DisplayName("the effective catalogue is the project's, else the machine's")
    void effectiveCatalogue() {
        var machine = catalogue();

        assertAll(
                () -> assertEquals(Optional.of(machine), RoleAssignmentValidation.effective(Optional.empty(),
                        Optional.of(machine))),
                () -> assertEquals(Optional.of(catalogue()),
                        RoleAssignmentValidation.effective(Optional.of(catalogue()), Optional.of(machine))),
                () -> assertEquals(Optional.empty(),
                        RoleAssignmentValidation.effective(Optional.empty(), Optional.empty())));
    }
}
