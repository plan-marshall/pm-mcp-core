/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.architecture;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;

/**
 * The offer a project setup shows before an architecture enrichment costs model tokens (Domain Extensions § Setup
 * Enrichment; Harness Profiles § Tokens, Not Money): the modules to describe, the expected tokens, and a cost line that
 * states tokens and never money.
 */
@EnableGeneratorController
@DisplayName("Enrichment cost offer")
class EnrichmentOfferTest {

    @Test
    @DisplayName("Should estimate the compiled tokens per module")
    void estimatesTheCompiledTokensPerModule() {
        assertAll(
                () -> assertEquals(3 * EnrichmentOffer.PER_MODULE, EnrichmentOffer.expectedTokens(3),
                        "Three modules cost three compiled estimates"),
                () -> assertEquals(0, EnrichmentOffer.expectedTokens(0), "No module costs no tokens"));
    }

    @Test
    @DisplayName("Should offer the named modules with their expected tokens")
    void offersTheNamedModules() {
        var modules = List.of("pm-core", "pm-runtime");

        var offer = EnrichmentOffer.of(modules);

        assertAll(
                () -> assertEquals(modules, offer.modules(), "The offer names the modules to describe"),
                () -> assertEquals(2 * EnrichmentOffer.PER_MODULE, offer.expectedTokens(),
                        "The offer states the expected tokens"));
    }

    @Test
    @DisplayName("Should state the cost in tokens and never in money")
    void statesTheCostInTokensOnly() {
        var line = EnrichmentOffer.of(List.of("pm-core")).costLine();

        assertAll(
                () -> assertTrue(line.endsWith(" tokens"), "The cost line ends in the token count"),
                () -> assertFalse(line.matches(".*[$€£¥].*"), "The cost line carries no currency"),
                () -> assertFalse(line.toLowerCase(Locale.ROOT).contains("usd"),
                        "The cost line names no money unit"));
    }

    @Test
    @DisplayName("Should refuse an offer of no module and a negative estimate")
    void refusesWhatItCannotOffer() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> EnrichmentOffer.of(List.of()),
                        "An enrichment offers at least one module"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new EnrichmentOffer(List.of(module()), -1),
                        "The expected tokens are not negative"),
                () -> assertThrows(IllegalArgumentException.class, () -> EnrichmentOffer.expectedTokens(-1),
                        "A module count is not negative"));
    }

    private static String module() {
        return Generators.letterStrings(3, 12).next();
    }
}
