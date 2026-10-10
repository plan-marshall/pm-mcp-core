/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.spi.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Validator of submitted AsciiDoc text")
class AsciiDocValidatorTest {

    private static final String DOCUMENT = """
            = Primitive SPI

            A table with a tab:\tsee below.

            == Contract

            [source,java]
            ----
            = not a title
            ==== not a section
            ....
            ----

            === Outcomes

            ****
            A sidebar with a listing.

            ----
            code
            ----
            ****

            |===
            | Name | Class
            |===

            == Registry
            """;

    private final AsciiDocValidator validator = new AsciiDocValidator();

    private static List<String> keysAndLines(List<ContentViolation> violations) {
        return violations.stream().map(violation -> violation.ruleKey() + "@" + violation.line()).toList();
    }

    static Stream<Arguments> acceptedTexts() {
        return Stream.of(
                Arguments.of("a title, sections, closed blocks and a tab", DOCUMENT),
                Arguments.of("no title", "A paragraph.\n\n== Section\n\nText."),
                Arguments.of("a section that starts below the title level", "== Section\n\n=== Sub\n\n== Next"),
                Arguments.of("a delimiter with trailing white space", "----  \ncode\n----\t"),
                Arguments.of("a commented-out title below the title", "= Title\n\n////\n= Draft\n////\n\nText."),
                Arguments.of("a listing delimiter inside a comment block", "////\n----\n////"),
                Arguments.of("a comment delimiter inside a listing", "----\n////\n----"),
                Arguments.of("a closed open block", "= Title\n\n--\nText.\n--"),
                Arguments.of("a listing inside an open block", "--\n----\ncode\n----\n--"),
                Arguments.of("an open block delimiter inside a listing", "----\n--\n----"),
                Arguments.of("a title and a deep section inside an open block",
                        "= Title\n\n--\n= Draft\n==== Deep\n--"),
                Arguments.of("a line of three hyphens, which opens nothing", "= Title\n\n---\n\nText."),
                Arguments.of("a title inside a table with a longer delimiter", "= Title\n\n|====\n= Draft\n|===="),
                Arguments.of("a line that only starts like a table delimiter", "= Title\n\n|===x\n\nText."),
                Arguments.of("a title inside a comma table", "= Title\n\n,===\n= Draft\n,==="),
                Arguments.of("a title inside a colon table", "= Title\n\n:===\n= Draft\n:==="),
                Arguments.of("a title inside an exclamation table", "= Title\n\n!===\n= Draft\n!==="),
                Arguments.of("a title inside a comma table with a longer delimiter",
                        "= Title\n\n,====\n= Draft\n,===="),
                Arguments.of("a title inside a colon table with a longer delimiter",
                        "= Title\n\n:====\n= Draft\n:===="),
                Arguments.of("a title inside an exclamation table with a longer delimiter",
                        "= Title\n\n!====\n= Draft\n!===="),
                Arguments.of("a line that only starts like a comma table delimiter", "= Title\n\n,===x\n\nText."),
                Arguments.of("an exclamation mark with two equals signs, which opens nothing",
                        "= Title\n\n!==\n\nText."));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("acceptedTexts")
    @DisplayName("accepts a text with")
    void acceptsText(String label, String text) {
        var violations = validator.validate(text);

        assertTrue(violations.isEmpty(), () -> label + " must be accepted, but: " + violations);
    }

    static Stream<Arguments> rejectedTexts() {
        return Stream.of(
                Arguments.of("no content", "", AsciiDocValidator.RULE_BLANK, 0),
                Arguments.of("white space only", " \n\t\n", AsciiDocValidator.RULE_BLANK, 0),
                Arguments.of("a bell character", "= Title\n\nText with a \u0007 bell.",
                        AsciiDocValidator.RULE_CONTROL_CHARACTER, 3),
                Arguments.of("a delete character", "= Title\n\u007F", AsciiDocValidator.RULE_CONTROL_CHARACTER, 2),
                Arguments.of("a listing that is never closed", "= Title\n\n----\ncode",
                        AsciiDocValidator.RULE_UNCLOSED_BLOCK, 3),
                Arguments.of("a listing closed by a longer delimiter", "----\ncode\n-----",
                        AsciiDocValidator.RULE_UNCLOSED_BLOCK, 1),
                Arguments.of("a comma table that is never closed", "= Title\n\n,===\ncell",
                        AsciiDocValidator.RULE_UNCLOSED_BLOCK, 3),
                Arguments.of("a colon table that is never closed", "= Title\n\n:===\ncell",
                        AsciiDocValidator.RULE_UNCLOSED_BLOCK, 3),
                Arguments.of("an exclamation table that is never closed", "= Title\n\n!===\ncell",
                        AsciiDocValidator.RULE_UNCLOSED_BLOCK, 3),
                Arguments.of("a title below a paragraph", "A paragraph.\n\n= Title",
                        AsciiDocValidator.RULE_TITLE_NOT_FIRST, 3),
                Arguments.of("a title below a block", "----\ncode\n----\n= Title",
                        AsciiDocValidator.RULE_TITLE_NOT_FIRST, 4),
                Arguments.of("a second title", "= One\n\nText.\n\n= Two", AsciiDocValidator.RULE_MULTIPLE_TITLES, 5),
                Arguments.of("a section two levels below the title", "= Title\n\n=== Deep",
                        AsciiDocValidator.RULE_SECTION_LEVEL_SKIPPED, 3),
                Arguments.of("a section two levels below its parent", "= Title\n\n== One\n\n==== Deep",
                        AsciiDocValidator.RULE_SECTION_LEVEL_SKIPPED, 5));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejectedTexts")
    @DisplayName("rejects a text with")
    void rejectsText(String label, String text, String ruleKey, int line) {
        var violations = validator.validate(text);

        assertEquals(List.of(ruleKey + "@" + line), keysAndLines(violations), label);
    }

    static Stream<Arguments> unclosedCommentAndOpenBlocks() {
        return Stream.of(
                Arguments.of("a comment block that is never closed", "= Title\n\n////\ncomment", 3),
                Arguments.of("a comment block closed by a longer delimiter", "////\ncomment\n/////", 1),
                Arguments.of("an open block that is never closed", "= Title\n\n--\nText.", 3),
                Arguments.of("an open block followed by three hyphens", "--\nText.\n---", 1));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unclosedCommentAndOpenBlocks")
    @DisplayName("reports as unclosed, on the line that opened it,")
    void rejectsUnclosedCommentAndOpenBlocks(String label, String text, int line) {
        var violations = validator.validate(text);

        assertEquals(List.of(AsciiDocValidator.RULE_UNCLOSED_BLOCK + "@" + line), keysAndLines(violations), label);
    }

    @Test
    @DisplayName("a listing left open inside an open block is reported with the open block")
    void unclosedListingInsideOpenBlock() {
        var text = "--\n----\ncode";

        var violations = validator.validate(text);

        assertEquals(List.of(AsciiDocValidator.RULE_UNCLOSED_BLOCK + "@1", AsciiDocValidator.RULE_UNCLOSED_BLOCK + "@2"),
                keysAndLines(violations));
    }

    @Test
    @DisplayName("reports every block left open, the outer one first")
    void nestedUnclosedBlocks() {
        var text = "****\nA sidebar.\n----\ncode";

        var violations = validator.validate(text);

        assertEquals(List.of(AsciiDocValidator.RULE_UNCLOSED_BLOCK + "@1", AsciiDocValidator.RULE_UNCLOSED_BLOCK + "@3"),
                keysAndLines(violations));
    }

    @Test
    @DisplayName("returns the violations of several rules in the order of the lines")
    void severalViolations() {
        var text = "Intro.\n= Title\n=== Deep\n\u0001\n....\nliteral";

        var violations = validator.validate(text);

        assertEquals(List.of(AsciiDocValidator.RULE_TITLE_NOT_FIRST + "@2",
                AsciiDocValidator.RULE_SECTION_LEVEL_SKIPPED + "@3",
                AsciiDocValidator.RULE_CONTROL_CHARACTER + "@4",
                AsciiDocValidator.RULE_UNCLOSED_BLOCK + "@5"), keysAndLines(violations));
    }

    @Test
    @DisplayName("refuses null instead of returning a violation")
    void nullText() {
        assertThrows(NullPointerException.class, () -> validator.validate(null));
    }
}
