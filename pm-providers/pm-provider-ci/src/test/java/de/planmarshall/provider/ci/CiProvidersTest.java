/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.ci;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The registry of the CI providers. The owner, repository name and number of the resolved URL are generated. What
 * stays literal is contract and each test says so: the provider ids {@code github}, {@code gitlab} and
 * {@code forgejo} (the ids the registry is addressed by), the id {@code Not-An-Id} outside the provider id pattern,
 * and the forge origin {@code https://github.com} with its issue path.
 */
@EnableGeneratorController
@DisplayName("CiProviders")
class CiProvidersTest {

    private static CiProvider provider(String id) {
        CiProvider provider = createMock(CiProvider.class);
        expect(provider.id()).andReturn(id).anyTimes();
        replay(provider);
        return provider;
    }

    @Test
    @DisplayName("finds a registered provider by its id (the provider ids are the contract)")
    void findsById() {
        var github = provider("github");
        var gitlab = provider("gitlab");

        var registry = new CiProviders(List.of(github, gitlab));

        assertAll("the registry lookup",
                () -> assertTrue(registry.find("github").isPresent(), "a registered id is found"),
                () -> assertEquals(gitlab, registry.find("gitlab").orElseThrow(), "the provider is returned"),
                () -> assertTrue(registry.find("forgejo").isEmpty(), "an unknown id is empty"));
    }

    @Test
    @DisplayName("keeps the registration order of the ids (the provider ids are the contract)")
    void keepsOrder() {
        var registry = new CiProviders(List.of(provider("gitlab"), provider("github")));

        assertEquals(List.of("gitlab", "github"), List.copyOf(registry.ids()), "the ids keep their order");
    }

    @Test
    @DisplayName("selects the provider of a resolved URL's forge (the github.com issue path is the grammar)")
    void selectsByUrl() {
        var github = provider("github");
        var registry = new CiProviders(List.of(github));
        var url = ForgeUrl.resolve("https://github.com/%s/%s/issues/%d".formatted(
                Generators.letterStrings(1, 20).next(), Generators.letterStrings(1, 20).next(),
                Generators.integers(1, 99_999).next())).url().orElseThrow();

        assertEquals(github, registry.select(url).orElseThrow(), "the URL's forge selects the provider");
    }

    @Test
    @DisplayName("refuses a duplicate id and an id outside the pattern (the ids are the id grammar)")
    void refusesInvalidRegistrations() {
        var github = provider("github");
        List<CiProvider> duplicate = List.of(github, provider("github"));
        List<CiProvider> invalid = List.of(provider("Not-An-Id"));
        var withNull = Collections.<CiProvider>singletonList(null);
        assertAll("the refused registrations",
                () -> assertThrows(IllegalArgumentException.class, () -> new CiProviders(duplicate),
                        "a duplicate id is refused"),
                () -> assertThrows(IllegalArgumentException.class, () -> new CiProviders(invalid),
                        "an id outside the pattern is refused"),
                () -> assertThrows(NullPointerException.class, () -> new CiProviders(withNull),
                        "a null provider is refused"),
                () -> assertThrows(NullPointerException.class, () -> new CiProviders(null),
                        "a null collection is refused"));
    }
}
