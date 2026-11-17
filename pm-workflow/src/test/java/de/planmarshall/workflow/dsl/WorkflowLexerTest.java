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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

// Every datum in this class is a contract: the tokenizer source under test and its expected tokens.
/**
 * The line-oriented tokenizer of the workflow language (PM-IMPL-5): comments, positions, token classes and the JSON
 * token of an {@code instance} line.
 */
@DisplayName("WorkflowLexer")
class WorkflowLexerTest {

    private static List<Token> tokensOf(String line) {
        var lines = WorkflowLexer.tokenize(line);
        assertEquals(1, lines.size(), "the source holds one line with tokens");
        return lines.getFirst().tokens();
    }

    private static List<String> texts(List<Token> tokens) {
        return tokens.stream().map(Token::text).toList();
    }

    @Nested
    @DisplayName("Lines and comments")
    class LinesAndComments {

        @Test
        @DisplayName("drops a comment to the end of its line and leaves out blank and comment-only lines")
        void dropsComments() {
            var source = "# a leading comment\n\nstate demo.begin # trailing words\n   \t\nstate demo.end\n";

            var lines = WorkflowLexer.tokenize(source);

            assertAll(
                    () -> assertEquals(2, lines.size(), "the comment leaves two lines"),
                    () -> assertEquals(3, lines.get(0).number(), "the first line keeps its number"),
                    () -> assertEquals(List.of("state", "demo", ".", "begin"), texts(lines.get(0).tokens()),
                            "the first line keeps its tokens"),
                    () -> assertEquals(5, lines.get(1).number(), "the second line keeps its number"),
                    () -> assertEquals(List.of("state", "demo", ".", "end"), texts(lines.get(1).tokens()),
                            "the second line keeps its tokens"));
        }

        @Test
        @DisplayName("returns no line for an empty source")
        void emptySource() {
            assertTrue(WorkflowLexer.tokenize("").isEmpty(), "an empty source yields no line");
        }

        @Test
        @DisplayName("treats a carriage return before the line feed as whitespace")
        void carriageReturn() {
            var lines = WorkflowLexer.tokenize("start demo\r\nphase init\r\n");

            assertAll(
                    () -> assertEquals(List.of("start", "demo"), texts(lines.get(0).tokens()),
                            "the first CRLF line keeps its tokens"),
                    () -> assertEquals(List.of("phase", "init"), texts(lines.get(1).tokens()),
                            "the second CRLF line keeps its tokens"));
        }

        @Test
        @DisplayName("returns immutable lines and token lists")
        void immutable() {
            var lines = WorkflowLexer.tokenize("start demo\n");
            var tokens = lines.getFirst().tokens();

            assertAll(
                    () -> assertThrows(UnsupportedOperationException.class, lines::clear,
                            "the line list refuses a change"),
                    () -> assertThrows(UnsupportedOperationException.class, tokens::clear,
                            "the token list refuses a change"));
        }
    }

    @Nested
    @DisplayName("Positions")
    class Positions {

        @Test
        @DisplayName("records the 1-based line and column of every token, indentation included")
        void columns() {
            var lines = WorkflowLexer.tokenize("\n\n    link alt detour [submit] ()\n");
            var tokens = lines.getFirst().tokens();

            assertAll(
                    () -> assertEquals(3, tokens.getFirst().line(), "the first token speaks on the third line"),
                    () -> assertEquals(List.of(5, 10, 14, 21, 22, 28, 30, 31),
                            tokens.stream().map(Token::column).toList(), "every token keeps its column"),
                    () -> assertEquals(9, tokens.getFirst().endColumn(),
                            "the first token ends at its column"));
        }

        @Test
        @DisplayName("counts a character outside the basic plane as one token and continues after it")
        void supplementaryCharacter() {
            var tokens = tokensOf("a 😀 b");

            assertAll(
                    () -> assertEquals(Token.Kind.INVALID, tokens.get(1).kind(),
                            "the surrogate pair is an invalid token"),
                    () -> assertEquals("😀", tokens.get(1).text(), "the surrogate pair keeps its text"),
                    () -> assertEquals(6, tokens.get(2).column(), "the next token follows the pair"));
        }
    }

    @Nested
    @DisplayName("Token classes")
    class TokenClasses {

