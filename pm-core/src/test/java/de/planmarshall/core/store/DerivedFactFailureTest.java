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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.role.corpus.JsonValue;
import de.planmarshall.core.store.DerivedFactDerivation.Derivation;
import de.planmarshall.core.store.DerivedFactDerivation.DerivationOutcome.Derived;
import de.planmarshall.core.store.DerivedFactDerivation.DerivationOutcome.Failed;

/**
 * The stale and failed operations of {@link DerivedFactStore#refresh} (Store Schemas § Derived Facts Store, bullet
 * <em>Failure</em>): one case per transition, the reason bound, the digest and value kept on {@code stale}, and an
 * unrelated entry left as it was.
 */
@EnableGeneratorController
@DisplayName("Derived fact failure")
class DerivedFactFailureTest {

    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
    private static final Instant LAST_GOOD = NOW.minusSeconds(3600);
    private static final String PROJECT = "pm-mcp-core";
    private static final String DIGEST_A = "a".repeat(64);
    private static final String DIGEST_B = "b".repeat(64);
    private static final String SCHEMA = "{\"type\":\"object\",\"required\":[\"name\"],"
            + "\"additionalProperties\":false,\"properties\":{\"name\":{\"type\":\"string\"}}}";
    private static final DerivedFactKey KEY = new DerivedFactKey(DerivedFactKind.MODULE_DISCOVERY, null);
    private static final DerivedFactKey OTHER = new DerivedFactKey(DerivedFactKind.PUSH_REMOTE, null);
    private static final JsonValue VALUE = JsonValue.parse("{\"name\":\"fresh\"}".getBytes(UTF_8));

    @TempDir
    Path base;

    private Path file;
    private DerivedFactStore store;

    @BeforeEach
    void createStore() {
        file = base.resolve("derived-facts.json");
        var kinds = new DerivedFactKinds();
        kinds.register(DerivedFactKind.MODULE_DISCOVERY, spec());
        kinds.register(DerivedFactKind.PUSH_REMOTE, spec());
        store = new DerivedFactStore(new AtomicStoreFile(file, LockKey.derivedFacts(base, PROJECT)),
                new RecordingLockManager(), kinds);
    }

    private static DerivedFactKindSpec spec() {
        return DerivedFactKindSpec.builder()
                .schema(JsonValue.parse(SCHEMA.getBytes(UTF_8)))
                .classes(Set.of(DerivedFact.DerivationClass.LOCAL))
                .qualifierAllowed(false)
                .inputs("the fixture inputs of the kind")
                .build();
    }

    private static Derivation succeeds() {
        return () -> new Derived(VALUE, "fixture", DerivedFact.DerivationClass.LOCAL);
    }

    private static Derivation fails(String reason) {
        return () -> new Failed(reason);
    }

    private void seed(DerivedFactKey key, DerivedFact.State state, String digest, String value, String reason)
            throws IOException {
        var fact = DerivedFact.builder()
                .kind(key.kind())
                .value(value == null ? null : JsonValue.parse(value.getBytes(UTF_8)))
                .source(new DerivedFact.Source("fixture", DerivedFact.DerivationClass.LOCAL))
                .inputDigest(digest)
                .derivedAt(LAST_GOOD)
                .state(state)
                .failure(state == DerivedFact.State.CURRENT ? null
                        : new DerivedFact.Failure(reason, LAST_GOOD))
                .build();
        var facts = new LinkedHashMap<DerivedFactKey, DerivedFact>();
        facts.put(key, fact);
        Files.write(file, DerivedFactsCodec.write(new DerivedFacts(facts)));
    }

    private void seedTwo() throws IOException {
        var target = DerivedFact.builder()
                .kind(DerivedFactKind.MODULE_DISCOVERY)
                .value(JsonValue.parse("{\"name\":\"old\"}".getBytes(UTF_8)))
                .source(new DerivedFact.Source("fixture", DerivedFact.DerivationClass.LOCAL))
                .inputDigest(DIGEST_A)
                .derivedAt(LAST_GOOD)
                .state(DerivedFact.State.CURRENT)
                .build();
        var other = DerivedFact.builder()
                .kind(DerivedFactKind.PUSH_REMOTE)
                .value(JsonValue.parse("{\"name\":\"origin\"}".getBytes(UTF_8)))
                .source(new DerivedFact.Source("fixture", DerivedFact.DerivationClass.LOCAL))
                .inputDigest(DIGEST_A)
                .derivedAt(LAST_GOOD)
                .state(DerivedFact.State.CURRENT)
                .build();
        var facts = new LinkedHashMap<DerivedFactKey, DerivedFact>();
        facts.put(KEY, target);
        facts.put(OTHER, other);
        Files.write(file, DerivedFactsCodec.write(new DerivedFacts(facts)));
    }

    @Nested
    @DisplayName("the transitions")
    class Transitions {

        @Test
        @DisplayName("a current entry becomes stale and keeps its last good state")
        void currentToStale() throws IOException {
            seed(KEY, DerivedFact.State.CURRENT, DIGEST_A, "{\"name\":\"old\"}", null);

            var reading = store.refresh(KEY, DIGEST_B, fails("boom"), NOW);

            var entry = store.fact(KEY).orElseThrow();
            assertAll(
                    () -> assertInstanceOf(DerivedFactReading.Indeterminate.class, reading,
                            "A stale entry is indeterminate"),
                    () -> assertEquals(DerivedFact.State.STALE, entry.state(), "The entry is stale"),
                    () -> assertEquals(JsonValue.parse("{\"name\":\"old\"}".getBytes(UTF_8)), entry.value(),
                            "The last good value is kept"),
                    () -> assertEquals(DIGEST_A, entry.inputDigest(), "The last good digest is kept"),
                    () -> assertEquals(LAST_GOOD, entry.derivedAt(), "The last good instant is kept"),
                    () -> assertEquals("boom", entry.failure().reason(), "The new failure is recorded"),
                    () -> assertEquals(NOW, entry.failure().at(), "The failure names the attempt"));
        }

