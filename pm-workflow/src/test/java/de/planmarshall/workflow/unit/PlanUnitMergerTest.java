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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.workflow.ast.UnitOrigin;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.ast.WorkflowScope;
import de.planmarshall.workflow.dsl.ParseResult;
import de.planmarshall.workflow.dsl.WorkflowParser;
import de.planmarshall.workflow.verify.InvariantFixtures;
import de.planmarshall.workflow.verify.VerificationRule;

/**
 * The merge of the seven phase files into the plan unit (PM-IMPL-5): the states and migrations are the union, the
 * start state is that of the init file, the version is the sum of the part versions, each recorded under its file,
 * and {@code derivedFrom} is recorded per file. The header of a part names the part and identifies the file only. A
 * part of another scope, a part of another format or origin, a state declared in two files and a {@code declare},
 * {@code decide} or {@code instance} line in a part are each refused with the file and line of the offending node, and
 * the merge then yields no unit.
 */
@DisplayName("PlanUnitMerger")
class PlanUnitMergerTest {

    private static final String INIT_FILE = "init.pmwf";
    private static final String REFINE_FILE = "refine.pmwf";

    /** The header JSON of a plan instance version; a part of the plan unit never carries it. */
    private static final String INSTANCE_JSON = """
            {"plan_id": "p", "version": 1, "event": "plan", "inputs": {"config_hash": "c", \
            "templates": [{"unit": "plan", "digest": "t", "origin": "default"}], "sub_units": [], \
            "composition_rules": {"revision": "r", "digest": "d"}, "plan_facts_digest": "f"}}""";

    /** The refine file declares a value: the merged unit carries no declarations. */
    private static final UnitFixtures.Mutation DECLARE_LINE = new UnitFixtures.Mutation("a declare line in a part",
            REFINE_FILE, "scope plan\n", "scope plan\ndeclare rounds = 2\n");

    /** The refine file declares a decision: the merged unit carries no decisions. */
    private static final UnitFixtures.Mutation DECIDE_LINE = new UnitFixtures.Mutation("a decide line in a part",
            REFINE_FILE, "scope plan\n", "scope plan\ndecide interpretation -> ask\n");

    /** The refine file carries an instance header: the merged unit carries none. */
    private static final UnitFixtures.Mutation INSTANCE_LINE = new UnitFixtures.Mutation(
            "an instance line in a part", REFINE_FILE, UnitFixtures.PLAN_HEADER,
            UnitFixtures.PLAN_HEADER + "\ninstance " + INSTANCE_JSON);

    private static WorkflowDefinition parse(String file, String source, UnitOrigin origin) {
        var result = WorkflowParser.parse(file, source, origin);
        return assertInstanceOf(ParseResult.Parsed.class, result, () -> file + " was refused: " + result).definition();
    }

    private static WorkflowDefinition parse(String file, UnitOrigin origin) {
        return parse(file, InvariantFixtures.read(file), origin);
    }

    /**
     * @param file    a phase file
     * @param header  the header that replaces {@link UnitFixtures#PLAN_HEADER}
     * @param origin  the origin the file is parsed with
     * @return the phase file with the changed header
     */
    private static WorkflowDefinition withHeader(String file, String header, UnitOrigin origin) {
        return parse(file, new UnitFixtures.Mutation(header, file, UnitFixtures.PLAN_HEADER, header)
                .apply(InvariantFixtures.read(file)), origin);
    }

    /**
     * @param mutation a change of a phase file
     * @return the project init file and the changed project phase file
     */
    private static List<WorkflowDefinition> initAnd(UnitFixtures.Mutation mutation) {
        return List.of(parse(INIT_FILE, UnitOrigin.PROJECT), parse(mutation.file(),
                mutation.apply(InvariantFixtures.read(mutation.file())), UnitOrigin.PROJECT));
    }

    private static List<WorkflowDefinition> phases(UnitOrigin origin) {
        return InvariantFixtures.PLAN_FILES.stream().map(file -> parse(file, origin)).toList();
    }

    private static int headerLine(String file) {
        return UnitFixtures.lineOf(file, UnitFixtures.PLAN_HEADER);
    }

    @Nested
    @DisplayName("Merged unit")
    class MergedUnit {

        @Test
        @DisplayName("holds the union of the states and the start state, header and origin of the init file")
        void union() {
            var parts = phases(UnitOrigin.DEFAULT);
            var expectedStates = new ArrayList<String>();
            parts.forEach(part -> expectedStates.addAll(part.states().keySet()));

            var plan = PlanUnitMerger.merge(parts).unit().orElseThrow();

            assertAll(
                    () -> assertEquals(PlanUnitMerger.PLAN_UNIT, plan.id()),
                    () -> assertEquals(WorkflowScope.PLAN, plan.scope()),
                    () -> assertEquals(expectedStates, List.copyOf(plan.states().keySet())),
                    () -> assertEquals(parts.getFirst().startState(), plan.startState()),
                    () -> assertEquals(parts.getFirst().location(), plan.location()),
                    () -> assertEquals(UnitOrigin.DEFAULT, plan.origin()),
                    () -> assertEquals(parts.stream().mapToInt(part -> part.migrations().size()).sum(),
                            plan.migrations().size()));
        }

