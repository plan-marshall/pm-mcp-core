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
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.testing.generators.CoreGenerators;

/**
 * Proves that the builder of {@link PromptComposer.PromptRequest} builds the value its canonical constructor builds,
 * including its null refusal for the shim paragraph. Every value is generated; the skill source is a mock the builder
 * only stores, the role skills are drawn from the generator artifact of {@code pm-core}, and the envelope is absent as
 * a warm worker carries none.
 */
@EnableGeneratorController
@DisplayName("PromptComposer.PromptRequest builder")
class PromptComposerRecordsTest {

    @Test
    @DisplayName("the builder builds the prompt request the constructor builds")
    void promptRequestBuilderEqualsConstructor() {
        var shimParagraph = Generators.letterStrings(1, 40).next();
        var zeroTool = Generators.booleans().next();
        var zeroToolPrompt = Generators.letterStrings(1, 40).next();
        SkillSource skills = createMock(SkillSource.class);
        var roleSkills = List.of(CoreGenerators.roleSkills().next());
        var fresh = Generators.booleans().next();
        var writesFreeText = Generators.booleans().next();
        var stateSkills = List.of(Generators.letterStrings(1, 40).next());
        var domainSkills = List.of(Generators.letterStrings(1, 40).next());
        var inBandBoundChars = Generators.integers(0, 10_000).next();

        var constructed = new PromptComposer.PromptRequest(shimParagraph, zeroTool, zeroToolPrompt, skills, roleSkills,
                fresh, writesFreeText, stateSkills, domainSkills, null, inBandBoundChars);
        var built = PromptComposer.PromptRequest.builder()
                .shimParagraph(shimParagraph)
                .zeroTool(zeroTool)
                .zeroToolPrompt(zeroToolPrompt)
                .skills(skills)
                .roleSkills(roleSkills)
                .fresh(fresh)
                .writesFreeText(writesFreeText)
                .stateSkills(stateSkills)
                .domainSkills(domainSkills)
                .envelope(null)
                .inBandBoundChars(inBandBoundChars)
                .build();

        assertEquals(constructed, built, "the builder builds the same prompt request");
    }

    @Test
    @DisplayName("the builder refuses a missing shim paragraph like the constructor")
    void promptRequestBuilderRefusesMissingShimParagraph() {
        var refusal = assertThrows(NullPointerException.class, () -> PromptComposer.PromptRequest.builder().build());

        assertEquals("shimParagraph", refusal.getMessage(), "the refusal names the shim paragraph");
    }
}
