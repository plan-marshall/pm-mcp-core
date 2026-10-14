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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import de.planmarshall.core.spi.AwaitedEvent;
import de.planmarshall.core.spi.OutcomeClass;
import de.planmarshall.core.spi.Primitive;
import de.planmarshall.core.spi.PrimitiveOutcome;
import de.planmarshall.core.spi.PrimitiveRegistry;
import de.planmarshall.core.spi.PrimitiveResult;
import de.planmarshall.core.spi.WorkflowExecutionContext;
import de.planmarshall.workflow.ast.StateMigration;
import de.planmarshall.workflow.ast.StateNode;
import de.planmarshall.workflow.ast.UnitOrigin;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.dsl.ParseResult;
import de.planmarshall.workflow.dsl.WorkflowParser;
import de.planmarshall.workflow.finalize.FinalizeStepDescriptor;
import de.planmarshall.workflow.finalize.FinalizeStepDescriptor.Lane;
import de.planmarshall.workflow.finalize.FinalizeStepDescriptor.LaneClass;

/**
 * The passing units of the compiled-invariant tests and their inputs, read from {@code unit-fixtures/defaults/}. The
 * units are written for the tests and never copied from the shipped content of the product (PM-IMPL-5): the seven
 * phase files of the plan unit, merged here into the unit {@code plan}, and the project-ownable sub-workflows
 * {@code adjudicate.decisions}, {@code replan}, {@code git.commit_and_push} and {@code remediation.qgate-findings}.
 * <p>
 * The units name the primitives of {@link #registry()} and the entity kinds of {@link #entityKinds()}, which are
 * written for the tests as well. A refusing unit is the passing source with one named {@link Mutation}: a text that
 * occurs exactly once in one file, replaced by another.
 * <p>
 * The tests of the unit loaders in {@code workflow.unit} read the same units and registries, so the members they use
 * are public.
 */
public final class InvariantFixtures {

    /** The classpath directory of the passing units. */
    public static final String ROOT = "unit-fixtures/defaults/";

    /** The phase files of the plan unit, in merge order. */
    public static final List<String> PLAN_FILES = List.of("init.pmwf", "refine.pmwf", "outline.pmwf", "plan.pmwf",
            "execute.pmwf", "land.pmwf", "finalize.pmwf");

    /** The file of the adjudication unit. */
    public static final String ADJUDICATION_FILE = "adjudicate-decisions.pmwf";

    /** The file of the re-planning unit. */
    public static final String REPLAN_FILE = "replan.pmwf";

    /** The file of the commit sub-workflow. */
    public static final String GIT_FILE = "git-commit-and-push.pmwf";

    /** The file of the Q-Gate remediation unit. */
    public static final String REMEDIATION_FILE = "remediation-qgate-findings.pmwf";

    /** The files of the project-ownable sub-workflows. */
    public static final List<String> SUB_UNIT_FILES = List.of(ADJUDICATION_FILE, REPLAN_FILE, GIT_FILE,
            REMEDIATION_FILE);

    /** The identifier of the merged plan unit. */
    static final String PLAN_UNIT = "plan";

    private InvariantFixtures() {
    }

