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

import static de.planmarshall.workflow.verify.InvariantFixtures.ADJUDICATION_FILE;
import static de.planmarshall.workflow.verify.InvariantFixtures.GIT_FILE;
import static de.planmarshall.workflow.verify.InvariantFixtures.PLAN_UNIT;
import static de.planmarshall.workflow.verify.InvariantFixtures.REMEDIATION_FILE;
import static de.planmarshall.workflow.verify.InvariantFixtures.REPLAN_FILE;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.workflow.ast.ParamDecl;
import de.planmarshall.workflow.ast.UnitOrigin;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.finalize.FinalizePipelineValidator;
import de.planmarshall.workflow.finalize.FinalizeStepDescriptor;
import de.planmarshall.workflow.finalize.FinalizeStepDescriptor.LaneClass;
import de.planmarshall.workflow.finalize.PipelineViolation;
import de.planmarshall.workflow.verify.InvariantFixtures.Mutation;

/**
 * The compiled invariants of the project-owned units (PM-IMPL-5, PM-WF-16), over the units of
 * {@code unit-fixtures/defaults/}, which are written for the tests and never copied from the shipped content.
 * <p>
 * Every passing unit passes the static verifier and every invariant against itself as default unit. Every invariant
 * has one refusing case: the passing source with one named mutation, refused with the invariant at the file and line
 * of the offending node. For the invariants with a named hook the refusing case breaks the tree-checkable part.
 */
@DisplayName("CompiledInvariants")
class CompiledInvariantsTest {

    private static final String INIT_FILE = "init.pmwf";
    private static final String REFINE_FILE = "refine.pmwf";
    private static final String OUTLINE_FILE = "outline.pmwf";
    private static final String PLAN_FILE = "plan.pmwf";
    private static final String EXECUTE_FILE = "execute.pmwf";
    private static final String FINALIZE_FILE = "finalize.pmwf";

    /** The header of the merged plan unit, where a violation of the unit as a whole is located. */
    private static final String PLAN_HEADER = "workflow plan version 1 format 1";

    private static final Map<InvariantId, RefusingCase> CASES = refusingCases();

    private static List<PipelineViolation> pipelineOf(WorkflowDefinition unit, List<FinalizeStepDescriptor> roster) {
        return PLAN_UNIT.equals(unit.id()) ? FinalizePipelineValidator.validate(roster) : List.of();
    }

    @Nested
    @DisplayName("Passing units")
    class PassingUnits {

