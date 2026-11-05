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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
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
 * The descriptor of a finalize step (PM-IMPL-5) is immutable: it copies its lists and sets, and it refuses a missing
 * value in itself and in its nested types.
 */
@EnableGeneratorController
@DisplayName("FinalizeStepDescriptor")
class FinalizeStepDescriptorTest {

    private static final Optional<String> NONE = Optional.empty();

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
                    Generators.letterStrings(1, 30).next(), false, false, Optional.empty(), List.of(), false, false,
                    reads, List.of(), List.of(), Optional.empty(), List.of(), List.of(), NONE, List.of(), NONE,
                    findings);

            presets.add(otherPreset);
            reads.add(otherRead);
            findings.clear();

            assertAll(
                    () -> assertEquals(Set.of(preset), descriptor.presets()),
                    () -> assertEquals(List.of(read), descriptor.reads()),
                    () -> assertEquals(List.of(finding), descriptor.filesFindings()));
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
                    () -> assertEquals("default:automatic-review", descriptor.name()),
                    () -> assertEquals(30, descriptor.order()),
                    () -> assertEquals(Optional.of(InputScope.DECLARED), descriptor.inputScope()),
                    () -> assertEquals(Optional.of(new Lane(LaneClass.ADVERSARIAL, Optional.of("standard"),
                            Optional.empty(), "M")), descriptor.lane()),
                    () -> assertEquals(List.of(new ConfigurableKey("required_bots", "coderabbit", "the bots")),
                            descriptor.configurable()),
                    () -> assertEquals(Optional.of("untested paths"), descriptor.cannotReach()));
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
    @DisplayName("Null refusal")
    class NullRefusal {

        static Stream<Arguments> missingValues() {
            var scope = Optional.<InputScope>empty();
            var lane = Optional.<Lane>empty();
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
                            false, scope, empty, false, false, empty, empty, empty, lane, empty, noKeys, NONE, empty,
                            NONE, empty)),
                    missing("presets", () -> new FinalizeStepDescriptor(name, 1, true, null, description, false, false,
                            scope, empty, false, false, empty, empty, empty, lane, empty, noKeys, NONE, empty, NONE,
                            empty)),
                    missing("description", () -> new FinalizeStepDescriptor(name, 1, true, noPresets, null, false,
                            false, scope, empty, false, false, empty, empty, empty, lane, empty, noKeys, NONE, empty,
                            NONE, empty)),
                    missing("inputScope", () -> new FinalizeStepDescriptor(name, 1, true, noPresets, description, false,
                            false, null, empty, false, false, empty, empty, empty, lane, empty, noKeys, NONE, empty,
                            NONE, empty)),
                    missing("verdictInputs", () -> new FinalizeStepDescriptor(name, 1, true, noPresets, description,
                            false, false, scope, null, false, false, empty, empty, empty, lane, empty, noKeys, NONE,
                            empty, NONE, empty)),
                    missing("reads", () -> new FinalizeStepDescriptor(name, 1, true, noPresets, description, false,
                            false, scope, empty, false, false, null, empty, empty, lane, empty, noKeys, NONE, empty,
                            NONE, empty)),
                    missing("destroys", () -> new FinalizeStepDescriptor(name, 1, true, noPresets, description, false,
                            false, scope, empty, false, false, empty, null, empty, lane, empty, noKeys, NONE, empty,
                            NONE, empty)),
                    missing("recordsFacts", () -> new FinalizeStepDescriptor(name, 1, true, noPresets, description,
                            false, false, scope, empty, false, false, empty, empty, null, lane, empty, noKeys, NONE,
                            empty, NONE, empty)),
                    missing("lane", () -> new FinalizeStepDescriptor(name, 1, true, noPresets, description, false,
                            false, scope, empty, false, false, empty, empty, empty, null, empty, noKeys, NONE, empty,
                            NONE, empty)),
                    missing("requires", () -> new FinalizeStepDescriptor(name, 1, true, noPresets, description, false,
                            false, scope, empty, false, false, empty, empty, empty, lane, null, noKeys, NONE, empty,
                            NONE, empty)),
                    missing("configurable", () -> new FinalizeStepDescriptor(name, 1, true, noPresets, description,
                            false, false, scope, empty, false, false, empty, empty, empty, lane, empty, null, NONE,
                            empty, NONE, empty)),
                    missing("persona", () -> new FinalizeStepDescriptor(name, 1, true, noPresets, description, false,
                            false, scope, empty, false, false, empty, empty, empty, lane, empty, noKeys, null, empty,
                            NONE, empty)),
                    missing("requiresPromptFields", () -> new FinalizeStepDescriptor(name, 1, true, noPresets,
                            description, false, false, scope, empty, false, false, empty, empty, empty, lane, empty,
                            noKeys, NONE, null, NONE, empty)),
                    missing("cannotReach", () -> new FinalizeStepDescriptor(name, 1, true, noPresets, description,
                            false, false, scope, empty, false, false, empty, empty, empty, lane, empty, noKeys, NONE,
                            empty, null, empty)),
                    missing("filesFindings", () -> new FinalizeStepDescriptor(name, 1, true, noPresets, description,
                            false, false, scope, empty, false, false, empty, empty, empty, lane, empty, noKeys, NONE,
                            empty, NONE, null)),
                    missing("an element of reads", () -> new FinalizeStepDescriptor(name, 1, true, noPresets,
                            description, false, false, scope, empty, false, false, nullElement(), empty, empty, lane,
                            empty, noKeys, NONE, empty, NONE, empty)),
                    missing("lane class", () -> new Lane(null, NONE, NONE, costSize)),
                    missing("lane tier", () -> new Lane(LaneClass.CORE, null, NONE, costSize)),
                    missing("lane prunableWhen", () -> new Lane(LaneClass.CORE, NONE, null, costSize)),
                    missing("lane costSize", () -> new Lane(LaneClass.CORE, NONE, NONE, null)),
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
        @DisplayName("refuses a missing value")
        void refuses(String what, Executable construction) {
            assertThrows(NullPointerException.class, construction, what);
        }
    }
}
