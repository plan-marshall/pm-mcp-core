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

import static de.planmarshall.workflow.plan.PlanFixtures.REMEDIATION_SITE;
import static de.planmarshall.workflow.plan.PlanFixtures.composer;
import static de.planmarshall.workflow.plan.PlanFixtures.facts;
import static de.planmarshall.workflow.plan.PlanFixtures.request;
import static de.planmarshall.workflow.plan.PlanFixtures.start;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.planmarshall.workflow.ast.CallValue;
import de.planmarshall.workflow.plan.DiffSummary.CeilingChange;
import de.planmarshall.workflow.plan.PrunedElement.Kind;

/**
 * The {@code diff_summary} of the composition log (PM-IMPL-5, PM-WF-1): the five flat arrays of a composed version
 * against the version before it, computed from two headers the composer composed over the fixtures of
 * {@link PlanFixtures}, and their canonical JSON.
 */
@DisplayName("DiffSummary")
class DiffSummaryTest {

    private static final InstancePosition AT_START = InstancePosition.at("finalize.push-barrier");

    /** The steps the shipped rule {@code local-only} prunes from the fixture's step list. */
    private static final List<String> REMOTE_STEPS = List.of("default:push", "default:create-pr",
            "default:ci-verify", "default:automatic-review", "default:analysis-roundtrip");

    private static PlanInstance composed(CompositionResult result) {
        return assertInstanceOf(CompositionResult.Composed.class, result, result::toString).instance();
    }

    private static PlanInstance version1() {
        return composed(composer().compose(start(facts().build())));
    }

    private static PlanInstance waiverVersion(List<Waiver> waivers) {
        return composed(composer().compose(request(CompositionEvent.WAIVER, facts().build(),
                Optional.of(version1()), waivers, AT_START)));
    }

    private static PlanInstance localOnlyVersion() {
        return composed(composer().compose(request(CompositionEvent.CONFIG_ADOPTED,
                facts().configuration("derived.push_remote", "").build(), Optional.of(version1()), List.of(),
                AT_START)));
    }

    private static PrunedElement localOnly(String step) {
        return new PrunedElement(Kind.STEP, step, PruneReason.LOCAL_ONLY, "derived.push_remote");
    }

    @Nested
    @DisplayName("Arrays")
    class Arrays {

        @Test
        @DisplayName("the prunes a version adds are pruned_added, and pruned_removed when the order is turned")
        void prunes() {
            var before = version1().header();
            var after = localOnlyVersion().header();
            var expected = REMOTE_STEPS.stream().map(DiffSummaryTest::localOnly).toList();

            var added = DiffSummary.between(before, after);
            var removed = DiffSummary.between(after, before);

            assertAll(
                    () -> assertEquals(expected, added.prunedAdded()),
                    () -> assertEquals(List.of(), added.prunedRemoved()),
                    () -> assertEquals(List.of(), removed.prunedAdded()),
                    () -> assertEquals(expected, removed.prunedRemoved()),
                    () -> assertEquals(List.of(), added.ceilingsChanged()),
                    () -> assertEquals(List.of(), added.gatesWaived()));
        }

        @Test
        @DisplayName("a raise changes the ceiling of its policy call from the previous value to the raised one")
        void raise() {
            var before = version1().header();
            var after = waiverVersion(List.of(Waiver.raise("W-1", "fix_budget:qgate-findings", 4))).header();

            var summary = DiffSummary.between(before, after);

            assertAll(
                    () -> assertEquals(List.of(new CeilingChange(REMEDIATION_SITE, "fix_budget",
                                    Optional.of(new CallValue.IntValue(3)), Optional.of(new CallValue.IntValue(4)))),
                            summary.ceilingsChanged()),
                    () -> assertEquals(List.of(), summary.prunedAdded()),
                    () -> assertEquals(List.of(), summary.gatesWaived()));
        }

        @Test
        @DisplayName("a met waiver waives its gate, and unwaives it when the order is turned")
        void gates() {
            var before = version1().header();
            var after = waiverVersion(List.of(Waiver.met("W-2", "ci_check:build"))).header();
            var gate = new WaivedGate("ci_check:build", "W-2");

            var waived = DiffSummary.between(before, after);
            var unwaived = DiffSummary.between(after, before);

            assertAll(
                    () -> assertEquals(List.of(gate), waived.gatesWaived()),
                    () -> assertEquals(List.of(), waived.gatesUnwaived()),
                    () -> assertEquals(List.of(), unwaived.gatesWaived()),
                    () -> assertEquals(List.of(gate), unwaived.gatesUnwaived()));
        }

