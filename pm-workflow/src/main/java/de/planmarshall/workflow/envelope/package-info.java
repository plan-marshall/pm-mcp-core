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
 * The task envelopes: the packing of plan tasks into envelopes by their predicted cost.
 * <p>
 * {@link de.planmarshall.workflow.envelope.TaskCostEstimator} sizes a plan task into a
 * {@link de.planmarshall.workflow.envelope.CostSize} and looks its predicted tokens up in a
 * {@link de.planmarshall.workflow.envelope.SizeTokenTable}; {@link de.planmarshall.workflow.envelope.EnvelopeBudget}
 * resolves the per-envelope budget; {@link de.planmarshall.workflow.envelope.EnvelopePacker} packs the ordered tasks
 * into envelopes under that budget. The package is a pure, deterministic computation over plain values: it reads no
 * store, no queue, no role artifact and no harness profile; the caller passes every input.
 * <p>
 * Specification: {@code doc/specification/model-work/06-envelopes-questions-deadlines.adoc}.
 */
@NullMarked
package de.planmarshall.workflow.envelope;

import org.jspecify.annotations.NullMarked;
