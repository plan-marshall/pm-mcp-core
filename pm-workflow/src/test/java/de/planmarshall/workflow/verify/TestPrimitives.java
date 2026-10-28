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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import de.planmarshall.core.spi.AwaitedEvent;
import de.planmarshall.core.spi.CycleMeasure;
import de.planmarshall.core.spi.FreeParam;
import de.planmarshall.core.spi.OutcomeClass;
import de.planmarshall.core.spi.Primitive;
import de.planmarshall.core.spi.PrimitiveOutcome;
import de.planmarshall.core.spi.PrimitiveRegistry;
import de.planmarshall.core.spi.PrimitiveResult;
import de.planmarshall.core.spi.WorkflowExecutionContext;
import de.planmarshall.workflow.ast.WorkflowDefinition;

/**
 * Primitives written for the verifier tests and the registry that holds them. The fixtures under {@code verify/} name
 * these primitives and no others; {@code test.missing} is the one id they name that is never registered.
 */
final class TestPrimitives {

    /** An {@code auto} primitive without free parameters: {@code done}, {@code skipped}. */
    static final String PREPARE = "test.prepare";

    /** A {@code job} and {@code composite_job} primitive: {@code succeeded}, {@code failed}, {@code cancelled}. */
    static final String BUILD = "test.build";

    /** A link primitive without free parameters: {@code done}. */
    static final String PROCEED = "test.proceed";

    /**
     * A link primitive with the bounded free text {@code note} ({@code 1..80}), the unbounded free text
     * {@code comment} and an engine-resolved {@code attempt}: {@code chosen}, {@code declined}.
     */
    static final String CHOOSE = "test.choose";

    /** A {@code facts} primitive: {@code done}. */
    static final String READ_FACTS = "test.read-facts";

    /** A {@code predicate:} primitive: {@code held}, {@code not_held}. */
    static final String READY = "test.ready";

    /** A return-evaluation primitive for entity kind change: {@code approved}. */
    static final String EVALUATE_CHANGE = "test.evaluate-change";

    /**
     * A return-evaluation primitive for the entity kind {@link #JUDGEMENT_KIND}: {@code approved} ({@code SUCCESS}),
     * {@code rejected} ({@code FAILURE}).
     */
    static final String EVALUATE_JUDGEMENT = "test.evaluate-judgement";

    /**
     * A return-evaluation primitive for the entity kind {@link #REPAIR_KIND}: {@code repaired} ({@code SUCCESS}),
     * {@code infeasible} ({@code FAILURE}), the task outcome Rule 9 admits {@code skipped} on by its name.
     */
    static final String EVALUATE_REPAIR = "test.evaluate-repair";

    /** A list primitive for each clause: {@code done}. */
    static final String LIST_CHANGES = "test.list-changes";

    /** A primitive for the {@code role each} clause, standing for one that returns role names: {@code done}. */
    static final String LIST_ROLES = "test.list-roles";

    /**
     * The primitive of a next-work state that may land an abort: {@code landed} ({@code SUCCESS}),
     * {@code abort_landed} ({@code FAILURE}).
     */
    static final String LAND_WORK = "test.land-work";

    /** A primitive that enqueues a job: {@code enqueued}, {@code skipped}. */
    static final String ENQUEUE = "test.enqueue";

    /** A primitive requiring artifact {@code test.artifact}: {@code done}. */
    static final String CONSUME = "test.consume";

    /** A primitive producing artifact {@code test.artifact}: {@code done}. */
    static final String PRODUCE = "test.produce";

    /**
     * A dispatch hub declaring {@link CycleMeasure#STEP_LIST}: {@code first}, {@code second}, {@code settled}, all of
     * class {@code SUCCESS}.
     */
    static final String NEXT_STEP = "test.next-step";

    /**
     * A next-work primitive declaring {@link CycleMeasure#ARCHIVE_STEPS}: {@code archive_uncommitted},
     * {@code archived}, both of class {@code SUCCESS}.
     */
    static final String NEXT_WORK = "test.next-work";

    /**
     * A primitive declaring {@link CycleMeasure#ATTEMPT_CAP} with the exhaustion outcome {@code exhausted} of class
     * {@code FAILURE}: {@code retry} ({@code SUCCESS}), {@code exhausted}.
     */
    static final String CAPPED = "test.capped";

