/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.ast;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The closed input types of PLAN-V2-15 that a unit declares in its frontmatter: their source form and the bound
 * refusal of the four that carry a range.
 */
@DisplayName("Input type source forms")
class InputTypeTest {

    @Test
    @DisplayName("the text-like and path types print the form the frontmatter declares")
    void sourceForms() {
        assertAll(
                () -> assertEquals("text", new InputType.Text(null, null).sourceText(), "an unbounded text source form"),
                () -> assertEquals("text[1..2000]", new InputType.Text(1, 2000).sourceText(), "a bounded text source form"),
                () -> assertEquals("commit_message", new InputType.CommitMessage(null, null).sourceText(),
                        "an unbounded commit message source form"),
                () -> assertEquals("commit_message[1..2000]", new InputType.CommitMessage(1, 2000).sourceText(),
                        "a bounded commit message source form"),
                () -> assertEquals("path", new InputType.Path().sourceText(), "a path prints its source form"),
                () -> assertEquals("list<path>", new InputType.PathList(null, null).sourceText(),
                        "an unbounded path list keeps its source form"),
                () -> assertEquals("list<path>[0..500]", new InputType.PathList(0, 500).sourceText(),
                        "a bounded path list keeps its source form"));
    }

    @Test
    @DisplayName("a single bound is refused by every ranged type")
    void singleBoundRefused() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new InputType.Text(1, null),
                        "text refuses a lone low bound"),
                () -> assertThrows(IllegalArgumentException.class, () -> new InputType.CommitMessage(null, 2000),
                        "commit message refuses a lone high bound"),
                () -> assertThrows(IllegalArgumentException.class, () -> new InputType.PathList(0, null),
                        "path list refuses a lone low bound"));
    }
}