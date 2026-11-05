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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.role.corpus.JsonValue;
import de.planmarshall.core.store.DerivedFactDerivation.DerivationOutcome.Derived;

/**
 * One lock and no lost update on the derived facts store (PM-IMPL-7, PM-WATCH-STORE-43): a deterministic cross-key
 * case and two racing cases that are the smoke test, and the place of the derived-facts lock in the total order (a
 * pure {@link LockOrder#check} on a hand-built list, never a refresh).
 */
@EnableGeneratorController
@Timeout(value = 30, unit = TimeUnit.SECONDS)
@DisplayName("Derived fact store concurrency")
class DerivedFactStoreConcurrencyTest {

    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
    private static final String PROJECT = "project-" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
    private static final String PLAN = "plan-" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
    private static final String DERIVATION = Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
    private static final String FIRST_NAME = Generators.letterStrings(3, 10).next();
    private static final String SECOND_NAME = Generators.letterStrings(3, 10).next();
    private static final String DIGEST = "a".repeat(64);
    private static final String SCHEMA = "{\"type\":\"object\",\"required\":[\"name\"],"
            + "\"additionalProperties\":false,\"properties\":{\"name\":{\"type\":\"string\"}}}";
    private static final DerivedFactKey FIRST = new DerivedFactKey(DerivedFactKind.MODULE_DISCOVERY, null);
    private static final DerivedFactKey SECOND = new DerivedFactKey(DerivedFactKind.PUSH_REMOTE, null);

    @TempDir
    Path base;

    private DerivedFactStore store;

    @BeforeEach
    void createStore() {
        var kinds = new DerivedFactKinds();
        kinds.register(DerivedFactKind.MODULE_DISCOVERY, spec());
        kinds.register(DerivedFactKind.PUSH_REMOTE, spec());
        store = new DerivedFactStore(
                new AtomicStoreFile(base.resolve("derived-facts.json"), LockKey.derivedFacts(base, PROJECT)),
                new SerializingLockManager(), kinds);
    }

    private static DerivedFactKindSpec spec() {
        return DerivedFactKindSpec.builder()
                .schema(JsonValue.parse(SCHEMA.getBytes(UTF_8)))
                .classes(Set.of(DerivedFact.DerivationClass.LOCAL))
                .qualifierAllowed(false)
                .inputs("the fixture inputs of the kind")
                .build();
    }