        @Test
        @DisplayName("a stale entry stays stale with the new failure")
        void staleToStale() throws IOException {
            seed(KEY, DerivedFact.State.STALE, DIGEST_A, "{\"name\":\"old\"}", "first");

            store.refresh(KEY, DIGEST_B, fails("second"), NOW);

            var entry = store.fact(KEY).orElseThrow();
            assertAll(
                    () -> assertEquals(DerivedFact.State.STALE, entry.state(), "The entry stays stale"),
                    () -> assertEquals(DIGEST_A, entry.inputDigest(), "The last good digest is kept"),
                    () -> assertEquals("second", entry.failure().reason(), "The newest failure is recorded"));
        }

        @Test
        @DisplayName("a failed entry stays failed and records the attempted digest")
        void failedToFailed() throws IOException {
            seed(KEY, DerivedFact.State.FAILED, DIGEST_A, null, "first");

            store.refresh(KEY, DIGEST_B, fails("again"), NOW);

            var entry = store.fact(KEY).orElseThrow();
            assertAll(
                    () -> assertEquals(DerivedFact.State.FAILED, entry.state(), "The entry stays failed"),
                    () -> assertNull(entry.value(), "A failed entry carries no value"),
                    () -> assertEquals(DIGEST_B, entry.inputDigest(), "The attempted digest is stored"),
                    () -> assertEquals(NOW, entry.derivedAt(), "The attempt names the instant"),
                    () -> assertEquals("again", entry.failure().reason(), "The newest failure is recorded"));
        }

        @Test
        @DisplayName("a failed entry becomes current and drops the failure")
        void failedToCurrent() throws IOException {
            seed(KEY, DerivedFact.State.FAILED, DIGEST_A, null, "first");

            store.refresh(KEY, DIGEST_B, succeeds(), NOW);

            var entry = store.fact(KEY).orElseThrow();
            assertAll(
                    () -> assertEquals(DerivedFact.State.CURRENT, entry.state(), "The entry is current"),
                    () -> assertNull(entry.failure(), "A current entry carries no failure"),
                    () -> assertEquals(DIGEST_B, entry.inputDigest(), "The new digest is stored"));
        }

        @Test
        @DisplayName("a stale entry becomes current and drops the failure")
        void staleToCurrent() throws IOException {
            seed(KEY, DerivedFact.State.STALE, DIGEST_A, "{\"name\":\"old\"}", "first");

            store.refresh(KEY, DIGEST_B, succeeds(), NOW);

            var entry = store.fact(KEY).orElseThrow();
            assertAll(
                    () -> assertEquals(DerivedFact.State.CURRENT, entry.state(), "The entry is current"),
                    () -> assertNull(entry.failure(), "A current entry carries no failure"),
                    () -> assertEquals(VALUE, entry.value(), "The new value is stored"));
        }

        @Test
        @DisplayName("an absent entry becomes failed")
        void absentToFailed() {
            store.refresh(KEY, DIGEST_B, fails("never derived"), NOW);

            var entry = store.fact(KEY).orElseThrow();
            assertAll(
                    () -> assertEquals(DerivedFact.State.FAILED, entry.state(), "The entry is failed"),
                    () -> assertNull(entry.value(), "No value was ever derived"),
                    () -> assertEquals(DIGEST_B, entry.inputDigest(), "The attempted digest is stored"),
                    () -> assertEquals(NOW, entry.derivedAt(), "The attempt names the instant"),
                    () -> assertTrue(entry.failure().reason().contains("never derived"),
                            "The failure reason is the derivation's"));
        }
    }

    @Nested
    @DisplayName("the failure record")
    class FailureRecord {

        @Test
        @DisplayName("a reason of 600 code points is stored as 500")
        void reasonIsCutToTheBound() {
            var reason = "x".repeat(600);

            store.refresh(KEY, DIGEST_B, fails(reason), NOW);

            assertEquals(500, store.fact(KEY).orElseThrow().failure().reason()
                    .codePointCount(0, store.fact(KEY).orElseThrow().failure().reason().length()),
                    "The stored reason has the schema bound");
        }

        @Test
        @DisplayName("a network failure carries the missing permission text")
        void networkFailureCarriesThePermission() {
            store.refresh(KEY, DIGEST_B, fails("missing permission: checks:read"), NOW);

            assertEquals("missing permission: checks:read", store.fact(KEY).orElseThrow()
                    .failure().reason(), "The consumer's permission text is carried");
        }
    }

    @Test
    @DisplayName("a failed refresh changes no other entry")
    void otherEntryIsUnchanged() throws IOException {
        seedTwo();
        var before = DerivedFactsCodec.write(new DerivedFacts(
                Map.of(OTHER, store.fact(OTHER).orElseThrow())));

        store.refresh(KEY, DIGEST_B, fails("boom"), NOW);

        var after = DerivedFactsCodec.write(new DerivedFacts(
                Map.of(OTHER, store.fact(OTHER).orElseThrow())));
        assertArrayEquals(before, after, "The unrelated entry is byte-identical");
    }
}
