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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The closed list of the compiled invariants every project-owned unit satisfies (PM-IMPL-5, PM-WF-16). Every
 * {@link InvariantViolation} names exactly one of them; this enum is the only source of the invariant vocabulary.
 * <p>
 * Each invariant carries its wire identifier ({@code INV-GEN-1}), the units it applies to, and, where a part of it
 * cannot be decided on the syntax tree of a unit, a named hook: the reason that part is checked elsewhere, as a code
 * constant at the check. {@link CompiledInvariants} checks every other part, and no configuration switches a check
 * off. The named hooks of {@link #INV_GEN_2}, {@link #INV_GEN_4} and {@link #INV_REM_6} are the only exemptions.
 * <p>
 * A unit pattern is a unit identifier, {@value #ALL_UNITS} for every unit, or a namespace followed by {@code .*} for
 * every unit of the namespace, for example {@code remediation.*}. The phase invariants apply to the merged plan unit
 * {@value #PLAN_UNIT}, which holds the states of every phase file.
 *
 * @since 0.1
 */
public enum InvariantId {

    /**
     * Rules 1-12 hold; the unit's scope, start state and terminal states equal those of the default unit, and no
     * transition targets {@code plan.aborted}.
     */
    INV_GEN_1("INV-GEN-1", List.of(InvariantId.ALL_UNITS), null),

    /** Waiver data flow: a waived path appears only as the {@code waived} terminal of a remediation unit. */
    INV_GEN_2("INV-GEN-2", List.of(InvariantId.ALL_UNITS),
            "Only the operator API writes waiver records and every waiver lookup logs a consumption; the primitive SPI"
                    + " offers no write capability for them, which is a property of the runtime, not of a unit's"
                    + " tree. The tree part is checked: a waived path ends only in a remediation unit's waived"
                    + " terminal, and only a primitive that declares waivable gates admits a failure as waived."),

    /** Condition overlays unchanged: a unit declares no condition and targets none. */
    INV_GEN_3("INV-GEN-3", List.of(InvariantId.ALL_UNITS), null),

    /** No guarantee weakening: no path of the unit realizes a removed option. */
    INV_GEN_4("INV-GEN-4", List.of(InvariantId.ALL_UNITS),
            "The removed options that a primitive reads from configuration at runtime (the lane, the Q-Gate mode,"
                    + " the confidence threshold, the merge method, the self-review scope) are runtime facts the"
                    + " tree cannot express. The tree part is checked: every state that lies on every path of the"
                    + " default unit to its success terminals lies on every such path of the unit; the specific"
                    + " guarantees are the phase invariants."),

    /** Model work only as tasks: no {@code worker-task} use, no resting link state, a conservative {@code lost}. */
    INV_GEN_5("INV-GEN-5", List.of(InvariantId.ALL_UNITS), null),

    /** {@code init.classify} on every path, init questions return to it, the stamps before {@code init.ready}. */
    INV_INIT_1("INV-INIT-1", List.of(InvariantId.PLAN_UNIT), null),

    /** The light lane passes {@code init.light-gate} and {@code outline.light-regate}; the ratchet is present. */
    INV_INIT_2("INV-INIT-2", List.of(InvariantId.PLAN_UNIT), null),

    /** Every deep path passes {@code refine.baseline-reconcile} and {@code refine.analyze}; the lesson Q-Gate. */
    INV_REFINE_1("INV-REFINE-1", List.of(InvariantId.PLAN_UNIT), null),

    /** {@code outline.gate} on every path into Phase 4; the review question's {@code approve} the only other way. */
    INV_OUTLINE_1("INV-OUTLINE-1", List.of(InvariantId.PLAN_UNIT), null),

    /** {@code outline.qgate} on every deep path to the gate; {@code blocking} calls the Q-Gate remediation. */
    INV_OUTLINE_2("INV-OUTLINE-2", List.of(InvariantId.PLAN_UNIT), null),

    /** Every path into Phase 5 passes the derivation, {@code plan.qgate} and the composition. */
    INV_PLAN_1("INV-PLAN-1", List.of(InvariantId.PLAN_UNIT), null),

    /** The worktree initialization precedes every envelope task, commit and build. */
    INV_EXEC_1("INV-EXEC-1", List.of(InvariantId.PLAN_UNIT), null),

    /** Commits only through {@code git.commit_and_push}; the deliverable build, the verify sweep and exit barrier. */
    INV_EXEC_2("INV-EXEC-2", List.of(InvariantId.PLAN_UNIT), null),

    /** Serial envelopes: one envelope task state, reacting only through {@code execute.evaluate-envelope-return}. */
    INV_EXEC_3("INV-EXEC-3", List.of(InvariantId.PLAN_UNIT), null),

    /** Every mandatory step of the default roster is completed by the unit. */
    INV_FIN_1("INV-FIN-1", List.of(InvariantId.PLAN_UNIT), null),

    /** The finalize pipeline validator holds over the project roster. */
    INV_FIN_2("INV-FIN-2", List.of(InvariantId.PLAN_UNIT), null),

    /** Completion once per firing (Rule 9). */
    INV_FIN_3("INV-FIN-3", List.of(InvariantId.PLAN_UNIT), null),

    /** The push barrier is the only push before the pull request; {@code finalize.create-pr} is reached. */
    INV_FIN_4("INV-FIN-4", List.of(InvariantId.PLAN_UNIT), null),

    /** Every path to the merge passes {@code finalize.merge-gate}; an overrun calls {@code remediation.spend}. */
    INV_FIN_5("INV-FIN-5", List.of(InvariantId.PLAN_UNIT), null),

    /** Branch cleanup on the merge, the one fallback, the landing, and {@code finalize.archive-plan} last. */
    INV_FIN_6("INV-FIN-6", List.of(InvariantId.PLAN_UNIT), null),

    /** The adjudication unit equals the default except its declarations, which lie within their bounds. */
    INV_ADJ_1("INV-ADJ-1", List.of("adjudicate.decisions"), null),

    /** The re-planning unit equals the default except its task role and declarations; no question link. */
    INV_REPLAN_1("INV-REPLAN-1", List.of("replan"), null),

    /** The six stages of the commit sub-workflow in order, its exit contract, capped loops, no force push. */
    INV_GIT_1("INV-GIT-1", List.of("git.commit_and_push"), null),

    /** The contract of a remediation unit: its parameters with their hard caps and its terminals. */
    INV_REM_1("INV-REM-1", List.of(InvariantId.REMEDIATION_UNITS), null),

    /** No dead end and no budget-free remedy in a remediation unit. */
    INV_REM_2("INV-REM-2", List.of(InvariantId.REMEDIATION_UNITS), null),

    /** The waived path of a remediation unit and its bounded waiver wait. */
    INV_REM_3("INV-REM-3", List.of(InvariantId.REMEDIATION_UNITS), null),

    /** A nested commit of a remediation unit routes {@code stepped_out} and a commit-less {@code done} back. */
    INV_REM_4("INV-REM-4", List.of(InvariantId.REMEDIATION_UNITS), null),

    /** The call sites of the remediation units route their exits and bind budgets within the hard caps. */
    INV_REM_5("INV-REM-5", List.of(InvariantId.PLAN_UNIT), null),

    /** Budget accounting: the counters are charged only by the remediation units' primitives. */
    INV_REM_6("INV-REM-6", List.of(InvariantId.ALL_UNITS),
            "The counters are kept per plan and class in the plan's status and are never reset by a re-entry, a new"
                    + " HEAD, a loop-back or a re-composition; that is runtime accounting the tree cannot express."
                    + " The tree part is checked: no unit but a remediation unit runs a remediation primitive, and"
                    + " a remediation unit runs no remediation primitive its default unit does not run."),

    /** The full template of a remediation unit with an action set. */
    INV_REM_7("INV-REM-7", List.of(InvariantId.REMEDIATION_UNITS), null);

    /** The unit pattern that matches every unit. */
    static final String ALL_UNITS = "*";

    /** The identifier of the merged plan unit. */
    static final String PLAN_UNIT = "plan";

    /** The unit pattern of the remediation units. */
    static final String REMEDIATION_UNITS = "remediation.*";

    private static final String NAMESPACE_WILDCARD = ".*";

    private final String wireId;

    private final List<String> units;

    private final String namedHook;

    InvariantId(String wireId, List<String> units, String namedHook) {
        this.wireId = wireId;
        this.units = List.copyOf(units);
        this.namedHook = namedHook;
    }

    /**
     * @return the identifier that names the invariant in a finding, for example {@code INV-GEN-1}
     */
    public String wireId() {
        return wireId;
    }

    /**
     * @return the patterns of the units the invariant applies to
     */
    public List<String> units() {
        return units;
    }

    /**
     * @return the reason a part of the invariant is checked outside the syntax tree; empty when the whole invariant
     *         is checked on the tree
     */
    public Optional<String> namedHook() {
        return Optional.ofNullable(namedHook);
    }

    /**
     * @param unitId the identifier of a unit
     * @return {@code true} when one of the invariant's unit patterns matches the unit
     */
    public boolean appliesTo(String unitId) {
        return units.stream().anyMatch(pattern -> matches(pattern, unitId));
    }

    /**
     * @param wireId the identifier that names an invariant in a finding
     * @return the invariant with that identifier; empty when there is none
     */
    public static Optional<InvariantId> ofWireId(String wireId) {
        return Arrays.stream(values()).filter(id -> id.wireId.equals(wireId)).findFirst();
    }

    private static boolean matches(String pattern, String unitId) {
        if (ALL_UNITS.equals(pattern)) {
            return true;
        }
        if (pattern.endsWith(NAMESPACE_WILDCARD)) {
            return unitId.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return pattern.equals(unitId);
    }
}
