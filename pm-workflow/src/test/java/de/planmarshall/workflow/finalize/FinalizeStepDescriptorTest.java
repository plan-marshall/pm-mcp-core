/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.finalize;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.workflow.finalize.FinalizeStepDescriptor.ConfigurableKey;
import de.planmarshall.workflow.finalize.FinalizeStepDescriptor.InputScope;
import de.planmarshall.workflow.finalize.FinalizeStepDescriptor.Lane;
import de.planmarshall.workflow.finalize.FinalizeStepDescriptor.LaneClass;

/**
 * The descriptor of a finalize step (PM-IMPL-5) is immutable: it copies its lists and sets, refuses a missing required
 * value in itself and in its nested types, and carries an absent optional value as {@code null}.
 */
@EnableGeneratorController
@DisplayName("FinalizeStepDescriptor")
class FinalizeStepDescriptorTest {

    @Nested
    @DisplayName("Immutability")
    class Immutability {

        @Test
        @DisplayName("copies its lists and sets, so a later change of the source leaves it unchanged")
        void copies() {
            var preset = Generators.letterStrings(3, 12).next();
            var read = Generators.letterStrings(3, 12).next();
            var finding = Generators.letterStrings(3, 12).next();
            var otherPreset = Generators.letterStrings(3, 12).next() + "-p";
            var otherRead = Generators.letterStrings(3, 12).next() + "-r";
            var presets = new HashSet<>(Set.of(preset));
            var reads = new ArrayList<>(List.of(read));
            var findings = new ArrayList<>(List.of(finding));
            var descriptor = new FinalizeStepDescriptor("default:sonar-roundtrip", 40, true, presets,
                    Generators.letterStrings(1, 30).next(), false, false, null, List.of(), false, false,
                    reads, List.of(), List.of(), null, List.of(), List.of(), null, List.of(), null, findings);

            presets.add(otherPreset);
            reads.add(otherRead);
            findings.clear();

            assertAll(
                    () -> assertEquals(Set.of(preset), descriptor.presets(), "the presets are a copy"),
                    () -> assertEquals(List.of(read), descriptor.reads(), "the reads are a copy"),
                    () -> assertEquals(List.of(finding), descriptor.filesFindings(), "the finding sources are a copy"));
        }

        @Test
        @DisplayName("hands out collections that refuse a change")
        void unmodifiable() {
            var descriptor = FinalizeFixtures.full();
            var presets = descriptor.presets();
            var verdictInputs = descriptor.verdictInputs();
            var reads = descriptor.reads();
            var destroys = descriptor.destroys();
            var recordsFacts = descriptor.recordsFacts();
            var requires = descriptor.requires();
            var configurable = descriptor.configurable();
            var requiresPromptFields = descriptor.requiresPromptFields();
            var filesFindings = descriptor.filesFindings();
            var item = Generators.letterStrings(1, 5).next();

            assertAll(
                    () -> assertThrows(UnsupportedOperationException.class, () -> presets.add(item)),
                    () -> assertThrows(UnsupportedOperationException.class, () -> verdictInputs.add(item)),
                    () -> assertThrows(UnsupportedOperationException.class, () -> reads.add(item)),
                    () -> assertThrows(UnsupportedOperationException.class, () -> destroys.add(item)),
                    () -> assertThrows(UnsupportedOperationException.class, () -> recordsFacts.add(item)),
                    () -> assertThrows(UnsupportedOperationException.class, () -> requires.add(item)),
                    () -> assertThrows(UnsupportedOperationException.class, configurable::clear),
                    () -> assertThrows(UnsupportedOperationException.class, () -> requiresPromptFields.add(item)),
                    () -> assertThrows(UnsupportedOperationException.class, () -> filesFindings.add(item)));
        }

        @Test
        @DisplayName("keeps every component it was given")
        void keepsComponents() {
            var descriptor = FinalizeFixtures.full();

            // The full fixture's step id, order, preset, lane and configurable key are the contract read here.
            assertAll(
                    () -> assertEquals("default:automatic-review", descriptor.name(), "the name is kept"),
                    () -> assertEquals(30, descriptor.order(), "the order is kept"),
                    () -> assertEquals(InputScope.DECLARED, descriptor.inputScope(), "the input scope is kept"),
                    () -> assertEquals(new Lane(LaneClass.ADVERSARIAL, "standard", null, "M"), descriptor.lane(),
                            "the lane is kept"),
                    () -> assertEquals(List.of(new ConfigurableKey("required_bots", "coderabbit", "the bots")),
                            descriptor.configurable(), "the configurable keys are kept"),
                    () -> assertEquals("untested paths", descriptor.cannotReach(), "the unreachable defect is kept"));
        }

        @Test
        @DisplayName("carries the order constants of the pipeline bands")
        void orderConstants() {
            assertAll(
                    () -> assertEquals(10, FinalizeStepDescriptor.PRE_PUSH_QUALITY_GATE_ORDER),
                    () -> assertEquals(11, FinalizeStepDescriptor.PUSH_ORDER),
                    () -> assertEquals(70, FinalizeStepDescriptor.MERGE_GATE_ORDER),
                    () -> assertEquals(900, FinalizeStepDescriptor.POST_RUN_MIN_ORDER),
                    () -> assertEquals(1100, FinalizeStepDescriptor.TERMINUS_ARCHIVE_ORDER));
        }
    }

