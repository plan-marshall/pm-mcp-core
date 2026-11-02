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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * The identity of the credential a provider acts with, as {@code ci.whoami} reports it: the login and user id the
 * provider assigns, the kind of the credential, and the permissions, scopes and expiry the provider reports for it.
 * <p>
 * A GitHub App acts with two identities at once: its own bot identity ({@link #login} and {@link #userId}) and the
 * operator whose user access token authors every write ({@link #operatorLogin}). The committer address of the bot is
 * derived from this identity by the consumer, not by the contract.
 *
 * @param login         the provider login the credential acts as (for a GitHub App, the App's bot login)
 * @param userId        the provider user id of that login
 * @param tokenKind     the kind of the credential
 * @param operatorLogin for a GitHub App, the login of the operator whose user access token authors the writes;
 *                      {@code null} otherwise
 * @param permissions   the permissions the credential has on the named repository, permission name to access level;
 *                      empty when no repository was named
 * @param scopes        the scopes the provider reports, empty where it reports none
 * @param expiresAt     the expiry the provider reports; {@code null} when it reports none
 * @since 0.1
 */
@Builder
public record Identity(String login, String userId, TokenKind tokenKind, @Nullable String operatorLogin,
                       Map<String, String> permissions, List<String> scopes, @Nullable Instant expiresAt) {

    /**
     * @param login         the login
     * @param userId        the user id
     * @param tokenKind     the kind
     * @param operatorLogin the operator login of a GitHub App, or {@code null}
     * @param permissions   the repository permissions
     * @param scopes        the scopes
     * @param expiresAt     the expiry, or {@code null}
     * @throws NullPointerException     if a required component is {@code null}
     * @throws IllegalArgumentException if the login or the user id is blank, or an operator login is blank
     */
    public Identity {
        requireText(login, "login");
        requireText(userId, "userId");
        Objects.requireNonNull(tokenKind, "tokenKind");
        if (operatorLogin != null && operatorLogin.isBlank()) {
            throw new IllegalArgumentException("an operator login is never blank");
        }
        permissions = Map.copyOf(permissions);
        scopes = List.copyOf(scopes);
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " is never blank");
        }
    }
}
