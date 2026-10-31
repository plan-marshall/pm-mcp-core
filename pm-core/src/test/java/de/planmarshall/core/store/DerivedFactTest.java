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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jspecify.annotations.Nullable;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.role.corpus.JsonValue;
import de.planmarshall.core.role.corpus.JsonValue.JsonString;

/**
 * One entry of the derived facts store and the document that holds it (Store Schemas § Derived Facts Store; PM-EXT-6):
 * the state shapes a value and a failure, the digest and the failure-reason bounds, and the invariant that a key's
 * kind is its entry's kind.
 */
@EnableGeneratorController
@DisplayName("Derived fact")
class DerivedFactTest {

    private static String digest() {
        var text = new StringBuilder(64);
        for (var index = 0; index < 64; index++) {
            text.append(Character.forDigit(Generators.integers(0, 15).next(), 16));
        }
        return text.toString();
    }

    private static Instant instant() {
        return Instant.ofEpochSecond(Generators.longs(1_600_000_000L, 1_900_000_000L).next());
    }

    private static String derivation() {
        return "derive" + Generators.integers(1, 1_000).next();
    }

    private static JsonValue value() {
        return new JsonString(Generators.letterStrings(1, 20).next());
    }

    private static DerivedFact.Failure failure() {
        return new DerivedFact.Failure(Generators.letterStrings(1, 100).next(), instant());
    }

    private static DerivedFact fact(DerivedFact.State state, @Nullable JsonValue value,
            DerivedFact.@Nullable Failure failure) {
        return DerivedFact.builder().kind(DerivedFactKind.MODULE_DISCOVERY).value(value)
                .source(new DerivedFact.Source(derivation(), DerivedFact.DerivationClass.LOCAL))
                .inputDigest(digest()).derivedAt(instant()).state(state).failure(failure).build();
    }

    private static DerivedFact factWithDigest(String digest) {
        return DerivedFact.builder().kind(DerivedFactKind.MODULE_DISCOVERY).value(value())
                .source(new DerivedFact.Source(derivation(), DerivedFact.DerivationClass.LOCAL))
                .inputDigest(digest).derivedAt(instant()).state(DerivedFact.State.CURRENT).build();
    }

    @Test
    @DisplayName("a current fact carries a value and no failure")
    void currentFactCarriesValueAndNoFailure() {
        var value = value();

        var fact = fact(DerivedFact.State.CURRENT, value, null);

        assertAll(
                () -> assertEquals(Optional.of(value), Optional.ofNullable(fact.value()),
                        "The current fact carries its value"),
                () -> assertEquals(Optional.empty(), Optional.ofNullable(fact.failure()),
                        "The current fact carries no failure"));
    }

