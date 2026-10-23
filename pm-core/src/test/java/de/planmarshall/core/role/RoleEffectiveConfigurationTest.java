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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The effective configuration of a role (PM-WORK-1, Role Assignment): the harness, model and effort taken from the shipped
 * artifact, the machine artifact, the machine's role assignment, the project artifact, the project's role assignment and
 * the plan's override, each later level overriding only what it names; read afresh at every resolution; and the steward's
 * configuration taken from the machine instead of its artifact.
 */
@DisplayName("Effective role configuration")
class RoleEffectiveConfigurationTest {

    private static final String FRONTIER = "acme-frontier";
    private static final String FAST = "acme-fast";

    @TempDir
    Path project;

    @Test
    @DisplayName("the project artifact's values come after the machine assignment")
    void projectArtifactOverridesMachineAssignment() throws Exception {
        var environment = accepted();
        environment.machineAssignments(assignments(RoleFixtures.ROLE,
                assignment(null, FRONTIER, new EffortValue("medium"))));
        Files.write(project.resolve(RoleFixtures.ROLE + ".json"),
                RoleFixtures.valid().set("/model", RoleFixtures.MODEL).bytes());

        var role = resolved(RoleFixtures.shippedValid().withProject(project), environment, RoleAssignment.none());

        assertAll(
                () -> assertEquals(RoleFixtures.MODEL, role.model(), "the project artifact replaces the assignment"),
                () -> assertNull(role.effort().value(), "the project artifact names no effort"));
    }

    @Test
    @DisplayName("the artifact's own values apply without an assignment")
    void artifactValuesApply() {
        var role = resolved(RoleFixtures.shippedValid(), RoleFixtures.environment(), RoleAssignment.none());

        assertAll(
                () -> assertEquals(RoleFixtures.HARNESS, role.harness()),
                () -> assertEquals(RoleFixtures.MODEL, role.model()),
                () -> assertNull(role.effort().value()),
                () -> assertEquals("1.4", role.harnessVersion()));
    }

    @Test
    @DisplayName("each level overrides only what it names")
    void eachLevelOverridesOnlyWhatItNames() {
        var environment = accepted();
        environment.machineAssignments(assignments(RoleFixtures.ROLE, assignment(null, FRONTIER, null)));
        environment.projectAssignments(assignments(RoleFixtures.ROLE, assignment(null, null, new EffortValue("medium"))));

        var afterProject = resolved(RoleFixtures.shippedValid(), environment, RoleAssignment.none());
        var afterPlan = resolved(RoleFixtures.shippedValid(), environment,
                assignment(null, RoleFixtures.MODEL, null));

        assertAll(
                () -> assertEquals(FRONTIER, afterProject.model(), "the project names no model"),
                () -> assertEquals(RoleFixtures.HARNESS, afterProject.harness(), "no level names a harness"),
                () -> assertEquals("medium", afterProject.effort().value()),
                () -> assertEquals(RoleFixtures.MODEL, afterPlan.model(), "the plan names the model"),
                () -> assertEquals("medium", afterPlan.effort().value(), "the plan names no effort"));
    }

    @Test
    @DisplayName("a level that names a null effort clears the effort of the level below")
    void namedNullEffortClears() {
        var environment = accepted();
        environment.machineAssignments(assignments(RoleFixtures.ROLE,
                assignment(null, RoleFixtures.MODEL, new EffortValue("medium"))));
        environment.projectAssignments(assignments(RoleFixtures.ROLE, assignment(null, null, new EffortValue(null))));

        var role = resolved(RoleFixtures.shippedValid(), environment, RoleAssignment.none());

        assertNull(role.effort().value());
    }

    @Test
    @DisplayName("a changed assignment is read at the next resolution")
    void assignmentIsReadAtEveryResolution() {
        var environment = accepted();
        environment.machineAssignments(assignments(RoleFixtures.ROLE,
                assignment(null, FRONTIER, new EffortValue("medium"))));
        var catalog = new RoleCatalog(RoleFixtures.shippedValid(), environment);

        var before = defined(catalog.resolve(RoleFixtures.ROLE, RoleAssignment.none()));
        environment.machineAssignments(assignments(RoleFixtures.ROLE,
                assignment(null, FAST, new EffortValue(null))));
        var after = defined(catalog.resolve(RoleFixtures.ROLE, RoleAssignment.none()));

        assertAll(
                () -> assertEquals(FRONTIER, before.model()),
                () -> assertEquals(FAST, after.model()),
                () -> assertNotEquals(before.configurationDigest(), after.configurationDigest()),
                () -> assertNotEquals(before.qualificationDigest(), after.qualificationDigest()));
    }

