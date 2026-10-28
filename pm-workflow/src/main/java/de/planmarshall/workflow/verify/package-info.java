/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
/**
 * The verification of workflow units (PM-IMPL-5, PM-TEST-3).
 * <ul>
 * <li><b>Static verifier</b>: {@link de.planmarshall.workflow.verify.WorkflowValidator} checks one parsed
 * {@link de.planmarshall.workflow.ast.WorkflowDefinition} against the registered primitives and the released state
 * names its caller passes: primitive binding and parameter equivalence, outcome coverage, reachability, exit paths,
 * unique link identifiers and released states, the interface rules (a starting point or entries, stop points or
 * exits, called units return, every exit reached from an entry, a call names its entry), which compare the unit with
 * the entries and exits its frontmatter declares, and the type check, which compares its scope and interface with the
 * row of its type. The call contract reads the exits the callee declares, not its terminals. The checks are the
 * constants of {@link de.planmarshall.workflow.verify.VerificationRule}, the only source of the rule vocabulary.</li>
 * <li><b>Result</b>: {@link de.planmarshall.workflow.verify.VerificationResult} lists every
 * {@link de.planmarshall.workflow.verify.Violation}, each naming its rule, its state and the source location of the
 * offending node. A violation is a value, never an exception, and no check stops at the first one.</li>
 * <li><b>Transition fuzzer</b>: {@link de.planmarshall.workflow.verify.TransitionFuzzer} walks a unit that passed the
 * verifier under seeded random assignments of the guard atoms its guards name (a built-in guard such as
 * {@code in_worktree}, or a {@code predicate:} primitive) and reports every reached non-terminal state
 * without a holding exit as a {@link de.planmarshall.workflow.verify.DeadState} in its
 * {@link de.planmarshall.workflow.verify.FuzzReport}; the seed, the number of runs and the steps of a run are its
 * {@link de.planmarshall.workflow.verify.FuzzConfiguration}, and the same configuration gives the same report.</li>
 * <li><b>Compiled invariants</b>: {@link de.planmarshall.workflow.verify.CompiledInvariants} checks a project-owned
 * unit against the closed list {@link de.planmarshall.workflow.verify.InvariantId} ({@code INV-GEN-*} for every unit,
 * the phase invariants {@code INV-INIT-*} to {@code INV-FIN-*} and {@code INV-REM-5} for the merged plan unit, and
 * {@code INV-ADJ-1}, {@code INV-REPLAN-1}, {@code INV-GIT-1} and {@code INV-REM-*} for their sub-workflows). The default
 * unit of the same identifier is the reference: the unit may tighten what the default unit guarantees but never open
 * a path the default unit does not have. One package-private checker class per phase or unit kind holds the checks
 * ({@code GeneralInvariants}, {@code InitInvariants}, {@code RefineInvariants}, {@code OutlineInvariants},
 * {@code PlanInvariants}, {@code ExecuteInvariants}, {@code FinalizeInvariants}, {@code SubWorkflowInvariants},
 * {@code RemediationInvariants}), each a path or dominator check over the transition graph. Every finding is an
 * {@link de.planmarshall.workflow.verify.InvariantViolation} with the file and the line of the offending node, and no
 * configuration switches a check off.</li>
 * <li><b>Named hooks</b>: the part of an invariant that cannot be decided on the syntax tree carries its reason as a
 * code constant on its {@link de.planmarshall.workflow.verify.InvariantId}
 * ({@link de.planmarshall.workflow.verify.InvariantId#namedHook()}): the waiver write capability and the consumption
 * log of {@code INV-GEN-2}, the runtime options of {@code INV-GEN-4}, and the counter persistence of
 * {@code INV-REM-6}. These are the only exemptions.</li>
 * <li><b>Declaration bounds</b>: {@link de.planmarshall.workflow.verify.DeclarationBounds} holds the compiled
 * declaration schemas of {@code adjudicate.decisions} and {@code replan} with their types, shipped values and bounds,
 * and the bounds of the decision declarations; a declaration outside them is the finding {@code declaration_invalid}
 * with the file, the line and the bound. It lives here rather than beside the unit loader, because the invariants use
 * it and the loader calls it, and the other placement would make the two packages depend on each other.</li>
 * <li><b>Graph</b>: a package-private graph holds the successor and predecessor edges of every state, built from the
 * transitions of every behaviour and of every declared link; the verifier, the fuzzer and the invariant checks share
 * it.</li>
 * </ul>
 */
package de.planmarshall.workflow.verify;
