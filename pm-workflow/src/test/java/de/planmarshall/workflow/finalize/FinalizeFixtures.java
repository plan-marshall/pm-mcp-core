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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

import de.planmarshall.workflow.finalize.FinalizeStepDescriptor.ConfigurableKey;
import de.planmarshall.workflow.finalize.FinalizeStepDescriptor.InputScope;
import de.planmarshall.workflow.finalize.FinalizeStepDescriptor.Lane;
import de.planmarshall.workflow.finalize.FinalizeStepDescriptor.LaneClass;

/**
 * Descriptors and rosters written for the finalize tests, never copied from the steps of the product: a builder of one
 * descriptor and a roster that passes every rule of {@link FinalizePipelineValidator}.
 */
final class FinalizeFixtures {

    private FinalizeFixtures() {
    }

    /**
     * Starts a descriptor of a step that is on by default and declares nothing else.
     *
     * @param name  the step id
     * @param order the order of the step
     * @return the builder
     */
    static Step step(String name, int order) {
        return new Step(name, order);
    }

    /**
     * A roster that passes every rule: steps before the pre-push gate, the gate and the push at their orders, review
     * and analysis steps, the merge gate that destroys the worktree, the post-run band with a snapshot reader of the
     * metrics before {@code record-metrics} and readers after it, and the archive that destroys the plan directory.
     *
     * @return the passing roster
     */
    static List<FinalizeStepDescriptor> passingRoster() {
        return List.of(
                step("default:simplify", 5).mutatesSource().headDependent(InputScope.PLAN_FOOTPRINT)
                        .reads("worktree").recordsFacts("work_performed").build(),
                step("default:pre-push-quality-gate", 10).headDependent(InputScope.PLAN_FOOTPRINT).reads("worktree")
                        .build(),
                step("default:push", 11).build(),
                step("default:create-pr", 20).build(),
                step("default:automatic-review", 30).mutatesSource().headDependent(InputScope.REMOTE_STATE)
                        .filesFindings("review_bot").build(),
                step("default:sonar-roundtrip", 40).headDependent(InputScope.DECLARED, "sonar_quality_gate")
                        .filesFindings("sonar").build(),
                step("default:branch-cleanup", 70).reads("worktree").destroys("worktree").build(),
                step("default:plan-retrospective", 995).postRunReview().reads("metrics")
                        .recordsFacts("measured_until").build(),
                step("default:record-metrics", 998).build(),
                step("default:print-phase-breakdown", 999).postRunReview().reads("metrics").build(),
                step("default:emit-landing", 1000).postRunReview().reads("metrics", "plan-directory")
                        .recordsFacts("work_performed").build(),
                step("default:archive-plan", 1100).reads("plan-directory").destroys("plan-directory").build());
    }

    /**
     * The passing roster with one step replaced by the step of the same name the operator returns.
     *
     * @param name     the step to replace
     * @param mutation the change of the step's builder
     * @return the changed roster
     */
    static List<FinalizeStepDescriptor> withStep(String name, UnaryOperator<Step> mutation) {
        var roster = new ArrayList<FinalizeStepDescriptor>();
        for (var step : passingRoster()) {
            roster.add(step.name().equals(name) ? mutation.apply(Step.of(step)).build() : step);
        }
        return roster;
    }

    /**
     * The passing roster with one more step.
     *
     * @param added the added step
     * @return the extended roster
     */
    static List<FinalizeStepDescriptor> withAdded(FinalizeStepDescriptor added) {
        var roster = new ArrayList<>(passingRoster());
        roster.add(added);
        return roster;
    }

    /** A builder of one descriptor, every list empty and every optional value absent unless set. */
    static final class Step {

        private final String name;
        private int order;
        private boolean mutatesSource;
        private boolean headDependent;
        private Optional<InputScope> inputScope = Optional.empty();
        private List<String> verdictInputs = List.of();
        private boolean postRunReview;
        private List<String> reads = List.of();
        private List<String> destroys = List.of();
        private List<String> recordsFacts = List.of();
        private List<String> filesFindings = List.of();

        private Step(String name, int order) {
            this.name = name;
            this.order = order;
        }

        static Step of(FinalizeStepDescriptor descriptor) {
            var step = new Step(descriptor.name(), descriptor.order());
            step.mutatesSource = descriptor.mutatesSource();
            step.headDependent = descriptor.headDependent();
            step.inputScope = descriptor.inputScope();
            step.verdictInputs = descriptor.verdictInputs();
            step.postRunReview = descriptor.postRunReview();
            step.reads = descriptor.reads();
            step.destroys = descriptor.destroys();
            step.recordsFacts = descriptor.recordsFacts();
            step.filesFindings = descriptor.filesFindings();
            return step;
        }

        Step order(int value) {
            order = value;
            return this;
        }

        Step mutatesSource() {
            mutatesSource = true;
            return this;
        }

        Step headDependent(InputScope scope, String... inputs) {
            headDependent = true;
            inputScope = Optional.of(scope);
            verdictInputs = List.of(inputs);
            return this;
        }

        Step headDependentWithoutScope() {
            headDependent = true;
            inputScope = Optional.empty();
            verdictInputs = List.of();
            return this;
        }

        Step scope(InputScope scope, String... inputs) {
            inputScope = Optional.of(scope);
            verdictInputs = List.of(inputs);
            return this;
        }

        Step postRunReview() {
            postRunReview = true;
            return this;
        }

        Step reads(String... artifacts) {
            reads = List.of(artifacts);
            return this;
        }

        Step destroys(String... artifacts) {
            destroys = List.of(artifacts);
            return this;
        }

        Step recordsFacts(String... facts) {
            recordsFacts = List.of(facts);
            return this;
        }

        Step filesFindings(String... sources) {
            filesFindings = List.of(sources);
            return this;
        }

        FinalizeStepDescriptor build() {
            return new FinalizeStepDescriptor(name, order, true, Set.of(), "a step written for the test", mutatesSource,
                    headDependent, inputScope, verdictInputs, false, postRunReview, reads, destroys, recordsFacts,
                    Optional.empty(), List.of(), List.of(), Optional.empty(), List.of(), Optional.empty(),
                    filesFindings);
        }
    }

    /**
     * A descriptor with every component set, the nested types included.
     *
     * @return the descriptor
     */
    static FinalizeStepDescriptor full() {
        return new FinalizeStepDescriptor("default:automatic-review", 30, true, Set.of("standard"),
                "reviews the pull request", true, true, Optional.of(InputScope.DECLARED), List.of("review_head"),
                false, false, List.of("worktree"), List.of(), List.of("work_performed"),
                Optional.of(new Lane(LaneClass.ADVERSARIAL, Optional.of("standard"), Optional.empty(), "M")),
                List.of("default:create-pr"), List.of(new ConfigurableKey("required_bots", "coderabbit", "the bots")),
                Optional.of("reviewer"), List.of("pr_number"), Optional.of("untested paths"), List.of("review_bot"));
    }
}
