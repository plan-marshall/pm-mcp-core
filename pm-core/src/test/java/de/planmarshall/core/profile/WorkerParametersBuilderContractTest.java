/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.profile;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builder of {@link WorkerParameters} builds the value the canonical constructor builds, and a missing deadline is
 * the {@code null} state the constructor builds from a {@code null} deadline.
 */
@DisplayName("Worker parameters builder")
class WorkerParametersBuilderContractTest {

    @Test
    @DisplayName("the builder builds the constructor's value, the missing deadline included")
    void shouldBuildLikeTheConstructor() {
        var canonical = new WorkerParameters(true, 270, false, 30, 3, 240, true);
        var built = WorkerParameters.builder().eligible(canonical.eligible())
                .ackDeadlineSeconds(canonical.ackDeadlineSeconds())
                .ackInformational(canonical.ackInformational())
                .silenceGraceSeconds(canonical.silenceGraceSeconds()).idleWakeups(canonical.idleWakeups())
                .boundedWaitSeconds(canonical.boundedWaitSeconds())
                .progressResetsTimeout(canonical.progressResetsTimeout()).build();
        var withoutDeadline = new WorkerParameters(true, null, false, 30, 3, 240, true);
        var builtWithoutDeadline = WorkerParameters.builder().eligible(true).ackInformational(false)
                .silenceGraceSeconds(30).idleWakeups(3).boundedWaitSeconds(240).progressResetsTimeout(true)
                .build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> assertEquals(withoutDeadline, builtWithoutDeadline,
                        "a missing deadline builds like a null one"),
                () -> assertNull(builtWithoutDeadline.ackDeadlineSeconds(),
                        "a missing deadline is the null state"));
    }
}
