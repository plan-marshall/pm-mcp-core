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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.role.RoleClass;

/**
 * The projection of a profile's {@code headless} block: the templates per class, the tier map, the effort values, the
 * version range, the agent mode, the MCP delivery and the capabilities.
 *
 * <p>
 * The unknown tier, the unmapped model, the versions inside and outside the range and the operator's home are
 * generated. The literals that stay are the contract: the values of the fixture profile the projection reads back
 * (executable, argv placeholder, environment, resource tools, writable paths, version probe and pattern, tier model and
 * efforts) and the bounds {@code >=1.0.0 <2.0.0} of its supported range, which the versions are drawn against.
 */
@EnableGeneratorController
@DisplayName("Headless block")
class HeadlessBlockTest {

    private final HeadlessBlock block = LaunchFixtures.block();

    @Test
    @DisplayName("projects the launch contract of the profile")
    void projectsTheLaunchContract() {
        assertAll(
                () -> assertEquals("claude", block.executable(), "the profile's executable is projected"),
                () -> assertTrue(block.argv().contains("{{prompt_file}}"), "the argv carries the prompt placeholder"),
                () -> assertEquals(HeadlessBlock.AgentMode.INLINE, block.agentMode(), "the agent mode is inline"),
                () -> assertEquals(HeadlessBlock.McpDelivery.SLOT, block.mcpConfig().delivery(),
                        "the MCP config is delivered by slot"),
                () -> assertTrue(block.mcpConfig().exclusive(), "the MCP config is exclusive"),
                () -> assertEquals("1", block.env().get("DISABLE_TOOL_SEARCH"),
                        "the profile's environment is projected"),
                () -> assertEquals(HeadlessBlock.OutputFormat.STREAM_JSON, block.outputFormat(),
                        "the output format is stream JSON"),
                () -> assertEquals(List.of("resources_list", "resources_read"), block.resourceTools(),
                        "the resource tools are projected"),
                () -> assertEquals(List.of("~/.claude.json", "~/.claude/sessions"), block.writablePaths(),
                        "the writable paths are projected"),
                () -> assertEquals(HeadlessBlock.SessionResidue.EPHEMERAL, block.sessionResidue(),
                        "the session residue is ephemeral"),
                () -> assertTrue(block.extensionIsolation(), "the extension isolation is declared"),
                () -> assertTrue(block.projectSettingsControl(), "the project settings control is declared"),
                () -> assertEquals(List.of("--version"), block.versionProbeArgv(), "the version probe is projected"),
                () -> assertEquals("(\\d+\\.\\d+\\.\\d+)", block.versionPattern(),
                        "the version pattern is projected"));
    }

    @Test
    @DisplayName("a class the profile declares is runnable, one it does not is not")
    void classes() {
        assertAll(
                () -> assertTrue(block.runs(RoleClass.WRITER), "the profile runs the writer class"),
                () -> assertTrue(block.runs(RoleClass.READ_ONLY), "the profile runs the read-only class"),
                () -> assertTrue(block.runs(RoleClass.ZERO_TOOL), "the profile runs the zero-tool class"),
                () -> assertTrue(block.argumentsFor(RoleClass.ZERO_TOOL).contains("--setting-sources"),
                        "the zero-tool class has its own arguments"));
    }

    @Test
    @DisplayName("renders a tier into the model the harness maps it to")
    void tiers() {
        var unknown = "unknown-" + Generators.letterStrings(2, 10).next();
        assertAll(
                () -> assertEquals("claude-opus-4-1", block.tierModel("frontier").orElseThrow(),
                        "the frontier tier maps to its model"),
                () -> assertTrue(block.tierModel(unknown).isEmpty(), "an unknown tier maps to no model"));
    }

    @Test
    @DisplayName("reads the accepted efforts, with the wildcard for the other models")
    void efforts() {
        var another = "another-" + Generators.letterStrings(2, 10).next();
        assertAll(
                () -> assertEquals(List.of("low", "medium", "high"), block.efforts("claude-opus-4-1"),
                        "a mapped model accepts the wildcard efforts"),
                () -> assertEquals(List.of("low", "medium", "high"), block.efforts(another),
                        "another model accepts the wildcard efforts"));
    }

    @Test
    @DisplayName("checks the supported version range")
    void supportedVersions() {
        var inside = "1." + minor();
        var above = Generators.integers(2, 99).next() + "." + minor();
        var below = "0." + minor();
        assertAll(
                () -> assertTrue(block.supports(inside), "a version inside the range is supported"),
                () -> assertFalse(block.supports(above), "a version above the range is not supported"),
                () -> assertFalse(block.supports(below), "a version below the range is not supported"));
    }

    @Test
    @DisplayName("a profile without a headless block is not a launch contract")
    void noHeadless() {
        var profile = LaunchFixtures.profile("{\"client_id\":\"claude\",\"headless\":null}");
        assertThrows(IllegalArgumentException.class, () -> HeadlessBlock.of(profile),
                "a profile without headless block is refused");
    }

    @Test
    @DisplayName("the writable sub-paths resolve the profile's home")
    void writablePathsResolveTheHome() {
        var home = Path.of("/home", Generators.letterStrings(2, 12).next());
        assertEquals(List.of(home.resolve(".claude.json"), home.resolve(".claude/sessions")),
                block.writablePaths(home), "the writable paths resolve against the home");
    }

    private static String minor() {
        return Generators.integers(0, 99).next() + "." + Generators.integers(0, 99).next();
    }
}
