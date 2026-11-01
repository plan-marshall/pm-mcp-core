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
import static org.junit.jupiter.api.Assertions.assertNull;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.role.corpus.JsonValue;
import de.planmarshall.core.store.DerivedFactDerivation.Derivation;
import de.planmarshall.core.store.DerivedFactDerivation.DerivationOutcome.Derived;

/**
 * The recompute rule of {@link DerivedFactStore#refresh} (Store Schemas § Derived Facts Store; Timeouts &amp; Budgets §
 * Derived Facts): one case per cell of the decision table, the two validity boundaries of a network entry, a local
 * entry that never expires by time, and a value that violates its registered schema.
 */
@EnableGeneratorController
@DisplayName("Derived fact refresh")
class DerivedFactRefreshTest {

    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
    private static final String PROJECT = "pm-mcp-core";
    private static final String DIGEST_A = "a".repeat(64);
    private static final String DIGEST_B = "b".repeat(64);
    private static final String SCHEMA = "{\"type\":\"object\",\"required\":[\"name\"],"
            + "\"additionalProperties\":false,\"properties\":{\"name\":{\"type\":\"string\"}}}";
    private static final DerivedFactKey LOCAL_KEY = new DerivedFactKey(DerivedFactKind.MODULE_DISCOVERY, null);
    private static final DerivedFactKey NETWORK_KEY = new DerivedFactKey(DerivedFactKind.MERGE_METHODS_ALLOWED, null);
    private static final JsonValue VALUE = JsonValue.parse("{\"name\":\"tree\"}".getBytes(UTF_8));

    @TempDir
    Path base;

    private Path file;
    private DerivedFactStore store;
    private final AtomicInteger calls = new AtomicInteger();

    @BeforeEach
    void createStore() {
        file = base.resolve("derived-facts.json");
        var kinds = new DerivedFactKinds();
        kinds.register(DerivedFactKind.MODULE_DISCOVERY, spec(DerivedFact.DerivationClass.LOCAL));
        kinds.register(DerivedFactKind.MERGE_METHODS_ALLOWED, spec(DerivedFact.DerivationClass.NETWORK));
        store = new DerivedFactStore(new AtomicStoreFile(file, LockKey.derivedFacts(base, PROJECT)),
                new RecordingLockManager(), kinds);
    }

    private static DerivedFactKindSpec spec(DerivedFact.DerivationClass derivationClass) {
        return DerivedFactKindSpec.builder()
                .schema(JsonValue.parse(SCHEMA.getBytes(UTF_8)))
                .classes(Set.of(derivationClass))
                .qualifierAllowed(false)
                .inputs("the fixture inputs of the kind")
                .build();
    }

    private Derivation succeeds(DerivedFact.DerivationClass derivationClass) {
        return () -> {
            calls.incrementAndGet();
            return new Derived(VALUE, "fixture", derivationClass);
        };
    }

    private DerivedFact seed(DerivedFactKind kind, DerivedFact.DerivationClass derivationClass, DerivedFact.State state,
            String digest, Instant derivedAt, String value) throws IOException {
        var fact = DerivedFact.builder()
                .kind(kind)
                .value(state == DerivedFact.State.FAILED ? null : JsonValue.parse(value.getBytes(UTF_8)))
                .source(new DerivedFact.Source("fixture", derivationClass))
                .inputDigest(digest)
                .derivedAt(derivedAt)
                .state(state)
                .failure(state == DerivedFact.State.CURRENT ? null
                        : new DerivedFact.Failure("the previous attempt failed", derivedAt))
                .build();
        var facts = new LinkedHashMap<DerivedFactKey, DerivedFact>();
        facts.put(new DerivedFactKey(kind, null), fact);
        Files.write(file, DerivedFactsCodec.write(new DerivedFacts(facts)));
        return fact;
    }

    @Nested
    @DisplayName("the decision table")
    class DecisionTable {

        @Test
        @DisplayName("an absent entry is derived")
        void absentIsDerived() {
            var reading = store.refresh(LOCAL_KEY, DIGEST_A, succeeds(DerivedFact.DerivationClass.LOCAL), NOW);

            assertAll(
                    () -> assertEquals(1, calls.get(), "The derivation is called once"),
                    () -> assertInstanceOf(DerivedFactReading.Value.class, reading, "A value is written"),
                    () -> assertEquals(DIGEST_A, store.fact(LOCAL_KEY).orElseThrow().inputDigest(),
                            "The attempted digest is stored"));
        }