    @Test
    @DisplayName("a project assignment follows the project artifact")
    void projectAssignmentFollowsProjectArtifact() throws Exception {
        var environment = accepted();
        environment.machineAssignments(assignments(RoleFixtures.ROLE,
                assignment(null, FRONTIER, new EffortValue("medium"))));
        environment.projectAssignments(assignments(RoleFixtures.ROLE, assignment(null, null, new EffortValue("high"))));
        Files.write(project.resolve(RoleFixtures.ROLE + ".json"),
                RoleFixtures.valid().set("/model", RoleFixtures.MODEL).bytes());

        var role = resolved(RoleFixtures.shippedValid().withProject(project), environment, RoleAssignment.none());

        assertAll(
                () -> assertEquals(RoleFixtures.MODEL, role.model(), "the project artifact replaces the assignment"),
                () -> assertEquals("high", role.effort().value(), "the project assignment names the effort"));
    }

    @Test
    @DisplayName("the plan's resolver applies the plan's overrides and changes both digests")
    void planResolverAppliesOverrides() {
        var overrides = new RoleAssignments(Map.of(RoleFixtures.ROLE,
                assignment(null, FAST, new EffortValue(null))));
        var catalog = new RoleCatalog(RoleFixtures.shippedValid(), accepted());

        var before = defined(catalog.resolve(RoleFixtures.ROLE, RoleAssignment.none()));
        var after = defined(catalog.forPlan(overrides).resolve(RoleFixtures.ROLE));

        assertAll(
                () -> assertEquals(FAST, after.model()),
                () -> assertNotEquals(before.configurationDigest(), after.configurationDigest()),
                () -> assertNotEquals(before.qualificationDigest(), after.qualificationDigest()));
    }

    @Test
    @DisplayName("an invalid effective model is model_not_allowed")
    void invalidEffectiveModelIsRefused() {
        var environment = RoleFixtures.environment();
        var refusal = definedRisky(new RoleCatalog(RoleFixtures.shippedValid(), environment)
                .resolve(RoleFixtures.ROLE, assignment(null, FAST, null)));

        assertEquals(RoleValidationReason.MODEL_NOT_ALLOWED, refusal);
    }

    @Test
    @DisplayName("the effective catalogue is the project's, else the machine's")
    void projectCatalogueReplacesTheMachineCatalogue() {
        var environment = RoleFixtures.environment();

        var withoutProject = resolved(RoleFixtures.shippedValid(), environment, RoleAssignment.none());
        environment.projectCatalogue(RoleFixtures.catalogue(RoleFixtures.HARNESS, FRONTIER));
        var withRefusingProject = new RoleCatalog(RoleFixtures.shippedValid(), environment)
                .resolve(RoleFixtures.ROLE, RoleAssignment.none());

        assertAll(
                () -> assertEquals(RoleFixtures.MODEL, withoutProject.model(), "the machine's catalogue applies"),
                () -> assertEquals(RoleValidationReason.MODEL_NOT_ALLOWED,
                        ((RoleResolution.Undefined) withRefusingProject).reason().orElseThrow(),
                        "the project's catalogue replaces the machine's"));
    }

    @Test
    @DisplayName("an absent role is undefined without a validation reason")
    void absentRoleIsUndefined() {
        var resolution = new RoleCatalog(RoleFixtures.shippedValid(), RoleFixtures.environment())
                .resolve("review-code", RoleAssignment.none());

        assertAll(
                () -> assertTrue(resolution instanceof RoleResolution.Undefined),
                () -> assertEquals(Optional.empty(), ((RoleResolution.Undefined) resolution).reason()));
    }

    @Test
    @DisplayName("an invalid artifact is undefined with schema_invalid")
    void invalidArtifactIsUndefined() {
        var sets = RoleFixtures.shipped(Map.of(RoleFixtures.ROLE,
                RoleFixtures.valid().set("/bogus", 1).bytes()));

        var resolution = new RoleCatalog(sets, RoleFixtures.environment())
                .resolve(RoleFixtures.ROLE, RoleAssignment.none());

        assertEquals(Optional.of(RoleValidationReason.SCHEMA_INVALID),
                ((RoleResolution.Undefined) resolution).reason());
    }