    @Test
    @DisplayName("a current fact without a value or with a failure is refused")
    void currentFactOutsideItsShapeIsRefused() {
        var someValue = value();
        var someFailure = failure();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> fact(DerivedFact.State.CURRENT, null, null),
                        "A current fact without a value is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> fact(DerivedFact.State.CURRENT, someValue, someFailure),
                        "A current fact with a failure is refused"));
    }

    @Test
    @DisplayName("a stale fact carries its last value and a failure")
    void staleFactCarriesValueAndFailure() {
        var fact = fact(DerivedFact.State.STALE, value(), failure());

        assertAll(
                () -> assertNotNull(fact.value(), "The stale fact keeps its last value"),
                () -> assertNotNull(fact.failure(), "The stale fact carries its failure"));
    }

    @Test
    @DisplayName("a stale fact without a value or without a failure is refused")
    void staleFactOutsideItsShapeIsRefused() {
        var someValue = value();
        var someFailure = failure();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> fact(DerivedFact.State.STALE, null, someFailure),
                        "A stale fact without a value is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> fact(DerivedFact.State.STALE, someValue, null),
                        "A stale fact without a failure is refused"));
    }

    @Test
    @DisplayName("a failed fact carries no value and a failure")
    void failedFactCarriesNoValueAndFailure() {
        var fact = fact(DerivedFact.State.FAILED, null, failure());

        assertAll(
                () -> assertEquals(Optional.empty(), Optional.ofNullable(fact.value()),
                        "The failed fact carries no value"),
                () -> assertNotNull(fact.failure(), "The failed fact carries its failure"));
    }

    @Test
    @DisplayName("a failed fact with a value or without a failure is refused")
    void failedFactOutsideItsShapeIsRefused() {
        var someValue = value();
        var someFailure = failure();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> fact(DerivedFact.State.FAILED, someValue, someFailure),
                        "A failed fact with a value is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> fact(DerivedFact.State.FAILED, null, null),
                        "A failed fact without a failure is refused"));
    }

    @Test
    @DisplayName("an input digest outside the grammar is refused")
    void digestOutsideGrammarIsRefused() {
        var letters = Generators.letterStrings(64, 64).next();
        var shortDigest = digest().substring(1);
        var upperCase = digest().toUpperCase(Locale.ROOT);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> factWithDigest(letters),
                        "A digest of letters is refused"),
                () -> assertThrows(IllegalArgumentException.class, () -> factWithDigest(shortDigest),
                        "A digest of 63 digits is refused"),
                () -> assertThrows(IllegalArgumentException.class, () -> factWithDigest(upperCase),
                        "An upper-case digest is refused"));
    }

    @Test
    @DisplayName("a failure reason of 500 code points is accepted, 501 is refused")
    void failureReasonBoundIsEnforced() {
        var at = instant();
        var accepted = "r".repeat(DerivedFact.Failure.MAX_REASON);

        assertAll(
                () -> assertDoesNotThrow(() -> new DerivedFact.Failure(accepted, at),
                        "A reason of 500 code points is accepted"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new DerivedFact.Failure(accepted + "r", at),
                        "A reason of 501 code points is refused"),
                () -> assertDoesNotThrow(() -> new DerivedFact.Failure("😀".repeat(DerivedFact.Failure.MAX_REASON), at),
                        "The bound counts code points, not characters"));
    }

    @Test
    @DisplayName("a derivation identifier outside its grammar is refused")
    void derivationOutsideGrammarIsRefused() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new DerivedFact.Source("Derive", DerivedFact.DerivationClass.LOCAL),
                        "An upper-case derivation is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new DerivedFact.Source("derive x", DerivedFact.DerivationClass.LOCAL),
                        "A derivation with a space is refused"));
    }

    @Test
    @DisplayName("the document refuses a key whose kind is not its entry's kind")
    void documentRefusesAKeyOfAnotherKind() {
        var key = new DerivedFactKey(DerivedFactKind.COST_CALIBRATION, null);
        var entry = fact(DerivedFact.State.CURRENT, value(), null);
        var entries = Map.of(key, entry);

        assertThrows(IllegalArgumentException.class, () -> new DerivedFacts(entries),
                "A key of another kind than its entry is refused");
    }

    @Test
    @DisplayName("the document keeps the order of its entries and answers a fact by key")
    void documentKeepsOrderAndAnswersFacts() {
        var first = new DerivedFactKey(DerivedFactKind.MODULE_DISCOVERY, null);
        var second = new DerivedFactKey(DerivedFactKind.ACTIVE_PROFILES, null);
        var entries = new LinkedHashMap<DerivedFactKey, DerivedFact>();
        entries.put(first, fact(DerivedFact.State.CURRENT, value(), null));
        entries.put(second, DerivedFact.builder().kind(DerivedFactKind.ACTIVE_PROFILES).value(value())
                .source(new DerivedFact.Source(derivation(), DerivedFact.DerivationClass.LOCAL))
                .inputDigest(digest()).derivedAt(instant()).state(DerivedFact.State.CURRENT).build());

        var document = new DerivedFacts(entries);

        assertAll(
                () -> assertEquals(List.of(first, second), List.copyOf(document.facts().keySet()),
                        "The document keeps the order of its entries"),
                () -> assertTrue(document.fact(first).isPresent(), "The document answers a known key"),
                () -> assertEquals(Optional.empty(),
                        document.fact(new DerivedFactKey(DerivedFactKind.CI_PROVIDER, null)),
                        "The document answers no unknown key"),
                () -> assertTrue(DerivedFacts.empty().facts().isEmpty(), "The empty document holds no fact"));
    }
}
