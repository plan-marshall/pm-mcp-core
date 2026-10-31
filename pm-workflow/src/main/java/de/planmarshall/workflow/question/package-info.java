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
 * The question records and their adjudication — a question has no deadline and no fallback by time: it ends by an
 * answer, a withdrawal, or the end of its scope — and the server's decision whether a task continues after its
 * question, with the bound on the {@code ask} link: the form (hold, resume or fallback) from the state, the role and the
 * harness, and at most three questions per state entry and per plan task. The record store itself lives in
 * {@code core.store}.
 * <p>
 * Specification:
 * <ul>
 * <li>{@code doc/specification/model-work/06-envelopes-questions-deadlines.adoc}</li>
 * <li>{@code doc/specification/phase-workflows/07-questions-waivers-operator-influence.adoc}</li>
 * <li>{@code doc/specification/timeouts/01-job-budgets-and-waits.adoc}</li>
 * </ul>
 */
@NullMarked
package de.planmarshall.workflow.question;

import org.jspecify.annotations.NullMarked;