    @Nested
    @DisplayName("Absent values")
    class AbsentValues {

        @Test
        @DisplayName("accepts an absent input scope, lane, persona and unreachable defect as null")
        void absentComponents() {
            var descriptor = new FinalizeStepDescriptor(Generators.letterStrings(1, 10).next(), 1, true, Set.of(),
                    Generators.letterStrings(1, 20).next(), false, false, null, List.of(), false, false, List.of(),
                    List.of(), List.of(), null, List.of(), List.of(), null, List.of(), null, List.of());

            assertAll(
                    () -> assertNull(descriptor.inputScope(), "an absent input scope is null"),
                    () -> assertNull(descriptor.lane(), "an absent lane is null"),
                    () -> assertNull(descriptor.persona(), "an absent persona is null"),
                    () -> assertNull(descriptor.cannotReach(), "an absent unreachable defect is null"));
        }

        @Test
        @DisplayName("accepts a lane without a tier or a prune condition")
        void absentLaneComponents() {
            var lane = new Lane(LaneClass.CORE, null, null, Generators.letterStrings(1, 3).next());

            assertAll(
                    () -> assertNull(lane.tier(), "an absent tier is null"),
                    () -> assertNull(lane.prunableWhen(), "an absent prune condition is null"));
        }
    }

    @Nested
    @DisplayName("Null refusal")
    class NullRefusal {

        static Stream<Arguments> missingValues() {
            List<String> empty = List.of();
            Set<String> noPresets = Set.of();
            List<ConfigurableKey> noKeys = List.of();
            var name = Generators.letterStrings(1, 10).next();
            var description = Generators.letterStrings(1, 20).next();
            var key = Generators.letterStrings(1, 10).next();
            var value = Generators.letterStrings(1, 10).next();
            var costSize = Generators.letterStrings(1, 3).next();
            return Stream.of(
                    missing("name", () -> new FinalizeStepDescriptor(null, 1, true, noPresets, description, false,
                            false, null, empty, false, false, empty, empty, empty, null, empty, noKeys, null, empty,
                            null, empty)),
                    missing("presets", () -> new FinalizeStepDescriptor(name, 1, true, null, description, false, false,
                            null, empty, false, false, empty, empty, empty, null, empty, noKeys, null, empty, null,
                            empty)),
                    missing("description", () -> new FinalizeStepDescriptor(name, 1, true, noPresets, null, false,
                            false, null, empty, false, false, empty, empty, empty, null, empty, noKeys, null, empty,
                            null, empty)),
                    missing("verdictInputs", () -> new FinalizeStepDescriptor(name, 1, true, noPresets, description,
                            false, false, null, null, false, false, empty, empty, empty, null, empty, noKeys, null,
                            empty, null, empty)),
                    missing("reads", () -> new FinalizeStepDescriptor(name, 1, true, noPresets, description, false,
                            false, null, empty, false, false, null, empty, empty, null, empty, noKeys, null, empty,
                            null, empty)),
                    missing("destroys", () -> new FinalizeStepDescriptor(name, 1, true, noPresets, description, false,
                            false, null, empty, false, false, empty, null, empty, null, empty, noKeys, null, empty,
                            null, empty)),
                    missing("recordsFacts", () -> new FinalizeStepDescriptor(name, 1, true, noPresets, description,
                            false, false, null, empty, false, false, empty, empty, null, null, empty, noKeys, null,
                            empty, null, empty)),
                    missing("requires", () -> new FinalizeStepDescriptor(name, 1, true, noPresets, description, false,
                            false, null, empty, false, false, empty, empty, empty, null, null, noKeys, null, empty,
                            null, empty)),
                    missing("configurable", () -> new FinalizeStepDescriptor(name, 1, true, noPresets, description,
                            false, false, null, empty, false, false, empty, empty, empty, null, empty, null, null,
                            empty, null, empty)),
                    missing("requiresPromptFields", () -> new FinalizeStepDescriptor(name, 1, true, noPresets,
                            description, false, false, null, empty, false, false, empty, empty, empty, null, empty,
                            noKeys, null, null, null, empty)),
                    missing("filesFindings", () -> new FinalizeStepDescriptor(name, 1, true, noPresets, description,
                            false, false, null, empty, false, false, empty, empty, empty, null, empty, noKeys, null,
                            empty, null, null)),
                    missing("an element of reads", () -> new FinalizeStepDescriptor(name, 1, true, noPresets,
                            description, false, false, null, empty, false, false, nullElement(), empty, empty, null,
                            empty, noKeys, null, empty, null, empty)),
                    missing("lane class", () -> new Lane(null, null, null, costSize)),
                    missing("lane costSize", () -> new Lane(LaneClass.CORE, null, null, null)),
                    missing("configurable key", () -> new ConfigurableKey(null, value, description)),
                    missing("configurable default value", () -> new ConfigurableKey(key, null, description)),
                    missing("configurable description", () -> new ConfigurableKey(key, value, null)));
        }

        private static Arguments missing(String what, Executable construction) {
            return Arguments.of(what, construction);
        }

        private static List<String> nullElement() {
            var list = new ArrayList<String>();
            list.add(null);
            return list;
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("missingValues")
        @DisplayName("refuses a missing required value")
        void refuses(String what, Executable construction) {
            assertThrows(NullPointerException.class, construction, what);
        }
    }
}
