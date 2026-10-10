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

/**
 * One token of a line of the workflow language, with its 1-based position in the source (PM-IMPL-5).
 *
 * @param kind   what the token is
 * @param text   the text of the token as read
 * @param line   the 1-based line of the token
 * @param column the 1-based column of its first character
 */
record Token(Kind kind, String text, int line, int column) {

    /**
     * The kinds of token the lexer distinguishes.
     */
    enum Kind {
        /** A lowercase word: a keyword, an identifier or a lowercase enum value. */
        IDENT,
        /** A word that starts with a letter and contains an upper-case letter: an enum value only. */
        ENUM_VALUE,
        /** A word of digits. */
        INT,
        /** A punctuation mark: {@code -> .. . : , ( ) [ ] < > | = ? /}. */
        PUNCT,
        /** The raw remainder of an {@code instance} line: one line of JSON. */
        JSON,
        /** A word or character the language does not know. */
        INVALID
    }

    /**
     * Tells whether the token is the given keyword or punctuation mark; the JSON of an instance line never is.
     *
     * @param value the expected text
     * @return {@code true} if the token has exactly that text and is no JSON token
     */
    boolean is(String value) {
        return kind != Kind.JSON && text.equals(value);
    }

    /**
     * @return the 1-based column just after the last character of the token
     */
    int endColumn() {
        return column + text.length();
    }
}