        static List<String> unitIds() {
            return List.copyOf(InvariantFixtures.defaultUnits().keySet());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("unitIds")
        @DisplayName("every passing unit passes the static verifier, Rules 1-12")
        void passesVerifier(String unitId) {
            var unit = InvariantFixtures.defaultUnits().get(unitId);

            var result = InvariantFixtures.validator().verify(unit);

            assertEquals(List.of(), result.violations(), () -> unitId + " " + result.violations());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("unitIds")
        @DisplayName("every passing unit, read as a project unit, satisfies every invariant against its default unit")
        void passesInvariants(String unitId) {
            var unit = InvariantFixtures.units(InvariantFixtures.sources(), UnitOrigin.PROJECT).get(unitId);
            var rules = InvariantFixtures.validator().verify(unit);

            var found = InvariantFixtures.invariants().check(unit, rules,
                    pipelineOf(unit, InvariantFixtures.defaultRoster()));

            assertEquals(List.of(), found);
        }

        @Test
        @DisplayName("the default roster of the tests passes the finalize pipeline validator")
        void defaultRosterPasses() {
            assertEquals(List.of(), FinalizePipelineValidator.validate(InvariantFixtures.defaultRoster()));
        }

        @Test
        @DisplayName("the passing units are the merged plan unit and the four project-ownable sub-workflows")
        void unitSet() {
            assertEquals(List.of(PLAN_UNIT, "adjudicate.decisions", "replan", "git.commit_and_push",
                    "remediation.qgate-findings"), unitIds());
        }

        @Test
        @DisplayName("the merge records the version of every phase file and their sum")
        void mergedPlanUnit() {
            var plan = InvariantFixtures.defaultUnits().get(PLAN_UNIT);

            assertAll(
                    () -> assertEquals(InvariantFixtures.PLAN_FILES, List.copyOf(plan.partVersions().keySet())),
                    () -> assertEquals(InvariantFixtures.PLAN_FILES.size(), plan.version()),
                    () -> assertEquals("init.begin", plan.startState()),
                    () -> assertEquals(INIT_FILE, plan.location().resourceName()));
        }
    }

    @Nested
    @DisplayName("Budget parameters")
    class BudgetParameters {

        @Test
        @DisplayName("are the parameters of the remediation contract but its actions, the set INV-REM-5 caps")
        void contractParameters() {
            var unit = InvariantFixtures.defaultUnits().get("remediation.qgate-findings");
            var declared = unit.params().stream().map(ParamDecl::name)
                    .filter(name -> !RemediationInvariants.ACTIONS.equals(name)).collect(Collectors.toSet());

            assertAll(
                    () -> assertEquals(Set.of("retry_budget", "fix_budget", "waiver_wait_seconds"),
                            CompiledInvariants.remediationBudgetParameters()),
                    () -> assertEquals(declared, CompiledInvariants.remediationBudgetParameters()),
                    () -> assertEquals(RemediationInvariants.BUDGET_CAPS.keySet(),
                            CompiledInvariants.remediationBudgetParameters()));
        }

        @Test
        @DisplayName("cannot be changed by a caller")
        void unmodifiable() {
            var parameters = CompiledInvariants.remediationBudgetParameters();

            assertThrows(UnsupportedOperationException.class, () -> parameters.add("other_budget"));
        }
    }

    @Nested
    @DisplayName("Refusing units")
    class RefusingUnits {

        @ParameterizedTest(name = "{0}")
        @EnumSource(InvariantId.class)
        @DisplayName("one refusing unit per invariant names the invariant, the file and the line")
        void refuses(InvariantId id) {
            var refusing = CASES.get(id);
            var unit = refusing.unit();
            var rules = InvariantFixtures.validator().verify(unit);
            var expectedLine = InvariantFixtures.lineOf(refusing.anchorText(), refusing.anchor());

            var found = InvariantFixtures.invariants().check(unit, rules, pipelineOf(unit, refusing.projectRoster()));

            assertTrue(found.stream().anyMatch(violation -> violation.id() == id
                            && refusing.anchorFile().equals(violation.location().resourceName())
                            && violation.location().line() == expectedLine),
                    () -> "%s expected at %s line %d, found %s".formatted(id.wireId(), refusing.anchorFile(),
                            expectedLine, found));
        }

        @Test
        @DisplayName("every invariant has a refusing case")
        void everyInvariantHasRefusingCase() {
            assertEquals(EnumSet.allOf(InvariantId.class), EnumSet.copyOf(CASES.keySet()));
        }

        @Test
        @DisplayName("every invariant has a check")
        void everyInvariantHasCheck() {
            assertEquals(EnumSet.allOf(InvariantId.class), CompiledInvariants.checkedIds());
        }

        @Test
        @DisplayName("a project unit without a default unit is refused at its header")
        void unitWithoutDefault() {
            var source = """
                    workflow test.unknown version 1 format 1
                    scope subworkflow
                    start unknown.done

                    state unknown.done terminal done success
                        phase inherit
                    """;
            var unit = InvariantFixtures.parse("unknown.pmwf", source, UnitOrigin.PROJECT);

            var found = InvariantFixtures.invariants().check(unit, InvariantFixtures.validator().verify(unit),
                    List.of());

            assertEquals(List.of(new InvariantLocation(InvariantId.INV_GEN_1, "unknown.pmwf", 1)),
                    found.stream().map(InvariantLocation::of).toList());
        }

        @Test
        @DisplayName("the violations are ordered by the declaration order of their state")
        void orderedByDeclaration() {
            var unit = InvariantFixtures.mutatedUnit(new Mutation("two violations", REMEDIATION_FILE,
                    "composite_job test.waiver-wait", "job test.waiver-wait"));
            var found = InvariantFixtures.invariants().check(unit, InvariantFixtures.validator().verify(unit),
                    List.of());
            var order = List.copyOf(unit.states().keySet());

            var positions = found.stream().map(violation -> order.indexOf(violation.stateId()))
                    .map(index -> index < 0 ? order.size() : index).toList();

            assertAll(
                    () -> assertFalse(found.isEmpty()),
                    () -> assertEquals(positions.stream().sorted().toList(), positions));
        }
    }

    @Nested
    @DisplayName("Named hooks")
    class NamedHooks {

        @Test
        @DisplayName("the runtime parts of INV-GEN-2, INV-GEN-4 and INV-REM-6 are the only named hooks")
        void enumerated() {
            var hooked = Arrays.stream(InvariantId.values()).filter(id -> id.namedHook().isPresent())
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(InvariantId.class)));

            assertAll(
                    () -> assertEquals(EnumSet.of(InvariantId.INV_GEN_2, InvariantId.INV_GEN_4, InvariantId.INV_REM_6),
                            hooked),
                    () -> hooked.forEach(id -> assertFalse(id.namedHook().orElseThrow().isBlank(), id::wireId)));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(InvariantId.class)
        @DisplayName("every invariant is found by its wire identifier")
        void wireIdentifier(InvariantId id) {
            assertEquals(Optional.of(id), InvariantId.ofWireId(id.wireId()));
        }

        @Test
        @DisplayName("the list holds the thirty invariants of the specification")
        void thirtyInvariants() {
            assertAll(
                    () -> assertEquals(30, InvariantId.values().length),
                    () -> assertEquals(Optional.empty(), InvariantId.ofWireId("INV-GEN-6")));
        }

        @Test
        @DisplayName("a unit pattern matches a unit, every unit or a namespace")
        void unitPatterns() {
            assertAll(
                    () -> assertTrue(InvariantId.INV_GEN_1.appliesTo("replan")),
                    () -> assertTrue(InvariantId.INV_REM_1.appliesTo("remediation.ci-failure")),
                    () -> assertFalse(InvariantId.INV_REM_1.appliesTo("replan")),
                    () -> assertTrue(InvariantId.INV_EXEC_1.appliesTo(PLAN_UNIT)),
                    () -> assertFalse(InvariantId.INV_EXEC_1.appliesTo("git.commit_and_push")));
        }
    }

