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

import static de.planmarshall.workflow.plan.PlanFixtures.ASK_SITE;
import static de.planmarshall.workflow.plan.PlanFixtures.EVERY_PHASE;
import static de.planmarshall.workflow.plan.PlanFixtures.FINALIZE_STEPS;
import static de.planmarshall.workflow.plan.PlanFixtures.KEY_POINTS;
import static de.planmarshall.workflow.plan.PlanFixtures.QUESTIONS_ONLY;
import static de.planmarshall.workflow.plan.PlanFixtures.REMEDIATION_SITE;
import static de.planmarshall.workflow.plan.PlanFixtures.TEMPLATE_FILE;
import static de.planmarshall.workflow.plan.PlanFixtures.asking;
import static de.planmarshall.workflow.plan.PlanFixtures.askingAtEveryLevel;
import static de.planmarshall.workflow.plan.PlanFixtures.composer;
import static de.planmarshall.workflow.plan.PlanFixtures.composerWithoutRules;
import static de.planmarshall.workflow.plan.PlanFixtures.facts;
import static de.planmarshall.workflow.plan.PlanFixtures.request;
import static de.planmarshall.workflow.plan.PlanFixtures.start;
import static de.planmarshall.workflow.plan.PlanFixtures.withAdjudication;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.workflow.ast.CallValue;
import de.planmarshall.workflow.ast.InstanceHeader;
import de.planmarshall.workflow.ast.OutcomeTransition;
import de.planmarshall.workflow.ast.StateBehavior;
import de.planmarshall.workflow.ast.StateNode;
import de.planmarshall.workflow.ast.UnitOrigin;
import de.planmarshall.workflow.ast.UnitType;
import de.planmarshall.workflow.plan.CompositionResult.Refusal;
import de.planmarshall.workflow.plan.PlanInstanceHeader.Lane;
import de.planmarshall.workflow.plan.PrunedElement.Kind;
import de.planmarshall.workflow.verify.DeclarationBounds;
import de.planmarshall.workflow.verify.InvariantFixtures;

/**
 * The composition of a plan instance version (PM-IMPL-5, PM-WF-1, PM-EXT-7): a composed version passes Rules 1-12, the
 * compiled invariants and the header schema, names every pruned element with its reason and source fact and every
 * policy call with the provenance of its arguments; each event composes the version after the one in force; a
 * version that lacks the current state or a call-stack state is refused {@code position_not_contained}, one that
 * breaks a check {@code invariant_failed}, as a value, and the version in force stays.
 */
@DisplayName("PlanInstanceComposer")
class PlanInstanceComposerTest {

    private static final InstancePosition AT_START = InstancePosition.at("finalize.push-barrier");

    private static PlanInstance composed(CompositionResult result) {
        return assertInstanceOf(CompositionResult.Composed.class, result, result::toString).instance();
    }

    private static CompositionResult.Refused refused(CompositionResult result) {
        return assertInstanceOf(CompositionResult.Refused.class, result, result::toString);
    }

    private static PlanInstance version1() {
        return composed(composer().compose(start(facts().build())));
    }

    private static PolicyCall remediationCall(PlanInstance instance) {
        return instance.header().policyCalls().stream().filter(call -> REMEDIATION_SITE.equals(call.callSite()))
                .findFirst().orElseThrow();
    }

    private static StateBehavior.SubWorkflowCall remediationState(PlanInstance instance) {
        return assertInstanceOf(StateBehavior.SubWorkflowCall.class,
                instance.unit().states().get(REMEDIATION_SITE).behavior());
    }

    private static List<String> allSteps() {
        return FINALIZE_STEPS.stream().map(CompositionRequest.Step::id).toList();
    }

    /** The pruned elements of the composition rules, without the variants the asking axis binds away. */
    private static List<PrunedElement> rulePrunes(PlanInstanceHeader header) {
        return header.pruned().stream().filter(prune -> prune.reason() != PruneReason.PLAN_DEFAULT).toList();
    }

    private static String askingCallee(PlanInstance instance) {
        return assertInstanceOf(StateBehavior.SubWorkflowCall.class,
                instance.unit().states().get(ASK_SITE).behavior()).subWorkflowId();
    }

    private static PrunedElement planDefault(String variant, String sourceFact) {
        return new PrunedElement(Kind.CALL, ASK_SITE + "/" + variant, PruneReason.PLAN_DEFAULT, sourceFact);
    }

    /** @return whether the auto, job or call state of the instance routes the outcome to the target */
    private static boolean routes(PlanInstance instance, String state, String outcome, String target) {
        var transitions = switch (instance.unit().states().get(state).behavior()) {
            case StateBehavior.AutoStep step -> step.transitions();
            case StateBehavior.JobStep job -> job.transitions();
            case StateBehavior.SubWorkflowCall call -> call.transitions();
            default -> List.<OutcomeTransition>of();
        };
        return transitions.stream().anyMatch(transition -> outcome.equals(transition.outcomeName())
                && target.equals(transition.targetStateId()));
    }

    /** @return the elements the composition pruned by lane */
    private static List<PrunedElement> otherLane(PlanInstanceHeader header) {
        return header.pruned().stream().filter(prune -> prune.reason() == PruneReason.OTHER_LANE).toList();
    }

    @Nested
    @DisplayName("Composed version")
    class Composed {

        @Test
        @DisplayName("version 1 passes every check and carries the header of the start event")
        void firstVersion() {
            var instance = version1();
            var header = instance.header();

            assertAll(
                    () -> assertEquals(1, instance.version()),
                    () -> assertEquals(CompositionEvent.START, header.event()),
                    () -> assertEquals(PlanFixtures.PLAN_ID, header.planId()),
                    () -> assertEquals(PlanFixtures.COMPOSED_AT, header.composedAt()),
                    () -> assertEquals(Optional.of(Lane.DEEP), header.lane()),
                    () -> assertEquals(List.of(), HeaderSchemaCheck.check(header)),
                    () -> assertEquals(PlanInstanceComposer.PLAN_UNIT, instance.unit().id()),
                    () -> assertEquals(1, instance.unit().version()),
                    () -> assertEquals(PlanFixtures.template().states().keySet(), instance.unit().states().keySet()),
                    () -> assertEquals(Optional.of(HeaderJson.write(header)),
                            instance.unit().instance().map(InstanceHeader::json)));
        }

        @Test
        @DisplayName("a rule whose selector names nothing the template declares prunes nothing and is not listed")
        void selectorNamingNothing() {
            var header = version1().header();

            assertAll(
                    () -> assertEquals(List.of(), rulePrunes(header)),
                    () -> assertEquals(allSteps(), header.steps().phase6()),
                    () -> assertEquals(PlanFixtures.VERIFICATION_STEPS, header.steps().phase5Verification()));
        }

