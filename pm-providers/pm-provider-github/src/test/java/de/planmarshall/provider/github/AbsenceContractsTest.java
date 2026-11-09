/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.github;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The absent case of the review components this conversion changed: a {@code null} input is the absence that
 * {@code Optional.empty()} carried before, and the accessor returns it unchanged.
 *
 * @since 0.1
 */
class AbsenceContractsTest {

    @Test
    @DisplayName("a review comment of a deleted account has no author")
    void reviewCommentWithoutAuthor() {
        var comment = new GitHubClient.ReviewComment("id", "1", null, "body", "now");

        assertNull(comment.author(), "a deleted account has no author");
    }

    @Test
    @DisplayName("an outdated or file-level review thread has no line")
    void reviewThreadWithoutLine() {
        var thread = new GitHubClient.ReviewThread("id", false, true, "file", null, List.of(), true);

        assertAll(
                () -> assertNull(thread.line(), "an outdated thread has no line"),
                () -> assertTrue(thread.comments().isEmpty(), "an empty thread carries no comments"));
    }
}
