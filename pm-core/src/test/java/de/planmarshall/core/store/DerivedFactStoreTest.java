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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jspecify.annotations.Nullable;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.role.corpus.JsonValue;

/**
 * The store and its reads (Store Schemas § Derived Facts Store; PM-EXT-6, PM-IMPL-2): the empty store, each variant of
 * {@link DerivedFactReading} with the instant passed in, and the refusals of an unreadable file, another format
 * version and a foreign lock level.
 */
@EnableGeneratorController
@DisplayName("Derived fact store")
class DerivedFactStoreTest {

    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
    private static final String PROJECT = "pm-mcp-core";
    private static final String SCHEMA = "{\"type\":\"object\",\"required\":[\"name\"],"
            + "\"additionalProperties\":false,\"properties\":{\"name\":{\"type\":\"string\"}}}";
    private static final DerivedFactKey LOCAL_KEY = new DerivedFactKey(DerivedFactKind.MODULE_DISCOVERY, null);
    private static final DerivedFactKey NETWORK_KEY = new DerivedFactKey(DerivedFactKind.MERGE_METHODS_ALLOWED, null);

    /** A schema-valid payload; the member value is generated, the member name is the fixture schema. */
    private static final String VALUE = "{\"name\":\"" + Generators.letterStrings(1, 20).next() + "\"}";

    @TempDir
    Path base;

    private Path file;
    private DerivedFactKinds kinds;
    private DerivedFactStore store;

