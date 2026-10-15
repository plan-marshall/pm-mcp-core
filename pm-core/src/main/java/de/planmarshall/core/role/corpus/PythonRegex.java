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
     */
    static String translate(String regex) {
        var java = new StringBuilder(regex.length() + 16);
        var inClass = false;
        var index = 0;
        while (index < regex.length()) {
            var current = regex.charAt(index);
            if (current == '\\' && index + 1 < regex.length()) {
                java.append(escape(regex.charAt(index + 1), inClass));
                index += 2;
                continue;
            }
            if (current == '[' && !inClass) {
                inClass = true;
                java.append(current);
                index++;
                // A ']' first in the class (after an optional '^') is a member, not the end of the class.
                if (index < regex.length() && regex.charAt(index) == '^') {
                    java.append('^');
                    index++;
                }
                if (index < regex.length() && regex.charAt(index) == ']') {
                    java.append("\\]");
                    index++;
                }
                continue;
            }
            if (current == ']' && inClass) {
                inClass = false;
            }
            if (inClass && (current == '[' || current == '&')) {
                // A literal in a Python class; in a Java class '[' opens a nested class and "&&" intersects.
                java.append('\\');
            }
            java.append(current);
            index++;
        }
        return java.toString();
    }

    private static String escape(char escaped, boolean inClass) {
        return switch (escaped) {
            case 'd' -> "\\p{Nd}";
            case 'D' -> "\\P{Nd}";
            case 'w' -> "[" + WORD + "]";
            case 'W' -> "[^" + WORD + "]";
            case 's' -> "[" + SPACE + "]";
            case 'S' -> "[^" + SPACE + "]";
            case 'b' -> inClass ? "\\x08" : BOUNDARY;
            case 'B' -> inClass ? "\\B" : NO_BOUNDARY;
            case 'Z' -> inClass ? "\\Z" : "\\z";
            default -> "\\" + escaped;
        };
    }
}
