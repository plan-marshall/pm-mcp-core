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
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.JobId;
import de.planmarshall.core.store.JobRecord;

/**
 * Proves that the builders of the {@link JobEvidence} records build the same value as the canonical constructors. The
 * parse result validates no component for null, so it has no refusal to mirror; the process end refuses a missing job
 * kind, which the builder carries too. Every value is generated; the empty declared exit-code set is the contract
 * value of a job that is no probe.
 */
@EnableGeneratorController
@DisplayName("JobEvidence record builders")
class JobEvidenceRecordsTest {

    @Test
    @DisplayName("the parse result builder builds the parse result the constructor builds")
    void parseResultBuilderEqualsConstructor() {
        var successMarker = Generators.booleans().next();
        var diagnostics = Generators.integers(0, 1000).next();
        var contradictingEvidence = Generators.booleans().next();
        var selfReportContradiction = new JobRecord.SelfReportContradiction(
                Generators.letterStrings(1, 20).next(), Generators.longs(0, 255).next(),
                Generators.letterStrings(1, 20).next());

        var constructed = new JobEvidence.ParseResult(successMarker, diagnostics, contradictingEvidence,
                selfReportContradiction);
        var built = JobEvidence.ParseResult.builder()
                .successMarker(successMarker)
                .diagnostics(diagnostics)
                .contradictingEvidence(contradictingEvidence)
                .selfReportContradiction(selfReportContradiction)
                .build();

        assertEquals(constructed, built, "the builder builds the same parse result");
    }

    @Test
    @DisplayName("the process end builder builds the process end the constructor builds")
    void processEndedBuilderEqualsConstructor() {
        var kind = Generators.enumValues(JobId.Kind.class).next();
        var termination = Generators.enumValues(JobEvidence.Termination.class).next();
        var waitStatus = new JobEvidence.Exited(Generators.integers(0, 255).next());
        var log = Generators.enumValues(JobEvidence.LogState.class).next();
        var parse = new JobEvidence.ParseResult(Generators.booleans().next(),
                Generators.integers(0, 1000).next(), Generators.booleans().next(), null);
        var declaredExitCodes = Set.<Integer>of();
        var exitReliable = true;

        var constructed = new JobEvidence.ProcessEnded(kind, termination, waitStatus, log, parse, declaredExitCodes,
                exitReliable);
        var built = JobEvidence.ProcessEnded.builder()
                .kind(kind)
                .termination(termination)
                .waitStatus(waitStatus)
                .log(log)
                .parse(parse)
                .declaredExitCodes(declaredExitCodes)
                .exitReliable(exitReliable)
                .build();

        assertEquals(constructed, built, "the builder builds the same process end");
    }

    @Test
    @DisplayName("the process end builder refuses a missing job kind like the constructor")
    void processEndedBuilderRefusesMissingKind() {
        var waitStatus = new JobEvidence.Exited(Generators.integers(0, 255).next());
        var log = Generators.enumValues(JobEvidence.LogState.class).next();

        var refusal = assertThrows(NullPointerException.class, () -> JobEvidence.ProcessEnded.builder()
                .waitStatus(waitStatus)
                .log(log)
                .parse(new JobEvidence.ParseResult(Generators.booleans().next(),
                        Generators.integers(0, 1000).next(), Generators.booleans().next(), null))
                .declaredExitCodes(Set.<Integer>of())
                .exitReliable(true)
                .build());

        assertEquals("kind", refusal.getMessage(), "the refusal names the job kind");
    }
}
