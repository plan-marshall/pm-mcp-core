/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.unit;

import static de.planmarshall.workflow.log.WorkflowLogMessages.ERROR;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.planmarshall.workflow.ast.Phase;
import de.planmarshall.workflow.ast.UnitOrigin;
import de.planmarshall.workflow.dsl.RefusalRule;
import de.planmarshall.workflow.verify.InvariantFixtures;
import de.planmarshall.workflow.verify.VerificationRule;

/**
 * The loader of the default units (PM-IMPL-5): it reads the project-ownable unit files from the classpath root it is
 * given, never from a fixed one, so the tests read {@code unit-fixtures/defaults/}, units written for them, and the
 * root {@code workflows/} of the product is not read at all. A default unit that is missing, refused by the parser,
 * refused by a rule or declaring an identifier already taken is a finding with its resource and line, logged as ERROR
 * {@code DEFAULT_UNIT_REFUSED}.
 */
@EnableTestLogger
@DisplayName("DefaultUnitLoader")
class DefaultUnitLoaderTest {

    /** The classpath root of the units written into a temporary directory. */
    private static final String TEMP_ROOT = "defaults/";

    @TempDir
    Path temp;

    /**
     * Loads the fixture units written into the temporary directory, each mutation applied, through a class loader
     * that sees only that directory.
     */
    private UnitLoadResult loadWritten(List<String> unitFiles, UnitFixtures.Mutation... mutations) throws IOException {
        UnitFixtures.writeUnits(temp.resolve(TEMP_ROOT), mutations);
        return loadTemp(unitFiles);
    }

    /**
     * Loads the units of the temporary directory through a class loader that sees only that directory.
     */
    private UnitLoadResult loadTemp(List<String> unitFiles) throws IOException {
        try (var classLoader = new URLClassLoader(new URL[]{temp.toUri().toURL()}, null)) {
            return new DefaultUnitLoader(classLoader, TEMP_ROOT, unitFiles, UnitFixtures.context(), Map.of()).load();
        }
    }

