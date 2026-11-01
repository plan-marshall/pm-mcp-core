/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.ci;

import java.util.Objects;

/**
 * The comment {@code ci.issue-comment} posted: the provider's id of it and its web URL.
 *
 * @param id  the comment id the provider assigns
 * @param url the comment's web URL
 * @since 0.1
 */
public record IssueComment(String id, String url) {

    /**
     * @param id  the comment id
     * @param url the comment URL
     * @throws NullPointerException     if a component is {@code null}
     * @throws IllegalArgumentException if a component is blank
     */
    public IssueComment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(url, "url");
        if (id.isBlank()) {
            throw new IllegalArgumentException("a comment id is never blank");
        }
        if (url.isBlank()) {
            throw new IllegalArgumentException("a comment URL is never blank");
        }
    }
}
