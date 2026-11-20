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

import lombok.experimental.UtilityClass;

/**
 * The lexer of {@link AssertionMessages}: the view of a source text in which only code is left readable.
 * <p>
 * Comments and the content of string, character and text-block literals are blanked to spaces, while the delimiters of
 * a string are kept, so a call inside a comment or a literal is not read as code. The length and the line breaks of the
 * source are kept, so an index into the view is an index into the source.
 *
 * @since 0.1
 */
@UtilityClass
class CodeView {

    /**
     * @param source the source text
     * @return the same text with comments and the content of string, character and text-block literals blanked
     */
    static String of(String source) {
        var view = new StringBuilder(source);
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
                        view.setCharAt(index, ' ');
                    } else if (opensTextBlock(source, index)) {
                        state = State.TEXT_BLOCK;
                        blank(view, index, 3);
                        index += 2;
                    } else if (current == '"') {
                        state = State.STRING;
                    } else if (current == '\'') {
                        state = State.CHARACTER;
                    }
                }
                case LINE_COMMENT -> {
                    if (current == '\n') {
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
                    } else if (current != '\n') {
                        view.setCharAt(index, ' ');
                    }
                }
                case TEXT_BLOCK -> {
                    if (current == '\\' && index + 1 < source.length()) {
                        blank(view, index, 2);
                        index++;
                    } else if (opensTextBlock(source, index)) {
                        blank(view, index, 3);
                        index += 2;
                        state = State.CODE;
                    } else if (current != '\n') {
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
                    } else if (current != '\n') {
                        view.setCharAt(index, ' ');
                    }
                }
            }
        }
        return view.toString();
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
