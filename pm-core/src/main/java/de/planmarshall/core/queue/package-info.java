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
 * The task queue store: the queue task records, the task leases and the session generation record.
 * <p>
 * Specification:
 * <ul>
 * <li>{@code doc/specification/model-work/02-task-protocol.adoc} (Queue Task)</li>
 * <li>{@code doc/specification/store-schemas/16-task-queue.adoc}</li>
 * </ul>
 */
package de.planmarshall.core.queue;
