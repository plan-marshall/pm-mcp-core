/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.workflow.ast.WorkflowDefinition;

// Every datum in this class is a contract: the generated DSL shapes, the state and edge counts, the cycle counts and
// the time limit. The generated units are their own test input; no generator draws them.
@DisplayName("UnitGraph Cycle Enumeration Scale")
class UnitGraphCycleScaleTest {

    /**
     * The time one large unit's enumeration may take: a generous bound of this test, not a benchmark. It is stated here
     * because the guide cites it as the limit the enumeration is measured against.
     */
    private static final Duration LIMIT = Duration.ofSeconds(15);

    /** The number of diamonds and of remediation sites of the small families, which are compared with the oracle. */
    private static final int SMALL_DIAMONDS = 5;

    /** The number of diamonds of the large family-(a) chain: 2^16 simple cycles. */
    private static final int LARGE_DIAMONDS = 16;

    /** The number of remediation call sites of the large family-(b) unit. */
    private static final int LARGE_SITES = 200;

    /** The number of branch-and-rejoin sections of the large family-(c) unit. */
    private static final int LARGE_SECTIONS = 100;

    /** The number of branch-and-rejoin sections of the family-(c) unit still compared with the legacy result. */
    private static final int SMALL_SECTIONS = 10;

    /** The classpath directory of the twelve default fixture units. */
    private static final String FIXTURES = "unit-fixtures/defaults";

    @Test
    @DisplayName("Every default fixture enumerates exactly as the legacy implementation does")
    void fixturesMatchLegacy() {
        var fixtures = VerifyFixtures.list(FIXTURES);
        assertEquals(12, fixtures.size(), "twelve default fixtures are read");

        for (var fixture : fixtures) {
            var unit = VerifyFixtures.parse(fixture);
            var name = fixture.substring(fixture.lastIndexOf('/') + 1);

            var expected = legacyFindSimpleCycles(unit);

            assertEquals(expected, UnitGraph.findSimpleCycles(unit), name + " matches the legacy order");
        }
    }

    @Test
    @DisplayName("A diamond chain of n up to five enumerates 2^n cycles in the legacy order")
    void diamondsMatchLegacy() {
        for (var diamonds = 1; diamonds <= SMALL_DIAMONDS; diamonds++) {
            var unit = diamonds(diamonds);

            var expected = legacyFindSimpleCycles(unit);
            var found = UnitGraph.findSimpleCycles(unit);

            assertEquals(expected, found, "the diamond chain agrees with the legacy order");
            assertEquals(1 << diamonds, found.size(), "each diamond doubles the cycle count");
            assertEquals(3 * diamonds + 1, unit.states().size(), "three states per diamond, one entry");
            assertEquals(4 * diamonds + 1, edgeCount(unit), "four edges per diamond, one entry");
        }
    }

    @Test
    @DisplayName("A phase of k remediation sites enumerates one cycle per site, in the legacy order")
    void phaseMatchesLegacy() {
        for (var sites = 1; sites <= SMALL_DIAMONDS; sites++) {
            var unit = phase(sites);

            var expected = legacyFindSimpleCycles(unit);
            var found = UnitGraph.findSimpleCycles(unit);

            assertEquals(expected, found, "the phase shape agrees with the legacy order");
            assertEquals(sites, found.size(), "every remediation site closes one cycle");
            assertEquals(sites + 3, unit.states().size(), "one state per site, gate and done");
            assertEquals(2 * sites + 3, edgeCount(unit), "two edges per site, gate and entry");
        }
    }

    @Test
    @DisplayName("A sprawling unit with few cycles enumerates exactly as the legacy implementation does")
    void sprawlingMatchesLegacy() {
        var unit = sprawling(SMALL_SECTIONS);

        var expected = legacyFindSimpleCycles(unit);
        var found = UnitGraph.findSimpleCycles(unit);

        assertEquals(expected, found, "the sprawling unit agrees with the legacy order");
        assertEquals(2, found.size(), "the sprawling unit carries exactly two cycles");
        assertEquals(3 * SMALL_SECTIONS + 5, unit.states().size(), "three states per section, five around");
        assertEquals(4 * SMALL_SECTIONS + 6, edgeCount(unit), "four edges per section, six around");
    }

    @Test
    @DisplayName("A unit whose only cycle is a state's own loop enumerates it once")
    void selfLoopMatchesLegacy() {
        var unit = parse("self-loop", """
                state scale.entry
                    auto test.proceed
                        again -> scale.entry
                        done -> scale.done

                state scale.done terminal done success
                """);

        var expected = legacyFindSimpleCycles(unit);
        var found = UnitGraph.findSimpleCycles(unit);

        assertEquals(expected, found, "the self loop agrees with the legacy result");
        assertEquals(List.of(List.of("scale.entry", "scale.entry")), found, "the self loop is one cycle");
        assertEquals(2, unit.states().size(), "the unit declares two states");
        assertEquals(2, edgeCount(unit), "the loop and the exit are two edges");
    }

