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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.provider.git.primitive.CommitTrailer.Stored;
import de.planmarshall.provider.git.primitive.CommitTrailer.TrailerSource;
import de.planmarshall.provider.git.testing.generators.GitGenerators;

/**
 * The trailer of a server commit: the constraints of the stored key {@code commit_trailer}, the compiled default and the
 * resolution per half with its source.
 */
@EnableGeneratorController
@DisplayName("CommitTrailer")
class CommitTrailerTest {

    private final TypedGenerator<CommitTrailer> trailers = GitGenerators.trailers();

    @Test
    @DisplayName("compiled default is plan-marshall <noreply@cuioss.de> (the specified default)")
    void compiledDefault() {
        var compiled = CommitTrailer.compiled();

        assertAll("compiled trailer",
                () -> assertEquals("plan-marshall", compiled.name(), "The compiled name is plan-marshall"),
                () -> assertEquals("noreply@cuioss.de", compiled.email(), "The compiled email is the noreply one"));
    }

    @Test
    @DisplayName("wire names of the sources are configured, default and invalid_ignored (the audit contract)")
    void wireNames() {
        assertAll("source wire names",
                () -> assertEquals("configured", TrailerSource.CONFIGURED.wireName(),
                        "The configured source has its wire name"),
                () -> assertEquals("default", TrailerSource.DEFAULT.wireName(),
                        "The default source has its wire name"),
                () -> assertEquals("invalid_ignored", TrailerSource.INVALID_IGNORED.wireName(),
                        "The ignored source has its wire name"));
    }

    @Nested
    @DisplayName("constraints")
    class Constraints {

        @RepeatedTest(20)
        @DisplayName("a generated trailer is accepted")
        void generatedTrailerIsAccepted() {
            var trailer = trailers.next();

            assertEquals(trailer, new CommitTrailer(trailer.name(), trailer.email()),
                    "A trailer within the constraints is built");
        }

        @Test
        @DisplayName("a name of 128 characters is accepted, one of 129 refused")
        void nameBound() {
            var email = trailers.next().email();
            var longest = "n".repeat(CommitTrailer.NAME_MAX_LENGTH);

            assertAll("name bound",
                    () -> assertEquals(longest, new CommitTrailer(longest, email).name(),
                            "A name of the largest length is accepted"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new CommitTrailer(longest + "n", email), "A name one beyond the bound is refused"));
        }

        @Test
        @DisplayName("an email of more than 254 characters is refused")
        void emailBound() {
            var name = trailers.next().name();
            var tooLong = "a".repeat(CommitTrailer.EMAIL_MAX_LENGTH) + "@x";

            assertThrows(IllegalArgumentException.class, () -> new CommitTrailer(name, tooLong),
                    "An email beyond the bound is refused");
        }

        // invalid names of the schema: an angle bracket, a line break, empty
        @ParameterizedTest
        @ValueSource(strings = {"", "a<b", "a>b", "line\nbreak", "carriage\rreturn"})
        @DisplayName("an invalid name is refused")
        void invalidNameIsRefused(String name) {
            var email = trailers.next().email();

            assertThrows(IllegalArgumentException.class, () -> new CommitTrailer(name, email),
                    "A name breaking the constraints is refused");
        }

        // invalid emails of the schema: no @, two @, an angle bracket, white space, empty
        @ParameterizedTest
        @ValueSource(strings = {"", "nobody", "a@b@c", "<a@b>", "a b@c", "a@b\nc"})
        @DisplayName("an invalid email is refused")
        void invalidEmailIsRefused(String email) {
            var name = trailers.next().name();

            assertThrows(IllegalArgumentException.class, () -> new CommitTrailer(name, email),
                    "An email breaking the constraints is refused");
        }
    }

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
    @DisplayName("resolution")
    class Resolution {

        @Test
        @DisplayName("nothing stored resolves the compiled default with source default")
        void nothingStored() {
            var resolution = CommitTrailer.resolve(null, null);

            assertAll("default resolution",
                    () -> assertEquals(CommitTrailer.compiled(), resolution.trailer(),
                            "The compiled default trailer is resolved"),
                    () -> assertEquals(TrailerSource.DEFAULT, resolution.nameSource(),
                            "The name half reports the default"),
                    () -> assertEquals(TrailerSource.DEFAULT, resolution.emailSource(),
                            "The email half reports the default"));
        }

