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

import java.util.Optional;

/**
 * The closed set of rules {@link FinalizePipelineValidator} checks over a roster of {@link FinalizeStepDescriptor}s
 * (PM-IMPL-5). Every {@link PipelineViolation} names exactly one of them; this enum is the only source of the rule
 * vocabulary.
 * <p>
 * A rule, or the part of a rule, that cannot be decided from the descriptors carries a named hook: the reason the
 * part is checked elsewhere, as a code constant. The validator checks every other part.
 *
 * @since 0.1
 */
public enum PipelineRule {

    /** Rule 1: no two steps of the roster share an order; insertion order never breaks a tie. */
    ORDER_UNIQUE(1, null),

    /**
     * Rule 2: {@code default:pre-push-quality-gate} is at {@value FinalizeStepDescriptor#PRE_PUSH_QUALITY_GATE_ORDER},
     * {@code default:push} at {@value FinalizeStepDescriptor#PUSH_ORDER}, and no other step occupies either order.
     */
    PRE_PUSH_PLACEMENT(2, null),

    /**
     * Rule 3: a step that mutates source is ordered before the merge gate
     * ({@value FinalizeStepDescriptor#MERGE_GATE_ORDER}); no step at or after it mutates source.
     */
    SOURCE_MUTATION_BEFORE_MERGE_GATE(3, null),

    /**
     * Rule 4: a post-run review step mutates no source and is ordered from
     * {@value FinalizeStepDescriptor#POST_RUN_MIN_ORDER} to 1000.
     */
    POST_RUN_REVIEW_BAND(4, null),

    /**
     * Rule 5: a step reading an artifact is ordered before every step destroying it, and a reader of {@code metrics}
     * is ordered after {@code record-metrics} or declares that it reads the snapshot labelled {@code measured_until}.
     */
    READS_BEFORE_DESTROYS(5, null),

    /**
     * Rule 6: a head-dependent step declares its input scope, a step that is not head-dependent declares neither scope
     * nor verdict inputs, and the scope {@code declared} goes with non-empty verdict inputs, every other scope with
     * none.
     */
    ANCHOR_AND_FACT_CONTRACT(6, "Whether a step's done outcome is reachable through both a no-op and a work-performing"
            + " branch, and so must record work_performed, is a property of the step's behaviour, not of its"
            + " descriptor; it is checked where the step's outcomes are known."),

    /**
     * Rule 7: no step after the merge gate declares an outcome that refuses on its inputs; a post-merge step records a
     * degraded result instead.
     */
    NO_GUARD_AFTER_MERGE(7, "The outcomes a step declares are not part of its descriptor; whether a post-merge step"
            + " refuses on its inputs is checked over the outcomes of the step."),

    /**
     * Rule 8: the finding-filing steps are derived from the roster as the steps with non-empty {@code filesFindings}
     * ({@link FinalizePipelineValidator#findingSources}), never listed.
     */
    DECLARED_FINDING_SOURCES(8, "A descriptor cannot violate the derivation; the refusal of a finding whose source the"
            + " filing step does not declare is a check of the findings store at the time of the write.");

    private final int specificationRule;
    private final String namedHook;

    PipelineRule(int specificationRule, String namedHook) {
        this.specificationRule = specificationRule;
        this.namedHook = namedHook;
    }

    /**
     * @return the number of the rule in the specification of the validator, {@code 1} to {@code 8}
     */
    public int specificationRule() {
        return specificationRule;
    }

    /**
     * @return the reason the rule, or a part of it, is checked outside the validator; empty when the validator checks
     *         the whole rule
     */
    public Optional<String> namedHook() {
        return Optional.ofNullable(namedHook);
    }
}