    @Test
    @DisplayName("A 16-diamond chain enumerates its 65536 cycles in the legacy order, inside the limit")
    void largeDiamondChainStaysInsideTheLimit() {
        var unit = diamonds(LARGE_DIAMONDS);
        var expected = legacyFindSimpleCycles(unit);

        var found = assertTimeoutPreemptively(LIMIT, () -> UnitGraph.findSimpleCycles(unit),
                "the 65536-cycle enumeration stays inside the limit");

        assertEquals(expected, found, "the large chain agrees with the legacy order");
        assertEquals(1 << LARGE_DIAMONDS, found.size(), "the large chain enumerates 2^16 cycles");
        assertEquals(3 * LARGE_DIAMONDS + 1, unit.states().size(), "three states per diamond, one entry");
        assertEquals(4 * LARGE_DIAMONDS + 1, edgeCount(unit), "four edges per diamond, one entry");
    }

    @Test
    @DisplayName("A phase of 200 remediation sites enumerates 200 cycles in the legacy order")
    void largePhaseStaysInsideTheLimit() {
        var unit = phase(LARGE_SITES);
        var expected = legacyFindSimpleCycles(unit);

        var found = assertTimeoutPreemptively(LIMIT, () -> UnitGraph.findSimpleCycles(unit),
                "the 200-site enumeration stays inside the limit");

        assertEquals(expected, found, "the large phase agrees with the legacy order");
        assertEquals(LARGE_SITES, found.size(), "the large phase enumerates one cycle per site");
        assertEquals(LARGE_SITES + 3, unit.states().size(), "one state per site, gate and done");
        assertEquals(2 * LARGE_SITES + 3, edgeCount(unit), "two edges per site, gate and entry");
    }

    @Test
    @DisplayName("A 305-state unit with two cycles enumerates them inside the limit")
    void largeSprawlingUnitStaysInsideTheLimit() {
        var unit = sprawling(LARGE_SECTIONS);

        var found = assertTimeoutPreemptively(LIMIT, () -> UnitGraph.findSimpleCycles(unit),
                "the 305-state enumeration stays inside the limit");

        assertEquals(2, found.size(), "the large sprawling unit carries two cycles");
        assertEquals(3 * LARGE_SECTIONS + 5, unit.states().size(), "three states per section, five around");
        assertEquals(4 * LARGE_SECTIONS + 6, edgeCount(unit), "four edges per section, six around");
    }

    /**
     * @param definition the unit
     * @return the number of its successor edges
     */
    private static int edgeCount(WorkflowDefinition definition) {
        var graph = UnitGraph.of(definition);
        return definition.states().keySet().stream().mapToInt(stateId -> graph.successors(stateId).size()).sum();
    }

    /**
     * A unit of {@code count} diamonds in a chain, closed by one loop-back edge from the last pair of branches to the
     * first hub: one simple cycle per choice of a branch in every diamond, so {@code 2^count} of them.
     *
     * @param count the number of diamonds
     * @return the parsed unit
     */
    private static WorkflowDefinition diamonds(int count) {
        var states = new StringBuilder("state scale.entry\n    auto test.proceed\n        done -> scale.hub-0\n\n");
        for (var index = 0; index < count; index++) {
            var join = "scale.hub-" + (index + 1 == count ? 0 : index + 1);
            appendDiamond(states, index, join);
        }
        return parse("diamonds-" + count, states);
    }

    /**
     * A unit of the shape of a phase: one gate, {@code sites} remediation call sites in a chain, and every site's
     * remediation edge looping back to the gate, so one cycle per site.
     *
     * @param sites the number of remediation call sites
     * @return the parsed unit
     */
    private static WorkflowDefinition phase(int sites) {
        var states = new StringBuilder("state scale.entry\n    auto test.proceed\n        done -> scale.gate\n\n");
        states.append("state scale.gate\n    call remediation.qgate-findings\n")
                .append("        resolved -> scale.site-0\n")
                .append("        exhausted -> scale.done\n\n");
        for (var index = 0; index < sites; index++) {
            var next = index + 1 == sites ? "scale.done" : "scale.site-" + (index + 1);
            states.append("state scale.site-").append(index).append('\n')
                    .append("    call remediation.qgate-findings\n")
                    .append("        resolved -> ").append(next).append('\n')
                    .append("        fix_requested -> scale.gate\n\n");
        }
        states.append("state scale.done terminal done success\n");
        return parse("phase-" + sites, states);
    }

