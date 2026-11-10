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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * Proves that the builder of {@link LaunchPrompt} builds the value its canonical constructor builds, including its null
 * refusal for the prompt text. Every value is generated; the delivered skills are drawn from the generator artifact of
 * {@code pm-runtime} and the prefix lies inside the generated text.
 */
@EnableGeneratorController
@DisplayName("LaunchPrompt builder")
class LaunchPromptRecordsTest {

    @Test
    @DisplayName("the builder builds the launch prompt the constructor builds")
    void launchPromptBuilderEqualsConstructor() {
        var text = Generators.letterStrings(10, 50).next();
        var digest = RuntimeGenerators.sha256Digests().next();
        var delivered = List.of(RuntimeGenerators.resolvedSkills().next());
        var prefixLength = Generators.integers(0, text.length()).next();

        var constructed = new LaunchPrompt(text, digest, delivered, prefixLength);
        var built = LaunchPrompt.builder()
                .text(text)
                .digest(digest)
                .delivered(delivered)
                .prefixLength(prefixLength)
                .build();

        assertEquals(constructed, built, "the builder builds the same launch prompt");
    }

    @Test
    @DisplayName("the builder refuses a missing text like the constructor")
    void launchPromptBuilderRefusesMissingText() {
        var refusal = assertThrows(NullPointerException.class, () -> LaunchPrompt.builder().build());

        assertEquals("text", refusal.getMessage(), "the refusal names the prompt text");
    }
}
