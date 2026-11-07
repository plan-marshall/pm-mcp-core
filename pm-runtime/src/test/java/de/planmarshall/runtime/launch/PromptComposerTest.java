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

import static de.planmarshall.runtime.testing.generators.RuntimeGenerators.planIds;
import static de.planmarshall.runtime.testing.generators.RuntimeGenerators.shortWords;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.role.RoleSkill;

/**
 * The launch prompt's order, shared prefix, writing skill and fail-closed composition.
 *
 * <p>
 * The task entities, the plan id, the link and parameter names, the zero-tool prompt and the unresolvable skill URI
 * are generated. The literals that stay are the contract: the prompt template's section heading {@code ## } and
 * {@code ## Skill index}, the fixture skill URIs, the parameter type {@code text}, the envelope kind {@code plan} and
 * the in-band bound 5 the index is tested at.
 */
@EnableGeneratorController
@DisplayName("Prompt composer")
class PromptComposerTest {

    private static final String OUTLINE_SKILL = "skill://pm/state/outline/SKILL.md";

    private final SkillSource skills = LaunchFixtures.skills();

    @Test
    @DisplayName("two launches of one role agree byte for byte up to the end of the role skill")
    void sharedPrefix() {
        var planId = planIds().next();
        var firstEntity = entity();
        var first = new PromptComposer().compose(request(false, List.of(OUTLINE_SKILL), envelope(planId,
                firstEntity)));
        var second = new PromptComposer().compose(request(false, List.of(), envelope(planId, entity())));
        assertEquals(first.sharedPrefix(), second.sharedPrefix(), "two launches of a role share their prefix");
        assertFalse(first.sharedPrefix().contains(firstEntity), "the shared prefix never names the task");
    }

    @Test
    @DisplayName("the shared prefix starts with the shim, then core, task protocol and role skill")
    void prefixOrder() {
        var prompt = new PromptComposer().compose(request(false, List.of(), null));
        var prefix = prompt.sharedPrefix();
        assertTrue(prefix.startsWith(LaunchFixtures.SHIM), "the shared prefix starts with the shim");
        assertTrue(prefix.indexOf(PromptComposer.CORE) < prefix.indexOf(PromptComposer.TASK_PROTOCOL),
                "the core skill precedes the task protocol");
        assertTrue(prefix.indexOf(PromptComposer.TASK_PROTOCOL) < prefix.indexOf(LaunchFixtures.ROLE_SKILL),
                "the task protocol precedes the role skill");
    }

    @Test
    @DisplayName("a task with free text receives the writing skill, one without does not")
    void writingSkillFromFreeText() {
        var with = new PromptComposer().compose(request(true, List.of(), null));
        var without = new PromptComposer().compose(request(false, List.of(), null));
        assertTrue(with.text().contains("## " + PromptComposer.WRITING), "free text brings the writing skill");
        assertFalse(without.text().contains("## " + PromptComposer.WRITING),
                "no free text brings no writing skill");
    }

    @Test
    @DisplayName("a role artifact that names the writing skill does not add it without free text")
    void roleSkillDoesNotAddWritingSkill() {
        var roleSkills = List.of(roleSkill(PromptComposer.WRITING), roleSkill(LaunchFixtures.ROLE_SKILL));
        var prompt = new PromptComposer().compose(new PromptComposer.PromptRequest(LaunchFixtures.SHIM, false, "",
                skills, roleSkills, true, false, List.of(), List.of(), null, 0));
        assertFalse(prompt.text().contains("## " + PromptComposer.WRITING),
                "the role cannot add the writing skill alone");
        assertTrue(prompt.text().contains("## " + LaunchFixtures.ROLE_SKILL), "the role skill is still delivered");
    }

    @Test
    @DisplayName("a zero_tool worker receives the server-fixed prompt of its role only")
    void zeroToolPrompt() {
        var fixed = Generators.letterStrings(2, 10).next() + " " + Generators.letterStrings(2, 10).next() + ".";
        var prompt = new PromptComposer().compose(new PromptComposer.PromptRequest(LaunchFixtures.SHIM, true,
                fixed, skills, List.of(), true, false, List.of(), List.of(), null, 0));
        assertEquals(fixed, prompt.text(), "the zero-tool prompt is the server-fixed one");
        assertTrue(prompt.delivered().isEmpty(), "a zero-tool worker is delivered no skill");
    }

    @Test
    @DisplayName("a required skill that does not resolve fails the composition")
    void missingSkill() {
        var composer = new PromptComposer();
        var missing = "skill://pm/missing-" + shortWords().next() + "/SKILL.md";
        assertThrows(PromptComposer.CompositionException.class, () -> composer.compose(request(false, List.of(
                missing), null)), "an unresolvable skill fails the composition");
    }

    @Test
    @DisplayName("a zero_tool worker without its fixed prompt fails the composition")
    void zeroToolWithoutPrompt() {
        var composer = new PromptComposer();
        assertThrows(PromptComposer.CompositionException.class, () -> composer.compose(
                new PromptComposer.PromptRequest(LaunchFixtures.SHIM, true, "", skills, List.of(), true, false,
                        List.of(), List.of(), null, 0)), "a zero-tool worker needs its fixed prompt");
    }

    @Test
    @DisplayName("above the in-band bound the remaining task skills are an index")
    void skillIndex() {
        var prompt = new PromptComposer().compose(new PromptComposer.PromptRequest(LaunchFixtures.SHIM, false, "",
                skills, List.of(roleSkill(LaunchFixtures.ROLE_SKILL)), true, false, List.of(OUTLINE_SKILL),
                List.of("skill://pm/ext/domain-java/SKILL.md"), null, 5));
        assertTrue(prompt.text().contains("## Skill index"), "above the bound the prompt has a skill index");
        assertTrue(prompt.text().contains(OUTLINE_SKILL), "the index names the remaining task skill");
    }

    private PromptComposer.PromptRequest request(boolean freeText, List<String> stateSkills, ContractEnvelope envelope) {
        return new PromptComposer.PromptRequest(LaunchFixtures.SHIM, false, "", skills,
                List.of(roleSkill(LaunchFixtures.ROLE_SKILL)), true, freeText, stateSkills, List.of(), envelope, 0);
    }

    private static RoleSkill roleSkill(String uri) {
        return new RoleSkill(uri, LaunchFixtures.skills().resolve(uri).orElseThrow().digest());
    }

    private static ContractEnvelope envelope(String planId, String entity) {
        return ContractEnvelope.of("plan", planId, entity, List.of(new LinkSchema(shortWords().next() + ":"
                + shortWords().next(), List.of(new LinkParameter(shortWords().next(), "text", null)))),
                schema("{\"type\":\"object\"}"));
    }

    private static String entity() {
        return "task-" + Generators.letterStrings(8, 16).next();
    }

    private static JsonNode schema(String document) {
        return JsonDocument.parse(document.getBytes(StandardCharsets.UTF_8), "schema");
    }
}