    @Nested
    @DisplayName("the steward")
    class Steward {

        @Test
        @DisplayName("runs on the machine's default harness and the tier standard, not on its artifact")
        void stewardUsesMachineConfiguration() {
            var sets = RoleFixtures.shipped(Map.of(RoleCatalog.STEWARD, stewardArtifact()));
            var role = defined(new RoleCatalog(sets, RoleFixtures.environment()).resolveSteward());

            assertAll(
                    () -> assertEquals(RoleFixtures.HARNESS, role.harness()),
                    () -> assertEquals(RoleFixtures.MODEL, role.model(), "the tier standard of the default harness"),
                    () -> assertEquals("medium", role.effort().value(), "the default assignment's effort"));
        }

        @Test
        @DisplayName("is never model_not_allowed")
        void stewardSkipsTheModelCatalogue() {
            var environment = RoleFixtures.environment().machineCatalogue(RoleFixtures.catalogue(RoleFixtures.HARNESS,
                    "another-model"));
            var sets = RoleFixtures.shipped(Map.of(RoleCatalog.STEWARD, stewardArtifact()));

            var role = defined(new RoleCatalog(sets, environment).resolveSteward());

            assertEquals(RoleFixtures.MODEL, role.model());
        }

        @Test
        @DisplayName("every resolution of the steward uses the machine configuration")
        void stewardResolvesThroughTheCatalog() {
            var sets = RoleFixtures.shipped(Map.of(RoleCatalog.STEWARD, stewardArtifact()));

            var role = defined(new RoleCatalog(sets, RoleFixtures.environment())
                    .resolve(RoleCatalog.STEWARD, RoleAssignment.none()));

            assertAll(
                    () -> assertEquals(RoleFixtures.HARNESS, role.harness()),
                    () -> assertEquals(RoleFixtures.MODEL, role.model()));
        }

        @Test
        @DisplayName("is undefined without a default harness")
        void stewardNeedsADefaultHarness() {
            var environment = RoleFixtures.environment()
                    .machineConfiguration(new MachineConfiguration(Optional.empty()));
            var sets = RoleFixtures.shipped(Map.of(RoleCatalog.STEWARD, stewardArtifact()));

            var resolution = new RoleCatalog(sets, environment).resolveSteward();

            assertEquals(Optional.of(RoleValidationReason.HARNESS_UNKNOWN),
                    ((RoleResolution.Undefined) resolution).reason());
        }
    }

    private static byte[] stewardArtifact() {
        return RoleFixtures.valid()
                .set("/name", RoleCatalog.STEWARD)
                .set("/description", "The machine's advisor.")
                .set("/harness", "other-harness")
                .set("/model", "other-model")
                .set("/skills", java.util.List.of("skill://pm/role/steward/SKILL.md"))
                .bytes();
    }

    private static RoleFixtures.TestEnvironment accepted() {
        return RoleFixtures.environment()
                .machineCatalogue(RoleFixtures.catalogue(RoleFixtures.HARNESS, RoleFixtures.MODEL, FRONTIER, FAST));
    }

    private static EffectiveRole resolved(RoleSets sets, RoleEnvironment environment, RoleAssignment override) {
        return defined(new RoleCatalog(sets, environment).resolve(RoleFixtures.ROLE, override));
    }

    private static EffectiveRole defined(RoleResolution resolution) {
        assertTrue(resolution instanceof RoleResolution.Defined, resolution::toString);
        return ((RoleResolution.Defined) resolution).role();
    }

    private static RoleValidationReason definedRisky(RoleResolution resolution) {
        assertTrue(resolution instanceof RoleResolution.Undefined, resolution::toString);
        return ((RoleResolution.Undefined) resolution).reason().orElseThrow();
    }

    private static RoleAssignments assignments(String role, RoleAssignment assignment) {
        var roles = new LinkedHashMap<String, RoleAssignment>();
        roles.put(role, assignment);
        return new RoleAssignments(roles);
    }

    private static RoleAssignment assignment(String harness, String model, EffortValue effort) {
        return new RoleAssignment(Optional.ofNullable(harness), Optional.ofNullable(model),
                Optional.ofNullable(effort));
    }
}
