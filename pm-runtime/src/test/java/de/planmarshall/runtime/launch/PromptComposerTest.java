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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.role.RoleSkill;

/** The launch prompt's order, shared prefix, writing skill and fail-closed composition. */
@DisplayName("Prompt composer")
class PromptComposerTest {

    private final SkillSource skills = LaunchFixtures.skills();

    @Test
    @DisplayName("two launches of one role agree byte for byte up to the end of the role skill")
    void sharedPrefix() {
        var first = new PromptComposer().compose(request(false, List.of("skill://pm/state/outline/SKILL.md"),
                envelope("task-1")));
        var second = new PromptComposer().compose(request(false, List.of(), envelope("task-2")));
        assertEquals(first.sharedPrefix(), second.sharedPrefix());
        assertFalse(first.sharedPrefix().contains("task-1"));
    }

    @Test
    @DisplayName("the shared prefix starts with the shim, then core, task protocol and role skill")
    void prefixOrder() {
        var prompt = new PromptComposer().compose(request(false, List.of(), null));
        var prefix = prompt.sharedPrefix();
        assertTrue(prefix.startsWith(LaunchFixtures.SHIM));
        assertTrue(prefix.indexOf(PromptComposer.CORE) < prefix.indexOf(PromptComposer.TASK_PROTOCOL));
        assertTrue(prefix.indexOf(PromptComposer.TASK_PROTOCOL) < prefix.indexOf(LaunchFixtures.ROLE_SKILL));
    }

    @Test
    @DisplayName("a task with free text receives the writing skill, one without does not")
    void writingSkillFromFreeText() {
        var with = new PromptComposer().compose(request(true, List.of(), null));
        var without = new PromptComposer().compose(request(false, List.of(), null));
        assertTrue(with.text().contains("## " + PromptComposer.WRITING));
        assertFalse(without.text().contains("## " + PromptComposer.WRITING));
    }

    @Test
    @DisplayName("a role artifact that names the writing skill does not add it without free text")
    void roleSkillDoesNotAddWritingSkill() {
        var roleSkills = List.of(roleSkill(PromptComposer.WRITING), roleSkill(LaunchFixtures.ROLE_SKILL));
        var prompt = new PromptComposer().compose(new PromptComposer.PromptRequest(LaunchFixtures.SHIM, false, "",
                skills, roleSkills, true, false, List.of(), List.of(), null, 0));
        assertFalse(prompt.text().contains("## " + PromptComposer.WRITING));
        assertTrue(prompt.text().contains("## " + LaunchFixtures.ROLE_SKILL));
    }

    @Test
    @DisplayName("a zero_tool worker receives the server-fixed prompt of its role only")
    void zeroToolPrompt() {
        var prompt = new PromptComposer().compose(new PromptComposer.PromptRequest(LaunchFixtures.SHIM, true,
                "Fixed screener prompt", skills, List.of(), true, false, List.of(), List.of(), null, 0));
        assertEquals("Fixed screener prompt", prompt.text());
        assertTrue(prompt.delivered().isEmpty());
    }

    @Test
    @DisplayName("a required skill that does not resolve fails the composition")
    void missingSkill() {
        var composer = new PromptComposer();
        assertThrows(PromptComposer.CompositionException.class, () -> composer.compose(request(false, List.of(
                "skill://pm/missing/SKILL.md"), null)));
    }

    @Test
    @DisplayName("above the in-band bound the remaining task skills are an index")
    void skillIndex() {
        var prompt = new PromptComposer().compose(new PromptComposer.PromptRequest(LaunchFixtures.SHIM, false, "",
                skills, List.of(roleSkill(LaunchFixtures.ROLE_SKILL)), true, false, List.of(
                        "skill://pm/state/outline/SKILL.md"), List.of("skill://pm/ext/domain-java/SKILL.md"), null, 5));
        assertTrue(prompt.text().contains("## Skill index"));
        assertTrue(prompt.text().contains("skill://pm/state/outline/SKILL.md"));
    }

    private PromptComposer.PromptRequest request(boolean freeText, List<String> stateSkills, ContractEnvelope envelope) {
        return new PromptComposer.PromptRequest(LaunchFixtures.SHIM, false, "", skills,
                List.of(roleSkill(LaunchFixtures.ROLE_SKILL)), true, freeText, stateSkills, List.of(), envelope, 0);
    }

    private static RoleSkill roleSkill(String uri) {
        return new RoleSkill(uri, LaunchFixtures.skills().resolve(uri).orElseThrow().digest());
    }

    private static ContractEnvelope envelope(String entity) {
        return ContractEnvelope.of("plan", "29-worker-launch", entity, List.of(new LinkSchema("submit:task", List.of(
                new LinkParameter("summary", "text", null)))), schema("{\"type\":\"object\"}"));
    }

    private static JsonNode schema(String document) {
        return JsonDocument.parse(document.getBytes(StandardCharsets.UTF_8), "schema");
    }
}