    /**
     * @param file the name of a fixture file, for example {@code init.pmwf}
     * @return the text of the passing fixture
     */
    public static String read(String file) {
        try (var in = InvariantFixtures.class.getClassLoader().getResourceAsStream(ROOT + file)) {
            Objects.requireNonNull(in, () -> "missing fixture " + ROOT + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @return the text of every fixture file by name, the phase files first in merge order
     */
    static Map<String, String> sources() {
        var sources = new LinkedHashMap<String, String>();
        PLAN_FILES.forEach(file -> sources.put(file, read(file)));
        SUB_UNIT_FILES.forEach(file -> sources.put(file, read(file)));
        return sources;
    }

    /**
     * Parses one file; it must follow the grammar.
     *
     * @param file   the file name, the resource name of every source location of the tree
     * @param source the text
     * @param origin the origin the file is parsed with
     * @return the tree
     */
    static WorkflowDefinition parse(String file, String source, UnitOrigin origin) {
        var result = WorkflowParser.parse(file, source, origin);
        return assertInstanceOf(ParseResult.Parsed.class, result, () -> file + " was refused: " + result).definition();
    }

    /**
     * Merges the phase files of the plan unit for the tests: the states and migrations are the union, the start state,
     * the header and the origin are those of the first part, the version is the sum of the part versions, recorded
     * per file in {@code partVersions}.
     *
     * @param parts the parsed phase files, in merge order
     * @return the merged plan unit
     */
    static WorkflowDefinition mergePlan(List<WorkflowDefinition> parts) {
        var first = parts.getFirst();
        var states = new LinkedHashMap<String, StateNode>();
        var migrations = new ArrayList<StateMigration>();
        var partVersions = new LinkedHashMap<String, Integer>();
        var version = 0;
        for (var part : parts) {
            part.states().forEach((id, state) -> {
                if (states.putIfAbsent(id, state) != null) {
                    throw new IllegalStateException("the state '%s' is declared in two phase files".formatted(id));
                }
            });
            migrations.addAll(part.migrations());
            partVersions.put(part.location().resourceName(), part.version());
            version += part.version();
        }
        return new WorkflowDefinition(PLAN_UNIT, version, first.scope(), List.of(), Optional.empty(),
                Optional.empty(), Map.of(), Map.of(), partVersions, first.formatVersion(), first.origin(), Map.of(),
                first.startState(), migrations, states, first.location());
    }

    /**
     * Parses the files and merges the phase files into the plan unit.
     *
     * @param sources the text of every fixture file by name
     * @param origin  the origin the files are parsed with
     * @return the units by identifier, the plan unit first
     */
    static Map<String, WorkflowDefinition> units(Map<String, String> sources, UnitOrigin origin) {
        var units = new LinkedHashMap<String, WorkflowDefinition>();
        var parts = PLAN_FILES.stream().map(file -> parse(file, sources.get(file), origin)).toList();
        units.put(PLAN_UNIT, mergePlan(parts));
        for (var file : SUB_UNIT_FILES) {
            var unit = parse(file, sources.get(file), origin);
            units.put(unit.id(), unit);
        }
        return units;
    }

    /**
     * @return the passing units by identifier, parsed as default units
     */
    static Map<String, WorkflowDefinition> defaultUnits() {
        return units(sources(), UnitOrigin.DEFAULT);
    }

    /**
     * @param file the name of a fixture file
     * @return the identifier of the unit the file belongs to
     */
    static String unitOf(String file) {
        if (PLAN_FILES.contains(file)) {
            return PLAN_UNIT;
        }
        return parse(file, read(file), UnitOrigin.DEFAULT).id();
    }

    /**
     * @param mutation a named change of one file
     * @return the unit of the changed file, parsed as a project unit and merged when it is a phase file
     */
    static WorkflowDefinition mutatedUnit(Mutation mutation) {
        var sources = sources();
        sources.put(mutation.file(), mutation.apply(sources.get(mutation.file())));
        return units(sources, UnitOrigin.PROJECT).get(unitOf(mutation.file()));
    }

    /**
     * @param text   the text of a file
     * @param anchor a text that occurs exactly once in it
     * @return the line, counted from 1, on which the anchor begins
     */
    public static int lineOf(String text, String anchor) {
        var index = uniqueIndex(text, anchor);
        return (int) text.substring(0, index).chars().filter(character -> character == '\n').count() + 1;
    }

    private static int uniqueIndex(String text, String fragment) {
        var index = text.indexOf(fragment);
        if (index < 0 || text.indexOf(fragment, index + 1) >= 0) {
            throw new IllegalArgumentException("'%s' does not occur exactly once".formatted(fragment));
        }
        return index;
    }

    /**
     * @return the primitives the fixture units name: those of {@link TestPrimitives} they use and the ones written
     *         for these tests
     */
    public static PrimitiveRegistry registry() {
        return new PrimitiveRegistry(List.of(
                new TestPrimitives.Fixed<>(TestPrimitives.PROCEED, TestPrimitives.NoParams.class,
                        TestPrimitives.DoneOutcome.class),
                new TestPrimitives.Fixed<>(TestPrimitives.BUILD, TestPrimitives.NoParams.class,
                        TestPrimitives.JobOutcome.class),
                new TestPrimitives.Fixed<>(TestPrimitives.EVALUATE_CHANGE, TestPrimitives.NoParams.class,
                        TestPrimitives.EvaluateChangeOutcome.class),
                new TestPrimitives.Fixed<>(TestPrimitives.EVALUATE_JUDGEMENT, TestPrimitives.NoParams.class,
                        TestPrimitives.JudgementOutcome.class),
                new Declared<>("test.classify", ClassifyOutcome.class),
                new Declared<>("test.lane", LaneOutcome.class),
                new Declared<>("test.gate", GateOutcome.class),
                new Declared<>("test.analyze", AnalyzeOutcome.class),
                new Declared<>("test.qgate", QgateOutcome.class),
                new Declared<>("test.route", RouteOutcome.class),
                new Declared<>("test.next-task", NextTaskOutcome.class),
                new Declared<>("test.merge", MergeOutcome.class),
                new Declared<>("test.deliver", DeliverOutcome.class),
                new Declared<>("test.evaluate-decision", DecisionOutcome.class),
                new Declared<>(TestPrimitives.CHECK_BUDGETS, BudgetOutcome.class),
                new Declared<>("test.conservative", ConservativeOutcome.class),
                new Declared<>("remediation.offer-actions", OfferOutcome.class),
                new Declared<>("test.gather", GatherOutcome.class, AwaitedEvent.NONE, Set.of("qgate")),
                new Declared<>("test.waiver-wait", WaiverWaitOutcome.class, AwaitedEvent.OPERATOR_WAIVER,
                        Set.of("qgate")),
                new Declared<>("git.stage-and-commit", CommitOutcome.class),
                new Declared<>("git.execute-push", PushOutcome.class),
                new Declared<>("git.push-with-lease", LeasePushOutcome.class)));
    }

    /**
     * @return the default entity kinds plus {@code change}, {@code vote} and {@code remediation-decision}, written for
     *         these tests
     */
    public static EntityKindRegistry entityKinds() {
        return EntityKindRegistry.defaults()
                .with(new EntityKind(TestPrimitives.CHANGE_KIND, "entity", TestPrimitives.EVALUATE_CHANGE))
                .with(new EntityKind("vote", Optional.empty(), TestPrimitives.EVALUATE_JUDGEMENT))
                .with(new EntityKind("remediation-decision", Optional.empty(), "test.evaluate-decision"));
    }

    /**
     * @param units the sub-workflows a call may name
     * @return a catalog of the units and of the entity unit {@code entity}
     */
    static WorkflowCatalog catalog(Collection<WorkflowDefinition> units) {
        var definitions = new ArrayList<>(units);
        definitions.add(entityUnit());
        return new WorkflowCatalog(definitions);
    }

    /**
     * @return the entity unit {@code entity}, which no project owns and the {@code job llm} states of the fixtures
     *         name
     */
    public static WorkflowDefinition entityUnit() {
        return parse("entity.pmwf", """
                workflow entity version 1 format 1
                scope entity
                start entity.completed

                state entity.completed terminal submitted success
                    phase inherit
                """, UnitOrigin.DEFAULT);
    }

    /**
     * @return the static verifier over the fixture registries and the catalog of the passing sub-workflows, without a
     *         released baseline
     */
    static WorkflowValidator validator() {
        var subUnits = defaultUnits().values().stream().filter(unit -> !PLAN_UNIT.equals(unit.id())).toList();
        return new WorkflowValidator(registry(), List.of(), entityKinds(), QuestionClassRegistry.defaults(),
                SchemaRegistry.defaults(), catalog(subUnits));
    }

    /**
     * @return the compiled invariants with the passing units as defaults and {@link #defaultRoster()}
     */
    static CompiledInvariants invariants() {
        return new CompiledInvariants(registry(), defaultUnits().values(), defaultRoster());
    }

    /**
     * The default finalize roster of the tests: the mandatory steps {@code default:create-pr},
     * {@code default:branch-cleanup}, {@code default:emit-landing} and {@code default:archive-plan}, which the
     * finalize fixture completes, and the prunable {@code default:sonar-roundtrip}, which it does not.
     *
     * @return the roster; it passes the finalize pipeline validator
     */
    public static List<FinalizeStepDescriptor> defaultRoster() {
        return List.of(
                step("default:create-pr", 20, LaneClass.CORE, false, List.of(), List.of()),
                step("default:sonar-roundtrip", 40, LaneClass.PRUNABLE, false, List.of(), List.of()),
                step("default:branch-cleanup", 70, LaneClass.CORE, false, List.of("worktree"), List.of("worktree")),
                step("default:emit-landing", 1000, LaneClass.DERIVED_STATE, true, List.of("plan-directory"),
                        List.of()),
                step("default:archive-plan", 1100, LaneClass.CORE, false, List.of("plan-directory"),
                        List.of("plan-directory")));
    }

    /**
     * @param name          the step id
     * @param order         the order of the step
     * @param laneClass     the class of its lane
     * @param postRunReview whether it reviews the run after the merge
     * @param reads         the artifacts it reads
     * @param destroys      the artifacts it destroys
     * @return a descriptor that declares nothing else
     */
    public static FinalizeStepDescriptor step(String name, int order, LaneClass laneClass, boolean postRunReview,
            List<String> reads, List<String> destroys) {
        return new FinalizeStepDescriptor(name, order, true, Set.of(), "a step written for the test", false, false,
                Optional.empty(), List.of(), false, postRunReview, reads, destroys, List.of(),
                Optional.of(new Lane(laneClass, Optional.empty(), Optional.empty(), "S")), List.of(), List.of(),
                Optional.empty(), List.of(), Optional.empty(), List.of());
    }

    /**
     * One named change of a passing fixture: a text that occurs exactly once in the file, replaced by another.
     *
     * @param name    what the change does to the unit
     * @param file    the fixture file it changes
     * @param find    the text it replaces
     * @param replace the text it puts in its place
     */
    record Mutation(String name, String file, String find, String replace) {

        /**
         * @param source the text of the file
         * @return the changed text
         * @throws IllegalArgumentException if the text to replace does not occur exactly once
         */
        String apply(String source) {
            var index = uniqueIndex(source, find);
            return source.substring(0, index) + replace + source.substring(index + find.length());
        }

        /**
         * @return the changed text of the file
         */
        String applied() {
            return apply(read(file));
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /** A primitive written for these tests: its outcomes, its awaited event and its waivable gates. */
    static final class Declared<R extends Enum<R> & PrimitiveOutcome> implements Primitive<TestPrimitives.NoParams, R> {

        private final String id;
        private final Class<R> outcomeType;
        private final AwaitedEvent awaitedEvent;
        private final Set<String> waivableGates;

        Declared(String id, Class<R> outcomeType) {
            this(id, outcomeType, AwaitedEvent.NONE, Set.of());
        }

        Declared(String id, Class<R> outcomeType, AwaitedEvent awaitedEvent, Set<String> waivableGates) {
            this.id = id;
            this.outcomeType = outcomeType;
            this.awaitedEvent = awaitedEvent;
            this.waivableGates = waivableGates;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Class<TestPrimitives.NoParams> parameterType() {
            return TestPrimitives.NoParams.class;
        }

        @Override
        public Class<R> outcomeType() {
            return outcomeType;
        }

        @Override
        public PrimitiveResult<R> execute(WorkflowExecutionContext context, TestPrimitives.NoParams parameters) {
            return new PrimitiveResult<>(outcomeType.getEnumConstants()[0], Map.of(), Optional.empty());
        }

        @Override
        public AwaitedEvent awaitedEvent() {
            return awaitedEvent;
        }

        @Override
        public Set<String> waivableGates() {
            return waivableGates;
        }
    }

    enum ClassifyOutcome implements PrimitiveOutcome {
        PROCEED("proceed"), UNCLEAR("unclear");

        private final String wireName;

        ClassifyOutcome(String wireName) {
            this.wireName = wireName;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.SUCCESS;
        }
    }

    enum LaneOutcome implements PrimitiveOutcome {
        DEEP("deep"), LIGHT("light");

        private final String wireName;

        LaneOutcome(String wireName) {
            this.wireName = wireName;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.SUCCESS;
        }
    }

    enum GateOutcome implements PrimitiveOutcome {
        PASS("pass"), DEEP("deep");

        private final String wireName;

        GateOutcome(String wireName) {
            this.wireName = wireName;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.SUCCESS;
        }
    }

    enum AnalyzeOutcome implements PrimitiveOutcome {
        CONFIDENT("confident"), LESSON("lesson");

        private final String wireName;

        AnalyzeOutcome(String wireName) {
            this.wireName = wireName;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.SUCCESS;
        }
    }

    enum QgateOutcome implements PrimitiveOutcome {
        PASS("pass"), BLOCKING("blocking");

        private final String wireName;

        QgateOutcome(String wireName) {
            this.wireName = wireName;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.SUCCESS;
        }
    }

    enum RouteOutcome implements PrimitiveOutcome {
        EXECUTE("execute"), LAND("land");

        private final String wireName;

        RouteOutcome(String wireName) {
            this.wireName = wireName;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.SUCCESS;
        }
    }

    enum NextTaskOutcome implements PrimitiveOutcome {
        NEXT_TASK("next_task"), DELIVERABLE_DONE("deliverable_done");

        private final String wireName;

        NextTaskOutcome(String wireName) {
            this.wireName = wireName;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.SUCCESS;
        }
    }

    enum MergeOutcome implements PrimitiveOutcome {
        MERGED("merged", OutcomeClass.SUCCESS), NOT_MERGED("not_merged", OutcomeClass.FAILURE);

        private final String wireName;
        private final OutcomeClass outcomeClass;

        MergeOutcome(String wireName, OutcomeClass outcomeClass) {
            this.wireName = wireName;
            this.outcomeClass = outcomeClass;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        @Override
        public OutcomeClass outcomeClass() {
            return outcomeClass;
        }
    }

    enum DeliverOutcome implements PrimitiveOutcome {
        COMPLETED("completed"), HANDED_BACK("handed_back"), RETURNED("returned");

        private final String wireName;

        DeliverOutcome(String wireName) {
            this.wireName = wireName;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.SUCCESS;
        }
    }

    /** The outcomes of the return evaluation of a remediation decision. */
    enum DecisionOutcome implements PrimitiveOutcome {
        RETRY("retry", OutcomeClass.SUCCESS), FIX("fix", OutcomeClass.SUCCESS),
        LOOP_BACK("loop_back", OutcomeClass.SUCCESS), REQUEST_WAIVER("request_waiver", OutcomeClass.SUCCESS),
        INFEASIBLE("infeasible", OutcomeClass.FAILURE);

        private final String wireName;
        private final OutcomeClass outcomeClass;

        DecisionOutcome(String wireName, OutcomeClass outcomeClass) {
            this.wireName = wireName;
            this.outcomeClass = outcomeClass;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        @Override
        public OutcomeClass outcomeClass() {
            return outcomeClass;
        }
    }

    /** The outcomes of the budget re-check {@code remediation.check-budgets}, as the Unit Template names them. */
    enum BudgetOutcome implements PrimitiveOutcome {
        BUDGET_LEFT("budget_left", OutcomeClass.SUCCESS), EXHAUSTED_WAIT("exhausted_wait", OutcomeClass.SUCCESS),
        EXHAUSTED("exhausted", OutcomeClass.FAILURE);

        private final String wireName;
        private final OutcomeClass outcomeClass;

        BudgetOutcome(String wireName, OutcomeClass outcomeClass) {
            this.wireName = wireName;
            this.outcomeClass = outcomeClass;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        @Override
        public OutcomeClass outcomeClass() {
            return outcomeClass;
        }
    }

    enum ConservativeOutcome implements PrimitiveOutcome {
        RETRY("retry", OutcomeClass.SUCCESS), GIVE_UP("give_up", OutcomeClass.FAILURE);

        private final String wireName;
        private final OutcomeClass outcomeClass;

        ConservativeOutcome(String wireName, OutcomeClass outcomeClass) {
            this.wireName = wireName;
            this.outcomeClass = outcomeClass;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        @Override
        public OutcomeClass outcomeClass() {
            return outcomeClass;
        }
    }

    enum OfferOutcome implements PrimitiveOutcome {
        CHOICE("choice", OutcomeClass.SUCCESS), NONE("none", OutcomeClass.FAILURE);

        private final String wireName;
        private final OutcomeClass outcomeClass;

        OfferOutcome(String wireName, OutcomeClass outcomeClass) {
            this.wireName = wireName;
            this.outcomeClass = outcomeClass;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        @Override
        public OutcomeClass outcomeClass() {
            return outcomeClass;
        }
    }

    enum GatherOutcome implements PrimitiveOutcome {
        FINDINGS("findings"), ALL_WAIVED("all_waived"), CLEAN("clean");

        private final String wireName;

        GatherOutcome(String wireName) {
            this.wireName = wireName;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.SUCCESS;
        }
    }

    enum WaiverWaitOutcome implements PrimitiveOutcome {
        WAIVED("waived", OutcomeClass.SUCCESS), EXPIRED("expired", OutcomeClass.FAILURE);

        private final String wireName;
        private final OutcomeClass outcomeClass;

        WaiverWaitOutcome(String wireName, OutcomeClass outcomeClass) {
            this.wireName = wireName;
            this.outcomeClass = outcomeClass;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        @Override
        public OutcomeClass outcomeClass() {
            return outcomeClass;
        }
    }

    enum CommitOutcome implements PrimitiveOutcome {
        COMMITTED;

        @Override
        public String wireName() {
            return "committed";
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.SUCCESS;
        }
    }

    enum PushOutcome implements PrimitiveOutcome {
        PUSHED("pushed", OutcomeClass.SUCCESS), SKIPPED("skipped", OutcomeClass.SKIP),
        REJECTED("rejected", OutcomeClass.FAILURE), PUSH_STATE_UNKNOWN("push_state_unknown", OutcomeClass.INDETERMINATE);

        private final String wireName;
        private final OutcomeClass outcomeClass;

        PushOutcome(String wireName, OutcomeClass outcomeClass) {
            this.wireName = wireName;
            this.outcomeClass = outcomeClass;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        @Override
        public OutcomeClass outcomeClass() {
            return outcomeClass;
        }
    }

    enum LeasePushOutcome implements PrimitiveOutcome {
        PUSHED("pushed", OutcomeClass.SUCCESS), PUSH_STATE_UNKNOWN("push_state_unknown", OutcomeClass.INDETERMINATE);

        private final String wireName;
        private final OutcomeClass outcomeClass;

        LeasePushOutcome(String wireName, OutcomeClass outcomeClass) {
            this.wireName = wireName;
            this.outcomeClass = outcomeClass;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        @Override
        public OutcomeClass outcomeClass() {
            return outcomeClass;
        }
    }
}
