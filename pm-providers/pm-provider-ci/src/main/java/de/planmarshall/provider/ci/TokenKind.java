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

import java.util.Optional;

/**
 * The closed kind of the credential a CI provider acts with: the {@code token_kind} values of the machine
 * credential store (Store Schemas § Credential File) plus the GitHub App, whose token kind is no
 * {@code token_kind} value because its stored secret is the App entry ({@code auth_type: github_app}).
 * <p>
 * A provider determines the kind from what it reports and never guesses one: a credential whose kind the
 * provider cannot establish is refused rather than labelled.
 *
 * @since 0.1
 */
public enum TokenKind {

    /** A GitHub fine-grained personal access token ({@code github_pat_}). */
    GITHUB_FINE_GRAINED_PAT("github_fine_grained_pat"),
    /** A GitHub classic personal access token ({@code ghp_}). */
    GITHUB_CLASSIC_PAT("github_classic_pat"),
    /** A GitLab project access token (a bot user {@code project_<id>_bot_…}). */
    GITLAB_PROJECT_TOKEN("gitlab_project_token"),
    /** A GitLab group access token (a bot user {@code group_<id>_bot_…}). */
    GITLAB_GROUP_TOKEN("gitlab_group_token"),
    /** A GitLab personal access token of a service user or of the operator. */
    GITLAB_PERSONAL_TOKEN("gitlab_personal_token"),
    /** A GitLab OAuth token (the token of a {@code glab} login). */
    GITLAB_OAUTH_TOKEN("gitlab_oauth_token"),
    /** An access token of a Forgejo or Gitea forge, Codeberg included. */
    FORGEJO_ACCESS_TOKEN("forgejo_access_token"),
    /** A GitHub App; its installation token or its user access token acts, never the App key itself. */
    GITHUB_APP("github_app");

    private final String wireName;

    TokenKind(String wireName) {
        this.wireName = wireName;
    }

    /**
     * @return the value the credential store records as {@code token_kind}
     */
    public String wireName() {
        return wireName;
    }

    /**
     * @param wireName a {@code token_kind} value
     * @return the kind recorded under that value; empty if the value names no kind
     */
    public static Optional<TokenKind> fromWireName(String wireName) {
        for (TokenKind kind : values()) {
            if (kind.wireName.equals(wireName)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
