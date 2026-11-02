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
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.role.corpus.JsonValue;
import de.planmarshall.core.service.AuditEvent;
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

/**
 * The derived facts store under the real {@link FileLockManager} (PM-IMPL-7; the guard of PM-WATCH-STORE-43): a
 * transaction that holds a plan lock may refresh a fact, which is permitted, and two refreshes of one key overlap
 * without a deadlock, a lost entry or a lock-order violation.
 * <p>
 * The manager asserts the order within one transaction only, and {@code refresh} opens its own transaction, so no test
 * here claims to show the lock order; the place of the derived-facts lock in the total order is asserted by the pure
 * {@code LockOrder.check} of the pm-core {@code LockOrderTest}.
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
    private RecordingAuditSink auditSink;
    private DerivedFactStore store;

    @BeforeEach
    void createStore() throws IOException {
        auditSink = new RecordingAuditSink();
        manager = new FileLockManager(auditSink);
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

    private static JsonValue payload(String name) {
        return JsonValue.parse(("{\"name\":\"" + name + "\"}").getBytes(UTF_8));
    }

    private static Derivation valueOf(String name) {
        // "first" and "second" identify the entries in the assertions; they are the fixture's data.
        var value = payload(name);
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
    @DisplayName("a transaction holding a plan lock may refresh a fact (the refresh is permitted, not its order)")
    void planLockThenRefresh() {
        var plan = LockKey.plan(base, PROJECT, "plan-one");
        try (var transaction = manager.openTransaction()) {
            transaction.acquire(plan);

            var reading = store.refresh(FIRST, DIGEST, valueOf("first"), NOW);

            assertInstanceOf(DerivedFactReading.Value.class, reading, "A held plan lock permits the refresh");
        }
    }

    @Test
    @DisplayName("a refresh under a held plan lock overlaps one of the same key without deadlock or violation")
    void planLockAndConcurrentRefresh() throws Exception {
        var plan = LockKey.plan(base, PROJECT, "plan-one");
        var planAcquired = new CountDownLatch(1);
        var firstDerivation = new CountDownLatch(1);
        var secondDerivation = new CountDownLatch(1);
        var firstResult = new AtomicReference<DerivedFactReading>();
        var secondResult = new AtomicReference<DerivedFactReading>();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstRun = executor.submit(() -> {
                try (var transaction = manager.openTransaction()) {
                    transaction.acquire(plan);
                    planAcquired.countDown();
                    firstResult.set(store.refresh(FIRST, DIGEST, () -> {
                        firstDerivation.countDown();
                        await(secondDerivation);
                        return new Derived(payload("first"), "fixture", DerivedFact.DerivationClass.LOCAL);
                    }, NOW));
                }
                return null;
            });
            var secondRun = executor.submit(() -> {
                await(planAcquired);
                secondResult.set(store.refresh(FIRST, DIGEST, () -> {
                    secondDerivation.countDown();
                    await(firstDerivation);
                    return new Derived(payload("second"), "fixture", DerivedFact.DerivationClass.LOCAL);
                }, NOW.plusSeconds(1)));
                return null;
            });
            firstRun.get();
            secondRun.get();
        }

        var stored = store.fact(FIRST).orElseThrow();
        var expectedDerivedAt = payload("second").equals(stored.value()) ? NOW.plusSeconds(1) : NOW;
        var violations = auditSink.events().stream()
                .filter(event -> AuditEvent.LOCK_ORDER_VIOLATION.equals(event.event()))
                .toList();
        assertAll(
                () -> assertInstanceOf(DerivedFactReading.Value.class, firstResult.get(),
                        "The refresh under the plan lock returns a value"),
                () -> assertInstanceOf(DerivedFactReading.Value.class, secondResult.get(),
                        "The concurrent refresh returns a value"),
                () -> assertTrue(payload("first").equals(stored.value()) || payload("second").equals(stored.value()),
                        "The stored entry is one of the two results"),
                () -> assertEquals(expectedDerivedAt, stored.derivedAt(),
                        "The stored instant belongs to the stored value"),
                () -> assertTrue(violations.isEmpty(), "No lock order violation was recorded"));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("The latch wait was interrupted", e);
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
