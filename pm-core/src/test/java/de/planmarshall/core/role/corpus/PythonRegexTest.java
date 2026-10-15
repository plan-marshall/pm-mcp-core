/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.corpus;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A regular expression of {@code validate.py} and the corpus schemas compiled with Python's meaning (PM-WORK-6). Every
 * expected result is what {@code bool(re.search(pattern, value))} answers in Python 3 (Unicode 16) for the same pattern
 * and string.
 */
@DisplayName("Python regex")
class PythonRegexTest {

    static Stream<Arguments> pinned() {
        return Stream.of(
                Arguments.of("$ before one final line feed", "^[0-9a-f]{7,40}$", "abc1234\n", true),
                Arguments.of("$ not before a final carriage return", "^[0-9a-f]{7,40}$", "abc1234\r", false),
                Arguments.of("$ not before a final CRLF", "^[0-9a-f]{7,40}$", "abc1234\r\n", false),
                Arguments.of("$ not before two final line feeds", "^[0-9a-f]{7,40}$", "abc1234\n\n", false),
                Arguments.of("$ not before a final NEL", "^[0-9a-f]{7,40}$", "abc1234\u0085", false),
                Arguments.of("$ not before a final line separator", "^[0-9a-f]{7,40}$", "abc1234 ", false),
                Arguments.of("$ not before a final paragraph separator", "^[0-9a-f]{7,40}$", "abc1234 ", false),
                Arguments.of("multiline $ not before a carriage return", "(?m)^a$", "a\rb", false),
                Arguments.of(". matches a carriage return", "^a.b$", "a\rb", true),
                Arguments.of("\\Z is the very end", "abc\\Z", "abc\n", false),
                Arguments.of("\\s matches the file separator", "^\\s$", "\u001c", true),
                Arguments.of("\\s matches a no-break space", "^\\s$", " ", true),
                Arguments.of("\\s matches the line separator", "^\\s$", " ", true),
                Arguments.of("\\s does not match a zero-width space", "^\\s$", "​", false),
                Arguments.of("\\S does not match the unit separator", "^\\S$", "\u001f", false),
                Arguments.of("\\s in a class matches the file separator", "^[\\s]$", "\u001c", true),
                Arguments.of("\\w matches a Latin letter with accent", "^\\w$", "é", true),
                Arguments.of("\\w does not match a combining mark", "^\\w$", "́", false),
                Arguments.of("\\w does not match an undertie", "^\\w$", "‿", false),
                Arguments.of("\\w matches a superscript two", "^\\w$", "²", true),
                Arguments.of("\\W matches a combining mark", "^\\W$", "́", true),
                Arguments.of("\\d matches an Arabic-Indic digit", "^\\d$", "١", true),
                Arguments.of("\\d does not match a superscript two", "^\\d$", "²", false),
                Arguments.of("\\D does not match an Arabic-Indic digit", "^\\D$", "١", false),
                Arguments.of("\\b is no boundary after a Latin letter with accent", "\\bE\\d{1,2}\\b", "éE12",
                        false),
                Arguments.of("\\b is a boundary after a hyphen", "\\bE\\d{1,2}\\b", "-E12", true),
                Arguments.of("\\B is no boundary after a Latin letter with accent", "\\BE", "éE", true),
                Arguments.of("\\b in a class is the backspace", "^[\\b]$", "\b", true),
                Arguments.of("an escaped backslash before s is a backslash", "^\\\\s$", "\\s", true),
                Arguments.of("(?i) folds Unicode case", "(?i)^é$", "É", true),
                Arguments.of("[ in a class is a member", "^[[a]+$", "[a[", true),
                Arguments.of("&& in a class are members", "^[a&&b]+$", "a&b", true),
                Arguments.of("] first in a class is a member", "^[]a]+$", "]a", true),
                Arguments.of("the repository pattern refuses the file separator", "^https://[^/?#@\\s]+(/[^?#\\s]*)?$",
                        "https://a\u001cb", false),
                Arguments.of("the repository pattern refuses a no-break space", "^https://[^/?#@\\s]+(/[^?#\\s]*)?$",
                        "https://a b", false),
                Arguments.of("the repository pattern accepts a path", "^https://[^/?#@\\s]+(/[^?#\\s]*)?$",
                        "https://ab/c", true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pinned")
    @DisplayName("matches what Python's re.search matches")
    void pinned(String name, String pattern, String value, boolean found) {
        assertEquals(found, PythonRegex.compile(pattern).matcher(value).find());
    }

    @Test
    @DisplayName("syntax Java lacks does not compile")
    void pythonOnlySyntax() {
        assertThrows(PatternSyntaxException.class, () -> PythonRegex.compile("(?P<name>a)"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"[\\B]", "[\\Z]", "^[^a\\B]$", "^[a-z\\Z]+$"})
    @DisplayName("\\B and \\Z in a class do not compile, as Python refuses them as a bad escape")
    void badEscapeInClass(String pattern) {
        var refusal = assertThrows(PatternSyntaxException.class, () -> PythonRegex.compile(pattern));
        assertTrue(refusal.getDescription().startsWith("bad escape"), refusal.getDescription());
    }

    @Test
    @DisplayName("\\B and \\Z outside a class still compile")
    void escapesOutsideClass() {
        assertAll(
                () -> assertTrue(PythonRegex.compile("a\\B").matcher("ab").find()),
                () -> assertFalse(PythonRegex.compile("[a]\\Z").matcher("a\n").find()));
    }
}
