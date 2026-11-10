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
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.JobRecord;
import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * Proves that the builder of {@link ProbeCandidate} builds the same value as the canonical constructor, including its
 * null refusal for the probe kind. Every value is generated; the machine scope and the job-private working directory
 * are contract values of a probe, and the digest is drawn from the generator artifact of {@code pm-runtime}.
 */
@EnableGeneratorController
@DisplayName("ProbeCandidate builder")
class ProbeCandidateRecordsTest {

    @Test
    @DisplayName("the builder builds the probe candidate the constructor builds")
    void probeCandidateBuilderEqualsConstructor() {
        var kind = Generators.enumValues(ProbeCandidate.Kind.class).next();
        var executable = digestedFile();
        var interpreter = digestedFile();
        var arguments = List.of(Generators.letterStrings(1, 20).next());
        var successExitCodes = Set.of(Generators.integers(0, 255).next());
        var scope = new JobRecord.Scope(JobRecord.Scope.Type.MACHINE, null);
        var workingDirectory = new ProbeCandidate.JobPrivate();

        var constructed = new ProbeCandidate(kind, executable, interpreter, arguments, successExitCodes, scope,
                workingDirectory);
        var built = ProbeCandidate.builder()
                .kind(kind)
                .executable(executable)
                .interpreter(interpreter)
                .arguments(arguments)
                .successExitCodes(successExitCodes)
                .scope(scope)
                .workingDirectory(workingDirectory)
                .build();

        assertEquals(constructed, built, "the builder builds the same probe candidate");
    }

    @Test
    @DisplayName("the builder refuses a missing probe kind like the constructor")
    void probeCandidateBuilderRefusesMissingKind() {
        var executable = digestedFile();
        var scope = new JobRecord.Scope(JobRecord.Scope.Type.MACHINE, null);

        var builder = ProbeCandidate.builder()
                .executable(executable)
                .arguments(List.of(Generators.letterStrings(1, 20).next()))
                .successExitCodes(Set.of(0))
                .scope(scope)
                .workingDirectory(new ProbeCandidate.JobPrivate());
        var refusal = assertThrows(NullPointerException.class, builder::build);

        assertEquals("kind", refusal.getMessage(), "the refusal names the probe kind");
    }

    private static ProbeCandidate.DigestedFile digestedFile() {
        return new ProbeCandidate.DigestedFile(Path.of("/tmp", Generators.letterStrings(1, 20).next()),
                RuntimeGenerators.sha256Digests().next());
    }
}
