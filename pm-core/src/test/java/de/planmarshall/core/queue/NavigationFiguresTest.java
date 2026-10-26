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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.role.RoleSkill;

/**
 * Each navigation figure is derived with its population and the right sentinel where the population cannot be read, and
 * the skill residency names the bytes delivered in-band and the sections read on demand (Model Work § Navigation
 * Figures).
 */
@DisplayName("Navigation figures")
class NavigationFiguresTest {

    private static final Instant AT = Instant.parse("2026-10-26T10:00:00Z");

    private static final NavigationVocabulary VOCABULARY = new NavigationVocabulary(
            Set.of("read_file", "read_range"), Set.of("read_file"), Set.of("search_code"),
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
                () -> assertEquals(Figure.UNMEASURED, figures.cacheReadShare().sentinel()),
                () -> assertEquals(Figure.UNMEASURED, notApplicable.cacheReadShare().sentinel()),
                () -> assertFalse(figures.cacheReadShare().isMeasured()));
    }

    @Test
    @DisplayName("revisits count a file read twice in the same task over all its reads")
    void revisits() {
        var figures = NavigationFigures.derive(List.of(
                call(1, "read_file", "A.java"), call(2, "read_file", "B.java"),
                call(3, "read_file", "A.java")), VOCABULARY, List.of(), Map.of());

        assertAll(
                () -> assertEquals(1.0 / 3, figures.revisits().value(), 0.0001),
                () -> assertEquals(3, figures.revisits().population()));
    }

    @Test
    @DisplayName("a figure whose population is empty is indeterminate")
    void indeterminate() {
        var figures = NavigationFigures.derive(List.of(), VOCABULARY, List.of(), Map.of());

        assertAll(
                () -> assertEquals(Figure.INDETERMINATE, figures.revisits().sentinel()),
                () -> assertEquals(Figure.INDETERMINATE, figures.languageServerUse().sentinel()),
                () -> assertEquals(Figure.INDETERMINATE, figures.fallbackToReadRate().sentinel()));
    }

    @Test
    @DisplayName("the language-server share counts its reads among all code lookups")
    void languageServerUse() {
        var figures = NavigationFigures.derive(List.of(
                        call(1, "pm_lsp", "A.java"), call(2, "pm_lsp", "B.java"), call(3, "search_code", "C")),
                VOCABULARY, List.of(), Map.of());

        assertAll(
                () -> assertEquals(2.0 / 3, figures.languageServerUse().value(), 0.0001),
                () -> assertEquals(3, figures.languageServerUse().population()));
    }

    @Test
    @DisplayName("a whole-file read after a lookup is a fallback; a partial read is not")
    void fallbackToRead() {
        var whole = NavigationFigures.derive(List.of(
                call(1, "pm_lsp", "A.java"), call(2, "read_file", "A.java")), VOCABULARY, List.of(), Map.of());
        var partial = NavigationFigures.derive(List.of(
                call(1, "pm_lsp", "A.java"), call(2, "read_range", "A.java")), VOCABULARY, List.of(), Map.of());

        assertAll(
                () -> assertEquals(1.0, whole.fallbackToReadRate().value(), 0.0001),
                () -> assertEquals(0.0, partial.fallbackToReadRate().value(), 0.0001),
                () -> assertEquals(1, partial.fallbackToReadRate().population()));
    }

    @Test
    @DisplayName("the residency of a skill names its in-band bytes and the sections read on demand")
    void residency() {
        var required = List.of(new RoleSkill("skill://pm/implementation/SKILL.md", "a".repeat(64)),
                new RoleSkill("skill://pm/testing/SKILL.md", "b".repeat(64)));
        var figures = NavigationFigures.derive(List.of(
                        call(1, "pm_skill", "skill://pm/implementation/SKILL.md#anchor-one"),
                        call(2, "pm_skill", "skill://pm/implementation/SKILL.md#anchor-two"),
                        call(3, "pm_skill", "skill://pm/implementation/SKILL.md")),
                VOCABULARY, required,
                Map.of("skill://pm/implementation/SKILL.md", 4096L));

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
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new Figure("f", 1.0, 0, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new Figure("f", null, 1, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new Figure("f", 1.0, 0, "other")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Figure("f", 1.0, 1, Figure.INDETERMINATE)),
                () -> assertTrue(Figure.measured("f", 0.5, 2).isMeasured()),
                () -> assertEquals(Figure.UNMEASURED, Figure.unmeasured("f").sentinel()));
    }
}