        @Test
        @DisplayName("a local entry of another digest is derived")
        void localDigestMismatchIsDerived() throws IOException {
            seed(DerivedFactKind.MODULE_DISCOVERY, DerivedFact.DerivationClass.LOCAL, DerivedFact.State.CURRENT,
                    DIGEST_A, NOW.minusSeconds(60), "{\"name\":\"old\"}");

            var reading = store.refresh(LOCAL_KEY, DIGEST_B, succeeds(DerivedFact.DerivationClass.LOCAL), NOW);

            assertAll(
                    () -> assertEquals(1, calls.get(), "The derivation is called once"),
                    () -> assertInstanceOf(DerivedFactReading.Value.class, reading, "A value is written"),
                    () -> assertEquals(DIGEST_B, store.fact(LOCAL_KEY).orElseThrow().inputDigest(),
                            "The new digest is stored"));
        }

        @Test
        @DisplayName("a network entry of another digest is derived")
        void networkDigestMismatchIsDerived() throws IOException {
            seed(DerivedFactKind.MERGE_METHODS_ALLOWED, DerivedFact.DerivationClass.NETWORK,
                    DerivedFact.State.CURRENT, DIGEST_A, NOW.minusSeconds(60), "{\"name\":\"old\"}");

            store.refresh(NETWORK_KEY, DIGEST_B, succeeds(DerivedFact.DerivationClass.NETWORK), NOW);

            assertEquals(1, calls.get(), "The derivation is called once");
        }

        @Test
        @DisplayName("a current entry of the same digest is returned without deriving")
        void cacheHitDoesNotDerive() throws IOException {
            var stored = seed(DerivedFactKind.MODULE_DISCOVERY, DerivedFact.DerivationClass.LOCAL,
                    DerivedFact.State.CURRENT, DIGEST_A, NOW.minusSeconds(60), "{\"name\":\"old\"}");

            var reading = store.refresh(LOCAL_KEY, DIGEST_A, succeeds(DerivedFact.DerivationClass.LOCAL), NOW);

            assertAll(
                    () -> assertEquals(0, calls.get(), "The derivation is not called"),
                    () -> assertEquals(new DerivedFactReading.Value(stored), reading, "The stored entry is returned"));
        }

        @Test
        @DisplayName("a stale entry is derived even when its digest matches")
        void staleIsDerived() throws IOException {
            seed(DerivedFactKind.MODULE_DISCOVERY, DerivedFact.DerivationClass.LOCAL, DerivedFact.State.STALE,
                    DIGEST_A, NOW.minusSeconds(60), "{\"name\":\"old\"}");

            store.refresh(LOCAL_KEY, DIGEST_A, succeeds(DerivedFact.DerivationClass.LOCAL), NOW);

            assertEquals(1, calls.get(), "A stale entry is retried by the next call");
        }

        @Test
        @DisplayName("a failed entry is derived even when its digest matches")
        void failedIsDerived() throws IOException {
            seed(DerivedFactKind.MODULE_DISCOVERY, DerivedFact.DerivationClass.LOCAL, DerivedFact.State.FAILED,
                    DIGEST_A, NOW.minusSeconds(60), "{\"name\":\"old\"}");

            store.refresh(LOCAL_KEY, DIGEST_A, succeeds(DerivedFact.DerivationClass.LOCAL), NOW);

            assertEquals(1, calls.get(), "A failed entry is retried by the next call");
        }
    }

    @Nested
    @DisplayName("the network validity period")
    class ValidityPeriod {

        @Test
        @DisplayName("a network entry one second before its period is returned")
        void beforeThePeriodDoesNotDerive() throws IOException {
            seed(DerivedFactKind.MERGE_METHODS_ALLOWED, DerivedFact.DerivationClass.NETWORK,
                    DerivedFact.State.CURRENT, DIGEST_A, NOW, "{\"name\":\"merge\"}");

            var reading = store.refresh(NETWORK_KEY, DIGEST_A, succeeds(DerivedFact.DerivationClass.NETWORK),
                    NOW.plusSeconds(86_399));

            assertAll(
                    () -> assertEquals(0, calls.get(), "The derivation is not called"),
                    () -> assertInstanceOf(DerivedFactReading.Value.class, reading, "The stored entry is current"));
        }