    /** A primitive with an outcome that hands the step back: {@code fixed} ({@code SUCCESS}), {@code fix_requested}
     * ({@code UNSETTLED}). */
    static final String FIX = "test.fix";

    /**
     * The primitive of the budget re-check {@code remediation-<class>.budget-check} of a remediation unit, with the
     * outcomes of the Unit Template (Workflow DSL, 08-remediation-units): {@code budget_left} ({@code SUCCESS}),
     * {@code exhausted_wait} ({@code SUCCESS}), {@code exhausted} ({@code FAILURE}). The specification names the
     * outcomes, not their classes.
     */
    static final String CHECK_BUDGETS = "remediation.check-budgets";

    /** The remediation unit of the test catalog: exit contract {@code done}, {@code waived}, {@code stepped_out}. */
    static final String REMEDIATION = "remediation.test-waiver";

    /** The id the fixtures name for a primitive that is not registered. */
    static final String MISSING = "test.missing";

    /** The entity kind of the tests: entity unit {@code entity}, return evaluation {@link #EVALUATE_CHANGE}. */
    static final String CHANGE_KIND = "change";

    /** The entity kind of the tests whose entity unit {@code test.no-such-unit} the catalog does not hold. */
    static final String UNRESOLVED_KIND = "unresolved";

    /** The entity kind of the tests whose return-evaluation primitive {@link #MISSING} is not registered. */
    static final String UNEVALUATED_KIND = "unevaluated";

    /** The entity kind of the tests without an entity unit, return evaluation {@link #EVALUATE_CHANGE}. */
    static final String UNIT_LESS_KIND = "glance";

    /** The entity kind of the tests whose evaluation can reject: entity unit {@code entity}, {@link #EVALUATE_JUDGEMENT}. */
    static final String JUDGEMENT_KIND = "judgement";

    /** The entity kind of the tests whose task can end infeasible: entity unit {@code entity}, {@link #EVALUATE_REPAIR}. */
    static final String REPAIR_KIND = "repair";

    /** A workspace unit of the test catalog, which no call may name. */
    static final String WORKSPACE_UNIT = "test.workspace-unit";

    /** A sub-workflow that holds no link and calls {@link #LINKS_INNER}. */
    static final String LINKS_OUTER = "test.links-outer";

    /** A session-driven sub-workflow with a resting link state. */
    static final String LINKS_INNER = "test.links-inner";

    /** A plan unit of the test catalog that calls {@link #PLAN_ONLY_OUTER} and {@link #MIXED_CALLERS}. */
    static final String PLAN_CALLER = "test.plan-caller";

    /** An epic unit of the test catalog that calls {@link #MIXED_CALLERS}. */
    static final String EPIC_CALLER = "test.epic-caller";

    /** A sub-workflow only {@link #PLAN_CALLER} calls; it calls {@link #PLAN_ONLY_INNER}. */
    static final String PLAN_ONLY_OUTER = "test.plan-only-outer";

    /**
     * A sub-workflow reached only from the plan unit {@link #PLAN_CALLER}, through {@link #PLAN_ONLY_OUTER}; its task
     * state leaves {@code lost} undeclared (Rule 2, ADR 025).
     */
    static final String PLAN_ONLY_INNER = "test.plan-only-inner";

    /**
     * The sub-workflow of the refusing fixture {@code model-job-lost-mixed-callers.pmwf}, called by the plan unit
     * {@link #PLAN_CALLER} and by the epic unit {@link #EPIC_CALLER}; the catalog does not register it.
     */
    static final String MIXED_CALLERS = "verify.model-job-lost-mixed-callers";

    /** A sub-workflow with the two entries {@code review} and {@code repair}, so a call names one of them. */
    static final String TWO_ENTRIES = "test.two-entries";

    /** A unit of the type {@code task} and the scope {@code subworkflow}, which declares no entry. */
    static final String TASK_UNIT = "test.task-unit";

    private TestPrimitives() {
    }