    private static void assertRefusalLogged(String resource) {
        assertAll("ERROR record",
                () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.ERROR,
                        ERROR.DEFAULT_UNIT_REFUSED.resolveIdentifierString()),
                () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.ERROR, resource));
    }

    @Nested
    @DisplayName("Passing defaults")
    class PassingDefaults {

        @Test
        @DisplayName("loads and verifies the fixture defaults from the given root, the plan unit first")
        void loadsFixtureDefaults() {
            var result = UnitFixtures.defaultLoader().load();

            assertAll(
                    () -> assertTrue(result.isVerified(), result.findings()::toString),
                    () -> assertEquals(List.of(Phase.PLAN_UNIT, "phase-1-init", "phase-2-refine", "phase-3-outline",
                                    "phase-4-plan", "phase-5-execute", "land", "phase-6-finalize",
                                    "adjudicate.decisions", "replan", "git.commit_and_push",
                                    "remediation.qgate-findings"),
                            List.copyOf(result.units().keySet())),
                    () -> assertEquals(Optional.empty(), result.refusal()),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.ERROR, DefaultUnitLoader.class));
        }

        @Test
        @DisplayName("parses every unit as a default unit of its own, the plan unit and each phase with its own file")
        void defaultOriginAndFiles() {
            var units = UnitFixtures.defaultUnits();
            var plan = units.get(Phase.PLAN_UNIT);

            assertAll(
                    () -> units.values().forEach(unit -> assertEquals(UnitOrigin.DEFAULT, unit.origin(), unit::id)),
                    () -> assertEquals(InvariantFixtures.ROOT + "plan.pmwf", plan.location().resourceName()),
                    () -> assertEquals(Optional.of("phases.init"), plan.startState()),
                    () -> assertEquals(InvariantFixtures.ROOT + "init.pmwf",
                            units.get("phase-1-init").location().resourceName()),
                    () -> assertEquals(Map.of(), plan.derivedFrom()));
        }

        @Test
        @DisplayName("passes Rule 5 when every released state name is still declared")
        void releasedStatesKept() {
            var loader = new DefaultUnitLoader(DefaultUnitLoaderTest.class.getClassLoader(), InvariantFixtures.ROOT,
                    UnitFixtures.UNIT_FILES, UnitFixtures.context(),
                    Map.of("replan", List.copyOf(UnitFixtures.defaultUnits().get("replan").states().keySet())));

            var result = loader.load();

            assertTrue(result.isVerified(), result.findings()::toString);
        }

        @Test
        @DisplayName("no unit lies below the root workflows/ of the product on the test classpath")
        void productRootUnread() {
            assertNull(DefaultUnitLoaderTest.class.getClassLoader().getResource("workflows/"));
        }
    }

    @Nested
    @DisplayName("Refused defaults")
    class RefusedDefaults {

        @Test
        @DisplayName("refuses a missing default unit with its resource and logs DEFAULT_UNIT_REFUSED")
        void missingDefault() {
            var files = List.of("replan.pmwf", "missing.pmwf");
            var loader = new DefaultUnitLoader(DefaultUnitLoaderTest.class.getClassLoader(), InvariantFixtures.ROOT,
                    files, UnitFixtures.context(), Map.of());
            var resource = InvariantFixtures.ROOT + "missing.pmwf";

            var result = loader.load();

            assertAll(
                    () -> assertEquals(List.of(new UnitFinding(UnitFinding.Kind.MISSING, UnitFinding.UNIT_MISSING,
                                    resource, 0, "the default unit '%s' is not on the classpath".formatted(resource))),
                            result.findings()),
                    () -> assertEquals(Map.of(), result.units()),
                    () -> assertRefusalLogged(resource));
        }

        @Test
        @DisplayName("refuses a default unit the parser refuses with its resource and line")
        void unparsableDefault() throws Exception {
            var resource = TEMP_ROOT + InvariantFixtures.REPLAN_FILE;

            var result = loadWritten(UnitFixtures.UNIT_FILES, UnitFixtures.UNPARSABLE);

            var finding = UnitFixtures.find(result.findings(), UnitFinding.Kind.PARSE, RefusalRule.STRUCTURE.name());
            assertAll(
                    () -> assertTrue(finding.isPresent(), result.findings()::toString),
                    () -> assertEquals(resource, finding.orElseThrow().file()),
                    () -> assertEquals(UnitFixtures.UNPARSABLE.lineOf("workflow replan revision"),
                            finding.orElseThrow().line()),
                    () -> assertEquals(Map.of(), result.units()),
                    () -> assertRefusalLogged(resource));
        }

        @Test
        @DisplayName("refuses a default unit that violates a rule with the file and line of the offending node")
        void ruleViolation() throws Exception {
            var resource = TEMP_ROOT + UnitFixtures.FINALIZE_FILE;
            var line = UnitFixtures.RULE_VIOLATION.lineOf("merged -> finalize.nowhere");

            var result = loadWritten(UnitFixtures.UNIT_FILES, UnitFixtures.RULE_VIOLATION);

            assertAll(
                    () -> assertFalse(result.isVerified()),
                    () -> assertTrue(result.findings().stream().allMatch(
                            finding -> finding.kind() == UnitFinding.Kind.RULE), result.findings()::toString),
                    () -> assertTrue(result.findings().stream().anyMatch(
                                    finding -> resource.equals(finding.file()) && finding.line() == line),
                            result.findings()::toString),
                    () -> assertRefusalLogged(resource));
        }

        @Test
        @DisplayName("refuses a default file declaring the id of an earlier unit at its header, replacing nothing")
        void twoFilesOneId() throws Exception {
            var mutation = UnitFixtures.DUPLICATE_UNIT_ID;
            var resource = TEMP_ROOT + mutation.file();

            var result = loadWritten(UnitFixtures.UNIT_FILES, mutation);

            var finding = UnitFixtures.find(result.findings(), UnitFinding.Kind.RULE, UnitFinding.DUPLICATE_UNIT_ID);
            assertAll(
                    () -> assertTrue(finding.isPresent(), result.findings()::toString),
                    () -> assertEquals(resource, finding.orElseThrow().file()),
                    () -> assertEquals(mutation.lineOf("workflow adjudicate.decisions"), finding.orElseThrow().line()),
                    () -> assertTrue(finding.orElseThrow().message()
                            .contains(TEMP_ROOT + InvariantFixtures.ADJUDICATION_FILE), finding.orElseThrow()::message),
                    () -> assertEquals(Map.of(), result.units()),
                    () -> assertRefusalLogged(resource));
        }

        @Test
        @DisplayName("refuses a default file declaring the id plan at its header, never in place of the plan unit")
        void planId() throws Exception {
            var mutation = UnitFixtures.PLAN_UNIT_ID;
            var resource = TEMP_ROOT + mutation.file();

            var result = loadWritten(UnitFixtures.UNIT_FILES, mutation);

            var finding = UnitFixtures.find(result.findings(), UnitFinding.Kind.RULE, UnitFinding.DUPLICATE_UNIT_ID);
            assertAll(
                    () -> assertTrue(finding.isPresent(), result.findings()::toString),
                    () -> assertEquals(resource, finding.orElseThrow().file()),
                    () -> assertEquals(mutation.lineOf(UnitFixtures.PLAN_HEADER), finding.orElseThrow().line()),
                    () -> assertTrue(finding.orElseThrow().message().contains(TEMP_ROOT + "plan.pmwf"),
                            finding.orElseThrow()::message),
                    () -> assertEquals(Map.of(), result.units()),
                    () -> assertRefusalLogged(resource));
        }

        @Test
        @DisplayName("refuses a default unit that drops a released state name (Rule 5)")
        void releasedStateDropped() {
            var loader = new DefaultUnitLoader(DefaultUnitLoaderTest.class.getClassLoader(), InvariantFixtures.ROOT,
                    UnitFixtures.UNIT_FILES, UnitFixtures.context(), Map.of("replan", List.of("replan.dropped")));

            var result = loader.load();

            assertAll(
                    () -> assertEquals(List.of(VerificationRule.RELEASED_STATE.name()),
                            result.findings().stream().map(UnitFinding::code).toList()),
                    () -> assertEquals(InvariantFixtures.ROOT + InvariantFixtures.REPLAN_FILE,
                            result.findings().getFirst().file()),
                    () -> assertRefusalLogged(InvariantFixtures.ROOT + InvariantFixtures.REPLAN_FILE));
        }

        @Test
        @DisplayName("refuses the plan unit that calls a missing phase file, and verifies no part on its own")
        void missingPhaseFile() throws Exception {
            UnitFixtures.writeUnits(temp.resolve(TEMP_ROOT));
            Files.delete(temp.resolve(TEMP_ROOT).resolve("refine.pmwf"));

            var result = loadTemp(UnitFixtures.UNIT_FILES);

            assertAll(
                    () -> assertEquals(List.of(TEMP_ROOT + "refine.pmwf", TEMP_ROOT + "plan.pmwf"),
                            result.findings().stream().map(UnitFinding::file).toList()),
                    () -> assertEquals(UnitFinding.Kind.MISSING, result.findings().getFirst().kind()),
                    () -> assertEquals(Map.of(), result.units()));
        }
    }

    @Test
    @DisplayName("refuses a null argument")
    void nullArguments() {
        var loader = DefaultUnitLoaderTest.class.getClassLoader();
        var context = UnitFixtures.context();
        var files = UnitFixtures.UNIT_FILES;

        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> new DefaultUnitLoader(null, InvariantFixtures.ROOT, files, context, Map.of())),
                () -> assertThrows(NullPointerException.class,
                        () -> new DefaultUnitLoader(loader, null, files, context, Map.of())),
                () -> assertThrows(NullPointerException.class,
                        () -> new DefaultUnitLoader(loader, InvariantFixtures.ROOT, null, context, Map.of())),
                () -> assertThrows(NullPointerException.class,
                        () -> new DefaultUnitLoader(loader, InvariantFixtures.ROOT, files, null, Map.of())),
                () -> assertThrows(NullPointerException.class,
                        () -> new DefaultUnitLoader(loader, InvariantFixtures.ROOT, files, context, null)));
    }
}
