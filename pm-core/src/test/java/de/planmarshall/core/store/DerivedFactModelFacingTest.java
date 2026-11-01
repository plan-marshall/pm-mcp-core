/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.role.corpus.JsonValue;

/**
 * The operator-only projection of the derived facts store (Store Schemas § Derived Facts Store, bullet
 * <em>Operator-Only Facts</em>; PM-EXT-6): {@code agent_identity}, qualified or not, appears in no model-facing view,
 * whether its kind was registered or not.
 */
@EnableGeneratorController
@DisplayName("Derived fact model-facing projection")
class DerivedFactModelFacingTest {

    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
    private static final String PROJECT = "pm-mcp-core";
    private static final DerivedFactKey IDENTITY = new DerivedFactKey(DerivedFactKind.AGENT_IDENTITY, null);
    private static final DerivedFactKey QUALIFIED_IDENTITY = new DerivedFactKey(DerivedFactKind.AGENT_IDENTITY, "app");
    private static final DerivedFactKey PUSH_REMOTE = new DerivedFactKey(DerivedFactKind.PUSH_REMOTE, null);

    /** A schema-valid payload; the member value is generated, the member name is the fixture schema. */
    private static final String PAYLOAD = "{\"name\":\"" + Generators.letterStrings(1, 20).next() + "\"}";

    @TempDir
    Path base;

    private DerivedFactKinds kinds;
    private DerivedFactStore store;

    @BeforeEach
    void createStore() throws IOException {
        var file = base.resolve("derived-facts.json");
        kinds = new DerivedFactKinds();
        store = new DerivedFactStore(new AtomicStoreFile(file, LockKey.derivedFacts(base, PROJECT)),
                new RecordingLockManager(), kinds);
        var facts = new LinkedHashMap<DerivedFactKey, DerivedFact>();
        facts.put(IDENTITY, fact(DerivedFactKind.AGENT_IDENTITY, DerivedFact.DerivationClass.NETWORK));
        facts.put(QUALIFIED_IDENTITY, fact(DerivedFactKind.AGENT_IDENTITY, DerivedFact.DerivationClass.NETWORK));
        facts.put(PUSH_REMOTE, fact(DerivedFactKind.PUSH_REMOTE, DerivedFact.DerivationClass.LOCAL));
        Files.write(file, DerivedFactsCodec.write(new DerivedFacts(facts)));
    }

    private static DerivedFact fact(DerivedFactKind kind, DerivedFact.DerivationClass derivationClass) {
        return DerivedFact.builder()
                .kind(kind)
                .value(JsonValue.parse(PAYLOAD.getBytes(UTF_8)))
                .source(new DerivedFact.Source("fixture", derivationClass))
                .inputDigest("a".repeat(64))
                .derivedAt(NOW)
                .state(DerivedFact.State.CURRENT)
                .build();
    }

    @Test
    @DisplayName("the model-facing projection holds only the non-operator keys")
    void modelFacingExcludesAgentIdentity() {
        var modelFacing = store.modelFacing();

        assertAll(
                () -> assertEquals(Set.of(PUSH_REMOTE), modelFacing.facts().keySet(),
                        "Only push_remote is model-facing"),
                () -> assertFalse(modelFacing.facts().containsKey(IDENTITY), "agent_identity is excluded"),
                () -> assertFalse(modelFacing.facts().containsKey(QUALIFIED_IDENTITY),
                        "A qualified agent_identity is excluded"));
    }

    @Test
    @DisplayName("the operator view holds the whole set")
    void operatorFactsHoldEverything() {
        assertEquals(Set.of(IDENTITY, QUALIFIED_IDENTITY, PUSH_REMOTE), store.operatorFacts().facts().keySet(),
                "The operator view holds all three entries");
    }

    @Test
    @DisplayName("both projections are projections of the snapshot")
    void projectionsOfTheSnapshot() {
        var snapshot = store.snapshot();

        assertAll(
                () -> assertEquals(snapshot.facts().values().stream().collect(Collectors.toSet()),
                        store.operatorFacts().facts().values().stream().collect(Collectors.toSet()),
                        "The operator view is the snapshot"),
                () -> assertTrue(snapshot.facts().values().containsAll(store.modelFacing().facts().values()),
                        "The model-facing view is a subset of the snapshot"));
    }

    @Test
    @DisplayName("the operator-only set derived from the enum at run time is agent_identity alone")
    void theOperatorOnlySetComesFromTheEnum() {
        var expected = Arrays.stream(DerivedFactKind.values())
                .filter(DerivedFactKind::operatorOnly)
                .collect(Collectors.toSet());

        assertEquals(Set.of(DerivedFactKind.AGENT_IDENTITY), expected,
                "The enum declares exactly one operator-only kind");
    }

    @Test
    @DisplayName("a registry that never registered agent_identity still excludes it")
    void exclusionDoesNotDependOnRegistration() {
        assertAll(
                () -> assertTrue(kinds.spec(DerivedFactKind.AGENT_IDENTITY).isEmpty(),
                        "No specification is registered for agent_identity"),
                () -> assertTrue(store.modelFacing().fact(IDENTITY).isEmpty(),
                        "The unregistered operator-only fact is still excluded"));
    }
}
