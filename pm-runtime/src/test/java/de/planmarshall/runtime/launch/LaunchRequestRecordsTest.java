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

import static org.easymock.EasyMock.createMock;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import java.time.InstantSource;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * Proves that the builder of {@link LaunchRequest} builds the value its canonical constructor builds, including
 * its null refusal for the effective role. The values are generated or drawn from the launch fixtures; the interfaces
 * are mocks the builder only stores, and the job-private launch names no plan, no working directory and no envelope.
 */
@EnableGeneratorController
@DisplayName("LaunchRequest builder")
class LaunchRequestRecordsTest {

    @Test
    @DisplayName("the builder builds the launch request the constructor builds")
    void launchRequestBuilderEqualsConstructor() {
        var skills = LaunchFixtures.skills();
        InstantSource clock = createMock(InstantSource.class);
        var role = LaunchFixtures.writer(skills);
        var block = LaunchFixtures.block();
        var enrolment = LaunchFixtures.enrolment(
                Path.of("/tmp", Generators.letterStrings(1, 12).next(), "claude"),
                RuntimeGenerators.sha256Digests().next(), "1.5.0");
        var generationId = Generators.letterStrings(4, 20).next();
        var runtimeHome = Path.of("/tmp", Generators.letterStrings(1, 12).next(), "runtime");
        var jobPrivateDirectory = Path.of("/tmp", Generators.letterStrings(1, 12).next(), "jobs", generationId);
        var workdirKind = LaunchRequest.WorkdirKind.JOB_PRIVATE;
        var relaySocket = Path.of("/tmp", Generators.letterStrings(1, 12).next(), "pm.sock");
        var shimParagraph = Generators.letterStrings(1, 40).next();
        var fresh = Generators.booleans().next();
        var writesFreeText = Generators.booleans().next();
        var stateSkills = List.of(Generators.letterStrings(1, 40).next());
        var domainSkills = List.of(Generators.letterStrings(1, 40).next());
        var inBandBoundChars = Generators.integers(0, 10_000).next();
        var zeroToolPrompt = Generators.letterStrings(1, 40).next();
        var resumeSessionId = Generators.letterStrings(1, 40).next();

        var constructed = new LaunchRequest(role, block, enrolment, generationId, runtimeHome, jobPrivateDirectory,
                workdirKind, null, null, relaySocket, shimParagraph, skills, fresh, writesFreeText, stateSkills,
                domainSkills, null, inBandBoundChars, zeroToolPrompt, resumeSessionId, clock);
        var built = LaunchRequest.builder()
                .role(role)
                .block(block)
                .enrolment(enrolment)
                .generationId(generationId)
                .runtimeHome(runtimeHome)
                .jobPrivateDirectory(jobPrivateDirectory)
                .workdirKind(workdirKind)
                .planId(null)
                .workdir(null)
                .relaySocket(relaySocket)
                .shimParagraph(shimParagraph)
                .skills(skills)
                .fresh(fresh)
                .writesFreeText(writesFreeText)
                .stateSkills(stateSkills)
                .domainSkills(domainSkills)
                .envelope(null)
                .inBandBoundChars(inBandBoundChars)
                .zeroToolPrompt(zeroToolPrompt)
                .resumeSessionId(resumeSessionId)
                .clock(clock)
                .build();

        assertEquals(constructed, built, "the builder builds the same launch request");
    }

    @Test
    @DisplayName("the builder refuses a missing role like the constructor")
    void launchRequestBuilderRefusesMissingRole() {
        var builder = LaunchRequest.builder();
        var refusal = assertThrows(NullPointerException.class, builder::build);

        assertEquals("role", refusal.getMessage(), "the refusal names the effective role");
    }
}
