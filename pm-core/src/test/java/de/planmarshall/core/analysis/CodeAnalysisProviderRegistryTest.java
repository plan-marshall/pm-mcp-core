/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.analysis;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.findings.Finding;
import de.planmarshall.core.findings.FindingScope;

/**
 * The registry of code analysis providers (PM-IMPL-1): members passed to its constructor, found by their id.
 */
@EnableGeneratorController
@DisplayName("Code analysis provider registry")
class CodeAnalysisProviderRegistryTest {

    private static final String PROVIDER = "p" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
    private static final String OTHER = "p" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
    private static final String THIRD = "p" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);

    /** A provider that only names itself; its operations are never called by the registry. */
    private record NamedProvider(String id) implements CodeAnalysisProvider {

        @Override
        public AnalysisCallResult<AnalysisResult> status(FindingScope target) {
            return AnalysisCallResult.status(AnalysisOutcome.PROVIDER_UNAVAILABLE, null);
        }

        @Override
        public AnalysisCallResult<List<Finding>> issues(FindingScope target) {
            return AnalysisCallResult.issues(AnalysisOutcome.PROVIDER_UNAVAILABLE, null, null);
        }
    }

    @Test
    @DisplayName("a provider registered under its id is found")
    void found() {
        var provider = new NamedProvider(PROVIDER);
        var registry = new CodeAnalysisProviderRegistry(List.of(provider, new NamedProvider(OTHER)));

        assertSame(provider, registry.find(PROVIDER).orElseThrow());
    }

    @Test
    @DisplayName("an unregistered id answers empty")
    void unregistered() {
        var registry = new CodeAnalysisProviderRegistry(List.of(new NamedProvider(PROVIDER)));

        assertAll(
                () -> assertEquals(Optional.empty(), registry.find(OTHER)),
                () -> assertEquals(Optional.empty(), registry.find(null)),
                () -> assertEquals(Optional.empty(), new CodeAnalysisProviderRegistry(List.of()).find(PROVIDER)));
    }

    @Test
    @DisplayName("ids lists the registered ids in the order the providers were passed, and cannot be changed")
    void ids() {
        var registry = new CodeAnalysisProviderRegistry(
                List.of(new NamedProvider(PROVIDER), new NamedProvider(OTHER), new NamedProvider(THIRD)));
        var ids = registry.ids();

        assertAll(
                () -> assertEquals(List.of(PROVIDER, OTHER, THIRD), new ArrayList<>(ids)),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> ids.add(Generators.letterStrings(3, 10).next())));
    }

    @Test
    @DisplayName("the registry keeps its members when the passed collection changes")
    void immutable() {
        var providers = new ArrayList<CodeAnalysisProvider>(List.of(new NamedProvider(PROVIDER)));
        var registry = new CodeAnalysisProviderRegistry(providers);
        providers.add(new NamedProvider(OTHER));

        assertEquals(Optional.empty(), registry.find(OTHER));
    }

    @Test
    @DisplayName("a second provider under the same id is refused")
    void duplicate() {
        var providers = List.of(new NamedProvider(PROVIDER), new NamedProvider(PROVIDER));

        assertThrows(IllegalArgumentException.class, () -> new CodeAnalysisProviderRegistry(providers));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"", "so", "Sonar", "-sonar", "sonar-", "son_ar", "sonar.cloud"})
    @DisplayName("an id outside the provider id pattern is refused")
    void invalidId(String id) {
        var providers = List.of(new NamedProvider(id));

        assertThrows(IllegalArgumentException.class, () -> new CodeAnalysisProviderRegistry(providers));
    }

    @Test
    @DisplayName("a missing collection, provider or id is refused")
    void missing() {
        var withNullProvider = Arrays.asList(new NamedProvider("sonar"), null);
        var withNullId = List.of(new NamedProvider(null));

        assertAll(
                () -> assertThrows(NullPointerException.class, () -> new CodeAnalysisProviderRegistry(null)),
                () -> assertThrows(NullPointerException.class,
                        () -> new CodeAnalysisProviderRegistry(withNullProvider)),
                () -> assertThrows(NullPointerException.class, () -> new CodeAnalysisProviderRegistry(withNullId)));
    }
}
