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
 * The task queue store: one queue task record per unit of model work, the delivery of a task to a worker generation,
 * the generation fence and the activity journal (Store Schemas § Task Queue, § Queue Task Record and § Activity
 * Journal).
 * <p>
 * The package owns the records and their atomic writes under the queue lock of a project or of the machine scope; the
 * protocol that moves a task — offer, acknowledgement, lease, fencing, outcome, re-offer and skill delivery — is
 * {@code de.planmarshall.workflow.task}. The record is the authority for the delivery; the scope store is the
 * authority for which tasks a model state owns and how they ended.
 * <p>
 * The records of the machine scope live in the machine's own queue {@code machine/queue/} under
 * {@code machine/locks/queue.lock}, beside the per-project queue of each project.
 *
 * @since 0.1
 */
@NullMarked
package de.planmarshall.core.queue;

import org.jspecify.annotations.NullMarked;
