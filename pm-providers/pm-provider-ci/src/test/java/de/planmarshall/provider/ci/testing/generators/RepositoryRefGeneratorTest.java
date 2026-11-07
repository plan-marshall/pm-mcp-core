/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.ci.testing.generators;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.Map;

import de.planmarshall.provider.ci.RepositoryRef;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

@DisplayName("RepositoryRef generator")
@EnableGeneratorController
class RepositoryRefGeneratorTest {

    /** The built-in provider table: provider id to web origin (contract: provider ids and forge origins). */
    private static final Map<String, String> FORGES = Map.of(
            "github", "https://github.com",
            "gitlab", "https://gitlab.com",
            "forgejo", "https://codeberg.org");

    private final RepositoryRefGenerator generator = new RepositoryRefGenerator();

    @RepeatedTest(20)
    @DisplayName("Should yield references the constructor accepts")
    void shouldYieldReferencesTheConstructorAccepts() {
        var generated = generator.next();

        var rebuilt = new RepositoryRef(generated.forge(), generated.origin(), generated.ownerOrPath(),
                generated.name());

        assertAll("generated reference",
                () -> assertNotNull(generated, "The generator yields a reference"),
                () -> assertEquals(generated, rebuilt, "The constructor accepts the generated values"));
    }

    @RepeatedTest(20)
    @DisplayName("Should take forge and origin from the built-in table")
    void shouldTakeForgeAndOriginFromTheBuiltInTable() {
        var generated = generator.next();

        assertAll("forge and origin",
                () -> assertTrue(FORGES.containsKey(generated.forge()), "The forge is a built-in provider id"),
                () -> assertEquals(FORGES.get(generated.forge()), generated.origin(),
                        "The origin is the web origin of the forge"));
    }

    @RepeatedTest(20)
    @DisplayName("Should use a group path on GitLab only")
    void shouldUseAGroupPathOnGitLabOnly() {
        var generated = generator.next();

        assertAll("owner and name",
                () -> assertTrue("gitlab".equals(generated.forge()) || !generated.ownerOrPath().contains("/"),
                        "A non-GitLab owner is one plain segment"),
                () -> assertFalse(generated.name().contains("/"), "The repository name is one plain segment"));
    }

    @Test
    @DisplayName("Should report the generated type")
    void shouldReportTheGeneratedType() {
        assertEquals(RepositoryRef.class, generator.getType(), "The generator names the reference type");
    }

    @Test
    @DisplayName("Should be registered by the module factory")
    void shouldBeRegisteredByTheModuleFactory() {
        assertEquals(RepositoryRef.class, CiGenerators.repositoryRefs().getType(),
                "The factory yields the reference generator");
    }
}
