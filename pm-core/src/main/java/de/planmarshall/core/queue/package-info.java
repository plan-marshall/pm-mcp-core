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
 * The task queue store of a project below {@code projects/<project_id>/queue/} (PM-WORK-2, PM-WORK-5).
 * <p>
 * The package holds the session generation record {@code queue/session-generations.json}: the generation id
 * ({@link de.planmarshall.core.queue.GenerationId}), what issued a generation
 * ({@link de.planmarshall.core.queue.GenerationCause}), the typed closed entry
 * ({@link de.planmarshall.core.queue.SessionGeneration}), its strict codec
 * ({@link de.planmarshall.core.queue.SessionGenerationCodec}), the check of the generation a call carries
 * ({@link de.planmarshall.core.queue.GenerationCheck}: current, fenced, superseded by an enter, unknown) and the store
 * ({@link de.planmarshall.core.queue.SessionGenerationStore}) that issues, fences, assigns a scope and removes old
 * fenced generations under the queue lock {@code locks/queue.lock}, with one atomic write-and-rename per change and
 * lock-free reads. The record is no part of a scope's transaction journal and has no digest sidecar.
 * <p>
 * Specification:
 * <ul>
 * <li>{@code doc/specification/model-work/02-task-protocol.adoc} (Queue Task)</li>
 * <li>{@code doc/specification/store-schemas/16-task-queue.adoc} (Session Generation Record)</li>
 * <li>{@code plan-marshall-documentation/doc/specification/runtime-model/03-relay.adoc} (Generations at the Relay)</li>
 * </ul>
 */
package de.planmarshall.core.queue;
