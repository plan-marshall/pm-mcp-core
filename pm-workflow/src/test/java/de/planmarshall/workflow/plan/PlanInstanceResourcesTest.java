/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.plan;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.core.store.JournalScope;
import de.planmarshall.core.store.ScopeTransaction;
import de.planmarshall.workflow.testing.LockDoubles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The instance resources of a plan (PM-IMPL-5, MCP Tools § Resource URI Templates, Workflow DSL § Display): the DSL text
 * of a version with its header, the diagram of the version in force with its active state, and the version with the
 * composition event that produced it, each read from the plan's store through the package's API.
 */
@EnableGeneratorController
@DisplayName("PlanInstanceResources")
class PlanInstanceResourcesTest {

    /** The fixture project of the temporary store. */
    private static final String PROJECT = "pm-mcp-core";

    @TempDir
    Path base;

    private JournalScope scope;

    private PlanInstanceStore store;

    private PlanInstanceResources resources;

    @BeforeEach
    void createScope() throws IOException {
        scope = JournalScope.plan(base, PROJECT, PlanFixtures.PLAN_ID);
        Files.createDirectories(scope.journalFile().getParent());
        store = new PlanInstanceStore(scope);
        resources = new PlanInstanceResources(store);
    }

    /** @return the version 1 the composer returns for the fixture plan */
    private static PlanInstance version1() {
        var result = PlanFixtures.composer().compose(PlanFixtures.start(PlanFixtures.facts().build()));
        return assertInstanceOf(CompositionResult.Composed.class, result, result::toString).instance();
    }

    /** @return the texts of the sub-units the fixture plan names, each with its reference */
    private static List<SubUnitText> texts() {
        return PlanFixtures.subUnits().stream().map(subUnit -> new SubUnitText(subUnit.reference(),
                PlanFixtures.subUnitText(Objects.requireNonNull(subUnit.reference().file(), "file")))).toList();
    }

    /** Records version 1 in one transaction of the plan and commits it, as one locked call of the runtime does. */
    private void recordVersion1() {
        var instance = version1();
        try (var lock = LockDoubles.holdingEverything()) {
            var transaction = ScopeTransaction.open(lock, scope, event -> {
                // no audit record is owed by an instance write
            }, Clock.fixed(PlanFixtures.COMPOSED_AT, ZoneOffset.UTC));
            store.recordComposed(transaction, instance, texts(),
                    CompositionRecord.composed(instance, null, PlanFixtures.COMPOSED_AT));
            transaction.commit();
        }
    }

    @Nested
    @DisplayName("The text of a version")
    class Text {

        @Test
        @DisplayName("is read with its header and every composed file of the version")
        void readWithHeader() {
            recordVersion1();
            var composed = version1();

            var read = resources.text(1).orElseThrow();

            assertAll(
                    () -> assertEquals(1, read.version(), "the read version is the version 1"),
                    () -> assertEquals(CompositionEvent.START, read.header().event(),
                            "the header names the composition event"),
                    () -> assertEquals(composed.text(), read.text(), "the text is the version's own text"),
                    () -> assertTrue(read.text().contains("workflow plan version 1 format 1"),
                            "the text names the version it is"),
                    () -> assertTrue(read.text().contains("\ninstance "),
                            "the text carries the instance header line"),
                    () -> assertEquals(composed.units().size(), resources.version(1).orElseThrow().units().size(),
                            "every composed file of the version is read back"));
        }
    }

    @Nested
    @DisplayName("The diagram of the version in force")
    class Diagram {

        @Test
        @DisplayName("marks the active state and marks none without one")
        void marksTheActiveState() {
            recordVersion1();
            var inForce = version1();
            var active = inForce.unit().startState();

            var diagram = resources.diagram(1, active).orElseThrow();

            assertAll(
                    () -> assertTrue(diagram.contains("stateDiagram-v2"),
                            "the diagram is a Mermaid state diagram"),
                    () -> assertTrue(diagram.startsWith("---\ntitle: "),
                            "the diagram names the version in its title block"),
                    () -> assertTrue(diagram.contains("classDef active fill:#90ee90"),
                            "the diagram styles the active state"),
                    () -> assertTrue(diagram.lines().map(String::strip)
                                    .anyMatch(line -> line.startsWith("class ") && line.endsWith(" active")),
                            "a state carries the active class"),
                    () -> assertFalse(resources.diagram(1, null).orElseThrow().contains("classDef active"),
                            "no state is marked when none is active"));
        }

        @Test
        @DisplayName("refuses an active state the version in force does not hold")
        void refusesAnUnknownActiveState() {
            recordVersion1();

            assertThrows(IllegalArgumentException.class, () -> resources.diagram(1, "no.such.state"),
                    "a state the version does not hold is refused");
        }
    }

    @Nested
    @DisplayName("The status of the version in force")
    class Status {

        @Test
        @DisplayName("names the version and the composition event that produced it")
        void namesVersionAndEvent() {
            recordVersion1();

            var status = resources.status(1).orElseThrow();

            assertAll(
                    () -> assertEquals(1, status.version(), "the status names the version in force"),
                    () -> assertEquals(CompositionEvent.START, status.event(),
                            "the status names the composition event"),
                    () -> assertEquals(PlanFixtures.COMPOSED_AT, status.composedAt(),
                            "the status names when the version was composed"));
        }
    }

    @Test
    @DisplayName("a version that was never written reads empty at every resource")
    void unwrittenVersionIsAbsent() {
        assertAll(
                () -> assertEquals(Optional.empty(), resources.version(1), "an unwritten version is not read"),
                () -> assertEquals(Optional.empty(), resources.text(1), "the text of no version is read"),
                () -> assertEquals(Optional.empty(), resources.diagram(1, null), "no diagram is rendered for it"),
                () -> assertEquals(Optional.empty(), resources.status(1), "the status of no version is read"));
    }
}
