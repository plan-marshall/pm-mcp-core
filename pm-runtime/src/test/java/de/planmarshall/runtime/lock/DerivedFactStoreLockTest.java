/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.lock;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
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
import de.planmarshall.core.store.AtomicStoreFile;
import de.planmarshall.core.store.DerivedFact;
import de.planmarshall.core.store.DerivedFactDerivation.Derivation;
import de.planmarshall.core.store.DerivedFactDerivation.DerivationOutcome.Derived;
import de.planmarshall.core.store.DerivedFactKey;
import de.planmarshall.core.store.DerivedFactKind;
import de.planmarshall.core.store.DerivedFactKindSpec;
import de.planmarshall.core.store.DerivedFactKinds;
import de.planmarshall.core.store.DerivedFactReading;
import de.planmarshall.core.store.DerivedFactStore;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.LockOrder;

/**
 * The derived facts store under the real {@link FileLockManager} (PM-IMPL-7; the guard of PM-WATCH-STORE-43): a
 * transaction that holds a plan lock may refresh a fact, which is permitted, and two threads refreshing two keys at
 * once lose no update.
 * <p>
 * The manager asserts the order within one transaction only, and {@code refresh} opens its own transaction; the place
 * of the derived-facts lock in the total order is therefore asserted explicitly here and by the pm-core
 * {@code LockOrderTest}.
 */
@EnableGeneratorController
@Timeout(value = 30, unit = TimeUnit.SECONDS)
@DisplayName("Derived fact store lock")
class DerivedFactStoreLockTest {

    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
    private static final String PROJECT = "pm-mcp-core";
    private static final String DIGEST = "a".repeat(64);
    private static final String SCHEMA = "{\"type\":\"object\",\"required\":[\"name\"],"
            + "\"additionalProperties\":false,\"properties\":{\"name\":{\"type\":\"string\"}}}";
    private static final DerivedFactKey FIRST = new DerivedFactKey(DerivedFactKind.MODULE_DISCOVERY, null);
    private static final DerivedFactKey SECOND = new DerivedFactKey(DerivedFactKind.PUSH_REMOTE, null);

    @TempDir
    Path base;

    private FileLockManager manager;
    private DerivedFactStore store;

    @BeforeEach
    void createStore() throws IOException {
        manager = new FileLockManager(new RecordingAuditSink());
        var directory = base.resolve("projects").resolve(PROJECT);
        Files.createDirectories(directory);
        var kinds = new DerivedFactKinds();
        kinds.register(DerivedFactKind.MODULE_DISCOVERY, spec());
        kinds.register(DerivedFactKind.PUSH_REMOTE, spec());
        store = new DerivedFactStore(
                new AtomicStoreFile(directory.resolve("derived-facts.json"), LockKey.derivedFacts(base, PROJECT)),
                manager, kinds);
    }

    private static DerivedFactKindSpec spec() {
        return DerivedFactKindSpec.builder()
                .schema(JsonValue.parse(SCHEMA.getBytes(UTF_8)))
                .classes(Set.of(DerivedFact.DerivationClass.LOCAL))
                .qualifierAllowed(false)
                .inputs("the fixture inputs of the kind")
                .build();
    }

    private static Derivation valueOf(String name) {
        // "first" and "second" identify the entries in the assertions; they are the fixture's data.
        var value = JsonValue.parse(("{\"name\":\"" + name + "\"}").getBytes(UTF_8));
        return () -> new Derived(value, "fixture", DerivedFact.DerivationClass.LOCAL);
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

    @Test
    @DisplayName("a transaction holding a plan lock may refresh a fact")
    void planLockThenRefresh() {
        var plan = LockKey.plan(base, PROJECT, "plan-one");
        var derivedFacts = LockKey.derivedFacts(base, PROJECT);
        try (var transaction = manager.openTransaction()) {
            transaction.acquire(plan);

            var reading = store.refresh(FIRST, DIGEST, valueOf("first"), NOW);

            assertAll(
                    () -> assertInstanceOf(DerivedFactReading.Value.class, reading,
                            "A held plan lock permits the refresh"),
                    () -> assertInstanceOf(LockOrder.Permitted.class, LockOrder.check(List.of(plan), derivedFacts),
                            "The derived-facts lock follows the plan lock"));
        }
    }

    @Test
    @DisplayName("two threads refreshing two keys at once lose no update")
    void twoKeysAtOnce() throws Exception {
        runConcurrently(
                () -> store.refresh(FIRST, DIGEST, valueOf("first"), NOW),
                () -> store.refresh(SECOND, DIGEST, valueOf("second"), NOW));

        var facts = store.snapshot().facts();
        assertAll(
                () -> assertEquals(2, facts.size(), "Both entries are stored"),
                () -> assertTrue(facts.containsKey(FIRST), "The first entry is stored"),
                () -> assertTrue(facts.containsKey(SECOND), "The second entry is stored"));
    }
}