        @RepeatedTest(10)
        @DisplayName("a stored local value wins over the machine value")
        void localWins() {
            var local = trailers.next();
            var machine = trailers.next();

            var resolution = CommitTrailer.resolve(stored(local), stored(machine));

            assertAll("local resolution",
                    () -> assertEquals(local, resolution.trailer(), "The local trailer is the resolved one"),
                    () -> assertEquals(TrailerSource.CONFIGURED, resolution.nameSource(),
                            "The name half reports configured"),
                    () -> assertEquals(TrailerSource.CONFIGURED, resolution.emailSource(),
                            "The email half reports configured"));
        }

        @RepeatedTest(10)
        @DisplayName("the machine value is used where the project stores none")
        void machineFallback() {
            var machine = trailers.next();

            var resolution = CommitTrailer.resolve(null, stored(machine));

            assertAll("machine resolution",
                    () -> assertEquals(machine, resolution.trailer(), "The machine trailer is the resolved one"),
                    () -> assertEquals(TrailerSource.CONFIGURED, resolution.nameSource(),
                            "The machine name half reports configured"));
        }

        @RepeatedTest(10)
        @DisplayName("each half resolves on its own level")
        void halvesResolveSeparately() {
            var local = trailers.next();
            var machine = trailers.next();

            var resolution = CommitTrailer.resolve(new Stored(local.name(), null), new Stored(null, machine.email()));

            assertAll("per-half resolution",
                    () -> assertEquals(local.name(), resolution.trailer().name(), "The name comes from the project"),
                    () -> assertEquals(machine.email(), resolution.trailer().email(),
                            "The email comes from the machine"));
        }

        @RepeatedTest(10)
        @DisplayName("an invalid local value falls through to a valid machine value as configured")
        void invalidLocalFallsThrough() {
            var machine = trailers.next();

            var resolution = CommitTrailer.resolve(new Stored("bad<name", "no-at-sign"), stored(machine));

            assertAll("fall-through resolution",
                    () -> assertEquals(machine, resolution.trailer(), "The valid machine trailer is resolved"),
                    () -> assertEquals(TrailerSource.CONFIGURED, resolution.emailSource(),
                            "A used stored value reports configured"));
        }

        // the three refusals of the schema named in the specification: an angle bracket, a line break, empty
        @ParameterizedTest
        @ValueSource(strings = {"bad<name", "bad>name", "line\nbreak", ""})
        @DisplayName("an invalid stored name is ignored and reported invalid_ignored")
        void invalidNameIsIgnored(String name) {
            var email = trailers.next().email();

            var resolution = CommitTrailer.resolve(new Stored(name, email), null);

            assertAll("ignored name",
                    () -> assertEquals(CommitTrailer.compiled().name(), resolution.trailer().name(),
                            "The compiled name replaces the bad name"),
                    () -> assertEquals(TrailerSource.INVALID_IGNORED, resolution.nameSource(),
                            "The name half reports invalid_ignored"),
                    () -> assertEquals(TrailerSource.CONFIGURED, resolution.emailSource(),
                            "The valid email half stays configured"));
        }

        // the three refusals of the schema named in the specification: an angle bracket, a line break, empty
        @ParameterizedTest
        @ValueSource(strings = {"<a@b>", "a@b\nc", ""})
        @DisplayName("an invalid stored email is ignored and reported invalid_ignored")
        void invalidEmailIsIgnored(String email) {
            var name = Generators.letterStrings(1, 20).next();

            var resolution = CommitTrailer.resolve(null, new Stored(name, email));

            assertAll("ignored email",
                    () -> assertEquals(CommitTrailer.compiled().email(), resolution.trailer().email(),
                            "The compiled email replaces the bad one"),
                    () -> assertEquals(TrailerSource.INVALID_IGNORED, resolution.emailSource(),
                            "The email half reports invalid_ignored"));
        }

        private Stored stored(CommitTrailer trailer) {
            return new Stored(trailer.name(), trailer.email());
        }
    }
}
