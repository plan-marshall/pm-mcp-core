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
 * The qualification records of the role sets and whether they qualify a role's configuration (PM-WORK-6).
 * <p>
 * A record is the measurement of one grading: per form and per topic the agreement and the item count, per form the
 * tokens and the time, the warm-fresh gap, and every item's answer, score, tokens and time. {@link QualificationRecord}
 * is that value, {@link QualificationRecordCodec} its one wire shape, and {@link QualificationRecordStore} the project
 * set's writer. The record holds no pass flag: {@link QualificationCheck} decides at every check, against the project's
 * current agreement level and allowed gap, whether a record qualifies the fresh form, the warm form, or no form at all.
 * <p>
 * {@link ConstantBaseline} bounds every topic by what a constant answerer reaches; {@link Scoring} scores a closed
 * item, and a rubric item goes to the judge through {@link JudgeScorer}. {@link JudgeInstrumentCheck} validates the
 * judge before it grades a rubric item, and {@link JudgeCheckStore} holds its measurements. A grading run is offered
 * through {@link GradingHarness} and assembled by the model caller's grader.
 * <p>
 * Specification: {@code doc/specification/model-work/01-roles-and-configuration.adoc} (Role Qualification, Qualification
 * Records, Constant Baseline, Corpus Topics of a Role, Grading, Judge Instrument Check) and
 * {@code doc/specification/store-schemas/16-task-queue.adoc} (Role Qualification Record, Judge Check Record).
 */
package de.planmarshall.core.role.qualification;
