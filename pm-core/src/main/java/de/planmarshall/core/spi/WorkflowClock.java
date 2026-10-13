/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.spi;

import java.time.Instant;

/**
 * The clock port of the engine (PM-ARCH-4, bullets 4 and 5; PM-IMPL-5). Every instant the engine writes into the
 * status store ({@code updated_at}, the {@code pushed_at} of a call frame, the {@code raised_at} of a condition) and
 * every deadline of a time wait it compares against is read from here, never from the system clock directly, so the
 * persisted state depends on nothing the engine keeps in memory and a test drives time deterministically.
 * <p>
 * The runtime of {@code pm-runtime} implements the port over the system clock; no class of {@code pm-core} does.
 *
 * @since 0.1
 */
@FunctionalInterface
public interface WorkflowClock {

    /**
     * @return the current instant
     */
    Instant now();
}
