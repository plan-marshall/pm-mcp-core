/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.dsl;

import java.util.ArrayList;
import java.util.List;

/**
 * The line-oriented tokenizer of the workflow language (PM-IMPL-5).
 * <p>
 * It cuts the source into lines, drops a comment from {@code #} to the end of its line, ignores indentation and
 * blank lines, and records every token with its 1-based line and column. A word is a run of letters, digits,
 * {@code _} and {@code -}; it is an {@link Token.Kind#INT} when it consists of digits, an {@link Token.Kind#IDENT}
 * when it is lowercase and starts with a letter, an {@link Token.Kind#ENUM_VALUE} when it starts with a letter and
 * contains an upper-case one, and {@link Token.Kind#INVALID} otherwise. A {@code -} directly followed by {@code >}
 * ends a word, so {@code done->next} reads as three tokens. The JSON of an {@code instance} line is kept as one
 * {@link Token.Kind#JSON} token, so a {@code #} inside it is no comment. The lexer never refuses: an unknown
 * character becomes an {@link Token.Kind#INVALID} token that the parser refuses where it occurs.
 */
final class WorkflowLexer {

    private static final String INSTANCE_KEYWORD = "instance";
    private static final String SINGLE_PUNCTUATION = ".:,()[]<>|=?/";

    private WorkflowLexer() {
    }

    /**
     * The tokens of one line that holds at least one token.
     *
     * @param number the 1-based line number
     * @param tokens the tokens of the line in source order, never empty
     */
    record Line(int number, List<Token> tokens) {

        /**
         * Copies the tokens into an immutable list.
         *
         * @param number the 1-based line number
         * @param tokens the tokens of the line in source order
         */
        Line {
            tokens = List.copyOf(tokens);
        }
    }

    /**
     * Tokenizes a source.
     *
     * @param source the text of the unit
     * @return the lines that hold tokens, in source order; blank and comment-only lines are left out
     */
    static List<Line> tokenize(String source) {
        var lines = new ArrayList<Line>();
        var rawLines = source.split("\n", -1);
        for (var index = 0; index < rawLines.length; index++) {
            var tokens = tokenizeLine(rawLines[index], index + 1);
            if (!tokens.isEmpty()) {
                lines.add(new Line(index + 1, tokens));
            }
        }
        return List.copyOf(lines);
    }

    private static List<Token> tokenizeLine(String text, int lineNumber) {
        var tokens = new ArrayList<Token>();
        var start = skipWhitespace(text, 0);
        if (isInstanceLine(text, start)) {
            tokens.add(new Token(Token.Kind.IDENT, INSTANCE_KEYWORD, lineNumber, start + 1));
            var jsonStart = skipWhitespace(text, start + INSTANCE_KEYWORD.length());
            tokens.add(new Token(Token.Kind.JSON, text.substring(jsonStart).strip(), lineNumber, jsonStart + 1));
            return tokens;
        }
        var position = start;
        while (position < text.length() && text.charAt(position) != '#') {
            position = readToken(text, position, lineNumber, tokens);
            position = skipWhitespace(text, position);
        }
        return tokens;
    }

    private static boolean isInstanceLine(String text, int start) {
        if (!text.startsWith(INSTANCE_KEYWORD, start)) {
            return false;
        }
        var afterKeyword = start + INSTANCE_KEYWORD.length();
        var jsonStart = skipWhitespace(text, afterKeyword);
        return jsonStart > afterKeyword && jsonStart < text.length() && text.charAt(jsonStart) == '{';
    }

    private static int readToken(String text, int position, int lineNumber, List<Token> tokens) {
        var column = position + 1;
        if (text.startsWith("->", position) || text.startsWith("..", position)) {
            tokens.add(new Token(Token.Kind.PUNCT, text.substring(position, position + 2), lineNumber, column));
            return position + 2;
        }
        var current = text.charAt(position);
        if (isWordCharacter(current)) {
            var end = position;
            while (end < text.length() && isWordCharacter(text.charAt(end)) && !text.startsWith("->", end)) {
                end++;
            }
            var word = text.substring(position, end);
            tokens.add(new Token(classify(word), word, lineNumber, column));
            return end;
        }
        if (SINGLE_PUNCTUATION.indexOf(current) >= 0) {
            tokens.add(new Token(Token.Kind.PUNCT, String.valueOf(current), lineNumber, column));
            return position + 1;
        }
        var width = Character.charCount(text.codePointAt(position));
        tokens.add(new Token(Token.Kind.INVALID, text.substring(position, position + width), lineNumber, column));
        return position + width;
    }

    private static Token.Kind classify(String word) {
        if (word.chars().allMatch(WorkflowLexer::isDigit)) {
            return Token.Kind.INT;
        }
        var first = word.charAt(0);
        if (isLowercase(first) && word.chars().noneMatch(WorkflowLexer::isUppercase)) {
            return Token.Kind.IDENT;
        }
        if (isLowercase(first) || isUppercase(first)) {
            return Token.Kind.ENUM_VALUE;
        }
        return Token.Kind.INVALID;
    }

    private static int skipWhitespace(String text, int position) {
        var current = position;
        while (current < text.length() && isWhitespace(text.charAt(current))) {
            current++;
        }
        return current;
    }

    private static boolean isWhitespace(char character) {
        return character == ' ' || character == '\t' || character == '\r';
    }

    private static boolean isWordCharacter(char character) {
        return isLowercase(character) || isUppercase(character) || isDigit(character) || character == '_'
                || character == '-';
    }

    private static boolean isLowercase(int character) {
        return character >= 'a' && character <= 'z';
    }

    private static boolean isUppercase(int character) {
        return character >= 'A' && character <= 'Z';
    }

    private static boolean isDigit(int character) {
        return character >= '0' && character <= '9';
    }
}
