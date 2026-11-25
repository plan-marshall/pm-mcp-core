/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.git.primitive;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.provider.git.primitive.CommitTrailer.Stored;
import de.planmarshall.provider.git.primitive.CommitTrailer.TrailerSource;
import de.planmarshall.provider.git.testing.generators.GitGenerators;

/**
 * The trailer of a server commit for the code points a literal cannot carry: an unpaired surrogate (no UTF-8 encoding)
 * and the Unicode line separators U+0085, U+2028 and U+2029.
 */
@EnableGeneratorController
@DisplayName("CommitTrailer: surrogates and Unicode line separators")
class CommitTrailerUnicodeTest {

    private final TypedGenerator<CommitTrailer> trailers = GitGenerators.trailers();

    @Nested
    @DisplayName("surrogates")
    class Surrogates {

        private final TypedGenerator<String> letters = Generators.letterStrings(1, 20);

        // a surrogate cannot be written as a literal in generated data, so the strings are built from char values
        private static final char HIGH = '\uD800';
        private static final char LOW = '\uDC00';
        private static final String PAIR = new String(Character.toChars(0x1F600));

        @ParameterizedTest
        @ValueSource(chars = {HIGH, LOW})
        @DisplayName("a name or an email with an unpaired surrogate is invalid")
        void unpairedSurrogateIsInvalid(char surrogate) {
            var name = letters.next() + surrogate + letters.next();
            var email = letters.next() + surrogate + "@" + letters.next();

            assertAll("unpaired surrogate",
                    () -> assertFalse(CommitTrailer.isValidName(name), "A name with a lone surrogate is invalid"),
                    () -> assertFalse(CommitTrailer.isValidEmail(email),
                            "An email with a lone surrogate is invalid"));
        }

        @Test
        @DisplayName("a surrogate pair, one supplementary character, stays valid")
        void surrogatePairIsValid() {
            var name = letters.next() + PAIR;
            var email = letters.next() + PAIR + "@" + letters.next();

            assertAll("surrogate pair",
                    () -> assertTrue(CommitTrailer.isValidName(name), "A name with a surrogate pair is valid"),
                    () -> assertTrue(CommitTrailer.isValidEmail(email), "An email with a surrogate pair is valid"));
        }

        @Test
        @DisplayName("a reversed pair is two unpaired surrogates and invalid")
        void reversedPairIsInvalid() {
            var name = letters.next() + LOW + HIGH;

            assertFalse(CommitTrailer.isValidName(name), "A low before a high surrogate is invalid");
        }

        @Test
        @DisplayName("a stored local value with an unpaired surrogate falls through to the machine value")
        void storedSurrogateFallsToMachine() {
            var machine = trailers.next();
            var local = new Stored(letters.next() + HIGH, letters.next() + LOW + "@" + letters.next());

            var resolution = CommitTrailer.resolve(local, new Stored(machine.name(), machine.email()));

            assertAll("fall-through to the machine",
                    () -> assertEquals(machine, resolution.trailer(), "The machine trailer replaces the local one"),
                    () -> assertEquals(TrailerSource.CONFIGURED, resolution.nameSource(),
                            "The used machine name reports configured"));
        }

        @Test
        @DisplayName("a stored value with an unpaired surrogate alone resolves the default as invalid_ignored")
        void storedSurrogateFallsToDefault() {
            var local = new Stored(letters.next() + LOW, letters.next() + HIGH + "@" + letters.next());

            var resolution = CommitTrailer.resolve(local, null);

            assertAll("fall-through to the default",
                    () -> assertEquals(CommitTrailer.compiled(), resolution.trailer(),
                            "The compiled trailer replaces the bad one"),
                    () -> assertEquals(TrailerSource.INVALID_IGNORED, resolution.nameSource(),
                            "The name half reports invalid_ignored"),
                    () -> assertEquals(TrailerSource.INVALID_IGNORED, resolution.emailSource(),
                            "The email half reports invalid_ignored"));
        }
    }

    @Nested
    @DisplayName("Unicode line separators")
    class LineSeparators {

        private final TypedGenerator<String> letters = Generators.letterStrings(1, 20);

        // a line-break code point cannot be a readable literal, so U+0085, U+2028 and U+2029 are given as char values
        private static final char NEL = (char) 0x0085;
        private static final char LINE_SEPARATOR = (char) 0x2028;
        private static final char PARAGRAPH_SEPARATOR = (char) 0x2029;
        private static final String SUPPLEMENTARY = new String(Character.toChars(0x1F600));