        @Test
        @DisplayName("sums the part versions and records each under its file, in merge order")
        void versions() {
            var parts = new ArrayList<>(phases(UnitOrigin.DEFAULT));
            parts.set(1, withHeader(REFINE_FILE, "workflow plan version 4 format 1", UnitOrigin.DEFAULT));
            var expected = new LinkedHashMap<String, Integer>();
            InvariantFixtures.PLAN_FILES.forEach(file -> expected.put(file, REFINE_FILE.equals(file) ? 4 : 1));

            var plan = PlanUnitMerger.merge(parts).unit().orElseThrow();

            assertAll(
                    () -> assertEquals(expected, plan.partVersions()),
                    () -> assertEquals(List.copyOf(expected.keySet()), List.copyOf(plan.partVersions().keySet())),
                    () -> assertEquals(InvariantFixtures.PLAN_FILES.size() + 3, plan.version()));
        }

        @Test
        @DisplayName("records the derived_from revision of every project file under the file")
        void derivedFromPerFile() {
            var parts = new ArrayList<>(phases(UnitOrigin.PROJECT));
            parts.set(0, withHeader(INIT_FILE, UnitFixtures.PLAN_HEADER + " derived_from 2", UnitOrigin.PROJECT));
            parts.set(1, withHeader(REFINE_FILE, UnitFixtures.PLAN_HEADER + " derived_from 3", UnitOrigin.PROJECT));

            var plan = PlanUnitMerger.merge(parts).unit().orElseThrow();

            assertAll(
                    () -> assertEquals(Map.of(INIT_FILE, 2, REFINE_FILE, 3), plan.derivedFrom()),
                    () -> assertEquals(UnitOrigin.PROJECT, plan.origin()));
        }

        @Test
        @DisplayName("records no derivedFrom for default files")
        void noDerivedFromForDefaults() {
            var plan = PlanUnitMerger.merge(phases(UnitOrigin.DEFAULT)).unit().orElseThrow();

            assertEquals(Map.of(), plan.derivedFrom());
        }

        @Test
        @DisplayName("merges parts whose headers name the phase into the unit plan")
        void partHeadersNameThePhase() {
            var parts = new ArrayList<>(phases(UnitOrigin.PROJECT));
            parts.set(0, withHeader(INIT_FILE, "workflow init version 1 format 1", UnitOrigin.PROJECT));
            parts.set(1, withHeader(REFINE_FILE, "workflow refine version 1 format 1", UnitOrigin.PROJECT));

            var merge = PlanUnitMerger.merge(parts);

            assertAll(
                    () -> assertEquals(List.of(), merge.findings()),
                    () -> assertEquals(PlanUnitMerger.PLAN_UNIT, merge.unit().orElseThrow().id()));
        }

        @Test
        @DisplayName("merges without a finding")
        void noFinding() {
            var merge = PlanUnitMerger.merge(phases(UnitOrigin.PROJECT));

            assertAll(
                    () -> assertTrue(merge.unit().isPresent()),
                    () -> assertEquals(List.of(), merge.findings()));
        }
    }

    @Nested
    @DisplayName("Refused merge")
    class RefusedMerge {

