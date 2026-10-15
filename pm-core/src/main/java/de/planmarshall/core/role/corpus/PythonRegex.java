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

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.experimental.UtilityClass;

/**
 * Compiles a regular expression written for Python's {@code re} module on a {@code str}, as {@code validate.py} of the
 * corpus and the corpus schemas write them, into a {@link Pattern} that matches the same strings (PM-WORK-6).
 * <p>
 * The two dialects share their syntax but not every meaning; the compiled pattern takes Python's:
 * <ul>
 * <li>{@code \n} is the only line terminator ({@link Pattern#UNIX_LINES}): {@code $} matches at the end and before a
 * final {@code \n} only, never before a final {@code \r}, U+0085, U+2028 or U+2029, and
 * {@code .} matches every character but {@code \n}.</li>
 * <li>{@code \d} is a Unicode decimal digit ({@code \p{Nd}}), {@code \w} a Unicode letter, a Unicode number or
 * {@code _} (Python's {@code str.isalnum()}), {@code \s} a character of Python's {@code str.isspace()}, which counts
 * {@code \x1c} to {@code \x1f} as well; {@code \D}, {@code \W} and {@code \S} are their complements. Inside a character
 * class each stands for the same set.</li>
 * <li>{@code \b} and {@code \B} are the word boundary of that {@code \w}; inside a character class {@code \b} is the
 * backspace.</li>
 * <li>{@code \Z} is the very end of the string, Java's {@code \z}.</li>
 * <li>{@code \B} and {@code \Z} inside a character class do not compile, as Python refuses them as a bad escape.</li>
 * <li>A case-insensitive match ({@code (?i)}) folds the case of every Unicode letter ({@link Pattern#UNICODE_CASE}).</li>
 * <li>In a character class {@code [} and {@code &} are members, as in Python, not a nested class or an
 * intersection.</li>
 * </ul>
 * Syntax Python has and Java lacks (such as {@code (?P<name>...)} or a brace that is no quantifier) does not compile;
 * the caller reports that, never a match.
 *
 * @since 0.1
 */
@UtilityClass
class PythonRegex {

    private static final String WORD = "\\p{L}\\p{N}_";
    private static final String SPACE = "\\x09-\\x0d\\x1c-\\x20\\x85\\xa0\\x{1680}\\x{2000}-\\x{200a}\\x{2028}\\x{2029}"
            + "\\x{202f}\\x{205f}\\x{3000}";
    private static final String BOUNDARY = "(?:(?<=[" + WORD + "])(?![" + WORD + "])|(?<![" + WORD + "])(?=[" + WORD
            + "]))";
    private static final String NO_BOUNDARY = "(?:(?<=[" + WORD + "])(?=[" + WORD + "])|(?<![" + WORD + "])(?![" + WORD
            + "]))";

    /**
     * @param regex a regular expression in Python's syntax
     * @return the pattern that matches what Python's {@code re} matches on a {@code str}
     * @throws PatternSyntaxException if the expression does not compile
     */
    static Pattern compile(String regex) {
        return Pattern.compile(translate(regex), Pattern.UNIX_LINES | Pattern.UNICODE_CASE);
    }

    /**
     * @param regex a regular expression in Python's syntax
     * @return the expression in Java's syntax with Python's meaning of the escapes this class names
     * @throws PatternSyntaxException for {@code \B} or {@code \Z} inside a character class, which Python refuses as a
     *                                bad escape
     */
    static String translate(String regex) {
        var java = new StringBuilder(regex.length() + 16);
        var inClass = false;
        var index = 0;
        while (index < regex.length()) {
            var current = regex.charAt(index);
            if (current == '\\' && index + 1 < regex.length()) {
                java.append(escape(regex, index, inClass));
                index += 2;
            } else if (current == '[' && !inClass) {
                inClass = true;
                java.append(current);
                index = classStart(regex, index + 1, java);
            } else {
                inClass = inClass && current != ']';
                literal(current, inClass, java);
                index++;
            }
        }
        return java.toString();
    }

    /**
     * Copies the start of a character class: a {@code ]} first in the class (after an optional {@code ^}) is a member,
     * not the end of the class.
     *
     * @param regex the expression
     * @param start the index after the {@code [} that opens the class
     * @param java  the translation so far
     * @return the index after the start of the class
     */
    private static int classStart(String regex, int start, StringBuilder java) {
        var index = start;
        if (index < regex.length() && regex.charAt(index) == '^') {
            java.append('^');
            index++;
        }
        if (index < regex.length() && regex.charAt(index) == ']') {
            java.append("\\]");
            index++;
        }
        return index;
    }

    private static void literal(char current, boolean inClass, StringBuilder java) {
        if (inClass && (current == '[' || current == '&')) {
            // A literal in a Python class; in a Java class '[' opens a nested class and "&&" intersects.
            java.append('\\');
        }
        java.append(current);
    }

    /**
     * @param regex   the expression
     * @param index   the index of the backslash of the escape
     * @param inClass {@code true} inside a character class
     * @return the escape in Java's syntax with Python's meaning
     * @throws PatternSyntaxException for {@code \B} or {@code \Z} inside a character class, a bad escape in Python
     */
    private static String escape(String regex, int index, boolean inClass) {
        var escaped = regex.charAt(index + 1);
        if (inClass && (escaped == 'B' || escaped == 'Z')) {
            throw new PatternSyntaxException("bad escape \\" + escaped + " inside a character class", regex, index);
        }
        return switch (escaped) {
            case 'd' -> "\\p{Nd}";
            case 'D' -> "\\P{Nd}";
            case 'w' -> "[" + WORD + "]";
            case 'W' -> "[^" + WORD + "]";
            case 's' -> "[" + SPACE + "]";
            case 'S' -> "[^" + SPACE + "]";
            case 'b' -> inClass ? "\\x08" : BOUNDARY;
            case 'B' -> NO_BOUNDARY;
            case 'Z' -> "\\z";
            default -> "\\" + escaped;
        };
    }
}
