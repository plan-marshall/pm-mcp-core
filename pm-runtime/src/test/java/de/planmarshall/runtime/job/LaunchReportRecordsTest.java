/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.JobRecord;

/**
 * Proves that the builder of {@link LaunchReport} builds the same value as the canonical constructor, including its
 * null refusal for the process start instant. Every value is generated; the confinement names a generated constant of
 * its closed vocabulary.
 */
@EnableGeneratorController
@DisplayName("LaunchReport builder")
class LaunchReportRecordsTest {

    @Test
    @DisplayName("the builder builds the launch report the constructor builds")
    void launchReportBuilderEqualsConstructor() {
        var pid = Generators.longs(1, 100_000).next();
        var processStartedAt = Instant.ofEpochSecond(Generators.longs(0, 1_900_000_000).next());
        var confinement = Generators.enumValues(JobRecord.Confinement.class).next();
        var landlockAbi = Generators.integers(2, 6).next();

        var constructed = new LaunchReport(pid, processStartedAt, confinement, landlockAbi);
        var built = LaunchReport.builder()
                .pid(pid)
                .processStartedAt(processStartedAt)
                .confinement(confinement)
                .landlockAbi(landlockAbi)
                .build();

        assertEquals(constructed, built, "the builder builds the same launch report");
    }

    @Test
    @DisplayName("the builder refuses a missing start instant like the constructor")
    void launchReportBuilderRefusesMissingProcessStartedAt() {
        var pid = Generators.longs(1, 100_000).next();
        var confinement = Generators.enumValues(JobRecord.Confinement.class).next();

        var builder = LaunchReport.builder().pid(pid).confinement(confinement);
        var refusal = assertThrows(NullPointerException.class, builder::build);

        assertEquals("processStartedAt", refusal.getMessage(), "the refusal names the start instant");
    }
}