    /**
     * One refusing case: a changed fixture, the roster of the project's finalize steps, and the text whose line the
     * violation must name.
     *
     * @param id            the invariant the case breaks
     * @param description   what the change does
     * @param mutation      the change of one fixture file; empty when the unit stays the passing one
     * @param projectRoster the roster the finalize pipeline validator checks
     * @param anchorFile    the file the violation must be located in
     * @param anchor        a text that occurs once in that file, on the line the violation must name
     */
    record RefusingCase(InvariantId id, String description, Optional<Mutation> mutation,
    List<FinalizeStepDescriptor> projectRoster, String anchorFile, String anchor) {

        WorkflowDefinition unit() {
            return mutation.map(InvariantFixtures::mutatedUnit)
                    .orElseGet(() -> InvariantFixtures.units(InvariantFixtures.sources(), UnitOrigin.PROJECT)
                            .get(PLAN_UNIT));
        }

        String anchorText() {
            return mutation.filter(change -> change.file().equals(anchorFile)).map(Mutation::applied)
                    .orElseGet(() -> InvariantFixtures.read(anchorFile));
        }

        @Override
        public String toString() {
            return id.wireId() + ": " + description;
        }
    }

    /**
     * A violation by its invariant and location alone.
     *
     * @param id   the invariant
     * @param file the file
     * @param line the line
     */
    record InvariantLocation(InvariantId id, String file, int line) {

        static InvariantLocation of(InvariantViolation violation) {
            return new InvariantLocation(violation.id(), violation.location().resourceName(),
                    violation.location().line());
        }
    }

    private static RefusingCase change(InvariantId id, String description, String file, String find, String replace,
            String anchor) {
        return changeAt(id, description, file, find, replace, file, anchor);
    }

    private static RefusingCase changeAt(InvariantId id, String description, String file, String find,
            String replace, String anchorFile, String anchor) {
        return new RefusingCase(id, description, Optional.of(new Mutation(description, file, find, replace)),
                InvariantFixtures.defaultRoster(), anchorFile, anchor);
    }

