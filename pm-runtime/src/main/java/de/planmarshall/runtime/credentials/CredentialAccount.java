/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.credentials;

import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The account of a credential entry (PM-CRED-2): {@code <credential_key>} for a global entry,
 * {@code <project>/<credential_key>} for a project entry, where {@code <project>} is the project's
 * enrolment identifier. Both parts are restricted to {@code [a-zA-Z0-9._-]} and are never
 * {@code .} or {@code ..} (PM-CRED-3), so an account is also a safe relative file path.
 *
 * @param project the enrolment identifier, {@code null} for a global entry
 * @param key     the credential key
 */
public record CredentialAccount(@Nullable String project, String key) {

    private static final Pattern NAME = Pattern.compile("[a-zA-Z0-9._-]{1,128}");

    /**
     * Validates both parts.
     *
     * @param project the enrolment identifier
     * @param key     the credential key
     * @throws InvalidCredentialNameException for a part outside the allowed characters
     */
    public CredentialAccount {
        check(key);
        if (project != null) {
            check(project);
        }
    }

    /**
     * @param key the credential key
     * @return the global entry of the key
     */
    public static CredentialAccount global(String key) {
        return new CredentialAccount(null, key);
    }

    /**
     * @param project the enrolment identifier
     * @param key     the credential key
     * @return the project entry of the key
     */
    public static CredentialAccount project(String project, String key) {
        return new CredentialAccount(project, key);
    }

    /**
     * The inverse of {@link #account()}, for a store that lists its accounts.
     *
     * @param account {@code <key>} or {@code <project>/<key>}
     * @return the account
     * @throws InvalidCredentialNameException for a part outside the allowed characters
     */
    static CredentialAccount parse(String account) {
        int slash = account.indexOf('/');
        return slash < 0 ? global(account) : project(account.substring(0, slash), account.substring(slash + 1));
    }

    /** @return the keyring account: {@code <key>} or {@code <project>/<key>} */
    public String account() {
        return project != null ? project + "/" + key : key;
    }

    private static void check(@Nullable String name) {
        if (name == null || !NAME.matcher(name).matches() || ".".equals(name) || "..".equals(name)) {
            throw new InvalidCredentialNameException(name);
        }
    }
}