    /**
     * @return the default entity kinds plus the kinds of the tests: {@link #CHANGE_KIND}, {@link #JUDGEMENT_KIND} and
     *         {@link #REPAIR_KIND}, whose entity unit the test catalog holds, {@link #UNRESOLVED_KIND}, whose entity
     *         unit it does not hold, {@link #UNEVALUATED_KIND}, whose return-evaluation primitive is not registered,
     *         and {@link #UNIT_LESS_KIND}, which has no entity unit
     */
    static EntityKindRegistry entityKinds() {
        return EntityKindRegistry.defaults()
                .with(new EntityKind(CHANGE_KIND, "entity", EVALUATE_CHANGE))
                .with(new EntityKind(JUDGEMENT_KIND, "entity", EVALUATE_JUDGEMENT))
                .with(new EntityKind(REPAIR_KIND, "entity", EVALUATE_REPAIR))
                .with(new EntityKind(UNRESOLVED_KIND, "test.no-such-unit", EVALUATE_CHANGE))
                .with(new EntityKind(UNEVALUATED_KIND, "entity", MISSING))
                .with(new EntityKind(UNIT_LESS_KIND, Optional.empty(), EVALUATE_CHANGE));
    }

    /**
     * @return a registry of every primitive of this class
     */
    static PrimitiveRegistry registry() {
        return new PrimitiveRegistry(List.of(
                new Fixed<>(PREPARE, NoParams.class, PrepareOutcome.class),
                new Fixed<>(BUILD, NoParams.class, JobOutcome.class),
                new Fixed<>(PROCEED, NoParams.class, DoneOutcome.class),
                new Fixed<>(CHOOSE, ChooseParams.class, ChooseOutcome.class),
                new Fixed<>(READ_FACTS, NoParams.class, DoneOutcome.class),
                new Fixed<>(READY, NoParams.class, PredicateOutcome.class),
                new Fixed<>(EVALUATE_CHANGE, NoParams.class, EvaluateChangeOutcome.class),
                new Fixed<>(EVALUATE_JUDGEMENT, NoParams.class, JudgementOutcome.class),
                new Fixed<>(EVALUATE_REPAIR, NoParams.class, RepairOutcome.class),
                new Fixed<>(LIST_CHANGES, NoParams.class, DoneOutcome.class),
                new Fixed<>(LIST_ROLES, NoParams.class, DoneOutcome.class),
                new Fixed<>(LAND_WORK, NoParams.class, LandOutcome.class),
                new Fixed<>(CHECK_BUDGETS, NoParams.class, BudgetOutcome.class),
                new EnqueuePrimitive(),
                new ArtifactConsumer(),
                new ArtifactProducer(),
                new Measured<>(NEXT_STEP, DispatchOutcome.class, CycleMeasure.STEP_LIST, Optional.empty()),
                new Measured<>(NEXT_WORK, ArchiveOutcome.class, CycleMeasure.ARCHIVE_STEPS, Optional.empty()),
                new Measured<>(CAPPED, AttemptOutcome.class, CycleMeasure.ATTEMPT_CAP, Optional.of("exhausted")),
                new Measured<>(FIX, FixOutcome.class, CycleMeasure.NONE, Optional.empty())));
    }