    private static Map<InvariantId, RefusingCase> refusingCases() {
        var collidingRoster = InvariantFixtures.defaultRoster().stream()
                .map(step -> "default:create-pr".equals(step.name())
                        ? InvariantFixtures.step(step.name(), 70, LaneClass.CORE, false, List.of(), List.of())
                        : step)
                .toList();
        var cases = List.of(
                change(InvariantId.INV_GEN_1, "a terminal reports another outcome class than the default",
                        FINALIZE_FILE, "state returned terminal returned failure",
                        "state returned terminal returned success", "state returned terminal"),
                change(InvariantId.INV_GEN_2, "a waived terminal outside a remediation unit", FINALIZE_FILE,
                        "state handed_back terminal handed_back failure", "state handed_back terminal waived success",
                        "state handed_back terminal"),
                change(InvariantId.INV_GEN_3, "a transition targets a reserved state", INIT_FILE,
                        "done -> init.ready", "done -> init.blocked", "done -> init.blocked"),
                changeAt(InvariantId.INV_GEN_4, "a path to the success terminal skips the main_sha stamp", INIT_FILE,
                        "proceed -> init.stamp", "proceed -> init.ready", FINALIZE_FILE, "state completed terminal"),
                change(InvariantId.INV_GEN_5, "the lost outcome of the vote ends in the decided terminal",
                        ADJUDICATION_FILE, "lost -> adjudicate.escalated", "lost -> adjudicate.decided",
                        "lost -> adjudicate.decided"),
                change(InvariantId.INV_INIT_1, "an init question returns elsewhere than init.classify", INIT_FILE,
                        "returned -> init.classify", "returned -> init.stamp", "returned -> init.stamp"),
                change(InvariantId.INV_INIT_2, "the light lane skips outline.light-regate", OUTLINE_FILE,
                        "done -> outline.light-regate", "done -> outline.gate", "state outline.gate"),
                change(InvariantId.INV_REFINE_1, "the deep path leaves refine without refine.analyze", REFINE_FILE,
                        "done -> refine.analyze", "done -> outline.deep", "done -> outline.deep"),
                change(InvariantId.INV_OUTLINE_1, "a remediation result enters Phase 4 past outline.gate",
                        OUTLINE_FILE, "resolved -> outline.gate", "resolved -> plan.derive",
                        "resolved -> plan.derive"),
                change(InvariantId.INV_OUTLINE_2, "the deep lane skips outline.qgate", OUTLINE_FILE,
                        "done -> outline.qgate", "done -> outline.gate", "done -> outline.gate"),
                change(InvariantId.INV_PLAN_1, "the blocking verdict of plan.qgate calls no remediation", PLAN_FILE,
                        "blocking -> plan.remediate", "blocking -> plan.compose", "blocking -> plan.compose"),
                changeAt(InvariantId.INV_EXEC_1, "the envelope task runs before the worktree initialization",
                        PLAN_FILE, "execute -> execute.worktree-init", "execute -> execute.envelope", EXECUTE_FILE,
                        "state execute.envelope"),
                change(InvariantId.INV_EXEC_2, "the exit barrier is reached without the deliverable build",
                        EXECUTE_FILE, "done -> execute.deliverable-build", "done -> execute.verify-sweep",
                        "state execute.exit-barrier"),
                change(InvariantId.INV_EXEC_3, "the envelope task routes lost past the envelope evaluation",
                        EXECUTE_FILE, "lost -> execute.evaluate-envelope-return", "lost -> finalize.hand-back",
                        "lost -> finalize.hand-back"),
                changeAt(InvariantId.INV_FIN_1, "no transition completes the mandatory step default:create-pr",
                        FINALIZE_FILE, "\n            completes default:create-pr as done", "", INIT_FILE,
                        PLAN_HEADER),
                new RefusingCase(InvariantId.INV_FIN_2, "the project roster orders default:create-pr at 70",
                        Optional.empty(), collidingRoster, FINALIZE_FILE, "state finalize.create-pr"),
                change(InvariantId.INV_FIN_3, "a step is completed as done on a failure", FINALIZE_FILE,
                        "not_merged -> finalize.hand-back",
                        "not_merged -> finalize.hand-back\n            completes default:audit as done",
                        "completes default:audit"),
                change(InvariantId.INV_FIN_4, "the deliverable commit pushes before the pull request", EXECUTE_FILE,
                        "push_mode = no_push", "push_mode = push", "state execute.commit"),
                change(InvariantId.INV_FIN_5, "the merge is reached without finalize.merge-gate", FINALIZE_FILE,
                        "done -> finalize.merge-gate", "done -> finalize.merge", "state finalize.merge\n"),
                change(InvariantId.INV_FIN_6, "the push barrier steps out to plan.return", FINALIZE_FILE,
                        "stepped_out -> finalize.hand-back", "stepped_out -> plan.return",
                        "stepped_out -> plan.return"),
                change(InvariantId.INV_ADJ_1, "the vote wait lies below its bound", ADJUDICATION_FILE,
                        "declare vote_wait_seconds = 900", "declare vote_wait_seconds = 30",
                        "declare vote_wait_seconds = 30"),
                change(InvariantId.INV_REPLAN_1, "the re-planning task asks the user", REPLAN_FILE,
                        "job llm change role planner", "job llm change role planner ask", "state replan.assess"),
                change(InvariantId.INV_GIT_1, "an unknown push state continues to the verification", GIT_FILE,
                        "        push_state_unknown -> git.stepped-out\n\nstate git.lease-push",
                        "        push_state_unknown -> git.verify\n\nstate git.lease-push",
                        "push_state_unknown -> git.verify"),
                change(InvariantId.INV_REM_1, "the fix budget exceeds its hard cap", REMEDIATION_FILE,
                        "param fix_budget : int[0..5]", "param fix_budget : int[0..9]", "param fix_budget"),
                change(InvariantId.INV_REM_2, "the lost decision task routes past the conservative remedy",
                        REMEDIATION_FILE,
                        "infeasible -> remediation-qgate.conservative\n        lost -> remediation-qgate.conservative",
                        "infeasible -> remediation-qgate.conservative\n        lost -> remediation-qgate.budget-check",
                        "lost -> remediation-qgate.budget-check"),
                change(InvariantId.INV_REM_3, "the waiver wait is no composite job", REMEDIATION_FILE,
                        "composite_job test.waiver-wait", "job test.waiver-wait",
                        "state remediation-qgate.waiver-wait"),
                change(InvariantId.INV_REM_4, "the nested commit steps out into the exhausted terminal",
                        REMEDIATION_FILE, "stepped_out -> remediation-qgate.budget-check",
                        "stepped_out -> remediation-qgate.exhausted", "stepped_out -> remediation-qgate.exhausted"),
                change(InvariantId.INV_REM_5, "an early remediation call routes exhausted past the fallback",
                        REFINE_FILE, "exhausted -> plan.return", "exhausted -> outline.deep",
                        "exhausted -> outline.deep"),
                change(InvariantId.INV_REM_6, "the plan unit runs a remediation primitive", PLAN_FILE,
                        "state plan.derive\n    phase plan\n    auto test.proceed",
                        "state plan.derive\n    phase plan\n    auto remediation.offer-actions", "state plan.derive"),
                change(InvariantId.INV_REM_7, "the decision offers its choice past the decision task",
                        REMEDIATION_FILE, "choice -> remediation-qgate.choose", "choice -> remediation-qgate.rerun",
                        "state remediation-qgate.decide"));
        var map = new EnumMap<InvariantId, RefusingCase>(InvariantId.class);
        cases.forEach(refusing -> {
            if (map.put(refusing.id(), refusing) != null) {
                throw new IllegalStateException("two refusing cases for " + refusing.id());
            }
        });
        return map;
    }

    @Test
    @DisplayName("refuses a null argument")
    void nullArguments() {
        var invariants = InvariantFixtures.invariants();
        var unit = InvariantFixtures.defaultUnits().get(PLAN_UNIT);
        var rules = InvariantFixtures.validator().verify(unit);
        var registry = InvariantFixtures.registry();
        var noUnits = Set.<WorkflowDefinition>of();

        assertAll(
                () -> assertThrows(NullPointerException.class, () -> invariants.check(null, rules, List.of())),
                () -> assertThrows(NullPointerException.class, () -> invariants.check(unit, null, List.of())),
                () -> assertThrows(NullPointerException.class, () -> invariants.check(unit, rules, null)),
                () -> assertThrows(NullPointerException.class,
                        () -> new CompiledInvariants(registry, noUnits, null)));
    }
}
