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
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.SupportedVersions;
import de.planmarshall.core.role.RoleClass;

/**
 * Proves that the builder of {@link HeadlessBlock} builds the value its canonical constructor builds, including
 * its null refusal for the executable. Every value is generated; the model efforts name the wildcard key, the
 * version range is a plain comparator pair, and the event mapping is absent for a harness without an event stream.
 */
@EnableGeneratorController
@DisplayName("HeadlessBlock builder")
class HeadlessBlockRecordsTest {

    @Test
    @DisplayName("the builder builds the headless block the constructor builds")
    void headlessBlockBuilderEqualsConstructor() {
        var executable = Generators.letterStrings(1, 20).next();
        var argv = List.of("--print");
        var classArgs = Map.of(RoleClass.WRITER, List.of("--writer"));
        var agentMode = HeadlessBlock.AgentMode.INLINE;
        var mcpConfig = new HeadlessBlock.McpConfig(HeadlessBlock.McpDelivery.SLOT, null, false);
        var env = Map.of("DISABLE_TOOL_SEARCH", "1");
        var outputFormat = HeadlessBlock.OutputFormat.STREAM_JSON;
        var modelTiers = Map.of("frontier", Generators.letterStrings(1, 20).next());
        var modelEfforts = Map.of("*", List.of("low", "high"));
        var resourceTools = List.of("resources_list");
        var dataHome = new HeadlessBlock.DataHome("PM_DATA_HOME", "login.json");
        var sessionResidue = HeadlessBlock.SessionResidue.EPHEMERAL;
        var resumeArgv = List.of("--resume", "{{session_id}}");
        var capabilities = Set.of("result_schema");
        var updateEvidence = new HeadlessBlock.UpdateEvidence(HeadlessBlock.EvidenceMethod.NONE, null);
        var supportedVersions = SupportedVersions.parse(">=1.0.0 <2.0.0", "profile",
                "/headless/supported_versions");
        var versionProbeArgv = List.of("--version");
        var versionPattern = "version ([0-9.]+)";
        var writablePaths = List.of("~/.claude.json");
        var usagePointers = Map.of("input_tokens", "/usage/input_tokens");

        var constructed = new HeadlessBlock(executable, argv, classArgs, agentMode, mcpConfig, env, outputFormat,
                modelTiers, modelEfforts, resourceTools, dataHome, sessionResidue, resumeArgv, capabilities,
                updateEvidence, supportedVersions, versionProbeArgv, versionPattern, writablePaths, usagePointers,
                null);
        var built = HeadlessBlock.builder()
                .executable(executable)
                .argv(argv)
                .classArgs(classArgs)
                .agentMode(agentMode)
                .mcpConfig(mcpConfig)
                .env(env)
                .outputFormat(outputFormat)
                .modelTiers(modelTiers)
                .modelEfforts(modelEfforts)
                .resourceTools(resourceTools)
                .dataHome(dataHome)
                .sessionResidue(sessionResidue)
                .resumeArgv(resumeArgv)
                .capabilities(capabilities)
                .updateEvidence(updateEvidence)
                .supportedVersions(supportedVersions)
                .versionProbeArgv(versionProbeArgv)
                .versionPattern(versionPattern)
                .writablePaths(writablePaths)
                .usagePointers(usagePointers)
                .events(null)
                .build();

        assertEquals(constructed, built, "the builder builds the same headless block");
    }

    @Test
    @DisplayName("the builder refuses a missing executable like the constructor")
    void headlessBlockBuilderRefusesMissingExecutable() {
        var refusal = assertThrows(NullPointerException.class, () -> HeadlessBlock.builder().build());

        assertEquals("executable", refusal.getMessage(), "the refusal names the executable");
    }
}
