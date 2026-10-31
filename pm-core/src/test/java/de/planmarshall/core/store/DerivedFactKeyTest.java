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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The key of a derived fact (Store Schemas § Derived Facts Store, {@code facts.propertyNames}; PM-EXT-6): the wire
 * form {@code <kind>} or {@code <kind>:<qualifier>}, its grammar, and the refusal of a kind part that names no kind.
 */
@EnableGeneratorController
@DisplayName("Derived fact key")
class DerivedFactKeyTest {

    private static String qualifier() {
        return Generators.letterStrings(1, 20).next();
    }

    @Test
    @DisplayName("a key without a qualifier is the kind alone and reads back")
    void keyWithoutQualifierRoundTrips() {
        var key = new DerivedFactKey(DerivedFactKind.MODULE_DISCOVERY, null);

        assertAll(
                () -> assertEquals("module_discovery", key.wireName(), "The wire form is the kind alone"),
                () -> assertEquals(Optional.of(key), DerivedFactKey.fromWireName(key.wireName()),
                        "The key reads back from its wire form"),
                () -> assertNull(key.qualifier(), "The key carries no qualifier"));
    }

    @Test
    @DisplayName("a key with a qualifier is kind:qualifier and reads back")
    void keyWithQualifierRoundTrips() {
        var qualifier = qualifier();
        var key = new DerivedFactKey(DerivedFactKind.DOMAIN_ACTIVATION, qualifier);

        assertAll(
                () -> assertEquals("domain_activation:" + qualifier, key.wireName(),
                        "The wire form is kind:qualifier"),
                () -> assertEquals(Optional.of(key), DerivedFactKey.fromWireName(key.wireName()),
                        "The key reads back from its wire form"));
    }

    @Test
    @DisplayName("a kind part that names no kind is refused")
    void unknownKindPartIsRefused() {
        var unknown = "not_a_kind_" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);

        assertAll(
                () -> assertEquals(Optional.empty(), DerivedFactKey.fromWireName(unknown),
                        "A bare unknown kind part is refused"),
                () -> assertEquals(Optional.empty(), DerivedFactKey.fromWireName(unknown + ":" + qualifier()),
                        "An unknown kind part with a qualifier is refused"));
    }

    @Test
    @DisplayName("a wire form outside the grammar is refused")
    void wireFormOutsideGrammarIsRefused() {
        var longQualifier = Generators.letterStrings(129, 140).next();

        assertAll(
                () -> assertEquals(Optional.empty(), DerivedFactKey.fromWireName(""),
                        "The empty wire form is refused"),
                () -> assertEquals(Optional.empty(), DerivedFactKey.fromWireName("Module_discovery"),
                        "An upper-case kind part is refused"),
                () -> assertEquals(Optional.empty(), DerivedFactKey.fromWireName("module_discovery:"),
                        "An empty qualifier is refused"),
                () -> assertEquals(Optional.empty(), DerivedFactKey.fromWireName("module_discovery:a b"),
                        "A qualifier with a space is refused"),
                () -> assertEquals(Optional.empty(), DerivedFactKey.fromWireName("module_discovery:" + longQualifier),
                        "A qualifier longer than 128 characters is refused"));
    }

    @Test
    @DisplayName("a qualifier outside its grammar is refused by the constructor")
    void qualifierOutsideGrammarIsRefused() {
        var kind = DerivedFactKind.ACTIVE_PROFILES;

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new DerivedFactKey(kind, ""),
                        "An empty qualifier is refused"),
                () -> assertThrows(IllegalArgumentException.class, () -> new DerivedFactKey(kind, "a b"),
                        "A qualifier with a space is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new DerivedFactKey(kind, Generators.letterStrings(129, 140).next()),
                        "A qualifier longer than 128 characters is refused"),
                () -> assertThrows(IllegalArgumentException.class, () -> new DerivedFactKey(kind, "a/b"),
                        "A qualifier with a slash is refused"));
    }

    @Test
    @DisplayName("the qualifier grammar admits letters, digits, dot, dash and underscore")
    void qualifierGrammarAdmitsItsAlphabet() {
        var qualifier = "v1." + Generators.letterStrings(1, 8).next() + "-"
                + Generators.integers(1, 99).next() + "_x";

        var key = new DerivedFactKey(DerivedFactKind.ACTIVE_PROFILES, qualifier);

        assertTrue(key.wireName().endsWith(":" + qualifier), "The qualifier keeps its characters");
    }
}
