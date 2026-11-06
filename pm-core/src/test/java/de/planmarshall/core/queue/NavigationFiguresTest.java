/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.queue;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.testing.generators.CoreGenerators;

/**
 * Each navigation figure is derived with its population and the right sentinel where the population cannot be read, and
 * the skill residency names the bytes delivered in-band and the sections read on demand (Model Work § Navigation
 * Figures).
 */
@EnableGeneratorController
@DisplayName("Navigation figures")
class NavigationFiguresTest {

    private static final Instant AT =
            Instant.ofEpochSecond(Generators.longs(1_600_000_000L, 1_900_000_000L).next());

    private static final String READ_FILE = "r" + Generators.letterStrings(3, 10).next();
    private static final String READ_RANGE = "q" + Generators.letterStrings(3, 10).next();
    private static final String SEARCH_CODE = "s" + Generators.letterStrings(3, 10).next();

    private static final NavigationVocabulary VOCABULARY = new NavigationVocabulary(
            Set.of(READ_FILE, READ_RANGE), Set.of(READ_FILE), Set.of(SEARCH_CODE),
            "pm_lsp", "pm_skill");

    private static ActivityEntry turn(String usage) {
        return ActivityEntry.builder().seq(1).attempt(1).source(ActivityEntry.Source.HARNESS)
                .kind(ActivityEntry.Kind.TURN).usage(usage).at(AT).build();
    }

    private static ActivityEntry call(int seq, String tool, String target) {
        return ActivityEntry.builder().seq(seq).attempt(1).source(ActivityEntry.Source.HARNESS)
                .kind(ActivityEntry.Kind.TOOL_CALL).tool(new ActivityEntry.Tool(tool, target)).at(AT).build();
    }

    @Test
    @DisplayName("the cache-read share divides the cache reads by the context tokens")
    void cacheReadShare() {
        var figures = NavigationFigures.derive(List.of(
                        turn("{\"input_tokens\":40,\"cache_read\":60,\"cache_creation\":0}"),
                        turn("{\"input_tokens\":10,\"cache_read\":10,\"cache_creation\":10}")),
                VOCABULARY, List.of(), Map.of());

        assertAll(
                () -> assertEquals(70.0 / 130, figures.cacheReadShare().value(), 0.0001),
                () -> assertEquals(2, figures.cacheReadShare().population()),
                () -> assertTrue(figures.cacheReadShare().isMeasured()));
    }

    @Test
    @DisplayName("a harness that reports no cache reads is unmeasured, never zero")
    void cacheReadUnmeasured() {
        var figures = NavigationFigures.derive(List.of(
                        turn("{\"input_tokens\":40,\"cache_read\":\"unmeasured\",\"cache_creation\":0}")),
                VOCABULARY, List.of(), Map.of());
        var notApplicable = NavigationFigures.derive(List.of(
                        turn("{\"input_tokens\":40,\"cache_read\":\"not_applicable\",\"cache_creation\":0}")),
                VOCABULARY, List.of(), Map.of());

        assertAll(
                () -> assertEquals(Figure.SENTINEL_UNMEASURED, figures.cacheReadShare().sentinel()),
                () -> assertEquals(Figure.SENTINEL_UNMEASURED, notApplicable.cacheReadShare().sentinel()),
                () -> assertFalse(figures.cacheReadShare().isMeasured()));
    }

    @Test
    @DisplayName("a turn population that cannot be read is indeterminate, not unmeasured")
    void cacheReadIndeterminate() {
        var empty = NavigationFigures.derive(List.of(), VOCABULARY, List.of(), Map.of());
        var nullUsage = NavigationFigures.derive(List.of(
                        ActivityEntry.builder().seq(1).attempt(1).source(ActivityEntry.Source.HARNESS)
                                .kind(ActivityEntry.Kind.TURN).at(AT).build()),
                VOCABULARY, List.of(), Map.of());

        assertAll(
                () -> assertEquals(Figure.SENTINEL_INDETERMINATE, empty.cacheReadShare().sentinel()),
                () -> assertEquals(Figure.SENTINEL_INDETERMINATE, nullUsage.cacheReadShare().sentinel()));
    }

    @Test
    @DisplayName("a negative token count is no measurement")
    void negativeCounts() {
        var figures = NavigationFigures.derive(List.of(
                        turn("{\"input_tokens\":-1,\"cache_read\":10,\"cache_creation\":0}")),
                VOCABULARY, List.of(), Map.of());

        assertEquals(Figure.SENTINEL_UNMEASURED, figures.cacheReadShare().sentinel());
    }

