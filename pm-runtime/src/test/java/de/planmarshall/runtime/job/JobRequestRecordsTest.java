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
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves that the builder of {@link JobRequest} builds the same value as the canonical constructor, including its null
 * refusal for the project id. Every value is generated; the target names a generated verb of the host-tool build
 * vocabulary.
 */
@EnableGeneratorController
@DisplayName("JobRequest builder")
class JobRequestRecordsTest {

    @Test
    @DisplayName("the builder builds the job request the constructor builds")
    void jobRequestBuilderEqualsConstructor() {
        var projectId = Generators.letterStrings(1, 20).next();
        var workingDirectory = Generators.letterStrings(1, 20).next();
        var liveWorktrees = Set.of(Path.of("/tmp", Generators.letterStrings(1, 20).next()));
        var modules = Set.of(Generators.letterStrings(1, 20).next());
        var target = new JobRequest.HostToolBuild(Generators.letterStrings(1, 20).next(), null);

        var constructed = new JobRequest(projectId, workingDirectory, liveWorktrees, modules, target);
        var built = JobRequest.builder()
                .projectId(projectId)
                .workingDirectory(workingDirectory)
                .liveWorktrees(liveWorktrees)
                .modules(modules)
                .target(target)
                .build();

        assertEquals(constructed, built, "the builder builds the same job request");
    }

    @Test
    @DisplayName("the builder refuses a missing project id like the constructor")
    void jobRequestBuilderRefusesMissingProjectId() {
        var target = new JobRequest.HostToolBuild(Generators.letterStrings(1, 20).next(), null);

        var builder = JobRequest.builder()
                .workingDirectory(Generators.letterStrings(1, 20).next())
                .liveWorktrees(Set.of(Path.of("/tmp", Generators.letterStrings(1, 20).next())))
                .modules(Set.of(Generators.letterStrings(1, 20).next()))
                .target(target);
        var refusal = assertThrows(NullPointerException.class, builder::build);

        assertEquals("projectId", refusal.getMessage(), "the refusal names the project id");
    }
}
