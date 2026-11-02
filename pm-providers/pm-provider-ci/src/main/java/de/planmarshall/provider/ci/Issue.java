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

import java.util.List;
import java.util.Objects;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * One issue as {@code ci.issue-view} answers it: the title and body as text, and the labels, milestone, assignees,
 * state and URL as structured fields the consumer maps to the issue record.
 * <p>
 * The title and body are remote text. This contract returns them unvalidated and says so: the level-1 validator
 * cleans them before a model sees them, never the provider.
 *
 * @param title      the title text
 * @param body       the body text, empty when the issue has none
 * @param labels     the labels by name, in the provider's order
 * @param milestone  the milestone title; {@code null} when the issue has no milestone
 * @param assignees  the assignee logins, in the provider's order
 * @param state      the state as the provider names it ({@code open}, {@code closed} or {@code opened})
 * @param url        the issue's web URL
 * @since 0.1
 */
@Builder
public record Issue(String title, String body, List<String> labels, @Nullable String milestone,
                    List<String> assignees, String state, String url) {

    /**
     * @param title     the title
     * @param body      the body
     * @param labels    the labels
     * @param milestone the milestone title, or {@code null}
     * @param assignees the assignee logins
     * @param state     the state
     * @param url       the web URL
     * @throws NullPointerException     if a required component is {@code null}
     * @throws IllegalArgumentException if the title, the state or the URL is blank, or a milestone title is blank
     */
    public Issue {
        requireText(title, "title");
        Objects.requireNonNull(body, "body");
        labels = List.copyOf(labels);
        if (milestone != null && milestone.isBlank()) {
            throw new IllegalArgumentException("a milestone title is never blank");
        }
        assignees = List.copyOf(assignees);
        requireText(state, "state");
        requireText(url, "url");
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " is never blank");
        }
    }
}
