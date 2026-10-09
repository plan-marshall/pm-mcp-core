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
 * The worker supervisor, one per project and role: starting, acknowledgement, silence and exit detection,
 * recycling, ending and fencing of workers.
 * <p>
 * Specification: {@code doc/specification/model-work/03-supervision-and-recycling.adoc}.
 */
package de.planmarshall.runtime.worker;
