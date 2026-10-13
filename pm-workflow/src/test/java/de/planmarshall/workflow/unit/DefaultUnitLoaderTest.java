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

import static de.planmarshall.core.log.PmMcpLogMessages.ERROR;
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
import de.planmarshall.workflow.ast.UnitOrigin;
import de.planmarshall.workflow.dsl.RefusalRule;
import de.planmarshall.workflow.verify.InvariantFixtures;
import de.planmarshall.workflow.verify.VerificationRule;

/**
 * The loader of the default units (PM-IMPL-5): it reads the project-ownable unit files from the classpath root it is
 * given, never from a fixed one, so the tests read {@code unit-fixtures/defaults/}, units written for them, and the
 * root {@code workflows/} of the product is not read at all. A default unit that is missing, refused by the parser or
 * refused by a rule is a finding with its resource and line, logged as ERROR {@code DEFAULT_UNIT_REFUSED}.
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
        try (var classLoader = new URLClassLoader(new URL[] {temp.toUri().toURL()}, null)) {
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
                    () -> assertEquals(List.of(PlanUnitMerger.PLAN_UNIT, "adjudicate.decisions", "replan",
                            "git.commit_and_push", "remediation.qgate-findings"),
                            List.copyOf(result.units().keySet())),
                    () -> assertEquals(Optional.empty(), result.refusal()),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.ERROR, DefaultUnitLoader.class));
        }

        @Test
        @DisplayName("parses every unit as a default unit and merges the phase files with their resource names")
        void defaultOriginAndMerge() {
            var units = UnitFixtures.defaultUnits();
            var plan = units.get(PlanUnitMerger.PLAN_UNIT);

            assertAll(
                    () -> units.values().forEach(unit -> assertEquals(UnitOrigin.DEFAULT, unit.origin(), unit::id)),
                    () -> assertEquals(InvariantFixtures.PLAN_FILES.stream().map(file -> InvariantFixtures.ROOT + file)
                            .toList(), List.copyOf(plan.partVersions().keySet())),
                    () -> assertEquals(InvariantFixtures.ROOT + "init.pmwf", plan.location().resourceName()),
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
        void unparsableDefault() throws IOException {
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
        void ruleViolation() throws IOException {
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
        @DisplayName("merges no plan unit when a phase file is missing, and verifies no part on its own")
        void missingPhaseFile() throws IOException {
            UnitFixtures.writeUnits(temp.resolve(TEMP_ROOT));
            Files.delete(temp.resolve(TEMP_ROOT).resolve("refine.pmwf"));

            var result = loadTemp(UnitFixtures.UNIT_FILES);

            assertAll(
                    () -> assertEquals(List.of(TEMP_ROOT + "refine.pmwf"),
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
