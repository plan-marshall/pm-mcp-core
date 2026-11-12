/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.launch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * Proves that the builder of {@link HarnessEnrolment} builds the value its canonical constructor builds, including its
 * null refusal for the client id. Every value is generated; the entry has an absolute executable path and its digest,
 * one reported event, and one recorded weights identity.
 */
@EnableGeneratorController
@DisplayName("HarnessEnrolment builder")
class HarnessEnrolmentRecordsTest {

    @Test
    @DisplayName("the builder builds the harness enrolment the constructor builds")
    void harnessEnrolmentBuilderEqualsConstructor() {
        var clientId = "claude";
        var executablePath = Path.of("/tmp", Generators.letterStrings(1, 12).next(), "claude");
        var sha256 = RuntimeGenerators.sha256Digests().next();
        var version = "1.5.0";
        var enrolledAt = Instant.ofEpochMilli(Generators.longs(0, 1_900_000_000_000L).next());
        var updatedAt = Instant.ofEpochMilli(Generators.longs(0, 1_900_000_000_000L).next());
        var usageReport = new HarnessEnrolment.UsageReport(HarnessEnrolment.UsageSource.DECLARED, Map.of());
        var events = Map.of("text", "reported");
        var weights = Map.of(Generators.letterStrings(1, 20).next(), new HarnessEnrolment.Weights(
                Generators.letterStrings(1, 64).next(), enrolledAt));

        var constructed = new HarnessEnrolment(clientId, executablePath, sha256, version, enrolledAt, updatedAt,
                usageReport, events, weights);
        var built = HarnessEnrolment.builder()
                .clientId(clientId)
                .executablePath(executablePath)
                .sha256(sha256)
                .version(version)
                .enrolledAt(enrolledAt)
                .updatedAt(updatedAt)
                .usageReport(usageReport)
                .events(events)
                .weights(weights)
                .build();

        assertEquals(constructed, built, "the builder builds the same harness enrolment");
    }

    @Test
    @DisplayName("the builder refuses a missing client id like the constructor")
    void harnessEnrolmentBuilderRefusesMissingClientId() {
        var builder = HarnessEnrolment.builder();
        var refusal = assertThrows(NullPointerException.class, builder::build);

        assertEquals("clientId", refusal.getMessage(), "the refusal names the client id");
    }
}