    @Test
    @DisplayName("revisits count a file read twice in the same task over all its reads")
    void revisits() {
        var readTwice = "a" + Generators.letterStrings(1, 8).next() + ".java";
        var readOnce = "b" + Generators.letterStrings(1, 8).next() + ".java";
        var figures = NavigationFigures.derive(List.of(
                call(1, READ_FILE, readTwice), call(2, READ_FILE, readOnce),
                call(3, READ_FILE, readTwice)), VOCABULARY, List.of(), Map.of());

        assertAll(
                () -> assertEquals(1.0 / 3, figures.revisits().value(), 0.0001),
                () -> assertEquals(3, figures.revisits().population()));
    }

    @Test
    @DisplayName("a figure whose population is empty is indeterminate")
    void indeterminate() {
        var figures = NavigationFigures.derive(List.of(), VOCABULARY, List.of(), Map.of());

        assertAll(
                () -> assertEquals(Figure.SENTINEL_INDETERMINATE, figures.revisits().sentinel()),
                () -> assertEquals(Figure.SENTINEL_INDETERMINATE, figures.languageServerUse().sentinel()),
                () -> assertEquals(Figure.SENTINEL_INDETERMINATE, figures.fallbackToReadRate().sentinel()));
    }

    @Test
    @DisplayName("the language-server share counts its reads among all code lookups")
    void languageServerUse() {
        var file = Generators.letterStrings(1, 8).next() + ".java";
        var code = Generators.letterStrings(1, 8).next();
        var figures = NavigationFigures.derive(List.of(
                        call(1, "pm_lsp", file), call(2, "pm_lsp", file), call(3, SEARCH_CODE, code)),
                VOCABULARY, List.of(), Map.of());

        assertAll(
                () -> assertEquals(2.0 / 3, figures.languageServerUse().value(), 0.0001),
                () -> assertEquals(3, figures.languageServerUse().population()));
    }

    @Test
    @DisplayName("a whole-file read after a lookup is a fallback; a partial read is not")
    void fallbackToRead() {
        var file = Generators.letterStrings(1, 8).next() + ".java";
        var whole = NavigationFigures.derive(List.of(
                call(1, "pm_lsp", file), call(2, READ_FILE, file)), VOCABULARY, List.of(), Map.of());
        var partial = NavigationFigures.derive(List.of(
                call(1, "pm_lsp", file), call(2, READ_RANGE, file)), VOCABULARY, List.of(), Map.of());

        assertAll(
                () -> assertEquals(1.0, whole.fallbackToReadRate().value(), 0.0001),
                () -> assertEquals(0.0, partial.fallbackToReadRate().value(), 0.0001),
                () -> assertEquals(1, partial.fallbackToReadRate().population()));
    }

    @Test
    @DisplayName("the residency of a skill names its in-band bytes and the sections read on demand")
    void residency() {
        var skill = CoreGenerators.roleSkills().next();
        var other = CoreGenerators.roleSkills().next();
        var required = List.of(skill, other);
        var figures = NavigationFigures.derive(List.of(
                        call(1, "pm_skill", skill.uri() + "#anchor-one"),
                        call(2, "pm_skill", skill.uri() + "#anchor-two"),
                        call(3, "pm_skill", skill.uri())),
                VOCABULARY, required,
                Map.of(skill.uri(), 4096L));

        assertAll(
                () -> assertEquals(2, figures.residency().size()),
                () -> assertEquals(4096L, figures.residency().getFirst().inBandBytes()),
                () -> assertEquals(List.of("anchor-one", "anchor-two"),
                        figures.residency().getFirst().sectionsOnDemand()),
                () -> assertEquals(0L, figures.residency().get(1).inBandBytes()),
                () -> assertTrue(figures.residency().get(1).sectionsOnDemand().isEmpty()));
    }

    @Test
    @DisplayName("a figure is either measured with a population or one of the two sentinels")
    void figureShape() {
        var name = Generators.letterStrings(1, 8).next();
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new Figure(name, 1.0, 0, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new Figure(name, null, 1, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new Figure(name, 1.0, 0, "other")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Figure(name, 1.0, 1, Figure.SENTINEL_INDETERMINATE)),
                () -> assertTrue(Figure.measured(name, 0.5, 2).isMeasured()),
                () -> assertEquals(Figure.SENTINEL_UNMEASURED, Figure.unmeasured(name).sentinel()));
    }
}