    @BeforeEach
    void createStore() {
        file = base.resolve("derived-facts.json");
        kinds = new DerivedFactKinds();
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

    private static DerivedFact entry(DerivedFactKind kind, String value, DerivedFact.DerivationClass derivationClass,
            Instant derivedAt, String digest, DerivedFact.State state, DerivedFact.@Nullable Failure failure) {
        return DerivedFact.builder()
                .kind(kind)
                .value(JsonValue.parse(value.getBytes(UTF_8)))
                .source(new DerivedFact.Source("fixture", derivationClass))
                .inputDigest(digest)
                .derivedAt(derivedAt)
                .state(state)
                .failure(failure)
                .build();
    }

    private void write(DerivedFactKey key, DerivedFact entry) throws IOException {
        var facts = new LinkedHashMap<DerivedFactKey, DerivedFact>();
        facts.put(key, entry);
        Files.write(file, DerivedFactsCodec.write(new DerivedFacts(facts)));
    }

    @Nested
    @DisplayName("the empty store")
    class EmptyStore {

        @Test
        @DisplayName("reads as empty and as an absent fact")
        void readsAbsent() {
            assertAll(
                    () -> assertTrue(store.snapshot().facts().isEmpty(), "No entry is stored yet"),
                    () -> assertTrue(store.fact(LOCAL_KEY).isEmpty(), "The fact entry is absent"),
                    () -> assertInstanceOf(DerivedFactReading.Absent.class, store.reading(LOCAL_KEY, NOW),
                            "The reading is absent"),
                    () -> assertTrue(store.reading(LOCAL_KEY, NOW).couldNotEvaluate().isPresent(),
                            "An absent fact cannot be evaluated"));
        }
    }

    @Nested
    @DisplayName("the readings")
    class Readings {

        @Test
        @DisplayName("a current local fact inside its validity is a value")
        void currentLocalIsValue() throws Exception {
            var fact = entry(DerivedFactKind.MODULE_DISCOVERY, VALUE,
                    DerivedFact.DerivationClass.LOCAL, NOW.minusSeconds(3600), "a".repeat(64),
                    DerivedFact.State.CURRENT, null);
            write(LOCAL_KEY, fact);

            var reading = store.reading(LOCAL_KEY, NOW);

            assertAll(
                    () -> assertEquals(new DerivedFactReading.Value(fact), reading, "The reading carries the entry"),
                    () -> assertTrue(reading.couldNotEvaluate().isEmpty(), "A current fact can be evaluated"));
        }

        @Test
        @DisplayName("a local fact never expires by time")
        void localNeverExpires() throws Exception {
            var fact = entry(DerivedFactKind.MODULE_DISCOVERY, VALUE,
                    DerivedFact.DerivationClass.LOCAL, NOW.minusSeconds(10_000_000), "a".repeat(64),
                    DerivedFact.State.CURRENT, null);
            write(LOCAL_KEY, fact);

            assertInstanceOf(DerivedFactReading.Value.class, store.reading(LOCAL_KEY, NOW),
                    "A local fact does not expire");
        }

        @Test
        @DisplayName("a stale entry is indeterminate with its kind and stored reason")
        void staleIsIndeterminate() throws Exception {
            var failure = new DerivedFact.Failure("the provider refused", NOW.minusSeconds(60));
            write(NETWORK_KEY, entry(DerivedFactKind.MERGE_METHODS_ALLOWED, VALUE,
                    DerivedFact.DerivationClass.NETWORK, NOW.minusSeconds(120), "a".repeat(64),
                    DerivedFact.State.STALE, failure));

            var reading = store.reading(NETWORK_KEY, NOW);

            var indeterminate = assertInstanceOf(DerivedFactReading.Indeterminate.class, reading,
                    "A stale entry is indeterminate");
            assertAll(
                    () -> assertEquals("merge_methods_allowed: the provider refused", indeterminate.reason(),
                            "The reason names the kind and the stored failure"),
                    () -> assertEquals(indeterminate.reason(), reading.couldNotEvaluate().orElseThrow(),
                            "The reason is the could-not-evaluate reason"));
        }

        @Test
        @DisplayName("a failed entry is indeterminate with its kind and stored reason")
        void failedIsIndeterminate() throws Exception {
            write(LOCAL_KEY, DerivedFact.builder()
                    .kind(DerivedFactKind.MODULE_DISCOVERY)
                    .source(new DerivedFact.Source("fixture", DerivedFact.DerivationClass.LOCAL))
                    .inputDigest("b".repeat(64))
                    .derivedAt(NOW.minusSeconds(60))
                    .state(DerivedFact.State.FAILED)
                    .failure(new DerivedFact.Failure("no descriptor tree", NOW.minusSeconds(60)))
                    .build());

            var indeterminate = assertInstanceOf(DerivedFactReading.Indeterminate.class,
                    store.reading(LOCAL_KEY, NOW), "A failed entry is indeterminate");

            assertEquals("module_discovery: no descriptor tree", indeterminate.reason(),
                    "The reason names the kind and the stored failure");
        }

        @Test
        @DisplayName("a current network fact expires exactly at derived_at plus 86400 s")
        void networkExpiresAtTheValidityPeriod() throws Exception {
            write(NETWORK_KEY, entry(DerivedFactKind.MERGE_METHODS_ALLOWED, VALUE,
                    DerivedFact.DerivationClass.NETWORK, NOW, "a".repeat(64), DerivedFact.State.CURRENT, null));

            assertAll(
                    () -> assertInstanceOf(DerivedFactReading.Value.class,
                            store.reading(NETWORK_KEY, NOW.plusSeconds(86_399)), "One second before is current"),
                    () -> assertInstanceOf(DerivedFactReading.Expired.class,
                            store.reading(NETWORK_KEY, NOW.plusSeconds(86_400)), "At the period it is expired"),
                    () -> assertInstanceOf(DerivedFactReading.Expired.class,
                            store.reading(NETWORK_KEY, NOW.plusSeconds(86_401)), "After the period it is expired"),
                    () -> assertTrue(store.reading(NETWORK_KEY, NOW.plusSeconds(86_400)).couldNotEvaluate()
                            .isPresent(), "An expired fact cannot be evaluated"));
        }
    }

    @Nested
    @DisplayName("the refusals")
    class Refusals {

        @Test
        @DisplayName("an unreadable store file is a refusal naming the file")
        void unreadableFileIsRefused() throws Exception {
            Files.write(file, "{".getBytes(UTF_8));

            var refusal = assertThrows(StoreRefusal.class, store::snapshot, "The content cannot be read");

            assertAll(
                    () -> assertEquals(file, refusal.file(), "The refusal names the store file"),
                    () -> assertEquals(StoreRefusal.Outcome.UNREADABLE, refusal.outcome(),
                            "The outcome is unreadable"));
        }

        @Test
        @DisplayName("another format version is a refusal naming the file")
        void otherFormatVersionIsRefused() throws Exception {
            Files.write(file, "{\"format_version\":2,\"facts\":{}}".getBytes(UTF_8));

            var refusal = assertThrows(StoreRefusal.class, store::snapshot, "The version is not accepted");

            assertAll(
                    () -> assertEquals(file, refusal.file(), "The refusal names the store file"),
                    () -> assertEquals(StoreRefusal.Outcome.FORMAT_NEWER, refusal.outcome(),
                            "The outcome names the newer version"));
        }

        @Test
        @DisplayName("a store file of another lock level is refused at construction")
        void foreignLockLevelIsRefused() {
            var timings = new AtomicStoreFile(file, LockKey.buildTimings(base, PROJECT));

            assertThrows(IllegalArgumentException.class,
                    () -> new DerivedFactStore(timings, new RecordingLockManager(), kinds),
                    "A build-timings lock is not a derived-facts lock");
        }
    }

    @Test
    @DisplayName("the store accessor answers the absolute store file")
    void storeAccessor() {
        assertEquals(file.toAbsolutePath(), store.store(), "The store path is absolute and normalized");
    }

    @Test
    @DisplayName("a stored entry is answered by the fact accessor")
    void factAccessor() throws Exception {
        var fact = entry(DerivedFactKind.MODULE_DISCOVERY, VALUE,
                DerivedFact.DerivationClass.LOCAL, NOW, "a".repeat(64), DerivedFact.State.CURRENT, null);
        write(LOCAL_KEY, fact);

        assertEquals(fact, store.fact(LOCAL_KEY).orElseThrow(), "The fact accessor answers the entry");
    }
}
