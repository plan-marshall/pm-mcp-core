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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.testing.generators.CoreGenerators;

/**
 * Proves that the builder of {@link VerifiedJob} builds the same value as the canonical constructor, including its
 * null refusal for the working directory. Every value is generated; the enrolment is drawn from the generator artifact
 * of {@code pm-core}.
 */
@EnableGeneratorController
@DisplayName("VerifiedJob builder")
class VerifiedJobRecordsTest {

    @Test
    @DisplayName("the builder builds the verified job the constructor builds")
    void verifiedJobBuilderEqualsConstructor() {
        var workingDirectory = Path.of("/tmp", Generators.letterStrings(1, 20).next());
        var argv = List.of(workingDirectory.resolve(Generators.letterStrings(1, 20).next()).toString());
        var templateSource = Generators.enumValues(VerifiedJob.TemplateSource.class).next();
        var projectScriptArguments = new JobRequest.Text(Generators.letterStrings(1, 20).next());
        var enrolment = CoreGenerators.projectEnrolments().next();

        var constructed = new VerifiedJob(workingDirectory, argv, templateSource, projectScriptArguments, enrolment);
        var built = VerifiedJob.builder()
                .workingDirectory(workingDirectory)
                .argv(argv)
                .templateSource(templateSource)
                .projectScriptArguments(projectScriptArguments)
                .enrolment(enrolment)
                .build();

        assertEquals(constructed, built, "the builder builds the same verified job");
    }

    @Test
    @DisplayName("the builder refuses a missing working directory like the constructor")
    void verifiedJobBuilderRefusesMissingWorkingDirectory() {
        var argv = List.of(Path.of("/usr/bin", Generators.letterStrings(1, 20).next()).toString());
        var enrolment = CoreGenerators.projectEnrolments().next();

        var refusal = assertThrows(NullPointerException.class,
                () -> VerifiedJob.builder().argv(argv).enrolment(enrolment).build());

        assertEquals("workingDirectory", refusal.getMessage(), "the refusal names the working directory");
    }
}
