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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.regex.Pattern;

/**
 * Validates AsciiDoc text a model submits (PM-WF-6), with the rules that the text decides without an AsciiDoc
 * processor: it is not blank, it holds no control character, every delimited block is closed, it has at most one
 * document title and that title comes first, and no section level is skipped on the way down.
 * <p>
 * A line inside a delimited block is content of the block: it is neither a title nor a section, and inside a listing,
 * literal, passthrough or comment block it does not open a further block. An open block, delimited by a line of
 * exactly two hyphens, is a block like every other that is not verbatim: a further block may open inside it.
 *
 * @since 0.1
 */
public final class AsciiDocValidator implements ContentValidator {

    /** The text is empty or consists of white space only. */
    public static final String RULE_BLANK = "asciidoc.blank";

    /** A line holds a control character other than a tab. */
    public static final String RULE_CONTROL_CHARACTER = "asciidoc.control-character";

    /** A delimited block is opened and never closed. */
    public static final String RULE_UNCLOSED_BLOCK = "asciidoc.unclosed-block";

    /** The document title is not the first non-blank line. */
    public static final String RULE_TITLE_NOT_FIRST = "asciidoc.title-not-first";

    /** The text has more than one document title. */
    public static final String RULE_MULTIPLE_TITLES = "asciidoc.multiple-titles";

    /** A section is more than one level below the section before it. */
    public static final String RULE_SECTION_LEVEL_SKIPPED = "asciidoc.section-level-skipped";

    /** The delimiter of an open block: exactly two hyphens, so that three are ordinary text. */
    private static final String OPEN_BLOCK_DELIMITER = "--";

    private static final Pattern BLOCK_DELIMITER = Pattern.compile(
            "--|-{4,}|\\.{4,}|={4,}|\\*{4,}|_{4,}|\\+{4,}|/{4,}|\\|={3,}");

    private static final Pattern TITLE = Pattern.compile("= \\S.*");

    private static final Pattern SECTION = Pattern.compile("(?<marker>={2,6}) \\S.*");

    /**
     * The characters that delimit the blocks whose content is taken verbatim: listing, literal, passthrough and
     * comment. The open block starts with the character of the listing block and is told apart by its length.
     */
    private static final String VERBATIM_DELIMITERS = "-.+/";

    @Override
    public List<ContentViolation> validate(String text) {
        if (text.isBlank()) {
            return List.of(new ContentViolation(RULE_BLANK, 0, "the text is blank"));
        }
        var lines = text.lines().toList();
        var violations = new ArrayList<ContentViolation>();
        checkControlCharacters(lines, violations);
        checkStructure(lines, violations);
        violations.sort(Comparator.comparingInt(ContentViolation::line));
        return List.copyOf(violations);
    }

    private static void checkControlCharacters(List<String> lines, List<ContentViolation> violations) {
        for (var index = 0; index < lines.size(); index++) {
            lines.get(index).chars()
                    .filter(character -> Character.isISOControl(character) && character != '\t')
                    .findFirst()
                    .ifPresent(violationAt(index + 1, violations));
        }
    }

    private static IntConsumer violationAt(int line, List<ContentViolation> violations) {
        return character -> violations.add(new ContentViolation(RULE_CONTROL_CHARACTER, line,
                "the line holds the control character U+%04X".formatted(character)));
    }

    private static void checkStructure(List<String> lines, List<ContentViolation> violations) {
        var openBlocks = new ArrayDeque<OpenBlock>();
        var outline = new Outline(violations);
        for (var index = 0; index < lines.size(); index++) {
            var line = lines.get(index).stripTrailing();
            if (BLOCK_DELIMITER.matcher(line).matches()) {
                openOrClose(line, index + 1, openBlocks);
                outline.acceptBlock();
            } else if (openBlocks.isEmpty() && !line.isBlank()) {
                outline.accept(line, index + 1);
            }
        }
        openBlocks.descendingIterator().forEachRemaining(block -> violations.add(new ContentViolation(
                RULE_UNCLOSED_BLOCK, block.line(), "the block opened with '%s' is never closed"
                .formatted(block.delimiter()))));
    }

    private static void openOrClose(String delimiter, int line, Deque<OpenBlock> openBlocks) {
        var innermost = openBlocks.peek();
        if (innermost != null && innermost.delimiter().equals(delimiter)) {
            openBlocks.pop();
        } else if (innermost == null || !innermost.isVerbatim()) {
            openBlocks.push(new OpenBlock(delimiter, line));
        }
    }

    private record OpenBlock(String delimiter, int line) {

        boolean isVerbatim() {
            return !OPEN_BLOCK_DELIMITER.equals(delimiter) && VERBATIM_DELIMITERS.indexOf(delimiter.charAt(0)) >= 0;
        }
    }

    /** Follows the title and the sections of a text over its lines outside of blocks. */
    private static final class Outline {

        private final List<ContentViolation> violations;
        private boolean firstLineSeen;
        private boolean titleSeen;
        private int level;

        Outline(List<ContentViolation> violations) {
            this.violations = violations;
        }

        void acceptBlock() {
            firstLineSeen = true;
        }

        void accept(String line, int number) {
            var first = !firstLineSeen;
            firstLineSeen = true;
            if (TITLE.matcher(line).matches()) {
                acceptTitle(line, number, first);
                return;
            }
            var section = SECTION.matcher(line);
            if (section.matches()) {
                var sectionLevel = section.group("marker").length() - 1;
                if (sectionLevel > level + 1) {
                    violations.add(new ContentViolation(RULE_SECTION_LEVEL_SKIPPED, number,
                            "the section '%s' is of level %d below a section of level %d".formatted(line,
                                    sectionLevel, level)));
                }
                level = sectionLevel;
            }
        }

        private void acceptTitle(String line, int number, boolean first) {
            if (titleSeen) {
                violations.add(new ContentViolation(RULE_MULTIPLE_TITLES, number,
                        "'%s' is a second document title".formatted(line)));
            } else if (!first) {
                violations.add(new ContentViolation(RULE_TITLE_NOT_FIRST, number,
                        "the document title '%s' is not the first non-blank line".formatted(line)));
            }
            titleSeen = true;
        }
    }
}
