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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builders of the two review records of {@link GitHubClient} build the value the canonical constructor builds.
 * Neither record validates its components, so there is no constructor refusal for the builder to mirror. Every value
 * is generated.
 */
@EnableGeneratorController
@DisplayName("GitHubClient review records")
class GitHubClientRecordsTest {

    private static String text() {
        return Generators.letterStrings(1, 20).next();
    }

    private static GitHubClient.ReviewComment comment() {
        return GitHubClient.ReviewComment.builder()
                .id(text())
                .databaseId(text())
                .author(text())
                .body(text())
                .createdAt(text())
                .build();
    }

    @Test
    @DisplayName("the comment builder builds the comment the constructor builds")
    void commentBuilderEqualsConstructor() {
        var id = text();
        var databaseId = text();
        var author = text();
        var body = text();
        var createdAt = text();

        var built = GitHubClient.ReviewComment.builder()
                .id(id)
                .databaseId(databaseId)
                .author(author)
                .body(body)
                .createdAt(createdAt)
                .build();
        var constructed = new GitHubClient.ReviewComment(id, databaseId, author, body, createdAt);

        assertEquals(constructed, built, "the builder builds the same comment value");
    }

    @Test
    @DisplayName("the thread builder builds the thread the constructor builds")
    void threadBuilderEqualsConstructor() {
        var id = text();
        var resolved = Generators.booleans().next();
        var outdated = Generators.booleans().next();
        var path = text();
        var line = text();
        var comments = List.of(comment());
        var complete = Generators.booleans().next();

        var built = GitHubClient.ReviewThread.builder()
                .id(id)
                .resolved(resolved)
                .outdated(outdated)
                .path(path)
                .line(line)
                .comments(comments)
                .complete(complete)
                .build();
        var constructed = new GitHubClient.ReviewThread(id, resolved, outdated, path, line, comments, complete);

        assertEquals(constructed, built, "the builder builds the same thread value");
    }
}