        @Test
        @DisplayName("the text is the frontmatter, the workflow header, the instance line and the composed states, and "
                + "parses back")
        void text() {
            var instance = version1();
            var reparsed = PlanFixtures.parse("plan.v1.pmwf", instance.text(), UnitOrigin.DEFAULT);

            assertAll(
                    () -> assertTrue(instance.text().startsWith("---\n"), instance.text()),
                    () -> assertTrue(instance.text().contains("\ntype: instance\n"), instance.text()),
                    () -> assertTrue(instance.text().contains("\n---\nworkflow plan version 1 format 1\ninstance {"),
                            instance.text()),
                    () -> assertEquals(instance.unit().states().keySet(), reparsed.states().keySet()),
                    () -> assertEquals(UnitType.INSTANCE, reparsed.frontmatter().type()),
                    () -> assertEquals(instance.unit().frontmatter().name(), reparsed.frontmatter().name()),
                    () -> assertEquals(instance.header().planId(),
                            reparsed.instance().orElseThrow().planId()));
        }

        @Test
        @DisplayName("the composed version carries the template's frontmatter with the type instance, and the "
                + "template's start state")
        void instanceFrontmatter() {
            var template = PlanFixtures.template();

            var unit = version1().unit();

            assertAll(
                    () -> assertEquals(UnitType.WORKFLOW, template.frontmatter().type()),
                    () -> assertEquals(UnitType.INSTANCE, unit.frontmatter().type()),
                    () -> assertEquals(template.frontmatter().name(), unit.frontmatter().name()),
                    () -> assertEquals(template.frontmatter().description(), unit.frontmatter().description()),
                    () -> assertEquals(template.frontmatter().author(), unit.frontmatter().author()),
                    () -> assertEquals(template.frontmatter().version(), unit.frontmatter().version()),
                    () -> assertEquals(template.startState(), unit.startState()),
                    () -> assertEquals(template.entryBindings(), unit.entryBindings()));
        }

        @Test
        @DisplayName("names the policy call with the template's arguments and the sub-units its calls reach")
        void policyCallsAndSubUnits() {
            var instance = version1();
            var call = remediationCall(instance);

            assertAll(
                    () -> assertEquals("remediation.qgate-findings", call.unit()),
                    () -> assertEquals(BoundArgument.fromTemplate(new CallValue.EnumList(List.of("retry", "fix",
                            "loop_back")), TEMPLATE_FILE), call.args().get("actions")),
                    () -> assertEquals(BoundArgument.fromTemplate(new CallValue.IntValue(3), TEMPLATE_FILE),
                            call.args().get("fix_budget")),
                    () -> assertEquals(1, instance.header().policyCalls().size()),
                    () -> assertEquals(List.of("git.commit_and_push", "remediation.qgate-findings"),
                            instance.header().inputs().subUnits().stream().map(UnitReference::unit).toList()),
                    () -> assertEquals(List.of("plan", QUESTIONS_ONLY),
                            instance.header().inputs().templates().stream().map(UnitReference::unit).toList()));
        }

        @Test
        @DisplayName("lists the adjudication unit the request supplies among the sub-units with its digest and origin, "
                + "although no call site calls it")
        void adjudicationUnit() {
            var text = PlanFixtures.subUnitText(InvariantFixtures.ADJUDICATION_FILE);

            var instance = composed(composer().compose(withAdjudication(start(facts().build()))));
            var subUnits = instance.header().inputs().subUnits();
            var adjudication = subUnits.getLast();
            var line = instance.unit().instance().orElseThrow().inputs().subUnits().getLast();

            assertAll(
                    () -> assertEquals(List.of("git.commit_and_push", "remediation.qgate-findings",
                            DeclarationBounds.ADJUDICATION_UNIT), subUnits.stream().map(UnitReference::unit).toList()),
                    () -> assertEquals(Optional.of(InvariantFixtures.ADJUDICATION_FILE), adjudication.file()),
                    () -> assertEquals(PlanFacts.sha256(text), adjudication.digest()),
                    () -> assertEquals(UnitOrigin.DEFAULT, adjudication.origin()),
                    () -> assertEquals(DeclarationBounds.ADJUDICATION_UNIT, line.unit()),
                    () -> assertEquals(PlanFacts.sha256(text), line.digest()),
                    () -> assertEquals(List.of(), HeaderSchemaCheck.check(instance.header())),
                    () -> assertFalse(instance.unit().states().values().stream()
                            .map(StateNode::behavior)
                            .anyMatch(behavior -> behavior instanceof StateBehavior.SubWorkflowCall call
                                    && DeclarationBounds.ADJUDICATION_UNIT.equals(call.subWorkflowId()))));
        }

        @Test
        @DisplayName("a request without the adjudication unit lists no sub-unit beyond those its calls reach")
        void noAdjudicationUnitControl() {
            var instance = version1();

            assertFalse(instance.header().inputs().subUnits().stream().map(UnitReference::unit)
                    .anyMatch(DeclarationBounds.ADJUDICATION_UNIT::equals));
        }
    }

    @Nested
    @DisplayName("Events")
    class Events {

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = CompositionEvent.class, mode = EnumSource.Mode.EXCLUDE, names = "START")
        @DisplayName("each event after the start composes the version after the one in force")
        void nextVersion(CompositionEvent event) {
            var instance = composed(composer().compose(request(event, facts(event).build(), Optional.of(version1()),
                    List.of(), AT_START)));

            assertAll(
                    () -> assertEquals(2, instance.version()),
                    () -> assertEquals(event, instance.header().event()),
                    () -> assertEquals(2, instance.unit().version()));
        }

        @Test
        @DisplayName("the log line of version 1 carries diff_summary null, that of version 2 the summary against "
                + "version 1")
        void diffSummary() {
            var first = version1();
            var second = composed(composer().compose(request(CompositionEvent.WAIVER, facts().build(),
                    Optional.of(first), List.of(Waiver.met("W-2", "ci_check:build")), AT_START)));

            var firstRecord = CompositionRecord.composed(first, Optional.empty(), PlanFixtures.COMPOSED_AT);
            var secondRecord = CompositionRecord.composed(second, Optional.of(first), PlanFixtures.COMPOSED_AT);

            assertAll(
                    () -> assertEquals(Optional.empty(), firstRecord.diffSummary()),
                    () -> assertTrue(firstRecord.toJson().contains("\"diff_summary\":null"), firstRecord.toJson()),
                    () -> assertEquals(Optional.of(DiffSummary.between(first.header(), second.header())),
                            secondRecord.diffSummary()),
                    () -> assertEquals(List.of(new WaivedGate("ci_check:build", "W-2")),
                            secondRecord.diffSummary().orElseThrow().gatesWaived()),
                    () -> assertTrue(secondRecord.toJson().contains("\"gates_waived\":[{\"gate\":\"ci_check:build\","
                            + "\"waiver_id\":\"W-2\"}]"), secondRecord.toJson()));
        }