    /**
     * @return a catalog of the units the tests call: {@code git.commit_and_push} with the result schema
     *         {@code commit-result} (registered by {@link SchemaRegistry#defaults()}), the link-free call chain
     *         {@code test.depth1} to {@code test.depth5}, the self-calling {@code verify.call-cycle}, the remediation
     *         unit {@link #REMEDIATION}, the entity unit {@code entity} of {@link #CHANGE_KIND},
     *         {@link #LINKS_OUTER} calling {@link #LINKS_INNER}, the workspace unit {@link #WORKSPACE_UNIT}, and the
     *         caller chains of Rule 2's {@code lost} omission: the plan unit {@link #PLAN_CALLER} calling
     *         {@link #PLAN_ONLY_OUTER}, which calls {@link #PLAN_ONLY_INNER}, and {@link #MIXED_CALLERS}, which
     *         {@link #EPIC_CALLER} calls as well, the sub-workflow {@link #TWO_ENTRIES} with two entries and the
     *         entry-less {@link #TASK_UNIT}
     */
    static WorkflowCatalog catalog() {
        var commitAndPush = VerifyFixtures.parseSource("git.commit_and_push.pmwf", """
                ---
                name: git.commit_and_push
                description: A unit written for the tests of pm-workflow.
                type: subworkflow
                icon: workflow.default
                entry: main
                exit: done
                modification_date: 2026-01-15
                version: 0.1.0
                author: plan-marshall
                ---
                workflow git.commit_and_push version 1 format 1
                scope subworkflow
                param push : enum[enabled|disabled]
                param attempts : int[1..5]
                param targets : list<enum[origin|mirror]>
                result schema[commit-result]
                entry main -> git.push

                state git.push
                    auto test.prepare
                        done -> git.done
                        skipped -> git.stepped-out

                state git.done terminal done success

                state git.stepped-out terminal stepped_out stepped_out
                """);
        var depth5 = VerifyFixtures.parseSource("test.depth5.pmwf", """
                ---
                name: test.depth5
                description: A unit written for the tests of pm-workflow.
                type: subworkflow
                icon: workflow.default
                entry: main
                exit: done
                modification_date: 2026-01-15
                version: 0.1.0
                author: plan-marshall
                ---
                workflow test.depth5 version 1 format 1
                scope subworkflow
                entry main -> depth5.done

                state depth5.done terminal done success
                """);
        var depth4 = depthUnit(4, "test.depth5");
        var depth3 = depthUnit(3, "test.depth4");
        var depth2 = depthUnit(2, "test.depth3");
        var depth1 = depthUnit(1, "test.depth2");
        var callCycle = VerifyFixtures.parseSource("verify.call-cycle.pmwf", """
                ---
                name: verify.call-cycle
                description: A unit written for the tests of pm-workflow.
                type: subworkflow
                icon: workflow.default
                entry: main
                exit: done
                modification_date: 2026-01-15
                version: 0.1.0
                author: plan-marshall
                ---
                workflow verify.call-cycle version 1 format 1
                scope subworkflow
                entry main -> verify.entry

                state verify.entry
                    call verify.call-cycle()
                        done -> verify.done
                        stepped_out -> verify.stepped-out

                state verify.done terminal done success

                state verify.stepped-out terminal stepped_out stepped_out
                """);
        var entity = VerifyFixtures.parseSource("entity.pmwf", """
                ---
                name: entity
                description: A unit written for the tests of pm-workflow.
                type: task
                icon: workflow.default
                exit: submitted
                modification_date: 2026-01-15
                version: 0.1.0
                author: plan-marshall
                ---
                workflow entity version 1 format 1
                scope entity
                start entity.completed

                state entity.completed terminal submitted success
                """);
        var linksInner = VerifyFixtures.parseSource("test.links-inner.pmwf", """
                ---
                name: test.links-inner
                description: A unit written for the tests of pm-workflow.
                type: subworkflow
                icon: workflow.default
                entry: main
                exit: done
                modification_date: 2026-01-15
                version: 0.1.0
                author: plan-marshall
                ---
                workflow test.links-inner version 1 format 1
                scope subworkflow
                entry main -> inner.rest

                state inner.rest
                    skill pm/verify/start
                    link next proceed ()
                        do test.proceed
                            done -> inner.done

                state inner.done terminal done success
                """);
        var linksOuter = VerifyFixtures.parseSource("test.links-outer.pmwf", """
                ---
                name: test.links-outer
                description: A unit written for the tests of pm-workflow.
                type: subworkflow
                icon: workflow.default
                entry: main
                exit: done
                modification_date: 2026-01-15
                version: 0.1.0
                author: plan-marshall
                ---
                workflow test.links-outer version 1 format 1
                scope subworkflow
                entry main -> outer.step

                state outer.step
                    call test.links-inner()
                        done -> outer.done
                        stepped_out -> outer.stepped-out

                state outer.done terminal done success

                state outer.stepped-out terminal stepped_out stepped_out
                """);
        var remediation = VerifyFixtures.parseSource("remediation.test-waiver.pmwf", """
                ---
                name: remediation.test-waiver
                description: A unit written for the tests of pm-workflow.
                type: subworkflow
                icon: workflow.default
                entry: main
                exit: done
                exit: waived
                modification_date: 2026-01-15
                version: 0.1.0
                author: plan-marshall
                ---
                workflow remediation.test-waiver version 1 format 1
                scope subworkflow
                entry main -> remediation.decide

                state remediation.decide
                    auto test.prepare
                        done -> remediation.done
                        skipped -> remediation.waived

                state remediation.done terminal done success

                state remediation.waived terminal waived success
                """);
        var workspaceUnit = VerifyFixtures.parseSource("test.workspace-unit.pmwf", """
                ---
                name: test.workspace-unit
                description: A unit written for the tests of pm-workflow.
                type: system
                icon: workflow.default
                modification_date: 2026-01-15
                version: 0.1.0
                author: plan-marshall
                ---
                workflow test.workspace-unit version 1 format 1
                scope workspace
                start workspace.done

                state workspace.done terminal done success
                """);
        return new WorkflowCatalog(List.of(commitAndPush, depth1, depth2, depth3, depth4, depth5, callCycle,
                remediation, entity, linksInner, linksOuter, workspaceUnit, planCaller(), epicCaller(),
                planOnlyOuter(), planOnlyInner(), twoEntries(), taskUnit()));
    }

