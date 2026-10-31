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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The closed fact-kind enumeration (Store Schemas § Derived Facts Store, {@code kind}; PM-EXT-6). The completeness
 * check derives its expected set from {@link DerivedFactKind#values()} at run time, so a kind added to or removed from
 * the enum is checked without editing this test.
 */
@EnableGeneratorController
@DisplayName("Derived fact kind")
class DerivedFactKindTest {

    @Test
    @DisplayName("every kind has one distinct wire name and reads back from it")
    void everyKindRoundTripsItsWireName() {
        var kinds = DerivedFactKind.values();
        var wireNames = Arrays.stream(kinds).map(DerivedFactKind::wireName).toList();

        assertAll(
                () -> assertFalse(wireNames.isEmpty(), "The enumeration declares at least one kind"),
                () -> assertEquals(wireNames.size(),
                        wireNames.stream().distinct().count(), "Every wire name is distinct"),
                () -> assertTrue(Arrays.stream(kinds).allMatch(kind -> !kind.wireName().isBlank()),
                        "Every kind has a non-blank wire name"),
                () -> assertEquals(Arrays.stream(kinds).toList(),
                        wireNames.stream().map(DerivedFactKind::fromWireName)
                                .map(Optional::orElseThrow).toList(),
                        "Every kind reads back from its wire name"));
    }

    @Test
    @DisplayName("agent_identity alone is operator-only, derived from the enumeration at run time")
    void agentIdentityAloneIsOperatorOnly() {
        var operatorOnly = Arrays.stream(DerivedFactKind.values()).filter(DerivedFactKind::operatorOnly).toList();

        assertAll(
                () -> assertEquals(List.of(DerivedFactKind.AGENT_IDENTITY), operatorOnly,
                        "agent_identity alone is operator-only"),
                () -> assertTrue(DerivedFactKind.AGENT_IDENTITY.operatorOnly(),
                        "agent_identity is operator-only"),
                () -> assertFalse(DerivedFactKind.MODULE_DISCOVERY.operatorOnly(),
                        "module_discovery is not operator-only"));
    }

    @Test
    @DisplayName("a wire name that no kind has is refused")
    void unknownWireNameIsRefused() {
        var unknown = "not_a_kind_" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);

        assertAll(
                () -> assertEquals(Optional.empty(), DerivedFactKind.fromWireName(unknown),
                        "An unknown wire name resolves to no kind"),
                () -> assertTrue(Arrays.stream(DerivedFactKind.values())
                                .noneMatch(kind -> kind.wireName().equals(unknown)),
                        "The unknown name is not a kind"));
    }

    @Test
    @DisplayName("the wire names are the lower-case identifiers of the specification")
    void wireNamesAreLowerCaseIdentifiers() {
        var wireNames = Arrays.stream(DerivedFactKind.values()).map(DerivedFactKind::wireName)
                .collect(Collectors.joining(","));

        assertAll(
                () -> assertTrue(wireNames.matches("[a-z_,]+"), "Every wire name is a lower-case identifier"),
                () -> assertEquals(16, DerivedFactKind.values().length, "The schema enumerates sixteen kinds"));
    }
}
