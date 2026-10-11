/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.findings;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.findings.FindingsContractTest.FixtureResponder;
import de.planmarshall.core.findings.FindingsContractTest.FixtureSource;

/**
 * The registry of finding sources and responders, keyed by the store's source vocabulary (PM-IMPL-1).
 */
@DisplayName("Finding registry")
class FindingRegistryTest {

    private static final FindingSourceKey ANALYSIS = FindingSourceKey.analysis("provider-a");
    private static final FindingSourceKey REVIEW = FindingSourceKey.of(FindingSourceKind.GITLAB_MR);

    private static FixtureSource source(FindingSourceKey key) {
        return new FixtureSource(key, Map.of());
    }

    private static FixtureResponder responder(FindingSourceKey key) {
        return new FixtureResponder(key, new ArrayList<>());
    }

    @Nested
    @DisplayName("lookup")
    class Lookup {

        @Test
        @DisplayName("a source and a responder registered under their key are found by it")
        void registeredFound() {
            var analysisSource = source(ANALYSIS);
            var reviewSource = source(REVIEW);
            var analysisResponder = responder(ANALYSIS);

            var registry = new FindingRegistry(List.of(analysisSource, reviewSource), List.of(analysisResponder));

            assertAll(
                    () -> assertEquals(Optional.of(analysisSource), registry.source(ANALYSIS)),
                    () -> assertEquals(Optional.of(reviewSource), registry.source(REVIEW)),
                    () -> assertEquals(Optional.of(analysisResponder), registry.responder(ANALYSIS)));
        }

        @Test
        @DisplayName("an unregistered key answers empty")
        void unregisteredEmpty() {
            var registry = new FindingRegistry(List.of(source(ANALYSIS)), List.of(responder(ANALYSIS)));

            assertAll(
                    () -> assertTrue(registry.source(REVIEW).isEmpty()),
                    () -> assertTrue(registry.responder(REVIEW).isEmpty()),
                    () -> assertTrue(registry.source(FindingSourceKey.of(FindingSourceKind.QGATE)).isEmpty()));
        }

        @Test
        @DisplayName("an analysis key of another provider id answers empty")
        void otherProviderEmpty() {
            var registry = new FindingRegistry(List.of(source(ANALYSIS)), List.of(responder(ANALYSIS)));
            var other = FindingSourceKey.analysis("provider-b");

            assertAll(
                    () -> assertTrue(registry.source(other).isEmpty()),
                    () -> assertTrue(registry.responder(other).isEmpty()));
        }

        @Test
        @DisplayName("an empty registry answers empty for every key")
        void emptyRegistry() {
            var registry = new FindingRegistry(List.of(), List.of());

            assertTrue(Arrays.stream(FindingSourceKind.values())
                    .filter(kind -> kind != FindingSourceKind.ANALYSIS)
                    .map(FindingSourceKey::of)
                    .allMatch(key -> registry.source(key).isEmpty() && registry.responder(key).isEmpty()));
        }

        @Test
        @DisplayName("the members are fixed at construction, a later change of the passed list is not seen")
        void immutable() {
            var sources = new ArrayList<FindingSource>(List.of(source(ANALYSIS)));
            var registry = new FindingRegistry(sources, List.of());

            sources.add(source(REVIEW));

            assertTrue(registry.source(REVIEW).isEmpty());
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @Test
        @DisplayName("a second source under the same key is refused")
        void duplicateSource() {
            var sources = List.of(source(ANALYSIS), source(ANALYSIS));
            List<FindingResponder> responders = List.of();

            assertThrows(IllegalArgumentException.class, () -> new FindingRegistry(sources, responders));
        }

        @Test
        @DisplayName("a second responder under the same key is refused")
        void duplicateResponder() {
            List<FindingSource> sources = List.of();
            var responders = List.of(responder(REVIEW), responder(REVIEW));

            assertThrows(IllegalArgumentException.class, () -> new FindingRegistry(sources, responders));
        }

        @Test
        @DisplayName("a source and a responder may share a key")
        void sourceAndResponderShareKey() {
            var registry = new FindingRegistry(List.of(source(REVIEW)), List.of(responder(REVIEW)));

            assertAll(
                    () -> assertTrue(registry.source(REVIEW).isPresent()),
                    () -> assertTrue(registry.responder(REVIEW).isPresent()));
        }

        @Test
        @DisplayName("a missing collection or a member without a key is refused")
        void missingMembers() {
            List<FindingSource> noSources = List.of();
            List<FindingResponder> noResponders = List.of();
            var keyless = List.of(source(null));

            assertAll(
                    () -> assertThrows(NullPointerException.class, () -> new FindingRegistry(null, noResponders)),
                    () -> assertThrows(NullPointerException.class, () -> new FindingRegistry(noSources, null)),
                    () -> assertThrows(NullPointerException.class, () -> new FindingRegistry(keyless, noResponders)));
        }
    }
}
