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
 * The adapter through which the daemon hands the engine the question record a {@code question} state waits on
 * (PM-ARCH-4, PM-IMPL-5): {@link de.planmarshall.runtime.question.QuestionLogRecordReader} implements the engine port
 * {@link de.planmarshall.core.spi.QuestionRecordReader} over the question log
 * {@link de.planmarshall.core.store.QuestionLog} of {@code core.store} — the {@code questions.jsonl} of a plan, an epic
 * or the workspace scope (ADR {@code core-store/032}).
 * <p>
 * The read is tri-state: {@code Open}, {@code Resolved} with the closed resolution outcome, or {@code Unreadable}. The
 * log is judged as a whole, so an absent log, a refused line, a torn tail and an unreadable store are never taken for an
 * open record (ADR {@code core-store/039}). The read takes no lock, writes nothing, and passes the log path through the
 * workspace confinement, where a refused path is a value (PM-SEC-2).
 * <p>
 * Specification:
 * <ul>
 * <li>{@code doc/specification/store-schemas/14-plan-instance-waivers-questions.adoc} § Question Record</li>
 * <li>{@code doc/specification/model-work/06-envelopes-questions-deadlines.adoc} § Question Records</li>
 * </ul>
 */
package de.planmarshall.runtime.question;
