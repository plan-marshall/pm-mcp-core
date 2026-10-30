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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
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
 * by the effective catalogue and the effort is one the harness accepts — each value against its own harness, the
 * escalation included.
 */
@DisplayName("Role assignment validation")
class RoleAssignmentValidationTest {

    private static final String HARNESS = "acme-cli";
    private static final String ESCALATION_HARNESS = "beta-cli";
    private static final String MODEL = "acme-standard";
    private static final String ROLE = "implement";

    private static RoleArtifact artifact(RoleClass roleClass) {
        return new RoleArtifact(1, ROLE, "Implements the tasks of a plan.", roleClass,
                List.of("pm_wait", "pm_state", "pm_do", "pm_skill", "pm_skill_file"), HARNESS, MODEL,
                new EffortValue(null), RoleForm.FRESH, 2, List.of("skill://pm/role/implement/SKILL.md"), null, 300,
                null, null, Map.of());
    }

    private static HarnessProfile profile() {
        return profile(HARNESS);
    }

    private static HarnessProfile profile(String clientId) {
        return new HarnessProfile(clientId, true, false, Set.of(), Map.of(RoleClass.WRITER,
                        HarnessProfile.ToolSet.ENFORCED, RoleClass.READ_ONLY, HarnessProfile.ToolSet.ADVISORY),
                Map.of(MODEL, List.of("low", "medium", "high")), Map.of("standard", MODEL), 400_000, null,
                new HarnessProfile.Resumability(false, null, false, null), "ask_user");
    }

    private static EnrolledHarness enrolled() {
        return new EnrolledHarness("acme", "1.4.2", "sha256:" + "0".repeat(64));
    }

    private static ModelCatalogue catalogue() {
        return new ModelCatalogue(Map.of(HARNESS, Set.of(MODEL), ESCALATION_HARNESS, Set.of("beta-standard")));
    }

    private static Function<String, Optional<HarnessProfile>> profiles(HarnessProfile... profiles) {
        var byId = new LinkedHashMap<String, HarnessProfile>();
        for (var profile : profiles) {
            byId.put(profile.clientId(), profile);
        }
        return clientId -> Optional.ofNullable(byId.get(clientId));
    }

    private static Function<String, Optional<EnrolledHarness>> enrolments(String... clientIds) {
        var ids = Set.of(clientIds);
        return clientId -> ids.contains(clientId) ? Optional.of(enrolled()) : Optional.empty();
    }

    private static void require(RoleAssignmentValue value, RoleArtifact artifact, HarnessProfile[] profileList,
            String[] enrolmentList, Optional<ModelCatalogue> catalogue) {
        RoleAssignmentValidation.require(ROLE, value, artifact, new RoleAssignmentValidation.Facts(
                profiles(profileList), enrolments(enrolmentList), catalogue));
    }

    private static void require(RoleAssignmentValue value, RoleArtifact artifact) {
        require(value, artifact, new HarnessProfile[]{profile(), profile(ESCALATION_HARNESS)},
                new String[]{HARNESS, ESCALATION_HARNESS}, Optional.of(catalogue()));
    }

    @Test
    @DisplayName("a value the harness and the catalogue allow passes")
    void passes() {
        assertDoesNotThrow(() -> require(RoleAssignmentValue.of(HARNESS, MODEL, "high"),
                artifact(RoleClass.READ_ONLY)));
    }

    @Test
    @DisplayName("a harness without a profile or enrolment is harness_unknown")
    void harnessUnknown() {
        var value = RoleAssignmentValue.of(HARNESS, MODEL, "high");
        var artifact = artifact(RoleClass.READ_ONLY);
        var machineCatalogue = catalogue();
        var harnessProfile = profile();
        var facts = Optional.of(machineCatalogue);
        var withoutProfile = assertThrows(RoleRefusal.class, () -> require(value, artifact, new HarnessProfile[]{},
                new String[]{HARNESS}, facts));
        var withoutEnrolment = assertThrows(RoleRefusal.class, () -> require(value, artifact,
                new HarnessProfile[]{harnessProfile}, new String[]{}, facts));
        var profileReason = withoutProfile.reason();
        var enrolmentReason = withoutEnrolment.reason();

        assertAll(
                () -> assertEquals(RoleValidationReason.HARNESS_UNKNOWN, profileReason),
                () -> assertEquals(RoleValidationReason.HARNESS_UNKNOWN, enrolmentReason));
    }

