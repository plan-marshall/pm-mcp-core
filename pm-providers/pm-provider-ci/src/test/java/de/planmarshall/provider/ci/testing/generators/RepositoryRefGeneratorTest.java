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
import java.net.URI;
import java.util.stream.Stream;

import de.planmarshall.provider.ci.ForgeUrl;
import de.planmarshall.provider.ci.RepositoryRef;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

@DisplayName("RepositoryRef generator")
@EnableGeneratorController
class RepositoryRefGeneratorTest {

    /** How many references are drawn to find a nested GitLab group path: a miss is below one in 10^50. */
    private static final int DRAWS = 500;

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
        var host = URI.create(generated.origin()).getHost();

        assertAll("forge and origin",
                () -> assertEquals(generated.forge(), ForgeUrl.BUILT_IN_HOSTS.get(host),
                        "The origin host names the forge in the table"),
                () -> assertEquals("https://" + host, generated.origin(),
                        "The origin is the https origin of the host"));
    }

    @Test
    @DisplayName("Should generate a nested group path on GitLab")
    void shouldGenerateANestedGroupPathOnGitLab() {
        var nested = Stream.generate(generator::next).limit(DRAWS)
                .anyMatch(ref -> "gitlab".equals(ref.forge()) && ref.ownerOrPath().contains("/"));

        assertTrue(nested, "A nested GitLab group path is generated");
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
