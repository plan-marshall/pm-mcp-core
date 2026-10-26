/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.worker;

import java.time.Instant;
import java.util.Objects;

/**
 * One detection of a lost worker (PM-WORK-3, Detection): its reason, the generation, the instant the loss was observed,
 * the deadline that was crossed, whether the worker had made its first call, and the task it held.
 *
 * @param reason       why the worker was detected as lost
 * @param generationId the worker's generation
 * @param measuredAt   the instant the loss was observed
 * @param deadline     the deadline of the signal; equal to {@code measuredAt} for an observed exit, which crosses none
 * @param firstCallMade whether the worker had made its first call
 * @param leasedTask   the queue task the worker held; {@code null} while it held none
 * @since 0.1
 */
public record LossSignal(LossReason reason, String generationId, Instant measuredAt, Instant deadline,
boolean firstCallMade, String leasedTask) {

    /**
     * @throws NullPointerException     if a required component is {@code null}
     * @throws IllegalArgumentException if the loss is observed before the deadline it crossed
     */
    public LossSignal {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(generationId, "generationId");
        Objects.requireNonNull(measuredAt, "measuredAt");
        Objects.requireNonNull(deadline, "deadline");
        if (!reason.equals(LossReason.EXIT) && measuredAt.isBefore(deadline)) {
            throw new IllegalArgumentException(
                    "A loss is observed at or after its deadline: %s before %s".formatted(measuredAt, deadline));
        }
    }
}
