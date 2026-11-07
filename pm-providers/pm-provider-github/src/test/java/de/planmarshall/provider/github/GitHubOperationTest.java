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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import de.cuioss.test.generator.junit.EnableGeneratorController;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The permission set per operation class. Every literal is a contract: the permission names ({@code pull_requests},
 * {@code issues}, {@code contents}, {@code metadata}, {@code administration}) and the access levels ({@code read},
 * {@code write}) are GitHub's wire vocabulary of an installation-token request, so no datum here is generated.
 */
@EnableGeneratorController
@DisplayName("GitHubOperation: permission set per operation class")
class GitHubOperationTest {

    @Test
    @DisplayName("requests pull_requests:write for a pull-request comment, not issues:write")
    void pullRequestCommentNeedsPullRequestsWrite() {
        var permissions = GitHubOperation.PULL_REQUEST_COMMENT.permissions();

        assertAll("the pull-request comment permissions",
                () -> assertEquals(Map.of("pull_requests", "write"), permissions,
                        "a pull-request comment needs pull_requests:write"),
                () -> assertFalse(permissions.containsKey("issues"),
                        "a pull-request comment asks for no issues access"));
    }

    @Test
    @DisplayName("maps every operation class to its narrowest permission set")
    void mapsOperations() {
        assertAll("the permission set of every operation class",
                () -> assertEquals(Map.of("pull_requests", "read"), GitHubOperation.REVIEW_THREADS_READ.permissions(),
                        "reading review threads needs pull_requests:read"),
                () -> assertEquals(Map.of("pull_requests", "write"),
                        GitHubOperation.REVIEW_THREAD_RESOLVE.permissions(),
                        "resolving a thread needs pull_requests:write"),
                () -> assertEquals(Map.of("issues", "write"), GitHubOperation.ISSUE_COMMENT.permissions(),
                        "an issue comment needs issues:write"),
                () -> assertEquals(Map.of("issues", "read"), GitHubOperation.ISSUE_READ.permissions(),
                        "reading an issue needs issues:read"),
                () -> assertEquals(Map.of("contents", "write", "pull_requests", "write"),
                        GitHubOperation.MERGE_QUEUE_ENQUEUE.permissions(),
                        "enqueueing needs contents and pull_requests write"),
                () -> assertEquals(Map.of("pull_requests", "read", "contents", "read", "metadata", "read"),
                        GitHubOperation.PULL_REQUEST_READ.permissions(),
                        "a pull-request read needs three read permissions"));
    }

    @ParameterizedTest
    @EnumSource(GitHubOperation.class)
    @DisplayName("never asks for an administrative permission and exposes an immutable set")
    void staysNarrow(GitHubOperation operation) {
        var permissions = operation.permissions();

        assertAll("the narrow permission set of " + operation,
                () -> assertFalse(permissions.isEmpty(), "every operation asks for some permission"),
                () -> assertFalse(permissions.containsKey("administration"),
                        "no operation asks for administration"),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> permissions.put("administration", "write"),
                        "the permission set cannot be modified"));
    }
}
