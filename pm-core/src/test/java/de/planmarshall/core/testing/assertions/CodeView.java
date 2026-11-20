/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.testing.assertions;

import java.util.BitSet;

/**
 * The lexer of {@link AssertionMessages}: the view of a source text in which only code is left readable.
 * <p>
 * Comments and the content of string, character and text-block literals are blanked to spaces, while the delimiters of
 * a string are kept, so a call inside a comment or a literal is not read as code. A text block is blanked whole,
 * delimiters included, and its opening is recorded in {@link #textBlocks()}, so a blank argument can still be told from
 * a text block. The length and the line terminators of the source — a line feed, a carriage return or both — are kept,
 * so an index into the view is an index into the source and a line counted in the view is a line of the source.
 *
 * @param view the source text with comments and the content of string, character and text-block literals blanked
 * @param textBlocks the indices at which a text block opens
 * @since 0.1
 */
record CodeView(String view, BitSet textBlocks) {

    /**
     * @param source the source text
     * @return the lexed view of the source
     */
    static CodeView of(String source) {
        var view = new StringBuilder(source);
        var textBlocks = new BitSet();
        var state = State.CODE;
        for (var index = 0; index < source.length(); index++) {
            var current = source.charAt(index);
            var next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            switch (state) {
                case CODE -> {
                    if (current == '/' && next == '/') {
                        state = State.LINE_COMMENT;
                        view.setCharAt(index, ' ');
                    } else if (current == '/' && next == '*') {
                        state = State.BLOCK_COMMENT;
                        blank(view, index, 2);
                        index++;
                    } else if (opensTextBlock(source, index)) {
                        state = State.TEXT_BLOCK;
                        textBlocks.set(index);
                        blank(view, index, 3);
                        index += 2;
                    } else if (current == '"') {
                        state = State.STRING;
                    } else if (current == '\'') {
                        state = State.CHARACTER;
                    }
                }
                case LINE_COMMENT -> {
                    if (isLineTerminator(current)) {
                        state = State.CODE;
                    } else {
                        view.setCharAt(index, ' ');
                    }
                }
                case BLOCK_COMMENT -> {
                    if (current == '*' && next == '/') {
                        blank(view, index, 2);
                        index++;
                        state = State.CODE;
                    } else if (!isLineTerminator(current)) {
                        view.setCharAt(index, ' ');
                    }
                }
                case TEXT_BLOCK -> {
                    if (current == '\\' && isLineTerminator(next)) {
                        view.setCharAt(index, ' ');
                    } else if (current == '\\' && index + 1 < source.length()) {
                        blank(view, index, 2);
                        index++;
                    } else if (opensTextBlock(source, index)) {
                        blank(view, index, 3);
                        index += 2;
                        state = State.CODE;
                    } else if (!isLineTerminator(current)) {
                        view.setCharAt(index, ' ');
                    }
                }
                case STRING, CHARACTER -> {
                    var closing = state == State.STRING ? '"' : '\'';
                    if (current == '\\' && index + 1 < source.length()) {
                        blank(view, index, 2);
                        index++;
                    } else if (current == closing) {
                        state = State.CODE;
                    } else if (!isLineTerminator(current)) {
                        view.setCharAt(index, ' ');
                    }
                }
            }
        }
        return new CodeView(view.toString(), textBlocks);
    }

    /**
     * @param text the text to count in
     * @param position the index whose line is asked for
     * @return the one-based line of the index, a carriage return followed by a line feed counted as one terminator
     */
    static int lineAt(String text, int position) {
        var line = 1;
        for (var index = 0; index < position; index++) {
            var current = text.charAt(index);
            if (current == '\n' || (current == '\r' && (index + 1 >= text.length() || text.charAt(index + 1) != '\n'))) {
                line++;
            }
        }
        return line;
    }

    /**
     * @param from the first index of a span
     * @param to the index after its end
     * @return whether a text block opens inside the span
     */
    boolean opensTextBlockIn(int from, int to) {
        var opening = textBlocks.nextSetBit(from);
        return opening >= 0 && opening < to;
    }

    /** @return whether a character ends a line: a line feed or a carriage return */
    private static boolean isLineTerminator(char current) {
        return current == '\n' || current == '\r';
    }

    /** @return whether three double quotes start at {@code index}, the delimiter of a text block */
    private static boolean opensTextBlock(String source, int index) {
        return index + 2 < source.length() && source.charAt(index) == '"' && source.charAt(index + 1) == '"'
                && source.charAt(index + 2) == '"';
    }

    private static void blank(StringBuilder view, int from, int count) {
        for (var offset = 0; offset < count; offset++) {
            view.setCharAt(from + offset, ' ');
        }
    }

    /** The lexical state of {@link #of(String)}. */
    private enum State {
        CODE, LINE_COMMENT, BLOCK_COMMENT, TEXT_BLOCK, STRING, CHARACTER
    }
}
