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

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.role.RoleClass;

/**
 * The projection of a profile's {@code headless} block: the templates per class, the tier map, the effort values, the
 * version range, the agent mode, the MCP delivery and the capabilities.
 */
@DisplayName("Headless block")
class HeadlessBlockTest {

    private final HeadlessBlock block = LaunchFixtures.block();

    @Test
    @DisplayName("projects the launch contract of the profile")
    void projectsTheLaunchContract() {
        assertAll(
                () -> assertEquals("claude", block.executable()),
                () -> assertTrue(block.argv().contains("{{prompt_file}}")),
                () -> assertEquals(HeadlessBlock.AgentMode.INLINE, block.agentMode()),
                () -> assertEquals(HeadlessBlock.McpDelivery.SLOT, block.mcpConfig().delivery()),
                () -> assertTrue(block.mcpConfig().exclusive()),
                () -> assertEquals("1", block.env().get("DISABLE_TOOL_SEARCH")),
                () -> assertEquals(HeadlessBlock.OutputFormat.STREAM_JSON, block.outputFormat()),
                () -> assertEquals(List.of("resources_list", "resources_read"), block.resourceTools()),
                () -> assertEquals(List.of("~/.claude.json", "~/.claude/sessions"), block.writablePaths()),
                () -> assertEquals(HeadlessBlock.SessionResidue.EPHEMERAL, block.sessionResidue()),
                () -> assertTrue(block.extensionIsolation()),
                () -> assertTrue(block.projectSettingsControl()),
                () -> assertEquals(List.of("--version"), block.versionProbeArgv()),
                () -> assertEquals("(\\d+\\.\\d+\\.\\d+)", block.versionPattern()));
    }

    @Test
    @DisplayName("a class the profile declares is runnable, one it does not is not")
    void classes() {
        assertAll(
                () -> assertTrue(block.runs(RoleClass.WRITER)),
                () -> assertTrue(block.runs(RoleClass.READ_ONLY)),
                () -> assertTrue(block.runs(RoleClass.ZERO_TOOL)),
                () -> assertTrue(block.argumentsFor(RoleClass.ZERO_TOOL).contains("--setting-sources")));
    }

    @Test
    @DisplayName("renders a tier into the model the harness maps it to")
    void tiers() {
        assertAll(
                () -> assertEquals("claude-opus-4-1", block.tierModel("frontier").orElseThrow()),
                () -> assertTrue(block.tierModel("unknown").isEmpty()));
    }

    @Test
    @DisplayName("reads the accepted efforts, with the wildcard for the other models")
    void efforts() {
        assertAll(
                () -> assertEquals(List.of("low", "medium", "high"), block.efforts("claude-opus-4-1")),
                () -> assertEquals(List.of("low", "medium", "high"), block.efforts("another-model")));
    }

    @Test
    @DisplayName("checks the supported version range")
    void supportedVersions() {
        assertAll(
                () -> assertTrue(block.supports("1.4.2")),
                () -> assertFalse(block.supports("2.0.0")),
                () -> assertFalse(block.supports("0.9.9")));
    }

    @Test
    @DisplayName("a profile without a headless block is not a launch contract")
    void noHeadless() {
        var profile = LaunchFixtures.profile("{\"client_id\":\"claude\",\"headless\":null}");
        assertThrows(IllegalArgumentException.class, () -> HeadlessBlock.of(profile));
    }

    @Test
    @DisplayName("the writable sub-paths resolve the profile's home")
    void writablePathsResolveTheHome() {
        var home = Path.of("/home/operator");
        assertEquals(List.of(home.resolve(".claude.json"), home.resolve(".claude/sessions")),
                block.writablePaths(home));
    }
}