        @ParameterizedTest(name = "''{0}'' is {1}")
        @CsvSource({
                "demo, IDENT",
                "commit_and_push, IDENT",
                "probe-2, IDENT",
                "FIX, ENUM_VALUE",
                "Mirror, ENUM_VALUE",
                "camelCase, ENUM_VALUE",
                "42, INT",
                "0, INT",
                "_hidden, INVALID",
                "-dash, INVALID",
                "2nd, INVALID",
                "@, INVALID",
                "{, INVALID"})
        @DisplayName("classifies a word or character")
        void classifies(String text, Token.Kind kind) {
            var tokens = tokensOf(text);

            assertAll(
                    () -> assertEquals(1, tokens.size(), "the source yields one token"),
                    () -> assertEquals(kind, tokens.getFirst().kind(), "the token carries its kind"),
                    () -> assertEquals(text, tokens.getFirst().text(), "the token keeps its text"));
        }

        @ParameterizedTest(name = "''{0}''")
        @ValueSource(strings = {"->", "..", ".", ":", ",", "(", ")", "[", "]", "<", ">", "|", "=", "?", "/"})
        @DisplayName("reads a punctuation mark")
        void punctuation(String mark) {
            var tokens = tokensOf(mark);

            assertAll(
                    () -> assertEquals(1, tokens.size(), "a punctuation mark yields one token"),
                    () -> assertEquals(Token.Kind.PUNCT, tokens.getFirst().kind(),
                            "the mark carries the punct kind"),
                    () -> assertEquals(mark, tokens.getFirst().text(), "the mark keeps its text"));
        }

        @Test
        @DisplayName("ends a word before an arrow")
        void arrowEndsWord() {
            assertEquals(List.of("done", "->", "next-state"), texts(tokensOf("done->next-state")),
                    "an arrow ends the word before it");
        }

        @Test
        @DisplayName("splits a range into its integers and the range mark")
        void range() {
            assertEquals(List.of("int", "[", "0", "..", "5", "]"), texts(tokensOf("int[0..5]")),
                    "a range splits into integers and the mark");
        }

        @Test
        @DisplayName("splits a primitive identifier and a predicate at their punctuation")
        void identifiers() {
            assertEquals(List.of("predicate", ":", "plan", ".", "allocate-remediation-tasks"),
                    texts(tokensOf("predicate:plan.allocate-remediation-tasks")),
                    "a predicate splits at its punctuation");
        }
    }

    @Nested
    @DisplayName("Instance line")
    class InstanceLine {

        @Test
        @DisplayName("keeps the JSON of an instance line as one token, a hash inside it included")
        void jsonToken() {
            var json = "{\"plan_id\": \"p#1\", \"version\": 2}";

            var tokens = tokensOf("  instance   " + json + "  ");

            assertAll(
                    () -> assertEquals(2, tokens.size(), "the line yields two tokens"),
                    () -> assertEquals(Token.Kind.IDENT, tokens.get(0).kind(), "the keyword is an identifier"),
                    () -> assertEquals("instance", tokens.get(0).text(), "the keyword keeps its text"),
                    () -> assertEquals(3, tokens.get(0).column(), "the keyword keeps its column"),
                    () -> assertEquals(Token.Kind.JSON, tokens.get(1).kind(), "the JSON is one token"),
                    () -> assertEquals(json, tokens.get(1).text(), "the JSON keeps its text"),
                    () -> assertEquals(14, tokens.get(1).column(), "the JSON keeps its column"));
        }

        @Test
        @DisplayName("reads an instance keyword without whitespace before the brace as ordinary tokens")
        void noWhitespace() {
            var tokens = tokensOf("instance{}");

            assertEquals(List.of(Token.Kind.IDENT, Token.Kind.INVALID, Token.Kind.INVALID),
                    tokens.stream().map(Token::kind).toList(),
                    "a brace without whitespace is invalid");
        }

        @Test
        @DisplayName("reads an instance keyword that is followed by no brace as ordinary tokens")
        void noBrace() {
            var tokens = tokensOf("instance plan # no JSON");

            assertEquals(List.of("instance", "plan"), texts(tokens),
                    "an instance without a brace stays plain");
        }

        @Test
        @DisplayName("reads a bare instance keyword as one identifier")
        void bareKeyword() {
            var tokens = tokensOf("instance");

            assertAll(
                    () -> assertEquals(1, tokens.size(), "a bare keyword is one token"),
                    () -> assertEquals(Token.Kind.IDENT, tokens.getFirst().kind(),
                            "a bare keyword is an identifier"));
        }

        @Test
        @DisplayName("never matches a JSON token against a keyword or punctuation mark")
        void jsonIsNoKeyword() {
            var json = new Token(Token.Kind.JSON, "{", 1, 1);
            var punct = new Token(Token.Kind.PUNCT, "{", 1, 1);

            assertAll(
                    () -> assertFalse(json.is("{"), "a JSON token matches no keyword"),
                    () -> assertTrue(punct.is("{"), "a punctuation token matches its mark"));
        }
    }
}