        @ParameterizedTest
        @ValueSource(chars = {NEL, LINE_SEPARATOR, PARAGRAPH_SEPARATOR})
        @DisplayName("a name with a Unicode line separator is invalid")
        void separatorInNameIsInvalid(char separator) {
            var name = letters.next() + separator + letters.next();

            assertFalse(CommitTrailer.isValidName(name), "A name with a line separator is invalid");
        }

        // the email needs no rule of its own: the three code points are Unicode white space, which it refuses
        @ParameterizedTest
        @ValueSource(chars = {NEL, LINE_SEPARATOR, PARAGRAPH_SEPARATOR})
        @DisplayName("an email with a Unicode line separator is refused as white space")
        void separatorInEmailIsInvalid(char separator) {
            var email = letters.next() + separator + "@" + letters.next();

            assertFalse(CommitTrailer.isValidEmail(email), "An email with a line separator is invalid");
        }

        @Test
        @DisplayName("a plain name and a name with a supplementary character stay valid")
        void namesWithoutSeparatorStayValid() {
            var plain = letters.next();
            var supplementary = letters.next() + SUPPLEMENTARY;

            assertAll("names without a separator",
                    () -> assertTrue(CommitTrailer.isValidName(plain), "A plain letter name stays valid"),
                    () -> assertTrue(CommitTrailer.isValidName(supplementary),
                            "A supplementary-character name stays valid"));
        }

        @ParameterizedTest
        @ValueSource(chars = {NEL, LINE_SEPARATOR, PARAGRAPH_SEPARATOR})
        @DisplayName("a stored local name with a separator falls through to the machine name as configured")
        void storedSeparatorFallsToMachine(char separator) {
            var localEmail = trailers.next().email();
            var machine = trailers.next();

            var resolution = CommitTrailer.resolve(new Stored(letters.next() + separator, localEmail),
                    new Stored(machine.name(), machine.email()));

            assertAll("fall-through to the machine name",
                    () -> assertEquals(machine.name(), resolution.trailer().name(),
                            "The machine name replaces the local one"),
                    () -> assertEquals(TrailerSource.CONFIGURED, resolution.nameSource(),
                            "The used machine name reports configured"),
                    () -> assertEquals(localEmail, resolution.trailer().email(),
                            "The valid local email is still used"),
                    () -> assertEquals(TrailerSource.CONFIGURED, resolution.emailSource(),
                            "The email half is unaffected by the name"));
        }

        @ParameterizedTest
        @ValueSource(chars = {NEL, LINE_SEPARATOR, PARAGRAPH_SEPARATOR})
        @DisplayName("a stored name with a separator and no machine name resolves the default as invalid_ignored")
        void storedSeparatorFallsToDefault(char separator) {
            var localEmail = trailers.next().email();

            var resolution = CommitTrailer.resolve(new Stored(letters.next() + separator, localEmail), null);

            assertAll("fall-through to the default name",
                    () -> assertEquals(CommitTrailer.compiled().name(), resolution.trailer().name(),
                            "The compiled name replaces the bad one"),
                    () -> assertEquals(TrailerSource.INVALID_IGNORED, resolution.nameSource(),
                            "The name half reports invalid_ignored"),
                    () -> assertEquals(localEmail, resolution.trailer().email(),
                            "The valid local email is still used"),
                    () -> assertEquals(TrailerSource.CONFIGURED, resolution.emailSource(),
                            "The email half is unaffected by the name"));
        }

        @ParameterizedTest
        @ValueSource(chars = {NEL, LINE_SEPARATOR, PARAGRAPH_SEPARATOR})
        @DisplayName("a stored name with a separator and a broken machine name resolves the default")
        void storedSeparatorWithBrokenMachineFallsToDefault(char separator) {
            var machineEmail = trailers.next().email();

            var resolution = CommitTrailer.resolve(new Stored(letters.next() + separator, null),
                    new Stored(separator + letters.next(), machineEmail));

            assertAll("both stored names refused",
                    () -> assertEquals(CommitTrailer.compiled().name(), resolution.trailer().name(),
                            "The compiled name replaces both bad names"),
                    () -> assertEquals(TrailerSource.INVALID_IGNORED, resolution.nameSource(),
                            "The name half reports invalid_ignored"),
                    () -> assertEquals(TrailerSource.CONFIGURED, resolution.emailSource(),
                            "The machine email half stays configured"));
        }
    }
}