        @Test
        @DisplayName("the start event has no previous version, every other event needs one")
        void eventAndPrevious() {
            var previous = Optional.of(version1());
            var facts = facts().build();

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> request(CompositionEvent.START, facts,
                            previous, List.of(), AT_START)),
                    () -> assertThrows(IllegalArgumentException.class, () -> request(CompositionEvent.PLAN, facts,
                            Optional.empty(), List.of(), AT_START)),
                    () -> assertThrows(IllegalArgumentException.class, () -> request("other-plan",
                            CompositionEvent.PLAN, facts, previous, List.of(), AT_START)));
        }

        @ParameterizedTest(name = "{0} with {1}")
        @CsvSource({"FALLBACK, returned", "FALLBACK, handed_back", "EMPTY_HARVEST, empty_harvest"})
        @DisplayName("a fallback and an empty harvest are accepted with the post_run fact they compose")
        void postRunMatchesEvent(CompositionEvent event, String postRun) {
            var previous = Optional.of(version1());
            var facts = facts().postRun(postRun).build();

            var request = request(event, facts, previous, List.of(), AT_START);

            assertEquals(Optional.of(postRun), request.facts().postRun());
        }

        @ParameterizedTest(name = "{0} with {1}")
        @CsvSource(value = {"FALLBACK, -", "FALLBACK, empty_harvest", "EMPTY_HARVEST, -", "EMPTY_HARVEST, returned",
                "PLAN, returned", "WAIVER, handed_back", "REPLAN, empty_harvest", "START, returned"}, nullValues = "-")
        @DisplayName("an event whose post_run fact it does not compose is refused, naming the event and the fact")
        void postRunContradictsEvent(CompositionEvent event, String postRun) {
            var previous = event == CompositionEvent.START ? Optional.<PlanInstance>empty() : Optional.of(version1());
            var facts = facts().postRun(postRun).build();

            var refused = assertThrows(IllegalArgumentException.class,
                    () -> request(event, facts, previous, List.of(), AT_START));

            assertAll(
                    () -> assertTrue(refused.getMessage().contains("'" + event.wireName() + "'"),
                            refused.getMessage()),
                    () -> assertTrue(refused.getMessage().contains(PlanFacts.POST_RUN_FACT), refused.getMessage()));
        }
    }

    @Nested
    @DisplayName("Rules")
    class Rules {

        /**
         * A land plan never authors a fix (PM-WATCH-PHASE-89): its instance prunes the fix and loop-back actions of
         * every remediation call with reason {@code land_lane} and binds every call to {@code actions=[retry]}.
         */
        @Test
        @DisplayName("land prunes fix and loop_back of the policy call and binds it to retry")
        void land() {
            var instance = composed(composer().compose(start(facts().lane(Lane.LAND).build())));

            assertAll(
                    () -> assertEquals(List.of(
                            new PrunedElement(Kind.POLICY_ACTION, REMEDIATION_SITE + "/fix", PruneReason.LAND_LANE,
                                    "lane"),
                            new PrunedElement(Kind.POLICY_ACTION, REMEDIATION_SITE + "/loop_back",
                                    PruneReason.LAND_LANE, "lane"),
                            new PrunedElement(Kind.STEP, "default:finalize-step-simplify", PruneReason.LAND_LANE,
                                    "lane")), rulePrunes(instance.header())),
                    () -> assertEquals(BoundArgument.fromCompositionRule(new CallValue.EnumList(List.of("retry"))),
                            remediationCall(instance).args().get("actions")),
                    () -> assertEquals(new CallValue.EnumList(List.of("retry")),
                            remediationState(instance).paramBindings().get("actions")));
        }

        @Test
        @DisplayName("a request plan keeps fix among the actions of its policy call")
        void requestPlanControl() {
            var instance = version1();

            assertEquals(new CallValue.EnumList(List.of("retry", "fix", "loop_back")),
                    remediationState(instance).paramBindings().get("actions"));
        }

        @Test
        @DisplayName("an empty push remote prunes the remote-bound steps from the step list and keeps the local gate")
        void localOnly() {
            var instance = composed(composer().compose(start(facts().configuration("derived.push_remote", "")
                    .build())));

            assertAll(
                    () -> assertEquals(List.of("default:push", "default:create-pr", "default:ci-verify",
                                    "default:automatic-review", "default:analysis-roundtrip"),
                            rulePrunes(instance.header()).stream().map(PrunedElement::id).toList()),
                    () -> assertTrue(rulePrunes(instance.header()).stream()
                            .allMatch(prune -> prune.reason() == PruneReason.LOCAL_ONLY)),
                    () -> assertEquals(List.of("default:finalize-step-simplify", "default:pre-push-quality-gate",
                                    "default:branch-cleanup", "default:lessons-capture", "default:emit-landing",
                                    "default:archive-plan"),
                            instance.header().steps().phase6()));
        }

        @Test
        @DisplayName("the post-run-only form prunes every step below order 900 and keeps the post-run band")
        void postRun() {
            var instance = composed(composer().compose(request(CompositionEvent.FALLBACK,
                    facts().postRun("returned").build(), Optional.of(version1()), List.of(), AT_START)));

            assertAll(
                    () -> assertEquals(List.of("default:lessons-capture", "default:emit-landing",
                            "default:archive-plan"), instance.header().steps().phase6()),
                    () -> assertEquals(8, rulePrunes(instance.header()).size()),
                    () -> assertTrue(rulePrunes(instance.header()).stream().allMatch(prune ->
                            prune.reason() == PruneReason.RETURNED && "post_run".equals(prune.sourceFact()))),
                    () -> assertEquals(PlanFixtures.VERIFICATION_STEPS,
                            instance.header().steps().phase5Verification()));
        }

        @Test
        @DisplayName("an element two rules prune is listed once, with the reason of the first rule")
        void prunedOnce() {
            var instance = composed(composer().compose(start(facts().lane(Lane.LAND).changeType("analysis")
                    .build())));

            assertEquals(List.of(PruneReason.LAND_LANE), instance.header().pruned().stream()
                    .filter(prune -> "default:finalize-step-simplify".equals(prune.id()))
                    .map(PrunedElement::reason).toList());
        }
    }

    @Nested
    @DisplayName("Waivers")
    class Waivers {

        @Test
        @DisplayName("a raise binds the budget of its policy with the waiver as the source")
        void raise() {
            var instance = composed(composer().compose(request(CompositionEvent.WAIVER, facts().build(),
                    Optional.of(version1()), List.of(Waiver.raise("W-1", "fix_budget:qgate-findings", 4)), AT_START)));

            assertAll(
                    () -> assertEquals(BoundArgument.fromWaiver(new CallValue.IntValue(4), "W-1"),
                            remediationCall(instance).args().get("fix_budget")),
                    () -> assertEquals(new CallValue.IntValue(4),
                            remediationState(instance).paramBindings().get("fix_budget")),
                    () -> assertEquals(BoundArgument.fromTemplate(new CallValue.IntValue(1), TEMPLATE_FILE),
                            remediationCall(instance).args().get("retry_budget")));
        }

        @Test
        @DisplayName("a raise of another policy binds nothing at this call")
        void raiseOfAnotherPolicy() {
            var instance = composed(composer().compose(request(CompositionEvent.WAIVER, facts().build(),
                    Optional.of(version1()), List.of(Waiver.raise("W-1", "fix_budget:ci-failure", 4)), AT_START)));

            assertEquals(BoundArgument.fromTemplate(new CallValue.IntValue(3), TEMPLATE_FILE),
                    remediationCall(instance).args().get("fix_budget"));
        }

        @Test
        @DisplayName("a met waiver marks its gate waived with the waiver id")
        void met() {
            var instance = composed(composer().compose(request(CompositionEvent.WAIVER, facts().build(),
                    Optional.of(version1()), List.of(Waiver.met("W-2", "ci_check:build")), AT_START)));

            assertEquals(List.of(new WaivedGate("ci_check:build", "W-2")), instance.header().waivedGates());
        }

        @Test
        @DisplayName("a raise needs a ceiling and a met waiver takes none")
        @SuppressWarnings("java:S5778") // each lambda is one constructor call; its arguments are the case table
        void waiverShape() {
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> new Waiver("W-1", "fix_budget:x",
                            Waiver.Mode.RAISE, Optional.empty())),
                    () -> assertThrows(IllegalArgumentException.class, () -> new Waiver("W-1", "ci_check:build",
                            Waiver.Mode.MET, Optional.of(1))),
                    () -> assertEquals(Optional.of(Waiver.Mode.RAISE), Waiver.Mode.fromWireName("raise")),
                    () -> assertEquals(Optional.empty(), Waiver.met("W-1", "ci_check:build").raisedUnit()));
        }
    }

    /**
     * The plan behaviour axes (PM-WF-25): the asking call site of the template calls the variant the nearest level
     * binds, else the shipped plan default, and every other variant of the axis is a pruned call with the reason
     * {@code plan_default} and the binding level as source fact; an override of the plan composes the version after
     * the one in force, unless it would not contain the plan's position. The binding cases compose without rules,
     * because the rules are not what these cases examine; with the shipped rules a variant that does not merge itself
     * composes as well, the engine settling the pruned merge execution (the cases of {@link MergeByOperator}).
     */
    @Nested
    @DisplayName("Axes")
    class Axes {

        private static final InstancePosition IN_MERGE_QUEUE = InstancePosition.at("finalize.merge-queue-wait");

        @Test
        @DisplayName("an axis set at plan, project and machine level binds the plan's variant and prunes the others "
                + "as calls with plan_default")
        void everyLevel() {
            var instance = composed(composerWithoutRules().compose(start(askingAtEveryLevel().build())));

            assertAll(
                    () -> assertEquals(EVERY_PHASE, askingCallee(instance)),
                    () -> assertEquals(List.of(planDefault(KEY_POINTS, "plan.axes.asking"),
                            planDefault(QUESTIONS_ONLY, "plan.axes.asking")), instance.header().pruned()),
                    () -> assertEquals(List.of(), HeaderSchemaCheck.check(instance.header())),
                    () -> assertTrue(instance.header().inputs().templates().stream().map(UnitReference::unit)
                            .toList().contains(EVERY_PHASE)),
                    () -> assertFalse(instance.header().inputs().templates().stream().map(UnitReference::unit)
                            .anyMatch(unit -> KEY_POINTS.equals(unit) || QUESTIONS_ONLY.equals(unit))));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = PlanFacts.Level.class, names = {"PLAN", "PROJECT", "MACHINE"})
        @DisplayName("a level that alone sets the axis binds its variant, the machine's among them")
        void oneLevel(PlanFacts.Level level) {
            var instance = composed(composerWithoutRules().compose(start(asking(level, KEY_POINTS).build())));
            var source = level.wireName() + ".axes.asking";

            assertAll(
                    () -> assertEquals(KEY_POINTS, askingCallee(instance)),
                    () -> assertEquals(List.of(planDefault(EVERY_PHASE, source), planDefault(QUESTIONS_ONLY, source)),
                            instance.header().pruned()));
        }

        @ParameterizedTest(name = "{0} waits after {1}")
        @CsvSource(delimiter = ';', value = {"ask.key-points;''", "ask.questions-only;''",
                "ask.every-phase;init,refine,plan,execute"})
        @DisplayName("an asking variant declaring its shipped wait_after, the empty list among them, compiles against"
                + " the asking schema and binds")
        void shippedWaitAfterBinds(String variant, String waitAfter) {
            var definition = PlanFixtures.subUnits().stream().map(CompositionRequest.SubUnit::definition)
                    .filter(unit -> variant.equals(unit.id())).findFirst().orElseThrow();
            var expected = waitAfter.isEmpty() ? List.<String>of() : List.of(waitAfter.split(","));

            var instance = composed(composerWithoutRules().compose(start(asking(PlanFacts.Level.PLAN, variant)
                    .build())));

            assertAll(
                    () -> assertEquals(new CallValue.EnumList(expected),
                            definition.declarations().get(DeclarationBounds.WAIT_AFTER).value()),
                    () -> assertEquals(List.of(), DeclarationBounds.check(definition)),
                    () -> assertEquals(variant, askingCallee(instance)));
        }

        @Test
        @DisplayName("the project's value wins over the machine's")
        void projectOverMachine() {
            var instance = composed(composerWithoutRules().compose(start(asking(PlanFacts.Level.PROJECT, EVERY_PHASE)
                    .axis(PlanFacts.Axis.ASKING, PlanFacts.Level.MACHINE, KEY_POINTS).build())));

            assertAll(
                    () -> assertEquals(EVERY_PHASE, askingCallee(instance)),
                    () -> assertEquals(List.of(planDefault(KEY_POINTS, "project.axes.asking"),
                            planDefault(QUESTIONS_ONLY, "project.axes.asking")), instance.header().pruned()));
        }

        @Test
        @DisplayName("where no level sets the axis, the shipped plan default stays bound")
        void shippedDefault() {
            var instance = version1();

            assertAll(
                    () -> assertEquals(QUESTIONS_ONLY, askingCallee(instance)),
                    () -> assertEquals(List.of(planDefault(EVERY_PHASE, "shipped.axes.asking"),
                            planDefault(KEY_POINTS, "shipped.axes.asking")), instance.header().pruned()));
        }

        @Test
        @DisplayName("an override of the plan composes the override version, which binds the plan's new variant")
        void override() {
            var instance = composed(composerWithoutRules().compose(request(CompositionEvent.OVERRIDE,
                    asking(PlanFacts.Level.PLAN, EVERY_PHASE).build(), Optional.of(version1()), List.of(),
                    AT_START)));

            assertAll(
                    () -> assertEquals(2, instance.version()),
                    () -> assertEquals(CompositionEvent.OVERRIDE, instance.header().event()),
                    () -> assertEquals(CompositionEvent.Trigger.OPERATOR, instance.header().event().trigger()),
                    () -> assertEquals(EVERY_PHASE, askingCallee(instance)),
                    () -> assertEquals(List.of(planDefault(KEY_POINTS, "plan.axes.asking"),
                            planDefault(QUESTIONS_ONLY, "plan.axes.asking")), instance.header().pruned()));
        }

        @Test
        @DisplayName("an override whose version would not contain the position is refused position_not_contained")
        void overrideOutsidePosition() {
            var refused = refused(composer().compose(request(CompositionEvent.OVERRIDE,
                    asking(PlanFacts.Level.PLAN, EVERY_PHASE).build(), Optional.of(version1()), List.of(),
                    IN_MERGE_QUEUE)));

            assertAll(
                    () -> assertEquals(Refusal.POSITION_NOT_CONTAINED, refused.refusal()),
                    () -> assertTrue(refused.violations().getFirst().contains("finalize.merge-queue-wait"),
                            refused.violations()::toString));
        }

        @Test
        @DisplayName("an override to a variant that merges by itself keeps the position and is composed")
        void overrideInsidePositionControl() {
            var instance = composed(composer().compose(request(CompositionEvent.OVERRIDE,
                    asking(PlanFacts.Level.PLAN, QUESTIONS_ONLY).build(), Optional.of(version1()), List.of(),
                    IN_MERGE_QUEUE)));

            assertAll(
                    () -> assertEquals(2, instance.version()),
                    () -> assertTrue(instance.unit().states().containsKey("finalize.merge-queue-wait")),
                    () -> assertEquals(List.of(), rulePrunes(instance.header())));
        }

        @Test
        @DisplayName("plan_facts_digest changes with the plan's overrides.json and stays for the same document")
        void overridesInDigest() {
            var first = """
                    {"format_version":1,"revision":1,"roles":{},"axes":{"asking":"ask.every-phase"}}""";
            var second = """
                    {"format_version":1,"revision":2,"roles":{},"axes":{"asking":"ask.every-phase"}}""";

            var withFirst = version(facts().overrides(first).build());
            var withFirstAgain = version(facts().overrides(first).build());
            var withSecond = version(facts().overrides(second).build());
            var without = version(facts().build());

            assertAll(
                    () -> assertEquals(withFirst, withFirstAgain),
                    () -> assertNotEquals(withFirst, withSecond),
                    () -> assertNotEquals(withFirst, without));
        }

        private static String version(PlanFacts facts) {
            return composed(composer().compose(start(facts))).header().inputs().planFactsDigest();
        }
    }

    /**
     * The shipped rule {@code merge-by-operator}: an asking variant that declares {@code self_merge = false} loses the
     * merge execution, the states {@code finalize.merge-dispatch}, {@code finalize.merge-queue-wait} and
     * {@code finalize.merge-queue-exit}, and composes: the {@code pass} of {@code finalize.merge-barrier} into the
     * pruned dispatch is settled by the prune record, and the barrier's {@code pass_hand_back} leaves the merge to the
     * operator. One that merges by itself keeps the merge execution.
     */
    @Nested
    @DisplayName("Merge by operator")
    class MergeByOperator {

        private static final List<String> MERGE_EXECUTION = List.of("finalize.merge-dispatch",
                "finalize.merge-queue-wait", "finalize.merge-queue-exit");

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"ask.every-phase", "ask.key-points"})
        @DisplayName("a variant that leaves the merge to the operator loses the merge execution behind the merge"
                + " barrier, lists it merge_by_operator, and verifies")
        void selfMergeFalse(String variant) {
            var instance = composed(composer().compose(start(asking(PlanFacts.Level.PLAN, variant).build())));

            assertAll(
                    () -> MERGE_EXECUTION.forEach(state -> assertFalse(instance.unit().states().containsKey(state),
                            state)),
                    () -> assertEquals(MERGE_EXECUTION.stream().map(state -> new PrunedElement(Kind.STATE, state,
                            PruneReason.MERGE_BY_OPERATOR, "axes.asking")).toList(), rulePrunes(instance.header())),
                    () -> assertTrue(instance.unit().states().keySet().containsAll(List.of("finalize.merge-gate",
                            "finalize.merge-barrier", "finalize.hand-back"))),
                    () -> assertEquals(List.of(), HeaderSchemaCheck.check(instance.header())));
        }

        @Test
        @DisplayName("a variant that merges by itself keeps the merge execution")
        void selfMergeTrueControl() {
            var instance = composed(composer().compose(start(asking(PlanFacts.Level.PLAN, QUESTIONS_ONLY)
                    .build())));

            assertAll(
                    () -> assertTrue(instance.unit().states().keySet().containsAll(MERGE_EXECUTION)),
                    () -> assertEquals(List.of(), rulePrunes(instance.header())));
        }
    }

    /**
     * The lane routes at the {@code outline} event, over the merged plan unit of the invariant fixtures, which holds
     * both lanes: a {@code deep} version prunes exactly the light route with {@code other_lane}, a {@code light} version
     * keeps the deep route its escalation reaches, and the shared states before the split survive in both; the
     * {@code start} event prunes nothing by lane.
     */
    @Nested
    @DisplayName("Lane routes")
    class LaneRoutes {

        private static final InstancePosition AT_INIT = InstancePosition.at("init.begin");

        private static PlanFacts factsOf(Lane lane) {
            return facts().lane(lane).build();
        }

        private static CompositionResult atStart(Lane lane, List<InvariantFixtures.Mutation> mutations) {
            return PlanFixtures.mergedComposer().compose(PlanFixtures.mergedRequest(CompositionEvent.START,
                    factsOf(lane), Optional.empty(), AT_INIT, mutations));
        }

        /** @return version 1 of the passing units, the version in force at the outline event */
        private static PlanInstance mergedVersion1(Lane lane) {
            return composed(atStart(lane, List.of()));
        }

        private static CompositionResult atOutline(Lane lane, String position,
                List<InvariantFixtures.Mutation> mutations) {
            return PlanFixtures.mergedComposer().compose(PlanFixtures.mergedRequest(CompositionEvent.OUTLINE,
                    factsOf(lane), Optional.of(mergedVersion1(lane)), InstancePosition.at(position), mutations));
        }

        @ParameterizedTest(name = "lane {0}")
        @EnumSource(value = Lane.class, names = {"DEEP", "LIGHT"})
        @DisplayName("the same template at start, where the lane is not yet pruned by, prunes nothing by lane")
        void startPrunesNothingByLane(Lane lane) {
            var instance = mergedVersion1(lane);
            var template = InvariantFixtures.passingUnits(UnitOrigin.PROJECT).get("plan");

            assertAll(
                    () -> assertEquals(template.states().keySet(), instance.unit().states().keySet()),
                    () -> assertEquals(List.of(), otherLane(instance.header())));
        }

        @Test
        @DisplayName("a light version of a template whose light gate lacks the ratchet is refused with INV-INIT-2")
        void lightWithoutRatchetRefused() {
            var refused = refused(atOutline(Lane.LIGHT, "outline.light",
                    List.of(InvariantFixtures.WITHOUT_RATCHET)));

            assertAll(
                    () -> assertEquals(Refusal.INVARIANT_FAILED, refused.refusal()),
                    () -> assertTrue(refused.violations().contains("INV-INIT-2 in 'init.light-gate': the ratchet"
                            + " transition 'deep' -> 'init.deep-exit' of 'init.light-gate' is"
                            + " missing"), refused.violations()::toString));
        }

        @Test
        @DisplayName("version 1 of a template whose light gate lacks the ratchet is refused with INV-INIT-2, since the"
                + " lane is not yet pruned by at start")
        void startWithoutRatchetRefused() {
            var refused = refused(atStart(Lane.DEEP, List.of(InvariantFixtures.WITHOUT_RATCHET)));

            assertAll(
                    () -> assertEquals(Refusal.INVARIANT_FAILED, refused.refusal()),
                    () -> assertTrue(refused.violations().contains("INV-INIT-2 in 'init.light-gate': the ratchet"
                            + " transition 'deep' -> 'init.deep-exit' of 'init.light-gate' is"
                            + " missing"), refused.violations()::toString));
        }

        @Test
        @DisplayName("a deep version of a template whose light gate lacks the ratchet owes no ratchet, and the light"
                + " route the removal opens is refused with INV-OUTLINE-2 instead")
        void deepWithoutRatchetOwesNoRatchet() {
            var refused = refused(atOutline(Lane.DEEP, "outline.deep", List.of(InvariantFixtures.WITHOUT_RATCHET)));

            assertAll(
                    () -> assertEquals(Refusal.INVARIANT_FAILED, refused.refusal()),
                    () -> assertTrue(refused.violations().stream().anyMatch(violation -> violation.startsWith(
                            "INV-OUTLINE-2 in 'init.light-gate'")), refused.violations()::toString),
                    () -> assertFalse(refused.violations().stream()
                            .anyMatch(violation -> violation.startsWith("INV-INIT-2")), refused.violations()::toString));
        }
    }

    /**
     * The per-lane rework of the plan unit's phase calls, over {@code plan/per-lane-rework.pmwf}: a {@code deep}
     * version prunes the light call states and the light outline route with {@code other_lane} while its own calls keep
     * routing their rework to {@code outline.deep}, a {@code light} version keeps both routes and routes the light
     * calls' rework to {@code outline.light-regate}.
     */
    @Nested
    @DisplayName("Per-lane rework")
    class PerLaneRework {

        private static final InstancePosition AT_INIT = InstancePosition.at("init.ready");

        private static PlanFacts factsOf(Lane lane) {
            return facts().lane(lane).build();
        }

        private static PlanInstance version1(Lane lane) {
            return composed(PlanFixtures.perLaneComposer().compose(PlanFixtures.perLaneRequest(
                    CompositionEvent.START, factsOf(lane), Optional.empty(), AT_INIT)));
        }

        private static PlanInstance outline(Lane lane) {
            return composed(PlanFixtures.perLaneComposer().compose(PlanFixtures.perLaneRequest(
                    CompositionEvent.OUTLINE, factsOf(lane), Optional.of(version1(lane)),
                    InstancePosition.at(lane == Lane.DEEP ? "outline.deep" : "outline.light"))));
        }

        @Test
        @DisplayName("a deep version prunes the light call states and the light outline route with other_lane, keeps"
                + " its own calls' rework on outline.deep, and verifies")
        void deepPrunesLightCalls() {
            var instance = outline(Lane.DEEP);

            assertAll(
                    () -> assertEquals(CompositionEvent.OUTLINE, instance.header().event()),
                    () -> assertEquals(List.of("init.light-gate", "outline.light", "outline.light-regate",
                                    "outline.light-review", "outline.light-approve", "phases.plan-light",
                                    "phases.execute-light"),
                            otherLane(instance.header()).stream().map(PrunedElement::id).toList()),
                    () -> assertTrue(instance.unit().states().keySet().containsAll(List.of("phases.plan",
                            "phases.execute", "outline.deep", "refine.baseline-reconcile"))),
                    () -> assertTrue(routes(instance, "phases.plan", "fix_requested", "outline.deep"),
                            "the deep plan call re-enters the deep authoring entry"),
                    () -> assertTrue(routes(instance, "phases.execute", "loop_back", "outline.deep"),
                            "the deep execute call re-enters the deep authoring entry"),
                    () -> assertEquals(List.of(), HeaderSchemaCheck.check(instance.header())));
        }

        @Test
        @DisplayName("a light version keeps both routes and routes the light calls' rework to outline.light-regate")
        void lightKeepsBothRoutes() {
            var instance = outline(Lane.LIGHT);

            assertAll(
                    () -> assertEquals(List.of(), otherLane(instance.header())),
                    () -> assertTrue(routes(instance, "phases.plan", "fix_requested", "outline.deep"),
                            "the deep call state keeps its own rework entry"),
                    () -> assertTrue(routes(instance, "phases.plan-light", "fix_requested", "outline.light-regate"),
                            "the light plan call re-enters the light authoring entry"),
                    () -> assertTrue(routes(instance, "phases.execute-light", "loop_back", "outline.light-regate"),
                            "the light execute call re-enters the light authoring entry"),
                    () -> assertTrue(routes(instance, "outline.light-review", "failed", "outline.light-regate"),
                            "the light review re-enters the light re-gate"),
                    () -> assertEquals(List.of(), HeaderSchemaCheck.check(instance.header())));
        }
    }

    /**
     * The version as the set of files the specification states: the plan unit's file and one file per composed phase
     * sub-workflow and bound axis variant, all named by the header by digest, a pruned call composing no file.
     */
    @Nested
    @DisplayName("Composed set")
    class ComposedSet {

        private static final InstancePosition AT_INIT = InstancePosition.at("init.begin");

        private static PlanInstance merged() {
            return composed(PlanFixtures.mergedComposer().compose(PlanFixtures.mergedRequest(CompositionEvent.START,
                    facts().build(), Optional.empty(), AT_INIT, List.of())));
        }

        private static PlanInstance perLane(CompositionEvent event, Lane lane, String position) {
            var previous = event == CompositionEvent.START ? Optional.<PlanInstance>empty()
                    : Optional.of(composed(PlanFixtures.perLaneComposer().compose(PlanFixtures.perLaneRequest(
                    CompositionEvent.START, facts().lane(lane).build(), Optional.empty(),
                    InstancePosition.at("init.ready")))));
            return composed(PlanFixtures.perLaneComposer().compose(PlanFixtures.perLaneRequest(event,
                    facts().lane(lane).build(), previous, InstancePosition.at(position))));
        }

        @Test
        @DisplayName("the header names the plan unit's file and one composed file per phase unit, each by the digest "
                + "of its text, and the version composes exactly those files")
        void headerNamesTheSet() {
            var instance = merged();
            var composedUnits = instance.header().inputs().templates().stream()
                    .filter(reference -> reference.file().filter(file -> file.startsWith("instance/"))
                            .isPresent())
                    .toList();

            assertAll(
                    () -> assertEquals(PlanInstanceStore.versionPath(1),
                            composedUnits.getFirst().file().orElseThrow()),
                    () -> assertTrue(composedUnits.size() > 2, composedUnits::toString),
                    () -> composedUnits.stream().skip(1).forEach(reference ->
                            assertEquals(reference.digest(), instance.composed(reference.unit()).orElseThrow()
                                    .digest())),
                    () -> assertEquals(instance.units().size(), composedUnits.size() - 1),
                    () -> instance.units().forEach(unit -> assertEquals(PlanFacts.sha256(unit.text()),
                            unit.digest())),
                    () -> assertEquals(List.of(), HeaderSchemaCheck.check(instance.header())));
        }

        @Test
        @DisplayName("a composed phase file keeps the id, type, entries and exits of its template, repeats the "
                + "version, and carries no instance line")
        void phaseFileKeepsItsTemplate() {
            var instance = merged();
            var phase = instance.units().stream().filter(unit -> unit.unit().startsWith("phase-")).findFirst()
                    .orElseThrow();
            var template = InvariantFixtures.passingUnits(UnitOrigin.PROJECT).get(phase.unit());

            assertAll(
                    () -> assertEquals(template.id(), phase.definition().id()),
                    () -> assertEquals(template.scope(), phase.definition().scope()),
                    () -> assertEquals(template.entryBindings(), phase.definition().entryBindings()),
                    () -> assertEquals(template.frontmatter().entries(), phase.definition().frontmatter().entries()),
                    () -> assertEquals(template.frontmatter().exits(), phase.definition().frontmatter().exits()),
                    () -> assertEquals(UnitType.SUBWORKFLOW, phase.definition().frontmatter().type()),
                    () -> assertEquals(1, phase.version()),
                    () -> assertTrue(phase.definition().instance().isEmpty()),
                    () -> assertTrue(phase.text().startsWith("---\n")),
                    () -> assertTrue(phase.text().contains("version 1 format 1"), phase.text()));
        }

        @Test
        @DisplayName("a pruned call composes no file: the light calls of a deep version have neither a call state nor "
                + "a file, and a light version keeps both routes and their files")
        void prunedCallComposesNoFile() {
            var deep = perLane(CompositionEvent.OUTLINE, Lane.DEEP, "outline.deep");
            var light = perLane(CompositionEvent.OUTLINE, Lane.LIGHT, "outline.light");

            assertAll(
                    () -> assertFalse(deep.unit().states().containsKey("phases.plan-light")),
                    () -> assertTrue(deep.composed("phase-plan-light").isEmpty(), deep.units()::toString),
                    () -> assertTrue(deep.composed("phase-execute-light").isEmpty(), deep.units()::toString),
                    () -> assertTrue(deep.composed("phase-plan").isPresent(), deep.units()::toString),
                    () -> assertTrue(light.composed("phase-plan-light").isPresent(), light.units()::toString),
                    () -> assertTrue(light.composed("phase-execute-light").isPresent(), light.units()::toString),
                    () -> assertEquals(deep.units().size() + 2, light.units().size(),
                            () -> deep.units() + " / " + light.units()));
        }

        @Test
        @DisplayName("the shared states before the lane split survive in both lanes' versions, and only the other "
                + "lane's call states are pruned")
        void sharedStatesSurvive() {
            var deep = perLane(CompositionEvent.OUTLINE, Lane.DEEP, "outline.deep");
            var light = perLane(CompositionEvent.OUTLINE, Lane.LIGHT, "outline.light");

            assertAll(
                    () -> assertTrue(deep.unit().states().containsKey("refine.baseline-reconcile"),
                            deep.unit().states().keySet()::toString),
                    () -> assertTrue(light.unit().states().containsKey("refine.baseline-reconcile"),
                            light.unit().states().keySet()::toString),
                    () -> assertTrue(deep.unit().states().containsKey("outline.deep")),
                    () -> assertTrue(light.unit().states().containsKey("outline.deep")),
                    () -> assertTrue(light.unit().states().containsKey("phases.execute-light")),
                    () -> assertFalse(deep.unit().states().containsKey("phases.execute-light")));
        }

        @Test
        @DisplayName("a call pruned inside a composed phase reaches nothing: its callee is composed as no file and its "
                + "states are no position of the version")
        void prunedCallInsideAComposedUnit() {
            var rules = ((CompositionRules.Loaded) CompositionRules.parse("""
                    {"format_version": 1, "revision": "t1", "rules": [
                      {"id": "prune-land-call",
                       "when": {"fact": "lane", "equals": "deep"},
                       "then": {"prune": [{"element": "call", "id": "execute.land-entry/land",
                                           "reason": "other_lane"}]}}]}
                    """.getBytes(StandardCharsets.UTF_8))).rules();
            var instance = composed(PlanFixtures.mergedComposer(rules).compose(PlanFixtures.mergedRequest(
                    CompositionEvent.START, facts().build(), Optional.empty(), AT_INIT, List.of())));
            var refused = refused(PlanFixtures.mergedComposer(rules).compose(PlanFixtures.mergedRequest(
                    CompositionEvent.START, facts().build(), Optional.empty(),
                    InstancePosition.at("land.verify-sweep"), List.of())));

            assertAll(
                    () -> assertTrue(instance.composed("phase-5-execute").isPresent(), instance.units()::toString),
                    () -> assertTrue(instance.composed("land").isEmpty(),
                            () -> "land is reached only through a pruned call: " + instance.units()),
                    () -> assertEquals(Refusal.POSITION_NOT_CONTAINED, refused.refusal()),
                    () -> assertEquals(List.of("position: version 1 does not contain the state 'land.verify-sweep'"),
                            refused.violations()),
                    () -> assertTrue(instance.header().pruned().stream().anyMatch(pruned ->
                                    pruned.element() == PrunedElement.Kind.CALL
                                            && "execute.land-entry/land".equals(pruned.id())
                                            && pruned.reason() == PruneReason.OTHER_LANE),
                            instance.header().pruned()::toString),
                    () -> assertEquals(PlanProcessStates.State.NOT_IN_THIS_PLAN,
                            PlanProcessStates.derive(instance, List.of()).stream()
                                    .filter(node -> "execute.land-entry".equals(node.node())).findFirst()
                                    .orElseThrow().state()));
        }

        @Test
        @DisplayName("a state only a pruned call reaches is no position of the version, so a version that no longer "
                + "contains the current state is refused position_not_contained")
        void positionOverTheSet() {
            var refused = refused(PlanFixtures.perLaneComposer().compose(PlanFixtures.perLaneRequest(
                    CompositionEvent.OUTLINE, facts().lane(Lane.DEEP).build(),
                    Optional.of(perLane(CompositionEvent.START, Lane.DEEP, "init.ready")),
                    InstancePosition.at("phase-plan-light.begin"))));

            assertAll(
                    () -> assertEquals(Refusal.POSITION_NOT_CONTAINED, refused.refusal()),
                    () -> assertTrue(refused.violations().contains(
                                    "position: version 2 does not contain the state 'phase-plan-light.begin'"),
                            refused.violations()::toString));
        }
    }

    @Nested
    @DisplayName("Refusals")
    class Refusals {

        private static final InstancePosition AT_INIT = InstancePosition.at("init.begin");

        /** The facts under which the shipped rule {@code merge-by-operator} prunes the merge execution. */
        private static PlanFacts mergePruned() {
            return asking(PlanFacts.Level.PLAN, EVERY_PHASE).build();
        }

        /** @return version 1 of the merged fixture units, a deep plan */
        private static PlanInstance mergedInForce() {
            return composed(PlanFixtures.mergedComposer().compose(PlanFixtures.mergedRequest(CompositionEvent.START,
                    facts().build(), Optional.empty(), AT_INIT, List.of())));
        }

        /**
         * @return the composition, after the version in force, of a light plan whose adopted template lacks the
         *         {@code light -> deep} ratchet of {@code init.light-gate}, which INV-INIT-2 refuses
         */
        private static CompositionResult withoutRatchet(PlanInstance inForce) {
            return PlanFixtures.mergedComposer().compose(PlanFixtures.mergedRequest(CompositionEvent.CONFIG_ADOPTED,
                    facts().lane(Lane.LIGHT).build(), Optional.of(inForce), AT_INIT,
                    List.of(InvariantFixtures.WITHOUT_RATCHET)));
        }

        @Test
        @DisplayName("a version without the current state is refused position_not_contained")
        void currentStateMissing() {
            var refused = refused(composer().compose(request(CompositionEvent.CONFIG_ADOPTED,
                    mergePruned(), Optional.of(version1()), List.of(),
                    InstancePosition.at("finalize.merge-dispatch"))));

            assertAll(
                    () -> assertEquals(Refusal.POSITION_NOT_CONTAINED, refused.refusal()),
                    () -> assertEquals("position_not_contained", refused.refusal().wireName()),
                    () -> assertTrue(refused.violations().getFirst().contains("finalize.merge-dispatch"),
                            refused.violations()::toString));
        }

        @Test
        @DisplayName("a version without a state of the call stack is refused position_not_contained")
        void callStackStateMissing() {
            var refused = refused(composer().compose(request(CompositionEvent.WAIVER,
                    mergePruned(), Optional.of(version1()), List.of(),
                    new InstancePosition("finalize.push-barrier", List.of("finalize.merge-dispatch")))));

            assertEquals(Refusal.POSITION_NOT_CONTAINED, refused.refusal());
        }

        @Test
        @DisplayName("a version that breaks a compiled invariant is refused invariant_failed")
        void invariantFailed() {
            var refused = refused(withoutRatchet(mergedInForce()));

            assertAll(
                    () -> assertEquals(Refusal.INVARIANT_FAILED, refused.refusal()),
                    () -> assertTrue(refused.violations().stream()
                                    .anyMatch(violation -> violation.startsWith("INV-INIT-2 in 'init.light-gate'")),
                            refused.violations()::toString));
        }

        @Test
        @DisplayName("a header that breaks its schema is refused invariant_failed")
        void headerSchema() {
            var refused = refused(composer().compose(request("X", CompositionEvent.START, facts().build(),
                    Optional.empty(), List.of(), AT_START)));

            assertAll(
                    () -> assertEquals(Refusal.INVARIANT_FAILED, refused.refusal()),
                    () -> assertTrue(refused.violations().stream()
                                    .anyMatch(violation -> violation.startsWith("header '/plan_id'")),
                            refused.violations()::toString));
        }

        @Test
        @DisplayName("a refused version composes nothing: the next composition follows the version in force")
        void versionInForceKept() {
            var inForce = mergedInForce();
            refused(withoutRatchet(inForce));

            var next = composed(PlanFixtures.mergedComposer().compose(PlanFixtures.mergedRequest(
                    CompositionEvent.WAIVER, facts().build(), Optional.of(inForce), AT_INIT, List.of())));

            assertEquals(2, next.version());
        }
    }
}