    @Test
    @DisplayName("an ineligible harness, a class it does not run, an unsupported effort and a refused model are refused")
    void refusals() {
        var base = profile();
        var ineligible = new HarnessProfile(HARNESS, false, false, Set.of(), base.classes(),
                base.modelEfforts(), base.modelTiers(), 400_000, null, base.resumability(), "ask_user");
        var noReadOnly = new HarnessProfile(HARNESS, true, false, Set.of(),
                Map.of(RoleClass.WRITER, HarnessProfile.ToolSet.ENFORCED), base.modelEfforts(),
                base.modelTiers(), 400_000, null, base.resumability(), "ask_user");
        var value = RoleAssignmentValue.of(HARNESS, MODEL, "high");
        var artifact = artifact(RoleClass.READ_ONLY);
        var machineCatalogue = catalogue();
        var fastOnly = new ModelCatalogue(Map.of(HARNESS, Set.of("acme-fast")));
        var facts = Optional.of(machineCatalogue);
        var fastFacts = Optional.of(fastOnly);
        var noFacts = Optional.<ModelCatalogue>empty();
        var ineligibleRefused = assertThrows(RoleRefusal.class, () -> require(value, artifact,
                new HarnessProfile[]{ineligible}, new String[]{HARNESS}, facts));
        var classRefused = assertThrows(RoleRefusal.class, () -> require(value, artifact,
                new HarnessProfile[]{noReadOnly}, new String[]{HARNESS}, facts));
        var unsupportedEffort = RoleAssignmentValue.of(HARNESS, "acme-frontier", "high");
        var effortRefused = assertThrows(RoleRefusal.class, () -> require(unsupportedEffort, artifact));
        var refusedModel = assertThrows(RoleRefusal.class, () -> require(value, artifact,
                new HarnessProfile[]{base}, new String[]{HARNESS}, fastFacts));
        var noCatalogue = assertThrows(RoleRefusal.class, () -> require(value, artifact,
                new HarnessProfile[]{base}, new String[]{HARNESS}, noFacts));
        var ineligibleReason = ineligibleRefused.reason();
        var classReason = classRefused.reason();
        var effortReason = effortRefused.reason();
        var refusedModelReason = refusedModel.reason();
        var noCatalogueReason = noCatalogue.reason();

        assertAll(
                () -> assertEquals(RoleValidationReason.HARNESS_NOT_ELIGIBLE, ineligibleReason),
                () -> assertEquals(RoleValidationReason.HARNESS_CLASS_NOT_RUNNABLE, classReason),
                () -> assertEquals(RoleValidationReason.EFFORT_UNSUPPORTED, effortReason),
                () -> assertEquals(RoleValidationReason.MODEL_NOT_ALLOWED, refusedModelReason),
                () -> assertEquals(RoleValidationReason.MODEL_NOT_ALLOWED, noCatalogueReason));
    }

    @Test
    @DisplayName("a value below every effort the harness lists is refused")
    void effortBelowEveryValue() {
        var value = RoleAssignmentValue.of(HARNESS, MODEL, "aa-lowest");
        var artifact = artifact(RoleClass.READ_ONLY);

        var refusal = assertThrows(RoleRefusal.class, () -> require(value, artifact));

        assertEquals(RoleValidationReason.EFFORT_UNSUPPORTED, refusal.reason());
    }

    @Test
    @DisplayName("the escalation's enrolment is looked up by its own harness")
    void escalationEnrolmentOnItsOwnHarness() {
        var escalation = new Escalation(ESCALATION_HARNESS, "beta-standard", Optional.empty());
        var value = RoleAssignmentValue.of(HARNESS, MODEL, "high").withEscalation(escalation);
        var artifact = artifact(RoleClass.READ_ONLY);
        var bothHarnesses = new HarnessProfile[]{profile(), profile(ESCALATION_HARNESS)};
        var machineCatalogue = catalogue();
        var facts = Optional.of(machineCatalogue);

        var refusal = assertThrows(RoleRefusal.class, () -> require(value, artifact, bothHarnesses,
                new String[]{HARNESS}, facts));
        var reason = refusal.reason();
        var detail = refusal.detail();

        assertAll(
                () -> assertEquals(RoleValidationReason.HARNESS_UNKNOWN, reason),
                () -> assertEquals("the harness 'beta-cli' is not enrolled", detail));
    }

    @Test
    @DisplayName("the escalation is validated against its own harness")
    void escalationOnAnotherHarness() {
        var escalation = new Escalation(ESCALATION_HARNESS, "beta-standard", Optional.empty());
        var value = RoleAssignmentValue.of(HARNESS, MODEL, "high").withEscalation(escalation);
        var withoutBeta = new HarnessProfile[]{profile()};
        var artifact = artifact(RoleClass.READ_ONLY);
        var machineCatalogue = catalogue();
        var facts = Optional.of(machineCatalogue);
        var refused = assertThrows(RoleRefusal.class, () -> require(value, artifact, withoutBeta,
                new String[]{HARNESS, ESCALATION_HARNESS}, facts));
        var reason = refused.reason();

        assertDoesNotThrow(() -> require(value, artifact));
        assertEquals(RoleValidationReason.HARNESS_UNKNOWN, reason);
    }

    @Test
    @DisplayName("an escalation the catalogue does not allow is refused")
    void escalationNotAllowed() {
        var escalation = new Escalation(HARNESS, "acme-fast", Optional.empty());
        var value = RoleAssignmentValue.of(HARNESS, MODEL, "high").withEscalation(escalation);
        var artifact = artifact(RoleClass.READ_ONLY);

        var refusal = assertThrows(RoleRefusal.class, () -> require(value, artifact));

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
