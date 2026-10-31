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
 * The consultation of one role by another (Model Work § Consultation; Timeouts &amp; Budgets § Consultation Bound).
 * <p>
 * {@link ConsultationService} derives the {@code consult:<role>} links a task's entity offers, opens the consultation
 * task of the consulted role, records its answer or its loss, and validates the {@code consultation_ids} of a submit.
 * The rules of the link — one open consultation, the compiled bound of three over all attempts, and depth one — are
 * derived from the asking task's queue task record.
 * <p>
 * Specification: {@code doc/specification/model-work/05-consultation.adoc};
 * {@code doc/specification/timeouts/01-job-budgets-and-waits.adoc#consultation-bound}.
 */
@NullMarked
package de.planmarshall.workflow.consult;

import org.jspecify.annotations.NullMarked;