    /**
     * A unit of {@code sections} branch-and-rejoin sections that carry no cycle, followed by one gate with two cycles
     * through it: a few hundred states whose cycles are few.
     *
     * @param sections the number of branch-and-rejoin sections
     * @return the parsed unit
     */
    private static WorkflowDefinition sprawling(int sections) {
        var states = new StringBuilder("state scale.entry\n    auto test.proceed\n        done -> scale.hub-0\n\n");
        for (var index = 0; index < sections; index++) {
            var join = index + 1 == sections ? "scale.gate" : "scale.hub-" + (index + 1);
            appendDiamond(states, index, join);
        }
        states.append("state scale.gate\n    auto test.proceed\n")
                .append("        back -> scale.loop-a\n")
                .append("        done -> scale.done\n\n")
                .append("state scale.loop-a\n    auto test.proceed\n")
                .append("        again -> scale.gate\n")
                .append("        next -> scale.loop-b\n\n")
                .append("state scale.loop-b\n    call remediation.qgate-findings\n")
                .append("        done -> scale.gate\n\n")
                .append("state scale.done terminal done success\n");
        return parse("sprawling-" + sections, states);
    }

    /**
     * Appends one branch-and-rejoin section: a hub with two branches that both leave for {@code join}.
     *
     * @param states the unit text written so far
     * @param index  the number of the section
     * @param join   the state both branches leave for
     */
    private static void appendDiamond(StringBuilder states, int index, String join) {
        states.append("state scale.hub-").append(index).append('\n')
                .append("    auto test.proceed\n")
                .append("        left -> scale.left-").append(index).append('\n')
                .append("        right -> scale.right-").append(index).append("\n\n")
                .append("state scale.left-").append(index).append('\n')
                .append("    auto test.proceed\n")
                .append("        done -> ").append(join).append("\n\n")
                .append("state scale.right-").append(index).append('\n')
                .append("    auto test.proceed\n")
                .append("        done -> ").append(join).append("\n\n");
    }

    /**
     * Parses a generated unit body through the real DSL parser.
     *
     * @param name   the name the generated unit is filed under
     * @param states the states of the unit
     * @return the parsed unit
     */
    private static WorkflowDefinition parse(String name, CharSequence states) {
        return VerifyFixtures.parseSource("scale-" + name + ".pmwf",
                VerifyFixtures.frontmatter("plan") + """
                        workflow scale.%s version 1 format 1
                        scope plan
                        start scale.entry

                        %s""".formatted(name, states));
    }

    /**
     * The enumeration of the implementation this test was written against, kept as the oracle: the depth-first search
     * from every state that descends only into states of a larger index than the start state. A private copy, so the
     * production method may be narrowed to the strongly connected components while the expected result stays the one
     * the callers have always seen.
     *
     * @param definition the unit
     * @return the simple cycles in the order the search finds them
     */
    private static List<List<String>> legacyFindSimpleCycles(WorkflowDefinition definition) {
        var stateIds = new ArrayList<>(definition.states().keySet());
        var search = new LegacySearch(UnitGraph.of(definition), stateIds);
        for (var index = 0; index < stateIds.size(); index++) {
            var startId = stateIds.get(index);
            var path = new ArrayList<String>();
            path.add(startId);
            var inPath = new LinkedHashSet<String>();
            inPath.add(startId);
            search.descend(startId, index, path, inPath);
        }
        return search.cycles();
    }

    /** The state of one legacy depth-first search: the graph, the index of every state and the cycles found. */
    private static final class LegacySearch {

        private final UnitGraph graph;

        private final Map<String, Integer> stateIndex = new LinkedHashMap<>();

        private final List<List<String>> cycles = new ArrayList<>();

        private LegacySearch(UnitGraph graph, List<String> stateIds) {
            this.graph = graph;
            for (var index = 0; index < stateIds.size(); index++) {
                stateIndex.put(stateIds.get(index), index);
            }
        }

        private void descend(String current, int startIndex, List<String> path, Set<String> inPath) {
            var start = path.getFirst();
            for (var next : graph.successors(current)) {
                if (next.equals(start)) {
                    var cycle = new ArrayList<>(path);
                    cycle.add(start);
                    cycles.add(Collections.unmodifiableList(cycle));
                } else {
                    var nextIndex = stateIndex.get(next);
                    if (nextIndex != null && nextIndex > startIndex && inPath.add(next)) {
                        path.add(next);
                        descend(next, startIndex, path, inPath);
                        path.removeLast();
                        inPath.remove(next);
                    }
                }
            }
        }

        private List<List<String>> cycles() {
            return cycles;
        }
    }
}
