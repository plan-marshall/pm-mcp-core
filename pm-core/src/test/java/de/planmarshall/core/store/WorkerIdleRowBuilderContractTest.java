/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Worker idle row builder contract")
class WorkerIdleRowBuilderContractTest {

    @Test
    @DisplayName("the builder builds the constructor's worker-idle row")
    void builderBuildsIdleRow() {
        var usage = new HarnessUsage(Measurement.measured(10), Measurement.measured(20),
                Measurement.measured(0), Measurement.measured(0), Measurement.measured(0),
                Measurement.measured(1), null, null);
        var canonical = new WorkerIdleRow("l-0123456789abcdef", "writer", "opencode", "1.2.3", "claude",
                usage, 0, Instant.parse("2026-10-22T10:00:00Z"));

        var built = WorkerIdleRow.builder().jobId(canonical.jobId()).role(canonical.role())
                .harness(canonical.harness()).harnessVersion(canonical.harnessVersion())
                .model(canonical.model()).usage(canonical.usage()).idleWakeups(canonical.idleWakeups())
                .recordedAt(canonical.recordedAt()).build();

        var fromConstructor = assertThrows(NullPointerException.class,
                () -> new WorkerIdleRow(null, canonical.role(), canonical.harness(),
                        canonical.harnessVersion(), canonical.model(), canonical.usage(),
                        canonical.idleWakeups(), canonical.recordedAt()),
                "the constructor refuses an absent job id");
        var incomplete = WorkerIdleRow.builder().role(canonical.role()).harness(canonical.harness())
                .harnessVersion(canonical.harnessVersion()).model(canonical.model())
                .usage(canonical.usage()).idleWakeups(canonical.idleWakeups())
                .recordedAt(canonical.recordedAt());
        var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                "the builder refuses an absent job id");

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                        "both refuse an absent id with the same message"));
    }
}
