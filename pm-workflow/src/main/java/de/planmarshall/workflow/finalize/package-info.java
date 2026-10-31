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
 * The finalize steps: the descriptor of a step, the checks over the pipeline of steps, and the currency of a
 * recorded verdict.
 * <p>
 * {@link de.planmarshall.workflow.finalize.FinalizeStepDescriptor} holds the facts of one step, and
 * {@link de.planmarshall.workflow.finalize.FinalizePipelineValidator} checks a roster of them against the
 * {@link de.planmarshall.workflow.finalize.PipelineRule}s, reporting every
 * {@link de.planmarshall.workflow.finalize.PipelineViolation} as a value. The input of the validator is a roster: a
 * project's roster is the default descriptors of the steps its finalize unit contains plus its project step
 * descriptors. The default roster lives in the assembly; this package holds no table of the default steps.
 * <p>
 * Specification:
 * <ul>
 * <li>{@code doc/specification/phase-workflows/04-finalize-step-bands.adoc}</li>
 * <li>{@code doc/specification/phase-workflows/05-finalize-review-metrics-descriptor.adoc}</li>
 * <li>{@code doc/specification/phase-workflows/06-finalize-re-entry-and-merge.adoc}</li>
 * </ul>
 */
@NullMarked
package de.planmarshall.workflow.finalize;

import org.jspecify.annotations.NullMarked;