    /** The sub-workflow {@link #TWO_ENTRIES}: the entries {@code review} and {@code repair}, the exit {@code done}. */
    private static WorkflowDefinition twoEntries() {
        return VerifyFixtures.parseSource("test.two-entries.pmwf", """
                ---
                name: test.two-entries
                description: A unit written for the tests of pm-workflow.
                type: subworkflow
                icon: workflow.default
                entry: review
                entry: repair
                exit: done
                modification_date: 2026-01-15
                version: 0.1.0
                author: plan-marshall
                ---
                workflow test.two-entries version 1 format 1
                scope subworkflow
                entry review -> two.review
                entry repair -> two.repair

                state two.review
                    auto test.prepare
                        done -> two.done
                        skipped -> two.stepped-out

                state two.repair
                    auto test.prepare
                        done -> two.done
                        skipped -> two.stepped-out

                state two.done terminal done success

                state two.stepped-out terminal stepped_out stepped_out
                """);
    }

    /**
     * The unit {@link #TASK_UNIT} of the type {@code task} and the scope {@code subworkflow}: it starts without an
     * entry and returns through the exit {@code done}, so no call may name it (interface rule 3).
     */
    private static WorkflowDefinition taskUnit() {
        return VerifyFixtures.parseSource("test.task-unit.pmwf", """
                ---
                name: test.task-unit
                description: A unit written for the tests of pm-workflow.
                type: task
                icon: workflow.default
                exit: done
                modification_date: 2026-01-15
                version: 0.1.0
                author: plan-marshall
                ---
                workflow test.task-unit version 1 format 1
                scope subworkflow
                start task.done

                state task.done terminal done success
                """);
    }

    /** The plan unit {@link #PLAN_CALLER}: it calls {@link #PLAN_ONLY_OUTER}, then {@link #MIXED_CALLERS}. */
    private static WorkflowDefinition planCaller() {
        return VerifyFixtures.parseSource("test.plan-caller.pmwf", """
                ---
                name: test.plan-caller
                description: A unit written for the tests of pm-workflow.
                type: workflow
                icon: workflow.default
                modification_date: 2026-01-15
                version: 0.1.0
                author: plan-marshall
                ---
                workflow test.plan-caller version 1 format 1
                scope plan
                start plan-caller.outer

                state plan-caller.outer
                    call test.plan-only-outer()
                        done -> plan-caller.mixed
                        stepped_out -> plan.return

                state plan-caller.mixed
                    call verify.model-job-lost-mixed-callers()
                        done -> plan-caller.done
                        stepped_out -> plan.return

                state plan.return terminal returned failure

                state plan-caller.done terminal done success
                """);
    }

    /** The epic unit {@link #EPIC_CALLER}: it calls {@link #MIXED_CALLERS} and rests on a job afterwards. */
    private static WorkflowDefinition epicCaller() {
        return VerifyFixtures.parseSource("test.epic-caller.pmwf", """
                ---
                name: test.epic-caller
                description: A unit written for the tests of pm-workflow.
                type: workflow
                icon: workflow.default
                modification_date: 2026-01-15
                version: 0.1.0
                author: plan-marshall
                ---
                workflow test.epic-caller version 1 format 1
                scope epic
                start epic-caller.mixed

                state epic-caller.mixed
                    call verify.model-job-lost-mixed-callers()
                        done -> epic-caller.done
                        stepped_out -> epic-caller.rest

                state epic-caller.rest
                    job test.build
                        succeeded -> epic-caller.done
                        failed -> epic-caller.done
                        cancelled -> epic-caller.done

                state epic-caller.done terminal done success
                """);
    }