        static Stream<Arguments> refusals() {
            return Stream.of(
                    refusal("a state declared in two files", UnitFinding.Kind.RULE,
                            VerificationRule.STATE_ID_UNIQUE.name(), "copy.pmwf",
                            UnitFixtures.lineOf(INIT_FILE, "state init.begin"),
                            () -> List.of(parse(INIT_FILE, UnitOrigin.PROJECT),
                                    parse("copy.pmwf", InvariantFixtures.read(INIT_FILE), UnitOrigin.PROJECT))),
                    refusal("a part of another DSL format", UnitFinding.Kind.FORMAT, PlanUnitMerger.MIXED_FORMATS,
                            REFINE_FILE, headerLine(REFINE_FILE),
                            () -> List.of(parse(INIT_FILE, UnitOrigin.PROJECT),
                                    withHeader(REFINE_FILE, "workflow plan version 1 format 2", UnitOrigin.PROJECT))),
                    refusal("a sub-workflow among the parts", UnitFinding.Kind.RULE, PlanUnitMerger.NOT_A_PLAN_PART,
                            InvariantFixtures.REPLAN_FILE,
                            UnitFixtures.lineOf(InvariantFixtures.REPLAN_FILE, UnitFixtures.REPLAN_HEADER),
                            () -> List.of(parse(INIT_FILE, UnitOrigin.PROJECT),
                                    parse(InvariantFixtures.REPLAN_FILE, UnitOrigin.PROJECT))),
                    refusal("a part of another scope", UnitFinding.Kind.RULE, PlanUnitMerger.NOT_A_PLAN_PART,
                            REFINE_FILE, headerLine(REFINE_FILE),
                            () -> List.of(parse(INIT_FILE, UnitOrigin.PROJECT), parse(REFINE_FILE,
                                    new UnitFixtures.Mutation("another scope", REFINE_FILE, "scope plan",
                                            "scope subworkflow").apply(InvariantFixtures.read(REFINE_FILE)),
                                    UnitOrigin.PROJECT))),
                    refusal("a default file mixed into project files", UnitFinding.Kind.RULE,
                            PlanUnitMerger.MIXED_ORIGINS, REFINE_FILE, headerLine(REFINE_FILE),
                            () -> List.of(parse(INIT_FILE, UnitOrigin.PROJECT), parse(REFINE_FILE, UnitOrigin.DEFAULT))),
                    refusal(DECLARE_LINE.name(), UnitFinding.Kind.RULE, PlanUnitMerger.DECLARATION_IN_PLAN_PART,
                            REFINE_FILE, DECLARE_LINE.lineOf("declare rounds = 2"), () -> initAnd(DECLARE_LINE)),
                    refusal(DECIDE_LINE.name(), UnitFinding.Kind.RULE, PlanUnitMerger.DECLARATION_IN_PLAN_PART,
                            REFINE_FILE, DECIDE_LINE.lineOf("decide interpretation"), () -> initAnd(DECIDE_LINE)),
                    refusal(INSTANCE_LINE.name(), UnitFinding.Kind.RULE, PlanUnitMerger.DECLARATION_IN_PLAN_PART,
                            REFINE_FILE, headerLine(REFINE_FILE), () -> initAnd(INSTANCE_LINE)));
        }

        private static Arguments refusal(String name, UnitFinding.Kind kind, String code, String file, int line,
                Supplier<List<WorkflowDefinition>> parts) {
            return Arguments.of(name, kind, code, file, line, parts);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("refusals")
        @DisplayName("refuses the merge with the code, the file and the line, and yields no unit")
        void refuses(String name, UnitFinding.Kind kind, String code, String file, int line,
                Supplier<List<WorkflowDefinition>> parts) {
            var merge = PlanUnitMerger.merge(parts.get());

            var finding = UnitFixtures.find(merge.findings(), kind, code);
            assertAll(name,
                    () -> assertEquals(Optional.empty(), merge.unit()),
                    () -> assertTrue(finding.isPresent(), merge.findings()::toString),
                    () -> assertEquals(file, finding.orElseThrow().file()),
                    () -> assertEquals(line, finding.orElseThrow().line()));
        }

        @Test
        @DisplayName("merges the same parts when the phase file carries no declare, decide or instance line")
        void partWithoutDeclarations() {
            var merge = PlanUnitMerger.merge(List.of(parse(INIT_FILE, UnitOrigin.PROJECT),
                    parse(REFINE_FILE, UnitOrigin.PROJECT)));

            assertAll(
                    () -> assertEquals(List.of(), merge.findings()),
                    () -> assertTrue(merge.unit().isPresent()));
        }

        @Test
        @DisplayName("reports every finding instead of stopping at the first")
        void everyFinding() {
            var init = parse(INIT_FILE, UnitOrigin.PROJECT);

            var merge = PlanUnitMerger.merge(List.of(init, parse("copy.pmwf", InvariantFixtures.read(INIT_FILE),
                    UnitOrigin.DEFAULT)));

            assertAll(
                    () -> assertEquals(init.states().size() + 1, merge.findings().size(), merge.findings()::toString),
                    () -> assertTrue(UnitFixtures.find(merge.findings(), UnitFinding.Kind.RULE,
                            PlanUnitMerger.MIXED_ORIGINS).isPresent()));
        }
    }

    @Test
    @DisplayName("refuses an empty or null list of parts")
    void refusesNoParts() {
        var parts = new ArrayList<WorkflowDefinition>();
        parts.add(null);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> PlanUnitMerger.merge(List.of())),
                () -> assertThrows(NullPointerException.class, () -> PlanUnitMerger.merge(null)),
                () -> assertThrows(NullPointerException.class, () -> PlanUnitMerger.merge(parts)));
    }

    @Test
    @DisplayName("names the seven phase files in merge order")
    void phaseFiles() {
        assertEquals(InvariantFixtures.PLAN_FILES, PlanUnitMerger.PHASE_FILES);
    }
}
