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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
@EnableGeneratorController
@DisplayName("Role assignment validation")
class RoleAssignmentValidationTest {

    private static final String HARNESS = "p" + Generators.letterStrings(3, 8).next().toLowerCase(Locale.ROOT);
    private static final String ESCALATION_HARNESS = "q" + Generators.letterStrings(3, 8).next()
            .toLowerCase(Locale.ROOT);
    private static final String MODEL = "m" + Generators.letterStrings(3, 8).next().toLowerCase(Locale.ROOT);
    private static final String ESCALATION_MODEL = "e" + Generators.letterStrings(3, 8).next()
            .toLowerCase(Locale.ROOT);
    private static final String FAST_MODEL = "f" + Generators.letterStrings(3, 8).next().toLowerCase(Locale.ROOT);
    private static final String UNSUPPORTED_MODEL = "u" + Generators.letterStrings(3, 8).next()
            .toLowerCase(Locale.ROOT);
    private static final String ROLE = "implement";
    private static final String ARTIFACT_DESCRIPTION = Generators.letterStrings(10, 40).next();
    private static final String EXECUTABLE = Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
    private static final String VERSION = "%d.%d.%d".formatted(Generators.integers(1, 9).next(),
            Generators.integers(0, 20).next(), Generators.integers(0, 20).next());
    private static final String EXECUTABLE_SHA = "sha256:" + "%064x".formatted(Generators.longs().next());
    private static final int BUDGET_FLOOR = Generators.integers(1, 1_000_000).next();

    private static RoleArtifact artifact(RoleClass roleClass) {
        return new RoleArtifact(1, ROLE, ARTIFACT_DESCRIPTION, roleClass,
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
                Map.of(MODEL, List.of("low", "medium", "high")), Map.of("standard", MODEL), BUDGET_FLOOR, null,
                new HarnessProfile.Resumability(false, null, false, null), "ask_user");
    }

    private static EnrolledHarness enrolled() {
        return new EnrolledHarness(EXECUTABLE, VERSION, EXECUTABLE_SHA);
    }

    private static ModelCatalogue catalogue() {
        return new ModelCatalogue(Map.of(HARNESS, Set.of(MODEL), ESCALATION_HARNESS, Set.of(ESCALATION_MODEL)));
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
                profiles(profileList), enrolments(enrolmentList), catalogue.orElse(null)));
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
                base.modelEfforts(), base.modelTiers(), BUDGET_FLOOR, null, base.resumability(), "ask_user");
        var noReadOnly = new HarnessProfile(HARNESS, true, false, Set.of(),
                Map.of(RoleClass.WRITER, HarnessProfile.ToolSet.ENFORCED), base.modelEfforts(),
                base.modelTiers(), BUDGET_FLOOR, null, base.resumability(), "ask_user");
        var value = RoleAssignmentValue.of(HARNESS, MODEL, "high");
        var artifact = artifact(RoleClass.READ_ONLY);
        var machineCatalogue = catalogue();
        var fastOnly = new ModelCatalogue(Map.of(HARNESS, Set.of(FAST_MODEL)));
        var facts = Optional.of(machineCatalogue);
        var fastFacts = Optional.of(fastOnly);
        var noFacts = Optional.<ModelCatalogue>empty();
        var ineligibleRefused = assertThrows(RoleRefusal.class, () -> require(value, artifact,
                new HarnessProfile[]{ineligible}, new String[]{HARNESS}, facts));
        var classRefused = assertThrows(RoleRefusal.class, () -> require(value, artifact,
                new HarnessProfile[]{noReadOnly}, new String[]{HARNESS}, facts));
        var unsupportedEffort = RoleAssignmentValue.of(HARNESS, UNSUPPORTED_MODEL, "high");
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
        var escalation = new Escalation(ESCALATION_HARNESS, ESCALATION_MODEL, null);
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
                () -> assertEquals("the harness '" + ESCALATION_HARNESS + "' is not enrolled", detail));
    }

    @Test
    @DisplayName("the escalation is validated against its own harness")
    void escalationOnAnotherHarness() {
        var escalation = new Escalation(ESCALATION_HARNESS, ESCALATION_MODEL, null);
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
        var escalation = new Escalation(HARNESS, FAST_MODEL, null);
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
                () -> assertEquals(Optional.of(machine), RoleAssignmentValidation.effective(null, machine)),
                () -> assertEquals(Optional.of(catalogue()),
                        RoleAssignmentValidation.effective(catalogue(), machine)),
                () -> assertEquals(Optional.empty(), RoleAssignmentValidation.effective(null, null)));
    }
}