        @Test
        @DisplayName("two versions that differ in none of the arrays give five empty arrays")
        void identical() {
            var header = version1().header();

            var summary = DiffSummary.between(header, waiverVersion(List.of()).header());

            assertEquals(new DiffSummary(List.of(), List.of(), List.of(), List.of(), List.of()), summary);
        }

        @Test
        @DisplayName("a pruned element whose reason changed is removed and added")
        void changedReason() {
            var before = version1().header();
            var step = new PrunedElement(Kind.STEP, "default:push", PruneReason.LOCAL_ONLY, "derived.push_remote");
            var otherReason = new PrunedElement(Kind.STEP, "default:push", PruneReason.RETURNED, "post_run");
            var withStep = header(before, List.of(step));
            var withOtherReason = header(before, List.of(otherReason));

            var summary = DiffSummary.between(withStep, withOtherReason);

            assertAll(
                    () -> assertEquals(List.of(otherReason), summary.prunedAdded()),
                    () -> assertEquals(List.of(step), summary.prunedRemoved()));
        }

        @Test
        @DisplayName("a policy call present in one version only changes every argument it binds, the absent side "
                + "empty")
        void policyCallInOneVersionOnly() {
            var with = version1().header();
            var without = new PlanInstanceHeader(with.planId(), with.version(), with.event(), with.composedAt(),
                    with.inputs(), with.lane(), with.posture(), with.steps(), with.pruned(), List.of(),
                    with.waivedGates());
            var expectedAdded = with.policyCalls().stream()
                    .flatMap(call -> call.args().entrySet().stream()
                            .map(argument -> new CeilingChange(call.callSite(), argument.getKey(), Optional.empty(),
                                    Optional.of(argument.getValue().value()))))
                    .toList();
            var expectedRemoved = with.policyCalls().stream()
                    .flatMap(call -> call.args().entrySet().stream()
                            .map(argument -> new CeilingChange(call.callSite(), argument.getKey(),
                                    Optional.of(argument.getValue().value()), Optional.empty())))
                    .toList();

            var added = DiffSummary.between(without, with);
            var removed = DiffSummary.between(with, without);

            assertAll(
                    () -> assertTrue(with.policyCalls().stream().anyMatch(call -> REMEDIATION_SITE.equals(call.callSite())),
                            "the fixture version binds the policy call of the remediation site"),
                    () -> assertFalse(expectedAdded.isEmpty(), "the fixture policy calls bind arguments"),
                    () -> assertEquals(expectedAdded, added.ceilingsChanged()),
                    () -> assertEquals(expectedRemoved, removed.ceilingsChanged()),
                    () -> assertEquals(List.of(), added.prunedAdded()),
                    () -> assertEquals(List.of(), removed.gatesWaived()));
        }

        private static PlanInstanceHeader header(PlanInstanceHeader template, List<PrunedElement> pruned) {
            return new PlanInstanceHeader(template.planId(), template.version(), template.event(),
                    template.composedAt(), template.inputs(), template.lane(), template.posture(), template.steps(),
                    pruned, template.policyCalls(), template.waivedGates());
        }
    }

    @Nested
    @DisplayName("JSON")
    class Json {

        @Test
        @DisplayName("the summary is written with its five arrays, a ceiling with its call site, argument and values")
        void canonical() {
            var summary = new DiffSummary(List.of(localOnly("default:push")), List.of(),
                    List.of(new CeilingChange(REMEDIATION_SITE, "fix_budget", Optional.of(new CallValue.IntValue(3)),
                            Optional.empty())), List.of(new WaivedGate("ci_check:build", "W-2")), List.of());

            var json = HeaderJson.canonical(summary.toNode());

            assertEquals("{\"ceilings_changed\":[{\"argument\":\"fix_budget\",\"call_site\":\"finalize.remediate\","
                    + "\"previous\":3,\"value\":null}],\"gates_unwaived\":[],\"gates_waived\":[{\"gate\":"
                    + "\"ci_check:build\",\"waiver_id\":\"W-2\"}],\"pruned_added\":[{\"element\":\"step\",\"id\":"
                    + "\"default:push\",\"reason\":\"local_only\",\"source_fact\":\"derived.push_remote\"}],"
                    + "\"pruned_removed\":[]}", json);
        }

        @Test
        @DisplayName("a ceiling that changes nothing is refused")
        void unchangedCeiling() {
            var three = Optional.<CallValue>of(new CallValue.IntValue(3));

            var refused = assertThrows(IllegalArgumentException.class,
                    () -> new CeilingChange(REMEDIATION_SITE, "fix_budget", three, three));

            assertTrue(refused.getMessage().contains("fix_budget"), refused.getMessage());
        }
    }
}
