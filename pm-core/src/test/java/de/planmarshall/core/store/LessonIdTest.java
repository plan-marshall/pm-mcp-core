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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The identifier of a lesson (Store Schemas § Lesson Record, Identifiers § {@code lesson_id}; PM-IMPL-2).
 * <p>
 * The hazard of PM-WATCH-STORE-14 is that a lesson listed by the store cannot be addressed by the id it is listed
 * under: a headerless legacy lesson must still parse as an identifier and stay addressable, and a file name that is
 * not {@code <lesson_id>.md} must be no lesson at all.
 */
@EnableGeneratorController
@DisplayName("Lesson id")
class LessonIdTest {

    @Test
    @DisplayName("the lesson ids of the watch fixtures parse")
    void watchFixtureIdsParse() {
        var fixtures = new String[]{LessonFixtures.WATCH_ID_NO_HEADER, LessonFixtures.WATCH_ID_HEADERLESS,
                LessonFixtures.WATCH_ID_HEADERLESS_SECOND};

        assertAll(
                () -> assertTrue(LessonId.parse(fixtures[0]).isPresent(), "the headerless lesson id parses"),
                () -> assertTrue(LessonId.parse(fixtures[1]).isPresent(), "the second fixture id parses"),
                () -> assertTrue(LessonId.parse(fixtures[2]).isPresent(), "the third fixture id parses"));
    }

    @Test
    @DisplayName("a value outside the grammar of the schema is refused")
    void valueOutsideTheGrammarIsRefused() {
        var monthWithoutLeadingZero = "2026-9-22-12-001";
        var sequenceOfTwoDigits = "2026-09-22-12-01";
        var withoutSequence = "2026-09-22-12";
        var notAnId = Generators.letterStrings(3, 20).next();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new LessonId(monthWithoutLeadingZero),
                        "A month without its leading zero is refused"),
                () -> assertThrows(IllegalArgumentException.class, () -> new LessonId(sequenceOfTwoDigits),
                        "A sequence of two digits is refused"),
                () -> assertThrows(IllegalArgumentException.class, () -> new LessonId(withoutSequence),
                        "An id without its sequence is refused"),
                () -> assertThrows(IllegalArgumentException.class, () -> new LessonId(notAnId),
                        "A text that is no id at all is refused"));
    }

    @Test
    @DisplayName("an id longer than the schema admits is refused, the longest admitted one is taken")
    void lengthIsBoundedByTheSchema() {
        var prefix = "2026-09-22-12-";
        var longest = prefix + "1".repeat(LessonId.MAX_LENGTH - prefix.length());
        var tooLong = prefix + "1".repeat(LessonId.MAX_LENGTH - prefix.length() + 1);

        assertAll(
                () -> assertEquals(LessonId.MAX_LENGTH, longest.length(), "The longest id has the schema length"),
                () -> assertEquals(longest, new LessonId(longest).value(), "The longest id is taken"),
                () -> assertThrows(IllegalArgumentException.class, () -> new LessonId(tooLong),
                        "One character above the bound is refused"));
    }

    @Test
    @DisplayName("the file name of an id round-trips")
    void fileNameRoundTrips() {
        var id = LessonFixtures.lessonId();

        assertAll(
                () -> assertEquals(id.value() + ".md", id.fileName(), "The file name appends the suffix"),
                () -> assertEquals(Optional.of(id), LessonId.fromFileName(id.fileName()),
                        "The file name parses back to the id"));
    }

    @Test
    @DisplayName("a file name that does not parse is no lesson")
    void fileNameOutsideTheGrammarIsNoLesson() {
        var otherSuffix = LessonFixtures.WATCH_ID_NO_HEADER + ".txt";
        var withoutSuffix = LessonFixtures.WATCH_ID_NO_HEADER;
        var foreignName = "notes.md";

        assertAll(
                () -> assertTrue(LessonId.fromFileName(otherSuffix).isEmpty(), "Another suffix is no lesson"),
                () -> assertTrue(LessonId.fromFileName(withoutSuffix).isEmpty(), "A name without suffix is no lesson"),
                () -> assertTrue(LessonId.fromFileName(foreignName).isEmpty(), "A foreign name is no lesson"));
    }

    @Test
    @DisplayName("parse returns empty instead of throwing")
    void parseIsTotal() {
        var outsideGrammar = "2026-09-22-12-01";

        assertAll(
                () -> assertEquals(Optional.of(LessonFixtures.lessonId()),
                        LessonId.parse(LessonFixtures.WATCH_ID_NO_HEADER), "A valid id is returned"),
                () -> assertEquals(Optional.empty(), LessonId.parse(outsideGrammar), "An invalid id is empty"));
    }

    @Test
    @DisplayName("the hour prefix is the UTC hour a new id is allocated against")
    void hourPrefixIsTheUtcHour() {
        var id = new LessonId("2026-09-22-12-001");

        assertEquals("2026-09-22-12", id.hourPrefix(), "The hour prefix is the first thirteen characters");
    }
}
