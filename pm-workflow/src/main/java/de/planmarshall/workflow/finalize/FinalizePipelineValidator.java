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

import static de.planmarshall.workflow.finalize.FinalizeStepDescriptor.MERGE_GATE_ORDER;
import static de.planmarshall.workflow.finalize.FinalizeStepDescriptor.POST_RUN_MIN_ORDER;
import static de.planmarshall.workflow.finalize.FinalizeStepDescriptor.PRE_PUSH_QUALITY_GATE_ORDER;
import static de.planmarshall.workflow.finalize.FinalizeStepDescriptor.PUSH_ORDER;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import de.planmarshall.workflow.finalize.FinalizeStepDescriptor.InputScope;

/**
 * Checks a roster of finalize steps against the static rules of the pipeline (PM-IMPL-5): the default roster at build
 * time, and a project's roster (the default descriptors of the steps its finalize unit contains plus its project step
 * descriptors) on every load of its units. The roster is input; this module holds no table of the default steps.
 * <p>
 * The validator checks rules 1 to 5 and the input-scope part of rule 6. The parts of the rules that cannot be decided
 * from a descriptor are named hooks of {@link PipelineRule}; the finding-filing steps of rule 8 are derived by
 * {@link #findingSources}. It reports every violation and never stops at the first one.
 *
 * @since 0.1
 */
public final class FinalizePipelineValidator {

    /** The step id of the pre-push quality gate. */
    static final String PRE_PUSH_QUALITY_GATE = "default:pre-push-quality-gate";

    /** The step id of the push. */
    static final String PUSH = "default:push";

    /** The order of {@code default:record-metrics}, the producer of the metrics. */
    static final int RECORD_METRICS_ORDER = 998;

    /** The highest order of a post-run review step: {@code emit-landing}. */
    static final int POST_RUN_MAX_ORDER = 1000;

    /** The artifact token of the metrics. */
    static final String METRICS = "metrics";

    /** The fact by which a step ordered before {@code record-metrics} declares that it reads the labelled snapshot. */
    static final String MEASURED_UNTIL = "measured_until";

    private FinalizePipelineValidator() {
    }

    /**
     * Checks a roster.
     *
     * @param roster the descriptors of the steps of the pipeline
     * @return every violation, ordered by the order of the step and then by rule; empty when the roster passes
     */
    public static List<PipelineViolation> validate(List<FinalizeStepDescriptor> roster) {
        Objects.requireNonNull(roster, "roster");
        var found = new ArrayList<Found>();
        for (var step : roster) {
            orderUnique(roster, step, found);
            prePushPlacement(step, found);
            sourceMutation(step, found);
            postRunReview(step, found);
            readsBeforeDestroys(roster, step, found);
            anchorAndFactContract(step, found);
        }
        return found.stream()
                .sorted(Comparator.comparingInt(Found::order).thenComparing(Found::rule))
                .map(Found::violation)
                .toList();
    }

    /**
     * Derives the finding-filing steps of a roster (rule 8): the steps that declare at least one finding source.
     *
     * @param roster the descriptors of the steps of the pipeline
     * @return the names of the steps with non-empty {@code filesFindings}, in roster order
     */
    public static Set<String> findingSources(List<FinalizeStepDescriptor> roster) {
        Objects.requireNonNull(roster, "roster");
        var sources = new LinkedHashSet<String>();
        roster.stream().filter(step -> !step.filesFindings().isEmpty()).forEach(step -> sources.add(step.name()));
        return sources;
    }

    private static void orderUnique(List<FinalizeStepDescriptor> roster, FinalizeStepDescriptor step,
            List<Found> found) {
        roster.stream()
                .filter(other -> other != step && other.order() == step.order())
                .forEach(other -> report(found, step, PipelineRule.ORDER_UNIQUE,
                        "order %d is also held by '%s'".formatted(step.order(), other.name())));
    }

