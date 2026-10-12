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
 * unique link identifiers and released states. The checks are the constants of
 * {@link de.planmarshall.workflow.verify.VerificationRule}, the only source of the rule vocabulary.</li>
 * <li><b>Result</b>: {@link de.planmarshall.workflow.verify.VerificationResult} lists every
 * {@link de.planmarshall.workflow.verify.Violation}, each naming its rule, its state and the source location of the
 * offending node. A violation is a value, never an exception, and no check stops at the first one.</li>
 * <li><b>Transition fuzzer</b>: {@link de.planmarshall.workflow.verify.TransitionFuzzer} walks a unit that passed the
 * verifier under seeded random assignments of the guard atoms its guards name (a built-in guard such as
 * {@code in_worktree}, or a {@code predicate:} primitive) and reports every reached non-terminal state
 * without a holding exit as a {@link de.planmarshall.workflow.verify.DeadState} in its
 * {@link de.planmarshall.workflow.verify.FuzzReport}; the seed, the number of runs and the steps of a run are its
 * {@link de.planmarshall.workflow.verify.FuzzConfiguration}, and the same configuration gives the same report.</li>
 * <li><b>Graph</b>: a package-private graph holds the successor and predecessor edges of every state, built from the
 * transitions of every behaviour and of every declared link; the verifier and the fuzzer share it.</li>
 * </ul>
 */
package de.planmarshall.workflow.verify;