        @Test
        @DisplayName("a network entry at exactly its period is derived")
        void atThePeriodDerives() throws IOException {
            seed(DerivedFactKind.MERGE_METHODS_ALLOWED, DerivedFact.DerivationClass.NETWORK,
                    DerivedFact.State.CURRENT, DIGEST_A, NOW, "{\"name\":\"merge\"}");

            store.refresh(NETWORK_KEY, DIGEST_A, succeeds(DerivedFact.DerivationClass.NETWORK),
                    NOW.plusSeconds(86_400));

            assertEquals(1, calls.get(), "At derived_at plus 86400 s the fact is refreshed");
        }

        @Test
        @DisplayName("a network entry one second after its period is derived")
        void afterThePeriodDerives() throws IOException {
            seed(DerivedFactKind.MERGE_METHODS_ALLOWED, DerivedFact.DerivationClass.NETWORK,
                    DerivedFact.State.CURRENT, DIGEST_A, NOW, "{\"name\":\"merge\"}");

            store.refresh(NETWORK_KEY, DIGEST_A, succeeds(DerivedFact.DerivationClass.NETWORK),
                    NOW.plusSeconds(86_401));

            assertEquals(1, calls.get(), "After the period the fact is refreshed");
        }

        @Test
        @DisplayName("a local entry never expires by time")
        void localNeverExpiresByTime() throws IOException {
            seed(DerivedFactKind.MODULE_DISCOVERY, DerivedFact.DerivationClass.LOCAL, DerivedFact.State.CURRENT,
                    DIGEST_A, NOW.minusSeconds(10_000_000), "{\"name\":\"old\"}");

            store.refresh(LOCAL_KEY, DIGEST_A, succeeds(DerivedFact.DerivationClass.LOCAL), NOW);

            assertEquals(0, calls.get(), "A local entry is decided by its digest alone");
        }

        @Test
        @DisplayName("the validity period is the fixed constant of the specification")
        void validityPeriodIsTheConstant() {
            assertEquals(Duration.ofSeconds(86_400), DerivedFactStore.NETWORK_VALIDITY,
                    "The period is fixed at 86400 seconds");
        }
    }

    @Nested
    @DisplayName("the validation of a derived value")
    class Validation {

        @Test
        @DisplayName("a value that violates its registered schema is a failure, not current")
        void schemaViolationIsAFailure() {
            var invalid = JsonValue.parse("{\"count\":1}".getBytes(UTF_8));

            var reading = store.refresh(LOCAL_KEY, DIGEST_A,
                    () -> new Derived(invalid, "fixture", DerivedFact.DerivationClass.LOCAL), NOW);

            assertAll(
                    () -> assertInstanceOf(DerivedFactReading.Indeterminate.class, reading,
                            "An invalid value is indeterminate"),
                    () -> assertEquals(DerivedFact.State.FAILED, store.fact(LOCAL_KEY).orElseThrow().state(),
                            "The entry is failed, never current"),
                    () -> assertNull(store.fact(LOCAL_KEY).orElseThrow().value(),
                            "A failed entry carries no value"));
        }

        @Test
        @DisplayName("a kind that was never registered is a failure")
        void unregisteredKindIsAFailure() {
            var key = new DerivedFactKey(DerivedFactKind.COST_CALIBRATION, null);

            var reading = store.refresh(key, DIGEST_A,
                    () -> new Derived(invalidValue(), "fixture", DerivedFact.DerivationClass.LOCAL), NOW);

            assertInstanceOf(DerivedFactReading.Indeterminate.class, reading,
                    "An unregistered kind is indeterminate");
        }

        @Test
        @DisplayName("a qualifier on a kind that admits none is a failure")
        void qualifierOnAnUnqualifiedKindIsAFailure() {
            var key = new DerivedFactKey(DerivedFactKind.MODULE_DISCOVERY, "java");

            var reading = store.refresh(key, DIGEST_A,
                    () -> new Derived(VALUE, "fixture", DerivedFact.DerivationClass.LOCAL), NOW);

            assertInstanceOf(DerivedFactReading.Indeterminate.class, reading,
                    "A refused qualifier is indeterminate");
        }

        @Test
        @DisplayName("a source class the kind does not admit is a failure")
        void refusedSourceClassIsAFailure() {
            var reading = store.refresh(LOCAL_KEY, DIGEST_A,
                    () -> new Derived(VALUE, "fixture", DerivedFact.DerivationClass.NETWORK), NOW);

            assertInstanceOf(DerivedFactReading.Indeterminate.class, reading,
                    "A refused source class is indeterminate");
        }

        private static JsonValue invalidValue() {
            return JsonValue.parse("{\"any\":\"value\"}".getBytes(UTF_8));
        }
    }
}