    private static void prePushPlacement(FinalizeStepDescriptor step, List<Found> found) {
        if (PRE_PUSH_QUALITY_GATE.equals(step.name()) && step.order() != PRE_PUSH_QUALITY_GATE_ORDER) {
            report(found, step, PipelineRule.PRE_PUSH_PLACEMENT, "the pre-push quality gate is at order %d, not %d"
                    .formatted(step.order(), PRE_PUSH_QUALITY_GATE_ORDER));
        }
        if (PUSH.equals(step.name()) && step.order() != PUSH_ORDER) {
            report(found, step, PipelineRule.PRE_PUSH_PLACEMENT,
                    "the push is at order %d, not %d".formatted(step.order(), PUSH_ORDER));
        }
        var named = PRE_PUSH_QUALITY_GATE.equals(step.name()) || PUSH.equals(step.name());
        if (!named && (step.order() == PRE_PUSH_QUALITY_GATE_ORDER || step.order() == PUSH_ORDER)) {
            report(found, step, PipelineRule.PRE_PUSH_PLACEMENT,
                    "order %d is reserved for the pre-push quality gate and the push".formatted(step.order()));
        }
    }

    private static void sourceMutation(FinalizeStepDescriptor step, List<Found> found) {
        if (step.mutatesSource() && step.order() >= MERGE_GATE_ORDER) {
            report(found, step, PipelineRule.SOURCE_MUTATION_BEFORE_MERGE_GATE,
                    "the step mutates source at order %d, at or after the merge gate at %d".formatted(step.order(),
                            MERGE_GATE_ORDER));
        }
    }

    private static void postRunReview(FinalizeStepDescriptor step, List<Found> found) {
        if (!step.postRunReview()) {
            return;
        }
        if (step.mutatesSource()) {
            report(found, step, PipelineRule.POST_RUN_REVIEW_BAND, "a post-run review step mutates source");
        }
        if (step.order() < POST_RUN_MIN_ORDER || step.order() > POST_RUN_MAX_ORDER) {
            report(found, step, PipelineRule.POST_RUN_REVIEW_BAND,
                    "the post-run review step is at order %d, outside %d to %d".formatted(step.order(),
                            POST_RUN_MIN_ORDER, POST_RUN_MAX_ORDER));
        }
    }

    private static void readsBeforeDestroys(List<FinalizeStepDescriptor> roster, FinalizeStepDescriptor step,
            List<Found> found) {
        for (var artifact : step.reads()) {
            roster.stream()
                    .filter(other -> other != step && other.destroys().contains(artifact)
                            && step.order() >= other.order())
                    .forEach(other -> report(found, step, PipelineRule.READS_BEFORE_DESTROYS,
                            "reads '%s' at order %d, but '%s' destroys it at order %d".formatted(artifact,
                                    step.order(), other.name(), other.order())));
        }
        if (step.reads().contains(METRICS) && step.order() < RECORD_METRICS_ORDER
                && !step.recordsFacts().contains(MEASURED_UNTIL)) {
            report(found, step, PipelineRule.READS_BEFORE_DESTROYS,
                    "reads '%s' at order %d, before record-metrics at %d, without recording the snapshot label '%s'"
                            .formatted(METRICS, step.order(), RECORD_METRICS_ORDER, MEASURED_UNTIL));
        }
    }

    private static void anchorAndFactContract(FinalizeStepDescriptor step, List<Found> found) {
        if (step.headDependent() && step.inputScope().isEmpty()) {
            report(found, step, PipelineRule.ANCHOR_AND_FACT_CONTRACT,
                    "a head-dependent step declares no input scope");
        }
        if (!step.headDependent() && (step.inputScope().isPresent() || !step.verdictInputs().isEmpty())) {
            report(found, step, PipelineRule.ANCHOR_AND_FACT_CONTRACT,
                    "a step that is not head-dependent declares an input scope or verdict inputs");
        }
        var declared = step.inputScope().filter(scope -> scope == InputScope.DECLARED).isPresent();
        if (declared && step.verdictInputs().isEmpty()) {
            report(found, step, PipelineRule.ANCHOR_AND_FACT_CONTRACT,
                    "the input scope 'declared' goes with no verdict inputs");
        }
        if (step.inputScope().isPresent() && !declared && !step.verdictInputs().isEmpty()) {
            report(found, step, PipelineRule.ANCHOR_AND_FACT_CONTRACT,
                    "the input scope '%s' goes with verdict inputs".formatted(step.inputScope().orElseThrow()));
        }
    }

    private static void report(List<Found> found, FinalizeStepDescriptor step, PipelineRule rule, String message) {
        found.add(new Found(step.order(), new PipelineViolation(rule, step.name(), message)));
    }

    /** A violation with the order of its step, the sort key of the result. */
    private record Found(int order, PipelineViolation violation) {

        PipelineRule rule() {
            return violation.rule();
        }
    }
}