    /** The sub-workflow {@link #PLAN_ONLY_OUTER}: it calls {@link #PLAN_ONLY_INNER}. */
    private static WorkflowDefinition planOnlyOuter() {
        return VerifyFixtures.parseSource("test.plan-only-outer.pmwf", """
                ---
                name: test.plan-only-outer
                description: A unit written for the tests of pm-workflow.
                type: subworkflow
                icon: workflow.default
                entry: main
                exit: done
                modification_date: 2026-01-15
                version: 0.1.0
                author: plan-marshall
                ---
                workflow test.plan-only-outer version 1 format 1
                scope subworkflow
                entry main -> plan-only-outer.inner

                state plan-only-outer.inner
                    call test.plan-only-inner()
                        done -> plan-only-outer.done
                        stepped_out -> plan-only-outer.stepped-out

                state plan-only-outer.done terminal done success

                state plan-only-outer.stepped-out terminal stepped_out stepped_out
                """);
    }

    /** The sub-workflow {@link #PLAN_ONLY_INNER}: one task state that leaves {@code lost} undeclared. */
    private static WorkflowDefinition planOnlyInner() {
        return VerifyFixtures.parseSource("test.plan-only-inner.pmwf", """
                ---
                name: test.plan-only-inner
                description: A unit written for the tests of pm-workflow.
                type: subworkflow
                icon: workflow.default
                entry: main
                exit: done
                modification_date: 2026-01-15
                version: 0.1.0
                author: plan-marshall
                ---
                workflow test.plan-only-inner version 1 format 1
                scope subworkflow
                entry main -> plan-only-inner.review

                state plan-only-inner.review
                    job llm change role reviewer
                        approved -> plan-only-inner.done

                state plan-only-inner.done terminal done success
                """);
    }

    /** A link-free sub-workflow {@code test.depth<n>} whose one state calls {@code callee}. */
    private static WorkflowDefinition depthUnit(int depth, String callee) {
        return VerifyFixtures.parseSource("test.depth%d.pmwf".formatted(depth), """
                ---
                name: test.depth%1$d
                description: A unit written for the tests of pm-workflow.
                type: subworkflow
                icon: workflow.default
                entry: main
                exit: done
                modification_date: 2026-01-15
                version: 0.1.0
                author: plan-marshall
                ---
                workflow test.depth%1$d version 1 format 1
                scope subworkflow
                entry main -> depth%1$d.step

                state depth%1$d.step
                    call %2$s()
                        done -> depth%1$d.done
                        stepped_out -> depth%1$d.stepped-out

                state depth%1$d.done terminal done success

                state depth%1$d.stepped-out terminal stepped_out stepped_out
                """.formatted(depth, callee));
    }

    /** A primitive that declares a cycle measure and, for an attempt cap, its exhaustion outcome. */
    static final class Measured<R extends Enum<R> & PrimitiveOutcome> implements Primitive<NoParams, R> {

        private final String id;
        private final Class<R> outcomeType;
        private final CycleMeasure cycleMeasure;
        private final Optional<String> exhaustionOutcome;

        Measured(String id, Class<R> outcomeType, CycleMeasure cycleMeasure, Optional<String> exhaustionOutcome) {
            this.id = id;
            this.outcomeType = outcomeType;
            this.cycleMeasure = cycleMeasure;
            this.exhaustionOutcome = exhaustionOutcome;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Class<NoParams> parameterType() {
            return NoParams.class;
        }

        @Override
        public Class<R> outcomeType() {
            return outcomeType;
        }

        @Override
        public PrimitiveResult<R> execute(WorkflowExecutionContext context, NoParams parameters) {
            return new PrimitiveResult<>(outcomeType.getEnumConstants()[0], Map.of(), Optional.empty());
        }

        @Override
        public AwaitedEvent awaitedEvent() {
            return AwaitedEvent.NONE;
        }

        @Override
        public CycleMeasure cycleMeasure() {
            return cycleMeasure;
        }

        @Override
        public Optional<String> exhaustionOutcome() {
            return exhaustionOutcome;
        }
    }