    private static void runConcurrently(Callable<?> first, Callable<?> second) throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstRun = executor.submit(() -> {
                start.await();
                return first.call();
            });
            var secondRun = executor.submit(() -> {
                start.await();
                return second.call();
            });
            start.countDown();
            firstRun.get();
            secondRun.get();
        }
    }

    private static JsonValue payload(String name) {
        return JsonValue.parse(("{\"name\":\"" + name + "\"}").getBytes(UTF_8));
    }

    private static DerivedFactDerivation.Derivation valueOf(String name) {
        return () -> new Derived(payload(name), DERIVATION, DerivedFact.DerivationClass.LOCAL);
    }

    private void seed(DerivedFactKey key, Instant derivedAt) throws IOException {
        var entry = DerivedFact.builder()
                .kind(key.kind())
                .value(JsonValue.parse("{\"name\":\"newer\"}".getBytes(UTF_8)))
                .source(new DerivedFact.Source(DERIVATION, DerivedFact.DerivationClass.LOCAL))
                .inputDigest(DIGEST)
                .derivedAt(derivedAt)
                .state(DerivedFact.State.CURRENT)
                .build();
        Files.write(base.resolve("derived-facts.json"),
                DerivedFactsCodec.write(new DerivedFacts(Map.of(key, entry))));
    }

    @Test
    @DisplayName("smoke: two threads refreshing two different keys lose no entry")
    void twoDifferentKeys() throws Exception {
        runConcurrently(
                () -> store.refresh(FIRST, DIGEST, valueOf(FIRST_NAME), NOW.plusSeconds(1)),
                () -> store.refresh(SECOND, DIGEST, valueOf(SECOND_NAME), NOW));

        var facts = store.snapshot().facts();
        assertAll(
                () -> assertEquals(2, facts.size(), "Both entries are stored"),
                () -> assertTrue(facts.containsKey(FIRST), "The first entry is stored"),
                () -> assertTrue(facts.containsKey(SECOND), "The second entry is stored"));
    }

    @Test
    @DisplayName("smoke: two threads refreshing the same key lose no update")
    void twoRefreshersOfOneKey() throws Exception {
        // The two generated names identify the two results; the winner decides the expected instant.
        runConcurrently(
                () -> store.refresh(FIRST, DIGEST, valueOf(FIRST_NAME), NOW),
                () -> store.refresh(FIRST, DIGEST, valueOf(SECOND_NAME), NOW.plusSeconds(1)));

        var facts = store.snapshot().facts();
        var entry = facts.get(FIRST);
        var firstStored = payload(FIRST_NAME).equals(entry.value());
        var expectedDerivedAt = firstStored ? NOW : NOW.plusSeconds(1);
        assertAll(
                () -> assertEquals(1, facts.size(), "Exactly one entry is stored"),
                () -> assertEquals(DerivedFact.State.CURRENT, entry.state(), "The entry is current"),
                () -> assertTrue(firstStored || payload(SECOND_NAME).equals(entry.value()),
                        "The stored value is one of the two results"),
                () -> assertEquals(expectedDerivedAt, entry.derivedAt(),
                        "The entry names the instant of its own value"));
    }

    @Test
    @DisplayName("a nested refresh of a second key inside the first derivation keeps both entries")
    void nestedRefreshOfASecondKey() {
        var reading = store.refresh(FIRST, DIGEST, () -> {
            store.refresh(SECOND, DIGEST, valueOf(SECOND_NAME), NOW);
            return new Derived(payload(FIRST_NAME), DERIVATION, DerivedFact.DerivationClass.LOCAL);
        }, NOW.plusSeconds(1));

        var facts = store.snapshot().facts();
        assertAll(
                () -> assertEquals(2, facts.size(), "Both entries are stored"),
                () -> assertEquals(payload(FIRST_NAME), facts.get(FIRST).value(),
                        "The first entry carries the outer value"),
                () -> assertEquals(payload(SECOND_NAME), facts.get(SECOND).value(),
                        "The second entry carries the nested value"),
                () -> assertEquals(new DerivedFactReading.Value(facts.get(FIRST)), reading,
                        "The outer reading is its own stored entry"));
    }

    @Test
    @DisplayName("a refresh keeps a newer stored result of the same digest")
    void keepsANewerStoredResult() throws Exception {
        seed(FIRST, NOW.plusSeconds(10));
        var newer = store.fact(FIRST).orElseThrow();

        var reading = store.refresh(FIRST, DIGEST, valueOf(FIRST_NAME), NOW);

        assertAll(
                () -> assertEquals(new DerivedFactReading.Value(newer), reading,
                        "The newer stored entry is returned"),
                () -> assertEquals(newer, store.fact(FIRST).orElseThrow(),
                        "The newer stored entry is kept"));
    }

    @Test
    @DisplayName("a write keeps a newer stored result of the same digest")
    void updateKeepsANewerStoredResult() {
        var reading = store.refresh(FIRST, DIGEST, () -> {
            store.refresh(FIRST, DIGEST, valueOf(SECOND_NAME), NOW.plusSeconds(10));
            return new Derived(payload(FIRST_NAME), DERIVATION, DerivedFact.DerivationClass.LOCAL);
        }, NOW);

        var stored = store.fact(FIRST).orElseThrow();
        assertAll(
                () -> assertEquals(payload(SECOND_NAME), stored.value(), "The newer stored value is kept"),
                () -> assertEquals(NOW.plusSeconds(10), stored.derivedAt(), "The newer instant is kept"),
                () -> assertEquals(new DerivedFactReading.Value(stored), reading,
                        "The kept entry is the reading returned"));
    }

    @Test
    @DisplayName("the derived-facts lock is taken after a held plan and workspace key")
    void lockOrderAfterTheScopeLocks() {
        var workspace = LockKey.workspace(base, PROJECT);
        var plan = LockKey.plan(base, PROJECT, PLAN);
        var derivedFacts = LockKey.derivedFacts(base, PROJECT);

        assertInstanceOf(LockOrder.Permitted.class, LockOrder.check(List.of(workspace, plan), derivedFacts),
                "The derived-facts lock is taken after the scope locks");
    }

    @Test
    @DisplayName("a plan key requested while the derived-facts lock is held is refused")
    void planKeyAfterTheDerivedFactsLock() {
        var plan = LockKey.plan(base, PROJECT, PLAN);
        var derivedFacts = LockKey.derivedFacts(base, PROJECT);

        assertInstanceOf(LockOrder.Violation.class, LockOrder.check(Set.of(derivedFacts), plan),
                "No scope lock is taken after the derived-facts lock");
    }
}