    enum DispatchOutcome implements PrimitiveOutcome {
        FIRST("first"), SECOND("second"), SETTLED("settled");

        private final String wireName;

        DispatchOutcome(String wireName) {
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

    enum ArchiveOutcome implements PrimitiveOutcome {
        ARCHIVE_UNCOMMITTED("archive_uncommitted"), ARCHIVED("archived");

        private final String wireName;

        ArchiveOutcome(String wireName) {
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

    enum AttemptOutcome implements PrimitiveOutcome {
        RETRY("retry", OutcomeClass.SUCCESS), EXHAUSTED("exhausted", OutcomeClass.FAILURE);

        private final String wireName;
        private final OutcomeClass outcomeClass;

        AttemptOutcome(String wireName, OutcomeClass outcomeClass) {
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

    enum FixOutcome implements PrimitiveOutcome {
        FIXED("fixed", OutcomeClass.SUCCESS), FIX_REQUESTED("fix_requested", OutcomeClass.UNSETTLED);

        private final String wireName;
        private final OutcomeClass outcomeClass;

        FixOutcome(String wireName, OutcomeClass outcomeClass) {
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

    /** A primitive that returns its first outcome and overrides no default of the SPI. */
    static final class Fixed<P extends Record, R extends Enum<R> & PrimitiveOutcome> implements Primitive<P, R> {

        private final String id;
        private final Class<P> parameterType;
        private final Class<R> outcomeType;

        Fixed(String id, Class<P> parameterType, Class<R> outcomeType) {
            this.id = id;
            this.parameterType = parameterType;
            this.outcomeType = outcomeType;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Class<P> parameterType() {
            return parameterType;
        }

        @Override
        public Class<R> outcomeType() {
            return outcomeType;
        }

        @Override
        public PrimitiveResult<R> execute(WorkflowExecutionContext context, P parameters) {
            return new PrimitiveResult<>(outcomeType.getEnumConstants()[0], Map.of(), Optional.empty());
        }

        @Override
        public AwaitedEvent awaitedEvent() {
            return AwaitedEvent.NONE;
        }
    }

    record NoParams() {
    }

    record ChooseParams(@FreeParam(minLength = 1, maxLength = 80) String note, @FreeParam String comment,
    int attempt) {
    }

    enum PrepareOutcome implements PrimitiveOutcome {
        DONE("done", OutcomeClass.SUCCESS), SKIPPED("skipped", OutcomeClass.SKIP);

        private final String wireName;
        private final OutcomeClass outcomeClass;

        PrepareOutcome(String wireName, OutcomeClass outcomeClass) {
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

    /** The outcomes of a job, {@code cancelled} included as on every job primitive. */
    enum JobOutcome implements PrimitiveOutcome {
        SUCCEEDED("succeeded", OutcomeClass.SUCCESS), FAILED("failed", OutcomeClass.FAILURE),
        CANCELLED("cancelled", OutcomeClass.INDETERMINATE);

        private final String wireName;
        private final OutcomeClass outcomeClass;

        JobOutcome(String wireName, OutcomeClass outcomeClass) {
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

    enum DoneOutcome implements PrimitiveOutcome {
        DONE;

        @Override
        public String wireName() {
            return "done";
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.SUCCESS;
        }
    }

    enum ChooseOutcome implements PrimitiveOutcome {
        CHOSEN("chosen"), DECLINED("declined");

        private final String wireName;

        ChooseOutcome(String wireName) {
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

    enum PredicateOutcome implements PrimitiveOutcome {
        HELD("held"), NOT_HELD("not_held");

        private final String wireName;

        PredicateOutcome(String wireName) {
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

    enum EvaluateChangeOutcome implements PrimitiveOutcome {
        APPROVED("approved");

        private final String wireName;

        EvaluateChangeOutcome(String wireName) {
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

    enum JudgementOutcome implements PrimitiveOutcome {
        APPROVED("approved", OutcomeClass.SUCCESS), REJECTED("rejected", OutcomeClass.FAILURE);

        private final String wireName;
        private final OutcomeClass outcomeClass;

        JudgementOutcome(String wireName, OutcomeClass outcomeClass) {
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

    /** The outcomes of {@link #EVALUATE_REPAIR}. */
    enum RepairOutcome implements PrimitiveOutcome {
        REPAIRED("repaired", OutcomeClass.SUCCESS), INFEASIBLE("infeasible", OutcomeClass.FAILURE);

        private final String wireName;
        private final OutcomeClass outcomeClass;

        RepairOutcome(String wireName, OutcomeClass outcomeClass) {
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

    enum LandOutcome implements PrimitiveOutcome {
        LANDED("landed", OutcomeClass.SUCCESS), ABORT_LANDED("abort_landed", OutcomeClass.FAILURE);

        private final String wireName;
        private final OutcomeClass outcomeClass;

        LandOutcome(String wireName, OutcomeClass outcomeClass) {
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

    /** The outcomes of {@link #CHECK_BUDGETS}. */
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

    enum EnqueueOutcome implements PrimitiveOutcome {
        ENQUEUED("enqueued", OutcomeClass.SUCCESS, true),
        SKIPPED("skipped", OutcomeClass.SKIP, false);

        private final String wireName;
        private final OutcomeClass outcomeClass;
        private final boolean startsJob;

        EnqueueOutcome(String wireName, OutcomeClass outcomeClass, boolean startsJob) {
            this.wireName = wireName;
            this.outcomeClass = outcomeClass;
            this.startsJob = startsJob;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        @Override
        public OutcomeClass outcomeClass() {
            return outcomeClass;
        }

        @Override
        public boolean startsJob() {
            return startsJob;
        }
    }

    static final class EnqueuePrimitive implements Primitive<NoParams, EnqueueOutcome> {
        @Override
        public String id() {
            return ENQUEUE;
        }

        @Override
        public Class<NoParams> parameterType() {
            return NoParams.class;
        }

        @Override
        public Class<EnqueueOutcome> outcomeType() {
            return EnqueueOutcome.class;
        }

        @Override
        public PrimitiveResult<EnqueueOutcome> execute(WorkflowExecutionContext context, NoParams parameters) {
            return new PrimitiveResult<>(EnqueueOutcome.ENQUEUED, Map.of(), Optional.empty());
        }

        @Override
        public AwaitedEvent awaitedEvent() {
            return AwaitedEvent.NONE;
        }

        @Override
        public boolean enqueuesJob() {
            return true;
        }
    }

    static final class ArtifactConsumer implements Primitive<NoParams, DoneOutcome> {
        @Override
        public String id() {
            return CONSUME;
        }

        @Override
        public Class<NoParams> parameterType() {
            return NoParams.class;
        }

        @Override
        public Class<DoneOutcome> outcomeType() {
            return DoneOutcome.class;
        }

        @Override
        public PrimitiveResult<DoneOutcome> execute(WorkflowExecutionContext context, NoParams parameters) {
            return new PrimitiveResult<>(DoneOutcome.DONE, Map.of(), Optional.empty());
        }

        @Override
        public AwaitedEvent awaitedEvent() {
            return AwaitedEvent.NONE;
        }

        @Override
        public Set<String> requiredArtifacts() {
            return Set.of("test.artifact");
        }
    }

    static final class ArtifactProducer implements Primitive<NoParams, DoneOutcome> {
        @Override
        public String id() {
            return PRODUCE;
        }

        @Override
        public Class<NoParams> parameterType() {
            return NoParams.class;
        }

        @Override
        public Class<DoneOutcome> outcomeType() {
            return DoneOutcome.class;
        }

        @Override
        public PrimitiveResult<DoneOutcome> execute(WorkflowExecutionContext context, NoParams parameters) {
            return new PrimitiveResult<>(DoneOutcome.DONE, Map.of(), Optional.empty());
        }

        @Override
        public AwaitedEvent awaitedEvent() {
            return AwaitedEvent.NONE;
        }

        @Override
        public Set<String> producedArtifacts() {
            return Set.of("test.artifact");
        }
    }
}
